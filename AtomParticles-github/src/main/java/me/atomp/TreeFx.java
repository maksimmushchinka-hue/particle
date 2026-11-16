package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.List;

/** Древо жизни: растёт из земли, проходит весну → лето → осень и рассыпается светом. */
final class TreeFx extends FxEffect {

    private static final int MAXD = 5;

    private static final class Seg {
        double ax, ay, az, bx, by, bz;
        int depth;
        double len;
    }

    private final List<Seg> segs = new ArrayList<>();
    private final List<double[]> tips = new ArrayList<>();
    private final List<double[]> roots = new ArrayList<>(); // каждая: углы+длина
    private final int growTicks;
    private final double crownR;
    private final double crownY;

    TreeFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 4.5 * scale, 0.0, true);
        this.growTicks = Math.max(40, Math.min(300, (int) (total * 0.28)));
        build(0, 0, 0, 0.04, 1.0, 0.03, 4.2 * scale, 0);
        double sy = 0, ay = 0, ar = 0;
        for (double[] t : tips) { sy += t[1]; ay = Math.max(ay, t[1]); ar = Math.max(ar, Math.hypot(t[0], t[2])); }
        this.crownY = tips.isEmpty() ? 7 * scale : sy / tips.size();
        this.crownR = Math.max(2.5 * scale, ar * 0.9);
        for (int i = 0; i < 6; i++) {
            roots.add(new double[]{i * Math.PI / 3 + rnd.nextDouble() * 0.5, (1.6 + rnd.nextDouble() * 1.4) * scale, (rnd.nextDouble() - 0.5) * 1.6});
        }
    }

    @Override String title() { return "Древо жизни"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 1.3f);
    }

    private void build(double x, double y, double z, double dx, double dy, double dz, double len, int depth) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= l; dy /= l; dz /= l;
        Seg s = new Seg();
        s.ax = x; s.ay = y; s.az = z;
        s.bx = x + dx * len; s.by = y + dy * len; s.bz = z + dz * len;
        s.depth = depth;
        s.len = len;
        segs.add(s);
        if (depth >= MAXD) {
            tips.add(new double[]{s.bx, s.by, s.bz});
            return;
        }
        int n = depth == 0 ? 3 : (depth <= 2 && rnd.nextDouble() < 0.3 ? 3 : 2);
        double base = rnd.nextDouble() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double az = base + i * Math.PI * 2 / n + (rnd.nextDouble() - 0.5) * 0.6;
            double tilt = 0.42 + rnd.nextDouble() * 0.38;
            // перпендикуляр к направлению
            double px, py, pz;
            if (Math.abs(dy) < 0.9) { px = -dz; py = 0; pz = dx; } else { px = 1; py = 0; pz = 0; }
            double pl = Math.sqrt(px * px + py * py + pz * pz);
            px /= pl; py /= pl; pz /= pl;
            double qx = dy * pz - dz * py, qy = dz * px - dx * pz, qz = dx * py - dy * px;
            double ca = Math.cos(az), sa = Math.sin(az);
            double ox = px * ca + qx * sa, oy = py * ca + qy * sa, oz = pz * ca + qz * sa;
            double ct = Math.cos(tilt), st = Math.sin(tilt);
            double nx = dx * ct + ox * st, ny = dy * ct + oy * st + 0.22, nz = dz * ct + oz * st;
            build(s.bx, s.by, s.bz, nx, ny, nz, len * (0.68 + 0.12 * rnd.nextDouble()), depth + 1);
        }
    }

    private int leafColor(double q) {
        return grad(q, rgb(255, 170, 210), rgb(255, 190, 225), rgb(110, 220, 90), rgb(60, 200, 110),
                rgb(255, 170, 40), rgb(230, 90, 30), rgb(255, 232, 160));
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double g = smooth(tick / (double) growTicks) * env;
        double pulseT = (t * 0.45) % 1.3;

        // ---- ветви
        for (int i = (tick % 3); i < segs.size(); i += 3) {
            Seg s = segs.get(i);
            double fr = clamp01(g * (MAXD + 1) - s.depth);
            if (fr <= 0.001) continue;
            double ex = lerp(s.ax, s.bx, fr), ey = lerp(s.ay, s.by, fr), ez = lerp(s.az, s.bz, fr);
            double len = s.len * fr;
            int steps = Math.max(2, (int) (len / 0.33));
            double thick = Math.max(0.9, 2.1 - s.depth * 0.25) * dustK;
            for (int k = 0; k <= steps; k++) {
                double f = k / (double) steps;
                double x = lerp(s.ax, ex, f), y = lerp(s.ay, ey, f), z = lerp(s.az, ez, f);
                double along = (s.depth + f) / (MAXD + 1.0);
                int c = grad(along, rgb(105, 66, 32), rgb(140, 96, 50), rgb(195, 155, 85));
                double sz = thick;
                if (Math.abs(along - pulseT) < 0.07) { c = rgb(255, 238, 165); sz += 0.5 * dustK; }
                dust(x, y, z, c, sz);
                if (s.depth == 0 && k % 2 == 0) {
                    double a = k * 1.7 + t;
                    dust(x + Math.cos(a) * 0.2 * scale, y, z + Math.sin(a) * 0.2 * scale, rgb(95, 58, 28), thick);
                }
            }
        }

        // ---- корни
        double rootG = smooth(tick / (double) (growTicks * 0.5)) * env;
        for (double[] r : roots) {
            int n = 8;
            for (int k = 1; k <= n; k++) {
                double f = k / (double) n * rootG;
                double a = r[0] + r[2] * f;
                double rr = f * r[1];
                dust(Math.cos(a) * rr, 0.05, Math.sin(a) * rr, grad(f, rgb(110, 70, 34), rgb(80, 52, 26)), 1.4 * dustK);
            }
        }

        // ---- листва по сезонам
        double leafG = clamp01(g * (MAXD + 1) - MAXD);
        if (leafG > 0.01) {
            double q = clamp01((p - 0.22) / 0.78); // сезоны начинаются, когда появляется листва
            double density = leafG * (q > 0.75 ? lerp(1.0, 0.45, (q - 0.75) / 0.25) : 1.0);
            for (int i = (tick & 1); i < tips.size(); i += 2) {
                double[] tp = tips.get(i);
                for (int k = 0; k < 2; k++) {
                    if (rnd.nextDouble() > density) continue;
                    double[] u = unit();
                    double rr = (0.4 + rnd.nextDouble() * 0.55) * scale;
                    double jitter = (rnd.nextDouble() - 0.5) * 0.12;
                    int c = leafColor(clamp01(q + jitter));
                    dust(tp[0] + u[0] * rr, tp[1] + u[1] * rr * 0.8, tp[2] + u[2] * rr, c, (1.0 + rnd.nextDouble() * 0.5) * dustK);
                }
            }
            // падающие лепестки
            if (rnd.nextDouble() < 0.7 * density && (q < 0.35 || q > 0.62)) {
                double[] tp = tips.get(rnd.nextInt(tips.size()));
                put("CHERRY_LEAVES", tp[0], tp[1] - 0.3, tp[2], 1, 0.5 * scale, 0.2, 0.5 * scale, 0);
            }
            // светлячки
            for (int k = 0; k < 2; k++) {
                double[] u = unit();
                double rr = crownR * Math.cbrt(rnd.nextDouble());
                put(rnd.nextBoolean() ? "GLOW" : "END_ROD", u[0] * rr, crownY + u[1] * rr * 0.7, u[2] * rr, 1, 0.1, 0.1, 0.1, 0.01);
            }
        }

        // ---- мистический круг под деревом
        double ringG = smooth(tick / 60.0) * env;
        if (ringG > 0.05) {
            double rr = 2.6 * scale;
            for (int k = 0; k < 22; k++) {
                double a = Math.PI * 2 * k / 22 + t * 0.4;
                int c = mix(rgb(70, 190, 100), rgb(255, 225, 120), 0.5 + 0.5 * Math.sin(a * 3 + t * 2));
                dust(Math.cos(a) * rr, 0.06, Math.sin(a) * rr, c, 1.0 * dustK);
            }
            if (tick % 5 == 0) {
                double a = -t * 0.6;
                put("ENCHANT", Math.cos(a) * rr, 0.4, Math.sin(a) * rr, 3, 0.3, 0.3, 0.3, 0.4);
            }
        }

        // ---- распад в свет в финале
        if (env < 0.999 && tick > total / 2) {
            for (int k = 0; k < 14; k++) {
                Seg s = segs.get(rnd.nextInt(segs.size()));
                double f = rnd.nextDouble();
                double x = lerp(s.ax, s.bx, f), y = lerp(s.ay, s.by, f), z = lerp(s.az, s.bz, f);
                putDir("END_ROD", x, y, z, gauss() * 0.2, 1.0, gauss() * 0.2, 0.05);
            }
            for (int k = 0; k < 6; k++) {
                double[] u = unit();
                double rr = crownR * Math.cbrt(rnd.nextDouble());
                dust(u[0] * rr, crownY + u[1] * rr * 0.7 + (1 - env) * 2 * scale, u[2] * rr, rgb(255, 235, 150), 1.4 * dustK);
            }
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        burst("END_ROD", 0, crownY, 0, 120, 0.45 * s);
        burst("FIREWORK", 0, crownY, 0, 70, 0.30 * s);
        burst("TOTEM_OF_UNDYING", 0, crownY, 0, 50, 0.40 * s);
        for (int i = 1; i <= 4; i++) ringFlat(0, 0.15, 0, i * 1.5 * scale, 36 + 8 * i, rgb(255, 225, 120), 1.6 * dustK);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, crownY, 0, 2f, 1.0f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, 0, 0, 2f, 1.5f);
    }
}
