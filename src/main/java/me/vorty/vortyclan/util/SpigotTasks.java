package me.vorty.vortyclan.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Scheduler for Spigot, Paper and Purpur. */
public final class SpigotTasks implements Tasks {

    private final Plugin plugin;

    public SpigotTasks(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void global(Runnable r) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, r);
    }

    @Override
    public void async(Runnable r) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    @Override
    public void entity(Entity e, Runnable r) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, r);
    }

    @Override
    public void entityLater(Entity e, Runnable r, long ticks) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(1L, ticks));
    }

    @Override
    public void teleport(Player p, Location loc) {
        p.teleport(loc);
    }

    @Override
    public void cancelAll() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
