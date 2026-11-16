package com.eduardo.teams.voice;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.CreateGroupEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.JoinGroupEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integracao com o Simple Voice Chat.
 * Cria um grupo persistente ("Team Group: <Nome> [<TAG>]") visivel para todos na tecla V -> Group,
 * mas que permite a entrada apenas de membros daquele time.
 */
public class TeamVoicechatIntegration implements VoicechatPlugin {

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;

    private volatile VoicechatServerApi serverApi;
    // teamKey (lowercase) -> Group
    private final Map<String, Group> teamGroups = new ConcurrentHashMap<>();
    // group UUID -> teamKey (lowercase)
    private final Map<UUID, String> groupToTeamKey = new ConcurrentHashMap<>();

    public TeamVoicechatIntegration(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
    }

    @Override
    public String getPluginId() {
        return "teams_voicechat";
    }

    @Override
    public void initialize(VoicechatApi api) {
        plugin.getLogger().info("Simple Voice Chat API inicializada com o TeamsPlugin!");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onVoicechatServerStarted);
        registration.registerEvent(CreateGroupEvent.class, this::onCreateGroup);
        registration.registerEvent(JoinGroupEvent.class, this::onJoinGroup);
    }

    private void onVoicechatServerStarted(VoicechatServerStartedEvent event) {
        this.serverApi = event.getVoicechat();
        syncAllTeams();
        plugin.getLogger().info("Grupos de voz dos times sincronizados no Simple Voice Chat!");
    }

    /**
     * Sincroniza todos os times registrados criando seus respectivos grupos persistentes no Voice Chat.
     */
    public void syncAllTeams() {
        if (serverApi == null) {
            return;
        }
        for (Team team : teamManager.getAllTeams()) {
            ensureTeamGroup(team);
        }
    }

    /**
     * Cria (ou retorna) o grupo persistente do time no Simple Voice Chat.
     */
    public Group ensureTeamGroup(Team team) {
        if (serverApi == null || team == null) {
            return null;
        }
        String key = team.getName().toLowerCase(Locale.ROOT);
        Group existing = teamGroups.get(key);
        if (existing != null) {
            return existing;
        }

        Group group = serverApi.groupBuilder()
                .setName(formatGroupName(team))
                .setPersistent(true)
                .setHidden(false)
                .setType(Group.Type.OPEN)
                .build();

        teamGroups.put(key, group);
        groupToTeamKey.put(group.getId(), key);
        return group;
    }

    /**
     * Atualiza o grupo de voz do time (ex: quando a tag muda), mantendo membros conectados no novo grupo.
     */
    public void refreshTeamGroup(Team team) {
        if (serverApi == null || team == null) {
            return;
        }
        String key = team.getName().toLowerCase(Locale.ROOT);
        Group oldGroup = teamGroups.remove(key);

        List<VoicechatConnection> connectedMembers = new ArrayList<>();
        if (oldGroup != null) {
            groupToTeamKey.remove(oldGroup.getId());
            for (UUID memberId : team.getMembers()) {
                VoicechatConnection conn = serverApi.getConnectionOf(memberId);
                if (conn != null && conn.getGroup() != null && conn.getGroup().getId().equals(oldGroup.getId())) {
                    connectedMembers.add(conn);
                    conn.setGroup(null);
                }
            }
            serverApi.removeGroup(oldGroup.getId());
        }

        Group newGroup = ensureTeamGroup(team);
        if (newGroup != null) {
            for (VoicechatConnection conn : connectedMembers) {
                conn.setGroup(newGroup);
            }
        }
    }

    /**
     * Remove o grupo de voz quando o time e desfeito.
     */
    public void removeTeamGroup(Team team) {
        if (serverApi == null || team == null) {
            return;
        }
        String key = team.getName().toLowerCase(Locale.ROOT);
        Group group = teamGroups.remove(key);
        if (group == null) {
            return;
        }
        groupToTeamKey.remove(group.getId());

        for (UUID memberId : team.getMembers()) {
            VoicechatConnection conn = serverApi.getConnectionOf(memberId);
            if (conn != null && conn.getGroup() != null && conn.getGroup().getId().equals(group.getId())) {
                conn.setGroup(null);
            }
        }
        serverApi.removeGroup(group.getId());
    }

    /**
     * Desconecta um jogador do grupo de voz do time caso ele saia ou seja expulso.
     */
    public void disconnectFromTeamGroup(Team team, UUID playerId) {
        if (serverApi == null || team == null || playerId == null) {
            return;
        }
        String key = team.getName().toLowerCase(Locale.ROOT);
        Group group = teamGroups.get(key);
        if (group == null) {
            return;
        }
        VoicechatConnection conn = serverApi.getConnectionOf(playerId);
        if (conn != null && conn.getGroup() != null && conn.getGroup().getId().equals(group.getId())) {
            conn.setGroup(null);
        }
    }

    /**
     * Coloca o jogador diretamente no grupo de voz do time dele (ex: pelo comando ou GUI).
     */
    public void joinOwnTeamGroup(Player player) {
        if (serverApi == null) {
            player.sendMessage(Component.text("O Simple Voice Chat nao esta ativo no servidor.", NamedTextColor.RED));
            return;
        }
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce precisa fazer parte de um time para entrar no Team Group.", NamedTextColor.RED));
            return;
        }
        VoicechatConnection conn = serverApi.getConnectionOf(player.getUniqueId());
        if (conn == null || !conn.isConnected()) {
            player.sendMessage(Component.text("Seu cliente nao esta conectado ao Simple Voice Chat (instale o mod no cliente).", NamedTextColor.RED));
            return;
        }
        Group group = ensureTeamGroup(team);
        if (group != null) {
            conn.setGroup(group);
            player.sendMessage(
                    Component.text("Voce entrou no canal de voz ", NamedTextColor.GREEN)
                            .append(Component.text(formatGroupName(team), NamedTextColor.GOLD))
            );
        }
    }

    /**
     * Impede a criacao de grupos fechados (NORMAL ou ISOLATED), forcando todos os grupos a serem OPEN (Abertos).
     */
    private void onCreateGroup(CreateGroupEvent event) {
        Group group = event.getGroup();
        if (group == null || group.getType() == Group.Type.OPEN) {
            return;
        }

        // Bloqueia a criacao do grupo fechado/isolado
        event.cancel();

        VoicechatConnection conn = event.getConnection();
        if (conn == null || conn.getPlayer() == null || serverApi == null) {
            return;
        }

        UUID playerId = conn.getPlayer().getUuid();

        if (!group.hasPassword()) {
            // Recria automaticamente o grupo no modo OPEN e coloca o jogador nele
            Group openGroup = serverApi.groupBuilder()
                    .setName(group.getName())
                    .setPersistent(group.isPersistent())
                    .setHidden(group.isHidden())
                    .setType(Group.Type.OPEN)
                    .build();
            conn.setGroup(openGroup);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player bukkitPlayer = Bukkit.getPlayer(playerId);
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(
                            Component.text("Grupos fechados estao desativados! Seu grupo ", NamedTextColor.YELLOW)
                                    .append(Component.text(group.getName(), NamedTextColor.GOLD))
                                    .append(Component.text(" foi criado automaticamente no modo Aberto (Open).", NamedTextColor.YELLOW))
                    );
                    bukkitPlayer.sendActionBar(
                            Component.text("Grupo convertido para o modo Aberto (Open)!", NamedTextColor.GREEN)
                    );
                }
            });
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player bukkitPlayer = Bukkit.getPlayer(playerId);
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    bukkitPlayer.sendMessage(
                            Component.text("Grupos fechados/isolados nao sao permitidos! Selecione o tipo ", NamedTextColor.RED)
                                    .append(Component.text("Open (Aberto)", NamedTextColor.GOLD))
                                    .append(Component.text(" ao criar seu grupo.", NamedTextColor.RED))
                    );
                    bukkitPlayer.sendActionBar(
                            Component.text("Apenas grupos Abertos (Open) sao permitidos!", NamedTextColor.RED)
                    );
                }
            });
        }
    }

    /**
     * Intercepta quando qualquer jogador clica para entrar em um grupo na interface do Voice Chat (V -> Group).
     */
    private void onJoinGroup(JoinGroupEvent event) {
        Group group = event.getGroup();
        VoicechatConnection conn = event.getConnection();
        if (group == null || conn == null || conn.getPlayer() == null) {
            return;
        }

        if (group.getType() != Group.Type.OPEN) {
            event.cancel();
            return;
        }

        String teamKey = groupToTeamKey.get(group.getId());
        if (teamKey == null) {
            // Grupo comum criado manualmente por jogadores, permite normalmente
            return;
        }

        UUID playerId = conn.getPlayer().getUuid();
        Team targetTeam = teamManager.getTeamByName(teamKey);

        if (targetTeam == null || !targetTeam.isMember(playerId)) {
            // Bloqueia a entrada de quem nao pertence ao time
            event.cancel();

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player bukkitPlayer = Bukkit.getPlayer(playerId);
                if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                    String teamDisplay = targetTeam != null ? targetTeam.getName() : teamKey;
                    bukkitPlayer.sendMessage(
                            Component.text("Acesso negado! Apenas membros do time ", NamedTextColor.RED)
                                    .append(Component.text(teamDisplay, NamedTextColor.GOLD))
                                    .append(Component.text(" podem entrar neste Team Group.", NamedTextColor.RED))
                    );
                    bukkitPlayer.sendActionBar(
                            Component.text("Apenas membros do time " + teamDisplay + " podem entrar neste grupo!", NamedTextColor.RED)
                    );
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player bukkitPlayer = Bukkit.getPlayer(playerId);
            if (bukkitPlayer != null && bukkitPlayer.isOnline()) {
                bukkitPlayer.sendActionBar(
                        Component.text("Conectado ao " + formatGroupName(targetTeam), NamedTextColor.GREEN)
                );
            }
        });
    }

    private String formatGroupName(Team team) {
        return "Team Group: " + team.getName() + " [" + team.getTag() + "]";
    }
}
