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
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Impede que as Bussolas de Evento sejam dropadas no chao, perdidas ao morrer, usadas em craft
 * ou guardadas em baus/slots externos, permitindo move-las livremente dentro do proprio inventario sem duplicar.
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
        event.getDrops().removeIf(compassManager::isEventCompass);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (compassManager.isEventCompass(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = (event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player p)
                ? p.getInventory().getItem(event.getHotbarButton())
                : null;

        boolean involvesCompass = compassManager.isEventCompass(current)
                || compassManager.isEventCompass(cursor)
                || compassManager.isEventCompass(hotbar);

        if (!involvesCompass) {
            return;
        }

        // No proprio inventario (2x2), permite mover livremente, mas bloqueia colocar nos slots de craft 2x2
        if (top.getType() == InventoryType.CRAFTING) {
            if (event.getSlotType() == InventoryType.SlotType.CRAFTING || event.getSlotType() == InventoryType.SlotType.RESULT) {
                event.setCancelled(true);
            }
            return;
        }

        // Em qualquer outro inventario aberto (bau, funil, shulker, fornalha, etc.), bloqueia mover a bussola para fora
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!compassManager.isEventCompass(event.getOldCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.CRAFTING) {
            event.setCancelled(true);
            return;
        }
        // Bloqueia arrastar sobre os slots de craft 2x2 (rawSlots 0..4 na view de Player)
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
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
        for (EventCompassManager.CompassEventType type : EventCompassManager.CompassEventType.values()) {
            compassManager.removeCompassFromPlayer(event.getPlayer(), type);
        }
        if (syncCompassesCallback != null) {
            syncCompassesCallback.run();
        }
    }
}
