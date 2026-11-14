package nevix.content;

import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.meta.BuildVisibility;
import nevix.PromotionSys;

public class NevixBlocks {
    public static Block
    promotionShard, promotionBlock, promotionCrystal;
    
    public static void load() {
        promotionShard = new Wall("promotion-shard") {{
            buildVisibility = BuildVisibility.editorOnly;
            
            requirements(Category.distribution, ItemStack.with(PromotionSys.promotionStar, 5));
        }};
        
        promotionBlock = new Wall("promotion-block") {{
            buildVisibility = BuildVisibility.editorOnly;
            
            requirements(Category.distribution, ItemStack.with(PromotionSys.promotionStar, 25));
        }};
        
        promotionCrystal = new Wall("promotion-crystal") {{
            buildVisibility = BuildVisibility.editorOnly;
            
            requirements(Category.distribution, ItemStack.with(PromotionSys.promotionStar, 50));
        }};
    }
}