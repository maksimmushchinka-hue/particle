package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Звёздный салют: ракеты взлетают с огненным следом и раскрываются разными узорами
 * (сфера, кольцо, сатурн, «ива», спираль, двойная сфера). К концу темп нарастает до
 * сплошного залпа, а в финале — гигантская вспышка.
 */
final class FireworksFx extends FxEffect {

    private static final int MAX_SPARKS = 800;

    private static final int[][] PALETTES = {
            {rgb(255, 80, 80), rgb(255, 210, 70)},
            {rgb(80, 200, 255), rgb(190, 120, 255)},
            {rgb(120, 255, 140), rgb(240, 255, 120)},
            {rgb(255, 120, 220), rgb(255, 220, 245)},
            {rgb(255, 190, 70), rgb(255, 90, 40)},
            {rgb(200, 230, 255), rgb(90, 140, 255)},
            {rgb(170, 100, 255), rgb(90, 230, 255)},
            {rgb(255, 255, 255), rgb(255, 215, 120)}
    };

    private static final class Rocket {
        double x, y, z, vy, wob;
        int age, flight, pal, pattern;
    }

    private static final class Spark {
        double x, y, z, vx, vy, vz, grav, drag, size;
        int age, life, c1, c2;
        boolean glitter;
    }

    private final double R;
    private final List<Rocket> rockets = new ArrayList<>();
    private final List<Spark> sparks = new ArrayList<>();
    private int nextLaunch;

    FireworksFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 7.0 * scale, 0.0, true);
        this.R = 7.0 * scale;
        this.nextLaunch = Math.max(6, introTicks / 3);
    }

    @Override String title() { return "Звёздный салют"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 1.2f);
    }

    @Override
    void draw(double env) {
        double p = progress();
        boolean launching = tick < total - outroTicks;

        if (launching && tick >= nextLaunch) {
            boolean crescendo = p > 0.85;
            double interval = crescendo ? 6 : lerp(30, 12, smooth(p / 0.8));
            nextLaunch = tick + (int) interval + rnd.nextInt(8);
            launch(p);
            if (crescendo && rnd.nextInt(3) == 0) launch(p);
            if (p > 0.6 && rnd.nextInt(4) == 0) launch(p);
        }

        // ---- ракеты
        for (Iterator<Rocket> it = rockets.iterator(); it.hasNext(); ) {
            Rocket r = it.next();
            r.age++;
            r.y += r.vy;
            r.x += r.wob;
            put("END_ROD", r.x, r.y, r.z, 1, 0.02, 0.02, 0.02, 0.0);
            dust(r.x, r.y, r.z, rgb(255, 245, 200), 1.4 * dustK);
            for (int k = 1; k <= 5; k++) {
                dust(r.x - r.wob * k * 0.7, r.y - r.vy * k * 0.7, r.z,
                        grad(k / 5.0, rgb(255, 210, 120), rgb(255, 120, 40), rgb(120, 50, 30)),
                        (1.2 - k * 0.12) * dustK);
            }
            if (r.age >= r.flight) {
                explode(r);
                it.remove();
            }
        }

        // ---- искры
        int idx = 0;
        for (Iterator<Spark> it = sparks.iterator(); it.hasNext(); idx++) {
            Spark s = it.next();
            s.age++;
            s.vx *= s.drag;
            s.vy = s.vy * s.drag - s.grav;
            s.vz *= s.drag;
            s.x += s.vx;
            s.y += s.vy;
            s.z += s.vz;
            double f = s.age / (double) s.life;
            if (((idx + tick) & 1) == 0) {
                int c = mix(s.c1, s.c2, f);
                if (f > 0.75) c = mix(c, rgb(90, 50, 40), (f - 0.75) * 2.0);
                dust(s.x, s.y, s.z, c, Math.max(0.7, (1.0 - 0.4 * f)) * s.size * dustK);
            }
            if (s.glitter && f > 0.55 && rnd.nextInt(12) == 0) {
                put("END_ROD", s.x, s.y, s.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if (s.age >= s.life || s.y < -0.5) it.remove();
        }
    }

    private void launch(double p) {
        if (sparks.size() > MAX_SPARKS - 120) return;
        Rocket r = new Rocket();
        r.x = (rnd.nextDouble() * 2 - 1) * R * 0.95;
        r.z = (rnd.nextDouble() * 0.9 - 0.2) * R;
        r.y = 0;
        double apex = R * (0.75 + rnd.nextDouble() * 0.8);
        r.flight = 16 + rnd.nextInt(9);
        r.vy = apex / r.flight;
        r.wob = (rnd.nextDouble() - 0.5) * 0.01 * R;
        r.pal = rnd.nextInt(PALETTES.length);
        r.pattern = rnd.nextInt(6);
        rockets.add(r);
    }

    private Spark spark(Rocket r, double[] dir, double speed, int c1, int c2, int life,
                        double grav, double drag, double size, boolean glitter) {
        Spark s = new Spark();
        s.x = r.x;
        s.y = r.y;
        s.z = r.z;
        s.vx = dir[0] * speed;
        s.vy = dir[1] * speed;
        s.vz = dir[2] * speed;
        s.grav = grav * R;
        s.drag = drag;
        s.life = life;
        s.c1 = c1;
        s.c2 = c2;
        s.size = size;
        s.glitter = glitter;
        sparks.add(s);
        return s;
    }

    /** Две взаимно перпендикулярные единичные оси плоскости с нормалью n. */
    private static double[][] plane(double[] n) {
        double[] a = (Math.abs(n[1]) < 0.9) ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] u = {n[1] * a[2] - n[2] * a[1], n[2] * a[0] - n[0] * a[2], n[0] * a[1] - n[1] * a[0]};
        double l = Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        u[0] /= l; u[1] /= l; u[2] /= l;
        double[] v = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
        return new double[][]{u, v};
    }

    private void explode(Rocket r) {
        double v = 0.032 * R * (0.9 + 0.3 * rnd.nextDouble());
        int c1 = PALETTES[r.pal][0];
        int c2 = PALETTES[r.pal][1];
        double g = 0.00035;
        int life = 34 + rnd.nextInt(10);
        int room = Math.max(0, MAX_SPARKS - sparks.size());

        switch (r.pattern) {
            case 0: { // сфера
                for (double[] d : sphere(Math.min(90, room))) spark(r, d, v, c1, c2, life, g, 0.93, 1.0, true);
                break;
            }
            case 1: { // кольцо + маленькая сердцевина
                double[][] pl = plane(unit());
                for (int i = 0; i < 64; i++) {
                    double a = Math.PI * 2 * i / 64;
                    double[] d = {pl[0][0] * Math.cos(a) + pl[1][0] * Math.sin(a),
                            pl[0][1] * Math.cos(a) + pl[1][1] * Math.sin(a),
                            pl[0][2] * Math.cos(a) + pl[1][2] * Math.sin(a)};
                    spark(r, d, v * 1.1, c1, c2, life, g, 0.93, 1.1, true);
                }
                for (double[] d : sphere(Math.min(20, Math.max(0, room - 64)))) {
                    spark(r, d, v * 0.35, c2, c1, life - 8, g, 0.92, 0.9, false);
                }
                break;
            }
            case 2: { // сатурн
                for (double[] d : sphere(Math.min(40, room))) spark(r, d, v * 0.6, c1, c2, life, g, 0.93, 1.0, true);
                double[][] pl = plane(new double[]{0.35, 0.9, 0.25});
                for (int i = 0; i < 56; i++) {
                    double a = Math.PI * 2 * i / 56;
                    double[] d = {pl[0][0] * Math.cos(a) + pl[1][0] * Math.sin(a),
                            pl[0][1] * Math.cos(a) + pl[1][1] * Math.sin(a),
                            pl[0][2] * Math.cos(a) + pl[1][2] * Math.sin(a)};
                    spark(r, d, v * 1.25, c2, c1, life, g, 0.93, 1.1, false);
                }
                break;
            }
            case 3: { // «ива» — золотые ниспадающие нити
                int w1 = rgb(255, 205, 90), w2 = rgb(255, 110, 40);
                for (double[] d : sphere(Math.min(70, room))) {
                    spark(r, d, v * 0.9, w1, w2, 62 + rnd.nextInt(10), 0.0008, 0.95, 1.0, true);
                }
                break;
            }
            case 4: { // спираль
                for (int arm = 0; arm < 3; arm++) {
                    for (int i = 0; i < 24; i++) {
                        double phi = Math.PI * (i + 0.5) / 24;
                        double th = arm * Math.PI * 2 / 3 + i * 0.5;
                        double[] d = {Math.sin(phi) * Math.cos(th), Math.cos(phi), Math.sin(phi) * Math.sin(th)};
                        spark(r, d, v * (0.6 + 0.6 * i / 24.0), c1, c2, life, g, 0.93, 1.0, true);
                    }
                }
                break;
            }
            default: { // двойная сфера
                for (double[] d : sphere(Math.min(50, room))) spark(r, d, v, c1, c2, life, g, 0.93, 1.0, true);
                for (double[] d : sphere(Math.min(40, Math.max(0, room - 50)))) {
                    spark(r, d, v * 0.55, rgb(255, 255, 255), c1, life - 6, g, 0.92, 0.9, false);
                }
                break;
            }
        }

        double s = Math.sqrt(scale);
        burst("FIREWORK", r.x, r.y, r.z, 14, 0.10 * s);
        put("END_ROD", r.x, r.y, r.z, 8, 0.15 * s, 0.15 * s, 0.15 * s, 0.05);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, r.x, r.y, r.z, 1.6f, 0.9f + rnd.nextFloat() * 0.4f);
        if (rnd.nextInt(2) == 0) {
            sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, r.x, r.y, r.z, 1.4f, 0.8f + rnd.nextFloat() * 0.5f);
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        for (int k = 0; k < 7; k++) {
            double a = Math.PI * (k / 6.0);
            double x = Math.cos(a) * R * 0.9;
            double z = Math.sin(a) * R * 0.5;
            double y = R * (0.85 + 0.25 * Math.sin(k * 1.7));
            int[] pal = PALETTES[k % PALETTES.length];
            burst("END_ROD", x, y, z, 70, 0.5 * s);
            burst("FIREWORK", x, y, z, 40, 0.3 * s);
            double[][] sph = sphere(90);
            for (double[] d : sph) {
                dust(x + d[0] * 0.35 * R, y + d[1] * 0.35 * R, z + d[2] * 0.35 * R,
                        mix(pal[0], pal[1], (d[1] + 1) / 2), 1.6 * dustK);
            }
            sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, x, y, z, 2f, 0.6f + 0.1f * k);
        }
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, R, 0, 1.5f, 0.7f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, 0, 0, 2f, 1.0f);
    }
}
