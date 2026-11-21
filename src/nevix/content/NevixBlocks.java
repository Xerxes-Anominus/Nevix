package nevix.content;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import nevix.PromotionSys;
import nevix.world.block.storage.VaultBlock;
import nevix.world.block.unit.UnitWorkshop;

public class NevixBlocks {
    public static Block
    safe, unitWorkshop;
    
    public static void load() {
        safe = new VaultBlock("safe") {{
            health = 750;
            size = 3;
            
            requirements(Category.effect, ItemStack.with(Items.titanium, 500, Items.thorium, 200, PromotionSys.promotionStar, 5));
            
            itemCapacity = 500;
        }};
        
        unitWorkshop = new UnitWorkshop("unit-workshop");
    }
}