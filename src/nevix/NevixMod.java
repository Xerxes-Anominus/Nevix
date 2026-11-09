package nevix;

import arc.Events;
import arc.util.CommandHandler;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import nevix.content.Turrets;
import nevix.ui.DayNightPanel;
import nevix.ui.NevixSettings;
import nevix.ui.UpdateLog;
import nevix.world.DayNightCycle;

public class NevixMod extends Mod {
    public NevixMod() {
        Events.on(ClientLoadEvent.class, event -> {
            NevixSettings.init();
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

    @Override
    public void loadContent() {
        Turrets.load();
    }
}
