package com.eduardo.teams.command;

import com.eduardo.teams.manager.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
import java.util.Set;
import java.util.stream.Stream;

/**
 * Comando /leader <add|remove|list> <nome> (apenas OP/admin):
 * Define quais jogadores possuem permissao para usar /team create e criar uma equipe.
 */
public class LeaderCommand implements CommandExecutor, TabCompleter {

    private final TeamManager teamManager;

    public LeaderCommand(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores podem gerenciar os líderes autorizados!", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("listar")) {
            Set<String> leaders = teamManager.getAuthorizedLeaders();
            if (leaders.isEmpty()) {
                sender.sendMessage(Component.text("Nenhum líder autorizado foi adicionado ainda. Use /leader add <nome>.", NamedTextColor.YELLOW));
                return true;
            }
            sender.sendMessage(Component.text("=== Líderes Autorizados a Criar Time (" + leaders.size() + ") ===", NamedTextColor.GOLD, TextDecoration.BOLD));
            sender.sendMessage(Component.text(String.join(", ", leaders), NamedTextColor.GREEN));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length < 2) {
            sender.sendMessage(Component.text("Uso: /leader <add|remove|list> <nome>", NamedTextColor.YELLOW));
            return true;
        }

        String targetName = sanitizePlayerName(args[1]);
        if (targetName.isEmpty()) {
            sender.sendMessage(Component.text("Nome de jogador inválido!", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "add", "adicionar" -> {
                if (!teamManager.addAuthorizedLeader(targetName)) {
                    sender.sendMessage(Component.text("O jogador '" + targetName + "' já está na lista de líderes autorizados!", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("✔ O jogador ", NamedTextColor.GREEN)
                        .append(Component.text(targetName, NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(" agora pode criar um time com /team create!", NamedTextColor.GREEN)));

                Player onlineTarget = Bukkit.getPlayerExact(targetName);
                if (onlineTarget != null && onlineTarget.isOnline()) {
                    onlineTarget.sendMessage(Component.text("👑 Você recebeu permissão de Líder! Agora você pode criar sua equipe usando /team create <nome> [tag]!", NamedTextColor.GREEN));
                }
            }
            case "remove", "remover", "del" -> {
                if (!teamManager.removeAuthorizedLeader(targetName)) {
                    sender.sendMessage(Component.text("O jogador '" + targetName + "' não estava na lista de líderes autorizados.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("✔ Permissão de criar time removida de ", NamedTextColor.RED)
                        .append(Component.text(targetName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.RED)));
            }
            default -> sender.sendMessage(Component.text("Uso: /leader <add|remove|list> <nome>", NamedTextColor.YELLOW));
        }
        return true;
    }

    private String sanitizePlayerName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\"", "").replace("'", "").replace(",", "").trim();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Stream.of("add", "remove", "list")
                    .filter(opt -> opt.startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("remove") || sub.equals("remover") || sub.equals("del")) {
                return teamManager.getAuthorizedLeaders().stream()
                        .filter(name -> name.startsWith(prefix))
                        .toList();
            }
            if (sub.equals("add") || sub.equals("adicionar")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
        }
        return Collections.emptyList();
    }
}
