package com.eduardo.teams.quest;

import com.eduardo.teams.gui.TeamGui;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.SmithingInventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Listener completo e protegido contra exploits para todas as Quests Diarias de Equipe.
 */
public class QuestListener implements Listener {

    private static final int MAX_TRACKED_PLACED_BLOCKS = 25000;
    private static final Set<Material> TREASURE_FISH_ITEMS = Set.of(
            Material.BOW,
            Material.ENCHANTED_BOOK,
            Material.NAME_TAG,
            Material.SADDLE,
            Material.NAUTILUS_SHELL
    );

    private final QuestManager questManager;
    private final TeamManager teamManager;
    private final QuestGui questGui;
    private final TeamGui teamGui;

    // Cache LRU de blocos colocados por jogadores para evitar farm de colocar e quebrar o mesmo bloco
    private final Set<Location> playerPlacedBlocks = Collections.newSetFromMap(
            new LinkedHashMap<Location, Boolean>(2048, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Location, Boolean> eldest) {
                    return size() > MAX_TRACKED_PLACED_BLOCKS;
                }
            }
    );

    public QuestListener(QuestManager questManager, TeamManager teamManager, QuestGui questGui, TeamGui teamGui) {
        this.questManager = questManager;
        this.teamManager = teamManager;
        this.questGui = questGui;
        this.teamGui = teamGui;
    }

    // =========================================================================
    // 1. CLIQUES NA GUI (/quest) E MESAS DE COMERCIO/FERRARIA
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof QuestGui.QuestMenuHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && event.getRawSlot() == QuestGui.SLOT_BACK_TO_TEAM) {
                teamGui.openMainMenu(player);
            }
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Troca com Aldeao (Slot 2 do MerchantInventory)
        if (event.getClickedInventory() instanceof MerchantInventory && event.getSlotType() == InventoryType.SlotType.RESULT) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                questManager.recordAction(player, QuestActionType.VILLAGER_TRADE, null, null, 1);
            }
        }

        // Upgrade na Mesa de Ferraria (Slot 3 do SmithingInventory)
        if (event.getClickedInventory() instanceof SmithingInventory && event.getSlotType() == InventoryType.SlotType.RESULT) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir()) {
                questManager.recordAction(player, QuestActionType.SMITHING_UPGRADE, null, null, 1);
            }
        }
    }

    // =========================================================================
    // 2. MINERACAO, COLHEITA E CONSTRUCAO (COM PROTECAO ANTI-EXPLOIT)
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlockPlaced();
        Location loc = block.getLocation().toBlockLocation();
        boolean wasAlreadyTracked = !playerPlacedBlocks.add(loc);

        // So conta construcao se nao estiver apenas recolocando no mesmo bloco repetidamente
        if (!wasAlreadyTracked) {
            Material type = block.getType();
            questManager.recordAction(player, QuestActionType.PLACE_BLOCK, type, null, 1);
            if (type.isSolid()) {
                questManager.recordAction(player, QuestActionType.PLACE_ANY_SOLID, null, null, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Block block = event.getBlock();
        Material type = block.getType();
        Location loc = block.getLocation().toBlockLocation();

        // Plantacoes com idade de crescimento (Trigo, Cenoura, Batata, Beterraba, Fungo do Nether)
        if (block.getBlockData() instanceof Ageable ageable
                && (type == Material.WHEAT || type == Material.CARROTS || type == Material.POTATOES
                || type == Material.BEETROOTS || type == Material.NETHER_WART)) {
            playerPlacedBlocks.remove(loc);
            if (ageable.getAge() == ageable.getMaximumAge()) {
                questManager.recordAction(player, QuestActionType.HARVEST_CROP, type, null, 1);
            }
            return;
        }

        // Se o bloco foi colocado por um jogador, remove da lista e NAO conta para mineracao/colheita
        if (playerPlacedBlocks.remove(loc)) {
            return;
        }

        // Colheita de Melancia, Abobora e Cana-de-Acucar naturais/crescidas
        if (type == Material.MELON || type == Material.PUMPKIN || type == Material.SUGAR_CANE) {
            questManager.recordAction(player, QuestActionType.HARVEST_CROP, type, null, 1);
        }

        questManager.recordAction(player, QuestActionType.MINE_BLOCK, type, null, 1);
        if (type.isSolid()) {
            questManager.recordAction(player, QuestActionType.MINE_ANY_SOLID, null, null, 1);
        }
    }

    // =========================================================================
    // 3. ABATE DE MOBS & PVP ENTRE TIMES
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }

        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        questManager.recordAction(killer, QuestActionType.KILL_MOB, null, entity.getType(), 1);

        if (entity instanceof Enemy) {
            questManager.recordAction(killer, QuestActionType.KILL_ANY_HOSTILE, null, null, 1);

            Material mainHand = killer.getInventory().getItemInMainHand().getType();
            boolean killedByArrow = entity.getLastDamageCause() instanceof EntityDamageByEntityEvent ede
                    && ede.getDamager() instanceof AbstractArrow;

            if (killedByArrow || mainHand == Material.BOW || mainHand == Material.CROSSBOW) {
                questManager.recordAction(killer, QuestActionType.KILL_MOB_BOW, null, null, 1);
            }
            if (mainHand.name().endsWith("_AXE")) {
                questManager.recordAction(killer, QuestActionType.KILL_MOB_AXE, null, null, 1);
            }

            World world = entity.getWorld();
            if (world.getEnvironment() == World.Environment.NORMAL) {
                long time = world.getTime();
                if (time >= 13000 && time <= 23000) {
                    questManager.recordAction(killer, QuestActionType.KILL_MOB_NIGHT, null, null, 1);
                }
            } else if (world.getEnvironment() == World.Environment.NETHER) {
                questManager.recordAction(killer, QuestActionType.KILL_MOB_NETHER, null, null, 1);
            }

            if (entity.getFireTicks() > 0) {
                questManager.recordAction(killer, QuestActionType.KILL_MOB_FIRE, null, null, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        if (teamManager.isSameTeam(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        if (!teamManager.wasLastKillRewarded(killer.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        questManager.recordAction(killer, QuestActionType.KILL_ENEMY_ANY, null, null, 1);

        Team victimTeam = teamManager.getPlayerTeam(victim.getUniqueId());
        if (victimTeam != null && victimTeam.isLeader(victim.getUniqueId())) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_LEADER, null, null, 1);
        }

        Material weapon = killer.getInventory().getItemInMainHand().getType();
        boolean arrowKill = victim.getLastDamageCause() instanceof EntityDamageByEntityEvent ede
                && ede.getDamager() instanceof AbstractArrow;

        if (arrowKill || weapon == Material.BOW || weapon == Material.CROSSBOW) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_BOW, null, null, 1);
        }
        if (weapon.name().endsWith("_AXE")) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_AXE, null, null, 1);
        }
        if (weapon.name().endsWith("_SWORD")) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_SWORD, null, null, 1);
        }

        World world = victim.getWorld();
        if (world.getEnvironment() == World.Environment.NETHER) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_NETHER, null, null, 1);
        } else if (world.getEnvironment() == World.Environment.NORMAL) {
            long time = world.getTime();
            if (time >= 13000 && time <= 23000) {
                questManager.recordAction(killer, QuestActionType.KILL_ENEMY_NIGHT, null, null, 1);
            }
        }

        if (victim.isInWater()) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_WATER, null, null, 1);
        }
        if (killer.getFallDistance() > 0.0f && !killer.isOnGround() && !killer.isInWater()) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_CRITICAL, null, null, 1);
        }
        if (killer.getHealth() <= 6.0) {
            questManager.recordAction(killer, QuestActionType.KILL_ENEMY_LOW_HP, null, null, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (teamManager.isSameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }

        int dmg = Math.max(1, (int) Math.round(event.getFinalDamage()));
        questManager.recordAction(attacker, QuestActionType.PVP_DAMAGE, null, null, dmg);

        if (event.getDamager() instanceof AbstractArrow) {
            questManager.recordAction(attacker, QuestActionType.PVP_ARROW_HIT, null, null, 1);
            if (attacker.getLocation().distanceSquared(victim.getLocation()) >= 25.0 * 25.0) {
                questManager.recordAction(attacker, QuestActionType.PVP_LONG_ARROW, null, null, 1);
            }
        } else if (event.getDamager() instanceof Snowball || event.getDamager() instanceof Egg) {
            questManager.recordAction(attacker, QuestActionType.PVP_SNOWBALL_EGG, null, null, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPearlThrow(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player) {
            questManager.recordAction(player, QuestActionType.USE_ENDER_PEARL, null, null, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTotemPop(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            questManager.recordAction(player, QuestActionType.POP_TOTEM_PVP, null, null, 1);
        }
    }

    // =========================================================================
    // 4. CRAFTING, FUNDICAO, PESCA, FAZENDA E ENCANTAMENTO
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result == null || result.getType().isAir()) {
            return;
        }

        int craftedAmount = calculateCraftedAmount(event);
        if (craftedAmount > 0) {
            questManager.recordAction(player, QuestActionType.CRAFT_ITEM, result.getType(), null, craftedAmount);
        }
    }

    private int calculateCraftedAmount(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        int baseAmount = result.getAmount();

        if (!event.isShiftClick()) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!cursor.isSimilar(result)) {
                    return 0;
                }
                if (cursor.getAmount() + baseAmount > cursor.getMaxStackSize()) {
                    return 0;
                }
            }
            return baseAmount;
        }

        CraftingInventory inv = event.getInventory();
        int minIngredientCount = Integer.MAX_VALUE;
        for (ItemStack item : inv.getMatrix()) {
            if (item != null && !item.getType().isAir()) {
                minIngredientCount = Math.min(minIngredientCount, item.getAmount());
            }
        }
        if (minIngredientCount == Integer.MAX_VALUE || minIngredientCount <= 0) {
            return 0;
        }

        int maxPossible = minIngredientCount * baseAmount;
        int freeSpace = countFreeSpaceFor(event.getWhoClicked().getInventory(), result);
        int craftsFit = freeSpace / baseAmount;
        return Math.min(minIngredientCount, craftsFit) * baseAmount;
    }

    private int countFreeSpaceFor(Inventory inv, ItemStack sample) {
        int space = 0;
        int maxStack = sample.getMaxStackSize();
        for (ItemStack slot : inv.getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                space += maxStack;
            } else if (slot.isSimilar(sample) && slot.getAmount() < maxStack) {
                space += (maxStack - slot.getAmount());
            }
        }
        return space;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (event.getItemAmount() > 0) {
            questManager.recordAction(event.getPlayer(), QuestActionType.SMELT_EXTRACT, event.getItemType(), null, event.getItemAmount());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        questManager.recordAction(player, QuestActionType.FISH_ANY, null, null, 1);

        if (event.getCaught() instanceof Item caughtItem) {
            ItemStack stack = caughtItem.getItemStack();
            if (TREASURE_FISH_ITEMS.contains(stack.getType())
                    || (stack.getType() == Material.FISHING_ROD && !stack.getEnchantments().isEmpty())) {
                questManager.recordAction(player, QuestActionType.FISH_TREASURE, null, null, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            questManager.recordAction(player, QuestActionType.BREED_ANIMAL, null, event.getEntityType(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (event.getOwner() instanceof Player player) {
            questManager.recordAction(player, QuestActionType.TAME_ANIMAL, null, event.getEntityType(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        questManager.recordAction(event.getPlayer(), QuestActionType.SHEAR_SHEEP, null, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack bucket = event.getItemStack();
        if (bucket != null && bucket.getType() == Material.MILK_BUCKET) {
            questManager.recordAction(event.getPlayer(), QuestActionType.MILK_COW, null, null, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) {
            questManager.recordAction(event.getPlayer(), QuestActionType.CONSUME_FOOD, null, null, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        questManager.recordAction(event.getEnchanter(), QuestActionType.ENCHANT_ITEM, null, null, 1);
    }

    // =========================================================================
    // 5. EXPLORACAO E MOVIMENTACAO
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (to.getBlockY() <= -50) {
            questManager.recordAction(player, QuestActionType.REACH_LOW_Y, null, null, 1);
        } else if (to.getBlockY() >= 180) {
            questManager.recordAction(player, QuestActionType.REACH_HIGH_Y, null, null, 1);
        }

        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        World world = to.getWorld();
        if (world != null && world.getEnvironment() == World.Environment.NETHER) {
            questManager.recordAction(player, QuestActionType.TRAVEL_NETHER, null, null, 1);
        }

        if (player.isInsideVehicle()) {
            if (player.getVehicle() instanceof Boat) {
                questManager.recordAction(player, QuestActionType.TRAVEL_BOAT, null, null, 1);
            } else if (player.getVehicle() instanceof AbstractHorse || player.getVehicle() instanceof Strider) {
                questManager.recordAction(player, QuestActionType.TRAVEL_MOUNT, null, null, 1);
            }
        } else if (player.isSwimming() || player.isInWater()) {
            questManager.recordAction(player, QuestActionType.TRAVEL_SWIM, null, null, 1);
        } else if (!player.isGliding() && !player.isFlying()) {
            questManager.recordAction(player, QuestActionType.TRAVEL_WALK, null, null, 1);
        }
    }
}
