package com.eduardo.teams.netherstorm;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Comando /tempestadenether (aliases: /netherstorm, /fendanether) restrito a OP/admin:
 * - /tempestadenether        -> Invoca a Tempestade do Nether em coordenada aleatoria do mapa
 * - /tempestadenether aqui   -> Invoca a Tempestade do Nether na frente do ADM
 * - /tempestadenether status -> Mostra HP do Cristal Infernal, tempo e coordenadas
 * - /tempestadenether parar  -> Encerra o evento e restaura 100% do terreno original
 */
public class NetherStormCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aqui", "status", "parar");

    private final NetherStormManager stormManager;

    public NetherStormCommand(NetherStormManager stormManager) {
        this.stormManager = stormManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("🔥 Apenas administradores (OP) podem usar /tempestadenether!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
            Location loc = stormManager.startRandomStorm(world);
            sender.sendMessage(Component.text("🔥 Tempestade do Nether invocada em " + NetherStormManager.formatCoords(loc) + "!", NamedTextColor.RED, TextDecoration.BOLD));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aqui", "here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /tempestadenether aqui.", NamedTextColor.RED));
                    return true;
                }
                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) {
                    dir = new Vector(1, 0, 0);
                } else {
                    dir.normalize();
                }
                double axisAngle = Math.atan2(dir.getZ(), dir.getX());
                // Posiciona o centro do X 28 blocos a frente do ADM
                Location target = player.getLocation().add(dir.clone().multiply(28)).toBlockLocation();
                stormManager.startStormAt(target, axisAngle);
                player.sendMessage(Component.text("🔥 Ravina da Tempestade do Nether em formato de X (200 blocos) rasgada na sua frente!", NamedTextColor.RED, TextDecoration.BOLD));
            }
            case "status", "info" -> {
                if (!stormManager.isActive()) {
                    sender.sendMessage(Component.text("🔥 Nenhuma Tempestade do Nether ativa no momento. Use /tempestadenether ou /tempestadenether aqui para iniciar.", NamedTextColor.YELLOW));
                    return true;
                }
                int rem = stormManager.getSecondsRemaining();
                sender.sendMessage(Component.text("=== 🔥 Status da Tempestade do Nether ===", NamedTextColor.RED, TextDecoration.BOLD));
                sender.sendMessage(Component.text(" • Coordenadas: ", NamedTextColor.GRAY)
                        .append(Component.text(NetherStormManager.formatCoords(stormManager.getBaseCenter()), NamedTextColor.YELLOW, TextDecoration.BOLD)));
                sender.sendMessage(Component.text(" • Tempo restante: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%02d:%02d", rem / 60, rem % 60), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text(" • Cristal Infernal: ", NamedTextColor.GRAY)
                        .append(Component.text(stormManager.getCrystalHp() + " / " + NetherStormManager.CRYSTAL_MAX_HP + " HP", NamedTextColor.GOLD, TextDecoration.BOLD)));
            }
            case "parar", "stop", "cancelar" -> {
                if (!stormManager.isActive()) {
                    sender.sendMessage(Component.text("🔥 Não há nenhuma Tempestade do Nether ativa no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                stormManager.stopStorm(true);
                Bukkit.broadcast(Component.text("🔥 A Tempestade do Nether foi encerrada por um administrador e o terreno foi restaurado.", NamedTextColor.RED));
            }
            default -> sender.sendMessage(Component.text("Uso: /tempestadenether [aqui|status|parar] (sem argumentos invoca aleatório no mapa)", NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> matches = new ArrayList<>();
            for (String s : SUBCOMMANDS) {
                if (s.startsWith(prefix)) {
                    matches.add(s);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
