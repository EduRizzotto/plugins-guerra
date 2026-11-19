package com.eduardo.teams.command;

import com.eduardo.teams.gui.TeamGui;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Executor e TabCompleter do comando /team e todos os seus subcomandos.
 */
public class TeamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "list", "members", "leader", "tag",
            "color", "invite", "accept", "kick", "tell", "leave", "disband", "quest"
    );

    private final TeamManager teamManager;
    private final TeamGui teamGui;

    public TeamCommand(TeamManager teamManager, TeamGui teamGui) {
        this.teamManager = teamManager;
        this.teamGui = teamGui;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Este comando so pode ser utilizado por jogadores.", NamedTextColor.RED));
            return true;
        }

        // /team sem argumentos abre a interface grafica (GUI)
        if (args.length == 0) {
            teamGui.openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(player, args);
            case "list" -> handleList(player);
            case "members" -> handleMembers(player, args);
            case "leader" -> handleLeader(player, args);
            case "tag" -> handleTag(player, args);
            case "color" -> handleColor(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player, args);
            case "kick" -> handleKick(player, args);
            case "tell" -> handleTell(player, args);
            case "leave" -> handleLeave(player);
            case "disband" -> handleDisband(player);
            case "quest", "quests", "missoes", "missao" -> teamGui.openQuestsMenu(player);
            case "gui", "menu" -> teamGui.openMainMenu(player);
            default -> sendHelp(player);
        }

        return true;
    }

    public void handleCreate(Player player, String[] args) {
        if (!teamManager.canCreateTeam(player)) {
            player.sendMessage(Component.text("Apenas líderes autorizados ou administradores podem criar times!", NamedTextColor.RED));
            return;
        }
        if (teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("Voce ja faz parte de um time! Saia dele antes de criar outro.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso correto: /team create <nome> [tag]", NamedTextColor.YELLOW));
            return;
        }

        String name = args[1];
        if (!name.matches("^[a-zA-Z0-9_]{3,16}$")) {
            player.sendMessage(Component.text("O nome do time deve ter entre 3 e 16 caracteres (letras, numeros ou _).", NamedTextColor.RED));
            return;
        }
        if (teamManager.getTeamByName(name) != null) {
            player.sendMessage(Component.text("Ja existe um time com esse nome!", NamedTextColor.RED));
            return;
        }

        String tag = args.length >= 3
                ? args[2].toUpperCase(Locale.ROOT)
                : name.substring(0, Math.min(3, name.length())).toUpperCase(Locale.ROOT);

        if (!tag.matches("^[A-Z0-9]{2,6}$")) {
            player.sendMessage(Component.text("A tag deve ter entre 2 e 6 caracteres alfanumericos.", NamedTextColor.RED));
            return;
        }
        if (teamManager.isTagTaken(tag)) {
            player.sendMessage(Component.text("Essa tag ja esta sendo usada por outro time!", NamedTextColor.RED));
            return;
        }

        Team team = teamManager.createTeam(name, tag, player.getUniqueId());
        player.sendMessage(
                Component.text("Time ", NamedTextColor.GREEN)
                        .append(Component.text(team.getName() + " ", NamedTextColor.GOLD))
                        .append(team.getFormattedTag())
                        .append(Component.text(" criado com sucesso!", NamedTextColor.GREEN))
        );
    }

    public void handleList(Player player) {
        Collection<Team> allTeams = teamManager.getAllTeams();
        if (allTeams.isEmpty()) {
            player.sendMessage(Component.text("Nenhum time foi criado no servidor ainda.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("=== Times Registrados (" + allTeams.size() + ") ===", NamedTextColor.GOLD));
        for (Team team : allTeams) {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(team.getLeader());
            String leaderName = leader.getName() != null ? leader.getName() : "Desconhecido";
            int tKills = teamManager.getTeamTotalKills(team);
            int tDeaths = teamManager.getTeamTotalDeaths(team);
            player.sendMessage(
                    Component.text(" - ", NamedTextColor.GRAY)
                            .append(team.getFormattedTag())
                            .append(Component.text(" " + team.getName(), NamedTextColor.WHITE))
                            .append(Component.text(" | Lider: ", NamedTextColor.GRAY))
                            .append(Component.text(leaderName, NamedTextColor.YELLOW))
                            .append(Component.text(" (" + team.getMembers().size() + " membros) | PvP: ", NamedTextColor.GRAY))
                            .append(teamManager.formatStatsBracket(tKills, tDeaths))
            );
        }
    }

    public void handleMembers(Player player, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = teamManager.getTeamByName(args[1]);
            if (team == null) {
                player.sendMessage(Component.text("Time '" + args[1] + "' nao encontrado.", NamedTextColor.RED));
                return;
            }
        } else {
            team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                player.sendMessage(Component.text("Voce nao esta em um time. Use /team members <nomeDoTime>.", NamedTextColor.RED));
                return;
            }
        }

        int totalKills = teamManager.getTeamTotalKills(team);
        int totalDeaths = teamManager.getTeamTotalDeaths(team);

        player.sendMessage(
                Component.text("=== Membros do Time " + team.getName() + " ", NamedTextColor.GOLD)
                        .append(team.getFormattedTag())
                        .append(Component.text(" | Total PvP: ", NamedTextColor.GRAY))
                        .append(teamManager.formatStatsBracket(totalKills, totalDeaths))
                        .append(Component.text(" ===", NamedTextColor.GOLD))
        );

        for (UUID memberId : team.getMembers()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
            String name = offline.getName() != null ? offline.getName() : memberId.toString().substring(0, 8);
            boolean online = offline.isOnline();
            boolean isLeader = team.isLeader(memberId);
            TeamManager.PlayerStats mStats = teamManager.getPlayerStats(memberId);

            player.sendMessage(
                    Component.text(online ? " ● " : " ○ ", online ? NamedTextColor.GREEN : NamedTextColor.RED)
                            .append(Component.text(name, isLeader ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                            .append(Component.text(isLeader ? " [Lider] " : " ", NamedTextColor.YELLOW))
                            .append(teamManager.formatStatsBracket(mStats.kills(), mStats.deaths()))
            );
        }
    }

    public void handleLeader(Player player, String[] args) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }

        // Sem argumento: exibe quem e o lider atual
        if (args.length < 2) {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(team.getLeader());
            String leaderName = leader.getName() != null ? leader.getName() : "Desconhecido";
            player.sendMessage(
                    Component.text("O lider atual do time ", NamedTextColor.GRAY)
                            .append(Component.text(team.getName(), NamedTextColor.GOLD))
                            .append(Component.text(" e: ", NamedTextColor.GRAY))
                            .append(Component.text(leaderName, NamedTextColor.YELLOW))
            );
            if (team.isLeader(player.getUniqueId())) {
                player.sendMessage(Component.text("Para transferir a lideranca, use: /team leader <jogador>", NamedTextColor.GRAY));
            }
            return;
        }

        // Com argumento: transfere a lideranca para outro membro do time
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o lider do time pode transferir a lideranca!", NamedTextColor.RED));
            return;
        }

        UUID targetId = findMemberByName(team, args[1]);
        if (targetId == null) {
            player.sendMessage(Component.text("Esse jogador nao faz parte do seu time.", NamedTextColor.RED));
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Voce ja e o lider do time!", NamedTextColor.RED));
            return;
        }

        team.setLeader(targetId);
        teamManager.saveTeams();

        OfflinePlayer newLeader = Bukkit.getOfflinePlayer(targetId);
        String newLeaderName = newLeader.getName() != null ? newLeader.getName() : args[1];
        broadcastToTeam(
                team,
                Component.text(newLeaderName, NamedTextColor.GOLD)
                        .append(Component.text(" agora e o novo lider do time!", NamedTextColor.GREEN))
        );
    }

    public void handleTag(Player player, String[] args) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o lider do time pode alterar a tag!", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso correto: /team tag <novaTag>", NamedTextColor.YELLOW));
            return;
        }

        String newTag = args[1].toUpperCase(Locale.ROOT);
        if (!newTag.matches("^[A-Z0-9]{2,6}$")) {
            player.sendMessage(Component.text("A tag deve ter entre 2 e 6 caracteres (apenas letras e numeros).", NamedTextColor.RED));
            return;
        }
        if (!newTag.equalsIgnoreCase(team.getTag()) && teamManager.isTagTaken(newTag)) {
            player.sendMessage(Component.text("Essa tag ja esta sendo usada por outro time!", NamedTextColor.RED));
            return;
        }

        team.setTag(newTag);
        teamManager.refreshTeamDisplays(team);
        broadcastToTeam(
                team,
                Component.text("A tag do time foi alterada para ", NamedTextColor.GREEN)
                        .append(team.getFormattedTag())
        );
    }

    public void handleColor(Player player, String[] args) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o lider do time pode alterar a cor da tag!", NamedTextColor.RED));
            return;
        }

        // Se digitar apenas /team color, abre o seletor visual de cores
        if (args.length < 2) {
            teamGui.openColorPickerMenu(player);
            return;
        }

        String colorKey = args[1].toLowerCase(Locale.ROOT);
        NamedTextColor color = NamedTextColor.NAMES.value(colorKey);
        if (color == null) {
            player.sendMessage(Component.text("Cor invalida! Use /team color para abrir o menu de cores ou escolha uma: "
                    + String.join(", ", TeamGui.getColorOptions().keySet()), NamedTextColor.RED));
            return;
        }

        team.setColor(color);
        teamManager.refreshTeamDisplays(team);
        broadcastToTeam(
                team,
                Component.text("A cor da tag do time foi atualizada: ", NamedTextColor.GREEN)
                        .append(team.getFormattedTag())
        );
    }

    public void handleInvite(Player player, String[] args) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o lider do time pode convidar jogadores!", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso correto: /team invite <jogador>", NamedTextColor.YELLOW));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Jogador '" + args[1] + "' nao esta online.", NamedTextColor.RED));
            return;
        }
        if (teamManager.getPlayerTeam(target.getUniqueId()) != null) {
            player.sendMessage(Component.text(target.getName() + " ja pertence a um time!", NamedTextColor.RED));
            return;
        }

        teamManager.invitePlayer(team, target.getUniqueId());
        player.sendMessage(Component.text("Convite enviado para " + target.getName() + "!", NamedTextColor.GREEN));

        Component acceptButton = Component.text("[CLIQUE PARA ACEITAR]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/team accept " + team.getName()))
                .hoverEvent(HoverEvent.showText(Component.text("Clique para entrar no time " + team.getName(), NamedTextColor.YELLOW)));

        target.sendMessage(
                Component.text("Voce foi convidado por ", NamedTextColor.AQUA)
                        .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" para entrar no time ", NamedTextColor.AQUA))
                        .append(Component.text(team.getName() + " ", NamedTextColor.GOLD))
                        .append(team.getFormattedTag())
                        .append(Component.text("! ", NamedTextColor.AQUA))
                        .append(acceptButton)
        );
    }

    public void handleAccept(Player player, String[] args) {
        if (teamManager.getPlayerTeam(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("Voce ja faz parte de um time!", NamedTextColor.RED));
            return;
        }

        Team invitedTeam = teamManager.getPendingInvite(player.getUniqueId());
        if (invitedTeam == null) {
            player.sendMessage(Component.text("Voce nao possui nenhum convite de time pendente.", NamedTextColor.RED));
            return;
        }

        if (args.length >= 2 && !invitedTeam.getName().equalsIgnoreCase(args[1])) {
            player.sendMessage(Component.text("Voce nao tem um convite pendente para o time '" + args[1] + "'.", NamedTextColor.RED));
            return;
        }

        teamManager.addMember(invitedTeam, player.getUniqueId());
        broadcastToTeam(
                invitedTeam,
                Component.text(player.getName(), NamedTextColor.YELLOW)
                        .append(Component.text(" entrou no time!", NamedTextColor.GREEN))
        );
    }

    public void handleKick(Player player, String[] args) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o lider do time pode expulsar membros!", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso correto: /team kick <jogador>", NamedTextColor.YELLOW));
            return;
        }

        UUID targetId = findMemberByName(team, args[1]);
        if (targetId == null) {
            player.sendMessage(Component.text("Esse jogador nao faz parte do seu time.", NamedTextColor.RED));
            return;
        }
        if (targetId.equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Voce nao pode expulsar a si mesmo! Use /team disband para desfazer o time.", NamedTextColor.RED));
            return;
        }

        OfflinePlayer targetOffline = Bukkit.getOfflinePlayer(targetId);
        String targetName = targetOffline.getName() != null ? targetOffline.getName() : args[1];

        teamManager.removeMember(team, targetId);
        broadcastToTeam(
                team,
                Component.text(targetName, NamedTextColor.YELLOW)
                        .append(Component.text(" foi expulso do time.", NamedTextColor.RED))
        );

        Player onlineTarget = Bukkit.getPlayer(targetId);
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.sendMessage(Component.text("Voce foi expulso do time " + team.getName() + ".", NamedTextColor.RED));
        }
    }

    public void handleTell(Player player, String[] args) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Uso correto: /team tell <mensagem>", NamedTextColor.YELLOW));
            return;
        }

        String messageText = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Component teamChatMessage = Component.text("[Time] ", NamedTextColor.DARK_AQUA)
                .append(team.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(messageText, NamedTextColor.AQUA));

        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(teamChatMessage);
            }
        }
    }

    public void handleLeave(Player player) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Voce e o lider! Transfira a lideranca com /team leader <membro> ou use /team disband para encerrar o time.", NamedTextColor.RED));
            return;
        }

        teamManager.removeMember(team, player.getUniqueId());
        player.sendMessage(Component.text("Voce saiu do time " + team.getName() + ".", NamedTextColor.YELLOW));
        broadcastToTeam(
                team,
                Component.text(player.getName() + " saiu do time.", NamedTextColor.YELLOW)
        );
    }

    public void handleDisband(Player player) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            player.sendMessage(Component.text("Voce nao faz parte de nenhum time.", NamedTextColor.RED));
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            player.sendMessage(Component.text("Apenas o lider pode desfazer o time!", NamedTextColor.RED));
            return;
        }

        broadcastToTeam(
                team,
                Component.text("O time " + team.getName() + " foi desfeito pelo lider.", NamedTextColor.RED)
        );
        teamManager.disbandTeam(team);
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Comandos do Sistema de Times ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/team ", NamedTextColor.YELLOW).append(Component.text("- Abre o painel interativo (GUI)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team create <nome> [tag] ", NamedTextColor.YELLOW).append(Component.text("- Cria um novo time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team list ", NamedTextColor.YELLOW).append(Component.text("- Lista todos os times", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team members [time] ", NamedTextColor.YELLOW).append(Component.text("- Lista os membros do time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team leader [jogador] ", NamedTextColor.YELLOW).append(Component.text("- Mostra ou transfere a lideranca", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team tag <novaTag> ", NamedTextColor.YELLOW).append(Component.text("- Altera a sigla/tag do time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team color [cor] ", NamedTextColor.YELLOW).append(Component.text("- Altera a cor da tag do time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team invite <jogador> ", NamedTextColor.YELLOW).append(Component.text("- Convida um jogador para o time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team accept [time] ", NamedTextColor.YELLOW).append(Component.text("- Aceita um convite pendente", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team kick <jogador> ", NamedTextColor.YELLOW).append(Component.text("- Expulsa um membro do time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team tell <mensagem> ", NamedTextColor.YELLOW).append(Component.text("- Envia mensagem privada para o time", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/team leave | disband ", NamedTextColor.YELLOW).append(Component.text("- Sai ou desfaz o time", NamedTextColor.GRAY)));
    }

    private UUID findMemberByName(Team team, String name) {
        for (UUID memberId : team.getMembers()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(name)) {
                return memberId;
            }
        }
        return null;
    }

    private void broadcastToTeam(Team team, Component message) {
        Component prefixed = team.getFormattedTag()
                .append(Component.text(" ", NamedTextColor.WHITE))
                .append(message);
        for (UUID memberId : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(prefixed);
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            boolean canCreate = teamManager.canCreateTeam(player);
            return SUBCOMMANDS.stream()
                    .filter(s -> canCreate || !s.equals("create"))
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String prefix = args[1].toLowerCase(Locale.ROOT);

            return switch (sub) {
                case "color" -> TeamGui.getColorOptions().keySet().stream()
                        .filter(c -> c.startsWith(prefix))
                        .toList();
                case "members" -> teamManager.getAllTeams().stream()
                        .map(Team::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                case "invite" -> Bukkit.getOnlinePlayers().stream()
                        .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                        .filter(p -> teamManager.getPlayerTeam(p.getUniqueId()) == null)
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
                case "kick", "leader" -> {
                    Team team = teamManager.getPlayerTeam(player.getUniqueId());
                    if (team == null) {
                        yield List.of();
                    }
                    List<String> names = new ArrayList<>();
                    for (UUID mId : team.getMembers()) {
                        if (mId.equals(player.getUniqueId())) {
                            continue;
                        }
                        OfflinePlayer off = Bukkit.getOfflinePlayer(mId);
                        if (off.getName() != null && off.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            names.add(off.getName());
                        }
                    }
                    yield names;
                }
                case "accept" -> {
                    Team pending = teamManager.getPendingInvite(player.getUniqueId());
                    yield pending != null ? List.of(pending.getName()) : List.of();
                }
                default -> List.of();
            };
        }

        return List.of();
    }
}
