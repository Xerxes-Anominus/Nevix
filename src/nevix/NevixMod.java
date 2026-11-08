package nevix;

import arc.util.CommandHandler;
import mindustry.mod.Mod;
import nevix.content.Turrets;
import nevix.ui.DayNightPanel;
import nevix.ui.NevixSettings;
import nevix.ui.UpdateLog;
import nevix.world.DayNightCycle;

public class NevixMod extends Mod {
    public NevixMod() {
        NevixSettings.init();
        DayNightPanel.init();
        UpdateLog.init();
    }

    @Override
    public void init() {
        DayNightCycle.init();
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        DayNightCycle.registerCommands(handler);
    }

    @Override
    public void loadContent() {
        Turrets.load();
    }
}
