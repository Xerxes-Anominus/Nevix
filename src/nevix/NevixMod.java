package nevix;

import arc.Events;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.content.StatusEffects;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.mod.Mod;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import nevix.turret.SpinupTurret;

public class NevixMod extends Mod {
    public NevixMod() {
        Events.on(ClientLoadEvent.class, event -> {
            StatusEffects.boss.healthMultiplier = 100f;
            StatusEffects.boss.damageMultiplier = 20f;
            StatusEffects.boss.speedMultiplier = 3f;
        });
        
        Events.on(UnitDamageEvent.class, event -> {
            if(!event.unit.dead()) return;
            
            if(event.bullet.owner instanceof Teamc b) {
                if(Mathf.chance(0.5f)) return;
                b.team().core().items.add(promotionShard, Mathf.random(4));
            }
        });
    }
    
    public static Item promotionShard;
    
    public static StatusEffect
    elite, commander, general;
    
    public static Block
    spinup;
    
    @Override
    public void loadContent() {
        promotionShard = new Item("promotion-shard") {{
            buildable = false;
            shownPlanets.add(Planets.serpulo);
        }};
        
        elite = new StatusEffect("elite") {{
            healthMultiplier = 10f;
            damageMultiplier = 2f;
            speedMultiplier = 1.25f;
        }};
        
        commander = new StatusEffect("commander") {{
            healthMultiplier = 25f;
            damageMultiplier = 5f;
            speedMultiplier = 1.5f;
        }};
        
        general = new StatusEffect("general") {{
            healthMultiplier = 50f;
            damageMultiplier = 10f;
            speedMultiplier = 2f;
        }};
        
        spinup = new SpinupTurret("spinup");
    }
}