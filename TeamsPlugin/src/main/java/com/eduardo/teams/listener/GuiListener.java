package com.eduardo.teams.listener;

import com.eduardo.teams.command.TeamCommand;
import com.eduardo.teams.gui.TeamGui;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gerencia os cliques nos menus interativos do comando /team.
 */
public class GuiListener implements Listener {

    private final TeamManager teamManager;
    private final TeamGui teamGui;
    private final TeamCommand teamCommand;

    public GuiListener(TeamManager teamManager, TeamGui teamGui, TeamCommand teamCommand) {
        this.teamManager = teamManager;
        this.teamGui = teamGui;
        this.teamCommand = teamCommand;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TeamGui.TeamMenuHolder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(teamGui.getActionKey(), PersistentDataType.STRING);
        if (action == null || action.equals("none")) {
            return;
        }

        String target = pdc.get(teamGui.getTargetKey(), PersistentDataType.STRING);

        switch (action) {
            case "back_main" -> teamGui.openMainMenu(player);

            case "prompt_create" -> {
                if (!teamManager.canCreateTeam(player)) {
                    player.closeInventory();
                    player.sendMessage(Component.text("Apenas líderes autorizados ou administradores podem criar times!", NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                teamManager.setChatPrompt(player.getUniqueId(), TeamManager.PromptType.CREATE_TEAM);
                player.sendMessage(Component.text("Digite no chat o ", NamedTextColor.GREEN)
                        .append(Component.text("nome do time (e opcionalmente a tag)", NamedTextColor.YELLOW))
                        .append(Component.text(" ou digite 'cancelar' para desistir.", NamedTextColor.GREEN)));
                player.sendMessage(Component.text("Exemplo: MeuTime TAG", NamedTextColor.GRAY));
            }

            case "prompt_tag" -> {
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null || !team.isLeader(player.getUniqueId())) {
                    player.sendMessage(Component.text("Apenas o lider pode alterar a tag do time!", NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                teamManager.setChatPrompt(player.getUniqueId(), TeamManager.PromptType.CHANGE_TAG);
                player.sendMessage(Component.text("Digite no chat a ", NamedTextColor.GREEN)
                        .append(Component.text("nova TAG (2 a 6 letras/numeros)", NamedTextColor.YELLOW))
                        .append(Component.text(" ou 'cancelar' para desistir.", NamedTextColor.GREEN)));
            }

            case "accept_invite" -> {
                player.closeInventory();
                if (target != null) {
                    teamCommand.handleAccept(player, new String[]{"accept", target});
                } else {
                    teamCommand.handleAccept(player, new String[]{"accept"});
                }
            }

            case "open_list" -> teamGui.openTeamListMenu(player);

            case "open_quests" -> teamGui.openQuestsMenu(player);

            case "open_members" -> {
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team != null) {
                    teamGui.openMembersMenu(player, team);
                }
            }

            case "view_team_members" -> {
                if (target != null) {
                    Team team = teamManager.getTeamByName(target);
                    if (team != null) {
                        teamGui.openMembersMenu(player, team);
                    }
                }
            }

            case "open_invite" -> {
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null || !team.isLeader(player.getUniqueId())) {
                    player.sendMessage(Component.text("Apenas o lider do time pode convidar jogadores!", NamedTextColor.RED));
                    return;
                }
                teamGui.openInviteMenu(player);
            }

            case "invite_player" -> {
                if (target != null) {
                    player.closeInventory();
                    teamCommand.handleInvite(player, new String[]{"invite", target});
                }
            }

            case "open_colors" -> {
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null || !team.isLeader(player.getUniqueId())) {
                    player.sendMessage(Component.text("Apenas o lider do time pode alterar a cor da tag!", NamedTextColor.RED));
                    return;
                }
                teamGui.openColorPickerMenu(player);
            }

            case "select_color" -> {
                if (target != null) {
                    teamCommand.handleColor(player, new String[]{"color", target});
                    teamGui.openMainMenu(player);
                }
            }

            case "member_manage" -> {
                if (target == null) {
                    return;
                }
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null || !team.isLeader(player.getUniqueId())) {
                    return;
                }
                if (event.isLeftClick()) {
                    teamCommand.handleLeader(player, new String[]{"leader", target});
                    teamGui.openMembersMenu(player, team);
                } else if (event.isRightClick()) {
                    teamCommand.handleKick(player, new String[]{"kick", target});
                    teamGui.openMembersMenu(player, team);
                }
            }

            case "leave_or_disband" -> {
                player.closeInventory();
                Team team = teamManager.getPlayerTeam(player.getUniqueId());
                if (team == null) {
                    return;
                }
                if (team.isLeader(player.getUniqueId())) {
                    teamCommand.handleDisband(player);
                } else {
                    teamCommand.handleLeave(player);
                }
            }

            case "join_voice_group" -> {
                player.closeInventory();
                if (teamManager.getVoicechatIntegration() != null) {
                    teamManager.getVoicechatIntegration().joinOwnTeamGroup(player);
                } else {
                    player.sendMessage(Component.text("O Simple Voice Chat nao esta instalado/ativo no servidor.", NamedTextColor.RED));
                }
            }
        }
    }
}
