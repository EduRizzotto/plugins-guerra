package com.eduardo.teams.koth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Protege a estrutura temporaria da Torre de Dominio contra quebra/construcao/explosoes
 * e exibe as BossBars ativas para jogadores que entrarem durante o evento.
 */
public class KothListener implements Listener {

    private final KothManager kothManager;

    public KothListener(KothManager kothManager) {
        this.kothManager = kothManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (kothManager.isProtectedTowerBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(
                    Component.text("🏰 A Torre de Domínio é indestrutível durante o evento!", NamedTextColor.RED)
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (kothManager.isProtectedTowerBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(
                    Component.text("🏰 Você não pode colocar blocos na Torre de Domínio!", NamedTextColor.RED)
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> kothManager.isProtectedTowerBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> kothManager.isProtectedTowerBlock(block.getLocation()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        kothManager.showActiveBossBarsToPlayer(event.getPlayer());
    }
}
