package me.vorty.vortyclan.manager;

import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.model.Clan;
import me.vorty.vortyclan.model.ClanHome;
import me.vorty.vortyclan.model.Member;
import me.vorty.vortyclan.model.Role;
import me.vorty.vortyclan.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * All clan rules live here. The /clan command and the GUIs both call these methods,
 * so permission checks and messages behave identically everywhere.
 */
public final class ClanService {

    private static final Pattern FORMAT_CODES = Pattern.compile("(?i)[&§](#[0-9a-f]{6}|[0-9a-fk-or])");
    private static final int PAGE_SIZE = 8;

    private final VortyClan plugin;
    private final ClanManager manager;
    private final Lang lang;
    private final Set<UUID> chatToggled = ConcurrentHashMap.newKeySet();

    public ClanService(VortyClan plugin) {
        this.plugin = plugin;
        this.manager = plugin.clans();
        this.lang = plugin.lang();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------ helpers

    public int maxMembers() {
        return cfg().getInt("clan.max-members", 20);
    }

    public int online(Clan c) {
        int n = 0;
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) n++;
        }
        return n;
    }

    /** Placeholder pairs describing a clan, for lang messages. */
    public Object[] ph(Clan c) {
        Member owner = c.owner();
        int rank = manager.ranking().indexOf(c) + 1;
        String created = new SimpleDateFormat("yyyy-MM-dd").format(new Date(c.created));
        return new Object[]{
                "clan", c.name,
                "tag", c.tag,
                "color", "&" + c.color,
                "owner", owner == null ? "-" : owner.name,
                "members", c.members.size(),
                "max", maxMembers(),
                "online", online(c),
                "score", c.score,
                "rank", rank,
                "status", lang.text(c.open ? "status.open" : "status.closed"),
                "ff", lang.text(c.friendlyFire ? "state.on" : "state.off"),
                "created", created,
                "desc", c.description.isEmpty() ? "-" : c.description
        };
    }

    private Clan requireClan(Player p) {
        Clan c = manager.byPlayer(p.getUniqueId());
        if (c == null) lang.send(p, "not-in-clan");
        return c;
    }

    private Clan requireRole(Player p, Role min) {
        Clan c = requireClan(p);
        if (c == null) return null;
        Member m = c.member(p.getUniqueId());
        if (m == null || !m.role.atLeast(min)) {
            lang.send(p, "need-role", "role", lang.roleText(min));
            return null;
        }
        return c;
    }

    public void broadcast(Clan c, String key, Object... kv) {
        for (UUID u : c.members.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) lang.send(p, key, kv);
        }
    }

    private void broadcastExcept(Clan c, UUID except, String key, Object... kv) {
        for (UUID u : c.members.keySet()) {
            if (u.equals(except)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null) lang.send(p, key, kv);
        }
    }

    private String sanitize(String s) {
        return FORMAT_CODES.matcher(s).replaceAll("").trim();
    }

    // ------------------------------------------------------------------ create / disband

    /** tag may be null: it is then derived from the name. */
    public boolean create(Player p, String name, String tag) {
        if (manager.byPlayer(p.getUniqueId()) != null) {
            lang.send(p, "already-in-clan");
            return false;
        }
        if (!validName(p, name, null)) return false;
        if (tag == null || tag.isEmpty()) {
            int maxTag = cfg().getInt("clan.tag.max-length", 5);
            tag = name.substring(0, Math.min(name.length(), maxTag)).toUpperCase(Locale.ROOT);
        }
        if (!validTag(p, tag, null)) return false;
        Clan c = manager.create(p.getUniqueId(), p.getName(), name, tag);
        lang.send(p, "create-success", ph(c));
        return true;
    }

    private boolean validName(Player p, String name, Clan except) {
        int min = cfg().getInt("clan.name.min-length", 3);
        int max = cfg().getInt("clan.name.max-length", 16);
        if (name.length() < min || name.length() > max) {
            lang.send(p, "name-length");
            return false;
        }
        if (!name.matches(cfg().getString("clan.name.regex", "^[A-Za-z0-9_]+$"))) {
            lang.send(p, "name-invalid");
            return false;
        }
        for (String blocked : cfg().getStringList("clan.name.blocked")) {
            if (name.toLowerCase(Locale.ROOT).contains(blocked.toLowerCase(Locale.ROOT))
                    && !p.hasPermission("vortyclan.admin")) {
                lang.send(p, "name-blocked");
                return false;
            }
        }
        if (manager.nameTaken(name, except)) {
            lang.send(p, "name-taken");
            return false;
        }
        return true;
    }

    private boolean validTag(Player p, String tag, Clan except) {
        int min = cfg().getInt("clan.tag.min-length", 2);
        int max = cfg().getInt("clan.tag.max-length", 5);
        if (tag.length() < min || tag.length() > max) {
            lang.send(p, "tag-length");
            return false;
        }
        if (!tag.matches(cfg().getString("clan.tag.regex", "^[A-Za-z0-9]+$"))) {
            lang.send(p, "tag-invalid");
            return false;
        }
        if (manager.tagTaken(tag, except)) {
            lang.send(p, "tag-taken");
            return false;
        }
        return true;
    }

    public boolean disband(Player p) {
        Clan c = requireRole(p, Role.OWNER);
        if (c == null) return false;
        doDisband(c, p.getName());
        return true;
    }

    public boolean adminDisband(CommandSender sender, String clanName) {
        Clan c = manager.find(clanName);
        if (c == null) {
            lang.send(sender, "clan-not-found", "name", clanName);
            return false;
        }
        doDisband(c, sender.getName());
        lang.send(sender, "admin-disbanded", "clan", c.name);
        return true;
    }

    private void doDisband(Clan c, String by) {
        broadcast(c, "disbanded", "clan", c.name, "player", by);
        for (UUID u : c.members.keySet()) chatToggled.remove(u);
        manager.disband(c);
    }

    // ------------------------------------------------------------------ invites & joining

    public boolean invite(Player actor, String targetName) {
        Clan c = requireRole(actor, Role.ADMIN);
        if (c == null) return false;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            lang.send(actor, "player-offline", "player", targetName);
            return false;
        }
        if (target.getUniqueId().equals(actor.getUniqueId())) {
            lang.send(actor, "invite-self");
            return false;
        }
        if (manager.byPlayer(target.getUniqueId()) != null) {
            lang.send(actor, "target-in-clan");
            return false;
        }
        if (c.banned.containsKey(target.getUniqueId())) {
            lang.send(actor, "target-banned");
            return false;
        }
        if (c.members.size() >= maxMembers()) {
            lang.send(actor, "clan-full");
            return false;
        }
        if (manager.invite(target.getUniqueId(), c.id) != null) {
            lang.send(actor, "invite-already");
            return false;
        }
        long expires = System.currentTimeMillis() + cfg().getInt("clan.invite-expire-seconds", 60) * 1000L;
        manager.addInvite(target.getUniqueId(), c, actor.getName(), expires);
        lang.send(actor, "invite-sent", "player", target.getName());
        lang.send(target, "invite-received", "clan", c.name, "player", actor.getName());
        return true;
    }

    /** Accepts an invite; clanName may be null when the player has exactly one invite. */
    public boolean accept(Player p, String clanName) {
        if (manager.byPlayer(p.getUniqueId()) != null) {
            lang.send(p, "already-in-clan");
            return false;
        }
        List<ClanManager.Invite> invs = manager.invites(p.getUniqueId());
        if (invs.isEmpty()) {
            lang.send(p, "invite-none");
            return false;
        }
        Clan target = null;
        if (clanName == null) {
            if (invs.size() > 1) {
                List<String> names = new ArrayList<>();
                for (ClanManager.Invite i : invs) {
                    Clan c = manager.byId(i.clanId());
                    if (c != null) names.add(c.name);
                }
                lang.send(p, "invite-choose", "clans", String.join(", ", names));
                return false;
            }
            target = manager.byId(invs.get(0).clanId());
        } else {
            Clan c = manager.find(clanName);
            if (c != null && manager.invite(p.getUniqueId(), c.id) != null) target = c;
            if (target == null) {
                lang.send(p, "no-invite", "clan", clanName);
                return false;
            }
        }
        if (target == null) {
            lang.send(p, "invite-none");
            return false;
        }
        return acceptClan(p, target);
    }

    public boolean acceptClan(Player p, Clan c) {
        if (manager.invite(p.getUniqueId(), c.id) == null) {
            lang.send(p, "no-invite", "clan", c.name);
            return false;
        }
        return joinClan(p, c);
    }

    public boolean deny(Player p, String clanName) {
        List<ClanManager.Invite> invs = manager.invites(p.getUniqueId());
        if (invs.isEmpty()) {
            lang.send(p, "invite-none");
            return false;
        }
        Clan target;
        if (clanName == null) {
            if (invs.size() > 1) {
                List<String> names = new ArrayList<>();
                for (ClanManager.Invite i : invs) {
                    Clan c = manager.byId(i.clanId());
                    if (c != null) names.add(c.name);
                }
                lang.send(p, "invite-choose", "clans", String.join(", ", names));
                return false;
            }
            target = manager.byId(invs.get(0).clanId());
        } else {
            target = manager.find(clanName);
        }
        if (target == null || manager.invite(p.getUniqueId(), target.id) == null) {
            lang.send(p, "no-invite", "clan", clanName == null ? "?" : clanName);
            return false;
        }
        return denyClan(p, target);
    }

    public boolean denyClan(Player p, Clan c) {
        manager.removeInvite(p.getUniqueId(), c.id);
        lang.send(p, "invite-denied", "clan", c.name);
        return true;
    }

    public boolean join(Player p, String clanName) {
        Clan c = manager.find(clanName);
        if (c == null) {
            lang.send(p, "clan-not-found", "name", clanName);
            return false;
        }
        return join(p, c);
    }

    public boolean join(Player p, Clan c) {
        if (manager.byPlayer(p.getUniqueId()) != null) {
            lang.send(p, "already-in-clan");
            return false;
        }
        if (c.banned.containsKey(p.getUniqueId())) {
            lang.send(p, "banned-from-clan");
            return false;
        }
        if (!c.open && manager.invite(p.getUniqueId(), c.id) == null) {
            lang.send(p, "join-closed");
            return false;
        }
        return joinClan(p, c);
    }

    private boolean joinClan(Player p, Clan c) {
        if (c.banned.containsKey(p.getUniqueId())) {
            lang.send(p, "banned-from-clan");
            return false;
        }
        synchronized (manager) {
            if (c.members.size() >= maxMembers()) {
                lang.send(p, "clan-full");
                return false;
            }
            manager.addMember(c, p.getUniqueId(), p.getName(), Role.MEMBER);
        }
        lang.send(p, "join-success", "clan", c.name);
        broadcastExcept(c, p.getUniqueId(), "join-broadcast", "player", p.getName());
        return true;
    }

    public boolean leave(Player p) {
        Clan c = requireClan(p);
        if (c == null) return false;
        Member m = c.member(p.getUniqueId());
        if (m != null && m.role == Role.OWNER) {
            lang.send(p, "owner-cannot-leave");
            return false;
        }
        manager.removeMember(c, p.getUniqueId());
        chatToggled.remove(p.getUniqueId());
        lang.send(p, "leave-success", "clan", c.name);
        broadcast(c, "leave-broadcast", "player", p.getName());
        return true;
    }

    // ------------------------------------------------------------------ member management

    public boolean kickByName(Player actor, String name) {
        Clan c = requireRole(actor, Role.ADMIN);
        if (c == null) return false;
        Member t = c.memberByName(name);
        if (t == null) {
            lang.send(actor, "not-member", "player", name);
            return false;
        }
        return kick(actor, t.id);
    }

    public boolean kick(Player actor, UUID target) {
        Clan c = requireRole(actor, Role.ADMIN);
        if (c == null) return false;
        Member t = c.member(target);
        Member a = c.member(actor.getUniqueId());
        if (t == null || a == null) {
            lang.send(actor, "not-member", "player", "?");
            return false;
        }
        if (t.id.equals(actor.getUniqueId())) {
            lang.send(actor, "cannot-target-self");
            return false;
        }
        if (!a.role.higherThan(t.role)) {
            lang.send(actor, "cannot-target");
            return false;
        }
        manager.removeMember(c, target);
        chatToggled.remove(target);
        Player tp = Bukkit.getPlayer(target);
        if (tp != null) lang.send(tp, "kicked", "clan", c.name, "player", actor.getName());
        broadcast(c, "kick-broadcast", "player", t.name, "by", actor.getName());
        return true;
    }

    public boolean promoteByName(Player actor, String name) {
        Clan c = requireRole(actor, Role.OWNER);
        if (c == null) return false;
        Member t = c.memberByName(name);
        if (t == null) {
            lang.send(actor, "not-member", "player", name);
            return false;
        }
        return promote(actor, t.id);
    }

    public boolean promote(Player actor, UUID target) {
        Clan c = requireRole(actor, Role.OWNER);
        if (c == null) return false;
        Member t = c.member(target);
        if (t == null) {
            lang.send(actor, "not-member", "player", "?");
            return false;
        }
        if (t.role != Role.MEMBER) {
            lang.send(actor, "promote-max");
            return false;
        }
        t.role = Role.ADMIN;
        manager.requestSave();
        broadcast(c, "role-changed", "player", t.name, "role", lang.roleText(Role.ADMIN));
        return true;
    }

    public boolean demoteByName(Player actor, String name) {
        Clan c = requireRole(actor, Role.OWNER);
        if (c == null) return false;
        Member t = c.memberByName(name);
        if (t == null) {
            lang.send(actor, "not-member", "player", name);
            return false;
        }
        return demote(actor, t.id);
    }

    public boolean demote(Player actor, UUID target) {
        Clan c = requireRole(actor, Role.OWNER);
        if (c == null) return false;
        Member t = c.member(target);
        if (t == null) {
            lang.send(actor, "not-member", "player", "?");
            return false;
        }
        if (t.role != Role.ADMIN) {
            lang.send(actor, "demote-min");
            return false;
        }
        t.role = Role.MEMBER;
        manager.requestSave();
        broadcast(c, "role-changed", "player", t.name, "role", lang.roleText(Role.MEMBER));
        return true;
    }

    public boolean transferByName(Player actor, String name) {
        Clan c = requireRole(actor, Role.OWNER);
        if (c == null) return false;
        Member t = c.memberByName(name);
        if (t == null) {
            lang.send(actor, "not-member", "player", name);
            return false;
        }
        return transfer(actor, t.id);
    }

    public boolean transfer(Player actor, UUID target) {
        Clan c = requireRole(actor, Role.OWNER);
        if (c == null) return false;
        Member t = c.member(target);
        Member a = c.member(actor.getUniqueId());
        if (t == null || a == null) {
            lang.send(actor, "not-member", "player", "?");
            return false;
        }
        if (t.id.equals(a.id)) {
            lang.send(actor, "cannot-target-self");
            return false;
        }
        t.role = Role.OWNER;
        a.role = Role.ADMIN;
        manager.requestSave();
        broadcast(c, "transfer-broadcast", "player", t.name);
        return true;
    }

    public boolean banByName(Player actor, String name) {
        Clan c = requireRole(actor, Role.ADMIN);
        if (c == null) return false;
        Member t = c.memberByName(name);
        if (t != null) return ban(actor, t.id, t.name);
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return ban(actor, online.getUniqueId(), online.getName());
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (off.hasPlayedBefore() && off.getName() != null) return ban(actor, off.getUniqueId(), off.getName());
        lang.send(actor, "player-unknown", "player", name);
        return false;
    }

    public boolean ban(Player actor, UUID target, String targetName) {
        Clan c = requireRole(actor, Role.ADMIN);
        if (c == null) return false;
        Member a = c.member(actor.getUniqueId());
        if (target.equals(actor.getUniqueId())) {
            lang.send(actor, "cannot-target-self");
            return false;
        }
        Member t = c.member(target);
        if (t != null) {
            if (a == null || !a.role.higherThan(t.role)) {
                lang.send(actor, "cannot-target");
                return false;
            }
            manager.removeMember(c, target);
            chatToggled.remove(target);
        }
        c.banned.put(target, targetName);
        manager.requestSave();
        Player tp = Bukkit.getPlayer(target);
        if (tp != null && t != null) lang.send(tp, "kicked", "clan", c.name, "player", actor.getName());
        lang.send(actor, "ban-success", "player", targetName);
        return true;
    }

    public boolean unbanByName(Player actor, String name) {
        Clan c = requireRole(actor, Role.ADMIN);
        if (c == null) return false;
        UUID found = null;
        String foundName = name;
        for (var e : c.banned.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) {
                found = e.getKey();
                foundName = e.getValue();
                break;
            }
        }
        if (found == null) {
            lang.send(actor, "not-banned");
            return false;
        }
        c.banned.remove(found);
        manager.requestSave();
        lang.send(actor, "unban-success", "player", foundName);
        return true;
    }

    // ------------------------------------------------------------------ home

    public boolean setHome(Player p) {
        Clan c = requireRole(p, Role.ADMIN);
        if (c == null) return false;
        c.home = ClanHome.of(p.getLocation());
        manager.requestSave();
        lang.send(p, "sethome-success");
        return true;
    }

    public boolean home(Player p) {
        Clan c = requireClan(p);
        if (c == null) return false;
        ClanHome h = c.home;
        if (h == null) {
            lang.send(p, "home-none");
            return false;
        }
        Location dest = h.toLocation();
        if (dest == null) {
            lang.send(p, "home-world-missing");
            return false;
        }
        int delay = cfg().getInt("home.delay-seconds", 3);
        if (delay <= 0 || p.hasPermission("vortyclan.bypass.delay")) {
            plugin.tasks().teleport(p, dest);
            lang.send(p, "home-teleported");
            return true;
        }
        lang.send(p, "home-warmup", "seconds", delay);
        countdown(p, p.getLocation(), delay, dest);
        return true;
    }

    private void countdown(Player p, Location start, int remaining, Location dest) {
        plugin.tasks().entityLater(p, () -> {
            if (!p.isOnline()) return;
            Location now = p.getLocation();
            if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.5D) {
                lang.send(p, "home-cancelled");
                return;
            }
            if (remaining <= 1) {
                plugin.tasks().teleport(p, dest);
                lang.send(p, "home-teleported");
            } else {
                countdown(p, start, remaining - 1, dest);
            }
        }, 20L);
    }

    // ------------------------------------------------------------------ settings

    public boolean setOpen(Player p, boolean open) {
        Clan c = requireRole(p, Role.ADMIN);
        if (c == null) return false;
        c.open = open;
        manager.requestSave();
        lang.send(p, "open-set", "state", lang.text(open ? "status.open" : "status.closed"));
        return true;
    }

    public boolean toggleOpen(Player p) {
        Clan c = requireRole(p, Role.ADMIN);
        return c != null && setOpen(p, !c.open);
    }

    public boolean toggleFriendlyFire(Player p) {
        Clan c = requireRole(p, Role.ADMIN);
        if (c == null) return false;
        c.friendlyFire = !c.friendlyFire;
        manager.requestSave();
        lang.send(p, "ff-set", "state", lang.text(c.friendlyFire ? "state.on" : "state.off"));
        return true;
    }

    public boolean rename(Player p, String name) {
        Clan c = requireRole(p, Role.OWNER);
        if (c == null) return false;
        if (!validName(p, name, c)) return false;
        c.name = name;
        manager.requestSave();
        lang.send(p, "rename-success", "clan", name);
        return true;
    }

    public boolean setTag(Player p, String tag) {
        Clan c = requireRole(p, Role.OWNER);
        if (c == null) return false;
        if (!validTag(p, tag, c)) return false;
        c.tag = tag;
        manager.requestSave();
        lang.send(p, "tag-success", "tag", tag);
        return true;
    }

    public boolean setDescription(Player p, String desc) {
        Clan c = requireRole(p, Role.ADMIN);
        if (c == null) return false;
        if (desc.equals("-")) desc = "";
        desc = sanitize(desc);
        if (desc.length() > cfg().getInt("clan.description-max-length", 64)) {
            lang.send(p, "desc-too-long");
            return false;
        }
        c.description = desc;
        manager.requestSave();
        lang.send(p, "desc-success");
        return true;
    }

    /** Accepts a color code (0-9, a-f), optionally prefixed with & or a color name. */
    public static Character parseColor(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("&")) s = s.substring(1);
        if (s.length() == 1 && "0123456789abcdef".indexOf(s.charAt(0)) >= 0) return s.charAt(0);
        try {
            ChatColor cc = ChatColor.valueOf(s.toUpperCase(Locale.ROOT).replace(' ', '_'));
            if (cc.isColor()) return cc.getChar();
        } catch (IllegalArgumentException ignored) {
            // not a color name
        }
        return null;
    }

    public boolean setColor(Player p, char color) {
        Clan c = requireRole(p, Role.OWNER);
        if (c == null) return false;
        c.color = color;
        manager.requestSave();
        lang.send(p, "color-success");
        return true;
    }

    // ------------------------------------------------------------------ chat

    public boolean isChatToggled(UUID id) {
        return chatToggled.contains(id);
    }

    public boolean toggleChat(Player p) {
        if (requireClan(p) == null) return false;
        if (chatToggled.remove(p.getUniqueId())) {
            lang.send(p, "chat-off");
        } else {
            chatToggled.add(p.getUniqueId());
            lang.send(p, "chat-on");
        }
        return true;
    }

    public void clearChat(UUID id) {
        chatToggled.remove(id);
    }

    public boolean sendClanChat(Player p, String message) {
        Clan c = requireClan(p);
        if (c == null) return false;
        Member m = c.member(p.getUniqueId());
        Role role = m == null ? Role.MEMBER : m.role;
        String symbol = cfg().getString("chat.symbols." + role.key(), "");
        String line = lang.color(lang.format(cfg().getString("chat.clan-format", "{player}: {message}"),
                "color", "&" + c.color, "symbol", symbol, "player", p.getName(),
                "clan", c.name, "tag", c.tag, "message", message));
        for (UUID u : c.members.keySet()) {
            Player t = Bukkit.getPlayer(u);
            if (t != null) t.sendMessage(line);
        }
        if (cfg().getBoolean("chat.log-to-console", true)) {
            plugin.getLogger().info("[ClanChat:" + c.name + "] " + p.getName() + ": " + message);
        }
        return true;
    }

    // ------------------------------------------------------------------ info & list

    public void info(CommandSender to, Clan c) {
        lang.sendList(to, "info", ph(c));
    }

    public void list(CommandSender to, int page) {
        List<Clan> ranking = manager.ranking();
        if (ranking.isEmpty()) {
            lang.send(to, "list-empty");
            return;
        }
        int pages = Math.max(1, (ranking.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(Math.max(1, page), pages);
        lang.sendRaw(to, "list-header", "page", page, "pages", pages);
        int from = (page - 1) * PAGE_SIZE;
        for (int i = from; i < Math.min(ranking.size(), from + PAGE_SIZE); i++) {
            Clan c = ranking.get(i);
            lang.sendRaw(to, "list-entry", ph(c));
        }
    }

    // ------------------------------------------------------------------ score

    public void onKill(Player killer, Player victim) {
        Clan kc = manager.byPlayer(killer.getUniqueId());
        Clan vc = manager.byPlayer(victim.getUniqueId());
        if (kc == null || vc == null || kc == vc) return;
        int gain = cfg().getInt("score.kill", 1);
        int loss = cfg().getInt("score.death", 0);
        if (gain != 0) {
            kc.score += gain;
            lang.send(killer, "score-gain", "points", gain);
        }
        if (loss != 0) vc.score = Math.max(0, vc.score - loss);
        if (gain != 0 || loss != 0) manager.requestSave();
    }
}
