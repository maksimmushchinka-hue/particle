package me.atomp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class AtomPlugin extends JavaPlugin implements TabExecutor {

    public static final double MAX_SECONDS = 600.0;
    public static final double MIN_SCALE = 0.1;
    public static final double MAX_SCALE = 10.0;
    public static final int MAX_PER_PLAYER = 3;

    /** Длительность новых эпичных анимаций по умолчанию — одна минута. */
    public static final double DEFAULT_SECONDS = 60.0;
    /** «Бесконечный» атом: ~3 года в тиках — на деле работает, пока не будет /atomp stop. */
    private static final double LOOP_SECONDS = 1.0e8;

    private static final String LOOP_KEY = "loop";
    private static final String[] LOOP_ALIASES = {"loop", "цикл", "бесконечный", "бесконечно", "infinite", "forever"};

    private final List<AtomAnimation> active = new ArrayList<>();
    private final List<FxEffect> activeFx = new ArrayList<>();

    @Override
    public void onEnable() {
        if (getCommand("atomp") == null) {
            getLogger().severe("Команда atomp не найдена в plugin.yml!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("atomp").setExecutor(this);

        // Если владелец вышел с сервера — останавливаем его анимации (важно для бесконечного атома).
        Runnable watchdog = this::stopOrphans;
        getServer().getScheduler().runTaskTimer(this, watchdog, 40L, 40L);
    }

    @Override
    public void onDisable() {
        for (AtomAnimation a : new ArrayList<>(active)) a.stop();
        active.clear();
        for (FxEffect e : new ArrayList<>(activeFx)) e.stop();
        activeFx.clear();
    }

    void unregister(AtomAnimation a) {
        active.remove(a);
    }

    void unregister(FxEffect e) {
        activeFx.remove(e);
    }

    private void stopOrphans() {
        for (AtomAnimation a : new ArrayList<>(active)) {
            Player pl = getServer().getPlayer(a.getOwner());
            if (pl == null || !pl.isOnline()) a.stop();
        }
        for (FxEffect e : new ArrayList<>(activeFx)) {
            Player pl = getServer().getPlayer(e.getOwner());
            if (pl == null || !pl.isOnline()) e.stop();
        }
    }

    private int countOf(UUID id) {
        int n = 0;
        for (AtomAnimation a : active) if (a.getOwner().equals(id)) n++;
        for (FxEffect e : activeFx) if (e.getOwner().equals(id)) n++;
        return n;
    }

    private static boolean isLoop(String s) {
        String v = s.toLowerCase(Locale.ROOT);
        for (String a : LOOP_ALIASES) if (a.equals(v)) return true;
        return false;
    }

    private static void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "Эту команду можно использовать только в игре.", NamedTextColor.RED);
            return true;
        }

        // /atomp stop
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            int n = 0;
            UUID id = player.getUniqueId();
            for (AtomAnimation a : new ArrayList<>(active)) {
                if (a.getOwner().equals(id)) { a.stop(); n++; }
            }
            for (FxEffect e : new ArrayList<>(activeFx)) {
                if (e.getOwner().equals(id)) { e.stop(); n++; }
            }
            msg(player, "Остановлено анимаций: " + n, NamedTextColor.YELLOW);
            return true;
        }

        if (args.length < 1 || args.length > 3) return usage(player, label);

        // Разбираем аргументы: числа (время, масштаб) и один вид.
        boolean random = false;
        boolean loop = false;
        AtomType atomType = null;
        FxType fxType = null;
        boolean typeSet = false;
        List<Double> numbers = new ArrayList<>();

        for (String a : args) {
            double num = parseNumber(a);
            if (!Double.isNaN(num)) {
                numbers.add(num);
                continue;
            }
            if (typeSet) return usage(player, label);
            typeSet = true;
            if (a.equalsIgnoreCase("random") || a.equalsIgnoreCase("случайный")) {
                random = true;
            } else if (isLoop(a)) {
                loop = true;
            } else if ((atomType = AtomType.parse(a)) != null) {
                // классический вид
            } else if ((fxType = FxType.parse(a)) != null) {
                // эпичный вид
            } else {
                msg(player, "Неизвестный вид атома: " + a, NamedTextColor.RED);
                return usage(player, label);
            }
        }
        if (numbers.size() > 2) return usage(player, label);

        double seconds = DEFAULT_SECONDS;
        double scale = 1.0;
        if (loop) {
            if (numbers.size() > 1) return usage(player, label);
            if (!numbers.isEmpty()) scale = numbers.get(0);
        } else {
            if (!numbers.isEmpty()) seconds = numbers.get(0);
            if (numbers.size() > 1) scale = numbers.get(1);
            if (!numbers.isEmpty() && seconds <= 0) {
                msg(player, "Время должно быть положительным числом (в секундах).", NamedTextColor.RED);
                return usage(player, label);
            }
            if (seconds > MAX_SECONDS) {
                msg(player, "Максимальное время — 600 сек.", NamedTextColor.RED);
                return true;
            }
        }
        if (scale < MIN_SCALE || scale > MAX_SCALE) {
            msg(player, "Масштаб должен быть числом от 0.1 до 10.0.", NamedTextColor.RED);
            return true;
        }

        if (random) {
            AtomType[] olds = AtomType.values();
            FxType[] news = FxType.values();
            int i = ThreadLocalRandom.current().nextInt(olds.length + news.length);
            if (i < olds.length) atomType = olds[i];
            else fxType = news[i - olds.length];
        }
        if (!loop && atomType == null && fxType == null) atomType = AtomType.CLASSIC;

        UUID id = player.getUniqueId();
        if (countOf(id) >= MAX_PER_PLAYER) {
            msg(player, "Одновременно можно держать не больше " + MAX_PER_PLAYER
                    + " анимаций. /" + label + " stop — остановить.", NamedTextColor.RED);
            return true;
        }

        if (loop) {
            // Классический атом без конца — останавливается только командой stop.
            AtomAnimation anim = new AtomAnimation(this, player, LOOP_SECONDS, scale, AtomType.CLASSIC);
            active.add(anim);
            anim.start();
            msg(player, String.format(Locale.ROOT,
                    "Бесконечный атом запущен, масштаб x%.2f. Остановить: /%s stop", scale, label),
                    NamedTextColor.AQUA);
            return true;
        }

        if (fxType != null) {
            FxEffect fx = fxType.create(this, player, seconds, scale);
            activeFx.add(fx);
            fx.start();
            msg(player, String.format(Locale.ROOT, "Запущено: %s — %.1f сек, масштаб x%.2f",
                    fxType.title(), seconds, scale), NamedTextColor.AQUA);
            return true;
        }

        AtomAnimation anim = new AtomAnimation(this, player, seconds, scale, atomType);
        active.add(anim);
        anim.start();
        msg(player, String.format(Locale.ROOT, "Атом запущен: %.1f сек, масштаб x%.2f, вид: %s",
                seconds, scale, atomType.title()), NamedTextColor.AQUA);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) return out;
        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);

        if (args.length == 1) {
            for (String s : new String[]{"5", "10", "30", "60", "stop", LOOP_KEY}) {
                if (s.startsWith(cur)) out.add(s);
            }
        }
        if (args.length >= 1 && args.length <= 3 && !args[0].equalsIgnoreCase("stop")) {
            if (args.length >= 2) {
                for (String s : new String[]{"0.5", "1", "2", "3", "5"}) {
                    if (s.startsWith(cur)) out.add(s);
                }
            }
            List<String> keys = new ArrayList<>(AtomType.keys());
            keys.addAll(FxType.keys());
            for (String k : keys) if (k.startsWith(cur)) out.add(k);
            if ("random".startsWith(cur)) out.add("random");
            if (args.length >= 2 && LOOP_KEY.startsWith(cur) && !out.contains(LOOP_KEY)) out.add(LOOP_KEY);
        }
        return out;
    }

    private boolean usage(Player p, String label) {
        msg(p, "Использование: /" + label + " <время в секундах> [масштаб] [вид]", NamedTextColor.GRAY);
        msg(p, "Классические: classic, gyro, cloud, fire, ice, void, random", NamedTextColor.GRAY);
        msg(p, "Эпичные (по умолчанию 60 с, время можно не указывать): galaxy, storm, phoenix, portal, tree, aurora, mandala, helix, fireworks, tesseract", NamedTextColor.GRAY);
        msg(p, "Бесконечный атом: /" + label + " loop [масштаб]", NamedTextColor.GRAY);
        msg(p, "Остановить: /" + label + " stop", NamedTextColor.GRAY);
        return true;
    }

    private static double parseNumber(String s) {
        try {
            double v = Double.parseDouble(s.replace(',', '.'));
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}
