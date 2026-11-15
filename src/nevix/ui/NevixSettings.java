package nevix.ui;

import arc.Core;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.SettingsMenuDialog.SettingsTable;

public final class NevixSettings {
    private NevixSettings() {}
    
    private static void section(SettingsTable table, String bundleKey) {
        table.add(Core.bundle.get(bundleKey)).color(Pal.accent).colspan(4).pad(10f).padBottom(4f).left().row();
        table.image().color(Pal.accent).fillX().height(3f).colspan(4).padTop(0f).padBottom(10f).row();
    }

    public static void init() {
        Vars.ui.settings.addCategory(Core.bundle.get("nevix-settings.name"), Icon.settings, table -> {
            section(table, "nevix-settings.category.daynight");
            table.checkPref("map-darkening", true);
            table.sliderPref("cycle-time", 10, 5, 60, 1, value -> value + " " + Core.bundle.get("unit.minutes"));
            table.checkPref("show-daynight-panel", false);
            
            section(table, "nevix-settings.category.rates");
            table.sliderPref("reactory-spawn-rate", 2, 1, 1000, 1, value -> (float)value / 10 + " %");
            table.sliderPref("star-drop-rate", 10, 1, 1000, 1, value -> (float)value / 10 + " %");
        });
    }
}
