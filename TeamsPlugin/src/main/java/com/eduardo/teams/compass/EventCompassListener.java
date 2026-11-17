package com.eduardo.teams.compass;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Impede que as Bussolas de Evento sejam dropadas no chao, perdidas ao morrer ou guardadas em baus,
 * e limpa bussolas antigas se um jogador entrar quando o evento ja acabou.
 */
public class EventCompassListener implements Listener {

    private final EventCompassManager compassManager;
    private final Runnable syncCompassesCallback;

    public EventCompassListener(EventCompassManager compassManager, Runnable syncCompassesCallback) {
        this.compassManager = compassManager;
        this.syncCompassesCallback = syncCompassesCallback;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropCompass(PlayerDropItemEvent event) {
        if (compassManager.isEventCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(
                    Component.text("🧭 Esta bússola pertence ao evento ativo e some sozinha quando ele acabar!", NamedTextColor.YELLOW)
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Nao deixa a bussola de evento dropar no chao ao morrer
        event.getDrops().removeIf(compassManager::isEventCompass);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = (event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player p)
                ? p.getInventory().getItem(event.getHotbarButton())
                : null;

        if (compassManager.isEventCompass(current) || compassManager.isEventCompass(cursor) || compassManager.isEventCompass(hotbar)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && compassManager.isEventCompass(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (compassManager.isEventCompass(player.getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Remove qualquer bussola residual de sessoes passadas; os eventos ativos entregam novamente no proximo segundo
        compassManager.removeCompassFromPlayer(event.getPlayer(), EventCompassManager.CompassEventType.AIRDROP);
        compassManager.removeCompassFromPlayer(event.getPlayer(), EventCompassManager.CompassEventType.RELIC);
        compassManager.removeCompassFromPlayer(event.getPlayer(), EventCompassManager.CompassEventType.HUNT);
        compassManager.removeCompassFromPlayer(event.getPlayer(), EventCompassManager.CompassEventType.KOTH);
        if (syncCompassesCallback != null) {
            syncCompassesCallback.run();
        }
    }
}
