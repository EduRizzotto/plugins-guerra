package com.eduardo.teams.airdrop;

import com.eduardo.teams.airdrop.AirDropManager.ActiveAirDrop;
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
 * Comando /airdrop (exclusivo para OP / teams.admin) para testar e gerenciar as Caixas de Suprimentos.
 */
public class AirDropCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aqui", "destrancar", "limpar");

    private final AirDropManager airDropManager;

    public AirDropCommand(AirDropManager airDropManager) {
        this.airDropManager = airDropManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores (OP) podem usar /airdrop.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
            ActiveAirDrop drop = airDropManager.spawnRandomAirDrop(world);
            if (drop != null) {
                sender.sendMessage(
                        Component.text("📦 AirDrop spawnado aleatoriamente em " + AirDropManager.formatCoords(drop.getBlockLocation()) + "!", NamedTextColor.GREEN)
                );
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aqui" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /airdrop aqui.", NamedTextColor.RED));
                    return true;
                }
                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) {
                    dir = new Vector(0, 0, 1);
                } else {
                    dir.normalize().multiply(2);
                }
                Location target = player.getLocation().add(dir).toBlockLocation();
                Block ground = target.getBlock();
                if (!ground.getType().isAir()) {
                    target = target.add(0, 1, 0);
                }
                ActiveAirDrop drop = airDropManager.spawnAirDropAt(target);
                if (drop != null) {
                    player.sendMessage(
                            Component.text("📦 AirDrop spawnado na sua frente em " + AirDropManager.formatCoords(drop.getBlockLocation()) + "!", NamedTextColor.GREEN)
                    );
                }
            }
            case "destrancar" -> {
                var active = airDropManager.getActiveDropsCount().drops();
                if (active.isEmpty()) {
                    sender.sendMessage(Component.text("Nao ha nenhum AirDrop ativo no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                int unlockedCount = 0;
                for (ActiveAirDrop drop : active) {
                    if (!drop.isUnlocked()) {
                        while (drop.getSecondsUntilUnlock() > 1) {
                            drop.decrementUnlockTimer();
                        }
                        unlockedCount++;
                    }
                }
                sender.sendMessage(
                        Component.text("🔓 Destrancando " + unlockedCount + " AirDrop(s) ativo(s) imediatamente!", NamedTextColor.GREEN)
                );
            }
            case "limpar" -> {
                int removed = airDropManager.clearAllDrops();
                sender.sendMessage(
                        Component.text("🗑 " + removed + " AirDrop(s) removido(s) do mapa.", NamedTextColor.YELLOW)
                );
            }
            default -> sender.sendMessage(
                    Component.text("Uso: /airdrop [aqui|destrancar|limpar] (sem argumentos spawna aleatório)", NamedTextColor.YELLOW)
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
