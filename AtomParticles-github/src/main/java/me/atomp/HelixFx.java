package me.atomp;

import org.bukkit.Sound;

/**
 * Двойная спираль света: вращающаяся «ДНК» растёт от земли в небо, перекладины-мосты,
 * бегущие энергетические импульсы, внешняя вуаль, световой столб и кольца у основания.
 */
final class HelixFx extends FxEffect {

    private static final int NODES = 110;
    private static final int OUTER = 70;
    private static final double TURNS = 3.0;
    private static final int PULSES = 4;

    private final double H;
    private final double Rh;

    HelixFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 2.6 * scale, 0.0, true);
        this.H = 15.0 * scale;
        this.Rh = 2.4 * scale;
    }

    @Override String title() { return "Двойная спираль"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 0.7f);
    }

    private static int colA(double s) {
        return grad(s, rgb(70, 230, 255), rgb(90, 120, 255), rgb(190, 110, 255));
    }

    private static int colB(double s) {
        return grad(s, rgb(255, 215, 90), rgb(255, 140, 90), rgb(255, 100, 190));
    }

    /** Радиус спирали на относительной высоте s. */
    private double radius(double s, double breathe) {
        return Rh * breathe * (0.55 + 0.45 * Math.sin(Math.PI * s));
    }

    @Override
    void draw(double env) {
        double t = sec();
        double g = smooth(env);
        double h = H * Math.max(0.03, g);
        double breathe = 1.0 + 0.25 * Math.sin(t * 0.6);
        double rot = t * 0.8;

        // ---- две нити
        for (int strand = 0; strand < 2; strand++) {
            double off = strand * Math.PI;
            for (int j = (tick % 3); j < NODES; j += 3) {
                double s = j / (double) (NODES - 1);
                double a = TURNS * Math.PI * 2 * s + rot + off;
                double rr = radius(s, breathe);
                int c = (strand == 0) ? colA(s) : colB(s);
                dust(Math.cos(a) * rr, s * h, Math.sin(a) * rr, c, 1.15 * dustK);
            }
        }

        // ---- перекладины между нитями
        for (int j = 0; j < NODES; j += 3) {
            if (((j / 3) + tick) % 3 != 0) continue;
            double s = j / (double) (NODES - 1);
            double a = TURNS * Math.PI * 2 * s + rot;
            double rr = radius(s, breathe);
            double x1 = Math.cos(a) * rr, z1 = Math.sin(a) * rr;
            double x2 = -x1, z2 = -z1;
            double y = s * h;
            int c1 = colA(s), c2 = colB(s);
            for (int k = 1; k <= 6; k++) {
                double f = k / 7.0;
                int c = mix(c1, c2, f);
                double mid = 1 - Math.abs(f - 0.5) * 2;
                c = mix(c, rgb(255, 255, 255), mid * 0.5);
                dust(lerp(x1, x2, f), y, lerp(z1, z2, f), c, 0.85 * dustK);
            }
            dust(x1, y, z1, c1, 1.9 * dustK);
            dust(x2, y, z2, c2, 1.9 * dustK);
        }

        // ---- бегущие энергетические импульсы
        for (int strand = 0; strand < 2; strand++) {
            double off = strand * Math.PI;
            for (int i = 0; i < PULSES; i++) {
                double s = (t * 0.12 + i / (double) PULSES + strand * 0.5) % 1.0;
                for (int k = 0; k <= 6; k++) {
                    double ss = s - k * 0.012;
                    if (ss < 0) continue;
                    double a = TURNS * Math.PI * 2 * ss + rot + off;
                    double rr = radius(ss, breathe);
                    int c = (k == 0) ? rgb(255, 255, 255)
                            : mix(rgb(220, 250, 255), (strand == 0) ? colA(ss) : colB(ss), k / 6.0);
                    dust(Math.cos(a) * rr, ss * h, Math.sin(a) * rr, c, (1.9 - k * 0.18) * dustK);
                    if (k == 0 && tick % 2 == 0) {
                        put("END_ROD", Math.cos(a) * rr, ss * h, Math.sin(a) * rr, 1, 0.05, 0.05, 0.05, 0.01);
                    }
                }
            }
        }

        // ---- внешняя вуаль (встречная спираль)
        for (int j = (tick % 4); j < OUTER; j += 4) {
            double s = j / (double) (OUTER - 1);
            double a = -2.0 * Math.PI * 2 * s - rot * 0.6;
            double rr = Rh * 1.7 * (0.5 + 0.5 * Math.sin(Math.PI * s));
            dust(Math.cos(a) * rr, s * h, Math.sin(a) * rr,
                    mix(rgb(200, 235, 255), rgb(150, 160, 255), s), 0.8 * dustK);
        }

        // ---- световой столб по оси
        for (int k = 0; k < 10; k++) {
            double y = rnd.nextDouble() * h;
            dust(gauss() * 0.05 * scale, y, gauss() * 0.05 * scale,
                    mix(rgb(255, 255, 255), rgb(140, 220, 255), rnd.nextDouble()), 0.8 * dustK);
        }
        if (tick % 2 == 0) {
            double y = rnd.nextDouble() * h;
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = Rh * rnd.nextDouble();
            put("END_ROD", Math.cos(a) * rr, y, Math.sin(a) * rr, 1, 0.1, 0.1, 0.1, 0.02);
        }

        // ---- кольца у основания и корона наверху
        if (tick % 2 == 0) {
            ringFlat(0, 0.05, 0, Rh * 1.5 * g, 60, rgb(120, 230, 255), 1.1 * dustK);
            int dashes = 40;
            for (int d = 0; d < dashes; d++) {
                double a = t * 0.5 + Math.PI * 2 * d / dashes;
                double rr = Rh * 2.1 * g;
                dust(Math.cos(a) * rr, 0.05, Math.sin(a) * rr, rgb(255, 215, 120), 1.0 * dustK);
            }
            ringFlat(0, h, 0, Rh * 1.2 * g, 50, rgb(220, 140, 255), 1.1 * dustK);
        }

        // ---- луч в небо
        if (tick % 3 == 0 && g > 0.5) {
            putDir("END_ROD", 0, h, 0, 0, 1, 0, 0.35);
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        burst("END_ROD", 0, H, 0, 140, 0.5 * s);
        burst("FIREWORK", 0, H, 0, 70, 0.3 * s);
        burst("END_ROD", 0, H * 0.5, 0, 100, 0.4 * s);
        for (int k = 0; k < 6; k++) {
            double y = H * k / 5.0;
            ringFlat(0, y, 0, Rh * (1.2 + k * 0.2), 64, grad(k / 5.0, rgb(70, 230, 255), rgb(190, 110, 255), rgb(255, 215, 90)), 1.8 * dustK);
        }
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, H, 0, 2f, 0.7f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, H * 0.5, 0, 1.2f, 0.7f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, 0, 0, 2f, 0.8f);
    }
}
