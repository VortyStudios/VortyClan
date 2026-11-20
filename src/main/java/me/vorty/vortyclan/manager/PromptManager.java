package me.vorty.vortyclan.manager;

import me.vorty.vortyclan.VortyClan;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lets GUIs ask a player to type something in chat (clan name, player name, ...). */
public final class PromptManager {

    public record Prompt(Consumer<String> callback, long expires) {
    }

    private final VortyClan plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public PromptManager(VortyClan plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String promptKey, Consumer<String> callback) {
        long ms = plugin.getConfig().getInt("prompt-timeout-seconds", 30) * 1000L;
        prompts.put(p.getUniqueId(), new Prompt(callback, System.currentTimeMillis() + ms));
        plugin.lang().send(p, promptKey);
        plugin.lang().send(p, "prompt.cancel-hint");
    }

    /** Removes and returns an active (non-expired) prompt, or null. */
    public Prompt take(UUID id) {
        Prompt pr = prompts.remove(id);
        if (pr == null || pr.expires() < System.currentTimeMillis()) return null;
        return pr;
    }

    public void clear(UUID id) {
        prompts.remove(id);
    }
}
