package nevix.ui;

import arc.Core;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import mindustry.game.Difficulty;
import mindustry.type.Planet;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import nevix.game.NevixDifficulty;

import static mindustry.Vars.content;
import static mindustry.Vars.ui;

public final class DifficultyButtons {
    private static final String tableName = "nevix-difficulty";

    private DifficultyButtons() {}

    public static void init() {
        for (Planet planet : content.planets()) {
            planet.campaignRules.difficulty = Difficulty.normal;
        }

        hook(ui.campaignRules);
        hook(ui.planet);
    }

    static void hook(BaseDialog dialog) {
        dialog.update(() -> {
            if (Core.graphics.getFrameId() % 10 == 0) scan(dialog);
        });
    }

    static boolean scan(Element e) {
        if (e instanceof Table t) {
            if (tableName.equals(t.name)) return false;
            if (isVanillaGroup(t)) {
                build(t);
                return true;
            }
        }

        if (e instanceof ScrollPane pane && pane.getWidget() != null) {
            return scan(pane.getWidget());
        }

        if (e instanceof Group g) {
            for (Element child : g.getChildren()) {
                if (scan(child)) return true;
            }
        }
        return false;
    }

    static boolean isVanillaGroup(Table t) {
        int hits = 0;
        for (Element c : t.getChildren()) {
            if (c instanceof TextButton b) {
                String text = b.getText().toString();
                for (Difficulty d : Difficulty.values()) {
                    if (text.equals(Core.bundle.get("difficulty." + d.name()))) {
                        hits++;
                        break;
                    }
                }
            }
        }
        return hits == Difficulty.values().length;
    }

    static void build(Table t) {
        t.clearChildren();
        t.name = tableName;

        boolean wide = Core.graphics.getWidth() >= Scl.scl(NevixDifficulty.all.length * 150f + 120f);
        int perRow = wide ? NevixDifficulty.all.length : Core.graphics.isPortrait() ? 2 : 4;

        ButtonGroup<TextButton> group = new ButtonGroup<>();
        t.defaults().size(140f, 50f);

        for (NevixDifficulty d : NevixDifficulty.all) {
            t.button(d.localized(), Styles.flatTogglet, () -> NevixDifficulty.set(d))
            .group(group)
            .checked(b -> NevixDifficulty.current() == d)
            .tooltip(d.info());

            if ((d.ordinal() + 1) % perRow == 0 && d.ordinal() + 1 < NevixDifficulty.all.length) {
                t.row();
            }
        }

        t.invalidateHierarchy();
    }
}