package com.eduardo.teams.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Comando /quest (aliases: /quests, /missoes, /missao) para abrir o painel de Quests Diarias
 * ou resetar/sortear novas quests (apenas OP/admin).
 */
public class QuestCommand implements CommandExecutor, TabCompleter {

    private final QuestManager questManager;
    private final QuestGui questGui;

    public QuestCommand(QuestManager questManager, QuestGui questGui) {
        this.questManager = questManager;
        this.questGui = questGui;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (args.length > 0 && args[0].equalsIgnoreCase("resetar")) {
            if (!sender.isOp() && !sender.hasPermission("teams.admin")) {
                sender.sendMessage(Component.text("Apenas administradores (OP) podem sortear novas Quests Diárias manualmente.", NamedTextColor.RED));
                return true;
            }
            questManager.rollNewDailyQuests(LocalDate.now().toString(), true);
            sender.sendMessage(Component.text("📜 Novas 5 Quests Diárias sorteadas com sucesso!", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores podem abrir o menu de Quests.", NamedTextColor.RED));
            return true;
        }

        questGui.openQuestsMenu(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1 && (sender.isOp() || sender.hasPermission("teams.admin"))) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return "resetar".startsWith(prefix) ? List.of("resetar") : List.of();
        }
        return List.of();
    }
}
