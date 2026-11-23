package com.eduardo.teams.endstorm;

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
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
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
 * Gerencia o evento Tempestade do End / Fenda do Vazio (/tempestadeend):
 * - Rasga literalmente uma ravina/fenda gigantesca no chao (~80 blocos de comprimento
 *   por ~10 a 12 blocos de profundidade), com End Stone, Obsidian, Crying Obsidian,
 *   Purpur, fendas luminosas do Vazio no fundo e corrupcao do End saindo para fora da terra.
 * - No centro da ravina ergue-se o Altar com o Cristal da Fenda (500 HP).
 * - Por toda a extensao dos 80 blocos da rachadura nascem os mobs do End
 *   (Endermen da Fenda, Sentinelas Shulker e Parasitas Endermite).
 * - Restaura 100% do terreno original quando o evento termina.
 */
public class EndStormManager {

    public static final int EVENT_DURATION_SECONDS = 10 * 60; // 10 minutos
    public static final int CRYSTAL_MAX_HP = 500;
    public static final int POINTS_DESTROY_CRYSTAL = 70;
    public static final int POINTS_KILL_ENDERMAN = 3;
    public static final int POINTS_KILL_SHULKER = 5;
    public static final int POINTS_KILL_ENDERMITE = 2;

    // Dimensoes da Fenda/Ravina do End: 80 blocos de comprimento (-40..+40) e ~10-11 blocos de fundura
    public static final int RAVINE_HALF_LENGTH = 40; // 81 blocos de ponta a ponta
    public static final int RAVINE_MAX_DEPTH = 11;
    public static final int MIN_SPAWN_RADIUS = 180;
    public static final int MAX_SPAWN_RADIUS = 700;
    private static final int MAX_ACTIVE_GUARDIANS = 28;
    private static final long CRYSTAL_HIT_COOLDOWN_MS = 350L;

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final NamespacedKey guardianPointKey;
    private final NamespacedKey riftCrystalKey;
    private EventCompassManager compassManager;

    private boolean active = false;
    private int secondsRemaining = 0;
    private int crystalHp = CRYSTAL_MAX_HP;
    private int lastPulseThreshold = CRYSTAL_MAX_HP;

    private Location baseCenter;
    private Location crystalLocation;
    private UUID crystalEntityId;
    private UUID hologramId;
    private BossBar bossBar;
    private BukkitTask tickTask;

    private final Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
    private final Set<Location> protectedRegionBlocks = new HashSet<>();
    private final List<Location> fissureSpawnPoints = new ArrayList<>();
    private final List<Location> fissureParticlePoints = new ArrayList<>();
    private final Set<UUID> activeGuardians = new HashSet<>();
    private final Map<UUID, Long> lastCrystalHitByPlayer = new HashMap<>();
    private final Map<String, Integer> pointsEarnedInStormByTeam = new LinkedHashMap<>();

    public EndStormManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.guardianPointKey = new NamespacedKey(plugin, "endstorm_guardian_pts");
        this.riftCrystalKey = new NamespacedKey(plugin, "endstorm_rift_crystal");
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void setCompassManager(EventCompassManager compassManager) {
        this.compassManager = compassManager;
    }

    public boolean isActive() {
        return active;
    }

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public int getCrystalHp() {
        return crystalHp;
    }

    public Location getBaseCenter() {
        return baseCenter != null ? baseCenter.clone() : null;
    }

    public boolean isProtectedRiftBlock(Location loc) {
        return active && loc != null && protectedRegionBlocks.contains(loc.toBlockLocation());
    }

    public boolean isRiftCrystal(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (crystalEntityId != null && crystalEntityId.equals(entity.getUniqueId())) {
            return true;
        }
        return entity.getPersistentDataContainer().has(riftCrystalKey, PersistentDataType.BYTE);
    }

    public boolean isRiftGuardian(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (activeGuardians.contains(entity.getUniqueId())) {
            return true;
        }
        return entity.getPersistentDataContainer().has(guardianPointKey, PersistentDataType.INTEGER);
    }

    public int getGuardianPoints(Entity entity) {
        if (entity == null) {
            return 0;
        }
        Integer pts = entity.getPersistentDataContainer().get(guardianPointKey, PersistentDataType.INTEGER);
        return pts != null ? pts : 0;
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        stopStorm(false);
    }

    public Location startRandomStorm(World world) {
        Location spawn = world.getSpawnLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS);
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Block highest = world.getHighestBlockAt(x, z);
            Material surface = highest.getType();
            if (surface == Material.WATER || surface == Material.LAVA || surface == Material.KELP_PLANT) {
                continue;
            }
            Location candidate = highest.getLocation();
            double ravineAngle = rng.nextDouble(0, Math.PI);
            startStormAt(candidate, ravineAngle);
            return candidate;
        }

        int fallbackX = spawn.getBlockX() + 240;
        int fallbackZ = spawn.getBlockZ() + 240;
        Block highest = world.getHighestBlockAt(fallbackX, fallbackZ);
        Location fallback = highest.getLocation();
        startStormAt(fallback, 0.45);
        return fallback;
    }

    public void startStormAt(Location centerLocation, double axisAngleRadians) {
        if (active) {
            stopStorm(false);
        }

        this.baseCenter = centerLocation.toBlockLocation();
        this.active = true;
        this.secondsRemaining = EVENT_DURATION_SECONDS;
        this.crystalHp = CRYSTAL_MAX_HP;
        this.lastPulseThreshold = CRYSTAL_MAX_HP;
        this.pointsEarnedInStormByTeam.clear();
        this.lastCrystalHitByPlayer.clear();

        buildGiantEndRavine(axisAngleRadians);
        spawnRiftCrystalAndHologram();
        spawnGuardianWave(16);

        this.bossBar = BossBar.bossBar(
                buildBossBarTitle(),
                1.0f,
                BossBar.Color.PURPLE,
                BossBar.Overlay.NOTCHED_10
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showBossBar(this.bossBar);
        }

        updateCompasses();
        broadcastStormStart();
    }

    /**
     * Escava uma ravina gigantesca de ~80 blocos de comprimento (-40..+40) e ~10-11 blocos de fundura,
     * em formato de rachadura organica (S-curve), com paredes e fundo dominados pelo End saindo das profundezas,
     * espinhos de obsidiana/purpur transbordando pelas bordas e o altar do Cristal da Fenda no centro da fenda.
     */
    private void buildGiantEndRavine(double angleRad) {
        originalBlocks.clear();
        protectedRegionBlocks.clear();
        fissureSpawnPoints.clear();
        fissureParticlePoints.clear();

        World world = baseCenter.getWorld();
        if (world == null) {
            return;
        }

        int cx = baseCenter.getBlockX();
        int baseCy = findGroundSurfaceY(world, cx, baseCenter.getBlockY(), baseCenter.getBlockZ());
        int cz = baseCenter.getBlockZ();
        this.baseCenter = new Location(world, cx, baseCy, cz);

        double dirX = Math.cos(angleRad);
        double dirZ = Math.sin(angleRad);
        double perpX = -dirZ;
        double perpZ = dirX;

        // Garante que todas as chunks ao longo dos 80 blocos estejam carregadas
        int minChunkX = (cx - RAVINE_HALF_LENGTH - 16) >> 4;
        int maxChunkX = (cx + RAVINE_HALF_LENGTH + 16) >> 4;
        int minChunkZ = (cz - RAVINE_HALF_LENGTH - 16) >> 4;
        int maxChunkZ = (cz + RAVINE_HALF_LENGTH + 16) >> 4;
        for (int chX = minChunkX; chX <= maxChunkX; chX++) {
            for (int chZ = minChunkZ; chZ <= maxChunkZ; chZ++) {
                if (!world.isChunkLoaded(chX, chZ)) {
                    world.getChunkAt(chX, chZ).load();
                }
            }
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Pre-calcula a linha central sinuosa da ravina para cada passo t (-40..+40)
        // para parecer uma ravina real rasgada no solo
        int steps = RAVINE_HALF_LENGTH * 2 + 1;
        double[] sliceCenterX = new double[steps];
        double[] sliceCenterZ = new double[steps];
        double[] sliceHalfWidth = new double[steps];
        int[] sliceDepth = new int[steps];
        int[] sliceSurfaceY = new int[steps];

        for (int t = -RAVINE_HALF_LENGTH; t <= RAVINE_HALF_LENGTH; t++) {
            int idx = t + RAVINE_HALF_LENGTH;
            double norm = Math.abs(t) / (double) RAVINE_HALF_LENGTH; // 0 no centro, 1 nas pontas

            // Curvatura estilo falha geologica / ravina antiga do Minecraft
            double sway = Math.sin(t * 0.11) * 3.8 + Math.sin(t * 0.29) * 1.4;
            double sx = cx + t * dirX + sway * perpX;
            double sz = cz + t * dirZ + sway * perpZ;
            sliceCenterX[idx] = sx;
            sliceCenterZ[idx] = sz;

            // Largura da ravina: mais larga no centro (~7.5 blocos p/ cada lado = ~15 de largura),
            // afinando gradualmente ate fechar em fenda estreita nas pontas (-40 e +40)
            double shapeProfile = Math.pow(Math.max(0.0, 1.0 - norm * norm), 0.65);
            double jaggedNoise = Math.sin(t * 0.75) * 0.75 + Math.cos(t * 1.35) * 0.45;
            double hw = Math.max(1.1, 1.0 + 6.4 * shapeProfile + jaggedNoise * (1.0 - 0.5 * norm));
            if (Math.abs(t) <= 6) {
                hw = Math.max(hw, 6.8); // Camara central para o Cristal da Fenda
            }
            sliceHalfWidth[idx] = hw;

            // Profundidade: 10 a 11 blocos na maior parte da fenda, subindo nas extremidades
            int depth = (int) Math.round(3.0 + (RAVINE_MAX_DEPTH - 3.0) * Math.pow(Math.max(0.0, 1.0 - Math.pow(norm, 2.4)), 0.55));
            if (Math.abs(t) <= 26) {
                depth = Math.max(10, depth);
            }
            sliceDepth[idx] = depth;

            int surfY = findGroundSurfaceY(world, (int) Math.round(sx), baseCy, (int) Math.round(sz));
            // Limita variacao brusca para manter a fenda coesa
            surfY = Math.max(baseCy - 5, Math.min(baseCy + 5, surfY));
            sliceSurfaceY[idx] = surfY;
        }

        // Bounding box em X/Z para varrer cada coluna ao redor da ravina de 80 blocos
        int boundRadius = RAVINE_HALF_LENGTH + 15;
        for (int wx = cx - boundRadius; wx <= cx + boundRadius; wx++) {
            for (int wz = cz - boundRadius; wz <= cz + boundRadius; wz++) {
                // Encontra a fatia t (-40..+40) da linha central da ravina mais proxima de (wx, wz)
                int bestIdx = -1;
                double bestDistSq = Double.MAX_VALUE;
                for (int i = 0; i < steps; i++) {
                    double dx = wx - sliceCenterX[i];
                    double dz = wz - sliceCenterZ[i];
                    double dSq = dx * dx + dz * dz;
                    if (dSq < bestDistSq) {
                        bestDistSq = dSq;
                        bestIdx = i;
                    }
                }
                if (bestIdx < 0) {
                    continue;
                }

                double distFromSpine = Math.sqrt(bestDistSq);
                double topHalfWidth = sliceHalfWidth[bestIdx];
                double rimOuterRadius = topHalfWidth + 4.2;
                if (distFromSpine > rimOuterRadius) {
                    continue;
                }

                int surfY = findGroundSurfaceY(world, wx, sliceSurfaceY[bestIdx], wz);
                surfY = Math.max(sliceSurfaceY[bestIdx] - 3, Math.min(sliceSurfaceY[bestIdx] + 4, surfY));
                int maxDepthHere = sliceDepth[bestIdx];

                // Caso 1: Coluna esta DENTRO da boca da ravina (distFromSpine <= topHalfWidth)
                if (distFromSpine <= topHalfWidth) {
                    // Limpa arvores/blocos logo acima da abertura da fenda para o ceu ficar aberto
                    for (int y = surfY + 1; y <= surfY + 8; y++) {
                        Block above = world.getBlockAt(wx, y, wz);
                        if (!above.getType().isAir()) {
                            setRavineBlock(world, wx, y, wz, Material.AIR);
                        }
                    }

                    // A ravina afunila levemente conforme desce (paredes inclinadas e irregulares de ravina)
                    for (int d = 0; d <= maxDepthHere; d++) {
                        int y = surfY - d;
                        double depthRatio = d / (double) Math.max(1, maxDepthHere);
                        // Largura na profundidade d (inclui degraus/saliencias nas paredes)
                        double ledgeWave = Math.sin(d * 1.15 + bestIdx * 0.4) * 0.55;
                        double widthAtDepth = topHalfWidth * (1.0 - 0.34 * depthRatio) + ledgeWave;

                        if (d < maxDepthHere && distFromSpine <= widthAtDepth - 1.15) {
                            // Interior escavado da ravina (ar aberto ate 10-11 blocos de fundura!)
                            setRavineBlock(world, wx, y, wz, Material.AIR);
                        } else if (d == maxDepthHere || distFromSpine <= widthAtDepth) {
                            // Paredes da ravina ou fundo da fenda: O End emergindo de dentro da Terra!
                            boolean isBottomFloor = (d >= maxDepthHere - 1) || (distFromSpine <= 1.45 && d >= maxDepthHere - 2);
                            if (isBottomFloor) {
                                // Fundo da ravina: veio luminoso do Vazio no centro + End Stone / Obsidian / Purpur
                                if (distFromSpine <= 1.35) {
                                    setRavineBlock(world, wx, y - 1, wz,
                                            rng.nextDouble() < 0.7 ? Material.SEA_LANTERN : Material.CRYING_OBSIDIAN);
                                    setRavineBlock(world, wx, y, wz,
                                            rng.nextBoolean() ? Material.PURPLE_STAINED_GLASS : Material.MAGENTA_STAINED_GLASS);
                                } else {
                                    setRavineBlock(world, wx, y, wz, pickEndRavineMaterial(rng, true));
                                    setRavineBlock(world, wx, y - 1, wz, Material.OBSIDIAN);
                                }
                            } else {
                                // Paredes rasgadas da ravina (End Stone, Obsidian, Crying Obsidian, End Stone Bricks, Purpur)
                                Material wallMat = pickEndRavineMaterial(rng, false);
                                setRavineBlock(world, wx, y, wz, wallMat);

                                // Cristais / End Rods brotando das paredes internas da ravina
                                if (d >= 2 && d <= maxDepthHere - 2 && rng.nextDouble() < 0.045) {
                                    Block inward = world.getBlockAt(wx, y + 1, wz);
                                    if (inward.getType().isAir()) {
                                        setRavineBlock(world, wx, y + 1, wz, Material.END_ROD);
                                    }
                                }
                            }
                        } else {
                            // Camada externa da parede da ravina cravada na pedra do Overworld
                            if (distFromSpine <= widthAtDepth + 1.15) {
                                setRavineBlock(world, wx, y, wz, pickEndRavineMaterial(rng, false));
                            }
                        }
                    }
                } else {
                    // Caso 2: Borda externa / labio da fenda (End transbordando para fora da rachadura!)
                    double rimDist = distFromSpine - topHalfWidth; // 0.0 a 4.2 blocos da borda
                    double corruptionChance = Math.max(0.0, 1.0 - (rimDist / 4.0));
                    if (rng.nextDouble() < corruptionChance * 0.85) {
                        Material rimMat = pickEndRavineMaterial(rng, false);
                        setRavineBlock(world, wx, surfY, wz, rimMat);
                        if (rimDist <= 1.6) {
                            setRavineBlock(world, wx, surfY - 1, wz, Material.END_STONE);
                        }

                        // Espinhos de Obsidian / Crying Obsidian / Purpur saindo de dentro da fenda nas bordas
                        if (rimDist <= 1.8 && rng.nextDouble() < 0.085) {
                            int spikeHeight = rng.nextInt(2, 5);
                            for (int sh = 1; sh <= spikeHeight; sh++) {
                                Material spikeMat = (sh == spikeHeight)
                                        ? Material.END_ROD
                                        : (rng.nextBoolean() ? Material.OBSIDIAN : Material.CRYING_OBSIDIAN);
                                setRavineBlock(world, wx, surfY + sh, wz, spikeMat);
                            }
                        }
                    }
                }
            }
        }

        // Constroi o Altar/Monolito central dentro da ravina (em t = 0) para o Cristal da Fenda
        int centerDepth = sliceDepth[RAVINE_HALF_LENGTH];
        int floorY = baseCy - centerDepth;
        int pedestalTopY = baseCy - 3; // Fica dentro da fenda, visivel de cima e acessivel por baixo!

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double dSq = dx * dx + dz * dz;
                if (dSq <= 5.0) {
                    for (int y = floorY; y <= pedestalTopY - 1; y++) {
                        Material pillarMat = (Math.abs(dx) <= 1 && Math.abs(dz) <= 1)
                                ? ((y % 2 == 0) ? Material.OBSIDIAN : Material.PURPUR_PILLAR)
                                : Material.END_STONE_BRICKS;
                        setRavineBlock(world, cx + dx, y, cz + dz, pillarMat);
                    }
                }
            }
        }
        // Topo do pedestal do Cristal da Fenda
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setRavineBlock(world, cx + dx, pedestalTopY, cz + dz, Material.PURPUR_BLOCK);
                for (int clearY = pedestalTopY + 1; clearY <= baseCy + 5; clearY++) {
                    setRavineBlock(world, cx + dx, clearY, cz + dz, Material.AIR);
                }
            }
        }
        setRavineBlock(world, cx, pedestalTopY, cz, Material.CRYING_OBSIDIAN);
        setRavineBlock(world, cx - 1, pedestalTopY + 1, cz - 1, Material.END_ROD);
        setRavineBlock(world, cx + 1, pedestalTopY + 1, cz - 1, Material.END_ROD);
        setRavineBlock(world, cx - 1, pedestalTopY + 1, cz + 1, Material.END_ROD);
        setRavineBlock(world, cx + 1, pedestalTopY + 1, cz + 1, Material.END_ROD);

        this.crystalLocation = new Location(world, cx + 0.5, pedestalTopY + 1.0, cz + 0.5);

        // Registra pontos de spawn de mobs e particulas ao longo de TODOS os 80 blocos da rachadura!
        for (int t = -RAVINE_HALF_LENGTH + 3; t <= RAVINE_HALF_LENGTH - 3; t += 3) {
            int idx = t + RAVINE_HALF_LENGTH;
            int sx = (int) Math.round(sliceCenterX[idx]);
            int sz = (int) Math.round(sliceCenterZ[idx]);
            int sSurfY = sliceSurfaceY[idx];
            int sDepth = sliceDepth[idx];
            double hw = sliceHalfWidth[idx];

            // Ponto de particulas subindo do fundo da fenda
            fissureParticlePoints.add(new Location(world, sx + 0.5, sSurfY - sDepth + 1.5, sz + 0.5));

            // 1. Ponto de spawn no FUNDO da ravina (se nao estiver em cima do pilar central)
            if (Math.abs(t) >= 4) {
                Location bottomSpawn = findSafeMobFloor(world, sx, sSurfY - sDepth + 1, sz, sSurfY);
                if (bottomSpawn != null) {
                    fissureSpawnPoints.add(bottomSpawn);
                }
            }

            // 2. Pontos de spawn nas duas BORDAS / PAREDES ao longo dos 80 blocos da rachadura
            int leftX = (int) Math.round(sx + perpX * (hw + 0.8));
            int leftZ = (int) Math.round(sz + perpZ * (hw + 0.8));
            Location leftRimSpawn = findSafeMobFloor(world, leftX, sSurfY, leftZ, sSurfY + 4);
            if (leftRimSpawn != null) {
                fissureSpawnPoints.add(leftRimSpawn);
            }

            int rightX = (int) Math.round(sx - perpX * (hw + 0.8));
            int rightZ = (int) Math.round(sz - perpZ * (hw + 0.8));
            Location rightRimSpawn = findSafeMobFloor(world, rightX, sSurfY, rightZ, sSurfY + 4);
            if (rightRimSpawn != null) {
                fissureSpawnPoints.add(rightRimSpawn);
            }
        }
    }

    private Material pickEndRavineMaterial(ThreadLocalRandom rng, boolean isFloor) {
        double r = rng.nextDouble();
        if (isFloor) {
            if (r < 0.35) return Material.END_STONE;
            if (r < 0.58) return Material.OBSIDIAN;
            if (r < 0.78) return Material.CRYING_OBSIDIAN;
            if (r < 0.92) return Material.END_STONE_BRICKS;
            return Material.PURPUR_BLOCK;
        } else {
            if (r < 0.42) return Material.END_STONE;
            if (r < 0.65) return Material.END_STONE_BRICKS;
            if (r < 0.84) return Material.OBSIDIAN;
            if (r < 0.95) return Material.CRYING_OBSIDIAN;
            return Material.PURPUR_PILLAR;
        }
    }

    private int findGroundSurfaceY(World world, int x, int referenceY, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        int y = Math.min(world.getMaxHeight() - 3, Math.max(highest, referenceY + 12));
        int minSearch = Math.max(world.getMinHeight() + 15, referenceY - 18);
        while (y > minSearch) {
            Material mat = world.getBlockAt(x, y, z).getType();
            String name = mat.name();
            if (mat.isSolid()
                    && !name.endsWith("_LEAVES")
                    && !name.endsWith("_LOG")
                    && !name.endsWith("_WOOD")
                    && mat != Material.BAMBOO
                    && mat != Material.MANGROVE_ROOTS
                    && mat != Material.VINE) {
                return y;
            }
            y--;
        }
        return referenceY;
    }

    private Location findSafeMobFloor(World world, int x, int startY, int z, int maxY) {
        int minY = Math.max(world.getMinHeight() + 2, startY - 4);
        int topY = Math.min(world.getMaxHeight() - 3, maxY + 3);
        for (int y = startY; y <= topY; y++) {
            if (isSafeSpawnColumn(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        for (int y = startY - 1; y >= minY; y--) {
            if (isSafeSpawnColumn(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private boolean isSafeSpawnColumn(World world, int x, int feetY, int z) {
        Block below = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);
        Block aboveHead = world.getBlockAt(x, feetY + 2, z);
        return below.getType().isSolid()
                && !feet.getType().isSolid()
                && !head.getType().isSolid()
                && !aboveHead.getType().isSolid();
    }

    private void setRavineBlock(World world, int x, int y, int z, Material material) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return;
        }
        Location loc = new Location(world, x, y, z);
        if (!originalBlocks.containsKey(loc)) {
            originalBlocks.put(loc, loc.getBlock().getBlockData().clone());
        }
        protectedRegionBlocks.add(loc);
        loc.getBlock().setType(material, false);
    }

    private void spawnRiftCrystalAndHologram() {
        removeCrystalAndHologram();
        World world = crystalLocation.getWorld();
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(crystalLocation.getBlockX() >> 4, crystalLocation.getBlockZ() >> 4)) {
            world.getChunkAt(crystalLocation.getBlockX() >> 4, crystalLocation.getBlockZ() >> 4).load();
        }

        EnderCrystal crystal = world.spawn(crystalLocation, EnderCrystal.class, ec -> {
            ec.setShowingBottom(true);
            ec.setBeamTarget(crystalLocation.clone().add(0, 32, 0));
            ec.setPersistent(false);
            ec.getPersistentDataContainer().set(riftCrystalKey, PersistentDataType.BYTE, (byte) 1);
        });
        this.crystalEntityId = crystal.getUniqueId();

        Location holoLoc = crystalLocation.clone().add(0, 2.5, 0);
        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, td -> {
            td.text(buildHologramText());
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setPersistent(false);
        });
        this.hologramId = display.getUniqueId();
    }

    private Component buildHologramText() {
        return Component.text("🌌 FENDA DA TEMPESTADE DO END 🌌", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.text("Cristal da Fenda: ", NamedTextColor.GRAY))
                .append(Component.text(crystalHp + " / " + CRYSTAL_MAX_HP + " HP", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" (+" + POINTS_DESTROY_CRYSTAL + " pts)", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("Abater Guardiões na Fenda: ", NamedTextColor.YELLOW))
                .append(Component.text("+2 a +5 pts + Ender Pearls & Shulker Shells", NamedTextColor.AQUA));
    }

    private void updateHologramAndBossBar() {
        if (hologramId != null) {
            Entity entity = Bukkit.getEntity(hologramId);
            if (entity instanceof TextDisplay td) {
                td.text(buildHologramText());
            }
        }
        if (bossBar != null) {
            bossBar.name(buildBossBarTitle());
            float hpFraction = Math.max(0.0f, Math.min(1.0f, (float) crystalHp / (float) CRYSTAL_MAX_HP));
            bossBar.progress(hpFraction);
        }
    }

    private Component buildBossBarTitle() {
        return Component.text("🌌 TEMPESTADE DO END ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("⏱ " + formatTime(secondsRemaining), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" | Cristal: ", NamedTextColor.GRAY))
                .append(Component.text(crystalHp + "/" + CRYSTAL_MAX_HP + " HP", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
    }

    private void removeCrystalAndHologram() {
        if (crystalEntityId != null) {
            Entity e = Bukkit.getEntity(crystalEntityId);
            if (e != null) {
                e.remove();
            }
            crystalEntityId = null;
        }
        if (hologramId != null) {
            Entity e = Bukkit.getEntity(hologramId);
            if (e != null) {
                e.remove();
            }
            hologramId = null;
        }
    }

    private void removeAllGuardians() {
        for (UUID id : new ArrayList<>(activeGuardians)) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        activeGuardians.clear();
    }

    private void tick() {
        if (!active || baseCenter == null) {
            return;
        }

        secondsRemaining--;
        activeGuardians.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid() || e.isDead();
        });

        World world = baseCenter.getWorld();
        if (world != null && world.isChunkLoaded(baseCenter.getBlockX() >> 4, baseCenter.getBlockZ() >> 4)) {
            Entity crystal = crystalEntityId != null ? Bukkit.getEntity(crystalEntityId) : null;
            if ((crystal == null || !crystal.isValid()) && crystalHp > 0) {
                spawnRiftCrystalAndHologram();
            }

            // Partículas no Cristal Central
            world.spawnParticle(Particle.PORTAL, crystalLocation.clone().add(0, 0.5, 0), 35, 2.2, 1.5, 2.2, 0.3);
            world.spawnParticle(Particle.DRAGON_BREATH, crystalLocation.clone().add(0, 0.4, 0), 12, 1.6, 0.8, 1.6, 0.02);

            // Partículas emergindo por toda a extensão dos 80 blocos da Fenda!
            for (Location pLoc : fissureParticlePoints) {
                if (world.isChunkLoaded(pLoc.getBlockX() >> 4, pLoc.getBlockZ() >> 4)) {
                    world.spawnParticle(Particle.PORTAL, pLoc, 14, 1.8, 2.5, 1.8, 0.25);
                    world.spawnParticle(Particle.END_ROD, pLoc.clone().add(0, 2.0, 0), 2, 1.4, 2.2, 1.4, 0.02);
                }
            }

            // A cada 15 segundos, spawna mais Guardiões espalhados por toda a rachadura de 80 blocos
            if (secondsRemaining % 15 == 0 && activeGuardians.size() < MAX_ACTIVE_GUARDIANS) {
                int toSpawn = Math.min(7, MAX_ACTIVE_GUARDIANS - activeGuardians.size());
                spawnGuardianWave(toSpawn);
            }
        }

        updateCompasses();
        updateHologramAndBossBar();

        if (secondsRemaining == 300 || secondsRemaining == 120 || secondsRemaining == 60) {
            int mins = secondsRemaining / 60;
            Bukkit.broadcast(Component.text("🌌 [TEMPESTADE DO END] ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                    .append(Component.text("A Fenda em " + formatCoords(baseCenter) + " se fechará em " + mins + (mins == 1 ? " minuto!" : " minutos!"), NamedTextColor.YELLOW))
                    .append(Component.text(" (Cristal: " + crystalHp + "/" + CRYSTAL_MAX_HP + " HP)", NamedTextColor.GRAY)));
        }

        if (secondsRemaining <= 0) {
            Bukkit.broadcast(Component.empty());
            Bukkit.broadcast(Component.text("🌌 A Fenda da Tempestade do End se fechou! O Cristal da Fenda resistiu com " + crystalHp + " HP.", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            broadcastStormSummary();
            Bukkit.broadcast(Component.empty());
            stopStorm(true);
        }
    }

    /**
     * Spawna mobs do End espalhados por toda a extensao dos 80 blocos da fenda (no fundo e nas bordas).
     */
    public void spawnGuardianWave(int count) {
        if (!active || baseCenter == null || count <= 0) {
            return;
        }
        World world = baseCenter.getWorld();
        if (world == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            if (activeGuardians.size() >= MAX_ACTIVE_GUARDIANS) {
                break;
            }

            Location spawnLoc;
            if (!fissureSpawnPoints.isEmpty()) {
                spawnLoc = fissureSpawnPoints.get(rng.nextInt(fissureSpawnPoints.size())).clone();
            } else {
                spawnLoc = baseCenter.clone().add(rng.nextDouble(-10, 10), 1.0, rng.nextDouble(-10, 10));
            }

            if (!world.isChunkLoaded(spawnLoc.getBlockX() >> 4, spawnLoc.getBlockZ() >> 4)) {
                world.getChunkAt(spawnLoc.getBlockX() >> 4, spawnLoc.getBlockZ() >> 4).load();
            }

            double roll = rng.nextDouble();
            LivingEntity mob;
            if (roll < 0.58) {
                // Enderman Guardião da Fenda (+3 pts)
                mob = world.spawn(spawnLoc, Enderman.class, em -> {
                    em.customName(Component.text("Guardião da Fenda ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                            .append(Component.text("[+" + POINTS_KILL_ENDERMAN + " pts]", NamedTextColor.LIGHT_PURPLE)));
                    em.setCustomNameVisible(true);
                    em.setRemoveWhenFarAway(false);
                    em.setPersistent(false);
                    AttributeInstance maxHealth = em.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealth != null) {
                        maxHealth.setBaseValue(45.0);
                        em.setHealth(45.0);
                    }
                    em.getPersistentDataContainer().set(guardianPointKey, PersistentDataType.INTEGER, POINTS_KILL_ENDERMAN);
                });
            } else if (roll < 0.80) {
                // Sentinela Shulker (+5 pts)
                mob = world.spawn(spawnLoc, Shulker.class, sh -> {
                    sh.customName(Component.text("Sentinela Shulker ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                            .append(Component.text("[+" + POINTS_KILL_SHULKER + " pts]", NamedTextColor.GOLD)));
                    sh.setCustomNameVisible(true);
                    sh.setRemoveWhenFarAway(false);
                    sh.setPersistent(false);
                    sh.getPersistentDataContainer().set(guardianPointKey, PersistentDataType.INTEGER, POINTS_KILL_SHULKER);
                });
            } else {
                // Parasita do Vazio (+2 pts)
                mob = world.spawn(spawnLoc, Endermite.class, mite -> {
                    mite.customName(Component.text("Parasita do Vazio ", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)
                            .append(Component.text("[+" + POINTS_KILL_ENDERMITE + " pts]", NamedTextColor.LIGHT_PURPLE)));
                    mite.setCustomNameVisible(true);
                    mite.setRemoveWhenFarAway(false);
                    mite.setPersistent(false);
                    AttributeInstance maxHealth = mite.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealth != null) {
                        maxHealth.setBaseValue(16.0);
                        mite.setHealth(16.0);
                    }
                    mite.getPersistentDataContainer().set(guardianPointKey, PersistentDataType.INTEGER, POINTS_KILL_ENDERMITE);
                });
            }

            activeGuardians.add(mob.getUniqueId());
            world.spawnParticle(Particle.PORTAL, spawnLoc.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.2);
        }
        world.playSound(baseCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 0.7f);
    }

    /**
     * Processa o golpe de um jogador no Cristal da Fenda.
     */
    public void handleCrystalHit(Player attacker, double rawDamage) {
        if (!active || attacker == null || crystalHp <= 0) {
            return;
        }
        if (attacker.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Team team = teamManager.getPlayerTeam(attacker.getUniqueId());
        if (team == null) {
            attacker.sendActionBar(Component.text("🌌 Você precisa estar em um time (/team) para atacar o Cristal da Fenda!", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        Long lastHit = lastCrystalHitByPlayer.get(attacker.getUniqueId());
        if (lastHit != null && (now - lastHit) < CRYSTAL_HIT_COOLDOWN_MS) {
            return;
        }
        lastCrystalHitByPlayer.put(attacker.getUniqueId(), now);

        int dmg = Math.max(4, Math.min(12, (int) Math.round(rawDamage)));
        crystalHp = Math.max(0, crystalHp - dmg);

        World world = crystalLocation.getWorld();
        if (world != null) {
            world.playSound(crystalLocation, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.2f, 0.8f);
            world.spawnParticle(Particle.DRAGON_BREATH, crystalLocation, 18, 0.4, 0.4, 0.4, 0.05);
        }

        attacker.sendActionBar(Component.text("🌌 Cristal da Fenda: ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(crystalHp + "/" + CRYSTAL_MAX_HP + " HP ", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text("(-" + dmg + " HP)", NamedTextColor.RED)));

        updateHologramAndBossBar();

        if (crystalHp > 0 && crystalHp <= lastPulseThreshold - 100) {
            lastPulseThreshold = (crystalHp / 100) * 100;
            triggerVoidPulse();
        }

        if (crystalHp <= 0) {
            handleCrystalDestroyed(attacker, team);
        }
    }

    private void triggerVoidPulse() {
        if (crystalLocation == null || crystalLocation.getWorld() == null) {
            return;
        }
        World world = crystalLocation.getWorld();
        world.playSound(crystalLocation, Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.6f);
        world.playSound(crystalLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, crystalLocation, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.DRAGON_BREATH, crystalLocation, 60, 2.5, 1.0, 2.5, 0.08);

        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(crystalLocation) <= 121.0) {
                Vector push = p.getLocation().toVector().subtract(crystalLocation.toVector());
                if (push.lengthSquared() < 0.01) {
                    push = new Vector(0.5, 0.4, 0.5);
                } else {
                    push.normalize().multiply(0.95).setY(0.45);
                }
                p.setVelocity(push);
            }
        }

        spawnGuardianWave(8);
        Bukkit.broadcast(Component.text("🌌 [TEMPESTADE DO END] ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("O Cristal da Fenda liberou um Pulso do Vazio e novos Guardiões emergiram da rachadura! (" + crystalHp + "/" + CRYSTAL_MAX_HP + " HP)", NamedTextColor.YELLOW)));
    }

    private void handleCrystalDestroyed(Player destroyer, Team team) {
        World world = crystalLocation.getWorld();
        if (world != null) {
            world.playSound(crystalLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.9f);
            world.playSound(crystalLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, crystalLocation, 2, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticle(Particle.DRAGON_BREATH, crystalLocation, 120, 3.0, 1.5, 3.0, 0.1);

            // Dropa os tesouros na borda/superficie central quando a fenda se fecha
            Location dropLoc = baseCenter.clone().add(0.5, 1.5, 0.5);
            world.dropItemNaturally(dropLoc, new ItemStack(Material.ENDER_PEARL, 12));
            world.dropItemNaturally(dropLoc, new ItemStack(Material.SHULKER_SHELL, 4));
            world.dropItemNaturally(dropLoc, new ItemStack(Material.GOLDEN_APPLE, 4));
            world.dropItemNaturally(dropLoc, new ItemStack(Material.DIAMOND, 8));
            world.dropItemNaturally(dropLoc, new ItemStack(Material.DRAGON_BREATH, 3));
            world.dropItemNaturally(dropLoc, new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
        }

        recordStormTeamPoints(team, POINTS_DESTROY_CRYSTAL);
        teamManager.awardTeamPoints(
                team,
                POINTS_DESTROY_CRYSTAL,
                "Destruiu o Cristal da Fenda na Tempestade do End (" + destroyer.getName() + ")!"
        );

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("      🌌 CRISTAL DA FENDA DESTRUÍDO! 🌌", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text(" • ", NamedTextColor.GRAY)
                .append(team.getFormattedTag())
                .append(Component.text(" " + destroyer.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" destruiu o Cristal da Fenda e garantiu ", NamedTextColor.WHITE))
                .append(Component.text("+" + POINTS_DESTROY_CRYSTAL + " PONTOS", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" para ", NamedTextColor.WHITE))
                .append(Component.text(team.getName() + "!", team.getColor(), TextDecoration.BOLD)));
        broadcastStormSummary();
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        stopStorm(true);
    }

    public void handleGuardianKilled(LivingEntity guardian, Player killer, List<ItemStack> drops) {
        activeGuardians.remove(guardian.getUniqueId());
        int pts = getGuardianPoints(guardian);
        if (pts <= 0) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (guardian instanceof Shulker) {
            drops.add(new ItemStack(Material.SHULKER_SHELL, 1));
            drops.add(new ItemStack(Material.ENDER_PEARL, rng.nextInt(1, 3)));
        } else if (guardian instanceof Enderman) {
            drops.add(new ItemStack(Material.ENDER_PEARL, rng.nextInt(1, 3)));
            if (rng.nextDouble() < 0.35) {
                drops.add(new ItemStack(Material.CHORUS_FRUIT, rng.nextInt(1, 4)));
            }
        } else {
            if (rng.nextDouble() < 0.65) {
                drops.add(new ItemStack(Material.ENDER_PEARL, 1));
            }
        }

        if (killer == null) {
            return;
        }
        Team team = teamManager.getPlayerTeam(killer.getUniqueId());
        if (team == null) {
            return;
        }

        recordStormTeamPoints(team, pts);
        teamManager.awardTeamPoints(team, pts, "Guardião da Tempestade do End abatido por " + killer.getName() + "!");
    }

    private void recordStormTeamPoints(Team team, int pts) {
        if (team == null || pts <= 0) {
            return;
        }
        String key = team.getName().toLowerCase(Locale.ROOT);
        pointsEarnedInStormByTeam.put(key, pointsEarnedInStormByTeam.getOrDefault(key, 0) + pts);
    }

    public void broadcastStormSummary() {
        if (pointsEarnedInStormByTeam.isEmpty()) {
            return;
        }
        Bukkit.broadcast(Component.text(" • Pontos conquistados na Tempestade do End:", NamedTextColor.LIGHT_PURPLE));
        for (Map.Entry<String, Integer> entry : pointsEarnedInStormByTeam.entrySet()) {
            Team t = teamManager.getTeamByName(entry.getKey());
            String display = t != null ? t.getName() : entry.getKey();
            NamedTextColor c = (t != null && t.getColor() != null) ? t.getColor() : NamedTextColor.WHITE;
            Bukkit.broadcast(Component.text("   - ", NamedTextColor.GRAY)
                    .append(Component.text(display, c, TextDecoration.BOLD))
                    .append(Component.text(": +" + entry.getValue() + " pts", NamedTextColor.GOLD, TextDecoration.BOLD)));
        }
    }

    public void stopStorm(boolean restoreTerrain) {
        this.active = false;
        removeCrystalAndHologram();
        removeAllGuardians();

        if (this.bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(this.bossBar);
            }
            this.bossBar = null;
        }

        if (this.compassManager != null) {
            this.compassManager.removeCompassFromAllPlayers(CompassEventType.END_STORM);
        }

        if (restoreTerrain || !originalBlocks.isEmpty()) {
            // Antes de fechar a ravina, teleporta para a superficie qualquer jogador que esteja dentro do buraco
            if (baseCenter != null && baseCenter.getWorld() != null) {
                World w = baseCenter.getWorld();
                for (Player p : w.getPlayers()) {
                    Location pLoc = p.getLocation().toBlockLocation();
                    if (protectedRegionBlocks.contains(pLoc) || p.getLocation().distanceSquared(baseCenter) <= 2500.0) {
                        int safeY = findGroundSurfaceY(w, pLoc.getBlockX(), baseCenter.getBlockY(), pLoc.getBlockZ());
                        if (pLoc.getBlockY() <= safeY) {
                            p.teleport(new Location(w, p.getLocation().getX(), safeY + 1.5, p.getLocation().getZ(),
                                    p.getLocation().getYaw(), p.getLocation().getPitch()));
                        }
                    }
                }
            }

            // 1a passada: restaura todos os blocos solidos primeiro
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                if (entry.getValue().getMaterial().isSolid()) {
                    Location loc = entry.getKey();
                    World w = loc.getWorld();
                    if (w != null) {
                        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                            w.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4).load();
                        }
                        loc.getBlock().setBlockData(entry.getValue(), false);
                    }
                }
            }
            // 2a passada: restaura vegetacao/ar/blocos nao-solidos
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                if (!entry.getValue().getMaterial().isSolid()) {
                    Location loc = entry.getKey();
                    World w = loc.getWorld();
                    if (w != null) {
                        loc.getBlock().setBlockData(entry.getValue(), false);
                    }
                }
            }
        }
        originalBlocks.clear();
        protectedRegionBlocks.clear();
        fissureSpawnPoints.clear();
        fissureParticlePoints.clear();
    }

    public void updateCompasses() {
        if (!active || baseCenter == null || compassManager == null) {
            return;
        }
        String subtitle = "X: " + baseCenter.getBlockX() + " Y: " + baseCenter.getBlockY() + " Z: " + baseCenter.getBlockZ();
        compassManager.giveOrUpdateAllPlayers(CompassEventType.END_STORM, baseCenter, subtitle);
    }

    public void handlePlayerJoin(Player player) {
        if (active && bossBar != null) {
            player.showBossBar(bossBar);
            updateCompasses();
        }
    }

    private void broadcastStormStart() {
        Title title = Title.title(
                Component.text("🌌 TEMPESTADE DO END", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Uma Fenda gigante abriu em " + formatCoords(baseCenter) + "!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.8f);
        }
        XaeroWaypointUtil.broadcastWaypoint("Tempestade do End", "E", baseCenter, 5);

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("        🌌 TEMPESTADE DO END INICIADA! 🌌", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text(" • Uma Fenda gigantesca de 80 blocos rasgou o mundo!", NamedTextColor.LIGHT_PURPLE));
        Bukkit.broadcast(Component.text(" • Coordenadas do Centro: ", NamedTextColor.GRAY)
                .append(Component.text(formatCoords(baseCenter), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text(" • Duração: ", NamedTextColor.GRAY)
                .append(Component.text("10 minutos", NamedTextColor.WHITE)));
        Bukkit.broadcast(Component.text(" • Guardiões da Fenda: ", NamedTextColor.GRAY)
                .append(Component.text("+2 a +5 pts por abate + Ender Pearls & Shulker Shells", NamedTextColor.AQUA)));
        Bukkit.broadcast(Component.text(" • Destruir o Cristal Central (" + CRYSTAL_MAX_HP + " HP): ", NamedTextColor.GRAY)
                .append(Component.text("+" + POINTS_DESTROY_CRYSTAL + " pontos + Loot do Vazio!", NamedTextColor.GOLD, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("================================================", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());
    }

    public static String formatCoords(Location loc) {
        if (loc == null) {
            return "(?)";
        }
        return "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
    }

    private static String formatTime(int totalSec) {
        int m = Math.max(0, totalSec) / 60;
        int s = Math.max(0, totalSec) % 60;
        return String.format("%02d:%02d", m, s);
    }
}
