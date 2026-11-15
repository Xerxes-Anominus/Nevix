package nevix.content;

import mindustry.content.Items;
import mindustry.entities.part.RegionPart;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.draw.DrawTurret;
import nevix.world.block.turret.BerserkerTurret;
import nevix.world.block.turret.MarkerTurret;
import nevix.world.block.turret.SpinupTurret;
import nevix.world.block.turret.ZoenTurret;

public final class Turrets {
    private Turrets() {}

    public static Block
    // Beta 0.1s Bros:))
    spinup, berserker, marker, zoen;

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
        
        berserker = new BerserkerTurret("berserker") {{
            requirements(Category.turret, ItemStack.with(Items.titanium, 300, Items.thorium, 100, Items.graphite, 850, Items.metaglass, 500));
            
            drawer = new DrawTurret("reinforced-");
        }};
        
        marker = new MarkerTurret("marker") {{
            requirements(Category.turret, ItemStack.with(Items.titanium, 50, Items.copper, 330, Items.graphite, 190));
            
            drawer = new DrawTurret("reinforced-");
        }};
        
        zoen = new ZoenTurret("zoen") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 20));
            
            drawer = new DrawTurret("reinforced-");
        }};
    }
}
