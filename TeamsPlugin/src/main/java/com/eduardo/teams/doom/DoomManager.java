package com.eduardo.teams.doom;

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
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia o mega-evento da Fortaleza DOOM (/doom):
 * - Gera uma dungeon gigantesca (37 blocos de largura x 125 blocos de comprimento x 19 blocos de altura),
 *   totalmente fechada e com APENAS UMA ENTRADA frontal, localizada entre 5000 e 6000 blocos de raio do spawn.
 * - Projetada para a disputa simultanea de 5 equipes, dividida em 5 grandes saloes interligados:
 *     1. Patio da Carnificina (Arena PvP/PvE inicial com barricadas e 6 spawners)
 *     2. Abismo das Almas (Grande Parkour de 3 rotas paralelas sobre magma/almas, com escadas de retorno)
 *     3. Labirinto das Sombras (Labirinto de 5 corredores largos com teias e 8 spawners)
 *     4. Pontes do Julgamento (Parkour de precisao em 3 pontes quebradas sob fogo de Blazes e Esqueletos)
 *     5. Santuario do DOOM (Salao do Trono com Guardioes de Elite e o Bau Final com o Coracao do Doom: +200 pts)
 * - Totalmente indestrutivel e bloqueada contra colocacao/quebra de blocos, baldes, explosoes e perolas do fim.
 */
public class DoomManager {

    public static final int MIN_SPAWN_RADIUS = 5000;
    public static final int MAX_SPAWN_RADIUS = 6000;
    public static final int DOOM_REWARD_POINTS = 200;
    public static final int COLLAPSE_COUNTDOWN_SECONDS = 60;

    // Dimensoes locais relativas a (originX, baseY, originZ)
    public static final int HALF_WIDTH = 18;     // dx: -18 .. +18 (37 blocos de largura)
    public static final int MIN_DZ = -10;        // Praca externa de entrada: -10 .. -1
    public static final int MAX_DZ = 114;        // Parede traseira selada: 113 .. 114
    public static final int MIN_DY = -1;         // Fundacao: -1
    public static final int MAX_DY = 17;         // Teto blindado: 16 .. 17

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private EventCompassManager compassManager;

    private final NamespacedKey doomArtifactKey;
    private final NamespacedKey doomMobKey;

    private boolean generating = false;
    private boolean active = false;
    private boolean artifactClaimed = false;
    private int collapseSecondsRemaining = 0;

    private World dungeonWorld;
    private int originX;
    private int baseY;
    private int originZ;

    private Location entranceLocation;
    private Location chestLocation;
    private UUID entranceHologramId;
    private UUID chestHologramId;

    private final Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
    private final List<SpawnerPoint> spawnerPoints = new ArrayList<>();
    private final Set<UUID> spawnedMobIds = new HashSet<>();
    private final Set<Long> ticketedChunks = new HashSet<>();

    private BossBar doomBossBar;
    private BukkitTask tickTask;
    private BukkitTask buildTask;
    private int tickCounter = 0;

    private record SpawnerPoint(int dx, int dy, int dz, EntityType type) {
    }

    public DoomManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.doomArtifactKey = new NamespacedKey(plugin, "doom_artifact_200pts");
        this.doomMobKey = new NamespacedKey(plugin, "doom_dungeon_mob");
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void setCompassManager(EventCompassManager compassManager) {
        this.compassManager = compassManager;
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        stopDoom(false);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isGenerating() {
        return generating;
    }

    public boolean isArtifactClaimed() {
        return artifactClaimed;
    }

    public Location getEntranceLocation() {
        return entranceLocation != null ? entranceLocation.clone() : null;
    }

    public Location getChestLocation() {
        return chestLocation != null ? chestLocation.clone() : null;
    }

    public boolean isDoomArtifact(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(doomArtifactKey, PersistentDataType.BYTE);
    }

    public ItemStack createDoomArtifactItem() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🔥 CORAÇÃO DO DOOM ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("(+200 PONTOS)", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("Artefato ancestral guardado no fundo", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("da gigantesca Fortaleza DOOM.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("⚡ Clique para reivindicar imediatamente", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("   +200 PONTOS permanentes para sua equipe!", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 10, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(doomArtifactKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createDoomTrophyItem(Team team, Player winner) {
        ItemStack stack = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🏆 Troféu do Conquistador DOOM", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.empty(),
                    Component.text("Conquistado por: ", NamedTextColor.GRAY)
                            .append(Component.text(winner.getName(), NamedTextColor.WHITE))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Equipe Vencedora: ", NamedTextColor.GRAY)
                            .append(team.getFormattedTag())
                            .append(Component.text(" " + team.getName(), team.getColor()))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("Recompensa: ", NamedTextColor.GRAY)
                            .append(Component.text("+" + DOOM_REWARD_POINTS + " Pontos", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 5, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Verifica se uma localizacao esta dentro da regiao protegida da Fortaleza DOOM.
     * Inclui margem extra de 2 blocos ao redor de toda a estrutura e da praca de entrada.
     */
    public boolean isProtectedLocation(Location loc) {
        if ((!active && !generating) || loc == null || dungeonWorld == null) {
            return false;
        }
        if (!dungeonWorld.equals(loc.getWorld())) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= (originX - HALF_WIDTH - 2) && x <= (originX + HALF_WIDTH + 2)
                && y >= (baseY + MIN_DY - 4) && y <= (baseY + MAX_DY + 3)
                && z >= (originZ + MIN_DZ - 3) && z <= (originZ + MAX_DZ + 2);
    }

    public boolean isInsideDungeonInterior(Location loc) {
        if ((!active && !generating) || loc == null || dungeonWorld == null || !dungeonWorld.equals(loc.getWorld())) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= (originX - HALF_WIDTH) && x <= (originX + HALF_WIDTH)
                && y >= (baseY + MIN_DY) && y <= (baseY + MAX_DY)
                && z >= (originZ + MIN_DZ) && z <= (originZ + MAX_DZ);
    }

    /**
     * Inicia a geracao da Fortaleza DOOM em uma coordenada aleatoria entre 5000 e 6000 blocos do spawn.
     */
    public void startRandomDoom(World world, CommandSender initiator) {
        if (world == null) {
            return;
        }
        if (active || generating) {
            stopDoom(false);
        }

        this.generating = true;
        if (initiator != null) {
            initiator.sendMessage(Component.text(
                    "⚡ Buscando terreno entre " + MIN_SPAWN_RADIUS + " e " + MAX_SPAWN_RADIUS + " blocos do spawn e erguendo a Fortaleza DOOM...",
                    NamedTextColor.YELLOW));
        }

        Location spawn = world.getSpawnLocation();
        findRandomLandCandidateAsync(world, spawn.getBlockX(), spawn.getBlockZ(), 1, 12)
                .thenAccept(chosenLoc -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!generating) {
                        return;
                    }
                    beginDungeonConstruction(chosenLoc, initiator);
                }));
    }

    /**
     * Inicia a geracao da Fortaleza DOOM imediatamente na frente da localizacao informada (/doom aqui).
     */
    public void startDoomAt(Location targetEntrance, CommandSender initiator) {
        if (targetEntrance == null || targetEntrance.getWorld() == null) {
            return;
        }
        if (active || generating) {
            stopDoom(false);
        }
        this.generating = true;
        if (initiator != null) {
            initiator.sendMessage(Component.text("⚡ Erguendo a gigantesca Fortaleza DOOM nesta localização...", NamedTextColor.YELLOW));
        }
        beginDungeonConstruction(targetEntrance.toBlockLocation(), initiator);
    }

    private CompletableFuture<Location> findRandomLandCandidateAsync(World world, int spawnX, int spawnZ, int attempt, int maxAttempts) {
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2.0);
        double radius = ThreadLocalRandom.current().nextDouble(MIN_SPAWN_RADIUS, MAX_SPAWN_RADIUS + 1);
        int candX = spawnX + (int) Math.round(Math.cos(angle) * radius);
        int candZ = spawnZ + (int) Math.round(Math.sin(angle) * radius);

        return world.getChunkAtAsync(candX >> 4, candZ >> 4, true).thenCompose(chunk -> {
            Block highest = world.getHighestBlockAt(candX, candZ);
            Material mat = highest.getType();
            boolean isLiquid = mat == Material.WATER || mat == Material.LAVA || mat == Material.KELP || mat == Material.SEAGRASS;
            if (!isLiquid || attempt >= maxAttempts) {
                int y = Math.max(world.getMinHeight() + 10, Math.min(world.getMaxHeight() - 30, highest.getY() + 1));
                return CompletableFuture.completedFuture(new Location(world, candX, y, candZ));
            }
            return findRandomLandCandidateAsync(world, spawnX, spawnZ, attempt + 1, maxAttempts);
        });
    }

    private void beginDungeonConstruction(Location chosenEntrance, CommandSender initiator) {
        this.dungeonWorld = chosenEntrance.getWorld();
        this.originX = chosenEntrance.getBlockX();
        this.originZ = chosenEntrance.getBlockZ();

        // Carrega os chunks da estrutura de forma assincrona antes de construir as fatias
        int minChunkX = (originX - HALF_WIDTH - 2) >> 4;
        int maxChunkX = (originX + HALF_WIDTH + 2) >> 4;
        int minChunkZ = (originZ + MIN_DZ - 3) >> 4;
        int maxChunkZ = (originZ + MAX_DZ + 2) >> 4;

        List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunkFutures.add(dungeonWorld.getChunkAtAsync(cx, cz, true));
            }
        }

        CompletableFuture.allOf(chunkFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!generating || dungeonWorld == null) {
                        return;
                    }
                    for (CompletableFuture<Chunk> cf : chunkFutures) {
                        Chunk chunk = cf.join();
                        chunk.addPluginChunkTicket(plugin);
                        ticketedChunks.add(chunkKey(chunk.getX(), chunk.getZ()));
                    }

                    // Calcula a altura ideal da entrada com o chunk da frente ja carregado
                    int surfaceY = dungeonWorld.getHighestBlockYAt(originX, originZ - 3) + 1;
                    this.baseY = Math.max(dungeonWorld.getMinHeight() + 12, Math.min(dungeonWorld.getMaxHeight() - 28, surfaceY));
                    this.entranceLocation = new Location(dungeonWorld, originX + 0.5, baseY + 1, originZ - 2.5);
                    this.chestLocation = new Location(dungeonWorld, originX, baseY + 8, originZ + 107);

                    prepareSpawnerList();
                    startSlicedBuild(initiator);
                }));
    }

    private void prepareSpawnerList() {
        spawnerPoints.clear();
        // Salao 1: Patio da Carnificina (6 spawners)
        spawnerPoints.add(new SpawnerPoint(-12, 2, 7, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(12, 2, 7, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(0, 2, 13, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(-12, 2, 19, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(12, 2, 19, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(0, 2, 20, EntityType.VINDICATOR));

        // Salao 2: Abismo de Fogo - Torres laterais de disparo sobre o Parkour (4 spawners)
        spawnerPoints.add(new SpawnerPoint(-15, 6, 36, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(15, 6, 36, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(-15, 6, 46, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(15, 6, 46, EntityType.BLAZE));

        // Salao 3: Labirinto das Sombras (8 spawners)
        spawnerPoints.add(new SpawnerPoint(-13, 2, 65, EntityType.CAVE_SPIDER));
        spawnerPoints.add(new SpawnerPoint(13, 2, 65, EntityType.CAVE_SPIDER));
        spawnerPoints.add(new SpawnerPoint(-7, 2, 65, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(7, 2, 65, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(0, 2, 68, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(-7, 2, 73, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(7, 2, 73, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(0, 2, 74, EntityType.BLAZE));

        // Salao 4: Pontes do Julgamento (4 spawners nas torres entre as pontes)
        spawnerPoints.add(new SpawnerPoint(-4, 5, 86, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(4, 5, 86, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(-4, 5, 90, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(4, 5, 90, EntityType.BLAZE));

        // Salao 5: Santuario do DOOM (6 spawners de elite)
        spawnerPoints.add(new SpawnerPoint(-10, 6, 100, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(10, 6, 100, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(-6, 6, 104, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(6, 6, 104, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(-10, 6, 108, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(10, 6, 108, EntityType.VINDICATOR));
    }

    /**
     * Constroi a dungeon em fatias ao longo do eixo Z (8 fatias por tick = ~0.8s no total)
     * para garantir zero travamento no servidor mesmo sendo uma estrutura de ~85.000 blocos.
     */
    private void startSlicedBuild(CommandSender initiator) {
        final int[] currentDz = {MIN_DZ};
        final int slicesPerTick = 8;

        this.buildTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dungeonWorld == null) {
                if (buildTask != null) {
                    buildTask.cancel();
                    buildTask = null;
                }
                return;
            }

            int endDz = Math.min(MAX_DZ, currentDz[0] + slicesPerTick - 1);
            for (int dz = currentDz[0]; dz <= endDz; dz++) {
                buildSliceZ(dz);
            }
            currentDz[0] = endDz + 1;

            if (currentDz[0] > MAX_DZ) {
                if (buildTask != null) {
                    buildTask.cancel();
                    buildTask = null;
                }
                finalizeDungeonSetup(initiator);
            }
        }, 1L, 1L);
    }

    private void buildSliceZ(int dz) {
        if (dz < 0) {
            buildExteriorEntrancePlazaSlice(dz);
            return;
        }

        // 1. Casco externo blindado (fundacao, piso base, paredes laterais duplas e teto duplo)
        for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
            for (int dy = MIN_DY; dy <= MAX_DY; dy++) {
                boolean isOuterWallX = Math.abs(dx) >= HALF_WIDTH - 1;
                boolean isFrontWall = dz <= 1;
                boolean isBackWall = dz >= MAX_DZ - 1;
                boolean isFoundation = dy == MIN_DY;
                boolean isFloor = dy == 0;
                boolean isRoof = dy >= MAX_DY - 1;

                if (isFoundation) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_TILES);
                } else if (isRoof) {
                    if (dy == MAX_DY) {
                        setDungeonBlock(dx, dy, dz, Material.POLISHED_DEEPSLATE);
                    } else {
                        boolean isRib = (dz % 6 == 0) || (Math.abs(dx) % 8 == 0);
                        setDungeonBlock(dx, dy, dz, isRib ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                    }
                } else if (isOuterWallX || isFrontWall || isBackWall) {
                    // Entrada Unica da Fortaleza DOOM: dz = 0..1, dx = -2..+2, dy = 1..5
                    if (isFrontWall && Math.abs(dx) <= 2 && dy >= 1 && dy <= 5) {
                        if (Math.abs(dx) == 2 && dy == 5) {
                            setDungeonBlock(dx, dy, dz, Material.CRYING_OBSIDIAN);
                        } else {
                            setDungeonBlock(dx, dy, dz, Material.AIR);
                        }
                        continue;
                    }
                    // Moldura do Portao de Entrada Unica
                    if (isFrontWall && Math.abs(dx) == 3 && dy >= 1 && dy <= 6) {
                        setDungeonBlock(dx, dy, dz, (dy % 2 == 0) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                        continue;
                    }
                    if (isFrontWall && Math.abs(dx) <= 3 && dy == 6) {
                        setDungeonBlock(dx, dy, dz, Material.GILDED_BLACKSTONE);
                        continue;
                    }

                    // Pilares decorativos internos a cada 6 blocos nas paredes laterais
                    boolean isInnerSideLayer = Math.abs(dx) == HALF_WIDTH - 1 && dz > 1 && dz < MAX_DZ - 1;
                    if (isInnerSideLayer && (dz % 6 == 0)) {
                        setDungeonBlock(dx, dy, dz, (dy == 4 || dy == 10) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                    } else if (Math.abs(dx) == HALF_WIDTH || dz == 0 || dz == MAX_DZ) {
                        setDungeonBlock(dx, dy, dz, Material.POLISHED_DEEPSLATE);
                    } else {
                        setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                    }
                } else if (isFloor) {
                    setDungeonBlock(dx, 0, dz, resolveFloorMaterial(dx, dz));
                } else {
                    // Limpa todo o interior (dy = 1..15) antes de erguer as estruturas internas
                    setDungeonBlock(dx, dy, dz, Material.AIR);
                }
            }
        }

        // Luminarias suspensas no teto (dy = 15) em pontos estrategicos longe dos spawners
        if (dz > 2 && dz < MAX_DZ - 2 && dz % 8 == 4) {
            setDungeonBlock(-8, 15, dz, Material.SOUL_LANTERN);
            setDungeonBlock(0, 15, dz, Material.SOUL_LANTERN);
            setDungeonBlock(8, 15, dz, Material.SOUL_LANTERN);
        }

        // 2. Constroi a arquitetura interna de cada um dos 5 Saloes
        if (dz >= 2 && dz <= 24) {
            buildHall1CarnageSlice(dz);
        } else if (dz == 25 || dz == 26) {
            buildPartitionWall(dz, new int[][]{{-11, -9}, {-2, 2}, {9, 11}}, 5, 9, true);
        } else if (dz >= 27 && dz <= 55) {
            buildHall2ParkourAbyssSlice(dz);
        } else if (dz == 56) {
            buildPartitionWall(dz, new int[][]{{-11, -9}, {-2, 2}, {9, 11}}, 5, 9, true);
        } else if (dz >= 57 && dz <= 79) {
            buildHall3ShadowMazeSlice(dz);
        } else if (dz == 80) {
            buildPartitionWall(dz, new int[][]{{-10, -8}, {-2, 2}, {8, 10}}, 5, 9, true);
        } else if (dz >= 81 && dz <= 95) {
            buildHall4JudgmentBridgesSlice(dz);
        } else if (dz == 96) {
            buildPartitionWall(dz, new int[][]{{-4, 4}}, 5, 11, true);
        } else if (dz >= 97 && dz <= 112) {
            buildHall5DoomSanctumSlice(dz);
        }
    }

    private Material resolveFloorMaterial(int dx, int dz) {
        // Salao 2 (Abismo de Parkour: dz 30..52) e Salao 4 (Garganta de Magma: dz 83..92) tem piso de Magma e Areia das Almas
        if ((dz >= 30 && dz <= 52) || (dz >= 83 && dz <= 92)) {
            if ((Math.abs(dx) + dz) % 3 == 0) {
                return Material.SOUL_SAND;
            }
            return ((dx + dz) % 2 == 0) ? Material.MAGMA_BLOCK : Material.BLACKSTONE;
        }
        // Tapete central imperial nos Saloes 1 e 5
        if (Math.abs(dx) <= 2) {
            return (dz % 4 == 0) ? Material.GILDED_BLACKSTONE : Material.RED_NETHER_BRICKS;
        }
        return ((dx + dz) % 5 == 0) ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
    }

    /**
     * Praca Externa Monumental (dz = -10 .. -1):
     * Garante que a entrada unica nunca fique obstruida por morros ou arvores e cria dois obeliscos com Fogo das Almas.
     */
    private void buildExteriorEntrancePlazaSlice(int dz) {
        int plazaHalfWidth = 9;
        for (int dx = -plazaHalfWidth; dx <= plazaHalfWidth; dx++) {
            // Limpa vegetacao/blocos acima da praca
            for (int dy = 1; dy <= MAX_DY; dy++) {
                setDungeonBlock(dx, dy, dz, Material.AIR);
            }
            // Piso da praca e rampa frontal
            if (dz >= -6) {
                Material floorMat = (Math.abs(dx) <= 2) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
                setDungeonBlock(dx, 0, dz, floorMat);
                // Fundacao ate 6 blocos abaixo para nao flutuar em encostas
                for (int dy = -1; dy >= -5; dy--) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                }
            } else {
                // Escadaria externa em dz = -10..-7
                int stepY = dz + 6; // -1, -2, -3, -4
                setDungeonBlock(dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                for (int dy = -1; dy >= stepY - 3; dy--) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                }
            }
        }

        // Dois obeliscos demoniacos flanqueando a entrada em dz = -4, dx = -6 e +6
        if (dz == -4) {
            for (int side : new int[]{-6, 6}) {
                for (int dy = 1; dy <= 6; dy++) {
                    setDungeonBlock(side, dy, dz, (dy == 3 || dy == 6) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                }
                setDungeonBlock(side, 7, dz, Material.NETHERRACK);
                setDungeonBlock(side, 8, dz, Material.SOUL_FIRE);
            }
        }
    }

    /**
     * Salao 1: Patio da Carnificina (dz = 2 .. 24)
     * - 4 grandes colunas, barricadas taticas para PvP de 5 times e 3 escadarias largas ao fundo subindo para dy = 4.
     */
    private void buildHall1CarnageSlice(int dz) {
        // 4 Grandes Pilares 3x3 em (dx = ±8, dz = 8 e 17)
        if (Math.abs(dz - 8) <= 1 || Math.abs(dz - 17) <= 1) {
            for (int centerColX : new int[]{-8, 8}) {
                for (int dx = centerColX - 1; dx <= centerColX + 1; dx++) {
                    for (int dy = 1; dy <= 15; dy++) {
                        boolean isCorner = Math.abs(dx - centerColX) == 1 && (Math.abs(dz - 8) == 1 || Math.abs(dz - 17) == 1);
                        setDungeonBlock(dx, dy, dz, isCorner ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                }
            }
        }

        // Barricadas taticas de combate (dy = 1..2)
        if (dz == 6 || dz == 14) {
            for (int dx = -4; dx <= 4; dx++) {
                if (Math.abs(dx) >= 2) {
                    setDungeonBlock(dx, 1, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    setDungeonBlock(dx, 2, dz, Material.IRON_BARS);
                }
            }
        }

        // 3 Escadarias largas (dz = 21..24) subindo do piso dy=1 para a plataforma do Salao 2 (dy=4)
        if (dz >= 21 && dz <= 24) {
            int stepHeight = dz - 20; // dz 21 -> dy 1; dz 22 -> dy 2; dz 23 -> dy 3; dz 24 -> dy 4
            int[][] stairLanes = {{-11, -9}, {-2, 2}, {9, 11}};
            for (int[] lane : stairLanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    for (int dy = 1; dy <= stepHeight; dy++) {
                        setDungeonBlock(dx, dy, dz, (dy == stepHeight) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                    }
                }
            }
        }
    }

    /**
     * Parede divisoria entre saloes com arcos de passagem e base solida dy = 1..4 quando elevatedFloor = true.
     */
    private void buildPartitionWall(int dz, int[][] doorwaysX, int doorMinY, int doorMaxY, boolean elevatedFloor) {
        for (int dx = -(HALF_WIDTH - 2); dx <= (HALF_WIDTH - 2); dx++) {
            boolean inDoorX = false;
            boolean isDoorFrameX = false;
            for (int[] range : doorwaysX) {
                if (dx >= range[0] && dx <= range[1]) {
                    inDoorX = true;
                } else if (dx == range[0] - 1 || dx == range[1] + 1) {
                    isDoorFrameX = true;
                }
            }

            for (int dy = 1; dy <= 15; dy++) {
                if (elevatedFloor && dy <= 4) {
                    setDungeonBlock(dx, dy, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                } else if (inDoorX && dy >= doorMinY && dy <= doorMaxY) {
                    setDungeonBlock(dx, dy, dz, Material.AIR);
                } else if ((isDoorFrameX && dy >= doorMinY && dy <= doorMaxY + 1) || (inDoorX && dy == doorMaxY + 1)) {
                    setDungeonBlock(dx, dy, dz, (dy % 2 == 0) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                } else {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_TILES);
                }
            }
        }
    }

    /**
     * Salao 2: Abismo das Almas - Grande Parkour em 3 Rotas Paralelas (dz = 27 .. 55)
     * - Balcao inicial: dz = 27..29 (piso em dy = 4, jogador em dy = 5)
     * - Fosso de Magma/Areia das Almas: dz = 30..52 (piso em dy = 0, jogador cai em dy = 1)
     * - Escadas e 5 colunas de escadas de mao (Ladders) em dz = 30..34 para quem cair voltar ao inicio e tentar de novo!
     * - 3 rotas de plataformas de parkour (dx = -11..-9, dx = -1..+1, dx = +9..+11) com saltos de 2 blocos de vao.
     * - Balcao final: dz = 53..55 (piso em dy = 4, bloqueado por baixo para impedir pular o parkour pelo chao).
     */
    private void buildHall2ParkourAbyssSlice(int dz) {
        // Balcao de partida (dz 27..29) e Balcao de chegada (dz 53..55)
        if (dz <= 29 || dz >= 53) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        // Escadas de retorno no fundo do fosso (dz = 30..33) para nunca prender jogadores que cairem
        if (dz == 30) {
            for (int ladderX : new int[]{-13, -5, 0, 5, 13}) {
                for (int dy = 1; dy <= 4; dy++) {
                    setLadderBlock(ladderX, dy, dz, BlockFace.SOUTH);
                }
            }
        }
        if (dz >= 30 && dz <= 33) {
            int stairY = 34 - dz; // dz 33 -> dy 1; dz 32 -> dy 2; dz 31 -> dy 3; dz 30 -> dy 4
            for (int dx : new int[]{-16, -15, 15, 16}) {
                for (int dy = 1; dy <= stairY; dy++) {
                    setDungeonBlock(dx, dy, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        // Balcoes laterais dos Spawners de Esqueleto/Blaze em dx = ±15 (dz = 36 e dz = 46)
        if (Math.abs(dz - 36) <= 1 || Math.abs(dz - 46) <= 1) {
            for (int sideX : new int[]{-15, 15}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= 16) {
                        setDungeonBlock(dx, 5, dz, Material.NETHER_BRICKS);
                    }
                }
            }
        }

        // Plataformas das 3 rotas de Parkour:
        // Pad 1: dz 32..33 (dy = 4)
        // Pad 2: dz 36..37 (dy = 5)
        // Pad 3: dz 40..42 (dy = 5) + pontes laterais em dz = 41
        // Pad 4: dz 45..46 (dy = 6)
        // Pad 5: dz 49..50 (dy = 5)
        int padY = -1;
        if (dz == 32 || dz == 33) {
            padY = 4;
        } else if (dz == 36 || dz == 37) {
            padY = 5;
        } else if (dz >= 40 && dz <= 42) {
            padY = 5;
        } else if (dz == 45 || dz == 46) {
            padY = 6;
        } else if (dz == 49 || dz == 50) {
            padY = 5;
        }

        if (padY != -1) {
            int[][] lanes = {{-11, -9}, {-1, 1}, {9, 11}};
            for (int[] lane : lanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    boolean isCenter = (dx == (lane[0] + lane[1]) / 2);
                    setDungeonBlock(dx, padY, dz, isCenter ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            // Plataformas de cruzamento entre as 3 rotas no meio do abismo (dz = 41)
            if (dz == 41) {
                for (int dx : new int[]{-6, -5, -4, 4, 5, 6}) {
                    setDungeonBlock(dx, padY, dz, Material.RED_NETHER_BRICKS);
                }
            }
        }
    }

    /**
     * Salao 3: Labirinto das Sombras (dz = 57 .. 79)
     * - Escadaria de descida (dz = 57..60) do nivel dy=4 para o chao dy=0.
     * - Labirinto simetrico de 5 grandes corredores com paredes de Deepslate e Grades de Ferro (dy = 1..5),
     *   teias de aranha estrategicas e 8 spawners.
     * - 3 escadarias de subida ao fundo (dz = 77..79) retornando ao nivel dy=4 para o Salao 4.
     */
    private void buildHall3ShadowMazeSlice(int dz) {
        // Escadaria larga de descida na entrada do Salao 3 (dz = 57..60)
        if (dz >= 57 && dz <= 60) {
            int stepY = 61 - dz; // dz 57 -> dy 4; dz 58 -> dy 3; dz 59 -> dy 2; dz 60 -> dy 1
            for (int dx = -16; dx <= 16; dx++) {
                for (int dy = 1; dy <= stepY; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == stepY) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        // 3 Escadarias de subida na saida do Salao 3 (dz = 77..79)
        if (dz >= 77 && dz <= 79) {
            int stepY = dz - 75; // dz 77 -> dy 2; dz 78 -> dy 3; dz 79 -> dy 4
            int[][] exitLanes = {{-10, -8}, {-2, 2}, {8, 10}};
            for (int[] lane : exitLanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    for (int dy = 1; dy <= stepY; dy++) {
                        setDungeonBlock(dx, dy, dz, (dy == stepY) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                    }
                }
            }
            return;
        }

        // Labirinto principal (dz = 62..75)
        if (dz >= 62 && dz <= 75) {
            for (int dx = -16; dx <= 16; dx++) {
                if (isMazeWall(dx, dz)) {
                    for (int dy = 1; dy <= 5; dy++) {
                        Material wallMat = (dy == 3 && (Math.abs(dx) + dz) % 2 == 0)
                                ? Material.IRON_BARS
                                : Material.DEEPSLATE_TILES;
                        setDungeonBlock(dx, dy, dz, wallMat);
                    }
                } else if ((dz == 64 || dz == 71) && (Math.abs(dx) == 14 || Math.abs(dx) == 3)) {
                    // Armadilhas de teia nos cantos dos corredores
                    setDungeonBlock(dx, 1, dz, Material.COBWEB);
                }
            }
        }
    }

    /**
     * Define as paredes do Labirinto das Sombras garantindo 5 corredores longitudinais largos (3 blocos cada)
     * e 3 avenidas transversais com desvios taticos (100% transitavel por todas as 5 equipes).
     */
    private boolean isMazeWall(int dx, int dz) {
        // Avenidas transversais abertas em dz = 65..66 e dz = 72..73
        if (dz == 65 || dz == 66 || dz == 72 || dz == 73) {
            return false;
        }
        // Paredes longitudinais separando os 5 corredores em |dx| = 4, 5 e |dx| = 10, 11
        int absX = Math.abs(dx);
        if (absX == 4 || absX == 5 || absX == 10 || absX == 11) {
            return true;
        }
        // Chicanes (bloqueios alternados) que forcam os jogadores a trocar de corredor nas encruzilhadas
        if (dz == 63 && absX <= 3) {
            return true; // Bloqueia corredor central no inicio, forcando ir pelas laterais
        }
        if (dz == 69 && (absX >= 6 && absX <= 9)) {
            return true; // Bloqueia corredores intermediarios no meio, forcando ir pelo centro ou extremos
        }
        if (dz == 75 && absX >= 12) {
            return true; // Bloqueia extremos no final, convergindo para as 3 escadas de saida
        }
        return false;
    }

    /**
     * Salao 4: Pontes do Julgamento (dz = 81 .. 95)
     * - Balcao inicial: dz = 81..82 (dy = 4)
     * - Garganta de Magma: dz = 83..92 com escadas laterais de retorno (dx = ±15..16, dz = 83..86) e escadas de mao
     * - 3 Pontes quebradas de parkour (dx = -10..-8, dx = -1..+1, dx = +8..+10) flanqueadas por 4 torres de Blaze/Esqueleto
     * - Balcao final antes do Santuario: dz = 93..95 (dy = 4)
     */
    private void buildHall4JudgmentBridgesSlice(int dz) {
        if (dz <= 82 || dz >= 93) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        // Escadas de mao e degraus de retorno em dz = 83..86 caso o jogador caia na garganta
        if (dz == 83) {
            for (int ladderX : new int[]{-12, 0, 12}) {
                for (int dy = 1; dy <= 4; dy++) {
                    setLadderBlock(ladderX, dy, dz, BlockFace.SOUTH);
                }
            }
        }
        if (dz >= 83 && dz <= 86) {
            int stairY = 87 - dz; // dz 86 -> dy 1 .. dz 83 -> dy 4
            for (int dx : new int[]{-16, -15, 15, 16}) {
                for (int dy = 1; dy <= stairY; dy++) {
                    setDungeonBlock(dx, dy, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }

        // 4 Torres de Spawners entre as pontes (dx = -4 e +4, dz = 86 e 90)
        if (dz == 86 || dz == 90) {
            for (int towerX : new int[]{-4, 4}) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(towerX, dy, dz, Material.CRYING_OBSIDIAN);
                }
            }
        }

        // Segmentos de Parkour das 3 Pontes Quebradas:
        // Segmento A: dz = 85..86 (dy = 4)
        // Segmento B: dz = 89..90 (dy = 5)
        int bridgeY = -1;
        if (dz == 85 || dz == 86) {
            bridgeY = 4;
        } else if (dz == 89 || dz == 90) {
            bridgeY = 5;
        }

        if (bridgeY != -1) {
            int[][] bridgeLanes = {{-10, -8}, {-1, 1}, {8, 10}};
            for (int[] lane : bridgeLanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    boolean center = (dx == (lane[0] + lane[1]) / 2);
                    setDungeonBlock(dx, bridgeY, dz, center ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
    }

    /**
     * Salao 5: Santuario do DOOM (dz = 97 .. 112)
     * - Piso imperial elevado em dy = 4 (jogadores caminham em dy = 5)
     * - Colunas de lava nos 4 cantos protegidas por vidro vermelho
     * - Altar piramidal em (dx = 0, dz = 107) com o Bau Final em (dx = 0, dy = 8, dz = 107)
     */
    private void buildHall5DoomSanctumSlice(int dz) {
        for (int dx = -16; dx <= 16; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dy < 4) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                } else {
                    boolean isRoyalCarpet = Math.abs(dx) <= 3;
                    boolean isRuneRing = (Math.abs(dx) == 6 || Math.abs(dz - 107) == 4) && Math.abs(dx) <= 8;
                    if (isRoyalCarpet) {
                        setDungeonBlock(dx, 4, dz, Material.RED_NETHER_BRICKS);
                    } else if (isRuneRing) {
                        setDungeonBlock(dx, 4, dz, Material.GILDED_BLACKSTONE);
                    } else {
                        setDungeonBlock(dx, 4, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                }
            }
        }

        // Colunas de Lava nos 4 cantos (dx = ±14, dz = 99 e 110)
        if (Math.abs(dz - 99) <= 1 || Math.abs(dz - 110) <= 1) {
            int centerZ = (Math.abs(dz - 99) <= 1) ? 99 : 110;
            for (int cornerX : new int[]{-14, 14}) {
                for (int dx = cornerX - 1; dx <= cornerX + 1; dx++) {
                    for (int dy = 5; dy <= 15; dy++) {
                        boolean isCore = (dx == cornerX && dz == centerZ);
                        setDungeonBlock(dx, dy, dz, isCore ? Material.LAVA : Material.RED_STAINED_GLASS);
                    }
                }
            }
        }

        // Altar Central do Bau DOOM (centro em dx = 0, dz = 107)
        int distZ = Math.abs(dz - 107);
        if (distZ <= 2) {
            for (int dx = -2; dx <= 2; dx++) {
                int chebyshev = Math.max(Math.abs(dx), distZ);
                // Camada 1 do altar: dy = 5 (5x5)
                setDungeonBlock(dx, 5, dz, (chebyshev == 2) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.CRYING_OBSIDIAN);
                // Camada 2 do altar: dy = 6 (3x3)
                if (chebyshev <= 1) {
                    setDungeonBlock(dx, 6, dz, (chebyshev == 1) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                }
                // Camada 3 do altar: dy = 7 (1x1 Bloco de Ouro sob o Bau + 4 fogos das almas nos cantos)
                if (chebyshev == 0) {
                    setDungeonBlock(0, 7, 107, Material.GOLD_BLOCK);
                } else if (Math.abs(dx) == 1 && distZ == 1) {
                    setDungeonBlock(dx, 7, dz, Material.SOUL_LANTERN);
                }
            }
        }
    }

    private void finalizeDungeonSetup(CommandSender initiator) {
        if (dungeonWorld == null) {
            return;
        }

        // 1. Configura todos os 28 Spawners nos 5 saloes
        for (SpawnerPoint sp : spawnerPoints) {
            if (sp.dy() > 1) {
                setDungeonBlock(sp.dx(), sp.dy() - 1, sp.dz(), Material.CHISELED_POLISHED_BLACKSTONE);
            }
            setDungeonBlock(sp.dx(), sp.dy(), sp.dz(), Material.SPAWNER);
            Block block = dungeonWorld.getBlockAt(originX + sp.dx(), baseY + sp.dy(), originZ + sp.dz());
            if (block.getState() instanceof CreatureSpawner spawner) {
                spawner.setSpawnedType(sp.type());
                spawner.setMinSpawnDelay(80);
                spawner.setMaxSpawnDelay(200);
                spawner.setSpawnCount(3);
                spawner.setMaxNearbyEntities(14);
                spawner.setRequiredPlayerRange(24);
                spawner.setSpawnRange(4);
                spawner.update(true, false);
            }
        }

        // 2. Posiciona e abastece o Bau Final com o Coracao do Doom (+200 pts) e tesouros extras
        setDungeonBlock(0, 8, 107, Material.CHEST);
        Block chestBlock = dungeonWorld.getBlockAt(originX, baseY + 8, originZ + 107);
        if (chestBlock.getBlockData() instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH); // Virado para quem chega da entrada
            chestBlock.setBlockData(directional, false);
        }
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.clear();
            inv.setItem(4, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            inv.setItem(10, new ItemStack(Material.DIAMOND, 12));
            inv.setItem(11, new ItemStack(Material.GOLDEN_APPLE, 6));
            inv.setItem(12, new ItemStack(Material.NETHERITE_INGOT, 1));
            inv.setItem(13, createDoomArtifactItem()); // Centro: O Item de +200 Pontos!
            inv.setItem(14, new ItemStack(Material.NETHERITE_INGOT, 1));
            inv.setItem(15, new ItemStack(Material.GOLDEN_APPLE, 6));
            inv.setItem(16, new ItemStack(Material.DIAMOND, 12));
            inv.setItem(22, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }

        // 3. Spawna o Esquadrao de Guardioes de Elite iniciais no Santuario e nos Saloes
        spawnInitialDungeonGuardians();

        // 4. Ativa estado oficial do evento, Hologramas, BossBar, Bussolas e Waypoint do Xaero
        this.generating = false;
        this.active = true;
        this.artifactClaimed = false;
        this.collapseSecondsRemaining = 0;

        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();
        XaeroWaypointUtil.broadcastWaypoint("Fortaleza DOOM", "D", entranceLocation, XaeroWaypointUtil.COLOR_DARK_RED);

        if (initiator != null) {
            initiator.sendMessage(Component.text(
                    "✔ Fortaleza DOOM gerada com sucesso! Entrada em X: " + entranceLocation.getBlockX()
                            + ", Y: " + entranceLocation.getBlockY()
                            + ", Z: " + entranceLocation.getBlockZ(),
                    NamedTextColor.GREEN));
        }

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("      💀 FORTALEZA DOOM SURGIU NO MAPA! 💀", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  Uma dungeon colossal com ", NamedTextColor.GRAY)
                .append(Component.text("UMA ÚNICA ENTRADA", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" foi aberta!", NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.text("  Entrada: ", NamedTextColor.GRAY)
                .append(Component.text("X: " + entranceLocation.getBlockX()
                        + ", Y: " + entranceLocation.getBlockY()
                        + ", Z: " + entranceLocation.getBlockZ(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" (Overworld)", NamedTextColor.DARK_GRAY)));
        Bukkit.broadcast(Component.text("  Desafios: ", NamedTextColor.GRAY)
                .append(Component.text("5 Salões • Parkour • Labirinto • Hordas", NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("  Baú Final: ", NamedTextColor.GRAY)
                .append(Component.text("🔥 Coração do Doom (+" + DOOM_REWARD_POINTS + " PONTOS)", NamedTextColor.GREEN, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        Title startTitle = Title.title(
                Component.text("💀 FORTALEZA DOOM 💀", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Entrada: X " + entranceLocation.getBlockX() + ", Z " + entranceLocation.getBlockZ()
                        + " | Baú: +" + DOOM_REWARD_POINTS + " pts", NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(800))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(startTitle);
            online.playSound(online.getLocation(), Sound.ENTITY_WITHER_SPAWN, SoundCategory.MASTER, 0.85f, 0.75f);
        }
    }

    private void spawnInitialDungeonGuardians() {
        if (dungeonWorld == null) {
            return;
        }
        // Chefe Guardiao do Altar no Salao 5
        Location bossLoc = new Location(dungeonWorld, originX + 0.5, baseY + 5, originZ + 103.5);
        dungeonWorld.spawn(bossLoc, WitherSkeleton.class, boss -> {
            tagDungeonMob(boss);
            boss.customName(Component.text("💀 Guardião Supremo DOOM", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            boss.setCustomNameVisible(true);
            AttributeInstance maxHp = boss.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.setBaseValue(100.0);
                boss.setHealth(100.0);
            }
            EntityEquipment eq = boss.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                eq.setHelmetDropChance(0f);
                eq.setChestplateDropChance(0f);
                eq.setLeggingsDropChance(0f);
                eq.setBootsDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            }
        });

        // 4 Guardas Brutos de Elite no Santuario
        int[][] bruteOffsets = {{-5, 102}, {5, 102}, {-4, 108}, {4, 108}};
        for (int[] offset : bruteOffsets) {
            Location bruteLoc = new Location(dungeonWorld, originX + offset[0] + 0.5, baseY + 5, originZ + offset[1] + 0.5);
            dungeonWorld.spawn(bruteLoc, PiglinBrute.class, brute -> {
                tagDungeonMob(brute);
                brute.setImmuneToZombification(true);
                brute.customName(Component.text("⚔ Carrasco DOOM", NamedTextColor.RED, TextDecoration.BOLD));
                brute.setCustomNameVisible(true);
            });
        }
    }

    private void tagDungeonMob(LivingEntity mob) {
        mob.getPersistentDataContainer().set(doomMobKey, PersistentDataType.BYTE, (byte) 1);
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);
        spawnedMobIds.add(mob.getUniqueId());
    }

    /**
     * Reivindica o Coracao do Doom (+200 pontos) para a equipe do jogador que o pegou no bau final.
     */
    public void claimArtifact(Player player, Team team) {
        if (!active || artifactClaimed || player == null || team == null) {
            return;
        }

        this.artifactClaimed = true;
        this.collapseSecondsRemaining = COLLAPSE_COUNTDOWN_SECONDS;

        teamManager.awardTeamPoints(team, DOOM_REWARD_POINTS, "Conquistou o Coração da Fortaleza DOOM (" + player.getName() + ")");
        int newTotal = team.getPoints();
        ItemStack trophy = createDoomTrophyItem(team, player);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(trophy);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        spawnVictoryFirework(player.getLocation());
        spawnOrUpdateHolograms();
        updateBossBar();

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("   🏆 FORTALEZA DOOM CONQUISTADA! 🏆", NamedTextColor.GREEN, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  Conquistador: ", NamedTextColor.GRAY)
                .append(team.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("  Recompensa: ", NamedTextColor.GRAY)
                .append(Component.text("+" + DOOM_REWARD_POINTS + " PONTOS", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" para a equipe ", NamedTextColor.GRAY))
                .append(Component.text(team.getName(), team.getColor(), TextDecoration.BOLD))
                .append(Component.text(" (Total: " + newTotal + " pts)!", NamedTextColor.YELLOW)));
        Bukkit.broadcast(Component.text("  ⚠️ A Fortaleza DOOM entrará em colapso em " + COLLAPSE_COUNTDOWN_SECONDS + " segundos!", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        Title winTitle = Title.title(
                Component.text("🏆 DOOM CONQUISTADA! 🏆", NamedTextColor.GOLD, TextDecoration.BOLD),
                team.getFormattedTag()
                        .append(Component.text(" " + player.getName() + " pegou o Coração (+" + DOOM_REWARD_POINTS + " pts)!", NamedTextColor.GREEN)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(winTitle);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 0.8f, 0.9f);
        }
    }

    private void tick() {
        if (!active || dungeonWorld == null) {
            return;
        }

        tickCounter++;
        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();

        // Particulas na Entrada Unica e no Altar Final
        if (entranceLocation != null) {
            dungeonWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, entranceLocation.clone().add(0, 1.5, 2.0), 15, 1.6, 1.2, 0.4, 0.02);
        }
        if (chestLocation != null && !artifactClaimed) {
            dungeonWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, chestLocation.clone().add(0.5, 0.8, 0.5), 14, 0.4, 0.5, 0.4, 0.02);
            dungeonWorld.spawnParticle(Particle.TOTEM_OF_UNDYING, chestLocation.clone().add(0.5, 1.1, 0.5), 8, 0.35, 0.4, 0.35, 0.05);
        }

        // A cada 6 segundos, ativa ondas extras nos spawners proximos a jogadores dentro da dungeon
        if (tickCounter % 6 == 0 && !artifactClaimed) {
            pulseNearbySpawners();
        }

        // Contagem regressiva de colapso apos o Coracao do Doom ser conquistado
        if (artifactClaimed) {
            collapseSecondsRemaining--;
            if (collapseSecondsRemaining == 30 || collapseSecondsRemaining == 15 || (collapseSecondsRemaining <= 5 && collapseSecondsRemaining > 0)) {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (isInsideDungeonInterior(online.getLocation())) {
                        online.sendMessage(Component.text("⚠️ A Fortaleza DOOM vai desmoronar em " + collapseSecondsRemaining + "s! Saia pela entrada!", NamedTextColor.RED, TextDecoration.BOLD));
                        online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 1.0f, 0.7f);
                    }
                }
            }
            if (collapseSecondsRemaining <= 0) {
                stopDoom(false);
            }
        }
    }

    /**
     * Garante que os spawners da dungeon gerem monstros ativamente quando houver jogadores por perto,
     * mesmo onde houver iluminacao decorativa.
     */
    private void pulseNearbySpawners() {
        if (dungeonWorld == null) {
            return;
        }
        spawnedMobIds.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || !e.isValid() || e.isDead();
        });
        if (spawnedMobIds.size() >= 65) {
            return;
        }

        for (SpawnerPoint sp : spawnerPoints) {
            Location spLoc = new Location(dungeonWorld, originX + sp.dx() + 0.5, baseY + sp.dy(), originZ + sp.dz() + 0.5);
            boolean playerNear = false;
            int nearbyMobs = 0;

            for (Player p : dungeonWorld.getPlayers()) {
                if (!p.isDead() && p.getLocation().distanceSquared(spLoc) <= 20.0 * 20.0) {
                    playerNear = true;
                    break;
                }
            }
            if (!playerNear) {
                continue;
            }

            for (Entity entity : dungeonWorld.getNearbyEntities(spLoc, 8, 5, 8)) {
                if (entity instanceof Monster) {
                    nearbyMobs++;
                }
            }
            if (nearbyMobs >= 5) {
                continue;
            }

            // Encontra uma posicao livre ao redor do spawner para invocar 1-2 monstros
            int offsetX = ThreadLocalRandom.current().nextInt(-2, 3);
            int offsetZ = ThreadLocalRandom.current().nextInt(-2, 3);
            if (offsetX == 0 && offsetZ == 0) {
                offsetX = 1;
            }
            Location spawnLoc = spLoc.clone().add(offsetX, 0, offsetZ);
            if (!spawnLoc.getBlock().isPassable() || !spawnLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
                continue;
            }

            Entity spawned = dungeonWorld.spawnEntity(spawnLoc, sp.type());
            if (spawned instanceof LivingEntity living) {
                tagDungeonMob(living);
                if (living instanceof Mob mob) {
                    Player nearest = findNearestPlayer(spawnLoc, 20.0);
                    if (nearest != null) {
                        mob.setTarget(nearest);
                    }
                }
                dungeonWorld.spawnParticle(Particle.FLAME, spawnLoc.clone().add(0, 1, 0), 12, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    private Player findNearestPlayer(Location loc, double maxDist) {
        Player best = null;
        double bestDistSq = maxDist * maxDist;
        if (loc.getWorld() == null) {
            return null;
        }
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.isDead()) continue;
            double dSq = p.getLocation().distanceSquared(loc);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = p;
            }
        }
        return best;
    }

    public void stopDoom(boolean announceCancel) {
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
        boolean wasActiveOrGenerating = this.active || this.generating;
        this.active = false;
        this.generating = false;
        this.artifactClaimed = false;
        this.collapseSecondsRemaining = 0;

        if (compassManager != null) {
            compassManager.removeCompassFromAllPlayers(CompassEventType.DOOM);
        }
        clearBossBar();
        removeHolograms();
        removeDungeonMobs();

        // Evacua com seguranca qualquer jogador dentro da estrutura antes de restaurar o terreno
        List<Player> evacuatedPlayers = new ArrayList<>();
        if (dungeonWorld != null) {
            for (Player p : dungeonWorld.getPlayers()) {
                if (isProtectedLocation(p.getLocation())) {
                    evacuatedPlayers.add(p);
                }
            }
        }

        // Esvazia o bau final antes de restaurar os blocos para nao dropar itens no chao
        if (chestLocation != null && chestLocation.getBlock().getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
        }

        restoreOriginalBlocks();

        // Teleporta jogadores evacuados para o topo natural do terreno restaurado
        for (Player p : evacuatedPlayers) {
            Location safe = p.getLocation();
            int safeY = safe.getWorld().getHighestBlockYAt(safe.getBlockX(), safe.getBlockZ()) + 1;
            safe.setY(safeY);
            p.teleport(safe);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 4, false, false, true));
        }

        releaseChunkTickets();

        this.dungeonWorld = null;
        this.entranceLocation = null;
        this.chestLocation = null;
        this.spawnerPoints.clear();

        if (wasActiveOrGenerating && announceCancel) {
            Bukkit.broadcast(Component.text("💀 A Fortaleza DOOM desapareceu nas sombras e o terreno foi restaurado.", NamedTextColor.RED));
        }
    }

    private void setDungeonBlock(int dx, int dy, int dz, Material material) {
        setDungeonBlockData(dx, dy, dz, Bukkit.createBlockData(material));
    }

    private void setLadderBlock(int dx, int dy, int dz, BlockFace facing) {
        Ladder ladder = (Ladder) Bukkit.createBlockData(Material.LADDER);
        ladder.setFacing(facing);
        setDungeonBlockData(dx, dy, dz, ladder);
    }

    private void setDungeonBlockData(int dx, int dy, int dz, BlockData newData) {
        if (dungeonWorld == null) {
            return;
        }
        int wx = originX + dx;
        int wy = baseY + dy;
        int wz = originZ + dz;
        if (wy < dungeonWorld.getMinHeight() || wy >= dungeonWorld.getMaxHeight()) {
            return;
        }
        Block block = dungeonWorld.getBlockAt(wx, wy, wz);
        Location key = block.getLocation();
        if (!originalBlocks.containsKey(key)) {
            originalBlocks.put(key, block.getBlockData().clone());
        }
        block.setBlockData(newData, false);
    }

    private void restoreOriginalBlocks() {
        if (originalBlocks.isEmpty()) {
            return;
        }
        List<Map.Entry<Location, BlockData>> entries = new ArrayList<>(originalBlocks.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            Map.Entry<Location, BlockData> entry = entries.get(i);
            Location loc = entry.getKey();
            if (loc.getWorld() != null) {
                loc.getBlock().setBlockData(entry.getValue(), false);
            }
        }
        originalBlocks.clear();
    }

    private void removeDungeonMobs() {
        for (UUID id : spawnedMobIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedMobIds.clear();
        if (dungeonWorld != null) {
            for (Entity entity : dungeonWorld.getEntitiesByClass(LivingEntity.class)) {
                if (entity.getPersistentDataContainer().has(doomMobKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    private void releaseChunkTickets() {
        if (dungeonWorld != null) {
            for (long key : ticketedChunks) {
                int cx = (int) (key >> 32);
                int cz = (int) key;
                dungeonWorld.removePluginChunkTicket(cx, cz, plugin);
            }
        }
        ticketedChunks.clear();
    }

    private long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    private void spawnOrUpdateHolograms() {
        if (!active || dungeonWorld == null) {
            return;
        }
        if (entranceLocation != null) {
            Location holoLoc = new Location(dungeonWorld, originX + 0.5, baseY + 6.8, originZ - 0.5);
            Component text = Component.text("💀 FORTALEZA DOOM 💀\n", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("Entrada Única — 5 Salões de Desafio\n", NamedTextColor.GOLD))
                    .append(Component.text("Baú Final: +" + DOOM_REWARD_POINTS + " Pontos de Equipe", NamedTextColor.GREEN, TextDecoration.BOLD));
            entranceHologramId = updateSingleHologram(entranceHologramId, holoLoc, text);
        }
        if (chestLocation != null) {
            Location holoLoc = chestLocation.clone().add(0.5, 1.5, 0.5);
            Component text = artifactClaimed
                    ? Component.text("💀 BAÚ DO DOOM CONQUISTADO 💀\n", NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text("Colapso em " + collapseSecondsRemaining + "s!", NamedTextColor.YELLOW, TextDecoration.BOLD))
                    : Component.text("🔥 BAÚ FINAL DO DOOM 🔥\n", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("Abra e pegue o Coração (+" + DOOM_REWARD_POINTS + " PONTOS)", NamedTextColor.GREEN, TextDecoration.BOLD));
            chestHologramId = updateSingleHologram(chestHologramId, holoLoc, text);
        }
    }

    private UUID updateSingleHologram(UUID currentId, Location holoLoc, Component text) {
        TextDisplay display = null;
        if (currentId != null) {
            Entity entity = Bukkit.getEntity(currentId);
            if (entity instanceof TextDisplay td && td.isValid()) {
                display = td;
            }
        }
        if (display == null && holoLoc.getWorld() != null) {
            display = holoLoc.getWorld().spawn(holoLoc, TextDisplay.class, td -> {
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setShadowed(true);
                td.setDefaultBackground(false);
                td.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setViewRange(1.5f);
            });
        }
        if (display != null) {
            display.text(text);
            return display.getUniqueId();
        }
        return currentId;
    }

    private void removeHolograms() {
        if (entranceHologramId != null) {
            Entity e = Bukkit.getEntity(entranceHologramId);
            if (e != null) e.remove();
            entranceHologramId = null;
        }
        if (chestHologramId != null) {
            Entity e = Bukkit.getEntity(chestHologramId);
            if (e != null) e.remove();
            chestHologramId = null;
        }
    }

    private void updateBossBar() {
        if (!active || entranceLocation == null) {
            clearBossBar();
            return;
        }
        Component title;
        float progress;
        BossBar.Color barColor;

        if (artifactClaimed) {
            title = Component.text("⚠️ COLAPSO DA FORTALEZA DOOM EM " + collapseSecondsRemaining + "s!", NamedTextColor.RED, TextDecoration.BOLD);
            progress = Math.max(0.0f, Math.min(1.0f, (float) collapseSecondsRemaining / COLLAPSE_COUNTDOWN_SECONDS));
            barColor = BossBar.Color.RED;
        } else {
            title = Component.text("💀 FORTALEZA DOOM ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("| Entrada: ", NamedTextColor.GRAY))
                    .append(Component.text("X: " + entranceLocation.getBlockX()
                            + " Y: " + entranceLocation.getBlockY()
                            + " Z: " + entranceLocation.getBlockZ(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" | Baú Final: ", NamedTextColor.GRAY))
                    .append(Component.text("+" + DOOM_REWARD_POINTS + " pts", NamedTextColor.GOLD, TextDecoration.BOLD));
            progress = 1.0f;
            barColor = BossBar.Color.PURPLE;
        }

        if (doomBossBar == null) {
            doomBossBar = BossBar.bossBar(title, progress, barColor, BossBar.Overlay.PROGRESS);
        } else {
            doomBossBar.name(title);
            doomBossBar.progress(progress);
            doomBossBar.color(barColor);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showBossBar(doomBossBar);
        }
    }

    private void clearBossBar() {
        if (doomBossBar != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.hideBossBar(doomBossBar);
            }
            doomBossBar = null;
        }
    }

    public void updateEventCompasses() {
        if (compassManager == null || !active || entranceLocation == null) {
            return;
        }
        String label = "Entrada da Fortaleza DOOM (X: " + entranceLocation.getBlockX()
                + ", Y: " + entranceLocation.getBlockY()
                + ", Z: " + entranceLocation.getBlockZ() + ")";
        compassManager.giveOrUpdateAllPlayers(CompassEventType.DOOM, entranceLocation, label);
    }

    private void spawnVictoryFirework(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().spawn(loc.clone().add(0, 1.2, 0), Firework.class, fw -> {
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.RED, Color.ORANGE, Color.YELLOW)
                    .withFade(Color.WHITE)
                    .trail(true)
                    .flicker(true)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        });
    }
}
