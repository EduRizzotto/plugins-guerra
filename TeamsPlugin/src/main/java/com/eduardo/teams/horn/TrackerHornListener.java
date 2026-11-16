package com.eduardo.teams.horn;

import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.quest.QuestActionType;
import com.eduardo.teams.quest.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.Event;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Listener responsavel pelo uso do Berrante Localizador, efeito luminoso e protecao da receita.
 */
public class TrackerHornListener implements Listener {

    private final TrackerHornManager hornManager;
    private final TeamManager teamManager;
    private QuestManager questManager;

    public TrackerHornListener(TrackerHornManager hornManager, TeamManager teamManager) {
        this.hornManager = hornManager;
        this.teamManager = teamManager;
    }

    public void setQuestManager(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipe(hornManager.getRecipeKey());
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(hornManager.getRecipeKey())) {
            return;
        }

        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        // Impede que um Berrante Localizador ja existente seja usado no meio para resetar os usos
        if (matrix.length > 4 && hornManager.isTrackerHorn(matrix[4])) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseTrackerHorn(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (!hornManager.isTrackerHorn(item)) {
            return;
        }

        Player player = event.getPlayer();

        // Se clicou num bloco interativo (bau, porta, bancada, etc.) sem estar no shift, deixa abrir o bloco
        if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable()) {
                return;
            }
        }

        // Controlamos o som, cooldown e desgaste diretamente para evitar bugs com atualizacao do ItemMeta
        event.setUseItemInHand(Event.Result.DENY);

        if (player.hasCooldown(Material.GOAT_HORN)) {
            int ticksLeft = player.getCooldown(Material.GOAT_HORN);
            int secondsLeft = Math.max(1, (int) Math.ceil(ticksLeft / 20.0));
            player.sendActionBar(
                    Component.text("Aguarde " + secondsLeft + "s para tocar o berrante novamente!", NamedTextColor.RED)
            );
            return;
        }

        player.setCooldown(Material.GOAT_HORN, TrackerHornManager.COOLDOWN_TICKS);

        Location origin = player.getLocation();
        player.getWorld().playSound(origin, Sound.ITEM_GOAT_HORN_SOUND_1, SoundCategory.RECORDS, 10.0f, 1.0f);

        double maxDistSq = TrackerHornManager.SEARCH_RADIUS * TrackerHornManager.SEARCH_RADIUS;
        int glowTicks = TrackerHornManager.GLOW_DURATION_SECONDS * 20;
        PotionEffect glowEffect = new PotionEffect(PotionEffectType.GLOWING, glowTicks, 0, false, false, true);

        int revealedCount = 0;
        for (Player target : player.getWorld().getPlayers()) {
            if (target.equals(player)) {
                continue;
            }
            if (target.isDead() || target.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (target.getLocation().distanceSquared(origin) > maxDistSq) {
                continue;
            }
            // Ignora jogadores que sao do MESMO time; afeta jogadores de outros times ou sem time
            if (teamManager.isSameTeam(player.getUniqueId(), target.getUniqueId())) {
                continue;
            }

            target.addPotionEffect(glowEffect);
            target.sendActionBar(
                    Component.text("⚠ Um Berrante Localizador revelou sua posição por 20s!", NamedTextColor.RED)
            );
            revealedCount++;
        }

        int usesLeft = hornManager.getUsesLeft(item);
        int nextUses = usesLeft - 1;

        if (questManager != null) {
            questManager.recordAction(player, QuestActionType.SERVER_USE_HORN, null, null, 1);
            if (revealedCount > 0) {
                questManager.recordAction(player, QuestActionType.SERVER_REVEAL_HORN, null, null, revealedCount);
            }
        }

        if (nextUses <= 0) {
            // 10 usos atingidos -> quebra o item
            player.getWorld().playSound(origin, Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            EquipmentSlot hand = event.getHand();
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
                hornManager.updateUsesLeft(item, TrackerHornManager.MAX_USES);
            } else if (hand != null) {
                player.getInventory().setItem(hand, null);
            }

            if (revealedCount > 0) {
                player.sendMessage(
                        Component.text("🎺 Berrante tocado! ", NamedTextColor.GOLD)
                                .append(Component.text(revealedCount + " jogador(es) ", NamedTextColor.YELLOW))
                                .append(Component.text("revelado(s) por 20s num raio de 150 blocos! ", NamedTextColor.GREEN))
                                .append(Component.text("(O seu berrante quebrou! 0/10)", NamedTextColor.RED))
                );
            } else {
                player.sendMessage(
                        Component.text("🎺 Berrante tocado! Nenhum inimigo num raio de 150 blocos. ", NamedTextColor.YELLOW)
                                .append(Component.text("(O seu berrante quebrou! 0/10)", NamedTextColor.RED))
                );
            }
        } else {
            hornManager.updateUsesLeft(item, nextUses);

            if (revealedCount > 0) {
                player.sendMessage(
                        Component.text("🎺 Berrante tocado! ", NamedTextColor.GOLD)
                                .append(Component.text(revealedCount + " jogador(es) ", NamedTextColor.YELLOW))
                                .append(Component.text("revelado(s) por 20s! ", NamedTextColor.GREEN))
                                .append(Component.text("(Usos restantes: " + nextUses + "/" + TrackerHornManager.MAX_USES + ")", NamedTextColor.GRAY))
                );
            } else {
                player.sendMessage(
                        Component.text("🎺 Berrante tocado! Nenhum inimigo encontrado em 150 blocos. ", NamedTextColor.YELLOW)
                                .append(Component.text("(Usos restantes: " + nextUses + "/" + TrackerHornManager.MAX_USES + ")", NamedTextColor.GRAY))
                );
            }
        }
    }
}
