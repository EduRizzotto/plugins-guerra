package com.eduardo.teams.doom;

import com.eduardo.teams.doom.DoomManager.DoomVariant;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Comandos /doom, /doom2 e /doom3 (apenas OP / admin):
 * - /doom   -> Gera a Fortaleza DOOM I (Cidadela Infernal) entre 5000 e 6000 blocos de raio do spawn
 * - /doom2  -> Gera a Fortaleza DOOM II (Abismo Corrompido) entre 5000 e 6000 blocos de raio do spawn
 * - /doom3  -> Gera a Fortaleza DOOM III (Templo do Sol Sangrento) entre 5000 e 6000 blocos de raio do spawn
 * - Subcomandos em qualquer um deles: [aqui|tp|abrir|parar]
 */
public class DoomCommand implements CommandExecutor, TabCompleter {

    private final DoomManager doomManager;

    public DoomCommand(DoomManager doomManager) {
        this.doomManager = doomManager;
    }

    private DoomVariant resolveVariantFromCommand(Command command, String label) {
        String key = label.toLowerCase(Locale.ROOT);
        if (key.contains("3") || command.getName().equalsIgnoreCase("doom3")) {
            return DoomVariant.DOOM_3;
        }
        if (key.contains("2") || command.getName().equalsIgnoreCase("doom2")) {
            return DoomVariant.DOOM_2;
        }
        return DoomVariant.DOOM_1;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores podem iniciar ou controlar o evento /" + label + "!", NamedTextColor.RED));
            return true;
        }

        DoomVariant variant = resolveVariantFromCommand(command, label);

        if (args.length == 0) {
            World world = (sender instanceof Player p) ? p.getWorld() : getDefaultOverworld();
            if (world == null) {
                sender.sendMessage(Component.text("Mundo Overworld não encontrado!", NamedTextColor.RED));
                return true;
            }
            doomManager.startRandomDoom(world, sender, variant);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "aqui", "here" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /" + label + " aqui.", NamedTextColor.RED));
                    return true;
                }
                Location targetEntrance = player.getLocation().toBlockLocation().add(0, 0, 10);
                doomManager.startDoomAt(targetEntrance, sender, variant);
            }
            case "tp", "teleport" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar /doom tp.", NamedTextColor.RED));
                    return true;
                }
                if (!doomManager.isActive()) {
                    player.sendMessage(Component.text("Não há nenhuma Fortaleza DOOM ativa no momento.", NamedTextColor.RED));
                    return true;
                }
                if (args.length >= 2 && (args[1].equalsIgnoreCase("bau") || args[1].equalsIgnoreCase("final"))) {
                    Location chestLoc = doomManager.getChestLocation();
                    if (chestLoc != null) {
                        player.teleport(chestLoc.clone().add(0.5, -3.0, -3.5));
                        player.sendMessage(Component.text("Teleportado para o Santuário Final da Fortaleza DOOM!", NamedTextColor.GREEN));
                    }
                    return true;
                }
                Location entrance = doomManager.getEntranceLocation();
                if (entrance != null) {
                    player.teleport(entrance.clone().add(0, 0, -2.0));
                    player.sendMessage(Component.text("Teleportado para a Entrada Única da Fortaleza DOOM!", NamedTextColor.GREEN));
                }
            }
            case "abrir", "open", "destrancar" -> {
                if (!doomManager.isActive()) {
                    sender.sendMessage(Component.text("Não há nenhuma Fortaleza DOOM ativa no momento.", NamedTextColor.RED));
                    return true;
                }
                if (doomManager.isGateOpened()) {
                    sender.sendMessage(Component.text("O Portão Principal da Fortaleza DOOM já está aberto!", NamedTextColor.YELLOW));
                    return true;
                }
                doomManager.openFrontGate();
                sender.sendMessage(Component.text("Portão Principal da Fortaleza DOOM aberto imediatamente!", NamedTextColor.GREEN));
            }
            case "parar", "stop", "limpar", "cancelar" -> {
                if (!doomManager.isActive() && !doomManager.isGenerating()) {
                    sender.sendMessage(Component.text("Não há nenhuma Fortaleza DOOM ativa no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                doomManager.stopDoom(true);
                sender.sendMessage(Component.text("Fortaleza DOOM encerrada e terreno original restaurado!", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Uso: /doom [aqui|tp|parar]", NamedTextColor.YELLOW));
        }
        return true;
    }

    private World getDefaultOverworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("aqui", "tp", "abrir", "parar")
                    .filter(opt -> opt.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("entrada", "bau")
                    .filter(opt -> opt.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}
