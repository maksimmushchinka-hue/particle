package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Спиральная галактика: рождение из ядра, дифференциальное вращение рукавов,
 * сверхновые, кометы, квазарные джеты, коллапс и «Большой взрыв».
 */
final class GalaxyFx extends FxEffect {

    private static final int STARS = 1100;
    private static final int ARMS = 4;

    private final double R;
    private final double[] sr = new double[STARS];
    private final double[] sth = new double[STARS];
    private final double[] sy = new double[STARS];
    private final double[] ssz = new double[STARS];
    private final int[] scol = new int[STARS];
    private final boolean[] bright = new boolean[STARS];

    private int nextNova;
    private int nextComet;
    private final List<double[]> comets = new ArrayList<>(); // x,y,z,vx,vy,vz,life
    private final List<double[]> novas = new ArrayList<>();  // x,y,z,age

    GalaxyFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 6.0 * scale, 6.0 * scale * 0.22, false);
        this.R = 6.0 * scale;
        for (int i = 0; i < STARS; i++) {
            int arm = i % ARMS;
            double r = 0.07 + 0.93 * Math.pow(rnd.nextDouble(), 0.85);
            sr[i] = r;
            sth[i] = arm * Math.PI * 2 / ARMS + 3.4 * r + gauss() * (0.20 + 0.10 * r);
            sy[i] = gauss() * 0.035 * (1.25 - r) * R;
            bright[i] = rnd.nextDouble() < 0.06;
            int c = grad(r, rgb(255, 240, 190), rgb(255, 195, 125), rgb(205, 150, 255),
                    rgb(115, 165, 255), rgb(75, 115, 235));
            if (rnd.nextDouble() < 0.12) c = rgb(255, 105, 200); // туманности
            if (bright[i]) c = rgb(245, 250, 255);
            scol[i] = c;
            ssz[i] = (bright[i] ? 1.3 : 0.7 + rnd.nextDouble() * 0.4) * dustK;
        }
        nextNova = introTicks + 80;
        nextComet = introTicks + 40;
    }

    @Override String title() { return "Галактика"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 0.6f);
    }

    private double omega(double r) { return 0.0215 / (0.3 + r); }

    /** Поворот диска вокруг оси «вправо» (наклон) — возвращает локальные xyz. */
    private double[] disc(double x, double yd, double z, double tilt) {
        double c = Math.cos(tilt), s = Math.sin(tilt);
        return new double[]{x, yd * c - z * s, yd * s + z * c};
    }

    private double[] starPos(int i, double grow, double spin, double tilt) {
        double r = sr[i] * grow;
        double th = sth[i] + omega(sr[i]) * tick + spin * (1 - grow) * 3.0;
        return disc(Math.cos(th) * r * R, sy[i] * grow, Math.sin(th) * r * R, tilt);
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double tilt = 0.55 + 0.42 * Math.sin(t * 0.11);
        double grow = Math.max(0.03, env);
        boolean outro = tick > total - outroTicks;
        double spin = outro ? 1.0 : 1.0;

        // звёзды (каждая 4-я за тик)
        for (int i = (tick & 3); i < STARS; i += 4) {
            double[] q = starPos(i, grow, spin, tilt);
            dust(q[0], q[1], q[2], scol[i], ssz[i]);
        }

        // ядро
        double pulse = 1 + 0.18 * Math.sin(t * 2.6);
        put("GLOW", 0, 0, 0, 2, 0.05 * R, 0.05 * R, 0.05 * R, 0.01);
        if (tick % 2 == 0) {
            for (int k = 0; k < 12; k++) {
                double[] u = unit();
                double rr = 0.07 * R * grow * Math.abs(gauss()) * pulse;
                dust(u[0] * rr, u[1] * rr * 0.8, u[2] * rr, rgb(255, 232, 170), 1.7 * dustK);
            }
        }
        if (tick % 3 == 0) put("END_ROD", 0, 0, 0, 2, 0.1 * R * grow, 0.08 * R * grow, 0.1 * R * grow, 0.005);

        // блёстки вдоль рукавов
        if (tick % 2 == 0) {
            int i = rnd.nextInt(STARS);
            double[] q = starPos(i, grow, spin, tilt);
            put("END_ROD", q[0], q[1], q[2], 1, 0.05, 0.05, 0.05, 0.002);
        }

        // квазарные джеты
        double jet = smooth((p - 0.42) / 0.08) * (1 - smooth((p - 0.78) / 0.08));
        if (jet > 0.01) {
            double nx = 0, ny = Math.cos(tilt), nz = Math.sin(tilt);
            for (int side = -1; side <= 1; side += 2) {
                for (int k = 0; k < 10; k++) {
                    double h = rnd.nextDouble() * R * 1.05 * jet;
                    double rad = (0.025 + 0.05 * h / R) * R;
                    double a = h * 1.7 + t * 6 + side;
                    double[] q = disc(Math.cos(a) * rad, side * h, Math.sin(a) * rad, tilt);
                    int c = grad(h / R, rgb(255, 255, 255), rgb(170, 215, 255), rgb(120, 130, 255));
                    dust(q[0], q[1], q[2], c, 1.1 * dustK);
                }
                if (tick % 2 == 0) {
                    double h = rnd.nextDouble() * R * jet;
                    put("ELECTRIC_SPARK", nx + 0, side * h * ny, side * h * nz, 1, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }

        // сверхновые
        if (tick >= nextNova && p < 0.88) {
            nextNova = tick + 130 + rnd.nextInt(90);
            int i;
            do { i = rnd.nextInt(STARS); } while (sr[i] < 0.35);
            double[] q = starPos(i, grow, spin, tilt);
            novas.add(new double[]{q[0], q[1], q[2], 0});
            burst("END_ROD", q[0], q[1], q[2], 46, 0.16 * Math.sqrt(scale));
            burst("FIREWORK", q[0], q[1], q[2], 24, 0.10 * Math.sqrt(scale));
            sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, q[0], q[1], q[2], 2f, 0.6f);
        }
        for (Iterator<double[]> it = novas.iterator(); it.hasNext(); ) {
            double[] n = it.next();
            n[3]++;
            double rad = n[3] * 0.045 * scale + 0.1;
            int c = mix(rgb(255, 255, 255), rgb(255, 120, 60), n[3] / 30.0);
            if (n[3] % 2 == 0) {
                for (double[] d : sphere(28)) {
                    dust(n[0] + d[0] * rad, n[1] + d[1] * rad, n[2] + d[2] * rad, c, 1.2 * dustK);
                }
            }
            if (n[3] > 30) it.remove();
        }

        // кометы
        if (tick >= nextComet && p < 0.9) {
            nextComet = tick + 80 + rnd.nextInt(70);
            double a = rnd.nextDouble() * Math.PI * 2;
            double sx = Math.cos(a) * R * 1.5, sz = Math.sin(a) * R * 1.5;
            double sY = (rnd.nextDouble() - 0.2) * R * 0.8;
            double ta = a + Math.PI + (rnd.nextDouble() - 0.5) * 1.2;
            double tx = Math.cos(ta) * R * 0.6, tz = Math.sin(ta) * R * 0.6;
            double life = 28;
            comets.add(new double[]{sx, sY, sz, (tx - sx) / life, (-sY * 0.3) / life, (tz - sz) / life, life});
        }
        for (Iterator<double[]> it = comets.iterator(); it.hasNext(); ) {
            double[] c = it.next();
            c[0] += c[3]; c[1] += c[4]; c[2] += c[5]; c[6]--;
            put("END_ROD", c[0], c[1], c[2], 1, 0, 0, 0, 0);
            dust(c[0], c[1], c[2], rgb(255, 255, 255), 1.9 * dustK);
            for (int k = 1; k <= 7; k++) {
                dust(c[0] - c[3] * k * 0.9, c[1] - c[4] * k * 0.9, c[2] - c[5] * k * 0.9,
                        grad(k / 7.0, rgb(200, 230, 255), rgb(110, 150, 255), rgb(60, 60, 160)),
                        (1.5 - k * 0.12) * dustK);
            }
            if (c[6] <= 0) it.remove();
        }

        // финальная ударная волна (после схлопывания)
        int into = tick - (total - 42);
        if (into >= 0) {
            double rad = R * (0.15 + into * 0.06);
            int n = 70;
            for (int k = 0; k < n; k++) {
                double a = Math.PI * 2 * k / n;
                double[] q = disc(Math.cos(a) * rad, 0, Math.sin(a) * rad, tilt);
                dust(q[0], q[1], q[2], mix(rgb(255, 255, 255), rgb(120, 140, 255), into / 42.0), 1.5 * dustK);
            }
        }
    }

    @Override
    void finale() {
        double s = 0.9 * Math.sqrt(scale);
        burst("END_ROD", 0, 0, 0, 260, 0.8 * s);
        burst("FIREWORK", 0, 0, 0, 120, 0.5 * s);
        burst("ELECTRIC_SPARK", 0, 0, 0, 100, 0.7 * s);
        for (int i = 0; i < 4; i++) ringVert(0, 0, 0, R * (0.4 + 0.3 * i), 70, rgb(255, 255, 255), 1.6 * dustK);
        put("EXPLOSION_EMITTER", 0, 0, 0, 1, 0, 0, 0, 0);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, 0, 0, 2f, 0.5f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, 0, 0, 1.5f, 0.6f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, 0, 0, 2f, 0.7f);
    }
}
