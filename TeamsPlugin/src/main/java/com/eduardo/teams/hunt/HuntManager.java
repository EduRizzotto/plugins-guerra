package com.eduardo.teams.hunt;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.quest.QuestActionType;
import com.eduardo.teams.quest.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia o Evento de Cacada (Hunt):
 * - Acontece 2 vezes por dia em horarios aleatorios.
 * - Fase de aviso previo (5 min, 1 min, 10s) para os jogadores sairem de suas bases.
 * - Sorteio de 1 jogador online de cada time como "Presa".
 * - Se a presa quitar no meio da cacada, outro membro online do mesmo time vira a presa no lugar.
 * - Duracao de 30 minutos com efeito Luminoso (GLOWING) continuo nas presas.
 * - Divulgacao das coordenadas das presas no chat a cada 3 minutos.
 */
public class HuntManager {

    public enum HuntState {
        IDLE,
        COUNTDOWN,
        ACTIVE
    }

    public static final int DAILY_HUNTS_COUNT = 2;
    public static final int COUNTDOWN_SECONDS = 5 * 60; // 5 minutos (300s) de aviso previo
    public static final int HUNT_DURATION_SECONDS = 30 * 60; // 30 minutos (1800s) de cacada
    public static final int COORDS_BROADCAST_INTERVAL_SECONDS = 3 * 60; // A cada 3 minutos (180s)

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private QuestManager questManager;

    private HuntState state = HuntState.IDLE;
    private int secondsLeft = 0;
    // Mapa de Nome do Time (minusculo) -> UUID da Presa ativa daquele time
    private final Map<String, UUID> activePreysByTeam = new LinkedHashMap<>();
    // Times cuja presa quitou sem deixar outro membro online (se alguem do time entrar, assume como presa)
    private final Set<String> teamsAwaitingReconnectPrey = new HashSet<>();

    // Agendamento de 2 horarios aleatorios por dia (em minutos a partir de 00:00)
    private LocalDate scheduledDate;
    private final List<Integer> scheduledMinutesOfDay = new ArrayList<>();
    private final Set<Integer> triggeredScheduleIndices = new HashSet<>();

    private BukkitTask tickTask;

    public HuntManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        rollDailySchedule(LocalDate.now());
        startSchedulers();
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    private void startSchedulers() {
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        stopHunt(false);
    }

    /**
     * Sorteia 2 horarios aleatorios para o dia atual, separados por pelo menos 2 horas.
     */
    public void rollDailySchedule(LocalDate date) {
        this.scheduledDate = date;
        this.scheduledMinutesOfDay.clear();
        this.triggeredScheduleIndices.clear();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int nowMinute = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();

        // Janela padrao do dia: entre 10:00 (600m) e 23:00 (1380m)
        // Se ja passou das 10:00 quando o servidor ligou, sorteia nos minutos restantes do dia
        int startWindow = Math.max(600, nowMinute + 10);
        int endWindow = 1410; // 23:30

        if (startWindow < endWindow - 90) {
            int midWindow = (startWindow + endWindow) / 2;
            int first = rng.nextInt(startWindow, Math.max(startWindow + 1, midWindow - 30));
            int second = rng.nextInt(Math.min(endWindow - 1, midWindow + 30), endWindow);
            scheduledMinutesOfDay.add(first);
            scheduledMinutesOfDay.add(second);
        } else {
            // Se o servidor ligou muito tarde da noite, agenda 2 horarios para o restante ou inicio
            int first = Math.min(1430, nowMinute + rng.nextInt(15, 45));
            int second = Math.min(1438, first + rng.nextInt(45, 90));
            scheduledMinutesOfDay.add(first);
            scheduledMinutesOfDay.add(second);
        }

        Collections.sort(scheduledMinutesOfDay);
        plugin.getLogger().info("[Cacada] Horarios aleatorios sorteados para hoje (" + date + "): " + getFormattedSchedule());
    }

    public List<String> getFormattedSchedule() {
        List<String> formatted = new ArrayList<>();
        for (int i = 0; i < scheduledMinutesOfDay.size(); i++) {
            int totalMins = scheduledMinutesOfDay.get(i);
            int h = totalMins / 60;
            int m = totalMins % 60;
            boolean done = triggeredScheduleIndices.contains(i);
            formatted.add(String.format("%02d:%02d%s", h, m, done ? " (já ocorreu)" : ""));
        }
        return formatted;
    }

    public HuntState getState() {
        return state;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public boolean isPrey(UUID playerId) {
        return playerId != null && activePreysByTeam.containsValue(playerId);
    }

    private boolean hasAnyOnlineTeamPlayers() {
        for (Team team : teamManager.getAllTeams()) {
            if (!getEligibleOnlineMembers(team, null).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<Player> getEligibleOnlineMembers(Team team, UUID excludePlayerId) {
        List<Player> list = new ArrayList<>();
        for (UUID memberId : team.getMembers()) {
            if (excludePlayerId != null && excludePlayerId.equals(memberId)) {
                continue;
            }
            Player p = Bukkit.getPlayer(memberId);
            if (p != null && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Inicia a contagem regressiva de 5 minutos antes da Cacada comecar.
     */
    public boolean startCountdown() {
        if (state != HuntState.IDLE) {
            return false;
        }
        this.state = HuntState.COUNTDOWN;
        this.secondsLeft = COUNTDOWN_SECONDS;
        this.activePreysByTeam.clear();
        this.teamsAwaitingReconnectPrey.clear();

        broadcastPreHuntWarning("5 minutos");
        return true;
    }

    /**
     * Pula a contagem regressiva (ou inicia direto) e comeca os 30 minutos de Cacada imediatamente.
     */
    public boolean startHuntImmediately() {
        this.activePreysByTeam.clear();
        this.teamsAwaitingReconnectPrey.clear();

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Team team : teamManager.getAllTeams()) {
            List<Player> onlineMembers = getEligibleOnlineMembers(team, null);
            if (!onlineMembers.isEmpty()) {
                Player chosen = onlineMembers.get(rng.nextInt(onlineMembers.size()));
                activePreysByTeam.put(team.getName().toLowerCase(Locale.ROOT), chosen.getUniqueId());
            }
        }

        if (activePreysByTeam.isEmpty()) {
            this.state = HuntState.IDLE;
            this.secondsLeft = 0;
            Bukkit.broadcast(
                    Component.text("🎯 [Caçada] ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("O evento de Caçada foi cancelado pois nenhum time possui jogadores online no momento.", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
            );
            return false;
        }

        this.state = HuntState.ACTIVE;
        this.secondsLeft = HUNT_DURATION_SECONDS;

        applyGlowToActivePreys();
        announceHuntStarted();
        broadcastPreyCoordinates();
        return true;
    }

    /**
     * Encerra o evento de Cacada e limpa os efeitos das presas.
     */
    public void stopHunt(boolean announceCancelled) {
        clearGlowFromPreys();
        activePreysByTeam.clear();
        teamsAwaitingReconnectPrey.clear();
        state = HuntState.IDLE;
        secondsLeft = 0;

        if (announceCancelled) {
            Bukkit.broadcast(
                    Component.text("🎯 [Caçada] ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("O evento de Caçada foi encerrado!", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
            );
        }
    }

    private void tick() {
        // Verifica virada de dia (00:00) para sortear os 2 novos horarios aleatorios
        LocalDate today = LocalDate.now();
        if (!today.equals(this.scheduledDate)) {
            rollDailySchedule(today);
        }

        if (state == HuntState.IDLE) {
            checkScheduledHuntTrigger();
            return;
        }

        if (state == HuntState.COUNTDOWN) {
            secondsLeft--;

            if (secondsLeft == 60) {
                broadcastPreHuntWarning("1 minuto");
            } else if (secondsLeft == 10) {
                broadcastPreHuntWarning("10 segundos");
            } else if (secondsLeft <= 0) {
                startHuntImmediately();
            }
            return;
        }

        if (state == HuntState.ACTIVE) {
            secondsLeft--;

            // Reaplica o efeito Luminoso (GLOWING) a cada segundo nas presas online
            applyGlowToActivePreys();

            if (secondsLeft <= 0) {
                finishHuntWithSurvivors();
                return;
            }

            // A cada 3 minutos (180 segundos) anuncia as coordenadas das presas no chat
            int elapsed = HUNT_DURATION_SECONDS - secondsLeft;
            if (elapsed > 0 && elapsed % COORDS_BROADCAST_INTERVAL_SECONDS == 0) {
                broadcastPreyCoordinates();
            }
        }
    }

    private void checkScheduledHuntTrigger() {
        int currentMinuteOfDay = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        for (int i = 0; i < scheduledMinutesOfDay.size(); i++) {
            if (triggeredScheduleIndices.contains(i)) {
                continue;
            }
            int targetMinute = scheduledMinutesOfDay.get(i);
            if (currentMinuteOfDay >= targetMinute && hasAnyOnlineTeamPlayers()) {
                triggeredScheduleIndices.add(i);
                startCountdown();
                break;
            }
        }
    }

    /**
     * Se a presa quitar no meio da Cacada, sorteia imediatamente outro membro online do mesmo time
     * para virar a presa no lugar dela!
     */
    public void handlePreyQuit(Player quittingPrey) {
        if (state != HuntState.ACTIVE || quittingPrey == null) {
            return;
        }

        UUID quitId = quittingPrey.getUniqueId();
        if (!isPrey(quitId)) {
            return;
        }

        quittingPrey.removePotionEffect(PotionEffectType.GLOWING);

        Team team = teamManager.getPlayerTeam(quitId);
        if (team == null) {
            activePreysByTeam.values().removeIf(id -> id.equals(quitId));
            return;
        }

        String teamKey = team.getName().toLowerCase(Locale.ROOT);
        List<Player> remainingOnline = getEligibleOnlineMembers(team, quitId);

        if (!remainingOnline.isEmpty()) {
            Player newPrey = remainingOnline.get(ThreadLocalRandom.current().nextInt(remainingOnline.size()));
            activePreysByTeam.put(teamKey, newPrey.getUniqueId());
            teamsAwaitingReconnectPrey.remove(teamKey);

            applyGlowToActivePreys();

            Title newPreyTitle = Title.title(
                    Component.text("🎯 VOCÊ É A NOVA PRESA!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(quittingPrey.getName() + " saiu e você assumiu o lugar!", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
            );
            newPrey.showTitle(newPreyTitle);
            newPrey.playSound(newPrey.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.RECORDS, 1.0f, 0.85f);

            Location loc = newPrey.getLocation();
            String coords = "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ()
                    + " (" + formatWorldEnvironment(loc.getWorld()) + ")";

            Bukkit.broadcast(
                    Component.empty()
                            .append(Component.newline())
                            .append(Component.text("🎯 [CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("A presa ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(formatPlayerWithTeam(quittingPrey.getName(), team))
                            .append(Component.text(" desconectou! ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(formatPlayerWithTeam(newPrey.getName(), team))
                            .append(Component.text(" virou a nova Presa do time em ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(coords, NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
            );
        } else {
            // Nenhum outro membro do time esta online no momento; se alguem do time reconectar durante a cacada, vira presa
            activePreysByTeam.remove(teamKey);
            teamsAwaitingReconnectPrey.add(teamKey);

            Bukkit.broadcast(
                    Component.text("🎯 [CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("A presa ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(formatPlayerWithTeam(quittingPrey.getName(), team))
                            .append(Component.text(" desconectou e não há outro membro do time online! (Se alguém do time reconectar, assumirá como Presa)", NamedTextColor.RED)
                                    .decoration(TextDecoration.BOLD, false))
            );
        }
    }

    /**
     * Se um jogador entrar durante a Cacada e o time dele estava sem presa por desconexao, ele assume como Presa.
     */
    public void handlePlayerJoin(Player player) {
        if (state != HuntState.ACTIVE || player == null) {
            return;
        }

        if (isPrey(player.getUniqueId())) {
            applyGlowToActivePreys();
            return;
        }

        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            return;
        }

        String teamKey = team.getName().toLowerCase(Locale.ROOT);
        if (teamsAwaitingReconnectPrey.remove(teamKey)) {
            activePreysByTeam.put(teamKey, player.getUniqueId());
            applyGlowToActivePreys();

            Title newPreyTitle = Title.title(
                    Component.text("🎯 VOCÊ É A PRESA!", NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text("Você reconectou e assumiu como Presa do seu time!", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
            );
            player.showTitle(newPreyTitle);

            Bukkit.broadcast(
                    Component.text("🎯 [CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(formatPlayerWithTeam(player.getName(), team))
                            .append(Component.text(" entrou no servidor e assumiu o posto de Presa do seu time!", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
            );
        }
    }

    /**
     * Chamado quando uma Presa morre durante o evento de Cacada.
     */
    public void handlePreyDeath(Player prey, Player killer) {
        if (state != HuntState.ACTIVE || !isPrey(prey.getUniqueId())) {
            return;
        }

        Team preyTeam = teamManager.getPlayerTeam(prey.getUniqueId());
        if (preyTeam != null) {
            teamsAwaitingReconnectPrey.remove(preyTeam.getName().toLowerCase(Locale.ROOT));
        }

        // Remove da lista de presas ativas
        activePreysByTeam.values().removeIf(uuid -> uuid.equals(prey.getUniqueId()));
        prey.removePotionEffect(PotionEffectType.GLOWING);

        Component preyDisplay = formatPlayerWithTeam(prey.getName(), preyTeam);

        if (killer != null && !killer.equals(prey)) {
            Team killerTeam = teamManager.getPlayerTeam(killer.getUniqueId());
            Component killerDisplay = formatPlayerWithTeam(killer.getName(), killerTeam);

            Bukkit.broadcast(
                    Component.empty()
                            .append(Component.newline())
                            .append(Component.text("🎯 [CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("A presa ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(preyDisplay)
                            .append(Component.text(" foi caçada e abatida por ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(killerDisplay)
                            .append(Component.text("!", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
            );

            onPreyKilledRewardHook(killer, killerTeam, prey, preyTeam);
        } else {
            Bukkit.broadcast(
                    Component.text("🎯 [CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("A presa ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(preyDisplay)
                            .append(Component.text(" morreu!", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
            );
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.5f, 1.4f);
        }

        if (activePreysByTeam.isEmpty() && teamsAwaitingReconnectPrey.isEmpty()) {
            state = HuntState.IDLE;
            secondsLeft = 0;
            Bukkit.broadcast(
                    Component.text("🎯 [CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("Todas as presas foram abatidas! O evento de Caçada chegou ao fim!", NamedTextColor.GREEN)
                                    .decoration(TextDecoration.BOLD, false))
            );
        }
    }

    /**
     * Gancho pronto para quando implementarmos o sistema de pontos por equipe.
     */
    protected void onPreyKilledRewardHook(Player killer, Team killerTeam, Player prey, Team preyTeam) {
        if (killerTeam != null && !killerTeam.equals(preyTeam)) {
            teamManager.awardTeamPoints(killerTeam, TeamManager.POINTS_HUNT_KILL_PREY, "Abate da Presa " + prey.getName() + " na Caçada");
        }
        if (questManager != null && killer != null) {
            questManager.recordAction(killer, QuestActionType.SERVER_KILL_PREY, null, null, 1);
        }
    }

    /**
     * Gancho pronto para quando uma presa sobrevive os 30 minutos inteiros.
     */
    protected void onPreySurvivedRewardHook(Player survivor, Team survivorTeam) {
        if (survivorTeam != null) {
            teamManager.awardTeamPoints(survivorTeam, TeamManager.POINTS_HUNT_SURVIVE, "Presa " + survivor.getName() + " sobreviveu aos 30 min da Caçada");
        }
        if (questManager != null && survivorTeam != null) {
            questManager.recordTeamAction(survivorTeam, survivor, QuestActionType.SERVER_SURVIVE_HUNT, null, null, 1);
        }
    }

    private void finishHuntWithSurvivors() {
        List<Component> survivorNames = new ArrayList<>();

        for (UUID preyId : new ArrayList<>(activePreysByTeam.values())) {
            Player p = Bukkit.getPlayer(preyId);
            Team t = teamManager.getPlayerTeam(preyId);
            if (p != null && p.isOnline()) {
                p.removePotionEffect(PotionEffectType.GLOWING);
                survivorNames.add(formatPlayerWithTeam(p.getName(), t));
                onPreySurvivedRewardHook(p, t);
            }
        }

        activePreysByTeam.clear();
        teamsAwaitingReconnectPrey.clear();
        state = HuntState.IDLE;
        secondsLeft = 0;

        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 🎯 FIM DO EVENTO DE CAÇADA!", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" Os 30 minutos terminaram! Presas sobreviventes:", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.newline());

        if (survivorNames.isEmpty()) {
            msg = msg.append(Component.text(" • Nenhuma presa sobreviveu.", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                    .append(Component.newline());
        } else {
            for (Component surv : survivorNames) {
                msg = msg.append(Component.text(" • ", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false))
                        .append(surv)
                        .append(Component.newline());
            }
        }

        msg = msg.append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());

        Bukkit.broadcast(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.RECORDS, 0.8f, 1.0f);
        }
    }

    public void applyGlowToActivePreys() {
        if (state != HuntState.ACTIVE) {
            return;
        }
        PotionEffect glow = new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true);
        for (UUID preyId : activePreysByTeam.values()) {
            Player p = Bukkit.getPlayer(preyId);
            if (p != null && p.isOnline() && !p.isDead()) {
                p.addPotionEffect(glow);
            }
        }
    }

    private void clearGlowFromPreys() {
        for (UUID preyId : activePreysByTeam.values()) {
            Player p = Bukkit.getPlayer(preyId);
            if (p != null) {
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
    }

    private void broadcastPreHuntWarning(String timeLabel) {
        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" ⚠ ALERTA DE CAÇADA!", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" O evento de Caçada vai começar em ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(timeLabel, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text(" Saiam da base de vocês agora se não quiserem", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text(" que a localização dela seja descoberta!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline());

        Bukkit.broadcast(msg);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.RECORDS, 1.0f, 0.8f);
            p.sendActionBar(
                    Component.text("⚠ Caçada começa em " + timeLabel + "! Afaste-se da sua base!", NamedTextColor.RED, TextDecoration.BOLD)
            );
        }
    }

    private void announceHuntStarted() {
        Component header = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.DARK_RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 🎯 A CAÇADA COMEÇOU! (Duração: 30 minutos)", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" Um jogador de cada time foi marcado como PRESA!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text(" As presas estão LUMINOSAS e terão suas coordenadas", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text(" reveladas no chat a cada 3 minutos!", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));

        Bukkit.broadcast(header);

        Title preyTitle = Title.title(
                Component.text("🎯 VOCÊ É A PRESA!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Você está luminoso e suas coords serão reveladas!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(500))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.RECORDS, 1.0f, 0.85f);
            if (isPrey(p.getUniqueId())) {
                p.showTitle(preyTitle);
            }
        }
    }

    /**
     * Envia no chat global as coordenadas atuais de todas as presas ativas.
     */
    public void broadcastPreyCoordinates() {
        if (state != HuntState.ACTIVE || activePreysByTeam.isEmpty()) {
            return;
        }

        int minsLeft = (int) Math.ceil(secondsLeft / 60.0);
        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("📡 [RADAR DA CAÇADA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("Localização das Presas (", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(minsLeft + " min restantes", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("):", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.newline());

        for (UUID preyId : activePreysByTeam.values()) {
            Player prey = Bukkit.getPlayer(preyId);
            Team team = teamManager.getPlayerTeam(preyId);
            if (prey != null && prey.isOnline()) {
                Location loc = prey.getLocation();
                String dim = formatWorldEnvironment(loc.getWorld());
                String coords = "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + " (" + dim + ")";

                msg = msg.append(Component.text(" 🎯 ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                        .append(formatPlayerWithTeam(prey.getName(), team))
                        .append(Component.text(" ➔ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.text(coords, NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.newline());
            }
        }

        Bukkit.broadcast(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, SoundCategory.RECORDS, 1.0f, 1.2f);
        }
    }

    private Component formatPlayerWithTeam(String playerName, Team team) {
        if (team != null) {
            return Component.text("[" + team.getTag() + "] ", team.getColor(), TextDecoration.BOLD)
                    .append(Component.text(playerName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
        }
        return Component.text(playerName, NamedTextColor.WHITE);
    }

    private String formatWorldEnvironment(World world) {
        if (world == null) {
            return "Mundo";
        }
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> world.getName();
        };
    }
}
