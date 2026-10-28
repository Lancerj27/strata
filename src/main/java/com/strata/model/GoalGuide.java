package com.strata.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** What each of the game's AI goals does, in plain words. */
public final class GoalGuide {

    private GoalGuide() {
    }

    /** The two kinds of goal. */
    public enum Role {
        TARGET("Who it goes after"),
        GOAL("What it does");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * One goal explained.
     *
     * @param goal the simple class name
     * @param role WHO or HOW
     * @param name a short name, "melee attack"
     * @param what what it does, in one or two sentences
     * @param needs what the mob must have for it to work, or ""
     * @param numbers what the behavior editor's numbers mean for it, or ""
     */
    public record Entry(String goal, Role role, String name, String what, String needs, String numbers) {
    }

    private static final Map<String, Entry> KNOWN = new LinkedHashMap<>();

    private static void who(String goal, String name, String what, String needs, String numbers) {
        KNOWN.put(goal, new Entry(goal, Role.TARGET, name, what, needs, numbers));
    }

    private static void how(String goal, String name, String what, String needs, String numbers) {
        KNOWN.put(goal, new Entry(goal, Role.GOAL, name, what, needs, numbers));
    }

    private static final String SPEED = "Speed: how fast it moves for this goal. 1.0 is its normal speed.";
    private static final String ATTACK_NEEDS = "A target from a target goal, and attack damage."
            + " A mob without attack damage (a cow) cannot use it.";

    static {
        who("NearestAttackableTargetGoal", "hunts",
                "Picks the nearest one it can see within its follow range (players by default) and locks"
                        + " on to it as its target."
                        + " It does not move or attack by itself: an attack goal does that.",
                "", "Target: who it hunts.");
        who("HurtByTargetGoal", "fights back",
                "Makes whoever hurt it its target. It does not move or attack by itself.",
                "", "");
        how("MeleeAttackGoal", "melee attack",
                "Runs to its target and hits it at arm's reach, with the item in its hand or bare-handed."
                        + " It does not pick the target: a target goal does.",
                ATTACK_NEEDS, SPEED + " (its run toward the target)");
        how("ZombieAttackGoal", "melee attack (zombie)",
                "The zombie's own melee attack: the same as melee attack, with its arms raised.",
                ATTACK_NEEDS, "");
        how("ShootProjectileGoal", "shoots a projectile",
                "Fires any projectile the game or a mod has at its target, in range and in sight, every so"
                        + " many ticks: a ghast's fireball, a blaze's, an arrow, a trident, a wither skull, a"
                        + " shulker's bullet, a mod's. A fireball flies straight; anything else is thrown in an arc.",
                "Any mob, and a target from a target goal.",
                "Fires: the projectile. Ticks between shots. Shoots from: how near the target must be."
                        + " Projectile speed. Arrow damage: for an arrow kind only.");
        how("RangedBowAttackGoal", "shoots a bow",
                "Runs only while it holds a bow. Walks in until its target is within about 15 blocks and"
                        + " in sight, then strafes, draws, and shoots. A skeleton that holds anything else"
                        + " uses melee attack instead.",
                "A skeleton kind holding a bow, and a target from a target goal."
                        + " Strata cannot add it to another mob, only keep or remove it.", "");
        how("RangedCrossbowAttackGoal", "shoots a crossbow",
                "Runs only while it holds a crossbow: loads it, aims, and fires at its target.",
                "A pillager or piglin holding a crossbow. Strata cannot add it, only keep or remove it.", "");
        how("RangedAttackGoal", "ranged attack",
                "Throws or fires the mob's own shot at its target: a snow golem's snowballs,"
                        + " a witch's potions.",
                "A mob that has its own shot. Strata cannot add it, only keep or remove it.", "");
        how("LeapAtTargetGoal", "leaps",
                "When its target is 2 to 4 blocks away and it stands on the ground, it jumps at it now and"
                        + " then. The jump does no damage by itself: an attack goal hits.",
                "A target from a target goal.",
                "Speed: how high it jumps. 0.4 is a spider's jump.");
        how("AvoidEntityGoal", "keeps away",
                "Runs from the chosen mobs when one comes within range.",
                "", "Target: who it runs from. Range: how close before it runs. Speed: how fast it runs"
                        + " once the threat is close; farther out it walks at its normal speed.");
        how("PanicGoal", "panics",
                "Runs around fast for a while after it is hurt, or while it burns.",
                "", SPEED);
        how("RestrictSunGoal", "stays in shade",
                "Stops it from walking out into sunlight.", "", "");
        how("FleeSunGoal", "hides from the sun",
                "When it burns in daylight, it runs to a spot out of the sky's view.", "", SPEED);
        how("FloatGoal", "swims",
                "Keeps it at the top of water, so it does not sink and drown.", "", "");
        how("RandomStrollGoal", "wanders",
                "Walks to random nearby spots when it has nothing else to do.", "", SPEED);
        how("WaterAvoidingRandomStrollGoal", "wanders on land",
                "Walks to random nearby spots on land when it has nothing else to do.", "", SPEED);
        how("LookAtPlayerGoal", "watches",
                "Turns its head to look at the chosen mob when one is near.",
                "", "Target: who it watches. Range: how near.");
        how("RandomLookAroundGoal", "looks around",
                "Turns its head to random sides when it has nothing to look at.", "", "");
        how("BreedGoal", "breeds",
                "Finds a partner and makes a baby after a player feeds it.", "", "");
        how("TemptGoal", "follows food",
                "Follows a player who holds its food.", "", "");
        how("FollowParentGoal", "follows its parent",
                "A baby walks after an adult of its kind.", "", "");
        how("MoveTowardsRestrictionGoal", "goes home",
                "Walks back into its home area when it leaves it.", "", "");

        // EVERY OTHER GOAL IN THE GAME.
        who("DefendVillageTargetGoal", "defends the village",
                "Iron golem: targets a player that a villager within about 10 blocks strongly dislikes,"
                        + " most often for hurting villagers.", "", "");
        who("NearestAttackableWitchTargetGoal", "hunts (witch)",
                "Witch: hunts like \"hunts\", but a raid can switch it off while the witch helps raiders.",
                "", "");
        who("NearestHealableRaiderTargetGoal", "picks a raider to heal",
                "Witch in a raid: targets a hurt raider, so it throws healing potions at it.", "", "");
        who("NonTameRandomTargetGoal", "hunts small animals",
                "Wolf or cat that is not tamed: now and then targets prey (a rabbit, a chicken, a baby turtle).",
                "", "");
        who("OwnerHurtByTargetGoal", "defends its owner",
                "Tamed wolf: targets whatever hurts its owner.", "", "");
        who("OwnerHurtTargetGoal", "helps its owner fight",
                "Tamed wolf: targets whatever its owner attacks.", "", "");
        who("ResetUniversalAngerTargetGoal", "calms down",
                "Mobs that anger as a group (bee, wolf, enderman, iron golem, polar bear, zombified piglin):"
                        + " ends the group's anger when it runs out.", "", "");
        how("SwellGoal", "swells and explodes",
                "Creeper: when its target is within 3 blocks, it stops and swells. It explodes unless the"
                        + " target gets more than 7 blocks away or out of sight.", "A target from a target goal.", "");
        how("BreakDoorGoal", "breaks doors",
                "Zombie: breaks a wooden door in its way. Only on Hard difficulty.", "", "");
        how("MoveThroughVillageGoal", "walks through villages",
                "Zombie: at night, walks from door to door through a village.", "", "");
        how("RemoveBlockGoal", "tramples eggs",
                "Zombie: walks to a turtle egg and breaks it.", "", "");
        how("OpenDoorGoal", "opens doors",
                "Opens a door, walks through, and closes it after (villagers, and raiders in a village).",
                "", "");
        how("StrollThroughVillageGoal", "strolls through the village", "Wanders between village spots.",
                "", "");
        how("MoveTowardsTargetGoal", "walks to its target",
                "Iron golem: walks toward its target from farther away.", "", "");
        how("GolemRandomStrollInVillageGoal", "patrols the village",
                "Iron golem: wanders around its village.", "", "");
        how("MoveBackToVillageGoal", "returns to the village",
                "Iron golem: walks back when it leaves its village.", "", "");
        how("OfferFlowerGoal", "offers a flower", "Iron golem: now and then holds out a poppy to a villager.",
                "", "");
        how("PathfindToRaidGoal", "joins the raid", "Raider: walks to the raid's center.", "", "");
        how("BegGoal", "begs", "Wolf: tilts its head at a player who holds a bone or meat.", "", "");
        how("FollowOwnerGoal", "follows its owner",
                "Tamed pet (wolf, cat, parrot): follows its owner, and teleports to it when far behind.", "", "");
        how("SitWhenOrderedToGoal", "sits when told", "Tamed pet: sits when its owner tells it to.", "", "");
        how("LandOnOwnersShoulderGoal", "lands on a shoulder", "Tamed parrot: lands on its owner's shoulder.",
                "", "");
        how("FollowMobGoal", "follows other mobs", "Parrot: flies after other mobs near it.", "", "");
        how("CatLieOnBedGoal", "lies on a bed", "Cat: lies on a bed near its owner.", "", "");
        how("CatSitOnBlockGoal", "sits on blocks", "Cat: sits on a chest, a furnace, or a bed.", "", "");
        how("OcelotAttackGoal", "pounces", "Cat or ocelot: runs at its prey and hits it.",
                "A target from a target goal.", "");
        how("EatBlockGoal", "eats grass", "Sheep: eats grass, which grows its wool back.", "", "");
        how("RandomStandGoal", "rears up", "Horse: now and then stands on its hind legs.", "", "");
        how("RunAroundLikeCrazyGoal", "bucks", "Untamed horse or llama: throws off a rider and runs around.",
                "", "");
        how("LlamaFollowCaravanGoal", "follows the caravan", "Llama: follows the llama in front of it.", "", "");
        how("FollowFlockLeaderGoal", "swims in a school", "Schooling fish: follows the leader of its school.",
                "", "");
        how("RandomSwimmingGoal", "swims around", "Swims to random spots in water.", "", SPEED);
        how("TryFindWaterGoal", "finds water", "A water mob on land moves toward nearby water.", "", "");
        how("BreathAirGoal", "comes up for air", "Dolphin: swims up to breathe when low on air.", "", "");
        how("DolphinJumpGoal", "leaps", "Dolphin: jumps out of the water now and then.", "", "");
        how("FollowBoatGoal", "follows boats", "Dolphin: swims after a player in a boat.", "", "");
        how("ClimbOnTopOfPowderSnowGoal", "climbs out of powder snow",
                "Fox, rabbit, silverfish, endermite: climbs up when it sinks in powder snow.", "", "");
        how("WaterAvoidingRandomFlyingGoal", "flies around", "Flies to random spots, away from water.", "", SPEED);
        how("InteractGoal", "watches while it trades", "Wandering trader: looks at the player it deals with.",
                "", "");
        how("LookAtTradingPlayerGoal", "looks at its customer",
                "Wandering trader: looks at the player trading with it.", "", "");
        how("TradeWithPlayerGoal", "stops to trade", "Wandering trader: stands still while a player trades.",
                "", "");
        how("UseItemGoal", "drinks a potion or milk",
                "Wandering trader: drinks invisibility at night and milk by day.", "", "");
    }

    /**
     * The explanation for a goal: a full class name, a simple name, or a GameGoal's chain (the first name the
     * guide knows wins).
     */
    public static Entry of(String goal) {
        String simple = goal == null ? "" : goal.substring(goal.lastIndexOf('.') + 1);
        Entry known = KNOWN.get(simple);
        return known != null ? known : unknown(simple, simple.contains("Target"));
    }

    /** The explanation for a mob's goal, by the first name in its chain the guide knows. */
    public static Entry of(GameGoal goal) {
        for (String name : goal.chain()) {
            Entry known = KNOWN.get(name);
            if (known != null) {
                return known;
            }
        }
        return unknown(goal.name(), goal.target());
    }

    private static Entry unknown(String simple, boolean target) {
        // A goal nested in its mob's class is "Zombie$ZombieAttackTurtleEggGoal": the name is the inner
        // class's.
        String inner = simple.substring(simple.lastIndexOf('$') + 1);
        String base = inner.endsWith("Goal") ? inner.substring(0, inner.length() - 4) : inner;
        String name = base.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).strip();
        return new Entry(simple, target ? Role.TARGET : Role.GOAL, name.isEmpty() ? "a goal" : name,
                "A goal of the game or of a mod, made for this mob. Strata cannot describe it"
                        + " further, but can remove it.", "", "");
    }

    /** True when the guide explains this goal by name. */
    public static boolean knows(String goal) {
        return KNOWN.containsKey(goal == null ? "" : goal.substring(goal.lastIndexOf('.') + 1));
    }

    /** Every goal the guide explains, in its order. */
    public static List<Entry> all() {
        return List.copyOf(KNOWN.values());
    }
}
