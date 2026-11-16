package com.eduardo.teams;

import com.eduardo.teams.board.PlacarCommand;
import com.eduardo.teams.board.WallBoardListener;
import com.eduardo.teams.board.WallBoardManager;
import com.eduardo.teams.command.TeamCommand;
import com.eduardo.teams.gui.TeamGui;
import com.eduardo.teams.listener.CombatListener;
import com.eduardo.teams.listener.GuiListener;
import com.eduardo.teams.listener.PlayerChatAndJoinListener;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.voice.TeamVoicechatIntegration;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeamsPlugin extends JavaPlugin {

    private TeamManager teamManager;
    private WallBoardManager wallBoardManager;

    @Override
    public void onEnable() {
        this.teamManager = new TeamManager(this);
        TeamGui teamGui = new TeamGui(this, this.teamManager);
        TeamCommand teamCommand = new TeamCommand(this.teamManager, teamGui);

        this.wallBoardManager = new WallBoardManager(this, this.teamManager);
        PlacarCommand placarCommand = new PlacarCommand(this, this.wallBoardManager);

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

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new GuiListener(this.teamManager, teamGui, teamCommand), this);
        pm.registerEvents(new CombatListener(this.teamManager), this);
        pm.registerEvents(new PlayerChatAndJoinListener(this, this.teamManager, teamCommand), this);
        pm.registerEvents(new WallBoardListener(this, this.wallBoardManager), this);

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
        if (this.wallBoardManager != null) {
            this.wallBoardManager.shutdown();
        }
        if (this.teamManager != null) {
            this.teamManager.saveTeams();
        }
        getLogger().info("TeamsPlugin desativado e dados salvos.");
    }
}
