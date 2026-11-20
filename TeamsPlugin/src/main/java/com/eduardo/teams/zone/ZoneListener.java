package com.eduardo.teams.zone;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Aplica protecao contra quebra/colocacao de blocos (em /proteger e /safezone)
 * e bloqueio total de PvP (em /safezone).
 * Administradores (OP) no modo Criativo podem construir normalmente nas areas protegidas.
 */
public class ZoneListener implements Listener {

    private final ZoneManager zoneManager;

    public ZoneListener(ZoneManager zoneManager) {
        this.zoneManager = zoneManager;
    }

    private boolean canBypassBuild(Player player) {
        return player != null
                && (player.isOp() || player.hasPermission("teams.admin"))
                && player.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (canBypassBuild(event.getPlayer())) {
            return;
        }
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("🔒 Você não pode quebrar blocos nesta área protegida!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (canBypassBuild(event.getPlayer())) {
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                if (zoneManager.isBuildProtected(state.getLocation())) {
                    event.setCancelled(true);
                    event.getPlayer().sendActionBar(Component.text("🔒 Você não pode colocar blocos nesta área protegida!", NamedTextColor.RED));
                    return;
                }
            }
            return;
        }
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("🔒 Você não pode colocar blocos nesta área protegida!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (canBypassBuild(event.getPlayer())) {
            return;
        }
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())
                || zoneManager.isBuildProtected(event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("🔒 Você não pode usar baldes nesta área protegida!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (canBypassBuild(event.getPlayer())) {
            return;
        }
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())
                || zoneManager.isBuildProtected(event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text("🔒 Você não pode usar baldes nesta área protegida!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && canBypassBuild(player)) {
            return;
        }
        if (zoneManager.isBuildProtected(event.getEntity().getLocation())) {
            event.setCancelled(true);
            if (event.getRemover() instanceof Player player) {
                player.sendActionBar(Component.text("🔒 Você não pode quebrar decorações nesta área protegida!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (canBypassBuild(event.getPlayer())) {
            return;
        }
        if (zoneManager.isBuildProtected(event.getEntity().getLocation())
                || zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            if (event.getPlayer() != null) {
                event.getPlayer().sendActionBar(Component.text("🔒 Você não pode colocar decorações nesta área protegida!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> zoneManager.isBuildProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> zoneManager.isBuildProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (zoneManager.isBuildProtected(block.getLocation())
                    || zoneManager.isBuildProtected(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (Block block : event.getBlocks()) {
            if (zoneManager.isBuildProtected(block.getLocation())
                    || zoneManager.isBuildProtected(block.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof Player player && canBypassBuild(player)) {
            return;
        }
        if (zoneManager.isBuildProtected(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (zoneManager.isPvpBlocked(victim.getLocation()) || zoneManager.isPvpBlocked(attacker.getLocation())) {
            event.setCancelled(true);
            attacker.sendActionBar(Component.text("🛡 PvP desativado nesta área segura!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ProjectileSource source = event.getPotion().getShooter();
        if (!(source instanceof Player thrower)) {
            return;
        }
        boolean throwerInSafezone = zoneManager.isPvpBlocked(thrower.getLocation());
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && !victim.equals(thrower)) {
                if (throwerInSafezone || zoneManager.isPvpBlocked(victim.getLocation())) {
                    event.setIntensity(victim, 0.0);
                }
            }
        }
    }

    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player source) {
            return source;
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player source) {
            return source;
        }
        return null;
    }
}
