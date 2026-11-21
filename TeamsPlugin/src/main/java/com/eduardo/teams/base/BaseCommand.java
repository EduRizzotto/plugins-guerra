package com.eduardo.teams.base;

import com.eduardo.teams.base.BaseManager.TeamBase;
import com.eduardo.teams.base.BaseManager.TntQuota;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comando /base:
 * - /base criar   -> (Lider do time) Cria a unica base do time protegendo 30 blocos para cada lado
 * - /base remover -> (Lider do time) Remove a base atual do time
 * - /base info    -> Mostra informacoes da base do time ou da base na posicao atual
 */
public class BaseCommand implements CommandExecutor, TabCompleter {

    private final BaseManager baseManager;
    private final TeamManager teamManager;

    public BaseCommand(BaseManager baseManager, TeamManager teamManager) {
        this.baseManager = baseManager;
        this.teamManager = teamManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Apenas jogadores podem usar o comando /base.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "criar", "create", "set" -> handleCreate(player);
            case "remover", "remove", "delete", "del" -> handleRemove(player, args);
            case "info", "status" -> handleInfo(player);
            default -> sendUsage(player);
        }
        return true;
    }

    private void handleCreate(Player player) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Você precisa estar em um time para criar uma base!", NamedTextColor.RED));
            return;
        }

        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o líder do time pode usar /base criar!", NamedTextColor.RED));
            return;
        }

        TeamBase existing = baseManager.getBaseByTeam(team.getName());
        if (existing != null) {
            player.sendMessage(Component.text("Seu time já possui uma base criada em (", NamedTextColor.RED)
                    .append(Component.text("X: " + existing.getCenterX() + ", Y: " + existing.getCenterY() + ", Z: " + existing.getCenterZ(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(")! Use ", NamedTextColor.RED))
                    .append(Component.text("/base remover", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" antes se quiser criar em outro lugar.", NamedTextColor.RED)));
            return;
        }

        Location loc = player.getLocation();
        TeamBase overlap = baseManager.findOverlappingBase(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ(), team.getName());
        if (overlap != null) {
            player.sendMessage(Component.text("Esta área (30 blocos para cada lado) sobrepõe a base do time ", NamedTextColor.RED)
                    .append(Component.text(overlap.getTeamDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("! Afaste-se mais para criar sua base.", NamedTextColor.RED)));
            return;
        }

        TeamBase created = baseManager.createBase(team.getName(), loc);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("🏰 Base do time ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(team.getName(), team.getColor(), TextDecoration.BOLD))
                .append(Component.text(" criada com sucesso!", NamedTextColor.GREEN, TextDecoration.BOLD)));
        player.sendMessage(Component.text(" • Centro: ", NamedTextColor.GRAY)
                .append(Component.text("X: " + created.getCenterX() + ", Y: " + created.getCenterY() + ", Z: " + created.getCenterZ(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" • Área protegida (30 blocos para cada lado): ", NamedTextColor.GRAY)
                .append(Component.text("X [" + created.getMinX() + " até " + created.getMaxX() + "] | Z [" + created.getMinZ() + " até " + created.getMaxZ() + "]", NamedTextColor.AQUA)));
        player.sendMessage(Component.text(" • Regras da Base: ", NamedTextColor.GOLD)
                .append(Component.text("Proibido colocar lava | Máx. 5 TNTs por time a cada 3 dias | Blocos normais liberados.", NamedTextColor.YELLOW)));
        player.sendMessage(Component.empty());
    }

    private void handleRemove(Player player, String[] args) {
        // Permite que OP remova base de qualquer time com /base remover <time>
        if (args.length >= 2 && (player.isOp() || player.hasPermission("teams.admin"))) {
            TeamBase removed = baseManager.removeBase(args[1]);
            if (removed == null) {
                player.sendMessage(Component.text("O time '" + args[1] + "' não possui base ativa.", NamedTextColor.RED));
                return;
            }
            player.sendMessage(Component.text("✔ Base do time '" + removed.getTeamDisplayName() + "' removida por ADM!", NamedTextColor.GREEN));
            return;
        }

        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Você não está em nenhum time!", NamedTextColor.RED));
            return;
        }

        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o líder do time pode usar /base remover!", NamedTextColor.RED));
            return;
        }

        TeamBase removed = baseManager.removeBase(team.getName());
        if (removed == null) {
            player.sendMessage(Component.text("Seu time não possui nenhuma base criada no momento.", NamedTextColor.RED));
            return;
        }

        player.sendMessage(Component.text("🗑 A base do time ", NamedTextColor.YELLOW)
                .append(Component.text(team.getName(), team.getColor(), TextDecoration.BOLD))
                .append(Component.text(" em (X: " + removed.getCenterX() + ", Z: " + removed.getCenterZ() + ") foi removida!", NamedTextColor.YELLOW)));
    }

    private void handleInfo(Player player) {
        TeamBase baseHere = baseManager.getBaseAt(player.getLocation());
        Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
        TeamBase targetBase = (baseHere != null) ? baseHere : (playerTeam != null ? baseManager.getBaseByTeam(playerTeam.getName()) : null);

        if (targetBase == null) {
            player.sendMessage(Component.text("Você não está dentro de uma base e seu time ainda não criou uma base (/base criar).", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== 🏰 Base do Time " + targetBase.getTeamDisplayName() + " ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(" • Mundo: ", NamedTextColor.GRAY)
                .append(Component.text(targetBase.getWorldName(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" • Centro: ", NamedTextColor.GRAY)
                .append(Component.text("X: " + targetBase.getCenterX() + ", Y: " + targetBase.getCenterY() + ", Z: " + targetBase.getCenterZ(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text(" • Área (30 blocos p/ cada lado): ", NamedTextColor.GRAY)
                .append(Component.text("X [" + targetBase.getMinX() + ".." + targetBase.getMaxX() + "], Z [" + targetBase.getMinZ() + ".." + targetBase.getMaxZ() + "]", NamedTextColor.AQUA)));

        Map<String, TntQuota> tntMap = targetBase.getActiveTntQuotas();
        if (tntMap.isEmpty()) {
            player.sendMessage(Component.text(" • TNTs usadas nesta base (ciclo de 3 dias): ", NamedTextColor.GRAY)
                    .append(Component.text("Nenhuma (0/" + BaseManager.MAX_TNT_PER_TEAM + ")", NamedTextColor.GREEN)));
        } else {
            long now = System.currentTimeMillis();
            player.sendMessage(Component.text(" • TNTs usadas por time (Máx. " + BaseManager.MAX_TNT_PER_TEAM + " a cada 3 dias):", NamedTextColor.GRAY));
            for (Map.Entry<String, TntQuota> entry : tntMap.entrySet()) {
                TntQuota quota = entry.getValue();
                String resetIn = BaseManager.formatDuration(quota.getRemainingMs(now));
                player.sendMessage(Component.text("   - " + entry.getKey() + ": ", NamedTextColor.YELLOW)
                        .append(Component.text(quota.getCount() + "/" + BaseManager.MAX_TNT_PER_TEAM + " TNTs", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(" (reseta em " + resetIn + ")", NamedTextColor.GRAY)));
            }
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("🏰 Comandos de Base do Time:", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(" • /base criar", NamedTextColor.YELLOW)
                .append(Component.text(" — (Líder) Cria a base do time (30 blocos para cada lado)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" • /base remover", NamedTextColor.YELLOW)
                .append(Component.text(" — (Líder) Remove a base atual do time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" • /base info", NamedTextColor.YELLOW)
                .append(Component.text(" — Mostra área e contagem de TNTs (0/5) da base", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> opts = List.of("criar", "remover", "info");
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
