package me.atomp;

import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Мандала света: огромный вертикальный узор из восьми лепестковых слоёв, вращающихся
 * навстречу друг другу, лучи-спицы, руническое кольцо, бегущие волны и сияющее солнце в центре.
 */
final class MandalaFx extends FxEffect {

    private static final int LAYERS = 8;
    private static final int SPOKES = 24;
    private static final int[] LAYER_COLORS = {
            rgb(255, 210, 90), rgb(255, 130, 110), rgb(255, 100, 190), rgb(170, 110, 255),
            rgb(90, 140, 255), rgb(90, 230, 255), rgb(230, 250, 255)
    };

    private final double R;
    private final List<int[]> pulses = new ArrayList<>(); // age
    private int nextPulse;
    private double cosY = 1, sinY = 0;

    MandalaFx(AtomPlugin plugin, org.bukkit.entity.Player p, double seconds, double scale) {
        super(plugin, p, seconds, scale, 5.5 * scale, 0.8 * 5.5 * scale, false);
        this.R = 5.5 * scale;
        this.nextPulse = introTicks + 30;
    }

    @Override String title() { return "Мандала света"; }

    @Override
    void begin() {
        sound(Sound.BLOCK_BEACON_ACTIVATE, 0, 0, 0, 2f, 0.9f);
    }

    /** Точка плоскости мандалы с лёгким покачиванием вокруг вертикальной оси. */
    private void pt(double x, double y, double z, int color, double size) {
        dust(x * cosY + z * sinY, y, -x * sinY + z * cosY, color, size);
    }

    @Override
    void draw(double env) {
        double t = sec();
        double p = progress();
        double yaw = 0.35 * Math.sin(t * 0.17);
        cosY = Math.cos(yaw);
        sinY = Math.sin(yaw);
        int half = tick & 1;

        // ---- лепестковые слои
        for (int l = 0; l < LAYERS; l++) {
            double bloom = clamp01(env * (LAYERS + 2) - l);
            if (bloom < 0.02) continue;
            double rBase = R * (0.16 + 0.105 * l) * smooth(bloom) * (1 + 0.03 * Math.sin(t * 1.5 + l));
            double amp = 0.22 * rBase;
            int petals = 4 + 2 * l;
            double rot = ((l % 2 == 0) ? 1 : -1) * t * (0.35 + 0.05 * l) + l * 0.4;
            double z = (l - 3.5) * 0.025 * R;
            int n = 36 + 12 * l;
            int base = grad(l / (LAYERS - 1.0), LAYER_COLORS);
            double size = (1.0 + 0.3 * (l % 2)) * dustK;
            for (int i = half; i < n; i += 2) {
                double th = Math.PI * 2 * i / n;
                double lobe = Math.cos(petals * th + rot * petals * 0.35);
                double rr = rBase + amp * lobe;
                int c = mix(base, rgb(255, 255, 255), 0.5 * Math.max(0, lobe));
                pt(Math.cos(th + rot * 0.2) * rr, Math.sin(th + rot * 0.2) * rr, z, c, size);
            }
        }

        // ---- спицы с бегущей волной яркости
        double spokeIn = smooth(env * 1.5);
        if (spokeIn > 0.05) {
            for (int s = half; s < SPOKES; s += 2) {
                double a = Math.PI * 2 * s / SPOKES + t * 0.12;
                for (int j = 0; j < 7; j++) {
                    double f = j / 6.0;
                    double rr = R * (0.12 + 0.83 * f) * spokeIn;
                    double wave = Math.max(0, Math.cos((f - (t * 0.35 % 1.0)) * Math.PI * 2));
                    int c = mix(grad(f, rgb(255, 215, 110), rgb(150, 120, 255), rgb(100, 230, 255)),
                            rgb(255, 255, 255), wave * 0.7);
                    pt(Math.cos(a) * rr, Math.sin(a) * rr, 0, c, (0.8 + 0.5 * wave) * dustK);
                }
            }
        }

        // ---- внешние кольца: сплошное и рунические «зубцы»
        double ringIn = smooth((env - 0.3) * 2);
        if (ringIn > 0.05) {
            int n = 120;
            for (int i = half; i < n; i += 2) {
                double a = Math.PI * 2 * i / n;
                pt(Math.cos(a) * R * 1.0 * ringIn, Math.sin(a) * R * 1.0 * ringIn, 0,
                        rgb(255, 225, 140), 1.0 * dustK);
            }
            int dashes = 36;
            for (int d = 0; d < dashes; d++) {
                double a = -t * 0.25 + Math.PI * 2 * d / dashes;
                for (int k = 0; k < 3; k++) {
                    if (((tick + d + k) & 1) != 0) continue;
                    double aa = a + k * 0.03;
                    pt(Math.cos(aa) * R * 1.09 * ringIn, Math.sin(aa) * R * 1.09 * ringIn, 0,
                            rgb(150, 220, 255), 0.9 * dustK);
                }
                if ((tick + d * 5) % 40 == 0) {
                    put("ENCHANT", Math.cos(a) * R * 1.09, Math.sin(a) * R * 1.09, 0, 2, 0.15, 0.15, 0.05, 0.3);
                }
            }
        }

        // ---- сияющее солнце в центре
        double sun = 0.07 * R * (1 + 0.2 * Math.sin(t * 3)) * smooth(env * 2);
        for (int k = 0; k < 6; k++) {
            double[] u = unit();
            dust(u[0] * sun, u[1] * sun, u[2] * sun, rgb(255, 245, 200), 1.6 * dustK);
        }
        put("END_ROD", 0, 0, 0, 1, 0.05 * R, 0.05 * R, 0.05 * R, 0.01);
        if (tick % 4 == 0) {
            for (int k = 0; k < 4; k++) {
                double a = Math.PI * 2 * k / 4 + t * 0.4;
                putDir("END_ROD", 0, 0, 0, Math.cos(a), Math.sin(a), 0, 0.22);
            }
        }

        // ---- искры на кончиках лепестков
        if (tick % 2 == 0) {
            int l = 3 + rnd.nextInt(5);
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = R * (0.16 + 0.105 * l);
            put("END_ROD", Math.cos(a) * rr, Math.sin(a) * rr, 0, 1, 0.05, 0.05, 0.05, 0.005);
        }

        // ---- бегущие кольца-импульсы от центра
        if (tick >= nextPulse && p < 0.9) {
            nextPulse = tick + 70;
            pulses.add(new int[]{0});
            sound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0, 0, 0, 1.5f, 0.8f);
        }
        for (Iterator<int[]> it = pulses.iterator(); it.hasNext(); ) {
            int[] a = it.next();
            a[0]++;
            double f = a[0] / 30.0;
            double rr = R * 1.25 * (1 - Math.pow(1 - f, 2));
            int c = mix(rgb(255, 255, 255), rgb(100, 220, 255), f);
            ringVert(0, 0, 0, rr, 60, c, 1.3 * dustK);
            if (a[0] >= 30) it.remove();
        }
    }

    @Override
    void finale() {
        double s = Math.sqrt(scale);
        burst("END_ROD", 0, 0, 0, 200, 0.6 * s);
        burst("FIREWORK", 0, 0, 0, 90, 0.35 * s);
        for (int k = 0; k < 6; k++) {
            ringVert(0, 0, 0, R * (0.25 + 0.17 * k), 80, grad(k / 5.0, LAYER_COLORS), 1.7 * dustK);
        }
        sound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0, 0, 0, 2f, 0.6f);
        sound(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0, 0, 0, 1.2f, 0.8f);
        sound(Sound.BLOCK_BEACON_DEACTIVATE, 0, 0, 0, 2f, 0.9f);
    }
}
