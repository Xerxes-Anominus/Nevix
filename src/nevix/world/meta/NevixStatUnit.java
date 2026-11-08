package nevix.world.meta;

import arc.Core;
import mindustry.world.meta.StatUnit;

public class NevixStatUnit {
    public static final StatUnit
    tile = new StatUnit("tile"),
    maxHealth = new StatUnit("max-health");
    
    public static String get(StatUnit unit) {
        return Core.bundle.has("unit." + unit.name) ? Core.bundle.get("unit." + unit.name) : unit.name;
    }
}