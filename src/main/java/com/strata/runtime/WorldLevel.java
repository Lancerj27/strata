package com.strata.runtime;

import com.strata.Strata;
import com.strata.data.Config;
import com.strata.data.CsvTable;
import com.strata.data.Parse;
import com.strata.data.Schema;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * THE WORLD'S LEVEL: a ladder of stages the whole world climbs as its players progress. The stage gates
 * what players can break, craft and use, what areas are safe to enter, and how hard mobs spawn.
 *
 * <p>The designer's stages and rules come from world_stages.csv and world_stage_rules.csv. The stage reached
 * and each player's progress are saved in the world. Stage 0 is the start; stage N is reached when stage N's
 * conditions are met, in order. A lock holds while the world stage is below the lock's stage.
 */
public final class WorldLevel {

    private WorldLevel() {
    }

    // ---- the kinds of rule ----

    public static final String ADVANCEMENT = "advancement";
    public static final String XP_LEVEL = "xp_level";
    public static final String TIER = "tier";
    public static final String BIOME = "biome";
    public static final String AREA_LEVEL = "area_level";
    public static final String DIMENSION = "dimension";
    public static final String STRUCTURE = "structure";
    public static final String ITEM = "item";
    public static final String KILL_ENTITY = "kill_entity";
    public static final String MOB_KILL_LEVEL = "mob_kill_level";
    public static final String KILL_COUNT = "kill_count";
    public static final String DAYS = "days";
    public static final String LOCK_BLOCK = "lock_block";
    public static final String LOCK_RECIPE = "lock_recipe";
    public static final String LOCK_ITEM = "lock_item";
    public static final String LOCK_TIER = "lock_tier";
    public static final String LOCK_BIOME = "lock_biome";
    public static final String LOCK_AREA_LEVEL = "lock_area_level";
    public static final String LOCK_DIMENSION = "lock_dimension";
    public static final String LOCK_STRUCTURE = "lock_structure";

    /** The condition kinds, in the editor's order. */
    public static final List<String> CONDITIONS = List.of(ADVANCEMENT, XP_LEVEL, TIER, BIOME, AREA_LEVEL, DIMENSION,
            STRUCTURE, ITEM, KILL_ENTITY, MOB_KILL_LEVEL, KILL_COUNT, DAYS);
    /** The lock kinds, in the editor's order. */
    public static final List<String> LOCKS = List.of(LOCK_BLOCK, LOCK_RECIPE, LOCK_ITEM, LOCK_TIER, LOCK_BIOME,
            LOCK_AREA_LEVEL, LOCK_DIMENSION, LOCK_STRUCTURE);
    /** Every kind: the conditions, then the locks. */
    public static final List<String> KINDS;

    static {
        List<String> all = new ArrayList<>(CONDITIONS);
        all.addAll(LOCKS);
        KINDS = List.copyOf(all);
    }

    /** The kinds whose value is a whole number. */
    public static boolean numeric(String kind) {
        return switch (kind) {
            case XP_LEVEL, AREA_LEVEL, MOB_KILL_LEVEL, KILL_COUNT, DAYS, LOCK_AREA_LEVEL -> true;
            default -> false;
        };
    }

    /** The lock kinds that give an effect where they hold (the rule's extra: "effect_id amplifier"). */
    public static boolean areaLock(String kind) {
        return switch (kind) {
            case LOCK_TIER, LOCK_BIOME, LOCK_AREA_LEVEL, LOCK_DIMENSION, LOCK_STRUCTURE -> true;
            default -> false;
        };
    }

    // ---- how a stage's numbers apply ----

    public static final String ADD = "add";
    public static final String SUBTRACT = "subtract";
    public static final String MULTIPLY = "multiply";
    public static final String SCALE_LEVEL = "scale_level";
    /**
     * How a stage's offset and coefficient join the world's running totals once it is reached: add puts them on
     * the totals, subtract takes them off, multiply scales the totals by them. scale_level leaves the totals and
     * instead scales every spawn level by (1 + offset + coefficient x top XP level) while reached.
     */
    public static final List<String> MODES = List.of(ADD, SUBTRACT, MULTIPLY, SCALE_LEVEL);

    /** A running total with one stage's number applied: add, subtract or multiply. */
    public static double apply(String mode, double total, double value) {
        return switch (mode == null ? ADD : mode) {
            case SUBTRACT -> total - value;
            case MULTIPLY -> total * value;
            default -> total + value;
        };
    }

    /**
     * THE WORLD'S TOTALS at a stage: the offset and the coefficient every reached stage put together by its mode
     * (the start's numbers first, as they are), and the factor the scale_level stages multiply a spawn level by.
     */
    public record Totals(double offset, double coefficient, double factor) {
        public static final Totals NONE = new Totals(0, 0, 1);

        /** What the totals add to a level for this top XP level. */
        public double extra(int highestXp) {
            return offset + coefficient * highestXp;
        }
    }

    // ---- the designer's tables ----

    // ---- whether a stage stays, and which player figure the coefficients use ----

    public static final String KEEP = "keep";
    public static final String WHILE_MET = "while_met";
    /** keep: a reached stage stays for good. while_met: the world falls back below it when its conditions stop holding. */
    public static final List<String> HOLDS = List.of(KEEP, WHILE_MET);

    public static final String HIGHEST_EVER = "highest_ever";
    public static final String HIGHEST_ONLINE = "highest_online";
    public static final String AVERAGE_ONLINE = "average_online";
    public static final String TOTAL_ONLINE = "total_online";
    /** The player figure the coefficients multiply, set on the start row. */
    public static final List<String> LEVEL_SOURCES = List.of(HIGHEST_EVER, HIGHEST_ONLINE, AVERAGE_ONLINE, TOTAL_ONLINE);

    /**
     * One stage row; stage 0 is the start, with the numbers and the level source only.
     *
     * @param hold KEEP or WHILE_MET
     * @param levelSource one of LEVEL_SOURCES; read on the start row only
     */
    public record Stage(int stage, String name, boolean allPlayers, boolean allConditions, double mobLevelOffset,
                        double playerLevelCoefficient, String mode, String hold, String levelSource) {

        /** The seven-field form: a stage that stays, the highest level ever. */
        public Stage(int stage, String name, boolean allPlayers, boolean allConditions, double mobLevelOffset,
                     double playerLevelCoefficient, String mode) {
            this(stage, name, allPlayers, allConditions, mobLevelOffset, playerLevelCoefficient, mode, KEEP, HIGHEST_EVER);
        }

        public boolean whileMet() {
            return WHILE_MET.equals(hold);
        }
    }

    /** One rule row: a condition or a lock of a stage. */
    public record Rule(int stage, String kind, String value, String extra) {
        public boolean isLock() {
            return kind.startsWith("lock_");
        }
    }

    /** The two tables as read, and the pure questions asked of them. */
    public record Rules(TreeMap<Integer, Stage> stages, List<Rule> rules) {

        public static final Rules EMPTY = new Rules(new TreeMap<>(), List.of());

        /** The stage after {@code current}, or null at the top of the ladder. */
        public Stage next(int current) {
            Map.Entry<Integer, Stage> e = stages.higherEntry(current);
            return e == null ? null : e.getValue();
        }

        /** The newest stage row reached (at or below current), or null before any. */
        public Stage at(int current) {
            Map.Entry<Integer, Stage> e = stages.floorEntry(current);
            return e == null ? null : e.getValue();
        }

        /** The totals of every stage reached at {@code current}, in order (see {@link Totals}). */
        public Totals totals(int current, int highestXp) {
            double offset = 0;
            double coefficient = 0;
            double factor = 1;
            for (Stage s : stages.headMap(current, true).values()) {
                if (s.stage() == 0) {
                    offset = s.mobLevelOffset();
                    coefficient = s.playerLevelCoefficient();
                } else if (SCALE_LEVEL.equals(s.mode())) {
                    factor *= 1 + s.mobLevelOffset() + s.playerLevelCoefficient() * highestXp;
                } else {
                    offset = apply(s.mode(), offset, s.mobLevelOffset());
                    coefficient = apply(s.mode(), coefficient, s.playerLevelCoefficient());
                }
            }
            return new Totals(offset, coefficient, factor);
        }

        /** The total mob level offset at a stage. */
        public double mobLevelOffset(int current) {
            return totals(current, 0).offset();
        }

        /** The total player level coefficient at a stage. */
        public double playerLevelCoefficient(int current) {
            return totals(current, 0).coefficient();
        }

        /** The conditions of one stage. */
        public List<Rule> conditions(int stage) {
            return rules.stream().filter(r -> r.stage() == stage && !r.isLock()).toList();
        }

        /** The locks of a kind still holding at {@code current}: those of every stage above it. */
        public List<Rule> locks(String kind, int current) {
            return rules.stream().filter(r -> r.stage() > current && kind.equals(r.kind())).toList();
        }

        /** The player figure the coefficients multiply: the start row's level source, highest ever by default. */
        public String levelSource() {
            Stage start = stages.get(0);
            return start == null || !LEVEL_SOURCES.contains(start.levelSource()) ? HIGHEST_EVER : start.levelSource();
        }

        /**
         * Whether the stage after {@code current} is reached by the players given (the online ones). A stage
         * with no conditions is reached by the command only.
         */
        public boolean reached(int current, Collection<Facts> online) {
            Stage next = next(current);
            return next != null && met(next, online);
        }

        /**
         * Whether a stage's conditions hold for these players: by what they have ever done for a stage that
         * keeps, by where they are and what they have now for one held while met.
         */
        public boolean met(Stage stage, Collection<Facts> online) {
            if (online.isEmpty()) {
                return false;
            }
            List<Rule> conditions = conditions(stage.stage());
            if (conditions.isEmpty()) {
                return false;
            }
            boolean now = stage.whileMet();
            int met = 0;
            for (Rule c : conditions) {
                boolean ok = stage.allPlayers()
                        ? online.stream().allMatch(f -> now ? f.meetsNow(c) : f.meets(c))
                        : online.stream().anyMatch(f -> now ? f.meetsNow(c) : f.meets(c));
                if (ok) {
                    met++;
                }
            }
            return stage.allConditions() ? met == conditions.size() : met > 0;
        }

        /**
         * The stage the world settles at from {@code current}: down past every while_met stage whose conditions
         * no longer hold, then up through every stage now reached.
         */
        public int settle(int current, Collection<Facts> online) {
            int stage = current;
            while (stage > 0) {
                Stage s = stages.get(stage);
                // A while_met stage with no conditions is the command's alone: it is not dropped.
                if (s != null && s.whileMet() && !conditions(stage).isEmpty() && !met(s, online)) {
                    Map.Entry<Integer, Stage> below = stages.lowerEntry(stage);
                    stage = below == null || below.getKey() == 0 ? 0 : below.getKey();
                } else {
                    break;
                }
            }
            while (reached(stage, online)) {
                stage = next(stage).stage();
            }
            return stage;
        }
    }

    /** One player's progress: what they have done, the highest numbers they have reached, and where they are now. */
    public static final class Facts {
        final Set<String> done = new HashSet<>();
        int xpLevel;
        int killLevel;
        int areaLevel;
        int kills;
        int days;
        /** Now, not saved: the XP level, the area's level, and where the player stands. */
        int xpNow;
        int areaNow;
        final Set<String> here = new HashSet<>();

        public Facts() {
        }

        public Facts(Collection<String> done, int xpLevel, int killLevel) {
            this.done.addAll(done);
            this.xpLevel = xpLevel;
            this.killLevel = killLevel;
        }

        public Facts(Collection<String> done, int xpLevel, int killLevel, int areaLevel, int kills, int days) {
            this(done, xpLevel, killLevel);
            this.areaLevel = areaLevel;
            this.kills = kills;
            this.days = days;
        }

        /** Whether this player meets one condition. */
        public boolean meets(Rule c) {
            return switch (c.kind()) {
                case XP_LEVEL -> xpLevel >= Parse.integer(c.value(), Integer.MAX_VALUE);
                case MOB_KILL_LEVEL -> killLevel >= Parse.integer(c.value(), Integer.MAX_VALUE);
                case AREA_LEVEL -> areaLevel >= Parse.integer(c.value(), Integer.MAX_VALUE);
                case KILL_COUNT -> kills >= Parse.integer(c.value(), Integer.MAX_VALUE);
                case DAYS -> days >= Parse.integer(c.value(), Integer.MAX_VALUE);
                default -> done.contains(c.kind() + ":" + c.value());
            };
        }

        /** Whether this player meets one condition NOW: their level and place as they stand, not their best. */
        public boolean meetsNow(Rule c) {
            return switch (c.kind()) {
                case XP_LEVEL -> xpNow >= Parse.integer(c.value(), Integer.MAX_VALUE);
                case AREA_LEVEL -> areaNow >= Parse.integer(c.value(), Integer.MAX_VALUE);
                case TIER, BIOME, DIMENSION, STRUCTURE -> here.contains(c.kind() + ":" + c.value());
                default -> meets(c);
            };
        }

        /** Sets the now values, for a while_met stage. */
        public void now(int xp, int area, Collection<String> placeKeys) {
            xpNow = xp;
            areaNow = area;
            here.clear();
            here.addAll(placeKeys);
        }

        /** Records a done thing; true when it is new. */
        boolean did(String kind, String value) {
            return done.add(kind + ":" + value);
        }
    }

    private static volatile Rules rules = Rules.EMPTY;

    /** The stage the open world has reached, for the hooks that run every tick; 0 with no world. */
    private static volatile int current;

    /** The players' level figure the coefficients multiply, by the start row's level source; 0 with no world. */
    private static volatile int highestXp;

    /** Reads the two tables (Config.reload). */
    public static void load(Path stages, Path ruleRows) {
        rules = read(stages, ruleRows);
    }

    /** The two tables, parsed; blank or unknown rows are skipped. */
    public static Rules read(Path stages, Path ruleRows) {
        TreeMap<Integer, Stage> byStage = new TreeMap<>();
        for (Map<String, String> row : CsvTable.read(stages)) {
            int stage = Parse.integer(row.get(Schema.WorldStages.STAGE), -1);
            if (stage < 0) continue;
            String mode = row.getOrDefault(Schema.WorldStages.MODE, "").strip().toLowerCase(Locale.ROOT);
            String hold = row.getOrDefault(Schema.WorldStages.HOLD, "").strip().toLowerCase(Locale.ROOT);
            String source = row.getOrDefault(Schema.WorldStages.LEVEL_SOURCE, "").strip().toLowerCase(Locale.ROOT);
            byStage.put(stage, new Stage(stage, row.getOrDefault(Schema.WorldStages.NAME, "").strip(),
                    "all".equalsIgnoreCase(row.getOrDefault(Schema.WorldStages.PLAYERS, "").strip()),
                    !"any".equalsIgnoreCase(row.getOrDefault(Schema.WorldStages.CONDITIONS, "").strip()),
                    Parse.number(row.get(Schema.WorldStages.MOB_LEVEL_OFFSET), 0.0),
                    Parse.number(row.get(Schema.WorldStages.PLAYER_LEVEL_COEFFICIENT), 0.0),
                    MODES.contains(mode) ? mode : ADD, HOLDS.contains(hold) ? hold : KEEP,
                    LEVEL_SOURCES.contains(source) ? source : HIGHEST_EVER));
        }
        List<Rule> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.read(ruleRows)) {
            int stage = Parse.integer(row.get(Schema.WorldStageRules.STAGE), 0);
            String kind = row.getOrDefault(Schema.WorldStageRules.KIND, "").strip().toLowerCase(Locale.ROOT);
            String value = row.getOrDefault(Schema.WorldStageRules.VALUE, "").strip();
            if (stage <= 0 || !KINDS.contains(kind) || value.isEmpty()) continue;
            out.add(new Rule(stage, kind, value, row.getOrDefault(Schema.WorldStageRules.EXTRA, "").strip()));
        }
        return new Rules(byStage, List.copyOf(out));
    }

    public static Rules rules() {
        return rules;
    }

    /** The stage the open world is at. */
    public static int stage() {
        return current;
    }

    /** The players' level figure the coefficients multiply (the start row's level source). */
    public static int highestPlayerLevel() {
        return highestXp;
    }

    /**
     * WHAT THE WORLD ADDS to a spawn level: the total offset, plus the total player level coefficient times the
     * highest XP level any player has reached.
     */
    public static double extra() {
        return rules.totals(current, highestXp).extra(highestXp);
    }

    /** THE WORLD LEVEL, as a number: the stage plus what it adds. */
    public static double level() {
        return current + extra();
    }

    /** A spawn level with the world's progress on it: the totals added, then the scale_level stages' factor. */
    public static double applyToSpawn(double level, double more) {
        Totals t = rules.totals(current, highestXp);
        return (level + t.extra(highestXp) + more) * t.factor();
    }

    /** Whether a recipe is still locked at the world's stage. */
    public static boolean recipeLocked(ResourceLocation recipe) {
        return idLocked(LOCK_RECIPE, recipe.toString());
    }

    /** Whether an item is still locked at the world's stage: it cannot be used. */
    public static boolean itemLocked(ItemStack stack) {
        return !stack.isEmpty() && idLocked(LOCK_ITEM, stack.getItemHolder().unwrapKey()
                .map(k -> k.location().toString()).orElse(""));
    }

    private static boolean idLocked(String kind, String id) {
        for (Rule r : rules.locks(kind, current)) {
            if (r.value().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block is still locked at the world's stage: by id, or by "#tag". */
    public static boolean blockLocked(BlockState state) {
        for (Rule r : rules.locks(LOCK_BLOCK, current)) {
            if (r.value().startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(r.value().substring(1));
                if (tag != null && state.is(TagKey.create(Registries.BLOCK, tag))) {
                    return true;
                }
            } else if (state.getBlockHolder().unwrapKey().map(k -> k.location().toString()).orElse("").equals(r.value())) {
                return true;
            }
        }
        return false;
    }

    // ---- the saved state ----

    /** The world's stage and each player's progress. */
    public static final class State extends SavedData {
        int stage;
        final Map<UUID, Facts> players = new HashMap<>();

        static State load(CompoundTag tag, HolderLookup.Provider registries) {
            State state = new State();
            state.stage = tag.getInt("stage");
            CompoundTag players = tag.getCompound("players");
            for (String key : players.getAllKeys()) {
                CompoundTag p = players.getCompound(key);
                List<String> done = new ArrayList<>();
                for (Tag t : p.getList("done", Tag.TAG_STRING)) {
                    done.add(t.getAsString());
                }
                try {
                    state.players.put(UUID.fromString(key), new Facts(done, p.getInt("xp"), p.getInt("kill"),
                            p.getInt("area"), p.getInt("kills"), p.getInt("days")));
                } catch (IllegalArgumentException ignored) {
                    // Not a player id: a hand-edited file. The row is dropped.
                }
            }
            return state;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putInt("stage", stage);
            CompoundTag players = new CompoundTag();
            this.players.forEach((id, facts) -> {
                CompoundTag p = new CompoundTag();
                ListTag done = new ListTag();
                for (String s : facts.done) {
                    done.add(StringTag.valueOf(s));
                }
                p.put("done", done);
                p.putInt("xp", facts.xpLevel);
                p.putInt("kill", facts.killLevel);
                p.putInt("area", facts.areaLevel);
                p.putInt("kills", facts.kills);
                p.putInt("days", facts.days);
                players.put(id.toString(), p);
            });
            tag.put("players", players);
            return tag;
        }
    }

    private static final SavedData.Factory<State> FACTORY = new SavedData.Factory<>(State::new, State::load, null);

    private static State state(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "strata_world_level");
    }

    private static Facts facts(ServerPlayer player) {
        return state(player.server).players.computeIfAbsent(player.getUUID(), k -> new Facts());
    }

    /** The players' level figure, by the start row's level source: the saved best, or the online players now. */
    private static int highest(MinecraftServer server, State state) {
        String source = rules.levelSource();
        if (HIGHEST_EVER.equals(source)) {
            int top = 0;
            for (Facts f : state.players.values()) {
                top = Math.max(top, f.xpLevel);
            }
            return top;
        }
        int top = 0;
        int total = 0;
        int count = 0;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            top = Math.max(top, p.experienceLevel);
            total += p.experienceLevel;
            count++;
        }
        return switch (source) {
            case TOTAL_ONLINE -> total;
            case AVERAGE_ONLINE -> count == 0 ? 0 : Math.round((float) total / count);
            default -> top;
        };
    }

    /** Sets the world's stage by hand (the command), and says what it is now. */
    public static int setStage(MinecraftServer server, int stage) {
        State state = state(server);
        state.stage = Math.max(0, stage);
        state.setDirty();
        current = state.stage;
        return current;
    }

    /**
     * After a fact changed: settles the stage (down past while_met stages no longer held, up through every stage
     * now reached) and tells the players when it moved.
     */
    private static void climb(MinecraftServer server) {
        State state = state(server);
        List<Facts> online = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            online.add(state.players.computeIfAbsent(p.getUUID(), k -> new Facts()));
        }
        highestXp = highest(server, state);
        int was = state.stage;
        state.stage = rules.settle(state.stage, online);
        if (state.stage != was) {
            current = state.stage;
            Stage s = rules.stages.get(state.stage);
            String name = s == null || s.name().isEmpty() ? "stage " + state.stage : s.name();
            String words = state.stage > was ? "The world reached " + name : "The world fell back to " + name;
            Strata.LOGGER.info("Strata: {}", words);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(words), true);
            }
        }
        state.setDirty();
    }

    // ---- the hooks (game bus) ----

    public static void onServerStarted(ServerStartedEvent event) {
        State state = state(event.getServer());
        current = state.stage;
        highestXp = highest(event.getServer(), state);
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        current = 0;
        highestXp = 0;
    }

    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && facts(player).did(ADVANCEMENT, event.getAdvancement().id().toString())) {
            climb(player.server);
        }
    }

    public static void onLevelChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Facts f = facts(player);
            int now = player.experienceLevel + event.getLevels();
            if (now > f.xpLevel) {
                f.xpLevel = now;
                climb(player.server);
            }
        }
    }

    /** A kill: the mob's kind, its Strata level, and one more on the count. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && event.getSource().getEntity() instanceof ServerPlayer player) {
            Facts f = facts(player);
            f.kills++;
            f.did(KILL_ENTITY, mob.getType().builtInRegistryHolder().key().location().toString());
            int level = mob.getData(MobLevel.MOB_LEVEL).level();
            if (level > f.killLevel) {
                f.killLevel = level;
            }
            climb(player.server);
        }
    }

    private static int nextStage() {
        Stage next = rules.next(current);
        return next == null ? -1 : next.stage();
    }

    /**
     * Every second: where the player is (the area's biome, tier, level, dimension and structures), what they
     * carry, the day, their XP level; then the effects of the locked areas they stand in.
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Tick 1 is a player's first: their now values are set before any climb can count them absent.
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 1
                || !(player.level() instanceof ServerLevel level) || rules.stages.isEmpty()) {
            return;
        }
        String biome = level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.location().toString()).orElse("");
        var resolution = Config.resolve(biome);
        String tier = resolution.tier();
        int area = resolution.level();
        String dimension = level.dimension().location().toString();
        Set<String> structures = structuresAt(level, player);
        int day = (int) (level.getDayTime() / 24000L);

        Facts f = facts(player);
        List<String> place = new ArrayList<>();
        place.add(BIOME + ":" + biome);
        place.add(DIMENSION + ":" + dimension);
        if (tier != null && !tier.isEmpty()) {
            place.add(TIER + ":" + tier);
        }
        for (String s : structures) {
            place.add(STRUCTURE + ":" + s);
        }
        f.now(player.experienceLevel, area, place);
        // A live figure or a stage held while met follows the players every second.
        boolean changed = !HIGHEST_EVER.equals(rules.levelSource())
                || rules.stages.values().stream().anyMatch(Stage::whileMet);
        changed |= f.did(BIOME, biome) | f.did(DIMENSION, dimension);
        if (tier != null && !tier.isEmpty()) {
            changed |= f.did(TIER, tier);
        }
        for (String s : structures) {
            changed |= f.did(STRUCTURE, s);
        }
        if (player.experienceLevel > f.xpLevel) {
            f.xpLevel = player.experienceLevel;
            changed = true;
        }
        if (area > f.areaLevel) {
            f.areaLevel = area;
            changed = true;
        }
        if (day > f.days) {
            f.days = day;
            changed = true;
        }
        // The items the next stage asks for, if the player carries one.
        for (Rule c : rules.conditions(nextStage())) {
            if (ITEM.equals(c.kind()) && carries(player, c.value())) {
                changed |= f.did(ITEM, c.value());
            }
        }
        if (changed) {
            climb(player.server);
        }

        if (!player.isCreative() && !player.isSpectator()) {
            for (Rule r : rules.locks(LOCK_BIOME, current)) {
                if (r.value().equals(biome)) affect(player, r.extra());
            }
            for (Rule r : rules.locks(LOCK_TIER, current)) {
                if (tier != null && r.value().equals(tier)) affect(player, r.extra());
            }
            for (Rule r : rules.locks(LOCK_DIMENSION, current)) {
                if (r.value().equals(dimension)) affect(player, r.extra());
            }
            for (Rule r : rules.locks(LOCK_AREA_LEVEL, current)) {
                if (area >= Parse.integer(r.value(), Integer.MAX_VALUE)) affect(player, r.extra());
            }
            for (Rule r : rules.locks(LOCK_STRUCTURE, current)) {
                if (structures.contains(r.value())) affect(player, r.extra());
            }
        }
    }

    /** The ids of the structures whose bounds hold the player. */
    private static Set<String> structuresAt(ServerLevel level, ServerPlayer player) {
        Set<String> out = new HashSet<>();
        try {
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (Structure s : level.structureManager().getAllStructuresAt(player.blockPosition()).keySet()) {
                ResourceLocation id = registry.getKey(s);
                if (id != null) {
                    out.add(id.toString());
                }
            }
        } catch (RuntimeException ignored) {
            // A level with no structure manager yet: nothing here this second.
        }
        return out;
    }

    /** Whether the player carries the item, in the inventory or the off hand. */
    private static boolean carries(ServerPlayer player, String itemId) {
        for (ItemStack stack : player.getInventory().items) {
            if (isItem(stack, itemId)) {
                return true;
            }
        }
        return isItem(player.getOffhandItem(), itemId);
    }

    private static boolean isItem(ItemStack stack, String itemId) {
        return !stack.isEmpty() && stack.getItemHolder().unwrapKey().map(k -> k.location().toString()).orElse("")
                .equals(itemId);
    }

    /** Gives the effect of a locked area's row, "effect_id amplifier", for a little over a second. */
    private static void affect(ServerPlayer player, String extra) {
        String[] parts = extra.trim().split("\\s+");
        ResourceLocation id = parts[0].isEmpty() ? null : ResourceLocation.tryParse(parts[0]);
        if (id == null) {
            return;
        }
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
        if (effect == null) {
            return;
        }
        int amplifier = parts.length > 1 ? Math.max(0, Parse.integer(parts[1], 0)) : 0;
        player.addEffect(new MobEffectInstance(effect, 30, amplifier, true, false));
    }

    /** A locked block does not break for a survival player. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !player.isCreative() && blockLocked(event.getState())) {
            event.setCanceled(true);
        }
    }

    /** A locked item does nothing when used, on the air or on a block, for a survival player. */
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isCreative() && itemLocked(event.getItemStack())) {
            event.setCanceled(true);
        }
    }

    public static void onUseItemOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isCreative() && itemLocked(event.getItemStack())) {
            // The item alone is held back: the block (a chest, a door) still opens.
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
        }
    }
}
