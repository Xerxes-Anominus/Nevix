package nevix.world.block.turret;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.struct.ObjectSet;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Teamc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;

import static mindustry.Vars.indexer;
import static mindustry.Vars.tilesize;

public class SwirdyTurret extends NevixTurret {
    public static final int skillCost = 50;

    /** Tầm đánh xa mặc định (tile) và tầm cận chiến (tile). */
    public float rangedTiles = 23f, meleeTiles = 9f;

    public BulletType wave, finalWave, knockWave;

    // ───────────────────── Vẽ kiếm khí (giống Tinh Quang Trảm của Honivery) ─────────────────────

    private static final Color HONIVERY_COLOR = Color.valueOf("D0C2FF");
    private static final Color STAR_CYAN = Color.valueOf("7CE8FF");
    private static final Color STAR_GOLD = Color.valueOf("FFE07D");
    private static final Color VOID_PURPLE = Color.valueOf("6E3AFF");

    /** Lưỡi liềm: viền ngoài đậm, mờ dần vào trong. */
    public static void drawStarCrescent(float x, float y, float ang, float rad, float half, float thick, Color col, float alpha){
        Tmp.c1.set(col).a(alpha);
        Tmp.c2.set(col).a(0f);
        float cOut = Tmp.c1.toFloatBits();
        float cIn = Tmp.c2.toFloatBits();
        int segs = 16;
        for(int i = 0; i < segs; i++){
            float f1 = (float)i / segs;
            float f2 = (float)(i + 1) / segs;
            float a1 = ang - half + half * 2f * f1;
            float a2 = ang - half + half * 2f * f2;
            float r1 = rad * (1f - thick * (float)Math.sin(Math.PI * f1));
            float r2 = rad * (1f - thick * (float)Math.sin(Math.PI * f2));
            Fill.quad(
                x + Angles.trnsx(a1, r1), y + Angles.trnsy(a1, r1), cIn,
                x + Angles.trnsx(a1, rad), y + Angles.trnsy(a1, rad), cOut,
                x + Angles.trnsx(a2, rad), y + Angles.trnsy(a2, rad), cOut,
                x + Angles.trnsx(a2, r2), y + Angles.trnsy(a2, r2), cIn
            );
        }
    }

    /**
     * Một đường kiếm khí hoàn chỉnh: (tuỳ chọn) vệt rách chữ X, lưỡi liềm 3 lớp
     * tím vũ trụ → xanh tinh quang → lõi trắng, và bụi sao dọc cung chém.
     * (x, y) là tâm của cung tròn.
     */
    public static void drawSlashLayers(float x, float y, float ang, float curR, float half, float fade, float time, float scale, boolean tear){
        if(fade <= 0.01f) return;
        Draw.blend(Blending.additive);

        if(tear){
            for(int d = -1; d <= 1; d += 2){
                float slashAng = ang + d * 34f;
                float len = curR * 0.92f;
                float cx = x + Angles.trnsx(ang, curR * 0.42f);
                float cy = y + Angles.trnsy(ang, curR * 0.42f);
                Draw.color(VOID_PURPLE, 0.65f * fade);
                Drawf.tri(cx, cy, 14f * scale * fade, len * 0.55f, slashAng);
                Drawf.tri(cx, cy, 14f * scale * fade, len * 0.55f, slashAng + 180f);
                Draw.color(Color.white, 0.95f * fade);
                Drawf.tri(cx, cy, 4.5f * scale * fade, len * 0.52f, slashAng);
                Drawf.tri(cx, cy, 4.5f * scale * fade, len * 0.52f, slashAng + 180f);
            }
        }

        drawStarCrescent(x, y, ang, curR, half, 0.46f, VOID_PURPLE, 0.75f * fade);
        drawStarCrescent(x, y, ang, curR * 0.96f, half * 0.92f, 0.34f, STAR_CYAN, 0.85f * fade);
        drawStarCrescent(x, y, ang, curR * 0.93f, half * 0.85f, 0.18f, Color.white, 0.98f * fade);

        for(int q = 0; q < 10; q++){
            float a = ang - half + (half * 2f) * (q / 9f);
            float rr = curR * (0.85f + 0.18f * Mathf.sin(time + q));
            float px = x + Angles.trnsx(a, rr);
            float py = y + Angles.trnsy(a, rr);
            Draw.color(q % 2 == 0 ? STAR_GOLD : Color.white, fade);
            Drawf.tri(px, py, 3.2f * scale * fade, 14f * scale * fade, a);
            Drawf.tri(px, py, 3.2f * scale * fade, 14f * scale * fade, a + 180f);
        }

        Draw.blend();
        Draw.reset();
    }

    // ───────────────────────── Effects ─────────────────────────

    /**
     * Nhát vung kiếm quét như Tinh Quang Trảm: cung mở rộng ra ngoài, tâm cung quét từ bên này sang bên kia.
     * data = float[]{dir(+1/-1), range, halfAngle}
     */
    public static final Effect slashFx = new Effect(22f, 400f, e -> {
        if(!(e.data instanceof float[])) return;
        float[] d = (float[])e.data;
        float dir = d[0], range = d[1], half = d[2];
        float fin = e.fin();
        float ease = Interp.pow3Out.apply(fin);
        float fade = 1f - Interp.pow2In.apply(fin);
        float curR = range * 0.17f + range * 0.83f * ease;
        float ang = e.rotation + dir * half * 0.8f * (1f - 2f * Interp.pow2Out.apply(fin));
        float scale = Mathf.clamp(range / 155f, 0.45f, 1.5f);

        Draw.z(Layer.bullet + 3f);
        drawSlashLayers(e.x, e.y, ang, curR, half, fade, e.time, scale, true);
        Drawf.light(e.x + Angles.trnsx(ang, curR * 0.6f), e.y + Angles.trnsy(ang, curR * 0.6f), curR * 1.4f, HONIVERY_COLOR, 0.85f * fade);
    });

    /** Đường kiếm thẳng (3 nhát 3000% của cận chiến). data = Float length */
    public static final Effect lineSlashFx = new Effect(18f, 600f, e -> {
        float len = e.data instanceof Float ? (Float)e.data : 100f;
        float fade = 1f - Interp.pow2In.apply(e.fin());
        float cx = e.x + Angles.trnsx(e.rotation, len / 2f);
        float cy = e.y + Angles.trnsy(e.rotation, len / 2f);

        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);

        Draw.color(VOID_PURPLE, 0.65f * fade);
        Drawf.tri(cx, cy, 14f * fade, len * 0.5f, e.rotation);
        Drawf.tri(cx, cy, 14f * fade, len * 0.5f, e.rotation + 180f);
        Draw.color(STAR_CYAN, 0.85f * fade);
        Drawf.tri(cx, cy, 9f * fade, len * 0.5f, e.rotation);
        Drawf.tri(cx, cy, 9f * fade, len * 0.5f, e.rotation + 180f);
        Draw.color(Color.white, 0.95f * fade);
        Drawf.tri(cx, cy, 4.5f * fade, len * 0.48f, e.rotation);
        Drawf.tri(cx, cy, 4.5f * fade, len * 0.48f, e.rotation + 180f);

        for(int q = 0; q < 10; q++){
            float f = q / 9f;
            float px = e.x + Angles.trnsx(e.rotation, len * f);
            float py = e.y + Angles.trnsy(e.rotation, len * f);
            Draw.color(q % 2 == 0 ? STAR_GOLD : Color.white, fade);
            Drawf.tri(px, py, 3.2f * fade, 14f * fade, e.rotation + 90f);
            Drawf.tri(px, py, 3.2f * fade, 14f * fade, e.rotation + 270f);
        }

        Draw.blend();
        Draw.reset();
        Drawf.light(cx, cy, len * 0.7f, HONIVERY_COLOR, 0.8f * fade);
    });

    /** Tụ lực: ấn tròn tím co lại + sao 4 cánh xoay. */
    public static final Effect chargeFx = new Effect(45f, 200f, e -> {
        float fr = e.fin(), inv = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);

        Draw.color(VOID_PURPLE, 0.7f * fr);
        Lines.stroke(2.2f);
        Lines.circle(e.x, e.y, 14f + 26f * inv);

        for(int k = 0; k < 4; k++){
            float a = e.rotation + k * 90f + fr * 180f;
            float len = (k == 0 ? 34f : 18f) * (0.4f + 0.8f * fr);
            Draw.color(k == 0 ? Color.white : HONIVERY_COLOR, 0.85f * fr);
            Drawf.tri(e.x, e.y, 3.8f * fr, len, a);
        }

        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 55f * fr, HONIVERY_COLOR, 0.8f * fr);
    });

    /** Bùng nổ sao 4 cánh khi chém trúng. */
    public static final Effect starHitFx = new Effect(26f, e -> {
        float fin = e.fin(), fout = e.fout();
        Draw.blend(Blending.additive);
        Draw.color(HONIVERY_COLOR, fout);
        Lines.stroke(2.5f * fout);
        Lines.circle(e.x, e.y, 6f + Interp.pow3Out.apply(fin) * 28f);

        for(int i = 0; i < 4; i++){
            float a = e.rotation + i * 90f + fin * 35f;
            float len = (i % 2 == 0 ? 32f : 18f) * Interp.pow2Out.apply(fin);
            Draw.color(i % 2 == 0 ? Color.white : STAR_CYAN, fout);
            Drawf.tri(e.x, e.y, 4.5f * fout, len, a);
            Drawf.tri(e.x, e.y, 4.5f * fout, len * 0.45f, a + 180f);
        }
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 42f, HONIVERY_COLOR, 0.8f * fout);
    });

    // ───────────────────────── Bullet ─────────────────────────

    /** Luồng kiếm khí lưỡi liềm 3 lớp như Honivery, xuyên không giới hạn (cả địch lẫn công trình). */
    public static class SwirdyWave extends BasicBulletType {
        public float arcSize;
        public float half = 45f;
        public boolean tear;

        public SwirdyWave(float speed, float damage, float arcSize, float lifetime, boolean tear){
            super(speed, damage);
            this.arcSize = arcSize;
            this.tear = tear;
            this.lifetime = lifetime;

            pierce = true;
            pierceCap = -1;
            pierceBuilding = true;
            hittable = false;
            absorbable = false;
            reflectable = false;
            hitSize = arcSize * 2.6f;
            drawSize = arcSize * 8f;
            layer = Layer.bullet + 3f;
            knockback = 1f;
            hitColor = STAR_CYAN;
            hitEffect = starHitFx;
            despawnEffect = Fx.none;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            trailLength = 0;
        }

        @Override
        public void draw(Bullet b){
            float rad = arcSize * 2f;
            float fade = Mathf.clamp(b.fout() * 5f);
            // tâm cung nằm phía sau đạn để đỉnh lưỡi liềm trùng vị trí đạn
            Tmp.v1.trns(b.rotation(), -rad);
            float cx = b.x + Tmp.v1.x, cy = b.y + Tmp.v1.y;
            drawSlashLayers(cx, cy, b.rotation(), rad, half, fade, b.time, arcSize / 20f, tear);
            Drawf.light(b.x, b.y, rad * 1.4f, HONIVERY_COLOR, 0.6f * fade);
        }

        @Override
        public void hitEntity(Bullet b, Hitboxc entity, float health){
            super.hitEntity(b, entity, health);
            if(b.owner instanceof SwirdyTurretBuild){
                ((SwirdyTurretBuild)b.owner).gainPoint();
            }
        }

        @Override
        public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
            super.hitTile(b, build, x, y, initialHealth, direct);
            if(direct && b.owner instanceof SwirdyTurretBuild){
                ((SwirdyTurretBuild)b.owner).gainPoint();
            }
        }
    }

    // ───────────────────────── Upgrades & stats ─────────────────────────

    public static Upgrade
    swirdy_melee,
    swirdy_melee_reload0,
    swirdy_melee_reload1,
    swirdy_melee_reload2,
    swirdy_melee_tier0,
    swirdy_melee_tier1,
    swirdy_melee_tier2,
    swirdy_range_tier0,
    swirdy_range_tier1,
    swirdy_range_tier2,
    swirdy_reload_tier0,
    swirdy_reload_tier1,
    swirdy_reload_tier2,
    swirdy_tier0,
    swirdy_tier1,
    swirdy_tier2,
    swirdy_melee_passive_skill,
    swirdy_passive_skill;

    public final float[]
    melee_reload = {90f, 75f, 60f},
    melee_damage = {95f, 160f, 310f},
    melee_bonus_slash_chance = {0f, 0.2f, 0.45f},
    range_boost = {0, 16f, 40f},
    reload_new = {80f, 65f, 50f},
    damage = {35.5f, 54f, 82.5f};

    public SwirdyTurret(String name){
        super(name);

        health = 1150;
        size = 2;
        reload = 60f;
        rotateSpeed = 8f;
        shootCone = 20f;
        range = rangedTiles * tilesize;

        wave = new SwirdyWave(9f, 0f, 12f, 38f, false);
        wave.knockback = 1f;

        finalWave = new SwirdyWave(10f, 0f, 34f, 40f, true);
        finalWave.knockback = 2f;

        // Không gây sát thương nhưng đẩy lùi rất mạnh.
        knockWave = new SwirdyWave(11f, 0f, 22f, 30f, false);
        knockWave.hitEffect = Fx.none; // luồng đẩy lùi không gây sát thương nên không nổ sao
        knockWave.knockback = 8f;
        knockWave.impact = true;
        knockWave.buildingDamageMultiplier = 0f;

        upgradeBranch();
    }

    @Override
    public void init(){
        super.init();
        wave.init();
        finalWave.init();
        knockWave.init();
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("swirdy-skill", (SwirdyTurretBuild e) -> new Bar(
            () -> Core.bundle.get("bar.swirdy-skill") + e.points + "/" + skillCost,
            () -> Pal.lancerLaser,
            () -> e.points / (float)skillCost
        ));
    }

    public void upgradeBranch(){
        swirdy_melee = addUpgrade("swirdy-melee", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeStateUnlock = true, (SwirdyTurretBuild build) -> build.meleeStateUnlock = false);
        swirdy_melee_reload0 = addUpgrade("swirdy-melee-reload-0", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeReloadTier++, (SwirdyTurretBuild build) -> build.meleeReloadTier = 0).requires(swirdy_melee);
        swirdy_melee_reload1 = addUpgrade("swirdy-melee-reload-1", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeReloadTier++, (SwirdyTurretBuild build) -> build.meleeReloadTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_reload0);
        swirdy_melee_reload2 = addUpgrade("swirdy-melee-reload-2", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeReloadTier++, (SwirdyTurretBuild build) -> build.meleeReloadTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_reload1);
        swirdy_melee_tier0 = addUpgrade("swirdy-melee-tier-0", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeTier++, (SwirdyTurretBuild build) -> build.meleeTier = 0).requires(swirdy_melee);
        swirdy_melee_tier1 = addUpgrade("swirdy-melee-tier-1", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeTier++, (SwirdyTurretBuild build) -> build.meleeTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_tier0);
        swirdy_melee_tier2 = addUpgrade("swirdy-melee-tier-2", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeTier++, (SwirdyTurretBuild build) -> build.meleeTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_tier1);
        swirdy_range_tier0 = addUpgrade("swirdy-range-tier-0", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.rangeTier++, (SwirdyTurretBuild build) -> build.rangeTier = 0);
        swirdy_range_tier1 = addUpgrade("swirdy-range-tier-1", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.rangeTier++, (SwirdyTurretBuild build) -> build.rangeTier = 0).levelAfter(swirdy_range_tier0);
        swirdy_range_tier2 = addUpgrade("swirdy-range-tier-2", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.rangeTier++, (SwirdyTurretBuild build) -> build.rangeTier = 0).levelAfter(swirdy_range_tier1);
        swirdy_reload_tier0 = addUpgrade("swirdy-reload-tier-0", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.reloadTier++, (SwirdyTurretBuild build) -> build.reloadTier = 0);
        swirdy_reload_tier1 = addUpgrade("swirdy-reload-tier-1", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.reloadTier++, (SwirdyTurretBuild build) -> build.reloadTier = 0).levelAfter(swirdy_reload_tier0);
        swirdy_reload_tier2 = addUpgrade("swirdy-reload-tier-2", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.reloadTier++, (SwirdyTurretBuild build) -> build.reloadTier = 0).levelAfter(swirdy_reload_tier1);
        swirdy_tier0 = addUpgrade("swirdy-tier-0", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.Tier++, (SwirdyTurretBuild build) -> build.Tier = 0);
        swirdy_tier1 = addUpgrade("swirdy-tier-1", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.Tier++, (SwirdyTurretBuild build) -> build.Tier = 0).levelAfter(swirdy_tier0);
        swirdy_tier2 = addUpgrade("swirdy-tier-2", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.Tier++, (SwirdyTurretBuild build) -> build.Tier = 0).levelAfter(swirdy_tier1);
        swirdy_melee_passive_skill = addUpgrade("swirdy-melee-passive-skill", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.meleeSkillUnlock = true, (SwirdyTurretBuild build) -> build.meleeSkillUnlock = false).requires(swirdy_melee, swirdy_melee_tier0);
        swirdy_passive_skill = addUpgrade("swirdy-passive-skill", "", ItemStack.with(Items.copper, 1), (SwirdyTurretBuild build) -> build.skillUnlock = true, (SwirdyTurretBuild build) -> build.skillUnlock = false);
    }

    // ───────────────────────── Build ─────────────────────────

    public class SwirdyTurretBuild extends NevixTurretBuild {
        public boolean meleeStateUnlock;
        public boolean meleeState;

        public int meleeReloadTier;
        public int meleeTier;

        public int rangeTier;
        public int reloadTier;
        public int Tier;

        public boolean meleeSkillUnlock;
        public boolean skillUnlock;

        /** Điểm kĩ năng (0..50). */
        public int points;

        protected float atkCounter, modeTimer, refreshTimer, slashTimer, skillTimer, skillBase;
        protected int slashQueue, slashChain, skillStage, skillCount;
        protected boolean skillActive, skillMelee;
        protected final ObjectSet<Object> skillHit = new ObjectSet<>();

        public float meleeRange(){
            return meleeTiles * tilesize;
        }

        // ── upgrade ──

        /** ĐIỂM CẦN SỬA DUY NHẤT: đổi thành hàm kiểm tra upgrade đã sở hữu trong NevixTurret. */
        protected boolean owned(Upgrade u){
            return u != null && isDone(u);
        }

        protected int tierOf(Upgrade a, Upgrade b, Upgrade c){
            return owned(c) ? 2 : owned(b) ? 1 : 0;
        }

        protected void refreshUpgrades(){
            meleeStateUnlock = owned(swirdy_melee);
            meleeSkillUnlock = owned(swirdy_melee_passive_skill);
            skillUnlock = owned(swirdy_passive_skill);

            meleeReloadTier = tierOf(swirdy_melee_reload0, swirdy_melee_reload1, swirdy_melee_reload2);
            meleeTier = tierOf(swirdy_melee_tier0, swirdy_melee_tier1, swirdy_melee_tier2);
            rangeTier = tierOf(swirdy_range_tier0, swirdy_range_tier1, swirdy_range_tier2);
            reloadTier = tierOf(swirdy_reload_tier0, swirdy_reload_tier1, swirdy_reload_tier2);
            Tier = tierOf(swirdy_tier0, swirdy_tier1, swirdy_tier2);
        }

        // ── ammo stubs (turret không dùng đạn) ──

        @Override
        public boolean hasAmmo(){
            return true;
        }

        @Override
        public BulletType peekAmmo(){
            return wave; // targetPosition() dùng speed của nó để dự đoán
        }

        @Override
        public BulletType useAmmo(){
            return wave;
        }

        @Override
        public float range(){
            return meleeState ? meleeRange() : rangedTiles * tilesize + range_boost[rangeTier];
        }

        protected float curReload(){
            return meleeState ? melee_reload[meleeReloadTier] : reload_new[reloadTier];
        }

        // ── main loop ──

        @Override
        public void updateTile(){
            refreshTimer += Time.delta;
            if(refreshTimer >= 30f){
                refreshTimer = 0f;
                refreshUpgrades();
            }

            float dt = delta() * efficiency;

            if(!skillActive) updateMode();
            if(atkCounter < 10000f) atkCounter += dt;

            super.updateTile();

            updateSlashQueue(dt);
            updateSkill(dt);
        }

        /** Tự chuyển giữa đánh xa / cận chiến. */
        protected void updateMode(){
            modeTimer += Time.delta;
            if(modeTimer < 6f) return;
            modeTimer = 0f;

            boolean was = meleeState;
            Teamc t = null;
            if(meleeStateUnlock){
                t = Units.closestTarget(team, x, y, meleeRange(),
                    u -> u.checkTarget(targetAir, targetGround), b -> targetGround);
            }
            meleeState = t != null;

            if(meleeState && (target == null || !target.within(this, meleeRange() + 8f))){
                target = t;
            }
            if(was != meleeState){
                atkCounter = Math.min(atkCounter, curReload());
            }
        }

        // ── attacks ──

        @Override
        protected void updateShooting(){
            if(skillActive || target == null || atkCounter < curReload()) return;

            if(meleeState){
                if(slashQueue > 0 || !target.within(this, meleeRange() + 16f)) return;
                atkCounter = 0f;
                slashQueue = 1;
                slashChain = 0;
                slashTimer = 0f;
            }else{
                atkCounter = 0f;
                float ang = angleTo(target);
                fireWave(wave, ang, damage[Tier]);
                swingFx(ang, 56f, 30f);
            }
        }

        protected void updateSlashQueue(float dt){
            if(slashQueue <= 0 || skillActive) return;
            slashTimer -= dt;
            if(slashTimer > 0f) return;

            if(target == null){
                slashQueue = 0;
                return;
            }

            coneSlash(angleTo(target), melee_damage[meleeTier]);
            slashQueue--;

            // Nhát bổ sung: cùng tỉ lệ, có thể nối tiếp (tối đa 10 nhát bổ sung)
            if(slashChain < 10 && Mathf.chance(melee_bonus_slash_chance[meleeTier])){
                slashQueue++;
                slashChain++;
            }
            slashTimer = 9f;
        }

        /** Vung kiếm cận chiến: hướng trái→phải hoặc phải→trái ngẫu nhiên. */
        protected void coneSlash(float ang, float dmg){
            swingFx(ang, meleeRange(), 30f);
            coneDamage(ang, 55f, meleeRange(), dmg);
        }

        protected void swingFx(float ang, float range, float half){
            slashFx.at(x, y, ang, Color.white, new float[]{Mathf.randomSign(), range, half});
        }

        protected void fireWave(BulletType type, float ang, float dmg){
            Tmp.v1.trns(ang, size * tilesize / 2f);
            type.create(this, team, x + Tmp.v1.x, y + Tmp.v1.y, ang, dmg, 1f, 1f, null);
        }

        // ── damage helpers ──

        protected void coneDamage(float ang, float half, float rng, float dmg){
            Units.nearbyEnemies(team, x - rng, y - rng, rng * 2f, rng * 2f, u -> {
                if(!u.isValid() || !u.checkTarget(targetAir, targetGround)) return;
                if(dst(u) - u.hitSize / 2f > rng) return;
                if(!Angles.within(angleTo(u), ang, half)) return;
                hitTarget(u.x, u.y, ang, () -> u.damage(dmg));
            });

            if(targetGround){
                indexer.allBuildings(x, y, rng, b -> {
                    if(b.team == team || b.team == mindustry.game.Team.derelict) return;
                    if(!Angles.within(angleTo(b), ang, half)) return;
                    hitTarget(b.x, b.y, ang, () -> b.damage(team, dmg));
                });
            }
        }

        protected void lineDamage(float ang, float len, float width, float dmg, boolean reduceRepeat){
            float dx = Mathf.cosDeg(ang), dy = Mathf.sinDeg(ang);

            Units.nearbyEnemies(team, x - len, y - len, len * 2f, len * 2f, u -> {
                if(!u.isValid() || !u.checkTarget(targetAir, targetGround)) return;
                if(!nearLine(u.x, u.y, dx, dy, len, width + u.hitSize / 2f)) return;
                float d = dmg;
                if(reduceRepeat && !skillHit.add(u)) d *= 0.5f; // trúng >1 đường: các nhát sau -50%
                float fd = d;
                hitTarget(u.x, u.y, ang, () -> u.damage(fd));
            });

            if(targetGround){
                indexer.allBuildings(x, y, len, b -> {
                    if(b.team == team || b.team == mindustry.game.Team.derelict) return;
                    if(!nearLine(b.x, b.y, dx, dy, len, width + b.block.size * tilesize / 2f)) return;
                    float d = dmg;
                    if(reduceRepeat && !skillHit.add(b)) d *= 0.5f;
                    float fd = d;
                    hitTarget(b.x, b.y, ang, () -> b.damage(team, fd));
                });
            }
        }

        protected boolean nearLine(float px, float py, float dx, float dy, float len, float rad){
            float along = Mathf.clamp((px - x) * dx + (py - y) * dy, 0f, len);
            float cx = x + dx * along, cy = y + dy * along;
            return Mathf.dst2(px, py, cx, cy) <= rad * rad;
        }

        protected void hitTarget(float hx, float hy, float ang, Runnable dealDamage){
            dealDamage.run();
            starHitFx.at(hx, hy, ang, HONIVERY_COLOR);
            gainPoint();
        }

        public void gainPoint(){
            if(skillActive || !(skillUnlock || meleeSkillUnlock)) return;
            points = Math.min(points + 1, skillCost);
        }

        // ── skills ──

        protected void updateSkill(float dt){
            if(!skillActive){
                if(points >= skillCost && target != null){
                    if(meleeState && meleeSkillUnlock) startSkill(true);
                    else if(!meleeState && skillUnlock) startSkill(false);
                }
                return;
            }

            skillTimer -= dt;
            if(skillTimer > 0f) return;

            float ang = target != null ? angleTo(target) : skillBase;
            skillBase = ang;

            if(skillMelee) meleeSkillStep(ang);
            else rangedSkillStep(ang);
        }

        protected void startSkill(boolean melee){
            points -= skillCost;
            skillActive = true;
            skillMelee = melee;
            skillStage = 0;
            skillTimer = 0f;
            slashQueue = 0;
            skillHit.clear();
            skillBase = angleTo(target);
            skillCount = melee ? Mathf.random(3, 15) : 5;
        }

        protected void endSkill(){
            skillActive = false;
            atkCounter = 0f;
            skillHit.clear();
        }

        /** Bình thường: 5 luồng 300% → tụ lực 45 tick → 1 nhát 3000%. */
        protected void rangedSkillStep(float ang){
            switch(skillStage){
                case 0 -> {
                    fireWave(wave, ang, damage[Tier] * 3f);
                    swingFx(ang, 56f, 30f);
                    if(--skillCount <= 0){
                        skillStage = 1;
                        skillTimer = 45f;
                        chargeFx.at(x, y, ang, Pal.lancerLaser);
                    }else{
                        skillTimer = 7f;
                    }
                }
                case 1 -> {
                    fireWave(finalWave, ang, damage[Tier] * 30f);
                    swingFx(ang, 96f, 40f);
                    Effect.shake(5.5f, 12f, x, y);
                    endSkill();
                }
            }
        }

        /** Cận chiến: 3-15 nhát 300% → luồng đẩy lùi mạnh → 3 nhát lệch 60° 3000%. */
        protected void meleeSkillStep(float ang){
            switch(skillStage){
                case 0 -> {
                    coneSlash(ang, melee_damage[meleeTier] * 3f);
                    skillTimer = 4f; // nhanh hơn bình thường
                    if(--skillCount <= 0) skillStage = 1;
                }
                case 1 -> {
                    fireWave(knockWave, ang, 0f);
                    swingFx(ang, meleeRange() * 0.9f, 40f);
                    skillStage = 2;
                    skillCount = 3;
                    skillTimer = 22f;
                }
                case 2 -> {
                    int i = 3 - skillCount; // 0,1,2 → -60°, 0°, +60°
                    float a = skillBase + (i - 1) * 60f;
                    lineDamage(a, meleeRange(), 12f, melee_damage[meleeTier] * 30f, true);
                    lineSlashFx.at(x, y, a, HONIVERY_COLOR, meleeRange());
                    Effect.shake(4f, 10f, x, y);
                    skillTimer = 10f;
                    if(--skillCount <= 0) endSkill();
                }
            }
        }

        // ── save ──

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(points);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            points = read.i();
        }
    }
}