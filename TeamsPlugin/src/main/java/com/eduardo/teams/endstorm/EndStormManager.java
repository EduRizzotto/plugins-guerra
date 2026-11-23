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
import org.bukkit.Color;
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
 * - Abre uma Fenda do End no Overworld por 10 minutos com Cristal da Fenda no centro (500 HP).
 * - Spawna ondas de Guardioes da Fenda (Endermen, Shulkers e Endermites customizados) que
 *   dropam Ender Pearls, Shulker Shells e concedem pontos imediatos por abate:
 *     * Enderman da Fenda: +3 pts
 *     * Sentinela Shulker: +5 pts
 *     * Parasita do Vazio: +2 pts
 * - Destruir o Cristal da Fenda no centro concede +70 pts para o time que der o golpe final
 *   e dropa tesouros do Vazio.
 * - Restaura 100% do terreno original ao encerrar.
 */
public class EndStormManager {

    public static final int EVENT_DURATION_SECONDS = 10 * 60; // 10 minutos
    public static final int CRYSTAL_MAX_HP = 500;
    public static final int POINTS_DESTROY_CRYSTAL = 70;
    public static final int POINTS_KILL_ENDERMAN = 3;
    public static final int POINTS_KILL_SHULKER = 5;
    public static final int POINTS_KILL_ENDERMITE = 2;

    public static final int RIFT_RADIUS = 7;
    public static final int MIN_SPAWN_RADIUS = 150;
    public static final int MAX_SPAWN_RADIUS = 700;
    private static final int MAX_ACTIVE_GUARDIANS = 14;
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
            Location candidate = highest.getLocation().add(0, 1, 0);
            startStormAt(candidate);
            return candidate;
        }

        int fallbackX = spawn.getBlockX() + 220;
        int fallbackZ = spawn.getBlockZ() + 220;
        Block highest = world.getHighestBlockAt(fallbackX, fallbackZ);
        Location fallback = highest.getLocation().add(0, 1, 0);
        startStormAt(fallback);
        return fallback;
    }

    public void startStormAt(Location centerLocation) {
        if (active) {
            stopStorm(false);
        }

        this.baseCenter = centerLocation.toBlockLocation();
        this.crystalLocation = this.baseCenter.clone().add(0.5, 3.0, 0.5);
        this.active = true;
        this.secondsRemaining = EVENT_DURATION_SECONDS;
        this.crystalHp = CRYSTAL_MAX_HP;
        this.lastPulseThreshold = CRYSTAL_MAX_HP;
        this.pointsEarnedInStormByTeam.clear();
        this.lastCrystalHitByPlayer.clear();

        buildRiftArena();
        spawnRiftCrystalAndHologram();
        spawnGuardianWave(6);

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

    private void buildRiftArena() {
        originalBlocks.clear();
        protectedRegionBlocks.clear();

        World world = baseCenter.getWorld();
        if (world == null) {
            return;
        }

        int cx = baseCenter.getBlockX();
        int cy = baseCenter.getBlockY();
        int cz = baseCenter.getBlockZ();

        // Salva todos os blocos na regiao antes de modificar
        for (int dx = -RIFT_RADIUS - 1; dx <= RIFT_RADIUS + 1; dx++) {
            for (int dz = -RIFT_RADIUS - 1; dz <= RIFT_RADIUS + 1; dz++) {
                for (int dy = -2; dy <= 10; dy++) {
                    Location loc = new Location(world, cx + dx, cy + dy, cz + dz);
                    protectedRegionBlocks.add(loc);
                    originalBlocks.put(loc, loc.getBlock().getBlockData().clone());
                }
            }
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Limpa espaco acima e constroi cratera corrompida do End
        for (int dx = -RIFT_RADIUS; dx <= RIFT_RADIUS; dx++) {
            for (int dz = -RIFT_RADIUS; dz <= RIFT_RADIUS; dz++) {
                double distSq = dx * dx + dz * dz;
                if (distSq > (RIFT_RADIUS + 0.5) * (RIFT_RADIUS + 0.5)) {
                    continue;
                }

                for (int dy = 0; dy <= 8; dy++) {
                    setBlockFast(world, cx + dx, cy + dy, cz + dz, Material.AIR);
                }

                // Subsolo luminoso para fendas roxas
                setBlockFast(world, cx + dx, cy - 2, cz + dz, Material.OBSIDIAN);

                boolean isFissure = (Math.abs(dx) == Math.abs(dz) && Math.abs(dx) >= 2 && Math.abs(dx) <= 5)
                        || ((dx == 0 || dz == 0) && Math.abs(dx + dz) >= 2 && Math.abs(dx + dz) <= 5);

                if (isFissure) {
                    setBlockFast(world, cx + dx, cy - 2, cz + dz, Material.SEA_LANTERN);
                    setBlockFast(world, cx + dx, cy - 1, cz + dz,
                            rng.nextBoolean() ? Material.PURPLE_STAINED_GLASS : Material.MAGENTA_STAINED_GLASS);
                } else {
                    Material floorMat;
                    double r = rng.nextDouble();
                    if (r < 0.38) {
                        floorMat = Material.END_STONE;
                    } else if (r < 0.65) {
                        floorMat = Material.END_STONE_BRICKS;
                    } else if (r < 0.85) {
                        floorMat = Material.OBSIDIAN;
                    } else {
                        floorMat = Material.CRYING_OBSIDIAN;
                    }
                    setBlockFast(world, cx + dx, cy - 1, cz + dz, floorMat);
                }
            }
        }

        // Pedestal central do Cristal da Fenda
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                setBlockFast(world, cx + dx, cy, cz + dz, Material.PURPUR_BLOCK);
            }
        }
        setBlockFast(world, cx, cy, cz, Material.CRYING_OBSIDIAN);
        setBlockFast(world, cx, cy + 1, cz, Material.PURPUR_PILLAR);
        setBlockFast(world, cx, cy + 2, cz, Material.OBSIDIAN);

        // 4 Pilares corrompidos nas diagonais
        int[][] pillarOffsets = {{-5, -5}, {5, -5}, {-5, 5}, {5, 5}};
        for (int[] off : pillarOffsets) {
            int px = cx + off[0];
            int pz = cz + off[1];
            setBlockFast(world, px, cy, pz, Material.OBSIDIAN);
            setBlockFast(world, px, cy + 1, pz, Material.CRYING_OBSIDIAN);
            setBlockFast(world, px, cy + 2, pz, Material.PURPUR_PILLAR);
            setBlockFast(world, px, cy + 3, pz, Material.OBSIDIAN);
            setBlockFast(world, px, cy + 4, pz, Material.END_ROD);
        }

        // 4 Totens menores nos pontos cardeais
        int[][] cardinalOffsets = {{0, -6}, {0, 6}, {-6, 0}, {6, 0}};
        for (int[] off : cardinalOffsets) {
            int px = cx + off[0];
            int pz = cz + off[1];
            setBlockFast(world, px, cy, pz, Material.END_STONE_BRICKS);
            setBlockFast(world, px, cy + 1, pz, Material.CRYING_OBSIDIAN);
            setBlockFast(world, px, cy + 2, pz, Material.END_ROD);
        }
    }

    private void setBlockFast(World world, int x, int y, int z, Material material) {
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
            ec.setBeamTarget(crystalLocation.clone().add(0, 25, 0));
            ec.setPersistent(false);
            ec.getPersistentDataContainer().set(riftCrystalKey, PersistentDataType.BYTE, (byte) 1);
        });
        this.crystalEntityId = crystal.getUniqueId();

        Location holoLoc = crystalLocation.clone().add(0, 2.35, 0);
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
                .append(Component.text("Abater Guardiões: ", NamedTextColor.YELLOW))
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
            // Garante que o Cristal da Fenda continue vivo enquanto crystalHp > 0
            Entity crystal = crystalEntityId != null ? Bukkit.getEntity(crystalEntityId) : null;
            if ((crystal == null || !crystal.isValid()) && crystalHp > 0) {
                spawnRiftCrystalAndHologram();
            }

            // Partículas de tempestade roxa na Fenda
            world.spawnParticle(Particle.PORTAL, crystalLocation.clone().add(0, 0.5, 0), 40, 2.5, 1.5, 2.5, 0.3);
            world.spawnParticle(Particle.DRAGON_BREATH, crystalLocation.clone().add(0, 0.3, 0), 12, 1.8, 0.8, 1.8, 0.02);
            world.spawnParticle(Particle.END_ROD, crystalLocation.clone().add(0, 1.2, 0), 8, 3.0, 1.5, 3.0, 0.03);

            // A cada 20 segundos, spawna mais Guardiões da Fenda se houver espaço
            if (secondsRemaining % 20 == 0 && activeGuardians.size() < MAX_ACTIVE_GUARDIANS) {
                int toSpawn = Math.min(4, MAX_ACTIVE_GUARDIANS - activeGuardians.size());
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
            Bukkit.broadcast(Component.text("🌌 A Tempestade do End se dissipou! O Cristal da Fenda resistiu com " + crystalHp + " HP.", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            broadcastStormSummary();
            Bukkit.broadcast(Component.empty());
            stopStorm(true);
        }
    }

    public void spawnGuardianWave(int count) {
        if (!active || baseCenter == null || count <= 0) {
            return;
        }
        World world = baseCenter.getWorld();
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(baseCenter.getBlockX() >> 4, baseCenter.getBlockZ() >> 4)) {
            world.getChunkAt(baseCenter.getBlockX() >> 4, baseCenter.getBlockZ() >> 4).load();
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            if (activeGuardians.size() >= MAX_ACTIVE_GUARDIANS) {
                break;
            }
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(2.5, RIFT_RADIUS - 1.0);
            Location spawnLoc = baseCenter.clone().add(
                    Math.cos(angle) * dist + 0.5,
                    1.0,
                    Math.sin(angle) * dist + 0.5
            );

            double roll = rng.nextDouble();
            LivingEntity mob;
            if (roll < 0.55) {
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
        world.playSound(baseCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.7f);
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

        // A cada 100 HP perdidos, solta um Pulso do Vazio e invoca reforços!
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
            if (p.getLocation().distanceSquared(crystalLocation) <= 100.0) { // raio 10
                Vector push = p.getLocation().toVector().subtract(crystalLocation.toVector());
                if (push.lengthSquared() < 0.01) {
                    push = new Vector(0.5, 0.4, 0.5);
                } else {
                    push.normalize().multiply(0.95).setY(0.45);
                }
                p.setVelocity(push);
            }
        }

        spawnGuardianWave(4);
        Bukkit.broadcast(Component.text("🌌 [TEMPESTADE DO END] ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text("O Cristal da Fenda liberou um Pulso do Vazio e invocou novos Guardiões! (" + crystalHp + "/" + CRYSTAL_MAX_HP + " HP)", NamedTextColor.YELLOW)));
    }

    private void handleCrystalDestroyed(Player destroyer, Team team) {
        World world = crystalLocation.getWorld();
        if (world != null) {
            world.playSound(crystalLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.9f);
            world.playSound(crystalLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.1f);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, crystalLocation, 2, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticle(Particle.DRAGON_BREATH, crystalLocation, 120, 3.0, 1.5, 3.0, 0.1);

            // Dropa tesouros do Vazio no centro da Fenda
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

    /**
     * Chamado quando um Guardiao da Fenda e morto.
     */
    public void handleGuardianKilled(LivingEntity guardian, Player killer, List<ItemStack> drops) {
        activeGuardians.remove(guardian.getUniqueId());
        int pts = getGuardianPoints(guardian);
        if (pts <= 0) {
            return;
        }

        // Adiciona drops garantidos do End
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
            for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
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
        originalBlocks.clear();
        protectedRegionBlocks.clear();
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
                Component.text("Fenda aberta em " + formatCoords(baseCenter) + "!", NamedTextColor.YELLOW),
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
        Bukkit.broadcast(Component.text(" • Coordenadas da Fenda: ", NamedTextColor.GRAY)
                .append(Component.text(formatCoords(baseCenter), NamedTextColor.YELLOW, TextDecoration.BOLD)));
        Bukkit.broadcast(Component.text(" • Duração: ", NamedTextColor.GRAY)
                .append(Component.text("10 minutos", NamedTextColor.WHITE)));
        Bukkit.broadcast(Component.text(" • Guardiões da Fenda: ", NamedTextColor.GRAY)
                .append(Component.text("+2 a +5 pts por abate + Ender Pearls & Shulker Shells", NamedTextColor.AQUA)));
        Bukkit.broadcast(Component.text(" • Destruir o Cristal da Fenda (" + CRYSTAL_MAX_HP + " HP): ", NamedTextColor.GRAY)
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
