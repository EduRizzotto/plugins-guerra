package com.eduardo.teams.endstorm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener para o evento Tempestade do End (/tempestadeend).
 */
public class EndStormListener implements Listener {

    private final EndStormManager stormManager;

    public EndStormListener(EndStormManager stormManager) {
        this.stormManager = stormManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalDamageByEntity(EntityDamageByEntityEvent event) {
        if (!stormManager.isActive()) {
            return;
        }
        Entity victim = event.getEntity();
        if (!stormManager.isRiftCrystal(victim)) {
            return;
        }

        // Cancela a explosao vanilla do End Crystal e processa HP customizado
        event.setCancelled(true);
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            stormManager.handleCrystalHit(attacker, event.getDamage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalAnyDamage(EntityDamageEvent event) {
        if (stormManager.isActive() && stormManager.isRiftCrystal(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGuardianDeath(EntityDeathEvent event) {
        if (!stormManager.isActive()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (stormManager.isRiftGuardian(entity)) {
            stormManager.handleGuardianKilled(entity, entity.getKiller(), event.getDrops());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuardianPickUpBlock(EntityChangeBlockEvent event) {
        if (stormManager.isActive() && stormManager.isRiftGuardian(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGuardianTeleportFar(EntityTeleportEvent event) {
        if (!stormManager.isActive() || !stormManager.isRiftGuardian(event.getEntity())) {
            return;
        }
        Location center = stormManager.getBaseCenter();
        Location to = event.getTo();
        if (center == null || to == null || to.getWorld() == null || !to.getWorld().equals(center.getWorld())) {
            event.setCancelled(true);
            return;
        }
        // Mantem os Endermen/Shulkers dentro do raio de 50 blocos da Fenda de 80 blocos
        if (to.distanceSquared(center) > 2500.0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (stormManager.isProtectedRiftBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🌌 Você não pode quebrar blocos da Fenda da Tempestade do End!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (stormManager.isProtectedRiftBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🌌 Você não pode construir dentro da Fenda da Tempestade do End!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (stormManager.isProtectedRiftBlock(event.getBlock().getLocation())
                || stormManager.isProtectedRiftBlock(event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🌌 Você não pode despejar líquidos na Fenda da Tempestade do End!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!stormManager.isActive()) {
            return;
        }
        if (stormManager.isRiftCrystal(event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        event.blockList().removeIf(b -> stormManager.isProtectedRiftBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!stormManager.isActive()) {
            return;
        }
        event.blockList().removeIf(b -> stormManager.isProtectedRiftBlock(b.getLocation()));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        stormManager.handlePlayerJoin(event.getPlayer());
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
