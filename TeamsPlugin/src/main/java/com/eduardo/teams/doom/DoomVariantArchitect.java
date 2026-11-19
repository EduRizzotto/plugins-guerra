package com.eduardo.teams.doom;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * Arquiteto das variantes /doom2 (Fortaleza DOOM II: Abismo Corrompido)
 * e /doom3 (Fortaleza DOOM III: Templo do Sol Sangrento).
 *
 * Ambas possuem 10 saloes gigantescos completamente diferentes da primeira Fortaleza DOOM,
 * mantendo 100% de continuidade nas escadas de 4 degraus, escadas de retorno nos fossos de parkour,
 * fileira de teias em todas as portas e o Bau Final de +200 Pontos na 10a Sala.
 */
public final class DoomVariantArchitect {

    private DoomVariantArchitect() {
    }

    public static void populateVariantSpawners(DoomManager.DoomVariant variant, List<DoomManager.SpawnerPoint> spawnerPoints) {
        spawnerPoints.clear();
        if (variant == DoomManager.DoomVariant.DOOM_2) {
            // DOOM 2: Abismo Corrompido (Sculk, Dark Prismarine, Strays, Drowned, Wither Skeletons, Vindicators, Blazes)
            // Sala 1: Trincheiras Abissais (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 7, EntityType.DROWNED));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 7, EntityType.DROWNED));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 13, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 18, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 18, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 19, EntityType.VINDICATOR));

            // Sala 2: Parkour Zigue-Zague Abissal (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 36, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 36, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 46, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 46, EntityType.BLAZE));

            // Sala 3: Labirinto Concentrico de Aneis (8 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 64, EntityType.CAVE_SPIDER));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 64, EntityType.CAVE_SPIDER));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-7, 2, 67, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(7, 2, 67, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 69, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-7, 2, 73, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(7, 2, 73, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 74, EntityType.WITHER_SKELETON));

            // Sala 4: Passarelas de Barreiras (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-8, 5, 88, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(8, 5, 88, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 5, 88, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 5, 93, EntityType.BLAZE));

            // Sala 5: Pantano das Almas & Pilares de Basalto (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 108, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 108, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 113, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 118, EntityType.CAVE_SPIDER));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 118, EntityType.CAVE_SPIDER));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 119, EntityType.STRAY));

            // Sala 6: Parkour da Ampulheta (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 7, 136, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 7, 136, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 7, 144, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 7, 144, EntityType.BLAZE));

            // Sala 7: Camara dos Cristais de Ametista (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-14, 2, 160, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(14, 2, 160, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-7, 2, 165, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(7, 2, 165, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-14, 2, 170, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(14, 2, 170, EntityType.STRAY));

            // Sala 8: Parkour das Gaiolas Suspensas (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 186, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 186, EntityType.STRAY));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 194, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 194, EntityType.BLAZE));

            // Sala 9: Panteao dos Guardioes Abissais (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-14, 6, 210, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(14, 6, 210, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 6, 215, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-14, 6, 221, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(14, 6, 221, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 6, 222, EntityType.STRAY));

            // Sala 10: Santuario Abissal DOOM II (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-11, 6, 231, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(11, 6, 231, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-7, 6, 235, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(7, 6, 235, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-11, 6, 239, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(11, 6, 239, EntityType.VINDICATOR));
        } else {
            // DOOM 3: Templo do Sol Sangrento (Bastiao Dourado & Carmesim)
            // Sala 1: Patio das Legioes Carmesins (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 7, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 7, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 3, 12, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 2, 18, EntityType.HUSK));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 2, 18, EntityType.HUSK));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 19, EntityType.BLAZE));

            // Sala 2: Parkour das Passarelas Cruzadas em X (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 36, EntityType.SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 36, EntityType.SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 46, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 46, EntityType.BLAZE));

            // Sala 3: Labirinto das Celas de Sangue (8 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-12, 2, 64, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(12, 2, 64, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 64, EntityType.CAVE_SPIDER));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-12, 2, 69, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(12, 2, 69, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 69, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-12, 2, 73, EntityType.HUSK));
            spawnerPoints.add(new DoomManager.SpawnerPoint(12, 2, 73, EntityType.HUSK));

            // Sala 4: Vigas Douradas sobre Fornalhas (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-9, 5, 88, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(9, 5, 88, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-9, 5, 93, EntityType.SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(9, 5, 93, EntityType.SKELETON));

            // Sala 5: Arena dos Gladiadores (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-10, 2, 108, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(10, 2, 108, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 113, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-10, 2, 118, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(10, 2, 118, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 119, EntityType.BLAZE));

            // Sala 6: Parkour das Piramides Flutuantes (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 7, 136, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 7, 136, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 7, 144, EntityType.SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 7, 144, EntityType.SKELETON));

            // Sala 7: Aqueduto de Lava & Passarelas (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-14, 2, 160, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(14, 2, 160, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 165, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-14, 2, 170, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(14, 2, 170, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 2, 171, EntityType.BLAZE));

            // Sala 8: Parkour dos Portais de Fogo (4 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 186, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 186, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-17, 6, 194, EntityType.SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(17, 6, 194, EntityType.SKELETON));

            // Sala 9: Salao Real dos Centurioes (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 6, 210, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 6, 210, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 6, 215, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-13, 6, 221, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(13, 6, 221, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(0, 6, 222, EntityType.BLAZE));

            // Sala 10: Santuario do Sol Sangrento DOOM III (6 spawners)
            spawnerPoints.add(new DoomManager.SpawnerPoint(-11, 6, 231, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(11, 6, 231, EntityType.WITHER_SKELETON));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-7, 6, 235, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(7, 6, 235, EntityType.BLAZE));
            spawnerPoints.add(new DoomManager.SpawnerPoint(-11, 6, 239, EntityType.VINDICATOR));
            spawnerPoints.add(new DoomManager.SpawnerPoint(11, 6, 239, EntityType.VINDICATOR));
        }
    }

    public static Material resolveVariantFloorMaterial(DoomManager.DoomVariant variant, int dx, int dz) {
        boolean isParkourPit = (dz >= 30 && dz <= 52)
                || (dz >= 85 && dz <= 96)
                || (dz >= 130 && dz <= 149)
                || (dz >= 182 && dz <= 199);

        if (variant == DoomManager.DoomVariant.DOOM_2) {
            if (isParkourPit) {
                if ((Math.abs(dx) + dz) % 3 == 0) {
                    return Material.SOUL_SAND;
                }
                return ((dx + dz) % 2 == 0) ? Material.SCULK : Material.MAGMA_BLOCK;
            }
            if (dz >= 106 && dz <= 121) {
                // Sala 5 DOOM 2: Pantano das Almas
                return ((Math.abs(dx) + dz) % 2 == 0) ? Material.SOUL_SAND : Material.SCULK;
            }
            if (Math.abs(dx) <= 2) {
                return (dz % 4 == 0) ? Material.CRYING_OBSIDIAN : Material.DARK_PRISMARINE;
            }
            return ((dx + dz) % 4 == 0) ? Material.PRISMARINE_BRICKS : Material.DEEPSLATE_TILES;
        } else {
            // DOOM 3: Templo do Sol Sangrento
            if (isParkourPit) {
                if ((Math.abs(dx) + dz) % 3 == 0) {
                    return Material.SOUL_SAND;
                }
                return ((dx + dz) % 2 == 0) ? Material.MAGMA_BLOCK : Material.RED_NETHER_BRICKS;
            }
            if (Math.abs(dx) <= 2) {
                return (dz % 3 == 0) ? Material.GOLD_BLOCK : Material.GILDED_BLACKSTONE;
            }
            return ((dx + dz) % 3 == 0) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS;
        }
    }

    // =========================================================================
    //  DOOM 2: ABISMO CORROMPIDO (10 SALOES EXCLUSIVOS)
    // =========================================================================

    public static void buildDoom2InteriorSlice(DoomManager dm, int dz, int[][] standardThreeDoors) {
        if (dz >= 2 && dz <= 24) {
            buildDoom2Hall1AbyssalTrenches(dm, dz, standardThreeDoors);
        } else if (dz == 25 || dz == 26) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 27 && dz <= 55) {
            buildDoom2Hall2ZigZagParkour(dm, dz);
        } else if (dz == 56) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 57 && dz <= 80) {
            buildDoom2Hall3ConcentricRingMaze(dm, dz, standardThreeDoors);
        } else if (dz == 81) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 82 && dz <= 100) {
            buildDoom2Hall4HurdleBridges(dm, dz);
        } else if (dz == 101) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 102 && dz <= 125) {
            buildDoom2Hall5SoulSwampBasalt(dm, dz, standardThreeDoors);
        } else if (dz == 126) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 127 && dz <= 152) {
            buildDoom2Hall6HourglassParkour(dm, dz);
        } else if (dz == 153) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 154 && dz <= 177) {
            buildDoom2Hall7AmethystCrystalChamber(dm, dz, standardThreeDoors);
        } else if (dz == 178) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 179 && dz <= 203) {
            buildDoom2Hall8SuspendedCageParkour(dm, dz);
        } else if (dz == 204) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 205 && dz <= 226) {
            buildDoom2Hall9AbyssalPantheon(dm, dz);
        } else if (dz == 227) {
            dm.buildPartitionWall(dz, new int[][]{{-4, 4}}, 5, 11, true);
        } else if (dz >= 228 && dz <= 244) {
            buildDoom2Hall10AbyssalSanctum(dm, dz);
        }
    }

    private static void buildDoom2Hall1AbyssalTrenches(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 21 && dz <= 24) {
            dm.buildAscendingStairsSlice(dz, 21, exitStairLanes);
            return;
        }
        // 5 Bunkers Octogonais de Prismarino Escuro & Sculk (dz = 9 e dz = 16)
        if (Math.abs(dz - 9) <= 1) {
            for (int bx : new int[]{-10, 0, 10}) {
                for (int dx = bx - 1; dx <= bx + 1; dx++) {
                    dm.setDungeonBlock(dx, 1, dz, Material.DARK_PRISMARINE);
                    dm.setDungeonBlock(dx, 2, dz, (dx == bx && dz == 9) ? Material.SEA_LANTERN : Material.IRON_BARS);
                }
            }
        }
        if (Math.abs(dz - 16) <= 1) {
            for (int bx : new int[]{-6, 6}) {
                for (int dx = bx - 1; dx <= bx + 1; dx++) {
                    for (int dy = 1; dy <= 15; dy++) {
                        dm.setDungeonBlock(dx, dy, dz, (dx == bx) ? Material.CRYING_OBSIDIAN : Material.PRISMARINE_BRICKS);
                    }
                }
            }
        }
        if (dz == 5 || dz == 13) {
            dm.setDungeonBlock(-16, 1, dz, Material.COBWEB);
            dm.setDungeonBlock(16, 1, dz, Material.COBWEB);
        }
    }

    private static void buildDoom2Hall2ZigZagParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 29 || dz >= 53) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 30);

        if (Math.abs(dz - 36) <= 1 || Math.abs(dz - 46) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        dm.setDungeonBlock(dx, 5, dz, Material.PRISMARINE_BRICKS);
                    }
                }
            }
        }

        // Ilhas de Parkour em Zigue-Zague (sobreposicao em X garante saltos diagonais 100% possiveis na ida e na volta)
        if (dz == 32 || dz == 33 || dz == 49 || dz == 50) {
            int[][] islands = {{-13, -9}, {-2, 2}, {9, 13}};
            for (int[] range : islands) {
                for (int dx = range[0]; dx <= range[1]; dx++) {
                    dm.setDungeonBlock(dx, 4, dz, (dx == (range[0] + range[1]) / 2) ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                }
            }
        } else if (dz == 36 || dz == 37 || dz == 45 || dz == 46) {
            int[][] shiftedIslands = {{-10, -6}, {-4, -1}, {1, 4}, {6, 10}};
            for (int[] range : shiftedIslands) {
                for (int dx = range[0]; dx <= range[1]; dx++) {
                    dm.setDungeonBlock(dx, 5, dz, Material.PRISMARINE_BRICKS);
                }
            }
        } else if (dz >= 40 && dz <= 42) {
            int[][] apexIslands = {{-13, -8}, {-4, 4}, {8, 13}};
            for (int[] range : apexIslands) {
                for (int dx = range[0]; dx <= range[1]; dx++) {
                    dm.setDungeonBlock(dx, 6, dz, (Math.abs(dx) % 3 == 0) ? Material.CRYING_OBSIDIAN : Material.DARK_PRISMARINE);
                }
            }
        }
    }

    private static void buildDoom2Hall3ConcentricRingMaze(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 57 && dz <= 60) {
            dm.buildDescendingStairsSlice(dz, 57);
            return;
        }
        if (dz >= 77 && dz <= 80) {
            dm.buildAscendingStairsSlice(dz, 77, exitStairLanes);
            return;
        }
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz >= 62 && dz <= 75) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                if (isConcentricRingWall(dx, dz)) {
                    for (int dy = 1; dy <= 5; dy++) {
                        Material wallMat = (dy == 3 && (Math.abs(dx) + dz) % 2 == 0) ? Material.IRON_BARS : Material.DARK_PRISMARINE;
                        dm.setDungeonBlock(dx, dy, dz, wallMat);
                    }
                } else if ((dz == 63 || dz == 74) && Math.abs(dx) == 14) {
                    dm.setDungeonBlock(dx, 1, dz, Material.COBWEB);
                }
            }
        }
    }

    private static boolean isConcentricRingWall(int dx, int dz) {
        int absX = Math.abs(dx);
        // Anel Externo: dz = 62, dz = 75 (para |dx| <= 15) e |dx| = 15 (para dz = 62..75)
        boolean onOuterZ = (dz == 62 || dz == 75) && absX <= 15;
        boolean onOuterX = (absX == 15) && (dz >= 62 && dz <= 75);
        if (onOuterZ || onOuterX) {
            // Portas largas no Anel Externo
            if (dz == 62 && (absX >= 9 && absX <= 12)) return false;
            if (dz == 75 && (absX <= 2 || (absX >= 10 && absX <= 12))) return false;
            if (absX == 15 && (dz == 68 || dz == 69)) return false;
            return true;
        }

        // Anel Intermediario: dz = 65, dz = 72 (para |dx| <= 10) e |dx| = 10 (para dz = 65..72)
        boolean onMidZ = (dz == 65 || dz == 72) && absX <= 10;
        boolean onMidX = (absX == 10) && (dz >= 65 && dz <= 72);
        if (onMidZ || onMidX) {
            if (dz == 65 && absX <= 2) return false;
            if (dz == 72 && (absX >= 5 && absX <= 7)) return false;
            if (absX == 10 && (dz == 68 || dz == 69)) return false;
            return true;
        }

        // Nucleo Central: |dx| == 3 e dz in 68..69
        return absX == 3 && (dz == 68 || dz == 69);
    }

    private static void buildDoom2Hall4HurdleBridges(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 84 || dz >= 97) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 85);

        // Torres de Spawners em dx = -8, 0, +8
        if (dz == 88 || dz == 93) {
            for (int tx : new int[]{-8, 0, 8}) {
                if (dz == 93 && tx != 0) continue;
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(tx, dy, dz, Material.CRYING_OBSIDIAN);
                }
            }
        }

        // 4 Passarelas com barreiras baixas (hurdles) para saltar por cima
        if ((dz >= 86 && dz <= 88) || (dz >= 90 && dz <= 92) || (dz == 94 || dz == 95)) {
            int[][] catwalks = {{-14, -12}, {-6, -4}, {4, 6}, {12, 14}};
            for (int[] lane : catwalks) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    dm.setDungeonBlock(dx, 4, dz, Material.DARK_PRISMARINE);
                    if (dz == 87 || dz == 91) {
                        dm.setDungeonBlock(dx, 5, dz, Material.DARK_PRISMARINE_SLAB);
                    }
                }
            }
        }
    }

    private static void buildDoom2Hall5SoulSwampBasalt(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 102 && dz <= 105) {
            dm.buildDescendingStairsSlice(dz, 102);
            return;
        }
        if (dz >= 122 && dz <= 125) {
            dm.buildAscendingStairsSlice(dz, 122, exitStairLanes);
            return;
        }
        // Passarelas baixas de Basalto Polido (dy = 1) sobre o pantano de Areia das Almas
        for (int laneCenter : new int[]{-11, 0, 11}) {
            if ((dz + Math.abs(laneCenter)) % 3 != 0) {
                for (int dx = laneCenter - 1; dx <= laneCenter + 1; dx++) {
                    dm.setDungeonBlock(dx, 1, dz, Material.POLISHED_BASALT);
                }
            }
        }
        // Pilares de Basalto e Sculk (dz = 109, 114, 119)
        if (dz == 109 || dz == 114 || dz == 119) {
            for (int px : new int[]{-6, 6}) {
                for (int dy = 1; dy <= 15; dy++) {
                    dm.setDungeonBlock(px, dy, dz, (dy % 4 == 0) ? Material.SEA_LANTERN : Material.POLISHED_BASALT);
                }
            }
        }
    }

    private static void buildDoom2Hall6HourglassParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 129 || dz >= 150) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 130);

        if (Math.abs(dz - 136) <= 1 || Math.abs(dz - 144) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        dm.setDungeonBlock(dx, 6, dz, Material.PRISMARINE_BRICKS);
                    }
                }
            }
        }

        if (dz == 132 || dz == 133 || dz == 147 || dz == 148) {
            int[][] outerPads = {{-14, -10}, {-6, -2}, {2, 6}, {10, 14}};
            for (int[] pad : outerPads) {
                for (int dx = pad[0]; dx <= pad[1]; dx++) {
                    dm.setDungeonBlock(dx, 5, dz, Material.DARK_PRISMARINE);
                }
            }
        } else if (dz == 136 || dz == 137 || dz == 144 || dz == 145) {
            int[][] midPads = {{-11, -7}, {-3, 3}, {7, 11}};
            for (int[] pad : midPads) {
                for (int dx = pad[0]; dx <= pad[1]; dx++) {
                    dm.setDungeonBlock(dx, 5, dz, Material.PRISMARINE_BRICKS);
                }
            }
        } else if (dz >= 139 && dz <= 142) {
            // Plataforma Central da Ampulheta onde todas as 5 equipes colidem!
            for (int dx = -8; dx <= 8; dx++) {
                boolean border = Math.abs(dx) == 8 || dz == 139 || dz == 142;
                dm.setDungeonBlock(dx, 6, dz, border ? Material.CRYING_OBSIDIAN : Material.DARK_PRISMARINE);
            }
        }
    }

    private static void buildDoom2Hall7AmethystCrystalChamber(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 154 && dz <= 157) {
            dm.buildDescendingStairsSlice(dz, 154);
            return;
        }
        if (dz >= 174 && dz <= 177) {
            dm.buildAscendingStairsSlice(dz, 174, exitStairLanes);
            return;
        }
        // Agulhas Gigantes de Cristal de Ametista & Obsidiana Chorona (dz = 160, 165, 170)
        for (int cz : new int[]{160, 165, 170}) {
            if (Math.abs(dz - cz) <= 1) {
                int[] crystalXs = (cz == 165) ? new int[]{-11, 0, 11} : new int[]{-7, 7};
                for (int cx : crystalXs) {
                    for (int dx = cx - 1; dx <= cx + 1; dx++) {
                        boolean isCore = (dx == cx && dz == cz);
                        int maxH = isCore ? 10 : 5;
                        for (int dy = 1; dy <= maxH; dy++) {
                            dm.setDungeonBlock(dx, dy, dz, isCore ? Material.CRYING_OBSIDIAN : Material.AMETHYST_BLOCK);
                        }
                    }
                }
            }
        }
    }

    private static void buildDoom2Hall8SuspendedCageParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 181 || dz >= 200) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 182);

        if (Math.abs(dz - 186) <= 1 || Math.abs(dz - 194) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        dm.setDungeonBlock(dx, 5, dz, Material.PRISMARINE_BRICKS);
                    }
                }
            }
        }

        int cageY = -1;
        if (dz == 184 || dz == 185) {
            cageY = 4;
        } else if (dz == 188 || dz == 189) {
            cageY = 5;
        } else if (dz == 192 || dz == 193) {
            cageY = 5;
        } else if (dz == 196 || dz == 197) {
            cageY = 4;
        }

        if (cageY != -1) {
            int[][] lanes = {{-12, -10}, {-1, 1}, {10, 12}};
            for (int[] lane : lanes) {
                int center = (lane[0] + lane[1]) / 2;
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    dm.setDungeonBlock(dx, cageY, dz, (dx == center) ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                    dm.setDungeonBlock(dx, cageY + 4, dz, Material.DARK_PRISMARINE);
                    if (dx != center) {
                        for (int dy = cageY + 1; dy <= cageY + 3; dy++) {
                            dm.setDungeonBlock(dx, dy, dz, Material.IRON_BARS);
                        }
                    }
                }
                for (int cy = cageY + 5; cy <= 15; cy++) {
                    dm.setDungeonBlock(center, cy, dz, Material.IRON_BARS);
                }
            }
        }
    }

    private static void buildDoom2Hall9AbyssalPantheon(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES);
            }
        }
        // Pilares de Sculk e Lanterna Maritima flanqueando a avenida central
        if (dz == 209 || dz == 215 || dz == 221) {
            for (int px : new int[]{-8, 8}) {
                for (int dx = px - 1; dx <= px + 1; dx++) {
                    for (int dy = 5; dy <= 15; dy++) {
                        dm.setDungeonBlock(dx, dy, dz, (dy % 3 == 0) ? Material.SEA_LANTERN : Material.DARK_PRISMARINE);
                    }
                }
            }
        }
    }

    private static void buildDoom2Hall10AbyssalSanctum(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dy < 4) {
                    dm.setDungeonBlock(dx, dy, dz, Material.DEEPSLATE_TILES);
                } else {
                    boolean isRoyalCarpet = Math.abs(dx) <= 3;
                    dm.setDungeonBlock(dx, 4, dz, isRoyalCarpet ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES);
                }
            }
        }

        // 4 Colunas de Lava revestidas em Vidro Ciano (dx = ±15, dz = 230 e 241)
        if (Math.abs(dz - 230) <= 1 || Math.abs(dz - 241) <= 1) {
            int centerZ = (Math.abs(dz - 230) <= 1) ? 230 : 241;
            for (int cornerX : new int[]{-15, 15}) {
                for (int dx = cornerX - 1; dx <= cornerX + 1; dx++) {
                    for (int dy = 5; dy <= 15; dy++) {
                        boolean isCore = (dx == cornerX && dz == centerZ);
                        dm.setDungeonBlock(dx, dy, dz, isCore ? Material.LAVA : Material.CYAN_STAINED_GLASS);
                    }
                }
            }
        }

        // Altar Central do Bau DOOM II (dx = 0, dz = 238)
        int distZ = Math.abs(dz - 238);
        if (distZ <= 2) {
            for (int dx = -2; dx <= 2; dx++) {
                int chebyshev = Math.max(Math.abs(dx), distZ);
                dm.setDungeonBlock(dx, 5, dz, (chebyshev == 2) ? Material.DARK_PRISMARINE : Material.CRYING_OBSIDIAN);
                if (chebyshev <= 1) {
                    dm.setDungeonBlock(dx, 6, dz, (chebyshev == 1) ? Material.SEA_LANTERN : Material.CRYING_OBSIDIAN);
                }
                if (chebyshev == 0) {
                    dm.setDungeonBlock(0, 7, 238, Material.GOLD_BLOCK);
                } else if (Math.abs(dx) == 1 && distZ == 1) {
                    dm.setDungeonBlock(dx, 7, dz, Material.SOUL_LANTERN);
                }
            }
        }
    }

    // =========================================================================
    //  DOOM 3: TEMPLO DO SOL SANGRENTO (10 SALOES EXCLUSIVOS)
    // =========================================================================

    public static void buildDoom3InteriorSlice(DoomManager dm, int dz, int[][] standardThreeDoors) {
        if (dz >= 2 && dz <= 24) {
            buildDoom3Hall1CrimsonLegions(dm, dz, standardThreeDoors);
        } else if (dz == 25 || dz == 26) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 27 && dz <= 55) {
            buildDoom3Hall2XCrossParkour(dm, dz);
        } else if (dz == 56) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 57 && dz <= 80) {
            buildDoom3Hall3BloodCellMaze(dm, dz, standardThreeDoors);
        } else if (dz == 81) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 82 && dz <= 100) {
            buildDoom3Hall4GoldenBeamParkour(dm, dz);
        } else if (dz == 101) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 102 && dz <= 125) {
            buildDoom3Hall5GladiatorArena(dm, dz, standardThreeDoors);
        } else if (dz == 126) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 127 && dz <= 152) {
            buildDoom3Hall6FloatingPyramidsParkour(dm, dz);
        } else if (dz == 153) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 154 && dz <= 177) {
            buildDoom3Hall7LavaAqueducts(dm, dz, standardThreeDoors);
        } else if (dz == 178) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 179 && dz <= 203) {
            buildDoom3Hall8FirePortalParkour(dm, dz);
        } else if (dz == 204) {
            dm.buildPartitionWall(dz, standardThreeDoors, 5, 9, true);
        } else if (dz >= 205 && dz <= 226) {
            buildDoom3Hall9CenturionColonnade(dm, dz);
        } else if (dz == 227) {
            dm.buildPartitionWall(dz, new int[][]{{-4, 4}}, 5, 11, true);
        } else if (dz >= 228 && dz <= 244) {
            buildDoom3Hall10BloodSunSanctum(dm, dz);
        }
    }

    private static void buildDoom3Hall1CrimsonLegions(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 21 && dz <= 24) {
            dm.buildAscendingStairsSlice(dz, 21, exitStairLanes);
            return;
        }
        // Bastiao Central Elevado (dx = -4..+4, dz = 10..15, dy = 1)
        if (dz >= 10 && dz <= 15) {
            for (int dx = -4; dx <= 4; dx++) {
                boolean border = Math.abs(dx) == 4 || dz == 10 || dz == 15;
                dm.setDungeonBlock(dx, 1, dz, border ? Material.GILDED_BLACKSTONE : Material.RED_NETHER_BRICKS);
            }
        }
        // 4 Torres Douradas Carmesins nos cantos (dx = ±10, dz = 6 e 17)
        if (Math.abs(dz - 6) <= 1 || Math.abs(dz - 17) <= 1) {
            for (int tx : new int[]{-10, 10}) {
                for (int dx = tx - 1; dx <= tx + 1; dx++) {
                    for (int dy = 1; dy <= 15; dy++) {
                        dm.setDungeonBlock(dx, dy, dz, (dy % 4 == 0) ? Material.GOLD_BLOCK : Material.RED_NETHER_BRICKS);
                    }
                }
            }
        }
    }

    private static void buildDoom3Hall2XCrossParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 29 || dz >= 53) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 30);

        if (Math.abs(dz - 36) <= 1 || Math.abs(dz - 46) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        dm.setDungeonBlock(dx, 5, dz, Material.RED_NETHER_BRICKS);
                    }
                }
            }
        }

        if (dz == 32 || dz == 33 || dz == 49 || dz == 50) {
            int[][] outerPads = {{-13, -9}, {-2, 2}, {9, 13}};
            for (int[] pad : outerPads) {
                for (int dx = pad[0]; dx <= pad[1]; dx++) {
                    dm.setDungeonBlock(dx, 4, dz, (dx == (pad[0] + pad[1]) / 2) ? Material.GOLD_BLOCK : Material.RED_NETHER_BRICKS);
                }
            }
        } else if (dz == 36 || dz == 37 || dz == 45 || dz == 46) {
            int[][] midPads = {{-10, -6}, {-2, 2}, {6, 10}};
            for (int[] pad : midPads) {
                for (int dx = pad[0]; dx <= pad[1]; dx++) {
                    dm.setDungeonBlock(dx, 5, dz, Material.GILDED_BLACKSTONE);
                }
            }
        } else if (dz >= 40 && dz <= 42) {
            // Grande Cruzamento Central em X + Ilhas Laterais
            for (int dx = -7; dx <= 7; dx++) {
                dm.setDungeonBlock(dx, 5, dz, (Math.abs(dx) <= 1) ? Material.GOLD_BLOCK : Material.RED_NETHER_BRICKS);
            }
            for (int dx : new int[]{-13, -12, -11, -10, 10, 11, 12, 13}) {
                dm.setDungeonBlock(dx, 5, dz, Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
    }

    private static void buildDoom3Hall3BloodCellMaze(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 57 && dz <= 60) {
            dm.buildDescendingStairsSlice(dz, 57);
            return;
        }
        if (dz >= 77 && dz <= 80) {
            dm.buildAscendingStairsSlice(dz, 77, exitStairLanes);
            return;
        }
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz >= 62 && dz <= 75) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                if (isBloodCellWall(dx, dz)) {
                    for (int dy = 1; dy <= 5; dy++) {
                        Material mat = (dy == 3 && (Math.abs(dx) + dz) % 2 == 0) ? Material.IRON_BARS : Material.RED_NETHER_BRICKS;
                        dm.setDungeonBlock(dx, dy, dz, mat);
                    }
                } else if ((dz == 66 || dz == 71) && Math.abs(dx) == 9) {
                    dm.setDungeonBlock(dx, 1, dz, Material.COBWEB);
                }
            }
        }
    }

    private static boolean isBloodCellWall(int dx, int dz) {
        int absX = Math.abs(dx);
        // Paredes divisoras das 9 celas em |dx| = 6 e dz = 66, 71
        if (absX == 6) {
            // Portas laterais entre celas em dz = 63..64, 68..69, 73..74
            return !(dz == 63 || dz == 64 || dz == 68 || dz == 69 || dz == 73 || dz == 74);
        }
        if (dz == 66) {
            // Portas frontais alternadas
            return !(absX >= 2 && absX <= 4) && !(absX >= 11 && absX <= 14);
        }
        if (dz == 71) {
            return !(absX <= 2) && !(absX >= 8 && absX <= 11);
        }
        return false;
    }

    private static void buildDoom3Hall4GoldenBeamParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 84 || dz >= 97) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 85);

        if (dz == 88 || dz == 93) {
            for (int tx : new int[]{-9, 9}) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(tx, dy, dz, Material.GILDED_BLACKSTONE);
                }
            }
        }

        int beamY = -1;
        if (dz == 87 || dz == 88) {
            beamY = 4;
        } else if (dz == 91 || dz == 92) {
            beamY = 5;
        } else if (dz == 94 || dz == 95) {
            beamY = 4;
        }

        if (beamY != -1) {
            int[][] fiveBeams = {{-14, -12}, {-7, -5}, {-1, 1}, {5, 7}, {12, 14}};
            for (int[] beam : fiveBeams) {
                for (int dx = beam[0]; dx <= beam[1]; dx++) {
                    boolean center = (dx == (beam[0] + beam[1]) / 2);
                    dm.setDungeonBlock(dx, beamY, dz, center ? Material.GOLD_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
    }

    private static void buildDoom3Hall5GladiatorArena(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 102 && dz <= 105) {
            dm.buildDescendingStairsSlice(dz, 102);
            return;
        }
        if (dz >= 122 && dz <= 125) {
            dm.buildAscendingStairsSlice(dz, 122, exitStairLanes);
            return;
        }
        // Obeliscos de Sangue e Ouro na Arena (dz = 110 e 117, dx = -6 e +6)
        if (Math.abs(dz - 110) <= 1 || Math.abs(dz - 117) <= 1) {
            for (int ox : new int[]{-6, 6}) {
                for (int dx = ox - 1; dx <= ox + 1; dx++) {
                    dm.setDungeonBlock(dx, 1, dz, Material.RED_NETHER_BRICKS);
                    if (dx == ox && (dz == 110 || dz == 117)) {
                        for (int dy = 2; dy <= 6; dy++) {
                            dm.setDungeonBlock(dx, dy, dz, Material.GILDED_BLACKSTONE);
                        }
                        dm.setDungeonBlock(dx, 7, dz, Material.NETHERRACK);
                        dm.setDungeonBlock(dx, 8, dz, Material.SOUL_FIRE);
                    }
                }
            }
        }
    }

    private static void buildDoom3Hall6FloatingPyramidsParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 129 || dz >= 150) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 130);

        if (Math.abs(dz - 136) <= 1 || Math.abs(dz - 144) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        dm.setDungeonBlock(dx, 6, dz, Material.RED_NETHER_BRICKS);
                    }
                }
            }
        }

        int[][] threePyramidLanes = {{-13, -9}, {-2, 2}, {9, 13}};
        if (dz >= 132 && dz <= 134) {
            for (int[] lane : threePyramidLanes) {
                int cx = (lane[0] + lane[1]) / 2;
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    dm.setDungeonBlock(dx, 4, dz, Material.RED_NETHER_BRICKS);
                    if (dz == 133 && Math.abs(dx - cx) <= 1) {
                        dm.setDungeonBlock(dx, 5, dz, Material.GOLD_BLOCK);
                    }
                }
            }
        } else if ((dz >= 137 && dz <= 139) || (dz >= 142 && dz <= 144)) {
            int centerZ = (dz <= 139) ? 138 : 143;
            for (int[] lane : threePyramidLanes) {
                int cx = (lane[0] + lane[1]) / 2;
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    dm.setDungeonBlock(dx, 5, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                    if (dz == centerZ && Math.abs(dx - cx) <= 1) {
                        dm.setDungeonBlock(dx, 6, dz, Material.GOLD_BLOCK);
                    }
                }
            }
            if (dz == 143) {
                for (int dx : new int[]{-8, -7, -6, -5, -4, -3, 3, 4, 5, 6, 7, 8}) {
                    dm.setDungeonBlock(dx, 5, dz, Material.GILDED_BLACKSTONE);
                }
            }
        } else if (dz == 147 || dz == 148) {
            for (int[] lane : threePyramidLanes) {
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    dm.setDungeonBlock(dx, 4, dz, Material.RED_NETHER_BRICKS);
                }
            }
        }
    }

    private static void buildDoom3Hall7LavaAqueducts(DoomManager dm, int dz, int[][] exitStairLanes) {
        if (dz >= 154 && dz <= 157) {
            dm.buildDescendingStairsSlice(dz, 154);
            return;
        }
        if (dz >= 174 && dz <= 177) {
            dm.buildAscendingStairsSlice(dz, 174, exitStairLanes);
            return;
        }
        // 2 Aquedutos Longitudinais de Lava com pontes transversais abertas em dz = 160, 165, 171
        if (dz != 160 && dz != 165 && dz != 171) {
            for (int aqX : new int[]{-7, 7}) {
                for (int dx = aqX - 1; dx <= aqX + 1; dx++) {
                    boolean isCore = (dx == aqX);
                    dm.setDungeonBlock(dx, 1, dz, isCore ? Material.LAVA : Material.RED_NETHER_BRICKS);
                    dm.setDungeonBlock(dx, 2, dz, isCore ? Material.LAVA : Material.IRON_BARS);
                    dm.setDungeonBlock(dx, 3, dz, Material.RED_NETHER_BRICKS);
                }
            }
        }
    }

    private static void buildDoom3Hall8FirePortalParkour(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        if (dz <= 181 || dz >= 200) {
            for (int dx = -innerWidth; dx <= innerWidth; dx++) {
                for (int dy = 1; dy <= 4; dy++) {
                    dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
            return;
        }

        dm.buildPitRecoveryStairsAndLadders(dz, 182);

        if (Math.abs(dz - 186) <= 1 || Math.abs(dz - 194) <= 1) {
            for (int sideX : new int[]{-17, 17}) {
                for (int dx = sideX - 1; dx <= sideX + 1; dx++) {
                    if (Math.abs(dx) <= innerWidth) {
                        dm.setDungeonBlock(dx, 5, dz, Material.RED_NETHER_BRICKS);
                    }
                }
            }
        }

        int padY = -1;
        if (dz == 184 || dz == 185) {
            padY = 4;
        } else if (dz == 188 || dz == 189) {
            padY = 5;
        } else if (dz == 192 || dz == 193) {
            padY = 5;
        } else if (dz == 196 || dz == 197) {
            padY = 4;
        }

        if (padY != -1) {
            int[][] lanes = {{-12, -10}, {-1, 1}, {10, 12}};
            for (int[] lane : lanes) {
                int center = (lane[0] + lane[1]) / 2;
                for (int dx = lane[0]; dx <= lane[1]; dx++) {
                    dm.setDungeonBlock(dx, padY, dz, (dx == center) ? Material.GOLD_BLOCK : Material.RED_NETHER_BRICKS);
                }
                // Portais de Ouro e Tijolos do Nether (com o meio aberto dy = padY+1..padY+3 para saltar por dentro!)
                if (dz == 185 || dz == 189 || dz == 193 || dz == 197) {
                    for (int dy = padY + 1; dy <= padY + 3; dy++) {
                        dm.setDungeonBlock(lane[0], dy, dz, Material.GILDED_BLACKSTONE);
                        dm.setDungeonBlock(lane[1], dy, dz, Material.GILDED_BLACKSTONE);
                    }
                    for (int dx = lane[0]; dx <= lane[1]; dx++) {
                        dm.setDungeonBlock(dx, padY + 4, dz, Material.GOLD_BLOCK);
                    }
                }
            }
        }
    }

    private static void buildDoom3Hall9CenturionColonnade(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                dm.setDungeonBlock(dx, dy, dz, (dy == 4) ? Material.RED_NETHER_BRICKS : Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        // Colunata Imperial Dourada (dz = 209, 215, 221 em dx = ±9)
        if (dz == 209 || dz == 215 || dz == 221) {
            for (int px : new int[]{-9, 9}) {
                for (int dx = px - 1; dx <= px + 1; dx++) {
                    for (int dy = 5; dy <= 15; dy++) {
                        dm.setDungeonBlock(dx, dy, dz, (dy % 3 == 0) ? Material.GOLD_BLOCK : Material.RED_NETHER_BRICKS);
                    }
                }
            }
        }
    }

    private static void buildDoom3Hall10BloodSunSanctum(DoomManager dm, int dz) {
        int innerWidth = DoomManager.HALF_WIDTH - 2;
        for (int dx = -innerWidth; dx <= innerWidth; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                if (dy < 4) {
                    dm.setDungeonBlock(dx, dy, dz, Material.POLISHED_BLACKSTONE_BRICKS);
                } else {
                    boolean isRoyalCarpet = Math.abs(dx) <= 3;
                    dm.setDungeonBlock(dx, 4, dz, isRoyalCarpet ? Material.GOLD_BLOCK : Material.RED_NETHER_BRICKS);
                }
            }
        }

        // 4 Colunas de Lava revestidas em Vidro Laranja (dx = ±15, dz = 230 e 241)
        if (Math.abs(dz - 230) <= 1 || Math.abs(dz - 241) <= 1) {
            int centerZ = (Math.abs(dz - 230) <= 1) ? 230 : 241;
            for (int cornerX : new int[]{-15, 15}) {
                for (int dx = cornerX - 1; dx <= cornerX + 1; dx++) {
                    for (int dy = 5; dy <= 15; dy++) {
                        boolean isCore = (dx == cornerX && dz == centerZ);
                        dm.setDungeonBlock(dx, dy, dz, isCore ? Material.LAVA : Material.ORANGE_STAINED_GLASS);
                    }
                }
            }
        }

        // Altar Central do Bau DOOM III (dx = 0, dz = 238)
        int distZ = Math.abs(dz - 238);
        if (distZ <= 2) {
            for (int dx = -2; dx <= 2; dx++) {
                int chebyshev = Math.max(Math.abs(dx), distZ);
                dm.setDungeonBlock(dx, 5, dz, (chebyshev == 2) ? Material.RED_NETHER_BRICKS : Material.GILDED_BLACKSTONE);
                if (chebyshev <= 1) {
                    dm.setDungeonBlock(dx, 6, dz, (chebyshev == 1) ? Material.GOLD_BLOCK : Material.CRYING_OBSIDIAN);
                }
                if (chebyshev == 0) {
                    dm.setDungeonBlock(0, 7, 238, Material.GOLD_BLOCK);
                } else if (Math.abs(dx) == 1 && distZ == 1) {
                    dm.setDungeonBlock(dx, 7, dz, Material.SOUL_LANTERN);
                }
            }
        }
    }
}
