package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Северное сияние: четыре колышущихся занавеса света, мерцающие звёзды,
 * падающие метеоры, «магнитная буря» с усилением сияния и финальный всплеск.
 */
final class AuroraFx extends FxEffect {

    private static final int CURTAINS = 4;
    private static final int RAYS = 36;
    private static final int PTS = 9;
    private static final int STARS = 90;

    private static final int[] PAL_A = {rgb(120, 255, 150), rgb(60, 235, 190), rgb(70, 150, 255), rgb(160, 100, 255)};
    private static final int[] PAL_B = {rgb(90, 255, 200), rgb(70, 200, 255), rgb(150, 110, 255), rgb(255, 110, 210)};

    private final double R;
    private final double[] phase = new double[CURTAINS];
    private final double[][] stars = new double[STARS][3];
    private final List<double[]> meteors = new ArrayList<>(); // x,y,z,vx,vy,vz,life
    private int nextMeteor;

    AuroraFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 9.0 * scale, 0.35 * 9.0 * scale, false);
        this.R = 9.0 * scale;
        for (int k = 0; k < CURTAINS; k++) phase[k] = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < STARS; i++) {
            stars[i][0] = (rnd.nextDouble() * 2 - 1) * 1.5 * R;
            stars[i][1] = (0.15 + rnd.nextDouble() * 1.15) * R;
            stars[i][2] = R * (0.9 + rnd.nextDouble() * 0.7);
        }
        nextMeteor = introTicks + 60;
    }

    @Override String title() { return "Северное сияние"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0.3 * R, 0, 2f, 1.5f);
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double e = Math.max(0.02, env);
        // «магнитная буря»: занавесы вытягиваются вверх и розовеют
        double storm = smooth((p - 0.45) / 0.10) * (1 - smooth((p - 0.72) / 0.10));
        double hk = 0.75 + 0.50 * storm;

        for (int k = 0; k < CURTAINS; k++) {
            double z0 = (k - (CURTAINS - 1) / 2.0) * R * 0.30;
            double ph = phase[k];
            int[] pal = (k % 2 == 0) ? PAL_A : PAL_B;
            for (int i = (tick + k) % 3; i < RAYS; i += 3) {
                double u = i / (double) (RAYS - 1);
                double x = (u * 2 - 1) * R * (0.95 + 0.05 * k);
                double z = z0 + R * 0.16 * Math.sin(u * 5.0 + t * 0.45 + ph)
                        + R * 0.06 * Math.sin(u * 13.0 - t * 0.8 + ph * 2);
                double yb = -0.40 * R + 0.05 * R * Math.sin(u * 7.0 + t * 0.6 + ph);
                double h = R * hk * e
                        * (0.55 + 0.45 * (0.5 + 0.5 * Math.sin(u * 6.0 - t * 0.9 + ph * 1.7)))
                        * (0.6 + 0.4 * Math.sin(u * Math.PI));
                for (int j = 0; j < PTS; j++) {
                    double f = j / (PTS - 1.0);
                    if (rnd.nextDouble() < f * 0.35) continue;
                    double y = yb + h * f;
                    int c = grad(f + 0.25 * storm, pal[0], pal[1], pal[2], pal[3]);
                    dust(x, y, z, c, (1.1 - 0.5 * f) * dustK);
                }
            }
            // искры на верхушках лучей
            if (tick % 2 == 0) {
                double u = rnd.nextDouble();
                double x = (u * 2 - 1) * R;
                double z = z0 + R * 0.16 * Math.sin(u * 5.0 + t * 0.45 + ph);
                double yTop = -0.40 * R + R * hk * e * 0.8;
                put("END_ROD", x, yTop, z, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }

        // звёзды
        if (tick % 4 == 0) {
            for (int k = 0; k < 6; k++) {
                double[] s = stars[rnd.nextInt(STARS)];
                put("END_ROD", s[0], s[1], s[2], 1, 0, 0, 0, 0);
            }
        }

        // метеоры
        if (tick >= nextMeteor && p < 0.9) {
            nextMeteor = tick + 70 + rnd.nextInt(60);
            double sx = (rnd.nextDouble() * 2 - 1) * R * 1.2;
            double sy = (0.9 + rnd.nextDouble() * 0.5) * R;
            double sz = R * (0.8 + rnd.nextDouble() * 0.5);
            double dirX = (rnd.nextDouble() < 0.5 ? -1 : 1);
            double life = 26;
            meteors.add(new double[]{sx, sy, sz, dirX * R * 1.0 / life, -R * 0.55 / life, 0, life});
        }
        for (Iterator<double[]> it = meteors.iterator(); it.hasNext(); ) {
            double[] m = it.next();
            m[0] += m[3];
            m[1] += m[4];
            m[2] += m[5];
            m[6]--;
            put("END_ROD", m[0], m[1], m[2], 1, 0, 0, 0, 0);
            dust(m[0], m[1], m[2], rgb(255, 255, 255), 1.7 * dustK);
            for (int k = 1; k <= 7; k++) {
                dust(m[0] - m[3] * k * 0.9, m[1] - m[4] * k * 0.9, m[2] - m[5] * k * 0.9,
                        grad(k / 7.0, rgb(210, 240, 255), rgb(120, 190, 255), rgb(70, 90, 200)),
                        (1.4 - k * 0.1) * dustK);
            }
            if (m[6] <= 0) it.remove();
        }

        // мерцающее свечение у горизонта
        if (tick % 3 == 0) {
            for (int k = 0; k < 8; k++) {
                double x = (rnd.nextDouble() * 2 - 1) * R;
                double z = (rnd.nextDouble() * 2 - 1) * R * 0.4;
                dust(x, -0.42 * R + rnd.nextDouble() * 0.06 * R, z,
                        mix(rgb(60, 255, 170), rgb(80, 200, 255), rnd.nextDouble()), 1.4 * dustK);
            }
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        double cyc = 0.3 * R;
        burst("END_ROD", 0, cyc, R * 0.1, 160, 0.5 * s);
        burst("FIREWORK", 0, cyc, R * 0.1, 70, 0.3 * s);
        int[] cols = {rgb(120, 255, 170), rgb(90, 220, 255), rgb(190, 120, 255)};
        for (int k = 0; k < 3; k++) {
            ringVert(0, cyc, R * 0.1, R * (0.4 + 0.3 * k), 80, cols[k], 1.6 * dustK);
        }
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, cyc, 0, 2f, 0.7f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, cyc, 0, 2f, 1.2f);
    }
}
