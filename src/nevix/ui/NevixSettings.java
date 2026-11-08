package nevix.ui;

import arc.Core;
import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;

public final class NevixSettings {
    private NevixSettings() {}

    public static void init() {
        Events.on(WorldLoadEvent.class, event -> {
            Vars.ui.settings.addCategory(Core.bundle.get("nevix-settings.name"), Icon.settings, table -> {
                table.checkPref("map-darkening", true);
                table.sliderPref("cycle-time", 10, 5, 60, 1, value -> value + " " + Core.bundle.get("unit.minutes"));
                table.checkPref("show-daynight-panel", false);
            });
        });
    }
}
