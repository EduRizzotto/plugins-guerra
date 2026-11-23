package com.eduardo.teams.base;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.base.BaseManager.TeamBase;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;

/**
 * Aplica as regras de protecao dentro da area de 30 blocos de uma Base (/base criar):
 * 1) Proibido colocar lava (balde, dispenser ou escorrer de fora para dentro)
 * 2) Cada time pode colocar e explodir no maximo 5 TNTs dentro da area da base
 * 3) Qualquer outro bloco pode ser colocado e quebrado livremente
 */
public class BaseListener implements Listener {

    private final BaseManager baseManager;
    private final TeamManager teamManager;
    private final NamespacedKey countedTntKey;

    public BaseListener(TeamsPlugin plugin, BaseManager baseManager, TeamManager teamManager) {
        this.baseManager = baseManager;
        this.teamManager = teamManager;
        this.countedTntKey = new NamespacedKey(plugin, "base_counted_tnt");
    }

    // =========================================================================
    // 1. BLOQUEIO DE LAVA DENTRO DA BASE
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLavaBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) {
            return;
        }
        TeamBase base = baseManager.getBaseAt(event.getBlock().getLocation());
        if (base == null) {
            base = baseManager.getBaseAt(event.getBlockClicked().getLocation());
        }
        if (base != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("🔥 Não é permitido colocar lava dentro da área da base de ", NamedTextColor.RED)
                    .append(Component.text(base.getTeamDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.RED)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenseLavaOrTnt(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        Block block = event.getBlock();
        Block targetBlock = block;
        if (block.getBlockData() instanceof Dispenser dispenser) {
            targetBlock = block.getRelative(dispenser.getFacing());
        }

        TeamBase base = baseManager.getBaseAt(targetBlock.getLocation());
        if (base == null) {
            base = baseManager.getBaseAt(block.getLocation());
        }
        if (base == null) {
            return;
        }

        if (item.getType() == Material.LAVA_BUCKET) {
            event.setCancelled(true);
            return;
        }

        if (item.getType() == Material.TNT || item.getType() == Material.TNT_MINECART) {
            // Bloqueia ejetor/dispenser de TNT para nao burlar o limite de 5 TNTs por time
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLavaFlowIntoBase(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.LAVA) {
            return;
        }
        TeamBase fromBase = baseManager.getBaseAt(event.getBlock().getLocation());
        TeamBase toBase = baseManager.getBaseAt(event.getToBlock().getLocation());
        // Impede que lava colocada fora da base escorra para dentro da base
        if (toBase != null && fromBase == null) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // 2. LIMITE DE 5 TNTs POR TIME DENTRO DA BASE
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceBlockInBase(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Material type = block.getType();

        TeamBase base = baseManager.getBaseAt(block.getLocation());
        if (base == null) {
            return;
        }

        Player player = event.getPlayer();
        if (type == Material.LAVA) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🔥 Não é permitido colocar lava dentro da área desta base!", NamedTextColor.RED));
            return;
        }

        Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
        boolean isEnemyBase = (playerTeam == null || !base.getTeamKey().equalsIgnoreCase(playerTeam.getName()));

        if (type == Material.RESPAWN_ANCHOR && isEnemyBase) {
            event.setCancelled(true);
            player.sendMessage(Component.text("🔒 Você não pode colocar Âncora de Renascimento dentro de uma base inimiga!", NamedTextColor.RED));
            return;
        }

        if (type != Material.TNT) {
            // Qualquer outro bloco e 100% permitido!
            return;
        }

        if (playerTeam == null) {
            event.setCancelled(true);
            player.sendMessage(Component.text("💣 Você precisa estar em um time para colocar TNT dentro de uma base!", NamedTextColor.RED));
            return;
        }

        int used = base.getTntUsed(playerTeam.getName());
        if (used >= BaseManager.MAX_TNT_PER_TEAM) {
            event.setCancelled(true);
            String remaining = BaseManager.formatDuration(base.getRemainingResetMs(playerTeam.getName()));
            player.sendMessage(Component.text("💣 Limite atingido! Seu time já usou as ", NamedTextColor.RED)
                    .append(Component.text(BaseManager.MAX_TNT_PER_TEAM + "/" + BaseManager.MAX_TNT_PER_TEAM + " TNTs", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" (a cada 3 dias) na base de ", NamedTextColor.RED))
                    .append(Component.text(base.getTeamDisplayName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("! Reseta em: ", NamedTextColor.RED))
                    .append(Component.text(remaining, NamedTextColor.AQUA, TextDecoration.BOLD)));
            return;
        }

        int nowUsed = base.incrementTntUsed(playerTeam.getName());
        baseManager.registerPlacedTntBlock(block.getLocation(), playerTeam.getName());
        baseManager.saveBases();

        player.sendMessage(Component.text("💣 TNT colocada na base de ", NamedTextColor.YELLOW)
                .append(Component.text(base.getTeamDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("! (", NamedTextColor.YELLOW))
                .append(Component.text(nowUsed + "/" + BaseManager.MAX_TNT_PER_TEAM, NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" TNTs usadas pelo seu time neste ciclo de 3 dias)", NamedTextColor.YELLOW)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceTntMinecartInBase(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        ItemStack item = event.getItem();
        TeamBase base = baseManager.getBaseAt(clickedBlock.getLocation());
        if (base == null) {
            base = baseManager.getBaseAt(clickedBlock.getRelative(event.getBlockFace()).getLocation());
        }
        if (base == null) {
            return;
        }

        if (item != null && item.getType() == Material.TNT_MINECART) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("💣 Carrinhos com TNT não são permitidos dentro da área protegida da base! Use TNT normal (máx. 5 por time).", NamedTextColor.RED));
            return;
        }

        Player player = event.getPlayer();
        Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
        boolean isEnemyBase = (playerTeam == null || !base.getTeamKey().equalsIgnoreCase(playerTeam.getName()));

        if (isEnemyBase) {
            if (item != null && item.getType() == Material.END_CRYSTAL) {
                event.setCancelled(true);
                player.sendMessage(Component.text("🔒 Você não pode colocar Cristal do End dentro de uma base inimiga!", NamedTextColor.RED));
                return;
            }
            if (clickedBlock.getType() == Material.RESPAWN_ANCHOR) {
                event.setCancelled(true);
                player.sendMessage(Component.text("🔒 Você não pode usar Âncora de Renascimento dentro de uma base inimiga!", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakUnignitedTnt(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.TNT) {
            return;
        }
        TeamBase base = baseManager.getBaseAt(block.getLocation());
        if (base == null) {
            return;
        }
        // Se alguem quebrou a TNT antes de acender/explodir, devolve 1 uso da cota daquele time
        String teamKey = baseManager.consumePlacedTntBlock(block.getLocation());
        if (teamKey != null) {
            base.decrementTntUsed(teamKey);
            baseManager.saveBases();
            int current = base.getTntUsed(teamKey);
            event.getPlayer().sendMessage(Component.text("💣 TNT recolhida antes de explodir! Uso atual do time nesta base: ", NamedTextColor.GRAY)
                    .append(Component.text(current + "/" + BaseManager.MAX_TNT_PER_TEAM, NamedTextColor.YELLOW, TextDecoration.BOLD)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTntPrimedSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed tnt)) {
            return;
        }
        String teamKey = baseManager.consumePlacedTntBlock(tnt.getLocation());
        if (teamKey != null) {
            // Marca que essa TNT acesa ja foi contabilizada quando o bloco foi colocado dentro da base
            tnt.getPersistentDataContainer().set(countedTntKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTntExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof TNTPrimed) && !(entity instanceof ExplosiveMinecart)) {
            return;
        }

        // Se essa TNT ja foi colocada e contabilizada dentro da base, deixa explodir normalmente
        if (entity.getPersistentDataContainer().has(countedTntKey, PersistentDataType.BYTE)) {
            return;
        }

        // Verifica se a explosao atinge alguma base (ex: TNT arremessada de fora da borda de 30 blocos)
        Set<TeamBase> affectedBases = new HashSet<>();
        TeamBase centerBase = baseManager.getBaseAt(event.getLocation());
        if (centerBase != null) {
            affectedBases.add(centerBase);
        }
        for (Block b : event.blockList()) {
            TeamBase bBase = baseManager.getBaseAt(b.getLocation());
            if (bBase != null) {
                affectedBases.add(bBase);
            }
        }
        if (affectedBases.isEmpty()) {
            return;
        }

        Player sourcePlayer = null;
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
            sourcePlayer = p;
        }
        Team sourceTeam = sourcePlayer != null ? teamManager.getPlayerTeam(sourcePlayer.getUniqueId()) : null;

        for (TeamBase base : affectedBases) {
            if (sourceTeam == null || base.getTntUsed(sourceTeam.getName()) >= BaseManager.MAX_TNT_PER_TEAM) {
                // Bloqueia destruicao de blocos dentro dessa base se veio de fora sem time ou acima do limite de 5 TNTs
                event.blockList().removeIf(b -> base.contains(b.getLocation()));
                if (sourcePlayer != null && sourceTeam != null) {
                    sourcePlayer.sendMessage(Component.text("💣 Seu time já atingiu o limite de ", NamedTextColor.RED)
                            .append(Component.text(BaseManager.MAX_TNT_PER_TEAM + "/" + BaseManager.MAX_TNT_PER_TEAM + " TNTs", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text(" na base de " + base.getTeamDisplayName() + "!", NamedTextColor.RED)));
                }
            } else {
                int nowUsed = base.incrementTntUsed(sourceTeam.getName());
                baseManager.saveBases();
                sourcePlayer.sendMessage(Component.text("💣 TNT explodiu na base de ", NamedTextColor.YELLOW)
                        .append(Component.text(base.getTeamDisplayName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text("! (", NamedTextColor.YELLOW))
                        .append(Component.text(nowUsed + "/" + BaseManager.MAX_TNT_PER_TEAM, NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.text(" TNTs usadas pelo seu time)", NamedTextColor.YELLOW)));
            }
        }
    }
}
