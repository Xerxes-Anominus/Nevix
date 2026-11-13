package nevix.content;

import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import nevix.world.block.effect.LuckyBox;

public final class EffectBlocks {
    private EffectBlocks() {}

    public static Block luckyBox;

    public static void load() {
        luckyBox = new LuckyBox("lucky-box") {{
            requirements(Category.effect, ItemStack.with(Items.copper, 100, Items.lead, 50));

            size = 1;
            health = 120;
            variants = 3;
            
            rewardBlock(Blocks.duo,            1, 2, 3f, 0.5f);
            rewardBlock(Blocks.copperWall,     2, 5, 3f, 0.5f);
            rewardBlock(Blocks.hail,           1, 1, 1f, 0.5f);
            rewardUnit(UnitTypes.dagger,       1, 3, 3f, 0.5f);
            rewardUnit(UnitTypes.flare,        2, 5, 2f, 0.5f);
            rewardUnit(UnitTypes.mace,         1, 1, 1f, 0.5f);
        }};
    }
}