package com.eduardo.teams.aura;

import com.eduardo.teams.aura.AuraManager.AuraColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Executor e TabCompleter do comando /aura:
 * - /aura                                    -> Abre o painel de Aura do jogador (com botao de ADM para OPs)
 * - /aura <cor|on|off>                       -> Equipa uma cor liberada para o jogador ou liga/desliga a propria aura
 * - /aura permission (ou /aura admin)        -> Abre a GUI de jogadores para o ADM selecionar quem tera aura e qual cor
 * - /aura <jogador>                          -> (ADM) Abre direto o menu de cores de aura daquele jogador
 * - /aura <jogador> <cor|todas|remover>      -> (ADM) Define diretamente a cor da aura do jogador (ou remove)
 * - /aura permission <jogador> [cor|todas|remover] -> (ADM) Igual ao comando acima (padrao /kit permission)
 */
public class AuraCommand implements CommandExecutor, TabCompleter {

    private final AuraManager auraManager;
    private final AuraGui auraGui;

    public AuraCommand(AuraManager auraManager, AuraGui auraGui) {
        this.auraManager = auraManager;
        this.auraGui = auraGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("teams.admin");

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Uso no console: /aura permission <jogador> <cor|todas|remover>", NamedTextColor.YELLOW));
                return true;
            }
            auraGui.openPlayerAuraMenu(player);
            return true;
        }

        String first = sanitizeToken(args[0]).toLowerCase(Locale.ROOT);

        // Subcomando administrativo explícito (/aura permission, /aura perm, /aura admin, /aura set)
        if (first.equals("permission") || first.equals("perm") || first.equals("permissao")
                || first.equals("permissoes") || first.equals("admin") || first.equals("set")) {
            handleAdminCommand(sender, args, 1);
            return true;
        }

        // Ligar/desligar a propria aura (/aura on | /aura off | /aura toggle)
        if (first.equals("on") || first.equals("ligar") || first.equals("ativar")
                || first.equals("off") || first.equals("desligar") || first.equals("desativar")
                || first.equals("toggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Apenas jogadores podem ligar/desligar a própria aura.", NamedTextColor.RED));
                return true;
            }
            if (!auraManager.hasAnyAura(player.getName())) {
                player.sendMessage(Component.text("🔒 Você ainda não possui nenhuma aura liberada pelo administrador!", NamedTextColor.RED));
                return true;
            }
            boolean enable;
            if (first.equals("on") || first.equals("ligar") || first.equals("ativar")) {
                enable = true;
                auraManager.setAuraEnabled(player.getName(), true);
            } else if (first.equals("off") || first.equals("desligar") || first.equals("desativar")) {
                enable = false;
                auraManager.setAuraEnabled(player.getName(), false);
            } else {
                enable = auraManager.toggleAuraEnabled(player.getName());
            }

            if (enable) {
                AuraColor active = auraManager.getActiveAuraColor(player.getName());
                player.sendMessage(Component.text("✨ Sua ", NamedTextColor.GREEN)
                        .append(Component.text(active != null ? active.getDisplayName() : "Aura", active != null ? active.getTextColor() : NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text(" foi ATIVADA!", NamedTextColor.GREEN)));
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.25f);
            } else {
                player.sendMessage(Component.text("🔴 Sua aura foi DESATIVADA. Use /aura para reativar quando quiser!", NamedTextColor.YELLOW));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            }
            return true;
        }

        // Verifica se o primeiro argumento e uma cor de aura (para o proprio jogador equipar)
        AuraColor directColor = AuraColor.fromInput(first);
        if (directColor != null && args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Apenas jogadores podem equipar uma aura.", NamedTextColor.RED));
                return true;
            }
            if (!auraManager.hasAuraColorPermission(player.getName(), directColor)) {
                player.sendMessage(Component.text("🔒 Você não possui a ", NamedTextColor.RED)
                        .append(Component.text(directColor.getDisplayName(), directColor.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(" liberada! Peça a um ADM para liberar.", NamedTextColor.RED)));
                return true;
            }
            auraManager.assignAndActivateColor(player.getName(), directColor);
            player.sendMessage(Component.text("✨ Você equipou a ", NamedTextColor.GREEN)
                    .append(Component.text(directColor.getDisplayName(), directColor.getTextColor(), TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
            return true;
        }

        // Se for ADM e digitou /aura <jogador> [cor|todas|remover], trata diretamente como gerenciamento daquele jogador!
        if (isAdmin) {
            handleAdminCommand(sender, args, 0);
            return true;
        }

        sender.sendMessage(Component.text("Cor de aura '" + args[0] + "' não encontrada! Use ", NamedTextColor.RED)
                .append(Component.text("/aura", NamedTextColor.YELLOW))
                .append(Component.text(" para abrir o menu.", NamedTextColor.RED)));
        return true;
    }

    private void handleAdminCommand(CommandSender sender, String[] args, int offset) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores podem gerenciar as auras dos jogadores!", NamedTextColor.RED));
            return;
        }

        // /aura permission (sem argumentos adicionais) -> abre a lista de jogadores na GUI
        if (args.length <= offset) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(Component.text("Uso no console: /aura permission <jogador> <cor|todas|remover>", NamedTextColor.YELLOW));
                return;
            }
            auraGui.openAdminPlayersMenu(admin, 0);
            return;
        }

        String targetPlayer = sanitizeToken(args[offset]);
        if (targetPlayer.isEmpty()) {
            sender.sendMessage(Component.text("Informe um nome de jogador válido!", NamedTextColor.RED));
            return;
        }

        // /aura permission <jogador> ou /aura <jogador> -> Abre a GUI de cores daquele jogador
        if (args.length == offset + 1) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(Component.text("Uso no console: /aura permission <jogador> <cor|todas|remover>", NamedTextColor.YELLOW));
                return;
            }
            auraGui.openPlayerAuraEditorMenu(admin, targetPlayer);
            return;
        }

        // /aura [permission] <jogador> <cor|todas|remover> [remover]
        String colorArg = sanitizeToken(args[offset + 1]).toLowerCase(Locale.ROOT);
        String displayTarget = auraManager.resolveDisplayName(targetPlayer);

        if (colorArg.equals("todas") || colorArg.equals("todos") || colorArg.equals("all") || colorArg.equals("*")) {
            auraManager.setAllAuraColorsPermission(displayTarget, true);
            sender.sendMessage(Component.text("✨ Todas as 11 cores de Aura foram ", NamedTextColor.GREEN)
                    .append(Component.text("LIBERADAS", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" para ", NamedTextColor.GREEN))
                    .append(Component.text(displayTarget, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            Player onlineTarget = Bukkit.getPlayerExact(displayTarget);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(Component.text("✨ Todas as cores de Aura foram liberadas para você! Use /aura para escolher!", NamedTextColor.GREEN));
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
            return;
        }

        if (colorArg.equals("nenhuma") || colorArg.equals("nenhum") || colorArg.equals("remover")
                || colorArg.equals("remove") || colorArg.equals("limpar") || colorArg.equals("clear")
                || colorArg.equals("off") || colorArg.equals("none")) {
            auraManager.setAllAuraColorsPermission(displayTarget, false);
            sender.sendMessage(Component.text("🔒 A aura de ", NamedTextColor.RED)
                    .append(Component.text(displayTarget, NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" foi removida.", NamedTextColor.RED)));
            Player onlineTarget = Bukkit.getPlayerExact(displayTarget);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(Component.text("🔒 Sua aura foi removida por um administrador.", NamedTextColor.RED));
            }
            return;
        }

        AuraColor color = AuraColor.fromInput(colorArg);
        if (color == null) {
            sender.sendMessage(Component.text("Cor '" + colorArg + "' inválida! Cores disponíveis: ", NamedTextColor.RED)
                    .append(Component.text("vermelho, laranja, dourado, verde, ciano, azul, roxo, rosa, branco, sombria, arcoiris, todas, remover", NamedTextColor.YELLOW)));
            return;
        }

        boolean removeMode = false;
        if (args.length >= offset + 3) {
            String mode = sanitizeToken(args[offset + 2]).toLowerCase(Locale.ROOT);
            if (mode.equals("remove") || mode.equals("remover") || mode.equals("bloquear") || mode.equals("off") || mode.equals("false")) {
                removeMode = true;
            }
        }

        if (removeMode) {
            auraManager.setAuraColorPermission(displayTarget, color, false);
            sender.sendMessage(Component.text("🔒 A ", NamedTextColor.RED)
                    .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                    .append(Component.text(" foi REMOVIDA de ", NamedTextColor.RED))
                    .append(Component.text(displayTarget, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.RED)));
        } else {
            auraManager.assignAndActivateColor(displayTarget, color);
            sender.sendMessage(Component.text("✨ Você definiu a ", NamedTextColor.GREEN)
                    .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                    .append(Component.text(" para o jogador ", NamedTextColor.GREEN))
                    .append(Component.text(displayTarget, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));

            Player onlineTarget = Bukkit.getPlayerExact(displayTarget);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(Component.text("✨ O administrador concedeu e ativou a ", NamedTextColor.GREEN)
                        .append(Component.text(color.getDisplayName(), color.getTextColor(), TextDecoration.BOLD))
                        .append(Component.text(" em você! Use ", NamedTextColor.GREEN))
                        .append(Component.text("/aura", NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" para gerenciar!", NamedTextColor.GREEN)));
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.25f);
            }
        }
    }

    private String sanitizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\"", "").replace("'", "").replace(",", "").trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("teams.admin");

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if (isAdmin) {
                if ("permission".startsWith(prefix)) {
                    suggestions.add("permission");
                }
                for (String pName : auraManager.getAllKnownPlayerNamesSorted()) {
                    if (pName.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        suggestions.add(pName);
                    }
                }
            }
            for (String toggleOpt : List.of("on", "off")) {
                if (toggleOpt.startsWith(prefix)) {
                    suggestions.add(toggleOpt);
                }
            }
            for (AuraColor c : AuraColor.values()) {
                if (c.getId().startsWith(prefix)) {
                    if (isAdmin || auraManager.hasAuraColorPermission(sender.getName(), c)) {
                        suggestions.add(c.getId());
                    }
                }
            }
            return suggestions;
        }

        if (isAdmin) {
            boolean explicitSub = args[0].equalsIgnoreCase("permission")
                    || args[0].equalsIgnoreCase("perm")
                    || args[0].equalsIgnoreCase("admin")
                    || args[0].equalsIgnoreCase("set");

            if (explicitSub && args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return auraManager.getAllKnownPlayerNamesSorted().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }

            if ((explicitSub && args.length == 3) || (!explicitSub && args.length == 2)) {
                String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
                List<String> opts = new ArrayList<>();
                for (AuraColor c : AuraColor.values()) {
                    opts.add(c.getId());
                }
                opts.add("todas");
                opts.add("remover");
                return opts.stream().filter(o -> o.startsWith(prefix)).toList();
            }
        }

        return Collections.emptyList();
    }
}
