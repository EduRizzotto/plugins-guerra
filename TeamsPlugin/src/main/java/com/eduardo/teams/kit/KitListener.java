package com.eduardo.teams.kit;

import com.eduardo.teams.kit.KitGui.KitMenuHolder;
import com.eduardo.teams.kit.KitManager.KitType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gerencia os cliques nas interfaces de /kit e /kit permission,
 * alem de registrar automaticamente todo jogador que entra no servidor na lista do /kit permission.
 */
public class KitListener implements Listener {

    private final KitManager kitManager;
    private final KitGui kitGui;

    public KitListener(KitManager kitManager, KitGui kitGui) {
        this.kitManager = kitManager;
        this.kitGui = kitGui;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        kitManager.recordPlayerSeen(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof KitMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(kitGui.getActionKey(), PersistentDataType.STRING);
        if (action == null || action.equals("none")) {
            return;
        }

        String target = pdc.get(kitGui.getTargetKey(), PersistentDataType.STRING);

        switch (action) {
            case "claim_kit" -> {
                KitType kit = KitType.fromInput(target);
                if (kit == null) {
                    return;
                }
                player.closeInventory();
                kitManager.tryClaimKit(player, kit);
            }

            case "open_admin_players" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                int page = 0;
                if (target != null) {
                    try {
                        page = Integer.parseInt(target);
                    } catch (NumberFormatException ignored) {
                    }
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                kitGui.openPermissionPlayersMenu(player, page);
            }

            case "select_player" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                if (target == null || target.isBlank()) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
                kitGui.openPlayerPermissionEditorMenu(player, target);
            }

            case "toggle_player_kit" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                String targetPlayerName = holder.getTargetPlayerName();
                KitType kit = KitType.fromInput(target);
                if (targetPlayerName == null || kit == null) {
                    return;
                }

                boolean unlocked = kitManager.toggleKitPermission(targetPlayerName, kit);
                if (unlocked) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.25f);
                    player.sendMessage(Component.text("✔ ", NamedTextColor.GREEN)
                            .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                            .append(Component.text(" LIBERADO para ", NamedTextColor.GREEN))
                            .append(Component.text(targetPlayerName, NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.GREEN)));

                    Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
                    if (onlineTarget != null && onlineTarget.isOnline()) {
                        onlineTarget.sendMessage(Component.text("🎁 O administrador liberou o ", NamedTextColor.GREEN)
                                .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                                .append(Component.text(" para você! Use ", NamedTextColor.GREEN))
                                .append(Component.text("/kit " + kit.getId(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text(" para receber os itens!", NamedTextColor.GREEN)));
                        onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    }
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    player.sendMessage(Component.text("🔒 ", NamedTextColor.RED)
                            .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                            .append(Component.text(" BLOQUEADO para ", NamedTextColor.RED))
                            .append(Component.text(targetPlayerName, NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text(".", NamedTextColor.RED)));
                }

                // Atualiza a GUI imediatamente para o ADM poder clicar em mais kits
                kitGui.openPlayerPermissionEditorMenu(player, targetPlayerName);
            }

            case "unlock_all_kits" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                String targetPlayerName = holder.getTargetPlayerName();
                if (targetPlayerName == null) {
                    return;
                }
                kitManager.setAllKitsPermission(targetPlayerName, true);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.sendMessage(Component.text("✔ Todos os 6 kits foram LIBERADOS para " + targetPlayerName + "!", NamedTextColor.GREEN));

                Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    onlineTarget.sendMessage(Component.text("🎁 Todos os 6 Kits Oficiais foram liberados para você! Use /kit para resgatar!", NamedTextColor.GREEN));
                    onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
                kitGui.openPlayerPermissionEditorMenu(player, targetPlayerName);
            }

            case "lock_all_kits" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                String targetPlayerName = holder.getTargetPlayerName();
                if (targetPlayerName == null) {
                    return;
                }
                kitManager.setAllKitsPermission(targetPlayerName, false);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                player.sendMessage(Component.text("🔒 Todos os kits de " + targetPlayerName + " foram bloqueados.", NamedTextColor.RED));
                kitGui.openPlayerPermissionEditorMenu(player, targetPlayerName);
            }
        }
    }
}
