package me.atomp;

import org.bukkit.Sound;

/** Огненный феникс: рождение из искры, взмахи крыльев, огненный хвост, пике, спираль в небо и взрыв. */
final class PhoenixFx extends FxEffect {

    private static final int HIST = 64;
    private static final int FEATHERS = 7;
    private static final double[] DIVES = {0.33, 0.58};

    private final double Rc;     // радиус орбиты
    private final double Lw;     // длина крыла

    private final double[][] hist = new double[HIST][3];
    private int histIdx;
    private double theta;
    private double[] prev = null;
    private double[] last = {0, 0, 0};
    private final boolean[] diveFired = new boolean[DIVES.length];

    PhoenixFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 4.6 * scale + 2.6 * scale, 0.4 * scale, false);
        this.Rc = 4.6 * scale;
        this.Lw = 2.5 * scale;
        this.theta = rnd.nextDouble() * Math.PI * 2;
    }

    @Override String title() { return "Феникс"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 1.6f);
    }

    private static double[] norm(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        if (l < 1e-9) return new double[]{1, 0, 0};
        return new double[]{x / l, y / l, z / l};
    }

    private static double bump(double x) { // 0..1..0 на отрезке [0,1]
        if (x <= 0 || x >= 1) return 0;
        double s = Math.sin(Math.PI * x);
        return s * s;
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double rise = smooth(p / 0.12);
        double wf = smooth(p / 0.10);

        // ---- траектория
        double d = 0;
        for (int i = 0; i < DIVES.length; i++) d = Math.max(d, bump((p - (DIVES[i] - 0.03)) / 0.06));
        double asc = smooth((p - 0.84) / 0.12);
        double rho = Rc * (0.15 + 0.85 * rise) * (1 + 0.10 * Math.sin(t * 0.5));
        double h = lerp(-1.6 * scale, 0, rise) + 0.8 * scale * Math.sin(t * 0.9);
        double omega = 0.052;
        rho *= (1 - 0.45 * d);
        h -= 2.4 * scale * d;
        omega *= (1 + 0.5 * d);
        rho *= (1 - 0.8 * asc);
        h += 4.5 * scale * asc;
        omega *= (1 + 0.8 * asc);
        theta += omega;
        double[] P = {rho * Math.cos(theta), h, rho * Math.sin(theta)};
        if (prev == null) {
            prev = new double[]{rho * Math.cos(theta - omega), h, rho * Math.sin(theta - omega)};
            for (double[] hh : hist) { hh[0] = prev[0]; hh[1] = prev[1]; hh[2] = prev[2]; }
        }
        double[] T = norm(P[0] - prev[0], P[1] - prev[1], P[2] - prev[2]);
        prev = P.clone();
        histIdx = (histIdx + 1) % HIST;
        hist[histIdx] = P.clone();
        last = P.clone();

        // ---- система координат птицы
        double[] S = norm(T[2], 0, -T[0]);
        double[] U = {T[1] * S[2] - T[2] * S[1], T[2] * S[0] - T[0] * S[2], T[0] * S[1] - T[1] * S[0]};
        double[] tc = norm(-P[0], 0, -P[2]);
        double bs = (S[0] * tc[0] + S[2] * tc[2]) >= 0 ? 1 : -1;
        double b = 0.30 * bs * (1 - 0.6 * d);
        double cb = Math.cos(b), sb = Math.sin(b);
        double[] S2 = {S[0] * cb - U[0] * sb, S[1] * cb - U[1] * sb, S[2] * cb - U[2] * sb};
        double[] U2 = {U[0] * cb + S[0] * sb, U[1] * cb + S[1] * sb, U[2] * cb + S[2] * sb};

        // ---- искра/яйцо при рождении
        if (p < 0.14) {
            double pulse = 1 + 0.3 * Math.sin(t * 9);
            for (int k = 0; k < 8; k++) {
                double[] u = unit();
                double rr = 0.35 * scale * pulse * Math.abs(gauss()) * (1 - rise * 0.6);
                dust(u[0] * rr, u[1] * rr + h * 0.0, u[2] * rr, rgb(255, 190 + rnd.nextInt(50), 60), 1.6 * dustK);
            }
            put("FLAME", 0, 0, 0, 2, 0.2 * scale, 0.2 * scale, 0.2 * scale, 0.02);
            for (int k = 0; k < 12; k++) {
                double s = rnd.nextDouble();
                double a = s * 9 + t * 8;
                double rr = 0.4 * scale * (1 - s);
                dust(lerp(0, P[0], s) + Math.cos(a) * rr, lerp(0, P[1], s) + Math.sin(a) * rr * 0.6,
                        lerp(0, P[2], s) + Math.sin(a) * rr, grad(s, rgb(255, 240, 160), rgb(255, 120, 30)), 1.2 * dustK);
            }
        }

        // ---- крылья
        double flap = (0.85 * Math.sin(Math.PI * 2 * 0.9 * t) + 0.1) * (1 - 0.65 * d) * (0.35 + 0.65 * wf);
        double sweepExtra = 0.55 * d;
        double cf = Math.cos(flap), sf = Math.sin(flap);
        for (int side = -1; side <= 1; side += 2) {
            double[] o = {side * S2[0] * cf + U2[0] * sf, side * S2[1] * cf + U2[1] * sf, side * S2[2] * cf + U2[2] * sf};
            for (int i = 0; i < FEATHERS; i++) {
                double a = 0.05 + i * 0.21 + sweepExtra;
                double len = Lw * wf * (0.55 + 0.45 * Math.sin(Math.PI * (i + 0.6) / 7.2));
                if (len < 0.2) continue;
                double ca = Math.cos(a), sa = Math.sin(a);
                double[] dir = {o[0] * ca - T[0] * sa, o[1] * ca - T[1] * sa, o[2] * ca - T[2] * sa};
                int n = Math.max(3, (int) (len / 0.34));
                for (int j = 1; j <= n; j++) {
                    double s = len * j / n;
                    double f = s / len;
                    double droop = -0.30 * scale * f * f;
                    double x = P[0] + side * S2[0] * 0.28 * scale + dir[0] * s;
                    double y = P[1] + side * S2[1] * 0.28 * scale + dir[1] * s + droop;
                    double z = P[2] + side * S2[2] * 0.28 * scale + dir[2] * s;
                    int c = grad(f * 0.85 + i * 0.03, rgb(255, 228, 95), rgb(255, 145, 30), rgb(222, 48, 20), rgb(120, 15, 10));
                    dust(x, y, z, c, (1.5 - 0.5 * f) * dustK);
                    if (j == n && (i % 2 == 0)) {
                        put("FLAME", x, y, z, 1, 0.04, 0.04, 0.04, 0.005);
                        if (tick % 2 == 0) put("SMALL_FLAME", x, y, z, 1, 0.08, 0.08, 0.08, 0.01);
                    }
                }
            }
        }

        // ---- тело, шея, голова
        for (int u = -3; u <= 3; u++) {
            double q = u * 0.2 * scale;
            dust(P[0] + T[0] * q, P[1] + T[1] * q, P[2] + T[2] * q, grad((u + 3) / 6.0, rgb(255, 150, 40), rgb(255, 215, 80)), 2.0 * dustK);
        }
        double[] neck = {P[0] + T[0] * 0.7 * scale + U2[0] * 0.15 * scale, P[1] + T[1] * 0.7 * scale + U2[1] * 0.15 * scale,
                P[2] + T[2] * 0.7 * scale + U2[2] * 0.15 * scale};
        double[] head = {P[0] + T[0] * 1.0 * scale + U2[0] * 0.32 * scale, P[1] + T[1] * 1.0 * scale + U2[1] * 0.32 * scale,
                P[2] + T[2] * 1.0 * scale + U2[2] * 0.32 * scale};
        dust(neck[0], neck[1], neck[2], rgb(255, 225, 120), 1.8 * dustK);
        dust(head[0], head[1], head[2], rgb(255, 248, 205), 2.2 * dustK);
        dust(head[0] + T[0] * 0.22 * scale, head[1] + T[1] * 0.22 * scale, head[2] + T[2] * 0.22 * scale, rgb(255, 165, 35), 1.2 * dustK);
        for (int k = 1; k <= 3; k++) {
            double q = 0.16 * k * scale;
            dust(head[0] - T[0] * q + U2[0] * q * 1.1 + S2[0] * (k - 2) * 0.05 * scale,
                    head[1] - T[1] * q + U2[1] * q * 1.1,
                    head[2] - T[2] * q + U2[2] * q * 1.1 + S2[2] * (k - 2) * 0.05 * scale,
                    rgb(255, 90, 40), 1.1 * dustK);
        }
        put("END_ROD", head[0], head[1], head[2], 1, 0.03, 0.03, 0.03, 0.002);

        // ---- хвост из огненных лент
        for (int j = -1; j <= 1; j++) {
            for (int k = 2; k <= 26; k += 2) {
                double[] hp = hist[((histIdx - k) % HIST + HIST) % HIST];
                double lat = j * 0.14 * scale * (1 + k * 0.05) + Math.sin(k * 0.45 + t * 3 + j) * 0.12 * scale;
                double x = hp[0] + S2[0] * lat - T[0] * 0.5 * scale;
                double y = hp[1] + S2[1] * lat - T[1] * 0.5 * scale - 0.015 * k * scale;
                double z = hp[2] + S2[2] * lat - T[2] * 0.5 * scale;
                dust(x, y, z, grad(k / 26.0, rgb(255, 215, 70), rgb(255, 120, 25), rgb(170, 30, 15), rgb(70, 10, 10)),
                        (1.5 - k / 26.0 * 0.8) * dustK);
            }
        }

        // ---- искры и угли
        put("FLAME", P[0] - T[0] * 0.5 * scale, P[1], P[2] - T[2] * 0.5 * scale, 2, 0.2 * scale, 0.12 * scale, 0.2 * scale, 0.01);
        if (tick % 3 == 0) put("LAVA", P[0], P[1] - 0.2 * scale, P[2], 1, 0.2, 0.1, 0.2, 0);
        if (asc > 0.1 || p < 0.14) {
            put("FLAME", P[0], P[1], P[2], 3, 0.5 * scale, 0.4 * scale, 0.5 * scale, 0.03);
        }

        // ---- вспышка на выходе из пике
        for (int i = 0; i < DIVES.length; i++) {
            if (!diveFired[i] && p > DIVES[i] + 0.0) {
                diveFired[i] = true;
                ringFlat(P[0], P[1], P[2], 1.7 * scale, 28, rgb(255, 160, 40), 1.6 * dustK);
                burst("FLAME", P[0], P[1], P[2], 36, 0.12 * Math.sqrt(scale));
                sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, P[0], P[1], P[2], 1.2f, 0.7f);
            }
        }
    }

    @Override
    void finale() {
        double x = last[0], y = last[1], z = last[2];
        double s = Math.sqrt(scale);
        burst("FLAME", x, y, z, 100, 0.40 * s);
        burst("FIREWORK", x, y, z, 60, 0.30 * s);
        burst("END_ROD", x, y, z, 80, 0.35 * s);
        burst("LAVA", x, y, z, 20, 0.15 * s);
        ringFlat(x, y, z, 2.0 * scale, 50, rgb(255, 150, 40), 1.8 * dustK);
        ringFlat(x, y, z, 3.6 * scale, 70, rgb(255, 220, 90), 1.6 * dustK);
        put("EXPLOSION_EMITTER", x, y, z, 1, 0, 0, 0, 0);
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, x, y, z, 2f, 0.6f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, x, y, z, 1.5f, 1.2f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, x, y, z, 2f, 1.4f);
    }
}
