package nevix.content;

import mindustry.content.Blocks;
import mindustry.gen.LegsUnit;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import nevix.ai.ReactoryAI;

public class NevixUnit {
    public static UnitType
    reactory;
    
    public static void load() {
        reactory = new UnitType("reactory") {{
            health = Blocks.thoriumReactor.health;
            hitSize = 24f;
            
            speed = 10f;
            
            range = 80f;
            
            legCount = 6;
            legLength = 48f;
            
            constructor = LegsUnit::create;
            aiController = ReactoryAI::new;
            
            drawCell = false;
            
            weapons.add(new Weapon("super-gun") {{
                reload = 30f;
                
                mirror = false;
                x = 0f;
                y = 0f;
                
                alwaysShooting = true;
                
                shootSound = Blocks.thoriumReactor.destroySound;
                
                bullet = Blocks.thoriumReactor.destroyBullet;
                bullet.shootEffect = Blocks.thoriumReactor.destroyEffect;
            }});
        }};
    }
}