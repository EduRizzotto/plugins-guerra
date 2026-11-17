package com.eduardo.teams.koth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
 * Comando /dominio (aliases: /torre, /koth) para OP/admin spawnar ou encerrar a Torre de Dominio.
 */
public class KothCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aqui", "parar");

    private final KothManager kothManager;

    public KothCommand(KothManager kothManager) {
        this.kothManager = kothManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores (OP) podem usar /dominio.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
            Location loc = kothManager.startRandomKoth(world);
            sender.sendMessage(
                    Component.text("🏰 Torre de Domínio spawnada em " + KothManager.formatCoords(loc) + "! (Use '/dominio aqui' para criar na sua frente)", NamedTextColor.GREEN)
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aqui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /dominio aqui.", NamedTextColor.RED));
                    return true;
                }
                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) {
                    dir = new Vector(0, 0, 1);
                } else {
                    dir.normalize().multiply(10); // 10 blocos a frente pois a torre tem raio 6 (13x13)
                }
                Location base = player.getLocation().add(dir).toBlockLocation();
                kothManager.startKothAt(base);
                player.sendMessage(Component.text("🏰 Torre de Domínio construída na sua frente!", NamedTextColor.GREEN));
            }
            case "parar" -> {
                if (!kothManager.isActive()) {
                    sender.sendMessage(Component.text("Não há nenhuma Torre de Domínio ativa no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                kothManager.stopKoth(true);
                sender.sendMessage(Component.text("🏰 Torre de Domínio removida e terreno restaurado!", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(
                    Component.text("Uso: /dominio [aqui|parar] (sem argumentos spawna aleatório)", NamedTextColor.YELLOW)
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
