package com.eduardo.teams.ctf;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.base.BaseManager;
import com.eduardo.teams.base.BaseManager.TeamBase;
import com.eduardo.teams.compass.EventCompassManager;
import com.eduardo.teams.compass.EventCompassManager.CompassEventType;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Gerencia o evento Capture The Flag (/capturetheflag).
 * - Gera uma bandeira da cor do time dentro da base (/base criar) de cada equipe.
 * - O jogador que roubar a bandeira de um time adversario e entrar no raio (30 blocos)
 *   da base do seu proprio time captura a bandeira: ela some e o time ganha +100 pontos.
 * - Duracao padrao: 1 hora (3600 segundos).
 * - Todo time que conseguir proteger sua bandeira ate o final ganha +200 pontos.
 */
public class CtfManager {

    public static final int POINTS_CAPTURE_FLAG = 100;
    public static final int POINTS_DEFEND_FLAG = 200;
    public static final int DEFAULT_DURATION_SECONDS = 3600; // 1 hora
    public static final int DROPPED_FLAG_RETURN_SECONDS = 60;

    public enum FlagStatus {
        AT_BASE,
        CARRIED,
        DROPPED,
        CAPTURED
    }

    public static class TeamFlag {
        private final String teamKey;
        private final String teamName;
        private final Material bannerMaterial;
        private final Location flagBlockLocation;
        private final Location supportBlockLocation;
        private BlockData originalFlagBlockData;
        private BlockData originalSupportBlockData;
        private boolean modifiedSupportBlock;

        private FlagStatus status = FlagStatus.AT_BASE;
        private UUID carrierId;
        private String carrierName;
        private String carrierTeamName;
        private boolean pickedUpInsideOwnBase = false;

        private UUID droppedItemEntityId;
        private Location droppedLocation;
        private int droppedRemainingSeconds;

        private String capturedByTeamName;
        private String capturedByPlayerName;

        private UUID hologramEntityId;

        public TeamFlag(String teamKey, String teamName, Material bannerMaterial,
                        Location flagBlockLocation, Location supportBlockLocation) {
            this.teamKey = teamKey.toLowerCase(Locale.ROOT);
            this.teamName = teamName;
            this.bannerMaterial = bannerMaterial;
            this.flagBlockLocation = flagBlockLocation;
            this.supportBlockLocation = supportBlockLocation;
        }

        public String getTeamKey() {
            return teamKey;
        }

        public String getTeamName() {
            return teamName;
        }

        public Material getBannerMaterial() {
            return bannerMaterial;
        }

        public Location getFlagBlockLocation() {
            return flagBlockLocation;
        }

        public Location getSupportBlockLocation() {
            return supportBlockLocation;
        }

        public FlagStatus getStatus() {
            return status;
        }

        public UUID getCarrierId() {
            return carrierId;
        }

        public String getCarrierName() {
            return carrierName;
        }

        public String getCarrierTeamName() {
            return carrierTeamName;
        }

        public UUID getDroppedItemEntityId() {
            return droppedItemEntityId;
        }

        public Location getDroppedLocation() {
            return droppedLocation;
        }

        public int getDroppedRemainingSeconds() {
            return droppedRemainingSeconds;
        }

        public String getCapturedByTeamName() {
            return capturedByTeamName;
        }

        public String getCapturedByPlayerName() {
            return capturedByPlayerName;
        }
    }

    public record CtfCompassTarget(String label, NamedTextColor color, Location location) {}

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final BaseManager baseManager;
    private final NamespacedKey ctfFlagKey;
    private EventCompassManager compassManager;

    private final Map<String, TeamFlag> flagsByTeamKey = new LinkedHashMap<>();
    private final Map<UUID, Integer> playerCompassTargetIndex = new HashMap<>();
    private final Map<UUID, Long> compassActionBarUntil = new HashMap<>();
    private boolean active = false;
    private int totalDurationSeconds = DEFAULT_DURATION_SECONDS;
    private int remainingSeconds = 0;
    private BukkitTask tickTask;
    private BossBar bossBar;

    public CtfManager(TeamsPlugin plugin, TeamManager teamManager, BaseManager baseManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.baseManager = baseManager;
        this.ctfFlagKey = new NamespacedKey(plugin, "ctf_flag_team");
    }

    public void setCompassManager(EventCompassManager compassManager) {
        this.compassManager = compassManager;
    }

    public EventCompassManager getCompassManager() {
        return compassManager;
    }

    public boolean isActive() {
        return active;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public Collection<TeamFlag> getAllFlags() {
        return Collections.unmodifiableCollection(flagsByTeamKey.values());
    }

    public TeamFlag getFlagByTeam(String teamName) {
        if (teamName == null) {
            return null;
        }
        return flagsByTeamKey.get(teamName.toLowerCase(Locale.ROOT));
    }

    /**
     * Inicia o evento Capture The Flag gerando a bandeira da cor de cada time dentro de sua base.
     */
    public boolean startEvent(int durationSeconds, org.bukkit.command.CommandSender initiator) {
        if (active) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("🚩 O evento Capture The Flag já está em andamento! Use /capturetheflag parar ou /capturetheflag status.", NamedTextColor.RED));
            }
            return false;
        }

        Collection<TeamBase> allBases = baseManager.getAllBases();
        if (allBases.isEmpty()) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("🚩 Nenhum time possui base criada (/base criar)! Crie as bases dos times antes de iniciar o /capturetheflag.", NamedTextColor.RED));
            }
            return false;
        }

        flagsByTeamKey.clear();
        playerCompassTargetIndex.clear();
        compassActionBarUntil.clear();

        for (TeamBase base : allBases) {
            Team team = teamManager.getTeamByName(base.getTeamKey());
            if (team == null) {
                team = teamManager.getTeamByName(base.getTeamDisplayName());
            }
            if (team == null) {
                continue;
            }

            World world = Bukkit.getWorld(base.getWorldName());
            if (world == null) {
                continue;
            }

            Location flagLoc = findSafeFlagLocation(world, base.getCenterX(), base.getCenterY(), base.getCenterZ());
            Location supportLoc = flagLoc.clone().subtract(0, 1, 0);
            Material bannerMat = mapColorToBanner(team.getColor());

            TeamFlag teamFlag = new TeamFlag(team.getName(), team.getName(), bannerMat, flagLoc, supportLoc);
            placeFlagAtBase(teamFlag, team, true);
            flagsByTeamKey.put(teamFlag.getTeamKey(), teamFlag);
        }

        if (flagsByTeamKey.isEmpty()) {
            if (initiator != null) {
                initiator.sendMessage(Component.text("🚩 Não foi possível encontrar times válidos com bases criadas (/base criar)!", NamedTextColor.RED));
            }
            return false;
        }

        this.active = true;
        this.totalDurationSeconds = Math.max(60, durationSeconds);
        this.remainingSeconds = this.totalDurationSeconds;

        this.bossBar = BossBar.bossBar(
                buildBossBarTitle(),
                1.0f,
                BossBar.Color.RED,
                BossBar.Overlay.NOTCHED_10
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showBossBar(this.bossBar);
        }

        updateAllPlayerCompasses();
        broadcastEventStart();

        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    /**
     * Procura a posicao exata no centro da base para posicionar o estandarte em pe sem sufocar em blocos.
     */
    private Location findSafeFlagLocation(World world, int centerX, int centerY, int centerZ) {
        if (!world.isChunkLoaded(centerX >> 4, centerZ >> 4)) {
            world.getChunkAt(centerX >> 4, centerZ >> 4).load();
        }
        int minHeight = world.getMinHeight() + 1;
        int maxHeight = world.getMaxHeight() - 2;
        int startY = Math.max(minHeight, Math.min(maxHeight, centerY));

        // Se o bloco em centerY for ar/substituivel e o de baixo for solido, usa centerY diretamente
        Block atCenter = world.getBlockAt(centerX, startY, centerZ);
        Block belowCenter = world.getBlockAt(centerX, startY - 1, centerZ);
        if ((atCenter.getType().isAir() || !atCenter.getType().isSolid()) && belowCenter.getType().isSolid()) {
            return new Location(world, centerX, startY, centerZ);
        }

        // Procura ate 8 blocos para cima ou para baixo por um piso solido com espaco livre acima
        for (int offset = 1; offset <= 8; offset++) {
            int upY = startY + offset;
            if (upY < maxHeight) {
                Block b = world.getBlockAt(centerX, upY, centerZ);
                Block below = world.getBlockAt(centerX, upY - 1, centerZ);
                if ((b.getType().isAir() || !b.getType().isSolid()) && below.getType().isSolid()) {
                    return new Location(world, centerX, upY, centerZ);
                }
            }
            int downY = startY - offset;
            if (downY > minHeight) {
                Block b = world.getBlockAt(centerX, downY, centerZ);
                Block below = world.getBlockAt(centerX, downY - 1, centerZ);
                if ((b.getType().isAir() || !b.getType().isSolid()) && below.getType().isSolid()) {
                    return new Location(world, centerX, downY, centerZ);
                }
            }
        }

        return new Location(world, centerX, startY, centerZ);
    }

    private void placeFlagAtBase(TeamFlag flag, Team team, boolean recordOriginals) {
        Location flagLoc = flag.getFlagBlockLocation();
        Location supportLoc = flag.getSupportBlockLocation();
        World world = flagLoc.getWorld();
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(flagLoc.getBlockX() >> 4, flagLoc.getBlockZ() >> 4)) {
            world.getChunkAt(flagLoc.getBlockX() >> 4, flagLoc.getBlockZ() >> 4).load();
        }

        Block supportBlock = supportLoc.getBlock();
        Block flagBlock = flagLoc.getBlock();

        if (recordOriginals) {
            flag.originalFlagBlockData = flagBlock.getBlockData().clone();
            flag.originalSupportBlockData = supportBlock.getBlockData().clone();
            flag.modifiedSupportBlock = false;
        }

        if (!supportBlock.getType().isSolid()) {
            supportBlock.setType(mapColorToWool(team != null ? team.getColor() : NamedTextColor.WHITE), false);
            flag.modifiedSupportBlock = true;
        }

        flagBlock.setType(flag.getBannerMaterial(), false);
        flag.status = FlagStatus.AT_BASE;
        flag.carrierId = null;
        flag.carrierName = null;
        flag.carrierTeamName = null;
        flag.droppedItemEntityId = null;
        flag.droppedLocation = null;
        flag.droppedRemainingSeconds = 0;

        spawnOrUpdateBaseHologram(flag, team);
    }

    private void removeFlagBlockFromBase(TeamFlag flag) {
        Location flagLoc = flag.getFlagBlockLocation();
        World world = flagLoc.getWorld();
        if (world != null) {
            if (!world.isChunkLoaded(flagLoc.getBlockX() >> 4, flagLoc.getBlockZ() >> 4)) {
                world.getChunkAt(flagLoc.getBlockX() >> 4, flagLoc.getBlockZ() >> 4).load();
            }
            Block flagBlock = flagLoc.getBlock();
            if (flagBlock.getType() == flag.getBannerMaterial() || flagBlock.getType().name().endsWith("_BANNER")) {
                flagBlock.setType(Material.AIR, false);
            }
        }
        Team team = teamManager.getTeamByName(flag.getTeamKey());
        spawnOrUpdateBaseHologram(flag, team);
    }

    private void restoreOriginalBaseBlocks(TeamFlag flag) {
        removeHologram(flag);
        Location flagLoc = flag.getFlagBlockLocation();
        Location supportLoc = flag.getSupportBlockLocation();
        World world = flagLoc.getWorld();
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(flagLoc.getBlockX() >> 4, flagLoc.getBlockZ() >> 4)) {
            world.getChunkAt(flagLoc.getBlockX() >> 4, flagLoc.getBlockZ() >> 4).load();
        }
        if (flag.originalFlagBlockData != null) {
            flagLoc.getBlock().setBlockData(flag.originalFlagBlockData, false);
        } else {
            flagLoc.getBlock().setType(Material.AIR, false);
        }
        if (flag.modifiedSupportBlock && flag.originalSupportBlockData != null) {
            supportLoc.getBlock().setBlockData(flag.originalSupportBlockData, false);
        }
    }

    private void spawnOrUpdateBaseHologram(TeamFlag flag, Team team) {
        removeHologram(flag);
        Location flagLoc = flag.getFlagBlockLocation();
        World world = flagLoc.getWorld();
        if (world == null) {
            return;
        }
        NamedTextColor color = (team != null && team.getColor() != null) ? team.getColor() : NamedTextColor.GOLD;
        Component tagComp = (team != null) ? team.getFormattedTag() : Component.text("[" + flag.getTeamName() + "]", color);

        Component text;
        if (flag.getStatus() == FlagStatus.AT_BASE) {
            text = Component.text("🚩 BANDEIRA DE ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(tagComp)
                    .append(Component.text(" " + flag.getTeamName(), color, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("Inimigos: Clique ou quebre para roubar! (+100 pts)", NamedTextColor.YELLOW));
        } else if (flag.getStatus() == FlagStatus.CARRIED) {
            boolean ownTeamCarrying = flag.getTeamName().equalsIgnoreCase(flag.getCarrierTeamName());
            if (ownTeamCarrying) {
                text = Component.text("🛡 BANDEIRA DE ", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .append(tagComp)
                        .append(Component.text(" SENDO RECUPERADA!", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text("Aliado trazendo de volta: " + flag.getCarrierName(), NamedTextColor.YELLOW));
            } else {
                text = Component.text("⚠ BANDEIRA DE ", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(tagComp)
                        .append(Component.text(" ROUBADA!", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline())
                        .append(Component.text("Ladrão: " + flag.getCarrierName() + " (" + flag.getCarrierTeamName() + ")", NamedTextColor.YELLOW));
            }
        } else if (flag.getStatus() == FlagStatus.DROPPED) {
            text = Component.text("⚠ BANDEIRA DE ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(tagComp)
                    .append(Component.text(" CAÍDA NO CHÃO!", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text("Pegue-a e traga de volta para a base!", NamedTextColor.WHITE));
        } else {
            return;
        }

        Location holoLoc = flagLoc.clone().add(0.5, 2.15, 0.5);
        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, td -> {
            td.text(text);
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setPersistent(false);
        });
        flag.hologramEntityId = display.getUniqueId();
    }

    private void removeHologram(TeamFlag flag) {
        if (flag.hologramEntityId != null) {
            Entity entity = Bukkit.getEntity(flag.hologramEntityId);
            if (entity != null) {
                entity.remove();
            }
            flag.hologramEntityId = null;
        }
    }

    private void removeDroppedFlagEntity(TeamFlag flag) {
        if (flag.droppedItemEntityId != null) {
            Entity entity = Bukkit.getEntity(flag.droppedItemEntityId);
            if (entity != null) {
                entity.remove();
            }
            flag.droppedItemEntityId = null;
        }
    }

    private void tick() {
        if (!active) {
            return;
        }

        remainingSeconds--;

        // Verifica portadores, aplica partículas/glowing e checa se entraram na própria base
        for (TeamFlag flag : flagsByTeamKey.values()) {
            Team flagTeam = teamManager.getTeamByName(flag.getTeamKey());
            NamedTextColor teamColor = (flagTeam != null && flagTeam.getColor() != null) ? flagTeam.getColor() : NamedTextColor.GOLD;

            if (flag.getStatus() == FlagStatus.AT_BASE) {
                Location loc = flag.getFlagBlockLocation();
                World w = loc.getWorld();
                if (w != null && w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    // Garante que o estandarte continue intacto no pedestal
                    if (loc.getBlock().getType() != flag.getBannerMaterial()) {
                        loc.getBlock().setType(flag.getBannerMaterial(), false);
                    }
                    // Garante que o holograma da bandeira reapareca caso o chunk tenha sido descarregado
                    Entity holo = flag.hologramEntityId != null ? Bukkit.getEntity(flag.hologramEntityId) : null;
                    if (holo == null || !holo.isValid()) {
                        spawnOrUpdateBaseHologram(flag, flagTeam);
                    }
                    Particle.DustOptions dust = new Particle.DustOptions(mapNamedColorToBukkitColor(teamColor), 1.5f);
                    w.spawnParticle(Particle.DUST, loc.clone().add(0.5, 1.3, 0.5), 10, 0.35, 0.5, 0.35, 0.0, dust);
                }
            } else if (flag.getStatus() == FlagStatus.CARRIED) {
                Player carrier = flag.getCarrierId() != null ? Bukkit.getPlayer(flag.getCarrierId()) : null;
                if (carrier == null || !carrier.isOnline()) {
                    continue;
                }

                // Se por algum motivo já está no raio da base do time dele, captura imediatamente
                if (checkCarrierReachedBase(carrier, carrier.getLocation())) {
                    continue;
                }

                // Aplica Glowing e partículas da cor da bandeira roubada no ladrão
                carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 45, 0, false, false, true));
                Particle.DustOptions dust = new Particle.DustOptions(mapNamedColorToBukkitColor(teamColor), 1.8f);
                carrier.getWorld().spawnParticle(Particle.DUST, carrier.getLocation().add(0, 2.1, 0), 14, 0.3, 0.3, 0.3, 0.0, dust);

                Team carrierTeam = teamManager.getPlayerTeam(carrier.getUniqueId());
                TeamBase carrierBase = carrierTeam != null ? baseManager.getBaseByTeam(carrierTeam.getName()) : null;
                String baseCoords = carrierBase != null
                        ? " (X: " + carrierBase.getCenterX() + ", Z: " + carrierBase.getCenterZ() + ")"
                        : "";

                boolean ownTeamCarrying = carrierTeam != null && carrierTeam.getName().equalsIgnoreCase(flag.getTeamName());
                if (ownTeamCarrying) {
                    if (!isShowingCompassActionBar(carrier.getUniqueId())) {
                        carrier.sendActionBar(Component.text("🛡 Recuperando a Bandeira de ", NamedTextColor.GREEN, TextDecoration.BOLD)
                                .append(Component.text(flag.getTeamName(), teamColor, TextDecoration.BOLD))
                                .append(Component.text("! Leve-a até sua Base" + baseCoords + " para devolvê-la ao pedestal!", NamedTextColor.YELLOW)));
                    }
                } else {
                    if (!isShowingCompassActionBar(carrier.getUniqueId())) {
                        carrier.sendActionBar(Component.text("🚩 Carregando Bandeira de ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text(flag.getTeamName(), teamColor, TextDecoration.BOLD))
                                .append(Component.text("! Corra para sua Base" + baseCoords + " [+" + POINTS_CAPTURE_FLAG + " pts]", NamedTextColor.YELLOW)));
                    }

                    // Avisa membros do time vítima com as coordenadas do ladrão
                    if (flagTeam != null) {
                        Location cLoc = carrier.getLocation();
                        Component victimAlert = Component.text("⚠ Bandeira do seu time com ", NamedTextColor.RED, TextDecoration.BOLD)
                                .append(Component.text(carrier.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                                .append(Component.text(" em X: " + cLoc.getBlockX() + ", Y: " + cLoc.getBlockY() + ", Z: " + cLoc.getBlockZ() + "!", NamedTextColor.WHITE));
                        for (UUID memberId : flagTeam.getMembers()) {
                            Player member = Bukkit.getPlayer(memberId);
                            if (member != null && member.isOnline() && !isShowingCompassActionBar(member.getUniqueId())) {
                                member.sendActionBar(victimAlert);
                            }
                        }
                    }
                }
            } else if (flag.getStatus() == FlagStatus.DROPPED) {
                Location dLoc = flag.getDroppedLocation();
                if (dLoc == null || dLoc.getWorld() == null) {
                    returnFlagToBase(flag, "A bandeira caída de " + flag.getTeamName() + " desapareceu e voltou para a base!");
                } else if (dLoc.getWorld().isChunkLoaded(dLoc.getBlockX() >> 4, dLoc.getBlockZ() >> 4)) {
                    Entity itemEntity = flag.getDroppedItemEntityId() != null ? Bukkit.getEntity(flag.getDroppedItemEntityId()) : null;
                    if (itemEntity == null || !itemEntity.isValid()) {
                        // Se o item sumiu por descarga de chunk, recria na última posição caída assim que o chunk carregar
                        if (dLoc.getBlockY() > dLoc.getWorld().getMinHeight()) {
                            spawnDroppedFlagEntity(flag, dLoc, teamColor);
                        } else {
                            returnFlagToBase(flag, "A bandeira caída de " + flag.getTeamName() + " caiu no void e voltou para a base!");
                        }
                    } else if (itemEntity.getLocation().getBlockY() < itemEntity.getWorld().getMinHeight()) {
                        returnFlagToBase(flag, "A bandeira caída de " + flag.getTeamName() + " caiu no void e voltou para a base!");
                    } else {
                        flag.droppedLocation = itemEntity.getLocation();
                        itemEntity.customName(Component.text("🚩 Bandeira de " + flag.getTeamName() + " (Pegue e leve p/ Base!)", teamColor, TextDecoration.BOLD));
                        Particle.DustOptions dust = new Particle.DustOptions(mapNamedColorToBukkitColor(teamColor), 1.6f);
                        itemEntity.getWorld().spawnParticle(Particle.DUST, itemEntity.getLocation().add(0, 0.6, 0), 10, 0.25, 0.3, 0.25, 0.0, dust);
                    }
                }
            }
        }

        updateBossBar();
        updateAllPlayerCompasses();
        checkPeriodicAnnouncements();

        if (shouldAutoFinishEvent() || remainingSeconds <= 0) {
            finishEventWithRewards();
        }
    }

    private void checkPeriodicAnnouncements() {
        // Anuncia faltando 45m, 30m, 15m, 10m, 5m, 1m e últimos 10s
        if (remainingSeconds == 2700 || remainingSeconds == 1800 || remainingSeconds == 900
                || remainingSeconds == 600 || remainingSeconds == 300 || remainingSeconds == 60) {
            int mins = remainingSeconds / 60;
            Bukkit.broadcast(Component.empty());
            Bukkit.broadcast(Component.text("🚩 [CAPTURE THE FLAG] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("Faltam " + mins + (mins == 1 ? " minuto" : " minutos") + " para o fim do evento!", NamedTextColor.YELLOW))
                    .append(Component.text(" (Proteger até o fim = +" + POINTS_DEFEND_FLAG + " pts | Capturar = +" + POINTS_CAPTURE_FLAG + " pts)", NamedTextColor.GRAY)));
            broadcastFlagsSummaryLine();
            Bukkit.broadcast(Component.empty());
        } else if (remainingSeconds <= 10 && remainingSeconds > 0) {
            Bukkit.broadcast(Component.text("🚩 [CAPTURE THE FLAG] Encerrando em " + remainingSeconds + "s!", NamedTextColor.RED, TextDecoration.BOLD));
        }
    }

    public void broadcastFlagsSummaryLine() {
        for (TeamFlag flag : flagsByTeamKey.values()) {
            Team t = teamManager.getTeamByName(flag.getTeamKey());
            NamedTextColor color = (t != null && t.getColor() != null) ? t.getColor() : NamedTextColor.WHITE;
            Location bLoc = flag.getFlagBlockLocation();
            Location dLoc = flag.getDroppedLocation();
            String statusText = switch (flag.getStatus()) {
                case AT_BASE -> "§aNa Base §7(X: " + bLoc.getBlockX() + ", Y: " + bLoc.getBlockY() + ", Z: " + bLoc.getBlockZ() + ")";
                case CARRIED -> flag.getTeamName().equalsIgnoreCase(flag.getCarrierTeamName())
                        ? "§aSENDO DEVOLVIDA por §e" + flag.getCarrierName() + " §7[" + flag.getCarrierTeamName() + "]"
                        : "§cROUBADA por §e" + flag.getCarrierName() + " §7[" + flag.getCarrierTeamName() + "]";
                case DROPPED -> dLoc != null
                        ? "§6CAÍDA NO CHÃO §7(X: " + dLoc.getBlockX() + ", Y: " + dLoc.getBlockY() + ", Z: " + dLoc.getBlockZ() + ")"
                        : "§6CAÍDA NO CHÃO";
                case CAPTURED -> "§4CAPTURADA por §e" + flag.getCapturedByTeamName() + " §7(" + flag.getCapturedByPlayerName() + ")";
            };
            Bukkit.broadcast(Component.text(" • Bandeira de ", NamedTextColor.GRAY)
                    .append(Component.text(flag.getTeamName(), color, TextDecoration.BOLD))
                    .append(Component.text(": " + statusText)));
        }
    }

    private void updateBossBar() {
        if (bossBar == null) {
            return;
        }
        bossBar.name(buildBossBarTitle());
        float progress = Math.max(0.0f, Math.min(1.0f, (float) remainingSeconds / (float) totalDurationSeconds));
        bossBar.progress(progress);
    }

    private Component buildBossBarTitle() {
        long aliveCount = flagsByTeamKey.values().stream()
                .filter(f -> f.getStatus() != FlagStatus.CAPTURED)
                .count();
        return Component.text("🚩 CAPTURE THE FLAG ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(Component.text("⏱ " + formatTime(remainingSeconds), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" | Bandeiras vivas: ", NamedTextColor.GRAY))
                .append(Component.text(aliveCount + "/" + flagsByTeamKey.size(), NamedTextColor.GREEN, TextDecoration.BOLD));
    }

    private static String formatTime(int totalSec) {
        int m = Math.max(0, totalSec) / 60;
        int s = Math.max(0, totalSec) % 60;
        return String.format("%02d:%02d", m, s);
    }

    private void broadcastEventStart() {
        Title title = Title.title(
                Component.text("🚩 CAPTURE THE FLAG", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Roube bandeiras (+100 pts) e proteja a sua (+200 pts)!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1.0f, 1.0f);
        }

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("         🚩 EVENTO CAPTURE THE FLAG INICIADO! 🚩", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text(" • Duração: ", NamedTextColor.GRAY)
                .append(Component.text((totalDurationSeconds / 60) + " minutos", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text(" • Roubar bandeira inimiga e entrar no raio da sua Base: ", NamedTextColor.GRAY)
                .append(Component.text("+" + POINTS_CAPTURE_FLAG + " pontos", NamedTextColor.GREEN, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text(" • Proteger a bandeira do seu time até o final: ", NamedTextColor.GRAY)
                .append(Component.text("+" + POINTS_DEFEND_FLAG + " pontos", NamedTextColor.GOLD, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text(" • Bandeiras geradas nas bases:", NamedTextColor.YELLOW));
        broadcastFlagsSummaryLine();
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());
    }

    /**
     * Finaliza o evento ao fim de 1 hora (ou quando o ADM usa /capturetheflag finalizar),
     * premiando com +200 pontos todos os times que protegeram sua bandeira ate o final.
     */
    public void finishEventWithRewards() {
        if (!active) {
            return;
        }

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("        🏆 FIM DO EVENTO CAPTURE THE FLAG! 🏆", NamedTextColor.YELLOW, TextDecoration.BOLD));

        List<String> defendedTeams = new ArrayList<>();
        for (TeamFlag flag : flagsByTeamKey.values()) {
            Team team = teamManager.getTeamByName(flag.getTeamKey());
            if (flag.getStatus() != FlagStatus.CAPTURED && team != null) {
                defendedTeams.add(team.getName());
                teamManager.awardTeamPoints(
                        team,
                        POINTS_DEFEND_FLAG,
                        "Protegeu a bandeira do time até o final do Capture The Flag!"
                );
                Bukkit.broadcast(Component.text(" 🛡 ", NamedTextColor.GREEN)
                        .append(team.getFormattedTag())
                        .append(Component.text(" " + team.getName(), team.getColor(), TextDecoration.BOLD))
                        .append(Component.text(" protegeu sua bandeira até o final e ganhou ", NamedTextColor.WHITE))
                        .append(Component.text("+" + POINTS_DEFEND_FLAG + " pontos!", NamedTextColor.GOLD, TextDecoration.BOLD)));
            } else if (flag.getStatus() == FlagStatus.CAPTURED) {
                NamedTextColor c = (team != null && team.getColor() != null) ? team.getColor() : NamedTextColor.RED;
                Bukkit.broadcast(Component.text(" ❌ Bandeira de ", NamedTextColor.RED)
                        .append(Component.text(flag.getTeamName(), c, TextDecoration.BOLD))
                        .append(Component.text(" foi capturada por ", NamedTextColor.GRAY))
                        .append(Component.text(flag.getCapturedByTeamName() + " (" + flag.getCapturedByPlayerName() + ")", NamedTextColor.YELLOW)));
            }
        }

        if (defendedTeams.isEmpty()) {
            Bukkit.broadcast(Component.text(" • Nenhuma bandeira sobreviveu até o final!", NamedTextColor.RED));
        }

        Bukkit.broadcast(Component.text("================================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
        }

        cleanupEvent();
    }

    /**
     * Cancela/para o evento imediatamente sem conceder os +200 pontos de finalizacao.
     */
    public void stopEventCancelled() {
        if (!active) {
            return;
        }
        cleanupEvent();
        Bukkit.broadcast(Component.text("🚩 O evento Capture The Flag foi encerrado por um administrador.", NamedTextColor.RED, TextDecoration.BOLD));
    }

    public void shutdown() {
        if (active) {
            cleanupEvent();
        }
    }

    private void cleanupEvent() {
        this.active = false;
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        if (this.bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(this.bossBar);
            }
            this.bossBar = null;
        }

        for (TeamFlag flag : flagsByTeamKey.values()) {
            removeDroppedFlagEntity(flag);
            restoreOriginalBaseBlocks(flag);
        }
        flagsByTeamKey.clear();
        playerCompassTargetIndex.clear();
        compassActionBarUntil.clear();

        if (this.compassManager != null) {
            this.compassManager.removeCompassFromAllPlayers(CompassEventType.CTF);
        }

        // Remove qualquer item de bandeira remanescente dos inventários de jogadores online
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeAllFlagItemsFromPlayer(p);
        }
    }

    public void handlePlayerJoin(Player player) {
        removeAllFlagItemsFromPlayer(player);
        if (active && bossBar != null) {
            player.showBossBar(bossBar);
            updateSinglePlayerCompass(player, buildCompassTargets());
        }
    }

    // =========================================================================
    // BUSSOLA UNICA DO CAPTURE THE FLAG (TROCA DE ALVO COM BOTAO DIREITO)
    // =========================================================================

    public List<CtfCompassTarget> buildCompassTargets() {
        List<CtfCompassTarget> targets = new ArrayList<>();
        if (!active) {
            return targets;
        }

        // 1. Todas as bases dos times participantes
        for (TeamFlag flag : flagsByTeamKey.values()) {
            Team team = teamManager.getTeamByName(flag.getTeamKey());
            NamedTextColor color = (team != null && team.getColor() != null) ? team.getColor() : NamedTextColor.GOLD;
            String stateSuffix = switch (flag.getStatus()) {
                case AT_BASE -> " [Na Base]";
                case CARRIED -> " [Roubada]";
                case DROPPED -> " [No Chão]";
                case CAPTURED -> " [Capturada]";
            };
            targets.add(new CtfCompassTarget(
                    "Base de " + flag.getTeamName() + stateSuffix,
                    color,
                    flag.getFlagBlockLocation()
            ));
        }

        // 2. Todos os jogadores que capturaram/roubaram uma bandeira e estao correndo com ela (ou bandeiras no chao)
        for (TeamFlag flag : flagsByTeamKey.values()) {
            Team team = teamManager.getTeamByName(flag.getTeamKey());
            NamedTextColor color = (team != null && team.getColor() != null) ? team.getColor() : NamedTextColor.RED;
            if (flag.getStatus() == FlagStatus.CARRIED && flag.getCarrierId() != null) {
                Player carrier = Bukkit.getPlayer(flag.getCarrierId());
                if (carrier != null && carrier.isOnline()) {
                    targets.add(new CtfCompassTarget(
                            "Portador: " + carrier.getName() + " [Bandeira de " + flag.getTeamName() + "]",
                            color,
                            carrier.getLocation()
                    ));
                }
            } else if (flag.getStatus() == FlagStatus.DROPPED && flag.getDroppedLocation() != null) {
                targets.add(new CtfCompassTarget(
                        "Bandeira Caída de " + flag.getTeamName(),
                        color,
                        flag.getDroppedLocation()
                ));
            }
        }
        return targets;
    }

    public void updateAllPlayerCompasses() {
        if (!active || compassManager == null) {
            return;
        }
        List<CtfCompassTarget> targets = buildCompassTargets();
        if (targets.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateSinglePlayerCompass(player, targets);
        }
    }

    public void updateSinglePlayerCompass(Player player, List<CtfCompassTarget> targets) {
        if (!active || compassManager == null || player == null || !player.isOnline() || targets == null || targets.isEmpty()) {
            return;
        }
        int idx = playerCompassTargetIndex.getOrDefault(player.getUniqueId(), 0);
        if (idx < 0 || idx >= targets.size()) {
            idx = 0;
            playerCompassTargetIndex.put(player.getUniqueId(), 0);
        }
        CtfCompassTarget chosen = targets.get(idx);
        compassManager.giveOrUpdatePlayerCompass(player, CompassEventType.CTF, chosen.location(), chosen.label());
    }

    public void cyclePlayerCompassTarget(Player player) {
        if (!active || compassManager == null || player == null) {
            return;
        }
        List<CtfCompassTarget> targets = buildCompassTargets();
        if (targets.isEmpty()) {
            return;
        }
        int currentIdx = playerCompassTargetIndex.getOrDefault(player.getUniqueId(), 0);
        int nextIdx = (currentIdx + 1) % targets.size();
        playerCompassTargetIndex.put(player.getUniqueId(), nextIdx);

        CtfCompassTarget chosen = targets.get(nextIdx);
        compassManager.giveOrUpdatePlayerCompass(player, CompassEventType.CTF, chosen.location(), chosen.label());

        Location loc = chosen.location();
        compassActionBarUntil.put(player.getUniqueId(), System.currentTimeMillis() + 3500L);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
        player.sendActionBar(Component.text("🧭 Bússola CTF [" + (nextIdx + 1) + "/" + targets.size() + "]: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(chosen.label(), chosen.color(), TextDecoration.BOLD))
                .append(Component.text(" (X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + ")", NamedTextColor.AQUA)));
    }

    private boolean isShowingCompassActionBar(UUID playerId) {
        Long until = compassActionBarUntil.get(playerId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() < until) {
            return true;
        }
        compassActionBarUntil.remove(playerId);
        return false;
    }

    /**
     * Verifica se o evento deve terminar imediatamente:
     * - Quando todas as bandeiras foram capturadas (0 bandeiras vivas), OU
     * - Quando ha 2+ times no evento e restou apenas 1 unica bandeira nao capturada que esta segura na base (AT_BASE),
     *   significando que todas as bandeiras adversarias ja foram capturadas!
     */
    public boolean shouldAutoFinishEvent() {
        if (!active || flagsByTeamKey.isEmpty()) {
            return false;
        }
        List<TeamFlag> uncaptured = new ArrayList<>();
        for (TeamFlag f : flagsByTeamKey.values()) {
            if (f.getStatus() != FlagStatus.CAPTURED) {
                uncaptured.add(f);
            }
        }
        if (uncaptured.isEmpty()) {
            return true;
        }
        if (flagsByTeamKey.size() >= 2 && uncaptured.size() == 1) {
            TeamFlag lastFlag = uncaptured.get(0);
            if (lastFlag.getStatus() == FlagStatus.AT_BASE) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // INTERACAO COM AS BANDEIRAS (ROUBAR, CAPTURAR, CAIR NO CHAO, RECUPERAR)
    // =========================================================================

    public TeamFlag getFlagAtBlock(Location loc) {
        if (!active || loc == null || loc.getWorld() == null) {
            return null;
        }
        for (TeamFlag flag : flagsByTeamKey.values()) {
            Location fLoc = flag.getFlagBlockLocation();
            if (fLoc.getWorld() != null
                    && fLoc.getWorld().equals(loc.getWorld())
                    && fLoc.getBlockX() == loc.getBlockX()
                    && fLoc.getBlockY() == loc.getBlockY()
                    && fLoc.getBlockZ() == loc.getBlockZ()) {
                return flag;
            }
        }
        return null;
    }

    public boolean isSupportBlockOfActiveFlag(Location loc) {
        if (!active || loc == null || loc.getWorld() == null) {
            return false;
        }
        for (TeamFlag flag : flagsByTeamKey.values()) {
            if (flag.getStatus() == FlagStatus.CAPTURED) {
                continue;
            }
            Location sLoc = flag.getSupportBlockLocation();
            if (sLoc.getWorld() != null
                    && sLoc.getWorld().equals(loc.getWorld())
                    && sLoc.getBlockX() == loc.getBlockX()
                    && sLoc.getBlockY() == loc.getBlockY()
                    && sLoc.getBlockZ() == loc.getBlockZ()) {
                return true;
            }
        }
        return false;
    }

    public boolean isProtectedFlagOrSupportBlock(Location loc) {
        return getFlagAtBlock(loc) != null || isSupportBlockOfActiveFlag(loc);
    }

    public TeamFlag getCarriedFlag(UUID playerId) {
        if (!active || playerId == null) {
            return null;
        }
        for (TeamFlag flag : flagsByTeamKey.values()) {
            if (flag.getStatus() == FlagStatus.CARRIED && playerId.equals(flag.getCarrierId())) {
                return flag;
            }
        }
        return null;
    }

    public boolean isCarryingFlag(UUID playerId) {
        return getCarriedFlag(playerId) != null;
    }

    public boolean isCtfFlagItem(ItemStack stack) {
        if (stack == null || !stack.getType().name().endsWith("_BANNER") || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(ctfFlagKey, PersistentDataType.STRING);
    }

    public String getFlagTeamKeyFromItem(ItemStack stack) {
        if (!isCtfFlagItem(stack)) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(ctfFlagKey, PersistentDataType.STRING);
    }

    public ItemStack createFlagItem(TeamFlag flag) {
        Team team = teamManager.getTeamByName(flag.getTeamKey());
        NamedTextColor color = (team != null && team.getColor() != null) ? team.getColor() : NamedTextColor.GOLD;

        ItemStack item = new ItemStack(flag.getBannerMaterial(), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🚩 Bandeira de " + flag.getTeamName(), color, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("Bandeira oficial do time " + flag.getTeamName(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Corra até o raio da base do seu time", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("para capturar e ganhar +" + POINTS_CAPTURE_FLAG + " pontos!", NamedTextColor.GREEN, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(ctfFlagKey, PersistentDataType.STRING, flag.getTeamKey());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Tenta roubar uma bandeira que esta posicionada na base inimiga (AT_BASE).
     */
    public void tryStealFlagFromBase(Player player, TeamFlag flag) {
        if (!active || player == null || flag == null || flag.getStatus() != FlagStatus.AT_BASE) {
            return;
        }
        if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
        if (playerTeam == null) {
            player.sendMessage(Component.text("🚩 Você precisa estar em um time (/team) para roubar uma bandeira!", NamedTextColor.RED));
            return;
        }

        if (playerTeam.getName().equalsIgnoreCase(flag.getTeamName())) {
            player.sendMessage(Component.text("🛡 Esta é a bandeira do seu próprio time! Proteja-a até o final para ganhar +" + POINTS_DEFEND_FLAG + " pontos!", NamedTextColor.GREEN));
            return;
        }

        TeamBase ownBase = baseManager.getBaseByTeam(playerTeam.getName());
        if (ownBase == null) {
            player.sendMessage(Component.text("🚩 Seu time precisa ter uma base criada (/base criar) para roubar e capturar bandeiras!", NamedTextColor.RED));
            return;
        }

        if (isCarryingFlag(player.getUniqueId())) {
            player.sendMessage(Component.text("🚩 Você já está carregando uma bandeira inimiga! Leve-a até a sua base primeiro!", NamedTextColor.RED));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("🚩 Seu inventário está cheio! Libere 1 espaço para pegar a bandeira inimiga!", NamedTextColor.RED));
            return;
        }

        // Rouba a bandeira!
        flag.status = FlagStatus.CARRIED;
        flag.carrierId = player.getUniqueId();
        flag.carrierName = player.getName();
        flag.carrierTeamName = playerTeam.getName();

        removeFlagBlockFromBase(flag);
        player.getInventory().addItem(createFlagItem(flag));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));

        Team victimTeam = teamManager.getTeamByName(flag.getTeamKey());
        Component victimTag = victimTeam != null ? victimTeam.getFormattedTag() : Component.text("[" + flag.getTeamName() + "]", NamedTextColor.RED);
        NamedTextColor victimColor = (victimTeam != null && victimTeam.getColor() != null) ? victimTeam.getColor() : NamedTextColor.RED;

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("🚩 BANDEIRA ROUBADA! ", NamedTextColor.RED, TextDecoration.BOLD)
                .append(playerTeam.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" roubou a bandeira do time ", NamedTextColor.WHITE))
                .append(victimTag)
                .append(Component.text(" " + flag.getTeamName(), victimColor, TextDecoration.BOLD))
                .append(Component.text(" e está correndo para a base de ", NamedTextColor.WHITE))
                .append(Component.text(playerTeam.getName(), playerTeam.getColor(), TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.WHITE)));
        Bukkit.broadcast(Component.empty());

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 0.8f);
        }

        if (victimTeam != null) {
            Title alertTitle = Title.title(
                    Component.text("⚠ BANDEIRA ROUBADA!", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Component.text(player.getName() + " [" + playerTeam.getName() + "] roubou sua bandeira!", NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(500))
            );
            for (UUID memberId : victimTeam.getMembers()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null && member.isOnline()) {
                    member.showTitle(alertTitle);
                    member.playSound(member.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.3f);
                }
            }
        }

        checkCarrierReachedBase(player, player.getLocation());
    }

    /**
     * Verifica se o jogador que esta carregando uma bandeira entrou no raio (30 blocos)
     * da base do seu proprio time:
     * - Se for a bandeira do PROPRIO time (recuperada do chao): ela some do inventario e volta para o pedestal da base!
     * - Se for uma bandeira INIMIGA: ela some do inventario e os +100 pontos somam na hora!
     */
    public boolean checkCarrierReachedBase(Player player, Location location) {
        if (!active || player == null || location == null) {
            return false;
        }
        TeamFlag flag = getCarriedFlag(player.getUniqueId());
        if (flag == null) {
            return false;
        }

        Team carrierTeam = teamManager.getPlayerTeam(player.getUniqueId());
        if (carrierTeam == null) {
            return false;
        }

        TeamBase ownBase = baseManager.getBaseByTeam(carrierTeam.getName());
        if (ownBase == null || !ownBase.contains(location)) {
            return false;
        }

        // Caso 1: O jogador é dono da bandeira e está trazendo a própria bandeira de volta para a base!
        if (carrierTeam.getName().equalsIgnoreCase(flag.getTeamName())) {
            // Se ele pegou a bandeira já dentro do raio da própria base, precisa chegar perto do centro/pedestal (<= 4 blocos)
            if (flag.pickedUpInsideOwnBase) {
                Location pedLoc = flag.getFlagBlockLocation();
                if (pedLoc.getWorld() != null && pedLoc.getWorld().equals(location.getWorld())) {
                    double dx = location.getX() - (pedLoc.getBlockX() + 0.5);
                    double dz = location.getZ() - (pedLoc.getBlockZ() + 0.5);
                    if ((dx * dx + dz * dz) > 16.0) {
                        return false;
                    }
                }
            }

            removeFlagItemFromPlayer(player, flag.getTeamKey());
            player.removePotionEffect(PotionEffectType.GLOWING);
            flag.pickedUpInsideOwnBase = false;

            returnFlagToBase(flag, player.getName() + " trouxe a bandeira de " + flag.getTeamName() + " de volta para a base!");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.3f);
            return true;
        }

        // Caso 2: Bandeira inimiga trazida para o raio da base do time ladrão! A bandeira some e soma +100 pontos!
        removeFlagItemFromPlayer(player, flag.getTeamKey());
        player.removePotionEffect(PotionEffectType.GLOWING);

        flag.status = FlagStatus.CAPTURED;
        flag.carrierId = null;
        flag.pickedUpInsideOwnBase = false;
        flag.capturedByTeamName = carrierTeam.getName();
        flag.capturedByPlayerName = player.getName();

        restoreOriginalBaseBlocks(flag);

        teamManager.awardTeamPoints(
                carrierTeam,
                POINTS_CAPTURE_FLAG,
                "Capturou a bandeira do time " + flag.getTeamName() + " por " + player.getName() + "!"
        );

        Team victimTeam = teamManager.getTeamByName(flag.getTeamKey());
        Component victimTag = victimTeam != null ? victimTeam.getFormattedTag() : Component.text("[" + flag.getTeamName() + "]", NamedTextColor.RED);
        NamedTextColor victimColor = (victimTeam != null && victimTeam.getColor() != null) ? victimTeam.getColor() : NamedTextColor.RED;

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("🏆 BANDEIRA CAPTURADA! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(carrierTeam.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" chegou na base de ", NamedTextColor.WHITE))
                .append(Component.text(carrierTeam.getName(), carrierTeam.getColor(), TextDecoration.BOLD))
                .append(Component.text(" com a bandeira de ", NamedTextColor.WHITE))
                .append(victimTag)
                .append(Component.text(" " + flag.getTeamName(), victimColor, TextDecoration.BOLD))
                .append(Component.text(" e garantiu ", NamedTextColor.WHITE))
                .append(Component.text("+" + POINTS_CAPTURE_FLAG + " PONTOS!", NamedTextColor.GREEN, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.empty());

        spawnVictoryFirework(player.getLocation(), carrierTeam.getColor());
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.85f, 1.15f);
        }

        updateBossBar();
        updateAllPlayerCompasses();
        if (shouldAutoFinishEvent()) {
            finishEventWithRewards();
        }
        return true;
    }

    /**
     * Chamado quando o portador da bandeira morre ou desconecta antes de chegar na base.
     * A bandeira fica dropada no chao ate que alguem (o time dono ou um time inimigo) pegue e leve para a base.
     */
    public void handleCarrierLostFlag(Player carrier, Location dropLoc) {
        if (!active || carrier == null) {
            return;
        }
        TeamFlag flag = getCarriedFlag(carrier.getUniqueId());
        if (flag == null) {
            return;
        }

        removeFlagItemFromPlayer(carrier, flag.getTeamKey());
        carrier.removePotionEffect(PotionEffectType.GLOWING);

        World world = dropLoc != null ? dropLoc.getWorld() : null;
        if (world == null || dropLoc.getBlockY() <= world.getMinHeight()) {
            returnFlagToBase(flag, "O portador " + carrier.getName() + " caiu no void e a bandeira de " + flag.getTeamName() + " voltou para a base!");
            return;
        }

        Team victimTeam = teamManager.getTeamByName(flag.getTeamKey());
        NamedTextColor victimColor = (victimTeam != null && victimTeam.getColor() != null) ? victimTeam.getColor() : NamedTextColor.GOLD;

        spawnDroppedFlagEntity(flag, dropLoc, victimColor);

        flag.status = FlagStatus.DROPPED;
        flag.carrierId = null;
        flag.carrierName = null;
        flag.carrierTeamName = null;
        flag.pickedUpInsideOwnBase = false;
        flag.droppedRemainingSeconds = 0;

        spawnOrUpdateBaseHologram(flag, victimTeam);

        Bukkit.broadcast(Component.text("🚩 BANDEIRA NO CHÃO! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(carrier.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" morreu e derrubou a bandeira de ", NamedTextColor.WHITE))
                .append(Component.text(flag.getTeamName(), victimColor, TextDecoration.BOLD))
                .append(Component.text(" em (X: " + dropLoc.getBlockX() + ", Y: " + dropLoc.getBlockY() + ", Z: " + dropLoc.getBlockZ() + ")! ", NamedTextColor.GRAY))
                .append(Component.text("(Pegue-a no chão e leve até a sua base!)", NamedTextColor.YELLOW)));
    }

    private void spawnDroppedFlagEntity(TeamFlag flag, Location dropLoc, NamedTextColor victimColor) {
        removeDroppedFlagEntity(flag);
        World world = dropLoc.getWorld();
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(dropLoc.getBlockX() >> 4, dropLoc.getBlockZ() >> 4)) {
            world.getChunkAt(dropLoc.getBlockX() >> 4, dropLoc.getBlockZ() >> 4).load();
        }
        ItemStack flagStack = createFlagItem(flag);
        Item dropped = world.dropItem(dropLoc.clone().add(0, 0.4, 0), flagStack);
        dropped.setInvulnerable(true);
        dropped.setGlowing(true);
        dropped.setUnlimitedLifetime(true);
        dropped.setPickupDelay(15);
        dropped.customName(Component.text("🚩 Bandeira de " + flag.getTeamName() + " (Pegue e leve p/ Base!)", victimColor, TextDecoration.BOLD));
        dropped.setCustomNameVisible(true);

        flag.droppedItemEntityId = dropped.getUniqueId();
        flag.droppedLocation = dropped.getLocation();
    }

    /**
     * Chamado quando um jogador passa por cima de uma bandeira caida no chao.
     * Tanto o time dono quanto um time inimigo pegam a bandeira no inventario e precisam leva-la ate a base!
     */
    public void handlePickupDroppedFlag(Player player, Item itemEntity, String flagTeamKey) {
        if (!active || player == null || itemEntity == null || flagTeamKey == null) {
            return;
        }
        if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        TeamFlag flag = flagsByTeamKey.get(flagTeamKey.toLowerCase(Locale.ROOT));
        if (flag == null || flag.getStatus() != FlagStatus.DROPPED) {
            itemEntity.remove();
            return;
        }

        Team playerTeam = teamManager.getPlayerTeam(player.getUniqueId());
        if (playerTeam == null) {
            return;
        }

        TeamBase ownBase = baseManager.getBaseByTeam(playerTeam.getName());
        if (ownBase == null) {
            player.sendActionBar(Component.text("🚩 Seu time precisa ter uma base criada (/base criar) para pegar a bandeira!", NamedTextColor.RED));
            return;
        }
        if (isCarryingFlag(player.getUniqueId())) {
            player.sendActionBar(Component.text("🚩 Você já está carregando outra bandeira!", NamedTextColor.RED));
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendActionBar(Component.text("🚩 Seu inventário está cheio! Libere 1 slot para pegar a bandeira!", NamedTextColor.RED));
            return;
        }

        boolean isOwnerTeam = playerTeam.getName().equalsIgnoreCase(flag.getTeamName());

        itemEntity.remove();
        flag.droppedItemEntityId = null;
        flag.droppedLocation = null;
        flag.droppedRemainingSeconds = 0;
        flag.status = FlagStatus.CARRIED;
        flag.carrierId = player.getUniqueId();
        flag.carrierName = player.getName();
        flag.carrierTeamName = playerTeam.getName();
        flag.pickedUpInsideOwnBase = isOwnerTeam && ownBase.contains(player.getLocation());

        player.getInventory().addItem(createFlagItem(flag));
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));

        Team victimTeam = teamManager.getTeamByName(flag.getTeamKey());
        spawnOrUpdateBaseHologram(flag, victimTeam);
        NamedTextColor victimColor = (victimTeam != null && victimTeam.getColor() != null) ? victimTeam.getColor() : NamedTextColor.RED;

        if (isOwnerTeam) {
            Bukkit.broadcast(Component.text("🛡 RECUPERANDO BANDEIRA! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(playerTeam.getFormattedTag())
                    .append(Component.text(" " + player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" pegou a bandeira caída do próprio time (", NamedTextColor.WHITE))
                    .append(Component.text(flag.getTeamName(), victimColor, TextDecoration.BOLD))
                    .append(Component.text(") e está levando de volta para a base!", NamedTextColor.WHITE)));
        } else {
            Bukkit.broadcast(Component.text("🚩 BANDEIRA PEGA! ", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(playerTeam.getFormattedTag())
                    .append(Component.text(" " + player.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text(" pegou a bandeira caída de ", NamedTextColor.WHITE))
                    .append(Component.text(flag.getTeamName(), victimColor, TextDecoration.BOLD))
                    .append(Component.text(" e está correndo para a base de " + playerTeam.getName() + "!", NamedTextColor.WHITE)));
        }

        checkCarrierReachedBase(player, player.getLocation());
    }

    public void returnFlagToBase(TeamFlag flag, String reasonMessage) {
        if (flag == null || flag.getStatus() == FlagStatus.CAPTURED) {
            return;
        }
        removeDroppedFlagEntity(flag);
        Team team = teamManager.getTeamByName(flag.getTeamKey());
        placeFlagAtBase(flag, team, false);

        NamedTextColor color = (team != null && team.getColor() != null) ? team.getColor() : NamedTextColor.GREEN;
        Bukkit.broadcast(Component.text("🛡 BANDEIRA RECUPERADA! ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text(reasonMessage, color)));
        updateAllPlayerCompasses();
        if (shouldAutoFinishEvent()) {
            finishEventWithRewards();
        }
    }

    public void removeFlagItemFromPlayer(Player player, String flagTeamKey) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isCtfFlagItem(item)) {
                String key = getFlagTeamKeyFromItem(item);
                if (flagTeamKey == null || (key != null && key.equalsIgnoreCase(flagTeamKey))) {
                    inv.setItem(i, null);
                }
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (isCtfFlagItem(cursor)) {
            String key = getFlagTeamKeyFromItem(cursor);
            if (flagTeamKey == null || (key != null && key.equalsIgnoreCase(flagTeamKey))) {
                player.setItemOnCursor(null);
            }
        }
    }

    public void removeAllFlagItemsFromPlayer(Player player) {
        removeFlagItemFromPlayer(player, null);
    }

    private void spawnVictoryFirework(Location loc, NamedTextColor teamColor) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        Firework fw = world.spawn(loc.clone().add(0, 1, 0), Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .withColor(mapNamedColorToBukkitColor(teamColor))
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BALL_LARGE)
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    public static Material mapColorToBanner(NamedTextColor color) {
        if (color == NamedTextColor.RED || color == NamedTextColor.DARK_RED) {
            return Material.RED_BANNER;
        } else if (color == NamedTextColor.BLUE || color == NamedTextColor.DARK_BLUE) {
            return Material.BLUE_BANNER;
        } else if (color == NamedTextColor.AQUA) {
            return Material.LIGHT_BLUE_BANNER;
        } else if (color == NamedTextColor.DARK_AQUA) {
            return Material.CYAN_BANNER;
        } else if (color == NamedTextColor.GREEN) {
            return Material.LIME_BANNER;
        } else if (color == NamedTextColor.DARK_GREEN) {
            return Material.GREEN_BANNER;
        } else if (color == NamedTextColor.YELLOW) {
            return Material.YELLOW_BANNER;
        } else if (color == NamedTextColor.GOLD) {
            return Material.ORANGE_BANNER;
        } else if (color == NamedTextColor.LIGHT_PURPLE) {
            return Material.MAGENTA_BANNER;
        } else if (color == NamedTextColor.DARK_PURPLE) {
            return Material.PURPLE_BANNER;
        } else if (color == NamedTextColor.GRAY) {
            return Material.LIGHT_GRAY_BANNER;
        } else if (color == NamedTextColor.DARK_GRAY) {
            return Material.GRAY_BANNER;
        } else if (color == NamedTextColor.BLACK) {
            return Material.BLACK_BANNER;
        }
        return Material.WHITE_BANNER;
    }

    public static Material mapColorToWool(NamedTextColor color) {
        if (color == NamedTextColor.RED || color == NamedTextColor.DARK_RED) {
            return Material.RED_WOOL;
        } else if (color == NamedTextColor.BLUE || color == NamedTextColor.DARK_BLUE) {
            return Material.BLUE_WOOL;
        } else if (color == NamedTextColor.AQUA) {
            return Material.LIGHT_BLUE_WOOL;
        } else if (color == NamedTextColor.DARK_AQUA) {
            return Material.CYAN_WOOL;
        } else if (color == NamedTextColor.GREEN) {
            return Material.LIME_WOOL;
        } else if (color == NamedTextColor.DARK_GREEN) {
            return Material.GREEN_WOOL;
        } else if (color == NamedTextColor.YELLOW) {
            return Material.YELLOW_WOOL;
        } else if (color == NamedTextColor.GOLD) {
            return Material.ORANGE_WOOL;
        } else if (color == NamedTextColor.LIGHT_PURPLE) {
            return Material.MAGENTA_WOOL;
        } else if (color == NamedTextColor.DARK_PURPLE) {
            return Material.PURPLE_WOOL;
        } else if (color == NamedTextColor.GRAY) {
            return Material.LIGHT_GRAY_WOOL;
        } else if (color == NamedTextColor.DARK_GRAY) {
            return Material.GRAY_WOOL;
        } else if (color == NamedTextColor.BLACK) {
            return Material.BLACK_WOOL;
        }
        return Material.WHITE_WOOL;
    }

    public static Color mapNamedColorToBukkitColor(NamedTextColor color) {
        if (color == null) {
            return Color.WHITE;
        }
        return Color.fromRGB(color.value());
    }
}
