package me.atomp;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Базовый класс эпичных анимаций.
 * Локальные координаты: x — вправо от игрока, y — вверх, z — вперёд (по взгляду).
 */
abstract class FxEffect extends BukkitRunnable {

    /** Максимум частиц за тик на одну анимацию (защита сервера). */
    static final int BUDGET = 900;

    private static final Map<String, Particle> PARTICLES = new HashMap<>();
    private static final Map<Long, Particle.DustOptions> DUSTS = new HashMap<>();

    final AtomPlugin plugin;
    final UUID owner;
    final World world;
    final Random rnd = new Random();

    final int total;
    final int introTicks;
    final int outroTicks;
    final double scale;
    /** Множитель размера пылинок. */
    final double dustK;

    final double cx, cy, cz;
    final double fwdX, fwdZ, rightX, rightZ;

    int tick;
    boolean stopped;
    private int used;
    private boolean failed;

    /**
     * @param radius   горизонтальный радиус эффекта (в блоках, уже с учётом масштаба)
     * @param yOffset  смещение центра по Y относительно глаз (или земли, если ground = true)
     * @param ground   true — центр привязан к уровню ног игрока
     */
    FxEffect(AtomPlugin plugin, Player player, double seconds, double scale,
             double radius, double yOffset, boolean ground) {
        this.plugin = plugin;
        this.owner = player.getUniqueId();
        this.scale = scale;
        this.dustK = Math.max(0.7, Math.min(2.5, Math.sqrt(scale)));
        this.total = Math.max(40, (int) Math.round(seconds * 20.0));
        this.introTicks = Math.max(10, Math.min(120, total / 6));
        this.outroTicks = Math.max(10, Math.min(100, total / 6));

        Location eye = player.getEyeLocation();
        this.world = eye.getWorld();
        double yaw = Math.toRadians(eye.getYaw());
        this.fwdX = -Math.sin(yaw);
        this.fwdZ = Math.cos(yaw);
        this.rightX = -Math.cos(yaw);
        this.rightZ = -Math.sin(yaw);

        double dist = 2.0 + radius * 1.05;
        this.cx = eye.getX() + fwdX * dist;
        this.cz = eye.getZ() + fwdZ * dist;
        double baseY = ground ? player.getLocation().getY() : eye.getY();
        this.cy = baseY + yOffset;
    }

    UUID getOwner() { return owner; }

    abstract String title();

    /** Один кадр. env — огибающая появления/исчезновения 0..1. */
    abstract void draw(double env);

    /** Финальный взрыв при естественном завершении. */
    void finale() { }

    /** Звук/эффекты в самом начале. */
    void begin() { }

    public void start() {
        begin();
        runTaskTimer(plugin, 0L, 1L);
    }

    public void stop() {
        if (stopped) return;
        stopped = true;
        try { cancel(); } catch (IllegalStateException ignored) { }
        plugin.unregister(this);
    }

    @Override
    public void run() {
        if (stopped) return;
        if (tick >= total) {
            used = -4000; // финальный залп не ограничиваем бюджетом кадра
            try { finale(); } catch (RuntimeException ex) { fail(ex); }
            stop();
            return;
        }
        try {
            step();
        } catch (RuntimeException ex) {
            fail(ex);
            stop();
        }
    }

    /** Один кадр без планировщика (используется и в тестах). */
    void step() {
        used = 0;
        double in = smooth((tick + 1.0) / introTicks);
        double out = smooth((total - tick) / (double) outroTicks);
        draw(Math.min(in, out));
        tick++;
    }

    void prepareFinaleForTest() { used = -4000; }

    private void fail(RuntimeException ex) {
        if (failed) return;
        failed = true;
        plugin.getLogger().warning("Анимация " + title() + " остановлена из-за ошибки: " + ex);
    }

    // ---------------------------------------------------------------- математика

    final double sec() { return tick / 20.0; }

    final double progress() { return tick / (double) total; }

    static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    static double smooth(double x) {
        x = clamp01(x);
        return x * x * (3 - 2 * x);
    }

    static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    static int rgb(int r, int g, int b) { return (r << 16) | (g << 8) | b; }

    static int mix(int a, int b, double t) {
        t = clamp01(t);
        int r = (int) Math.round(lerp((a >> 16) & 255, (b >> 16) & 255, t));
        int g = (int) Math.round(lerp((a >> 8) & 255, (b >> 8) & 255, t));
        int bl = (int) Math.round(lerp(a & 255, b & 255, t));
        return rgb(r, g, bl);
    }

    /** Градиент по нескольким цветам, t = 0..1. */
    static int grad(double t, int... stops) {
        if (stops.length == 1) return stops[0];
        t = clamp01(t) * (stops.length - 1);
        int i = Math.min((int) t, stops.length - 2);
        return mix(stops[i], stops[i + 1], t - i);
    }

    final double gauss() { return rnd.nextGaussian(); }

    final double[] unit() {
        double z = rnd.nextDouble() * 2 - 1;
        double a = rnd.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(1 - z * z);
        return new double[]{r * Math.cos(a), z, r * Math.sin(a)};
    }

    /** Равномерная сфера (спираль Фибоначчи). */
    static double[][] sphere(int n) {
        double[][] out = new double[n][];
        double ga = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - 2 * (i + 0.5) / n;
            double r = Math.sqrt(Math.max(0, 1 - y * y));
            out[i] = new double[]{r * Math.cos(ga * i), y, r * Math.sin(ga * i)};
        }
        return out;
    }

    // ---------------------------------------------------------------- частицы

    static Particle particle(String name) {
        Particle p = PARTICLES.get(name);
        if (p != null) return p;
        try {
            Object o = Particle.class.getField(name).get(null);
            p = (Particle) o;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            p = null;
        }
        if (p == null) {
            try {
                p = (Particle) Particle.class.getField("END_ROD").get(null);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                p = Particle.END_ROD;
            }
        }
        PARTICLES.put(name, p);
        return p;
    }

    static Particle.DustOptions dustOpt(int rgb, double size) {
        float s = (float) Math.max(0.3, Math.min(4.0, size));
        long key = ((long) rgb << 16) | Math.round(s * 10);
        Particle.DustOptions o = DUSTS.get(key);
        if (o == null) {
            o = new Particle.DustOptions(Color.fromRGB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255), s);
            if (DUSTS.size() > 20000) DUSTS.clear();
            DUSTS.put(key, o);
        }
        return o;
    }

    final double wx(double lx, double lz) { return cx + rightX * lx + fwdX * lz; }

    final double wz(double lx, double lz) { return cz + rightZ * lx + fwdZ * lz; }

    final double wy(double ly) { return cy + ly; }

    /** Цветная пылинка в локальных координатах. */
    final void dust(double lx, double ly, double lz, int rgb, double size) {
        if (used >= BUDGET) return;
        used++;
        world.spawnParticle(Particle.DUST, wx(lx, lz), wy(ly), wz(lx, lz),
                1, 0, 0, 0, 0, dustOpt(rgb, size), true);
    }

    /** Обычная частица (count > 0 — облако с разбросом). */
    final void put(String name, double lx, double ly, double lz, int count,
                   double dx, double dy, double dz, double speed) {
        if (used >= BUDGET) return;
        used += Math.max(1, count);
        world.spawnParticle(particle(name), wx(lx, lz), wy(ly), wz(lx, lz),
                count, dx, dy, dz, speed, null, true);
    }

    /** Частица, летящая в заданном направлении (локальные координаты). */
    final void putDir(String name, double lx, double ly, double lz,
                      double dx, double dy, double dz, double speed) {
        if (used >= BUDGET) return;
        used++;
        double ddx = rightX * dx + fwdX * dz;
        double ddz = rightZ * dx + fwdZ * dz;
        world.spawnParticle(particle(name), wx(lx, lz), wy(ly), wz(lx, lz),
                0, ddx, dy, ddz, speed, null, true);
    }

    /** Равномерный взрыв-сфера из точки. */
    final void burst(String name, double lx, double ly, double lz, int n, double speed) {
        for (double[] d : sphere(n)) putDir(name, lx, ly, lz, d[0], d[1], d[2], speed);
    }

    /** Кольцо пылинок в горизонтальной плоскости. */
    final void ringFlat(double lx, double ly, double lz, double r, int n, int rgb, double size) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dust(lx + Math.cos(a) * r, ly, lz + Math.sin(a) * r, rgb, size);
        }
    }

    /** Кольцо пылинок в вертикальной плоскости (лицом к игроку). */
    final void ringVert(double lx, double ly, double lz, double r, int n, int rgb, double size) {
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dust(lx + Math.cos(a) * r, ly + Math.sin(a) * r, lz, rgb, size);
        }
    }

    final void sound(Sound s, double lx, double ly, double lz, float vol, float pitch) {
        world.playSound(new Location(world, wx(lx, lz), wy(ly), wz(lx, lz)), s, vol, pitch);
    }
}
