package com.eduardo.teams.board;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.command.TeamCommand;
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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class PlacarCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ADMIN_SUBS = List.of(
            "kit", "clans", "players", "destaques", "rotativo",
            "ver", "tamanho", "atualizar", "listar", "remover", "limpar", "ajuda"
    );
    private static final List<String> SCALE_SUBS = List.of("mapa", "normal", "grande", "gigante");

    private final TeamsPlugin plugin;
    private final WallBoardManager boardManager;

    public PlacarCommand(TeamsPlugin plugin, WallBoardManager boardManager) {
        this.plugin = plugin;
        this.boardManager = boardManager;
    }

    public boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("teams.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando só pode ser utilizado por jogadores.", NamedTextColor.RED));
            return true;
        }
        return executePlacar(player, args);
    }

    public boolean executePlacar(Player player, String[] args) {
        if (args.length == 0) {
            if (isAdmin(player)) {
                giveFullKit(player);
            } else {
                boardManager.openInspectorGui(player, WallBoardManager.BoardType.CLANS);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "ver", "gui", "menu" -> {
                WallBoardManager.BoardType tab = args.length >= 2
                        ? WallBoardManager.BoardType.fromString(args[1])
                        : WallBoardManager.BoardType.CLANS;
                boardManager.openInspectorGui(player, tab != null ? tab : WallBoardManager.BoardType.CLANS);
                return true;
            }
            case "kit", "todos", "all" -> {
                if (!requireAdmin(player)) return true;
                giveFullKit(player);
                return true;
            }
            case "dar", "give", "pegar", "moldura" -> {
                if (!requireAdmin(player)) return true;
                if (args.length < 2 || args[1].equalsIgnoreCase("kit") || args[1].equalsIgnoreCase("todos")) {
                    giveFullKit(player);
                    return true;
                }
                WallBoardManager.BoardType type = WallBoardManager.BoardType.fromString(args[1]);
                if (type == null) {
                    player.sendMessage(Component.text("Tipo inválido! Use: clans, players, destaques, rotativo ou kit.", NamedTextColor.RED));
                    return true;
                }
                giveSingleFrame(player, type);
                return true;
            }
            case "clans", "clan", "clas", "times", "players", "player", "jogadores", "pvp",
                 "destaques", "highlights", "resumo", "rotativo", "rotating", "dinamico" -> {
                if (!requireAdmin(player)) {
                    WallBoardManager.BoardType tab = WallBoardManager.BoardType.fromString(sub);
                    boardManager.openInspectorGui(player, tab != null ? tab : WallBoardManager.BoardType.CLANS);
                    return true;
                }
                WallBoardManager.BoardType type = WallBoardManager.BoardType.fromString(sub);
                if (type != null) {
                    giveSingleFrame(player, type);
                }
                return true;
            }
            case "tamanho", "escala", "estilo", "scale" -> {
                if (!requireAdmin(player)) return true;
                WallBoardManager.PlacedBoard nearest = boardManager.findNearestBoard(player.getLocation(), 8.0);
                if (nearest == null) {
                    player.sendMessage(Component.text("Nenhum placar de parede encontrado num raio de 8 blocos!", NamedTextColor.RED));
                    return true;
                }
                WallBoardManager.DisplayScale newScale;
                if (args.length < 2) {
                    newScale = nearest.getDisplayScale().next();
                } else {
                    String sArg = args[1].toLowerCase(Locale.ROOT);
                    newScale = switch (sArg) {
                        case "mapa", "moldura", "pixel" -> WallBoardManager.DisplayScale.MOLDURA_COM_MAPA;
                        case "normal", "1", "1x" -> WallBoardManager.DisplayScale.MURAL_NORMAL;
                        case "grande", "medio", "1.35x" -> WallBoardManager.DisplayScale.MURAL_GRANDE;
                        case "gigante", "telao", "2x" -> WallBoardManager.DisplayScale.MURAL_GIGANTE;
                        default -> nearest.getDisplayScale().next();
                    };
                }
                nearest.setDisplayScale(newScale);
                boardManager.refreshSingleBoard(nearest);
                boardManager.saveBoards();
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.9f, 1.4f);
                player.sendMessage(Component.text("✦ Estilo do placar alterado para: ", NamedTextColor.GREEN)
                        .append(Component.text(newScale.getLabel(), NamedTextColor.GOLD, TextDecoration.BOLD)));
                return true;
            }
            case "atualizar", "refresh", "reload" -> {
                if (!requireAdmin(player)) return true;
                boardManager.refreshAllBoards();
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
                player.sendMessage(Component.text("✔ Todos os placares de parede foram atualizados com sucesso!", NamedTextColor.GREEN));
                return true;
            }
            case "listar", "list" -> {
                if (!requireAdmin(player)) return true;
                Collection<WallBoardManager.PlacedBoard> all = boardManager.getAllBoards();
                if (all.isEmpty()) {
                    player.sendMessage(Component.text("Não há nenhum placar instalado nas paredes no momento.", NamedTextColor.YELLOW));
                    return true;
                }
                player.sendMessage(Component.text("━━━ Placares de Parede Ativos (" + all.size() + ") ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
                int idx = 1;
                for (WallBoardManager.PlacedBoard b : all) {
                    player.sendMessage(Component.text(" #" + (idx++) + " ", NamedTextColor.YELLOW)
                            .append(Component.text(b.getBoardType().getDisplayName(), b.getBoardType().getThemeColor(), TextDecoration.BOLD))
                            .append(Component.text(" em " + b.getWorldName() + " (" + b.getBlockX() + ", " + b.getBlockY() + ", " + b.getBlockZ() + ") ", NamedTextColor.GRAY))
                            .append(Component.text("[" + b.getDisplayScale().name() + "]", NamedTextColor.AQUA)));
                }
                return true;
            }
            case "remover", "remove", "del" -> {
                if (!requireAdmin(player)) return true;
                WallBoardManager.PlacedBoard nearest = boardManager.findNearestBoard(player.getLocation(), 8.0);
                if (nearest == null) {
                    player.sendMessage(Component.text("Nenhum placar de parede encontrado num raio de 8 blocos!", NamedTextColor.RED));
                    return true;
                }
                boardManager.removeBoard(nearest, true);
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
                player.sendMessage(Component.text("✔ O placar mais próximo foi removido da parede!", NamedTextColor.GREEN));
                return true;
            }
            case "limpar", "clear" -> {
                if (!requireAdmin(player)) return true;
                int count = boardManager.removeAllBoards();
                player.sendMessage(Component.text("✔ Foram removidos " + count + " placares de parede do servidor.", NamedTextColor.YELLOW));
                return true;
            }
            default -> {
                sendHelp(player);
                return true;
            }
        }
    }

    private boolean requireAdmin(Player player) {
        if (!isAdmin(player)) {
            player.sendMessage(Component.text("Apenas administradores podem pegar ou gerenciar as Molduras de Placar!", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private void giveSingleFrame(Player player, WallBoardManager.BoardType type) {
        ItemStack item = boardManager.createBoardFrameItem(type);
        player.getInventory().addItem(item);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
        player.sendMessage(Component.text("✔ Você recebeu a ", NamedTextColor.GREEN)
                .append(Component.text("Moldura de Placar: " + type.getDisplayName(), type.getThemeColor(), TextDecoration.BOLD))
                .append(Component.text("! Coloque-a em uma parede do lobby.", NamedTextColor.GREEN)));
    }

    private void giveFullKit(Player player) {
        for (WallBoardManager.BoardType type : WallBoardManager.BoardType.values()) {
            player.getInventory().addItem(boardManager.createBoardFrameItem(type));
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("🏆 Kit de Molduras de Placar recebido!", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Moldura: Ranking de Clãs", NamedTextColor.GOLD))
                .append(Component.text(" (Ranking completo dos clãs)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Moldura: Ranking de Players", NamedTextColor.AQUA))
                .append(Component.text(" (Top PvP individual)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Moldura: Destaques & Resumo", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" (Troféus, MVP e Rivalidade)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text("Moldura: Painel Dinâmico (3 em 1)", NamedTextColor.GREEN))
                .append(Component.text(" (Alterna sozinho a cada 8s)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("💡 Dica: Coloque na parede e use ", NamedTextColor.YELLOW)
                .append(Component.text("Shift + Clique Direito", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" para ajustar o tamanho!", NamedTextColor.YELLOW)));
        player.sendMessage(Component.empty());
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("━━━ Comandos do Placar de Lobby ━━━", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/placar kit", NamedTextColor.YELLOW)
                .append(Component.text(" - Recebe as 4 molduras exclusivas de placar", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/placar <clans|players|destaques|rotativo>", NamedTextColor.YELLOW)
                .append(Component.text(" - Pega uma moldura específica", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/placar ver [clans|players|destaques]", NamedTextColor.YELLOW)
                .append(Component.text(" - Abre o placar em menu GUI", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/placar tamanho <mapa|normal|grande|gigante>", NamedTextColor.YELLOW)
                .append(Component.text(" - Muda o estilo/escala do placar próximo", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/placar atualizar", NamedTextColor.YELLOW)
                .append(Component.text(" - Atualiza todos os placares de parede", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/placar remover", NamedTextColor.YELLOW)
                .append(Component.text(" - Remove o placar mais próximo", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        return tabCompletePlacar(player, args);
    }

    public List<String> tabCompletePlacar(Player player, String[] args) {
        if (!isAdmin(player)) {
            if (args.length == 1) {
                return List.of("ver");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("ver")) {
                return List.of("clans", "players", "destaques");
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return ADMIN_SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("dar") || sub.equals("give") || sub.equals("pegar") || sub.equals("ver")) {
                return List.of("clans", "players", "destaques", "rotativo", "kit").stream()
                        .filter(s -> s.startsWith(prefix)).toList();
            }
            if (sub.equals("tamanho") || sub.equals("escala") || sub.equals("estilo")) {
                return SCALE_SUBS.stream().filter(s -> s.startsWith(prefix)).toList();
            }
        }
        return Collections.emptyList();
    }

    /**
     * Wraps the existing TeamCommand so `/team placar ...` works seamlessly
     * and any `/team` modification automatically refreshes all wall scoreboards!
     */
    public CommandExecutor wrapTeamExecutor(TeamCommand delegate) {
        return (sender, command, label, args) -> {
            if (args.length > 0) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (sub.equals("placar") || sub.equals("placares") || sub.equals("board") || sub.equals("ranking")) {
                    if (sender instanceof Player player) {
                        String[] rest = Arrays.copyOfRange(args, 1, args.length);
                        return executePlacar(player, rest);
                    }
                }
            }
            boolean res = delegate.onCommand(sender, command, label, args);
            Bukkit.getScheduler().runTaskLater(plugin, boardManager::refreshAllBoards, 2L);
            return res;
        };
    }

    public TabCompleter wrapTeamTabCompleter(TeamCommand delegate) {
        return (sender, command, alias, args) -> {
            if (args.length >= 1) {
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (args.length > 1 && (sub.equals("placar") || sub.equals("placares") || sub.equals("board") || sub.equals("ranking"))) {
                    if (sender instanceof Player player) {
                        String[] rest = Arrays.copyOfRange(args, 1, args.length);
                        return tabCompletePlacar(player, rest);
                    }
                    return Collections.emptyList();
                }
            }
            List<String> base = delegate.onTabComplete(sender, command, alias, args);
            if (args.length == 1 && sender instanceof Player player && isAdmin(player)) {
                List<String> merged = new ArrayList<>(base != null ? base : List.of());
                String prefix = args[0].toLowerCase(Locale.ROOT);
                if ("placar".startsWith(prefix) && !merged.contains("placar")) {
                    merged.add("placar");
                }
                return merged;
            }
            return base;
        };
    }
}
