package nevix.content;

import mindustry.content.Items;
import mindustry.entities.part.RegionPart;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.draw.DrawTurret;
import nevix.world.block.turret.SpinupTurret;

public final class Turrets {
    private Turrets() {}

    public static Block
    spinup;

    public static void load() {
        spinup = new SpinupTurret("spinup") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 60, Items.lead, 20));

            inaccuracy = 5f;

            drawer = new DrawTurret("reinforced-") {{
                parts.add(new RegionPart("-wing") {{
                    mirror = false;
                    under = false;
                    moveY = -2f;
                    progress = PartProgress.recoil;
                }});
            }};
        }};
    }
}
