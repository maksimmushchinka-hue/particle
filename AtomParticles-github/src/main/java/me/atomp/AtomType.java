package me.atomp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Виды классического (детализированного) атома. */
public enum AtomType {
    CLASSIC("classic", "Классический атом",
            Pal.make(rgb(25, 70, 235), rgb(160, 215, 255), rgb(235, 252, 255),
                    rgb(110, 220, 255), rgb(20, 70, 220), rgb(60, 150, 255), rgb(8, 25, 150),
                    rgb(200, 250, 255), rgb(20, 120, 210), rgb(210, 250, 255), rgb(30, 100, 255),
                    new int[]{rgb(60, 215, 70), rgb(40, 100, 230), rgb(240, 240, 200), rgb(175, 95, 255), rgb(255, 150, 55)},
                    "END_ROD", "ELECTRIC_SPARK", false, false),
            "классика", "atom", "атом"),

    GYRO("gyro", "Атом-гироскоп",
            Pal.make(rgb(25, 70, 235), rgb(160, 215, 255), rgb(235, 252, 255),
                    rgb(110, 220, 255), rgb(20, 70, 220), rgb(60, 150, 255), rgb(8, 25, 150),
                    rgb(200, 250, 255), rgb(20, 120, 210), rgb(210, 250, 255), rgb(30, 100, 255),
                    new int[]{rgb(60, 215, 70), rgb(40, 100, 230), rgb(240, 240, 200), rgb(175, 95, 255), rgb(255, 150, 55)},
                    "END_ROD", "ELECTRIC_SPARK", true, false),
            "гироскоп"),

    CLOUD("cloud", "Электронное облако",
            Pal.make(rgb(60, 40, 200), rgb(200, 170, 255), rgb(245, 235, 255),
                    rgb(190, 150, 255), rgb(70, 40, 190), rgb(130, 100, 255), rgb(30, 15, 120),
                    rgb(230, 210, 255), rgb(90, 60, 200), rgb(235, 215, 255), rgb(110, 80, 240),
                    new int[]{rgb(255, 120, 200), rgb(120, 200, 255), rgb(255, 230, 140), rgb(140, 255, 190), rgb(200, 150, 255)},
                    "END_ROD", "ELECTRIC_SPARK", false, true),
            "облако"),

    FIRE("fire", "Огненный атом",
            Pal.make(rgb(200, 40, 10), rgb(255, 200, 90), rgb(255, 245, 200),
                    rgb(255, 170, 50), rgb(160, 30, 0), rgb(255, 100, 20), rgb(90, 10, 0),
                    rgb(255, 240, 170), rgb(200, 50, 10), rgb(255, 230, 150), rgb(220, 60, 10),
                    new int[]{rgb(255, 200, 60), rgb(255, 110, 30), rgb(255, 60, 40), rgb(255, 235, 120), rgb(255, 150, 90)},
                    "FLAME", "SMALL_FLAME", false, false),
            "огонь"),

    ICE("ice", "Ледяной атом",
            Pal.make(rgb(120, 200, 255), rgb(235, 250, 255), rgb(255, 255, 255),
                    rgb(200, 240, 255), rgb(80, 160, 240), rgb(140, 210, 255), rgb(40, 100, 200),
                    rgb(240, 255, 255), rgb(90, 170, 240), rgb(240, 255, 255), rgb(110, 190, 255),
                    new int[]{rgb(255, 255, 255), rgb(150, 220, 255), rgb(190, 235, 255), rgb(120, 180, 255), rgb(210, 200, 255)},
                    "SNOWFLAKE", "END_ROD", false, false),
            "лёд", "лед"),

    VOID("void", "Атом бездны",
            Pal.make(rgb(40, 0, 90), rgb(190, 90, 255), rgb(240, 200, 255),
                    rgb(200, 100, 255), rgb(50, 0, 120), rgb(130, 40, 220), rgb(25, 0, 70),
                    rgb(235, 190, 255), rgb(80, 20, 160), rgb(230, 170, 255), rgb(90, 30, 200),
                    new int[]{rgb(255, 60, 200), rgb(170, 80, 255), rgb(90, 60, 255), rgb(255, 120, 255), rgb(60, 30, 160)},
                    "REVERSE_PORTAL", "PORTAL", false, false),
            "бездна", "пустота");

    /** Палитра и особенности вида. */
    static final class Pal {
        final int nucDark, nucLight, core;
        final int haloA0, haloA1, haloB0, haloB1;
        final int trail0, trail1, shock0, shock1;
        final int[] elec;
        final String spark, spark2;
        final boolean gyro, cloud;

        private Pal(int nucDark, int nucLight, int core, int haloA0, int haloA1, int haloB0, int haloB1,
                    int trail0, int trail1, int shock0, int shock1, int[] elec,
                    String spark, String spark2, boolean gyro, boolean cloud) {
            this.nucDark = nucDark;
            this.nucLight = nucLight;
            this.core = core;
            this.haloA0 = haloA0;
            this.haloA1 = haloA1;
            this.haloB0 = haloB0;
            this.haloB1 = haloB1;
            this.trail0 = trail0;
            this.trail1 = trail1;
            this.shock0 = shock0;
            this.shock1 = shock1;
            this.elec = elec;
            this.spark = spark;
            this.spark2 = spark2;
            this.gyro = gyro;
            this.cloud = cloud;
        }

        static Pal make(int nucDark, int nucLight, int core, int haloA0, int haloA1, int haloB0, int haloB1,
                        int trail0, int trail1, int shock0, int shock1, int[] elec,
                        String spark, String spark2, boolean gyro, boolean cloud) {
            return new Pal(nucDark, nucLight, core, haloA0, haloA1, haloB0, haloB1,
                    trail0, trail1, shock0, shock1, elec, spark, spark2, gyro, cloud);
        }
    }

    private static int rgb(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    private final String key;
    private final String title;
    private final Pal pal;
    private final String[] aliases;

    AtomType(String key, String title, Pal pal, String... aliases) {
        this.key = key;
        this.title = title;
        this.pal = pal;
        this.aliases = aliases;
    }

    public String key() { return key; }

    public String title() { return title; }

    Pal pal() { return pal; }

    public static AtomType parse(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (AtomType t : values()) {
            if (t.key.equals(v)) return t;
            for (String a : t.aliases) if (a.equals(v)) return t;
        }
        return null;
    }

    public static List<String> keys() {
        List<String> l = new ArrayList<>();
        for (AtomType t : values()) l.add(t.key);
        return l;
    }
}
