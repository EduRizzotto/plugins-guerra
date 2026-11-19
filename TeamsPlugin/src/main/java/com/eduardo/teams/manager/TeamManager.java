package com.eduardo.teams.manager;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.voice.TeamVoicechatIntegration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gerencia todos os times, convites pendentes, persistencia em YAML e exibicao de tags.
 */
public class TeamManager {

    public enum PromptType {
        CREATE_TEAM,
        CHANGE_TAG
    }

    private final TeamsPlugin plugin;
    private final Map<String, Team> teamsByName = new LinkedHashMap<>();
    private final Map<UUID, Team> playerTeamMap = new HashMap<>();
    // Mapa de Jogador Convidado -> Nome do Time (em minusculo)
    private final Map<UUID, String> pendingInvites = new HashMap<>();
    // Mapa de Jogador -> Acao aguardando digitacao no chat (via GUI)
    private final Map<UUID, PromptType> chatPrompts = new HashMap<>();

    // Estatisticas de PvP por jogador (UUID -> [kills, deaths])
    public record PlayerStats(int kills, int deaths) {}
    private record CombatTag(UUID attackerId, long timestamp) {}

    public static final int POINTS_PVP_KILL = 20;
    public static final int POINTS_AIRDROP_FIRST = 15;
    public static final int POINTS_HUNT_KILL_PREY = 50;
    public static final int POINTS_HUNT_SURVIVE = 40;
    public static final int POINTS_RELIC_WIN = 60;
    public static final int POINTS_KOTH_WIN = 50;

    private static final long COMBAT_TAG_DURATION_MS = 15_000L;
    private final Map<UUID, PlayerStats> playerStatsMap = new HashMap<>();
    private final Map<UUID, CombatTag> lastDamageMap = new HashMap<>();
    // Nomes de jogadores (em minusculo) autorizados via /leader add <nome> a criar times
    private final Set<String> authorizedLeaders = new LinkedHashSet<>();

    private File storageFile;
    private FileConfiguration storageConfig;
    private TeamVoicechatIntegration voicechatIntegration;

    public TeamManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        loadTeams();
    }

    public void setVoicechatIntegration(TeamVoicechatIntegration voicechatIntegration) {
        this.voicechatIntegration = voicechatIntegration;
    }

    public TeamVoicechatIntegration getVoicechatIntegration() {
        return voicechatIntegration;
    }

    public Collection<Team> getAllTeams() {
        return Collections.unmodifiableCollection(teamsByName.values());
    }

    public boolean canCreateTeam(Player player) {
        if (player == null) {
            return false;
        }
        return player.isOp()
                || player.hasPermission("teams.admin")
                || isAuthorizedLeader(player.getName());
    }

    public boolean isAuthorizedLeader(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        return authorizedLeaders.contains(playerName.trim().toLowerCase(Locale.ROOT));
    }

    public boolean addAuthorizedLeader(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        boolean added = authorizedLeaders.add(playerName.trim().toLowerCase(Locale.ROOT));
        if (added) {
            saveTeams();
        }
        return added;
    }

    public boolean removeAuthorizedLeader(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return false;
        }
        boolean removed = authorizedLeaders.remove(playerName.trim().toLowerCase(Locale.ROOT));
        if (removed) {
            saveTeams();
        }
        return removed;
    }

    public Set<String> getAuthorizedLeaders() {
        return Collections.unmodifiableSet(authorizedLeaders);
    }

    public Team getTeamByName(String name) {
        if (name == null) {
            return null;
        }
        return teamsByName.get(name.toLowerCase(Locale.ROOT));
    }

    public Team getPlayerTeam(UUID playerId) {
        return playerTeamMap.get(playerId);
    }

    public boolean isSameTeam(UUID playerA, UUID playerB) {
        if (playerA == null || playerB == null || playerA.equals(playerB)) {
            return false;
        }
        Team teamA = playerTeamMap.get(playerA);
        return teamA != null && teamA.equals(playerTeamMap.get(playerB));
    }

    public boolean isTagTaken(String tag) {
        for (Team t : teamsByName.values()) {
            if (t.getTag().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    public Team createTeam(String name, String tag, UUID leaderId) {
        Team team = new Team(name, tag, NamedTextColor.AQUA, leaderId);
        teamsByName.put(name.toLowerCase(Locale.ROOT), team);
        playerTeamMap.put(leaderId, team);
        pendingInvites.remove(leaderId);
        updatePlayerDisplay(Bukkit.getPlayer(leaderId));
        if (voicechatIntegration != null) {
            voicechatIntegration.ensureTeamGroup(team);
        }
        saveTeams();
        return team;
    }

    public void disbandTeam(Team team) {
        if (voicechatIntegration != null) {
            voicechatIntegration.removeTeamGroup(team);
        }
        unregisterScoreboardTeam(team);
        teamsByName.remove(team.getName().toLowerCase(Locale.ROOT));
        for (UUID memberId : team.getMembers()) {
            playerTeamMap.remove(memberId);
            updatePlayerDisplay(Bukkit.getPlayer(memberId));
        }
        pendingInvites.values().removeIf(invitedTeam -> invitedTeam.equalsIgnoreCase(team.getName()));
        saveTeams();
    }

    public void addMember(Team team, UUID playerId) {
        team.addMember(playerId);
        playerTeamMap.put(playerId, team);
        pendingInvites.remove(playerId);
        updatePlayerDisplay(Bukkit.getPlayer(playerId));
        saveTeams();
    }

    public void removeMember(Team team, UUID playerId) {
        if (voicechatIntegration != null) {
            voicechatIntegration.disconnectFromTeamGroup(team, playerId);
        }
        team.removeMember(playerId);
        playerTeamMap.remove(playerId);
        updatePlayerDisplay(Bukkit.getPlayer(playerId));
        saveTeams();
    }

    public void invitePlayer(Team team, UUID targetId) {
        pendingInvites.put(targetId, team.getName().toLowerCase(Locale.ROOT));
    }

    public Team getPendingInvite(UUID playerId) {
        String teamKey = pendingInvites.get(playerId);
        return teamKey != null ? teamsByName.get(teamKey) : null;
    }

    public void clearInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    public void setChatPrompt(UUID playerId, PromptType promptType) {
        if (promptType == null) {
            chatPrompts.remove(playerId);
        } else {
            chatPrompts.put(playerId, promptType);
        }
    }

    public PromptType consumeChatPrompt(UUID playerId) {
        return chatPrompts.remove(playerId);
    }

    public PlayerStats getPlayerStats(UUID playerId) {
        return playerStatsMap.getOrDefault(playerId, new PlayerStats(0, 0));
    }

    public int getTeamPoints(Team team) {
        return team != null ? team.getPoints() : 0;
    }

    /**
     * Adiciona pontos permanentes para uma equipe, avisa os membros online e salva no arquivo.
     * Mesmo que um jogador saia do time depois, esses pontos permanecem na equipe.
     */
    public void awardTeamPoints(Team team, int pointsToAdd, String reason) {
        if (team == null || pointsToAdd <= 0) {
            return;
        }
        team.addPoints(pointsToAdd);
        saveTeams();

        Component msg = Component.text("⭐ [+" + pointsToAdd + " pts] ", NamedTextColor.GOLD)
                .append(Component.text(reason + " ", NamedTextColor.YELLOW))
                .append(Component.text("(Total da equipe: " + team.getPoints() + " pts)", NamedTextColor.GREEN));

        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(msg);
            }
        }
    }

    public int getTeamTotalKills(Team team) {
        if (team == null) {
            return 0;
        }
        return team.getTeamKills();
    }

    public int getTeamTotalDeaths(Team team) {
        if (team == null) {
            return 0;
        }
        return team.getTeamDeaths();
    }

    /**
     * Registra que 'attackerId' causou dano em 'victimId' para rastrear mortes em PvP.
     */
    public void recordPvpDamage(UUID victimId, UUID attackerId) {
        if (victimId == null || attackerId == null || victimId.equals(attackerId)) {
            return;
        }
        lastDamageMap.put(victimId, new CombatTag(attackerId, System.currentTimeMillis()));
    }

    /**
     * Retorna o ultimo jogador que atacou a vitima nos ultimos 15 segundos (e limpa o registro).
     */
    public UUID consumeRecentPvpAttacker(UUID victimId) {
        CombatTag tag = lastDamageMap.remove(victimId);
        if (tag == null) {
            return null;
        }
        if (System.currentTimeMillis() - tag.timestamp() <= COMBAT_TAG_DURATION_MS) {
            return tag.attackerId();
        }
        return null;
    }

    /**
     * Contabiliza 1 kill para o assassino e 1 morte para a vitima (em PvP),
     * somando +20 pontos permanentes na equipe do assassino (morrer em PvP = 0 pts perdidos).
     */
    public void recordPvpKillAndDeath(UUID killerId, UUID victimId) {
        if (killerId != null && !killerId.equals(victimId)) {
            PlayerStats killerStats = getPlayerStats(killerId);
            playerStatsMap.put(killerId, new PlayerStats(killerStats.kills() + 1, killerStats.deaths()));
            updatePlayerDisplay(Bukkit.getPlayer(killerId));

            if (!isSameTeam(killerId, victimId)) {
                Team killerTeam = getPlayerTeam(killerId);
                if (killerTeam != null) {
                    killerTeam.incrementTeamKills();
                    killerTeam.addPoints(POINTS_PVP_KILL);
                    Player victimPlayer = victimId != null ? Bukkit.getPlayer(victimId) : null;
                    String victimName = victimPlayer != null ? victimPlayer.getName() : "inimigo";
                    Component msg = Component.text("⭐ [+" + POINTS_PVP_KILL + " pts] ", NamedTextColor.GOLD)
                            .append(Component.text("Abate PvP sobre " + victimName + " ", NamedTextColor.YELLOW))
                            .append(Component.text("(Total da equipe: " + killerTeam.getPoints() + " pts)", NamedTextColor.GREEN));
                    for (UUID memberId : killerTeam.getMembers()) {
                        Player m = Bukkit.getPlayer(memberId);
                        if (m != null && m.isOnline()) {
                            m.sendMessage(msg);
                        }
                    }
                }
            }
        }

        if (victimId != null) {
            PlayerStats victimStats = getPlayerStats(victimId);
            playerStatsMap.put(victimId, new PlayerStats(victimStats.kills(), victimStats.deaths() + 1));
            updatePlayerDisplay(Bukkit.getPlayer(victimId));

            Team victimTeam = getPlayerTeam(victimId);
            if (victimTeam != null) {
                victimTeam.incrementTeamDeaths();
            }
        }

        saveTeams();
    }

    /**
     * Formata o sufixo [kills, mortes] para exibicao no Tab e menus.
     */
    public Component formatStatsBracket(int kills, int deaths) {
        return Component.text("[", NamedTextColor.GRAY)
                .append(Component.text(String.valueOf(kills), NamedTextColor.GREEN))
                .append(Component.text(", ", NamedTextColor.GRAY))
                .append(Component.text(String.valueOf(deaths), NamedTextColor.RED))
                .append(Component.text("]", NamedTextColor.GRAY));
    }

    /**
     * Atualiza o nome do jogador na Tablist com a tag colorida do time e [kills, mortes].
     */
    public void updatePlayerDisplay(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerStats stats = getPlayerStats(player.getUniqueId());
        Component statsSuffix = Component.text(" ").append(formatStatsBracket(stats.kills(), stats.deaths()));

        Team team = getPlayerTeam(player.getUniqueId());
        syncPlayerScoreboardTeam(player, team);
        if (team != null) {
            Component tabName = team.getFormattedTag()
                    .append(Component.text(" " + player.getName(), NamedTextColor.WHITE))
                    .append(statsSuffix);
            player.playerListName(tabName);
        } else {
            player.playerListName(
                    Component.text(player.getName(), NamedTextColor.WHITE)
                            .append(statsSuffix)
            );
        }
    }

    private String toScoreboardTeamName(Team team) {
        return "tp_" + team.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * Sincroniza o jogador no Scoreboard Team correspondente para que o efeito GLOWING
     * apareca exatamente na cor escolhida pelo time.
     */
    private void syncPlayerScoreboardTeam(Player player, Team team) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getMainScoreboard();
        String entry = player.getName();

        org.bukkit.scoreboard.Team currentSbTeam = scoreboard.getEntryTeam(entry);
        if (team == null) {
            if (currentSbTeam != null && currentSbTeam.getName().startsWith("tp_")) {
                currentSbTeam.removeEntry(entry);
            }
            return;
        }

        String sbName = toScoreboardTeamName(team);
        org.bukkit.scoreboard.Team sbTeam = scoreboard.getTeam(sbName);
        if (sbTeam == null) {
            sbTeam = scoreboard.registerNewTeam(sbName);
        }
        sbTeam.color(team.getColor());
        sbTeam.setAllowFriendlyFire(true);

        if (currentSbTeam != null && !currentSbTeam.equals(sbTeam) && currentSbTeam.getName().startsWith("tp_")) {
            currentSbTeam.removeEntry(entry);
        }
        if (!sbTeam.hasEntry(entry)) {
            sbTeam.addEntry(entry);
        }
    }

    private void unregisterScoreboardTeam(Team team) {
        if (team == null) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        org.bukkit.scoreboard.Team sbTeam = manager.getMainScoreboard().getTeam(toScoreboardTeamName(team));
        if (sbTeam != null) {
            sbTeam.unregister();
        }
    }

    /**
     * Atualiza todos os membros online de um time (ex: ao trocar a tag ou a cor).
     */
    public void refreshTeamDisplays(Team team) {
        for (UUID memberId : team.getMembers()) {
            updatePlayerDisplay(Bukkit.getPlayer(memberId));
        }
        if (voicechatIntegration != null) {
            voicechatIntegration.refreshTeamGroup(team);
        }
        saveTeams();
    }

    public void loadTeams() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        storageFile = new File(plugin.getDataFolder(), "teams.yml");
        if (!storageFile.exists()) {
            return;
        }
        storageConfig = YamlConfiguration.loadConfiguration(storageFile);
        teamsByName.clear();
        playerTeamMap.clear();
        playerStatsMap.clear();
        authorizedLeaders.clear();

        List<String> savedLeaders = storageConfig.getStringList("authorized-leaders");
        for (String leaderName : savedLeaders) {
            if (leaderName != null && !leaderName.isBlank()) {
                authorizedLeaders.add(leaderName.trim().toLowerCase(Locale.ROOT));
            }
        }

        ConfigurationSection statsSection = storageConfig.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String uuidStr : statsSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidStr);
                    int kills = statsSection.getInt(uuidStr + ".kills", 0);
                    int deaths = statsSection.getInt(uuidStr + ".deaths", 0);
                    playerStatsMap.put(playerId, new PlayerStats(kills, deaths));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        ConfigurationSection teamsSection = storageConfig.getConfigurationSection("teams");
        if (teamsSection == null) {
            return;
        }

        for (String key : teamsSection.getKeys(false)) {
            ConfigurationSection sec = teamsSection.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String name = sec.getString("name", key);
            String tag = sec.getString("tag", name.substring(0, Math.min(3, name.length())).toUpperCase(Locale.ROOT));
            String colorName = sec.getString("color", "aqua");
            NamedTextColor color = NamedTextColor.NAMES.value(colorName.toLowerCase(Locale.ROOT));
            if (color == null) {
                color = NamedTextColor.AQUA;
            }

            String leaderStr = sec.getString("leader");
            if (leaderStr == null) {
                continue;
            }
            UUID leaderId = UUID.fromString(leaderStr);
            Team team = new Team(name, tag, color, leaderId);

            List<String> memberStrs = sec.getStringList("members");
            for (String mStr : memberStrs) {
                UUID mId = UUID.fromString(mStr);
                team.addMember(mId);
                playerTeamMap.put(mId, team);
            }
            playerTeamMap.put(leaderId, team);

            int fallbackKills = 0;
            int fallbackDeaths = 0;
            for (UUID mId : team.getMembers()) {
                PlayerStats ps = getPlayerStats(mId);
                fallbackKills += ps.kills();
                fallbackDeaths += ps.deaths();
            }

            int savedTeamKills = sec.contains("teamKills") ? sec.getInt("teamKills", fallbackKills) : fallbackKills;
            int savedTeamDeaths = sec.contains("teamDeaths") ? sec.getInt("teamDeaths", fallbackDeaths) : fallbackDeaths;
            int savedPoints = sec.contains("points") ? sec.getInt("points", 0) : (savedTeamKills * POINTS_PVP_KILL);

            team.setTeamKills(savedTeamKills);
            team.setTeamDeaths(savedTeamDeaths);
            team.setPoints(savedPoints);

            teamsByName.put(name.toLowerCase(Locale.ROOT), team);
        }
    }

    public void saveTeams() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (storageFile == null) {
            storageFile = new File(plugin.getDataFolder(), "teams.yml");
        }
        storageConfig = new YamlConfiguration();
        for (Team team : teamsByName.values()) {
            String path = "teams." + team.getName().toLowerCase(Locale.ROOT);
            storageConfig.set(path + ".name", team.getName());
            storageConfig.set(path + ".tag", team.getTag());
            storageConfig.set(path + ".color", team.getColor().toString());
            storageConfig.set(path + ".leader", team.getLeader().toString());
            storageConfig.set(path + ".points", team.getPoints());
            storageConfig.set(path + ".teamKills", team.getTeamKills());
            storageConfig.set(path + ".teamDeaths", team.getTeamDeaths());
            List<String> memberIds = team.getMembers().stream().map(UUID::toString).toList();
            storageConfig.set(path + ".members", memberIds);
        }
        for (Map.Entry<UUID, PlayerStats> entry : playerStatsMap.entrySet()) {
            String path = "stats." + entry.getKey();
            storageConfig.set(path + ".kills", entry.getValue().kills());
            storageConfig.set(path + ".deaths", entry.getValue().deaths());
        }
        storageConfig.set("authorized-leaders", new ArrayList<>(authorizedLeaders));
        try {
            storageConfig.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar teams.yml: " + e.getMessage());
        }
    }
}
