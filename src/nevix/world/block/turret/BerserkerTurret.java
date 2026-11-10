package nevix.world.block.turret;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.ShrapnelBulletType;
import mindustry.entities.pattern.ShootSpread;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Sounds;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;

public class BerserkerTurret extends NevixTurret {

    // ===== Bảng thông số theo tier (index 0 = chưa có) =====
    public static final float[] RELOAD_SCALE  = {1f, 0.75f, 0.5f};            // reload 60 -> 45 -> 30
    public static final int[]   RAGE_SHOTS    = {0, 50, 40, 30};              // số phát để vào Rage
    public static final float[] RAGE_TIME     = {0f, 60f, 120f, 180f};        // tick (1s / 2s / 3s)
    public static final float[] RAGE_DAMAGE   = {0f, 0.25f, 0.5f, 1f};        // +% sát thương khi Rage (cộng dồn với Shrapness)
    public static final float[] RAGE_HEAL     = {0f, 0.005f, 0.0075f, 0.01f}; // % máu tối đa mỗi enemy trúng
    public static final float[] CHARGE_DAMAGE = {0f, 30f, 50f, 100f};         // 3000% / 5000% / 10000%

    // ===== Thông số chỉnh tay =====
    public float rageFireInterval = 5f;   // reload trong Rage (tick)
    public float ragePull = 0.15f;        // lực hút quái trong Rage
    public float ragePullMinDst = 24f;    // quái lại gần hơn mức này thì thôi không hút
    public float bloodShieldRatio = 0.5f; // Blood Shield: hồi 50% sát thương của đạn
    public float bloodShieldRadius = 40f; // bán kính chặn đạn địch

    // ===== Đạn =====
    public static class BerserkerBulletType extends ShrapnelBulletType {
        public BerserkerBulletType() {
            super();
        }

        // Shrapnel gây sát thương ngay trong init(), nên phải nhân sát thương TRƯỚC super.init()
        @Override
        public void init(Bullet b) {
            if(b.owner instanceof BerserkerTurretBuild owner) {
                b.damage *= owner.berserkDamageMult();
            }
            super.init(b);
        }

        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            if(bullet.owner instanceof BerserkerTurretBuild owner && owner.isValid()) {
                owner.onBerserkHit(bullet.damage);
            }
            super.hitEntity(bullet, entity, health);
        }
    }

    public BerserkerTurret(String name) {
        super(name);
        
        health = 5000;
        size = 3;
        
        reload = 60f;
        range = 5 * Vars.tilesize;
        
        shootSound = Sounds.shootFuse;
        shoot = new ShootSpread(5, 12f);
        
        ammoTypes.put(Items.blastCompound, new BerserkerBulletType() {{
            damage = 120;
            ammoMultiplier = 3;
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
        shrapness = addUpgrade("shrapness", "Base damage increased by 50%", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 100, Items.titanium, 20), (BerserkerTurretBuild build) -> build.hasShrapness = true, (BerserkerTurretBuild build) -> build.hasShrapness = false);
        endurance = addUpgrade("endurance", "Increases maximum health by 100%", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 300, Items.titanium, 100), (BerserkerTurretBuild build) -> build.hasEndurance = true, (BerserkerTurretBuild build) -> build.hasEndurance = false);
        blood_shield = addUpgrade("blood-shield", "Each time it deals damage, Blood Shield restores an amount of health equal to 50% of the bullet's damage. Healing beyond full health becomes Blood Shield points, which have no upper limit. Blood Shield points absorb all incoming damage; a hit larger than the remaining points breaks the shield and the excess damage is nullified. Nearby enemy bullets are blocked while it holds.", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 300, Items.titanium, 50), (BerserkerTurretBuild build) -> build.hasBloodShield = true, (BerserkerTurretBuild build) -> build.hasBloodShield = false);
        enrage_0 = addUpgrade("enrage-0", "When 50 rounds are fired, enter a Rage state for 1 second. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 0.5% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 25%. When the Rage state ends, fire a charged shot that deals 3000% damage.", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 250), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0);
        enrage_1 = addUpgrade("enrage-1", "When 40 rounds are fired, enter a Rage state for 2 seconds. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 0.75% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 50%. When the Rage state ends, fire a charged shot that deals 5000% damage.", ItemStack.with(Items.copper, 900, Items.lead, 1000, Items.graphite, 600, Items.titanium, 500, Items.thorium, 100), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).levelAfter(enrage_0);
        enrage_2 = addUpgrade("enrage-2", "When 30 rounds are fired, enter a Rage state for 3 seconds. While in the Rage state, fire continuously without stopping (without consuming ammo), while pulling enemies in and recovering 1.0% of max HP for each enemy hit (if at full HP, the entire healing amount will be converted into Blood Shield points), and increasing damage dealt by 100%. When the Rage state ends, fire a charged shot that deals 10000% damage.", ItemStack.with(Items.copper, 2000, Items.lead, 2000, Items.graphite, 1200, Items.titanium, 800, Items.thorium, 300), (BerserkerTurretBuild build) -> build.enrageTier++, (BerserkerTurretBuild build) -> build.enrageTier = 0).levelAfter(enrage_1);

        // Nối quan hệ SAU KHI tất cả upgrade đã được tạo (tránh tham chiếu null)
        shrapness.removes(endurance);
        endurance.removes(shrapness);
        enrage_0.requires(blood_shield);
    }

    public class BerserkerTurretBuild extends NevixTurretBuild {
        public boolean hasShrapness;
        public boolean hasEndurance;
        public boolean hasBloodShield;
        public int reloaderTier;
        public int enrageTier;

        public float bloodShieldHealth;
        public boolean inRage;
        public int shootCount;
        public int rageShots;
        public float rageTimer;
        public float rageFireTimer;
        public BulletType lastType;

        @Override
        public void getStats(Table table) {

        }

        protected int tier() {
            return Mathf.clamp(enrageTier, 0, 3);
        }

        // ===== Endurance =====
        @Override
        public float maxHealth() {
            return hasEndurance ? block.health * 2f : block.health;
        }

        // ===== Shrapness + Rage: hệ số sát thương (được gọi từ BerserkerBulletType.init) =====
        public float berserkDamageMult() {
            float m = 1f;
            if(hasShrapness) m += 0.5f;
            if(inRage) m += RAGE_DAMAGE[tier()];
            return m;
        }

        // ===== Blood Shield =====
        /** Hồi máu; phần dư (hoặc toàn bộ nếu đã đầy máu) đổi thành điểm Blood Shield. */
        public void gainHealth(float amount) {
            if(amount <= 0f) return;
            float healed = Math.min(Math.max(maxHealth() - health, 0f), amount);
            if(healed > 0f) heal(healed);
            float overflow = amount - healed;
            if(overflow > 0f && hasBloodShield) {
                bloodShieldHealth += overflow;
            }
        }

        /** Gọi mỗi lần một enemy bị trúng đạn. */
        public void onBerserkHit(float bulletDamage) {
            float amount = 0f;
            if(inRage) {
                // Trong Rage: chỉ hồi % máu tối đa mỗi enemy trúng (đầy máu thì cộng vào giáp)
                amount = maxHealth() * RAGE_HEAL[tier()];
            } else if(hasBloodShield) {
                // Ngoài Rage: Blood Shield hồi 50% sát thương của đạn
                amount = bulletDamage * bloodShieldRatio;
            }
            gainHealth(amount);
        }

        // Blood Shield nhận toàn bộ sát thương trước. Đòn vượt quá máu giáp thì giáp vỡ
        // và phần sát thương thừa bị triệt tiêu (turret không mất máu).
        @Override
        public void damage(float amount) {
            if(bloodShieldHealth > 0f) {
                bloodShieldHealth = Math.max(bloodShieldHealth - amount, 0f);
                return;
            }
            super.damage(amount);
        }

        @Override
        public void kill() {
            if(bloodShieldHealth > 0f) return;
            super.kill();
        }

        // Chặn đạn địch trong bán kính khi còn Blood Shield
        public void updateShield() {
            if(bloodShieldHealth <= 0f) return;
            float r = bloodShieldRadius;
            Groups.bullet.intersect(x - r, y - r, r * 2f, r * 2f).each(b -> {
                if(b.team != team && b.type.absorbable && b.within(this, r)) {
                    b.absorb();
                }
            });
        }

        // ===== Reloader =====
        public float reloadTime() {
            return reload * RELOAD_SCALE[Mathf.clamp(reloaderTier, 0, 2)];
        }

        @Override
        protected void updateShooting() {
            if(inRage) return; // trong Rage dùng cơ chế bắn riêng
            float threshold = reloadTime();
            if(reloadCounter >= threshold && !charging() && shootWarmup >= minWarmup) {
                BulletType type = peekAmmo();
                lastType = type;
                shoot(type);
                reloadCounter %= threshold;
                if(enrageTier > 0) shootCount++;
            }
        }

        // ===== Update =====
        @Override
        public void updateTile() {
            super.updateTile();

            // Gỡ Endurance -> máu không được vượt max mới
            if(health > maxHealth()) health = maxHealth();
            if(!hasBloodShield) bloodShieldHealth = 0f;

            updateShield();
            updateEnrage();
        }

        // ===== Enrage =====
        public void updateEnrage() {
            int t = tier();
            if(t == 0) {
                inRage = false;
                return;
            }

            if(!inRage) {
                if(shootCount >= RAGE_SHOTS[t] && lastType != null) startRage(t);
                return;
            }

            pullEnemies();

            rageTimer -= delta();
            rageFireTimer += delta();
            if(rageFireTimer >= rageFireInterval) {
                rageFireTimer %= rageFireInterval;
                rageShots++;
                fireFree(lastType, -1f, rageShots % 3 == 0); // âm thanh thưa bớt cho đỡ ồn
            }

            if(rageTimer <= 0f) endRage(t);
        }

        protected void startRage(int t) {
            inRage = true;
            shootCount = 0;
            rageShots = 0;
            rageTimer = RAGE_TIME[t];
            rageFireTimer = rageFireInterval; // bắn ngay phát đầu
        }

        protected void endRage(int t) {
            inRage = false; // tắt trước để charged shot không nhận thêm buff Rage
            shootCount = 0;
            reloadCounter = 0f;

            if(lastType != null) {
                fireFree(lastType, lastType.damage * CHARGE_DAMAGE[t], true);
                Effect.shake(6f, 6f, this);
            }
        }

        protected void pullEnemies() {
            float r = range();
            Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, u -> {
                if(!u.within(this, r) || u.within(this, ragePullMinDst)) return;
                if(!u.checkTarget(targetAir, targetGround)) return;
                Tmp.v1.set(x - u.x, y - u.y).nor().scl(ragePull * u.mass() * delta());
                u.impulse(Tmp.v1);
            });
        }

        /** Bắn 1 viên không tốn ammo. damage < 0 = dùng sát thương gốc của đạn. */
        protected Bullet fireFree(BulletType type, float damage, boolean withSound) {
            float xSpread = Mathf.range(xRand);
            float bulletX = x + Angles.trnsx(rotation - 90, shootX + xSpread, shootY);
            float bulletY = y + Angles.trnsy(rotation - 90, shootX + xSpread, shootY);
            float shootAngle = rotation + Mathf.range(inaccuracy + type.inaccuracy);

            Bullet b = type.create(this, team, bulletX, bulletY, shootAngle, damage, 1f, 1f, null, null, targetPos.x, targetPos.y);

            (shootEffect == null ? type.shootEffect : shootEffect).at(bulletX, bulletY, rotation, type.hitColor);
            (smokeEffect == null ? type.smokeEffect : smokeEffect).at(bulletX, bulletY, rotation, type.hitColor);
            if(withSound) {
                (type.shootSound != Sounds.none ? type.shootSound : shootSound).at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax), shootSoundVolume);
            }

            if(shake > 0) Effect.shake(shake, shake, this);
            curRecoil = 1f;
            heat = 1f;
            return b;
        }

        // ===== Hiển thị =====
        @Override
        public void draw() {
            super.draw();

            if(bloodShieldHealth > 0f) {
                Draw.z(Layer.effect);
                Draw.color(Pal.remove, 0.3f + 0.4f * Mathf.clamp(bloodShieldHealth / maxHealth()));
                Lines.stroke(1.5f);
                Lines.circle(x, y, block.size * Vars.tilesize * 0.65f);
            }
            if(inRage) {
                Draw.z(Layer.effect);
                Draw.color(Pal.remove, 0.5f + 0.3f * Mathf.absin(Time.time, 4f, 1f));
                Lines.stroke(2f);
                Lines.circle(x, y, block.size * Vars.tilesize * 0.8f);
            }
            Draw.reset();
        }
    }
}