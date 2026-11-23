package com.eduardo.teams.netherstorm;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener para o evento Tempestade do Nether (/tempestadenether).
 * Jogadores podem colocar e quebrar blocos livremente na estrutura da Fenda em X.
 */
public class NetherStormListener implements Listener {

    private final NetherStormManager stormManager;

    public NetherStormListener(NetherStormManager stormManager) {
        this.stormManager = stormManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrystalDamage(EntityDamageEvent event) {
        if (!stormManager.isActive() || !stormManager.isRiftCrystal(event.getEntity())) {
            return;
        }
        // Cancela a explosao vanilla do End Crystal e processa HP customizado do Cristal Infernal
        event.setCancelled(true);
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = resolveAttacker(byEntity.getDamager());
            if (attacker != null) {
                stormManager.handleCrystalHit(attacker, event.getDamage());
            }
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreakRecordOriginal(BlockBreakEvent event) {
        if (stormManager.isActive()) {
            stormManager.recordOriginalBlockBeforePlayerEdit(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlaceRecordOriginal(BlockPlaceEvent event) {
        if (stormManager.isActive()) {
            stormManager.recordOriginalBlockBeforePlayerEdit(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (stormManager.isActive() && stormManager.isRiftCrystal(event.getEntity())) {
            event.setCancelled(true);
        }
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
