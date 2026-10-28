package com.strata.domain;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * WORLD SETTINGS: the world's own editable settings, read from the running server and set on it through the
 * server's own setters.
 */
public final class WorldSettingsOps {

    private WorldSettingsOps() {
    }

    /** The difficulties, as the dropdown lists them. */
    public static final List<String> DIFFICULTIES = List.of("Peaceful", "Easy", "Normal", "Hard");
    /** The weathers, as the dropdown lists them. */
    public static final List<String> WEATHERS = List.of("Clear", "Rain", "Thunder");
    /** Yes or No, for the difficulty lock. */
    public static final List<String> YES_NO = List.of("Yes", "No");

    /** Minutes one day takes in the game as it is: 24000 ticks at 20 ticks a second. */
    public static final double VANILLA_DAY_MINUTES = 20;
    /** A day is this many ticks long. */
    public static final int DAY_TICKS = 24000;

    /** Why hardcore cannot be changed here. */
    public static final String HARDCORE_REASON = "Fixed when the world was created";
    /** Why the datapacks cannot be changed here. */
    public static final String DATAPACKS_REASON = "Read-only for now";
    /** What the screen says when there is no world to change. */
    public static final String NO_WORLD = "Open a single-player world to change its settings. A server's settings are changed on the server.";

    /**
     * Every editable value as the screen holds it: text for the numbers, the dropdown's word for the sets.
     */
    public record Form(String centerX, String centerZ, String size, String damagePerBlock, String safeZone,
                       String warningBlocks, String warningSeconds, String difficulty, String locked,
                       String time, String dayMinutes, String weather, String weatherTicks, String spawnAngle) {
    }

    /** What the world has now, shown beside the editable values. */
    public record Snapshot(Form form, boolean hardcore, List<String> datapacks) {
    }

    /** A form with nothing in it, for a screen with no world. */
    public static final Form EMPTY = new Form("", "", "", "", "", "", "", "Normal", "No", "", "", "Clear", "", "");

    // ---- Pure.

    /** A number as the field shows it: no trailing ".0". */
    public static String num(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Minutes a day takes at a day-time-per-tick rate; a negative rate is the game's own (20 minutes). */
    public static double minutesFor(float dayTimePerTick) {
        return dayTimePerTick <= 0 ? VANILLA_DAY_MINUTES : VANILLA_DAY_MINUTES / dayTimePerTick;
    }

    /** The day-time-per-tick rate for a day of this many minutes (20 is 1.0, the game's own). */
    public static float dayTimePerTickFor(double minutes) {
        return (float) (VANILLA_DAY_MINUTES / minutes);
    }

    private static Double number(String s) {
        try {
            double v = Double.parseDouble(s.strip());
            return Double.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Why a value in the form is refused, or null when all are fine. */
    public static String formProblem(Form f) {
        Double cx = number(f.centerX());
        Double cz = number(f.centerZ());
        if (cx == null || cz == null || Math.abs(cx) > WorldBorder.MAX_CENTER_COORDINATE
                || Math.abs(cz) > WorldBorder.MAX_CENTER_COORDINATE) {
            return "The border's centre is a number from -29999984 to 29999984 on each side";
        }
        Double size = number(f.size());
        if (size == null || size < 1 || size > WorldBorder.MAX_SIZE) {
            return "The border's size is a number of blocks, 1 to 59999968";
        }
        Double damage = number(f.damagePerBlock());
        if (damage == null || damage < 0) {
            return "Damage per block is a number, 0 or more";
        }
        Double safe = number(f.safeZone());
        if (safe == null || safe < 0) {
            return "The safe zone is a number of blocks, 0 or more";
        }
        if (!isWhole(f.warningBlocks(), 0, Integer.MAX_VALUE)) {
            return "The warning distance is a whole number of blocks, 0 or more";
        }
        if (!isWhole(f.warningSeconds(), 0, Integer.MAX_VALUE)) {
            return "The warning time is a whole number of seconds, 0 or more";
        }
        if (!DIFFICULTIES.contains(f.difficulty())) {
            return "Pick a difficulty";
        }
        if (!YES_NO.contains(f.locked())) {
            return "Pick Yes or No for the difficulty lock";
        }
        if (!isWhole(f.time(), 0, DAY_TICKS - 1)) {
            return "The time of day is a whole number of ticks, 0 to 23999";
        }
        Double minutes = number(f.dayMinutes());
        if (minutes == null || minutes < 0.1 || minutes > 100000) {
            return "A day's length is a number of minutes, 0.1 or more (20 is the game's own)";
        }
        if (!WEATHERS.contains(f.weather())) {
            return "Pick a weather";
        }
        if (!isWhole(f.weatherTicks(), 0, 1000000000)) {
            return "The weather's duration is a whole number of ticks, 0 or more";
        }
        Double angle = number(f.spawnAngle());
        if (angle == null || Math.abs(angle) > 360) {
            return "The spawn angle is a number of degrees, -360 to 360";
        }
        return null;
    }

    private static boolean isWhole(String s, long min, long max) {
        try {
            long v = Long.parseLong(s.strip());
            return v >= min && v <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }

    // ---- The running server.

    /** The running server, or null with no world open here (a title screen, or a remote server). */
    public static MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** Runs on the server thread and waits for the answer. */
    private static <T> T onServer(MinecraftServer server, java.util.function.Supplier<T> job) {
        return server.isSameThread() ? job.get() : server.submit(job).join();
    }

    /** What the world has now; the default of every field. */
    public static Snapshot read() {
        MinecraftServer server = server();
        return server == null ? null : onServer(server, () -> read(server));
    }

    /** What the world has now. */
    public static Snapshot read(MinecraftServer server) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        ServerLevelData data = (ServerLevelData) level.getLevelData();
        String weather = data.isThundering() ? "Thunder" : data.isRaining() ? "Rain" : "Clear";
        int ticks = data.isThundering() ? data.getThunderTime() : data.isRaining() ? data.getRainTime()
                : data.getClearWeatherTime();
        Form form = new Form(num(border.getCenterX()), num(border.getCenterZ()), num(border.getSize()),
                num(border.getDamagePerBlock()), num(border.getDamageSafeZone()),
                String.valueOf(border.getWarningBlocks()), String.valueOf(border.getWarningTime()),
                cap(server.getWorldData().getDifficulty().getKey()),
                server.getWorldData().isDifficultyLocked() ? "Yes" : "No",
                String.valueOf(Math.floorMod(level.getDayTime(), (long) DAY_TICKS)),
                num(Math.round(minutesFor(level.getDayTimePerTick()) * 100) / 100.0), weather,
                String.valueOf(Math.max(0, ticks)), num(level.getSharedSpawnAngle()));
        return new Snapshot(form, server.isHardcore(), List.copyOf(server.getPackRepository().getSelectedIds()));
    }

    /** Sets every value that differs from what the world has now, on the running server. */
    public static WriteReport save(Form form) {
        String problem = formProblem(form);
        if (problem != null) {
            return WriteReport.refused(problem);
        }
        MinecraftServer server = server();
        if (server == null) {
            return WriteReport.refused(NO_WORLD);
        }
        WriteReport report = new WriteReport();
        String failed = onServer(server, () -> {
            try {
                apply(server, form);
                return null;
            } catch (RuntimeException e) {
                return String.valueOf(e);
            }
        });
        if (failed != null) {
            report.record("the world's settings (" + failed + ")", false);
        }
        return report;
    }

    /** Applies the changes. */
    public static void apply(MinecraftServer server, Form f) {
        Form now = read(server).form();
        ServerLevel overworld = server.overworld();
        WorldBorder border = overworld.getWorldBorder();
        if (!f.centerX().equals(now.centerX()) || !f.centerZ().equals(now.centerZ())) {
            border.setCenter(number(f.centerX()), number(f.centerZ()));
        }
        if (!f.size().equals(now.size())) {
            border.setSize(number(f.size()));
        }
        if (!f.damagePerBlock().equals(now.damagePerBlock())) {
            border.setDamagePerBlock(number(f.damagePerBlock()));
        }
        if (!f.safeZone().equals(now.safeZone())) {
            border.setDamageSafeZone(number(f.safeZone()));
        }
        if (!f.warningBlocks().equals(now.warningBlocks())) {
            border.setWarningBlocks(Integer.parseInt(f.warningBlocks().strip()));
        }
        if (!f.warningSeconds().equals(now.warningSeconds())) {
            border.setWarningTime(Integer.parseInt(f.warningSeconds().strip()));
        }
        if (!f.difficulty().equals(now.difficulty())) {
            Difficulty d = Difficulty.byName(f.difficulty().toLowerCase(java.util.Locale.ROOT));
            if (d != null) {
                server.setDifficulty(d, true);
            }
        }
        if (!f.locked().equals(now.locked())) {
            server.setDifficultyLocked("Yes".equals(f.locked()));
        }
        if (!f.time().equals(now.time())) {
            long t = Long.parseLong(f.time().strip());
            for (ServerLevel level : server.getAllLevels()) {
                long day = level.getDayTime() - Math.floorMod(level.getDayTime(), (long) DAY_TICKS);
                level.setDayTime(day + t);
            }
        }
        if (!f.dayMinutes().equals(now.dayMinutes())) {
            overworld.setDayTimePerTick(dayTimePerTickFor(number(f.dayMinutes())));
        }
        if (!f.weather().equals(now.weather()) || !f.weatherTicks().equals(now.weatherTicks())) {
            int ticks = Integer.parseInt(f.weatherTicks().strip());
            switch (f.weather()) {
                case "Rain" -> overworld.setWeatherParameters(0, ticks, true, false);
                case "Thunder" -> overworld.setWeatherParameters(0, ticks, true, true);
                default -> overworld.setWeatherParameters(ticks, 0, false, false);
            }
        }
        if (!f.spawnAngle().equals(now.spawnAngle())) {
            overworld.setDefaultSpawnPos(overworld.getSharedSpawnPos(), number(f.spawnAngle()).floatValue());
        }
    }
}
