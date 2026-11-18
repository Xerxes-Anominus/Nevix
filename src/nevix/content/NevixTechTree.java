package nevix.content;

import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.ctype.UnlockableContent;
import mindustry.content.TechTree;
import mindustry.content.TechTree.TechNode;
import nevix.PromotionSys;

public final class NevixTechTree {
    private NevixTechTree() {}

    public static void load() {
        TechNode root = Planets.serpulo.techTree;
        if(root != null){
            new TechNode(root, PromotionSys.promotionStar, PromotionSys.promotionStar.researchRequirements());
        }

        attach(Blocks.scatter, Turrets.spinup);
        attach(Blocks.fuse, Turrets.berserker);
        attach(Blocks.tsunami, Turrets.marker);
        attach(Blocks.duo, Turrets.zoen);
        attach(Blocks.hail, Turrets.swirdy);
        
        attach(Blocks.vault, NevixBlocks.safe);
    }

    private static void attach(UnlockableContent parent, UnlockableContent child) {
        TechNode parentNode = TechTree.all.find(n -> n.content == parent);
        if(parentNode == null) return;

        new TechNode(parentNode, child, child.researchRequirements());
    }
}
