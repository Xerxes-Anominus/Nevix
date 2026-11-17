package nevix.content;

import arc.audio.Sound;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.math.Angles;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.PointBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.LegsUnit;
import mindustry.gen.MechUnit;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import nevix.ai.CitadelDroneAI;
import nevix.ai.ReactoryAI;
import nevix.type.unit.CitadelTitanUnitType;

public class NevixUnits {
    public static UnitType
    reactory,
    citadel, citadelDrone;
    
    public static Effect
    shootCitadelFlame;
    
    public static void load() {
        reactory = new UnitType("reactory") {{
            health = Blocks.thoriumReactor.health;
            hitSize = 24f;
            
            speed = 5f;
            
            range = 80f;
            
            legCount = 6;
            legLength = 48f;
            
            hidden = true;
            
            constructor = LegsUnit::create;
            aiController = ReactoryAI::new;
            
            drawCell = false;
            isEnemy = false;
            useUnitCap = false;
            
            weapons.add(new Weapon("super-gun") {{
                reload = 60f;
                
                mirror = false;
                x = 0f;
                y = 0f;
                
                alwaysShooting = true;
                
                shootSound = Sounds.explosionReactor;
                
                bullet = new BasicBulletType() {{
                    damage = 0f;
                    speed = 0f;
                    lifetime = 0f;
                    despawnEffect = Fx.reactorExplosion;
                    splashDamage = 5000f;
                    splashDamageRadius = 120f;
                }};
            }});
        }};
        
        final float R = 100f * Vars.tilesize;
        final Sound cannonSound = UnitTypes.reign.weapons.get(0).shootSound;
        
        citadelDrone = new UnitType("citadel-drone") {{
            health = 1000f;
            hitSize = 16f;
            speed = 3.5f;
            accel = 0.08f;
            drag = 0.04f;
            flying = true;
            lowAltitude = true;
            range = 35f * Vars.tilesize;
            maxRange = range;

            targetable = false;
            hittable = false;
            killable = false;

            hidden = true;
            isEnemy = false;
            useUnitCap = false;
            playerControllable = false;
            logicControllable = false;
            allowedInPayloads = false;
            singleTarget = true;

            constructor = UnitEntity::create;
            aiController = CitadelDroneAI::new;
            
            weapons.add(new Weapon("") {{
                x = 0f; y = 0f;
                mirror = false;
                rotate = true;
                reload = 5f;
                inaccuracy = 3f;
                shootSound = Sounds.shoot;
                bullet = new BasicBulletType(14f, 90f) {{
                    lifetime = (35f * Vars.tilesize) / 14f;
                    pierce = true;
                    pierceCap = 1;
                    width = 5f; height = 9f;
                }};
            }});
            
            weapons.add(new Weapon("") {{
                x = 7f; y = 0f;
                mirror = true;
                alternate = true;
                rotate = true;
                reload = 180f;
                shoot.shots = 4;
                shoot.shotDelay = 5f;
                shootSound = Sounds.shoot;
                bullet = new BasicBulletType(10f, 450f) {{
                    lifetime = (30f * Vars.tilesize) / 10f;
                    pierce = true;
                    pierceCap = 3;
                    splashDamage = 300f;
                    splashDamageRadius = 5f * Vars.tilesize;
                    width = 8f; height = 12f;
                    hitEffect = Fx.blastExplosion;
                }};
            }});
        }};
        
        citadel = new CitadelTitanUnitType("citadel") {{
            health = 2300000f;
            hitSize = 240f;
            speed = 0.15f;
            canDrown = false;
            constructor = MechUnit::create;

            rotateSpeed = 0.6f;
            mechStride = 180f;

            range = 75f * Vars.tilesize;
            
            auraHealth = 400;
            auraRechargeTime = 60f * 60f;
            auraHealPercent = 0.015f;

            droneType = citadelDrone;
            droneCount = 5;
            
            weapons.add(new Weapon("") {{
                x = 100f; y = 0f;
                mirror = true;
                alternate = true;
                rotate = false;
                rotateSpeed = 2f;
                reload = 600f;
                shake = 10f;
                inaccuracy = 2f;
                shoot.shots = 2;
                shoot.shotDelay = 6f;
                shootSound = cannonSound;
                soundPitchMin = 0.7f;
                soundPitchMax = 0.9f;
                
                bullet = new BasicBulletType(14f, 900f) {{
                    lifetime = (50f * Vars.tilesize) / 14f;
                    width = 96f; height = 128f;
                    hitSize = 48f;

                    pierce = true;
                    pierceCap = -1;
                    pierceBuilding = true;

                    splashDamage = 4500f;
                    splashDamageRadius = 12f * Vars.tilesize;
                    hitEffect = Fx.massiveExplosion;

                    despawnHit = true;
                    fragOnHit = false;
                    fragBullets = 3;
                    fragBullet = fragLayer(5);
                }
                    @Override
                    public void init(Bullet b) {
                        super.init(b);
                        cannonSound.at(b.x, b.y, 0.7f, 1f);
                        cannonSound.at(b.x, b.y, 0.85f, 1f);
                        cannonSound.at(b.x, b.y, 1f, 1f);
                    }
                };
            }});
            
            weapons.add(new Weapon("") {{
                x = 0f; y = 46f;
                mirror = false;
                rotate = false;
                rotateSpeed = 8f;
                reload = 15f;
                controllable = false;
                autoTarget = true;
                shootSound = Sounds.shootFlame;

                bullet = new BulletType(8f, 250f) {{
                    lifetime = (20f * Vars.tilesize) / 8f;
                    hitSize = 14f;
                    pierce = true;
                    pierceArmor = true;
                    collidesAir = false;
                    hittable = false;
                    keepVelocity = false;
                    status = StatusEffects.burning;
                    statusDuration = 60f * 10f;
                    shootEffect = new Effect(45f, 80f, e -> {
                        Draw.color(Pal.lightFlame, Pal.darkFlame, Color.gray, e.fin());
                        
                        Angles.randLenVectors(e.id, 12, e.finpow() * 60f, e.rotation, 10f, (x, y) -> {
                            Fill.circle(e.x + x, e.y + y, 0.65f + e.fout() * 1.5f);
                        });
                    }).followParent(false);
                    hitEffect = Fx.hitFlameSmall;
                    despawnEffect = Fx.none;
                }
                    @Override
                    public void hitEntity(Bullet b, Hitboxc entity, float health) {
                        super.hitEntity(b, entity, health);
                        if (entity instanceof Unit) {
                            ((Unit) entity).apply(StatusEffects.melting, 60f * 3f);
                        }
                    }
                };
            }});
            
            float[][] snipePos = {
                {-67f, 9f}, {-67f, -23f},
                {-32f, -6f}, {28f, -9f},
                {72f, 15f}, {61f, -23f}
            };
            for (float[] p : snipePos) {
                weapons.add(new Weapon("") {{
                    x = p[0]; y = p[1];
                    mirror = false;
                    rotate = true;
                    rotateSpeed = 6f;
                    reload = 60f;
                    controllable = false;
                    autoTarget = true;
                    shootSound = Sounds.shootForeshadow;

                    bullet = new PointBulletType() {{
                        damage = 600f;
                        speed = 75f;
                        shootEffect = Fx.instShoot;
                        hitEffect = Fx.instHit;
                        smokeEffect = Fx.smokeCloud;
                        trailEffect = Fx.instTrail;
                        despawnEffect = Fx.instBomb;
                        trailSpacing = 20f;
                        hitShake = 6f;
                    }};
                }});
            }
        }};
    }
    
    private static BulletType fragLayer(int layersLeft) {
        return new BasicBulletType(4f, 75f) {{
            lifetime = 30f;
            width = 28f;
            height = 36f;
            hitSize = 16f;
            despawnHit = true;
            if (layersLeft > 1) {
                fragBullets = 3;
                fragBullet = fragLayer(layersLeft - 1);
            }
        }};
    }
}