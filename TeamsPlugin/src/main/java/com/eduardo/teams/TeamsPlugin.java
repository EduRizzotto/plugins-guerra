package com.eduardo.teams;

import com.eduardo.teams.airdrop.AirDropCommand;
import com.eduardo.teams.airdrop.AirDropListener;
import com.eduardo.teams.airdrop.AirDropManager;
import com.eduardo.teams.aura.AuraCommand;
import com.eduardo.teams.aura.AuraGui;
import com.eduardo.teams.aura.AuraListener;
import com.eduardo.teams.aura.AuraManager;
import com.eduardo.teams.base.BaseCommand;
import com.eduardo.teams.base.BaseListener;
import com.eduardo.teams.base.BaseManager;
import com.eduardo.teams.board.PlacarCommand;
import com.eduardo.teams.board.WallBoardListener;
import com.eduardo.teams.board.WallBoardManager;
import com.eduardo.teams.command.LeaderCommand;
import com.eduardo.teams.command.TeamCommand;
import com.eduardo.teams.compass.EventCompassListener;
import com.eduardo.teams.compass.EventCompassManager;
import com.eduardo.teams.ctf.CtfCommand;
import com.eduardo.teams.ctf.CtfListener;
import com.eduardo.teams.ctf.CtfManager;
import com.eduardo.teams.dimension.DimensionLockCommand;
import com.eduardo.teams.dimension.DimensionLockListener;
import com.eduardo.teams.dimension.DimensionLockManager;
import com.eduardo.teams.doom.DoomCommand;
import com.eduardo.teams.doom.DoomListener;
import com.eduardo.teams.doom.DoomManager;
import com.eduardo.teams.endstorm.EndStormCommand;
import com.eduardo.teams.endstorm.EndStormListener;
import com.eduardo.teams.endstorm.EndStormManager;
import com.eduardo.teams.netherstorm.NetherStormCommand;
import com.eduardo.teams.netherstorm.NetherStormListener;
import com.eduardo.teams.netherstorm.NetherStormManager;
import com.eduardo.teams.gui.TeamGui;
import com.eduardo.teams.horn.TrackerHornListener;
import com.eduardo.teams.horn.TrackerHornManager;
import com.eduardo.teams.hunt.HuntCommand;
import com.eduardo.teams.hunt.HuntListener;
import com.eduardo.teams.hunt.HuntManager;
import com.eduardo.teams.kit.KitCommand;
import com.eduardo.teams.kit.KitGui;
import com.eduardo.teams.kit.KitListener;
import com.eduardo.teams.kit.KitManager;
import com.eduardo.teams.koth.KothCommand;
import com.eduardo.teams.koth.KothListener;
import com.eduardo.teams.koth.KothManager;
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
import com.eduardo.teams.zone.ZoneCommand;
import com.eduardo.teams.zone.ZoneListener;
import com.eduardo.teams.zone.ZoneManager;
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
    private KothManager kothManager;
    private DoomManager doomManager;
    private DimensionLockManager dimensionLockManager;
    private KitManager kitManager;
    private AuraManager auraManager;
    private ZoneManager zoneManager;
    private BaseManager baseManager;
    private CtfManager ctfManager;
    private EndStormManager endStormManager;
    private NetherStormManager netherStormManager;
    private CombatListener combatListener;

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

        this.kothManager = new KothManager(this, this.teamManager);
        this.kothManager.setCompassManager(this.compassManager);
        KothCommand kothCommand = new KothCommand(this.kothManager);

        this.doomManager = new DoomManager(this, this.teamManager);
        this.doomManager.setCompassManager(this.compassManager);
        DoomCommand doomCommand = new DoomCommand(this.doomManager);

        this.dimensionLockManager = new DimensionLockManager(this);
        DimensionLockCommand openCmdExecutor = new DimensionLockCommand(this.dimensionLockManager, true);
        DimensionLockCommand closeCmdExecutor = new DimensionLockCommand(this.dimensionLockManager, false);

        this.kitManager = new KitManager(this);
        KitGui kitGui = new KitGui(this, this.kitManager);
        KitCommand kitCommand = new KitCommand(this.kitManager, kitGui);

        this.auraManager = new AuraManager(this);
        AuraGui auraGui = new AuraGui(this, this.auraManager);
        AuraCommand auraCommand = new AuraCommand(this.auraManager, auraGui);

        this.zoneManager = new ZoneManager(this);
        ZoneCommand protegerCommand = new ZoneCommand(this.zoneManager, false);
        ZoneCommand safezoneCommand = new ZoneCommand(this.zoneManager, true);

        this.baseManager = new BaseManager(this);
        BaseCommand baseCommand = new BaseCommand(this.baseManager, this.teamManager);

        this.ctfManager = new CtfManager(this, this.teamManager, this.baseManager);
        baseCommand.setCtfManager(this.ctfManager);
        CtfCommand ctfCommand = new CtfCommand(this.ctfManager);

        this.endStormManager = new EndStormManager(this, this.teamManager);
        this.endStormManager.setCompassManager(this.compassManager);
        EndStormCommand endStormCommand = new EndStormCommand(this.endStormManager);

        this.netherStormManager = new NetherStormManager(this, this.teamManager);
        this.netherStormManager.setCompassManager(this.compassManager);
        NetherStormCommand netherStormCommand = new NetherStormCommand(this.netherStormManager);

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

        PluginCommand dominioCmd = getCommand("dominio");
        if (dominioCmd != null) {
            dominioCmd.setExecutor(kothCommand);
            dominioCmd.setTabCompleter(kothCommand);
        }

        for (String doomCmdName : new String[]{"doom", "doom2", "doom3"}) {
            PluginCommand doomCmd = getCommand(doomCmdName);
            if (doomCmd != null) {
                doomCmd.setExecutor(doomCommand);
                doomCmd.setTabCompleter(doomCommand);
            }
        }

        PluginCommand openCmd = getCommand("open");
        if (openCmd != null) {
            openCmd.setExecutor(openCmdExecutor);
            openCmd.setTabCompleter(openCmdExecutor);
        }

        PluginCommand closeCmd = getCommand("close");
        if (closeCmd != null) {
            closeCmd.setExecutor(closeCmdExecutor);
            closeCmd.setTabCompleter(closeCmdExecutor);
        }

        LeaderCommand leaderCommand = new LeaderCommand(this.teamManager);
        PluginCommand leaderCmd = getCommand("leader");
        if (leaderCmd != null) {
            leaderCmd.setExecutor(leaderCommand);
            leaderCmd.setTabCompleter(leaderCommand);
        }

        PluginCommand kitCmd = getCommand("kit");
        if (kitCmd != null) {
            kitCmd.setExecutor(kitCommand);
            kitCmd.setTabCompleter(kitCommand);
        }

        PluginCommand auraCmd = getCommand("aura");
        if (auraCmd != null) {
            auraCmd.setExecutor(auraCommand);
            auraCmd.setTabCompleter(auraCommand);
        }

        PluginCommand protegerCmd = getCommand("proteger");
        if (protegerCmd != null) {
            protegerCmd.setExecutor(protegerCommand);
            protegerCmd.setTabCompleter(protegerCommand);
        }

        PluginCommand safezoneCmd = getCommand("safezone");
        if (safezoneCmd != null) {
            safezoneCmd.setExecutor(safezoneCommand);
            safezoneCmd.setTabCompleter(safezoneCommand);
        }

        PluginCommand baseCmd = getCommand("base");
        if (baseCmd != null) {
            baseCmd.setExecutor(baseCommand);
            baseCmd.setTabCompleter(baseCommand);
        }

        PluginCommand ctfCmd = getCommand("capturetheflag");
        if (ctfCmd != null) {
            ctfCmd.setExecutor(ctfCommand);
            ctfCmd.setTabCompleter(ctfCommand);
        }

        PluginCommand endStormCmd = getCommand("tempestadeend");
        if (endStormCmd != null) {
            endStormCmd.setExecutor(endStormCommand);
            endStormCmd.setTabCompleter(endStormCommand);
        }

        PluginCommand netherStormCmd = getCommand("tempestadenether");
        if (netherStormCmd != null) {
            netherStormCmd.setExecutor(netherStormCommand);
            netherStormCmd.setTabCompleter(netherStormCommand);
        }

        TrackerHornListener hornListener = new TrackerHornListener(this.trackerHornManager, this.teamManager);
        hornListener.setQuestManager(this.questManager);

        AirDropListener airDropListener = new AirDropListener(this.airDropManager, this.teamManager);
        airDropListener.setQuestManager(this.questManager);

        this.combatListener = new CombatListener(this, this.teamManager);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new GuiListener(this.teamManager, teamGui, teamCommand), this);
        pm.registerEvents(this.combatListener, this);
        pm.registerEvents(new PlayerChatAndJoinListener(this, this.teamManager, teamCommand), this);
        pm.registerEvents(new WallBoardListener(this, this.wallBoardManager), this);
        pm.registerEvents(hornListener, this);
        pm.registerEvents(airDropListener, this);
        pm.registerEvents(new HuntListener(this, this.huntManager), this);
        pm.registerEvents(new QuestListener(this.questManager, this.teamManager, questGui, teamGui), this);
        pm.registerEvents(new RelicListener(this, this.relicManager), this);
        pm.registerEvents(new KothListener(this.kothManager), this);
        pm.registerEvents(new DoomListener(this.doomManager, this.teamManager), this);
        pm.registerEvents(new DimensionLockListener(this.dimensionLockManager), this);
        pm.registerEvents(new KitListener(this.kitManager, kitGui), this);
        pm.registerEvents(new AuraListener(this.auraManager, auraGui), this);
        pm.registerEvents(new ZoneListener(this.zoneManager), this);
        pm.registerEvents(new BaseListener(this, this.baseManager, this.teamManager), this);
        pm.registerEvents(new CtfListener(this.ctfManager), this);
        pm.registerEvents(new EndStormListener(this.endStormManager), this);
        pm.registerEvents(new NetherStormListener(this.netherStormManager), this);
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
        if (this.combatListener != null) {
            this.combatListener.shutdown();
        }
        if (this.ctfManager != null) {
            this.ctfManager.shutdown();
        }
        if (this.endStormManager != null) {
            this.endStormManager.shutdown();
        }
        if (this.netherStormManager != null) {
            this.netherStormManager.shutdown();
        }
        if (this.doomManager != null) {
            this.doomManager.shutdown();
        }
        if (this.kothManager != null) {
            this.kothManager.shutdown();
        }
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
        if (this.kitManager != null) {
            this.kitManager.saveData();
        }
        if (this.auraManager != null) {
            this.auraManager.shutdown();
        }
        if (this.zoneManager != null) {
            this.zoneManager.saveZones();
        }
        if (this.baseManager != null) {
            this.baseManager.saveBases();
        }
        getLogger().info("TeamsPlugin desativado e dados salvos.");
    }
}
