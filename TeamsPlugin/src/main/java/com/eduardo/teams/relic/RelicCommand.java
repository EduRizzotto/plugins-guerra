package com.eduardo.teams.relic;

import com.eduardo.teams.relic.RelicManager.RelicState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Comando /reliquia (aliases: /relic, /coroa) para OP/admin iniciar ou encerrar o evento Portador da Reliquia.
 */
public class RelicCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aqui", "ping", "parar");

    private final RelicManager relicManager;

    public RelicCommand(RelicManager relicManager) {
        this.relicManager = relicManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores (OP) podem usar /reliquia.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
            Location loc = relicManager.spawnRandomRelic(world);
            sender.sendMessage(
                    Component.text("👑 Relíquia da Guerra spawnada em " + RelicManager.formatCoords(loc) + "! (Use '/reliquia aqui' para spawnar do seu lado)", NamedTextColor.GREEN)
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aqui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /reliquia aqui.", NamedTextColor.RED));
                    return true;
                }
                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) {
                    dir = new Vector(0, 0, 1);
                } else {
                    dir.normalize().multiply(2.5);
                }
                Location target = player.getLocation().add(dir).toBlockLocation().add(0.5, 0, 0.5);
                Block b = target.getBlock();
                if (!b.getType().isAir()) {
                    target = target.add(0, 1, 0);
                }
                relicManager.spawnRelicAt(target);
                player.sendMessage(Component.text("👑 Relíquia da Guerra spawnada na sua frente!", NamedTextColor.GREEN));
            }
            case "ping" -> {
                if (relicManager.getState() == RelicState.IDLE) {
                    sender.sendMessage(Component.text("O evento Portador da Relíquia não está ativo no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                relicManager.broadcastRelicPing();
            }
            case "parar" -> {
                if (relicManager.getState() == RelicState.IDLE) {
                    sender.sendMessage(Component.text("O evento Portador da Relíquia não está ativo no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                relicManager.stopEvent(true);
                sender.sendMessage(Component.text("👑 Evento Portador da Relíquia encerrado.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(
                    Component.text("Uso: /reliquia [aqui|ping|parar] (sem argumentos spawna aleatório)", NamedTextColor.YELLOW)
            );
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
