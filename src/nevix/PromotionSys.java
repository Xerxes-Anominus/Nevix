package nevix;

import arc.Events;
import arc.math.Mathf;
import mindustry.game.EventType.UnitBulletDestroyEvent;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.type.Item;

public class PromotionSys {
    public static Item promotionStar;
    
    public static void load() {
        promotionStar = new Item("promotion-star") {{
            hidden = true;
        }};
    }
    
    public static void init() {
        Events.on(UnitBulletDestroyEvent.class, event -> {
            Unit u = event.unit;
            Bullet b = event.bullet;
            if(!u.dead()) return;
            if(u.team == b.team) return;
            if(Mathf.chance(0.9f)) return;
            b.team.core().items.add(promotionStar, Mathf.random(0, 3));
        });
    }
}