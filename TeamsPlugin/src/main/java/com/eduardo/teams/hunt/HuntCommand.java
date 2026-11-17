package com.eduardo.teams.hunt;

import com.eduardo.teams.hunt.HuntManager.HuntState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Comando /cacada (ou /hunt) para OP/admin iniciar, pular a contagem, ver horarios sorteados, anunciar coords ou encerrar.
 */
public class HuntCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("agora", "coords", "horarios", "parar");

    private final HuntManager huntManager;

    public HuntCommand(HuntManager huntManager) {
        this.huntManager = huntManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores (OP) podem controlar o evento de Caçada.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (huntManager.getState() != HuntState.IDLE) {
                sender.sendMessage(
                        Component.text("O evento de Caçada já está em andamento! Use '/cacada agora' para pular os 5 min ou '/cacada parar' para cancelar.", NamedTextColor.YELLOW)
                );
                return true;
            }
            huntManager.startCountdown();
            sender.sendMessage(
                    Component.text("🎯 Contagem regressiva de 5 minutos da Caçada iniciada!", NamedTextColor.GREEN)
            );
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "agora" -> {
                boolean started = huntManager.startHuntImmediately();
                if (started) {
                    sender.sendMessage(
                            Component.text("🎯 Evento de Caçada iniciado imediatamente (30 minutos)!", NamedTextColor.GREEN)
                    );
                } else {
                    sender.sendMessage(
                            Component.text("Não foi possível iniciar a Caçada: é necessário ter pelo menos 1 jogador online em algum time (/team create).", NamedTextColor.RED)
                    );
                }
            }
            case "coords" -> {
                if (huntManager.getState() != HuntState.ACTIVE) {
                    sender.sendMessage(Component.text("A Caçada ainda não está ativa no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                huntManager.broadcastPreyCoordinates();
            }
            case "horarios" -> {
                List<String> schedule = huntManager.getFormattedSchedule();
                sender.sendMessage(
                        Component.text("🎯 Horários secretos sorteados para as 2 Caçadas de hoje: ", NamedTextColor.GOLD)
                                .append(Component.text(String.join(" e ", schedule), NamedTextColor.AQUA))
                );
            }
            case "parar" -> {
                if (huntManager.getState() == HuntState.IDLE) {
                    sender.sendMessage(Component.text("Nenhum evento de Caçada está ativo no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                huntManager.stopHunt(true);
                sender.sendMessage(Component.text("🎯 Evento de Caçada encerrado com sucesso.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(
                    Component.text("Uso: /cacada [agora|coords|horarios|parar] (sem argumentos inicia aviso de 5 min)", NamedTextColor.YELLOW)
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
