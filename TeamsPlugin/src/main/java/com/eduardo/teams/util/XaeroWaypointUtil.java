package com.eduardo.teams.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Utilitario para envio de waypoints e modo Fair-Play compativeis com Xaero's Minimap / World Map.
 */
public final class XaeroWaypointUtil {

    // Codigos de chat invisiveis que ativam o Fair-Play Mode no Xaero's Minimap
    // (bloqueia radar de jogadores/entidades e Cave Mode, mantendo minimapa de superficie e waypoints)
    public static final String XAERO_COMBINED_FAIR_PLAY_CODE =
            "§f§a§i§r§x§a§e§r§o§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r";

    // Indices de cores do Xaero's Minimap
    public static final int COLOR_DARK_RED = 4;
    public static final int COLOR_GOLD = 6;
    public static final int COLOR_GREEN = 10;
    public static final int COLOR_AQUA = 11;
    public static final int COLOR_RED = 12;
    public static final int COLOR_YELLOW = 14;

    private XaeroWaypointUtil() {
    }

    /**
     * Anexa o codigo invisivel do Xaero Fair-Play diretamente ao Component (ex: joinMessage)
     * sem gerar nenhuma linha vazia ou barra extra no chat.
     */
    public static Component appendFairPlayCode(Component base) {
        if (base == null) {
            return null;
        }
        return base.append(Component.text(XAERO_COMBINED_FAIR_PLAY_CODE));
    }

    /**
     * Envia para todos os jogadores online a linha no protocolo nativo do Xaero's Minimap:
     * xaero-waypoint:Name:Initial:X:Y:Z:Color:false:0:Internal-dim%minecraft$overworld-waypoints
     */
    public static void broadcastWaypoint(String name, String initial, Location loc, int colorIndex) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        String raw = buildXaeroString(name, initial, loc, colorIndex);
        Bukkit.broadcast(Component.text(raw));
    }

    public static String buildXaeroString(String name, String initial, Location loc, int colorIndex) {
        String safeName = sanitizeToken(name, "Evento");
        String safeInitial = sanitizeToken(initial, "E");
        if (safeInitial.length() > 2) {
            safeInitial = safeInitial.substring(0, 2);
        }

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String dimToken = resolveDimensionToken(loc.getWorld());

        return "xaero-waypoint:" + safeName
                + ":" + safeInitial
                + ":" + x
                + ":" + y
                + ":" + z
                + ":" + colorIndex
                + ":false:0:" + dimToken;
    }

    private static String resolveDimensionToken(World world) {
        if (world == null) {
            return "Internal-dim%minecraft$overworld-waypoints";
        }
        return switch (world.getEnvironment()) {
            case NETHER -> "Internal-dim%minecraft$the_nether-waypoints";
            case THE_END -> "Internal-dim%minecraft$the_end-waypoints";
            default -> "Internal-dim%minecraft$overworld-waypoints";
        };
    }

    private static String sanitizeToken(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String cleaned = input.replace(":", "").trim();
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
