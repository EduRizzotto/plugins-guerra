package com.eduardo.teams.listener;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.command.TeamCommand;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.util.XaeroWaypointUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Gerencia prompts de chat abertos via GUI, formatacao da tag no chat e atualizacao ao entrar.
 */
public class PlayerChatAndJoinListener implements Listener {

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final TeamCommand teamCommand;

    public PlayerChatAndJoinListener(TeamsPlugin plugin, TeamManager teamManager, TeamCommand teamCommand) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.teamCommand = teamCommand;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        teamManager.updatePlayerDisplay(player);
        XaeroWaypointUtil.sendFairPlayMode(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> XaeroWaypointUtil.sendFairPlayMode(player), 20L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        XaeroWaypointUtil.sendFairPlayMode(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> XaeroWaypointUtil.sendFairPlayMode(player), 20L);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> XaeroWaypointUtil.sendFairPlayMode(player), 20L);
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        // Remove todos os comandos duplicados com prefixo de plugin (ex: skinsrestorer:skin, teamsplugin:team, minecraft:...)
        event.getCommands().removeIf(cmd -> cmd.contains(":"));

        // Remove comandos/atalhos secundarios, deixando apenas os comandos principais (/team, /skin, etc.)
        event.getCommands().remove("teammsg");
        event.getCommands().remove("tm");
        event.getCommands().remove("skinsrestorer");
        event.getCommands().remove("sr");
        event.getCommands().remove("skull");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        teamManager.setChatPrompt(event.getPlayer().getUniqueId(), null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        TeamManager.PromptType prompt = teamManager.consumeChatPrompt(player.getUniqueId());

        if (prompt != null) {
            event.setCancelled(true);
            String plainInput = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

            if (plainInput.equalsIgnoreCase("cancelar") || plainInput.equalsIgnoreCase("cancel")) {
                player.sendMessage(Component.text("Operacao cancelada.", NamedTextColor.YELLOW));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (prompt == TeamManager.PromptType.CREATE_TEAM) {
                    String[] parts = plainInput.split("\\s+");
                    if (parts.length >= 2) {
                        teamCommand.handleCreate(player, new String[]{"create", parts[0], parts[1]});
                    } else {
                        teamCommand.handleCreate(player, new String[]{"create", parts[0]});
                    }
                } else if (prompt == TeamManager.PromptType.CHANGE_TAG) {
                    String[] parts = plainInput.split("\\s+");
                    teamCommand.handleTag(player, new String[]{"tag", parts[0]});
                }
            });
            return;
        }

        // Adiciona a [TAG] colorida antes do nome do jogador no chat
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team != null) {
            event.renderer((source, sourceDisplayName, message, viewer) ->
                    team.getFormattedTag()
                            .append(Component.text(" ", NamedTextColor.WHITE))
                            .append(sourceDisplayName.colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.text(": ", NamedTextColor.GRAY))
                            .append(message)
            );
        }
    }
}
