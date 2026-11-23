package com.eduardo.teams.endstorm;

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
 * Comando /tempestadeend (aliases: /endstorm, /fendaend, /tempestade) restrito a OP/admin:
 * - /tempestadeend        -> Invoca a Tempestade do End em coordenada aleatoria do mapa
 * - /tempestadeend aqui   -> Invoca a Tempestade do End na frente do ADM
 * - /tempestadeend status -> Mostra HP do Cristal da Fenda, tempo e coordenadas
 * - /tempestadeend parar  -> Encerra o evento e restaura 100% do terreno original
 */
public class EndStormCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("aqui", "status", "parar");

    private final EndStormManager stormManager;

    public EndStormCommand(EndStormManager stormManager) {
        this.stormManager = stormManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("🌌 Apenas administradores (OP) podem usar /tempestadeend!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            World world = sender instanceof Player p ? p.getWorld() : Bukkit.getWorlds().getFirst();
            Location loc = stormManager.startRandomStorm(world);
            sender.sendMessage(Component.text("🌌 Tempestade do End invocada em " + EndStormManager.formatCoords(loc) + "!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aqui", "here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /tempestadeend aqui.", NamedTextColor.RED));
                    return true;
                }
                Vector dir = player.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.001) {
                    dir = new Vector(1, 0, 0);
                } else {
                    dir.normalize();
                }
                double axisAngle = Math.atan2(dir.getZ(), dir.getX());
                // Posiciona o centro da ravina 44 blocos a frente para que a ponta da fenda de 80 blocos comece a ~4 blocos dos pes do ADM
                Location target = player.getLocation().add(dir.clone().multiply(EndStormManager.RAVINE_HALF_LENGTH + 4)).toBlockLocation();
                stormManager.startStormAt(target, axisAngle);
                player.sendMessage(Component.text("🌌 Ravina da Tempestade do End (80 blocos) rasgada na sua frente!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            }
            case "status", "info" -> {
                if (!stormManager.isActive()) {
                    sender.sendMessage(Component.text("🌌 Nenhuma Tempestade do End ativa no momento. Use /tempestadeend ou /tempestadeend aqui para iniciar.", NamedTextColor.YELLOW));
                    return true;
                }
                int rem = stormManager.getSecondsRemaining();
                sender.sendMessage(Component.text("=== 🌌 Status da Tempestade do End ===", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
                sender.sendMessage(Component.text(" • Coordenadas: ", NamedTextColor.GRAY)
                        .append(Component.text(EndStormManager.formatCoords(stormManager.getBaseCenter()), NamedTextColor.YELLOW, TextDecoration.BOLD)));
                sender.sendMessage(Component.text(" • Tempo restante: ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%02d:%02d", rem / 60, rem % 60), NamedTextColor.WHITE)));
                sender.sendMessage(Component.text(" • Cristal da Fenda: ", NamedTextColor.GRAY)
                        .append(Component.text(stormManager.getCrystalHp() + " / " + EndStormManager.CRYSTAL_MAX_HP + " HP", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)));
            }
            case "parar", "stop", "cancelar" -> {
                if (!stormManager.isActive()) {
                    sender.sendMessage(Component.text("🌌 Não há nenhuma Tempestade do End ativa no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                stormManager.stopStorm(true);
                Bukkit.broadcast(Component.text("🌌 A Tempestade do End foi encerrada por um administrador e o terreno foi restaurado.", NamedTextColor.LIGHT_PURPLE));
            }
            default -> sender.sendMessage(Component.text("Uso: /tempestadeend [aqui|status|parar] (sem argumentos invoca aleatório no mapa)", NamedTextColor.YELLOW));
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
