package nevix;

import mindustry.type.Item;

public class PromotionSys {
    public static Item promotionStar;
    
    public static void load() {
        promotionStar = new Item("promotion-star") {{
            hidden = true;
        }};
    }
}