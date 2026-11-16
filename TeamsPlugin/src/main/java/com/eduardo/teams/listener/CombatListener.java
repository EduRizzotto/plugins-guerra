package com.eduardo.teams.listener;

import com.eduardo.teams.manager.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * Impede que jogadores do mesmo time causem dano uns aos outros (fogo amigo)
 * e contabiliza Kills e Mortes em PvP.
 */
public class CombatListener implements Listener {

    private final TeamManager teamManager;

    public CombatListener(TeamManager teamManager) {
        this.teamManager = teamManager;
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
            return;
        }

        // Registra combate PvP para contabilizar caso a vitima morra em seguida (ex: queda, fogo, etc.)
        teamManager.recordPvpDamage(victim.getUniqueId(), attacker.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
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
