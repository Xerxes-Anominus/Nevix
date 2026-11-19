package nevix.world.block.turret;

import arc.graphics.Color;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.units.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import nevix.*;

/**
 * Viết cho Mindustry v7 (build 146).
 * Spirit là một UnitType thật -> cần 1 sprite placeholder: sprites/units/emperical-sword-spirit.png
 */
public class EmpericalTurret extends NevixTurret {
    // ===================== CẤU HÌNH =====================
    public static final float BASE_DAMAGE = 510f;

    static final float[] CHARGE_CHANCE = {0f, 0.02f, 0.05f};
    static final float[] CHARGE_DAMAGE = {0f, 5f, 9f};          // 500% / 900%
    static final float NORMAL_CHARGE = 30f;                       // tick tụ lực kiếm thường
    static final float GREAT_CHARGE_MUL = 2f;                     // đại kiếm tụ lâu gấp đôi

    static final float INTRINSIC_STEP = 0.10f;                    // +10% mỗi thanh
    static final float INTRINSIC_MAX = 4f;                        // buff tối đa +400%
    static final float INTRINSIC_RESET_TIME = 300f;               // 5s không phóng thanh kiếm nào -> buff về 0%
    static final int INTRINSIC_VOLLEY = 5;

    static final int GREAT_SWORD_NEED = 300;
    static final float GREAT_SWEEP_DAMAGE = 20f;                  // 2000%
    static final float SWEEP_RANGE_MUL = 1.5f;
    static final float SWEEP_TIME = 36f;
    static final float SWEEP_ARC = 70f;                           // nửa góc quét
    static final int SWEEP_MAX = 5;

    static final float SPLIT_DAMAGE = 1.5f;                       // 150%
    static final float SPLIT_RADIUS = 4f * 8f;
    static final float RETURN_DAMAGE = 2f;                        // 200% * số kiếm

    static final float SPIRIT_KILL_CHANCE = 0.01f;
    static final float SPIRIT_EXTRA_BONUS = 0.05f;                // mỗi điểm thừa: +5% máu & sát thương
    static final float SPIRIT_RESPAWN = 180f;                     // chết -> hồi sinh sau 3s (không tốn điểm)
    static final float SKILL_LOCK = 60f;                          // nghỉ 1s sau khi tung xong một kỹ năng
    static final float SLASH_RANGE = 4.5f;                        // tile
    static final float SLASH_REACH = 16f, SLASH_RADIUS = 28f;
    static final float DASH_SPEED = 10f;

    static final Color swordColor = Color.valueOf("ffd37f");
    static final Color swordCore = Color.white;
    static final Color spiritColor = Color.valueOf("7fd4ff");

    // ===================== NỘI DUNG DÙNG CHUNG =====================
    public static SwordBullet normalSword, greatSword, convergeSword, returnSword;
    public static StatusEffect swordSlow;
    public static UnitType swordSpirit;
    public static Effect slashFx, dashFx, mergeFx, spawnFx;

    public EmpericalTurret(String name) {
        super(name);

        health = 11500;
        size = 4;

        range = 45f * Vars.tilesize;
        reload = 70f;

        // Điện: 203.7/tick. Tốc độ phóng kiếm nhân theo hiệu suất điện (baseReloadSpeed()).
        consumePower(203.7f);

        // Đạn: hợp kim đột biến, mỗi 2 thanh kiếm phóng ra tốn 1 item (ammoMultiplier = 2, ammoPerShot = 1).
        // BulletType này chỉ để turret nhận item làm đạn, không được bắn thật.
        ammoPerShot = 1;
        ammo(Items.surgeAlloy, new BasicBulletType(0f, BASE_DAMAGE) {{
            ammoMultiplier = 2f;
        }});

        initContent();
        upgradeBranch();
    }

    static void initContent() {
        if (normalSword != null) return;

        swordSlow = new StatusEffect("emperical-sword-slow") {{
            speedMultiplier = 0.3f;   // làm chậm 70%
            color = swordColor;
        }};

        normalSword = new SwordBullet(SwordBullet.NORMAL, 14f, 60f, 10f);
        greatSword = new SwordBullet(SwordBullet.GREAT, 9f, 80f, 24f);
        convergeSword = new SwordBullet(SwordBullet.CONVERGE, 9f, 20f, 6f);
        returnSword = new SwordBullet(SwordBullet.RETURN, 16f, 40f, 26f);

        slashFx = new Effect(12f, 80f, e -> {
            Draw.color(Color.white, e.color, e.fin());
            Lines.stroke(e.fout() * 3f);
            Lines.arc(e.x, e.y, 18f + e.fin() * 12f, 0.3f, e.rotation - 54f);
        });
        dashFx = new Effect(14f, e -> {
            Draw.color(e.color, 0.7f);
            Fill.circle(e.x, e.y, 7f * e.fout());
        });
        mergeFx = new Effect(25f, 400f, e -> {
            Draw.z(Layer.effect);
            drawSword(e.x, e.y, e.rotation, 70f * e.fin(), 9f * e.fin(), e.fin());
            Draw.color(swordColor, e.fout());
            Lines.stroke(e.fout() * 2f);
            Lines.circle(e.x, e.y, 40f * e.fout());
        });
        spawnFx = new Effect(20f, e -> {
            Draw.color(spiritColor, e.fout());
            Lines.stroke(e.fout() * 2.5f);
            Lines.circle(e.x, e.y, 6f + e.fin() * 22f);
        });

        swordSpirit = new SpiritType("emperical-sword-spirit");
    }

    // ===================== STAT =====================
    public float[]
    RELOAD = {70f, 65f, 60f, 50f},
    RANGE = {45f * Vars.tilesize, 55f * Vars.tilesize},
    DAMAGE_MULTI = {1f, 1.25f, 1.5f};
    public int[] SPIRIT_COUNT = {0, 1, 3, 5};

    public static Upgrade
    emperical_reload_0,
    emperical_reload_1,
    emperical_reload_2,
    emperical_range,
    emperical_damage_1,
    emperical_damage_2,
    emperical_intrinsic,
    emperical_charge_shoot_0,
    emperical_charge_shoot_1,
    emperical_great_sword,
    emperical_sword_spirit_0,
    emperical_sword_spirit_1,
    emperical_sword_spirit_2;

    public void upgradeBranch() {
        // Chi phí chỉ là số tạm theo phong cách SwirdyTurret, bạn tự cân lại.
        emperical_reload_0 = addUpgrade("emperical-reload-0", "", ItemStack.with(Items.copper, 200, Items.lead, 150, Items.graphite, 120, Items.silicon, 80),
            (EmpericalTurretBuild build) -> build.ReloadTier = Math.max(build.ReloadTier, 1),
            (EmpericalTurretBuild build) -> build.ReloadTier = 0);
        emperical_reload_1 = addUpgrade("emperical-reload-1", "", ItemStack.with(Items.graphite, 200, Items.silicon, 150, Items.titanium, 150, Items.metaglass, 100),
            (EmpericalTurretBuild build) -> build.ReloadTier = Math.max(build.ReloadTier, 2),
            (EmpericalTurretBuild build) -> build.ReloadTier = Math.min(build.ReloadTier, 1)).levelAfter(emperical_reload_0);
        emperical_reload_2 = addUpgrade("emperical-reload-2", "", ItemStack.with(Items.silicon, 250, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100),
            (EmpericalTurretBuild build) -> build.ReloadTier = Math.max(build.ReloadTier, 3),
            (EmpericalTurretBuild build) -> build.ReloadTier = Math.min(build.ReloadTier, 2)).levelAfter(emperical_reload_1);

        emperical_range = addUpgrade("emperical-range", "", ItemStack.with(Items.graphite, 200, Items.silicon, 200, Items.titanium, 250, Items.thorium, 100),
            (EmpericalTurretBuild build) -> build.RangeBoosted = true,
            (EmpericalTurretBuild build) -> build.RangeBoosted = false);

        emperical_damage_1 = addUpgrade("emperical-damage-1", "", ItemStack.with(Items.graphite, 200, Items.silicon, 150, Items.titanium, 200, Items.thorium, 100),
            (EmpericalTurretBuild build) -> build.DamageTier = Math.max(build.DamageTier, 1),
            (EmpericalTurretBuild build) -> build.DamageTier = 0);
        emperical_damage_2 = addUpgrade("emperical-damage-2", "", ItemStack.with(Items.silicon, 250, Items.titanium, 300, Items.thorium, 200, Items.plastanium, 120),
            (EmpericalTurretBuild build) -> build.DamageTier = Math.max(build.DamageTier, 2),
            (EmpericalTurretBuild build) -> build.DamageTier = Math.min(build.DamageTier, 1)).levelAfter(emperical_damage_1);

        emperical_intrinsic = addUpgrade("emperical-intrinsic", "", ItemStack.with(Items.silicon, 250, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100),
            (EmpericalTurretBuild build) -> build.hasIntrinsic = true,
            (EmpericalTurretBuild build) -> build.hasIntrinsic = false);

        emperical_charge_shoot_0 = addUpgrade("emperical-charge-shoot-0", "", ItemStack.with(Items.silicon, 250, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100),
            (EmpericalTurretBuild build) -> build.chargeTier = Math.max(build.chargeTier, 1),
            (EmpericalTurretBuild build) -> build.chargeTier = 0);
        emperical_charge_shoot_1 = addUpgrade("emperical-charge-shoot-1", "", ItemStack.with(Items.silicon, 300, Items.titanium, 350, Items.thorium, 250, Items.plastanium, 150),
            (EmpericalTurretBuild build) -> build.chargeTier = Math.max(build.chargeTier, 2),
            (EmpericalTurretBuild build) -> build.chargeTier = Math.min(build.chargeTier, 1)).levelAfter(emperical_charge_shoot_0);

        emperical_great_sword = addUpgrade("emperical-great-sword", "", ItemStack.with(PromotionSys.promotionStar, 15, Items.silicon, 250, Items.titanium, 300, Items.thorium, 200, Items.plastanium, 150),
            (EmpericalTurretBuild build) -> build.hasGreatSword = true,
            (EmpericalTurretBuild build) -> build.hasGreatSword = false);

        emperical_sword_spirit_0 = addUpgrade("emperical-sword-spirit-0", "", ItemStack.with(PromotionSys.promotionStar, 10, Items.silicon, 200, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100),
            (EmpericalTurretBuild build) -> build.spiritTier = Math.max(build.spiritTier, 1),
            (EmpericalTurretBuild build) -> build.spiritTier = 0);
        emperical_sword_spirit_1 = addUpgrade("emperical-sword-spirit-1", "", ItemStack.with(PromotionSys.promotionStar, 15, Items.silicon, 250, Items.titanium, 300, Items.thorium, 200, Items.plastanium, 150),
            (EmpericalTurretBuild build) -> build.spiritTier = Math.max(build.spiritTier, 2),
            (EmpericalTurretBuild build) -> build.spiritTier = Math.min(build.spiritTier, 1)).levelAfter(emperical_sword_spirit_0);
        emperical_sword_spirit_2 = addUpgrade("emperical-sword-spirit-2", "", ItemStack.with(PromotionSys.promotionStar, 20, Items.silicon, 300, Items.titanium, 350, Items.thorium, 250, Items.plastanium, 200),
            (EmpericalTurretBuild build) -> build.spiritTier = Math.max(build.spiritTier, 3),
            (EmpericalTurretBuild build) -> build.spiritTier = Math.min(build.spiritTier, 2)).levelAfter(emperical_sword_spirit_1);
    }

    // ===================== HELPER STATIC =====================
    /** Kiếm: mũi hướng theo rot. len = chiều dài lưỡi, wid = nửa bề rộng. */
    static void drawSword(float x, float y, float rot, float len, float wid, float a) {
        float c = Mathf.cosDeg(rot), s = Mathf.sinDeg(rot);
        float px = -s, py = c;
        float bx = x + c * len * 0.8f, by = y + s * len * 0.8f;

        Draw.color(swordColor, a);
        Fill.quad(x + px * wid, y + py * wid, x - px * wid, y - py * wid,
                bx - px * wid, by - py * wid, bx + px * wid, by + py * wid);
        Fill.tri(bx + px * wid, by + py * wid, bx - px * wid, by - py * wid, x + c * len, y + s * len);

        Draw.color(swordCore, a);
        Lines.stroke(Math.max(1f, wid * 0.35f));
        Lines.line(x, y, x + c * len * 0.95f, y + s * len * 0.95f);

        Lines.stroke(Math.max(1.5f, wid * 0.5f));
        Lines.line(x + px * wid * 2.2f, y + py * wid * 2.2f, x - px * wid * 2.2f, y - py * wid * 2.2f);
        Lines.line(x, y, x - c * len * 0.25f, y - s * len * 0.25f);
        Draw.reset();
    }

    /** Gây sát thương lên 1 unit, báo kill cho Emperical nếu hạ được. */
    public static float hurt(EmpericalTurretBuild src, Unit u, float dmg) {
        boolean alive = u.health > 0f && !u.dead;
        u.damage(dmg);
        if (alive && src != null && (u.health <= 0f || u.dead)) src.onKill();
        return dmg;
    }

    /** Sát thương vùng tròn (unit + công trình địch). Trả về tổng sát thương gây ra. */
    public static float areaHit(Team team, EmpericalTurretBuild src, float x, float y, float radius, float damage, float slowTime) {
        float[] total = {0f};
        Units.nearbyEnemies(team, x - radius, y - radius, radius * 2f, radius * 2f, u -> {
            if (!u.within(x, y, radius + u.hitSize / 2f)) return;
            total[0] += hurt(src, u, damage);
            if (slowTime > 0f) u.apply(swordSlow, slowTime);
        });
        Vars.indexer.allBuildings(x, y, radius, b -> {
            if (b.team == team || b.team == Team.derelict) return;
            b.damage(team, damage);
            total[0] += damage;
        });
        return total[0];
    }

    /** Sát thương dọc đoạn thẳng (mỗi mục tiêu 1 lần nhờ set hit). */
    public static float lineHit(Team team, EmpericalTurretBuild src, float x1, float y1, float x2, float y2, float width, float damage, ObjectSet<Object> hit) {
        float[] total = {0f};
        Units.nearbyEnemies(team, Math.min(x1, x2) - width, Math.min(y1, y2) - width,
                Math.abs(x2 - x1) + width * 2f, Math.abs(y2 - y1) + width * 2f, u -> {
            if (hit.contains(u)) return;
            if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, u.x, u.y) <= width + u.hitSize / 2f) {
                hit.add(u);
                total[0] += hurt(src, u, damage);
            }
        });
        return total[0];
    }

    // ===================== DỮ LIỆU KIẾM / BULLET =====================
    public static class SwordData {
        public EmpericalTurretBuild src;
        public float radius = SPLIT_RADIUS, slowTime = 180f;

        public SwordData(EmpericalTurretBuild src) {
            this.src = src;
        }
    }

    public static class SwordBullet extends BulletType {
        public static final int NORMAL = 0, GREAT = 1, CONVERGE = 2, RETURN = 3;
        public final int kind;

        public SwordBullet(int kind, float speed, float lifetime, float hitSize) {
            super(speed, 0f);
            this.kind = kind;
            this.lifetime = lifetime;
            this.hitSize = hitSize;

            hittable = false;
            absorbable = false;
            reflectable = false;
            keepVelocity = false;
            hitEffect = Fx.hitBulletColor;
            hitColor = swordColor;
            despawnEffect = Fx.none;

            if (kind == CONVERGE) {
                collides = false;
                collidesTiles = false;
                collidesAir = false;
                collidesGround = false;
                hitEffect = Fx.none;
            }
            pierce = kind == NORMAL || kind == RETURN;
            pierceCap = -1;
            pierceBuilding = false;
        }

        @Override
        public void draw(Bullet b) {
            switch (kind) {
                case NORMAL -> drawSword(b.x, b.y, b.rotation(), 24f, 3f, 1f);
                case GREAT -> drawSword(b.x, b.y, b.rotation(), 60f, 8f, 1f);
                case CONVERGE -> drawSword(b.x, b.y, b.rotation(), 16f, 2f, Mathf.clamp(b.fin() * 4f));
                case RETURN -> drawSword(b.x, b.y, b.rotation(), 80f, 10f, b.fout(0.2f));
            }
        }

        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health) {
            boolean alive = entity instanceof Unit && ((Unit) entity).health > 0f && !((Unit) entity).dead;
            super.hitEntity(b, entity, health);
            if (alive && b.data instanceof SwordData) {
                EmpericalTurretBuild src = ((SwordData) b.data).src;
                Unit u = (Unit) entity;
                if (src != null && (u.health <= 0f || u.dead)) src.onKill();
            }
        }

        @Override
        public void removed(Bullet b) {
            super.removed(b);
            if (!(b.data instanceof SwordData)) return;
            SwordData d = (SwordData) b.data;

            if (kind == GREAT) {
                if (d.src != null && d.src.isValid()) d.src.onGreatVanish(b.x, b.y);
            } else if (kind == CONVERGE) {
                areaHit(b.team, d.src, b.x, b.y, d.radius, b.damage, d.slowTime);
                Fx.hitBulletColor.at(b.x, b.y, 0f, swordColor);
            }
        }
    }

    // ===================== SPIRIT (UNIT) =====================
    public static class SpiritType extends UnitType {
        public SpiritType(String name) {
            super(name);
            constructor = UnitEntity::create;
            flying = true;
            lowAltitude = true;
            health = 500f;
            speed = 3.6f;
            accel = 0.08f;
            drag = 0.04f;
            hitSize = 12f;
            rotateSpeed = 20f;
            faceTarget = false;
            useUnitCap = false;
            isEnemy = false;
            playerControllable = false;
            logicControllable = false;
            allowedInPayloads = false;
            engineSize = 0f;
            createWreck = false;
            createScorch = false;
            controller = u -> new SpiritAI();
            aiController = SpiritAI::new;
        }

        @Override
        public void draw(Unit unit) {
            float x = unit.x, y = unit.y;
            Draw.z(Layer.flyingUnit);

            float pulse = 1f + Mathf.absin(Time.time + unit.id, 5f, 0.12f);
            Draw.color(spiritColor, 0.25f);
            Fill.circle(x, y, 13f * pulse);
            Draw.color(spiritColor, 0.6f);
            Fill.circle(x, y, 8f * pulse);
            Draw.color(Color.white);
            Fill.circle(x, y, 4f);

            // tia sét
            Lines.stroke(1.3f);
            Draw.color(Color.white, 0.9f);
            Mathf.rand.setSeed(unit.id * 977L + (long) (Time.time / 4f));
            for (int i = 0; i < 4; i++) {
                float a = Mathf.rand.random(360f);
                float px = x + Angles.trnsx(a, 5f), py = y + Angles.trnsy(a, 5f);
                for (int j = 0; j < 3; j++) {
                    a += Mathf.rand.range(40f);
                    float len = Mathf.rand.random(4f, 8f);
                    float nx = px + Angles.trnsx(a, len), ny = py + Angles.trnsy(a, len);
                    Lines.line(px, py, nx, ny);
                    px = nx;
                    py = ny;
                }
            }

            float sa = unit.rotation;
            drawSword(x + Angles.trnsx(sa, 10f), y + Angles.trnsy(sa, 10f), sa, 20f, 2.4f, 1f);
            Draw.reset();
        }
    }

    public static class SpiritAI extends AIController {
        public EmpericalTurretBuild owner;
        Teamc tgt;
        float retargetTimer, cd1, cd2, cd3, skillLock, atk, comboTimer, haste, lastHealth = -1f, stored;
        int comboLeft;

        boolean dashing, dashSwords;
        float dashLeft, dashDmg, dashDx, dashDy, dashTx, dashTy;
        ObjectSet<Object> dashHit = new ObjectSet<>();

        @Override
        public void updateUnit() {
            if (owner == null || !owner.isValid()) {
                unit.kill();
                return;
            }
            float dt = Time.delta;
            syncHealth();

            cd1 -= dt;
            cd2 -= dt;
            cd3 -= dt;
            skillLock -= dt;
            atk -= dt * (haste > 0f ? 1.5f : 1f);
            haste -= dt;

            if (dashing) {
                updateDash(dt);
                return;
            }

            // tìm mục tiêu gần Emperical nhất trong 125% tầm
            float R = owner.range() * 1.25f;
            if (tgt != null && Units.invalidateTarget(tgt, unit.team, owner.x, owner.y, R)) tgt = null;
            retargetTimer -= dt;
            if (tgt == null && retargetTimer <= 0f) {
                tgt = Units.closestTarget(unit.team, owner.x, owner.y, R, u -> true, b -> true);
                retargetTimer = 10f;
            }

            if (tgt == null) {
                Tmp.v1.trns(Time.time * 0.02f + unit.id, owner.block.size * Vars.tilesize * 1.2f + 20f).add(owner);
                moveTo(Tmp.v1, 6f);
                unit.rotation = Angles.moveToward(unit.rotation, owner.rotation, 10f);
                comboLeft = 0;
                return;
            }

            float dist = unit.dst(tgt) / Vars.tilesize;
            unit.lookAt(tgt);

            // combo của skill 3
            if (comboLeft > 0) {
                moveTo(tgt, SLASH_RANGE * Vars.tilesize * 0.4f);
                comboTimer -= dt;
                if (comboTimer <= 0f) {
                    slash();
                    comboLeft--;
                    comboTimer = 7f;
                    if (comboLeft <= 0) {
                        haste = 90f;   // +50% tốc chém trong 1.5s
                        skillLock = SKILL_LOCK;
                    }
                }
                return;
            }

            // Chỉ được tung kỹ năng mới khi đã nghỉ đủ 1s sau kỹ năng trước.
            // Thứ tự kiểm tra = độ ưu tiên: skill 2 luôn trước skill 1 khi địch nằm trong tầm cả hai.
            // skill 3: 0-4 tile, hồi 8s
            if (skillLock <= 0f && dist <= 4f && cd3 <= 0f) {
                comboLeft = Mathf.random(2, 5);
                comboTimer = 0f;
                cd3 = 480f;
                return;
            }
            // skill 2: 15-25 tile, hồi 25s
            if (skillLock <= 0f && dist >= 15f && dist <= 25f && cd2 <= 0f) {
                startDash(20f, owner.spiritDamage() * 0.6f, true);
                cd2 = 1500f;
                return;
            }
            // skill 1: 20-30 tile, hồi 10s
            if (skillLock <= 0f && dist > 20f && dist < 30f && cd1 <= 0f) {
                startDash(15f, 0f, false);
                cd1 = 600f;
                return;
            }

            if (dist > SLASH_RANGE) {
                moveTo(tgt, SLASH_RANGE * Vars.tilesize * 0.6f);
            } else {
                unit.vel.scl(0.9f);
                if (atk <= 0f) {
                    slash();
                    atk = owner.spiritInterval();
                }
            }
        }

        void startDash(float tiles, float dmg, boolean swords) {
            float ang = unit.angleTo(tgt);
            float len = unit.dst(tgt);
            dashLeft = Math.min(tiles * Vars.tilesize, len);
            dashDx = Angles.trnsx(ang, 1f);
            dashDy = Angles.trnsy(ang, 1f);
            dashDmg = dmg;
            dashSwords = swords;
            dashTx = tgt.x();
            dashTy = tgt.y();
            dashHit.clear();
            dashing = true;
            unit.rotation = ang;
        }

        void updateDash(float dt) {
            float step = Math.min(DASH_SPEED * dt, dashLeft);
            float ox = unit.x, oy = unit.y;
            unit.x += dashDx * step;
            unit.y += dashDy * step;
            unit.vel.setZero();
            dashLeft -= step;
            dashFx.at(unit.x, unit.y, unit.rotation, spiritColor);

            if (dashDmg > 0f) {
                float dealt = lineHit(unit.team, owner, ox, oy, unit.x, unit.y, 2.5f * Vars.tilesize, dashDmg, dashHit);
                lifesteal(dealt * 0.05f);
            }
            if (dashLeft <= 0.001f) {
                dashing = false;
                skillLock = SKILL_LOCK;
                if (dashSwords) {
                    owner.convergeSwords(dashTx, dashTy, Mathf.random(1, 5), owner.spiritDamage() * 0.9f);
                }
            }
        }

        void slash() {
            float ang = tgt != null ? unit.angleTo(tgt) : unit.rotation;
            float cx = unit.x + Angles.trnsx(ang, SLASH_REACH), cy = unit.y + Angles.trnsy(ang, SLASH_REACH);
            float dealt = areaHit(unit.team, owner, cx, cy, SLASH_RADIUS, owner.spiritDamage(), 0f);
            slashFx.at(unit.x, unit.y, ang, spiritColor);
            lifesteal(dealt * 0.05f);   // hồi 5% sát thương gây ra
        }

        /** Hồi máu; phần thừa khi đầy máu được lưu (tối đa 75% máu). */
        void lifesteal(float amount) {
            if (amount <= 0f) return;
            if (unit.health < unit.maxHealth) {
                float use = Math.min(unit.maxHealth - unit.health, amount);
                unit.heal(use);
                amount -= use;
            }
            stored = Math.min(stored + amount, unit.maxHealth * 0.75f);
            lastHealth = unit.health;
        }

        void syncHealth() {
            float hp = owner.spiritMaxHealth();
            if (Math.abs(unit.maxHealth - hp) > 0.01f) {
                float r = unit.maxHealth > 1f ? unit.health / unit.maxHealth : 1f;
                unit.maxHealth = hp;
                unit.health = hp * r;
            }
            if (lastHealth < 0f) lastHealth = unit.health;
            float lost = lastHealth - unit.health;
            if (lost > 0f && stored > 0f) {
                float use = Math.min(lost, stored);
                unit.heal(use);
                stored -= use;
            }
            lastHealth = unit.health;
        }
    }

    // ===================== BUILD =====================
    static class ChargeSword {
        float ox, oy, front, t, time;   // front > 0: đặt trước mặt theo hướng xoay
        boolean great;
    }

    public class EmpericalTurretBuild extends NevixTurretBuild {
        public int ReloadTier;
        public boolean RangeBoosted;
        public int DamageTier;
        public boolean hasIntrinsic;
        public int chargeTier;
        public boolean hasGreatSword;
        public int spiritTier;

        // runtime
        Teamc tgt;
        float retarget, swordCd, intrinsicBuff, sinceLaunch, refreshTimer;
        int swordsFired, spiritPoints;
        Seq<ChargeSword> charging = new Seq<>();

        boolean sweepActive;
        float sweepT, sweepDir, sweepSign = 1f;
        int sweepCount;
        ObjectSet<Object> sweepHit = new ObjectSet<>();

        Seq<Unit> spirits = new Seq<>();
        FloatSeq respawns = new FloatSeq();

        protected boolean owned(Upgrade u) {
            return u != null && isDone(u);
        }

        protected int tierOf(Upgrade a, Upgrade b, Upgrade c) {
            return owned(c) ? 3 : owned(b) ? 2 : owned(a) ? 1 : 0;
        }

        /** Đồng bộ lại theo upgrade đã sở hữu (giống SwirdyTurret). */
        protected void refreshUpgrades() {
            ReloadTier = tierOf(emperical_reload_0, emperical_reload_1, emperical_reload_2);
            RangeBoosted = owned(emperical_range);
            DamageTier = owned(emperical_damage_2) ? 2 : owned(emperical_damage_1) ? 1 : 0;
            hasIntrinsic = owned(emperical_intrinsic);
            chargeTier = owned(emperical_charge_shoot_1) ? 2 : owned(emperical_charge_shoot_0) ? 1 : 0;
            hasGreatSword = owned(emperical_great_sword);
            spiritTier = tierOf(emperical_sword_spirit_0, emperical_sword_spirit_1, emperical_sword_spirit_2);
        }

        // ---------- stat ----------
        @Override
        public float range() {
            return RANGE[RangeBoosted ? 1 : 0];
        }

        float baseDamage() {
            return BASE_DAMAGE * DAMAGE_MULTI[DamageTier];
        }

        float spiritBonus() {
            int extra = Math.max(0, spiritPoints - SPIRIT_COUNT[spiritTier]);
            return 1f + SPIRIT_EXTRA_BONUS * extra;
        }

        public float spiritDamage() {
            return baseDamage() * 0.10f * spiritBonus();
        }

        public float spiritMaxHealth() {
            return maxHealth() * 0.05f * spiritBonus();
        }

        /** Tốc độ đánh của spirit = reload của Emperical, không tính buff Intrinsic. */
        public float spiritInterval() {
            return RELOAD[ReloadTier];
        }

        public void onKill() {
            if (spiritTier > 0 && Mathf.chance(SPIRIT_KILL_CHANCE)) spiritPoints++;
        }

        // ---------- vòng lặp ----------
        @Override
        protected void updateShooting() {
            // tắt cơ chế bắn mặc định của Turret
        }

        @Override
        public void updateTile() {
            super.updateTile();
            float dt = delta();
            refreshTimer += dt;
            if (refreshTimer >= 30f) {
                refreshTimer = 0f;
                refreshUpgrades();
            }
            updateTarget(dt);
            updateAttack(dt);
            updateCharging(dt);
            updateSweep(dt);
            updateSpirits(dt);
        }

        void updateTarget(float dt) {
            if (tgt != null && Units.invalidateTarget(tgt, team, x, y, range())) tgt = null;
            retarget -= dt;
            if (retarget <= 0f) {
                retarget = 15f;
                if (tgt == null) tgt = Units.closestTarget(team, x, y, range(), u -> true, b -> true);
            }
            if (tgt != null) {
                rotation = Angles.moveToward(rotation, angleTo(tgt.x(), tgt.y()), rotateSpeed * dt);
            }
        }

        // ---------- đánh thường + intrinsic ----------
        void updateAttack(float dt) {
            // 5s không phóng thanh kiếm nào -> buff về 0%
            sinceLaunch += dt;
            if (sinceLaunch >= INTRINSIC_RESET_TIME) intrinsicBuff = 0f;

            swordCd = Math.min(swordCd + dt * rate(), RELOAD[ReloadTier]);
            if (tgt != null && charging.isEmpty() && hasAmmo() && swordCd >= RELOAD[ReloadTier]) {
                swordCd = 0f;
                startVolley();
            }
        }

        /** Tốc độ phóng = hiệu suất điện * (1 + buff Intrinsic). */
        float rate() {
            return baseReloadSpeed() * (1f + (hasIntrinsic ? intrinsicBuff : 0f));
        }

        /** Đủ điều kiện hoạt động: có điện và còn đạn. */
        boolean operational() {
            return potentialEfficiency > 0.001f && hasAmmo();
        }

        @Override
        public boolean shouldConsume() {
            // Turret mặc định chỉ tiêu điện khi đang bắn theo cơ chế gốc; Emperical tự bắn nên phải khai báo lại.
            return super.shouldConsume() || tgt != null || !charging.isEmpty();
        }

        void startVolley() {
            boolean scatter = hasIntrinsic && intrinsicBuff > 1f;   // tốc bắn > +100%
            int count = scatter ? INTRINSIC_VOLLEY : 1;
            for (int i = 0; i < count; i++) {
                ChargeSword c = new ChargeSword();
                if (scatter) {
                    float a = Mathf.random(360f);
                    float d = size * Vars.tilesize / 2f + Mathf.random(14f, 44f);
                    c.ox = Angles.trnsx(a, d);
                    c.oy = Angles.trnsy(a, d);
                } else {
                    c.front = size * Vars.tilesize / 2f + 12f;
                }
                c.great = chargeTier > 0 && Mathf.chance(CHARGE_CHANCE[chargeTier]);
                c.time = NORMAL_CHARGE * (c.great ? GREAT_CHARGE_MUL : 1f);
                charging.add(c);
            }
        }

        float cx(ChargeSword c) {
            return x + (c.front > 0f ? Angles.trnsx(rotation, c.front) : c.ox);
        }

        float cy(ChargeSword c) {
            return y + (c.front > 0f ? Angles.trnsy(rotation, c.front) : c.oy);
        }

        void updateCharging(float dt) {
            float rate = rate();   // điện yếu / mất điện -> tụ lực chậm / đứng yên
            for (int i = charging.size - 1; i >= 0; i--) {
                ChargeSword c = charging.get(i);
                c.t += dt * rate;
                if (c.t >= c.time) {
                    launch(c);
                    charging.remove(i);
                }
            }
            if (hasGreatSword && swordsFired >= GREAT_SWORD_NEED && !sweepActive) {
                swordsFired -= GREAT_SWORD_NEED;
                startSweep();
            }
        }

        void fire(SwordBullet type, float sx, float sy, float angle, float damage, float dist, SwordData data) {
            float vs = Math.max(0.01f, dist / (type.speed * type.lifetime));
            type.create(this, team, sx, sy, angle, damage, vs, 1f, data);
        }

        void launch(ChargeSword c) {
            if (!hasAmmo()) return;   // hết đạn: thanh kiếm tan biến
            useAmmo();                // 2 thanh = 1 item (ammoMultiplier 2)

            float sx = cx(c), sy = cy(c);
            float ang = tgt != null ? Angles.angle(sx, sy, tgt.x(), tgt.y()) : rotation;
            float dist = range() * 1.1f;

            if (c.great) {
                fire(greatSword, sx, sy, ang, baseDamage() * CHARGE_DAMAGE[chargeTier], dist, new SwordData(this));
            } else {
                fire(normalSword, sx, sy, ang, baseDamage(), dist, new SwordData(this));
            }
            // Đại kiếm chỉ tính 1 thanh; các thanh tách ra / chém ngược không gọi launch() nên không tính.
            swordsFired++;
            sinceLaunch = 0f;
            if (hasIntrinsic) intrinsicBuff = Math.min(INTRINSIC_MAX, intrinsicBuff + INTRINSIC_STEP);
            Fx.hitBulletColor.at(sx, sy, ang, swordColor);
        }

        // ---------- charge shoot: tách -> phóng vào -> hợp nhất -> chém ngược ----------
        public void onGreatVanish(float px, float py) {
            int n = Mathf.random(3, 10);
            float arrive = convergeSwords(px, py, n, baseDamage() * SPLIT_DAMAGE);

            Time.run(arrive + 8f, () -> {
                if (!isValid()) return;
                float ox = x * 2f - px, oy = y * 2f - py;      // toạ độ đối diện Emperical
                float a = Angles.angle(ox, oy, x, y);
                mergeFx.at(ox, oy, a);
                Time.run(25f, () -> {
                    if (!isValid()) return;
                    float d = Mathf.dst(ox, oy, x, y);
                    fire(returnSword, ox, oy, a, baseDamage() * RETURN_DAMAGE * n, d, new SwordData(this));
                });
            });
        }

        /** Tạo n kiếm quanh (px,py), lần lượt phóng vào đó. Trả về thời gian (tick) tới khi kiếm cuối chạm. */
        public float convergeSwords(float px, float py, int n, float dmg) {
            for (int i = 0; i < n; i++) {
                float ang = Mathf.random(360f);
                float dist = Mathf.random(5f, 9f) * Vars.tilesize;
                float sx = px + Angles.trnsx(ang, dist), sy = py + Angles.trnsy(ang, dist);
                spawnFx.at(sx, sy);
                Time.run(i * 5f + 8f, () -> {
                    if (!isValid()) return;
                    fire(convergeSword, sx, sy, Angles.angle(sx, sy, px, py), dmg, dist, new SwordData(this));
                });
            }
            return n * 5f + 8f + convergeSword.lifetime;
        }

        // ---------- great sword: quét ----------
        void startSweep() {
            sweepActive = true;
            sweepT = 0f;
            sweepCount = 1;
            sweepSign = 1f;
            sweepDir = tgt != null ? angleTo(tgt.x(), tgt.y()) : rotation;
            sweepHit.clear();
        }

        void updateSweep(float dt) {
            if (!sweepActive) return;
            float R = range() * SWEEP_RANGE_MUL;
            float dmg = baseDamage() * GREAT_SWEEP_DAMAGE;

            sweepT += dt;
            float p1 = Math.min(1f, sweepT / SWEEP_TIME);
            float o0 = sweepSign * -SWEEP_ARC;
            float oc = sweepSign * (-SWEEP_ARC + 2f * SWEEP_ARC * p1);
            float lo = Math.min(o0, oc), hi = Math.max(o0, oc);

            Units.nearbyEnemies(team, x - R, y - R, R * 2f, R * 2f, u -> {
                if (sweepHit.contains(u) || !u.within(this, R + u.hitSize / 2f)) return;
                float s = Mathf.mod(angleTo(u.x, u.y) - sweepDir + 180f, 360f) - 180f;
                if (s >= lo && s <= hi) {
                    sweepHit.add(u);
                    hurt(this, u, dmg);
                }
            });
            Vars.indexer.allBuildings(x, y, R, b -> {
                if (b.team == team || b.team == Team.derelict || sweepHit.contains(b)) return;
                float s = Mathf.mod(angleTo(b.x, b.y) - sweepDir + 180f, 360f) - 180f;
                if (s >= lo && s <= hi) {
                    sweepHit.add(b);
                    b.damage(team, dmg);
                }
            });

            if (p1 >= 1f) {
                // 50% quét lại, rồi 50% tiếp, tối đa 5 lần liên tục
                if (sweepCount < SWEEP_MAX && Mathf.chance(0.5f)) {
                    sweepCount++;
                    sweepSign = -sweepSign;
                    sweepT = 0f;
                    sweepHit.clear();
                } else {
                    sweepActive = false;
                }
            }
        }

        // ---------- Sword Spirit ----------
        void updateSpirits(float dt) {
            // Chưa đủ điều kiện hoạt động: Spirit chết, không hồi sinh (đồng hồ hồi sinh đứng yên) cho tới khi hoạt động lại.
            if (!operational()) {
                for (Unit u : spirits) {
                    if (u.isValid()) {
                        u.kill();
                        respawns.add(SPIRIT_RESPAWN);
                    }
                }
                spirits.clear();
                return;
            }

            int before = spirits.size;
            spirits.removeAll(u -> !u.isValid());
            for (int i = before - spirits.size; i > 0; i--) respawns.add(SPIRIT_RESPAWN);

            int waiting = 0;
            for (int i = respawns.size - 1; i >= 0; i--) {
                float t = respawns.get(i) - dt;
                if (t <= 0f) respawns.removeIndex(i);
                else {
                    respawns.set(i, t);
                    waiting++;
                }
            }

            // Hồi sinh / spawn không tốn điểm: chỉ cần còn slot trống mà không đang chờ hồi sinh.
            int free = Math.min(spiritPoints, SPIRIT_COUNT[spiritTier]) - spirits.size;
            for (int i = free - waiting; i > 0; i--) spawnSpirit();
        }

        void spawnSpirit() {
            Unit u = swordSpirit.create(team);
            u.set(x + Mathf.range(12f), y + Mathf.range(12f));
            u.rotation = rotation;
            u.maxHealth = spiritMaxHealth();
            u.health = u.maxHealth;
            u.add();
            if (u.controller() instanceof SpiritAI) ((SpiritAI) u.controller()).owner = this;
            spirits.add(u);
            spawnFx.at(u.x, u.y);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            for (Unit u : spirits) if (u.isValid()) u.kill();
        }

        // ---------- vẽ ----------
        @Override
        public void draw() {
            super.draw();
            Draw.z(Layer.effect);

            for (ChargeSword c : charging) {
                float px = cx(c), py = cy(c);
                float p = Mathf.clamp(c.t / c.time);
                float a = tgt != null ? Angles.angle(px, py, tgt.x(), tgt.y()) : rotation;
                float len = (c.great ? 60f : 24f) * Mathf.lerp(0.5f, 1f, p);
                float wid = (c.great ? 8f : 3f) * Mathf.lerp(0.5f, 1f, p);

                Draw.color(swordColor, 0.3f + 0.5f * p);
                Lines.stroke(1.5f);
                Lines.circle(px, py, (1f - p) * (c.great ? 36f : 18f) + 4f);
                drawSword(px, py, a, len, wid, 0.35f + 0.65f * p);
            }

            if (sweepActive) {
                float R = range() * SWEEP_RANGE_MUL;
                float p = Mathf.clamp(sweepT / SWEEP_TIME);
                float from = sweepDir + sweepSign * -SWEEP_ARC;
                float cur = sweepDir + sweepSign * (-SWEEP_ARC + 2f * SWEEP_ARC * p);

                Draw.color(swordColor, 0.18f);
                Fill.arc(x, y, R, Math.abs(cur - from) / 360f, Math.min(from, cur));
                drawSword(x, y, cur, R, 16f, 0.9f);
            }
            Draw.reset();
        }

        // ---------- lưu / tải ----------
        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(swordsFired);
            write.i(spiritPoints);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            swordsFired = read.i();
            spiritPoints = read.i();
        }
    }
}
