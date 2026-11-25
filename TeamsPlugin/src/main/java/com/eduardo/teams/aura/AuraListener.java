package com.eduardo.teams.aura;

import com.eduardo.teams.aura.AuraGui.AuraMenuHolder;
import com.eduardo.teams.aura.AuraManager.AuraColor;
import com.eduardo.teams.manager.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Gerencia os cliques nas interfaces de /aura e /aura permission,
 * alem de registrar automaticamente todo jogador que entra no servidor na lista do /aura permission
 * e disparar o raio visual ao abater um inimigo com a aura ativa.
 */
public class AuraListener implements Listener {

    private final AuraManager auraManager;
    private final AuraGui auraGui;
    private final TeamManager teamManager;

    public AuraListener(AuraManager auraManager, AuraGui auraGui, TeamManager teamManager) {
        this.auraManager = auraManager;
        this.auraGui = auraGui;
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        auraManager.recordPlayerSeen(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEnemyDeathWithAura(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player directKiller = victim.getKiller();
        UUID killerId = null;
        if (directKiller != null && !directKiller.equals(victim)) {
            killerId = directKiller.getUniqueId();
        } else if (teamManager != null) {
            UUID recentAttacker = teamManager.getRecentPvpAttacker(victim.getUniqueId());
            if (recentAttacker != null && !recentAttacker.equals(victim.getUniqueId())) {
                killerId = recentAttacker;
            }
        }

        if (killerId == null) {
            return;
        }
        if (teamManager != null && teamManager.isSameTeam(killerId, victim.getUniqueId())) {
            return;
        }

        Player killer = (directKiller != null && directKiller.getUniqueId().equals(killerId))
                ? directKiller
                : Bukkit.getPlayer(killerId);
        if (killer == null || !auraManager.hasActiveAura(killer)) {
            return;
        }

        Location deathLoc = victim.getLocation();
        World world = deathLoc.getWorld();
        if (world != null) {
            // strikeLightningEffect cria apenas o efeito visual e sonoro do raio (sem dano, sem fogo e sem queimar itens)
            world.strikeLightningEffect(deathLoc);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuraMenuHolder holder)) {
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
        String action = pdc.get(auraGui.getActionKey(), PersistentDataType.STRING);
        if (action == null || action.equals("none")) {
            return;
        }

        String target = pdc.get(auraGui.getTargetKey(), PersistentDataType.STRING);

        switch (action) {
            case "equip_aura" -> {
                AuraColor color = AuraColor.fromInput(target);
                if (color == null) {
                    return;
                }
                if (!auraManager.hasAuraColorPermission(player.getName(), color)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    player.sendMessage(Component.text("🔒 Você ainda não possui a ", NamedTextColor.RED)
                            .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                            .append(Component.text(" liberada! Peça a um ADM para liberar.", NamedTextColor.RED)));
                    return;
                }
                auraManager.assignAndActivateColor(player.getName(), color);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
                player.sendMessage(Component.text("✨ Você equipou a ", NamedTextColor.GREEN)
                        .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.GREEN)));
                auraGui.openPlayerAuraMenu(player);
            }

            case "toggle_own_aura" -> {
                if (!auraManager.hasAnyAura(player.getName())) {
                    return;
                }
                boolean enabled = auraManager.toggleAuraEnabled(player.getName());
                if (enabled) {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                    player.sendMessage(Component.text("✨ Sua aura foi ATIVADA!", NamedTextColor.GREEN));
                } else {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
                    player.sendMessage(Component.text("🔴 Sua aura foi DESATIVADA.", NamedTextColor.YELLOW));
                }
                auraGui.openPlayerAuraMenu(player);
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
                auraGui.openAdminPlayersMenu(player, page);
            }

            case "select_player" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                if (target == null || target.isBlank()) {
                    return;
                }
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.1f);
                auraGui.openPlayerAuraEditorMenu(player, target);
            }

            case "admin_click_color" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                String targetPlayerName = holder.getTargetPlayerName();
                AuraColor color = AuraColor.fromInput(target);
                if (targetPlayerName == null || color == null) {
                    return;
                }

                boolean currentlyUnlocked = auraManager.hasAuraColorPermission(targetPlayerName, color);
                AuraColor currentActive = auraManager.getActiveAuraColor(targetPlayerName);
                boolean isRightOrShift = event.isRightClick() || event.isShiftClick();

                if (currentlyUnlocked && (isRightOrShift || color == currentActive)) {
                    // Remove esta cor do jogador
                    auraManager.setAuraColorPermission(targetPlayerName, color, false);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
                    player.sendMessage(Component.text("🔒 ", NamedTextColor.RED)
                            .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                            .append(Component.text(" REMOVIDA de ", NamedTextColor.RED))
                            .append(Component.text(targetPlayerName, NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text(".", NamedTextColor.RED)));
                } else {
                    // Libera e define como a cor ativa do jogador
                    auraManager.assignAndActivateColor(targetPlayerName, color);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.25f);
                    player.sendMessage(Component.text("✨ ", NamedTextColor.GREEN)
                            .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                            .append(Component.text(" DEFINIDA e ATIVADA para ", NamedTextColor.GREEN))
                            .append(Component.text(targetPlayerName, NamedTextColor.WHITE, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.GREEN)));

                    Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
                    if (onlineTarget != null && onlineTarget.isOnline()) {
                        onlineTarget.sendMessage(Component.text("✨ O administrador definiu a ", NamedTextColor.GREEN)
                                .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                                .append(Component.text(" para você! Digite ", NamedTextColor.GREEN))
                                .append(Component.text("/aura", NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text(" quando quiser ativar ou desativar!", NamedTextColor.GREEN)));
                        onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
                    }
                }

                auraGui.openPlayerAuraEditorMenu(player, targetPlayerName);
            }

            case "unlock_all_auras" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                String targetPlayerName = holder.getTargetPlayerName();
                if (targetPlayerName == null) {
                    return;
                }
                auraManager.setAllAuraColorsPermission(targetPlayerName, true);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                player.sendMessage(Component.text("✨ Todas as 11 cores de Aura foram LIBERADAS para " + targetPlayerName + "!", NamedTextColor.GREEN));

                Player onlineTarget = Bukkit.getPlayerExact(targetPlayerName);
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    onlineTarget.sendMessage(Component.text("✨ Todas as 11 cores de Aura foram liberadas para você! Use /aura para escolher!", NamedTextColor.GREEN));
                    onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                }
                auraGui.openPlayerAuraEditorMenu(player, targetPlayerName);
            }

            case "lock_all_auras" -> {
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    return;
                }
                String targetPlayerName = holder.getTargetPlayerName();
                if (targetPlayerName == null) {
                    return;
                }
                auraManager.setAllAuraColorsPermission(targetPlayerName, false);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                player.sendMessage(Component.text("🔒 Todas as auras de " + targetPlayerName + " foram removidas.", NamedTextColor.RED));
                auraGui.openPlayerAuraEditorMenu(player, targetPlayerName);
            }
        }
    }
}
