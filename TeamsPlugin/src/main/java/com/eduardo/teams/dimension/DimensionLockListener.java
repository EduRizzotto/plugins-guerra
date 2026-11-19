package com.eduardo.teams.dimension;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Intercepta entrada em portais, criacao de portais do Nether, colocacao de Olhos do Fim
 * e teletransportes para o Nether ou The End enquanto estiverem bloqueados.
 */
public class DimensionLockListener implements Listener {

    private final DimensionLockManager dimensionManager;
    private final Map<UUID, Long> lastWarningTimestamp = new HashMap<>();

    public DimensionLockListener(DimensionLockManager dimensionManager) {
        this.dimensionManager = dimensionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        World targetWorld = (event.getTo() != null) ? event.getTo().getWorld() : null;

        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || (targetWorld != null && targetWorld.getEnvironment() == World.Environment.THE_END)) {
            if (!dimensionManager.isEndOpen()) {
                event.setCancelled(true);
                sendRateLimitedWarning(player, "🔒 O The End está bloqueado no momento! Aguarde a liberação dos administradores.");
            }
            return;
        }

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || (targetWorld != null && targetWorld.getEnvironment() == World.Environment.NETHER)) {
            if (!dimensionManager.isNetherOpen()) {
                event.setCancelled(true);
                sendRateLimitedWarning(player, "🔒 O Nether está bloqueado no momento! Aguarde a liberação dos administradores.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        World.Environment targetEnv = event.getTo().getWorld().getEnvironment();
        if (targetEnv == World.Environment.NETHER && !dimensionManager.isNetherOpen()) {
            event.setCancelled(true);
            sendRateLimitedWarning(event.getPlayer(), "🔒 O Nether está bloqueado no momento!");
        } else if (targetEnv == World.Environment.THE_END && !dimensionManager.isEndOpen()) {
            event.setCancelled(true);
            sendRateLimitedWarning(event.getPlayer(), "🔒 O The End está bloqueado no momento!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        World.Environment targetEnv = event.getTo().getWorld().getEnvironment();
        if (targetEnv == World.Environment.NETHER && !dimensionManager.isNetherOpen()) {
            event.setCancelled(true);
        } else if (targetEnv == World.Environment.THE_END && !dimensionManager.isEndOpen()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.END_PLATFORM) {
            if (!dimensionManager.isEndOpen()) {
                event.setCancelled(true);
                if (event.getEntity() instanceof Player player) {
                    sendRateLimitedWarning(player, "🔒 O The End está bloqueado no momento! Você ainda não pode entrar no Fim.");
                }
            }
            return;
        }
        if (!dimensionManager.isNetherOpen()) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                sendRateLimitedWarning(player, "🔒 O Nether está bloqueado no momento! Você ainda não pode abrir o portal.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceEnderEyeInFrame(PlayerInteractEvent event) {
        if (dimensionManager.isEndOpen()) {
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.END_PORTAL_FRAME
                && event.getItem() != null
                && event.getItem().getType() == Material.ENDER_EYE) {
            event.setCancelled(true);
            sendRateLimitedWarning(event.getPlayer(), "🔒 O The End está bloqueado no momento! Você ainda não pode ativar o Portal do Fim.");
        }
    }

    private void sendRateLimitedWarning(Player player, String message) {
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastWarningTimestamp.get(player.getUniqueId());
        if (last == null || (now - last) >= 2000L) {
            lastWarningTimestamp.put(player.getUniqueId(), now);
            player.sendMessage(Component.text(message, NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        }
    }
}
