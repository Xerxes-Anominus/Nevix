package nevix;

import arc.Events;
import arc.math.Mathf;
import arc.util.CommandHandler;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.game.Teams;
import mindustry.game.EventType.BlockDestroyEvent;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import mindustry.world.Tile;
import mindustry.world.blocks.power.NuclearReactor;
import mindustry.world.blocks.power.NuclearReactor.NuclearReactorBuild;
import nevix.content.EffectBlocks;
import nevix.content.NevixUnits;
import nevix.content.Turrets;
import nevix.game.NevixDifficulty;
import nevix.ui.DayNightPanel;
import nevix.ui.DifficultyButtons;
import nevix.ui.NevixSettings;
import nevix.ui.UpdateLog;
import nevix.world.DayNightCycle;

public class NevixMod extends Mod {
    public NevixMod() {
        Events.on(ClientLoadEvent.class, event -> {
            NevixSettings.init();
            DayNightPanel.init();
            UpdateLog.init();
            DifficultyButtons.init();
        });
    }

    @Override
    public void init() {
        DayNightCycle.init();
        PromotionSys.init();
        NevixDifficulty.init();
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        DayNightCycle.registerCommands(handler);
    }

    @Override
    public void loadContent() {
        PromotionSys.load();
        Turrets.load();
        EffectBlocks.load();
        NevixUnits.load();
    }
    
    public void runEvent() {
        Events.on(BlockDestroyEvent.class, event -> {
            if(event.tile.build instanceof NuclearReactorBuild build) {
                if(event.tile.block() != Blocks.thoriumReactor) return;
                if(Mathf.chance(0.002f)) {
                    Team team = Team.get(Mathf.random(255));
                    NevixUnits.reactory.spawn(team, build.x, build.y);
                }
            }
        });
    }
}