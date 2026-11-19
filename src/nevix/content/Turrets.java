package nevix.content;

import mindustry.content.Items;
import mindustry.entities.part.RegionPart;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.draw.DrawTurret;
import nevix.world.block.turret.BerserkerTurret;
import nevix.world.block.turret.EmpericalTurret;
import nevix.world.block.turret.MarkerTurret;
import nevix.world.block.turret.SpinupTurret;
import nevix.world.block.turret.SwirdyTurret;
import nevix.world.block.turret.ZoenTurret;

public final class Turrets {
    private Turrets() {}

    public static Block
    // Beta 0.1s Bros:))
    spinup, berserker, marker, zoen,
    
    swirdy, emperical;

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
        }};
        
        swirdy = new SwirdyTurret("swirdy") {{
            requirements(Category.turret, ItemStack.with(Items.copper, 200, Items.lead, 180, Items.graphite, 80));
            
            shootWarmupSpeed = 0.03f;

            drawer = new DrawTurret("reinforced-") {{
                parts.addAll(
                new RegionPart("-wing-r") {{
                    mirror = false;
                    under = false;
                    moveRot = -15f;
                    progress = PartProgress.warmup;
                }},
                new RegionPart("-wing-l") {{
                    mirror = false;
                    under = false;
                    moveRot = 15f;
                    progress = PartProgress.warmup;
                }}
                );
            }};
        }};
        
        emperical = new EmpericalTurret("emperical") {{
            requirements(Category.turret, ItemStack.with(Items.titanium, 4000, Items.thorium, 2000, Items.silicon, 7000, Items.surgeAlloy, 500, Items.phaseFabric, 700, Items.plastanium, 1000));
            
            consumesPower = true;
            consumePower(203.7f);
            hasPower = true;
            
            drawer = new DrawTurret("reinforced-");
        }};
    }
}
