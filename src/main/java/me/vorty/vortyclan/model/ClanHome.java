package me.vorty.vortyclan.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record ClanHome(String world, double x, double y, double z, float yaw, float pitch) {

    public static ClanHome of(Location l) {
        return new ClanHome(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** Returns null when the world is not loaded. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
