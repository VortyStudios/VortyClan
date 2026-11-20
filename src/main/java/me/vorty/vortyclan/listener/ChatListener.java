package me.vorty.vortyclan.listener;

import me.vorty.vortyclan.VortyClan;
import me.vorty.vortyclan.manager.PromptManager;
import me.vorty.vortyclan.model.Clan;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public final class ChatListener implements Listener {

    private final VortyClan plugin;

    public ChatListener(VortyClan plugin) {
        this.plugin = plugin;
    }

    /** GUI prompts ("type the clan name in chat") are answered before anything else sees the message. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPrompt(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        PromptManager.Prompt prompt = plugin.prompts().take(p.getUniqueId());
        if (prompt == null) return;
        e.setCancelled(true);
        String msg = e.getMessage().trim();
        plugin.tasks().entity(p, () -> {
            if (msg.equalsIgnoreCase("cancel") || msg.isEmpty()) {
                plugin.lang().send(p, "prompt.cancelled");
                return;
            }
            prompt.callback().accept(msg);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClanChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.service().isChatToggled(p.getUniqueId())) return;
        if (plugin.clans().byPlayer(p.getUniqueId()) == null) {
            plugin.service().clearChat(p.getUniqueId());
            return;
        }
        e.setCancelled(true);
        plugin.service().sendClanChat(p, e.getMessage());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTag(AsyncPlayerChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat.public-tag", true)) return;
        Clan c = plugin.clans().byPlayer(e.getPlayer().getUniqueId());
        if (c == null) return;
        String prefix = plugin.lang().color(plugin.lang().format(
                plugin.getConfig().getString("chat.public-tag-format", "&8[{color}{tag}&8] &r"),
                "color", "&" + c.color, "tag", c.tag, "clan", c.name));
        // '%' must be escaped because the chat format is a String.format pattern.
        e.setFormat(prefix.replace("%", "%%") + e.getFormat());
    }
}
