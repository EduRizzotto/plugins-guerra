package com.eduardo.teams.zone;

import com.eduardo.teams.zone.ZoneManager.ProtectedZone;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Comandos estilo /fill para proteger areas por coordenadas:
 * - /proteger <x1> <y1> <z1> <x2> <y2> <z2> [nome] -> Bloqueia quebrar e colocar blocos (PvP liberado)
 * - /safezone <x1> <y1> <z1> <x2> <y2> <z2> [nome] -> Bloqueia quebrar, colocar blocos e PvP
 * Subcomandos: listar, remover <nome>, limpar
 */
public class ZoneCommand implements CommandExecutor, TabCompleter {

    private final ZoneManager zoneManager;
    private final boolean blockPvpMode;

    public ZoneCommand(ZoneManager zoneManager, boolean blockPvpMode) {
        this.zoneManager = zoneManager;
        this.blockPvpMode = blockPvpMode;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores (OP) podem gerenciar áreas protegidas!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("listar") || first.equals("list")) {
            listZones(sender);
            return true;
        }

        if (first.equals("remover") || first.equals("remove") || first.equals("del") || first.equals("delete")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Uso: /" + label + " remover <nome>", NamedTextColor.YELLOW));
                return true;
            }
            ProtectedZone removed = zoneManager.removeZone(args[1]);
            if (removed == null) {
                sender.sendMessage(Component.text("Área protegida '" + args[1] + "' não encontrada! Use /" + label + " listar.", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(Component.text("✔ Área protegida '", NamedTextColor.GREEN)
                    .append(Component.text(removed.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("' removida com sucesso!", NamedTextColor.GREEN)));
            return true;
        }

        if (first.equals("limpar") || first.equals("clear")) {
            int count = zoneManager.clearZonesByType(blockPvpMode);
            String tipo = blockPvpMode ? "Safezones (Sem Blocos + Sem PvP)" : "Áreas Protegidas (Sem Blocos)";
            sender.sendMessage(Component.text("🗑 " + count + " " + tipo + " removida(s)!", NamedTextColor.YELLOW));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores podem definir áreas por coordenadas no mundo atual.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 6) {
            sendUsage(sender, label);
            return true;
        }

        try {
            int x1 = parseCoord(args[0], player.getLocation().getBlockX());
            int y1 = parseCoord(args[1], player.getLocation().getBlockY());
            int z1 = parseCoord(args[2], player.getLocation().getBlockZ());
            int x2 = parseCoord(args[3], player.getLocation().getBlockX());
            int y2 = parseCoord(args[4], player.getLocation().getBlockY());
            int z2 = parseCoord(args[5], player.getLocation().getBlockZ());

            String zoneName = (args.length >= 7) ? sanitizeName(args[6]) : zoneManager.generateNextName(blockPvpMode);
            if (zoneName.isEmpty()) {
                zoneName = zoneManager.generateNextName(blockPvpMode);
            }

            ProtectedZone zone = new ProtectedZone(
                    zoneName,
                    player.getWorld().getName(),
                    x1, y1, z1, x2, y2, z2,
                    blockPvpMode
            );
            zoneManager.addZone(zone);

            if (blockPvpMode) {
                player.sendMessage(Component.text("🛡 Safezone '", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(Component.text(zone.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.text("' criada! (Sem quebrar, sem colocar blocos e SEM PvP)", NamedTextColor.GREEN)));
            } else {
                player.sendMessage(Component.text("🔒 Área Protegida '", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text(zone.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text("' criada! (Sem quebrar e sem colocar blocos | PvP liberado)", NamedTextColor.GOLD)));
            }

            player.sendMessage(Component.text("   📍 De (", NamedTextColor.GRAY)
                    .append(Component.text(zone.getMinX() + ", " + zone.getMinY() + ", " + zone.getMinZ(), NamedTextColor.WHITE))
                    .append(Component.text(") até (", NamedTextColor.GRAY))
                    .append(Component.text(zone.getMaxX() + ", " + zone.getMaxY() + ", " + zone.getMaxZ(), NamedTextColor.WHITE))
                    .append(Component.text(") — " + zone.getVolume() + " blocos no mundo " + zone.getWorldName(), NamedTextColor.GRAY)));
            player.sendMessage(Component.text("   ℹ OPs no modo Criativo ainda podem editar a área. Para remover: /" + label + " remover " + zone.getName(), NamedTextColor.DARK_GRAY));
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Coordenadas inválidas! Use números inteiros ou '~' (ex: /" + label + " 100 60 100 150 90 150 lobby).", NamedTextColor.RED));
        }

        return true;
    }

    private void listZones(CommandSender sender) {
        Collection<ProtectedZone> all = zoneManager.getZones();
        if (all.isEmpty()) {
            sender.sendMessage(Component.text("Nenhuma área protegida cadastrada no momento.", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("=== Áreas Protegidas (" + all.size() + ") ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (ProtectedZone z : all) {
            Component tag = z.isBlockPvp()
                    ? Component.text("[🛡 SAFEZONE - Sem Blocos/PvP] ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    : Component.text("[🔒 PROTEGIDA - Sem Blocos] ", NamedTextColor.YELLOW, TextDecoration.BOLD);
            sender.sendMessage(tag
                    .append(Component.text(z.getName(), NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(" (" + z.getWorldName() + "): ", NamedTextColor.GRAY))
                    .append(Component.text(z.getMinX() + "," + z.getMinY() + "," + z.getMinZ()
                            + " -> " + z.getMaxX() + "," + z.getMaxY() + "," + z.getMaxZ(), NamedTextColor.WHITE)));
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        if (blockPvpMode) {
            sender.sendMessage(Component.text("🛡 Safezone (Proíbe colocar blocos, quebrar blocos e PvP):", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            sender.sendMessage(Component.text("🔒 Proteção de Área (Proíbe colocar e quebrar blocos):", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        sender.sendMessage(Component.text(" • /" + label + " <x1> <y1> <z1> <x2> <y2> <z2> [nome]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" • /" + label + " listar", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" • /" + label + " remover <nome>", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text(" • /" + label + " limpar", NamedTextColor.YELLOW));
    }

    private int parseCoord(String input, int base) {
        if (input.startsWith("~")) {
            if (input.length() == 1) {
                return base;
            }
            return base + Integer.parseInt(input.substring(1));
        }
        return Integer.parseInt(input);
    }

    private String sanitizeName(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 0) {
            return Collections.emptyList();
        }

        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender instanceof Player player) {
                suggestions.add(String.valueOf(getSuggestedCoord(player, 0)));
                suggestions.add("~");
            }
            suggestions.add("listar");
            suggestions.add("remover");
            suggestions.add("limpar");
            return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(current)).toList();
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("remover") || first.equals("remove") || first.equals("del") || first.equals("delete")) {
            if (args.length == 2) {
                return zoneManager.getZones().stream()
                        .map(ProtectedZone::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(current))
                        .toList();
            }
            return Collections.emptyList();
        }

        if (first.equals("listar") || first.equals("list") || first.equals("limpar") || first.equals("clear")) {
            return Collections.emptyList();
        }

        if (sender instanceof Player player && args.length >= 2 && args.length <= 6) {
            int axis = (args.length - 1) % 3; // 0=x, 1=y, 2=z
            String coord = String.valueOf(getSuggestedCoord(player, axis));
            List<String> out = new ArrayList<>();
            if (coord.startsWith(current)) {
                out.add(coord);
            }
            if ("~".startsWith(current)) {
                out.add("~");
            }
            return out;
        }

        if (args.length == 7) {
            String suggested = blockPvpMode ? "lobby" : "arena";
            return suggested.startsWith(current) ? List.of(suggested) : Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private int getSuggestedCoord(Player player, int axis) {
        Block target = player.getTargetBlockExact(6);
        if (target != null) {
            return switch (axis) {
                case 0 -> target.getX();
                case 1 -> target.getY();
                default -> target.getZ();
            };
        }
        return switch (axis) {
            case 0 -> player.getLocation().getBlockX();
            case 1 -> player.getLocation().getBlockY();
            default -> player.getLocation().getBlockZ();
        };
    }
}
