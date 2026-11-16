package com.eduardo.teams.airdrop;

import com.eduardo.teams.airdrop.AirDropManager.ActiveAirDrop;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.quest.QuestActionType;
import com.eduardo.teams.quest.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Protege o bau do AirDrop enquanto trancado, anuncia quando alguem abre e remove o bau quando esvaziado.
 */
public class AirDropListener implements Listener {

    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final AirDropManager airDropManager;
    private final TeamManager teamManager;
    private QuestManager questManager;

    public AirDropListener(AirDropManager airDropManager, TeamManager teamManager) {
        this.airDropManager = airDropManager;
        this.teamManager = teamManager;
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAirDrop(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.CHEST) {
            return;
        }

        ActiveAirDrop drop = airDropManager.getAirDropAt(clicked.getLocation());
        if (drop == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!drop.isUnlocked()) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            notifyLocked(player, clicked.getLocation(), drop);
            return;
        }

        announceOpenIfNeeded(player, drop);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpenAirDropInventory(InventoryOpenEvent event) {
        Inventory inv = event.getInventory();
        Location loc = inv.getLocation();
        if (loc == null && inv.getHolder() instanceof Chest chest) {
            loc = chest.getLocation();
        } else if (loc == null && inv.getHolder() instanceof DoubleChest doubleChest) {
            loc = doubleChest.getLocation();
        }
        if (loc == null) {
            return;
        }

        ActiveAirDrop drop = airDropManager.getAirDropAt(loc);
        if (drop == null) {
            return;
        }

        if (!drop.isUnlocked()) {
            event.setCancelled(true);
            if (event.getPlayer() instanceof Player player) {
                notifyLocked(player, drop.getBlockLocation(), drop);
            }
            return;
        }

        if (event.getPlayer() instanceof Player player) {
            announceOpenIfNeeded(player, drop);
        }
    }

    private void notifyLocked(Player player, Location loc, ActiveAirDrop drop) {
        int secs = drop.getSecondsUntilUnlock();
        int mins = secs / 60;
        int remSecs = secs % 60;
        String formatted = String.format("%02d:%02d", mins, remSecs);

        player.playSound(loc, Sound.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 1.0f);
        player.sendActionBar(
                Component.text("🔒 Caixa de Suprimentos trancada! Destranca em " + formatted, NamedTextColor.RED)
        );
    }

    private void announceOpenIfNeeded(Player player, ActiveAirDrop drop) {
        if (!drop.isAnnouncedOpen()) {
            drop.setAnnouncedOpen(true);
            if (questManager != null) {
                questManager.recordAction(player, QuestActionType.SERVER_OPEN_AIRDROP, null, null, 1);
            }
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team != null) {
                teamManager.awardTeamPoints(team, TeamManager.POINTS_AIRDROP_FIRST, "Primeiro a abrir o AirDrop");
            }
            Component playerTag = team != null
                    ? Component.text("[" + team.getTag() + "] ", team.getColor(), TextDecoration.BOLD)
                            .append(Component.text(player.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                    : Component.text(player.getName(), NamedTextColor.YELLOW);

            Bukkit.broadcast(
                    Component.text("📦 [AirDrop] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(playerTag)
                            .append(Component.text(" abriu a Caixa de Suprimentos em ", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(AirDropManager.formatCoords(drop.getBlockLocation()), NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceNearAirDrop(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CHEST && placed.getType() != Material.TRAPPED_CHEST && placed.getType() != Material.HOPPER) {
            return;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            if (airDropManager.getAirDropAt(placed.getRelative(face).getLocation()) != null) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(
                        Component.text("🔒 Voce nao pode colocar baus ou funis colados na Caixa de Suprimentos!", NamedTextColor.RED)
                );
                return;
            }
        }
        if (airDropManager.getAirDropAt(placed.getRelative(BlockFace.UP).getLocation()) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("🔒 Voce nao pode colocar funis embaixo da Caixa de Suprimentos!", NamedTextColor.RED)
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakAirDrop(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) {
            return;
        }
        ActiveAirDrop drop = airDropManager.getAirDropAt(block.getLocation());
        if (drop == null) {
            return;
        }

        if (!drop.isUnlocked()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(
                    Component.text("🔒 Voce nao pode quebrar a Caixa de Suprimentos enquanto ela estiver trancada!", NamedTextColor.RED)
            );
            return;
        }

        // Se ja estava destrancado e alguem quebrou, remove o holograma (os itens dropam normalmente no chao)
        airDropManager.removeAirDrop(drop, false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> airDropManager.getAirDropAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> airDropManager.getAirDropAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        Location srcLoc = event.getSource().getLocation();
        if (srcLoc != null && airDropManager.getAirDropAt(srcLoc) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCloseAirDrop(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof Chest chest)) {
            return;
        }
        Location loc = chest.getLocation();
        ActiveAirDrop drop = airDropManager.getAirDropAt(loc);
        if (drop == null || !drop.isUnlocked()) {
            return;
        }

        boolean empty = true;
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                empty = false;
                break;
            }
        }

        if (empty) {
            if (loc.getWorld() != null) {
                loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, 0.05);
                loc.getWorld().playSound(loc, Sound.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 0.8f);
            }
            airDropManager.removeAirDrop(drop, true);
        }
    }
}
