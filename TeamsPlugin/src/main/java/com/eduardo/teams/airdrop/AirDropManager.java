package com.eduardo.teams.airdrop;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.horn.TrackerHornManager;
import com.eduardo.teams.util.XaeroWaypointUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia o evento de Supply Drop (AirDrop): spawn a cada 1 hora, contagem regressiva de trava,
 * holograma TextDisplay e tabela de loot com probabilidades (60% Comum, 35% Incomum, 5% Raro).
 */
public class AirDropManager {

    public static final long AUTO_DROP_INTERVAL_TICKS = 60L * 60L * 20L; // 1 hora
    public static final int UNLOCK_DURATION_SECONDS = 120; // 2 minutos trancado
    public static final int DESPAWN_AFTER_UNLOCK_SECONDS = 900; // 15 minutos apos destrancar
    public static final int MIN_RADIUS = 150;
    public static final int MAX_RADIUS = 800;

    public static class ActiveAirDrop {
        private final Location blockLocation;
        private UUID hologramId;
        private int secondsUntilUnlock;
        private int secondsUntilDespawn;
        private boolean unlocked;
        private boolean announcedOpen;

        public ActiveAirDrop(Location blockLocation, UUID hologramId, int secondsUntilUnlock) {
            this.blockLocation = blockLocation;
            this.hologramId = hologramId;
            this.secondsUntilUnlock = secondsUntilUnlock;
            this.secondsUntilDespawn = DESPAWN_AFTER_UNLOCK_SECONDS;
            this.unlocked = false;
            this.announcedOpen = false;
        }

        public Location getBlockLocation() {
            return blockLocation;
        }

        public UUID getHologramId() {
            return hologramId;
        }

        public void setHologramId(UUID hologramId) {
            this.hologramId = hologramId;
        }

        public int getSecondsUntilUnlock() {
            return secondsUntilUnlock;
        }

        public void decrementUnlockTimer() {
            if (secondsUntilUnlock > 0) {
                secondsUntilUnlock--;
            }
        }

        public int getSecondsUntilDespawn() {
            return secondsUntilDespawn;
        }

        public void decrementDespawnTimer() {
            if (secondsUntilDespawn > 0) {
                secondsUntilDespawn--;
            }
        }

        public boolean isUnlocked() {
            return unlocked;
        }

        public void setUnlocked(boolean unlocked) {
            this.unlocked = unlocked;
        }

        public boolean isAnnouncedOpen() {
            return announcedOpen;
        }

        public void setAnnouncedOpen(boolean announcedOpen) {
            this.announcedOpen = announcedOpen;
        }
    }

    private final TeamsPlugin plugin;
    private final TrackerHornManager trackerHornManager;
    private final Map<Location, ActiveAirDrop> activeDrops = new HashMap<>();
    private BukkitTask tickTask;
    private BukkitTask hourlyTask;

    public AirDropManager(TeamsPlugin plugin, TrackerHornManager trackerHornManager) {
        this.plugin = plugin;
        this.trackerHornManager = trackerHornManager;
        startSchedulers();
    }

    private void startSchedulers() {
        // Task de 1 em 1 segundo para atualizar os hologramas, partículas e destrancar caixas
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActiveDrops, 20L, 20L);

        // Task de 1 em 1 hora para cair um AirDrop automatico se houver jogadores online
        this.hourlyTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                return;
            }
            World world = getDefaultOverworld();
            if (world != null) {
                spawnRandomAirDrop(world);
            }
        }, AUTO_DROP_INTERVAL_TICKS, AUTO_DROP_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (hourlyTask != null) {
            hourlyTask.cancel();
            hourlyTask = null;
        }
        clearAllDrops();
    }

    public int clearAllDrops() {
        int count = activeDrops.size();
        for (ActiveAirDrop drop : new ArrayList<>(activeDrops.values())) {
            removeAirDrop(drop, true);
        }
        activeDrops.clear();
        return count;
    }

    private static Location normalizeBlockLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public ActiveAirDrop getAirDropAt(Location blockLoc) {
        Location norm = normalizeBlockLocation(blockLoc);
        if (norm == null) {
            return null;
        }
        return activeDrops.get(norm);
    }

    public CollectionView getActiveDropsCount() {
        return new CollectionView(activeDrops.values());
    }

    public record CollectionView(java.util.Collection<ActiveAirDrop> drops) {}

    private World getDefaultOverworld() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    /**
     * Spawna um AirDrop em uma coordenada aleatoria segura na superficie do mundo.
     */
    public ActiveAirDrop spawnRandomAirDrop(World world) {
        Location center = world.getSpawnLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location chosen = null;

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(MIN_RADIUS, MAX_RADIUS);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Block highest = world.getHighestBlockAt(x, z);
            Material surfaceType = highest.getType();
            if (surfaceType == Material.WATER || surfaceType == Material.LAVA || surfaceType.isAir()) {
                continue;
            }

            Location chestLoc = highest.getLocation().add(0, 1, 0).toBlockLocation();
            chosen = chestLoc;
            break;
        }

        if (chosen == null) {
            Block fallback = world.getHighestBlockAt(center.getBlockX() + 150, center.getBlockZ() + 150);
            chosen = fallback.getLocation().add(0, 1, 0).toBlockLocation();
        }

        return spawnAirDropAt(chosen);
    }

    /**
     * Spawna um AirDrop exatamente na posicao informada.
     */
    public ActiveAirDrop spawnAirDropAt(Location chestLocation) {
        Location blockLoc = normalizeBlockLocation(chestLocation);
        if (blockLoc == null) {
            return null;
        }
        World world = blockLoc.getWorld();

        // Se ja havia um airdrop nessa coordenada, limpa antes
        ActiveAirDrop existing = activeDrops.get(blockLoc);
        if (existing != null) {
            removeAirDrop(existing, true);
        }

        Block block = blockLoc.getBlock();
        block.setType(Material.CHEST, false);

        if (block.getState() instanceof Chest chest) {
            chest.customName(
                    Component.text("📦 Caixa de Suprimentos", NamedTextColor.GOLD, TextDecoration.BOLD)
            );
            chest.update();
            fillLoot(chest.getBlockInventory());
        }

        // Efeito visual de queda
        world.strikeLightningEffect(blockLoc.clone().add(0.5, 0, 0.5));
        world.playSound(blockLoc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 2.0f, 0.8f);
        world.playSound(blockLoc, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 2.5f, 1.0f);

        UUID hologramId = spawnHologram(blockLoc, UNLOCK_DURATION_SECONDS, false);
        ActiveAirDrop drop = new ActiveAirDrop(blockLoc, hologramId, UNLOCK_DURATION_SECONDS);
        activeDrops.put(blockLoc, drop);

        broadcastDropSpawned(blockLoc);
        return drop;
    }

    private void tickActiveDrops() {
        if (activeDrops.isEmpty()) {
            return;
        }

        for (ActiveAirDrop drop : new ArrayList<>(activeDrops.values())) {
            Location loc = drop.getBlockLocation();
            World world = loc.getWorld();
            if (world == null || loc.getBlock().getType() != Material.CHEST) {
                removeAirDrop(drop, false);
                continue;
            }

            Location particleLoc = loc.clone().add(0.5, 1.1, 0.5);

            if (!drop.isUnlocked()) {
                drop.decrementUnlockTimer();
                world.spawnParticle(Particle.FLAME, particleLoc, 8, 0.3, 0.3, 0.3, 0.02);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc.clone().add(0, 0.5, 0), 3, 0.15, 0.4, 0.15, 0.01);

                if (drop.getSecondsUntilUnlock() <= 0) {
                    drop.setUnlocked(true);
                    world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 3.0f, 1.2f);
                    world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 2.0f, 1.0f);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, particleLoc, 40, 0.5, 0.6, 0.5, 0.15);
                    updateHologram(drop);
                    broadcastDropUnlocked(loc);
                } else {
                    if (drop.getSecondsUntilUnlock() == 60 || drop.getSecondsUntilUnlock() == 30) {
                        Bukkit.broadcast(
                                Component.text("📦 [AirDrop] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                        .append(Component.text("A Caixa de Suprimentos em ", NamedTextColor.YELLOW)
                                                .decoration(TextDecoration.BOLD, false))
                                        .append(Component.text(formatCoords(loc), NamedTextColor.AQUA, TextDecoration.BOLD))
                                        .append(Component.text(" destranca em " + drop.getSecondsUntilUnlock() + "s!", NamedTextColor.YELLOW)
                                                .decoration(TextDecoration.BOLD, false))
                        );
                    }
                    updateHologram(drop);
                }
            } else {
                world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 6, 0.35, 0.35, 0.35, 0.02);
                drop.decrementDespawnTimer();
                if (drop.getSecondsUntilDespawn() <= 0) {
                    removeAirDrop(drop, true);
                } else {
                    updateHologram(drop);
                }
            }
        }
    }

    public void removeAirDrop(ActiveAirDrop drop, boolean removeChestBlock) {
        if (drop == null) {
            return;
        }
        activeDrops.remove(drop.getBlockLocation());
        removeHologram(drop.getHologramId());
        if (activeDrops.isEmpty()) {
            XaeroWaypointUtil.clearTemporaryCompassTargets();
        }
        if (removeChestBlock) {
            Block b = drop.getBlockLocation().getBlock();
            if (b.getType() == Material.CHEST) {
                if (b.getState() instanceof Chest chest) {
                    chest.getBlockInventory().clear();
                }
                b.setType(Material.AIR);
            }
        }
    }

    private UUID spawnHologram(Location blockLoc, int secondsLeft, boolean unlocked) {
        World world = blockLoc.getWorld();
        if (world == null) {
            return null;
        }
        Location holoLoc = blockLoc.clone().add(0.5, 1.45, 0.5);
        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            td.text(buildHologramText(secondsLeft, unlocked));
        });
        return display.getUniqueId();
    }

    private void updateHologram(ActiveAirDrop drop) {
        UUID id = drop.getHologramId();
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity instanceof TextDisplay td && td.isValid()) {
            td.text(buildHologramText(drop.getSecondsUntilUnlock(), drop.isUnlocked()));
        } else {
            UUID newId = spawnHologram(drop.getBlockLocation(), drop.getSecondsUntilUnlock(), drop.isUnlocked());
            drop.setHologramId(newId);
        }
    }

    private void removeHologram(UUID id) {
        if (id == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(id);
        if (entity != null) {
            entity.remove();
        }
    }

    private Component buildHologramText(int secondsLeft, boolean unlocked) {
        Component title = Component.text("📦 CAIXA DE SUPRIMENTOS", NamedTextColor.GOLD, TextDecoration.BOLD);
        if (!unlocked) {
            int mins = secondsLeft / 60;
            int secs = secondsLeft % 60;
            String formatted = String.format("%02d:%02d", mins, secs);
            return title.append(Component.newline())
                    .append(Component.text("🔒 Trancado: ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(formatted, NamedTextColor.YELLOW, TextDecoration.BOLD));
        } else {
            return title.append(Component.newline())
                    .append(Component.text("🔓 DESTRANCADO! ", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text("Clique para saquear", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
        }
    }

    private void broadcastDropSpawned(Location loc) {
        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 📦 CAIXA DE SUPRIMENTOS (AIRDROP) CAIU!", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 📍 Coordenadas: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(formatCoords(loc), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" ⏳ Destranca em: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text("2 minutos", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());

        Bukkit.broadcast(msg);
        XaeroWaypointUtil.broadcastWaypoint("AirDrop", "A", loc, XaeroWaypointUtil.COLOR_GOLD);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.RECORDS, 0.8f, 1.0f);
        }
    }

    private void broadcastDropUnlocked(Location loc) {
        Component msg = Component.text("🔓 [AirDrop] ", NamedTextColor.GREEN, TextDecoration.BOLD)
                .append(Component.text("A Caixa de Suprimentos em ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text(formatCoords(loc), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" foi DESTRANCADA! Corra para saquear!", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false));
        Bukkit.broadcast(msg);
        XaeroWaypointUtil.broadcastWaypoint("AirDrop", "A", loc, XaeroWaypointUtil.COLOR_GREEN);
    }

    public static String formatCoords(Location loc) {
        return "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ();
    }

    /**
     * Preenche o bau com 4 a 6 itens sorteados seguindo a tabela de probabilidades:
     * - 60% Comum
     * - 35% Incomum
     * - 5% Raro
     */
    private void fillLoot(Inventory inventory) {
        inventory.clear();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int itemCount = rng.nextInt(4, 7); // 4 a 6 itens

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, rng);

        Set<Material> rareAlreadyAdded = new HashSet<>();

        for (int i = 0; i < itemCount; i++) {
            ItemStack item = rollSingleLootItem(rng, rareAlreadyAdded);
            inventory.setItem(slots.get(i), item);
        }
    }

    private ItemStack rollSingleLootItem(ThreadLocalRandom rng, Set<Material> rareAlreadyAdded) {
        double roll = rng.nextDouble(); // 0.0 ate 1.0

        // 5% Raro (roll >= 0.95)
        if (roll >= 0.95) {
            ItemStack rare = rollRareItem(rng);
            if (!rareAlreadyAdded.contains(rare.getType())) {
                rareAlreadyAdded.add(rare.getType());
                return rare;
            }
            // Se ja saiu um raro do mesmo tipo na mesma caixa, cai para Incomum
        }

        // 35% Incomum (0.60 <= roll < 0.95)
        if (roll >= 0.60) {
            return rollUncommonItem(rng);
        }

        // 60% Comum (roll < 0.60)
        return rollCommonItem(rng);
    }

    private ItemStack rollCommonItem(ThreadLocalRandom rng) {
        int choice = rng.nextInt(5);
        return switch (choice) {
            case 0 -> new ItemStack(Material.IRON_INGOT, rng.nextInt(6, 15));      // 6-14 Ferros
            case 1 -> new ItemStack(Material.GOLD_INGOT, rng.nextInt(4, 11));      // 4-10 Ouros
            case 2 -> new ItemStack(Material.COOKED_BEEF, rng.nextInt(8, 17));     // 8-16 Bifes Assados
            case 3 -> new ItemStack(Material.ENDER_PEARL, rng.nextInt(3, 7));      // 3-6 Perolas do Ender
            default -> new ItemStack(Material.ARROW, rng.nextInt(12, 25));         // 12-24 Flechas
        };
    }

    private ItemStack rollUncommonItem(ThreadLocalRandom rng) {
        int choice = rng.nextInt(5);
        return switch (choice) {
            case 0 -> new ItemStack(Material.DIAMOND, rng.nextInt(2, 6));          // 2-5 Diamantes
            case 1 -> new ItemStack(Material.GOLDEN_APPLE, rng.nextInt(1, 3));     // 1-2 Macas Douradas
            case 2 -> new ItemStack(Material.EXPERIENCE_BOTTLE, rng.nextInt(8, 17)); // 8-16 Frascos de XP
            case 3 -> createRandomSplashPotion(rng);                               // 1 Pocao Arremessavel
            default -> createRandomEnchantedBook(rng);                             // 1 Livro Encantado
        };
    }

    private ItemStack rollRareItem(ThreadLocalRandom rng) {
        int choice = rng.nextInt(4);
        return switch (choice) {
            case 0 -> trackerHornManager.createHornItem(TrackerHornManager.MAX_USES); // 1x Berrante Localizador (10/10)
            case 1 -> new ItemStack(Material.TOTEM_OF_UNDYING, 1);                    // 1x Totem da Imortalidade
            case 2 -> new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1); // 1x Molde de Netherita
            default -> new ItemStack(Material.ANCIENT_DEBRIS, 2);                     // 2x Detritos Ancestrais
        };
    }

    private ItemStack createRandomSplashPotion(ThreadLocalRandom rng) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION, 1);
        if (potion.getItemMeta() instanceof PotionMeta meta) {
            PotionType type = switch (rng.nextInt(4)) {
                case 0 -> PotionType.STRONG_HEALING;       // Cura Instantanea II
                case 1 -> PotionType.STRONG_SWIFTNESS;     // Velocidade II
                case 2 -> PotionType.STRENGTH;             // Forca I
                default -> PotionType.LONG_FIRE_RESISTANCE; // Resistencia ao Fogo (8:00)
            };
            meta.setBasePotionType(type);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private ItemStack createRandomEnchantedBook(ThreadLocalRandom rng) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        if (book.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            switch (rng.nextInt(4)) {
                case 0 -> meta.addStoredEnchant(Enchantment.PROTECTION, rng.nextInt(2, 4), true);      // Protecao II ou III
                case 1 -> meta.addStoredEnchant(Enchantment.SHARPNESS, rng.nextInt(2, 4), true);       // Afiacao II ou III
                case 2 -> meta.addStoredEnchant(Enchantment.UNBREAKING, rng.nextInt(2, 4), true);      // Inquebravel II ou III
                default -> meta.addStoredEnchant(Enchantment.FEATHER_FALLING, 3, true);                // Queda Pena III
            }
            book.setItemMeta(meta);
        }
        return book;
    }
}
