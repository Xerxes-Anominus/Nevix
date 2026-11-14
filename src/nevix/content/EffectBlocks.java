package nevix.content;

import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import nevix.PromotionSys;
import nevix.world.block.effect.LuckyBox;
import nevix.world.block.storage.VaultBlock;

public final class EffectBlocks {
    private EffectBlocks() {}

    public static Block
    basicBox,
    safe;

    public static void load() {
        basicBox = new LuckyBox("basic-box") {{
            health = 20;
            variants = 3;
            
            rewardBlock(Blocks.air, 0, 0, 4000f, 1f);
            rewardBlock(Blocks.copperWall, 0, 3, 2000f, 0.4f);
            rewardBlock(Blocks.duo, 0, 1, 1500f, 0.4f);
            rewardBlock(NevixBlocks.promotionShard, 0, 1, 300f, 1f);
            rewardBlock(NevixBlocks.promotionBlock, 0, 1, 100f, 1f);
            
            rewardUnit(UnitTypes.dagger, 1, 5, 500f, 0.4f);
            rewardUnit(UnitTypes.flare, 1, 4, 500f, 0.4f);
            rewardUnit(UnitTypes.nova, 1, 2, 400f, 0.4f);
            rewardUnit(UnitTypes.crawler, 1, 5, 400f, 0.4f);
            rewardUnit(UnitTypes.mace, 1, 1, 300f, 0.4f);
            
            requirements(Category.effect, ItemStack.with(Items.copper, 20));
        }};
        
        safe = new VaultBlock("safe") {{
            health = 750;
            size = 3;
            
            itemCapacity = 500;
            
            requirements(Category.effect, ItemStack.with(Items.titanium, 500, Items.thorium, 200, PromotionSys.promotionStar, 5));
        }};
    }
}