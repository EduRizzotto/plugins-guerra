package com.eduardo.teams.relic;

import com.eduardo.teams.TeamsPlugin;
import com.eduardo.teams.compass.EventCompassManager;
import com.eduardo.teams.compass.EventCompassManager.CompassEventType;
import com.eduardo.teams.manager.TeamManager;
import com.eduardo.teams.model.Team;
import com.eduardo.teams.util.XaeroWaypointUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gerencia o evento Portador da Reliquia (Relic Bearer):
 * - Spawna a Reliquia no mapa.
 * - Quando um jogador pega, aplica GLOWING continuo e inicia/continua a contagem de 10 minutos.
 * - A cada 30 segundos envia um ping no chat com as coordenadas do portador.
 * - Se o portador morrer (ou quitar), a Reliquia cai no chao indestrutivel para outro jogador pegar.
 * - Quem estiver com a Reliquia ao final dos 10 minutos vence o evento.
 */
public class RelicManager {

    public enum RelicState {
        IDLE,
        WAITING_PICKUP,
        ACTIVE
    }

    public static final long AUTO_RELIC_INTERVAL_TICKS = 5L * 60L * 60L * 20L; // A cada 5 horas
    public static final int RELIC_HOLD_DURATION_SECONDS = 10 * 60; // 10 minutos (600s)
    public static final int PING_INTERVAL_SECONDS = 120; // Ping no chat a cada 2 minutos (a bussola aponta 100% do tempo)
    public static final int WAITING_PICKUP_TIMEOUT_SECONDS = 15 * 60; // 15 min se ninguem pegar do chao inicial
    public static final int MIN_RADIUS = 150;
    public static final int MAX_RADIUS = 700;

    private final TeamsPlugin plugin;
    private final TeamManager teamManager;
    private final NamespacedKey relicKey;
    private EventCompassManager compassManager;

    private RelicState state = RelicState.IDLE;
    private int secondsLeft = 0;
    private int waitingTimeoutSeconds = 0;

    private UUID carrierId;
    private UUID droppedItemEntityId;
    private UUID hologramEntityId;
    private Location lastKnownLocation;

    private BukkitTask tickTask;
    private BukkitTask autoTask;

    public RelicManager(TeamsPlugin plugin, TeamManager teamManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.relicKey = new NamespacedKey(plugin, "war_relic");
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        this.autoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state == RelicState.IDLE && !Bukkit.getOnlinePlayers().isEmpty()) {
                World world = getDefaultOverworld();
                if (world != null) {
                    spawnRandomRelic(world);
                }
            }
        }, AUTO_RELIC_INTERVAL_TICKS, AUTO_RELIC_INTERVAL_TICKS);
    }

    private World getDefaultOverworld() {
        for (World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == World.Environment.NORMAL) {
                return w;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
    }

    public void setCompassManager(EventCompassManager compassManager) {
        this.compassManager = compassManager;
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
        stopEvent(false);
    }

    public RelicState getState() {
        return state;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public boolean isCarrier(UUID playerId) {
        return playerId != null && playerId.equals(carrierId);
    }

    /**
     * Cria o ItemStack da Reliquia da Guerra.
     */
    public ItemStack createRelicItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text("👑 Relíquia da Guerra", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .decoration(TextDecoration.ITALIC, false)
            );
            meta.lore(List.of(
                    Component.text("Segure esta relíquia até o fim dos 10 minutos", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("para conquistar a vitória para o seu time!", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("• ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("Você fica Luminoso (Glowing)", NamedTextColor.YELLOW))
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("• ", NamedTextColor.DARK_GRAY)
                            .append(Component.text("Ping de coordenadas a cada 30s", NamedTextColor.RED))
                            .decoration(TextDecoration.ITALIC, false)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(relicKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isRelicItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(relicKey, PersistentDataType.BYTE);
    }

    /**
     * Spawna a Reliquia em uma coordenada aleatoria segura na superficie do mundo.
     */
    public Location spawnRandomRelic(World world) {
        Location center = world.getSpawnLocation();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location chosen = null;

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = rng.nextDouble(0, Math.PI * 2);
            double dist = rng.nextDouble(MIN_RADIUS, MAX_RADIUS);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

            Block highest = world.getHighestBlockAt(x, z);
            Material surface = highest.getType();
            if (surface == Material.WATER || surface == Material.LAVA || surface.isAir()) {
                continue;
            }
            chosen = highest.getLocation().add(0.5, 1.2, 0.5);
            break;
        }

        if (chosen == null) {
            Block fallback = world.getHighestBlockAt(center.getBlockX() + 150, center.getBlockZ() + 150);
            chosen = fallback.getLocation().add(0.5, 1.2, 0.5);
        }

        spawnRelicAt(chosen);
        return chosen;
    }

    /**
     * Inicia o evento spawnando a Reliquia na posicao informada.
     */
    public void spawnRelicAt(Location location) {
        stopEvent(false);

        this.state = RelicState.WAITING_PICKUP;
        this.secondsLeft = RELIC_HOLD_DURATION_SECONDS;
        this.waitingTimeoutSeconds = WAITING_PICKUP_TIMEOUT_SECONDS;
        this.carrierId = null;
        this.lastKnownLocation = location.clone();

        dropRelicEntityAt(location);

        World world = location.getWorld();
        if (world != null) {
            world.strikeLightningEffect(location);
            world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.RECORDS, 2.5f, 1.0f);
        }

        Component msg = Component.empty()
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 👑 A RELÍQUIA DA GUERRA APARECEU!", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" 📍 Localização: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(formatCoords(location), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text(" ⏳ Objetivo: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text("Pegue a Relíquia e sobreviva com ela por 10 minutos!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.newline())
                .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());

        Bukkit.broadcast(msg);
        XaeroWaypointUtil.broadcastWaypoint("Reliquia", "R", location, XaeroWaypointUtil.COLOR_GOLD);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, SoundCategory.RECORDS, 0.9f, 1.15f);
        }
    }

    /**
     * Dropa a entidade indestrutivel da Reliquia no chao com holograma.
     */
    public void dropRelicEntityAt(Location location) {
        removeDroppedEntities();
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        Location dropLoc = location.clone().add(0, 0.3, 0);
        Item itemEntity = world.dropItem(dropLoc, createRelicItem());
        itemEntity.setVelocity(itemEntity.getVelocity().zero());
        itemEntity.setInvulnerable(true);
        itemEntity.setUnlimitedLifetime(true);
        itemEntity.setGlowing(true);
        itemEntity.setPickupDelay(20); // 1 segundo para nao pegar instantaneamente ao morrer
        itemEntity.customName(Component.text("👑 Relíquia da Guerra", NamedTextColor.GOLD, TextDecoration.BOLD));
        itemEntity.setCustomNameVisible(true);
        this.droppedItemEntityId = itemEntity.getUniqueId();

        // Aplica cor Dourada no Glowing da Reliquia dropada no chao
        var sbManager = Bukkit.getScoreboardManager();
        if (sbManager != null) {
            var sb = sbManager.getMainScoreboard();
            var relicSbTeam = sb.getTeam("tp_relic");
            if (relicSbTeam == null) {
                relicSbTeam = sb.registerNewTeam("tp_relic");
            }
            relicSbTeam.color(NamedTextColor.GOLD);
            relicSbTeam.addEntity(itemEntity);
        }

        Location holoLoc = dropLoc.clone().add(0, 1.1, 0);
        TextDisplay display = world.spawn(holoLoc, TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            td.text(Component.text("👑 RELÍQUIA DA GUERRA", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .append(Component.newline())
                    .append(Component.text("Passe por cima para se tornar o Portador!", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.BOLD, false)));
        });
        this.hologramEntityId = display.getUniqueId();
        this.lastKnownLocation = dropLoc;
    }

    /**
     * Chamado quando um jogador pega a Reliquia do chao.
     */
    public void handleCarrierPickup(Player newCarrier) {
        if (state == RelicState.IDLE || newCarrier == null) {
            return;
        }

        removeHologramEntity();
        this.droppedItemEntityId = null;
        this.carrierId = newCarrier.getUniqueId();
        this.lastKnownLocation = newCarrier.getLocation();

        boolean firstPickup = (state == RelicState.WAITING_PICKUP);
        this.state = RelicState.ACTIVE;

        applyCarrierGlow(newCarrier);

        Title title = Title.title(
                Component.text("👑 VOCÊ É O PORTADOR!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("Sobreviva até o fim dos 10 minutos!", NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(400))
        );
        newCarrier.showTitle(title);

        Team team = teamManager.getPlayerTeam(newCarrier.getUniqueId());
        Component carrierDisplay = formatPlayerWithTeam(newCarrier.getName(), team);
        String timeStr = formatTime(secondsLeft);

        Bukkit.broadcast(
                Component.empty()
                        .append(Component.newline())
                        .append(Component.text("👑 [RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(carrierDisplay)
                        .append(Component.text(firstPickup
                                ? " pegou a Relíquia da Guerra! O cronômetro de 10 minutos começou!"
                                : " pegou a Relíquia da Guerra! (" + timeStr + " restantes)", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.BOLD, false))
                        .append(Component.newline())
                        .append(Component.text(" 📍 Coordenadas atuais: ", NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false))
                        .append(Component.text(formatCoords(newCarrier.getLocation()), NamedTextColor.AQUA, TextDecoration.BOLD))
                        .append(Component.newline())
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.RECORDS, 1.0f, 1.2f);
        }
        XaeroWaypointUtil.broadcastWaypoint("Reliquia-" + newCarrier.getName(), "R", newCarrier.getLocation(), XaeroWaypointUtil.COLOR_YELLOW);
    }

    /**
     * Chamado quando o Portador morre ou desconecta.
     */
    public void handleCarrierLostRelic(Player formerCarrier, Player killer, boolean disconnected) {
        if (state != RelicState.ACTIVE || formerCarrier == null || !isCarrier(formerCarrier.getUniqueId())) {
            return;
        }

        formerCarrier.removePotionEffect(PotionEffectType.GLOWING);
        removeRelicFromInventory(formerCarrier);

        Location dropLoc = formerCarrier.getLocation();
        this.carrierId = null;
        dropRelicEntityAt(dropLoc);

        Team victimTeam = teamManager.getPlayerTeam(formerCarrier.getUniqueId());
        Component victimDisplay = formatPlayerWithTeam(formerCarrier.getName(), victimTeam);

        if (disconnected) {
            Bukkit.broadcast(
                    Component.empty()
                            .append(Component.newline())
                            .append(Component.text("👑 [RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("O portador ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(victimDisplay)
                            .append(Component.text(" desconectou e deixou a Relíquia cair em ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(formatCoords(dropLoc), NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
            );
        } else if (killer != null && !killer.equals(formerCarrier)) {
            Team killerTeam = teamManager.getPlayerTeam(killer.getUniqueId());
            Component killerDisplay = formatPlayerWithTeam(killer.getName(), killerTeam);

            Bukkit.broadcast(
                    Component.empty()
                            .append(Component.newline())
                            .append(Component.text("👑 [RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("O portador ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(victimDisplay)
                            .append(Component.text(" foi abatido por ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(killerDisplay)
                            .append(Component.text("! A Relíquia caiu em ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(formatCoords(dropLoc), NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
            );
        } else {
            Bukkit.broadcast(
                    Component.empty()
                            .append(Component.newline())
                            .append(Component.text("👑 [RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("O portador ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(victimDisplay)
                            .append(Component.text(" morreu! A Relíquia está caída em ", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(formatCoords(dropLoc), NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
            );
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.RECORDS, 0.8f, 1.1f);
        }
        XaeroWaypointUtil.broadcastWaypoint("Reliquia", "R", dropLoc, XaeroWaypointUtil.COLOR_GOLD);
    }

    private void tick() {
        if (state == RelicState.IDLE) {
            return;
        }

        if (state == RelicState.WAITING_PICKUP) {
            waitingTimeoutSeconds--;
            spawnGroundParticles();
            if (compassManager != null && lastKnownLocation != null) {
                compassManager.giveOrUpdateAllPlayers(CompassEventType.RELIC, lastKnownLocation, "Relíquia no Chão");
            }
            if (waitingTimeoutSeconds <= 0) {
                stopEvent(false);
                Bukkit.broadcast(
                        Component.text("👑 [Relíquia] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Ninguém pegou a Relíquia da Guerra a tempo e ela desapareceu!", NamedTextColor.YELLOW)
                                        .decoration(TextDecoration.BOLD, false))
                );
            }
            return;
        }

        if (state == RelicState.ACTIVE) {
            secondsLeft--;

            if (carrierId != null) {
                Player carrier = Bukkit.getPlayer(carrierId);
                if (carrier != null && carrier.isOnline() && !carrier.isDead()) {
                    applyCarrierGlow(carrier);
                    lastKnownLocation = carrier.getLocation();
                    if (compassManager != null) {
                        compassManager.giveOrUpdateAllPlayers(CompassEventType.RELIC, lastKnownLocation, carrier.getName());
                    }
                    carrier.sendActionBar(
                            Component.text("👑 Portador da Relíquia — Tempo restante: ", NamedTextColor.GOLD)
                                    .append(Component.text(formatTime(secondsLeft), NamedTextColor.GREEN, TextDecoration.BOLD))
                    );
                }
            } else {
                spawnGroundParticles();
                ensureDroppedEntityValid();
                if (compassManager != null && lastKnownLocation != null) {
                    compassManager.giveOrUpdateAllPlayers(CompassEventType.RELIC, lastKnownLocation, "Relíquia Caída");
                }
            }

            if (secondsLeft <= 0) {
                finishRelicEvent();
                return;
            }

            // Ping no chat a cada 30 segundos!
            int elapsed = RELIC_HOLD_DURATION_SECONDS - secondsLeft;
            if (elapsed > 0 && elapsed % PING_INTERVAL_SECONDS == 0) {
                broadcastRelicPing();
            }
        }
    }

    /**
     * Envia no chat o ping de 30 em 30 segundos com a coordenada do Portador (ou da Reliquia no chao).
     */
    public void broadcastRelicPing() {
        if (state == RelicState.IDLE) {
            return;
        }

        String timeStr = formatTime(secondsLeft);

        if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier != null && carrier.isOnline()) {
                Team team = teamManager.getPlayerTeam(carrierId);
                Location loc = carrier.getLocation();
                Bukkit.broadcast(
                        Component.text("📡 [PING DA RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                                .append(Component.text("Portador: ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                                .append(formatPlayerWithTeam(carrier.getName(), team))
                                .append(Component.text(" ➔ ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                                .append(Component.text(formatCoords(loc), NamedTextColor.AQUA, TextDecoration.BOLD))
                                .append(Component.text(" (" + timeStr + " restantes)", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                );
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, SoundCategory.RECORDS, 0.9f, 1.2f);
                }
                XaeroWaypointUtil.broadcastWaypoint("Reliquia-" + carrier.getName(), "R", loc, XaeroWaypointUtil.COLOR_YELLOW);
                return;
            }
        }

        if (lastKnownLocation != null) {
            Bukkit.broadcast(
                    Component.text("📡 [PING DA RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD)
                            .append(Component.text("A Relíquia está caída no chão em ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                            .append(Component.text(formatCoords(lastKnownLocation), NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.text("! (" + timeStr + " restantes)", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
            );
            XaeroWaypointUtil.broadcastWaypoint("Reliquia", "R", lastKnownLocation, XaeroWaypointUtil.COLOR_GOLD);
        }
    }

    private void finishRelicEvent() {
        Player winner = carrierId != null ? Bukkit.getPlayer(carrierId) : null;

        if (winner != null && winner.isOnline() && !winner.isDead()) {
            winner.removePotionEffect(PotionEffectType.GLOWING);
            removeRelicFromInventory(winner);

            Team winnerTeam = teamManager.getPlayerTeam(winner.getUniqueId());
            Component winnerDisplay = formatPlayerWithTeam(winner.getName(), winnerTeam);

            Component msg = Component.empty()
                    .append(Component.newline())
                    .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(" 🏆 VITÓRIA NO PORTADOR DA RELÍQUIA!", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.newline())
                    .append(Component.text(" O jogador ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                    .append(winnerDisplay)
                    .append(Component.newline())
                    .append(Component.text(" segurou a Relíquia da Guerra até o fim dos 10 minutos!", NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false))
                    .append(Component.newline())
                    .append(Component.text("========================================", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.newline());

            Bukkit.broadcast(msg);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.RECORDS, 1.0f, 1.0f);
            }

            onRelicWonRewardHook(winner, winnerTeam);
        } else {
            Bukkit.broadcast(
                    Component.empty()
                            .append(Component.newline())
                            .append(Component.text("👑 [RELÍQUIA] ", NamedTextColor.GOLD, TextDecoration.BOLD))
                            .append(Component.text("Os 10 minutos terminaram e ninguém estava segurando a Relíquia no final! O evento encerrou sem vencedor.", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
                            .append(Component.newline())
            );
        }

        stopEvent(false);
    }

    /**
     * Gancho preparado para quando definirmos a pontuacao/recompensa do time vencedor da Reliquia.
     */
    protected void onRelicWonRewardHook(Player winner, Team winnerTeam) {
        if (winnerTeam != null) {
            teamManager.awardTeamPoints(winnerTeam, TeamManager.POINTS_RELIC_WIN, "Vitória no Portador da Relíquia por " + winner.getName());
        }
    }

    public void stopEvent(boolean announceStopped) {
        if (carrierId != null) {
            Player carrier = Bukkit.getPlayer(carrierId);
            if (carrier != null) {
                carrier.removePotionEffect(PotionEffectType.GLOWING);
                removeRelicFromInventory(carrier);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            removeRelicFromInventory(p);
        }

        removeDroppedEntities();
        if (compassManager != null) {
            compassManager.removeCompassFromAllPlayers(CompassEventType.RELIC);
        }
        this.carrierId = null;
        this.state = RelicState.IDLE;
        this.secondsLeft = 0;

        if (announceStopped) {
            Bukkit.broadcast(
                    Component.text("👑 [Relíquia] ", NamedTextColor.RED, TextDecoration.BOLD)
                            .append(Component.text("O evento Portador da Relíquia foi encerrado!", NamedTextColor.YELLOW)
                                    .decoration(TextDecoration.BOLD, false))
            );
        }
    }

    public void applyCarrierGlow(Player carrier) {
        if (carrier != null && carrier.isOnline() && !carrier.isDead()) {
            carrier.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));
        }
    }

    public void removeRelicFromInventory(Player player) {
        if (player == null) {
            return;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isRelicItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
        if (isRelicItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void spawnGroundParticles() {
        if (lastKnownLocation == null || lastKnownLocation.getWorld() == null) {
            return;
        }
        World w = lastKnownLocation.getWorld();
        if (!w.isChunkLoaded(lastKnownLocation.getBlockX() >> 4, lastKnownLocation.getBlockZ() >> 4)) {
            return;
        }
        w.spawnParticle(
                Particle.END_ROD,
                lastKnownLocation.clone().add(0, 0.5, 0),
                6, 0.25, 0.4, 0.25, 0.02
        );
    }

    private void ensureDroppedEntityValid() {
        if (lastKnownLocation == null || lastKnownLocation.getWorld() == null) {
            return;
        }
        World w = lastKnownLocation.getWorld();
        if (!w.isChunkLoaded(lastKnownLocation.getBlockX() >> 4, lastKnownLocation.getBlockZ() >> 4)) {
            return;
        }
        if (droppedItemEntityId == null) {
            dropRelicEntityAt(lastKnownLocation);
            return;
        }
        Entity ent = Bukkit.getEntity(droppedItemEntityId);
        if (ent == null || !ent.isValid()) {
            dropRelicEntityAt(lastKnownLocation);
        } else {
            lastKnownLocation = ent.getLocation();
        }
    }

    private void removeDroppedEntities() {
        if (droppedItemEntityId != null) {
            Entity itemEnt = Bukkit.getEntity(droppedItemEntityId);
            if (itemEnt != null) {
                itemEnt.remove();
            }
            droppedItemEntityId = null;
        }
        removeHologramEntity();
    }

    private void removeHologramEntity() {
        if (hologramEntityId != null) {
            Entity holoEnt = Bukkit.getEntity(hologramEntityId);
            if (holoEnt != null) {
                holoEnt.remove();
            }
            hologramEntityId = null;
        }
    }

    private Component formatPlayerWithTeam(String playerName, Team team) {
        if (team != null) {
            return Component.text("[" + team.getTag() + "] ", team.getColor(), TextDecoration.BOLD)
                    .append(Component.text(playerName, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
        }
        return Component.text(playerName, NamedTextColor.WHITE);
    }

    public static String formatCoords(Location loc) {
        String dim = loc.getWorld() != null ? switch (loc.getWorld().getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "The End";
            default -> loc.getWorld().getName();
        } : "Mundo";
        return "X: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + " (" + dim + ")";
    }

    public static String formatTime(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }
}
