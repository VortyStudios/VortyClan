package me.vorty.vortyclan.manager;

import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.model.Clan;
import me.vorty.vortyclan.model.ClanHome;
import me.vorty.vortyclan.model.Member;
import me.vorty.vortyclan.model.Role;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Holds all clan data in memory and persists it to data.yml. Thread-safe (Folia). */
public final class ClanManager {

    public record Invite(UUID clanId, String inviter, long expires) {
    }

    private final VortyClan plugin;
    private final File file;
    private final Object fileLock = new Object();
    private final AtomicBoolean savePending = new AtomicBoolean(false);

    private final Map<UUID, Clan> clans = new ConcurrentHashMap<>();
    private final Map<UUID, Clan> byPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Invite>> invites = new ConcurrentHashMap<>();

    private volatile List<Clan> rankCache = List.of();
    private volatile long rankTime = 0L;

    public ClanManager(VortyClan plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    // ------------------------------------------------------------------ lookups

    public Clan byPlayer(UUID player) {
        return byPlayer.get(player);
    }

    public Clan byId(UUID id) {
        return clans.get(id);
    }

    public Clan byName(String name) {
        if (name == null) return null;
        for (Clan c : clans.values()) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    public Clan byTag(String tag) {
        if (tag == null) return null;
        for (Clan c : clans.values()) {
            if (c.tag.equalsIgnoreCase(tag)) return c;
        }
        return null;
    }

    /** Finds a clan by name, then by tag. */
    public Clan find(String s) {
        Clan c = byName(s);
        return c != null ? c : byTag(s);
    }

    public Collection<Clan> all() {
        return clans.values();
    }

    public boolean nameTaken(String name, Clan except) {
        Clan c = byName(name);
        return c != null && c != except;
    }

    public boolean tagTaken(String tag, Clan except) {
        Clan c = byTag(tag);
        return c != null && c != except;
    }

    // ------------------------------------------------------------------ mutations

    public synchronized Clan create(UUID owner, String ownerName, String name, String tag) {
        Clan c = new Clan(UUID.randomUUID(), name, tag, System.currentTimeMillis());
        c.members.put(owner, new Member(owner, ownerName, Role.OWNER));
        clans.put(c.id, c);
        byPlayer.put(owner, c);
        invites.remove(owner);
        requestSave();
        return c;
    }

    public synchronized void disband(Clan c) {
        clans.remove(c.id);
        for (UUID u : c.members.keySet()) byPlayer.remove(u, c);
        for (Map<UUID, Invite> m : invites.values()) m.remove(c.id);
        requestSave();
    }

    public synchronized void addMember(Clan c, UUID id, String name, Role role) {
        c.members.put(id, new Member(id, name, role));
        byPlayer.put(id, c);
        invites.remove(id);
        requestSave();
    }

    public synchronized void removeMember(Clan c, UUID id) {
        c.members.remove(id);
        byPlayer.remove(id, c);
        requestSave();
    }

    public void updateName(UUID id, String name) {
        Clan c = byPlayer.get(id);
        if (c == null) return;
        Member m = c.members.get(id);
        if (m != null && !m.name.equals(name)) {
            m.name = name;
            requestSave();
        }
    }

    // ------------------------------------------------------------------ invites

    public void addInvite(UUID target, Clan clan, String inviter, long expires) {
        invites.computeIfAbsent(target, k -> new ConcurrentHashMap<>())
                .put(clan.id, new Invite(clan.id, inviter, expires));
    }

    public Invite invite(UUID target, UUID clanId) {
        Map<UUID, Invite> m = invites.get(target);
        if (m == null) return null;
        Invite inv = m.get(clanId);
        if (inv == null) return null;
        if (inv.expires() < System.currentTimeMillis() || !clans.containsKey(clanId)) {
            m.remove(clanId);
            return null;
        }
        return inv;
    }

    /** Valid (non-expired) invites for a player. */
    public List<Invite> invites(UUID target) {
        List<Invite> out = new ArrayList<>();
        Map<UUID, Invite> m = invites.get(target);
        if (m == null) return out;
        long now = System.currentTimeMillis();
        for (Invite inv : new ArrayList<>(m.values())) {
            if (inv.expires() < now || !clans.containsKey(inv.clanId())) {
                m.remove(inv.clanId());
            } else {
                out.add(inv);
            }
        }
        return out;
    }

    public void removeInvite(UUID target, UUID clanId) {
        Map<UUID, Invite> m = invites.get(target);
        if (m != null) m.remove(clanId);
    }

    // ------------------------------------------------------------------ ranking

    private static final Comparator<Clan> RANKING = Comparator
            .comparingInt((Clan c) -> c.score).reversed()
            .thenComparing(Comparator.comparingInt((Clan c) -> c.members.size()).reversed())
            .thenComparing(c -> c.name.toLowerCase());

    public List<Clan> ranking() {
        List<Clan> list = new ArrayList<>(clans.values());
        list.sort(RANKING);
        return list;
    }

    /** Ranking cached for a few seconds; meant for frequent callers such as placeholders. */
    public List<Clan> rankingCached() {
        long now = System.currentTimeMillis();
        if (now - rankTime > 3000L) {
            rankCache = ranking();
            rankTime = now;
        }
        return rankCache;
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        clans.clear();
        byPlayer.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("clans");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                Clan c = new Clan(UUID.fromString(key), s.getString("name", "clan"), s.getString("tag", "CLAN"),
                        s.getLong("created", System.currentTimeMillis()));
                String col = s.getString("color", "b");
                c.color = col.isEmpty() ? 'b' : col.charAt(0);
                c.description = s.getString("description", "");
                c.open = s.getBoolean("open", false);
                c.friendlyFire = s.getBoolean("friendly-fire", false);
                c.score = s.getInt("score", 0);

                ConfigurationSection h = s.getConfigurationSection("home");
                if (h != null) {
                    c.home = new ClanHome(h.getString("world", "world"), h.getDouble("x"), h.getDouble("y"),
                            h.getDouble("z"), (float) h.getDouble("yaw"), (float) h.getDouble("pitch"));
                }
                ConfigurationSection ms = s.getConfigurationSection("members");
                if (ms != null) {
                    for (String mk : ms.getKeys(false)) {
                        UUID uid = UUID.fromString(mk);
                        c.members.put(uid, new Member(uid, ms.getString(mk + ".name", "?"),
                                Role.fromString(ms.getString(mk + ".role"))));
                    }
                }
                ConfigurationSection bs = s.getConfigurationSection("banned");
                if (bs != null) {
                    for (String bk : bs.getKeys(false)) c.banned.put(UUID.fromString(bk), bs.getString(bk, "?"));
                }
                if (c.members.isEmpty()) continue;
                if (c.owner() == null) {
                    // Heal data with a missing owner: promote the first member.
                    c.members.values().iterator().next().role = Role.OWNER;
                }
                clans.put(c.id, c);
                for (UUID u : c.members.keySet()) byPlayer.put(u, c);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping corrupt clan entry '" + key + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + clans.size() + " clan(s).");
    }

    public void requestSave() {
        if (!plugin.isEnabled()) {
            saveNow();
            return;
        }
        if (savePending.compareAndSet(false, true)) {
            plugin.tasks().async(() -> {
                savePending.set(false);
                saveNow();
            });
        }
    }

    public void saveNow() {
        String data = serialize();
        synchronized (fileLock) {
            try {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                File tmp = new File(file.getPath() + ".tmp");
                Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not save data.yml: " + ex.getMessage());
            }
        }
    }

    private synchronized String serialize() {
        YamlConfiguration y = new YamlConfiguration();
        for (Clan c : clans.values()) {
            String p = "clans." + c.id + ".";
            y.set(p + "name", c.name);
            y.set(p + "tag", c.tag);
            y.set(p + "color", String.valueOf(c.color));
            y.set(p + "description", c.description);
            y.set(p + "open", c.open);
            y.set(p + "friendly-fire", c.friendlyFire);
            y.set(p + "score", c.score);
            y.set(p + "created", c.created);
            ClanHome h = c.home;
            if (h != null) {
                y.set(p + "home.world", h.world());
                y.set(p + "home.x", h.x());
                y.set(p + "home.y", h.y());
                y.set(p + "home.z", h.z());
                y.set(p + "home.yaw", (double) h.yaw());
                y.set(p + "home.pitch", (double) h.pitch());
            }
            for (Member m : c.members.values()) {
                y.set(p + "members." + m.id + ".name", m.name);
                y.set(p + "members." + m.id + ".role", m.role.name());
            }
            for (Map.Entry<UUID, String> b : c.banned.entrySet()) {
                y.set(p + "banned." + b.getKey(), b.getValue());
            }
        }
        return y.saveToString();
    }
}
