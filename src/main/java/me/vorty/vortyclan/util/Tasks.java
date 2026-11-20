package me.vorty.vortyclan.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Scheduler abstraction so the plugin runs on Spigot/Paper/Purpur (BukkitScheduler)
 * and on Folia (region schedulers) with the same code.
 */
public interface Tasks {

    /** Run on the global / main thread. */
    void global(Runnable r);

    /** Run off the main thread. */
    void async(Runnable r);

    /** Run on the thread that owns the entity. */
    void entity(Entity e, Runnable r);

    /** Run on the thread that owns the entity after a delay (ticks). */
    void entityLater(Entity e, Runnable r, long ticks);

    /** Teleport safely for the current platform. */
    void teleport(Player p, Location loc);

    void cancelAll();
}
