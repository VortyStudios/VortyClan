package me.vorty.vortyclan.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Scheduler for Folia. This class is only ever loaded when Folia is detected,
 * so Spigot servers never touch the region scheduler API.
 */
public final class FoliaTasks implements Tasks {

    private final Plugin plugin;

    public FoliaTasks(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void global(Runnable r) {
        if (!plugin.isEnabled()) return;
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> r.run());
    }

    @Override
    public void async(Runnable r) {
        if (!plugin.isEnabled()) return;
        Bukkit.getAsyncScheduler().runNow(plugin, t -> r.run());
    }

    @Override
    public void entity(Entity e, Runnable r) {
        if (!plugin.isEnabled()) return;
        e.getScheduler().run(plugin, t -> r.run(), null);
    }

    @Override
    public void entityLater(Entity e, Runnable r, long ticks) {
        if (!plugin.isEnabled()) return;
        e.getScheduler().runDelayed(plugin, t -> r.run(), null, Math.max(1L, ticks));
    }

    @Override
    public void teleport(Player p, Location loc) {
        p.teleportAsync(loc);
    }

    @Override
    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
