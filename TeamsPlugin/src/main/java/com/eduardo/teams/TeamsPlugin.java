package com.eduardo.teams;

import com.eduardo.teams.airdrop.AirDropCommand;
import com.eduardo.teams.airdrop.AirDropListener;
import com.eduardo.teams.airdrop.AirDropManager;
import com.eduardo.teams.board.PlacarCommand;
import com.eduardo.teams.board.WallBoardListener;
import com.eduardo.teams.board.WallBoardManager;
import com.eduardo.teams.command.TeamCommand;
import com.eduardo.teams.compass.EventCompassListener;
import com.eduardo.teams.compass.EventCompassManager;
import com.eduardo.teams.gui.TeamGui;
import com.eduardo.teams.horn.TrackerHornListener;
import com.eduardo.teams.horn.TrackerHornManager;
import com.eduardo.teams.hunt.HuntCommand;
import com.eduardo.teams.hunt.HuntListener;
import com.eduardo.teams.hunt.HuntManager;
import com.eduardo.teams.listener.CombatListener;
import com.eduardo.teams.listener.GuiListener;
import com.eduardo.teams.listener.PlayerChatAndJoinListener;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.quest.QuestCommand;
import com.eduardo.teams.quest.QuestGui;
import com.eduardo.teams.quest.QuestListener;
import com.eduardo.teams.quest.QuestManager;
import com.eduardo.teams.relic.RelicCommand;
import com.eduardo.teams.relic.RelicListener;
import com.eduardo.teams.relic.RelicManager;
import com.eduardo.teams.voice.TeamVoicechatIntegration;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeamsPlugin extends JavaPlugin {

    private TeamManager teamManager;
    private WallBoardManager wallBoardManager;
    private TrackerHornManager trackerHornManager;
    private AirDropManager airDropManager;
    private HuntManager huntManager;
    private QuestManager questManager;
    private RelicManager relicManager;
    private EventCompassManager compassManager;

    @Override
    public void onEnable() {
        this.teamManager = new TeamManager(this);
        this.compassManager = new EventCompassManager(this);
        TeamGui teamGui = new TeamGui(this, this.teamManager);
        TeamCommand teamCommand = new TeamCommand(this.teamManager, teamGui);

        this.questManager = new QuestManager(this, this.teamManager);
        QuestGui questGui = new QuestGui(this.questManager, this.teamManager);
        teamGui.setQuestGui(questGui);
        QuestCommand questCommand = new QuestCommand(this.questManager, questGui);

        this.relicManager = new RelicManager(this, this.teamManager);
        this.relicManager.setCompassManager(this.compassManager);
        RelicCommand relicCommand = new RelicCommand(this.relicManager);

        this.wallBoardManager = new WallBoardManager(this, this.teamManager);
        PlacarCommand placarCommand = new PlacarCommand(this, this.wallBoardManager);

        this.trackerHornManager = new TrackerHornManager(this);
        this.trackerHornManager.registerRecipe();

        this.airDropManager = new AirDropManager(this, this.trackerHornManager);
        this.airDropManager.setCompassManager(this.compassManager);
        AirDropCommand airDropCommand = new AirDropCommand(this.airDropManager);

        this.huntManager = new HuntManager(this, this.teamManager);
        this.huntManager.setQuestManager(this.questManager);
        this.huntManager.setCompassManager(this.compassManager);
        HuntCommand huntCommand = new HuntCommand(this.huntManager);

        PluginCommand cmd = getCommand("team");
        if (cmd != null) {
            cmd.setExecutor(placarCommand.wrapTeamExecutor(teamCommand));
            cmd.setTabCompleter(placarCommand.wrapTeamTabCompleter(teamCommand));
        } else {
            getLogger().severe("Comando 'team' nao foi encontrado no plugin.yml!");
        }

        PluginCommand placarCmd = getCommand("placar");
        if (placarCmd != null) {
            placarCmd.setExecutor(placarCommand);
            placarCmd.setTabCompleter(placarCommand);
        }

        PluginCommand berranteCmd = getCommand("berrante");
        if (berranteCmd != null) {
            berranteCmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Apenas jogadores podem usar este comando.", NamedTextColor.RED));
                    return true;
                }
                if (!player.isOp() && !player.hasPermission("teams.admin")) {
                    player.sendMessage(Component.text("Voce nao tem permissao! Crafte o Berrante Localizador na bancada de trabalho.", NamedTextColor.RED));
                    return true;
                }
                player.getInventory().addItem(this.trackerHornManager.createHornItem(TrackerHornManager.MAX_USES));
                player.sendMessage(Component.text("Voce recebeu um Berrante Localizador (10/10)!", NamedTextColor.GREEN));
                return true;
            });
        }

        PluginCommand airdropCmd = getCommand("airdrop");
        if (airdropCmd != null) {
            airdropCmd.setExecutor(airDropCommand);
            airdropCmd.setTabCompleter(airDropCommand);
        }

        PluginCommand cacadaCmd = getCommand("cacada");
        if (cacadaCmd != null) {
            cacadaCmd.setExecutor(huntCommand);
            cacadaCmd.setTabCompleter(huntCommand);
        }

        PluginCommand questCmd = getCommand("quest");
        if (questCmd != null) {
            questCmd.setExecutor(questCommand);
            questCmd.setTabCompleter(questCommand);
        }

        PluginCommand reliquiaCmd = getCommand("reliquia");
        if (reliquiaCmd != null) {
            reliquiaCmd.setExecutor(relicCommand);
            reliquiaCmd.setTabCompleter(relicCommand);
        }

        TrackerHornListener hornListener = new TrackerHornListener(this.trackerHornManager, this.teamManager);
        hornListener.setQuestManager(this.questManager);

        AirDropListener airDropListener = new AirDropListener(this.airDropManager, this.teamManager);
        airDropListener.setQuestManager(this.questManager);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new GuiListener(this.teamManager, teamGui, teamCommand), this);
        pm.registerEvents(new CombatListener(this.teamManager), this);
        pm.registerEvents(new PlayerChatAndJoinListener(this, this.teamManager, teamCommand), this);
        pm.registerEvents(new WallBoardListener(this, this.wallBoardManager), this);
        pm.registerEvents(hornListener, this);
        pm.registerEvents(airDropListener, this);
        pm.registerEvents(new HuntListener(this, this.huntManager), this);
        pm.registerEvents(new QuestListener(this.questManager, this.teamManager, questGui, teamGui), this);
        pm.registerEvents(new RelicListener(this, this.relicManager), this);
        pm.registerEvents(new EventCompassListener(this.compassManager, null), this);

        BukkitVoicechatService vcService = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (vcService != null) {
            TeamVoicechatIntegration vcIntegration = new TeamVoicechatIntegration(this, this.teamManager);
            this.teamManager.setVoicechatIntegration(vcIntegration);
            vcService.registerPlugin(vcIntegration);
            getLogger().info("Integracao com Simple Voice Chat registrada com sucesso!");
        } else {
            getLogger().info("Simple Voice Chat nao detectado (integracao de voz desativada).");
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            this.teamManager.updatePlayerDisplay(player);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (this.wallBoardManager != null) {
                this.wallBoardManager.refreshAllBoards();
            }
        }, 20L);

        getLogger().info("TeamsPlugin v" + getPluginMeta().getVersion() + " ativado com sucesso (com Placares de Parede)!");
    }

    @Override
    public void onDisable() {
        if (this.compassManager != null) {
            this.compassManager.removeAllEventCompassesFromOnlinePlayers();
        }
        if (this.relicManager != null) {
            this.relicManager.shutdown();
        }
        if (this.questManager != null) {
            this.questManager.shutdown();
        }
        if (this.huntManager != null) {
            this.huntManager.shutdown();
        }
        if (this.airDropManager != null) {
            this.airDropManager.shutdown();
        }
        if (this.trackerHornManager != null) {
            this.trackerHornManager.unregisterRecipe();
        }
        if (this.wallBoardManager != null) {
            this.wallBoardManager.shutdown();
        }
        if (this.teamManager != null) {
            this.teamManager.saveTeams();
        }
        getLogger().info("TeamsPlugin desativado e dados salvos.");
    }
}
