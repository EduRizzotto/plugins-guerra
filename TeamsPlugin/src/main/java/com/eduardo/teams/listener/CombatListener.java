package com.eduardo.teams.listener;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Impede fogo amigo entre membros do mesmo time, contabiliza Kills/Mortes/Pontos em PvP
 * e aplica sistema Anti-Combat Log (15 segundos: desconectar em combate mata o jogador automaticamente).
 */
public class CombatListener implements Listener {

    private final TeamManager teamManager;
    private BukkitTask combatActionBarTask;
    private boolean shuttingDown = false;

    public CombatListener(TeamsPlugin plugin, TeamManager teamManager) {
        this.teamManager = teamManager;
        this.combatActionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickCombatActionBars, 20L, 20L);
    }

    public void shutdown() {
        this.shuttingDown = true;
        if (this.combatActionBarTask != null) {
            this.combatActionBarTask.cancel();
            this.combatActionBarTask = null;
        }
    }

    private void tickCombatActionBars() {
        for (UUID playerId : teamManager.getTrackedCombatPlayerIds()) {
            int remaining = teamManager.getCombatRemainingSeconds(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (remaining > 0) {
                player.sendActionBar(Component.text("⚔ Em combate PvP: ", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text(remaining + "s", NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" — Não desconecte!", NamedTextColor.GRAY)));
            } else {
                player.sendActionBar(Component.text("✔ Você saiu de combate PvP.", NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }

        if (teamManager.isSameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            attacker.sendActionBar(
                    Component.text("Voce nao pode atacar membros do seu proprio time!", NamedTextColor.RED)
            );
        }
    }

    /**
     * Roda em MONITOR (ignoreCancelled = true) para so ativar a tag de combate de 15s
     * quando o golpe realmente aconteceu (ex: fora de /safezone e nao sendo fogo amigo).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamageConfirmed(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (teamManager.isSameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (victim.getGameMode() == GameMode.CREATIVE || victim.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Marca AMBOS em combate PvP (15s) para impedir que qualquer um dos lados quite para fugir
        teamManager.recordPvpDamage(victim.getUniqueId(), attacker.getUniqueId());
        if (attacker.getGameMode() != GameMode.CREATIVE && attacker.getGameMode() != GameMode.SPECTATOR) {
            teamManager.recordPvpDamage(attacker.getUniqueId(), victim.getUniqueId());
        }

        Component bar = Component.text("⚔ Em combate PvP: ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("15s", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" — Não desconecte!", NamedTextColor.GRAY));
        victim.sendActionBar(bar);
        if (attacker.getGameMode() != GameMode.CREATIVE && attacker.getGameMode() != GameMode.SPECTATOR) {
            attacker.sendActionBar(bar);
        }
    }

    /**
     * Anti-Combat Log: se o jogador desconectar durante os 15s de combate PvP,
     * ele morre automaticamente ao sair (dropa todos os itens e contabiliza +20/-10 pontos).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCombatLogQuit(PlayerQuitEvent event) {
        Player quitter = event.getPlayer();
        if (shuttingDown || Bukkit.isStopping()) {
            teamManager.clearCombatTag(quitter.getUniqueId());
            return;
        }
        if (quitter.isDead()
                || quitter.getGameMode() == GameMode.CREATIVE
                || quitter.getGameMode() == GameMode.SPECTATOR) {
            teamManager.clearCombatTag(quitter.getUniqueId());
            return;
        }

        UUID opponentId = teamManager.getRecentPvpAttacker(quitter.getUniqueId());
        if (opponentId == null || opponentId.equals(quitter.getUniqueId())) {
            return;
        }

        Team quitterTeam = teamManager.getPlayerTeam(quitter.getUniqueId());
        Team opponentTeam = teamManager.getPlayerTeam(opponentId);
        OfflinePlayer opponentOffline = Bukkit.getOfflinePlayer(opponentId);
        String opponentName = opponentOffline.getName() != null ? opponentOffline.getName() : "rival";

        Component quitterTag = (quitterTeam != null) ? quitterTeam.getFormattedTag().append(Component.text(" ")) : Component.empty();
        Component opponentTag = (opponentTeam != null) ? opponentTeam.getFormattedTag().append(Component.text(" ")) : Component.empty();

        Bukkit.broadcast(Component.text("⚠ COMBAT LOG! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(quitterTag)
                .append(Component.text(quitter.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" desconectou em combate contra ", NamedTextColor.RED))
                .append(opponentTag)
                .append(Component.text(opponentName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" e morreu automaticamente!", NamedTextColor.RED)));

        // Mata o jogador sincronamente: dropa todos os itens e dispara PlayerDeathEvent
        // (que contabiliza os +20 pts / -10 pts e kills/mortes automaticamente via onPlayerDeath)
        quitter.setHealth(0.0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        Player directKiller = victim.getKiller();
        UUID taggedAttacker = teamManager.consumeRecentPvpAttacker(victim.getUniqueId());

        UUID killerId = (directKiller != null && !directKiller.equals(victim))
                ? directKiller.getUniqueId()
                : taggedAttacker;

        // Contabiliza apenas se a morte foi causada por um jogador ou durante combate PvP
        if (killerId != null && !killerId.equals(victim.getUniqueId())) {
            teamManager.recordPvpKillAndDeath(killerId, victim.getUniqueId());
            teamManager.clearCombatTagIfOpponent(killerId, victim.getUniqueId());
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
