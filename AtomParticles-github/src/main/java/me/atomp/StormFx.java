package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Грозовой смерч: воронка, штормовая туча, ливень, ветвистые молнии и финальный удар. */
final class StormFx extends FxEffect {

    private static final int FUNNEL = 130;
    private static final int CLOUD = 80;

    private final double H;     // высота воронки
    private final double RT;    // радиус воронки сверху
    private final double RC;    // радиус тучи
    private final double R0;    // радиус у земли

    private static final class Bolt {
        final List<double[]> pts = new ArrayList<>();
        int life;
        double gx, gz;
    }

    private final List<Bolt> bolts = new ArrayList<>();
    private int nextBolt;
    private int flash;

    StormFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 5.5 * scale, 0.0, true);
        this.H = 9.0 * scale;
        this.RT = 3.0 * scale;
        this.RC = 5.5 * scale;
        this.R0 = 0.35 * scale;
        this.nextBolt = introTicks + 10;
    }

    @Override String title() { return "Смерч"; }

    @Override
    void begin() {
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, H, 0, 1.5f, 0.5f);
    }

    private double axisX(double u, double t) { return Math.sin(t * 0.7 + u * 2.0) * 0.35 * scale * u; }

    private double axisZ(double u, double t) { return Math.cos(t * 0.55 + u * 1.7) * 0.35 * scale * u; }

    private Bolt makeBolt(double sx, double sy, double sz, double ex, double ey, double ez, double jag) {
        Bolt b = new Bolt();
        int n = 26;
        double px = 0, pz = 0;
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            double x = lerp(sx, ex, f), y = lerp(sy, ey, f), z = lerp(sz, ez, f);
            if (i > 0 && i < n) {
                px = px * 0.45 + gauss() * jag;
                pz = pz * 0.45 + gauss() * jag;
                x += px; z += pz;
            }
            b.pts.add(new double[]{x, y, z});
        }
        b.gx = ex;
        b.gz = ez;
        b.life = 7;
        return b;
    }

    private void spawnBolt(boolean thunder) {
        double a = rnd.nextDouble() * Math.PI * 2;
        double rr = Math.sqrt(rnd.nextDouble()) * RC * 0.75;
        double sx = Math.cos(a) * rr, sz = Math.sin(a) * rr;
        double ga = rnd.nextDouble() * Math.PI * 2;
        double gr = (0.3 + rnd.nextDouble() * 1.2) * RT * 1.4;
        double ex = Math.cos(ga) * gr, ez = Math.sin(ga) * gr;
        Bolt main = makeBolt(sx, H + 0.6 * scale, sz, ex, 0.05, ez, 0.55 * scale);
        bolts.add(main);
        int forks = 2 + rnd.nextInt(2);
        for (int k = 0; k < forks; k++) {
            double[] from = main.pts.get(4 + rnd.nextInt(14));
            double fa = rnd.nextDouble() * Math.PI * 2;
            double len = (2.0 + rnd.nextDouble() * 3.5) * scale;
            Bolt f = makeBolt(from[0], from[1], from[2],
                    from[0] + Math.cos(fa) * len, Math.max(0.05, from[1] - len * 1.3), from[2] + Math.sin(fa) * len,
                    0.3 * scale);
            f.life = 5;
            bolts.add(f);
        }
        flash = 5;
        // удар по земле
        burst("ELECTRIC_SPARK", ex, 0.2, ez, 16, 0.12 * Math.sqrt(scale));
        ringFlat(ex, 0.1, ez, 0.9 * scale, 18, rgb(210, 225, 255), 1.3 * dustK);
        if (thunder) {
            sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, ex, 1, ez, 1.0f, 0.7f + rnd.nextFloat() * 0.5f);
        }
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double hEff = H * Math.max(0.08, env);
        double rEff = Math.max(0.15, env);

        // воронка
        for (int k = 0; k < FUNNEL; k++) {
            double u = ((k * 0.618034 + tick * 0.012) % 1.0);
            u = Math.pow(u, 0.9);
            double h = u * hEff;
            double rad = (R0 + (RT - R0) * Math.pow(u, 1.7)) * rEff;
            double ang = k * 2.399963 + t * (7.5 - 4.0 * u);
            double x = axisX(u, t) + Math.cos(ang) * rad;
            double z = axisZ(u, t) + Math.sin(ang) * rad;
            int c = grad(u, rgb(62, 52, 44), rgb(96, 96, 106), rgb(150, 150, 168));
            if (flash > 0) c = mix(c, rgb(230, 230, 255), 0.4);
            dust(x, h, z, c, (1.3 + 0.7 * u) * dustK);
        }

        // обломки у земли
        for (int k = 0; k < 8; k++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = (R0 + rnd.nextDouble() * 2.2 * scale) * rEff;
            dust(Math.cos(a) * rr, rnd.nextDouble() * 1.6 * scale, Math.sin(a) * rr, rgb(112, 86, 55), 0.9 * dustK);
        }
        put("LARGE_SMOKE", 0, 0.2, 0, 1, 0.5 * scale, 0.1, 0.5 * scale, 0.01);

        // туча
        double cy = hEff + 0.8 * scale;
        for (int k = 0; k < CLOUD; k++) {
            double rr = RC * rEff * Math.sqrt((k + 0.5) / CLOUD);
            double a = k * 2.399963 + t * 0.4;
            double lay = (k % 2 == 0 ? 0.0 : 0.55 * scale) + gauss() * 0.15 * scale;
            int c = mix(rgb(42, 42, 58), rgb(96, 96, 118), rnd.nextDouble());
            if (flash > 0) c = mix(c, rgb(235, 235, 255), 0.5 + 0.1 * flash);
            dust(Math.cos(a) * rr, cy + lay, Math.sin(a) * rr, c, 2.0 * dustK);
        }

        // ливень
        for (int k = 0; k < 6; k++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = Math.sqrt(rnd.nextDouble()) * RC * 0.9 * rEff;
            put("FALLING_WATER", Math.cos(a) * rr, cy - 0.3, Math.sin(a) * rr, 1, 0.1, 0.0, 0.1, 0);
        }

        // молнии
        double intensity = smooth(p / 0.18) * (1 - smooth((p - 0.9) / 0.08));
        if (tick >= nextBolt && intensity > 0.05) {
            double base = lerp(40, 13, intensity);
            nextBolt = tick + (int) (base + rnd.nextDouble() * base);
            spawnBolt(rnd.nextInt(3) != 0);
        }
        for (Iterator<Bolt> it = bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (b.life % 2 == 0 || b.life > 4) {
                for (int i = 0; i < b.pts.size() - 1; i++) {
                    double[] a = b.pts.get(i), c = b.pts.get(i + 1);
                    double dx = c[0] - a[0], dy = c[1] - a[1], dz = c[2] - a[2];
                    int steps = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) / 0.4));
                    for (int s = 0; s < steps; s++) {
                        double f = s / (double) steps;
                        dust(a[0] + dx * f, a[1] + dy * f, a[2] + dz * f, rgb(225, 235, 255), 1.4 * dustK);
                    }
                    if (i % 3 == 0) put("ELECTRIC_SPARK", a[0], a[1], a[2], 1, 0.1, 0.1, 0.1, 0.02);
                }
            }
            if (--b.life <= 0) it.remove();
        }
        if (flash > 0) flash--;
    }

    @Override
    void finale() {
        // мега-молния по оси воронки
        for (int k = 0; k < 5; k++) {
            Bolt b = makeBolt(gauss() * 0.4, H + 0.6 * scale, gauss() * 0.4, gauss() * 0.3, 0.05, gauss() * 0.3, 0.5 * scale);
            for (int i = 0; i < b.pts.size() - 1; i++) {
                double[] a = b.pts.get(i), c = b.pts.get(i + 1);
                for (int s = 0; s < 4; s++) {
                    double f = s / 4.0;
                    dust(lerp(a[0], c[0], f), lerp(a[1], c[1], f), lerp(a[2], c[2], f), rgb(240, 245, 255), 2.2 * dustK);
                }
                put("ELECTRIC_SPARK", a[0], a[1], a[2], 2, 0.2, 0.2, 0.2, 0.05);
            }
        }
        double s = Math.sqrt(scale);
        burst("ELECTRIC_SPARK", 0, 0.5, 0, 140, 0.45 * s);
        burst("END_ROD", 0, 1.0, 0, 90, 0.35 * s);
        burst("CLOUD", 0, 0.5, 0, 60, 0.3 * s);
        for (int i = 1; i <= 4; i++) ringFlat(0, 0.15, 0, i * 1.6 * scale, 40 + 8 * i, rgb(200, 215, 255), 1.5 * dustK);
        put("EXPLOSION_EMITTER", 0, 0.5, 0, 1, 0, 0, 0, 0);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, 1, 0, 3f, 0.5f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, 1, 0, 3f, 0.8f);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, 1, 0, 2f, 0.5f);
    }
}
