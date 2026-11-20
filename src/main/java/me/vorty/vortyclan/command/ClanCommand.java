package me.vorty.vortyclan.command;

import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.manager.ClanService;
import me.vorty.vortyclan.model.Clan;
import me.vorty.vortyclan.model.Member;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ClanCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "help", "menu", "create", "info", "list", "top", "invite", "accept", "deny", "join", "leave",
            "kick", "ban", "unban", "promote", "demote", "transfer", "home", "sethome", "open", "close",
            "pvp", "chat", "rename", "tag", "desc", "color", "disband", "admin", "reload");

    private static final List<String> COLORS = List.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");

    private final VortyClan plugin;
    private final ClanService service;

    public ClanCommand(VortyClan plugin) {
        this.plugin = plugin;
        this.service = plugin.service();
    }

    private void usage(CommandSender s, String usage) {
        plugin.lang().send(s, "usage", "usage", usage);
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) return p;
        plugin.lang().send(s, "player-only");
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vortyclan.use")) {
            plugin.lang().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player p) plugin.menus().openMain(p);
            else plugin.lang().sendList(sender, "help");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] a = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "help", "?" -> plugin.lang().sendList(sender, "help");
            case "menu", "gui" -> {
                Player p = player(sender);
                if (p != null) plugin.menus().openMain(p);
            }
            case "create" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (!p.hasPermission("vortyclan.create")) {
                    plugin.lang().send(p, "no-permission");
                    return true;
                }
                if (a.length < 1) usage(p, "/clan create <nama> [tag]");
                else service.create(p, a[0], a.length > 1 ? a[1] : null);
            }
            case "info" -> info(sender, a);
            case "list", "top" -> {
                int page = 1;
                if (a.length > 0) {
                    try {
                        page = Integer.parseInt(a[0]);
                    } catch (NumberFormatException ignored) {
                        // keep page 1
                    }
                }
                service.list(sender, page);
            }
            case "invite" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length < 1) usage(p, "/clan invite <pemain>");
                else service.invite(p, a[0]);
            }
            case "accept" -> {
                Player p = player(sender);
                if (p != null) service.accept(p, a.length > 0 ? a[0] : null);
            }
            case "deny" -> {
                Player p = player(sender);
                if (p != null) service.deny(p, a.length > 0 ? a[0] : null);
            }
            case "join" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length < 1) usage(p, "/clan join <clan>");
                else service.join(p, a[0]);
            }
            case "leave" -> {
                Player p = player(sender);
                if (p != null) service.leave(p);
            }
            case "kick" -> targeted(sender, a, "/clan kick <pemain>", service::kickByName);
            case "ban" -> targeted(sender, a, "/clan ban <pemain>", service::banByName);
            case "unban" -> targeted(sender, a, "/clan unban <pemain>", service::unbanByName);
            case "promote" -> targeted(sender, a, "/clan promote <pemain>", service::promoteByName);
            case "demote" -> targeted(sender, a, "/clan demote <pemain>", service::demoteByName);
            case "transfer" -> targeted(sender, a, "/clan transfer <pemain>", service::transferByName);
            case "home" -> {
                Player p = player(sender);
                if (p != null) service.home(p);
            }
            case "sethome" -> {
                Player p = player(sender);
                if (p != null) service.setHome(p);
            }
            case "open" -> {
                Player p = player(sender);
                if (p != null) service.setOpen(p, true);
            }
            case "close" -> {
                Player p = player(sender);
                if (p != null) service.setOpen(p, false);
            }
            case "pvp", "ff" -> {
                Player p = player(sender);
                if (p != null) service.toggleFriendlyFire(p);
            }
            case "chat", "c" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length == 0) service.toggleChat(p);
                else service.sendClanChat(p, String.join(" ", a));
            }
            case "rename" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length < 1) usage(p, "/clan rename <nama>");
                else service.rename(p, a[0]);
            }
            case "tag" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length < 1) usage(p, "/clan tag <tag>");
                else service.setTag(p, a[0]);
            }
            case "desc", "description" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length < 1) usage(p, "/clan desc <teks|->");
                else service.setDescription(p, String.join(" ", a));
            }
            case "color", "colour" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length < 1) {
                    usage(p, "/clan color <kode|nama>");
                    return true;
                }
                Character c = ClanService.parseColor(a[0]);
                if (c == null) plugin.lang().send(p, "color-invalid");
                else service.setColor(p, c);
            }
            case "disband" -> {
                Player p = player(sender);
                if (p == null) return true;
                if (a.length > 0 && a[0].equalsIgnoreCase("confirm")) service.disband(p);
                else if (plugin.clans().byPlayer(p.getUniqueId()) == null) plugin.lang().send(p, "not-in-clan");
                else plugin.lang().send(p, "disband-confirm");
            }
            case "reload" -> {
                if (!sender.hasPermission("vortyclan.admin")) {
                    plugin.lang().send(sender, "no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.lang().send(sender, "reload-success");
            }
            case "admin" -> {
                if (!sender.hasPermission("vortyclan.admin")) {
                    plugin.lang().send(sender, "no-permission");
                    return true;
                }
                if (a.length >= 2 && a[0].equalsIgnoreCase("disband")) service.adminDisband(sender, a[1]);
                else usage(sender, "/clan admin disband <clan>");
            }
            default -> plugin.lang().send(sender, "unknown-sub");
        }
        return true;
    }

    private interface PlayerAction {
        boolean run(Player actor, String target);
    }

    private void targeted(CommandSender sender, String[] a, String usage, PlayerAction action) {
        Player p = player(sender);
        if (p == null) return;
        if (a.length < 1) usage(p, usage);
        else action.run(p, a[0]);
    }

    private void info(CommandSender sender, String[] a) {
        Clan c;
        if (a.length == 0) {
            if (!(sender instanceof Player p)) {
                usage(sender, "/clan info <clan>");
                return;
            }
            c = plugin.clans().byPlayer(p.getUniqueId());
            if (c == null) {
                plugin.lang().send(sender, "not-in-clan");
                return;
            }
        } else {
            c = plugin.clans().find(a[0]);
            if (c == null) {
                Player target = Bukkit.getPlayerExact(a[0]);
                if (target != null) c = plugin.clans().byPlayer(target.getUniqueId());
            }
            if (c == null) {
                plugin.lang().send(sender, "clan-not-found", "name", a[0]);
                return;
            }
        }
        service.info(sender, c);
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!sender.hasPermission("vortyclan.use")) return List.of();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(SUBS);
            if (!sender.hasPermission("vortyclan.admin")) {
                subs.remove("admin");
                subs.remove("reload");
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            Clan own = sender instanceof Player p ? plugin.clans().byPlayer(p.getUniqueId()) : null;
            switch (sub) {
                case "invite" -> {
                    List<String> names = new ArrayList<>();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (plugin.clans().byPlayer(p.getUniqueId()) == null) names.add(p.getName());
                    }
                    return filter(names, args[1]);
                }
                case "kick", "promote", "demote", "transfer", "ban" -> {
                    List<String> names = new ArrayList<>();
                    if (own != null) {
                        for (Member m : own.members.values()) names.add(m.name);
                    }
                    return filter(names, args[1]);
                }
                case "unban" -> {
                    return own == null ? List.of() : filter(new ArrayList<>(own.banned.values()), args[1]);
                }
                case "info", "join", "accept", "deny" -> {
                    List<String> names = new ArrayList<>();
                    for (Clan c : plugin.clans().all()) names.add(c.name);
                    return filter(names, args[1]);
                }
                case "color", "colour" -> {
                    return filter(COLORS, args[1]);
                }
                case "disband" -> {
                    return filter(List.of("confirm"), args[1]);
                }
                case "admin" -> {
                    return sender.hasPermission("vortyclan.admin") ? filter(List.of("disband"), args[1]) : List.of();
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3 && sub.equals("admin") && args[1].equalsIgnoreCase("disband")
                && sender.hasPermission("vortyclan.admin")) {
            List<String> names = new ArrayList<>();
            for (Clan c : plugin.clans().all()) names.add(c.name);
            return filter(names, args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(t)) out.add(o);
        }
        return out;
    }
}
