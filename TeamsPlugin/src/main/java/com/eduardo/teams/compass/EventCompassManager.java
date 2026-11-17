package com.eduardo.teams.compass;

import com.eduardo.teams.TeamsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

/**
 * Gerencia as Bussolas Encantadas Temporarias de Eventos (AirDrop, Portador da Reliquia e Cacada):
 * - Aparecem automaticamente no inventario dos jogadores quando o evento comeca.
 * - Apontam 100% do tempo em tempo real para o alvo do evento (via CompassMeta Lodestone).
 * - Somem automaticamente do inventario de todos os jogadores assim que o evento termina.
 */
public class EventCompassManager {

    public enum CompassEventType {
        AIRDROP("airdrop", "🧭 Bússola: AirDrop", NamedTextColor.GOLD),
        RELIC("relic", "🧭 Bússola: Portador da Relíquia", NamedTextColor.YELLOW),
        HUNT("hunt", "🧭 Bússola: Caçada", NamedTextColor.RED);

        private final String id;
        private final String displayTitle;
        private final NamedTextColor color;

        CompassEventType(String id, String displayTitle, NamedTextColor color) {
            this.id = id;
            this.displayTitle = displayTitle;
            this.color = color;
        }

        public String getId() {
            return id;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public NamedTextColor getColor() {
            return color;
        }

        public static CompassEventType fromId(String id) {
            if (id == null) return null;
            for (CompassEventType type : values()) {
                if (type.id.equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final NamespacedKey compassEventKey;

    public EventCompassManager(TeamsPlugin plugin) {
        this.compassEventKey = new NamespacedKey(plugin, "event_compass_type");
    }

    public boolean isEventCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(compassEventKey, PersistentDataType.STRING);
    }

    public CompassEventType getCompassType(ItemStack item) {
        if (!isEventCompass(item)) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String id = meta.getPersistentDataContainer().get(compassEventKey, PersistentDataType.STRING);
        return CompassEventType.fromId(id);
    }

    /**
     * Cria ou atualiza a Bussola Encantada do evento no inventario de um jogador,
     * apontando em tempo real para targetLocation.
     */
    public void giveOrUpdatePlayerCompass(Player player, CompassEventType type, Location targetLocation, String targetLabel) {
        if (player == null || !player.isOnline() || player.isDead() || targetLocation == null) {
            return;
        }

        Location blockTarget = targetLocation.toBlockLocation();
        PlayerInventory inv = player.getInventory();
        int existingSlot = findCompassSlot(inv, type);

        if (existingSlot >= 0) {
            ItemStack existing = inv.getItem(existingSlot);
            if (existing != null && existing.getItemMeta() instanceof CompassMeta cm) {
                Location prev = cm.getLodestone();
                if (prev != null
                        && Objects.equals(prev.getWorld(), blockTarget.getWorld())
                        && prev.getBlockX() == blockTarget.getBlockX()
                        && prev.getBlockY() == blockTarget.getBlockY()
                        && prev.getBlockZ() == blockTarget.getBlockZ()) {
                    return; // Ja esta apontando exatamente para este bloco
                }
            }
            inv.setItem(existingSlot, buildCompassStack(player, type, blockTarget, targetLabel));
        } else {
            ItemStack compass = buildCompassStack(player, type, blockTarget, targetLabel);
            inv.addItem(compass);
        }
    }

    /**
     * Entrega ou atualiza a bussola de um evento para todos os jogadores online apontando para o mesmo local.
     */
    public void giveOrUpdateAllPlayers(CompassEventType type, Location targetLocation, String targetLabel) {
        if (targetLocation == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveOrUpdatePlayerCompass(player, type, targetLocation, targetLabel);
        }
    }

    /**
     * Remove a bussola de um tipo especifico de evento do inventario de um jogador.
     */
    public void removeCompassFromPlayer(Player player, CompassEventType type) {
        if (player == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (getCompassType(item) == type) {
                inv.setItem(i, null);
            }
        }
        if (getCompassType(player.getItemOnCursor()) == type) {
            player.setItemOnCursor(null);
        }
    }

    /**
     * Remove todas as bussolas de um tipo especifico de evento de todos os jogadores online.
     */
    public void removeCompassFromAllPlayers(CompassEventType type) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeCompassFromPlayer(player, type);
        }
    }

    /**
     * Remove todas as bussolas de eventos de todos os jogadores online (no shutdown do plugin).
     */
    public void removeAllEventCompassesFromOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (isEventCompass(inv.getItem(i))) {
                    inv.setItem(i, null);
                }
            }
            if (isEventCompass(player.getItemOnCursor())) {
                player.setItemOnCursor(null);
            }
        }
    }

    private int findCompassSlot(PlayerInventory inv, CompassEventType type) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (getCompassType(inv.getItem(i)) == type) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack buildCompassStack(Player holder, CompassEventType type, Location targetLoc, String targetLabel) {
        ItemStack stack = new ItemStack(Material.COMPASS, 1);
        if (stack.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(targetLoc);
            meta.setLodestoneTracked(false);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(compassEventKey, PersistentDataType.STRING, type.getId());

            String subtitle = (targetLabel != null && !targetLabel.isBlank()) ? " (" + targetLabel + ")" : "";
            meta.displayName(
                    Component.text(type.getDisplayTitle(), type.getColor(), TextDecoration.BOLD)
                            .append(Component.text(subtitle, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                            .decoration(TextDecoration.ITALIC, false)
            );

            String coords = "X: " + targetLoc.getBlockX() + ", Y: " + targetLoc.getBlockY() + ", Z: " + targetLoc.getBlockZ();
            String distStr = (holder.getWorld().equals(targetLoc.getWorld()))
                    ? ((int) Math.round(holder.getLocation().distance(targetLoc)) + " blocos")
                    : "Outra dimensão";

            meta.lore(List.of(
                    Component.text("Aponta em tempo real para o evento ativo!", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("• Alvo: ", NamedTextColor.GRAY)
                            .append(Component.text(targetLabel != null ? targetLabel : type.getDisplayTitle(), NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("• Coordenadas: ", NamedTextColor.GRAY)
                            .append(Component.text(coords, NamedTextColor.AQUA))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("• Distância: ", NamedTextColor.GRAY)
                            .append(Component.text(distStr, NamedTextColor.GREEN))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("Desaparece quando o evento terminar.", NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            stack.setItemMeta(meta);
        }
        return stack;
    }
}
