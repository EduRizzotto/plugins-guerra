package com.eduardo.teams.dimension;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

/**
 * Gerencia o bloqueio e desbloqueio do Nether e do End:
 * - Por padrao, ambos iniciam BLOQUEADOS (netherOpen = false, endOpen = false).
 * - Podem ser abertos e fechados a qualquer momento com /open <nether|end> e /close <nether|end>.
 * - O estado e salvo em dimensions.yml para persistir entre reinicializacoes.
 */
public class DimensionLockManager {

    private final TeamsPlugin plugin;
    private final File configFile;

    private boolean netherOpen = false;
    private boolean endOpen = false;

    public DimensionLockManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "dimensions.yml");
        loadState();
    }

    public boolean isNetherOpen() {
        return netherOpen;
    }

    public boolean isEndOpen() {
        return endOpen;
    }

    public void setNetherOpen(boolean open, boolean broadcast) {
        this.netherOpen = open;
        saveState();

        if (!open) {
            evacuatePlayersFromDimension(World.Environment.NETHER);
        }

        if (broadcast) {
            if (open) {
                Bukkit.broadcast(Component.empty());
                Bukkit.broadcast(Component.text("==============================================", NamedTextColor.RED, TextDecoration.BOLD));
                Bukkit.broadcast(Component.text("      🔥 O NETHER FOI LIBERADO! 🔥", NamedTextColor.GOLD, TextDecoration.BOLD));
                Bukkit.broadcast(Component.text("  Os portais para o Nether agora estão abertos", NamedTextColor.YELLOW));
                Bukkit.broadcast(Component.text("  para todas as equipes do servidor!", NamedTextColor.YELLOW));
                Bukkit.broadcast(Component.text("==============================================", NamedTextColor.RED, TextDecoration.BOLD));
                Bukkit.broadcast(Component.empty());

                Title title = Title.title(
                        Component.text("🔥 NETHER LIBERADO! 🔥", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.text("Os portais do Nether agora estão abertos!", NamedTextColor.GOLD),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))
                );
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showTitle(title);
                    online.playSound(online.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, SoundCategory.MASTER, 0.7f, 1.0f);
                }
            } else {
                Bukkit.broadcast(Component.empty());
                Bukkit.broadcast(Component.text("🔒 O acesso ao ", NamedTextColor.RED)
                        .append(Component.text("NETHER", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(" foi BLOQUEADO pelos administradores!", NamedTextColor.RED)));
                Bukkit.broadcast(Component.empty());

                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.playSound(online.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.MASTER, 1.0f, 0.8f);
                }
            }
        }
    }

    public void setEndOpen(boolean open, boolean broadcast) {
        this.endOpen = open;
        saveState();

        if (!open) {
            evacuatePlayersFromDimension(World.Environment.THE_END);
        }

        if (broadcast) {
            if (open) {
                Bukkit.broadcast(Component.empty());
                Bukkit.broadcast(Component.text("==============================================", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                Bukkit.broadcast(Component.text("      🌌 O THE END FOI LIBERADO! 🌌", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                Bukkit.broadcast(Component.text("  O Portal do Fim agora está aberto", NamedTextColor.YELLOW));
                Bukkit.broadcast(Component.text("  para todas as equipes do servidor!", NamedTextColor.YELLOW));
                Bukkit.broadcast(Component.text("==============================================", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                Bukkit.broadcast(Component.empty());

                Title title = Title.title(
                        Component.text("🌌 THE END LIBERADO! 🌌", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                        Component.text("O Portal do Fim agora está aberto!", NamedTextColor.YELLOW),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))
                );
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.showTitle(title);
                    online.playSound(online.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.8f, 1.0f);
                }
            } else {
                Bukkit.broadcast(Component.empty());
                Bukkit.broadcast(Component.text("🔒 O acesso ao ", NamedTextColor.RED)
                        .append(Component.text("THE END", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                        .append(Component.text(" foi BLOQUEADO pelos administradores!", NamedTextColor.RED)));
                Bukkit.broadcast(Component.empty());

                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.playSound(online.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.MASTER, 1.0f, 0.8f);
                }
            }
        }
    }

    private void evacuatePlayersFromDimension(World.Environment env) {
        World overworld = getDefaultOverworld();
        if (overworld == null) {
            return;
        }
        Location fallbackSpawn = overworld.getSpawnLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getEnvironment() == env) {
                Location target = player.getRespawnLocation();
                if (target == null || target.getWorld() == null || target.getWorld().getEnvironment() == env) {
                    target = fallbackSpawn;
                }
                player.teleport(target);
                player.sendMessage(Component.text("🔒 Esta dimensão foi bloqueada! Você foi enviado de volta ao Overworld.", NamedTextColor.YELLOW));
            }
        }
    }

    private World getDefaultOverworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private void loadState() {
        if (!configFile.exists()) {
            this.netherOpen = false;
            this.endOpen = false;
            saveState();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        this.netherOpen = yaml.getBoolean("nether-open", false);
        this.endOpen = yaml.getBoolean("end-open", false);
    }

    public void saveState() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("nether-open", this.netherOpen);
        yaml.set("end-open", this.endOpen);
        try {
            yaml.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Nao foi possivel salvar dimensions.yml: " + e.getMessage());
        }
    }
}
