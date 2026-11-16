package com.eduardo.teams.quest;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia o sorteio diario das 5 Quests (2 Faceis, 2 Medias, 1 Dificil),
 * o reset automatico as 00:00 e o progresso compartilhado por equipe.
 */
public class QuestManager {

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;

    private String currentDateStr;
    private final List<QuestDefinition> activeQuests = new ArrayList<>();
    // Mapa: nomeDoTimeMinusculo -> (questId -> progressoAtual)
    private final Map<String, Map<String, Integer>> teamProgress = new HashMap<>();

    private File storageFile;
    private FileConfiguration storageConfig;
    private boolean dirty = false;

    private BukkitTask midnightCheckTask;

    public QuestManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        loadQuests();
        startMidnightScheduler();
    }

    private void startMidnightScheduler() {
        // Verifica a cada 20 segundos se virou meia-noite (00:00) e salva dados pendentes
        this.midnightCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            String today = LocalDate.now().toString();
            if (!today.equals(this.currentDateStr)) {
                rollNewDailyQuests(today, true);
            } else if (dirty) {
                saveQuests();
            }
        }, 400L, 400L);
    }

    public void shutdown() {
        if (midnightCheckTask != null) {
            midnightCheckTask.cancel();
            midnightCheckTask = null;
        }
        saveQuests();
    }

    public List<QuestDefinition> getActiveQuests() {
        return Collections.unmodifiableList(activeQuests);
    }

    public String getCurrentDateStr() {
        return currentDateStr;
    }

    /**
     * Retorna o tempo restante formatado ate as 00:00 (meia-noite).
     */
    public String getTimeUntilMidnightFormatted() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        Duration duration = Duration.between(now, midnight);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }

    public int getProgress(Team team, String questId) {
        if (team == null || questId == null) {
            return 0;
        }
        String teamKey = team.getName().toLowerCase(Locale.ROOT);
        Map<String, Integer> map = teamProgress.get(teamKey);
        if (map == null) {
            return 0;
        }
        return map.getOrDefault(questId, 0);
    }

    public boolean isCompleted(Team team, QuestDefinition quest) {
        if (team == null || quest == null) {
            return false;
        }
        return getProgress(team, quest.getId()) >= quest.getTargetAmount();
    }

    public int getCompletedCount(Team team) {
        if (team == null) {
            return 0;
        }
        int count = 0;
        for (QuestDefinition q : activeQuests) {
            if (isCompleted(team, q)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Sorteia 5 novas quests (2 Faceis, 2 Medias e 1 Dificil) e reseta o progresso diario.
     */
    public void rollNewDailyQuests(String dateStr, boolean broadcastReset) {
        Set<String> previousIds = new HashSet<>();
        for (QuestDefinition q : activeQuests) {
            previousIds.add(q.getId());
        }

        activeQuests.clear();
        teamProgress.clear();
        this.currentDateStr = dateStr;

        pickRandomQuests(QuestDifficulty.EASY, 2, previousIds);
        pickRandomQuests(QuestDifficulty.MEDIUM, 2, previousIds);
        pickRandomQuests(QuestDifficulty.HARD, 1, previousIds);

        saveQuests();

        if (broadcastReset) {
            Component msg = Component.empty()
                    .append(Component.newline())
                    .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(" 📜 NOVAS QUESTS DIÁRIAS DE EQUIPE!", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(" As 5 missões do dia (2 Fáceis, 2 Médias e 1 Difícil)", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.newline())
                    .append(Component.text(" foram atualizadas! Use ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.text("/quest", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(" ou ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.text("/team", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(" para ver!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.newline())
                    .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline());

            Bukkit.broadcast(msg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.MASTER, 1.0f, 1.0f);
            }
        }
    }

    private void pickRandomQuests(QuestDifficulty difficulty, int count, Set<String> avoidIds) {
        List<QuestDefinition> pool = new ArrayList<>(QuestDefinition.getByDifficulty(difficulty));
        Collections.shuffle(pool, ThreadLocalRandom.current());

        int added = 0;
        for (QuestDefinition q : pool) {
            if (!avoidIds.contains(q.getId())) {
                activeQuests.add(q);
                added++;
                if (added >= count) {
                    return;
                }
            }
        }
        // Fallback caso o pool seja menor
        for (QuestDefinition q : pool) {
            if (!activeQuests.contains(q)) {
                activeQuests.add(q);
                added++;
                if (added >= count) {
                    return;
                }
            }
        }
    }

    /**
     * Incrementa o progresso compartilhado do time do jogador para qualquer quest ativa compativel.
     */
    public void recordAction(Player player, QuestActionType actionType, Material material, EntityType entityType, int amount) {
        if (player == null || amount <= 0) {
            return;
        }
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            return;
        }
        recordTeamAction(team, player, actionType, material, entityType, amount);
    }

    public void recordTeamAction(Team team, Player actor, QuestActionType actionType, Material material, EntityType entityType, int amount) {
        if (team == null || amount <= 0 || activeQuests.isEmpty()) {
            return;
        }

        // Verifica se mudou de dia exatamente agora
        String today = LocalDate.now().toString();
        if (!today.equals(this.currentDateStr)) {
            rollNewDailyQuests(today, true);
        }

        String teamKey = team.getName().toLowerCase(Locale.ROOT);
        Map<String, Integer> progressMap = teamProgress.computeIfAbsent(teamKey, k -> new HashMap<>());

        for (QuestDefinition quest : activeQuests) {
            if (quest.getActionType() != actionType) {
                continue;
            }
            if (material != null && !quest.matchesMaterial(material)) {
                continue;
            }
            if (material == null && !quest.getTargetMaterials().isEmpty()) {
                continue;
            }
            if (entityType != null && !quest.matchesEntity(entityType)) {
                continue;
            }
            if (entityType == null && !quest.getTargetEntities().isEmpty()) {
                continue;
            }

            int current = progressMap.getOrDefault(quest.getId(), 0);
            if (current >= quest.getTargetAmount()) {
                continue; // Ja concluida pelo time hoje
            }

            int updated = Math.min(quest.getTargetAmount(), current + amount);
            progressMap.put(quest.getId(), updated);
            this.dirty = true;

            if (updated >= quest.getTargetAmount()) {
                handleQuestCompleted(team, quest, actor);
            } else if (actor != null && shouldNotifyActionBar(actionType, current, updated, quest.getTargetAmount())) {
                actor.sendActionBar(
                        Component.text("📜 [" + quest.getDifficulty().getDisplayName() + "] ", quest.getDifficulty().getColor())
                                .append(Component.text(quest.getTitle() + ": ", NamedTextColor.WHITE))
                                .append(Component.text(updated + "/" + quest.getTargetAmount(), NamedTextColor.GREEN, TextDecoration.BOLD))
                );
            }
        }
    }

    private boolean shouldNotifyActionBar(QuestActionType type, int oldVal, int newVal, int target) {
        if (type == QuestActionType.TRAVEL_WALK
                || type == QuestActionType.TRAVEL_BOAT
                || type == QuestActionType.TRAVEL_MOUNT
                || type == QuestActionType.TRAVEL_SWIM
                || type == QuestActionType.TRAVEL_NETHER) {
            // Para distancia, mostra a cada 25 blocos para nao poluir a actionbar a cada passo
            return (newVal / 25) > (oldVal / 25);
        }
        return true;
    }

    private void handleQuestCompleted(Team team, QuestDefinition quest, Player lastActor) {
        saveQuests();

        Component teamTag = team.getFormattedTag();
        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("✨ [QUEST CONCLUÍDA] ", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("O time ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(team.getName() + " ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(teamTag)
                .append(Component.text(" completou a quest ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text("[" + quest.getDifficulty().getDisplayName() + "] " + quest.getTitle(), quest.getDifficulty().getColor(), TextDecoration.BOLD))
                .append(Component.text("! (" + getCompletedCount(team) + "/5 hoje)", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.newline());

        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(msg);
                member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.9f, 1.1f);
            }
        }

        onTeamQuestCompletedRewardHook(team, quest, lastActor);
    }

    /**
     * Gancho preparado para quando definirmos a recompensa/pontos de equipe por quest concluida.
     */
    protected void onTeamQuestCompletedRewardHook(Team team, QuestDefinition quest, Player completedBy) {
        // TODO: Adicionar pontos de equipe conforme a dificuldade (EASY, MEDIUM, HARD) quando o sistema de pontos for ativado
    }

    // =========================================================================
    // PERSISTENCIA EM YAML (quests.yml)
    // =========================================================================

    public void loadQuests() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.storageFile = new File(plugin.getDataFolder(), "quests.yml");
        this.storageConfig = YamlConfiguration.loadConfiguration(storageFile);

        String today = LocalDate.now().toString();
        String savedDate = storageConfig.getString("date", "");
        List<String> savedIds = storageConfig.getStringList("activeQuests");

        activeQuests.clear();
        teamProgress.clear();

        if (today.equals(savedDate) && savedIds.size() == 5) {
            for (String id : savedIds) {
                QuestDefinition q = QuestDefinition.getById(id);
                if (q != null) {
                    activeQuests.add(q);
                }
            }
        }

        if (activeQuests.size() != 5) {
            rollNewDailyQuests(today, false);
            return;
        }

        this.currentDateStr = today;

        ConfigurationSection progressSec = storageConfig.getConfigurationSection("teamProgress");
        if (progressSec != null) {
            for (String teamKey : progressSec.getKeys(false)) {
                ConfigurationSection teamSec = progressSec.getConfigurationSection(teamKey);
                if (teamSec == null) continue;
                Map<String, Integer> map = new HashMap<>();
                for (String qId : teamSec.getKeys(false)) {
                    map.put(qId, teamSec.getInt(qId, 0));
                }
                teamProgress.put(teamKey.toLowerCase(Locale.ROOT), map);
            }
        }
    }

    public void saveQuests() {
        if (storageConfig == null || storageFile == null) {
            return;
        }
        storageConfig.set("date", currentDateStr);
        List<String> ids = new ArrayList<>();
        for (QuestDefinition q : activeQuests) {
            ids.add(q.getId());
        }
        storageConfig.set("activeQuests", ids);

        storageConfig.set("teamProgress", null);
        for (Map.Entry<String, Map<String, Integer>> entry : teamProgress.entrySet()) {
            String teamKey = entry.getKey();
            for (Map.Entry<String, Integer> qEntry : entry.getValue().entrySet()) {
                storageConfig.set("teamProgress." + teamKey + "." + qEntry.getKey(), qEntry.getValue());
            }
        }

        try {
            storageConfig.save(storageFile);
            this.dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar quests.yml: " + e.getMessage());
        }
    }
}
