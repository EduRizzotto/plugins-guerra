package com.eduardo.teams.koth;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.compass.EventCompassManager;
import com.eduardo.teams.compass.EventCompassManager.CompassEventType;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.util.XaeroWaypointUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia o evento Capture a Area / Torre de Dominio (KOTH):
 * - Constroi uma torre larga (13x13, 12 blocos de altura) com plataforma de dominio no topo
 *   e salva todos os blocos originais para restaurar o terreno ao final.
 * - Enquanto jogadores de um time estiverem no topo, sobem os pontos de captura (+5 pts/s por jogador).
 * - Se outro time subir junto, a barra do outro time tambem sobe.
 * - Cada time com pontos > 0 tem sua propria BossBar empilhada na tela (times com 0 pontos nao aparecem).
 * - O primeiro time a chegar em 1000 pontos vence e ganha +50 pts permanentes de equipe.
 */
public class KothManager {

    public static final long AUTO_KOTH_INTERVAL_TICKS = 90L * 60L * 20L; // A cada 1h30
    public static final int MAX_EVENT_DURATION_SECONDS = 25 * 60; // Tempo maximo: 25 minutos
    public static final int TARGET_CAPTURE_POINTS = 1000;
    public static final int POINTS_PER_PLAYER_SECOND = 5;
    public static final int TOWER_RADIUS = 6; // 13x13 largura (-6..+6)
    public static final int TOWER_TOP_OFFSET_Y = 12; // Altura do piso do topo
    public static final int MIN_SPAWN_RADIUS = 150;
    public static final int MAX_SPAWN_RADIUS = 700;

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private EventCompassManager compassManager;

    private boolean active = false;
    private int secondsRemaining = 0;
    private Location baseCenter;
    private Location topCaptureCenter;
    private UUID hologramId;

    // Blocos originais antes da construcao da torre (para restaurar 100% do terreno)
    private final Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
    private final Set<Location> protectedRegionBlocks = new HashSet<>();

    // Pontos de captura por time (chave: nome do time em minusculo)
    private final Map<String, Integer> capturePointsByTeam = new LinkedHashMap<>();
    // BossBars ativas apenas para times que tem capturePoints > 0
    private final Map<String, BossBar> teamBossBars = new LinkedHashMap<>();

    private BukkitTask tickTask;
    private BukkitTask autoTask;

    public KothManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        startSchedulers();
    }

    public void setCompassManager(EventCompassManager compassManager) {
        this.compassManager = compassManager;
    }

    private void startSchedulers() {
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        this.autoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active && !Bukkit.getOnlinePlayers().isEmpty() && !teamManager.getAllTeams().isEmpty()) {
                World world = getDefaultOverworld();
                if (world != null) {
                    startRandomKoth(world);
                }
            }
        }, AUTO_KOTH_INTERVAL_TICKS, AUTO_KOTH_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        stopKoth(false);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isProtectedTowerBlock(Location loc) {
        return active && loc != null && protectedRegionBlocks.contains(loc.toBlockLocation());
    }

    private World getDefaultOverworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    /**
     * Spawna a Torre de Dominio em uma coordenada aleatoria segura na superficie do mundo.
     */
    public Location startRandomKoth(World world) {
        Location spawn = world.getSpawnLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location chosen = null;

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Block highest = world.getHighestBlockAt(x, z);
            Material surface = highest.getType();
            if (surface == Material.WATER || surface == Material.LAVA || surface.isAir()) {
                continue;
            }
            chosen = highest.getLocation().add(0, 1, 0).toBlockLocation();
            break;
        }

        if (chosen == null) {
            Block fallback = world.getHighestBlockAt(spawn.getBlockX() + 180, spawn.getBlockZ() + 180);
            chosen = fallback.getLocation().add(0, 1, 0).toBlockLocation();
        }

        startKothAt(chosen);
        return chosen;
    }

    /**
     * Inicia o evento construindo a Torre Larga na posicao base informada.
     */
    public void startKothAt(Location baseLocation) {
        stopKoth(false);

        this.baseCenter = baseLocation.toBlockLocation();
        this.topCaptureCenter = baseCenter.clone().add(0.5, TOWER_TOP_OFFSET_Y + 1.0, 0.5);
        this.active = true;
        this.secondsRemaining = MAX_EVENT_DURATION_SECONDS;
        this.capturePointsByTeam.clear();
        this.teamBossBars.clear();

        buildWideTower(this.baseCenter);
        spawnTopHologram();

        if (compassManager != null) {
            compassManager.giveOrUpdateAllPlayers(CompassEventType.KOTH, topCaptureCenter, "Topo da Torre");
        }

        World world = baseCenter.getWorld();
        if (world != null) {
            world.strikeLightningEffect(topCaptureCenter);
            world.playSound(topCaptureCenter, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.RECORDS, 3.0f, 1.0f);
        }

        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 🏰 TORRE DE DOMÍNIO APARECEU!", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 📍 Coordenadas: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(formatCoords(topCaptureCenter), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" ⚔ Objetivo: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text("Suba até o topo da torre e domine a área!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text(" 🏆 Primeiro time a ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(TARGET_CAPTURE_POINTS + " pontos", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" ganha ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text("+" + TeamManager.POINTS_KOTH_WIN + " pts de equipe!", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline());

        Bukkit.broadcast(msg);
        XaeroWaypointUtil.broadcastWaypoint("Torre-Dominio", "T", topCaptureCenter, XaeroWaypointUtil.COLOR_AQUA);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.RECORDS, 1.0f, 0.95f);
        }
    }

    /**
     * Constroi uma torre larga 13x13 com 4 entradas, rampas/plataformas internas,
     * 4 colunas de escadas de mao e arena de dominio no topo (y + 12).
     */
    private void buildWideTower(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        originalBlocks.clear();
        protectedRegionBlocks.clear();

        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        // 1. Salva todos os blocos originais na regiao (incluindo 1 bloco abaixo para fundacao e ate +17 acima)
        for (int dy = -1; dy <= TOWER_TOP_OFFSET_Y + 5; dy++) {
            for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
                for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
                    Location loc = new Location(world, cx + dx, cy + dy, cz + dz);
                    Block b = loc.getBlock();
                    originalBlocks.put(loc, b.getBlockData().clone());
                    protectedRegionBlocks.add(loc);
                    b.setType(Material.AIR, false);
                }
            }
        }

        // 2. Base e Piso Terreo (dy = -1 e dy = 0)
        for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
            for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
                setBlock(world, cx + dx, cy - 1, cz + dz, Material.STONE_BRICKS);
                boolean border = Math.abs(dx) == TOWER_RADIUS || Math.abs(dz) == TOWER_RADIUS;
                setBlock(world, cx + dx, cy, cz + dz, border ? Material.POLISHED_DEEPSLATE : Material.STONE_BRICKS);
            }
        }

        // 3. Paredes externas (dy = 1..11) com 4 portoes largos (3x4) nos 4 lados
        for (int dy = 1; dy < TOWER_TOP_OFFSET_Y; dy++) {
            for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
                for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
                    boolean isXEdge = Math.abs(dx) == TOWER_RADIUS;
                    boolean isZEdge = Math.abs(dz) == TOWER_RADIUS;
                    if (!isXEdge && !isZEdge) {
                        continue;
                    }

                    // Quinas reforcadas
                    if (isXEdge && isZEdge) {
                        setBlock(world, cx + dx, cy + dy, cz + dz, Material.DEEPSLATE_TILES);
                        continue;
                    }

                    // Portoes de entrada (dy = 1..4 e centro de cada parede |coord| <= 1)
                    boolean isDoorway = dy <= 4 && ((isXEdge && Math.abs(dz) <= 1) || (isZEdge && Math.abs(dx) <= 1));
                    if (isDoorway) {
                        continue;
                    }

                    // Janelas no meio da torre
                    boolean isWindow = (dy == 6 || dy == 7) && ((isXEdge && dz == 0) || (isZEdge && dx == 0));
                    if (isWindow) {
                        setBlock(world, cx + dx, cy + dy, cz + dz, Material.IRON_BARS);
                        continue;
                    }

                    setBlock(world, cx + dx, cy + dy, cz + dz, (dy % 4 == 0) ? Material.POLISHED_DEEPSLATE : Material.STONE_BRICKS);
                }
            }
        }

        // 4. Plataformas intermediarias (dy = 4 e dy = 8) para facilitar subida e combate interno
        for (int platformY : new int[]{4, 8}) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    setBlock(world, cx + dx, cy + platformY, cz + dz, Material.SMOOTH_STONE_SLAB);
                }
            }
            setBlock(world, cx, cy + platformY, cz, Material.SEA_LANTERN);
        }

        // 5. Piso da Arena de Dominio no Topo (dy = 12)
        for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
            for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
                boolean border = Math.abs(dx) == TOWER_RADIUS || Math.abs(dz) == TOWER_RADIUS;
                boolean innerRing = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                boolean centerBlock = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;

                if (centerBlock) {
                    setBlock(world, cx + dx, cy + TOWER_TOP_OFFSET_Y, cz + dz, (dx == 0 && dz == 0) ? Material.SEA_LANTERN : Material.GOLD_BLOCK);
                } else if (innerRing) {
                    setBlock(world, cx + dx, cy + TOWER_TOP_OFFSET_Y, cz + dz, Material.CHISELED_STONE_BRICKS);
                } else if (border) {
                    setBlock(world, cx + dx, cy + TOWER_TOP_OFFSET_Y, cz + dz, Material.DEEPSLATE_TILES);
                } else {
                    setBlock(world, cx + dx, cy + TOWER_TOP_OFFSET_Y, cz + dz, Material.POLISHED_DEEPSLATE);
                }
            }
        }

        // Ameias baixas apenas nas quinas do topo (dy = 13) para cobertura tatica
        for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
            for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
                boolean isEdge = Math.abs(dx) == TOWER_RADIUS || Math.abs(dz) == TOWER_RADIUS;
                if (isEdge && (Math.abs(dx) >= 4 && Math.abs(dz) >= 4)) {
                    setBlock(world, cx + dx, cy + TOWER_TOP_OFFSET_Y + 1, cz + dz, Material.DEEPSLATE_TILE_WALL);
                }
            }
        }

        // 6. 4 Pilares de Subida com Escadas de Mao (do chao dy=1 ate o topo dy=12)
        // Cada pilar fica em (px, pz) e tem uma abertura 1x1 nas plataformas e no topo na frente da escada
        int[][] ladderPillars = {
                {-3, -3},
                {3, -3},
                {-3, 3},
                {3, 3}
        };

        for (int[] pillar : ladderPillars) {
            int px = pillar[0];
            int pz = pillar[1];
            // A escada fica na face voltada para o centro em Z (pz < 0 -> SOUTH; pz > 0 -> NORTH)
            int ladderZ = pz < 0 ? (pz + 1) : (pz - 1);
            BlockFace facing = pz < 0 ? BlockFace.SOUTH : BlockFace.NORTH;

            for (int dy = 1; dy <= TOWER_TOP_OFFSET_Y; dy++) {
                // Pilar solido onde a escada se apoia
                setBlock(world, cx + px, cy + dy, cz + pz, Material.POLISHED_DEEPSLATE);
                // Abre buraco na plataforma/topo exatamente onde o jogador sobe pela escada
                setBlock(world, cx + px, cy + dy, cz + ladderZ, Material.AIR);

                Block ladderBlock = world.getBlockAt(cx + px, cy + dy, cz + ladderZ);
                ladderBlock.setType(Material.LADDER, false);
                if (ladderBlock.getBlockData() instanceof Ladder ladderData) {
                    ladderData.setFacing(facing);
                    ladderBlock.setBlockData(ladderData, false);
                }
            }
            // Iluminacao nos pilares
            setBlock(world, cx + px, cy + TOWER_TOP_OFFSET_Y + 1, cz + pz, Material.LANTERN);
        }
    }

    private void setBlock(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private void restoreTowerBlocks() {
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            if (loc.getWorld() != null) {
                loc.getBlock().setBlockData(entry.getValue(), false);
            }
        }
        originalBlocks.clear();
        protectedRegionBlocks.clear();
    }

    private void spawnTopHologram() {
        if (topCaptureCenter == null || topCaptureCenter.getWorld() == null) {
            return;
        }
        Location holoLoc = topCaptureCenter.clone().add(0, 2.6, 0);
        TextDisplay td = topCaptureCenter.getWorld().spawn(holoLoc, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(true);
            display.setBackgroundColor(Color.fromARGB(170, 0, 0, 0));
            display.text(
                    Component.text("🏰 ÁREA DE DOMÍNIO", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .append(Component.newline())
                            .append(Component.text("Fique no topo para pontuar (+5/s por jogador)", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
                            .append(Component.text("Meta: " + TARGET_CAPTURE_POINTS + " pts ➔ +" + TeamManager.POINTS_KOTH_WIN + " pts de equipe", NamedTextColor.GREEN)
                                    .decoration(TextDecoration.BOLD, false))
            );
        });
        this.hologramId = td.getUniqueId();
    }

    private void removeTopHologram() {
        if (hologramId != null) {
            Entity e = Bukkit.getEntity(hologramId);
            if (e != null) {
                e.remove();
            }
            hologramId = null;
        }
    }

    private void tick() {
        if (!active || baseCenter == null || topCaptureCenter == null) {
            return;
        }

        secondsRemaining--;
        if (secondsRemaining <= 0) {
            Bukkit.broadcast(
                    Component.text("🏰 [Domínio] ", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .append(Component.text("O tempo máximo da Torre de Domínio acabou sem nenhum time atingir " + TARGET_CAPTURE_POINTS + " pontos!", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
            );
            stopKoth(false);
            return;
        }

        World world = topCaptureCenter.getWorld();
        if (world != null && world.isChunkLoaded(topCaptureCenter.getBlockX() >> 4, topCaptureCenter.getBlockZ() >> 4)) {
            world.spawnParticle(Particle.END_ROD, topCaptureCenter.clone().add(0, 0.5, 0), 8, 2.5, 0.4, 2.5, 0.02);
        }

        if (compassManager != null) {
            compassManager.giveOrUpdateAllPlayers(CompassEventType.KOTH, topCaptureCenter, "Topo da Torre");
        }

        // Conta quantos jogadores vivos de cada time estao no topo da torre
        Map<String, Integer> playersOnTopByTeam = new HashMap<>();
        if (world != null) {
            int cx = baseCenter.getBlockX();
            int cz = baseCenter.getBlockZ();
            int topY = baseCenter.getBlockY() + TOWER_TOP_OFFSET_Y;

            for (Player p : world.getPlayers()) {
                if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                Location ploc = p.getLocation();
                if (ploc.getBlockY() < topY || ploc.getBlockY() > topY + 5) {
                    continue;
                }
                if (Math.abs(ploc.getBlockX() - cx) > TOWER_RADIUS || Math.abs(ploc.getBlockZ() - cz) > TOWER_RADIUS) {
                    continue;
                }

                Team team = teamManager.getPlayerTeam(p.getUniqueId());
                if (team == null) {
                    p.sendActionBar(Component.text("⚠ Entre em um time (/team) para capturar a Torre de Domínio!", NamedTextColor.RED));
                    continue;
                }

                String teamKey = team.getName().toLowerCase(Locale.ROOT);
                playersOnTopByTeam.merge(teamKey, 1, Integer::sum);
            }
        }

        // Incrementa os pontos de captura de cada time que tem jogadores no topo
        Team winningTeam = null;
        for (Map.Entry<String, Integer> entry : playersOnTopByTeam.entrySet()) {
            String teamKey = entry.getKey();
            int countOnTop = entry.getValue();
            int pointsGained = countOnTop * POINTS_PER_PLAYER_SECOND;

            int newCapturePts = Math.min(TARGET_CAPTURE_POINTS, capturePointsByTeam.getOrDefault(teamKey, 0) + pointsGained);
            capturePointsByTeam.put(teamKey, newCapturePts);

            Team team = teamManager.getTeamByName(teamKey);
            if (newCapturePts >= TARGET_CAPTURE_POINTS && winningTeam == null && team != null) {
                winningTeam = team;
            }
        }

        // Atualiza as BossBars empilhadas (APENAS para times com pontos de captura > 0!)
        updateAllBossBars(playersOnTopByTeam);

        if (winningTeam != null) {
            handleTeamWin(winningTeam);
        }
    }

    /**
     * Cria e atualiza uma BossBar para cada time que possui pontos de captura > 0.
     * Times com 0 pontos nao exibem BossBar.
     */
    private void updateAllBossBars(Map<String, Integer> playersOnTopByTeam) {
        for (Map.Entry<String, Integer> entry : capturePointsByTeam.entrySet()) {
            String teamKey = entry.getKey();
            int pts = entry.getValue();
            if (pts <= 0) {
                continue; // So aparece se os pontos de captura da equipe forem > 0
            }

            Team team = teamManager.getTeamByName(teamKey);
            if (team == null) {
                continue;
            }

            int onTop = playersOnTopByTeam.getOrDefault(teamKey, 0);
            int rate = onTop * POINTS_PER_PLAYER_SECOND;

            Component title = Component.text("🏰 ", NamedTextColor.AQUA)
                    .append(team.getFormattedTag())
                    .append(Component.text(" " + team.getName() + ": ", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(pts + "/" + TARGET_CAPTURE_POINTS + " pts", NamedTextColor.YELLOW, TextDecoration.BOLD));

            if (onTop > 0) {
                title = title.append(Component.text(" (+" + rate + "/s • " + onTop + " no topo)", NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, false));
            }

            float progress = Math.max(0.0f, Math.min(1.0f, (float) pts / TARGET_CAPTURE_POINTS));
            BossBar bar = teamBossBars.get(teamKey);
            if (bar == null) {
                bar = BossBar.bossBar(title, progress, mapBossBarColor(team.getColor()), BossBar.Overlay.NOTCHED_10);
                teamBossBars.put(teamKey, bar);
            } else {
                bar.name(title);
                bar.progress(progress);
                bar.color(mapBossBarColor(team.getColor()));
            }

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.showBossBar(bar);
            }
        }
    }

    public void showActiveBossBarsToPlayer(Player player) {
        if (!active || player == null) {
            return;
        }
        for (BossBar bar : teamBossBars.values()) {
            player.showBossBar(bar);
        }
    }

    private void clearAllBossBars() {
        for (BossBar bar : teamBossBars.values()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
        }
        teamBossBars.clear();
    }

    private BossBar.Color mapBossBarColor(NamedTextColor color) {
        if (color == NamedTextColor.RED || color == NamedTextColor.DARK_RED) return BossBar.Color.RED;
        if (color == NamedTextColor.GREEN || color == NamedTextColor.DARK_GREEN) return BossBar.Color.GREEN;
        if (color == NamedTextColor.YELLOW || color == NamedTextColor.GOLD) return BossBar.Color.YELLOW;
        if (color == NamedTextColor.LIGHT_PURPLE) return BossBar.Color.PINK;
        if (color == NamedTextColor.DARK_PURPLE) return BossBar.Color.PURPLE;
        if (color == NamedTextColor.WHITE || color == NamedTextColor.GRAY) return BossBar.Color.WHITE;
        return BossBar.Color.BLUE;
    }

    private void handleTeamWin(Team winnerTeam) {
        teamManager.awardTeamPoints(winnerTeam, TeamManager.POINTS_KOTH_WIN, "Dominou a Torre de Domínio (1000/1000)");

        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 🏆 TORRE DE DOMÍNIO CONQUISTADA!", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" A equipe ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(winnerTeam.getName() + " ", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(winnerTeam.getFormattedTag())
                .append(Component.newline())
                .append(Component.text(" atingiu " + TARGET_CAPTURE_POINTS + " pontos de captura primeiro e ganhou ", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text("+" + TeamManager.POINTS_KOTH_WIN + " pts de equipe!", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());

        Bukkit.broadcast(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.RECORDS, 1.0f, 1.0f);
        }

        stopKoth(false);
    }

    public void stopKoth(boolean announceStopped) {
        if (!active && originalBlocks.isEmpty()) {
            return;
        }
        this.active = false;
        this.secondsRemaining = 0;

        clearAllBossBars();
        removeTopHologram();
        restoreTowerBlocks();
        capturePointsByTeam.clear();

        if (compassManager != null) {
            compassManager.removeCompassFromAllPlayers(CompassEventType.KOTH);
        }

        if (announceStopped) {
            Bukkit.broadcast(
                    Component.text("🏰 [Domínio] ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("O evento Torre de Domínio foi encerrado e o terreno restaurado!", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
            );
        }
    }

    public static String formatCoords(Location loc) {
        return "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
    }
}
