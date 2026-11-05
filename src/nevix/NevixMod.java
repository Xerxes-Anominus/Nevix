package nevix;

import arc.Core;
import arc.Events;
import arc.util.CommandHandler;
import mindustry.content.Items;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import nevix.turret.TestTurret;
import nevix.ui.DayNightPanel;
import nevix.ui.UpdateLog;
import nevix.world.DayNightCycle;

import static mindustry.Vars.ui;

public class NevixMod extends Mod {
    public NevixMod() {
        Events.on(ClientLoadEvent.class, e -> {
            addSettings();
            DayNightPanel.init();
            UpdateLog.init();
        });
    }

    @Override
    public void init() {
        DayNightCycle.init();
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        DayNightCycle.registerCommands(handler);
    }

    void addSettings() {
        ui.settings.addCategory(Core.bundle.get("nevix-settings.name"), Icon.settings, table -> {
            table.checkPref("map-darkening", true);
            table.sliderPref("cycle-time", 10, 5, 60, 1, value -> value + " min");
            table.checkPref("show-daynight-panel", false);
        });
    }
    
    public static Block test;
    
    @Override
    public void loadContent() {
        test = new TestTurret("test") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 50));
        }};
    }
}