package nevix.ui;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.util.Tmp;
import mindustry.ui.Styles;
import nevix.world.DayNightCycle;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public final class DayNightPanel {
    private DayNightPanel() {}

    public static void init() {
        Table panel = new Table(Styles.black6);
        panel.touchable = Touchable.enabled;
        panel.label(() -> Core.bundle.get(DayNightCycle.isDay() ? "daynight.day" : "daynight.night")).pad(8f);
        panel.visible(() -> state.isGame() && Core.settings.getBool("show-daynight-panel", false));
        panel.pack();
        
        panel.addListener(new InputListener() {
            float lastX, lastY;

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                Vec2 v = panel.localToStageCoordinates(Tmp.v1.set(x, y));
                lastX = v.x;
                lastY = v.y;
                return true;
            }

            @Override
            public void touchDragged(InputEvent event, float x, float y, int pointer) {
                Vec2 v = panel.localToStageCoordinates(Tmp.v1.set(x, y));
                panel.moveBy(v.x - lastX, v.y - lastY);
                lastX = v.x;
                lastY = v.y;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                Core.settings.put("daynight-panel-x", panel.x);
                Core.settings.put("daynight-panel-y", panel.y);
            }
        });

        panel.setPosition(
            Core.settings.getFloat("daynight-panel-x", 20f),
            Core.settings.getFloat("daynight-panel-y", Core.graphics.getHeight() / 2f)
        );
        
        panel.update(() -> panel.setPosition(
            Mathf.clamp(panel.x, 0f, Core.graphics.getWidth() - panel.getWidth()),
            Mathf.clamp(panel.y, 0f, Core.graphics.getHeight() - panel.getHeight())
        ));

        ui.hudGroup.addChild(panel);
    }
}