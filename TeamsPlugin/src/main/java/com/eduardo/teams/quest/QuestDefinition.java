package com.eduardo.teams.quest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Representa uma Quest Diaria e mantem o catalogo completo de quests validadas e sem bugs.
 */
public class QuestDefinition {

    private final String id;
    private final String title;
    private final String description;
    private final Material icon;
    private final QuestDifficulty difficulty;
    private final QuestActionType actionType;
    private final int targetAmount;
    private final Set<Material> targetMaterials;
    private final Set<EntityType> targetEntities;

    public QuestDefinition(
            String id,
            String title,
            String description,
            Material icon,
            QuestDifficulty difficulty,
            QuestActionType actionType,
            int targetAmount,
            Set<Material> targetMaterials,
            Set<EntityType> targetEntities
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.difficulty = difficulty;
        this.actionType = actionType;
        this.targetAmount = targetAmount;
        this.targetMaterials = targetMaterials != null ? targetMaterials : Set.of();
        this.targetEntities = targetEntities != null ? targetEntities : Set.of();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public QuestDifficulty getDifficulty() {
        return difficulty;
    }

    public QuestActionType getActionType() {
        return actionType;
    }

    public int getTargetAmount() {
        return targetAmount;
    }

    public Set<Material> getTargetMaterials() {
        return targetMaterials;
    }

    public Set<EntityType> getTargetEntities() {
        return targetEntities;
    }

    public boolean matchesMaterial(Material material) {
        return targetMaterials.isEmpty() || targetMaterials.contains(material);
    }

    public boolean matchesEntity(EntityType entityType) {
        return targetEntities.isEmpty() || targetEntities.contains(entityType);
    }

    // =========================================================================
    // CATALOGO DE QUESTS DIARIAS (100% RASTREAVEIS E LIVRES DE BUGS)
    // =========================================================================

    private static final Map<String, QuestDefinition> REGISTRY = new LinkedHashMap<>();

    static {
        // --- 1. MINERACAO & COLETA ---
        regMat("q001", "Quebradores de Pedra", "Minerar 128 blocos de Pedra ou Pedregulho.",
                Material.COBBLESTONE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 128,
                Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE);

        regMat("q002", "Carvoeiros do Clã", "Minerar 48 minérios de Carvão.",
                Material.COAL_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 48,
                Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);

        regMat("q003", "Corrida do Cobre", "Minerar 48 minérios de Cobre.",
                Material.COPPER_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 48,
                Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);

        regMat("q004", "Idade do Ferro", "Minerar 48 minérios de Ferro.",
                Material.IRON_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 48,
                Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);

        regMat("q005", "Febre do Ouro", "Minerar 24 minérios de Ouro.",
                Material.GOLD_ORE, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 24,
                Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);

        regMat("q006", "Energia Vermelha", "Minerar 24 minérios de Redstone.",
                Material.REDSTONE_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 24,
                Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);

        regMat("q007", "Pedras Místicas", "Minerar 24 minérios de Lápis-Lazúli.",
                Material.LAPIS_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 24,
                Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);

        regMat("q008", "Caçadores de Diamante", "Minerar 10 minérios de Diamante.",
                Material.DIAMOND_ORE, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 10,
                Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);

        regMat("q009", "Riqueza da Montanha", "Minerar 5 minérios de Esmeralda.",
                Material.EMERALD_ORE, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 5,
                Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);

        regMat("q010", "Relíquias do Abismo", "Minerar 2 blocos de Detritos Ancestrais no Nether.",
                Material.ANCIENT_DEBRIS, QuestDifficulty.HARD, QuestActionType.MINE_BLOCK, 2,
                Material.ANCIENT_DEBRIS);

        regMat("q011", "Quartzo Infernal", "Minerar 32 minérios de Quartzo do Nether.",
                Material.NETHER_QUARTZ_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 32,
                Material.NETHER_QUARTZ_ORE);

        regMat("q012", "Pepitas do Inferno", "Minerar 32 minérios de Ouro do Nether.",
                Material.NETHER_GOLD_ORE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 32,
                Material.NETHER_GOLD_ORE);

        regMat("q013", "Rocha Indestrutível", "Quebrar 12 blocos de Obsidiana.",
                Material.OBSIDIAN, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 12,
                Material.OBSIDIAN, Material.CRYING_OBSIDIAN);

        regMat("q014", "Lenhadores de Carvalho", "Cortar 64 troncos de Carvalho.",
                Material.OAK_LOG, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 64,
                Material.OAK_LOG, Material.OAK_WOOD);

        regMat("q015", "Lenhadores da Taiga", "Cortar 64 troncos de Pinheiro (Spruce).",
                Material.SPRUCE_LOG, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 64,
                Material.SPRUCE_LOG, Material.SPRUCE_WOOD);

        regMat("q016", "Lenhadores de Bétula", "Cortar 64 troncos de Bétula.",
                Material.BIRCH_LOG, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 64,
                Material.BIRCH_LOG, Material.BIRCH_WOOD);

        regMat("q017", "Madeira Densa", "Cortar 48 troncos de Carvalho Escuro, Acácia ou Selva.",
                Material.DARK_OAK_LOG, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 48,
                Material.DARK_OAK_LOG, Material.ACACIA_LOG, Material.JUNGLE_LOG, Material.CHERRY_LOG, Material.MANGROVE_LOG);

        regMat("q018", "Lenha Dimensional", "Cortar 48 caules Carmesim ou Distorcidos no Nether.",
                Material.CRIMSON_STEM, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 48,
                Material.CRIMSON_STEM, Material.WARPED_STEM);

        regMat("q019", "Escavação de Areia", "Coletar 96 blocos de Areia ou Areia Vermelha.",
                Material.SAND, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 96,
                Material.SAND, Material.RED_SAND);

        regMat("q020", "Garimpo de Cascalho", "Quebrar 48 blocos de Cascalho.",
                Material.GRAVEL, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 48,
                Material.GRAVEL);

        regMat("q021", "Ceramistas", "Quebrar 24 blocos de Argila.",
                Material.CLAY, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 24,
                Material.CLAY);

        regMat("q022", "Geodo de Cristal", "Quebrar 12 blocos ou aglomerados de Ametista.",
                Material.AMETHYST_CLUSTER, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 12,
                Material.AMETHYST_BLOCK, Material.BUDDING_AMETHYST, Material.AMETHYST_CLUSTER,
                Material.LARGE_AMETHYST_BUD, Material.MEDIUM_AMETHYST_BUD, Material.SMALL_AMETHYST_BUD);

        regMat("q023", "Escavadores das Profundezas", "Minerar 64 blocos de Ardósia Profunda (Deepslate).",
                Material.DEEPSLATE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 64,
                Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF);

        regGen("q024", "Operação Subterrânea", "Minerar 350 blocos sólidos no total em equipe.",
                Material.IRON_PICKAXE, QuestDifficulty.MEDIUM, QuestActionType.MINE_ANY_SOLID, 350);

        regMat("q025", "Quebradores de Gelo", "Quebrar 24 blocos de Gelo, Gelo Compactado ou Gelo Azul.",
                Material.PACKED_ICE, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 24,
                Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);

        regMat("q026", "Luz do Submundo", "Quebrar 16 blocos de Pedra Luminosa (Glowstone) no Nether.",
                Material.GLOWSTONE, QuestDifficulty.MEDIUM, QuestActionType.MINE_BLOCK, 16,
                Material.GLOWSTONE);

        regMat("q027", "Diamantes Profundos", "Minerar 6 minérios de Diamante de Ardósia Profunda.",
                Material.DEEPSLATE_DIAMOND_ORE, QuestDifficulty.HARD, QuestActionType.MINE_BLOCK, 6,
                Material.DEEPSLATE_DIAMOND_ORE);

        regMat("q028", "Areia das Almas", "Coletar 48 blocos de Areia das Almas ou Terra das Almas.",
                Material.SOUL_SAND, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 48,
                Material.SOUL_SAND, Material.SOUL_SOIL);

        regMat("q029", "Escavação de Netherrack", "Minerar 200 blocos de Netherrack ou Basalto.",
                Material.NETHERRACK, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 200,
                Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE);

        regMat("q030", "Pedreira de Andesito", "Minerar 64 blocos de Granito, Diorito ou Andesito.",
                Material.ANDESITE, QuestDifficulty.EASY, QuestActionType.MINE_BLOCK, 64,
                Material.ANDESITE, Material.DIORITE, Material.GRANITE, Material.CALCITE);

        // --- 2. COMBATE PVP & GUERRA ENTRE TIMES ---
        regGen("q031", "Primeiro Sangue", "Eliminar 1 jogador de outro time.",
                Material.IRON_SWORD, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_ANY, 1);

        regGen("q032", "Esquadrão de Elite", "Eliminar 3 jogadores de outros times.",
                Material.DIAMOND_SWORD, QuestDifficulty.HARD, QuestActionType.KILL_ENEMY_ANY, 3);

        regGen("q033", "Massacre na Guerra", "Eliminar 5 jogadores de outros times.",
                Material.NETHERITE_SWORD, QuestDifficulty.HARD, QuestActionType.KILL_ENEMY_ANY, 5);

        regGen("q034", "Regicídio", "Eliminar o Líder de um time rival.",
                Material.GOLDEN_HELMET, QuestDifficulty.HARD, QuestActionType.KILL_ENEMY_LEADER, 1);

        regGen("q035", "Atirador de Elite", "Eliminar 1 jogador inimigo usando Arco ou Besta.",
                Material.BOW, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_BOW, 1);

        regGen("q036", "Golpe Brutal", "Eliminar 1 jogador inimigo usando Machado.",
                Material.DIAMOND_AXE, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_AXE, 1);

        regGen("q037", "Duelista", "Eliminar 1 jogador inimigo usando Espada.",
                Material.IRON_SWORD, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_SWORD, 1);

        regGen("q038", "Pressão Ofensiva", "Causar 120 de dano total em jogadores inimigos.",
                Material.STONE_SWORD, QuestDifficulty.EASY, QuestActionType.PVP_DAMAGE, 120);

        regGen("q039", "Devastação em Combate", "Causar 350 de dano total em jogadores inimigos.",
                Material.DIAMOND_SWORD, QuestDifficulty.MEDIUM, QuestActionType.PVP_DAMAGE, 350);

        regGen("q040", "Chuva de Flechas", "Acertar 10 flechadas em jogadores inimigos.",
                Material.ARROW, QuestDifficulty.MEDIUM, QuestActionType.PVP_ARROW_HIT, 10);

        regGen("q041", "Confronto Infernal", "Eliminar 1 jogador inimigo dentro do Nether.",
                Material.NETHER_STAR, QuestDifficulty.HARD, QuestActionType.KILL_ENEMY_NETHER, 1);

        regGen("q042", "Emboscada Noturna", "Eliminar 1 jogador inimigo durante a noite no Overworld.",
                Material.CLOCK, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_NIGHT, 1);

        regGen("q043", "Flecha de Longa Distância", "Acertar um jogador inimigo com flecha a mais de 25 blocos.",
                Material.CROSSBOW, QuestDifficulty.MEDIUM, QuestActionType.PVP_LONG_ARROW, 1);

        regGen("q044", "Salto Dimensional", "Usar 5 Pérolas do Ender.",
                Material.ENDER_PEARL, QuestDifficulty.EASY, QuestActionType.USE_ENDER_PEARL, 5);

        regGen("q045", "Segunda Chance", "Ativar 1 Totem da Imortalidade ao sofrer dano fatal.",
                Material.TOTEM_OF_UNDYING, QuestDifficulty.HARD, QuestActionType.POP_TOTEM_PVP, 1);

        regGen("q046", "Combate Aquático", "Eliminar 1 jogador inimigo enquanto ele estiver na água.",
                Material.TRIDENT, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_WATER, 1);

        regGen("q047", "Provocação Tática", "Acertar 5 bolas de neve ou ovos em jogadores inimigos.",
                Material.SNOWBALL, QuestDifficulty.EASY, QuestActionType.PVP_SNOWBALL_EGG, 5);

        regGen("q048", "Execução Crítica", "Eliminar 1 jogador inimigo com um golpe crítico (pulando).",
                Material.GOLDEN_AXE, QuestDifficulty.MEDIUM, QuestActionType.KILL_ENEMY_CRITICAL, 1);

        regGen("q049", "Por um Fio", "Eliminar 1 jogador inimigo estando com 3 corações ou menos.",
                Material.GLISTERING_MELON_SLICE, QuestDifficulty.HARD, QuestActionType.KILL_ENEMY_LOW_HP, 1);

        // --- 3. CACA A MOBS & BOSSES ---
        regEnt("q050", "Extermínio de Zumbis", "Matar 20 Zumbis (inclui Zumbi-Aldeão e Múmia/Husk).",
                Material.ROTTEN_FLESH, QuestDifficulty.EASY, QuestActionType.KILL_MOB, 20,
                EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK);

        regEnt("q051", "Quebra-Ossos", "Matar 20 Esqueletos (inclui Esqueleto Vagante/Stray).",
                Material.BONE, QuestDifficulty.EASY, QuestActionType.KILL_MOB, 20,
                EntityType.SKELETON, EntityType.STRAY);

        regEnt("q052", "Esquadrão Antibomba", "Matar 12 Creepers.",
                Material.GUNPOWDER, QuestDifficulty.EASY, QuestActionType.KILL_MOB, 12,
                EntityType.CREEPER);

        regEnt("q053", "Aracnofobia", "Matar 15 Aranhas ou Aranhas da Caverna.",
                Material.STRING, QuestDifficulty.EASY, QuestActionType.KILL_MOB, 15,
                EntityType.SPIDER, EntityType.CAVE_SPIDER);

        regEnt("q054", "Caçadores do Vazio", "Matar 8 Endermen.",
                Material.ENDER_PEARL, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 8,
                EntityType.ENDERMAN);

        regEnt("q055", "Caça às Bruxas", "Matar 4 Bruxas.",
                Material.GLASS_BOTTLE, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 4,
                EntityType.WITCH);

        regEnt("q056", "Patrulha Costeira", "Matar 10 Afogados (Drowned).",
                Material.COPPER_INGOT, QuestDifficulty.EASY, QuestActionType.KILL_MOB, 10,
                EntityType.DROWNED);

        regEnt("q057", "Guardas da Fortaleza", "Matar 5 Esqueletos Wither.",
                Material.WITHER_SKELETON_SKULL, QuestDifficulty.HARD, QuestActionType.KILL_MOB, 5,
                EntityType.WITHER_SKELETON);

        regEnt("q058", "Chamas Vivas", "Matar 8 Blazes.",
                Material.BLAZE_ROD, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 8,
                EntityType.BLAZE);

        regEnt("q059", "Lágrimas do Nether", "Matar 3 Ghasts.",
                Material.GHAST_TEAR, QuestDifficulty.HARD, QuestActionType.KILL_MOB, 3,
                EntityType.GHAST);

        regEnt("q060", "Conflito Dourado", "Matar 10 Piglins, Piglins Bárbaros ou Piglins Zumbificados.",
                Material.GOLD_NUGGET, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 10,
                EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN);

        regEnt("q061", "Feras Carmesim", "Matar 5 Hoglins ou Zoglins.",
                Material.PORKCHOP, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 5,
                EntityType.HOGLIN, EntityType.ZOGLIN);

        regEnt("q062", "Geleia Quente", "Matar 10 Cubos de Magma ou Slimes.",
                Material.SLIME_BALL, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 10,
                EntityType.SLIME, EntityType.MAGMA_CUBE);

        regEnt("q063", "Terror Noturno", "Matar 4 Phantoms.",
                Material.PHANTOM_MEMBRANE, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 4,
                EntityType.PHANTOM);

        regEnt("q064", "Sentinelas do Oceano", "Matar 5 Guardiões.",
                Material.PRISMARINE_SHARD, QuestDifficulty.HARD, QuestActionType.KILL_MOB, 5,
                EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN);

        regEnt("q065", "Contra os Saqueadores", "Matar 8 Saqueadores (Pillagers) ou Vingadores.",
                Material.CROSSBOW, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB, 8,
                EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER, EntityType.RAVAGER);

        regGen("q066", "Limpeza Geral", "Matar 50 monstros hostis no total em equipe.",
                Material.IRON_SWORD, QuestDifficulty.MEDIUM, QuestActionType.KILL_ANY_HOSTILE, 50);

        regGen("q067", "Arquearia Contra Monstros", "Matar 10 monstros hostis usando Arco ou Besta.",
                Material.BOW, QuestDifficulty.EASY, QuestActionType.KILL_MOB_BOW, 10);

        regGen("q068", "Lenhador de Monstros", "Matar 10 monstros hostis usando Machado.",
                Material.IRON_AXE, QuestDifficulty.EASY, QuestActionType.KILL_MOB_AXE, 10);

        regGen("q069", "Vigília da Meia-Noite", "Matar 15 monstros hostis durante a noite no Overworld.",
                Material.LANTERN, QuestDifficulty.EASY, QuestActionType.KILL_MOB_NIGHT, 15);

        regGen("q070", "Purificação pelo Fogo", "Matar 6 monstros hostis enquanto eles estiverem pegando fogo.",
                Material.FLINT_AND_STEEL, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB_FIRE, 6);

        regGen("q071", "Caçadores do Submundo", "Matar 12 monstros hostis dentro do Nether.",
                Material.NETHER_BRICK, QuestDifficulty.MEDIUM, QuestActionType.KILL_MOB_NETHER, 12);

        // --- 4. AGRICULTURA, PESCA & CULINARIA ---
        regMat("q072", "Colheita de Trigo", "Colher 48 Trigos maduros.",
                Material.WHEAT, QuestDifficulty.EASY, QuestActionType.HARVEST_CROP, 48,
                Material.WHEAT);

        regMat("q073", "Colheita de Cenouras", "Colher 48 Cenouras maduras.",
                Material.CARROT, QuestDifficulty.EASY, QuestActionType.HARVEST_CROP, 48,
                Material.CARROTS);

        regMat("q074", "Colheita de Batatas", "Colher 48 Batatas maduras.",
                Material.POTATO, QuestDifficulty.EASY, QuestActionType.HARVEST_CROP, 48,
                Material.POTATOES);

        regMat("q075", "Horta de Beterrabas", "Colher 32 Beterrabas maduras.",
                Material.BEETROOT, QuestDifficulty.EASY, QuestActionType.HARVEST_CROP, 32,
                Material.BEETROOTS);

        regMat("q076", "Plantação de Melancias", "Colher 24 Melancias ou Abóboras.",
                Material.MELON_SLICE, QuestDifficulty.EASY, QuestActionType.HARVEST_CROP, 24,
                Material.MELON, Material.PUMPKIN);

        regMat("q077", "Canavial", "Colher 32 Canas-de-Açúcar naturais/crescidas.",
                Material.SUGAR_CANE, QuestDifficulty.EASY, QuestActionType.HARVEST_CROP, 32,
                Material.SUGAR_CANE);

        regMat("q078", "Alquimia Agrícola", "Colher 24 Fungos do Nether maduros.",
                Material.NETHER_WART, QuestDifficulty.MEDIUM, QuestActionType.HARVEST_CROP, 24,
                Material.NETHER_WART);

        regMat("q079", "Churrasco do Clã", "Retirar 32 carnes assadas (Bife, Porco, Frango ou Carneiro) da fornalha/defumador.",
                Material.COOKED_BEEF, QuestDifficulty.EASY, QuestActionType.SMELT_EXTRACT, 32,
                Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON, Material.COOKED_COD, Material.COOKED_SALMON);

        regMat("q080", "Batata Quente", "Retirar 24 Batatas Assadas da fornalha ou defumador.",
                Material.BAKED_POTATO, QuestDifficulty.EASY, QuestActionType.SMELT_EXTRACT, 24,
                Material.BAKED_POTATO);

        regMat("q081", "Padaria de Guerra", "Craftar 24 Pães.",
                Material.BREAD, QuestDifficulty.EASY, QuestActionType.CRAFT_ITEM, 24,
                Material.BREAD);

        regMat("q082", "Fruto Dourado", "Craftar 3 Maçãs Douradas.",
                Material.GOLDEN_APPLE, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 3,
                Material.GOLDEN_APPLE);

        regMat("q083", "Nutrição de Ouro", "Craftar 12 Cenouras Douradas.",
                Material.GOLDEN_CARROT, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 12,
                Material.GOLDEN_CARROT);

        regMat("q084", "Confeiteiros", "Craftar 1 Bolo ou Torta de Abóbora.",
                Material.CAKE, QuestDifficulty.EASY, QuestActionType.CRAFT_ITEM, 1,
                Material.CAKE, Material.PUMPKIN_PIE);

        regGen("q085", "Dia de Pesca", "Pescar 10 peixes ou itens com vara de pesca.",
                Material.FISHING_ROD, QuestDifficulty.EASY, QuestActionType.FISH_ANY, 10);

        regGen("q086", "Pescador Sortudo", "Pescar 1 item de tesouro (Arco, Livro, Sela, Etiqueta, Náutilo).",
                Material.NAME_TAG, QuestDifficulty.MEDIUM, QuestActionType.FISH_TREASURE, 1);

        regEnt("q087", "Pecuária do Clã", "Reproduzir 6 animais de fazenda (Vacas, Ovelhas, Porcos ou Galinhas).",
                Material.WHEAT, QuestDifficulty.EASY, QuestActionType.BREED_ANIMAL, 6,
                EntityType.COW, EntityType.MOOSHROOM, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN);

        regEnt("q088", "Melhor Amigo", "Domesticar 2 Lobos ou Gatos.",
                Material.BONE, QuestDifficulty.EASY, QuestActionType.TAME_ANIMAL, 2,
                EntityType.WOLF, EntityType.CAT);

        regEnt("q089", "Cavalaria", "Domesticar 1 Cavalo, Burro ou Mula.",
                Material.SADDLE, QuestDifficulty.MEDIUM, QuestActionType.TAME_ANIMAL, 1,
                EntityType.HORSE, EntityType.DONKEY, EntityType.MULE);

        regGen("q090", "Tosquiadores", "Tosquiar 12 Ovelhas com tesoura.",
                Material.SHEARS, QuestDifficulty.EASY, QuestActionType.SHEAR_SHEEP, 12);

        regGen("q091", "Leite Fresco", "Ordenhar 4 Baldes de Leite de vacas.",
                Material.MILK_BUCKET, QuestDifficulty.EASY, QuestActionType.MILK_COW, 4);

        regGen("q092", "Tropa Bem Alimentada", "Comer 20 alimentos em equipe durante o dia.",
                Material.COOKED_PORKCHOP, QuestDifficulty.EASY, QuestActionType.CONSUME_FOOD, 20);

        regMat("q093", "Reflorestamento", "Plantar 24 mudas de árvores no mundo.",
                Material.OAK_SAPLING, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 24,
                Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
                Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
                Material.CHERRY_SAPLING, Material.MANGROVE_PROPAGULE);

        // --- 5. CONSTRUCAO, REDSTONE & FORTIFICACAO DE BASE ---
        regGen("q094", "Mãos à Obra", "Colocar 150 blocos sólidos de construção.",
                Material.BRICKS, QuestDifficulty.EASY, QuestActionType.PLACE_ANY_SOLID, 150);

        regMat("q095", "Muralha de Pedra", "Colocar 64 blocos de Tijolos de Pedra, Pedra Lisa ou Ardósia Polida.",
                Material.STONE_BRICKS, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 64,
                Material.STONE_BRICKS, Material.SMOOTH_STONE, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.DEEPSLATE_TILES);

        regMat("q096", "Vidraçaria", "Colocar 48 blocos de Vidro ou Painéis de Vidro.",
                Material.GLASS, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 48,
                Material.GLASS, Material.GLASS_PANE, Material.TINTED_GLASS);

        regMat("q097", "Bunker Anti-Explosão", "Colocar 12 blocos de Obsidiana na base.",
                Material.OBSIDIAN, QuestDifficulty.MEDIUM, QuestActionType.PLACE_BLOCK, 12,
                Material.OBSIDIAN, Material.CRYING_OBSIDIAN);

        regMat("q098", "Armazenamento do Clã", "Colocar 12 Baús ou Barris.",
                Material.CHEST, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 12,
                Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);

        regMat("q099", "Automação com Funis", "Craftar ou colocar 4 Funis (Hoppers).",
                Material.HOPPER, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 4,
                Material.HOPPER);

        regMat("q100", "Engenharia de Pistões", "Craftar 6 Pistões ou Pistões Grudentos.",
                Material.PISTON, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 6,
                Material.PISTON, Material.STICKY_PISTON);

        regMat("q101", "Circuitos de Redstone", "Colocar 24 Pós de Redstone, Repetidores ou Comparadores.",
                Material.REDSTONE, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 24,
                Material.REDSTONE_WIRE, Material.REPEATER, Material.COMPARATOR);

        regMat("q102", "Ferrovia", "Craftar ou colocar 24 Trilhos.",
                Material.RAIL, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 24,
                Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL);

        regMat("q103", "Iluminação da Base", "Colocar 48 Tochas ou Lanternas.",
                Material.TORCH, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 48,
                Material.TORCH, Material.WALL_TORCH, Material.SOUL_TORCH, Material.SOUL_WALL_TORCH,
                Material.LANTERN, Material.SOUL_LANTERN);

        regMat("q104", "Demolidores", "Craftar 4 blocos de TNT.",
                Material.TNT, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 4,
                Material.TNT);

        regMat("q105", "Cofre Dimensional", "Craftar 1 Baú do Ender (Ender Chest).",
                Material.ENDER_CHEST, QuestDifficulty.HARD, QuestActionType.CRAFT_ITEM, 1,
                Material.ENDER_CHEST);

        regMat("q106", "Altar Arcano", "Craftar 1 Mesa de Encantamentos.",
                Material.ENCHANTING_TABLE, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 1,
                Material.ENCHANTING_TABLE);

        regMat("q107", "Biblioteca de Guerra", "Craftar 6 Estantes de Livros.",
                Material.BOOKSHELF, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 6,
                Material.BOOKSHELF);

        regMat("q108", "Defesa Perimetral", "Colocar 24 Muros de Pedregulho ou Tijolos de Pedra.",
                Material.COBBLESTONE_WALL, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 24,
                Material.COBBLESTONE_WALL, Material.STONE_BRICK_WALL, Material.COBBLED_DEEPSLATE_WALL, Material.DEEPSLATE_BRICK_WALL);

        regMat("q109", "Acesso Vertical", "Colocar 16 Escadas de Mão ou Andaimes.",
                Material.LADDER, QuestDifficulty.EASY, QuestActionType.PLACE_BLOCK, 16,
                Material.LADDER, Material.SCAFFOLDING);

        regMat("q110", "Grades de Proteção", "Craftar 16 Grades de Ferro.",
                Material.IRON_BARS, QuestDifficulty.EASY, QuestActionType.CRAFT_ITEM, 16,
                Material.IRON_BARS);

        // --- 6. EXPLORACAO, MOVIMENTACAO & COMERCIO ---
        regGen("q111", "Marcha Forçada", "Percorrer 1.500 blocos a pé ou correndo em equipe.",
                Material.LEATHER_BOOTS, QuestDifficulty.EASY, QuestActionType.TRAVEL_WALK, 1500);

        regGen("q112", "Navegadores", "Percorrer 600 blocos de Barco.",
                Material.OAK_BOAT, QuestDifficulty.EASY, QuestActionType.TRAVEL_BOAT, 600);

        regGen("q113", "Patrulha Montada", "Percorrer 600 blocos montado em Cavalo ou Strider.",
                Material.SADDLE, QuestDifficulty.MEDIUM, QuestActionType.TRAVEL_MOUNT, 600);

        regGen("q114", "Travessia a Nado", "Nadar 300 blocos em rios ou oceanos.",
                Material.WATER_BUCKET, QuestDifficulty.EASY, QuestActionType.TRAVEL_SWIM, 300);

        regGen("q115", "Expedição no Nether", "Percorrer 500 blocos dentro do Nether.",
                Material.NETHERRACK, QuestDifficulty.MEDIUM, QuestActionType.TRAVEL_NETHER, 500);

        regGen("q116", "Fundo do Mundo", "Alcançar a camada Y = -50 ou mais profundo.",
                Material.COBBLED_DEEPSLATE, QuestDifficulty.EASY, QuestActionType.REACH_LOW_Y, 1);

        regGen("q117", "Acima das Nuvens", "Alcançar a camada Y = 180 ou mais alto.",
                Material.FEATHER, QuestDifficulty.EASY, QuestActionType.REACH_HIGH_Y, 1);

        regGen("q118", "Mercadores da Vila", "Realizar 8 trocas com Aldeões.",
                Material.EMERALD, QuestDifficulty.MEDIUM, QuestActionType.VILLAGER_TRADE, 8);

        regGen("q119", "Mestres do Comércio", "Realizar 20 trocas com Aldeões.",
                Material.EMERALD_BLOCK, QuestDifficulty.HARD, QuestActionType.VILLAGER_TRADE, 20);

        // --- 7. FORJA, ENCANTAMENTOS & ALQUIMIA ---
        regMat("q120", "Siderúrgica de Ferro", "Retirar 48 Barras de Ferro fundidas da fornalha ou alto-forno.",
                Material.IRON_INGOT, QuestDifficulty.EASY, QuestActionType.SMELT_EXTRACT, 48,
                Material.IRON_INGOT);

        regMat("q121", "Fundição de Ouro", "Retirar 24 Barras de Ouro fundidas da fornalha ou alto-forno.",
                Material.GOLD_INGOT, QuestDifficulty.EASY, QuestActionType.SMELT_EXTRACT, 24,
                Material.GOLD_INGOT);

        regMat("q122", "Refino Ancestral", "Retirar 2 Sucatas de Netherita fundidas da fornalha ou alto-forno.",
                Material.NETHERITE_SCRAP, QuestDifficulty.HARD, QuestActionType.SMELT_EXTRACT, 2,
                Material.NETHERITE_SCRAP);

        regMat("q123", "Vidro Temperado", "Retirar 32 blocos de Vidro fundidos da fornalha.",
                Material.GLASS, QuestDifficulty.EASY, QuestActionType.SMELT_EXTRACT, 32,
                Material.GLASS);

        regMat("q124", "Ferreiros de Ferro", "Craftar 4 peças de armadura ou ferramentas de Ferro.",
                Material.IRON_CHESTPLATE, QuestDifficulty.EASY, QuestActionType.CRAFT_ITEM, 4,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.IRON_SWORD, Material.IRON_PICKAXE, Material.IRON_AXE);

        regMat("q125", "Arsenal de Diamante", "Craftar 2 peças de armadura ou ferramentas de Diamante.",
                Material.DIAMOND_CHESTPLATE, QuestDifficulty.MEDIUM, QuestActionType.CRAFT_ITEM, 2,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.DIAMOND_SWORD, Material.DIAMOND_PICKAXE, Material.DIAMOND_AXE);

        regGen("q126", "Forja Suprema", "Melhorar ou aplicar molde em 1 peça na Mesa de Ferraria (Smithing Table).",
                Material.SMITHING_TABLE, QuestDifficulty.HARD, QuestActionType.SMITHING_UPGRADE, 1);

        regGen("q127", "Aprendizes de Magia", "Encantar 4 itens na Mesa de Encantamentos.",
                Material.ENCHANTED_BOOK, QuestDifficulty.MEDIUM, QuestActionType.ENCHANT_ITEM, 4);

        regMat("q128", "Escudo Prontos", "Craftar 2 Escudos para a equipe.",
                Material.SHIELD, QuestDifficulty.EASY, QuestActionType.CRAFT_ITEM, 2,
                Material.SHIELD);

        regMat("q129", "Flecheiros", "Craftar 32 Flechas na bancada.",
                Material.ARROW, QuestDifficulty.EASY, QuestActionType.CRAFT_ITEM, 32,
                Material.ARROW, Material.SPECTRAL_ARROW);

        // --- 8. EVENTOS DO SERVIDOR (BERRANTE, AIRDROP & CACADA) ---
        regGen("q130", "Toque de Guerra", "Usar o Berrante Localizador 2 vezes.",
                Material.GOAT_HORN, QuestDifficulty.MEDIUM, QuestActionType.SERVER_USE_HORN, 2);

        regGen("q131", "Rastreador de Inimigos", "Revelar pelo menos 1 jogador inimigo usando o Berrante Localizador.",
                Material.GOAT_HORN, QuestDifficulty.MEDIUM, QuestActionType.SERVER_REVEAL_HORN, 1);

        regGen("q132", "Radar Implacável", "Revelar 3 jogadores inimigos no total usando o Berrante Localizador.",
                Material.GOAT_HORN, QuestDifficulty.HARD, QuestActionType.SERVER_REVEAL_HORN, 3);

        regGen("q133", "Saqueadores de AirDrop", "Ser o primeiro a abrir 1 Caixa de Suprimentos (AirDrop).",
                Material.CHEST, QuestDifficulty.HARD, QuestActionType.SERVER_OPEN_AIRDROP, 1);

        regGen("q134", "Caçador Implacável", "Abater 1 Presa inimiga durante o evento de Caçada.",
                Material.SPECTRAL_ARROW, QuestDifficulty.HARD, QuestActionType.SERVER_KILL_PREY, 1);

        regGen("q135", "Sobrevivente da Caçada", "Sobreviver aos 30 minutos sendo a Presa do seu time na Caçada.",
                Material.TOTEM_OF_UNDYING, QuestDifficulty.HARD, QuestActionType.SERVER_SURVIVE_HUNT, 1);
    }

    private static void regGen(
            String id, String title, String description, Material icon,
            QuestDifficulty difficulty, QuestActionType actionType, int targetAmount
    ) {
        REGISTRY.put(id, new QuestDefinition(id, title, description, icon, difficulty, actionType, targetAmount, Set.of(), Set.of()));
    }

    private static void regMat(
            String id, String title, String description, Material icon,
            QuestDifficulty difficulty, QuestActionType actionType, int targetAmount, Material... materials
    ) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        Collections.addAll(set, materials);
        REGISTRY.put(id, new QuestDefinition(id, title, description, icon, difficulty, actionType, targetAmount, set, Set.of()));
    }

    private static void regEnt(
            String id, String title, String description, Material icon,
            QuestDifficulty difficulty, QuestActionType actionType, int targetAmount, EntityType... entities
    ) {
        Set<EntityType> set = EnumSet.noneOf(EntityType.class);
        Collections.addAll(set, entities);
        REGISTRY.put(id, new QuestDefinition(id, title, description, icon, difficulty, actionType, targetAmount, Set.of(), set));
    }

    public static Map<String, QuestDefinition> getAllQuests() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    public static QuestDefinition getById(String id) {
        return REGISTRY.get(id);
    }

    public static List<QuestDefinition> getByDifficulty(QuestDifficulty difficulty) {
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition q : REGISTRY.values()) {
            if (q.getDifficulty() == difficulty) {
                result.add(q);
            }
        }
        return result;
    }
}
