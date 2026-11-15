package nevix;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitBulletDestroyEvent;
import mindustry.type.Item;

public class PromotionSys {
    public static Item promotionStar;
    
    private static final float dropChance = 1f;

    public static void load() {
        promotionStar = new Item("promotion-star");
    }

    public static void init() {
        Events.on(UnitBulletDestroyEvent.class, event -> {
            var u = event.unit;
            var b = event.bullet;
            if(u == null || b == null || b.team == null) return;
            if(u.team == b.team) return;
            if(!Mathf.chance(Core.settings.getInt("star-drop-chance") / 1000f)) return;

            var core = b.team.core();
            if(core == null) return;

            int amount = Mathf.random(1, 3);
            amount = Math.min(amount, core.storageCapacity - core.items.get(promotionStar));
            if(amount > 0) core.items.add(promotionStar, amount);
        });
        
        Events.run(Trigger.update, () -> {
            if(promotionStar == null) return;
            promotionStar.color.fromHsv(Time.time % 360f, 0.85f, 1f);
        });
    }
}