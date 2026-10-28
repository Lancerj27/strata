package com.strata.domain;

import com.strata.data.CsvTable;
import com.strata.data.GameLayer;
import com.strata.data.Paths;
import com.strata.data.Schema;
import com.strata.data.TableWriter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Every extracted value of one mob, with the user's edits on top. */
public final class MobDataOps {

    private MobDataOps() {
    }

    /** The groups, in the order the editor shows them. */
    public static final List<String> GROUPS = List.of(
            GameLayer.ATTRIBUTE, GameLayer.TYPE, GameLayer.FIELD, GameLayer.TAG, GameLayer.MODEL,
            GameLayer.SOUNDS, GameLayer.VISUALS, GameLayer.PHYSICS, GameLayer.FLAGS,
            GameLayer.COMBAT, GameLayer.PARTICLES, GameLayer.LOOT_XP, GameLayer.BOSS, GameLayer.PATHFINDING);

    // ---- THE MOB'S SWITCHES: Category, Mob griefing, and Spawn as head the Attributes tab as dropdowns
    // ("make them a top row...

    /** The dropdowns' names, by key. */
    public static String switchName(String key) {
        return switch (key) {
            case CATEGORY -> "Category";
            case MOB_GRIEFING -> "Mob griefing";
            case PASSIVE -> "Passive";
            case CONVERTS -> "Converts";
            case SPAWN_AS -> "Spawn as";
            case BOSS -> "Boss";
            default -> "Scaling";
        };
    }

    /** The switches Mob data shows: every Spawning value but Scaling, which the mob editor has. */
    public static List<Value> switches(Target t, String scope, String scopeId) {
        return spawning(t, scope, scopeId).stream().filter(v -> !v.key().equals(SCALING)).toList();
    }

    /** The Spawning tab's group. */
    public static final String SPAWNING = "spawning";
    public static final String CATEGORY = "category";
    /** Where a mob type may spawn: the game's four placement types, as the game layer names them. */
    public static final List<String> SPAWN_PLACEMENTS = List.of("on_ground", "in_water", "in_lava", "no_restrictions");
    public static final String SCALING = "scaling";
    public static final String MOB_GRIEFING = "mob_griefing";
    public static final String PASSIVE = "passive";
    public static final String CONVERTS = "converts";
    public static final String SPAWN_AS = "spawn_as";
    public static final String BOSS = "boss";

    /** The Spawning tab's values for a mob or variant. */
    public static List<Value> spawning(Target t, String scope, String scopeId) {
        List<Value> out = new ArrayList<>();
        String entity = t.entity();
        String gameWord = CategoryOps.word(CategoryOps.gameCategory(entity));
        if (t.baseMob()) {
            String shown = CategoryOps.shownWord(entity);
            out.add(new Value(SPAWNING, CATEGORY, gameWord, "", shown.equals(gameWord) ? "" : shown));
        } else {
            String base = CategoryOps.word(CategoryOps.categoryOf(entity));
            String own = CategoryOps.word(CategoryOps.categoryOf(entity, t.variant()));
            out.add(new Value(SPAWNING, CATEGORY, gameWord, base.equals(gameWord) ? "" : base,
                    own.equals(base) ? "" : own));
        }
        for (String[] k : new String[][]{{SCALING, Schema.Variants.SCALING}, {MOB_GRIEFING, Schema.Variants.MOB_GRIEFING},
                {PASSIVE, Schema.Variants.PASSIVE}, {CONVERTS, Schema.Variants.CONVERTS}}) {
            String word = VariantOps.switchOf(entity, t.variant(), scope, scopeId, k[1]);
            out.add(new Value(SPAWNING, k[0], "Default", "", word.equals("Default") ? "" : word));
        }
        // The Boss flag is a property (mob_flags.csv), not a variants.csv switch, so it is read from its layer.
        out.add(new Value(SPAWNING, BOSS, "Default", "", bossOn(t) ? "On" : ""));
        if (SpecialSpawnsOps.flagged(entity) && !t.scoped()) {
            String base = SpecialSpawnsOps.spawnAsWord(entity);
            if (t.baseMob()) {
                String game = com.strata.model.SpecialSpawners.SPECIAL_BY_DEFAULT.contains(entity) ? "Special" : "Normal";
                out.add(new Value(SPAWNING, SPAWN_AS, game, "", base.equals(game) ? "" : base));
            } else {
                String own = SpecialSpawnsOps.spawnAsWord(entity, t.variant());
                out.add(new Value(SPAWNING, SPAWN_AS, base, "", own.equals(base) ? "" : own));
            }
        }
        return out;
    }

    /** The boss flag effective for this target, read from the flags layer (mob_flags.csv). */
    public static boolean bossOn(Target t) {
        Map<String, String> base = new HashMap<>();
        Map<String, String> global = new HashMap<>();
        Map<String, String> own = new HashMap<>();
        String k = key(GameLayer.FLAGS, Schema.MobFlags.BOSS);
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobFlags.FILE))) {
            if (!t.entity().equals(cell(row, Schema.MobSounds.ENTITY))) {
                continue;
            }
            Map<String, String> into = layer(t, cell(row, Schema.MobSounds.VARIANT_NAME),
                    cell(row, Schema.MobSounds.SCOPE), cell(row, Schema.MobSounds.SCOPE_ID), base, global, own);
            String v = cell(row, Schema.MobFlags.BOSS);
            if (!v.isEmpty()) {
                into.put(k, v);
            }
        }
        base.putAll(global);
        String o = own.getOrDefault(k, "");
        String b = base.getOrDefault(k, "");
        return "true".equals(!o.isEmpty() ? o : b);
    }

    /** A Spawning value's choices, as the picker shows them. */
    public static List<String> spawningChoices(Target t, String key) {
        return switch (key) {
            case CATEGORY -> t.baseMob() ? CategoryOps.CHOICES
                    : CategoryOps.CHOICES.stream().filter(w -> !w.equals(CategoryOps.SPECIAL_WORD)).toList();
            case SPAWN_AS -> com.strata.model.SpecialSpawners.SPAWN_AS;
            case BOSS -> List.of("Default", "On", "Off");
            default -> VariantOps.SWITCH_CHOICES;
        };
    }

    /** What a Spawning value does, for the list. */
    public static String spawningHelp(String key) {
        return switch (key) {
            case CATEGORY -> CategoryOps.HELP;
            case SCALING -> VariantOps.SCALING_HELP;
            case MOB_GRIEFING -> VariantOps.MOB_GRIEFING_HELP;
            case PASSIVE -> VariantOps.PASSIVE_HELP;
            case CONVERTS -> VariantOps.CONVERTS_HELP;
            case SPAWN_AS -> SpecialSpawnsOps.SPAWN_AS_HELP;
            case BOSS -> "On: the game shows a boss health bar to nearby players and plays its music; the bar and"
                    + " music are set on the Boss tab. Default and Off: an ordinary mob.";
            default -> "";
        };
    }

    /** Saves a Spawning value where the mob editor kept it. */
    public static WriteReport saveSpawning(Target t, String scope, String scopeId, String key, String word) {
        return switch (key) {
            case CATEGORY -> CategoryOps.setCategory(t.entity(), t.variant(), word).needsReload(CategoryOps.RELOAD_NOTE);
            case SCALING -> VariantOps.saveSwitch(t.entity(), t.variant(), scope, scopeId, Schema.Variants.SCALING, word);
            case MOB_GRIEFING -> VariantOps.saveSwitch(t.entity(), t.variant(), scope, scopeId,
                    Schema.Variants.MOB_GRIEFING, word);
            case PASSIVE -> VariantOps.saveSwitch(t.entity(), t.variant(), scope, scopeId,
                    Schema.Variants.PASSIVE, word);
            case CONVERTS -> VariantOps.saveSwitch(t.entity(), t.variant(), scope, scopeId,
                    Schema.Variants.CONVERTS, word);
            case SPAWN_AS -> SpecialSpawnsOps.setSpawnAs(t.entity(), t.variant(), word);
            case BOSS -> {
                Value v = new Value(GameLayer.FLAGS, Schema.MobFlags.BOSS, "", "", "");
                yield switch (word) {
                    case "On" -> save(t, v, "true");
                    case "Off" -> save(t, v, "false");
                    default -> restore(t, v);
                };
            }
            default -> WriteReport.refused("Nothing to set");
        };
    }

    /** Puts a Spawning value back to what it inherits: the base mob's, the game's, or Default. */
    public static WriteReport restoreSpawning(Target t, String scope, String scopeId, Value v) {
        String back = !v.base().isEmpty() ? v.base() : v.game();
        return saveSpawning(t, scope, scopeId, v.key(), back);
    }

    /**
     * One value of the mob.
     *
     * @param game the game's value, from the game layer
     * @param base the value beneath this mob's own edit: the base mob's edit, and for a tier or biome
     *     instance the variant's global one over that, or ""
     * @param own this mob's own edit, or ""
     */
    public record Value(String group, String key, String game, String base, String own) {
        public String effective() {
            return !own.isEmpty() ? own : !base.isEmpty() ? base : game;
        }

        /** Where the value in effect comes from: "game", "inherited", or "edited". */
        public String source() {
            return !own.isEmpty() ? "edited" : !base.isEmpty() ? "inherited" : "game";
        }
    }

    /** The mob's values, and whether the game layer exists at all. */
    public record Sheet(boolean extracted, List<Value> values) {
    }

    /** Which mob the editor edits: a base mob (variant "") or one variant instance. */
    public record Target(String entity, String variant, String scope, String scopeId) {
        public Target {
            entity = entity == null ? "" : entity.strip();
            variant = variant == null ? "" : variant.strip();
            scope = variant.isEmpty() ? "" : VariantOps.normalScope(scope);
            scopeId = variant.isEmpty() ? "" : VariantOps.normalScopeId(scope, scopeId);
        }

        public boolean baseMob() {
            return variant.isEmpty();
        }

        /** A tier or biome instance, which inherits the variant's global one. */
        public boolean scoped() {
            return !variant.isEmpty() && !scope.isEmpty();
        }
    }

    public static Sheet sheet(Target t) {
        Path file = GameLayer.dir().resolve(GameLayer.MOBS);
        List<GameLayer.Row> game = new ArrayList<>();
        for (GameLayer.Row r : GameLayer.read(file)) {
            if (r.entity().equals(t.entity())) {
                game.add(r);
            }
        }
        // The texture, read by the client into its own sheet.
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.TEXTURES))) {
            if (r.entity().equals(t.entity())) {
                game.add(r);
            }
        }
        Map<String, String> base = new HashMap<>();
        Map<String, String> global = new HashMap<>();
        Map<String, String> own = new HashMap<>();
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobData.FILE))) {
            if (t.entity().equals(cell(row, Schema.MobData.ENTITY))) {
                layer(t, cell(row, Schema.MobData.VARIANT_NAME), cell(row, Schema.MobData.SCOPE),
                        cell(row, Schema.MobData.SCOPE_ID), base, global, own)
                        .put(key(cell(row, Schema.MobData.GROUP), cell(row, Schema.MobData.KEY)),
                                cell(row, Schema.MobData.VALUE));
            }
        }
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.MobBaseStats.FILE))) {
            if (t.entity().equals(cell(row, Schema.MobBaseStats.ENTITY))) {
                base.put(key(GameLayer.ATTRIBUTE, cell(row, Schema.MobBaseStats.ATTRIBUTE)),
                        cell(row, Schema.MobBaseStats.VALUE));
            }
        }
        // A variant's attribute is its absolute stat, in the variant stats table.
        for (Map<String, String> row : CsvTable.read(Paths.table(Schema.VariantStats.FILE))) {
            String absolute = cell(row, Schema.VariantStats.ABSOLUTE);
            if (!absolute.isEmpty()) {
                layer(t, cell(row, Schema.VariantStats.VARIANT_NAME), cell(row, Schema.VariantStats.SCOPE),
                        cell(row, Schema.VariantStats.SCOPE_ID), new HashMap<>(), global, own)
                        .put(key(GameLayer.ATTRIBUTE, cell(row, Schema.VariantStats.ATTRIBUTE)), absolute);
            }
        }
        base.putAll(global);

        // THE EIGHT PROPERTY TABLES, layer by layer as every other table here: a base mob's row is the base,
        // the variant's Global row is global, the target's own is own.
        for (Map.Entry<String, String> table : Schema.MOB_PROPERTY_FILES.entrySet()) {
            List<Schema.Prop> props = Schema.mobProperties(table.getKey());
            for (Map<String, String> row : CsvTable.read(Paths.table(table.getValue()))) {
                if (!t.entity().equals(cell(row, Schema.MobSounds.ENTITY))) {
                    continue;
                }
                Map<String, String> into = layer(t, cell(row, Schema.MobSounds.VARIANT_NAME),
                        cell(row, Schema.MobSounds.SCOPE), cell(row, Schema.MobSounds.SCOPE_ID), base, global, own);
                for (Schema.Prop p : props) {
                    String v = cell(row, p.column());
                    if (!v.isEmpty()) {
                        into.put(key(table.getKey(), p.column()), v);
                    }
                }
            }
        }

        base.putAll(global);
        return new Sheet(!game.isEmpty() || java.nio.file.Files.isRegularFile(file),
                textureFollowsModel(withSetProperties(merge(game, base, own), base, own), t.entity(), modelTextures()));
    }

    /** A PROPERTY SOMEONE HAS SET SHOWS AS SET. */
    static List<Value> withSetProperties(List<Value> values, Map<String, String> base, Map<String, String> own) {
        List<Value> out = new ArrayList<>(values);
        for (Schema.Prop p : Schema.MOB_PROPERTIES) {
            String k = key(p.group(), p.column());
            boolean has = values.stream().anyMatch(v -> v.group().equals(p.group()) && v.key().equals(p.column()));
            String b = base.getOrDefault(k, "");
            String o = own.getOrDefault(k, "");
            if (!has && (!b.isEmpty() || !o.isEmpty())) {
                out.add(new Value(p.group(), p.column(), "", b, o));
            }
        }
        return out;
    }

    /** Each mob's own texture, from the client's sheet (game/textures.csv). */
    private static Map<String, String> modelTextures() {
        Map<String, String> out = new HashMap<>();
        for (GameLayer.Row r : GameLayer.read(GameLayer.dir().resolve(GameLayer.TEXTURES))) {
            out.put(r.entity(), r.value());
        }
        return out;
    }

    /** THE TEXTURE FOLLOWS THE MODEL (as MobModels#texture draws it). */
    public static List<Value> textureFollowsModel(List<Value> values, String entity, Map<String, String> textures) {
        Value model = null;
        for (Value v : values) {
            if (GameLayer.MODEL.equals(v.group()) && v.key().equals("model")) {
                model = v;
            }
        }
        if (model == null || model.effective().equals(entity)) {
            return values;
        }
        boolean ownModel = !model.own().isEmpty() && !model.own().equals(model.base().isEmpty()
                ? model.game() : model.base());
        String modelTexture = textures.getOrDefault(model.effective(), "");
        List<Value> out = new ArrayList<>();
        for (Value v : values) {
            if (GameLayer.MODEL.equals(v.group()) && v.key().equals("texture")) {
                v = new Value(v.group(), v.key(), modelTexture.isEmpty() ? v.game() : modelTexture,
                        ownModel ? "" : v.base(), v.own());
            }
            out.add(v);
        }
        return out;
    }

    /**
     * The layer a stored row belongs to, seen from the target: the base mob's, the variant's global instance
     * (beneath a tier or biome instance), the target's own, or none (a throwaway map).
     */
    public static Map<String, String> layer(Target t, String variant, String scope, String scopeId,
                                             Map<String, String> base, Map<String, String> global,
                                             Map<String, String> own) {
        String sc = VariantOps.normalScope(scope);
        String sid = VariantOps.normalScopeId(scope, scopeId);
        if (variant.isEmpty()) {
            return base;
        }
        if (t.baseMob() || !variant.equals(t.variant())) {
            return new HashMap<>();
        }
        if (sc.equals(t.scope()) && sid.equals(t.scopeId())) {
            return own;
        }
        return t.scoped() && sc.isEmpty() ? global : new HashMap<>();
    }
    /** The game's rows with the edits laid over them, in group order then file order. */
    public static List<Value> merge(List<GameLayer.Row> game, Map<String, String> base, Map<String, String> own) {
        Map<String, List<Value>> byGroup = new LinkedHashMap<>();
        GROUPS.forEach(g -> byGroup.put(g, new ArrayList<>()));
        for (GameLayer.Row r : game) {
            String k = key(r.group(), r.key());
            byGroup.computeIfAbsent(r.group(), g -> new ArrayList<>()).add(new Value(r.group(), r.key(), r.value(),
                    base.getOrDefault(k, ""), own.getOrDefault(k, "")));
        }
        List<Value> out = new ArrayList<>();
        byGroup.values().forEach(out::addAll);
        return out;
    }

    /**
     * The collapsible section a value sits in, within its tab: an attribute by its namespace and prefix
     * (minecraft:generic), a field by the class that declares it (LivingEntity), a tag by its namespace, a
     * type value by what it is about.
     */
    public static String section(String group, String key) {
        switch (group) {
            case GameLayer.ATTRIBUTE -> {
                int colon = key.indexOf(':');
                int dot = key.indexOf('.', colon + 1);
                return dot > 0 ? key.substring(0, dot) : colon > 0 ? key.substring(0, colon) : key;
            }
            case GameLayer.FIELD -> {
                // Settings first, the running state apart, the rest by the class that declares them.
                if (com.strata.model.FieldGuide.SETTINGS.containsKey(key)) {
                    return SETTINGS_SECTION;
                }
                if (com.strata.model.FieldGuide.runningState(key)) {
                    return STATE_SECTION;
                }
                int dot = key.indexOf('.');
                return dot > 0 ? key.substring(0, dot) : key;
            }
            case GameLayer.TAG -> {
                int colon = key.indexOf(':');
                return colon > 0 ? key.substring(0, colon) : key;
            }
            case SPAWNING -> {
                return "Spawning";
            }
            case GameLayer.MODEL -> {
                return key.startsWith("sound_") ? "sound" : "model";
            }
            case GameLayer.TYPE -> {
                return switch (key) {
                    case "width", "height", "eye_height", "fixed_size" -> "size";
                    case "category", "summonable", "spawn_far_from_player", "spawn_placement", "spawn_heightmap" -> "spawning";
                    case "tracking_range", "update_interval" -> "network";
                    default -> "mob";
                };
            }
            default -> {
                return group;
            }
        }
    }

    /** The Fields tab's section of settings, and of running state. */
    public static final String SETTINGS_SECTION = "Settings";
    public static final String STATE_SECTION = "Running state (the game sets these; locked)";

    /**
     * The values in their sections, sections in first-seen order, values in sheet order; Settings first and
     * Running state last.
     */
    public static Map<String, List<Value>> sections(List<Value> values) {
        Map<String, List<Value>> found = new LinkedHashMap<>();
        for (Value v : values) {
            found.computeIfAbsent(section(v.group(), v.key()), k -> new ArrayList<>()).add(v);
        }
        Map<String, List<Value>> out = new LinkedHashMap<>();
        if (found.containsKey(SETTINGS_SECTION)) {
            out.put(SETTINGS_SECTION, found.remove(SETTINGS_SECTION));
        }
        List<Value> state = found.remove(STATE_SECTION);
        out.putAll(found);
        if (state != null) {
            out.put(STATE_SECTION, state);
        }
        return out;
    }

    /** The values of one group whose key or value holds the search text. */
    // ---- THE SCREEN'S VIEWS.

    /** The Body view: a mob's size and gravity (physics) and its flags, one view. */
    public static final String BODY = "body";

    /** The buttons on the Attributes view, in order. */
    public static final List<String> SUB_VIEWS = List.of(
            GameLayer.TYPE, GameLayer.FIELD, GameLayer.TAG, GameLayer.VISUALS, GameLayer.SOUNDS, BODY,
            GameLayer.COMBAT, GameLayer.LOOT_XP, GameLayer.BOSS);

    /** EVERY PROPERTY HAS A VIEW */
    public static final Map<String, Map<String, Set<String>>> VIEW_KEYS = viewKeys();

    private static Map<String, Map<String, Set<String>>> viewKeys() {
        Map<String, Map<String, Set<String>>> out = new LinkedHashMap<>();
        Map<String, Set<String>> visuals = new LinkedHashMap<>();
        visuals.put(GameLayer.MODEL, new java.util.LinkedHashSet<>(List.of("model", "texture")));
        visuals.put(GameLayer.VISUALS, keysOf(GameLayer.VISUALS));
        visuals.put(GameLayer.PARTICLES, keysOf(GameLayer.PARTICLES));
        out.put(GameLayer.VISUALS, visuals);
        out.put(GameLayer.SOUNDS, Map.of(GameLayer.SOUNDS, keysOf(GameLayer.SOUNDS)));
        Map<String, Set<String>> body = new LinkedHashMap<>();
        body.put(GameLayer.PHYSICS, keysOf(GameLayer.PHYSICS));
        body.put(GameLayer.FLAGS, keysOf(GameLayer.FLAGS));
        // Terrain costs sit with the body.
        body.put(GameLayer.PATHFINDING, keysOf(GameLayer.PATHFINDING));
        out.put(BODY, body);
        out.put(GameLayer.COMBAT, Map.of(GameLayer.COMBAT, keysOf(GameLayer.COMBAT)));
        out.put(GameLayer.LOOT_XP, Map.of(GameLayer.LOOT_XP, keysOf(GameLayer.LOOT_XP)));
        // The Boss tab: the bar's own values; MobDataScreen shows it only on a mob whose Boss flag is on.
        out.put(GameLayer.BOSS, Map.of(GameLayer.BOSS, keysOf(GameLayer.BOSS)));
        return Map.copyOf(out);
    }

    private static Set<String> keysOf(String group) {
        Set<String> keys = new java.util.LinkedHashSet<>();
        Schema.mobProperties(group).forEach(p -> keys.add(p.column()));
        return keys;
    }

    /** What the Visuals view shows, by group. */
    public static final Map<String, Set<String>> VISUAL_KEYS = VIEW_KEYS.get(GameLayer.VISUALS);

    /** What the Sounds view shows. */
    public static final Set<String> SOUND_KEYS = VIEW_KEYS.get(GameLayer.SOUNDS).get(GameLayer.SOUNDS);


    /** WHETHER THE BOSS TAB SHOWS: the mob's effective Boss flag is true. */
    public static boolean isBoss(List<Value> values) {
        return values.stream().anyMatch(v -> v.group().equals(GameLayer.FLAGS)
                && v.key().equals(Schema.MobFlags.BOSS) && v.effective().strip().equals("true"));
    }
    /** True when a value belongs to a view. */
    public static boolean inView(String view, Value v) {
        Map<String, Set<String>> wanted = VIEW_KEYS.get(view);
        if (wanted != null) {
            return wanted.getOrDefault(v.group(), Set.of()).contains(v.key());
        }
        return v.group().equals(view);
    }

    /** {@link #filter} over a view rather than one group. */
    public static List<Value> filterView(List<Value> values, String view, String search) {
        String s = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        List<Value> out = new ArrayList<>();
        for (Value v : values) {
            if (inView(view, v) && (s.isEmpty() || v.key().toLowerCase(Locale.ROOT).contains(s)
                    || v.effective().toLowerCase(Locale.ROOT).contains(s))) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * The values a view offers that hold nothing yet, so they can be set: a sound or a particle the game has
     * no value of its own for.
     */
    public static List<Value> unsetInView(String view, List<Value> values) {
        Map<String, Set<String>> wanted = VIEW_KEYS.getOrDefault(view, Map.of());
        List<Value> out = new ArrayList<>();
        wanted.forEach((group, keys) -> keys.forEach(key -> {
            boolean has = values.stream().anyMatch(v -> v.group().equals(group) && v.key().equals(key));
            if (!has && !GameLayer.MODEL.equals(group)) {
                out.add(new Value(group, key, "", "", ""));
            }
        }));
        return out;
    }

    public static List<Value> filter(List<Value> values, String group, String search) {
        String s = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        List<Value> out = new ArrayList<>();
        for (Value v : values) {
            if (v.group().equals(group) && (s.isEmpty() || v.key().toLowerCase(Locale.ROOT).contains(s)
                    || v.effective().toLowerCase(Locale.ROOT).contains(s))) {
                out.add(v);
            }
        }
        return out;
    }

    /** WHAT A ROW TAKES, for the value control. */
    public static com.strata.model.ValueType typeOf(Value v) {
        // Pick-from-a-set type values: where it spawns, its spawn surface.
        if (v.group().equals(GameLayer.TYPE) && v.key().equals("spawn_placement")) {
            return com.strata.model.ValueType.choice(SPAWN_PLACEMENTS);
        }
        if (v.group().equals(GameLayer.TYPE) && v.key().equals("spawn_heightmap")) {
            return com.strata.model.ValueType.choice(com.strata.runtime.SpawnPlacementEdits.HEIGHTMAPS);
        }
        if (v.group().equals(GameLayer.TAG)) {
            return com.strata.model.ValueType.BOOL;
        }
        Schema.Prop prop = Schema.mobProperty(v.group(), v.key());
        if (prop != null) {
            return switch (prop.kind()) {
                case FLAG -> com.strata.model.ValueType.BOOL;
                case RESOURCE -> resourceType(v.key());
                case NUMBER -> com.strata.model.ValueType.WHOLE;
                case DECIMAL -> com.strata.model.ValueType.NUMBER;
                case TEXT -> com.strata.model.ValueType.TEXT;
                case CHOICE -> com.strata.model.ValueType.choice(prop.choices());
            };
        }
        if (v.group().equals(GameLayer.MODEL)) {
            return resourceType(v.key());
        }
        String g = v.game() == null ? "" : v.game().strip();
        if (g.equals("true") || g.equals("false")) {
            return com.strata.model.ValueType.BOOL;
        }
        if (number(g)) {
            return g.contains(".") || g.contains("E") ? com.strata.model.ValueType.NUMBER
                    : com.strata.model.ValueType.WHOLE;
        }
        return com.strata.model.ValueType.TEXT;
    }

    /** The registry a resource property is picked from, by its key. */
    private static com.strata.model.ValueType resourceType(String key) {
        com.strata.model.ValueType.Registry kind;
        if (key.equals(Schema.MobVisuals.MODEL)) {
            kind = com.strata.model.ValueType.Registry.LIVING_MOB;
        } else if (key.equals(Schema.MobVisuals.TEXTURE) || key.equals(Schema.MobVisuals.GLOW_TEXTURE)) {
            kind = com.strata.model.ValueType.Registry.TEXTURE;
        } else if (key.equals(Schema.MobParticles.PARTICLE_TYPE)) {
            kind = com.strata.model.ValueType.Registry.PARTICLE;
        } else if (key.endsWith("_sound")) {
            kind = com.strata.model.ValueType.Registry.SOUND;
        } else if (key.equals(Schema.MobCombat.PROJECTILE_TYPE)) {
            kind = com.strata.model.ValueType.Registry.ENTITY;
        } else {
            return com.strata.model.ValueType.TEXT;
        }
        return com.strata.model.ValueType.registry(kind);
    }

    /** Why a typed value cannot replace the game's, or null when it can. */
    public static String problem(String game, String typed) {
        String v = typed == null ? "" : typed.strip();
        if (v.isEmpty()) {
            return "Type a value first";
        }
        if (v.contains("\n") || v.contains("\r")) {
            return "The value must be one line";
        }
        String g = game == null ? "" : game.strip();
        if (g.equals("true") || g.equals("false")) {
            return v.equals("true") || v.equals("false") ? null : "The value must be true or false";
        }
        if (number(g)) {
            if (!number(v)) {
                return "The value must be a number";
            }
            if (!g.contains(".") && !g.contains("E") && v.contains(".")) {
                return "The value must be a whole number";
            }
        }
        return null;
    }

    private static boolean number(String s) {
        try {
            return !s.isEmpty() && Double.isFinite(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Why this value cannot be edited for the target, or null when it can. */
    public static String lockReason(Target t, Value v) {
        String shared = GameLayer.lockReason(v.group(), v.key());
        if (shared != null) {
            return shared;
        }
        // ONE PLACE: the category is the mob editor's Category dropdown, which also moves the mob's spawn
        // rows (CategoryOps).
        if (v.group().equals(GameLayer.MODEL) && t.scoped()) {
            return "Set the model, texture, and sounds on the variant itself, not per tier or biome";
        }
        if (v.group().equals(GameLayer.TYPE) && v.key().equals("category")) {
            return "Set it on the Spawning tab: it moves the mob's spawn rows too";
        }
        return !t.baseMob() && (v.group().equals(GameLayer.TYPE) || v.group().equals(GameLayer.TAG))
                ? "This belongs to the whole mob type. Change it on the base mob" : null;
    }

    /** Saves one value for the target, then reloads. */
    public static WriteReport save(Target t, Value v, String typed) {
        String problem = lockReason(t, v);
        if (problem == null) {
            problem = problem(v.game(), typed);
        }
        if (problem == null) {
            // A CHOICE property takes one word of its set.
            Schema.Prop chosen = Schema.mobProperty(v.group(), v.key());
            if (chosen != null && chosen.kind() == Schema.Kind.CHOICE && !chosen.choices().contains(typed.strip())) {
                problem = "One of: " + String.join(", ", chosen.choices());
            }
        }
        if (problem == null && v.group().equals(GameLayer.TYPE)) {
            problem = typeProblem(v.key(), typed.strip());
        }
        if (problem == null && v.group().equals(GameLayer.MODEL)) {
            problem = modelProblem(v.key(), typed.strip());
        }
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        String value = typed.strip();
        if (v.group().equals(GameLayer.ATTRIBUTE)) {
            if (t.baseMob()) {
                return MobStatsOps.saveOverride(t.entity(), v.key(), value);
            }
            return Commit.reloadIfOk(new WriteReport().record("the stat",
                    TableWriter.upsert(Schema.VariantStats.FILE, statKeys(t, v),
                            Map.of(Schema.VariantStats.ABSOLUTE, value))));
        }
        // THE EIGHT PROPERTY GROUPS GO TO THEIR OWN TABLES.
        String file = Schema.mobPropertyFile(v.group());
        if (file != null) {
            Schema.Prop prop = Schema.mobProperty(v.group(), v.key());
            if (prop == null) {
                return WriteReport.refused("\"" + v.key() + "\" is not a " + v.group() + " property");
            }
            return Commit.reloadIfOk(new WriteReport().record("the value",
                    TableWriter.upsert(file, propertyKeys(t), Map.of(prop.column(), value))));
        }
        return Commit.reloadIfOk(new WriteReport().record("the value",
                TableWriter.upsert(Schema.MobData.FILE, keys(t, v), Map.of(Schema.MobData.VALUE, value))));
    }

    /**
     * THE KEY COLUMNS OF A PROPERTY TABLE ROW: the five every one of the eight shares, from the one target
     * the editor is editing.
     */
    public static Map<String, String> propertyKeys(Target t) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobSounds.SCOPE, t.scope());
        keys.put(Schema.MobSounds.SCOPE_ID, t.scopeId());
        keys.put(Schema.MobSounds.ENTITY, t.entity());
        keys.put(Schema.MobSounds.VARIANT_NAME, t.variant());
        keys.put(Schema.MobSounds.IS_VARIANT, String.valueOf(!t.variant().isEmpty()));
        return keys;
    }

    /**
     * The extra rule for a model value: the model is a living mob of the game, the texture a.png path, a
     * sound one the game has.
     */
    public static String modelProblem(String key, String value) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(value);
        if (id == null) {
            return "The value must be an id, as minecraft:bat";
        }
        if (key.equals("model")) {
            return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? null
                    : "No mob has the id " + value;
        }
        if (key.equals("texture")) {
            return value.endsWith(".png") ? null
                    : "The texture must be a .png path, as minecraft:textures/entity/bat.png";
        }
        if (key.startsWith("sound_")) {
            return net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(id) ? null
                    : "The game has no sound " + value;
        }
        return null;
    }

    /** The extra rule for a type value: a real category, a size above zero. */
    public static String typeProblem(String key, String value) {
        // Where it may spawn and its spawn surface: one of the game's, set at the next game start.
        if (key.equals("spawn_placement")) {
            return SPAWN_PLACEMENTS.contains(value) ? null
                    : "Where it spawns must be one of: " + String.join(", ", SPAWN_PLACEMENTS);
        }
        if (key.equals("spawn_heightmap")) {
            return com.strata.runtime.SpawnPlacementEdits.HEIGHTMAPS.contains(value) ? null
                    : "The spawn surface must be one of: " + String.join(", ", com.strata.runtime.SpawnPlacementEdits.HEIGHTMAPS);
        }
        if (key.equals("category")) {
            for (net.minecraft.world.entity.MobCategory c : net.minecraft.world.entity.MobCategory.values()) {
                if (c.getName().equals(value)) {
                    return null;
                }
            }
            return "The category must be one of the game's: monster, creature, ambient, axolotls,"
                    + " underground_water_creature, water_creature, water_ambient, misc";
        }
        if (List.of("width", "height", "eye_height").contains(key) && Double.parseDouble(value) <= 0) {
            return "The size must be above zero";
        }
        //, how far players see it (chunks) and how often it is sent (ticks).
        if (key.equals("tracking_range") && Double.parseDouble(value) < 0) {
            return "The tracking range must be 0 or more chunks";
        }
        if (key.equals("update_interval") && Double.parseDouble(value) < 1) {
            return "The update interval must be 1 tick or more";
        }
        if (key.equals("immune_to") && !"none".equalsIgnoreCase(value.strip())) {
            for (String raw : value.split(",")) {
                String id = raw.strip();
                net.minecraft.resources.ResourceLocation rl = id.isEmpty() ? null
                        : net.minecraft.resources.ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
                if (rl == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(rl)) {
                    return "The game has no block \"" + id + "\". Give block ids separated by commas, as minecraft:wither_rose, or none";
                }
            }
        }
        return null;
    }

    /** Removes the target's own edit, so the value falls back to the one beneath. */
    public static WriteReport restore(Target t, Value v) {
        if (v.group().equals(GameLayer.ATTRIBUTE)) {
            if (t.baseMob()) {
                return MobStatsOps.resetOverride(t.entity(), v.key());
            }
            // The multiplier on the same row stays.
            return Commit.reloadIfOk(new WriteReport().cascaded("the stat",
                    TableWriter.updateColumnWhere(Schema.VariantStats.FILE, statKeys(t, v),
                            Schema.VariantStats.ABSOLUTE, "")));
        }
        // The eight property groups are cleared in their own tables, by the same route a save takes -
        // otherwise Restore would look for the edit in mob_data.csv, never find it, and leave the real edit
        // in place.
        String file = Schema.mobPropertyFile(v.group());
        if (file != null) {
            Schema.Prop prop = Schema.mobProperty(v.group(), v.key());
            if (prop == null) {
                return WriteReport.refused("\"" + v.key() + "\" is not a " + v.group() + " property");
            }
            // The row may hold other properties, so the edit is BLANKED rather than the row dropped: clearing
            // one sound must not clear the mob's physics.
            return Commit.reloadIfOk(new WriteReport().cascaded("the edit",
                    TableWriter.updateColumnWhere(file, propertyKeys(t), prop.column(), "")));
        }
        return Commit.reloadIfOk(new WriteReport().cascaded("the edit",
                TableWriter.delete(Schema.MobData.FILE, keys(t, v))));
    }

    private static Map<String, String> statKeys(Target t, Value v) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.VariantStats.VARIANT_NAME, t.variant());
        keys.put(Schema.VariantStats.SCOPE, t.scope());
        keys.put(Schema.VariantStats.SCOPE_ID, t.scopeId());
        keys.put(Schema.VariantStats.ATTRIBUTE, v.key());
        return keys;
    }
    private static Map<String, String> keys(Target t, Value v) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put(Schema.MobData.ENTITY, t.entity());
        keys.put(Schema.MobData.VARIANT_NAME, t.variant());
        keys.put(Schema.MobData.SCOPE, t.scope());
        keys.put(Schema.MobData.SCOPE_ID, t.scopeId());
        keys.put(Schema.MobData.GROUP, v.group());
        keys.put(Schema.MobData.KEY, v.key());
        return keys;
    }

    private static String key(String group, String key) {
        return group + "|" + key;
    }

    private static String cell(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null ? "" : v.strip();
    }
}
