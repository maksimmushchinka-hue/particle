package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Древний портал: каменное кольцо с бегущим светом, руны, вихрь, молнии-дуги, лучи и схлопывание. */
final class PortalFx extends FxEffect {

    private static final int RING_N = 150;
    private static final int STONES = 14;
    private static final int RUNES = 12;

    private final double R;
    private final double cyL; // центр портала по Y (локально от земли)

    private static final class Arc {
        final List<double[]> pts = new ArrayList<>();
        int life = 6;
    }

    private final List<Arc> arcs = new ArrayList<>();
    private final List<int[]> beams = new ArrayList<>(); // age
    private int nextSurge;
    private int nextBeam;
    private int surge;

    PortalFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 3.0 * scale * 1.2, 0.0, true);
        this.R = 3.0 * scale;
        this.cyL = R + 0.3 * scale;
        this.nextSurge = introTicks + 50;
        this.nextBeam = introTicks + 90;
    }

    @Override String title() { return "Портал"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, cyL, 0, 2f, 0.5f);
    }

    private static double wrap(double a) {
        a = (a + Math.PI) % (Math.PI * 2);
        if (a < 0) a += Math.PI * 2;
        return a - Math.PI;
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        boolean outro = tick > total - outroTicks;
        double arcFrac = outro ? 1.0 : smooth((tick + 1.0) / introTicks);
        double vortexK = clamp01((arcFrac - 0.5) * 2) * (outro ? env : 1.0);
        double vr = R * 0.93 * (outro ? (0.2 + 0.8 * env) : 1.0);
        double heat = outro ? 1 - env : 0;
        double speed = 1.6 + 3.5 * (surge > 0 ? 1 : 0) + 6 * heat;

        // ---- каменное кольцо
        for (int j = (tick & 1); j < RING_N; j += 2) {
            double a = Math.PI * 2 * j / RING_N;
            double fromBottom = Math.abs(wrap(a + Math.PI / 2));
            if (fromBottom > Math.PI * arcFrac) continue;
            double stone = (a / (Math.PI * 2) * STONES) % 1.0;
            if (stone < 0) stone += 1;
            if (stone > 0.84) continue;
            double hl = 0;
            for (int k = 0; k < 3; k++) {
                double hk = t * speed + k * Math.PI * 2 / 3;
                double dd = Math.abs(wrap(a - hk));
                hl = Math.max(hl, Math.exp(-dd * dd / 0.18));
            }
            hl = Math.min(1, hl + heat * 0.6);
            int base = mix(rgb(75, 42, 155), rgb(55, 150, 255), 0.5 + 0.5 * Math.sin(a * 3 + t));
            int c = mix(base, rgb(195, 250, 255), hl);
            dust(Math.cos(a) * R, cyL + Math.sin(a) * R, 0, c, (1.2 + 0.9 * hl) * dustK);
            if (stone < 0.08 && tick % 4 == 0) {
                dust(Math.cos(a) * R * 1.07, cyL + Math.sin(a) * R * 1.07, 0, rgb(120, 100, 210), 1.0 * dustK);
            }
        }

        // ---- руны на внешнем кольце
        if (arcFrac > 0.3) {
            double rr = R * 1.22;
            for (int k = 0; k < RUNES; k++) {
                double a = -t * 0.5 + k * Math.PI * 2 / RUNES;
                double x = Math.cos(a) * rr, y = cyL + Math.sin(a) * rr;
                dust(x, y, 0, rgb(255, 210, 110), 1.0 * dustK);
                dust(x + Math.cos(a + 1.57) * 0.12 * scale, y + Math.sin(a + 1.57) * 0.12 * scale, 0, rgb(255, 235, 170), 0.8 * dustK);
                if ((tick + k * 3) % 18 == 0) put("ENCHANT", x, y, 0, 3, 0.25, 0.25, 0.1, 0.4);
            }
        }

        // ---- вихрь
        if (vortexK > 0.02) {
            for (int arm = 0; arm < 3; arm++) {
                for (int j = 0; j < 14; j++) {
                    if (rnd.nextDouble() > vortexK) continue;
                    double u = ((j / 14.0) + tick * 0.012 * (1 + 2 * heat)) % 1.0;
                    double s = 1 - u;
                    double rad = vr * s;
                    double th = arm * Math.PI * 2 / 3 + 4.6 * (1 - s) + t * 1.8 * (1 + 1.5 * (1 - s));
                    int c = grad(1 - s, rgb(110, 60, 255), rgb(190, 90, 255), rgb(255, 205, 255));
                    dust(Math.cos(th) * rad, cyL + Math.sin(th) * rad, 0.02, c, (0.9 + 0.9 * (1 - s)) * dustK);
                }
            }
            // мембрана
            for (int k = 0; k < 10; k++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(rnd.nextDouble()) * vr * 0.92;
                dust(Math.cos(a) * rr, cyL + Math.sin(a) * rr, 0, mix(rgb(40, 14, 95), rgb(25, 10, 60), rnd.nextDouble()), 2.0 * dustK);
            }
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = Math.sqrt(rnd.nextDouble()) * vr * 0.8;
            put("REVERSE_PORTAL", Math.cos(a) * rr, cyL + Math.sin(a) * rr, 0, 2, 0.2, 0.2, 0.05, 0.05);
            put("PORTAL", Math.cos(a) * rr * 0.7, cyL + Math.sin(a) * rr * 0.7, 0, 1, 0.2, 0.2, 0.05, 0.3);
            // ядро
            double pulse = 1.8 + 0.5 * Math.sin(t * 5);
            for (int k = 0; k < 3; k++) {
                double[] u = unit();
                double q = 0.22 * scale * vortexK;
                dust(u[0] * q, cyL + u[1] * q, u[2] * q * 0.3, rgb(235, 250, 255), pulse * dustK);
            }
            put("END_ROD", 0, cyL, 0, 1, 0.12 * scale, 0.12 * scale, 0.05, 0.02);
            put("GLOW", 0, cyL, 0, 1, 0.2 * scale, 0.2 * scale, 0.05, 0.01);
        }

        // ---- всплески энергии
        if (surge > 0) surge--;
        if (tick >= nextSurge && p > 0.12 && p < 0.86) {
            nextSurge = tick + 100 + rnd.nextInt(60);
            surge = 28;
            for (int k = 0; k < 5; k++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Arc arc = new Arc();
                int n = 14;
                double sx = Math.cos(a) * R, sy = Math.sin(a) * R;
                double jx = 0, jy = 0;
                for (int i = 0; i <= n; i++) {
                    double f = i / (double) n;
                    double x = lerp(sx, 0, f), y = lerp(sy, 0, f);
                    if (i > 0 && i < n) {
                        jx = jx * 0.4 + gauss() * 0.35 * scale;
                        jy = jy * 0.4 + gauss() * 0.35 * scale;
                        x += jx; y += jy;
                    }
                    arc.pts.add(new double[]{x, cyL + y});
                }
                arcs.add(arc);
            }
            sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0, cyL, 0, 2f, 0.5f);
        }
        for (Iterator<Arc> it = arcs.iterator(); it.hasNext(); ) {
            Arc arc = it.next();
            if (arc.life % 2 == 0 || arc.life > 4) {
                for (int i = 0; i < arc.pts.size() - 1; i++) {
                    double[] a = arc.pts.get(i), b = arc.pts.get(i + 1);
                    for (int s = 0; s < 3; s++) {
                        double f = s / 3.0;
                        dust(lerp(a[0], b[0], f), lerp(a[1], b[1], f), 0.05, rgb(190, 245, 255), 1.2 * dustK);
                    }
                    if (i % 3 == 0) put("ELECTRIC_SPARK", a[0], a[1], 0, 1, 0.08, 0.08, 0.05, 0.02);
                }
            }
            if (--arc.life <= 0) it.remove();
        }

        // ---- лучи энергии через портал
        if (tick >= nextBeam && p > 0.18 && p < 0.84) {
            nextBeam = tick + 140 + rnd.nextInt(80);
            beams.add(new int[]{0});
        }
        for (Iterator<int[]> it = beams.iterator(); it.hasNext(); ) {
            int[] bm = it.next();
            double head = bm[0] * 0.5 * scale;
            put("END_ROD", 0, cyL, head, 1, 0.05, 0.05, 0.05, 0.01);
            dust(0, cyL, head, rgb(255, 255, 255), 2.0 * dustK);
            for (int m = 0; m < 10; m++) {
                double zz = head - m * 0.5 * scale;
                if (zz < 0) break;
                double ang = zz * 1.8 / scale + t * 8;
                double rad = 0.4 * scale * (1 - m * 0.04);
                int c = grad(m / 10.0, rgb(200, 250, 255), rgb(120, 90, 255));
                dust(Math.cos(ang) * rad, cyL + Math.sin(ang) * rad, zz, c, 1.2 * dustK);
                dust(-Math.cos(ang) * rad, cyL - Math.sin(ang) * rad, zz, c, 1.2 * dustK);
            }
            if (++bm[0] > 34) it.remove();
        }

        // ---- ударная волна при закрытии
        int into = tick - (total - 30);
        if (into >= 0) {
            double rad = R * (0.35 + into * 0.09);
            ringVert(0, cyL, 0.05, rad, 64, mix(rgb(255, 255, 255), rgb(130, 90, 255), into / 30.0), 1.5 * dustK);
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        burst("END_ROD", 0, cyL, 0, 120, 0.55 * s);
        burst("FIREWORK", 0, cyL, 0, 70, 0.35 * s);
        burst("REVERSE_PORTAL", 0, cyL, 0, 80, 0.6 * s);
        burst("ELECTRIC_SPARK", 0, cyL, 0, 60, 0.5 * s);
        double[] rs = {0.6, 1.1, 1.7, 2.4};
        for (double k : rs) ringVert(0, cyL, 0, R * k, 64, rgb(225, 215, 255), 1.6 * dustK);
        put("EXPLOSION_EMITTER", 0, cyL, 0, 1, 0, 0, 0, 0);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, cyL, 0, 2f, 0.6f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, cyL, 0, 1.5f, 0.6f);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, cyL, 0, 2f, 0.5f);
    }
}
