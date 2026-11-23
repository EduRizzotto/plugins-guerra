package com.eduardo.teams.ctf;

import com.eduardo.teams.ctf.CtfManager.FlagStatus;
import com.eduardo.teams.ctf.CtfManager.TeamFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para todas as mecanicas do evento Capture The Flag (/capturetheflag).
 */
public class CtfListener implements Listener {

    private final CtfManager ctfManager;

    public CtfListener(CtfManager ctfManager) {
        this.ctfManager = ctfManager;
    }

    // =========================================================================
    // 1. ROUBAR BANDEIRA NA BASE INIMIGA & PROTECAO DO ESTANDARTE / SUPORTE
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        Block block = event.getBlock();
        TeamFlag flag = ctfManager.getFlagAtBlock(block.getLocation());
        if (flag != null) {
            event.setCancelled(true);
            if (flag.getStatus() == FlagStatus.AT_BASE) {
                ctfManager.tryStealFlagFromBase(event.getPlayer(), flag);
            }
            return;
        }

        if (ctfManager.isSupportBlockOfActiveFlag(block.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🚩 Você não pode quebrar o bloco de suporte de uma bandeira do Capture The Flag!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractFlagBlock(PlayerInteractEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }

        // Bloqueia Ender Pearl / Chorus Fruit para quem esta carregando bandeira
        Player player = event.getPlayer();
        ItemStack handItem = event.getItem();
        if (handItem != null && ctfManager.isCarryingFlag(player.getUniqueId())) {
            Material mat = handItem.getType();
            if (mat == Material.ENDER_PEARL || mat == Material.CHORUS_FRUIT) {
                event.setCancelled(true);
                player.sendMessage(Component.text("🚩 Você não pode usar Ender Pearl ou Chorus Fruit enquanto carrega uma bandeira! Corra até a sua base!", NamedTextColor.RED));
                return;
            }
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        TeamFlag flag = ctfManager.getFlagAtBlock(clicked.getLocation());
        if (flag != null && flag.getStatus() == FlagStatus.AT_BASE) {
            event.setCancelled(true);
            ctfManager.tryStealFlagFromBase(player, flag);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (ctfManager.isCtfFlagItem(event.getItemInHand())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🚩 Você não pode colocar a bandeira no chão! Entre no raio da sua base para capturá-la!", NamedTextColor.RED));
            return;
        }
        if (ctfManager.isActive() && ctfManager.isProtectedFlagOrSupportBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        event.blockList().removeIf(b -> ctfManager.isProtectedFlagOrSupportBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        event.blockList().removeIf(b -> ctfManager.isProtectedFlagOrSupportBlock(b.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (ctfManager.isActive() && ctfManager.isProtectedFlagOrSupportBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLiquidFlowBreakBanner(BlockFromToEvent event) {
        if (ctfManager.isActive() && ctfManager.isProtectedFlagOrSupportBlock(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        TeamFlag flag = ctfManager.getFlagAtBlock(event.getBlock().getLocation());
        if (flag != null && flag.getStatus() == FlagStatus.AT_BASE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        for (Block b : event.getBlocks()) {
            if (ctfManager.isProtectedFlagOrSupportBlock(b.getLocation())
                    || ctfManager.isProtectedFlagOrSupportBlock(b.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        for (Block b : event.getBlocks()) {
            if (ctfManager.isProtectedFlagOrSupportBlock(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // =========================================================================
    // 2. CORRER PARA A BASE: CAPTURA INSTANTANEA AO ENTRAR NO RAIO DA BASE
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCarrierMove(PlayerMoveEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (ctfManager.isCarryingFlag(player.getUniqueId())) {
            ctfManager.checkCarrierReachedBase(player, to);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCarrierTeleport(PlayerTeleportEvent event) {
        if (!ctfManager.isActive()) {
            return;
        }
        Player player = event.getPlayer();
        if (!ctfManager.isCarryingFlag(player.getUniqueId())) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || cause == PlayerTeleportEvent.TeleportCause.COMMAND
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🚩 Você não pode se teleportar enquanto carrega uma bandeira inimiga! Vá correndo até sua base!", NamedTextColor.RED));
        }
    }

    // =========================================================================
    // 3. PROTECAO DO ITEM DA BANDEIRA NO INVENTARIO, MORTE, QUIT E CHAO
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropFlag(PlayerDropItemEvent event) {
        if (ctfManager.isCtfFlagItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🚩 Você não pode largar a bandeira! Entre no raio da sua base para ganhar +100 pontos!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = (event.getHotbarButton() >= 0) ? player.getInventory().getItem(event.getHotbarButton()) : null;

        boolean involvesFlag = ctfManager.isCtfFlagItem(current)
                || ctfManager.isCtfFlagItem(cursor)
                || ctfManager.isCtfFlagItem(hotbar);
        if (!involvesFlag) {
            return;
        }

        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getType() != InventoryType.CRAFTING && topInv.getType() != InventoryType.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🚩 Você não pode guardar a bandeira em baús ou recipientes! Leve-a até a sua base!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (ctfManager.isCtfFlagItem(event.getOldCursor())) {
            Inventory topInv = event.getView().getTopInventory();
            if (topInv.getType() != InventoryType.CRAFTING && topInv.getType() != InventoryType.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (ctfManager.isCtfFlagItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntityWithFlag(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (ctfManager.isCtfFlagItem(main) || ctfManager.isCtfFlagItem(off)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupDroppedFlag(EntityPickupItemEvent event) {
        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();
        if (!ctfManager.isCtfFlagItem(stack)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        String flagTeamKey = ctfManager.getFlagTeamKeyFromItem(stack);
        ctfManager.handlePickupDroppedFlag(player, itemEntity, flagTeamKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCarrierDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        event.getDrops().removeIf(ctfManager::isCtfFlagItem);
        if (ctfManager.isCarryingFlag(dead.getUniqueId())) {
            ctfManager.handleCarrierLostFlag(dead, dead.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCarrierQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        if (ctfManager.isCarryingFlag(quitter.getUniqueId())) {
            ctfManager.handleCarrierLostFlag(quitter, quitter.getLocation());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        ctfManager.handlePlayerJoin(event.getPlayer());
    }
}
