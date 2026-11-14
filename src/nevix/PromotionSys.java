package nevix;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitBulletDestroyEvent;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.blocks.distribution.Sorter;

public class PromotionSys {
    public static Item promotionStar;
    
    public static void load() {
        promotionStar = new Item("promotion-star") {{
        }};
    }
    
    public static void init() {
        Events.on(UnitBulletDestroyEvent.class, event -> {
            Unit u = event.unit;
            Bullet b = event.bullet;
            if(!u.dead()) return;
            if(u.team == b.team) return;
            if(Mathf.random(1f) > 0.09f) return;
            b.team.core().items.add(promotionStar, Mathf.random(0, 3));
        });
        
        
        Events.on(ClientLoadEvent.class, event -> {
            Vars.content.blocks().each(b -> {
                if(b instanceof Sorter sorter) {
                    sorter.drawDynamic = true;
                }
            });
            
            Events.run(Trigger.update, () -> {
                promotionStar.color = Color.HSVtoRGB(Time.time % 360, 0.85f, 1f);
            });
        });
    }
}