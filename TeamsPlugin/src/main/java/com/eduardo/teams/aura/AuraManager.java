package com.eduardo.teams.aura;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gerencia o sistema de Auras de Particulas Coloridas (/aura),
 * onde o ADM pode atribuir uma ou varias cores de aura para cada jogador,
 * e o jogador pode ativar/desativar ou alternar entre as cores liberadas para ele.
 */
public class AuraManager {

    public enum AuraColor {
        VERMELHO(
                "vermelho",
                "Aura Vermelha Carmesim",
                Material.RED_DYE,
                NamedTextColor.RED,
                Color.fromRGB(255, 35, 35)
        ),
        LARANJA(
                "laranja",
                "Aura Laranja Solar",
                Material.ORANGE_DYE,
                NamedTextColor.GOLD,
                Color.fromRGB(255, 140, 0)
        ),
        DOURADO(
                "dourado",
                "Aura Dourada Imperial",
                Material.YELLOW_DYE,
                NamedTextColor.YELLOW,
                Color.fromRGB(255, 225, 35)
        ),
        VERDE(
                "verde",
                "Aura Verde Esmeralda",
                Material.LIME_DYE,
                NamedTextColor.GREEN,
                Color.fromRGB(40, 255, 75)
        ),
        CIANO(
                "ciano",
                "Aura Ciano Glacial",
                Material.CYAN_DYE,
                NamedTextColor.AQUA,
                Color.fromRGB(0, 235, 255)
        ),
        AZUL(
                "azul",
                "Aura Azul Celestial",
                Material.BLUE_DYE,
                NamedTextColor.BLUE,
                Color.fromRGB(45, 95, 255)
        ),
        ROXO(
                "roxo",
                "Aura Roxa Abissal",
                Material.PURPLE_DYE,
                NamedTextColor.DARK_PURPLE,
                Color.fromRGB(165, 35, 255)
        ),
        ROSA(
                "rosa",
                "Aura Rosa Mística",
                Material.PINK_DYE,
                NamedTextColor.LIGHT_PURPLE,
                Color.fromRGB(255, 85, 215)
        ),
        BRANCO(
                "branco",
                "Aura Branca Divina",
                Material.WHITE_DYE,
                NamedTextColor.WHITE,
                Color.fromRGB(250, 250, 255)
        ),
        SOMBRIA(
                "sombria",
                "Aura Negra das Sombras",
                Material.BLACK_DYE,
                NamedTextColor.DARK_GRAY,
                Color.fromRGB(20, 20, 25)
        ),
        ARCOIRIS(
                "arcoiris",
                "Aura Arco-Íris (Cromática)",
                Material.NETHER_STAR,
                NamedTextColor.LIGHT_PURPLE,
                null
        );

        private final String id;
        private final String displayName;
        private final Material icon;
        private final NamedTextColor textColor;
        private final Color bukkitColor;
        private final Particle.DustOptions cachedStrandDust;
        private final Particle.DustOptions cachedFootDust;

        AuraColor(String id, String displayName, Material icon, NamedTextColor textColor, Color bukkitColor) {
            this.id = id;
            this.displayName = displayName;
            this.icon = icon;
            this.textColor = textColor;
            this.bukkitColor = bukkitColor;
            this.cachedStrandDust = bukkitColor != null ? new Particle.DustOptions(bukkitColor, 1.35f) : null;
            this.cachedFootDust = bukkitColor != null ? new Particle.DustOptions(bukkitColor, 1.15f) : null;
        }

        public Particle.DustOptions getCachedStrandDust() {
            return cachedStrandDust;
        }

        public Particle.DustOptions getCachedFootDust() {
            return cachedFootDust;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }

        public NamedTextColor getTextColor() {
            return textColor;
        }

        public Color getBukkitColor() {
            return bukkitColor;
        }

        public boolean isRainbow() {
            return this == ARCOIRIS;
        }

        public static AuraColor fromInput(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            String norm = normalizeSlug(input);
            for (AuraColor c : values()) {
                if (c.id.equalsIgnoreCase(norm) || normalizeSlug(c.displayName).equalsIgnoreCase(norm)) {
                    return c;
                }
            }
            // Aliases extras amigaveis
            return switch (norm) {
                case "red", "vermelha" -> VERMELHO;
                case "orange" -> LARANJA;
                case "amarelo", "amarela", "yellow", "gold", "dourada" -> DOURADO;
                case "green", "lime", "esmeralda" -> VERDE;
                case "aqua", "cyan", "azulclaro" -> CIANO;
                case "blue", "azulescuro" -> AZUL;
                case "purple", "roxa", "violeta" -> ROXO;
                case "pink", "magenta" -> ROSA;
                case "white", "branca" -> BRANCO;
                case "preto", "preta", "negra", "negro", "black", "dark", "sombra" -> SOMBRIA;
                case "rainbow", "colorida", "colorido", "rgb", "cromatica" -> ARCOIRIS;
                default -> null;
            };
        }

        private static String normalizeSlug(String text) {
            String n = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                    .toLowerCase(Locale.ROOT);
            if (n.startsWith("aura_") || n.startsWith("aura ")) {
                n = n.substring(5).trim();
            }
            return n.replace(" ", "").replace("-", "").replace("_", "");
        }
    }

    private final TeamsPlugin plugin;
    private final File storageFile;

    // Jogador (nome em minusculo) -> Conjunto de cores de aura liberadas pelo ADM
    private final Map<String, Set<AuraColor>> unlockedColorsByPlayer = new HashMap<>();
    // Jogador (nome em minusculo) -> Cor de aura atualmente equipada pelo jogador ou definida pelo ADM
    private final Map<String, AuraColor> activeColorByPlayer = new HashMap<>();
    // Jogador (nome em minusculo) -> Se a aura esta visivel/ligada (true por padrao quando tem cor ativa)
    private final Map<String, Boolean> auraEnabledByPlayer = new HashMap<>();
    // Jogador (nome em minusculo) -> Nome com capitalizacao original para exibir na GUI
    private final Map<String, String> knownPlayerNames = new LinkedHashMap<>();

    private BukkitTask particleTask;
    private double globalStep = 0.0;

    public AuraManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "auras.yml");
        loadData();
        syncKnownPlayersFromServer();
        startParticleTask();
    }

    public void shutdown() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        saveData();
    }

    private void startParticleTask() {
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            globalStep += 0.28;
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isValid() || player.isDead()) {
                    continue;
                }
                if (player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()
                        || player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    continue;
                }
                AuraColor active = getActiveAuraColor(player.getName());
                if (active == null || !isAuraEnabled(player.getName())) {
                    continue;
                }
                spawnAuraParticles(player, active, globalStep);
            }
        }, 5L, 5L);
    }

    private void spawnAuraParticles(Player player, AuraColor auraColor, double step) {
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }

        double bx = base.getX();
        double by = base.getY();
        double bz = base.getZ();
        double radius = 0.72;
        // Dupla espiral subindo ao redor do jogador + anel suave na base
        for (int strand = 0; strand < 2; strand++) {
            double angle = step + (strand * Math.PI);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double yOffset = 0.15 + ((Math.sin(step * 0.65 + strand * Math.PI) + 1.0) * 0.85);

            Particle.DustOptions dust;
            if (auraColor.isRainbow()) {
                float hue = (float) (((step * 0.08) + (strand * 0.5)) % 1.0);
                if (hue < 0) {
                    hue += 1.0f;
                }
                int rgb = java.awt.Color.HSBtoRGB(hue, 0.95f, 1.0f);
                Color particleColor = Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                dust = new Particle.DustOptions(particleColor, 1.35f);
            } else {
                dust = auraColor.getCachedStrandDust();
            }

            world.spawnParticle(Particle.DUST, bx + x, by + yOffset, bz + z, 2, 0.05, 0.05, 0.05, 0.0, dust);
        }

        // Anel extra na altura dos pes a cada ciclo
        double footAngle = -step * 1.3;
        double fx = Math.cos(footAngle) * (radius * 0.9);
        double fz = Math.sin(footAngle) * (radius * 0.9);
        Particle.DustOptions footDust;
        if (auraColor.isRainbow()) {
            float hue = (float) (((step * 0.08) + 0.25) % 1.0);
            if (hue < 0) {
                hue += 1.0f;
            }
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.95f, 1.0f);
            Color footColor = Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            footDust = new Particle.DustOptions(footColor, 1.15f);
        } else {
            footDust = auraColor.getCachedFootDust();
        }
        world.spawnParticle(Particle.DUST, bx + fx, by + 0.12, bz + fz, 1, 0.02, 0.02, 0.02, 0.0, footDust);
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

    public List<String> getAllKnownPlayerNamesSorted() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            knownPlayerNames.putIfAbsent(online.getName().toLowerCase(Locale.ROOT), online.getName());
        }
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

    public boolean hasAnyAura(String playerName) {
        if (playerName == null) {
            return false;
        }
        Set<AuraColor> set = unlockedColorsByPlayer.get(playerName.trim().toLowerCase(Locale.ROOT));
        return set != null && !set.isEmpty();
    }

    public boolean hasAuraColorPermission(String playerName, AuraColor color) {
        if (playerName == null || color == null) {
            return false;
        }
        Set<AuraColor> set = unlockedColorsByPlayer.get(playerName.trim().toLowerCase(Locale.ROOT));
        return set != null && set.contains(color);
    }

    public Set<AuraColor> getUnlockedColors(String playerName) {
        if (playerName == null) {
            return Collections.emptySet();
        }
        Set<AuraColor> set = unlockedColorsByPlayer.get(playerName.trim().toLowerCase(Locale.ROOT));
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(set));
    }

    public AuraColor getActiveAuraColor(String playerName) {
        if (playerName == null) {
            return null;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        AuraColor active = activeColorByPlayer.get(key);
        Set<AuraColor> unlocked = unlockedColorsByPlayer.get(key);
        if (unlocked == null || unlocked.isEmpty()) {
            return null;
        }
        if (active != null && unlocked.contains(active)) {
            return active;
        }
        // Se nao tiver cor ativa salva mas tiver cores liberadas, pega a primeira liberada
        for (AuraColor c : AuraColor.values()) {
            if (unlocked.contains(c)) {
                activeColorByPlayer.put(key, c);
                return c;
            }
        }
        return null;
    }

    public boolean isAuraEnabled(String playerName) {
        if (playerName == null) {
            return false;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        if (!hasAnyAura(playerName)) {
            return false;
        }
        return auraEnabledByPlayer.getOrDefault(key, true);
    }

    public void setAuraEnabled(String playerName, boolean enabled) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        knownPlayerNames.putIfAbsent(key, playerName.trim());
        auraEnabledByPlayer.put(key, enabled);
        saveData();
    }

    public boolean toggleAuraEnabled(String playerName) {
        boolean next = !isAuraEnabled(playerName);
        setAuraEnabled(playerName, next);
        return next;
    }

    /**
     * Define e equipa uma cor de aura para o jogador (ja liberando essa cor caso ainda nao esteja liberada).
     */
    public void assignAndActivateColor(String playerName, AuraColor color) {
        if (playerName == null || playerName.isBlank() || color == null) {
            return;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        knownPlayerNames.putIfAbsent(key, playerName.trim());
        Set<AuraColor> set = unlockedColorsByPlayer.computeIfAbsent(key, k -> EnumSet.noneOf(AuraColor.class));
        set.add(color);
        activeColorByPlayer.put(key, color);
        auraEnabledByPlayer.put(key, true);
        saveData();
    }

    /**
     * Libera ou remove uma cor de aura especifica para o jogador.
     */
    public void setAuraColorPermission(String playerName, AuraColor color, boolean allowed) {
        if (playerName == null || playerName.isBlank() || color == null) {
            return;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        knownPlayerNames.putIfAbsent(key, playerName.trim());
        Set<AuraColor> set = unlockedColorsByPlayer.computeIfAbsent(key, k -> EnumSet.noneOf(AuraColor.class));
        if (allowed) {
            set.add(color);
            activeColorByPlayer.put(key, color);
            auraEnabledByPlayer.put(key, true);
        } else {
            set.remove(color);
            if (set.isEmpty()) {
                unlockedColorsByPlayer.remove(key);
                activeColorByPlayer.remove(key);
                auraEnabledByPlayer.remove(key);
            } else if (color == activeColorByPlayer.get(key)) {
                for (AuraColor c : AuraColor.values()) {
                    if (set.contains(c)) {
                        activeColorByPlayer.put(key, c);
                        break;
                    }
                }
            }
        }
        saveData();
    }

    public boolean toggleAuraColorPermission(String playerName, AuraColor color) {
        boolean next = !hasAuraColorPermission(playerName, color);
        setAuraColorPermission(playerName, color, next);
        return next;
    }

    public void setAllAuraColorsPermission(String playerName, boolean allowed) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        String key = playerName.trim().toLowerCase(Locale.ROOT);
        knownPlayerNames.putIfAbsent(key, playerName.trim());
        if (allowed) {
            unlockedColorsByPlayer.put(key, EnumSet.allOf(AuraColor.class));
            activeColorByPlayer.putIfAbsent(key, AuraColor.ARCOIRIS);
            auraEnabledByPlayer.put(key, true);
        } else {
            unlockedColorsByPlayer.remove(key);
            activeColorByPlayer.remove(key);
            auraEnabledByPlayer.remove(key);
        }
        saveData();
    }

    public void loadData() {
        unlockedColorsByPlayer.clear();
        activeColorByPlayer.clear();
        auraEnabledByPlayer.clear();
        knownPlayerNames.clear();

        if (!storageFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
        ConfigurationSection knownSec = config.getConfigurationSection("known-players");
        if (knownSec != null) {
            for (String key : knownSec.getKeys(false)) {
                String display = knownSec.getString(key, key);
                knownPlayerNames.put(key.toLowerCase(Locale.ROOT), display);
            }
        }

        ConfigurationSection playersSec = config.getConfigurationSection("players");
        if (playersSec != null) {
            for (String key : playersSec.getKeys(false)) {
                String lower = key.toLowerCase(Locale.ROOT);
                ConfigurationSection pSec = playersSec.getConfigurationSection(key);
                if (pSec == null) {
                    continue;
                }
                List<String> unlockedList = pSec.getStringList("unlocked");
                Set<AuraColor> set = EnumSet.noneOf(AuraColor.class);
                for (String cId : unlockedList) {
                    AuraColor c = AuraColor.fromInput(cId);
                    if (c != null) {
                        set.add(c);
                    }
                }
                if (!set.isEmpty()) {
                    unlockedColorsByPlayer.put(lower, set);
                    knownPlayerNames.putIfAbsent(lower, key);
                }

                String activeId = pSec.getString("active");
                AuraColor active = AuraColor.fromInput(activeId);
                if (active != null && set.contains(active)) {
                    activeColorByPlayer.put(lower, active);
                }

                boolean enabled = pSec.getBoolean("enabled", true);
                auraEnabledByPlayer.put(lower, enabled);
            }
        }
    }

    public void saveData() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, String> entry : knownPlayerNames.entrySet()) {
            config.set("known-players." + entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Set<AuraColor>> entry : unlockedColorsByPlayer.entrySet()) {
            String key = entry.getKey();
            Set<AuraColor> set = entry.getValue();
            if (set == null || set.isEmpty()) {
                continue;
            }
            List<String> ids = set.stream().map(AuraColor::getId).toList();
            config.set("players." + key + ".unlocked", ids);
            AuraColor active = activeColorByPlayer.get(key);
            if (active != null) {
                config.set("players." + key + ".active", active.getId());
            }
            config.set("players." + key + ".enabled", auraEnabledByPlayer.getOrDefault(key, true));
        }

        try {
            config.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erro ao salvar auras.yml: " + e.getMessage());
        }
    }
}
