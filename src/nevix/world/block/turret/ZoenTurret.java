package nevix.world.block.turret;

import arc.Core;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import nevix.world.meta.NevixStat;
import nevix.world.meta.NevixStatUnit;

public class ZoenTurret extends NevixTurret {
    public ZoenTurret(String name) {
        super(name);
        
        health = 10;
        
        range = 14f * 8;
        reload = 40f;
        
        shoot.shots = 2;
        shoot.shotDelay = 5f;
        
        inaccuracy = 3f;
        
        ammoTypes.put(Items.copper, new BasicBulletType() {{
            damage = 1f;
            speed = 6f;
            lifetime = 14f * 8f / 6f;
        }});
        
        upgradeBranch();
    }
    
    public static Upgrade
    zoen_time_scaling,
    zoen_damage_scaling,
    zoen_health_scaling,
    zoen_range;
    
    public void upgradeBranch() {
        zoen_time_scaling = addUpgrade("zoen-time-scaling", "", ItemStack.with(Items.copper, 100), (ZoenTurretBuild build) -> build.hasTimeScaling = true, (ZoenTurretBuild build) -> build.hasTimeScaling = false);
        zoen_damage_scaling = addUpgrade("zoen-damage-scaling", "", ItemStack.with(Items.copper, 120), (ZoenTurretBuild build) -> build.hasDamageScaling = true, (ZoenTurretBuild build) -> build.hasDamageScaling = false).requires(zoen_time_scaling);
        zoen_health_scaling = addUpgrade("zoen-health-scaling", "", ItemStack.with(Items.copper, 150), (ZoenTurretBuild build) -> build.hasHealthScaling = true, (ZoenTurretBuild build) -> build.hasHealthScaling = false).requires(zoen_time_scaling);
        zoen_range = addUpgrade("zoen-range", "", ItemStack.with(Items.copper, 80, Items.lead, 20), (ZoenTurretBuild build) -> build.hasRange = true, (ZoenTurretBuild build) -> build.hasRange = false);
    }
    
    @Override
    public void setBars() {
        super.setBars();
        
        addBar("age", build -> new Bar(
        () -> Core.bundle.get("bar.nevix-zoen-age") + ": " + Math.round(((ZoenTurretBuild)build).getSec()),
        () -> Pal.accent,
        () -> 1f
        ));
    }
    
    public class ZoenTurretBuild extends NevixTurretBuild {
        public boolean hasTimeScaling;
        public boolean hasDamageScaling;
        public boolean hasHealthScaling;
        public boolean hasRange;
        
        public float age;
        
        @Override
        public void getStats(Table table) {
            addStat(table, NevixStat.generals, "", Pal.accent);

            int hpBonus = hasHealthScaling ? Mathf.round(getSec() * 5f) : 0;
            addStat(table, Stat.health, "" + Mathf.round(maxHealth())
            + (hpBonus > 0 ? " [gray](+" + hpBonus + ")" : ""));

            int rangeBonus = hasRange ? 6 : 0;
            addStat(table, Stat.range, Mathf.round(range() / Vars.tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile)
            + (rangeBonus > 0 ? " [gray](+" + rangeBonus + " " + NevixStatUnit.get(NevixStatUnit.tile) + ")" : ""));

            addStat(table, Stat.reload, (Mathf.round(reload / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds));

            addStat(table, NevixStat.none, "");
            addStat(table, NevixStat.ammo, "", Pal.accent);
            if (peekAmmo() != null) {
                float baseDmg = peekAmmo().damage;
                float totalDmg = hasDamageScaling ? baseDmg + getSec() / 2f : baseDmg;
                float dmgBonus = totalDmg - baseDmg;
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, Stat.damage, "" + (Mathf.round(totalDmg * 10f) / 10f)
                    + (dmgBonus > 0.05f ? " [gray](+" + (Mathf.round(dmgBonus * 10f) / 10f) + ")" : ""));
                });
            }
            
            if (hasTimeScaling || hasDamageScaling || hasHealthScaling || hasRange) {
                addStat(table, NevixStat.none, "");
                addStat(table, NevixStat.special, "", Pal.accent);
                
                if (hasTimeScaling) {
                    addBulletStatTable(table, bt -> {
                        addBulletStat(bt, NevixStat.age,
                        (Mathf.round(getSec() * 10f) / 10f) + " " + NevixStatUnit.get(StatUnit.seconds));
                        addBulletStat(bt, NevixStat.none, "zoenTimeScaling");
                    });
                }
                if (hasDamageScaling) {
                    addBulletStatTable(table, bt -> addBulletStat(bt, NevixStat.none, "zoenDamageScaling"));
                }
                if (hasHealthScaling) {
                    addBulletStatTable(table, bt -> addBulletStat(bt, NevixStat.none, "zoenHealthScaling"));
                }
                if (hasRange) {
                    addBulletStatTable(table, bt -> addBulletStat(bt, NevixStat.none, "zoenRange"));
                }
            }
        }
        
        public void growing() {
            if(!hasTimeScaling) return;
            
            age += Time.delta;
        }
        
        public float getSec() {
            return age / 60f;
        }
        
        @Override
        public float range() {
            float re = super.range() + (hasRange ? 6f * 8f : 0f);
            return re;
        }
        
        @Override
        public float maxHealth() {
            if(!hasHealthScaling) return 10f;
            return 10f + getSec() * 5f;
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
            if(hasDamageScaling) {
                b.damage = 1f + getSec() / 2f;
            }
            if(hasRange) {
                b.lifetime += 6f * 8f / b.type.speed;
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
        public void updateTile() {
            growing();
            super.updateTile();
        }
        
        @Override
        public void write(Writes w) {
            super.write(w);
            w.bool(hasTimeScaling);
            w.bool(hasDamageScaling);
            w.bool(hasHealthScaling);
            w.bool(hasRange);
            w.f(age);
        }
        
        @Override
        public void read(Reads r, byte re) {
            super.read(r, re);
            hasTimeScaling = r.bool();
            hasDamageScaling = r.bool();
            hasHealthScaling = r.bool();
            hasRange = r.bool();
            age = r.f();
        }
    }
}