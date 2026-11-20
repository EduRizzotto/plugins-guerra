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
import org.bukkit.GameMode;
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
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gerencia o mega-evento da Fortaleza DOOM (/doom):
 * - Gera uma dungeon colossal (41 blocos de largura x 257 blocos de comprimento x 19 blocos de altura),
 *   totalmente fechada e com APENAS UMA ENTRADA frontal, localizada entre 5000 e 6000 blocos de raio do spawn.
 * - Projetada para a disputa simultanea de 5 equipes, dividida em 10 grandes saloes interligados:
 *     1. Patio da Carnificina (Arena PvP/PvE inicial com barricadas e 6 spawners)
 *     2. Abismo das Almas (Grande Parkour de 3 rotas paralelas sobre magma/almas, com escadas de retorno)
 *     3. Labirinto das Sombras (Labirinto de 5 corredores largos com teias e 8 spawners)
 *     4. Pontes do Julgamento (Parkour de precisao em 3 pontes quebradas sob fogo de Blazes e Esqueletos)
 *     5. Catacumbas Congeladas (Arena escorregadia de Gelo Azul, teias, pilares e 6 spawners de Esqueletos/Strays)
 *     6. Espiral do Vacuo (Parkour de 5 rotas de pilares ascendentes e passarelas suspensas)
 *     7. Forja Infernal (3 grandes fornalhas de lava com grades, corredores taticos e 6 spawners)
 *     8. Pendulos da Morte (Parkour com obstaculos suspensos de Obsidiana Chorona e Correntes)
 *     9. Coliseu dos Campeoes (Anfiteatro pre-santuario com ringue central, guardas e 6 spawners de elite)
 *    10. Santuario do DOOM (Salao do Trono com Guardioes de Elite e o Bau Final com o Coracao do Doom: +200 pts)
 * - Totalmente indestrutivel e bloqueada contra colocacao/quebra de blocos, baldes, explosoes e perolas do fim.
 */
public class DoomManager {

    public static final int MIN_SPAWN_RADIUS = 5000;
    public static final int MAX_SPAWN_RADIUS = 6000;
    public static final int DOOM_REWARD_POINTS = 200;
    public static final int GATE_UNLOCK_SECONDS = 3 * 60; // 3 minutos (180s) apos um jogador se aproximar
    public static final double GATE_TRIGGER_RADIUS = 25.0; // Raio de aproximacao da porta da frente
    public static final int COLLAPSE_COUNTDOWN_SECONDS = 60;

    // Dimensoes locais relativas a (originX, baseY, originZ)
    public static final int HALF_WIDTH = 20;     // dx: -20 .. +20 (41 blocos de largura; interior -18..+18 = 37 blocos)
    public static final int MIN_DZ = -10;        // Praca externa de entrada: -10 .. -1
    public static final int MAX_DZ = 246;        // Parede traseira selada: 245 .. 246 (257 blocos de comprimento total!)
    public static final int MIN_DY = -1;         // Fundacao: -1
    public static final int MAX_DY = 17;         // Teto blindado: 16 .. 17

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private EventCompassManager compassManager;

    private final NamespacedKey doomArtifactKey;
    private final NamespacedKey doomMobKey;

    private boolean generating = false;
    private boolean active = false;
    private boolean gateOpened = false;
    private boolean gateCountdownStarted = false;
    private int gateSecondsRemaining = GATE_UNLOCK_SECONDS;
    private boolean artifactClaimed = false;
    private int collapseSecondsRemaining = 0;

    private UUID carrierId;
    private UUID droppedArtifactEntityId;

    private World dungeonWorld;
    private int originX;
    private int baseY;
    private int originZ;

    private Location entranceLocation;
    private Location chestLocation;
    private UUID entranceHologramId;
    private UUID chestHologramId;

    private final Map<Location, BlockData> originalBlocks = new LinkedHashMap<>();
    private final Set<Location> playerPlacedBlocks = new HashSet<>();
    private final Map<Location, BlockData> extraPlayerOriginalBlocks = new LinkedHashMap<>();
    private final List<SpawnerPoint> spawnerPoints = new ArrayList<>();
    private final Set<UUID> spawnedMobIds = new HashSet<>();
    private final Set<Long> ticketedChunks = new HashSet<>();

    private BossBar doomBossBar;
    private BukkitTask tickTask;
    private BukkitTask buildTask;
    private int tickCounter = 0;

    public enum DoomVariant {
        DOOM_1("Fortaleza DOOM", "Cidadela Infernal", Material.DEEPSLATE_BRICKS, Material.CRYING_OBSIDIAN, Material.GILDED_BLACKSTONE),
        DOOM_2("Fortaleza DOOM II", "Abismo Corrompido", Material.DARK_PRISMARINE, Material.SCULK, Material.SEA_LANTERN),
        DOOM_3("Fortaleza DOOM III", "Templo do Sol Sangrento", Material.RED_NETHER_BRICKS, Material.GILDED_BLACKSTONE, Material.GOLD_BLOCK);

        private final String title;
        private final String subtitle;
        private final Material innerWallMaterial;
        private final Material pillarMaterial;
        private final Material accentMaterial;

        DoomVariant(String title, String subtitle, Material innerWallMaterial, Material pillarMaterial, Material accentMaterial) {
            this.title = title;
            this.subtitle = subtitle;
            this.innerWallMaterial = innerWallMaterial;
            this.pillarMaterial = pillarMaterial;
            this.accentMaterial = accentMaterial;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getFullTitle() {
            return title + " (" + subtitle + ")";
        }

        public Material getInnerWallMaterial() {
            return innerWallMaterial;
        }

        public Material getPillarMaterial() {
            return pillarMaterial;
        }

        public Material getAccentMaterial() {
            return accentMaterial;
        }
    }

    private DoomVariant currentVariant = DoomVariant.DOOM_1;

    record SpawnerPoint(int dx, int dy, int dz, EntityType type) {
    }

    public DoomManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.doomArtifactKey = new NamespacedKey(plugin, "doom_artifact_200pts");
        this.doomMobKey = new NamespacedKey(plugin, "doom_dungeon_mob");
        loadDoomState();
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
        if (generating) {
            // Se ainda estava no meio da construcao incompleta ao desligar, reverte
            stopDoom(false);
            return;
        }
        if (active) {
            // Preserva a Fortaleza DOOM ativa no disco para continuar funcionando (e permitir /doom parar) apos reiniciar
            saveDynamicState();
            clearBossBar();
            releaseChunkTickets();
        }
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

    public UUID getCarrierId() {
        return carrierId;
    }

    public boolean isCarrier(Player player) {
        return player != null && carrierId != null && carrierId.equals(player.getUniqueId());
    }

    /**
     * Verifica se a localizacao ainda esta dentro do casco fechado da Fortaleza (dz >= 0).
     * Quando o portador cruza a Entrada Unica para fora (dz < 0 na praca externa ou fora das paredes),
     * ele escapou da dungeon com sucesso!
     */
    public boolean isInsideSealedFortress(Location loc) {
        if ((!active && !generating) || loc == null || dungeonWorld == null || !dungeonWorld.equals(loc.getWorld())) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= (originX - HALF_WIDTH) && x <= (originX + HALF_WIDTH)
                && y >= (baseY + MIN_DY) && y <= (baseY + MAX_DY)
                && z >= originZ && z <= (originZ + MAX_DZ);
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
                    Component.text("Artefato ancestral guardado na 10ª sala", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("da colossal Fortaleza DOOM.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("⚡ Pegue este artefato e SAIA DA DUNGEON", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("   pela entrada única para conquistar", NamedTextColor.YELLOW)
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

    /**
     * Verifica se uma localizacao esta dentro da regiao protegida da Fortaleza DOOM.
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

    private Location normalizeBlockLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public boolean isPlayerPlacedBlock(Location loc) {
        Location norm = normalizeBlockLoc(loc);
        return norm != null && playerPlacedBlocks.contains(norm);
    }

    public void registerPlayerPlacedBlock(Location loc, BlockData previousData) {
        Location norm = normalizeBlockLoc(loc);
        if (norm == null) {
            return;
        }
        if (!originalBlocks.containsKey(norm) && previousData != null) {
            BlockData cloned = previousData.clone();
            originalBlocks.put(norm, cloned);
            extraPlayerOriginalBlocks.put(norm, cloned);
        }
        playerPlacedBlocks.add(norm);
    }

    public void unregisterPlayerPlacedBlock(Location loc) {
        Location norm = normalizeBlockLoc(loc);
        if (norm != null) {
            playerPlacedBlocks.remove(norm);
        }
    }

    /**
     * Registra qualquer mob spawnado pelos CreatureSpawners da dungeon para garantir remocao total no colapso.
     */
    public void registerDungeonMob(Entity entity) {
        if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
            tagDungeonMob(living);
        }
    }

    public void startRandomDoom(World world, CommandSender initiator) {
        startRandomDoom(world, initiator, DoomVariant.DOOM_1);
    }

    public void startRandomDoom(World world, CommandSender initiator, DoomVariant variant) {
        if (world == null) {
            return;
        }
        if (active || generating) {
            stopDoom(false);
        }

        this.currentVariant = (variant != null) ? variant : DoomVariant.DOOM_1;
        this.generating = true;
        if (initiator != null) {
            initiator.sendMessage(Component.text(
                    "⚡ Buscando terreno entre " + MIN_SPAWN_RADIUS + " e " + MAX_SPAWN_RADIUS + " blocos do spawn e erguendo a "
                            + currentVariant.getFullTitle() + " (10 Salões)...",
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

    public void startDoomAt(Location targetEntrance, CommandSender initiator) {
        startDoomAt(targetEntrance, initiator, DoomVariant.DOOM_1);
    }

    public void startDoomAt(Location targetEntrance, CommandSender initiator, DoomVariant variant) {
        if (targetEntrance == null || targetEntrance.getWorld() == null) {
            return;
        }
        if (active || generating) {
            stopDoom(false);
        }
        this.currentVariant = (variant != null) ? variant : DoomVariant.DOOM_1;
        this.generating = true;
        if (initiator != null) {
            initiator.sendMessage(Component.text("⚡ Erguendo a colossal " + currentVariant.getFullTitle() + " (10 Salões) nesta localização...", NamedTextColor.YELLOW));
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

                    int surfaceY = dungeonWorld.getHighestBlockYAt(originX, originZ - 3) + 1;
                    this.baseY = Math.max(dungeonWorld.getMinHeight() + 12, Math.min(dungeonWorld.getMaxHeight() - 28, surfaceY));
                    this.entranceLocation = new Location(dungeonWorld, originX + 0.5, baseY + 1, originZ - 2.5);
                    this.chestLocation = new Location(dungeonWorld, originX, baseY + 8, originZ + 238);

                    prepareSpawnerList();
                    startSlicedBuild(initiator);
                }));
    }

    private void prepareSpawnerList() {
        if (currentVariant != DoomVariant.DOOM_1) {
            DoomVariantArchitect.populateVariantSpawners(currentVariant, spawnerPoints);
            return;
        }
        spawnerPoints.clear();
        // Sala 1: Patio da Carnificina (6 spawners)
        spawnerPoints.add(new SpawnerPoint(-13, 2, 7, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(13, 2, 7, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(0, 2, 13, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(-13, 2, 18, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(13, 2, 18, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(0, 2, 19, EntityType.VINDICATOR));

        // Sala 2: Abismo das Almas - Torres laterais sobre o Parkour (4 spawners)
        spawnerPoints.add(new SpawnerPoint(-17, 6, 36, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(17, 6, 36, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(-17, 6, 46, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(17, 6, 46, EntityType.BLAZE));

        // Sala 3: Labirinto das Sombras (8 spawners)
        spawnerPoints.add(new SpawnerPoint(-14, 2, 65, EntityType.CAVE_SPIDER));
        spawnerPoints.add(new SpawnerPoint(14, 2, 65, EntityType.CAVE_SPIDER));
        spawnerPoints.add(new SpawnerPoint(-7, 2, 65, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(7, 2, 65, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(0, 2, 68, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(-7, 2, 73, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(7, 2, 73, EntityType.HUSK));
        spawnerPoints.add(new SpawnerPoint(0, 2, 74, EntityType.BLAZE));

        // Sala 4: Pontes do Julgamento (4 spawners)
        spawnerPoints.add(new SpawnerPoint(-5, 5, 88, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(5, 5, 88, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(-5, 5, 93, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(5, 5, 93, EntityType.BLAZE));

        // Sala 5: Catacumbas Congeladas (6 spawners)
        spawnerPoints.add(new SpawnerPoint(-13, 2, 108, EntityType.STRAY));
        spawnerPoints.add(new SpawnerPoint(13, 2, 108, EntityType.STRAY));
        spawnerPoints.add(new SpawnerPoint(0, 2, 113, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(-13, 2, 118, EntityType.STRAY));
        spawnerPoints.add(new SpawnerPoint(13, 2, 118, EntityType.STRAY));
        spawnerPoints.add(new SpawnerPoint(0, 2, 119, EntityType.VINDICATOR));

        // Sala 6: Espiral do Vacuo (4 spawners nas passarelas suspensas)
        spawnerPoints.add(new SpawnerPoint(-17, 7, 136, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(17, 7, 136, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(-17, 7, 144, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(17, 7, 144, EntityType.BLAZE));

        // Sala 7: Forja Infernal (6 spawners ao redor das fornalhas de lava)
        spawnerPoints.add(new SpawnerPoint(-14, 2, 160, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(14, 2, 160, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(-6, 2, 165, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(6, 2, 165, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(-14, 2, 170, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(14, 2, 170, EntityType.VINDICATOR));

        // Sala 8: Pendulos da Morte (4 spawners)
        spawnerPoints.add(new SpawnerPoint(-17, 6, 186, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(17, 6, 186, EntityType.SKELETON));
        spawnerPoints.add(new SpawnerPoint(-17, 6, 194, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(17, 6, 194, EntityType.BLAZE));

        // Sala 9: Coliseu dos Campeoes (6 spawners no anfiteatro)
        spawnerPoints.add(new SpawnerPoint(-14, 6, 210, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(14, 6, 210, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(0, 6, 215, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(-14, 6, 221, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(14, 6, 221, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(0, 6, 222, EntityType.BLAZE));

        // Sala 10: Santuario do DOOM (6 spawners de elite ao redor do Bau Final)
        spawnerPoints.add(new SpawnerPoint(-11, 6, 231, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(11, 6, 231, EntityType.WITHER_SKELETON));
        spawnerPoints.add(new SpawnerPoint(-7, 6, 235, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(7, 6, 235, EntityType.BLAZE));
        spawnerPoints.add(new SpawnerPoint(-11, 6, 239, EntityType.VINDICATOR));
        spawnerPoints.add(new SpawnerPoint(11, 6, 239, EntityType.VINDICATOR));
    }

    private void startSlicedBuild(CommandSender initiator) {
        final int[] currentDz = {MIN_DZ};
        final int slicesPerTick = 10;

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
                    // Inicia FECHADA (Grades de Ferro por fora em dz=0 e Obsidiana Chorona por dentro em dz=1)
                    if (isFrontWall && Math.abs(dx) <= 2 && dy >= 1 && dy <= 5) {
                        if (Math.abs(dx) == 2 && dy == 5) {
                            setDungeonBlock(dx, dy, dz, Material.CRYING_OBSIDIAN);
                        } else if (dz == 0) {
                            setDungeonBlock(dx, dy, dz, Material.IRON_BARS);
                        } else {
                            setDungeonBlock(dx, dy, dz, Material.CRYING_OBSIDIAN);
                        }
                        continue;
                    }
                    if (isFrontWall && Math.abs(dx) == 3 && dy >= 1 && dy <= 6) {
                        setDungeonBlock(dx, dy, dz, (dy % 2 == 0) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                        continue;
                    }
                    if (isFrontWall && Math.abs(dx) <= 3 && dy == 6) {
                        setDungeonBlock(dx, dy, dz, Material.GILDED_BLACKSTONE);
                        continue;
                    }

                    boolean isInnerSideLayer = Math.abs(dx) == HALF_WIDTH - 1 && dz > 1 && dz < MAX_DZ - 1;
                    if (isInnerSideLayer && (dz % 6 == 0)) {
                        setDungeonBlock(dx, dy, dz, (dy == 4 || dy == 10) ? currentVariant.getAccentMaterial() : currentVariant.getPillarMaterial());
                    } else if (Math.abs(dx) == HALF_WIDTH || dz == 0 || dz == MAX_DZ) {
                        setDungeonBlock(dx, dy, dz, Material.POLISHED_DEEPSLATE);
                    } else {
                        setDungeonBlock(dx, dy, dz, currentVariant.getInnerWallMaterial());
                    }
                } else if (isFloor) {
                    setDungeonBlock(dx, 0, dz, resolveFloorMaterial(dx, dz));
                } else {
                    setDungeonBlock(dx, dy, dz, Material.AIR);
                }
            }
        }

        // Luminarias suspensas no teto (dy = 15)
        if (dz > 2 && dz < MAX_DZ - 2 && dz % 8 == 4) {
            setDungeonBlock(-10, 15, dz, Material.SOUL_LANTERN);
            setDungeonBlock(0, 15, dz, Material.SOUL_LANTERN);
            setDungeonBlock(10, 15, dz, Material.SOUL_LANTERN);
        }

        int[][] standardThreeDoors = {{-12, -10}, {-2, 2}, {10, 12}};

        if (currentVariant == DoomVariant.DOOM_2) {
            DoomVariantArchitect.buildDoom2InteriorSlice(this, dz, standardThreeDoors);
            return;
        } else if (currentVariant == DoomVariant.DOOM_3) {
            DoomVariantArchitect.buildDoom3InteriorSlice(this, dz, standardThreeDoors);
            return;
        }

        // 2. Arquitetura interna dos 10 Saloes Interligados (DOOM 1)
        if (dz >= 2 && dz <= 24) {
            buildHall1CarnageSlice(dz, standardThreeDoors);
        } else if (dz == 25 || dz == 26) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 27 && dz <= 55) {
            buildHall2ParkourAbyssSlice(dz);
        } else if (dz == 56) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 57 && dz <= 80) {
            buildHall3ShadowMazeSlice(dz, standardThreeDoors);
        } else if (dz == 81) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 82 && dz <= 100) {
            buildHall4JudgmentBridgesSlice(dz);
        } else if (dz == 101) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 102 && dz <= 125) {
            buildHall5FrozenCatacombsSlice(dz, standardThreeDoors);
        } else if (dz == 126) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 127 && dz <= 152) {
            buildHall6VoidSpiralParkourSlice(dz);
        } else if (dz == 153) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 154 && dz <= 177) {
            buildHall7InfernalForgeSlice(dz, standardThreeDoors);
        } else if (dz == 178) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 179 && dz <= 203) {
            buildHall8PendulumParkourSlice(dz);
        } else if (dz == 204) {
            buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 205 && dz <= 226) {
            buildHall9ChampionsColiseumSlice(dz);
        } else if (dz == 227) {
            buildPartitionWall(dz, new int[][]{{-4, 4}}, 5, 11, true);
        } else if (dz >= 228 && dz <= 244) {
            buildHall10DoomSanctumSlice(dz);
        }
    }

    private Material resolveFloorMaterial(int dx, int dz) {
        if (currentVariant != DoomVariant.DOOM_1) {
            return DoomVariantArchitect.resolveVariantFloorMaterial(currentVariant, dx, dz);
        }
        // Fossos de Parkour (Salas 2, 4, 6 e 8): Magma e Areia das Almas
        if ((dz >= 30 && dz <= 52)
                || (dz >= 85 && dz <= 96)
                || (dz >= 130 && dz <= 149)
                || (dz >= 182 && dz <= 199)) {
            if ((Math.abs(dx) + dz) % 3 == 0) {
                return Material.SOUL_SAND;
            }
            return ((dx + dz) % 2 == 0) ? Material.MAGMA_BLOCK : Material.BLACKSTONE;
        }
        // Sala 5: Catacumbas Congeladas (dz = 106..121) -> Piso escorregadio de Gelo Azul e Gelo Compactado
        if (dz >= 106 && dz <= 121) {
            if ((Math.abs(dx) + dz) % 5 == 0) {
                return Material.SOUL_SAND;
            }
            return ((dx + dz) % 2 == 0) ? Material.BLUE_ICE : Material.PACKED_ICE;
        }
        // Tapete central imperial
        if (Math.abs(dx) <= 2) {
            return (dz % 4 == 0) ? Material.GILDED_BLACKSTONE : Material.RED_NETHER_BRICKS;
        }
        return ((dx + dz) % 5 == 0) ? Material.CRACKED_POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
    }

    /**
     * Helper universal para escadarias que sobem 4 blocos (dy = 1 -> 2 -> 3 -> 4) ao longo de 4 fatias Z
     * usando blocos reais de escada (POLISHED_BLACKSTONE_BRICK_STAIRS) para subida fluida sem precisar pular!
     */
    void buildAscendingStairsSlice(int dz, int startDz, int[][] lanes) {
        int stepHeight = (dz - startDz) + 1; // 1, 2, 3, 4
        if (stepHeight < 1 || stepHeight > 4) {
            return;
        }
        for (int[] lane : lanes) {
            for (int dx = lane[0]; dx <= lane[1]; dx++) {
                for (int dy = 1; dy < stepHeight; dy++) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                }
                setStairBlock(dx, stepHeight, dz, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.SOUTH);
                // Garante espaco livre acima dos degraus
                for (int dy = stepHeight + 1; dy <= stepHeight + 5; dy++) {
                    setDungeonBlock(dx, dy, dz, Material.AIR);
                }
            }
        }
    }

    /**
     * Helper universal para escadarias largas que descem 4 blocos (dy = 4 -> 3 -> 2 -> 1) ao longo de 4 fatias Z.
     */
    void buildDescendingStairsSlice(int dz, int startDz) {
        int stepHeight = 4 - (dz - startDz); // 4, 3, 2, 1
        if (stepHeight < 1 || stepHeight > 4) {
            return;
        }
        int innerWidth = HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy < stepHeight; dy++) {
                setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
            }
            setStairBlock(dx, stepHeight, dz, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH);
        }
    }

    /**
     * Helper para escadas e ladders de retorno no inicio dos fossos de parkour (direcao -Z de volta ao balcao inicial).
     */
    void buildPitRecoveryStairsAndLadders(int dz, int pitStartDz) {
        if (dz == pitStartDz) {
            for (int ladderX : new int[]{-14, -6, 0, 6, 14}) {
                for (int dy = 1; dy <= 4; dy++) {
                    setLadderBlock(ladderX, dy, dz, BlockFace.SOUTH);
                }
            }
        }
        if (dz >= pitStartDz && dz <= pitStartDz + 3) {
            int stairY = 4 - (dz - pitStartDz); // pitStartDz+3 -> dy 1 .. pitStartDz -> dy 4
            for (int dx : new int[]{-18, -17, 17, 18}) {
                for (int dy = 1; dy < stairY; dy++) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                }
                setStairBlock(dx, stairY, dz, Material.POLISHED_BLACKSTONE_BRICK_STAIRS, BlockFace.NORTH);
            }
        }
    }

    private void buildExteriorEntrancePlazaSlice(int dz) {
        int plazaHalfWidth = 10;
        for (int dx = -plazaHalfWidth; dx <= plazaHalfWidth; dx++) {
            for (int dy = 1; dy <= MAX_DY; dy++) {
                setDungeonBlock(dx, dy, dz, Material.AIR);
            }
            if (dz >= -6) {
                Material floorMat = (Math.abs(dx) <= 2) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
                setDungeonBlock(dx, 0, dz, floorMat);
                for (int dy = -1; dy >= -5; dy--) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                }
            } else {
                int stepY = dz + 6;
                setDungeonBlock(dx, 0, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                for (int dy = -1; dy >= stepY - 3; dy--) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                }
            }
        }

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
     * SALA 1: Patio da Carnificina (dz = 2 .. 24)
     */
    private void buildHall1CarnageSlice(int dz, int[][] exitStairLanes) {
        if (Math.abs(dz - 8) <= 1 || Math.abs(dz - 17) <= 1) {
            for (int centerColX : new int[]{-9, 9}) {
                for (int dx = centerColX - 1; dx <= centerColX + 1; dx++) {
                    for (int dy = 1; dy <= 15; dy++) {
                        boolean isCorner = Math.abs(dx - centerColX) == 1 && (Math.abs(dz - 8) == 1 || Math.abs(dz - 17) == 1);
                        setDungeonBlock(dx, dy, dz, isCorner ? Material.CRYING_OBSIDIAN : Material.POLISHED_BLACKSTONE_BRICKS);
                    }
                }
            }
        }

        if (dz == 6 || dz == 14) {
            for (int dx = -5; dx <= 5; dx++) {
                if (Math.abs(dx) >= 2) {
                    setDungeonBlock(dx, 1, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    setDungeonBlock(dx, 2, dz, Material.IRON_BARS);
                }
            }
        }

        if (dz >= 21 && dz <= 24) {
            buildAscendingStairsSlice(dz, 21, exitStairLanes);
        }
    }

    void buildPartitionWall(int dz, int[][] doorwaysX, int doorMinY, int doorMaxY, boolean elevatedFloor) {
        int innerWidth = HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
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
     * SALA 2: Abismo das Almas - Grande Parkour em 3 Rotas Paralelas (dz = 27 .. 55)
     */
    private void buildHall2ParkourAbyssSlice(int dz) {
        int innerWidth = HALF_WIDTH - 2;
        if (dz <= 29 || dz >= 53) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        buildPitRecoveryStairsAndLadders(dz, 30);

        if (Math.abs(dz - 36) <= 1 || Math.abs(dz - 46) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        setDungeonBlock(dx, 5, dz, Material.NETHER_BRICKS);
                    }
                }
            }
        }

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
            int[][] lanes = {{-12, -10}, {-1, 1}, {10, 12}};
            for (int[] lane : lanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    boolean isCenter = (dx == (lane[0] + lane[1]) / 2);
                    setDungeonBlock(dx, padY, dz, isCenter ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            if (dz == 41) {
                for (int dx : new int[]{-7, -6, -5, -4, 4, 5, 6, 7}) {
                    setDungeonBlock(dx, padY, dz, Material.RED_NETHER_BRICKS);
                }
            }
        }
    }

    /**
     * SALA 3: Labirinto das Sombras (dz = 57 .. 80)
     * - Escadaria de descida: dz = 57..60 (dy = 4 -> 3 -> 2 -> 1)
     * - Labirinto de 5 corredores: dz = 62..75
     * - Escadaria de subida CORRIGIDA (4 degraus suaves com escadas reais): dz = 77..80 (dy = 1 -> 2 -> 3 -> 4)!
     */
    private void buildHall3ShadowMazeSlice(int dz, int[][] exitStairLanes) {
        if (dz >= 57 && dz <= 60) {
            buildDescendingStairsSlice(dz, 57);
            return;
        }

        if (dz >= 77 && dz <= 80) {
            buildAscendingStairsSlice(dz, 77, exitStairLanes);
            return;
        }

        int innerWidth = HALF_WIDTH - 2;
        if (dz >= 62 && dz <= 75) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                if (isMazeWall(dx, dz)) {
                    for (int dy = 1; dy <= 5; dy++) {
                        Material wallMat = (dy == 3 && (Math.abs(dx) + dz) % 2 == 0)
                                ? Material.IRON_BARS
                                : Material.DEEPSLATE_TILES;
                        setDungeonBlock(dx, dy, dz, wallMat);
                    }
                } else if ((dz == 64 || dz == 71) && (Math.abs(dx) == 15 || Math.abs(dx) == 3)) {
                    setDungeonBlock(dx, 1, dz, Material.COBWEB);
                }
            }
        }
    }

    private boolean isMazeWall(int dx, int dz) {
        if (dz == 65 || dz == 66 || dz == 72 || dz == 73) {
            return false;
        }
        int absX = Math.abs(dx);
        if (absX == 4 || absX == 5 || absX == 10 || absX == 11) {
            return true;
        }
        if (dz == 63 && absX <= 3) {
            return true;
        }
        if (dz == 69 && (absX >= 6 && absX <= 9)) {
            return true;
        }
        if (dz == 75 && absX >= 12) {
            return true;
        }
        return false;
    }

    /**
     * SALA 4: Pontes do Julgamento (dz = 82 .. 100)
     */
    private void buildHall4JudgmentBridgesSlice(int dz) {
        int innerWidth = HALF_WIDTH - 2;
        if (dz <= 84 || dz >= 97) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        buildPitRecoveryStairsAndLadders(dz, 85);

        if (dz == 88 || dz == 93) {
            for (int towerX : new int[]{-5, 5}) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(towerX, dy, dz, Material.CRYING_OBSIDIAN);
                }
            }
        }

        int bridgeY = -1;
        if (dz == 87 || dz == 88) {
            bridgeY = 4;
        } else if (dz == 91 || dz == 92) {
            bridgeY = 5;
        } else if (dz == 94 || dz == 95) {
            bridgeY = 4;
        }

        if (bridgeY != -1) {
            int[][] bridgeLanes = {{-12, -10}, {-1, 1}, {10, 12}};
            for (int[] lane : bridgeLanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    boolean center = (dx == (lane[0] + lane[1]) / 2);
                    setDungeonBlock(dx, bridgeY, dz, center ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
    }

    /**
     * SALA 5 (NOVA): Catacumbas Congeladas (dz = 102 .. 125)
     * - Escadaria de descida: dz = 102..105 (dy = 4 -> 3 -> 2 -> 1)
     * - Arena de Gelo Azul escorregadio com pilares de Gelo Compactado, braseiros de Fogo das Almas,
     *   armadilhas de teia/neve fofa e 6 Spawners de Strays/Wither Skeletons/Vindicators.
     * - Escadaria de subida: dz = 122..125 (dy = 1 -> 2 -> 3 -> 4)
     */
    private void buildHall5FrozenCatacombsSlice(int dz, int[][] exitStairLanes) {
        if (dz >= 102 && dz <= 105) {
            buildDescendingStairsSlice(dz, 102);
            return;
        }
        if (dz >= 122 && dz <= 125) {
            buildAscendingStairsSlice(dz, 122, exitStairLanes);
            return;
        }

        // Pilares de Gelo e Obsidiana Chorona (dz = 109, 114, 119)
        if (dz == 109 || dz == 114 || dz == 119) {
            for (int colX : new int[]{-10, 0, 10}) {
                if (dz == 114 && colX == 0) continue; // Centro livre para o Spawner
                for (int dx = colX - 1; dx <= colX + 1; dx++) {
                    for (int dy = 1; dy <= 15; dy++) {
                        boolean isCore = (dx == colX);
                        setDungeonBlock(dx, dy, dz, isCore ? Material.CRYING_OBSIDIAN : Material.PACKED_ICE);
                    }
                }
            }
        }

        // Barricadas congeladas e teias nas laterais
        if (dz == 111 || dz == 116) {
            for (int dx : new int[]{-15, -5, 5, 15}) {
                setDungeonBlock(dx, 1, dz, Material.BLUE_ICE);
                setDungeonBlock(dx, 2, dz, Material.SOUL_LANTERN);
            }
            setDungeonBlock(-17, 1, dz, Material.COBWEB);
            setDungeonBlock(17, 1, dz, Material.COBWEB);
        }
    }

    /**
     * SALA 6 (NOVA): Espiral do Vacuo - Parkour de 5 Rotas de Pilares Ascendentes (dz = 127 .. 152)
     * - 5 rotas paralelas (uma para cada equipe!) que sobem ate dy = 7 no centro e descem ate o balcao final.
     * - Passarela transversal no topo (dz = 140) conectando todas as 5 rotas.
     */
    private void buildHall6VoidSpiralParkourSlice(int dz) {
        int innerWidth = HALF_WIDTH - 2;
        if (dz <= 129 || dz >= 150) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        buildPitRecoveryStairsAndLadders(dz, 130);

        if (Math.abs(dz - 136) <= 1 || Math.abs(dz - 144) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        setDungeonBlock(dx, 6, dz, Material.NETHER_BRICKS);
                    }
                }
            }
        }

        int padY = -1;
        if (dz == 132 || dz == 133) {
            padY = 5;
        } else if (dz == 136 || dz == 137) {
            padY = 6;
        } else if (dz >= 139 && dz <= 141) {
            padY = 7;
        } else if (dz == 144 || dz == 145) {
            padY = 6;
        } else if (dz == 147 || dz == 148) {
            padY = 5;
        }

        if (padY != -1) {
            int[][] fiveTeamLanes = {{-15, -13}, {-8, -6}, {-1, 1}, {6, 8}, {13, 15}};
            for (int[] lane : fiveTeamLanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    boolean center = (dx == (lane[0] + lane[1]) / 2);
                    setDungeonBlock(dx, padY, dz, center ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            if (dz == 140) {
                for (int dx = -15; dx <= 15; dx++) {
                    setDungeonBlock(dx, padY, dz, (Math.abs(dx) % 2 == 0) ? Material.RED_NETHER_BRICKS : Material.GILDED_BLACKSTONE);
                }
            }
        }
    }

    /**
     * SALA 7 (NOVA): Forja Infernal (dz = 154 .. 177)
     * - Escadaria de descida: dz = 154..157
     * - 3 Grandes Fornalhas/Caldeiroes de Lava cercados por Grades de Ferro e Deepslate no centro,
     *   passarelas taticas de combate e 6 Spawners de Blazes, Wither Skeletons e Vindicators.
     * - Escadaria de subida: dz = 174..177
     */
    private void buildHall7InfernalForgeSlice(int dz, int[][] exitStairLanes) {
        if (dz >= 154 && dz <= 157) {
            buildDescendingStairsSlice(dz, 154);
            return;
        }
        if (dz >= 174 && dz <= 177) {
            buildAscendingStairsSlice(dz, 174, exitStairLanes);
            return;
        }

        // 3 Grandes Caldeiroes de Forja (centros em dz = 161, 166, 171 e dx = -9, 0, +9)
        for (int forgeZ : new int[]{161, 166, 171}) {
            if (Math.abs(dz - forgeZ) <= 1) {
                for (int forgeX : new int[]{-9, 0, 9}) {
                    for (int dx = forgeX - 1; dx <= forgeX + 1; dx++) {
                        boolean isCenter = (dx == forgeX && dz == forgeZ);
                        setDungeonBlock(dx, 1, dz, isCenter ? Material.LAVA : Material.POLISHED_BLACKSTONE_BRICKS);
                        setDungeonBlock(dx, 2, dz, isCenter ? Material.LAVA : Material.IRON_BARS);
                        setDungeonBlock(dx, 3, dz, isCenter ? Material.AIR : Material.IRON_BARS);
                        setDungeonBlock(dx, 4, dz, Material.DEEPSLATE_TILES);
                    }
                }
            }
        }
    }

    /**
     * SALA 8 (NOVA): Pendulos da Morte - Parkour com Correntes e Obstaculos Suspensos (dz = 179 .. 203)
     */
    private void buildHall8PendulumParkourSlice(int dz) {
        int innerWidth = HALF_WIDTH - 2;
        if (dz <= 181 || dz >= 200) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
                }
            }
            return;
        }

        buildPitRecoveryStairsAndLadders(dz, 182);

        if (Math.abs(dz - 186) <= 1 || Math.abs(dz - 194) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        setDungeonBlock(dx, 5, dz, Material.NETHER_BRICKS);
                    }
                }
            }
        }

        int padY = -1;
        if (dz == 184 || dz == 185) {
            padY = 4;
        } else if (dz == 188 || dz == 189) {
            padY = 5;
        } else if (dz == 192 || dz == 193) {
            padY = 5;
        } else if (dz == 196 || dz == 197) {
            padY = 4;
        }

        if (padY != -1) {
            int[][] lanes = {{-12, -10}, {-1, 1}, {10, 12}};
            for (int[] lane : lanes) {
                int centerLaneX = (lane[0] + lane[1]) / 2;
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    setDungeonBlock(dx, padY, dz, (dx == centerLaneX) ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                }
                // Pendulo suspenso do teto no centro da plataforma em dz = 188 e dz = 193, exigindo contornar pela borda da plataforma
                if (dz == 188 || dz == 193) {
                    setDungeonBlock(centerLaneX, padY + 1, dz, Material.CRYING_OBSIDIAN);
                    setDungeonBlock(centerLaneX, padY + 2, dz, Material.CRYING_OBSIDIAN);
                    for (int cy = padY + 3; cy <= 15; cy++) {
                        setDungeonBlock(centerLaneX, cy, dz, Material.IRON_BARS);
                    }
                }
            }
        }
    }

    /**
     * SALA 9 (NOVA): Coliseu dos Campeoes - Pre-Camara de Guerra das 5 Equipes (dz = 205 .. 226)
     * - Arena em nivel elevado (dy = 4, jogadores em dy = 5) com arquibancadas laterais,
     *   ringue central de Ouro e Obsidiana Chorona e 6 Spawners de Elite antes do Portao do Santuario.
     */
    private void buildHall9ChampionsColiseumSlice(int dz) {
        int innerWidth = HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS);
            }
            // Arquibancadas laterais em degraus (|dx| >= 15)
            int absX = Math.abs(dx);
            if (absX >= 15) {
                int bleacherH = 4 + (absX - 14);
                for (int dy = 5; dy <= bleacherH; dy++) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_TILE_SLAB);
                }
            }
        }

        // Ringue central de combate (centro em dx = 0, dz = 215, raio 5)
        int distZ = Math.abs(dz - 215);
        if (distZ <= 5) {
            for (int dx = -5; dx <= 5; dx++) {
                int cheb = Math.max(Math.abs(dx), distZ);
                if (cheb == 5) {
                    setDungeonBlock(dx, 4, dz, Material.GILDED_BLACKSTONE);
                    if (Math.abs(dx) == 5 && distZ == 5) {
                        for (int dy = 5; dy <= 8; dy++) {
                            setDungeonBlock(dx, dy, dz, Material.CRYING_OBSIDIAN);
                        }
                        setDungeonBlock(dx, 9, dz, Material.NETHERRACK);
                        setDungeonBlock(dx, 10, dz, Material.SOUL_FIRE);
                    }
                } else if (cheb <= 2) {
                    setDungeonBlock(dx, 4, dz, Material.RED_NETHER_BRICKS);
                }
            }
        }
    }

    /**
     * SALA 10: Santuario do DOOM (dz = 228 .. 244)
     * - Altar Central em (dx = 0, dz = 238) com o Bau Final de +200 Pontos em (dx = 0, dy = 8, dz = 238)
     */
    private void buildHall10DoomSanctumSlice(int dz) {
        int innerWidth = HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dy < 4) {
                    setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_BRICKS);
                } else {
                    boolean isRoyalCarpet = Math.abs(dx) <= 3;
                    boolean isRuneRing = (Math.abs(dx) == 7 || Math.abs(dz - 238) == 4) && Math.abs(dx) <= 9;
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

        // Colunas de Lava nos 4 cantos (dx = ±15, dz = 230 e 241)
        if (Math.abs(dz - 230) <= 1 || Math.abs(dz - 241) <= 1) {
            int centerZ = (Math.abs(dz - 230) <= 1) ? 230 : 241;
            for (int cornerX : new int[]{-15, 15}) {
                for (int dx = cornerX - 1; dx <= cornerX + 1; dx++) {
                    for (int dy = 5; dy <= 15; dy++) {
                        boolean isCore = (dx == cornerX && dz == centerZ);
                        setDungeonBlock(dx, dy, dz, isCore ? Material.LAVA : Material.RED_STAINED_GLASS);
                    }
                }
            }
        }

        // Altar Central do Bau DOOM (centro em dx = 0, dz = 238)
        int distZ = Math.abs(dz - 238);
        if (distZ <= 2) {
            for (int dx = -2; dx <= 2; dx++) {
                int chebyshev = Math.max(Math.abs(dx), distZ);
                setDungeonBlock(dx, 5, dz, (chebyshev == 2) ? Material.POLISHED_BLACKSTONE_BRICKS : Material.CRYING_OBSIDIAN);
                if (chebyshev <= 1) {
                    setDungeonBlock(dx, 6, dz, (chebyshev == 1) ? Material.GILDED_BLACKSTONE : Material.CRYING_OBSIDIAN);
                }
                if (chebyshev == 0) {
                    setDungeonBlock(0, 7, 238, Material.GOLD_BLOCK);
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

        // 1. Configura todos os 54 Spawners distribuidos pelas 10 salas
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

        // 2. Posiciona e abastece o Bau Final na 10a Sala (dx = 0, dy = 8, dz = 238)
        setDungeonBlock(0, 8, 238, Material.CHEST);
        Block chestBlock = dungeonWorld.getBlockAt(originX, baseY + 8, originZ + 238);
        if (chestBlock.getBlockData() instanceof Directional directional) {
            directional.setFacing(BlockFace.NORTH);
            chestBlock.setBlockData(directional, false);
        }
        if (chestBlock.getState() instanceof Chest chest) {
            Inventory inv = chest.getBlockInventory();
            inv.clear();
            inv.setItem(4, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            inv.setItem(10, new ItemStack(Material.DIAMOND, 12));
            inv.setItem(11, new ItemStack(Material.GOLDEN_APPLE, 6));
            inv.setItem(12, new ItemStack(Material.NETHERITE_INGOT, 1));
            inv.setItem(13, createDoomArtifactItem());
            inv.setItem(14, new ItemStack(Material.NETHERITE_INGOT, 1));
            inv.setItem(15, new ItemStack(Material.GOLDEN_APPLE, 6));
            inv.setItem(16, new ItemStack(Material.DIAMOND, 12));
            inv.setItem(22, new ItemStack(Material.TOTEM_OF_UNDYING, 1));
        }

        // 3. Spawna os Guardioes de Elite iniciais
        spawnInitialDungeonGuardians();

        // 4. Ativa estado oficial do evento, Hologramas, BossBar, Bussolas e Waypoint do Xaero
        this.generating = false;
        this.active = true;
        this.gateOpened = false;
        this.gateCountdownStarted = false;
        this.gateSecondsRemaining = GATE_UNLOCK_SECONDS;
        this.artifactClaimed = false;
        this.carrierId = null;
        this.droppedArtifactEntityId = null;
        this.collapseSecondsRemaining = 0;

        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();
        saveBlocksArchive();
        saveDynamicState();
        XaeroWaypointUtil.broadcastWaypoint(currentVariant.getTitle(), "D", entranceLocation, XaeroWaypointUtil.COLOR_DARK_RED);

        if (initiator != null) {
            initiator.sendMessage(Component.text(
                    "✔ " + currentVariant.getFullTitle() + " (10 Salões) gerada com sucesso! Entrada em X: " + entranceLocation.getBlockX()
                            + ", Y: " + entranceLocation.getBlockY()
                            + ", Z: " + entranceLocation.getBlockZ(),
                    NamedTextColor.GREEN));
        }

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("   💀 " + currentVariant.getTitle().toUpperCase() + " SURGIU NO MAPA! 💀", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  Tema: ", NamedTextColor.GRAY)
                .append(Component.text(currentVariant.getSubtitle(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" (10 Salões • Entrada Única)", NamedTextColor.YELLOW)));
        Bukkit.broadcast(Component.text("  Entrada: ", NamedTextColor.GRAY)
                .append(Component.text("X: " + entranceLocation.getBlockX()
                        + ", Y: " + entranceLocation.getBlockY()
                        + ", Z: " + entranceLocation.getBlockZ(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" (Overworld)", NamedTextColor.DARK_GRAY)));
        Bukkit.broadcast(Component.text("  Portão Principal: ", NamedTextColor.GRAY)
                .append(Component.text("🔒 FECHADO (Abre 3 min após alguém se aproximar!)", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("  Baú Final: ", NamedTextColor.GRAY)
                .append(Component.text("🔥 Coração do Doom (+" + DOOM_REWARD_POINTS + " PONTOS ao sair com ele)", NamedTextColor.GREEN, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        Title startTitle = Title.title(
                Component.text("💀 " + currentVariant.getTitle().toUpperCase() + " 💀", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text(currentVariant.getSubtitle() + " | X " + entranceLocation.getBlockX() + ", Z " + entranceLocation.getBlockZ()
                        + " | +" + DOOM_REWARD_POINTS + " pts", NamedTextColor.GOLD),
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
        // Chefe Guardiao do Altar na 10a Sala
        Location bossLoc = new Location(dungeonWorld, originX + 0.5, baseY + 5, originZ + 234.5);
        dungeonWorld.spawn(bossLoc, WitherSkeleton.class, boss -> {
            tagDungeonMob(boss);
            boss.customName(Component.text("💀 Guardião Supremo DOOM", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            boss.setCustomNameVisible(true);
            AttributeInstance maxHp = boss.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.setBaseValue(120.0);
                boss.setHealth(120.0);
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

        // 6 Guardas Brutos de Elite (2 no Coliseu Sala 9 + 4 no Santuario Sala 10)
        int[][] bruteOffsets = {{-4, 215}, {4, 215}, {-5, 233}, {5, 233}, {-4, 239}, {4, 239}};
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

    public boolean pickupArtifactFromChest(Player player, Team team) {
        if (!active || artifactClaimed || player == null || team == null) {
            return false;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("Libere pelo menos 1 espaço no inventário para pegar o Coração do Doom!", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        removeAllDoomArtifactsFromPlayer(player);
        player.getInventory().addItem(createDoomArtifactItem());
        this.carrierId = player.getUniqueId();
        this.droppedArtifactEntityId = null;

        applyCarrierGlowing(player);
        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("   🔥 O CORAÇÃO DO DOOM FOI ROUBADO DO BAÚ! 🔥", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  Portador: ", NamedTextColor.GRAY)
                .append(team.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("  Objetivo: ", NamedTextColor.GRAY)
                .append(Component.text("SAIR PELA ENTRADA DA FORTALEZA DOOM", NamedTextColor.YELLOW, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text("  ⚔ Matem o portador antes que ele saia para roubar o Coração!", NamedTextColor.RED));
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.RED, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        Title pickupTitle = Title.title(
                Component.text("🔥 CORAÇÃO CAPTURADO! 🔥", NamedTextColor.RED, TextDecoration.BOLD),
                team.getFormattedTag()
                        .append(Component.text(" " + player.getName() + " está fugindo para a saída!", NamedTextColor.YELLOW)),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(600))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(pickupTitle);
            online.playSound(online.getLocation(), Sound.ENTITY_WITHER_AMBIENT, SoundCategory.MASTER, 1.0f, 0.8f);
        }
        return true;
    }

    public void pickupDroppedArtifact(Player player, Team team, Item itemEntity) {
        if (!active || artifactClaimed || player == null || team == null) {
            return;
        }
        if (itemEntity != null && itemEntity.isValid()) {
            itemEntity.remove();
        }
        removeAllDoomArtifactsFromPlayer(player);
        player.getInventory().addItem(createDoomArtifactItem());
        this.carrierId = player.getUniqueId();
        this.droppedArtifactEntityId = null;

        applyCarrierGlowing(player);
        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();

        Bukkit.broadcast(Component.text("🔥 ", NamedTextColor.RED)
                .append(team.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" pegou o Coração do Doom do chão e está correndo para a saída!", NamedTextColor.GOLD)));
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.MASTER, 1.0f, 1.0f);
        }
    }

    public void handleCarrierLostArtifact(Player formerCarrier, Player killer, Location dropLocation) {
        if (!active || artifactClaimed) {
            return;
        }
        if (formerCarrier != null) {
            removeAllDoomArtifactsFromPlayer(formerCarrier);
        }

        // Se foi morto por outro jogador que pertence a uma equipe e tem espaco no inventario, transfere direto!
        if (killer != null && !killer.equals(formerCarrier) && killer.isOnline() && !killer.isDead()) {
            Team killerTeam = teamManager.getPlayerTeam(killer.getUniqueId());
            if (killerTeam != null && killer.getInventory().firstEmpty() != -1) {
                removeAllDoomArtifactsFromPlayer(killer);
                killer.getInventory().addItem(createDoomArtifactItem());
                this.carrierId = killer.getUniqueId();
                this.droppedArtifactEntityId = null;

                applyCarrierGlowing(killer);
                spawnOrUpdateHolograms();
                updateBossBar();
                updateEventCompasses();

                Bukkit.broadcast(Component.empty());
                Bukkit.broadcast(Component.text("⚔ ROUBO NA FORTALEZA DOOM! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                        .append(killerTeam.getFormattedTag())
                        .append(Component.text(" " + killer.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                        .append(Component.text(" eliminou o portador e ROUBOU o Coração do Doom!", NamedTextColor.GOLD, TextDecoration.BOLD)));
                Bukkit.broadcast(Component.empty());

                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, SoundCategory.MASTER, 1.0f, 0.8f);
                }
                return;
            }
        }

        this.carrierId = null;
        Location safeDrop = (dropLocation != null && dropLocation.getWorld() != null) ? dropLocation.clone() : chestLocation;
        if (safeDrop != null && safeDrop.getWorld() != null) {
            Item dropped = safeDrop.getWorld().dropItem(safeDrop.clone().add(0, 0.5, 0), createDoomArtifactItem(), item -> {
                item.setInvulnerable(true);
                item.setUnlimitedLifetime(true);
                item.setGlowing(true);
                item.setPickupDelay(15);
                item.customName(Component.text("🔥 CORAÇÃO DO DOOM (+200 PTS)", NamedTextColor.GOLD, TextDecoration.BOLD));
                item.setCustomNameVisible(true);
            });
            this.droppedArtifactEntityId = dropped.getUniqueId();
            Bukkit.broadcast(Component.text("⚠️ O Portador caiu! O ", NamedTextColor.RED)
                    .append(Component.text("Coração do Doom", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" está no chão em X: " + safeDrop.getBlockX()
                            + ", Y: " + safeDrop.getBlockY()
                            + ", Z: " + safeDrop.getBlockZ() + "!", NamedTextColor.YELLOW)));
        }

        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();
    }

    public void checkCarrierEscaped(Player player) {
        if (!active || artifactClaimed || player == null || !isCarrier(player)) {
            return;
        }
        if (player.isDead() || !player.isOnline()) {
            return;
        }
        // Quando o portador sai do casco fechado da Fortaleza (cruza a Entrada Unica para dz < 0)
        if (!isInsideSealedFortress(player.getLocation())) {
            Team team = teamManager.getPlayerTeam(player.getUniqueId());
            if (team == null) {
                handleCarrierLostArtifact(player, null, entranceLocation);
                return;
            }
            completeEscapeVictory(player, team);
        }
    }

    public void completeEscapeVictory(Player player, Team team) {
        if (!active || artifactClaimed || player == null || team == null) {
            return;
        }

        this.artifactClaimed = true;
        this.carrierId = null;
        this.droppedArtifactEntityId = null;
        this.collapseSecondsRemaining = COLLAPSE_COUNTDOWN_SECONDS;

        removeAllDoomArtifactsFromPlayer(player);
        teamManager.awardTeamPoints(team, DOOM_REWARD_POINTS, "Escapou da Fortaleza DOOM com o Coração (" + player.getName() + ")");
        int newTotal = team.getPoints();

        spawnVictoryFirework(player.getLocation());
        spawnOrUpdateHolograms();
        updateBossBar();

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("   🏆 FUGA COMPLETA! FORTALEZA DOOM VENCIDA! 🏆", NamedTextColor.GREEN, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  Conquistador: ", NamedTextColor.GRAY)
                .append(team.getFormattedTag())
                .append(Component.text(" " + player.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" saiu vivo da Fortaleza com o Coração!", NamedTextColor.YELLOW)));
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
                        .append(Component.text(" " + player.getName() + " escapou com o Coração (+" + DOOM_REWARD_POINTS + " pts)!", NamedTextColor.GREEN)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(800))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(winTitle);
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 1.0f, 1.0f);
            online.playSound(online.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.MASTER, 0.8f, 0.9f);
        }
    }

    public void removeAllDoomArtifactsFromPlayer(Player player) {
        if (player == null) {
            return;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isDoomArtifact(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isDoomArtifact(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void applyCarrierGlowing(Player carrier) {
        if (carrier == null || !carrier.isOnline()) {
            return;
        }
        teamManager.updatePlayerDisplay(carrier);
        carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));
    }

    private void tick() {
        if (!active || dungeonWorld == null) {
            return;
        }

        tickCounter++;
        updateGateProximityAndCountdown();
        verifyCarrierAndDroppedArtifactState();
        spawnOrUpdateHolograms();
        updateBossBar();
        updateEventCompasses();

        if (entranceLocation != null) {
            dungeonWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, entranceLocation.clone().add(0, 1.5, 2.0), 15, 1.6, 1.2, 0.4, 0.02);
        }
        if (chestLocation != null && !artifactClaimed) {
            dungeonWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, chestLocation.clone().add(0.5, 0.8, 0.5), 14, 0.4, 0.5, 0.4, 0.02);
            dungeonWorld.spawnParticle(Particle.TOTEM_OF_UNDYING, chestLocation.clone().add(0.5, 1.1, 0.5), 8, 0.35, 0.4, 0.35, 0.05);
        }

        if (tickCounter % 6 == 0 && !artifactClaimed) {
            pulseNearbySpawners();
        }
        if (tickCounter % 10 == 0) {
            saveDynamicState();
        }

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

    public boolean isGateOpened() {
        return gateOpened;
    }

    private void updateGateProximityAndCountdown() {
        if (!active || gateOpened || dungeonWorld == null || entranceLocation == null) {
            return;
        }

        if (!gateCountdownStarted) {
            double radiusSq = GATE_TRIGGER_RADIUS * GATE_TRIGGER_RADIUS;
            for (Player player : dungeonWorld.getPlayers()) {
                if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (player.getLocation().distanceSquared(entranceLocation) <= radiusSq) {
                    triggerGateCountdown(player);
                    break;
                }
            }
            return;
        }

        gateSecondsRemaining--;
        if (gateSecondsRemaining == 120 || gateSecondsRemaining == 60 || gateSecondsRemaining == 30
                || gateSecondsRemaining == 10 || (gateSecondsRemaining <= 5 && gateSecondsRemaining > 0)) {
            Bukkit.broadcast(Component.text("⏳ O Portão Principal da Fortaleza DOOM abrirá em ", NamedTextColor.GOLD)
                    .append(Component.text(formatSeconds(gateSecondsRemaining), NamedTextColor.YELLOW, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GOLD)));
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 1.0f, 1.2f);
            }
        }

        if (gateSecondsRemaining <= 0) {
            openFrontGate();
        }
    }

    private void triggerGateCountdown(Player triggerPlayer) {
        this.gateCountdownStarted = true;
        this.gateSecondsRemaining = GATE_UNLOCK_SECONDS;

        Team team = teamManager.getPlayerTeam(triggerPlayer.getUniqueId());
        Component tagComp = (team != null) ? team.getFormattedTag().append(Component.text(" ")) : Component.empty();

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("   🔒 CONTAGEM DO PORTÃO DOOM INICIADA! 🔒", NamedTextColor.YELLOW, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  ", NamedTextColor.GRAY)
                .append(tagComp)
                .append(Component.text(triggerPlayer.getName(), NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" aproximou-se da Entrada Única!", NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.text("  ⏳ O Portão da Frente abrirá em ", NamedTextColor.GRAY)
                .append(Component.text("3 MINUTOS (03:00)", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        Title countdownTitle = Title.title(
                Component.text("🔒 PORTÃO DOOM: 03:00", NamedTextColor.GOLD, TextDecoration.BOLD),
                tagComp.append(Component.text(triggerPlayer.getName() + " ativou a abertura da Fortaleza!", NamedTextColor.YELLOW)),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(countdownTitle);
            online.playSound(online.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.MASTER, 1.0f, 0.9f);
        }
    }

    public void openFrontGate() {
        if (!active || gateOpened || dungeonWorld == null) {
            return;
        }
        this.gateOpened = true;
        this.gateCountdownStarted = false;
        this.gateSecondsRemaining = 0;

        // Abre o Portao Principal (dz = 0..1, dx = -2..+2, dy = 1..5)
        for (int dz = 0; dz <= 1; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 1; dy <= 5; dy++) {
                    if (Math.abs(dx) == 2 && dy == 5) {
                        setDungeonBlock(dx, dy, dz, Material.CRYING_OBSIDIAN);
                    } else {
                        setDungeonBlock(dx, dy, dz, Material.AIR);
                    }
                }
            }
        }

        if (entranceLocation != null) {
            Location gateCenter = new Location(dungeonWorld, originX + 0.5, baseY + 3.0, originZ + 0.5);
            dungeonWorld.spawnParticle(Particle.EXPLOSION_EMITTER, gateCenter, 2, 0.5, 0.5, 0.5, 0.0);
            dungeonWorld.spawnParticle(Particle.SOUL_FIRE_FLAME, gateCenter, 80, 1.8, 1.8, 0.6, 0.08);
        }

        spawnOrUpdateHolograms();
        updateBossBar();

        Bukkit.broadcast(Component.empty());
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GREEN, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("   🔓 O PORTÃO DA FORTALEZA DOOM ABRIU! 🔓", NamedTextColor.GOLD, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("  A Entrada Única está aberta! Invadam os 10 Salões,", NamedTextColor.YELLOW));
        Bukkit.broadcast(Component.text("  peguem o Coração do Doom (+" + DOOM_REWARD_POINTS + " pts) e saiam vivos!", NamedTextColor.GREEN, TextDecoration.BOLD));
        Bukkit.broadcast(Component.text("==============================================", NamedTextColor.GREEN, TextDecoration.BOLD));
        Bukkit.broadcast(Component.empty());

        Title openTitle = Title.title(
                Component.text("🔓 PORTÃO DOOM ABERTO!", NamedTextColor.GREEN, TextDecoration.BOLD),
                Component.text("Invadam a Fortaleza e tragam o Coração (+" + DOOM_REWARD_POINTS + " pts)!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(600))
        );
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.showTitle(openTitle);
            online.playSound(online.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.MASTER, 1.0f, 0.8f);
            online.playSound(online.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, SoundCategory.MASTER, 0.8f, 1.0f);
        }
    }

    private String formatSeconds(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void verifyCarrierAndDroppedArtifactState() {
        if (!active || artifactClaimed) {
            return;
        }
        if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier == null || !carrier.isOnline() || carrier.isDead()) {
                handleCarrierLostArtifact(carrier, null, carrier != null ? carrier.getLocation() : chestLocation);
                return;
            }
            applyCarrierGlowing(carrier);
            checkCarrierEscaped(carrier);
        } else if (droppedArtifactEntityId != null) {
            Entity dropped = Bukkit.getEntity(droppedArtifactEntityId);
            if (dropped == null || !dropped.isValid() || dropped.isDead()) {
                // Caso a entidade do item caido suma por algum motivo externo, devolve ao Bau Final
                droppedArtifactEntityId = null;
                if (chestLocation != null && chestLocation.getBlock().getState() instanceof Chest chest) {
                    chest.getBlockInventory().setItem(13, createDoomArtifactItem());
                    Bukkit.broadcast(Component.text("🔥 O Coração do Doom retornou ao Baú Final na 10ª Sala!", NamedTextColor.GOLD, TextDecoration.BOLD));
                }
            }
        }
    }

    private void pulseNearbySpawners() {
        if (dungeonWorld == null) {
            return;
        }
        spawnedMobIds.removeIf(uuid -> {
            Entity e = Bukkit.getEntity(uuid);
            return e == null || !e.isValid() || e.isDead();
        });
        if (spawnedMobIds.size() >= 90) {
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

        // Salva os jogadores que estao dentro antes de desativar a flag active
        List<Player> evacuatedPlayers = new ArrayList<>();
        if (dungeonWorld != null) {
            for (Player p : dungeonWorld.getPlayers()) {
                if (isProtectedLocation(p.getLocation())) {
                    evacuatedPlayers.add(p);
                }
            }
        }

        // Remove 100% dos monstros da dungeon (incluindo os gerados pelos spawners vanilla)
        removeDungeonMobs();

        if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier != null) {
                removeAllDoomArtifactsFromPlayer(carrier);
            }
        }
        if (droppedArtifactEntityId != null) {
            Entity dropped = Bukkit.getEntity(droppedArtifactEntityId);
            if (dropped != null) {
                dropped.remove();
            }
        }

        this.active = false;
        this.generating = false;
        this.gateOpened = false;
        this.gateCountdownStarted = false;
        this.gateSecondsRemaining = GATE_UNLOCK_SECONDS;
        this.artifactClaimed = false;
        this.carrierId = null;
        this.droppedArtifactEntityId = null;
        this.collapseSecondsRemaining = 0;

        if (compassManager != null) {
            compassManager.removeCompassFromAllPlayers(CompassEventType.DOOM);
        }
        clearBossBar();
        removeHolograms();

        if (chestLocation != null && chestLocation.getBlock().getState() instanceof Chest chest) {
            chest.getBlockInventory().clear();
        }

        restoreOriginalBlocks();
        this.playerPlacedBlocks.clear();
        this.extraPlayerOriginalBlocks.clear();
        deleteSavedState();

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
            Bukkit.broadcast(Component.text("💀 A Fortaleza DOOM desapareceu nas sombras, todos os monstros sumiram e o terreno foi restaurado.", NamedTextColor.RED));
        }
    }

    void setDungeonBlock(int dx, int dy, int dz, Material material) {
        setDungeonBlockData(dx, dy, dz, Bukkit.createBlockData(material));
    }

    private void setStairBlock(int dx, int dy, int dz, Material stairMaterial, BlockFace facing) {
        Stairs stairs = (Stairs) Bukkit.createBlockData(stairMaterial);
        stairs.setFacing(facing);
        setDungeonBlockData(dx, dy, dz, stairs);
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

    /**
     * Remove todos os monstros da Fortaleza DOOM:
     * 1) Todos os UUIDs registrados em spawnedMobIds
     * 2) Todas as entidades com a tag doomMobKey
     * 3) Qualquer Mob/Monster que esteja dentro ou ao redor da caixa delimitadora da estrutura!
     */
    private void removeDungeonMobs() {
        for (UUID id : spawnedMobIds) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
        spawnedMobIds.clear();

        if (dungeonWorld != null) {
            int minX = originX - HALF_WIDTH - 15;
            int maxX = originX + HALF_WIDTH + 15;
            int minY = baseY + MIN_DY - 10;
            int maxY = baseY + MAX_DY + 10;
            int minZ = originZ + MIN_DZ - 15;
            int maxZ = originZ + MAX_DZ + 15;

            for (LivingEntity entity : dungeonWorld.getEntitiesByClass(LivingEntity.class)) {
                if (entity instanceof Player) {
                    continue;
                }
                if (entity.getPersistentDataContainer().has(doomMobKey, PersistentDataType.BYTE)) {
                    entity.remove();
                    continue;
                }
                Location loc = entity.getLocation();
                int x = loc.getBlockX();
                int y = loc.getBlockY();
                int z = loc.getBlockZ();
                if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                    if (entity instanceof Monster || entity instanceof Mob) {
                        entity.remove();
                    }
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
            Component statusLine;
            if (!gateOpened) {
                if (gateCountdownStarted) {
                    statusLine = Component.text("🔒 Portão Abrindo em: " + formatSeconds(gateSecondsRemaining), NamedTextColor.RED, TextDecoration.BOLD);
                } else {
                    statusLine = Component.text("🔒 Portão Fechado — Aproxime-se para iniciar (3 min)", NamedTextColor.YELLOW, TextDecoration.BOLD);
                }
            } else {
                statusLine = Component.text("🔓 Portão Aberto — Pegue o Coração (+" + DOOM_REWARD_POINTS + " pts) e Saia!", NamedTextColor.GREEN, TextDecoration.BOLD);
            }
            Component text = Component.text("💀 " + currentVariant.getTitle().toUpperCase() + " 💀\n", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text(currentVariant.getSubtitle() + " — 10 Salões\n", NamedTextColor.GOLD))
                    .append(statusLine);
            entranceHologramId = updateSingleHologram(entranceHologramId, holoLoc, text);
        }
        if (chestLocation != null) {
            Location holoLoc = chestLocation.clone().add(0.5, 1.5, 0.5);
            Component text;
            if (artifactClaimed) {
                text = Component.text("💀 BAÚ DO DOOM CONQUISTADO 💀\n", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text("Colapso em " + collapseSecondsRemaining + "s!", NamedTextColor.YELLOW, TextDecoration.BOLD));
            } else if (carrierId != null) {
                Player carrier = Bukkit.getPlayer(carrierId);
                String carrierName = carrier != null ? carrier.getName() : "Portador";
                text = Component.text("⚠️ CORAÇÃO EM FUGA! ⚠️\n", NamedTextColor.RED, TextDecoration.BOLD)
                        .append(Component.text(carrierName + " está tentando sair da Fortaleza!", NamedTextColor.YELLOW, TextDecoration.BOLD));
            } else if (droppedArtifactEntityId != null) {
                text = Component.text("⚠️ CORAÇÃO CAÍDO NO CHÃO! ⚠️\n", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Encontre-o na Fortaleza e saia pela entrada!", NamedTextColor.YELLOW));
            } else {
                text = Component.text("🔥 BAÚ FINAL DO DOOM 🔥\n", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("Pegue o Coração e SAIA da Fortaleza (+" + DOOM_REWARD_POINTS + " PTS)", NamedTextColor.GREEN, TextDecoration.BOLD));
            }
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
        } else if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            Team team = carrier != null ? teamManager.getPlayerTeam(carrier.getUniqueId()) : null;
            int distExit = (carrier != null && entranceLocation != null && carrier.getWorld().equals(entranceLocation.getWorld()))
                    ? (int) Math.round(carrier.getLocation().distance(entranceLocation))
                    : 0;
            Component carrierTag = (team != null) ? team.getFormattedTag().append(Component.text(" ")) : Component.empty();
            String carrierName = (carrier != null) ? carrier.getName() : "Portador";

            title = Component.text("🔥 PORTADOR DO DOOM: ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(carrierTag)
                    .append(Component.text(carrierName, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" | Saída a ", NamedTextColor.GRAY))
                    .append(Component.text(distExit + "m", NamedTextColor.YELLOW, TextDecoration.BOLD));
            progress = Math.max(0.05f, Math.min(1.0f, (float) distExit / 245.0f));
            barColor = BossBar.Color.YELLOW;
        } else if (!gateOpened && gateCountdownStarted) {
            title = Component.text("🔒 PORTÃO DOOM ABRINDO EM " + formatSeconds(gateSecondsRemaining) + " ", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.text("| Entrada: ", NamedTextColor.GRAY))
                    .append(Component.text("X: " + entranceLocation.getBlockX()
                            + " Y: " + entranceLocation.getBlockY()
                            + " Z: " + entranceLocation.getBlockZ(), NamedTextColor.WHITE, TextDecoration.BOLD));
            progress = Math.max(0.0f, Math.min(1.0f, (float) gateSecondsRemaining / GATE_UNLOCK_SECONDS));
            barColor = BossBar.Color.YELLOW;
        } else if (!gateOpened) {
            title = Component.text("🔒 FORTALEZA DOOM (PORTÃO FECHADO) ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("| Entrada: ", NamedTextColor.GRAY))
                    .append(Component.text("X: " + entranceLocation.getBlockX()
                            + " Y: " + entranceLocation.getBlockY()
                            + " Z: " + entranceLocation.getBlockZ(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" | Aproxime-se!", NamedTextColor.YELLOW, TextDecoration.BOLD));
            progress = 1.0f;
            barColor = BossBar.Color.PURPLE;
        } else {
            title = Component.text("💀 FORTALEZA DOOM (10 SALÕES) ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text("| Entrada: ", NamedTextColor.GRAY))
                    .append(Component.text("X: " + entranceLocation.getBlockX()
                            + " Y: " + entranceLocation.getBlockY()
                            + " Z: " + entranceLocation.getBlockZ(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(" | Baú: ", NamedTextColor.GRAY))
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
        if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier != null && carrier.isOnline()) {
                Location target = carrier.getLocation();
                String label = "Portador DOOM: " + carrier.getName() + " (X: " + target.getBlockX()
                        + ", Y: " + target.getBlockY()
                        + ", Z: " + target.getBlockZ() + ")";
                compassManager.giveOrUpdateAllPlayers(CompassEventType.DOOM, target, label);
                return;
            }
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

    private File getBlocksArchiveFile() {
        return new File(plugin.getDataFolder(), "doom_blocks.dat.gz");
    }

    private File getDynamicStateFile() {
        return new File(plugin.getDataFolder(), "doom_state.yml");
    }

    private void saveBlocksArchive() {
        if (!active || dungeonWorld == null || originalBlocks.isEmpty()) {
            return;
        }
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File file = getBlocksArchiveFile();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(file))))) {
            out.writeUTF(dungeonWorld.getName());
            out.writeInt(originX);
            out.writeInt(baseY);
            out.writeInt(originZ);
            out.writeUTF(currentVariant.name());

            Map<String, Integer> paletteIndex = new HashMap<>();
            List<String> palette = new ArrayList<>();
            for (BlockData data : originalBlocks.values()) {
                String str = data.getAsString();
                if (!paletteIndex.containsKey(str)) {
                    paletteIndex.put(str, palette.size());
                    palette.add(str);
                }
            }

            out.writeInt(palette.size());
            for (String entry : palette) {
                out.writeUTF(entry);
            }

            out.writeInt(originalBlocks.size());
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
                Location loc = entry.getKey();
                out.writeInt(loc.getBlockX());
                out.writeInt(loc.getBlockY());
                out.writeInt(loc.getBlockZ());
                out.writeInt(paletteIndex.get(entry.getValue().getAsString()));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[DOOM] Falha ao salvar arquivo de blocos originais: " + e.getMessage());
        }
    }

    private void saveDynamicState() {
        if (!active || dungeonWorld == null) {
            return;
        }
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("world", dungeonWorld.getName());
        cfg.set("originX", originX);
        cfg.set("baseY", baseY);
        cfg.set("originZ", originZ);
        cfg.set("variant", currentVariant.name());
        cfg.set("gateOpened", gateOpened);
        cfg.set("gateCountdownStarted", gateCountdownStarted);
        cfg.set("gateSecondsRemaining", gateSecondsRemaining);
        cfg.set("artifactClaimed", artifactClaimed);
        cfg.set("collapseSecondsRemaining", collapseSecondsRemaining);
        cfg.set("entranceHologramId", entranceHologramId != null ? entranceHologramId.toString() : null);
        cfg.set("chestHologramId", chestHologramId != null ? chestHologramId.toString() : null);
        cfg.set("droppedArtifactEntityId", droppedArtifactEntityId != null ? droppedArtifactEntityId.toString() : null);

        List<String> placedList = new ArrayList<>(playerPlacedBlocks.size());
        for (Location loc : playerPlacedBlocks) {
            placedList.add(loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ());
        }
        cfg.set("playerPlacedBlocks", placedList);

        List<String> extraList = new ArrayList<>(extraPlayerOriginalBlocks.size());
        for (Map.Entry<Location, BlockData> entry : extraPlayerOriginalBlocks.entrySet()) {
            Location loc = entry.getKey();
            extraList.add(loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ() + ";" + entry.getValue().getAsString());
        }
        cfg.set("extraPlayerOriginalBlocks", extraList);

        try {
            cfg.save(getDynamicStateFile());
        } catch (IOException e) {
            plugin.getLogger().warning("[DOOM] Falha ao salvar estado da Fortaleza DOOM: " + e.getMessage());
        }
    }

    private void loadDoomState() {
        File archive = getBlocksArchiveFile();
        File stateFile = getDynamicStateFile();
        if (!archive.exists() || !stateFile.exists()) {
            return;
        }

        try {
            String worldName;
            int loadedOriginX;
            int loadedBaseY;
            int loadedOriginZ;
            DoomVariant loadedVariant = DoomVariant.DOOM_1;

            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(archive))))) {
                worldName = in.readUTF();
                loadedOriginX = in.readInt();
                loadedBaseY = in.readInt();
                loadedOriginZ = in.readInt();
                try {
                    loadedVariant = DoomVariant.valueOf(in.readUTF());
                } catch (IllegalArgumentException ignored) {
                }

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    deleteSavedState();
                    return;
                }

                int paletteSize = in.readInt();
                List<BlockData> palette = new ArrayList<>(paletteSize);
                for (int i = 0; i < paletteSize; i++) {
                    palette.add(Bukkit.createBlockData(in.readUTF()));
                }

                int blockCount = in.readInt();
                originalBlocks.clear();
                for (int i = 0; i < blockCount; i++) {
                    int x = in.readInt();
                    int y = in.readInt();
                    int z = in.readInt();
                    int palIdx = in.readInt();
                    originalBlocks.put(new Location(world, x, y, z), palette.get(palIdx));
                }

                this.dungeonWorld = world;
                this.originX = loadedOriginX;
                this.baseY = loadedBaseY;
                this.originZ = loadedOriginZ;
                this.currentVariant = loadedVariant;
            }

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(stateFile);
            this.gateOpened = cfg.getBoolean("gateOpened", false);
            this.gateCountdownStarted = cfg.getBoolean("gateCountdownStarted", false);
            this.gateSecondsRemaining = cfg.getInt("gateSecondsRemaining", GATE_UNLOCK_SECONDS);
            this.artifactClaimed = cfg.getBoolean("artifactClaimed", false);
            this.collapseSecondsRemaining = cfg.getInt("collapseSecondsRemaining", 0);

            String entHolo = cfg.getString("entranceHologramId");
            this.entranceHologramId = (entHolo != null && !entHolo.isBlank()) ? UUID.fromString(entHolo) : null;
            String chHolo = cfg.getString("chestHologramId");
            this.chestHologramId = (chHolo != null && !chHolo.isBlank()) ? UUID.fromString(chHolo) : null;
            String droppedId = cfg.getString("droppedArtifactEntityId");
            this.droppedArtifactEntityId = (droppedId != null && !droppedId.isBlank()) ? UUID.fromString(droppedId) : null;

            playerPlacedBlocks.clear();
            for (String s : cfg.getStringList("playerPlacedBlocks")) {
                String[] parts = s.split(";");
                if (parts.length == 3) {
                    playerPlacedBlocks.add(new Location(
                            dungeonWorld,
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                    ));
                }
            }

            extraPlayerOriginalBlocks.clear();
            for (String s : cfg.getStringList("extraPlayerOriginalBlocks")) {
                String[] parts = s.split(";", 4);
                if (parts.length == 4) {
                    Location loc = new Location(
                            dungeonWorld,
                            Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])
                    );
                    BlockData data = Bukkit.createBlockData(parts[3]);
                    extraPlayerOriginalBlocks.put(loc, data);
                    if (!originalBlocks.containsKey(loc)) {
                        originalBlocks.put(loc, data);
                    }
                }
            }

            this.entranceLocation = new Location(dungeonWorld, originX + 0.5, baseY + 1, originZ - 2.5);
            this.chestLocation = new Location(dungeonWorld, originX, baseY + 8, originZ + 238);
            prepareSpawnerList();

            int minChunkX = (originX - HALF_WIDTH - 2) >> 4;
            int maxChunkX = (originX + HALF_WIDTH + 2) >> 4;
            int minChunkZ = (originZ + MIN_DZ - 3) >> 4;
            int maxChunkZ = (originZ + MAX_DZ + 2) >> 4;
            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    Chunk chunk = dungeonWorld.getChunkAt(cx, cz);
                    chunk.addPluginChunkTicket(plugin);
                    ticketedChunks.add(chunkKey(cx, cz));
                }
            }

            this.generating = false;
            this.active = true;
            plugin.getLogger().info("[DOOM] " + currentVariant.getFullTitle()
                    + " restaurada da sessao anterior em X=" + originX + ", Y=" + baseY + ", Z=" + originZ
                    + " (" + originalBlocks.size() + " blocos rastreados).");
        } catch (Exception e) {
            plugin.getLogger().warning("[DOOM] Falha ao carregar Fortaleza DOOM salva: " + e.getMessage());
            originalBlocks.clear();
            playerPlacedBlocks.clear();
            extraPlayerOriginalBlocks.clear();
            deleteSavedState();
        }
    }

    private void deleteSavedState() {
        File archive = getBlocksArchiveFile();
        if (archive.exists()) {
            archive.delete();
        }
        File stateFile = getDynamicStateFile();
        if (stateFile.exists()) {
            stateFile.delete();
        }
    }
}
