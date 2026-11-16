package com.eduardo.wand;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Slab;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class ConstructionWandPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    /**
     * Tiers oficiais do mod Construction Wand:
     * - IRON: 250 durabilidade, até 27 blocos por uso, reparável com IRON_INGOT
     * - DIAMOND: 1561 durabilidade, até 128 blocos por uso, reparável com DIAMOND
     * - NETHERITE (Infinity): Indestrutível (-1), até 1024 blocos por uso, feita na Mesa de Ferraria (Smithing Table)
     */
    public enum WandTier {
        IRON(
                "Varinha de Construção de Ferro",
                ChatColor.AQUA,
                250,
                27,
                Material.IRON_INGOT,
                false
        ),
        DIAMOND(
                "Varinha de Construção de Diamante",
                ChatColor.LIGHT_PURPLE,
                1561,
                128,
                Material.DIAMOND,
                false
        ),
        NETHERITE(
                "Varinha de Construção de Netherita",
                ChatColor.GOLD,
                -1,
                1024,
                null,
                true
        );

        public final String displayName;
        public final ChatColor color;
        public final int maxDurability;
        public final int maxBlocks;
        public final Material repairMaterial;
        public final boolean unbreakable;

        WandTier(String displayName, ChatColor color, int maxDurability, int maxBlocks, Material repairMaterial, boolean unbreakable) {
            this.displayName = displayName;
            this.color = color;
            this.maxDurability = maxDurability;
            this.maxBlocks = maxBlocks;
            this.repairMaterial = repairMaterial;
            this.unbreakable = unbreakable;
        }
    }

    private NamespacedKey wandKey;
    private NamespacedKey tierKey;
    private NamespacedKey durabilityKey;
    private NamespacedKey optLockKey;
    private NamespacedKey optDirectionKey;
    private NamespacedKey optReplaceKey;
    private NamespacedKey optMatchKey;
    private NamespacedKey optRandomKey;

    private NamespacedKey ironRecipeKey;
    private NamespacedKey diamondRecipeKey;
    private NamespacedKey netheriteSmithingKey;

    private static final String GUI_TITLE = ChatColor.DARK_GRAY + "⚙ Opções da Varinha";

    // Cooldown anti-duplo clique
    private final Map<UUID, Long> interactCooldown = new HashMap<>();

    // Grupos de blocos similares do mod Construction Wand
    private static final List<Set<Material>> SIMILAR_GROUPS = new ArrayList<>();

    static {
        SIMILAR_GROUPS.add(EnumSet.of(
                Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT,
                Material.PODZOL, Material.MYCELIUM, Material.ROOTED_DIRT,
                Material.FARMLAND, Material.DIRT_PATH
        ));
        SIMILAR_GROUPS.add(EnumSet.of(
                Material.STONE, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
                Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS
        ));
        SIMILAR_GROUPS.add(EnumSet.of(
                Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.POLISHED_DEEPSLATE,
                Material.DEEPSLATE_BRICKS, Material.CRACKED_DEEPSLATE_BRICKS,
                Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES
        ));
        SIMILAR_GROUPS.add(EnumSet.of(
                Material.SAND, Material.RED_SAND, Material.SANDSTONE, Material.RED_SANDSTONE
        ));
    }

    // Enums das Opções da Varinha (idênticos ao WandOptions do mod)
    public enum LockOption {
        NOLOCK("Nenhum (Livre)", ChatColor.GREEN, Material.COMPASS, "Estende em todas as direções do plano"),
        HORIZONTAL("Horizontal", ChatColor.AQUA, Material.SPECTRAL_ARROW, "Estende apenas na linha horizontal"),
        VERTICAL("Vertical", ChatColor.YELLOW, Material.ARROW, "Estende apenas na coluna vertical"),
        NORTHSOUTH("Norte / Sul", ChatColor.GOLD, Material.RAIL, "Estende na direção Norte/Sul (ao olhar Cima/Baixo)"),
        EASTWEST("Leste / Oeste", ChatColor.LIGHT_PURPLE, Material.POWERED_RAIL, "Estende na direção Leste/Oeste (ao olhar Cima/Baixo)");

        public final String label;
        public final ChatColor color;
        public final Material icon;
        public final String desc;

        LockOption(String label, ChatColor color, Material icon, String desc) {
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.desc = desc;
        }

        public LockOption next() {
            LockOption[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public enum DirectionOption {
        TARGET("Alvo (Bloco)", ChatColor.GOLD, Material.TARGET, "Coloca blocos com a mesma orientação do bloco clicado"),
        PLAYER("Jogador", ChatColor.AQUA, Material.ARMOR_STAND, "Coloca blocos virados para a direção do jogador");

        public final String label;
        public final ChatColor color;
        public final Material icon;
        public final String desc;

        DirectionOption(String label, ChatColor color, Material icon, String desc) {
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.desc = desc;
        }

        public DirectionOption next() {
            DirectionOption[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public enum ReplaceOption {
        YES("Sim", ChatColor.GREEN, Material.WATER_BUCKET, "Substitui líquidos, grama baixa, neve e plantas"),
        NO("Não", ChatColor.RED, Material.BARRIER, "Coloca apenas no ar vazio");

        public final String label;
        public final ChatColor color;
        public final Material icon;
        public final String desc;

        ReplaceOption(String label, ChatColor color, Material icon, String desc) {
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.desc = desc;
        }

        public ReplaceOption next() {
            ReplaceOption[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public enum MatchOption {
        EXACT("Exato", ChatColor.GREEN, Material.STONE_BRICKS, "Estende apenas blocos exatamente iguais"),
        SIMILAR("Similar", ChatColor.YELLOW, Material.GRASS_BLOCK, "Trata blocos parecidos (ex: Terra e Grama) como iguais"),
        ANY("Qualquer", ChatColor.LIGHT_PURPLE, Material.CRAFTING_TABLE, "Estende qualquer bloco adjacente na face");

        public final String label;
        public final ChatColor color;
        public final Material icon;
        public final String desc;

        MatchOption(String label, ChatColor color, Material icon, String desc) {
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.desc = desc;
        }

        public MatchOption next() {
            MatchOption[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public enum RandomOption {
        NO("Não", ChatColor.RED, Material.REDSTONE_TORCH, "Usa o mesmo bloco que está sendo estendido"),
        YES("Sim (Hotbar)", ChatColor.GREEN, Material.EXPERIENCE_BOTTLE, "Coloca blocos aleatórios presentes na sua Hotbar");

        public final String label;
        public final ChatColor color;
        public final Material icon;
        public final String desc;

        RandomOption(String label, ChatColor color, Material icon, String desc) {
            this.label = label;
            this.color = color;
            this.icon = icon;
            this.desc = desc;
        }

        public RandomOption next() {
            RandomOption[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    @Override
    public void onEnable() {
        this.wandKey = new NamespacedKey(this, "iron_construction_wand");
        this.tierKey = new NamespacedKey(this, "wand_tier");
        this.durabilityKey = new NamespacedKey(this, "wand_durability");
        this.optLockKey = new NamespacedKey(this, "opt_lock");
        this.optDirectionKey = new NamespacedKey(this, "opt_direction");
        this.optReplaceKey = new NamespacedKey(this, "opt_replace");
        this.optMatchKey = new NamespacedKey(this, "opt_match");
        this.optRandomKey = new NamespacedKey(this, "opt_random");

        this.ironRecipeKey = new NamespacedKey(this, "iron_wand_recipe");
        this.diamondRecipeKey = new NamespacedKey(this, "diamond_wand_recipe");
        this.netheriteSmithingKey = new NamespacedKey(this, "netherite_wand_smithing");

        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("wand") != null) {
            getCommand("wand").setExecutor(this);
            getCommand("wand").setTabCompleter(this);
        }

        registerWandRecipes();

        // Tarefa visual de preview (a cada 6 ticks) para mostrar os blocos que serão colocados
        Bukkit.getScheduler().runTaskTimer(this, this::tickPreviewParticles, 10L, 6L);

        getLogger().info("ConstructionWandPlugin v1.1.0 (Ferro, Diamante e Netherita) ativado com sucesso!");
    }

    @Override
    public void onDisable() {
        if (ironRecipeKey != null) Bukkit.removeRecipe(ironRecipeKey);
        if (diamondRecipeKey != null) Bukkit.removeRecipe(diamondRecipeKey);
        if (netheriteSmithingKey != null) Bukkit.removeRecipe(netheriteSmithingKey);
    }

    /**
     * Registra os crafts das varinhas:
     * 1. Varinha de Ferro (Bancada 3x3): Ferro em 2, 3, 6 e Graveto em 5, 7
     * 2. Varinha de Diamante (Bancada 3x3): Diamante em 2, 3, 6 e Graveto em 5, 7
     * 3. Varinha de Netherita (Mesa de Ferraria / Smithing Table): Molde de Netherita + Varinha de Diamante + Barra de Netherita
     */
    private void registerWandRecipes() {
        Bukkit.removeRecipe(ironRecipeKey);
        Bukkit.removeRecipe(diamondRecipeKey);
        Bukkit.removeRecipe(netheriteSmithingKey);

        // 1. Varinha de Ferro
        ShapedRecipe ironRecipe = new ShapedRecipe(ironRecipeKey, createWand(WandTier.IRON));
        ironRecipe.shape(
                " II",
                " SI",
                "S  "
        );
        ironRecipe.setIngredient('I', Material.IRON_INGOT);
        ironRecipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(ironRecipe);

        // 2. Varinha de Diamante
        ShapedRecipe diamondRecipe = new ShapedRecipe(diamondRecipeKey, createWand(WandTier.DIAMOND));
        diamondRecipe.shape(
                " DD",
                " SD",
                "S  "
        );
        diamondRecipe.setIngredient('D', Material.DIAMOND);
        diamondRecipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(diamondRecipe);

        // 3. Varinha de Netherita na Mesa de Ferraria (Smithing Table)
        SmithingTransformRecipe smithingRecipe = new SmithingTransformRecipe(
                netheriteSmithingKey,
                createWand(WandTier.NETHERITE),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                new RecipeChoice.MaterialChoice(Material.STICK),
                new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT),
                false
        );
        Bukkit.addRecipe(smithingRecipe);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.discoverRecipe(ironRecipeKey);
            p.discoverRecipe(diamondRecipeKey);
            p.discoverRecipe(netheriteSmithingKey);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.discoverRecipe(ironRecipeKey);
        p.discoverRecipe(diamondRecipeKey);
        p.discoverRecipe(netheriteSmithingKey);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        interactCooldown.remove(event.getPlayer().getUniqueId());
    }

    public ItemStack createWand(WandTier tier) {
        ItemStack item = new ItemStack(Material.STICK, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(tier.color + "" + ChatColor.BOLD + tier.displayName);
            meta.setEnchantmentGlintOverride(true);
            meta.setMaxStackSize(1);
            meta.setFireResistant(tier == WandTier.NETHERITE);

            if (tier.unbreakable) {
                meta.setUnbreakable(true);
            } else if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(tier.maxDurability);
                damageable.setDamage(0);
            }

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(tierKey, PersistentDataType.STRING, tier.name());
            if (!tier.unbreakable) {
                pdc.set(durabilityKey, PersistentDataType.INTEGER, tier.maxDurability);
            }
            pdc.set(optLockKey, PersistentDataType.STRING, LockOption.NOLOCK.name());
            pdc.set(optDirectionKey, PersistentDataType.STRING, DirectionOption.TARGET.name());
            pdc.set(optReplaceKey, PersistentDataType.STRING, ReplaceOption.YES.name());
            pdc.set(optMatchKey, PersistentDataType.STRING, MatchOption.SIMILAR.name());
            pdc.set(optRandomKey, PersistentDataType.STRING, RandomOption.NO.name());

            item.setItemMeta(meta);
            updateWandLore(item);
        }
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.STICK || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public WandTier getWandTier(ItemStack wand) {
        if (!isWand(wand)) return WandTier.IRON;
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return WandTier.IRON;
        String tierStr = meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (tierStr == null) return WandTier.IRON; // Compatibilidade com varinhas de ferro já criadas
        try {
            return WandTier.valueOf(tierStr);
        } catch (IllegalArgumentException e) {
            return WandTier.IRON;
        }
    }

    public int getRemainingDurability(ItemStack wand) {
        if (!isWand(wand)) return 0;
        WandTier tier = getWandTier(wand);
        if (tier.unbreakable) return Integer.MAX_VALUE;
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return 0;
        Integer rem = meta.getPersistentDataContainer().get(durabilityKey, PersistentDataType.INTEGER);
        return rem != null ? rem : tier.maxDurability;
    }

    public void setRemainingDurability(ItemStack wand, int remaining) {
        if (!isWand(wand)) return;
        WandTier tier = getWandTier(wand);
        if (tier.unbreakable) return;

        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return;
        int clamped = Math.max(0, Math.min(tier.maxDurability, remaining));
        meta.getPersistentDataContainer().set(durabilityKey, PersistentDataType.INTEGER, clamped);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(tier.maxDurability);
            damageable.setDamage(tier.maxDurability - clamped);
        }
        wand.setItemMeta(meta);
        updateWandLore(wand);
    }

    public LockOption getLockOption(ItemStack wand) {
        try {
            String val = wand.getItemMeta().getPersistentDataContainer().get(optLockKey, PersistentDataType.STRING);
            return val != null ? LockOption.valueOf(val) : LockOption.NOLOCK;
        } catch (Exception e) {
            return LockOption.NOLOCK;
        }
    }

    public DirectionOption getDirectionOption(ItemStack wand) {
        try {
            String val = wand.getItemMeta().getPersistentDataContainer().get(optDirectionKey, PersistentDataType.STRING);
            return val != null ? DirectionOption.valueOf(val) : DirectionOption.TARGET;
        } catch (Exception e) {
            return DirectionOption.TARGET;
        }
    }

    public ReplaceOption getReplaceOption(ItemStack wand) {
        try {
            String val = wand.getItemMeta().getPersistentDataContainer().get(optReplaceKey, PersistentDataType.STRING);
            return val != null ? ReplaceOption.valueOf(val) : ReplaceOption.YES;
        } catch (Exception e) {
            return ReplaceOption.YES;
        }
    }

    public MatchOption getMatchOption(ItemStack wand) {
        try {
            String val = wand.getItemMeta().getPersistentDataContainer().get(optMatchKey, PersistentDataType.STRING);
            return val != null ? MatchOption.valueOf(val) : MatchOption.SIMILAR;
        } catch (Exception e) {
            return MatchOption.SIMILAR;
        }
    }

    public RandomOption getRandomOption(ItemStack wand) {
        try {
            String val = wand.getItemMeta().getPersistentDataContainer().get(optRandomKey, PersistentDataType.STRING);
            return val != null ? RandomOption.valueOf(val) : RandomOption.NO;
        } catch (Exception e) {
            return RandomOption.NO;
        }
    }

    public void updateWandLore(ItemStack wand) {
        if (!isWand(wand)) return;
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return;

        WandTier tier = getWandTier(wand);
        LockOption lock = getLockOption(wand);
        DirectionOption dir = getDirectionOption(wand);
        ReplaceOption rep = getReplaceOption(wand);
        MatchOption match = getMatchOption(wand);
        RandomOption rand = getRandomOption(wand);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Estende estruturas em até " + ChatColor.WHITE + tier.maxBlocks + " blocos" + ChatColor.GRAY + " por clique.");
        lore.add("");

        if (tier.unbreakable) {
            lore.add(ChatColor.GRAY + "Durabilidade: " + ChatColor.GOLD + "" + ChatColor.BOLD + "∞ Indestrutível");
        } else {
            int rem = getRemainingDurability(wand);
            double ratio = (double) rem / tier.maxDurability;
            ChatColor durColor = ratio > 0.5 ? ChatColor.GREEN : (ratio > 0.2 ? ChatColor.YELLOW : ChatColor.RED);
            lore.add(ChatColor.GRAY + "Durabilidade: " + durColor + rem + ChatColor.GRAY + " / " + ChatColor.WHITE + tier.maxDurability);
        }

        lore.add("");
        lore.add(ChatColor.GOLD + "Opções Ativas:");
        lore.add(ChatColor.GRAY + " • Restrição: " + lock.color + lock.label);
        lore.add(ChatColor.GRAY + " • Direção: " + dir.color + dir.label);
        lore.add(ChatColor.GRAY + " • Substituição: " + rep.color + rep.label);
        lore.add(ChatColor.GRAY + " • Coincidindo: " + match.color + match.label);
        lore.add(ChatColor.GRAY + " • Aleatório: " + rand.color + rand.label);
        lore.add("");
        lore.add(ChatColor.YELLOW + "▸ Clique Direito no Bloco: " + ChatColor.WHITE + "Construir");
        lore.add(ChatColor.YELLOW + "▸ Shift + Clique Esquerdo: " + ChatColor.WHITE + "Alternar Restrição");
        lore.add(ChatColor.YELLOW + "▸ Shift + Clique Direito (Ar): " + ChatColor.WHITE + "Menu de Opções");

        meta.setLore(lore);
        wand.setItemMeta(meta);
    }

    /**
     * Upgrade da Varinha de Diamante -> Varinha de Netherita na Mesa de Ferraria (Smithing Table),
     * preservando as opções configuradas pelo jogador e exigindo exatamente a Varinha de Diamante!
     */
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getInputTemplate();
        ItemStack base = inv.getInputEquipment();
        ItemStack mineral = inv.getInputMineral();

        // Se o jogador colocou um graveto comum ou uma varinha que NÃO é de Diamante, bloqueia o resultado
        if (base != null && base.getType() == Material.STICK) {
            if (!isWand(base) || getWandTier(base) != WandTier.DIAMOND) {
                event.setResult(null);
                return;
            }
            if (template != null && template.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE
                    && mineral != null && mineral.getType() == Material.NETHERITE_INGOT) {
                ItemStack upgraded = createWand(WandTier.NETHERITE);
                copyOptions(base, upgraded);
                event.setResult(upgraded);
            }
        }
    }

    private void copyOptions(ItemStack sourceWand, ItemStack targetWand) {
        ItemMeta srcMeta = sourceWand.getItemMeta();
        ItemMeta dstMeta = targetWand.getItemMeta();
        if (srcMeta == null || dstMeta == null) return;

        PersistentDataContainer srcPdc = srcMeta.getPersistentDataContainer();
        PersistentDataContainer dstPdc = dstMeta.getPersistentDataContainer();

        dstPdc.set(optLockKey, PersistentDataType.STRING, getLockOption(sourceWand).name());
        dstPdc.set(optDirectionKey, PersistentDataType.STRING, getDirectionOption(sourceWand).name());
        dstPdc.set(optReplaceKey, PersistentDataType.STRING, getReplaceOption(sourceWand).name());
        dstPdc.set(optMatchKey, PersistentDataType.STRING, getMatchOption(sourceWand).name());
        dstPdc.set(optRandomKey, PersistentDataType.STRING, getRandomOption(sourceWand).name());

        targetWand.setItemMeta(dstMeta);
        updateWandLore(targetWand);
    }

    /**
     * Reparo na Bigorna:
     * - Varinha de Ferro + Barra de Ferro (IRON_INGOT)
     * - Varinha de Diamante + Diamante (DIAMOND)
     * Define o custo de XP e permite retirar o item reparado normalmente!
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first = inv.getFirstItem();
        ItemStack second = inv.getSecondItem();
        if (!isWand(first) || second == null) {
            return;
        }

        WandTier tier = getWandTier(first);
        if (tier.unbreakable || tier.repairMaterial == null || second.getType() != tier.repairMaterial) {
            return;
        }

        int rem = getRemainingDurability(first);
        if (rem >= tier.maxDurability) return;

        // Cada unidade do material repara 50% da durabilidade máxima (máximo 2 materiais para 100%)
        int repairPerMaterial = (int) Math.ceil(tier.maxDurability / 2.0);
        int missing = tier.maxDurability - rem;
        int materialsNeeded = Math.min(second.getAmount(), (int) Math.ceil((double) missing / repairPerMaterial));
        if (materialsNeeded <= 0) return;

        ItemStack result = first.clone();
        int newDur = Math.min(tier.maxDurability, rem + repairPerMaterial * materialsNeeded);
        setRemainingDurability(result, newDur);

        event.setResult(result);

        // Configura o custo de reparo na AnvilView do Paper 1.21+ para liberar a retirada pelo cliente
        try {
            AnvilView view = event.getView();
            view.setRepairItemCountCost(materialsNeeded);
            view.setRepairCost(materialsNeeded);
        } catch (Throwable ignored) {
            inv.setRepairCostAmount(materialsNeeded);
            inv.setRepairCost(materialsNeeded);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!isWand(wand)) return;

        Action action = event.getAction();

        // Shift + Clique Direito no ar -> Abre o Menu GUI da Varinha
        if (action == Action.RIGHT_CLICK_AIR && player.isSneaking()) {
            event.setCancelled(true);
            openOptionsGui(player);
            return;
        }

        // Shift + Clique Esquerdo (no ar ou em bloco) -> Alterna rapidamente a Restrição (Lock) igual ao mod
        if ((action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) && player.isSneaking()) {
            event.setCancelled(true);
            cycleLockOption(player, wand);
            return;
        }

        // Clique Direito em um bloco -> Executa a Construção (ActionConstruction)
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            long now = System.currentTimeMillis();
            long last = interactCooldown.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < 120L) {
                event.setCancelled(true);
                return;
            }
            interactCooldown.put(player.getUniqueId(), now);

            event.setCancelled(true);
            executeWandConstruction(player, wand, event.getClickedBlock(), event.getBlockFace());
        }
    }

    private void cycleLockOption(Player player, ItemStack wand) {
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return;
        LockOption next = getLockOption(wand).next();
        meta.getPersistentDataContainer().set(optLockKey, PersistentDataType.STRING, next.name());
        wand.setItemMeta(meta);
        updateWandLore(wand);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
        player.sendMessage(ChatColor.GOLD + "[Varinha] " + ChatColor.GRAY + "Restrição: " + next.color + next.label);
    }

    /**
     * Executa o algoritmo BFS de extensão de blocos idêntico ao ActionConstruction do mod Construction Wand.
     */
    private void executeWandConstruction(Player player, ItemStack wand, Block clickedBlock, BlockFace clickedFace) {
        Material targetMat = clickedBlock.getType();
        if (!isPlaceableSolidBlock(targetMat)) {
            return;
        }

        WandTier tier = getWandTier(wand);
        int remainingDurability = getRemainingDurability(wand);
        boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
        boolean consumesDurability = !isCreative && !tier.unbreakable;

        if (consumesDurability && remainingDurability <= 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.getInventory().setItemInMainHand(null);
            return;
        }

        LockOption lock = getLockOption(wand);
        DirectionOption directionOpt = getDirectionOption(wand);
        ReplaceOption replaceOpt = getReplaceOption(wand);
        MatchOption matchOpt = getMatchOption(wand);
        RandomOption randomOpt = getRandomOption(wand);

        // Verifica se há um bloco posicionável na mão secundária (Offhand Priority do mod)
        ItemStack offhand = player.getInventory().getItemInOffHand();
        Material desiredPlaceMaterial = targetMat;
        BlockData desiredBaseData = clickedBlock.getBlockData().clone();

        if (offhand != null && offhand.getType().isBlock() && isPlaceableSolidBlock(offhand.getType())) {
            desiredPlaceMaterial = offhand.getType();
            desiredBaseData = desiredPlaceMaterial.createBlockData();
        }

        int maxAllowed = consumesDurability ? Math.min(tier.maxBlocks, remainingDurability) : tier.maxBlocks;

        // Calcula os candidatos de colocação via BFS no plano perpendicular à face clicada
        List<Block> candidates = findExtensionCandidates(player, clickedBlock, clickedFace, lock, replaceOpt, matchOpt, maxAllowed);
        if (candidates.isEmpty()) {
            return;
        }

        int placedCount = 0;
        Material firstPlacedMaterial = null;
        Random rng = new Random();

        for (Block placeBlock : candidates) {
            Material matToPlace = desiredPlaceMaterial;
            BlockData dataToPlace = desiredBaseData.clone();

            // Se modo Aleatório (Random = YES) estiver ativo, escolhe um bloco aleatório da Hotbar (slots 0..8)
            if (randomOpt == RandomOption.YES) {
                List<Material> hotbarBlocks = getHotbarPlaceableMaterials(player);
                if (hotbarBlocks.isEmpty()) break;
                matToPlace = hotbarBlocks.get(rng.nextInt(hotbarBlocks.size()));
                dataToPlace = matToPlace.createBlockData();
            } else {
                // Verifica se o jogador possui o bloco no inventário/offhand/shulker box (exceto no Criativo)
                if (!isCreative && !hasBlockInSupply(player, matToPlace, matchOpt)) {
                    break;
                }
            }

            // Se MatchOption == SIMILAR e não tiver o bloco exato mas tiver um similar no inventário
            if (!isCreative && randomOpt == RandomOption.NO && !hasExactBlockInSupply(player, matToPlace)) {
                Material fallback = findSimilarBlockInSupply(player, matToPlace);
                if (fallback == null) break;
                matToPlace = fallback;
                dataToPlace = matToPlace.createBlockData();
            }

            // Aplica a opção de Direção (TARGET copia a orientação do bloco alvo; PLAYER orienta para o jogador)
            if (directionOpt == DirectionOption.PLAYER || dataToPlace.getMaterial() != clickedBlock.getType()) {
                applyPlayerOrientation(dataToPlace, player, clickedFace);
            }

            // Checa se não há uma entidade viva (jogador/mob) colidindo com a caixa do bloco
            BoundingBox blockBox = BoundingBox.of(placeBlock);
            boolean entityCollision = false;
            for (Entity ent : placeBlock.getWorld().getNearbyEntities(blockBox)) {
                if (ent instanceof LivingEntity) {
                    entityCollision = true;
                    break;
                }
            }
            if (entityCollision) continue;

            // Dispara BlockPlaceEvent para respeitar proteções de mundo/clãs/spawn
            BlockState previousState = placeBlock.getState();
            BlockData previousData = placeBlock.getBlockData().clone();

            placeBlock.setBlockData(dataToPlace, true);

            Block supportingBlock = placeBlock.getRelative(clickedFace.getOppositeFace());
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                    placeBlock,
                    previousState,
                    supportingBlock,
                    wand,
                    player,
                    true,
                    EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
                placeBlock.setBlockData(previousData, false);
                continue;
            }

            // Consome 1 bloco do suprimento do jogador (Inventário / Offhand / Shulker Box)
            if (!isCreative) {
                if (!consumeOneBlockFromSupply(player, matToPlace)) {
                    placeBlock.setBlockData(previousData, false);
                    continue;
                }
            }

            if (firstPlacedMaterial == null) {
                firstPlacedMaterial = matToPlace;
            }
            placedCount++;
        }

        if (placedCount == 0) {
            return;
        }

        // Toca o som nativo do bloco colocado
        Sound placeSound = firstPlacedMaterial.createBlockData().getSoundGroup().getPlaceSound();
        player.getWorld().playSound(clickedBlock.getLocation(), placeSound, 1.0f, 1.0f);

        // Consome durabilidade (1 por bloco colocado, exceto no Criativo ou Varinha de Netherita Indestrutível)
        if (consumesDurability) {
            int newDurability = remainingDurability - placedCount;
            if (newDurability <= 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                player.spawnParticle(Particle.ITEM, player.getEyeLocation(), 15, 0.2, 0.2, 0.2, 0.05, wand);
                player.getInventory().setItemInMainHand(null);
            } else {
                setRemainingDurability(wand, newDurability);
            }
        }
    }

    /**
     * Encontra até `maxBlocks` blocos candidatos usando Busca em Largura (BFS) idêntica ao ActionConstruction do mod.
     */
    public List<Block> findExtensionCandidates(
            Player player,
            Block clickedBlock,
            BlockFace clickedFace,
            LockOption lock,
            ReplaceOption replaceOpt,
            MatchOption matchOpt,
            int maxBlocks
    ) {
        List<Block> result = new ArrayList<>();
        Block firstTarget = clickedBlock.getRelative(clickedFace);
        if (!canReplace(firstTarget, replaceOpt)) {
            return result;
        }

        Material sourceMaterial = clickedBlock.getType();
        BlockData sourceData = clickedBlock.getBlockData();

        List<int[]> offsets = getAllowedPlaneOffsets(clickedFace, lock);

        Queue<Block> queue = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();

        queue.add(firstTarget);
        visited.add(firstTarget.getLocation());

        // Raio máximo (igual ao maxRange = 100 do mod Construction Wand)
        double maxDistSq = maxBlocks > 128 ? 1600.0 : 256.0;

        while (!queue.isEmpty() && result.size() < maxBlocks) {
            Block currentTarget = queue.poll();
            result.add(currentTarget);

            for (int[] off : offsets) {
                Block nextTarget = currentTarget.getRelative(off[0], off[1], off[2]);
                Location nextLoc = nextTarget.getLocation();
                if (visited.contains(nextLoc)) continue;

                if (nextLoc.distanceSquared(firstTarget.getLocation()) > maxDistSq) continue;

                Block supportingBlock = nextTarget.getRelative(clickedFace.getOppositeFace());
                if (!matchesSourceBlock(supportingBlock, sourceMaterial, sourceData, matchOpt)) {
                    continue;
                }

                if (!canReplace(nextTarget, replaceOpt)) {
                    continue;
                }

                visited.add(nextLoc);
                queue.add(nextTarget);
            }
        }

        return result;
    }

    /**
     * Retorna os vetores [dx, dy, dz] permitidos no plano de construção de acordo com a face clicada e a Restrição (Lock).
     */
    private List<int[]> getAllowedPlaneOffsets(BlockFace face, LockOption lock) {
        List<int[]> offsets = new ArrayList<>();
        int fx = face.getModX();
        int fy = face.getModY();
        int fz = face.getModZ();

        // Se clicou em UP ou DOWN (plano X-Z)
        if (fy != 0) {
            if (lock == LockOption.HORIZONTAL || lock == LockOption.VERTICAL) {
                addPlaneOffsets(offsets, true, true, true);
            } else if (lock == LockOption.NORTHSOUTH) {
                offsets.add(new int[]{0, 0, 1});
                offsets.add(new int[]{0, 0, -1});
            } else if (lock == LockOption.EASTWEST) {
                offsets.add(new int[]{1, 0, 0});
                offsets.add(new int[]{-1, 0, 0});
            } else {
                // NOLOCK: 8 vizinhos (cardinais + diagonais no plano X-Z)
                addPlaneOffsets(offsets, true, false, true);
            }
            return offsets;
        }

        // Se clicou em NORTH, SOUTH, EAST ou WEST (plano vertical)
        boolean allowHorizontal = (lock == LockOption.NOLOCK || lock == LockOption.HORIZONTAL);
        boolean allowVertical = (lock == LockOption.NOLOCK || lock == LockOption.VERTICAL);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    // Deve ser perpendicular à normal da face clicada
                    if (fx != 0 && dx != 0) continue;
                    if (fz != 0 && dz != 0) continue;

                    if (!allowVertical && dy != 0) continue;
                    if (!allowHorizontal && (dx != 0 || dz != 0)) continue;

                    offsets.add(new int[]{dx, dy, dz});
                }
            }
        }
        return offsets;
    }

    private void addPlaneOffsets(List<int[]> offsets, boolean xAxis, boolean yAxis, boolean zAxis) {
        for (int dx = (xAxis ? -1 : 0); dx <= (xAxis ? 1 : 0); dx++) {
            for (int dy = (yAxis ? -1 : 0); dy <= (yAxis ? 1 : 0); dy++) {
                for (int dz = (zAxis ? -1 : 0); dz <= (zAxis ? 1 : 0); dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    offsets.add(new int[]{dx, dy, dz});
                }
            }
        }
    }

    private boolean matchesSourceBlock(Block candidateSupport, Material sourceMat, BlockData sourceData, MatchOption matchOpt) {
        Material candMat = candidateSupport.getType();
        if (!isPlaceableSolidBlock(candMat)) return false;

        return switch (matchOpt) {
            case ANY -> true;
            case EXACT -> candMat == sourceMat && candidateSupport.getBlockData().matches(sourceData);
            case SIMILAR -> areMaterialsSimilar(sourceMat, candMat);
        };
    }

    private boolean areMaterialsSimilar(Material a, Material b) {
        if (a == b) return true;
        for (Set<Material> group : SIMILAR_GROUPS) {
            if (group.contains(a) && group.contains(b)) {
                return true;
            }
        }
        return false;
    }

    private boolean canReplace(Block block, ReplaceOption replaceOpt) {
        Material type = block.getType();
        if (type.isAir()) return true;
        if (replaceOpt == ReplaceOption.NO) return false;

        return type == Material.WATER
                || type == Material.LAVA
                || type == Material.SHORT_GRASS
                || type == Material.TALL_GRASS
                || type == Material.FERN
                || type == Material.LARGE_FERN
                || type == Material.DEAD_BUSH
                || type == Material.VINE
                || type == Material.SNOW
                || type == Material.SEAGRASS
                || type == Material.TALL_SEAGRASS
                || type == Material.FIRE
                || type == Material.SOUL_FIRE;
    }

    private boolean isPlaceableSolidBlock(Material mat) {
        if (mat == null || mat.isAir() || !mat.isBlock()) return false;
        // Evita duplicar containers/entidades de bloco especiais ou indestrutíveis
        if (mat == Material.BEDROCK || mat == Material.BARRIER || mat == Material.COMMAND_BLOCK
                || mat == Material.SPAWNER || mat == Material.TRIAL_SPAWNER || mat == Material.VAULT
                || mat.name().endsWith("_SHULKER_BOX") || mat == Material.SHULKER_BOX) {
            return false;
        }
        return true;
    }

    private void applyPlayerOrientation(BlockData data, Player player, BlockFace clickedFace) {
        if (data instanceof Directional directional) {
            Set<BlockFace> faces = directional.getFaces();
            BlockFace facing = player.getFacing().getOppositeFace();
            if (faces.contains(facing)) {
                directional.setFacing(facing);
            } else if (faces.contains(clickedFace)) {
                directional.setFacing(clickedFace);
            }
        }
        if (data instanceof Orientable orientable) {
            Axis axis = switch (clickedFace) {
                case UP, DOWN -> Axis.Y;
                case NORTH, SOUTH -> Axis.Z;
                case EAST, WEST -> Axis.X;
                default -> Axis.Y;
            };
            if (orientable.getAxes().contains(axis)) {
                orientable.setAxis(axis);
            }
        }
        if (data instanceof Slab slab) {
            if (clickedFace == BlockFace.DOWN) {
                slab.setType(Slab.Type.TOP);
            } else if (clickedFace == BlockFace.UP) {
                slab.setType(Slab.Type.BOTTOM);
            }
        }
        if (data instanceof Bisected bisected) {
            if (clickedFace == BlockFace.DOWN) {
                bisected.setHalf(Bisected.Half.TOP);
            } else if (clickedFace == BlockFace.UP) {
                bisected.setHalf(Bisected.Half.BOTTOM);
            }
        }
    }

    private List<Material> getHotbarPlaceableMaterials(Player player) {
        List<Material> list = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getAmount() > 0 && isPlaceableSolidBlock(item.getType())) {
                list.add(item.getType());
            }
        }
        return list;
    }

    private boolean hasBlockInSupply(Player player, Material target, MatchOption matchOpt) {
        if (hasExactBlockInSupply(player, target)) return true;
        if (matchOpt == MatchOption.SIMILAR) {
            return findSimilarBlockInSupply(player, target) != null;
        }
        return false;
    }

    private boolean hasExactBlockInSupply(Player player, Material mat) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() == mat && item.getAmount() > 0) return true;
            if (hasBlockInsideShulker(item, mat)) return true;
        }
        return false;
    }

    private Material findSimilarBlockInSupply(Player player, Material target) {
        for (Set<Material> group : SIMILAR_GROUPS) {
            if (group.contains(target)) {
                for (Material candidate : group) {
                    if (hasExactBlockInSupply(player, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean hasBlockInsideShulker(ItemStack item, Material mat) {
        if (item == null || !item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof BlockStateMeta bsm)) return false;
        if (!(bsm.getBlockState() instanceof ShulkerBox shulker)) return false;
        for (ItemStack content : shulker.getInventory().getContents()) {
            if (content != null && content.getType() == mat && content.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeOneBlockFromSupply(Player player, Material mat) {
        PlayerInventory inv = player.getInventory();
        // 1. Tenta consumir da mão secundária primeiro
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == mat && off.getAmount() > 0) {
            off.setAmount(off.getAmount() - 1);
            inv.setItemInOffHand(off.getAmount() > 0 ? off : null);
            return true;
        }
        // 2. Tenta consumir do inventário normal
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == mat && item.getAmount() > 0) {
                item.setAmount(item.getAmount() - 1);
                inv.setItem(i, item.getAmount() > 0 ? item : null);
                return true;
            }
        }
        // 3. Tenta consumir de dentro de Shulker Boxes no inventário (igual ao ContainerShulkerBox do mod!)
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.hasItemMeta() && item.getItemMeta() instanceof BlockStateMeta bsm) {
                if (bsm.getBlockState() instanceof ShulkerBox shulker) {
                    Inventory sInv = shulker.getInventory();
                    ItemStack[] sContents = sInv.getContents();
                    for (int j = 0; j < sContents.length; j++) {
                        ItemStack sItem = sContents[j];
                        if (sItem != null && sItem.getType() == mat && sItem.getAmount() > 0) {
                            sItem.setAmount(sItem.getAmount() - 1);
                            sInv.setItem(j, sItem.getAmount() > 0 ? sItem : null);
                            bsm.setBlockState(shulker);
                            item.setItemMeta(bsm);
                            inv.setItem(i, item);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Partículas de Preview ao segurar a Varinha de Construção (mostra onde os blocos serão colocados).
     */
    private void tickPreviewParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack wand = player.getInventory().getItemInMainHand();
            if (!isWand(wand)) continue;

            RayTraceResult ray = player.rayTraceBlocks(5.0, FluidCollisionMode.NEVER);
            if (ray == null || ray.getHitBlock() == null || ray.getHitBlockFace() == null) continue;

            Block hitBlock = ray.getHitBlock();
            BlockFace hitFace = ray.getHitBlockFace();
            if (!isPlaceableSolidBlock(hitBlock.getType())) continue;

            WandTier tier = getWandTier(wand);
            int rem = getRemainingDurability(wand);
            boolean consumesDur = player.getGameMode() != GameMode.CREATIVE && !tier.unbreakable;
            int maxAllowed = consumesDur ? Math.min(tier.maxBlocks, rem) : tier.maxBlocks;
            // Limita partículas visuais de preview em até 128 pontos para não causar lag visual na Netherite
            int previewLimit = Math.min(maxAllowed, 128);

            List<Block> previewBlocks = findExtensionCandidates(
                    player,
                    hitBlock,
                    hitFace,
                    getLockOption(wand),
                    getReplaceOption(wand),
                    getMatchOption(wand),
                    previewLimit
            );

            Color pColor = switch (tier) {
                case IRON -> Color.fromRGB(100, 220, 255);
                case DIAMOND -> Color.fromRGB(230, 110, 255);
                case NETHERITE -> Color.fromRGB(255, 200, 60);
            };
            Particle.DustOptions dust = new Particle.DustOptions(pColor, 0.65f);
            for (Block b : previewBlocks) {
                player.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 1, 0.15, 0.15, 0.15, 0, dust);
            }
        }
    }

    /**
     * Abre a GUI interativa de opções da Varinha (centralizada com as 5 opções).
     */
    public void openOptionsGui(Player player) {
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!isWand(wand)) {
            player.sendMessage(ChatColor.RED + "Segure sua Varinha de Construção na mão principal!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName(" ");
            glass.setItemMeta(gMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, glass);
        }

        LockOption lock = getLockOption(wand);
        DirectionOption dir = getDirectionOption(wand);
        ReplaceOption rep = getReplaceOption(wand);
        MatchOption match = getMatchOption(wand);
        RandomOption rand = getRandomOption(wand);

        // Centralizado nos slots 11, 12, 13, 14 e 15
        gui.setItem(11, createOptionIcon(lock.icon, ChatColor.GOLD + "Restrição: " + lock.color + lock.label, lock.desc));
        gui.setItem(12, createOptionIcon(dir.icon, ChatColor.GOLD + "Direção: " + dir.color + dir.label, dir.desc));
        gui.setItem(13, createOptionIcon(rep.icon, ChatColor.GOLD + "Substituição: " + rep.color + rep.label, rep.desc));
        gui.setItem(14, createOptionIcon(match.icon, ChatColor.GOLD + "Coincidindo: " + match.color + match.label, match.desc));
        gui.setItem(15, createOptionIcon(rand.icon, ChatColor.GOLD + "Aleatório: " + rand.color + rand.label, rand.desc));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.4f);
    }

    private ItemStack createOptionIcon(Material mat, String title, String description) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + description);
            lore.add("");
            lore.add(ChatColor.YELLOW + "Clique para alternar!");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Suporte garantido ao clique no slot de resultado da Bigorna para reparar Varinha de Ferro/Diamante
        if (event.getInventory().getType() == InventoryType.ANVIL && event.getRawSlot() == 2) {
            AnvilInventory anvil = (AnvilInventory) event.getInventory();
            ItemStack first = anvil.getFirstItem();
            ItemStack second = anvil.getSecondItem();
            ItemStack result = anvil.getResult();

            if (isWand(first) && second != null && isWand(result) && event.getWhoClicked() instanceof Player player) {
                WandTier tier = getWandTier(first);
                if (!tier.unbreakable && tier.repairMaterial != null && second.getType() == tier.repairMaterial) {
                    event.setCancelled(true);
                    int rem = getRemainingDurability(first);
                    int repairPerMaterial = (int) Math.ceil(tier.maxDurability / 2.0);
                    int missing = tier.maxDurability - rem;
                    int materialsUsed = Math.min(second.getAmount(), (int) Math.ceil((double) missing / repairPerMaterial));

                    if (materialsUsed > 0) {
                        ItemStack cursor = event.getCursor();
                        if (cursor != null && !cursor.getType().isAir()) {
                            return;
                        }
                        if (event.isShiftClick()) {
                            if (!player.getInventory().addItem(result.clone()).isEmpty()) {
                                return;
                            }
                        } else {
                            player.setItemOnCursor(result.clone());
                        }

                        anvil.setFirstItem(null);
                        if (second.getAmount() > materialsUsed) {
                            second.setAmount(second.getAmount() - materialsUsed);
                            anvil.setSecondItem(second);
                        } else {
                            anvil.setSecondItem(null);
                        }
                        anvil.setResult(null);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    }
                    return;
                }
            }
        }

        if (!GUI_TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack wand = player.getInventory().getItemInMainHand();
        if (!isWand(wand)) {
            player.closeInventory();
            return;
        }

        int slot = event.getRawSlot();
        ItemMeta meta = wand.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        switch (slot) {
            case 11 -> pdc.set(optLockKey, PersistentDataType.STRING, getLockOption(wand).next().name());
            case 12 -> pdc.set(optDirectionKey, PersistentDataType.STRING, getDirectionOption(wand).next().name());
            case 13 -> pdc.set(optReplaceKey, PersistentDataType.STRING, getReplaceOption(wand).next().name());
            case 14 -> pdc.set(optMatchKey, PersistentDataType.STRING, getMatchOption(wand).next().name());
            case 15 -> pdc.set(optRandomKey, PersistentDataType.STRING, getRandomOption(wand).next().name());
            default -> {
                return;
            }
        }

        wand.setItemMeta(meta);
        updateWandLore(wand);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        openOptionsGui(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Apenas jogadores podem usar este comando.");
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give") || sub.equals("pegar")) {
                if (!player.isOp() && !player.hasPermission("constructionwand.give")) {
                    player.sendMessage(ChatColor.RED + "Sem permissão! Faça o craft na bancada de trabalho.");
                    return true;
                }
                WandTier tier = WandTier.IRON;
                if (args.length >= 2) {
                    String tArg = args[1].toLowerCase(Locale.ROOT);
                    if (tArg.startsWith("dia")) tier = WandTier.DIAMOND;
                    else if (tArg.startsWith("net") || tArg.startsWith("inf")) tier = WandTier.NETHERITE;
                }
                player.getInventory().addItem(createWand(tier));
                player.sendMessage(ChatColor.GREEN + "[Varinha] Você recebeu uma " + tier.color + tier.displayName + ChatColor.GREEN + "!");
                return true;
            }
        }

        openOptionsGui(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(Collections.singletonList("opcoes"));
            if (sender.isOp() || sender.hasPermission("constructionwand.give")) {
                opts.add("give");
            }
            return opts;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("pegar"))) {
            if (sender.isOp() || sender.hasPermission("constructionwand.give")) {
                return Arrays.asList("ferro", "diamante", "netherite");
            }
        }
        return Collections.emptyList();
    }
}
