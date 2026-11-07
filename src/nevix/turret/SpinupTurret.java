package nevix.turret;

import arc.Core;
import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import nevix.world.NevixStat;
import nevix.world.NevixStatUnit;

public class SpinupTurret extends NevixTurret {
    public class SpinupBulletType extends BasicBulletType {
        public SpinupBulletType() {
            super();
        }
        
        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            if(bullet.owner instanceof SpinupTurretBuild ownerBuild) {
                if(ownerBuild.explosiveBullet) {
                    Damage.damage(bullet.x, bullet.y, 3f * Vars.tilesize, bullet.damage * 3f);
                }
                if(ownerBuild.percentBullet) {
                    if(entity instanceof Healthc hitter) {
                        hitter.damagePierce(hitter.health() * 0.005f);
                    }
                }
            }
            super.hitEntity(bullet, entity, health);
        }
    }
    
    public SpinupTurret(String name) {
        super(name);
        
        health = 850;
        size = 2;
        
        reload = 45f;
        range = 30f * Vars.tilesize;
        
        shoot.shots = 5;
        inaccuracy = 5;
        
        shootSound = Sounds.shootDisperse;
        
        ammoTypes.put(Items.lead, new SpinupBulletType() {{
            damage = 24;
            speed = 5;
            lifetime = 48;
            
            collidesGround = false;
            
            ammoMultiplier = 3;
            
            width = 5;
            height = 5;
            
            trailWidth = 1f;
            trailLength = 10;
            
            frontColor = Color.white;
            backColor = Items.lead.color;
            trailColor = Items.lead.color;
        }});
        SpinupBulletType frag_bullet = new SpinupBulletType() {{
            damage = 45;
            speed = 2.5f;
            lifetime = 6;
            
            collidesGround = false;
            
            width = 2;
            height = 2;
            
            trailWidth = 0.4f;
            trailLength = 4;
            
            frontColor = Color.white;
            backColor = Items.metaglass.color;
            trailColor = Items.metaglass.color;
        }};
        ammoTypes.put(Items.metaglass, new SpinupBulletType() {{
            damage = 18;
            speed = 5;
            lifetime = 48;
            
            ammoMultiplier = 3;
            
            collidesGround = false;
            
            width = 5;
            height = 5;
            
            trailWidth = 1;
            trailLength = 10;
            
            frontColor = Color.white;
            backColor = Items.metaglass.color;
            trailColor = Items.metaglass.color;
            
            fragBullets = 3;
            fragBullet = frag_bullet;
        }});
        
        Upgrade spinup_explosive_bullet = addUpgrade("spinup-explosive-bullet", "The projectile deals an additional 300% splash damage.", ItemStack.with(Items.copper, 200, Items.lead, 200, Items.graphite, 40), (SpinupTurretBuild build) -> build.explosiveBullet = true, (SpinupTurretBuild build) -> build.explosiveBullet = false);
        Upgrade spinup_percent_bullet = addUpgrade("spinup-percent-bullet", "The projectile deals additional true damage equal to 0.5% of the enemy's maximum health.", ItemStack.with(Items.copper, 600, Items.lead, 600, Items.graphite, 350, Items.titanium, 100), (SpinupTurretBuild build) -> build.percentBullet = true, (SpinupTurretBuild build) -> build.percentBullet = false);
        Upgrade spinup_haste_0 = addUpgrade("spinup-haste-0", "Increases maximum fire rate by 50%.", ItemStack.with(Items.copper, 100, Items.lead, 50), (SpinupTurretBuild build) -> build.hasteTier++, (SpinupTurretBuild build) -> build.hasteTier--);
        Upgrade spinup_haste_1 = addUpgrade("spinup-haste-1", "Increases maximum fire rate by 100%.", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 100), (SpinupTurretBuild build) -> build.hasteTier++, (SpinupTurretBuild build) -> build.hasteTier--).levelAfter(spinup_haste_0);
        Upgrade spinup_haste_2 = addUpgrade("spinup-haste-2", "Increases maximum fire rate by 200%.", ItemStack.with(Items.copper, 800, Items.lead, 800, Items.graphite, 300, Items.titanium, 50), (SpinupTurretBuild build) -> build.hasteTier++, (SpinupTurretBuild build) -> build.hasteTier--).levelAfter(spinup_haste_1);
        Upgrade spinup_multi_shot = addUpgrade("spinup-multi-shot", "Each shot has a 40% chance to trigger two additional shots.", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 70), (SpinupTurretBuild build) -> build.multiShot = true, (SpinupTurretBuild build) -> build.multiShot = false).requires(spinup_haste_0);
    }
    
    @Override
    public void setBars() {
        super.setBars();
        
        addBar("shootCount", build -> {
            SpinupTurretBuild b = (SpinupTurretBuild) build;
            return new Bar(
            () -> Core.bundle.has("bar.spinup-shoot-count")
                ? Core.bundle.get("bar.spinup-shoot-count") + ": " + (10f * b.shootCount) + "%"
                : "Fire rate: " + (10f * b.shootCount) + "%",
            () -> Color.cyan,
            () -> b.max <= 0 ? 0f : (float) b.shootCount / (float) b.max
            );
        });
    }
    
    public class SpinupTurretBuild extends NevixTurretBuild {
        public boolean explosiveBullet;
        public boolean percentBullet;
        public boolean multiShot;
        public int hasteTier;
        public int shootCount;
        public int max;
        public float nonShootTimer;
        
        @Override
        protected void getStats(Table table) {
            addStat(table, NevixStat.generals, "", Pal.accent);
            addStat(table, Stat.health, "" + maxHealth());
            addStat(table, Stat.range, "" + range());
            addStat(table, NevixStat.fireRate, "+" + (hasteBoost() * 100f) + "%");
            addStat(table, NevixStat.fireRatePerShoot, "+10%");
            
            addStat(table, NevixStat.none, "");
            
            addStat(table, NevixStat.ammo, "", Pal.accent);
            addBulletStatTable(table, bt -> {
                if(peekAmmo() != null) {
                    addBulletStat(bt, Stat.damage, "" + peekAmmo().damage);
                    if(peekAmmo() instanceof SpinupBulletType sbt && explosiveBullet) {
                        addBulletStat(bt, NevixStat.splashDamage, "" + (sbt.damage * 3f));
                        addBulletStat(bt, NevixStat.splashDamageRadius, "3.0" + NevixStatUnit.tile);
                    }
                    if(peekAmmo() instanceof SpinupBulletType sbt && percentBullet) {
                        addBulletStat(bt, NevixStat.bonusDamage, "0.5%" + NevixStatUnit.maxHealth);
                    }
                    if(peekAmmo().fragBullets > 0 && peekAmmo().fragBullet != null) {
                        addBulletStat(bt, Stat.damage, "" + peekAmmo().fragBullet.damage, 1);
                        if(peekAmmo().fragBullet instanceof SpinupBulletType sbt && explosiveBullet) {
                            addBulletStat(bt, NevixStat.splashDamage, "" + (sbt.damage * 3f));
                            addBulletStat(bt, NevixStat.splashDamageRadius, "3.0" + NevixStatUnit.tile);
                        }
                        if(peekAmmo().fragBullet instanceof SpinupBulletType sbt && percentBullet) {
                            addBulletStat(bt, NevixStat.bonusDamage, "0.5%" + NevixStatUnit.maxHealth);
                        }
                    }
                }
            });
            
            addStat(table, NevixStat.none, "");
            
            addStat(table, NevixStat.special, "", Pal.accent);
            if(multiShot) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "Each shot has a 40% chance to trigger two additional shots.");
                });
            }
        }
        
        public int getMax() {
            switch (hasteTier) {
                case 1 -> {
                    return 5;
                }
                case 2 -> {
                    return 10;
                }
                case 3 -> {
                    return 20;
                }
                default -> {
                    return 0;
                }
            }
        }
        public float hasteBoost() {
            return getMax() * 0.1f;
        }
        
        protected void clampHaste() {
            max = getMax();
            shootCount = Mathf.clamp(shootCount, 0, max);
        }
        
        @Override
        public void updateTile() {
            clampHaste();
            super.updateTile();
            
            if (isShooting) {
                nonShootTimer = 0f;
            } else if (shootCount > 0) {
                nonShootTimer += Time.delta;
                if (nonShootTimer >= 180f) {
                    nonShootTimer = 0f;
                    shootCount = 0;
                }
            }
        }
        
        protected void extraShoot(BulletType bullet) {
            float
            bulletX = x + Angles.trnsx(rotation - 90, shootX, shootY),
            bulletY = y + Angles.trnsy(rotation - 90, shootX, shootY);

            if(shoot.firstShotDelay > 0){
                chargeSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax));
                bullet.chargeEffect.at(bulletX, bulletY, rotation);
            }

            ShootPattern pattern = bullet.shootPattern != null ? bullet.shootPattern : shoot;

            pattern.shoot(barrelCounter, (xOffset, yOffset, angle, delay, mover) -> {
                queuedBullets++;
                int barrel = barrelCounter;

                if(delay > 0f){
                    Time.run(delay, () -> {
                        int prev = barrelCounter;
                        barrelCounter = barrel;
                        bullet(bullet, xOffset, yOffset, angle, mover);
                        barrelCounter = prev;
                    });
                }else{
                    bullet(bullet, xOffset, yOffset, angle, mover);
                }
            }, () -> barrelCounter++);
        }
        
        @Override
        protected void updateShooting() {
            float threshold = reload / (1f + shootCount * 0.1f);
            if (reloadCounter >= threshold && !charging() && shootWarmup >= minWarmup) {
                BulletType type = peekAmmo();
                shoot(type);
                reloadCounter %= threshold;
            }
        }
        
        @Override
        protected void shoot(BulletType type) {
            if (type == null) return;
            super.shoot(type);
            if (multiShot && Mathf.chance(0.4f)) {
                extraShoot(type);
                extraShoot(type);
            }
            shootCount = Math.min(shootCount + 1, getMax());
        }
        
        @Override
        public void write(Writes w) {
            super.write(w);
            w.bool(explosiveBullet);
            w.bool(percentBullet);
            w.bool(multiShot);
            w.i(hasteTier);
            w.i(shootCount);
            w.i(max);
            w.f(nonShootTimer);
        }
        
        @Override
        public void read(Reads r, byte re) {
            super.read(r, re);
            explosiveBullet = r.bool();
            percentBullet = r.bool();
            multiShot = r.bool();
            hasteTier = r.i();
            shootCount = r.i();
            max = r.i();
            nonShootTimer = r.f();
        }
    }
}