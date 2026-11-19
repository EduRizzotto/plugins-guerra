package com.eduardo.teams.kit;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Gerencia os 6 Kits Oficiais do Servidor, permissoes individuais por jogador (/kit permission)
 * e controle de resgate diario (1x por dia por kit liberado).
 */
public class KitManager {

    public enum KitType {
        RANGO(
                "rango",
                "Kit Rango",
                "R$ 04,99",
                Material.COOKED_BEEF,
                NamedTextColor.GOLD,
                List.of(
                        "15x Bife Assado",
                        "3x Maçã Dourada",
                        "10x Cenoura",
                        "10x Batata"
                )
        ),
        SOBREVIVENCIA(
                "sobrevivencia",
                "Kit Sobrevivência",
                "R$ 07,99",
                Material.IRON_SWORD,
                NamedTextColor.GREEN,
                List.of(
                        "1x Capacete de Ferro",
                        "1x Peitoral Cota de Malha",
                        "1x Calça de Ferro",
                        "1x Bota Cota de Malha",
                        "1x Espada de Ferro (Afiação I)",
                        "1x Cama",
                        "4x Pérolas do Ender"
                )
        ),
        MINERADOR(
                "minerador",
                "Kit Minerador",
                "R$ 07,99",
                Material.IRON_PICKAXE,
                NamedTextColor.AQUA,
                List.of(
                        "1x Picareta Ferro (Efic. I, Inq. I)",
                        "1x Pá de Ferro (Efic. I, Inq. I)",
                        "1x Machado de Ferro (Efic. I)",
                        "32x Tochas",
                        "5x Bifes Assados",
                        "1x Balde de Água",
                        "1x Poção Visão Noturna (8:00)"
                )
        ),
        ALQUIMISTA(
                "alquimista",
                "Kit Alquimista",
                "R$ 07,99",
                Material.BREWING_STAND,
                NamedTextColor.LIGHT_PURPLE,
                List.of(
                        "1x Suporte de Poções",
                        "2x Poção Arrem. Dano Inst. I",
                        "2x Poção Arrem. Veneno I",
                        "2x Poção Arrem. Cura Inst. II"
                )
        ),
        ARQUEIRO(
                "arqueiro",
                "Kit Arqueiro",
                "R$ 07,99",
                Material.BOW,
                NamedTextColor.YELLOW,
                List.of(
                        "1x Arco (Força I)",
                        "24x Flechas",
                        "8x Flechas de Veneno",
                        "8x Flechas Espectrais"
                )
        ),
        CONSTRUTOR(
                "construtor",
                "Kit Construtor",
                "R$ 06,99",
                Material.STONE_BRICKS,
                NamedTextColor.BLUE,
                List.of(
                        "1x Varinha Construção (Ferro)",
                        "64x Tijolos de Pedra",
                        "32x Troncos de Carvalho",
                        "16x Vidros",
                        "8x Baús"
                )
        );

        private final String id;
        private final String displayName;
        private final String priceLabel;
        private final Material icon;
        private final NamedTextColor color;
        private final List<String> itemDescriptions;

        KitType(String id, String displayName, String priceLabel, Material icon, NamedTextColor color, List<String> itemDescriptions) {
            this.id = id;
            this.displayName = displayName;
            this.priceLabel = priceLabel;
            this.icon = icon;
            this.color = color;
            this.itemDescriptions = itemDescriptions;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getPriceLabel() {
            return priceLabel;
        }

        public Material getIcon() {
            return icon;
        }

        public NamedTextColor getColor() {
            return color;
        }

        public List<String> getItemDescriptions() {
            return itemDescriptions;
        }

        public static KitType fromInput(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            String normalized = normalizeSlug(input);
            for (KitType type : values()) {
                if (type.id.equalsIgnoreCase(normalized)
                        || normalizeSlug(type.displayName).equalsIgnoreCase(normalized)) {
                    return type;
                }
            }
            return null;
        }

        private static String normalizeSlug(String text) {
            String n = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                    .toLowerCase(Locale.ROOT);
            if (n.startsWith("kit_") || n.startsWith("kit ")) {
                n = n.substring(4).trim();
            }
            return n.replace(" ", "");
        }
    }

    private final TeamsPlugin plugin;
    private final File storageFile;

    // Jogador (nome em minusculo) -> Conjunto de Kits liberados pelo ADM
    private final Map<String, Set<KitType>> unlockedKitsByPlayer = new HashMap<>();
    // Jogador (nome em minusculo) -> Nome com capitalizacao original para exibir na GUI
    private final Map<String, String> knownPlayerNames = new LinkedHashMap<>();
    // Jogador (nome em minusculo) -> (KitType -> Data do ultimo resgate YYYY-MM-DD)
    private final Map<String, Map<KitType, String>> lastClaimDateByPlayer = new HashMap<>();

    public KitManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "kits.yml");
        loadData();
        syncKnownPlayersFromServer();
    }

    public void recordPlayerSeen(Player player) {
        if (player == null) {
            return;
        }
        String key = player.getName().toLowerCase(Locale.ROOT);
        if (!player.getName().equals(knownPlayerNames.get(key))) {
            knownPlayerNames.put(key, player.getName());
            saveData();
        }
    }

    public void syncKnownPlayersFromServer() {
        boolean changed = false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            String key = online.getName().toLowerCase(Locale.ROOT);
            if (!knownPlayerNames.containsKey(key)) {
                knownPlayerNames.put(key, online.getName());
                changed = true;
            }
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && !name.isBlank()) {
                String key = name.toLowerCase(Locale.ROOT);
                if (!knownPlayerNames.containsKey(key)) {
                    knownPlayerNames.put(key, name);
                    changed = true;
                }
            }
        }
        if (changed) {
            saveData();
        }
    }

    /**
     * Retorna a lista de todos os jogadores que ja estiveram no servidor (online primeiro, depois ordem alfabetica).
     */
    public List<String> getAllKnownPlayerNamesSorted() {
        syncKnownPlayersFromServer();
        List<String> all = new ArrayList<>(knownPlayerNames.values());
        all.sort((a, b) -> {
            boolean aOnline = Bukkit.getPlayerExact(a) != null;
            boolean bOnline = Bukkit.getPlayerExact(b) != null;
            if (aOnline != bOnline) {
                return aOnline ? -1 : 1;
            }
            return a.compareToIgnoreCase(b);
        });
        return all;
    }

    public String resolveDisplayName(String playerName) {
        if (playerName == null) {
            return "";
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        Player online = Bukkit.getPlayerExact(playerName.trim());
        if (online != null) {
            knownPlayerNames.put(key, online.getName());
            return online.getName();
        }
        return knownPlayerNames.getOrDefault(key, playerName.trim());
    }

    public boolean hasKitPermission(String playerName, KitType kit) {
        if (playerName == null || kit == null) {
            return false;
        }
        Set<KitType> unlocked = unlockedKitsByPlayer.get(playerName.trim().toLowerCase(Locale.ROOT));
        return unlocked != null && unlocked.contains(kit);
    }

    public Set<KitType> getUnlockedKits(String playerName) {
        if (playerName == null) {
            return Collections.emptySet();
        }
        Set<KitType> unlocked = unlockedKitsByPlayer.get(playerName.trim().toLowerCase(Locale.ROOT));
        if (unlocked == null || unlocked.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(unlocked);
    }

    public void setKitPermission(String playerName, KitType kit, boolean allowed) {
        if (playerName == null || playerName.isBlank() || kit == null) {
            return;
        }
        String clean = playerName.trim();
        String key = clean.toLowerCase(Locale.ROOT);
        knownPlayerNames.putIfAbsent(key, clean);

        Set<KitType> unlocked = unlockedKitsByPlayer.computeIfAbsent(key, k -> EnumSet.noneOf(KitType.class));
        if (allowed) {
            unlocked.add(kit);
            // Ao liberar um kit, limpa qualquer cooldown antigo desse kit para que o jogador possa resgatar na hora
            Map<KitType, String> claims = lastClaimDateByPlayer.get(key);
            if (claims != null) {
                claims.remove(kit);
            }
        } else {
            unlocked.remove(kit);
        }
        saveData();
    }

    public boolean toggleKitPermission(String playerName, KitType kit) {
        boolean newState = !hasKitPermission(playerName, kit);
        setKitPermission(playerName, kit, newState);
        return newState;
    }

    public void setAllKitsPermission(String playerName, boolean allowed) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        String clean = playerName.trim();
        String key = clean.toLowerCase(Locale.ROOT);
        knownPlayerNames.putIfAbsent(key, clean);

        Set<KitType> unlocked = unlockedKitsByPlayer.computeIfAbsent(key, k -> EnumSet.noneOf(KitType.class));
        if (allowed) {
            unlocked.addAll(EnumSet.allOf(KitType.class));
            lastClaimDateByPlayer.remove(key);
        } else {
            unlocked.clear();
        }
        saveData();
    }

    public boolean hasClaimedToday(String playerName, KitType kit) {
        if (playerName == null || kit == null) {
            return false;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        Map<KitType, String> claims = lastClaimDateByPlayer.get(key);
        if (claims == null) {
            return false;
        }
        String today = LocalDate.now().toString();
        return today.equals(claims.get(kit));
    }

    public String getRemainingTimeUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDate.now().plusDays(1).atStartOfDay();
        Duration d = Duration.between(now, midnight);
        long hours = Math.max(0, d.toHours());
        long minutes = Math.max(0, d.toMinutesPart());
        return String.format("%02dh %02dmin", hours, minutes);
    }

    /**
     * Tenta resgatar um kit para o jogador verificando permissao e limite diario (1x por dia).
     */
    public boolean tryClaimKit(Player player, KitType kit) {
        if (player == null || kit == null) {
            return false;
        }

        if (!hasKitPermission(player.getName(), kit)) {
            player.sendMessage(Component.text("🔒 Você não tem permissão para usar o ", NamedTextColor.RED)
                    .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                    .append(Component.text("! Apenas kits aprovados por um administrador podem ser resgatados.", NamedTextColor.RED)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        if (hasClaimedToday(player.getName(), kit)) {
            player.sendMessage(Component.text("⏳ Você já resgatou o ", NamedTextColor.YELLOW)
                    .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                    .append(Component.text(" hoje! Próximo resgate disponível em ", NamedTextColor.YELLOW))
                    .append(Component.text(getRemainingTimeUntilMidnight(), NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(".", NamedTextColor.YELLOW)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        List<ItemStack> items = buildKitItems(kit);
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }

        String key = player.getName().toLowerCase(Locale.ROOT);
        lastClaimDateByPlayer.computeIfAbsent(key, k -> new HashMap<>())
                .put(kit, LocalDate.now().toString());
        saveData();

        player.sendMessage(Component.text("🎁 Você resgatou o ", NamedTextColor.GREEN)
                .append(Component.text(kit.getDisplayName(), kit.getColor(), TextDecoration.BOLD))
                .append(Component.text(" com sucesso!", NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.15f);
        return true;
    }

    /**
     * Constroi a lista exata de itens de cada um dos 6 kits conforme as imagens oficiais.
     */
    public List<ItemStack> buildKitItems(KitType kit) {
        List<ItemStack> items = new ArrayList<>();
        switch (kit) {
            case RANGO -> {
                items.add(new ItemStack(Material.COOKED_BEEF, 15));
                items.add(new ItemStack(Material.GOLDEN_APPLE, 3));
                items.add(new ItemStack(Material.CARROT, 10));
                items.add(new ItemStack(Material.POTATO, 10));
            }
            case SOBREVIVENCIA -> {
                items.add(new ItemStack(Material.IRON_HELMET, 1));
                items.add(createEnchantedItem(Material.CHAINMAIL_CHESTPLATE, Map.of(Enchantment.PROTECTION, 1)));
                items.add(new ItemStack(Material.IRON_LEGGINGS, 1));
                items.add(createEnchantedItem(Material.CHAINMAIL_BOOTS, Map.of(Enchantment.PROTECTION, 1)));
                items.add(createEnchantedItem(Material.IRON_SWORD, Map.of(Enchantment.SHARPNESS, 1)));
                items.add(new ItemStack(Material.RED_BED, 1));
                items.add(new ItemStack(Material.ENDER_PEARL, 4));
            }
            case MINERADOR -> {
                items.add(createEnchantedItem(Material.IRON_PICKAXE, Map.of(
                        Enchantment.EFFICIENCY, 1,
                        Enchantment.UNBREAKING, 1
                )));
                items.add(createEnchantedItem(Material.IRON_SHOVEL, Map.of(
                        Enchantment.EFFICIENCY, 1,
                        Enchantment.UNBREAKING, 1
                )));
                items.add(createEnchantedItem(Material.IRON_AXE, Map.of(
                        Enchantment.EFFICIENCY, 1
                )));
                items.add(new ItemStack(Material.TORCH, 32));
                items.add(new ItemStack(Material.COOKED_BEEF, 5));
                items.add(new ItemStack(Material.WATER_BUCKET, 1));
                items.add(createPotionItem(Material.POTION, 1, PotionType.LONG_NIGHT_VISION));
            }
            case ALQUIMISTA -> {
                items.add(new ItemStack(Material.BREWING_STAND, 1));
                items.add(createPotionItem(Material.SPLASH_POTION, 2, PotionType.HARMING));
                items.add(createPotionItem(Material.SPLASH_POTION, 2, PotionType.POISON));
                items.add(createPotionItem(Material.SPLASH_POTION, 2, PotionType.STRONG_HEALING));
            }
            case ARQUEIRO -> {
                items.add(createEnchantedItem(Material.BOW, Map.of(Enchantment.POWER, 1)));
                items.add(new ItemStack(Material.ARROW, 24));
                items.add(createPotionItem(Material.TIPPED_ARROW, 8, PotionType.POISON));
                items.add(new ItemStack(Material.SPECTRAL_ARROW, 8));
            }
            case CONSTRUTOR -> {
                items.add(createIronConstructionWand());
                items.add(new ItemStack(Material.STONE_BRICKS, 64));
                items.add(new ItemStack(Material.OAK_LOG, 32));
                items.add(new ItemStack(Material.GLASS, 16));
                items.add(new ItemStack(Material.CHEST, 8));
            }
        }
        return items;
    }

    private ItemStack createEnchantedItem(Material material, Map<Enchantment, Integer> enchants) {
        ItemStack stack = new ItemStack(material, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                meta.addEnchant(entry.getKey(), entry.getValue(), true);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createPotionItem(Material material, int amount, PotionType potionType) {
        ItemStack stack = new ItemStack(material, amount);
        if (stack.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(potionType);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Cria a Varinha de Construcao de Ferro 100% compativel com o ConstructionWandPlugin.
     */
    @SuppressWarnings("deprecation")
    private ItemStack createIronConstructionWand() {
        Recipe recipe = Bukkit.getRecipe(new NamespacedKey("constructionwandplugin", "iron_wand_recipe"));
        if (recipe != null && recipe.getResult().getType() != Material.AIR) {
            return recipe.getResult().clone();
        }

        // Fallback direto com as mesmas NamespacedKeys do ConstructionWandPlugin
        String ns = "constructionwandplugin";
        ItemStack wand = new ItemStack(Material.STICK, 1);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Varinha de Construção de Ferro");
            meta.setEnchantmentGlintOverride(true);
            meta.setMaxStackSize(1);
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(250);
                damageable.setDamage(0);
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(ns, "iron_construction_wand"), PersistentDataType.BYTE, (byte) 1);
            pdc.set(new NamespacedKey(ns, "wand_tier"), PersistentDataType.STRING, "IRON");
            pdc.set(new NamespacedKey(ns, "wand_durability"), PersistentDataType.INTEGER, 250);
            pdc.set(new NamespacedKey(ns, "opt_lock"), PersistentDataType.STRING, "NOLOCK");
            pdc.set(new NamespacedKey(ns, "opt_direction"), PersistentDataType.STRING, "TARGET");
            pdc.set(new NamespacedKey(ns, "opt_replace"), PersistentDataType.STRING, "YES");
            pdc.set(new NamespacedKey(ns, "opt_match"), PersistentDataType.STRING, "SIMILAR");
            pdc.set(new NamespacedKey(ns, "opt_random"), PersistentDataType.STRING, "NO");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public void loadData() {
        unlockedKitsByPlayer.clear();
        knownPlayerNames.clear();
        lastClaimDateByPlayer.clear();

        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);

        ConfigurationSection namesSec = yaml.getConfigurationSection("known-players");
        if (namesSec != null) {
            for (String key : namesSec.getKeys(false)) {
                String display = namesSec.getString(key, key);
                knownPlayerNames.put(key.toLowerCase(Locale.ROOT), display);
            }
        }

        ConfigurationSection permsSec = yaml.getConfigurationSection("permissions");
        if (permsSec != null) {
            for (String playerKey : permsSec.getKeys(false)) {
                List<String> kitIds = permsSec.getStringList(playerKey);
                Set<KitType> set = EnumSet.noneOf(KitType.class);
                for (String id : kitIds) {
                    KitType type = KitType.fromInput(id);
                    if (type != null) {
                        set.add(type);
                    }
                }
                unlockedKitsByPlayer.put(playerKey.toLowerCase(Locale.ROOT), set);
                knownPlayerNames.putIfAbsent(playerKey.toLowerCase(Locale.ROOT), playerKey);
            }
        }

        ConfigurationSection claimsSec = yaml.getConfigurationSection("claims");
        if (claimsSec != null) {
            for (String playerKey : claimsSec.getKeys(false)) {
                ConfigurationSection playerClaims = claimsSec.getConfigurationSection(playerKey);
                if (playerClaims == null) continue;
                Map<KitType, String> map = new HashMap<>();
                for (String kitId : playerClaims.getKeys(false)) {
                    KitType type = KitType.fromInput(kitId);
                    String dateStr = playerClaims.getString(kitId);
                    if (type != null && dateStr != null) {
                        map.put(type, dateStr);
                    }
                }
                lastClaimDateByPlayer.put(playerKey.toLowerCase(Locale.ROOT), map);
            }
        }
    }

    public void saveData() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, String> entry : knownPlayerNames.entrySet()) {
            yaml.set("known-players." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Set<KitType>> entry : unlockedKitsByPlayer.entrySet()) {
            List<String> kitIds = entry.getValue().stream().map(KitType::getId).toList();
            yaml.set("permissions." + entry.getKey(), kitIds);
        }

        for (Map.Entry<String, Map<KitType, String>> entry : lastClaimDateByPlayer.entrySet()) {
            String playerKey = entry.getKey();
            for (Map.Entry<KitType, String> claim : entry.getValue().entrySet()) {
                yaml.set("claims." + playerKey + "." + claim.getKey().getId(), claim.getValue());
            }
        }

        try {
            yaml.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar kits.yml: " + e.getMessage());
        }
    }
}
