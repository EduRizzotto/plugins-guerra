package com.eduardo.teams.doom;

import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Protege 100% dos blocos da Fortaleza DOOM contra quebra, colocacao, baldes, explosoes e perolas do fim,
 * alem de gerenciar a captura, roubo em PvP e fuga com o Coracao do Doom (+200 pontos).
 */
public class DoomListener implements Listener {

    private final DoomManager doomManager;
    private final TeamManager teamManager;

    public DoomListener(DoomManager doomManager, TeamManager teamManager) {
        this.doomManager = doomManager;
        this.teamManager = teamManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        if (!doomManager.isProtectedLocation(loc)) {
            return;
        }
        if (block.getType() == Material.FIRE) {
            return;
        }
        if (doomManager.isPlayerPlacedBlock(loc)) {
            doomManager.unregisterPlayerPlacedBlock(loc);
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("Você não pode destruir blocos da estrutura da Fortaleza DOOM!", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onExtinguishFireInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked.getType() == Material.FIRE && doomManager.isProtectedLocation(clicked.getLocation())) {
            clicked.setType(Material.AIR, false);
            clicked.getWorld().playSound(clicked.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.2f);
            return;
        }
        Block adjacent = clicked.getRelative(event.getBlockFace());
        if (adjacent.getType() == Material.FIRE && doomManager.isProtectedLocation(adjacent.getLocation())) {
            adjacent.setType(Material.AIR, false);
            adjacent.getWorld().playSound(adjacent.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event instanceof BlockMultiPlaceEvent multi) {
            boolean touchesProtected = false;
            for (BlockState state : multi.getReplacedBlockStates()) {
                Location loc = state.getLocation();
                if (doomManager.isProtectedLocation(loc)) {
                    touchesProtected = true;
                    if (!canOverwriteBlock(loc, state.getType())) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(Component.text("Você não pode substituir blocos da estrutura da Fortaleza DOOM!", NamedTextColor.RED));
                        return;
                    }
                }
            }
            if (touchesProtected) {
                for (BlockState state : multi.getReplacedBlockStates()) {
                    Location loc = state.getLocation();
                    if (doomManager.isProtectedLocation(loc)) {
                        doomManager.registerPlayerPlacedBlock(loc, state.getBlockData());
                    }
                }
            }
            return;
        }

        Location loc = event.getBlock().getLocation();
        if (!doomManager.isProtectedLocation(loc)) {
            return;
        }

        BlockState replacedState = event.getBlockReplacedState();
        if (!canOverwriteBlock(loc, replacedState.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Você não pode substituir blocos da estrutura da Fortaleza DOOM!", NamedTextColor.RED));
            return;
        }

        doomManager.registerPlayerPlacedBlock(loc, replacedState.getBlockData());
    }

    private boolean canOverwriteBlock(Location loc, Material replacedType) {
        if (replacedType.isAir() || replacedType == Material.FIRE || doomManager.isPlayerPlacedBlock(loc)) {
            return true;
        }
        // Na borda externa fora do interior da dungeon, permite cobrir grama/neve natural
        if (!doomManager.isInsideDungeonInterior(loc)) {
            return replacedType == Material.SHORT_GRASS
                    || replacedType == Material.TALL_GRASS
                    || replacedType == Material.FERN
                    || replacedType == Material.LARGE_FERN
                    || replacedType == Material.SNOW
                    || replacedType == Material.DEAD_BUSH;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())
                || doomManager.isProtectedLocation(event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Você não pode usar baldes dentro da Fortaleza DOOM!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())
                || doomManager.isProtectedLocation(event.getBlockClicked().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Você não pode usar baldes dentro da Fortaleza DOOM!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterProtectedExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterProtectedExplosionBlocks(event.blockList());
    }

    private void filterProtectedExplosionBlocks(java.util.List<Block> blocks) {
        blocks.removeIf(block -> {
            Location loc = block.getLocation();
            if (!doomManager.isProtectedLocation(loc)) {
                return false;
            }
            if (doomManager.isPlayerPlacedBlock(loc)) {
                doomManager.unregisterPlayerPlacedBlock(loc);
                return false;
            }
            return true;
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        Location loc = event.getBlock().getLocation();
        if (doomManager.isProtectedLocation(loc)) {
            if (doomManager.isPlayerPlacedBlock(loc)) {
                doomManager.unregisterPlayerPlacedBlock(loc);
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())
                || doomManager.isProtectedLocation(event.getSource().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        if (event.getBlock().getType() == Material.FIRE) {
            return;
        }
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())
                || doomManager.isProtectedLocation(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (Block b : event.getBlocks()) {
            if (doomManager.isProtectedLocation(b.getLocation())
                    || doomManager.isProtectedLocation(b.getRelative(event.getDirection()).getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        for (Block b : event.getBlocks()) {
            if (doomManager.isProtectedLocation(b.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderPearlUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) {
            return;
        }
        Player player = event.getPlayer();
        if (doomManager.isProtectedLocation(player.getLocation()) || doomManager.isCarrier(player)) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            player.sendMessage(Component.text("Você não pode usar Ender Pearl dentro da Fortaleza DOOM!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        if (pearl.getShooter() instanceof Player player) {
            if (doomManager.isProtectedLocation(player.getLocation())
                    || doomManager.isProtectedLocation(pearl.getLocation())
                    || doomManager.isCarrier(player)) {
                event.setCancelled(true);
                player.sendMessage(Component.text("Você não pode usar Ender Pearl dentro da Fortaleza DOOM!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnderPearlHitDungeon(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl)) {
            return;
        }
        boolean hitProtected = doomManager.isProtectedLocation(pearl.getLocation())
                || (event.getHitBlock() != null && doomManager.isProtectedLocation(event.getHitBlock().getLocation()));
        if (hitProtected) {
            event.setCancelled(true);
            pearl.remove();
            if (pearl.getShooter() instanceof Player player) {
                player.sendMessage(Component.text("Você não pode usar Ender Pearl dentro da Fortaleza DOOM!", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleportInsideDungeon(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            if (doomManager.isProtectedLocation(event.getFrom())
                    || (event.getTo() != null && doomManager.isProtectedLocation(event.getTo()))
                    || doomManager.isCarrier(event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "A magia sombria da Fortaleza DOOM bloqueia Pérolas do Fim e Frutas do Coro!",
                        NamedTextColor.RED));
            }
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

        boolean involvesArtifact = doomManager.isDoomArtifact(current)
                || doomManager.isDoomArtifact(cursor)
                || doomManager.isDoomArtifact(hotbar);
        if (!involvesArtifact) {
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        // 1. Se o jogador esta clicando no Coracao do Doom dentro do Bau Final para pega-lo:
        if (clickedInv != null && clickedInv.equals(topInv) && topInv.getType() == InventoryType.CHEST && doomManager.isDoomArtifact(current)) {
            event.setCancelled(true);
            if (doomManager.isArtifactClaimed()) {
                event.setCurrentItem(null);
                return;
            }

            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Component.text(
                        "Você precisa estar em uma equipe (/team) para pegar o Coração do Doom (+200 pts)!",
                        NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            if (doomManager.pickupArtifactFromChest(player, team)) {
                event.setCurrentItem(null);
                player.closeInventory();
            }
            return;
        }

        // 2. Impede que o portador guarde o Coracao do Doom em qualquer bau, ender chest, bigorna, etc.
        if (topInv.getType() != InventoryType.CRAFTING && topInv.getType() != InventoryType.CREATIVE) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Você não pode guardar o Coração do Doom em baús! Saia pela entrada da Fortaleza!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (doomManager.isDoomArtifact(event.getOldCursor())) {
            Inventory topInv = event.getView().getTopInventory();
            if (topInv.getType() != InventoryType.CRAFTING && topInv.getType() != InventoryType.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (doomManager.isDoomArtifact(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropArtifact(PlayerDropItemEvent event) {
        if (doomManager.isDoomArtifact(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "Você não pode largar o Coração do Doom! Saia vivo pela entrada da Fortaleza para vencer!",
                    NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArtifactPickup(EntityPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (!doomManager.isDoomArtifact(stack)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        doomManager.pickupDroppedArtifact(player, team, event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCarrierDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        boolean hadArtifact = doomManager.isCarrier(dead)
                || event.getDrops().stream().anyMatch(doomManager::isDoomArtifact);
        if (!hadArtifact) {
            return;
        }
        event.getDrops().removeIf(doomManager::isDoomArtifact);
        doomManager.handleCarrierLostArtifact(dead, dead.getKiller(), dead.getLocation());
    }

    @EventHandler
    public void onCarrierQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (doomManager.isCarrier(player)) {
            doomManager.handleCarrierLostArtifact(player, null, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCarrierMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!doomManager.isCarrier(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            doomManager.checkCarrierEscaped(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (doomManager.isActive()) {
            doomManager.updateEventCompasses();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() != null && doomManager.isProtectedLocation(event.getSpawner().getLocation())) {
            doomManager.registerDungeonMob(event.getEntity());
        }
    }
}
