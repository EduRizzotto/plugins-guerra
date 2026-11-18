package com.eduardo.teams.dimension;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Comandos /open <nether|end> e /close <nether|end> (apenas OP / admin).
 */
public class DimensionLockCommand implements CommandExecutor, TabCompleter {

    private final DimensionLockManager dimensionManager;
    private final boolean openAction;

    public DimensionLockCommand(DimensionLockManager dimensionManager, boolean openAction) {
        this.dimensionManager = dimensionManager;
        this.openAction = openAction;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores podem usar este comando!", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            String cmdName = openAction ? "/open" : "/close";
            sender.sendMessage(Component.text("Uso: " + cmdName + " <nether|end>", NamedTextColor.YELLOW));
            return true;
        }

        String target = args[0].toLowerCase(Locale.ROOT);
        switch (target) {
            case "nether" -> {
                if (openAction) {
                    if (dimensionManager.isNetherOpen()) {
                        sender.sendMessage(Component.text("O Nether já está liberado!", NamedTextColor.YELLOW));
                        return true;
                    }
                    dimensionManager.setNetherOpen(true, true);
                    sender.sendMessage(Component.text("✔ Você liberou o acesso ao Nether!", NamedTextColor.GREEN));
                } else {
                    if (!dimensionManager.isNetherOpen()) {
                        sender.sendMessage(Component.text("O Nether já está bloqueado!", NamedTextColor.YELLOW));
                        return true;
                    }
                    dimensionManager.setNetherOpen(false, true);
                    sender.sendMessage(Component.text("✔ Você bloqueou o acesso ao Nether!", NamedTextColor.RED));
                }
            }
            case "end", "the_end", "fim" -> {
                if (openAction) {
                    if (dimensionManager.isEndOpen()) {
                        sender.sendMessage(Component.text("O The End já está liberado!", NamedTextColor.YELLOW));
                        return true;
                    }
                    dimensionManager.setEndOpen(true, true);
                    sender.sendMessage(Component.text("✔ Você liberou o acesso ao The End!", NamedTextColor.GREEN));
                } else {
                    if (!dimensionManager.isEndOpen()) {
                        sender.sendMessage(Component.text("O The End já está bloqueado!", NamedTextColor.YELLOW));
                        return true;
                    }
                    dimensionManager.setEndOpen(false, true);
                    sender.sendMessage(Component.text("✔ Você bloqueou o acesso ao The End!", NamedTextColor.RED));
                }
            }
            default -> {
                String cmdName = openAction ? "/open" : "/close";
                sender.sendMessage(Component.text("Dimensão inválida! Use: " + cmdName + " <nether|end>", NamedTextColor.RED));
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("nether", "end")
                    .filter(opt -> opt.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }
}
