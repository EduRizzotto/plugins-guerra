package com.eduardo.teams.ctf;

import com.eduardo.teams.ctf.CtfManager.TeamFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Comando /capturetheflag (alias /ctf) — restrito a administradores (OP / teams.admin).
 * - /capturetheflag                 -> Inicia o evento de 1 hora (ou mostra status se ja estiver ativo)
 * - /capturetheflag iniciar [min]   -> Inicia o evento (padrao 60 minutos = 1 hora)
 * - /capturetheflag status          -> Mostra situacao das bandeiras de cada time
 * - /capturetheflag finalizar       -> Finaliza o evento premiando +200 pts para as bandeiras protegidas
 * - /capturetheflag parar           -> Cancela o evento imediatamente sem premiar o bonus final
 */
public class CtfCommand implements CommandExecutor, TabCompleter {

    private final CtfManager ctfManager;

    public CtfCommand(CtfManager ctfManager) {
        this.ctfManager = ctfManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("🚩 Apenas administradores podem usar o comando /capturetheflag!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (!ctfManager.isActive()) {
                ctfManager.startEvent(CtfManager.DEFAULT_DURATION_SECONDS, sender);
            } else {
                sendStatus(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "iniciar", "start", "agora" -> {
                int durationSeconds = CtfManager.DEFAULT_DURATION_SECONDS;
                if (args.length >= 2) {
                    try {
                        int minutes = Integer.parseInt(args[1]);
                        if (minutes < 1 || minutes > 360) {
                            sender.sendMessage(Component.text("Informe uma duração entre 1 e 360 minutos.", NamedTextColor.RED));
                            return true;
                        }
                        durationSeconds = minutes * 60;
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Component.text("Duração inválida! Use: /capturetheflag iniciar [minutos]", NamedTextColor.RED));
                        return true;
                    }
                }
                ctfManager.startEvent(durationSeconds, sender);
            }
            case "status", "info" -> sendStatus(sender);
            case "finalizar", "encerrar", "finish" -> {
                if (!ctfManager.isActive()) {
                    sender.sendMessage(Component.text("🚩 O evento Capture The Flag não está ativo no momento.", NamedTextColor.RED));
                    return true;
                }
                ctfManager.finishEventWithRewards();
            }
            case "parar", "cancelar", "stop" -> {
                if (!ctfManager.isActive()) {
                    sender.sendMessage(Component.text("🚩 O evento Capture The Flag não está ativo no momento.", NamedTextColor.RED));
                    return true;
                }
                ctfManager.stopEventCancelled();
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        if (!ctfManager.isActive()) {
            sender.sendMessage(Component.text("🚩 O evento Capture The Flag não está ativo. Use /capturetheflag para iniciar!", NamedTextColor.YELLOW));
            return;
        }
        int rem = ctfManager.getRemainingSeconds();
        int mins = rem / 60;
        int secs = rem % 60;
        sender.sendMessage(Component.text("=== 🚩 Status do Capture The Flag ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(" • Tempo restante: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%02d:%02d", mins, secs), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        for (TeamFlag flag : ctfManager.getAllFlags()) {
            Location loc = flag.getFlagBlockLocation();
            String state = switch (flag.getStatus()) {
                case AT_BASE -> "Na Base (X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + ")";
                case CARRIED -> "ROUBADA por " + flag.getCarrierName() + " [" + flag.getCarrierTeamName() + "]";
                case DROPPED -> "CAÍDA NO CHÃO (" + flag.getDroppedRemainingSeconds() + "s p/ voltar)";
                case CAPTURED -> "CAPTURADA por " + flag.getCapturedByTeamName() + " (" + flag.getCapturedByPlayerName() + ")";
            };
            NamedTextColor stateColor = switch (flag.getStatus()) {
                case AT_BASE -> NamedTextColor.GREEN;
                case CARRIED -> NamedTextColor.RED;
                case DROPPED -> NamedTextColor.GOLD;
                case CAPTURED -> NamedTextColor.DARK_RED;
            };
            sender.sendMessage(Component.text(" • " + flag.getTeamName() + ": ", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .append(Component.text(state, stateColor)));
        }
        sender.sendMessage(Component.text(" • Comandos ADM: ", NamedTextColor.GRAY)
                .append(Component.text("/capturetheflag finalizar", NamedTextColor.GREEN))
                .append(Component.text(" (entrega +200 pts) | ", NamedTextColor.GRAY))
                .append(Component.text("/capturetheflag parar", NamedTextColor.RED))
                .append(Component.text(" (cancela)", NamedTextColor.GRAY)));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("🚩 Comandos do Capture The Flag (ADM):", NamedTextColor.GOLD, TextDecoration.BOLD));
        sender.sendMessage(Component.text(" • /capturetheflag", NamedTextColor.YELLOW)
                .append(Component.text(" — Inicia o evento (1 hora) gerando as bandeiras nas bases", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(" • /capturetheflag status", NamedTextColor.YELLOW)
                .append(Component.text(" — Mostra o status de cada bandeira e tempo restante", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(" • /capturetheflag finalizar", NamedTextColor.YELLOW)
                .append(Component.text(" — Encerra o evento premiando +200 pts às bandeiras protegidas", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text(" • /capturetheflag parar", NamedTextColor.YELLOW)
                .append(Component.text(" — Cancela o evento e remove as bandeiras sem bônus final", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> opts = List.of("iniciar", "status", "finalizar", "parar");
            List<String> matches = new ArrayList<>();
            for (String opt : opts) {
                if (opt.startsWith(prefix)) {
                    matches.add(opt);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
