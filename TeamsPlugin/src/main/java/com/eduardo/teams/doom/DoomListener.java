package com.eduardo.teams.doom;

import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Protege 100% dos blocos da Fortaleza DOOM contra quebra, colocacao, baldes, explosoes e perolas do fim,
 * alem de gerenciar a conquista do Coracao do Doom (+200 pontos) no Bau Final.
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
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Você não pode destruir blocos dentro da Fortaleza DOOM!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Você não pode colocar blocos dentro da Fortaleza DOOM!", NamedTextColor.RED));
        }
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
        event.blockList().removeIf(block -> doomManager.isProtectedLocation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> doomManager.isProtectedLocation(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (doomManager.isProtectedLocation(event.getBlock().getLocation())) {
            event.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleportInsideDungeon(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            if (doomManager.isProtectedLocation(event.getFrom())
                    || (event.getTo() != null && doomManager.isProtectedLocation(event.getTo()))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text(
                        "A magia sombria da Fortaleza DOOM bloqueia Pérolas do Fim e Frutas do Coro!",
                        NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (!doomManager.isDoomArtifact(clicked)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (doomManager.isArtifactClaimed()) {
            event.setCurrentItem(null);
            return;
        }

        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text(
                    "Você precisa estar em uma equipe (/team) para reivindicar o Coração do Doom (+200 pts)!",
                    NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        event.setCurrentItem(null);
        player.closeInventory();
        doomManager.claimArtifact(player, team);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (doomManager.isDoomArtifact(event.getItem())) {
            event.setCancelled(true);
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
        event.getItem().remove();
        doomManager.claimArtifact(player, team);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArtifactRightClick(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (!doomManager.isDoomArtifact(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text(
                    "Você precisa estar em uma equipe (/team) para reivindicar o Coração do Doom (+200 pts)!",
                    NamedTextColor.RED));
            return;
        }
        item.setAmount(0);
        doomManager.claimArtifact(player, team);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (doomManager.isActive()) {
            doomManager.updateEventCompasses();
        }
    }
}
