package com.strata.runtime;

import com.strata.Strata;
import com.strata.model.SpecialSpawners;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** NUMBERS THE GAME KEEPS IN ITS CODE, READ FROM ITS CLASS FILES. */
public final class GameCode {

    private GameCode() {
    }

    /**
     * The days a player must stay awake before phantoms come: PhantomSpawner#tick compares a draw over the
     * player's Time Since Last Rest with a number of ticks (72000 in 1.21.1), the one whole number of game
     * days it loads.
     */
    public static int phantomRestDays() {
        List<Integer> ints = constants("net/minecraft/world/level/levelgen/PhantomSpawner", "tick");
        List<Integer> days = new ArrayList<>();
        for (int v : ints) {
            if (v > SpecialSpawners.DAY_TICKS && v % SpecialSpawners.DAY_TICKS == 0 && v < Integer.MAX_VALUE) {
                days.add(v / SpecialSpawners.DAY_TICKS);
            }
        }
        if (days.size() == 1) {
            return days.get(0);
        }
        Strata.LOGGER.warn("Could not read the phantom's days awake from PhantomSpawner ({}): using {}", ints,
                SpecialSpawners.GAME_REST_DAYS);
        return SpecialSpawners.GAME_REST_DAYS;
    }

    /**
     * The first day patrols come: PatrolSpawner#tick divides the day time by 24000 and compares the day with
     * a long (5 in 1.21.1), the one it loads other than the day's length.
     */
    public static int patrolFromDay() {
        List<Number> all = numbers("net/minecraft/world/level/levelgen/PatrolSpawner", "tick");
        List<Long> days = new ArrayList<>();
        for (Number n : all) {
            if (n instanceof Long l && l != SpecialSpawners.DAY_TICKS && l >= 0 && l < SpecialSpawners.DAY_TICKS) {
                days.add(l);
            }
        }
        if (days.size() == 1) {
            return days.get(0).intValue();
        }
        Strata.LOGGER.warn("Could not read the patrols' first day from PatrolSpawner ({}): using {}", all,
                SpecialSpawners.GAME_PATROL_DAY);
        return SpecialSpawners.GAME_PATROL_DAY;
    }

    /**
     * One mob's spawn check as the game registers it (SpawnPlacements' static block): the EntityType field,
     * where it may stand (SpawnPlacementTypes field), the ground height it uses (Heightmap.Types), and the
     * check it runs (the method a register call names, "Monster.checkMonsterSpawnRules").
     */
    public record SpawnCheck(String typeField, String placement, String heightmap, String check) {
    }

    /** Every spawn check the game registers, read from SpawnPlacements' class file. */
    public static List<SpawnCheck> spawnChecks() {
        List<SpawnCheck> out = new ArrayList<>();
        try (InputStream in = GameCode.class.getClassLoader()
                .getResourceAsStream("net/minecraft/world/entity/SpawnPlacements.class")) {
            if (in == null) {
                return out;
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    if (!name.equals("<clinit>")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        private final List<String> fields = new ArrayList<>();
                        private String check = "";

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                            if (opcode == Opcodes.GETSTATIC) {
                                fields.add(owner.substring(owner.lastIndexOf('/') + 1) + "." + field);
                            }
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String n, String desc, org.objectweb.asm.Handle bsm,
                                                           Object... args) {
                            for (Object a : args) {
                                if (a instanceof org.objectweb.asm.Handle h && h.getTag() == Opcodes.H_INVOKESTATIC) {
                                    check = h.getOwner().substring(h.getOwner().lastIndexOf('/') + 1) + "." + h.getName();
                                }
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String m, String desc, boolean itf) {
                            if (m.equals("register") && owner.endsWith("SpawnPlacements") && fields.size() >= 3) {
                                List<String> last = fields.subList(fields.size() - 3, fields.size());
                                out.add(new SpawnCheck(after(last.get(0)), after(last.get(1)), after(last.get(2)), check));
                                fields.clear();
                                check = "";
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            Strata.LOGGER.warn("Could not read SpawnPlacements: {}", e.toString());
        }
        return out;
    }

    private static String after(String ownerDotField) {
        return ownerDotField.substring(ownerDotField.indexOf('.') + 1);
    }

    /** The whole numbers (int) a method loads as constants (LDC), in order. */
    public static List<Integer> constants(String internalName, String method) {
        List<Integer> out = new ArrayList<>();
        for (Number n : numbers(internalName, method)) {
            if (n instanceof Integer i) {
                out.add(i);
            }
        }
        return out;
    }

    /** The int and long constants (LDC) a method loads, in order. */
    public static List<Number> numbers(String internalName, String method) {
        List<Number> out = new ArrayList<>();
        try (InputStream in = GameCode.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            if (in == null) {
                return out;
            }
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                                 String[] exceptions) {
                    if (!name.equals(method)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if (value instanceof Integer || value instanceof Long) {
                                out.add((Number) value);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException | RuntimeException e) {
            Strata.LOGGER.warn("Could not read {}: {}", internalName, e.toString());
        }
        return out;
    }
}
