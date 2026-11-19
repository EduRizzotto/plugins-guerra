package com.eduardo.teams.gui;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.quest.QuestGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Construtor e gerenciador das interfaces graficas (GUI) do comando /team.
 */
public class TeamGui {

    public enum MenuType {
        MAIN,
        MEMBERS,
        INVITE,
        COLOR_PICKER,
        TEAM_LIST
    }

    public static class TeamMenuHolder implements InventoryHolder {
        private final MenuType menuType;
        private Inventory inventory;

        public TeamMenuHolder(MenuType menuType) {
            this.menuType = menuType;
        }

        public MenuType getMenuType() {
            return menuType;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey targetKey;
    private QuestGui questGui;

    private static final Map<String, ColorOption> COLOR_OPTIONS = new LinkedHashMap<>();

    public record ColorOption(String id, String displayName, NamedTextColor color, Material icon) {}

    static {
        COLOR_OPTIONS.put("red", new ColorOption("red", "Vermelho", NamedTextColor.RED, Material.RED_WOOL));
        COLOR_OPTIONS.put("dark_red", new ColorOption("dark_red", "Vermelho Escuro", NamedTextColor.DARK_RED, Material.NETHER_WART_BLOCK));
        COLOR_OPTIONS.put("gold", new ColorOption("gold", "Dourado", NamedTextColor.GOLD, Material.ORANGE_WOOL));
        COLOR_OPTIONS.put("yellow", new ColorOption("yellow", "Amarelo", NamedTextColor.YELLOW, Material.YELLOW_WOOL));
        COLOR_OPTIONS.put("green", new ColorOption("green", "Verde Claro", NamedTextColor.GREEN, Material.LIME_WOOL));
        COLOR_OPTIONS.put("dark_green", new ColorOption("dark_green", "Verde Escuro", NamedTextColor.DARK_GREEN, Material.GREEN_WOOL));
        COLOR_OPTIONS.put("aqua", new ColorOption("aqua", "Azul Claro (Aqua)", NamedTextColor.AQUA, Material.LIGHT_BLUE_WOOL));
        COLOR_OPTIONS.put("dark_aqua", new ColorOption("dark_aqua", "Ciano", NamedTextColor.DARK_AQUA, Material.CYAN_WOOL));
        COLOR_OPTIONS.put("blue", new ColorOption("blue", "Azul", NamedTextColor.BLUE, Material.BLUE_WOOL));
        COLOR_OPTIONS.put("dark_blue", new ColorOption("dark_blue", "Azul Escuro", NamedTextColor.DARK_BLUE, Material.LAPIS_BLOCK));
        COLOR_OPTIONS.put("light_purple", new ColorOption("light_purple", "Rosa / Roxo Claro", NamedTextColor.LIGHT_PURPLE, Material.PINK_WOOL));
        COLOR_OPTIONS.put("dark_purple", new ColorOption("dark_purple", "Roxo Escuro", NamedTextColor.DARK_PURPLE, Material.PURPLE_WOOL));
        COLOR_OPTIONS.put("white", new ColorOption("white", "Branco", NamedTextColor.WHITE, Material.WHITE_WOOL));
        COLOR_OPTIONS.put("gray", new ColorOption("gray", "Cinza Claro", NamedTextColor.GRAY, Material.LIGHT_GRAY_WOOL));
        COLOR_OPTIONS.put("dark_gray", new ColorOption("dark_gray", "Cinza Escuro", NamedTextColor.DARK_GRAY, Material.GRAY_WOOL));
    }

    public TeamGui(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.targetKey = new NamespacedKey(plugin, "gui_target");
    }

    public void setQuestGui(QuestGui questGui) {
        this.questGui = questGui;
    }

    public void openQuestsMenu(Player player) {
        if (this.questGui != null) {
            this.questGui.openQuestsMenu(player);
        }
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public NamespacedKey getTargetKey() {
        return targetKey;
    }

    public static Map<String, ColorOption> getColorOptions() {
        return COLOR_OPTIONS;
    }

    /**
     * Abre o menu principal do /team.
     */
    public void openMainMenu(Player player) {
        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        TeamMenuHolder holder = new TeamMenuHolder(MenuType.MAIN);
        Inventory inv = Bukkit.createInventory(
                holder,
                27,
                Component.text("Gerenciamento de Times", NamedTextColor.DARK_GRAY)
        );
        holder.setInventory(inv);

        fillBackground(inv);

        if (team == null) {
            // Jogador sem time
            TeamManager.PlayerStats myStats = teamManager.getPlayerStats(player.getUniqueId());
            inv.setItem(4, createActionItem(
                    Material.IRON_SWORD,
                    Component.text("Suas Estatisticas PvP", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Abates (Kills): ", NamedTextColor.GRAY)
                                    .append(Component.text(String.valueOf(myStats.kills()), NamedTextColor.GREEN))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Mortes (PvP): ", NamedTextColor.GRAY)
                                    .append(Component.text(String.valueOf(myStats.deaths()), NamedTextColor.RED))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Placar no Tab: ", NamedTextColor.GRAY)
                                    .append(teamManager.formatStatsBracket(myStats.kills(), myStats.deaths()))
                                    .decoration(TextDecoration.ITALIC, false)
                    ),
                    "none",
                    null
            ));

            if (teamManager.canCreateTeam(player)) {
                inv.setItem(11, createActionItem(
                        Material.ANVIL,
                        Component.text("Criar um Novo Time", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                        List.of(
                                Component.text("Clique para criar seu proprio time", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                                Component.text("digitando o nome no chat!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        ),
                        "prompt_create",
                        null
                ));
            } else {
                inv.setItem(11, createActionItem(
                        Material.BARRIER,
                        Component.text("Criar Time (Restrito a Líderes)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                        List.of(
                                Component.text("Apenas líderes autorizados pelo", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                                Component.text("administrador podem criar equipes.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                        ),
                        "none",
                        null
                ));
            }

            Team pendingInvite = teamManager.getPendingInvite(player.getUniqueId());
            if (pendingInvite != null) {
                inv.setItem(13, createActionItem(
                        Material.WRITABLE_BOOK,
                        Component.text("Aceitar Convite Pendente", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                        List.of(
                                Component.text("Time: ", NamedTextColor.GRAY)
                                        .append(Component.text(pendingInvite.getName() + " ", NamedTextColor.WHITE))
                                        .append(pendingInvite.getFormattedTag())
                                        .decoration(TextDecoration.ITALIC, false),
                                Component.empty(),
                                Component.text("Clique para entrar neste time!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                        ),
                        "accept_invite",
                        pendingInvite.getName()
                ));
            } else {
                inv.setItem(13, createActionItem(
                        Material.BOOK,
                        Component.text("Convites Pendentes", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                        List.of(
                                Component.text("Voce nao possui nenhum convite", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                                Component.text("de time no momento.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
                        ),
                        "none",
                        null
                ));
            }

            inv.setItem(15, createActionItem(
                    Material.COMPASS,
                    Component.text("Listar Todos os Times", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Veja todos os times criados", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("no servidor.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_list",
                    null
            ));

            inv.setItem(22, createActionItem(
                    Material.WRITABLE_BOOK,
                    Component.text("📜 Quests Diárias de Equipe", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Veja as 5 missões diárias do servidor!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("Reseta todo dia às 00:00.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_quests",
                    null
            ));
        } else {
            // Jogador ja pertence a um time
            OfflinePlayer leaderOffline = Bukkit.getOfflinePlayer(team.getLeader());
            String leaderName = leaderOffline.getName() != null ? leaderOffline.getName() : "Desconhecido";
            boolean isLeader = team.isLeader(player.getUniqueId());
            int teamPoints = teamManager.getTeamPoints(team);
            int teamKills = teamManager.getTeamTotalKills(team);
            int teamDeaths = teamManager.getTeamTotalDeaths(team);
            double teamKdr = teamDeaths == 0 ? teamKills : (double) teamKills / teamDeaths;
            TeamManager.PlayerStats myStats = teamManager.getPlayerStats(player.getUniqueId());

            inv.setItem(4, createActionItem(
                    Material.SHIELD,
                    Component.text("Time: " + team.getName() + " ", NamedTextColor.GOLD)
                            .append(team.getFormattedTag())
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("⭐ Pontos da Equipe: ", NamedTextColor.GOLD)
                                    .append(Component.text(teamPoints + " pts", NamedTextColor.YELLOW, TextDecoration.BOLD))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Lider: ", NamedTextColor.GRAY)
                                    .append(Component.text(leaderName, NamedTextColor.YELLOW))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Membros: ", NamedTextColor.GRAY)
                                    .append(Component.text(String.valueOf(team.getMembers().size()), NamedTextColor.GREEN))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Kills do Time: ", NamedTextColor.GRAY)
                                    .append(Component.text(String.valueOf(teamKills), NamedTextColor.GREEN))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Mortes do Time (PvP): ", NamedTextColor.GRAY)
                                    .append(Component.text(String.valueOf(teamDeaths), NamedTextColor.RED))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Placar Geral: ", NamedTextColor.GRAY)
                                    .append(teamManager.formatStatsBracket(teamKills, teamDeaths))
                                    .append(Component.text(String.format(" (K/D: %.2f)", teamKdr), NamedTextColor.GOLD))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Seu Placar: ", NamedTextColor.GRAY)
                                    .append(teamManager.formatStatsBracket(myStats.kills(), myStats.deaths()))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Fogo Amigo: ", NamedTextColor.GRAY)
                                    .append(Component.text("Desativado", NamedTextColor.RED))
                                    .decoration(TextDecoration.ITALIC, false)
                    ),
                    "none",
                    null
            ));

            inv.setItem(10, createActionItem(
                    Material.PLAYER_HEAD,
                    Component.text("Membros do Time", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Veja os membros, promova um novo", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("lider ou expulse jogadores.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_members",
                    null
            ));

            inv.setItem(12, createActionItem(
                    Material.EMERALD,
                    Component.text("Convidar Jogador", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text(isLeader ? "Clique para escolher um jogador online" : "Apenas o lider pode convidar jogadores",
                                    isLeader ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_invite",
                    null
            ));

            inv.setItem(13, createActionItem(
                    Material.WRITABLE_BOOK,
                    Component.text("📜 Quests Diárias do Time", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("2 Fáceis, 2 Médias e 1 Difícil", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                            Component.text("Progresso somado entre toda a equipe!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Clique para abrir as missões (/quest)", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_quests",
                    null
            ));

            inv.setItem(14, createActionItem(
                    Material.CYAN_DYE,
                    Component.text("Cor da Tag", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Tag atual: ", NamedTextColor.GRAY)
                                    .append(team.getFormattedTag())
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text(isLeader ? "Clique para escolher uma nova cor!" : "Apenas o lider pode alterar a cor",
                                    isLeader ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_colors",
                    null
            ));

            inv.setItem(16, createActionItem(
                    Material.NAME_TAG,
                    Component.text("Alterar Tag", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Tag atual: ", NamedTextColor.GRAY)
                                    .append(team.getFormattedTag())
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text(isLeader ? "Clique para digitar uma nova tag no chat!" : "Apenas o lider pode alterar a tag",
                                    isLeader ? NamedTextColor.GRAY : NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
                    ),
                    "prompt_tag",
                    null
            ));

            inv.setItem(21, createActionItem(
                    Material.COMPASS,
                    Component.text("Todos os Times", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Veja a lista de todos os times", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("registrados no servidor.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "open_list",
                    null
            ));

            inv.setItem(22, createActionItem(
                    Material.GOAT_HORN,
                    Component.text("Team Group (Voice Chat)", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Clique para entrar no canal de voz", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("do seu time (ou aperte V -> Group)!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "join_voice_group",
                    null
            ));

            inv.setItem(23, createActionItem(
                    Material.BARRIER,
                    Component.text(isLeader ? "Desfazer Time" : "Sair do Time", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text(isLeader
                                            ? "Clique para encerrar o time."
                                            : "Clique para sair do time atual.",
                                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "leave_or_disband",
                    null
            ));
        }

        player.openInventory(inv);
    }

    /**
     * Abre o menu de membros do time (com acoes de promover a lider e expulsar).
     */
    public void openMembersMenu(Player player, Team team) {
        TeamMenuHolder holder = new TeamMenuHolder(MenuType.MEMBERS);
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("Membros: " + team.getName(), NamedTextColor.DARK_GRAY)
        );
        holder.setInventory(inv);

        boolean viewerIsLeader = team.isLeader(player.getUniqueId());
        int slot = 0;

        for (UUID memberId : team.getMembers()) {
            if (slot >= 27) {
                break;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(memberId);
            String memberName = offline.getName() != null ? offline.getName() : memberId.toString().substring(0, 8);
            boolean isMemberLeader = team.isLeader(memberId);
            boolean isOnline = offline.isOnline();

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(offline);
            meta.displayName(
                    Component.text(memberName, isMemberLeader ? NamedTextColor.GOLD : NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> lore = new ArrayList<>();
            TeamManager.PlayerStats mStats = teamManager.getPlayerStats(memberId);
            lore.add(Component.text("Cargo: ", NamedTextColor.GRAY)
                    .append(Component.text(isMemberLeader ? "Lider" : "Membro", isMemberLeader ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                    .append(Component.text(isOnline ? "Online" : "Offline", isOnline ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("PvP [Kills, Mortes]: ", NamedTextColor.GRAY)
                    .append(teamManager.formatStatsBracket(mStats.kills(), mStats.deaths()))
                    .decoration(TextDecoration.ITALIC, false));

            if (viewerIsLeader && !memberId.equals(player.getUniqueId())) {
                lore.add(Component.empty());
                lore.add(Component.text("Clique Esquerdo: ", NamedTextColor.YELLOW)
                        .append(Component.text("Transferir Lideranca", NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Clique Direito: ", NamedTextColor.RED)
                        .append(Component.text("Expulsar do Time (Kick)", NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "member_manage");
                meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, memberName);
            }

            meta.lore(lore);
            skull.setItemMeta(meta);
            inv.setItem(slot++, skull);
        }

        inv.setItem(31, createBackButton());
        player.openInventory(inv);
    }

    /**
     * Abre o menu para convidar jogadores online que ainda nao possuem time.
     */
    public void openInviteMenu(Player player) {
        TeamMenuHolder holder = new TeamMenuHolder(MenuType.INVITE);
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("Convidar Jogador Online", NamedTextColor.DARK_GRAY)
        );
        holder.setInventory(inv);

        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 27) {
                break;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (teamManager.getPlayerTeam(target.getUniqueId()) != null) {
                continue;
            }

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Clique para enviar um convite", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("para o seu time!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "invite_player");
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target.getName());
            skull.setItemMeta(meta);

            inv.setItem(slot++, skull);
        }

        if (slot == 0) {
            inv.setItem(13, createActionItem(
                    Material.STRUCTURE_VOID,
                    Component.text("Nenhum jogador disponivel", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Nao ha outros jogadores online", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("sem time no momento.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "none",
                    null
            ));
        }

        inv.setItem(31, createBackButton());
        player.openInventory(inv);
    }

    /**
     * Abre o seletor visual de cores para a tag do time.
     */
    public void openColorPickerMenu(Player player) {
        TeamMenuHolder holder = new TeamMenuHolder(MenuType.COLOR_PICKER);
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("Escolher Cor da Tag", NamedTextColor.DARK_GRAY)
        );
        holder.setInventory(inv);

        Team team = teamManager.getPlayerTeam(player.getUniqueId());
        String sampleTag = team != null ? team.getTag() : "TAG";

        int slot = 0;
        for (ColorOption option : COLOR_OPTIONS.values()) {
            inv.setItem(slot++, createActionItem(
                    option.icon(),
                    Component.text(option.displayName(), option.color()).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Previa: ", NamedTextColor.GRAY)
                                    .append(Component.text("[" + sampleTag + "]", option.color()))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Clique para aplicar esta cor!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                    ),
                    "select_color",
                    option.id()
            ));
        }

        inv.setItem(31, createBackButton());
        player.openInventory(inv);
    }

    /**
     * Abre a lista visual de todos os times do servidor.
     */
    public void openTeamListMenu(Player player) {
        TeamMenuHolder holder = new TeamMenuHolder(MenuType.TEAM_LIST);
        Inventory inv = Bukkit.createInventory(
                holder,
                36,
                Component.text("Times do Servidor", NamedTextColor.DARK_GRAY)
        );
        holder.setInventory(inv);

        int slot = 0;
        for (Team team : teamManager.getAllTeams()) {
            if (slot >= 27) {
                break;
            }
            OfflinePlayer leader = Bukkit.getOfflinePlayer(team.getLeader());
            String leaderName = leader.getName() != null ? leader.getName() : "Desconhecido";
            int tPoints = teamManager.getTeamPoints(team);
            int tKills = teamManager.getTeamTotalKills(team);
            int tDeaths = teamManager.getTeamTotalDeaths(team);

            inv.setItem(slot++, createActionItem(
                    Material.WHITE_BANNER,
                    Component.text(team.getName() + " ", NamedTextColor.GOLD)
                            .append(team.getFormattedTag())
                            .decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("⭐ Pontos: ", NamedTextColor.GOLD)
                                    .append(Component.text(tPoints + " pts", NamedTextColor.YELLOW, TextDecoration.BOLD))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Lider: ", NamedTextColor.GRAY)
                                    .append(Component.text(leaderName, NamedTextColor.YELLOW))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Membros: ", NamedTextColor.GRAY)
                                    .append(Component.text(String.valueOf(team.getMembers().size()), NamedTextColor.GREEN))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("PvP do Time: ", NamedTextColor.GRAY)
                                    .append(teamManager.formatStatsBracket(tKills, tDeaths))
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.empty(),
                            Component.text("Clique para ver os membros!", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
                    ),
                    "view_team_members",
                    team.getName()
            ));
        }

        if (slot == 0) {
            inv.setItem(13, createActionItem(
                    Material.BARRIER,
                    Component.text("Nenhum time criado ainda", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(
                            Component.text("Use /team create <nome> para", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                            Component.text("criar o primeiro time!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                    ),
                    "none",
                    null
            ));
        }

        inv.setItem(31, createBackButton());
        player.openInventory(inv);
    }

    private void fillBackground(Inventory inv) {
        ItemStack glass = createActionItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "),
                List.of(),
                "none",
                null
        );
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }
    }

    private ItemStack createBackButton() {
        return createActionItem(
                Material.ARROW,
                Component.text("Voltar ao Menu Principal", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                List.of(),
                "back_main",
                null
        );
    }

    private ItemStack createActionItem(
            Material material,
            Component name,
            List<Component> lore,
            String action,
            String target
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (target != null) {
            meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, target);
        }
        item.setItemMeta(meta);
        return item;
    }
}
