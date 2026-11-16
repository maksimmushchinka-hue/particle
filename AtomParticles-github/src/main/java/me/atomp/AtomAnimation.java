package me.atomp;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Детализированный атом: светящееся пульсирующее ядро с гало, пять электронов на наклонных
 * орбитах со шлейфами, контуры орбит, звёздная пыль, ударные волны и искры.
 * Вся система медленно вращается и покачивается. Если время «бесконечное» (/atomp loop),
 * анимация идёт по кругу без финала, пока её не остановят.
 */
public final class AtomAnimation extends BukkitRunnable {

    // ======================= НАСТРОЙКИ =======================
    private static final double BASE_UNIT = 3.0;
    private static final double SPIN_PER_TICK = 0.016;
    private static final double ROLL_AMPLITUDE = 0.18;
    private static final double ROLL_SPEED = 0.021;
    private static final int TRAIL_POINTS = 14;
    private static final double TRAIL_STEP = 0.12;
    private static final double NUCLEUS_RADIUS = 0.14;
    private static final int INTRO_TICKS = 30;
    private static final int OUTRO_TICKS = 24;
    private static final int FINALE_TICKS = 24;
    private static final int SHOCK_INTERVAL = 70;
    /** Максимум частиц за тик на одну анимацию. */
    private static final int BUDGET = 800;
    /** Если время не меньше этого значения — анимация считается бесконечной. */
    private static final double ENDLESS_SECONDS = 1.0e6;
    private static final boolean SOUNDS = true;
    // =========================================================

    private static final double[][] NUC_DIRS = FxEffect.sphere(40);
    private static final double[][] HEAD_DIRS = FxEffect.sphere(6);

    /** a, b, shift (доля a), rx, ry, rz, omega, phase, headRadius (доля радиуса ядра), headSize */
    private static final double[][] ORBITS = {
            {1.15, 0.36, -0.25, 0.5, 0.3, 1.45, 0.130, 0.0, 0.60, 1.0},
            {1.05, 0.40, 0.25, -0.7, 0.6, -0.3, 0.105, 2.1, 0.55, 0.9},
            {0.95, 0.34, 0.45, 0.9, -0.5, 0.75, 0.150, 4.0, 0.45, 0.8},
            {1.25, 0.45, 0.15, -1.1, -0.9, 0.2, 0.095, 1.2, 0.50, 0.9},
            {0.85, 0.30, 0.30, 1.3, 1.1, -1.2, 0.170, 3.0, 0.42, 0.8}
    };

    private static final class Electron {
        final double a, b, shift, omega, headRadius;
        final double ux, uy, uz, vx, vy, vz;
        final int color;
        double theta;
        Particle.DustOptions headOpt;
        Particle.DustOptions outlineOpt;
        Particle.DustOptions cloudOpt;
        final Particle.DustOptions[] trailOpts = new Particle.DustOptions[TRAIL_POINTS];

        Electron(double[] o, int color) {
            this.a = o[0];
            this.b = o[1];
            this.shift = o[2] * o[0];
            this.omega = o[6];
            this.theta = o[7];
            this.headRadius = o[8];
            this.color = color;
            Vector u = new Vector(1, 0, 0).rotateAroundZ(o[5]).rotateAroundX(o[3]).rotateAroundY(o[4]);
            Vector v = new Vector(0, 1, 0).rotateAroundZ(o[5]).rotateAroundX(o[3]).rotateAroundY(o[4]);
            this.ux = u.getX();
            this.uy = u.getY();
            this.uz = u.getZ();
            this.vx = v.getX();
            this.vy = v.getY();
            this.vz = v.getZ();
        }
    }

    private static final class Shock {
        final long start;
        final int dur;
        final double maxR;

        Shock(long start, int dur, double maxR) {
            this.start = start;
            this.dur = dur;
            this.maxR = maxR;
        }
    }

    private final AtomPlugin plugin;
    private final UUID owner;
    private final World world;
    private final AtomType type;
    private final AtomType.Pal pal;
    private final Random rnd = new Random();

    private final long totalTicks;
    private final boolean endless;
    private final int intro;
    private final int outro;

    private final double unit;
    private final double scale;
    private final double sizeMul;
    private final double sparkSpeed;
    private final double volume;

    private final double cx, cy, cz;
    private final double rightX, rightZ, fwdX, fwdZ;

    private final Electron[] electrons = new Electron[ORBITS.length];
    private final Particle.DustOptions[] nucleusOpts = new Particle.DustOptions[NUC_DIRS.length];
    private final List<Shock> shocks = new ArrayList<>();

    private long tick = 0;
    private int used = 0;
    private double cosS = 1, sinS = 0, cosR = 1, sinR = 0;
    private boolean stopped = false;

    public AtomAnimation(AtomPlugin plugin, Player player, double seconds, double scale, AtomType type) {
        this.plugin = plugin;
        this.owner = player.getUniqueId();
        this.type = type;
        this.pal = type.pal();
        this.scale = scale;
        this.endless = seconds >= ENDLESS_SECONDS;
        this.totalTicks = Math.max(1L, Math.round(seconds * 20.0));
        this.intro = (int) Math.max(1L, Math.min((long) INTRO_TICKS, totalTicks / 3L));
        this.outro = (int) Math.max(1L, Math.min((long) OUTRO_TICKS, totalTicks / 3L));

        this.unit = BASE_UNIT * scale;
        this.sizeMul = Math.max(0.7, Math.min(2.5, Math.sqrt(scale)));
        this.sparkSpeed = Math.max(0.5, Math.sqrt(scale));
        this.volume = (float) Math.min(8.0, 2.0 + scale);

        // Атом появляется перед игроком на уровне глаз
        Location eye = player.getEyeLocation();
        this.world = eye.getWorld();
        double yaw = Math.toRadians(eye.getYaw());
        this.fwdX = -Math.sin(yaw);
        this.fwdZ = Math.cos(yaw);
        this.rightX = -Math.cos(yaw);
        this.rightZ = -Math.sin(yaw);
        double dist = 1.5 + 1.7 * unit;
        this.cx = eye.getX() + fwdX * dist;
        this.cy = eye.getY();
        this.cz = eye.getZ() + fwdZ * dist;

        // Ядро: блик сверху-слева, к зрителю
        double[] light = normalize(-0.45, 0.6, -0.65);
        for (int i = 0; i < NUC_DIRS.length; i++) {
            double[] n = NUC_DIRS[i];
            double b = Math.max(0.0, n[0] * light[0] + n[1] * light[1] + n[2] * light[2]);
            b = Math.pow(b, 0.8);
            nucleusOpts[i] = FxEffect.dustOpt(FxEffect.mix(pal.nucDark, pal.nucLight, b), 1.5 * sizeMul);
        }

        // Электроны
        for (int i = 0; i < electrons.length; i++) {
            double[] o = ORBITS[i];
            int col = pal.elec[i % pal.elec.length];
            Electron e = new Electron(o, col);
            e.headOpt = FxEffect.dustOpt(col, o[9] * sizeMul);
            e.outlineOpt = FxEffect.dustOpt(FxEffect.mix(col, pal.trail1, 0.5), 0.45 * sizeMul);
            e.cloudOpt = FxEffect.dustOpt(col, 0.6 * sizeMul);
            int start = FxEffect.mix(pal.trail0, col, 0.35);
            int end = FxEffect.mix(pal.trail1, col, 0.2);
            for (int j = 0; j < TRAIL_POINTS; j++) {
                double f = j / (double) (TRAIL_POINTS - 1);
                double size = (0.85 + (0.30 - 0.85) * f) * sizeMul;
                e.trailOpts[j] = FxEffect.dustOpt(FxEffect.mix(start, end, f), size);
            }
            electrons[i] = e;
        }
    }

    public UUID getOwner() {
        return owner;
    }

    public void start() {
        runTaskTimer(plugin, 0L, 1L);
    }

    public void stop() {
        if (stopped) return;
        stopped = true;
        try {
            cancel();
        } catch (IllegalStateException ignored) {
            // задача ещё не была запланирована
        }
        plugin.unregister(this);
    }

    @Override
    public void run() {
        if (stopped) return;
        try {
            frame();
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Анимация атома остановлена из-за ошибки: " + ex);
            stop();
        }
    }

    private boolean every(int n) {
        return tick % n == 0;
    }

    private void frame() {
        if (!endless && tick >= totalTicks + FINALE_TICKS) {
            stop();
            return;
        }
        used = 0;

        double spin = tick * SPIN_PER_TICK;
        cosS = Math.cos(spin);
        sinS = Math.sin(spin);
        double roll = ROLL_AMPLITUDE * Math.sin(tick * ROLL_SPEED);
        cosR = Math.cos(roll);
        sinR = Math.sin(roll);

        if (tick == 0) {
            play(Sound.BLOCK_BEACON_ACTIVATE, 1.3f);
            shocks.add(new Shock(0, 18, 2.3));
        }

        boolean main = endless || tick < totalTicks;
        if (main) {
            double introE = tick < intro ? easeOut((tick + 1.0) / intro) : 1.0;
            double outroP = 0.0;
            if (!endless && tick >= totalTicks - outro) {
                outroP = (tick - (totalTicks - outro) + 1.0) / outro;
                if (tick == totalTicks - outro) play(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f);
            }
            double grow = Math.min(introE, 1.0 - easeIn(outroP));
            double speedMul = 1.0 + 1.8 * (1.0 - grow);

            // Периодическая ударная волна
            if (tick > 0 && every(SHOCK_INTERVAL) && (endless || tick < totalTicks - outro - 10)) {
                shocks.add(new Shock(tick, 16, 1.7));
                play(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f);
            }

            double pulse = 1.0 + 0.10 * Math.sin(tick * 0.18) + 0.55 * outroP;
            double R = NUCLEUS_RADIUS * unit * pulse * Math.max(0.08, introE);

            // ---- ядро
            if (every(3)) drawSurface(R);
            if (every(2)) drawHalo(R);
            drawCore(R);
            if (every(3)) {
                double[] d = randomDir();
                spark(pal.spark, d[0] * R, d[1] * R, d[2] * R, d[0], d[1], d[2], 0.12 * sparkSpeed);
            }
            if (every(9)) {
                for (int k = 0; k < 2; k++) {
                    double[] d = randomDir();
                    spark(pal.spark2, d[0] * R, d[1] * R, d[2] * R, d[0], d[1], d[2], 0.30 * sparkSpeed);
                }
            }

            // ---- гироскоп
            if (pal.gyro && every(3)) drawGyro(introE * (1.0 - outroP));

            // ---- электроны
            boolean trailTick = every(2);
            for (int i = 0; i < electrons.length; i++) {
                Electron e = electrons[i];
                e.theta += e.omega * speedMul;
                if (trailTick) {
                    drawTrail(e, grow);
                } else {
                    drawHead(e, grow);
                }
                if (pal.cloud) drawCloud(e, grow);
                if ((tick + i) % 3 == 0) drawOutline(e, grow);
            }
        }

        if (!endless && tick == totalTicks) finale();

        drawShocks();
        tick++;
    }

    // ----------------------------------------------------------------- ядро

    private void drawSurface(double R) {
        for (int i = 0; i < NUC_DIRS.length; i++) {
            double[] p = NUC_DIRS[i];
            emit(Particle.DUST, nucleusOpts[i], p[0] * R, p[1] * R, p[2] * R, false);
        }
        Particle.DustOptions inner = FxEffect.dustOpt(FxEffect.mix(pal.nucDark, pal.nucLight, 0.55), 1.5 * sizeMul);
        for (int k = 0; k < 6; k++) {
            double[] d = ball(0.7);
            emit(Particle.DUST, inner, d[0] * R, d[1] * R, d[2] * R, false);
        }
    }

    private void drawHalo(double R) {
        for (int i = 0; i < 10; i++) {
            double[] d = randomDir();
            double rr = R * (1.35 + rnd.nextDouble() * 0.8);
            int c = (i % 2 == 0)
                    ? FxEffect.mix(pal.haloA0, pal.haloA1, rnd.nextDouble())
                    : FxEffect.mix(pal.haloB0, pal.haloB1, rnd.nextDouble());
            emit(Particle.DUST, FxEffect.dustOpt(c, 1.1 * sizeMul), d[0] * rr, d[1] * rr, d[2] * rr, false);
        }
    }

    private void drawCore(double R) {
        Particle.DustOptions core = FxEffect.dustOpt(pal.core, 1.1 * sizeMul);
        for (int k = 0; k < 5; k++) {
            double[] d = ball(0.45);
            emit(Particle.DUST, core, d[0] * R, d[1] * R, d[2] * R, false);
        }
        if (every(3)) put("END_ROD", 0, 0, 0, 1, 0.03 * unit, 0.03 * unit, 0.03 * unit, 0.002);
    }

    // ----------------------------------------------------------------- электроны

    /** Точка на орбите в локальных координатах (до вращения системы). */
    private double[] orbitPoint(Electron e, double ang, double grow) {
        double ex = (e.a * Math.cos(ang) - e.shift) * unit * grow;
        double ey = e.b * Math.sin(ang) * unit * grow;
        return new double[]{
                e.ux * ex + e.vx * ey,
                e.uy * ex + e.vy * ey,
                e.uz * ex + e.vz * ey
        };
    }

    private void drawTrail(Electron e, double grow) {
        for (int i = 1; i <= TRAIL_POINTS; i++) {
            double[] p = orbitPoint(e, e.theta - i * TRAIL_STEP, grow);
            emit(Particle.DUST, e.trailOpts[i - 1], p[0], p[1], p[2], true);
        }
        // звёздная пыль за электроном
        double[] s = orbitPoint(e, e.theta - 2 * TRAIL_STEP, grow);
        put("END_ROD", transformX(s), transformY(s), transformZ(s), 1,
                0.05 * sparkSpeed, 0.05 * sparkSpeed, 0.05 * sparkSpeed, 0.004);
    }

    private void drawHead(Electron e, double grow) {
        double[] p = orbitPoint(e, e.theta, grow);
        double hx = transformX(p), hy = transformY(p), hz = transformZ(p);
        double hr = e.headRadius * NUCLEUS_RADIUS * unit;
        spawnAt(Particle.DUST, e.headOpt, hx, hy, hz);
        for (int i = 0; i < HEAD_DIRS.length; i++) {
            double[] d = HEAD_DIRS[i];
            spawnAt(Particle.DUST, e.headOpt, hx + d[0] * hr, hy + d[1] * hr, hz + d[2] * hr);
        }
        put("END_ROD", hx, hy, hz, 1, 0.02, 0.02, 0.02, 0.0);
    }

    private void drawOutline(Electron e, double grow) {
        int n = 24;
        for (int i = 0; i < n; i++) {
            double ang = Math.PI * 2 * i / n;
            double[] p = orbitPoint(e, ang, grow);
            emit(Particle.DUST, e.outlineOpt, p[0], p[1], p[2], true);
        }
    }

    private void drawCloud(Electron e, double grow) {
        for (int k = 0; k < 4; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double[] p = orbitPoint(e, ang, grow);
            double j = 0.07 * unit;
            emit(Particle.DUST, e.cloudOpt,
                    p[0] + rnd.nextGaussian() * j, p[1] + rnd.nextGaussian() * j, p[2] + rnd.nextGaussian() * j, true);
        }
    }

    // ----------------------------------------------------------------- гироскоп

    private void drawGyro(double k) {
        if (k < 0.02) return;
        for (int r = 0; r < 3; r++) {
            double dir = (r % 2 == 0) ? 1.0 : -1.0;
            double rx = r * 1.05 + tick * 0.021 * dir;
            double rz = r * 0.7 + tick * 0.013 * (r + 1) * dir;
            Vector u = new Vector(1, 0, 0).rotateAroundX(rx).rotateAroundZ(rz);
            Vector v = new Vector(0, 0, 1).rotateAroundX(rx).rotateAroundZ(rz);
            double rad = unit * (1.45 + 0.12 * r) * k;
            int col = FxEffect.mix(pal.shock0, pal.shock1, r / 2.0);
            Particle.DustOptions opt = FxEffect.dustOpt(col, 0.7 * sizeMul);
            int n = 44;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                double c = Math.cos(a) * rad, s = Math.sin(a) * rad;
                emit(Particle.DUST, opt,
                        u.getX() * c + v.getX() * s,
                        u.getY() * c + v.getY() * s,
                        u.getZ() * c + v.getZ() * s, true);
            }
            // яркая бусина на кольце
            double a = tick * 0.07 * dir + r * 2.1;
            double c = Math.cos(a) * rad, s = Math.sin(a) * rad;
            emit(Particle.DUST, FxEffect.dustOpt(pal.core, 1.6 * sizeMul),
                    u.getX() * c + v.getX() * s,
                    u.getY() * c + v.getY() * s,
                    u.getZ() * c + v.getZ() * s, true);
        }
    }

    // ----------------------------------------------------------------- ударные волны и финал

    private void drawShocks() {
        for (Iterator<Shock> it = shocks.iterator(); it.hasNext(); ) {
            Shock s = it.next();
            long age = tick - s.start;
            if (age < 0) continue;
            if (age > s.dur) {
                it.remove();
                continue;
            }
            if (age % 2 != 0) continue;
            double f = age / (double) s.dur;
            double r = s.maxR * unit * (1.0 - Math.pow(1.0 - f, 3));
            int n = (int) Math.max(14, Math.min(44, Math.round(2 * Math.PI * r / 0.5)));
            Particle.DustOptions opt = FxEffect.dustOpt(FxEffect.mix(pal.shock0, pal.shock1, f), 1.1 * sizeMul);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                double c = Math.cos(a) * r, sn = Math.sin(a) * r;
                emit(Particle.DUST, opt, c, 0, sn, false);
                emit(Particle.DUST, opt, c, sn, 0, false);
                emit(Particle.DUST, opt, 0, c, sn, false);
            }
        }
    }

    private void finale() {
        used = 0;
        for (double[] d : FxEffect.sphere(70)) spark(pal.spark, 0, 0, 0, d[0], d[1], d[2], 0.32 * sparkSpeed);
        for (double[] d : FxEffect.sphere(26)) spark(pal.spark2, 0, 0, 0, d[0], d[1], d[2], 0.50 * sparkSpeed);
        for (double[] d : FxEffect.sphere(30)) spark("FIREWORK", 0, 0, 0, d[0], d[1], d[2], 0.18 * sparkSpeed);
        shocks.add(new Shock(tick, 20, 3.2));
        shocks.add(new Shock(tick + 4, 20, 2.2));
        play(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.2f);
        play(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f);
    }

    // ----------------------------------------------------------------- низкоуровневые утилиты

    private double tx, ty, tz;

    /** Вращение системы вокруг вертикали + лёгкий крен. Результат в tx/ty/tz. */
    private void transform(double lx, double ly, double lz) {
        double x = lx * cosS + lz * sinS;
        double z = -lx * sinS + lz * cosS;
        tx = x * cosR - ly * sinR;
        ty = x * sinR + ly * cosR;
        tz = z;
    }

    private double transformX(double[] p) {
        transform(p[0], p[1], p[2]);
        return tx;
    }

    private double transformY(double[] p) {
        transform(p[0], p[1], p[2]);
        return ty;
    }

    private double transformZ(double[] p) {
        transform(p[0], p[1], p[2]);
        return tz;
    }

    private void emit(Particle particle, Object data, double lx, double ly, double lz, boolean spin) {
        if (spin) {
            transform(lx, ly, lz);
            spawnAt(particle, data, tx, ty, tz);
        } else {
            spawnAt(particle, data, lx, ly, lz);
        }
    }

    private void spawnAt(Particle particle, Object data, double x, double y, double z) {
        if (used >= BUDGET) return;
        used++;
        world.spawnParticle(particle,
                cx + rightX * x + fwdX * z,
                cy + y,
                cz + rightZ * x + fwdZ * z,
                1, 0, 0, 0, 0, data, true);
    }

    private void put(String name, double lx, double ly, double lz, int count,
                     double dx, double dy, double dz, double speed) {
        if (used >= BUDGET) return;
        used += Math.max(1, count);
        world.spawnParticle(FxEffect.particle(name),
                cx + rightX * lx + fwdX * lz,
                cy + ly,
                cz + rightZ * lx + fwdZ * lz,
                count, dx, dy, dz, speed, null, true);
    }

    /** Частица, летящая в заданном направлении (локальные координаты, без вращения системы). */
    private void spark(String name, double lx, double ly, double lz,
                       double dx, double dy, double dz, double speed) {
        if (used >= BUDGET && tick != totalTicks) return;
        used++;
        world.spawnParticle(FxEffect.particle(name),
                cx + rightX * lx + fwdX * lz,
                cy + ly,
                cz + rightZ * lx + fwdZ * lz,
                0,
                rightX * dx + fwdX * dz, dy, rightZ * dx + fwdZ * dz,
                speed, null, true);
    }

    private void play(Sound sound, float pitch) {
        if (!SOUNDS) return;
        world.playSound(new Location(world, cx, cy, cz), sound, (float) volume, pitch);
    }

    private double[] randomDir() {
        double x, y, z, l;
        do {
            x = rnd.nextGaussian();
            y = rnd.nextGaussian();
            z = rnd.nextGaussian();
            l = Math.sqrt(x * x + y * y + z * z);
        } while (l < 1.0e-6);
        return new double[]{x / l, y / l, z / l};
    }

    /** Случайная точка внутри шара радиуса r. */
    private double[] ball(double r) {
        double[] d = randomDir();
        double k = Math.cbrt(rnd.nextDouble()) * r;
        return new double[]{d[0] * k, d[1] * k, d[2] * k};
    }

    private static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private static double easeOut(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return 1.0 - Math.pow(1.0 - c, 3);
    }

    private static double easeIn(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return c * c * c;
    }
}
