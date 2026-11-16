package com.eduardo.teams.horn;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Gerencia a criacao, receita de craft e controle de usos (durabilidade) do Berrante Localizador.
 */
public class TrackerHornManager {

    public static final int MAX_USES = 10;
    public static final double SEARCH_RADIUS = 150.0;
    public static final int GLOW_DURATION_SECONDS = 20;
    public static final int COOLDOWN_TICKS = 140; // 7 segundos (padrao do Goat Horn)

    private final TeamsPlugin plugin;
    private final NamespacedKey hornTagKey;
    private final NamespacedKey hornUsesKey;
    private final NamespacedKey recipeKey;

    public TrackerHornManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.hornTagKey = new NamespacedKey(plugin, "tracker_horn");
        this.hornUsesKey = new NamespacedKey(plugin, "tracker_horn_uses");
        this.recipeKey = new NamespacedKey(plugin, "tracker_horn_recipe");
    }

    /**
     * Registra a receita de craft 3x3 na bancada de trabalho:
     * [Bloco de Ferro] [Bloco de Diamante] [Bloco de Ferro]
     * [Diamante]       [Chifre de Cabra]   [Diamante]
     * [Bloco de Ferro] [Bloco de Diamante] [Bloco de Ferro]
     */
    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createHornItem(MAX_USES));
        recipe.shape(
                "IBI",
                "DHD",
                "IBI"
        );
        recipe.setIngredient('I', Material.IRON_BLOCK);
        recipe.setIngredient('B', Material.DIAMOND_BLOCK);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('H', Material.GOAT_HORN);

        Bukkit.addRecipe(recipe);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.discoverRecipe(recipeKey);
        }
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    public NamespacedKey getRecipeKey() {
        return recipeKey;
    }

    /**
     * Cria um novo Berrante Localizador com a quantidade de usos informada.
     */
    public ItemStack createHornItem(int usesLeft) {
        int clampedUses = Math.max(1, Math.min(MAX_USES, usesLeft));
        ItemStack item = new ItemStack(Material.GOAT_HORN, 1);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (meta instanceof MusicInstrumentMeta instrumentMeta) {
                instrumentMeta.setInstrument(MusicInstrument.SEEK_GOAT_HORN);
            }

            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(hornTagKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(hornUsesKey, PersistentDataType.INTEGER, clampedUses);

            applyDisplayAndLore(meta, clampedUses);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Verifica se o ItemStack e um Berrante Localizador do plugin.
     */
    public boolean isTrackerHorn(ItemStack item) {
        if (item == null || item.getType() != Material.GOAT_HORN || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(hornTagKey, PersistentDataType.BYTE);
    }

    /**
     * Retorna quantos usos restam no berrante.
     */
    public int getUsesLeft(ItemStack item) {
        if (!isTrackerHorn(item)) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer uses = meta.getPersistentDataContainer().get(hornUsesKey, PersistentDataType.INTEGER);
        return uses != null ? uses : MAX_USES;
    }

    /**
     * Atualiza a quantidade de usos restantes no ItemStack.
     */
    public void updateUsesLeft(ItemStack item, int usesLeft) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(hornUsesKey, PersistentDataType.INTEGER, usesLeft);
        applyDisplayAndLore(meta, usesLeft);
        item.setItemMeta(meta);
    }

    private void applyDisplayAndLore(ItemMeta meta, int usesLeft) {
        NamedTextColor usesColor = usesLeft > 5
                ? NamedTextColor.GREEN
                : (usesLeft > 2 ? NamedTextColor.YELLOW : NamedTextColor.RED);

        meta.displayName(
                Component.text("Berrante Localizador ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text("(" + usesLeft + "/" + MAX_USES + ")", usesColor)
                                .decoration(TextDecoration.BOLD, false))
                        .decoration(TextDecoration.ITALIC, false)
        );

        meta.lore(List.of(
                Component.text("Toque para revelar jogadores inimigos", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("num raio de " + (int) SEARCH_RADIUS + " blocos por " + GLOW_DURATION_SECONDS + "s.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("• ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Ignora aliados do seu time", NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("• ", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Usos restantes: ", NamedTextColor.WHITE))
                        .append(Component.text(usesLeft + "/" + MAX_USES, usesColor))
                        .decoration(TextDecoration.ITALIC, false)
        ));
    }
}
