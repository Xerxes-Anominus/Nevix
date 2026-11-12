package nevix.world.block.turret;

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
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootAlternate;
import mindustry.entities.pattern.ShootBarrel;
import mindustry.entities.pattern.ShootPattern;
import mindustry.gen.Bullet;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import nevix.world.meta.NevixStat;
import nevix.world.meta.NevixStatUnit;

public class SpinupTurret extends NevixTurret {
    public class SpinupBulletType extends BasicBulletType {
        public SpinupBulletType() {
            super();
        }
        
        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            if(bullet.owner instanceof SpinupTurretBuild ownerBuild) {
                if(ownerBuild.explosiveBullet) {
                    Damage.damage(bullet.team, bullet.x, bullet.y, 3f * Vars.tilesize, bullet.damage * 3f, true, false);
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
        
        reload = 30f;
        range = 30f * Vars.tilesize;
        
        recoil = 2f;
        
        shoot = new ShootAlternate() {{
            spread = 5f;
            shots = 6;
            barrels = 3;
        }};
        
        shootSound = Sounds.shootDisperse;
        targetGround = false;
        
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
        
        upgradeBranch();
        
        
    }
    
    public static Upgrade
    spinup_explosive_bullet, spinup_percent_bullet,
    spinup_haste_0, spinup_haste_1, spinup_haste_2,
    spinup_multi_shot, spinup_ranger_0, spinup_ranger_1;
    
    public void upgradeBranch() {
        spinup_explosive_bullet = addUpgrade("spinup-explosive-bullet", "The projectile deals an additional 300% splash damage.", ItemStack.with(Items.copper, 200, Items.lead, 200, Items.graphite, 40), (SpinupTurretBuild build) -> build.explosiveBullet = true, (SpinupTurretBuild build) -> build.explosiveBullet = false).removes(spinup_percent_bullet);
        spinup_percent_bullet = addUpgrade("spinup-percent-bullet", "The projectile deals additional true damage equal to 0.5% of the enemy's maximum health.", ItemStack.with(Items.copper, 600, Items.lead, 600, Items.graphite, 350, Items.titanium, 100), (SpinupTurretBuild build) -> build.percentBullet = true, (SpinupTurretBuild build) -> build.percentBullet = false).removes(spinup_explosive_bullet);
        spinup_haste_0 = addUpgrade("spinup-haste-0", "Increases maximum fire rate by 50%.", ItemStack.with(Items.copper, 100, Items.lead, 50), (SpinupTurretBuild build) -> build.hasteTier++, (SpinupTurretBuild build) -> build.hasteTier--);
        spinup_haste_1 = addUpgrade("spinup-haste-1", "Increases maximum fire rate by 100%.", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 100), (SpinupTurretBuild build) -> build.hasteTier++, (SpinupTurretBuild build) -> build.hasteTier--).levelAfter(spinup_haste_0);
        spinup_haste_2 = addUpgrade("spinup-haste-2", "Increases maximum fire rate by 200%.", ItemStack.with(Items.copper, 800, Items.lead, 800, Items.graphite, 300, Items.titanium, 50), (SpinupTurretBuild build) -> build.hasteTier++, (SpinupTurretBuild build) -> build.hasteTier--).levelAfter(spinup_haste_1);
        spinup_multi_shot = addUpgrade("spinup-multi-shot", "Each shot has a 40% chance to trigger two additional shots.", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 70), (SpinupTurretBuild build) -> build.multiShot = true, (SpinupTurretBuild build) -> build.multiShot = false).requires(spinup_haste_0).removes(spinup_ranger_0, spinup_ranger_1);
        spinup_ranger_0 = addUpgrade("spinup-ranger-0", "Increase range by 5 tiles", ItemStack.with(Items.copper, 250, Items.lead, 200, Items.graphite, 20), (SpinupTurretBuild build) -> build.ranger++, (SpinupTurretBuild build) -> build.ranger = 0).removes(spinup_multi_shot);
        spinup_ranger_1 = addUpgrade("spinup-ranger-1", "increase range by 10 tiles", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 200, Items.titanium, 25), (SpinupTurretBuild build) -> build.ranger++, (SpinupTurretBuild build) -> build.ranger = 0).levelAfter(spinup_ranger_0).removes(spinup_multi_shot);
    }
    
    @Override
    public void setBars() {
        super.setBars();
        
        addBar("shootCount", build -> {
            SpinupTurretBuild b = (SpinupTurretBuild) build;
            return new Bar(
            () -> Core.bundle.has("bar.nevix-spinup-shoot-count")
                ? Core.bundle.get("bar.nevix-spinup-shoot-count") + ": " + (10f * b.shootCount) + "%"
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
        public int ranger;
        public int hasteTier;
        public int shootCount;
        public int max;
        public float nonShootTimer;
        
        @Override
        protected void getStats(Table table) {
            addStat(table, NevixStat.generals, "", Pal.accent);
            addStat(table, Stat.health, "" + maxHealth());
            addStat(table, Stat.range, "" + (range() / Vars.tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile)
            + (ranger > 0 ? " [gray](+" + rangerBoost() + ")" : ""));
            addStat(table, Stat.reload, "" + (Mathf.round(reload / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds));

            addStat(table, NevixStat.none, "");

            addStat(table, NevixStat.ammo, "", Pal.accent);
            if(peekAmmo() != null) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, Stat.damage, "" + peekAmmo().damage);
                    if(peekAmmo().fragBullets > 0 && peekAmmo().fragBullet != null) {
                        addBulletStat(bt, NevixStat.fragBullets, "" + peekAmmo().fragBullets);
                        addBulletStat(bt, Stat.damage, "" + peekAmmo().fragBullet.damage, 1);
                    }
                });
            }

            addStat(table, NevixStat.none, "");

            addStat(table, NevixStat.special, "", Pal.accent);

            if (hasteTier > 0) {
                int maxBonus = getMax() * 10;
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.attackSpeed, "+10%");
                    addBulletStat(bt, NevixStat.attackSpeedMax, "+" + maxBonus + "%");
                    addBulletStat(bt, NevixStat.none, "spinupHasteStack");
                });
            }
            if (multiShot) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.multiShotChance, "40%");
                });
            }
            if (explosiveBullet) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.splashDamageBonus, "+300%");
                });
            }
            if (percentBullet) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.trueDamage, "0.5% " + NevixStatUnit.get(NevixStatUnit.maxHealth));
                });
            }
        }
        
        public float rangerBoost() {
            if(ranger == 1) {
                return 5.0f;
            }else if(ranger == 2) {
                return 10.0f;
            }
            return 0f;
        }
        
        @Override
        public float range() {
            return super.range() + rangerBoost() * 8f;
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
            
            if (isShooting() && hasAmmo()) {
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
        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, Mover mover){
            queuedBullets --;

            if(dead || (!consumeAmmoOnce && !hasAmmo())) return;

            float
            xSpread = Mathf.range(xRand),
            bulletX = x + Angles.trnsx(rotation - 90, shootX + xOffset + xSpread, shootY + yOffset),
            bulletY = y + Angles.trnsy(rotation - 90, shootX + xOffset + xSpread, shootY + yOffset),
            shootAngle = rotation + angleOffset + Mathf.range(inaccuracy + type.inaccuracy);

            float baseLife = (1f - lifeRnd) + Mathf.random(lifeRnd) + extraLife,
                  lifeScl = type.scaleLife ? Mathf.clamp((baseLife + scaleLifetimeOffset) * Mathf.dst(bulletX, bulletY, targetPos.x, targetPos.y) / type.range, minRange() / type.range, range() / type.range) : baseLife;
            
            Bullet b = type.create(this, this.team, bulletX, bulletY, shootAngle, -1f, (1f - velocityRnd) + Mathf.random(velocityRnd) + extraVelocity, lifeScl, null, mover, targetPos.x, targetPos.y);
            if(b != null) {
                b.lifetime += rangerBoost() * 8f / type.speed;
            }
            
            (shootEffect == null ? type.shootEffect : shootEffect).at(bulletX, bulletY, rotation + angleOffset, type.hitColor);
            (smokeEffect == null ? type.smokeEffect : smokeEffect).at(bulletX, bulletY, rotation + angleOffset, type.hitColor);
            (type.shootSound != Sounds.none ? type.shootSound : shootSound).at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);

            ammoUseEffect.at(
                x - Angles.trnsx(rotation, ammoEjectBack),
                y - Angles.trnsy(rotation, ammoEjectBack),
                rotation * Mathf.sign(xOffset)
            );

            if(shake > 0){
                Effect.shake(shake, shake, this);
            }

            curRecoil = 1f;
            if(recoils > 0){
                curRecoils[barrelCounter % recoils] = 1f;
            }
            heat = 1f;
            totalShots++;

            if(!consumeAmmoOnce){
                useAmmo();
            }
        }
        
        @Override
        public void write(Writes w) {
            super.write(w);
            w.bool(explosiveBullet);
            w.bool(percentBullet);
            w.bool(multiShot);
            w.i(hasteTier);
            w.i(ranger);
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
            ranger = r.i();
            shootCount = r.i();
            max = r.i();
            nonShootTimer = r.f();
        }
    }
}