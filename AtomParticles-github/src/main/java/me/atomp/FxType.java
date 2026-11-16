package me.atomp;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Эпичные анимации (по умолчанию длятся одну минуту). */
public enum FxType {
    GALAXY("galaxy", "Спиральная галактика", "галактика", "космос"),
    STORM("storm", "Грозовой смерч", "шторм", "смерч", "гроза", "торнадо"),
    PHOENIX("phoenix", "Огненный феникс", "феникс", "птица"),
    PORTAL("portal", "Древний портал", "портал", "врата"),
    TREE("tree", "Древо жизни", "древо", "дерево"),
    AURORA("aurora", "Северное сияние", "сияние", "аврора"),
    MANDALA("mandala", "Мандала света", "мандала"),
    HELIX("helix", "Двойная спираль", "спираль", "днк", "dna"),
    FIREWORKS("fireworks", "Звёздный салют", "салют", "фейерверк"),
    TESSERACT("tesseract", "Гиперкуб", "гиперкуб", "тессеракт");

    private final String key;
    private final String title;
    private final String[] aliases;

    FxType(String key, String title, String... aliases) {
        this.key = key;
        this.title = title;
        this.aliases = aliases;
    }

    public String key() { return key; }

    public String title() { return title; }

    public static FxType parse(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase(Locale.ROOT);
        for (FxType t : values()) {
            if (t.key.equals(v)) return t;
            for (String a : t.aliases) if (a.equals(v)) return t;
        }
        return null;
    }

    public static List<String> keys() {
        List<String> l = new ArrayList<>();
        for (FxType t : values()) l.add(t.key);
        return l;
    }

    FxEffect create(AtomPlugin plugin, Player p, double seconds, double scale) {
        switch (this) {
            case GALAXY:  return new GalaxyFx(plugin, p, seconds, scale);
            case STORM:   return new StormFx(plugin, p, seconds, scale);
            case PHOENIX: return new PhoenixFx(plugin, p, seconds, scale);
            case PORTAL:  return new PortalFx(plugin, p, seconds, scale);
            case AURORA:  return new AuroraFx(plugin, p, seconds, scale);
            case MANDALA: return new MandalaFx(plugin, p, seconds, scale);
            case HELIX:   return new HelixFx(plugin, p, seconds, scale);
            case FIREWORKS: return new FireworksFx(plugin, p, seconds, scale);
            case TESSERACT: return new TesseractFx(plugin, p, seconds, scale);
            default:      return new TreeFx(plugin, p, seconds, scale);
        }
    }
}
