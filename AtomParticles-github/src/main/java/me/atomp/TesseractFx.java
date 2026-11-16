package me.atomp;

import org.bukkit.Sound;

/**
 * Тессеракт: четырёхмерный гиперкуб вращается в пяти плоскостях и проецируется в 3D,
 * по рёбрам бегут световые импульсы, вокруг крутятся орбитальные кольца со спутниками,
 * время от времени проходит волна-вспышка. Финал — схлопывание и взрыв света.
 */
final class TesseractFx extends FxEffect {

    private static final int[][] EDGES = buildEdges();
    private static final int PACKETS = 8;
    private static final int EDGE_POINTS = 12;
    private static final int RING_POINTS = 70;

    private static int[][] buildEdges() {
        int[][] e = new int[32][2];
        int n = 0;
        for (int v = 0; v < 16; v++) {
            for (int b = 0; b < 4; b++) {
                if (((v >> b) & 1) == 0) {
                    e[n][0] = v;
                    e[n][1] = v | (1 << b);
                    n++;
                }
            }
        }
        return e;
    }

    private static final class Packet {
        int vtx, bit;
        double s;
    }

    private final double R;
    private final Packet[] packets = new Packet[PACKETS];
    private final double[] px = new double[16];
    private final double[] py = new double[16];
    private final double[] pz = new double[16];
    private final double[] pw = new double[16];
    private int nextPulse;
    private int pulseAge = -1;
    private double flash;

    TesseractFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 1.9 * 3.2 * scale, 1.6 * 3.2 * scale, false);
        this.R = 3.2 * scale;
        for (int i = 0; i < PACKETS; i++) {
            Packet pk = new Packet();
            pk.vtx = rnd.nextInt(16);
            pk.bit = rnd.nextInt(4);
            pk.s = rnd.nextDouble();
            packets[i] = pk;
        }
        nextPulse = introTicks + 40;
    }

    @Override String title() { return "Гиперкуб"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 0.8f);
    }

    private static void rot(double[] q, int i, int j, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        double qi = q[i] * c - q[j] * s;
        double qj = q[i] * s + q[j] * c;
        q[i] = qi;
        q[j] = qj;
    }

    /** Поворот 4D-вершин и перспективная проекция в 3D. */
    private void project(double t, double g) {
        double[] q = new double[4];
        for (int v = 0; v < 16; v++) {
            q[0] = ((v & 1) == 0) ? -1 : 1;
            q[1] = (((v >> 1) & 1) == 0) ? -1 : 1;
            q[2] = (((v >> 2) & 1) == 0) ? -1 : 1;
            q[3] = (((v >> 3) & 1) == 0) ? -1 : 1;
            rot(q, 0, 3, t * 0.35);
            rot(q, 2, 3, t * 0.29);
            rot(q, 1, 2, t * 0.23);
            rot(q, 0, 1, t * 0.17);
            rot(q, 1, 3, t * 0.11);
            double k = 4.2 / (4.2 - q[3]);
            double sc = 0.55 * R * g * k;
            px[v] = q[0] * sc;
            py[v] = q[1] * sc;
            pz[v] = q[2] * sc;
            pw[v] = q[3];
        }
    }

    private static int edgeColor(double w, double flash) {
        double tt = (w + 2.0) / 4.0;
        int c = grad(tt, rgb(90, 230, 255), rgb(150, 120, 255), rgb(255, 110, 220));
        return mix(c, rgb(255, 255, 255), flash);
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double g = Math.max(0.03, env);
        project(t, g);
        flash *= 0.93;

        // ---- рёбра
        for (int e = 0; e < EDGES.length; e++) {
            if (((e + tick) & 1) != 0) continue;
            int a = EDGES[e][0], b = EDGES[e][1];
            int c = edgeColor((pw[a] + pw[b]) / 2.0, flash);
            double tt = ((pw[a] + pw[b]) / 2.0 + 2.0) / 4.0;
            for (int k = 0; k < EDGE_POINTS; k++) {
                double f = (k + 0.5) / EDGE_POINTS;
                dust(lerp(px[a], px[b], f), lerp(py[a], py[b], f), lerp(pz[a], pz[b], f),
                        c, (0.9 + 0.3 * tt) * dustK);
            }
        }

        // ---- вершины
        for (int v = 0; v < 16; v++) {
            dust(px[v], py[v], pz[v], mix(edgeColor(pw[v], flash), rgb(255, 255, 255), 0.5), 2.0 * dustK);
            if ((tick + v) % 6 == 0) put("END_ROD", px[v], py[v], pz[v], 1, 0.03, 0.03, 0.03, 0.0);
        }

        // ---- энергетические импульсы по рёбрам
        for (int i = 0; i < PACKETS; i++) {
            Packet pk = packets[i];
            pk.s += 0.045;
            if (pk.s >= 1.0) {
                pk.vtx ^= (1 << pk.bit);
                int nb;
                do {
                    nb = rnd.nextInt(4);
                } while (nb == pk.bit);
                pk.bit = nb;
                pk.s -= 1.0;
            }
            int a = pk.vtx;
            int b = a ^ (1 << pk.bit);
            for (int k = 0; k <= 5; k++) {
                double sf = pk.s - k * 0.07;
                if (sf < 0) continue;
                double x = lerp(px[a], px[b], sf);
                double y = lerp(py[a], py[b], sf);
                double z = lerp(pz[a], pz[b], sf);
                int c = (k == 0) ? rgb(255, 255, 255) : mix(rgb(210, 245, 255), rgb(80, 150, 255), k / 5.0);
                dust(x, y, z, c, (1.9 - k * 0.2) * dustK);
                if (k == 0 && tick % 2 == 0) put("END_ROD", x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
            }
        }

        // ---- орбитальные кольца со спутниками
        double RR = 1.9 * R * g;
        int[] ringCols = {rgb(90, 230, 255), rgb(190, 120, 255), rgb(255, 215, 110)};
        for (int r = 0; r < 3; r++) {
            double aX, aY;
            if (r == 0) { aX = t * 0.31; aY = t * 0.17; }
            else if (r == 1) { aX = t * 0.23 + 2.1; aY = -t * 0.19 + 1.0; }
            else { aX = t * 0.37 + 4.2; aY = t * 0.13 + 2.0; }
            double cY = Math.cos(aY), sY = Math.sin(aY);
            double vy0 = -Math.sin(aX), vz0 = Math.cos(aX);
            // базис кольца: u = (cY, 0, -sY), v = (vz0*sY, vy0, vz0*cY)
            double ux = cY, uy = 0, uz = -sY;
            double vx = vz0 * sY, vy = vy0, vz = vz0 * cY;
            int half = tick & 1;
            for (int i = half; i < RING_POINTS; i += 2) {
                double a = Math.PI * 2 * i / RING_POINTS;
                double ca = Math.cos(a) * RR, sa = Math.sin(a) * RR;
                dust(ux * ca + vx * sa, uy * ca + vy * sa, uz * ca + vz * sa, ringCols[r], 0.8 * dustK);
            }
            double dir = (r == 1) ? -1 : 1;
            for (int sat = 0; sat < 2; sat++) {
                double a0 = t * 0.9 * dir + sat * Math.PI + r * 1.3;
                for (int k = 0; k <= 5; k++) {
                    double a = a0 - dir * k * 0.09;
                    double ca = Math.cos(a) * RR, sa = Math.sin(a) * RR;
                    double x = ux * ca + vx * sa, y = uy * ca + vy * sa, z = uz * ca + vz * sa;
                    int c = (k == 0) ? rgb(255, 255, 255) : mix(rgb(230, 250, 255), ringCols[r], k / 5.0);
                    dust(x, y, z, c, (1.8 - k * 0.2) * dustK);
                    if (k == 0 && tick % 2 == 0) put("END_ROD", x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
                }
            }
        }

        // ---- ядро
        for (int k = 0; k < 4; k++) {
            double[] u = unit();
            double q = 0.10 * R * g;
            dust(u[0] * q, u[1] * q, u[2] * q, rgb(235, 250, 255), 1.5 * dustK);
        }
        put("GLOW", 0, 0, 0, 1, 0.05 * R, 0.05 * R, 0.05 * R, 0.01);

        // ---- волна-вспышка
        if (tick >= nextPulse && p < 0.88) {
            nextPulse = tick + 90;
            pulseAge = 0;
            flash = 1.0;
            sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0, 0, 0, 1.5f, 0.7f);
        }
        if (pulseAge >= 0) {
            pulseAge++;
            double f = pulseAge / 26.0;
            if (pulseAge % 2 == 0) {
                double rr = 2.4 * R * (1 - Math.pow(1 - Math.min(1.0, f), 2));
                int c = mix(rgb(255, 255, 255), rgb(100, 210, 255), Math.min(1.0, f));
                for (double[] d : sphere(40)) {
                    dust(d[0] * rr, d[1] * rr, d[2] * rr, c, 1.2 * dustK);
                }
            }
            if (pulseAge >= 26) pulseAge = -1;
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        burst("END_ROD", 0, 0, 0, 170, 0.6 * s);
        burst("FIREWORK", 0, 0, 0, 80, 0.35 * s);
        burst("ELECTRIC_SPARK", 0, 0, 0, 70, 0.6 * s);
        for (int k = 0; k < 3; k++) {
            double rr = R * (0.8 + 0.6 * k);
            ringVert(0, 0, 0, rr, 80, rgb(255, 255, 255), 1.7 * dustK);
            ringFlat(0, 0, 0, rr, 80, rgb(150, 220, 255), 1.7 * dustK);
        }
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, 0, 0, 2f, 0.6f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, 0, 0, 1.3f, 0.7f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, 0, 0, 2f, 0.9f);
    }
}
