package me.vorty.vortyclan.listener;

import me.vorty.vortyclan.VortyClan;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class PlayerListener implements Listener {

    private final VortyClan plugin;

    public PlayerListener(VortyClan plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Keep the stored name fresh so offline members show their current name.
        plugin.clans().updateName(e.getPlayer().getUniqueId(), e.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.prompts().clear(e.getPlayer().getUniqueId());
        plugin.service().clearChat(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        var a = plugin.clans().byPlayer(attacker.getUniqueId());
        var v = plugin.clans().byPlayer(victim.getUniqueId());
        if (a == null || a != v || a.friendlyFire) return;

        e.setCancelled(true);
        plugin.lang().send(attacker, "ff-blocked");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null) plugin.service().onKill(killer, victim);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }
}
