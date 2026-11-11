package nevix.world.block.turret;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Healthc;
import mindustry.gen.Hitboxc;
import mindustry.gen.Sounds;
import mindustry.gen.Teamc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import nevix.world.meta.NevixStat;
import nevix.world.meta.NevixStatUnit;

public class BerserkerTurret extends NevixTurret {
    public class BerserkerBulletType extends ShrapnelBulletType {
        public BerserkerBulletType() {
            super();
        }

        @Override
        public void init(Bullet bullet) {
            if(bullet.owner instanceof BerserkerTurretBuild o && o.isValid()) {
                if(o.lastShoot) {
                    bullet.damage *= switch(o.enrageTier) {
                        case 1 -> 30f;
                        case 2 -> 50f;
                        case 3 -> 100f;
                        default -> 1f;
                    };
                }else if(o.inRage) {
                    bullet.damage *= switch(o.enrageTier) {
                        case 1 -> 1.5f;
                        case 2 -> 2f;
                        case 3 -> 2.5f;
                        default -> 1f;
                    };
                }else if(o.hasShrapness) {
                    bullet.damage *= 1.5f;
                }
            }
            super.init(bullet);
        }
        
        void onValidHit(Bullet bullet) {
            if(!(bullet.owner instanceof BerserkerTurretBuild o) || !o.isValid()) return;

            if(o.inRage || o.lastShoot) {
                float pct = switch(o.enrageTier) {
                    case 1 -> 0.005f;
                    case 2 -> 0.0075f;
                    case 3 -> 0.01f;
                    default -> 0f;
                };
                o.restore(o.maxHealth() * pct);
            }else if(o.hasBloodShield) {
                o.restore(bullet.damage / 2f);
            }
        }

        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            if(entity instanceof Healthc h && !h.dead() && entity instanceof Teamc t && t.team() != bullet.team) {
                onValidHit(bullet);
            }
            super.hitEntity(bullet, entity, health);
        }

        @Override
        public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct) {
            if(build != null && build.team != b.team && build.isValid()) {
                onValidHit(b);
            }
            super.hitTile(b, build, x, y, initialHealth, direct);
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
    berserker_reloader_0,
    berserker_reloader_1,
    berserker_shrapness,
    berserker_endurance,
    berserker_enrage_0,
    berserker_enrage_1,
    berserker_enrage_2,
    berserker_blood_shield;

    public void upgradeBranch() {
        berserker_reloader_0 = addUpgrade("berserker-reloader-0", "Increase fire rate by 25%", ItemStack.with(Items.copper, 200, Items.lead, 200, Items.graphite, 50), (BerserkerTurretBuild build) -> build.reloaderTier++, (BerserkerTurretBuild build) -> build.reloaderTier = 0);
        berserker_reloader_1 = addUpgrade("berserker-reloader-1", "Increase fire rate by 50%", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 200, Items.titanium, 50), (BerserkerTurretBuild build) -> build.reloaderTier++, (BerserkerTurretBuild build) -> build.reloaderTier = 0).levelAfter(berserker_reloader_0);
        berserker_shrapness = addUpgrade("berserker-shrapness", "Base damage increased by 50%", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 100, Items.titanium, 20), (BerserkerTurretBuild build) -> build.hasShrapness = true, (BerserkerTurretBuild build) -> build.hasShrapness = false).removes(berserker_endurance);
        berserker_endurance = addUpgrade("berserker-endurance", "Increases maximum health by 100%", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 300, Items.titanium, 100), (BerserkerTurretBuild build) -> build.hasEndurance = true, (BerserkerTurretBuild build) -> build.hasEndurance = false).removes(berserker_shrapness);
        berserker_blood_shield = addUpgrade("berserker-blood-shield", "Each time it deals damage, Blood Shield restores an amount of health equal to 50% of the bullet's damage. Healing beyond full health becomes Blood Shield points, which have no upper limit. Blood Shield points absorb all incoming damage; a hit larger than the remaining points breaks the shield and the excess damage is nullified. Nearby enemy bullets are blocked while it holds.", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 300, Items.titanium, 50), (BerserkerTurretBuild build) -> build.hasBloodShield = true, (BerserkerTurretBuild build) -> build.hasBloodShield = false);
        berserker_enrage_0 = addUpgrade("berserker-enrage-0", "When 50 rounds are fired, enter a Rage state for 1 second. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 0.5% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 25%. When the Rage state ends, fire a charged shot that deals 3000% damage.", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 250), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).requires(berserker_blood_shield);
        berserker_enrage_1 = addUpgrade("berserker-enrage-1", "When 40 rounds are fired, enter a Rage state for 2 seconds. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 0.75% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 50%. When the Rage state ends, fire a charged shot that deals 5000% damage.", ItemStack.with(Items.copper, 900, Items.lead, 1000, Items.graphite, 600, Items.titanium, 500, Items.thorium, 100), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).levelAfter(berserker_enrage_0);
        berserker_enrage_2 = addUpgrade("berserker-enrage-2", "When 30 rounds are fired, enter a Rage state for 3 seconds. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 1.0% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 100%. When the Rage state ends, fire a charged shot that deals 10000% damage.", ItemStack.with(Items.copper, 2000, Items.lead, 2000, Items.graphite, 1200, Items.titanium, 800, Items.thorium, 300), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).levelAfter(berserker_enrage_1);
    }
    
    @Override
    public void setBars() {
        super.setBars();
        
        addBar("berserker-shoot-count-bar", build -> new Bar(
        () -> Core.bundle.get("nevix-berserker-shoot-count") + ": " + ((BerserkerTurretBuild)build).shootCount,
        () -> Pal.accent,
        () -> 1f
        ));
        
        addBar("berserker-shield-health", build -> new Bar(
        () -> Core.bundle.get("nevix-berserker-shield-health") + ": " + Math.max(0f, ((BerserkerTurretBuild)build).bloodShieldHealth),
        () -> Color.red,
        () -> 1f
        ));
    }
    
    public BerserkerBulletType chargeShoot = new BerserkerBulletType() {{
        damage = 120;
        ammoMultiplier = 5;
        width = 40f;
        length = 140f;
        status = StatusEffects.blasted;
        statusDuration = 120f;
        toColor = Items.blastCompound.color;
    }};
    
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
            addStat(table, NevixStat.generals, "", Pal.accent);
            addStat(table, Stat.health, "" + maxHealth() + (hasEndurance ? " [gray](+100%)" : ""));
            addStat(table, Stat.range, "" + range() + " " + NevixStatUnit.get(NevixStatUnit.tile));
            addStat(table, Stat.reload, "" + (reloaderTier == 1 ? "6.66666/" + NevixStatUnit.get(StatUnit.seconds) : (reloaderTier == 2 ? "10/" + NevixStatUnit.get(StatUnit.seconds) : "5/" + NevixStatUnit.get(StatUnit.seconds))));
            
            addStat(table, NevixStat.none, "");
            
            addStat(table, NevixStat.ammo, "", Pal.accent);
            if(peekAmmo() != null) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, Stat.damage, "" + peekAmmo().damage, 1);
                    addBulletStat(bt, NevixStat.pierce, "True", 1);
                });
            }
            
            addStat(table, NevixStat.special, "", Pal.accent);
            if(hasShrapness) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "hasShrapness");
                });
            }
            if(hasEndurance) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "hasEndurance");
                });
            }
            
            if(reloaderTier == 1) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "reloader1");
                });
            }
            if(reloaderTier == 2) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "reloader2");
                });
            }
            
            if(hasBloodShield) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "hasBloodShield");
                });
            }
            
            if(enrageTier == 1) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "enrage1");
                });
            }
            if(enrageTier == 2) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "enrage2");
                });
            }
            if(enrageTier == 3) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, NevixStat.none, "enrage3");
                });
            }
        }
        
        @Override
        public float maxHealth() {
            maxHealth = (hasEndurance ? 2f * block.health : block.health);
            return maxHealth;
        }
        
        @Override
        public void updateShooting() {
            if(inRage) return;
            
            float threshold = reload - reload * (reloaderTier == 0 ? 0f : (reloaderTier == 1 ? 0.25f : 0.5f));
            if (reloadCounter >= threshold && !charging() && shootWarmup >= minWarmup) {
                BulletType type = peekAmmo();
                shoot(type);
                reloadCounter %= threshold;
            }
        }
        
        public void restore(float amount) {
            if(amount <= 0f) return;
            
            float missing = maxHealth() - this.health;
            if(missing > 0f) {
                float healed = Math.min(missing, amount);
                heal(healed);
                amount -= healed;
            }
            if(amount >= 0f) {
                bloodShieldHealth += amount;
            }
        }
        
        public void updateShield() {
            if(bloodShieldHealth <= 0f) return;
            
            Groups.bullet.intersect(this.x - 40f, this.y - 40f, 80f, 80f).each(b -> {
                if(bloodShieldHealth <= 0f) return;
                if(b.within(this, 40f) && b.team != this.team) {
                    bloodShieldHealth -= b.damage;
                    Fx.absorb.at(b.x, b.y);
                    b.remove();
                }
            });
        }
        
        @Override
        protected void shoot(BulletType type) {
            super.shoot(type);
            if(!inRage) {
                shootCount++;
            }
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
        
        public float rageDuration() {
            return (enrageTier == 1 ? 60f : (enrageTier == 2 ? 120f : (enrageTier == 3 ? 180f : 0f)));
        }
        
        public void updateRage() {
            int needed = (enrageTier == 1 ? 50 : (enrageTier == 2 ? 40 : (enrageTier == 3 ? 30 : Integer.MAX_VALUE)));
            
            if(shootCount < needed) return;
            
            inRage = true;
            rageTimer += Time.delta;
            rageFireTimer += Time.delta;
            if(rageFireTimer >= 12f && rageTimer < rageDuration()) {
                rageFireTimer -= 12f;
                shoot(peekAmmo());
            }
            
            if(rageTimer >= rageDuration()) {
                lastShoot = true;
                shoot(chargeShoot);
                lastShoot = false;
                inRage = false;
                rageTimer = 0f;
                shootCount = 0;
                return;
            }
        }
        
        @Override
        public void updateTile() {
            if(enrageTier == 0) {
                shootCount = 0;
            }
            if(!hasBloodShield) {
                bloodShieldHealth = 0f;
            }
            updateShield();
            updateRage();
            super.updateTile();
        }
        
        @Override
        public void draw() {
            super.draw();
            
            if(bloodShieldHealth > 0f) {
                Draw.color(Color.valueOf("ff000080"));
                Draw.z(Layer.effect);
                
                Fill.circle(this.x, this.y, 40f);
                
                Draw.color(Color.red);
                Draw.blend(Blending.normal);
                Draw.z(Layer.effect + 0.1f);
                
                float id = this.y * Vars.world.width() - this.x;
                Lines.stroke(2f);
                Lines.arc(this.x, this.y, 38f, 1f / 6, (Time.time + id) % 360f);
                Lines.arc(this.x, this.y, 40f, 1f / 4, (120f + Time.time - id) % 360f);
                Lines.arc(this.x, this.y, 42f, 1f / 5, (150f - Time.time + id) % 360f);
                Draw.reset();
            }
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