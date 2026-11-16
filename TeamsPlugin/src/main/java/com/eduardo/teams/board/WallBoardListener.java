package com.eduardo.teams.board;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WallBoardListener implements Listener {

    private final TeamsPlugin plugin;
    private final WallBoardManager boardManager;
    private final Map<UUID, Long> clickCooldowns = new HashMap<>();

    public WallBoardListener(TeamsPlugin plugin, WallBoardManager boardManager) {
        this.plugin = plugin;
        this.boardManager = boardManager;
    }

    private boolean checkDebounce(UUID playerUuid, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = clickCooldowns.get(playerUuid);
        if (last != null && (now - last) < cooldownMs) {
            return false;
        }
        clickCooldowns.put(playerUuid, now);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        ItemStack handItem = event.getItemStack();
        WallBoardManager.BoardType type = boardManager.getBoardTypeFromItem(handItem);
        if (type == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null || (!player.isOp() && !player.hasPermission("teams.admin"))) {
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage(Component.text("Apenas administradores podem colocar Molduras de Placar!", NamedTextColor.RED));
            }
            return;
        }

        if (!(event.getEntity() instanceof ItemFrame frame)) {
            return;
        }

        if (!WallBoardManager.isHorizontalWallFace(frame.getFacing())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Coloque a Moldura de Placar em uma parede vertical!", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!frame.isValid()) return;
            WallBoardManager.PlacedBoard placed = boardManager.registerPlacedBoard(frame, type);
            player.playSound(frame.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
            player.playSound(frame.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("✔ Placar de Parede instalado com sucesso! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text("(" + placed.getBoardType().getDisplayName() + ")", NamedTextColor.GOLD)));
            player.sendMessage(Component.text(" • Clique Direito: ", NamedTextColor.AQUA)
                    .append(Component.text("Alternar aba no mural", NamedTextColor.GRAY)));
            player.sendMessage(Component.text(" • Clique Esquerdo: ", NamedTextColor.AQUA)
                    .append(Component.text("Abrir menu interativo de estatísticas", NamedTextColor.GRAY)));
            player.sendMessage(Component.text(" • Shift + Clique Direito: ", NamedTextColor.GOLD)
                    .append(Component.text("Mudar tamanho/estilo do painel", NamedTextColor.GRAY)));
            player.sendMessage(Component.text(" • Shift + Clique Esquerdo: ", NamedTextColor.RED)
                    .append(Component.text("Remover placar da parede", NamedTextColor.GRAY)));
            player.sendMessage(Component.empty());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity rightClicked = event.getRightClicked();
        WallBoardManager.PlacedBoard board = boardManager.getBoardByEntity(rightClicked);
        if (board == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!checkDebounce(player.getUniqueId(), 180L)) {
            return;
        }

        if (player.isSneaking() && (player.isOp() || player.hasPermission("teams.admin"))) {
            WallBoardManager.DisplayScale nextScale = board.getDisplayScale().next();
            board.setDisplayScale(nextScale);
            boardManager.refreshSingleBoard(board);
            boardManager.saveBoards();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.4f);
            player.sendActionBar(Component.text("✦ Estilo do Placar: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text(nextScale.getLabel(), NamedTextColor.YELLOW)));
        } else {
            board.cycleManualTab();
            boardManager.refreshSingleBoard(board);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
            player.sendActionBar(Component.text("▸ Aba do Placar: ", NamedTextColor.AQUA, TextDecoration.BOLD)
                    .append(Component.text(board.getActiveTab().getDisplayName(), board.getActiveTab().getThemeColor())));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        WallBoardManager.PlacedBoard board = boardManager.getBoardByEntity(event.getEntity());
        if (board == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getRemover() instanceof Player player) {
            handlePlayerLeftClickBoard(player, board);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            return;
        }
        WallBoardManager.PlacedBoard board = boardManager.getBoardByEntity(event.getEntity());
        if (board != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        WallBoardManager.PlacedBoard board = boardManager.getBoardByEntity(event.getEntity());
        if (board == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            handlePlayerLeftClickBoard(player, board);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        WallBoardManager.PlacedBoard board = boardManager.getBoardByEntity(event.getEntity());
        if (board != null) {
            event.setCancelled(true);
        }
    }

    private void handlePlayerLeftClickBoard(Player player, WallBoardManager.PlacedBoard board) {
        if (!checkDebounce(player.getUniqueId(), 250L)) {
            return;
        }

        if (player.isSneaking()) {
            if (player.isOp() || player.hasPermission("teams.admin")) {
                WallBoardManager.BoardType type = board.getBoardType();
                boardManager.removeBoard(board, true);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    ItemStack frameItem = boardManager.createBoardFrameItem(type);
                    player.getInventory().addItem(frameItem);
                }
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
                player.sendMessage(Component.text("✖ Placar de Parede removido com sucesso!", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Apenas administradores podem remover este placar da parede!", NamedTextColor.RED));
            }
        } else {
            boardManager.openInspectorGui(player, board.getActiveTab());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WallBoardManager.BoardMenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 47) {
            boardManager.openInspectorGui(player, WallBoardManager.BoardType.CLANS);
        } else if (slot == 49) {
            boardManager.openInspectorGui(player, WallBoardManager.BoardType.PLAYERS);
        } else if (slot == 51) {
            boardManager.openInspectorGui(player, WallBoardManager.BoardType.HIGHLIGHTS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, boardManager::refreshAllBoards, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, boardManager::refreshAllBoards, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, boardManager::refreshAllBoards, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        String worldName = event.getWorld().getName();
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        for (WallBoardManager.PlacedBoard b : boardManager.getAllBoards()) {
            if (b.getWorldName().equalsIgnoreCase(worldName)
                    && (b.getBlockX() >> 4) == cx
                    && (b.getBlockZ() >> 4) == cz) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> boardManager.refreshSingleBoard(b), 5L);
            }
        }
    }
}
