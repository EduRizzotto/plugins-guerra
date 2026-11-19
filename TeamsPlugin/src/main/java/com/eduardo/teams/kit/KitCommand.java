package com.eduardo.teams.kit;

import com.eduardo.teams.kit.KitManager.KitType;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Executor e TabCompleter do comando /kit:
 * - /kit                                      -> Abre o menu de kits do jogador
 * - /kit <rango|sobrevivencia|minerador|...>  -> Resgata o kit (se liberado para o jogador)
 * - /kit permission                           -> Abre a GUI de jogadores para liberar/remover kits (OP/ADM)
 * - /kit permission <jogador>                 -> Abre a GUI de kits daquele jogador especifico (OP/ADM)
 * - /kit permission <jogador> <kit|todos>     -> Atalho direto para liberar/alternar um kit para o jogador (OP/ADM)
 */
public class KitCommand implements CommandExecutor, TabCompleter {

    private final KitManager kitManager;
    private final KitGui kitGui;

    public KitCommand(KitManager kitManager, KitGui kitGui) {
        this.kitManager = kitManager;
        this.kitGui = kitGui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Uso no console: /kit permission <jogador> <kit>", NamedTextColor.YELLOW));
                return true;
            }
            kitGui.openPlayerKitsMenu(player);
            return true;
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("permission") || first.equals("perm") || first.equals("permissao") || first.equals("permissoes")) {
            handlePermissionCommand(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores podem resgatar kits.", NamedTextColor.RED));
            return true;
        }

        String kitInput = String.join(" ", args);
        KitType kit = KitType.fromInput(kitInput);
        if (kit == null) {
            player.sendMessage(Component.text("Kit '" + kitInput + "' não encontrado! Use: ", NamedTextColor.RED)
                    .append(Component.text("/kit <rango|sobrevivencia|minerador|alquimista|arqueiro|construtor>", NamedTextColor.YELLOW)));
            return true;
        }

        kitManager.tryClaimKit(player, kit);
        return true;
    }

    private void handlePermissionCommand(CommandSender sender, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
            sender.sendMessage(Component.text("Apenas administradores podem gerenciar permissões de kits!", NamedTextColor.RED));
            return;
        }

        // /kit permission -> Abre a GUI com todos os jogadores que ja entraram no servidor
        if (args.length == 1) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(Component.text("Uso no console: /kit permission <jogador> <kit>", NamedTextColor.YELLOW));
                return;
            }
            kitGui.openPermissionPlayersMenu(admin, 0);
            return;
        }

        String targetPlayer = sanitizeToken(args[1]);
        if (targetPlayer.isEmpty()) {
            sender.sendMessage(Component.text("Informe um nome de jogador válido!", NamedTextColor.RED));
            return;
        }

        // /kit permission <jogador> -> Abre direto a GUI de kits daquele jogador
        if (args.length == 2) {
            if (!(sender instanceof Player admin)) {
                sender.sendMessage(Component.text("Uso no console: /kit permission <jogador> <kit>", NamedTextColor.YELLOW));
                return;
            }
            kitGui.openPlayerPermissionEditorMenu(admin, targetPlayer);
            return;
        }

        // /kit permission <jogador> <kit|todos|limpar> [add|remove]
        String kitArg = sanitizeToken(args[2]).toLowerCase(Locale.ROOT);
        String displayTarget = kitManager.resolveDisplayName(targetPlayer);

        if (kitArg.equals("todos") || kitArg.equals("all") || kitArg.equals("*")) {
            kitManager.setAllKitsPermission(displayTarget, true);
            sender.sendMessage(Component.text("✔ Todos os 6 kits foram ", NamedTextColor.GREEN)
                    .append(Component.text("LIBERADOS", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" para o jogador ", NamedTextColor.GREEN))
                    .append(Component.text(displayTarget, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GREEN)));
            Player onlineTarget = Bukkit.getPlayerExact(displayTarget);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(Component.text("🎁 Todos os 6 Kits Oficiais foram liberados para você! Use /kit para resgatar!", NamedTextColor.GREEN));
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
            return;
        }

        if (kitArg.equals("nenhum") || kitArg.equals("limpar") || kitArg.equals("clear") || kitArg.equals("none")) {
            kitManager.setAllKitsPermission(displayTarget, false);
            sender.sendMessage(Component.text("🔒 Todos os kits do jogador ", NamedTextColor.RED)
                    .append(Component.text(displayTarget, NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" foram bloqueados.", NamedTextColor.RED)));
            return;
        }

        KitType kit = KitType.fromInput(kitArg);
        if (kit == null) {
            sender.sendMessage(Component.text("Kit '" + kitArg + "' inválido! Kits disponíveis: ", NamedTextColor.RED)
                    .append(Component.text("rango, sobrevivencia, minerador, alquimista, arqueiro, construtor, todos", NamedTextColor.YELLOW)));
            return;
        }

        boolean newState;
        if (args.length >= 4) {
            String mode = sanitizeToken(args[3]).toLowerCase(Locale.ROOT);
            if (mode.equals("remove") || mode.equals("remover") || mode.equals("bloquear") || mode.equals("off") || mode.equals("false")) {
                newState = false;
                kitManager.setKitPermission(displayTarget, kit, false);
            } else {
                newState = true;
                kitManager.setKitPermission(displayTarget, kit, true);
            }
        } else {
            newState = kitManager.toggleKitPermission(displayTarget, kit);
        }

        if (newState) {
            sender.sendMessage(Component.text("✔ O ", NamedTextColor.GREEN)
                    .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                    .append(Component.text(" foi ", NamedTextColor.GREEN))
                    .append(Component.text("LIBERADO", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" para ", NamedTextColor.GREEN))
                    .append(Component.text(displayTarget, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("! (Ele já pode usar /kit " + kit.getId() + ")", NamedTextColor.GREEN)));

            Player onlineTarget = Bukkit.getPlayerExact(displayTarget);
            if (onlineTarget != null && onlineTarget.isOnline()) {
                onlineTarget.sendMessage(Component.text("🎁 O administrador liberou o ", NamedTextColor.GREEN)
                        .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                        .append(Component.text(" para você! Digite ", NamedTextColor.GREEN))
                        .append(Component.text("/kit " + kit.getId(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                        .append(Component.text(" para receber seus itens!", NamedTextColor.GREEN)));
                onlineTarget.playSound(onlineTarget.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        } else {
            sender.sendMessage(Component.text("🔒 A permissão do ", NamedTextColor.RED)
                    .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                    .append(Component.text(" foi ", NamedTextColor.RED))
                    .append(Component.text("REMOVIDA", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" de ", NamedTextColor.RED))
                    .append(Component.text(displayTarget, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.RED)));
        }
    }

    private String sanitizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\"", "").replace("'", "").replace(",", "").trim();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        boolean isAdmin = sender.isOp() || sender.hasPermission("teams.admin");

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if (isAdmin && "permission".startsWith(prefix)) {
                suggestions.add("permission");
            }
            for (KitType kit : KitType.values()) {
                if (kit.getId().startsWith(prefix)) {
                    // Para jogadores comuns, sugere apenas os kits que eles tem liberados
                    if (isAdmin || kitManager.hasKitPermission(sender.getName(), kit)) {
                        suggestions.add(kit.getId());
                    }
                }
            }
            return suggestions;
        }

        if (isAdmin && (args[0].equalsIgnoreCase("permission") || args[0].equalsIgnoreCase("perm"))) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return kitManager.getAllKnownPlayerNamesSorted().stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                List<String> opts = new ArrayList<>();
                for (KitType kit : KitType.values()) {
                    opts.add(kit.getId());
                }
                opts.add("todos");
                opts.add("limpar");
                return opts.stream().filter(o -> o.startsWith(prefix)).toList();
            }
            if (args.length == 4) {
                String prefix = args[3].toLowerCase(Locale.ROOT);
                return Arrays.asList("liberar", "remover").stream()
                        .filter(o -> o.startsWith(prefix))
                        .toList();
            }
        }

        return Collections.emptyList();
    }
}
