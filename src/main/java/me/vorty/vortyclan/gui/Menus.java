package me.vorty.vortyclan.gui;

import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.manager.ClanManager;
import me.vorty.vortyclan.manager.ClanService;
import me.vorty.vortyclan.model.Clan;
import me.vorty.vortyclan.model.Member;
import me.vorty.vortyclan.model.Role;
import me.vorty.vortyclan.util.Items;
import me.vorty.vortyclan.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Builds and opens every VortyClan GUI. All text comes from the language file. */
public final class Menus {

    private static final int PAGE_SLOTS = 45;

    private final VortyClan plugin;
    private final Lang lang;
    private final ClanManager manager;
    private final ClanService service;

    public Menus(VortyClan plugin) {
        this.plugin = plugin;
        this.lang = plugin.lang();
        this.manager = plugin.clans();
        this.service = plugin.service();
    }

    // ------------------------------------------------------------------ helpers

    private ItemStack item(Material m, String key, Object... kv) {
        return Items.item(m, lang.get(key + "-name", kv), lang.list(key + "-lore", kv));
    }

    private ItemStack plain(Material m, String name, List<String> lore) {
        return Items.item(m, name, lore);
    }

    private void back(Menu menu, int slot, Runnable action) {
        menu.set(slot, plain(Material.ARROW, lang.get("gui.back"), null), e -> action.run());
    }

    private void pager(Menu menu, int page, int total, Runnable prev, Runnable next) {
        if (page > 0) menu.set(48, plain(Material.SPECTRAL_ARROW, lang.get("gui.prev"), null), e -> prev.run());
        if ((page + 1) * PAGE_SLOTS < total) {
            menu.set(50, plain(Material.SPECTRAL_ARROW, lang.get("gui.next"), null), e -> next.run());
        }
    }

    /** Closes the inventory and refuses to continue when the player's clan vanished meanwhile. */
    private Clan clanOf(Player p) {
        Clan c = manager.byPlayer(p.getUniqueId());
        if (c == null) {
            p.closeInventory();
            return null;
        }
        return c;
    }

    private Role roleOf(Clan c, Player p) {
        Member m = c.member(p.getUniqueId());
        return m == null ? Role.MEMBER : m.role;
    }

    // ------------------------------------------------------------------ main

    public void openMain(Player p) {
        Clan c = manager.byPlayer(p.getUniqueId());
        if (c == null) openNoClan(p);
        else openClanMain(p, c);
    }

    private void openNoClan(Player p) {
        Menu m = new Menu(lang.get("gui.main.title"), 3);

        m.set(11, item(Material.WRITABLE_BOOK, "gui.main.create"), e -> {
            p.closeInventory();
            plugin.prompts().ask(p, "prompt.create", input -> {
                String[] parts = input.split("\\s+");
                service.create(p, parts[0], parts.length > 1 ? parts[1] : null);
                openMain(p);
            });
        });

        int count = manager.invites(p.getUniqueId()).size();
        m.set(13, item(Material.PAPER, "gui.main.invites", "count", count), e -> openInvites(p));
        m.set(15, item(Material.GOLD_INGOT, "gui.main.top-browse"), e -> openTop(p, 0));
        m.fillEmpty().open(p);
    }

    private void openClanMain(Player p, Clan c) {
        Role role = roleOf(c, p);
        Object[] ph = service.ph(c);
        Menu m = new Menu(lang.get("gui.main.title"), 4);

        m.set(13, Items.item(Items.colorMaterial(c.color), lang.get("gui.main.info-name", ph),
                lang.list("gui.main.info-lore", ph)));

        m.set(19, item(Material.PLAYER_HEAD, "gui.main.members"), e -> openMembers(p, 0));
        m.set(20, item(Material.RED_BED, "gui.main.home"), e -> {
            p.closeInventory();
            service.home(p);
        });
        m.set(21, item(Material.GOLD_INGOT, "gui.main.top"), e -> openTop(p, 0));

        if (role.atLeast(Role.ADMIN)) {
            m.set(22, item(Material.EMERALD, "gui.main.invite"), e -> {
                p.closeInventory();
                plugin.prompts().ask(p, "prompt.invite", input -> {
                    service.invite(p, input.trim());
                    openMain(p);
                });
            });
            m.set(23, item(Material.COMPARATOR, "gui.main.settings"), e -> openSettings(p));
        }

        String state = lang.text(service.isChatToggled(p.getUniqueId()) ? "state.on" : "state.off");
        m.set(24, item(Material.OAK_SIGN, "gui.main.chat", "state", state), e -> {
            service.toggleChat(p);
            openMain(p);
        });

        if (role == Role.OWNER) {
            m.set(25, item(Material.TNT, "gui.main.disband"), e ->
                    confirm(p, lang.get("gui.main.disband-confirm"), () -> {
                        p.closeInventory();
                        service.disband(p);
                    }, () -> openMain(p)));
        } else {
            m.set(25, item(Material.BARRIER, "gui.main.leave"), e ->
                    confirm(p, lang.get("gui.main.leave-confirm"), () -> {
                        p.closeInventory();
                        service.leave(p);
                    }, () -> openMain(p)));
        }
        m.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ confirm

    private void confirm(Player p, String question, Runnable yes, Runnable no) {
        Menu m = new Menu(lang.get("gui.confirm.title"), 3);
        m.set(11, Items.item(Material.LIME_CONCRETE, lang.get("gui.confirm.yes"), lang.list("gui.confirm.yes-lore")),
                e -> yes.run());
        m.set(13, plain(Material.PAPER, question, null));
        m.set(15, Items.item(Material.RED_CONCRETE, lang.get("gui.confirm.no"), lang.list("gui.confirm.no-lore")),
                e -> no.run());
        m.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ members

    public void openMembers(Player p, int page) {
        Clan c = clanOf(p);
        if (c == null) return;
        Role myRole = roleOf(c, p);

        List<Member> list = new ArrayList<>(c.members.values());
        list.sort(Comparator.comparingInt((Member x) -> x.role.level()).reversed()
                .thenComparing(x -> x.name.toLowerCase()));

        Menu m = new Menu(lang.get("gui.members.title"), 6);
        int from = page * PAGE_SLOTS;
        for (int i = from; i < Math.min(list.size(), from + PAGE_SLOTS); i++) {
            Member mem = list.get(i);
            Player online = Bukkit.getPlayer(mem.id);
            String state = lang.text(online != null ? "gui.members.online" : "gui.members.offline");
            boolean manageable = myRole.atLeast(Role.ADMIN) && myRole.higherThan(mem.role);
            List<String> lore = lang.list("gui.members.entry-lore", "role", lang.roleText(mem.role), "state", state);
            if (manageable) lore.add(lang.get("gui.members.manage-hint"));
            ItemStack head = Items.head(Bukkit.getOfflinePlayer(mem.id),
                    lang.get("gui.members.entry-name", "player", mem.name), lore);
            m.set(i - from, head, manageable ? e -> openMember(p, mem.id) : null);
        }
        back(m, 45, () -> openMain(p));
        pager(m, page, list.size(), () -> openMembers(p, page - 1), () -> openMembers(p, page + 1));
        m.fillEmpty().open(p);
    }

    private void openMember(Player p, UUID targetId) {
        Clan c = clanOf(p);
        if (c == null) return;
        Member target = c.member(targetId);
        Role myRole = roleOf(c, p);
        if (target == null || !myRole.higherThan(target.role)) {
            openMembers(p, 0);
            return;
        }
        String name = target.name;
        Menu m = new Menu(lang.get("gui.member.title", "player", name), 3);

        if (myRole == Role.OWNER && target.role == Role.MEMBER) {
            m.set(10, item(Material.LIME_DYE, "gui.member.promote"), e -> {
                service.promote(p, targetId);
                openMembers(p, 0);
            });
        }
        if (myRole == Role.OWNER && target.role == Role.ADMIN) {
            m.set(11, item(Material.ORANGE_DYE, "gui.member.demote"), e -> {
                service.demote(p, targetId);
                openMembers(p, 0);
            });
        }
        m.set(13, item(Material.IRON_BOOTS, "gui.member.kick"), e ->
                confirm(p, lang.get("gui.member.kick-confirm", "player", name), () -> {
                    service.kick(p, targetId);
                    openMembers(p, 0);
                }, () -> openMember(p, targetId)));
        m.set(14, item(Material.NETHERITE_SWORD, "gui.member.ban"), e ->
                confirm(p, lang.get("gui.member.ban-confirm", "player", name), () -> {
                    service.ban(p, targetId, name);
                    openMembers(p, 0);
                }, () -> openMember(p, targetId)));
        if (myRole == Role.OWNER) {
            m.set(16, item(Material.GOLDEN_HELMET, "gui.member.transfer"), e ->
                    confirm(p, lang.get("gui.member.transfer-confirm", "player", name), () -> {
                        service.transfer(p, targetId);
                        openMain(p);
                    }, () -> openMember(p, targetId)));
        }
        back(m, 22, () -> openMembers(p, 0));
        m.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ settings

    private void openSettings(Player p) {
        Clan c = clanOf(p);
        if (c == null) return;
        if (!roleOf(c, p).atLeast(Role.ADMIN)) {
            openMain(p);
            return;
        }
        Menu m = new Menu(lang.get("gui.settings.title"), 3);

        m.set(10, item(c.open ? Material.LIME_DYE : Material.GRAY_DYE, "gui.settings.open",
                "state", lang.text(c.open ? "status.open" : "status.closed")), e -> {
            service.toggleOpen(p);
            openSettings(p);
        });
        m.set(11, item(Material.IRON_SWORD, "gui.settings.ff",
                "state", lang.text(c.friendlyFire ? "state.on" : "state.off")), e -> {
            service.toggleFriendlyFire(p);
            openSettings(p);
        });
        m.set(12, item(Material.RED_BED, "gui.settings.home"), e -> {
            service.setHome(p);
            openSettings(p);
        });
        m.set(13, item(Material.NAME_TAG, "gui.settings.desc"), e -> {
            p.closeInventory();
            plugin.prompts().ask(p, "prompt.desc", input -> {
                service.setDescription(p, input);
                openSettings(p);
            });
        });
        m.set(14, item(Material.ANVIL, "gui.settings.rename"), e -> {
            if (roleOf(c, p) != Role.OWNER) {
                lang.send(p, "need-role", "role", lang.roleText(Role.OWNER));
                return;
            }
            p.closeInventory();
            plugin.prompts().ask(p, "prompt.rename", input -> {
                service.rename(p, input.split("\\s+")[0]);
                openSettings(p);
            });
        });
        m.set(15, item(Material.OAK_SIGN, "gui.settings.tag"), e -> {
            if (roleOf(c, p) != Role.OWNER) {
                lang.send(p, "need-role", "role", lang.roleText(Role.OWNER));
                return;
            }
            p.closeInventory();
            plugin.prompts().ask(p, "prompt.tag", input -> {
                service.setTag(p, input.split("\\s+")[0]);
                openSettings(p);
            });
        });
        m.set(16, item(Items.colorMaterial(c.color), "gui.settings.color"), e -> {
            if (roleOf(c, p) != Role.OWNER) {
                lang.send(p, "need-role", "role", lang.roleText(Role.OWNER));
                return;
            }
            openColors(p);
        });
        back(m, 22, () -> openMain(p));
        m.fillEmpty().open(p);
    }

    private void openColors(Player p) {
        Menu m = new Menu(lang.get("gui.color.title"), 4);
        List<String> names = plugin.lang().list("gui.color.names");
        String codes = "0123456789abcdef";
        // The language file stores one list; fall back to the code letter when it is short.
        for (int i = 0; i < 16; i++) {
            char code = codes.charAt(i);
            String label = i < names.size() ? names.get(i) : String.valueOf(code);
            m.set(9 + i, Items.item(Items.colorMaterial(code), lang.color("&" + code + label),
                    List.of(lang.get("gui.color.pick"))), e -> {
                service.setColor(p, code);
                openSettings(p);
            });
        }
        back(m, 31, () -> openSettings(p));
        m.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ top clans

    public void openTop(Player p, int page) {
        List<Clan> ranking = manager.ranking();
        boolean hasClan = manager.byPlayer(p.getUniqueId()) != null;

        Menu m = new Menu(lang.get("gui.top.title"), 6);
        int from = page * PAGE_SLOTS;
        for (int i = from; i < Math.min(ranking.size(), from + PAGE_SLOTS); i++) {
            Clan c = ranking.get(i);
            List<Object> ph = new ArrayList<>(List.of(service.ph(c)));
            ph.add("rank");
            ph.add(i + 1);
            Object[] kv = ph.toArray();
            List<String> lore = lang.list("gui.top.entry-lore", kv);
            boolean joinable = !hasClan && c.open && !c.banned.containsKey(p.getUniqueId());
            if (joinable) lore.add(lang.get("gui.top.join-hint"));
            m.set(i - from, Items.item(Items.colorMaterial(c.color), lang.get("gui.top.entry-name", kv), lore),
                    joinable ? e -> {
                        p.closeInventory();
                        service.join(p, c);
                    } : null);
        }
        back(m, 45, () -> openMain(p));
        pager(m, page, ranking.size(), () -> openTop(p, page - 1), () -> openTop(p, page + 1));
        m.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ invites

    private void openInvites(Player p) {
        Menu m = new Menu(lang.get("gui.invites.title"), 4);
        int slot = 0;
        for (ClanManager.Invite inv : manager.invites(p.getUniqueId())) {
            Clan c = manager.byId(inv.clanId());
            if (c == null || slot >= 27) continue;
            Object[] ph = service.ph(c);
            m.set(slot++, Items.item(Items.colorMaterial(c.color), lang.get("gui.invites.entry-name", ph),
                    lang.list("gui.invites.entry-lore", ph)), e -> {
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                    service.denyClan(p, c);
                    openInvites(p);
                } else {
                    p.closeInventory();
                    service.acceptClan(p, c);
                }
            });
        }
        back(m, 31, () -> openMain(p));
        m.fillEmpty().open(p);
    }
}
