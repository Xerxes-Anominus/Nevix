package nevix.ui;

import arc.Core;
import arc.Events;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.gen.Icon;
import mindustry.ui.dialogs.BaseDialog;

public final class UpdateLog {

    private static final String PREFIX = "update-log.";

    private UpdateLog() {}

    public static void init() {
        Vars.ui.menufrag.addButton(
                Core.bundle.get(PREFIX + ".name"),
                Icon.book,
                UpdateLog::show
        );
    }
    
    private static int versionCount() {
        int n = 0;
        while (Core.bundle.has(PREFIX + n + ".version")) n++;
        return n;
    }

    public static void show() {
        BaseDialog dialog = new BaseDialog(Core.bundle.get(PREFIX + ".name"));

        float width = Math.min(Core.graphics.getWidth() / Scl.scl(1.05f), 550f);
        float height = Math.min(Core.graphics.getHeight() / Scl.scl(1.6f), 400f);

        dialog.cont.pane(content -> {
            content.top();
            content.defaults().growX().padBottom(6f);
            
            for (int i = versionCount() - 1; i >= 0; i--) {
                addVersion(content, i);
                content.row();
            }
        }).width(width).height(height);

        dialog.addCloseButton();
        dialog.show();
    }

    private static void addVersion(Table parent, int index) {
        String key = PREFIX + index;

        String version = Core.bundle.get(key + ".version");
        String changes = Core.bundle.get(key + ".content");

        boolean[] expanded = {false};

        parent.table(section -> {
            section.defaults().growX();

            section.button(
                version,
                Icon.book,
                () -> expanded[0] = !expanded[0]
            ).height(48f);

            section.row();
            
            section.collapser(body -> {
                body.left();
                body.add(changes)
                    .wrap()
                    .growX()
                    .left()
                    .labelAlign(Align.left)
                    .pad(12f);
            }, () -> expanded[0]).growX();
        }).growX();
    }
}