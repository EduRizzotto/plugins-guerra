package com.eduardo.teams.hunt;

import com.eduardo.teams.TeamsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener do Evento de Cacada: detecta abate das presas, transfere presa ao quitar e impede remover Glowing com leite.
 */
public class HuntListener implements Listener {

    private final TeamsPlugin plugin;
    private final HuntManager huntManager;

    public HuntListener(TeamsPlugin plugin, HuntManager huntManager) {
        this.plugin = plugin;
        this.huntManager = huntManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreyDeath(PlayerDeathEvent event) {
        Player prey = event.getEntity();
        if (huntManager.isPrey(prey.getUniqueId())) {
            huntManager.handlePreyDeath(prey, prey.getKiller());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreyQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (huntManager.isPrey(player.getUniqueId())) {
            huntManager.handlePreyQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrinkMilk(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() == Material.MILK_BUCKET && huntManager.isPrey(event.getPlayer().getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, huntManager::applyGlowToActivePreys, 2L);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> huntManager.handlePlayerJoin(player), 10L);
    }
}
