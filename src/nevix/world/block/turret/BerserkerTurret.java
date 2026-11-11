package nevix.world.block.turret;

import arc.graphics.Color;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;

public class BerserkerTurret extends NevixTurret {
    public class BerserkerBulletType extends ShrapnelBulletType {
        public BerserkerBulletType() {
            super();
        }
        
        @Override
        public void init(Bullet bullet) {
            if(bullet.owner instanceof BerserkerTurretBuild owner && owner.isValid()) {
                BerserkerTurretBuild o = owner;
                if(o.hasShrapness && !o.inRage) {
                    bullet.damage *= 1.5f;
                }
                if(o.hasBloodShield && !o.inRage) {
                    o.bloodShieldHealth += bullet.damage / 2;
                }
                if(o.inRage && !o.lastShoot) {
                    switch(o.enrageTier) {
                        case 1 -> {
                            bullet.damage *= 1.5f;
                            return;
                        }
                        case 2 -> {
                            bullet.damage *= 2f;
                            return;
                        }
                        case 3 -> {
                            bullet.damage *= 2.5f;
                            return;
                        }
                        default -> {
                            bullet.damage *= 1f;
                            return;
                        }
                    }
                }
            }
        }
    }
    
    public BerserkerTurret(String name) {
        super(name);
        
        health = 5000;
        size = 3;
        
        reload = 60f;
        range = 10 * Vars.tilesize;
        
        shootSound = Sounds.shootFuse;
        shoot = new ShootSpread(5, 12f);
        
        ammoTypes.put(Items.blastCompound, new BerserkerBulletType() {{
            damage = 120;
            ammoMultiplier = 5;
            width = 20f;
            length = 70f;
            status = StatusEffects.blasted;
            statusDuration = 120f;
            toColor = Items.blastCompound.color;
        }});
        
        upgradeBranch();
    }
    
    public static Upgrade
    reloader_0, reloader_1, shrapness, endurance,
    enrage_0, enrage_1, enrage_2, blood_shield;

    public void upgradeBranch() {
        reloader_0 = addUpgrade("reloader-0", "Increase fire rate by 25%", ItemStack.with(Items.copper, 200, Items.lead, 200, Items.graphite, 50), (BerserkerTurretBuild build) -> build.reloaderTier++, (BerserkerTurretBuild build) -> build.reloaderTier = 0);
        reloader_1 = addUpgrade("reloader-1", "Increase fire rate by 50%", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 200, Items.titanium, 50), (BerserkerTurretBuild build) -> build.reloaderTier++, (BerserkerTurretBuild build) -> build.reloaderTier = 0).levelAfter(reloader_0);
        shrapness = addUpgrade("shrapness", "Base damage increased by 50%", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 100, Items.titanium, 20), (BerserkerTurretBuild build) -> build.hasShrapness = true, (BerserkerTurretBuild build) -> build.hasShrapness = false).removes(endurance);
        endurance = addUpgrade("endurance", "Increases maximum health by 100%", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 300, Items.titanium, 100), (BerserkerTurretBuild build) -> build.hasEndurance = true, (BerserkerTurretBuild build) -> build.hasEndurance = false).removes(shrapness);
        blood_shield = addUpgrade("blood-shield", "Each time it deals damage, Blood Shield restores an amount of health equal to 50% of the bullet's damage. Healing beyond full health becomes Blood Shield points, which have no upper limit. Blood Shield points absorb all incoming damage; a hit larger than the remaining points breaks the shield and the excess damage is nullified. Nearby enemy bullets are blocked while it holds.", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 300, Items.titanium, 50), (BerserkerTurretBuild build) -> build.hasBloodShield = true, (BerserkerTurretBuild build) -> build.hasBloodShield = false);
        enrage_0 = addUpgrade("enrage-0", "When 50 rounds are fired, enter a Rage state for 1 second. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 0.5% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 25%. When the Rage state ends, fire a charged shot that deals 3000% damage.", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 250), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).requires(blood_shield);
        enrage_1 = addUpgrade("enrage-1", "When 40 rounds are fired, enter a Rage state for 2 seconds. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 0.75% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 50%. When the Rage state ends, fire a charged shot that deals 5000% damage.", ItemStack.with(Items.copper, 900, Items.lead, 1000, Items.graphite, 600, Items.titanium, 500, Items.thorium, 100), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).levelAfter(enrage_0);
        enrage_2 = addUpgrade("enrage-2", "When 30 rounds are fired, enter a Rage state for 3 seconds. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 1.0% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 100%. When the Rage state ends, fire a charged shot that deals 10000% damage.", ItemStack.with(Items.copper, 2000, Items.lead, 2000, Items.graphite, 1200, Items.titanium, 800, Items.thorium, 300), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).levelAfter(enrage_1);
    }
    
    @Override
    public void setBars() {
        super.setBars();
        
        addBar("berserker-shoot-count-bar", build -> new Bar(
        () -> "Shoot Count: " + ((BerserkerTurretBuild)build).shootCount,
        () -> Color.red,
        () -> 1f
        ));
    }
    
    public class BerserkerTurretBuild extends NevixTurretBuild {
        public boolean hasShrapness;
        public boolean hasEndurance;
        public boolean hasBloodShield;
        public int reloaderTier;
        public int enrageTier;
        
        public float bloodShieldHealth;
        
        public boolean inRage;
        public boolean lastShoot;
        public float rageTimer;
        public float rageFireTimer;
        public int shootCount;
        
        @Override
        public void getStats(Table table) {
            
        }
        
        @Override
        public float maxHealth() {
            maxHealth = (hasEndurance ? 2f * maxHealth : block.health);
            return maxHealth;
        }
        
        @Override
        public void updateShooting() {
            float threshold = reload - reload * (reloaderTier == 0 ? 0f : (reloaderTier == 1 ? 0.25f : 0.5f));
            if (reloadCounter >= threshold && !charging() && shootWarmup >= minWarmup) {
                BulletType type = peekAmmo();
                shoot(type);
                reloadCounter %= threshold;
            }
        }
        
        public void updateShield() {
            if(bloodShieldHealth <= 0f) return;
            
            Groups.bullet.intersect(this.x, this.y, 80f, 80f).each(b -> {
                float dx = b.x - this.x;
                float dy = b.y - this.y;
                if(dx * dx + dy * dy <= 40f) {
                    b.remove();
                    bloodShieldHealth -= b.damage;
                    if(bloodShieldHealth <= 0f) return;
                }
            });
        }
        
        @Override
        protected void shoot(BulletType type) {
            if(inRage) return;
            
            super.shoot(type);
            shootCount++;
        }
        
        @Override
        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, Mover mover) {
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
            if(rageTimer >= 180f) {
                b.damage = b.type.damage * (enrageTier == 1 ? 30 : (enrageTier == 2 ? 50 : 100));
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
                if(!inRage) {
                    useAmmo();
                }
            }
        }
        
        public void updateRage() {
            int needed = (enrageTier == 1 ? 50 : (enrageTier == 2 ? 40 : (enrageTier == 3 ? 30 : Integer.MAX_VALUE)));
            
            if(shootCount < needed) return;
            
            inRage = true;
            rageTimer += Time.delta;
            rageFireTimer += Time.delta;
            if(rageFireTimer % 12f == 0 && rageTimer < 180f) {
                rageFireTimer %= 12f;
                shoot(peekAmmo());
            }
            
            if(rageTimer >= 180f) {
                lastShoot = true;
                shoot(peekAmmo());
                lastShoot = false;
                inRage = false;
                rageTimer = 0f;
                shootCount = 0;
                return;
            }
        }
        
        @Override
        public void updateTile() {
            updateShield();
            updateRage();
            super.updateTile();
        }
        
        @Override
        public void write(Writes w) {
            super.write(w);
            w.bool(hasShrapness);
            w.bool(hasEndurance);
            w.bool(hasBloodShield);
            w.i(reloaderTier);
            w.i(enrageTier);
            w.f(bloodShieldHealth);
            w.bool(inRage);
            w.bool(lastShoot);
            w.f(rageTimer);
            w.f(rageFireTimer);
            w.i(shootCount);
        }
        
        @Override
        public void read(Reads r, byte re) {
            super.read(r, re);
            hasShrapness = r.bool();
            hasEndurance = r.bool();
            hasBloodShield = r.bool();
            reloaderTier = r.i();
            enrageTier = r.i();
            bloodShieldHealth = r.f();
            inRage = r.bool();
            lastShoot = r.bool();
            rageTimer = r.f();
            rageFireTimer = r.f();
            shootCount = r.i();
        }
    }
}