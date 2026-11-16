package com.eduardo.teams.relic;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener do evento Portador da Reliquia: controla coleta, queda na morte/quit e protecoes anti-exploit.
 */
public class RelicListener implements Listener {

    private final TeamsPlugin plugin;
    private final RelicManager relicManager;

    public RelicListener(TeamsPlugin plugin, RelicManager relicManager) {
        this.plugin = plugin;
        this.relicManager = relicManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupRelic(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!relicManager.isRelicItem(stack)) {
            return;
        }

        // Impede que mobs (Zumbis,Raposas,Golfinhos,Allays) peguem a Reliquia
        if (!(event.getEntity() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> relicManager.handleCarrierPickup(player), 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (relicManager.isRelicItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelicItemDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item item && relicManager.isRelicItem(item.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRelicDespawn(ItemDespawnEvent event) {
        if (relicManager.isRelicItem(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCarrierDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        boolean removed = event.getDrops().removeIf(relicManager::isRelicItem);
        if (removed || relicManager.isCarrier(victim.getUniqueId())) {
            relicManager.handleCarrierLostRelic(victim, victim.getKiller(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCarrierQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (relicManager.isCarrier(player.getUniqueId())) {
            relicManager.handleCarrierLostRelic(player, null, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDropRelic(PlayerDropItemEvent event) {
        if (relicManager.isRelicItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(
                    Component.text("👑 Você não pode largar a Relíquia manualmente! Segure-a até o fim!", NamedTextColor.RED)
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareCraftWithRelic(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (relicManager.isRelicItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickWithRelic(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING) {
            return; // Inventario proprio do jogador (2x2)
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = (event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player p)
                ? p.getInventory().getItem(event.getHotbarButton())
                : null;

        if (relicManager.isRelicItem(current) || relicManager.isRelicItem(cursor) || relicManager.isRelicItem(hotbar)
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && relicManager.isRelicItem(current)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player p) {
                p.sendActionBar(Component.text("👑 A Relíquia da Guerra não pode ser guardada em baús ou blocos!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDragWithRelic(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && relicManager.isRelicItem(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntityWithRelic(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (relicManager.isRelicItem(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrinkMilk(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (event.getItem().getType() == Material.MILK_BUCKET && relicManager.isCarrier(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> relicManager.applyCarrierGlow(player), 2L);
        }
    }
}
