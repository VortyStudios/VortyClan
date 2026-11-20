package me.vorty.vortyclan.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.model.Clan;
import me.vorty.vortyclan.model.Member;
import me.vorty.vortyclan.model.Role;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * PlaceholderAPI expansion, identifier "vortyclan".
 *
 * Player placeholders: name, tag, color, tag_colored, role, role_symbol, members, online, max_members,
 * score, rank, owner, description, open, friendlyfire, has_clan, invites
 * Global placeholders: total_clans, top_<n>_name|tag|score|members|owner
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final VortyClan plugin;

    public PapiExpansion(VortyClan plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "vortyclan";
    }

    @Override
    public String getAuthor() {
        return "Vorty";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String p = params.toLowerCase(Locale.ROOT);

        if (p.equals("total_clans")) return String.valueOf(plugin.clans().all().size());
        if (p.startsWith("top_")) return top(p);

        if (player == null) return "";
        String none = plugin.getConfig().getString("placeholders.no-clan", "");
        Clan c = plugin.clans().byPlayer(player.getUniqueId());

        if (p.equals("has_clan")) return String.valueOf(c != null);
        if (p.equals("invites")) return String.valueOf(plugin.clans().invites(player.getUniqueId()).size());
        if (c == null) {
            return switch (p) {
                case "members", "online", "score", "rank", "max_members" -> "0";
                case "open", "friendlyfire" -> "false";
                default -> none;
            };
        }

        return switch (p) {
            case "name" -> c.name;
            case "tag" -> c.tag;
            case "color" -> "§" + c.color;
            case "tag_colored" -> "§" + c.color + c.tag;
            case "role" -> role(player, c);
            case "role_symbol" -> plugin.lang().color(plugin.getConfig().getString(
                    "chat.symbols." + roleKey(player, c), ""));
            case "members" -> String.valueOf(c.members.size());
            case "online" -> String.valueOf(plugin.service().online(c));
            case "max_members" -> String.valueOf(plugin.service().maxMembers());
            case "score" -> String.valueOf(c.score);
            case "rank" -> String.valueOf(plugin.clans().rankingCached().indexOf(c) + 1);
            case "owner" -> {
                Member o = c.owner();
                yield o == null ? "" : o.name;
            }
            case "description" -> c.description;
            case "open" -> String.valueOf(c.open);
            case "friendlyfire" -> String.valueOf(c.friendlyFire);
            default -> null;
        };
    }

    private String roleKey(OfflinePlayer player, Clan c) {
        Member m = c.member(player.getUniqueId());
        return (m == null ? Role.MEMBER : m.role).key();
    }

    private String role(OfflinePlayer player, Clan c) {
        Member m = c.member(player.getUniqueId());
        return plugin.lang().strip(plugin.lang().roleText(m == null ? Role.MEMBER : m.role));
    }

    /** top_<n>_<field> */
    private String top(String p) {
        String[] parts = p.split("_");
        if (parts.length != 3) return null;
        int n;
        try {
            n = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        List<Clan> ranking = plugin.clans().rankingCached();
        boolean numeric = parts[2].equals("score") || parts[2].equals("members");
        if (n < 1 || n > ranking.size()) return numeric ? "0" : "";
        Clan c = ranking.get(n - 1);
        return switch (parts[2]) {
            case "name" -> c.name;
            case "tag" -> c.tag;
            case "score" -> String.valueOf(c.score);
            case "members" -> String.valueOf(c.members.size());
            case "owner" -> {
                Member o = c.owner();
                yield o == null ? "" : o.name;
            }
            default -> null;
        };
    }
}
