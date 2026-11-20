package me.vorty.vortyclan.util;

import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.model.Role;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads the language file and formats colored messages with {placeholders}. */
public final class Lang {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern VAR = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private final VortyClan plugin;
    private FileConfiguration cfg = new YamlConfiguration();

    public Lang(VortyClan plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "lang");
        for (String bundled : new String[]{"id", "en"}) {
            File f = new File(folder, bundled + ".yml");
            if (!f.exists()) plugin.saveResource("lang/" + bundled + ".yml", false);
        }
        String code = plugin.getConfig().getString("language", "id").toLowerCase();
        File file = new File(folder, code + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file '" + code + "' not found, falling back to 'id'.");
            code = "id";
            file = new File(folder, "id.yml");
        }
        cfg = YamlConfiguration.loadConfiguration(file);

        // Missing keys fall back to the bundled copy so old lang files keep working after updates.
        InputStream def = plugin.getResource("lang/" + code + ".yml");
        if (def == null) def = plugin.getResource("lang/id.yml");
        if (def != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
    }

    // ---------------------------------------------------------------- formatting

    public String color(String s) {
        if (s == null) return "";
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char c : m.group(1).toCharArray()) r.append('§').append(c);
            m.appendReplacement(sb, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public String strip(String s) {
        return ChatColor.stripColor(color(s));
    }

    /** Replaces {key} placeholders in a single pass (values are never re-scanned). */
    public String format(String s, Object... kv) {
        if (s == null) return "";
        Map<String, String> vars = new HashMap<>();
        vars.put("max_members", String.valueOf(plugin.getConfig().getInt("clan.max-members", 20)));
        vars.put("min_name", String.valueOf(plugin.getConfig().getInt("clan.name.min-length", 3)));
        vars.put("max_name", String.valueOf(plugin.getConfig().getInt("clan.name.max-length", 16)));
        vars.put("min_tag", String.valueOf(plugin.getConfig().getInt("clan.tag.min-length", 2)));
        vars.put("max_tag", String.valueOf(plugin.getConfig().getInt("clan.tag.max-length", 5)));
        vars.put("max_desc", String.valueOf(plugin.getConfig().getInt("clan.description-max-length", 64)));
        vars.put("invite_expire", String.valueOf(plugin.getConfig().getInt("clan.invite-expire-seconds", 60)));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            vars.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
        Matcher m = VAR.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v != null ? v : m.group()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---------------------------------------------------------------- lookups

    /** Raw, uncolored text for a key (useful to embed in other messages). */
    public String text(String key) {
        return cfg.getString(key, "&cMissing: " + key);
    }

    /** Colored text without prefix. */
    public String get(String key, Object... kv) {
        return color(format(text(key), kv));
    }

    /** Colored list. */
    public List<String> list(String key, Object... kv) {
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList(key)) out.add(color(format(line, kv)));
        return out;
    }

    public String prefix() {
        return text("prefix");
    }

    public String roleText(Role r) {
        return text("role." + r.key());
    }

    // ---------------------------------------------------------------- sending

    public void send(CommandSender to, String key, Object... kv) {
        if (to == null) return;
        to.sendMessage(color(prefix() + format(text(key), kv)));
    }

    public void sendRaw(CommandSender to, String key, Object... kv) {
        if (to == null) return;
        to.sendMessage(get(key, kv));
    }

    public void sendList(CommandSender to, String key, Object... kv) {
        for (String line : list(key, kv)) to.sendMessage(line);
    }
}
