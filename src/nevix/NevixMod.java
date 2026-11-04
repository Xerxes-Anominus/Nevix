package nevix;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.util.Tmp;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.Styles;

import static mindustry.Vars.state;
import static mindustry.Vars.ui;

public class NevixMod extends Mod {
    public NevixMod() {
        Events.on(ClientLoadEvent.class, e -> {
            addSettings();
            DayNightCycle.init();
            DayNightPanel.init();
        });
    }

    void addSettings() {
        ui.settings.addCategory("nevix-settings", Icon.settings, table -> {
            table.checkPref("map-darkening", true);
            table.sliderPref("cycle-time", 10, 5, 60, 1, value -> value + "min");
            table.checkPref("show-daynight-panel", false);
        });
    }

    public static class DayNightCycle {
        static final float maxDark = 1f;

        static long offset;
        static double cycle;
        static boolean applied, origLighting;
        static Color origAmbient = new Color();

        static double cycleMs() {
            return Core.settings.getInt("cycle-time", 10) * 60000.0;
        }

        public static void init() {
            cycle = cycleMs();
            if (!Core.settings.has("daynight-offset")) {
                Core.settings.put("daynight-offset", (long) (Mathf.random() * cycle));
            }
            offset = Core.settings.getLong("daynight-offset", 0L);
            
            Events.on(WorldLoadEvent.class, e -> {
                origLighting = state.rules.lighting;
                origAmbient.set(state.rules.ambientLight);
                applied = false;
            });

            Events.run(Trigger.update, () -> {
                if (!state.isGame()) return;

                if (Core.settings.getBool("map-darkening", true)) {
                    float dark = (1f - daylight()) * maxDark;
                    state.rules.lighting = dark > 0.01f;
                    state.rules.ambientLight.set(0.02f, 0.03f, 0.10f, dark);
                    applied = true;
                } else if (applied) {
                    state.rules.lighting = origLighting;
                    state.rules.ambientLight.set(origAmbient);
                    applied = false;
                }
            });
        }
        
        public static float phase() {
            long now = System.currentTimeMillis();
            double newCycle = cycleMs();

            if (newCycle != cycle) {
                double p = ((now + offset) % cycle) / cycle;
                offset = Math.floorMod((long) (p * newCycle) - (long) (now % newCycle), (long) newCycle);
                cycle = newCycle;
                Core.settings.put("daynight-offset", offset);
            }
            return (float) (((now + offset) % cycle) / cycle);
        }
        
        public static float daylight() {
            return 0.5f + 0.5f * Mathf.cos(phase() * Mathf.PI2);
        }
        
        public static boolean isDay() {
            return daylight() >= 0.5f;
        }
    }

    public static class DayNightPanel {
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
}