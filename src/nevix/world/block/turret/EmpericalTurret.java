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
import arc.math.geom.Intersector;
import arc.scene.ui.layout.Table;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.type.StatusEffect;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import nevix.world.meta.NevixStat;
import nevix.world.meta.NevixStatUnit;

import static mindustry.Vars.indexer;
import static mindustry.Vars.net;
import static mindustry.Vars.tilesize;

public class EmpericalTurret extends NevixTurret {
    public static final float
        BASE_DAMAGE = 510f,
        CHARGE_TIME = 30f,
        BLADE_SPEED = 16f,
        INTRINSIC_STEP = 0.1f,
        INTRINSIC_MAX = 4f,
        INTRINSIC_RESET_TIME = 300f,
        MERGE_DIST = 12f * tilesize,
        MERGED_SLASH_SPEED = 36f, 
        SPIRIT_RESPAWN = 600f,
        SPIRIT_FADE_TIME = 30f,
        HALO_FADE_TIME = 60f,
        SPIRIT_MIN_EFFICIENCY = 0.05f,
        SPIRIT_SPEED = 3f,
        SPIRIT_CONTACT_DPS = 15f;
    
    public static final int SWORDS_PER_ITEM = 3;
    
    public static final int PAL = 3;

    public static SwirdyTurret.SwirdyPalette pal() {
        return SwirdyTurret.palette(PAL);
    }

    public static StatusEffect swordSlow;
    public BulletType alloyAmmo;

    public float[]
    RELOAD = {70f, 65f, 60f, 50f},
    RANGE = {45f * tilesize, 55f * tilesize},
    DAMAGE_MULTI = {1f, 1.25f, 1.5f};
    public int[] SPIRIT_COUNT = {0, 1, 3, 5};
    
    private static float sx(float x, float ang, float f, float s) {
        return x + Angles.trnsx(ang, f) + Angles.trnsx(ang + 90f, s);
    }

    private static float sy(float y, float ang, float f, float s) {
        return y + Angles.trnsy(ang, f) + Angles.trnsy(ang + 90f, s);
    }
    
    public static void drawBlade(float x, float y, float ang, float len, float fade, SwirdyTurret.SwirdyPalette p) {
        if (fade <= 0.01f) return;
        float grip = len * 0.19f;
        float blade = len - grip;
        float w = len * 0.07f;
        float shoulder = blade * 0.82f;
        float tipW = w * 0.55f;

        float gw = w * 1.9f;
        Draw.blend(Blending.additive);
        Draw.color(p.outer, 0.4f * fade);
        Fill.quad(
            sx(x, ang, 0f, -gw), sy(y, ang, 0f, -gw),
            sx(x, ang, 0f, gw), sy(y, ang, 0f, gw),
            sx(x, ang, shoulder, gw * 0.7f), sy(y, ang, shoulder, gw * 0.7f),
            sx(x, ang, shoulder, -gw * 0.7f), sy(y, ang, shoulder, -gw * 0.7f)
        );
        Fill.tri(
            sx(x, ang, shoulder, -gw * 0.7f), sy(y, ang, shoulder, -gw * 0.7f),
            sx(x, ang, shoulder, gw * 0.7f), sy(y, ang, shoulder, gw * 0.7f),
            sx(x, ang, blade * 1.05f, 0f), sy(y, ang, blade * 1.05f, 0f)
        );
        Draw.blend();

        Tmp.c3.set(p.mid).lerp(p.core, 0.55f);
        Draw.color(Tmp.c3, fade);
        Fill.quad(
            sx(x, ang, 0f, 0f), sy(y, ang, 0f, 0f),
            sx(x, ang, 0f, -w), sy(y, ang, 0f, -w),
            sx(x, ang, shoulder, -tipW), sy(y, ang, shoulder, -tipW),
            sx(x, ang, shoulder, 0f), sy(y, ang, shoulder, 0f)
        );
        Fill.tri(
            sx(x, ang, shoulder, 0f), sy(y, ang, shoulder, 0f),
            sx(x, ang, shoulder, -tipW), sy(y, ang, shoulder, -tipW),
            sx(x, ang, blade, 0f), sy(y, ang, blade, 0f)
        );

        Draw.color(p.mid, fade);
        Fill.quad(
            sx(x, ang, 0f, 0f), sy(y, ang, 0f, 0f),
            sx(x, ang, 0f, w), sy(y, ang, 0f, w),
            sx(x, ang, shoulder, tipW), sy(y, ang, shoulder, tipW),
            sx(x, ang, shoulder, 0f), sy(y, ang, shoulder, 0f)
        );
        Fill.tri(
            sx(x, ang, shoulder, 0f), sy(y, ang, shoulder, 0f),
            sx(x, ang, shoulder, tipW), sy(y, ang, shoulder, tipW),
            sx(x, ang, blade, 0f), sy(y, ang, blade, 0f)
        );

        Draw.color(p.core, fade);
        Lines.stroke(Math.max(0.8f, w * 0.25f));
        Lines.line(sx(x, ang, blade * 0.06f, 0f), sy(y, ang, blade * 0.06f, 0f), sx(x, ang, blade * 0.97f, 0f), sy(y, ang, blade * 0.97f, 0f));

        float gt = len * 0.04f, gs = len * 0.25f;
        Tmp.c3.set(p.mid).lerp(p.outer, 0.25f);
        Draw.color(Tmp.c3, fade);
        Fill.quad(
            sx(x, ang, -gt, -gs), sy(y, ang, -gt, -gs),
            sx(x, ang, -gt, gs), sy(y, ang, -gt, gs),
            sx(x, ang, gt, gs), sy(y, ang, gt, gs),
            sx(x, ang, gt, -gs), sy(y, ang, gt, -gs)
        );

        Draw.color(p.outer, fade);
        Fill.quad(
            sx(x, ang, -grip, -w * 0.4f), sy(y, ang, -grip, -w * 0.4f),
            sx(x, ang, -grip, w * 0.4f), sy(y, ang, -grip, w * 0.4f),
            sx(x, ang, -gt, w * 0.4f), sy(y, ang, -gt, w * 0.4f),
            sx(x, ang, -gt, -w * 0.4f), sy(y, ang, -gt, -w * 0.4f)
        );
        Draw.color(p.mid, fade);
        Fill.circle(sx(x, ang, -grip, 0f), sy(y, ang, -grip, 0f), w * 0.85f);

        Draw.reset();
    }

    public static final Effect
    launchFx = new Effect(22f, 160f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        Draw.color(p.mid, fout);
        Lines.stroke(2.2f * fout);
        Lines.circle(e.x, e.y, 4f + Interp.pow3Out.apply(fin) * 22f);
        Draw.color(p.light, fout);
        Drawf.tri(e.x, e.y, 5f * fout, 26f * Interp.pow2Out.apply(fin), e.rotation + 180f);
        Draw.color(Color.white, fout);
        Drawf.tri(e.x, e.y, 3f * fout, 44f * Interp.pow2Out.apply(fin), e.rotation + 180f);
        Draw.color(p.core, 0.9f * fout);
        Drawf.tri(e.x, e.y, 4f * fout, 16f * Interp.pow2Out.apply(fin), e.rotation);
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 36f, p.light, 0.7f * fout);
    }),
    
    impactFx = new Effect(24f, 160f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        Draw.color(p.light, fout);
        Lines.stroke(2.4f * fout);
        Lines.circle(e.x, e.y, 6f + Interp.pow3Out.apply(fin) * 26f);
        Draw.color(p.mid, 0.8f * fout);
        Lines.stroke(1.4f * fout);
        Lines.circle(e.x, e.y, 3f + Interp.pow2Out.apply(fin) * 14f);
        Angles.randLenVectors(e.id, 8, 8f + Interp.pow3Out.apply(fin) * 30f, (dx, dy) -> {
            float a = Mathf.angle(dx, dy);
            Draw.color(p.dustA, fout);
            Drawf.tri(e.x + dx, e.y + dy, 2.4f * fout, 10f * fout + 3f, a);
            Drawf.tri(e.x + dx, e.y + dy, 2.4f * fout, 5f * fout + 1f, a + 180f);
        });
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 40f, p.light, 0.7f * fout);
    }),
    
    greatImpactFx = new Effect(46f, 400f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        Draw.color(p.mid, 0.5f * fout);
        Fill.circle(e.x, e.y, 30f * fout);
        Draw.color(p.light, fout);
        Lines.stroke(4f * fout);
        Lines.circle(e.x, e.y, 8f + Interp.pow3Out.apply(fin) * 54f);
        Lines.stroke(2f * fout);
        Lines.circle(e.x, e.y, 4f + Interp.pow2Out.apply(fin) * 32f);
        for (int k = 0; k < 8; k++) {
            float a = e.rotation + k * 45f + fin * 30f;
            float len = (k % 2 == 0 ? 70f : 38f) * Interp.pow2Out.apply(fin);
            Draw.color(k % 2 == 0 ? Color.white : p.mid, fout);
            Drawf.tri(e.x, e.y, 6f * fout, len, a);
            Drawf.tri(e.x, e.y, 6f * fout, len * 0.4f, a + 180f);
        }
        Angles.randLenVectors(e.id, 14, 10f + Interp.pow3Out.apply(fin) * 70f, (dx, dy) -> {
            Draw.color(p.dustA, fout);
            Drawf.tri(e.x + dx, e.y + dy, 3f * fout, 14f * fout + 3f, Mathf.angle(dx, dy));
        });
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 110f, p.light, 0.9f * fout);
    }),
    
    shardFx = new Effect(30f, 200f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        Draw.color(p.light, fout);
        Lines.stroke(2.4f * fout);
        Lines.circle(e.x, e.y, 6f + Interp.pow3Out.apply(fin) * 28f);
        Draw.color(p.mid, fout);
        Lines.stroke(1.6f * fout);
        Lines.poly(e.x, e.y, 4, 4f + Interp.pow2Out.apply(fin) * 22f, 45f + fin * 90f);
        for (int k = 0; k < 4; k++) {
            float a = k * 90f + fin * 45f;
            Draw.color(k % 2 == 0 ? Color.white : p.dustA, fout);
            Drawf.tri(e.x, e.y, 3.6f * fout, 22f * Interp.pow2Out.apply(fin), a);
        }
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 46f, p.light, 0.7f * fout);
    }),
    
    mergeFx = new Effect(40f, 300f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        for (int k = 0; k < 8; k++) {
            float a = k * 45f + fin * 90f;
            float r = 70f * (1f - Interp.pow2Out.apply(fin));
            Draw.color(k % 2 == 0 ? p.light : p.mid, 0.4f + 0.6f * fin);
            Drawf.tri(e.x + Angles.trnsx(a, r), e.y + Angles.trnsy(a, r), 3f + 3f * fin, 16f + 10f * fin, a + 180f);
        }
        Draw.color(p.outer, 0.8f * fout);
        Lines.stroke(2.5f * fout);
        Lines.circle(e.x, e.y, 40f * fout);
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 80f * fin, p.light, 0.8f * fin);
    }),
    
    ghostFx = new Effect(18f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fout = e.fout();
        Draw.z(Layer.bullet + 2.8f);
        Draw.blend(Blending.additive);
        Draw.color(p.mid, 0.6f * fout);
        Fill.circle(e.x, e.y, 7f * fout);
        Draw.color(p.light, 0.8f * fout);
        for (int k = 0; k < 4; k++) Drawf.tri(e.x, e.y, 2f * fout, 12f * fout, e.rotation + k * 90f);
        Draw.blend();
        Draw.reset();
    }),
    
    spiritSpawnFx = new Effect(36f, 200f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        Draw.color(p.mid, fout);
        Lines.stroke(3f * fout);
        Lines.circle(e.x, e.y, 6f + Interp.pow3Out.apply(fin) * 32f);
        Draw.color(p.light, 0.9f * fout);
        Drawf.tri(e.x, e.y, 8f * fout, 60f * Interp.pow2Out.apply(fin), 90f);
        Drawf.tri(e.x, e.y, 8f * fout, 24f * Interp.pow2Out.apply(fin), 270f);
        Angles.randLenVectors(e.id, 8, 6f + Interp.pow3Out.apply(fin) * 26f, (dx, dy) -> {
            Draw.color(p.dustA, fout);
            Drawf.tri(e.x + dx, e.y + dy, 2f * fout, 8f * fout + 2f, Mathf.angle(dx, dy));
        });
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 60f, p.light, 0.8f * fout);
    }),
    
    spiritDeathFx = new Effect(40f, 240f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fin = e.fin(), fout = e.fout();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);
        
        Draw.color(p.mid, 0.55f * fout);
        Fill.circle(e.x, e.y, 22f * Interp.pow2Out.apply(Mathf.clamp(fin / 0.4f)) * fout + 4f * fout);
        Draw.color(p.light, fout);
        Lines.stroke(3.2f * fout);
        Lines.circle(e.x, e.y, 4f + Interp.pow3Out.apply(fin) * 40f);
        Draw.color(p.outer, 0.8f * fout);
        Lines.stroke(1.6f * fout);
        Lines.circle(e.x, e.y, 2f + Interp.pow2Out.apply(fin) * 24f);
        
        float sl = 38f * Interp.pow2Out.apply(Mathf.clamp(fin / 0.5f));
        for (int k = 0; k < 4; k++) {
            Draw.color(k % 2 == 0 ? Color.white : p.core, fout);
            Drawf.tri(e.x, e.y, 5f * fout, k % 2 == 0 ? sl : sl * 0.6f, e.rotation + k * 90f);
        }
        
        Angles.randLenVectors(e.id, 14, 6f + Interp.pow3Out.apply(fin) * 54f, (dx, dy) -> {
            float a = Mathf.angle(dx, dy);
            Draw.color(Color.white, p.dustA, fin);
            Draw.alpha(fout);
            Lines.stroke(1.3f * fout + 0.3f);
            Lines.lineAngle(e.x + dx, e.y + dy, a, 2f + fout * 7f);
        });
        Draw.blend();
        Draw.reset();
        Drawf.light(e.x, e.y, 70f, p.light, 0.8f * fout);
    }),
    
    slowFx = new Effect(26f, e -> {
        SwirdyTurret.SwirdyPalette p = pal();
        float fout = e.fout();
        Draw.z(Layer.bullet + 2f);
        Draw.blend(Blending.additive);
        Angles.randLenVectors(e.id, 2, 4f + Interp.pow2Out.apply(e.fin()) * 10f, (dx, dy) -> {
            Draw.color(p.dustA, fout);
            Drawf.tri(e.x + dx, e.y + dy, 1.8f * fout, 7f * fout + 1f, Mathf.angle(dx, dy));
        });
        Draw.blend();
        Draw.reset();
    });

    public EmpericalTurret(String name) {
        super(name);

        health = 11500;
        size = 4;

        range = 45f * tilesize;
        reload = 70f;
        rotateSpeed = 5f;
        shootCone = 25f;
        
        alloyAmmo = new BulletType() {{
            damage = 0f;
            speed = 0f;
            lifetime = 1f;
            ammoMultiplier = SWORDS_PER_ITEM;
            collides = false;
            collidesTiles = false;
            hittable = false;
            absorbable = false;
            reflectable = false;
            hitEffect = Fx.none;
            despawnEffect = Fx.none;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
        }};
        ammo(Items.surgeAlloy, alloyAmmo);

        if (swordSlow == null) {
            swordSlow = new StatusEffect("emperical-sword-slow") {{
                speedMultiplier = 0.3f;
                color = pal().light;
                effect = slowFx;
                effectChance = 0.2f;
            }};
        }

        upgradeBranch();
    }

    @Override
    public void init() {
        super.init();
        alloyAmmo.init();
        clipSize = Math.max(clipSize, RANGE[RANGE.length - 1] * 3f);
    }

    @Override
    public void setBars() {
        super.setBars();
        
        addBar("emperical-intrinsic", (EmpericalTurretBuild e) -> new Bar(
        () -> Core.bundle.get("bar.emperical-intrinsic") + ": +" + Mathf.round(e.intrinsicBuff * 100f) + "%",
        () -> pal().mid,
        () -> e.intrinsicBuff / INTRINSIC_MAX
        ));
        
        addBar("emperical-spirit", (EmpericalTurretBuild e) -> new Bar(
        () -> Core.bundle.get("bar.emperical-spirit") + ": " + e.spiritPoints + " (" + e.spirits.size + ")",
        () -> pal().outer,
        () -> Mathf.clamp(e.spiritPoints / 5f)
        ));
    }
    
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
    emperical_sword_spirit_0,
    emperical_sword_spirit_1,
    emperical_sword_spirit_2;
    
    public void upgradeBranch() {
        emperical_reload_0 = addUpgrade("emperical-reload-0", "", ItemStack.with(Items.copper, 200, Items.graphite, 150, Items.silicon, 100, Items.titanium, 100),
            (EmpericalTurretBuild b) -> b.ReloadTier = Math.max(b.ReloadTier, 1), (EmpericalTurretBuild b) -> b.ReloadTier = 0);
        emperical_reload_1 = addUpgrade("emperical-reload-1", "", ItemStack.with(Items.graphite, 200, Items.silicon, 200, Items.titanium, 200, Items.thorium, 100),
            (EmpericalTurretBuild b) -> b.ReloadTier = Math.max(b.ReloadTier, 2), (EmpericalTurretBuild b) -> b.ReloadTier = 0).levelAfter(emperical_reload_0);
        emperical_reload_2 = addUpgrade("emperical-reload-2", "", ItemStack.with(Items.silicon, 300, Items.titanium, 300, Items.thorium, 200, Items.plastanium, 120),
            (EmpericalTurretBuild b) -> b.ReloadTier = Math.max(b.ReloadTier, 3), (EmpericalTurretBuild b) -> b.ReloadTier = 0).levelAfter(emperical_reload_1);

        emperical_range = addUpgrade("emperical-range", "", ItemStack.with(Items.graphite, 200, Items.silicon, 200, Items.titanium, 250, Items.thorium, 100),
            (EmpericalTurretBuild b) -> b.RangeBoosted = true, (EmpericalTurretBuild b) -> b.RangeBoosted = false);

        emperical_damage_1 = addUpgrade("emperical-damage-1", "", ItemStack.with(Items.graphite, 200, Items.silicon, 200, Items.titanium, 200, Items.thorium, 100),
            (EmpericalTurretBuild b) -> b.DamageTier = Math.max(b.DamageTier, 1), (EmpericalTurretBuild b) -> b.DamageTier = 0);
        emperical_damage_2 = addUpgrade("emperical-damage-2", "", ItemStack.with(Items.silicon, 300, Items.titanium, 300, Items.thorium, 250, Items.plastanium, 150),
            (EmpericalTurretBuild b) -> b.DamageTier = Math.max(b.DamageTier, 2), (EmpericalTurretBuild b) -> b.DamageTier = 0).levelAfter(emperical_damage_1);

        emperical_intrinsic = addUpgrade("emperical-intrinsic", "", ItemStack.with(Items.silicon, 250, Items.titanium, 250, Items.thorium, 200, Items.plastanium, 100),
            (EmpericalTurretBuild b) -> b.hasIntrinsic = true, (EmpericalTurretBuild b) -> b.hasIntrinsic = false);

        emperical_charge_shoot_0 = addUpgrade("emperical-charge-shoot-0", "", ItemStack.with(Items.silicon, 250, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100),
            (EmpericalTurretBuild b) -> b.chargeTier = Math.max(b.chargeTier, 1), (EmpericalTurretBuild b) -> b.chargeTier = 0);
        emperical_charge_shoot_1 = addUpgrade("emperical-charge-shoot-1", "", ItemStack.with(Items.silicon, 350, Items.thorium, 250, Items.plastanium, 200, Items.surgeAlloy, 100),
            (EmpericalTurretBuild b) -> b.chargeTier = Math.max(b.chargeTier, 2), (EmpericalTurretBuild b) -> b.chargeTier = 0).levelAfter(emperical_charge_shoot_0);

        emperical_sword_spirit_0 = addUpgrade("emperical-sword-spirit-0", "", ItemStack.with(Items.silicon, 300, Items.thorium, 250, Items.plastanium, 150, Items.surgeAlloy, 100),
            (EmpericalTurretBuild b) -> b.spiritTier = Math.max(b.spiritTier, 1), (EmpericalTurretBuild b) -> b.spiritTier = 0);
        emperical_sword_spirit_1 = addUpgrade("emperical-sword-spirit-1", "", ItemStack.with(Items.thorium, 300, Items.plastanium, 200, Items.surgeAlloy, 150),
            (EmpericalTurretBuild b) -> b.spiritTier = Math.max(b.spiritTier, 2), (EmpericalTurretBuild b) -> b.spiritTier = 0).levelAfter(emperical_sword_spirit_0);
        emperical_sword_spirit_2 = addUpgrade("emperical-sword-spirit-2", "", ItemStack.with(Items.thorium, 400, Items.plastanium, 300, Items.surgeAlloy, 250),
            (EmpericalTurretBuild b) -> b.spiritTier = Math.max(b.spiritTier, 3), (EmpericalTurretBuild b) -> b.spiritTier = 0).levelAfter(emperical_sword_spirit_1);
    }

    public class EmpericalTurretBuild extends NevixTurretBuild {
        public int ReloadTier;
        public boolean RangeBoosted;
        public int DamageTier;
        public boolean hasIntrinsic;
        public int chargeTier;
        public int spiritTier;
        
        public Teamc spiritTarget;
        public float atkCounter, refreshTimer, spiritTargetTimer, sinceLaunch, intrinsicBuff, spiritRespawn;
        public int spiritPoints;
        public float haloFade;
        public Seq<Blade> blades = new Seq<>();
        public Seq<Spirit> spirits = new Seq<>();
        
        @Override
        public void getStats(Table table) {
            addStat(table, NevixStat.generals, "", Pal.accent);
            addStat(table, Stat.health, "" + Mathf.round(maxHealth()));

            int rangeBonus = Mathf.round((range() - RANGE[0]) / tilesize);
            addStat(table, Stat.range, Mathf.round(range() / tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile)
            + (rangeBonus > 0 ? " [gray](+" + rangeBonus + " " + NevixStatUnit.get(NevixStatUnit.tile) + ")" : ""));

            float rl = baseReload();
            float rlDelta = (RELOAD[0] - rl) / 60f;
            addStat(table, Stat.reload, (Mathf.round(rl / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds)
            + (rlDelta > 0.01f ? " [gray](-" + (Mathf.round(rlDelta * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds) + ")" : ""));

            addStat(table, NevixStat.none, "");
            addStat(table, NevixStat.ammo, "", Pal.accent);
            float dmg = calcDamage();
            float dmgBonus = dmg - BASE_DAMAGE;
            addBulletStatTable(table, bt -> {
                addBulletStat(bt, Stat.damage, "" + (Mathf.round(dmg * 10f) / 10f)
                + (dmgBonus > 0.05f ? " [gray](+" + (Mathf.round(dmgBonus * 10f) / 10f) + ")" : ""));
                addBulletStat(bt, NevixStat.chargeTime, (Mathf.round(CHARGE_TIME / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds));
            });

            boolean hasSpecial = chargeTier > 0 || hasIntrinsic || spiritTier > 0;
            if (hasSpecial) {
                addStat(table, NevixStat.none, "");
                addStat(table, NevixStat.special, "", Pal.accent);

                if (chargeTier > 0) {
                    float chance = chargeTier == 1 ? 2f : 5f;
                    float mult = greatMult(chargeTier);
                    addBulletStatTable(table, bt -> {
                        addBulletStat(bt, NevixStat.greatBladeChance, Mathf.round(chance) + "%");
                        addBulletStat(bt, NevixStat.greatBladeMultiplier, Mathf.round(mult * 100f) + "%");
                        addBulletStat(bt, NevixStat.slowAmount, "70% (2 " + NevixStatUnit.get(StatUnit.seconds) + ")");
                        addBulletStat(bt, NevixStat.none, "empericalGreatBlade");
                    });
                }

                if (hasIntrinsic) {
                    addBulletStatTable(table, bt -> {
                        addBulletStat(bt, NevixStat.intrinsicStep, "+" + Mathf.round(INTRINSIC_STEP * 100f) + "%");
                        addBulletStat(bt, NevixStat.intrinsicMax, "+" + Mathf.round(INTRINSIC_MAX * 100f) + "%");
                        addBulletStat(bt, NevixStat.intrinsicReset, (Mathf.round(INTRINSIC_RESET_TIME / 60f * 10f) / 10f) + " " + NevixStatUnit.get(StatUnit.seconds));
                        addBulletStat(bt, NevixStat.none, "empericalIntrinsic");
                    });
                }

                if (spiritTier > 0) {
                    int count = SPIRIT_COUNT[spiritTier];
                    addBulletStatTable(table, bt -> {
                        addBulletStat(bt, NevixStat.swordSpiritCount, "" + count);
                        addBulletStat(bt, NevixStat.swordSpiritHp, "5%");
                        addBulletStat(bt, NevixStat.swordSpiritDamage, "10%");
                        addBulletStat(bt, NevixStat.swordSpiritDps, "" + Mathf.round(SPIRIT_CONTACT_DPS));
                        addBulletStat(bt, NevixStat.none, "empericalSwordSpirit");
                    });
                }
            }
        }
        
        protected boolean owned(Upgrade u) {
            return u != null && isDone(u);
        }

        protected int count(Upgrade a, Upgrade b, Upgrade c) {
            return (owned(a) ? 1 : 0) + (owned(b) ? 1 : 0) + (owned(c) ? 1 : 0);
        }

        protected void refreshUpgrades() {
            ReloadTier = count(emperical_reload_0, emperical_reload_1, emperical_reload_2);
            RangeBoosted = owned(emperical_range);
            DamageTier = count(emperical_damage_1, emperical_damage_2, null);
            hasIntrinsic = owned(emperical_intrinsic);
            chargeTier = count(emperical_charge_shoot_0, emperical_charge_shoot_1, null);
            spiritTier = count(emperical_sword_spirit_0, emperical_sword_spirit_1, emperical_sword_spirit_2);
        }

        @Override
        public float range() {
            return RANGE[RangeBoosted ? 1 : 0];
        }

        public float calcDamage() {
            return BASE_DAMAGE * DAMAGE_MULTI[Math.min(DamageTier, DAMAGE_MULTI.length - 1)];
        }

        public float greatMult(int tier) {
            return tier >= 2 ? 9f : 5f;
        }

        public float speedMult() {
            return 1f + (hasIntrinsic ? intrinsicBuff : 0f);
        }

        public float baseReload() {
            return RELOAD[Math.min(ReloadTier, RELOAD.length - 1)];
        }

        protected boolean valid(Teamc t) {
            return t != null && t.isAdded() && !(t instanceof Healthc h && h.dead());
        }
        
        @Override
        public void updateTile() {
            refreshTimer += Time.delta;
            if (refreshTimer >= 30f) {
                refreshTimer = 0f;
                refreshUpgrades();
            }

            if (net.client()) {
                super.updateTile();
                return;
            }

            float dt = delta() * efficiency;
            
            sinceLaunch = Math.min(sinceLaunch + Time.delta, INTRINSIC_RESET_TIME);
            if (sinceLaunch >= INTRINSIC_RESET_TIME) intrinsicBuff = 0f;
            if (atkCounter < 10000f) atkCounter += dt * speedMult();

            super.updateTile();

            spiritTargetTimer += Time.delta;
            if (spiritTargetTimer >= 8f || !valid(spiritTarget)) {
                spiritTargetTimer = 0f;
                spiritTarget = Units.closestTarget(team, x, y, range() * 1.25f,
                    u -> u.checkTarget(targetAir, targetGround), b -> targetGround);
            }

            float d = Time.delta;
            for (int i = 0; i < blades.size; i++) blades.get(i).update(d);
            blades.removeAll(b -> b.dead);
            updateSpirits(d);
        }

        @Override
        protected void updateShooting() {
            if (net.client() || target == null || atkCounter < baseReload() || !hasAmmo()) return;
            atkCounter = 0f;
            fireCycle();
        }
        
        void fireCycle() {
            boolean wide = hasIntrinsic && intrinsicBuff > 1f;
            int n = wide ? 5 : 1;
            for (int i = 0; i < n; i++) {
                if (wide) {
                    Tmp.v1.trns(Mathf.random(360f), size * tilesize * 0.5f + Mathf.random(16f, 56f));
                } else {
                    Tmp.v1.trns(rotation, size * tilesize * 0.5f + 24f);
                }
                spawnMainBlade(x + Tmp.v1.x, y + Tmp.v1.y);
            }
        }

        void spawnMainBlade(float bx, float by) {
            float chance = chargeTier <= 0 ? 0f : (chargeTier == 1 ? 0.02f : 0.05f);
            boolean great = Mathf.chance(chance);

            Blade b = new Blade();
            b.kind = great ? 1 : 0;
            b.px = bx;
            b.py = by;
            b.rot = rotation;
            b.dmg = calcDamage();
            b.mult = greatMult(chargeTier);
            b.timer = b.chargeTotal = CHARGE_TIME * (great ? 2f : 1f);
            b.counts = true;
            b.merge = true;
            blades.add(b);
        }
        
        void onLaunch() {
            sinceLaunch = 0f;
            if (hasIntrinsic) intrinsicBuff = Math.min(INTRINSIC_MAX, intrinsicBuff + INTRINSIC_STEP);
        }
        
        float hitUnit(Unit u, float dmg) {
            if (!u.isValid()) return 0f;
            float before = u.health + u.shield;
            u.damage(dmg);
            float dealt = Math.max(0f, before - (u.health + u.shield));
            SwirdyTurret.starHitFxs[PAL].at(u.x, u.y, Mathf.random(360f));
            if (u.health <= 0f || u.dead) onKill();
            return dealt;
        }

        float hitBuild(Building b, float dmg) {
            float before = b.health;
            b.damage(team, dmg);
            SwirdyTurret.starHitFxs[PAL].at(b.x, b.y, Mathf.random(360f));
            return Math.max(0f, before - b.health);
        }

        void onKill() {
            if (spiritTier > 0 && Mathf.chance(0.01f)) spiritPoints++;
        }

        float areaDamage(float ax, float ay, float radius, float dmg, boolean slow) {
            float[] total = {0f};
            Units.nearbyEnemies(team, ax - radius, ay - radius, radius * 2f, radius * 2f, u -> {
                if (!u.isValid() || !u.checkTarget(targetAir, targetGround) || !u.within(ax, ay, radius + u.hitSize / 2f)) return;
                total[0] += hitUnit(u, dmg);
                if (slow) u.apply(swordSlow, 120f);
            });
            if (targetGround) {
                indexer.allBuildings(ax, ay, radius, b -> {
                    if (b.team == team || b.team == Team.derelict) return;
                    total[0] += hitBuild(b, dmg);
                });
            }
            return total[0];
        }

        float lineDamage(float x1, float y1, float x2, float y2, float width, float dmg) {
            float[] total = {0f};
            float mx = (x1 + x2) / 2f, my = (y1 + y2) / 2f;
            float half = Mathf.dst(x1, y1, x2, y2) / 2f + width;
            Units.nearbyEnemies(team, mx - half, my - half, half * 2f, half * 2f, u -> {
                if (!u.isValid() || !u.checkTarget(targetAir, targetGround)) return;
                if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, u.x, u.y) <= width + u.hitSize / 2f) {
                    total[0] += hitUnit(u, dmg);
                }
            });
            if (targetGround) {
                indexer.allBuildings(mx, my, half, b -> {
                    if (b.team == team || b.team == Team.derelict) return;
                    if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, b.x, b.y) <= width + b.block.size * tilesize / 2f) {
                        total[0] += hitBuild(b, dmg);
                    }
                });
            }
            return total[0];
        }
        
        float lineDamageOnce(float x1, float y1, float x2, float y2, float width, float dmg, IntSet hit) {
            float[] total = {0f};
            float mx = (x1 + x2) / 2f, my = (y1 + y2) / 2f;
            float half = Mathf.dst(x1, y1, x2, y2) / 2f + width;
            Units.nearbyEnemies(team, mx - half, my - half, half * 2f, half * 2f, u -> {
                if (!u.isValid() || !u.checkTarget(targetAir, targetGround) || hit.contains(u.id)) return;
                if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, u.x, u.y) <= width + u.hitSize / 2f) {
                    hit.add(u.id);
                    total[0] += hitUnit(u, dmg);
                }
            });
            if (targetGround) {
                indexer.allBuildings(mx, my, half, b -> {
                    if (b.team == team || b.team == Team.derelict || hit.contains(b.id)) return;
                    if (Intersector.distanceSegmentPoint(x1, y1, x2, y2, b.x, b.y) <= width + b.block.size * tilesize / 2f) {
                        hit.add(b.id);
                        total[0] += hitBuild(b, dmg);
                    }
                });
            }
            return total[0];
        }
        
        public class Group {
            int count, gathered;
            float tx, ty, ox, oy, mx, my, dmg;
            boolean merge;
            Seq<Blade> shards = new Seq<>();
        }
        
        public class Blade {
            int kind, phase;
            float px, py, rot, timer, chargeTotal, tx, ty, dmg, mult;
            boolean dead, counts, merge, parked;
            Teamc aim;
            Group g;
            IntSet hit;

            boolean big() {
                return kind == 1 || kind == 3;
            }

            void update(float dt) {
                if (dead || parked) return;
                if (phase == 0) {
                    timer -= dt;
                    if (kind == 0 || kind == 1) {
                        if (counts) rot = rotation;
                        else if (valid(aim)) rot = Angles.angle(px, py, aim.getX(), aim.getY());
                    }
                    if (timer <= 0f) launch();
                } else {
                    float d = Mathf.dst(px, py, tx, ty);
                    float step = (kind == 3 ? MERGED_SLASH_SPEED : BLADE_SPEED) * dt;
                    boolean reached = d <= step;
                    float nx, ny;
                    if (reached) {
                        nx = tx;
                        ny = ty;
                    } else {
                        rot = Angles.angle(px, py, tx, ty);
                        nx = px + Angles.trnsx(rot, step);
                        ny = py + Angles.trnsy(rot, step);
                    }
                    
                    if (kind == 3) lineDamageOnce(px, py, nx, ny, 14f, g.dmg * 2f * g.count, hit);
                    px = nx;
                    py = ny;
                    if (reached) {
                        if (phase == 2) gather();
                        else arrive();
                    }
                }
            }

            void launch() {
                SwirdyTurret.SwirdyPalette p = pal();
                if (kind == 2) {
                    tx = g.tx;
                    ty = g.ty;
                } else {
                    if (counts) {
                        if (!hasAmmo()) {
                            shardFx.at(px, py, 0f, p.light);
                            dead = true;
                            return;
                        }
                        useAmmo();
                        if (target instanceof Teamc t) aim = t;
                    }
                    if (valid(aim)) {
                        tx = aim.getX();
                        ty = aim.getY();
                    } else if (tx == 0f && ty == 0f) {
                        tx = px + Angles.trnsx(rot, range() * 0.5f);
                        ty = py + Angles.trnsy(rot, range() * 0.5f);
                    }
                    if (counts) onLaunch();
                }
                launchFx.at(px, py, Angles.angle(px, py, tx, ty), p.light);
                if (big()) Effect.shake(2f, 8f, px, py);
                phase = 1;
            }

            void arrive() {
                SwirdyTurret.SwirdyPalette p = pal();
                switch (kind) {
                    case 0:
                        areaDamage(px, py, 24f, dmg, false);
                        impactFx.at(px, py, rot, p.light);
                        dead = true;
                        break;
                    case 1: {
                        areaDamage(px, py, 40f, dmg * mult, false);
                        greatImpactFx.at(px, py, rot, p.light);
                        Effect.shake(4f, 14f, px, py);
                        int n = Mathf.random(3, 10);
                        Group grp = new Group();
                        grp.count = n;
                        grp.tx = px;
                        grp.ty = py;
                        grp.ox = x;
                        grp.oy = y;
                        
                        grp.mx = px + Angles.trnsx(rot, MERGE_DIST);
                        grp.my = py + Angles.trnsy(rot, MERGE_DIST);
                        grp.dmg = dmg;
                        grp.merge = merge;
                        for (int i = 0; i < n; i++) {
                            Blade s = new Blade();
                            s.kind = 2;
                            s.g = grp;
                            s.timer = s.chargeTotal = 10f + i * 8f;
                            Tmp.v2.trns(Mathf.random(360f), Mathf.random(30f, 64f));
                            s.px = px + Tmp.v2.x;
                            s.py = py + Tmp.v2.y;
                            s.rot = Angles.angle(s.px, s.py, px, py);
                            launchFx.at(s.px, s.py, s.rot + 180f, p.light);
                            grp.shards.add(s);
                            blades.add(s);
                        }
                        dead = true;
                        break;
                    }
                    case 2:
                        areaDamage(px, py, 24f, g.dmg * 1.5f, true);
                        shardFx.at(px, py, 0f, p.light);
                        if (g.merge) {
                            phase = 2;
                            tx = g.mx;
                            ty = g.my;
                        } else {
                            dead = true;
                        }
                        break;
                    case 3:
                        greatImpactFx.at(px, py, 0f, p.light);
                        Effect.shake(6.5f, 14f, px, py);
                        dead = true;
                        break;
                }
            }
            
            void gather() {
                SwirdyTurret.SwirdyPalette p = pal();
                parked = true;
                rot = Angles.angle(px, py, g.ox, g.oy);
                if (++g.gathered < g.count) return;

                for (int i = 0; i < g.shards.size; i++) g.shards.get(i).dead = true;

                Blade m = new Blade();
                m.kind = 3;
                m.g = g;
                m.px = g.mx;
                m.py = g.my;
                m.tx = g.ox;
                m.ty = g.oy;
                m.rot = Angles.angle(m.px, m.py, g.ox, g.oy);
                m.phase = 1;
                m.hit = new IntSet();
                float len = Mathf.dst(m.px, m.py, g.ox, g.oy);
                mergeFx.at(m.px, m.py, 0f, p.light);
                launchFx.at(m.px, m.py, m.rot, p.light);
                SwirdyTurret.lineSlashFx.at(m.px, m.py, m.rot, p.light, new float[]{len, PAL, 1.3f});
                Effect.shake(3f, 10f, m.px, m.py);
                blades.add(m);
            }

            void draw() {
                SwirdyTurret.SwirdyPalette p = pal();
                boolean big = big();
                boolean merged = kind == 3;
                float f = (phase == 0 && chargeTotal > 0f) ? 1f - Mathf.clamp(timer / chargeTotal) : 1f;
                Draw.z(Layer.bullet + 3f);

                if (phase == 0) {
                    if (big && kind != 2) {
                        SwirdyTurret.drawCharge(p, px, py, rot, f);
                    } else {
                        Draw.blend(Blending.additive);
                        Draw.color(p.outer, 0.6f * f);
                        Lines.stroke(1.6f);
                        Lines.circle(px, py, 5f + 12f * (1f - f));
                        Draw.color(p.light, 0.85f * f);
                        Drawf.tri(px, py, 2.4f * f, 16f * f, rot + 180f);
                        Draw.blend();
                        Draw.reset();
                    }
                } else if (!parked) {
                    float sp = merged ? MERGED_SLASH_SPEED : BLADE_SPEED;
                    float wm = merged ? 1.5f : 1f;
                    Draw.blend(Blending.additive);
                    Draw.color(p.outer, 0.6f);
                    Drawf.tri(px, py, (big ? 9f : 5f) * wm, sp * (big ? 6f : 4f), rot + 180f);
                    Draw.color(p.mid, 0.85f);
                    Drawf.tri(px, py, (big ? 5f : 3f) * wm, sp * (big ? 5f : 3.2f), rot + 180f);
                    Draw.color(Color.white, 0.95f);
                    Drawf.tri(px, py, (big ? 2f : 1.2f) * wm, sp * (big ? 4f : 2.4f), rot + 180f);
                    Draw.blend();
                    Draw.reset();
                }

                float len = (big ? (merged ? 72f : 46f) : 24f) * (kind == 2 && phase == 0 ? 0.7f : (0.4f + 0.6f * f));
                float fade = (kind == 2 && phase == 0) ? Mathf.clamp(1f - timer / Math.max(1f, chargeTotal) + 0.3f) : 1f;
                drawBlade(px - Angles.trnsx(rot, len * 0.3f), py - Angles.trnsy(rot, len * 0.3f), rot, len, fade, p);
                Drawf.light(px, py, len * 1.4f, p.light, 0.6f);
                Draw.reset();
            }
        }
        
        public boolean spiritsAllowed() {
            return spiritTier > 0 && hasAmmo() && efficiency > SPIRIT_MIN_EFFICIENCY;
        }

        void updateSpirits(float dt) {
            if (!spiritsAllowed()) {
                for (int i = 0; i < spirits.size; i++) spirits.get(i).die();
                spiritRespawn = 0f;
            } else {
                int want = Math.min(spiritPoints, SPIRIT_COUNT[Math.min(spiritTier, SPIRIT_COUNT.length - 1)]);
                int alive = 0;
                for (int i = 0; i < spirits.size; i++) if (!spirits.get(i).fading) alive++;

                if (alive < want) {
                    spiritRespawn += dt;
                    if (spiritRespawn >= SPIRIT_RESPAWN || alive == 0) {
                        spiritRespawn = 0f;
                        Spirit sp = new Spirit();
                        spiritSpawnFx.at(sp.px, sp.py, 0f, pal().light);
                        spirits.add(sp);
                    }
                } else {
                    spiritRespawn = 0f;
                }
            }

            for (int i = 0; i < spirits.size; i++) spirits.get(i).update(dt);
            spirits.removeAll(s -> s.dead);
        }
        
        float spiritBonus() {
            int spawnPoints = SPIRIT_COUNT[SPIRIT_COUNT.length - 1];
            return 1f + 0.05f * Math.max(0, spiritPoints - spawnPoints);
        }

        public class Spirit {
            float px = x, py = y, health, stored;
            float slashTimer, cd1, cd2, cd3, boostTimer, comboTimer;
            float sx, sy, ex, ey, dashT;
            int comboLeft;
            boolean dead, fading;
            float facing, fade;

            Spirit() {
                health = maxHp();
            }

            float maxHp() {
                return maxHealth() * 0.05f * spiritBonus();
            }

            float dmg() {
                return calcDamage() * 0.10f * spiritBonus();
            }

            float interval() {
                return baseReload() / (boostTimer > 0f ? 1.5f : 1f);
            }
            
            void die() {
                if (fading) return;
                fading = true;
                fade = SPIRIT_FADE_TIME;
                dashT = 0f;
                comboLeft = 0;
            }

            void heal(float amt) {
                float add = Math.min(maxHp() - health, amt);
                health += add;
                stored = Math.min(stored + (amt - add), maxHp() * 0.75f);
            }

            void update(float dt) {
                if (fading) {
                    fade -= dt;
                    if (fade <= 0f) {
                        spiritDeathFx.at(px, py, Mathf.random(45f), pal().light);
                        dead = true;
                    }
                    return;
                }
                cd1 = Math.max(0f, cd1 - dt);
                cd2 = Math.max(0f, cd2 - dt);
                cd3 = Math.max(0f, cd3 - dt);
                slashTimer -= dt;
                boostTimer -= dt;
                
                float[] taken = {0f};
                Units.nearbyEnemies(team, px - 24f, py - 24f, 48f, 48f, u -> {
                    if (u.isValid()) taken[0] += SPIRIT_CONTACT_DPS / 60f * dt;
                });
                health -= taken[0];
                if (health < maxHp() && stored > 0f) {
                    float h = Math.min(stored, maxHp() - health);
                    health += h;
                    stored -= h;
                }
                if (health <= 0f) {
                    die();
                    return;
                }

                if (dashT > 0f) {
                    dashT -= dt;
                    float f = 1f - Math.max(dashT, 0f) / 12f;
                    px = Mathf.lerp(sx, ex, f);
                    py = Mathf.lerp(sy, ey, f);
                    ghostFx.at(px, py, facing, pal().light);
                    return;
                }

                Teamc tgt = valid(spiritTarget) ? spiritTarget : null;
                if (tgt == null) {
                    comboLeft = 0;
                    float a = Time.time * 1.5f + spirits.indexOf(this) * (360f / Math.max(1, spirits.size));
                    Tmp.v3.trns(a, size * tilesize * 0.5f + 28f);
                    moveTo(x + Tmp.v3.x, y + Tmp.v3.y, dt);
                    return;
                }

                float d = Mathf.dst(px, py, tgt.getX(), tgt.getY()) / tilesize;
                facing = Angles.angle(px, py, tgt.getX(), tgt.getY());

                if (comboLeft > 0) {
                    comboTimer -= dt;
                    if (comboTimer <= 0f) {
                        slash();
                        comboLeft--;
                        comboTimer = 8f;
                        if (comboLeft == 0) {
                            boostTimer = 90f;
                            launchFx.at(px, py, facing + 180f, pal().light);
                        }
                    }
                    return;
                }

                if (cd3 <= 0f && d <= 4f) {
                    comboLeft = Mathf.random(2, 5);
                    comboTimer = 0f;
                    cd3 = 480f;
                    return;
                }
                if (cd2 <= 0f && d >= 15f && d <= 25f) {
                    dash(Math.min(20f, d) * tilesize);
                    cd2 = 1500f;
                    lineDamage(sx, sy, ex, ey, 16f, dmg() * 0.6f);
                    SwirdyTurret.lineSlashFx.at(sx, sy, facing, pal().light, new float[]{Mathf.dst(sx, sy, ex, ey), PAL, 0.6f});
                    int n = Mathf.random(1, 5);
                    for (int i = 0; i < n; i++) spawnSkillBlade(tgt);
                    return;
                }
                if (cd1 <= 0f && d > 20f && d < 30f) {
                    dash(15f * tilesize);
                    cd1 = 600f;
                    SwirdyTurret.lineSlashFx.at(sx, sy, facing, pal().light, new float[]{Mathf.dst(sx, sy, ex, ey), PAL, 0.4f});
                    return;
                }

                if (d > 3f) moveTo(tgt.getX(), tgt.getY(), dt);
                if (d <= 4f && slashTimer <= 0f) {
                    slash();
                    slashTimer = interval();
                }
            }

            void moveTo(float tx, float ty, float dt) {
                float d = Mathf.dst(px, py, tx, ty);
                float step = SPIRIT_SPEED * dt;
                if (d <= step) {
                    px = tx;
                    py = ty;
                } else {
                    float a = Angles.angle(px, py, tx, ty);
                    px += Angles.trnsx(a, step);
                    py += Angles.trnsy(a, step);
                }
            }

            void dash(float dist) {
                sx = px;
                sy = py;
                ex = px + Angles.trnsx(facing, dist);
                ey = py + Angles.trnsy(facing, dist);
                dashT = 12f;
                launchFx.at(sx, sy, facing, pal().light);
            }

            void slash() {
                float cx = px + Angles.trnsx(facing, 12f);
                float cy = py + Angles.trnsy(facing, 12f);
                float dealt = areaDamage(cx, cy, 22f, dmg(), false);
                
                SwirdyTurret.slashFx.at(px, py, facing, pal().light, new float[]{Mathf.randomSign(), 4f * tilesize, 38f, PAL, 0.6f});
                heal(dealt * 0.05f);
            }
            
            void spawnSkillBlade(Teamc tgt) {
                Tmp.v3.trns(Mathf.random(360f), Mathf.random(16f, 40f));
                Blade b = new Blade();
                b.kind = 1;
                b.px = ex + Tmp.v3.x;
                b.py = ey + Tmp.v3.y;
                b.rot = facing;
                b.dmg = dmg() * 0.9f;
                b.mult = greatMult(Math.max(1, chargeTier));
                b.timer = b.chargeTotal = CHARGE_TIME * 2f;
                b.aim = tgt;
                b.tx = tgt.getX();
                b.ty = tgt.getY();
                b.counts = false;
                b.merge = false;
                blades.add(b);
            }

            void draw() {
                SwirdyTurret.SwirdyPalette p = pal();
                float al = fading ? Mathf.clamp(fade / SPIRIT_FADE_TIME) : 1f;
                
                SwirdyTurret.drawHalo(p, px, py, facing, Time.time, al);
                Draw.z(Layer.bullet + 3f);
                drawBlade(px + Angles.trnsx(facing, 6f), py + Angles.trnsy(facing, 6f), facing, 20f, al, p);

                float w = 12f, f = Mathf.clamp(health / maxHp());
                Draw.color(Color.black, 0.7f * al);
                Fill.rect(px, py + 12f, w, 2f);
                Draw.color(p.mid, al);
                Fill.rect(px - w / 2f + w * f / 2f, py + 12f, w * f, 2f);
                if (stored > 0f) {
                    Draw.color(p.core, 0.8f * al);
                    float sf = Mathf.clamp(stored / (maxHp() * 0.75f));
                    Fill.rect(px - w / 2f + w * sf / 2f, py + 14.2f, w * sf, 1.2f);
                }
                Draw.reset();
            }
        }
        
        @Override
        public void draw() {
            super.draw();
            SwirdyTurret.SwirdyPalette p = pal();

            float a = Mathf.clamp(intrinsicBuff / INTRINSIC_MAX);
            float base = size * tilesize * 0.5f;
            float spin = Time.time * (0.4f + 2.2f * a);
            
            haloFade = Mathf.approachDelta(haloFade, spiritsAllowed() ? 1f : 0f, 1f / HALO_FADE_TIME);
            float h = Interp.smooth.apply(haloFade);
            boolean halo = h > 0.01f;
            Draw.z(Layer.bullet + 2f);
            Draw.blend(Blending.additive);
            if (halo) {
                float rs = 0.9f + 0.1f * h;
                Draw.color(p.outer, (0.22f + 0.3f * a) * h);
                Lines.stroke(1.6f + 1.6f * a);
                Lines.circle(x, y, base * 1.45f * rs);
                Draw.color(p.mid, (0.3f + 0.35f * a) * h);
                Lines.stroke(1.2f);
                Lines.poly(x, y, 4, base * 1.3f * rs, spin);
                Lines.poly(x, y, 4, base * 1.3f * rs, -spin + 45f);
                int spikes = 4 + Mathf.floor(a * 4f);
                for (int k = 0; k < spikes; k++) {
                    float ang = Time.time * (0.8f + 1.5f * a) + k * 360f / spikes;
                    Draw.color(k % 2 == 0 ? p.core : p.light, 0.8f * h);
                    Drawf.tri(x + Angles.trnsx(ang, base * 1.45f * rs), y + Angles.trnsy(ang, base * 1.45f * rs), 2.2f, (7f + 5f * a) * h, ang);
                }
            }
            if (target != null && hasAmmo()) {
                float c = Mathf.clamp(atkCounter / baseReload());
                float fx = x + Angles.trnsx(rotation, base + 24f), fy = y + Angles.trnsy(rotation, base + 24f);
                Draw.color(p.mid, 0.35f + 0.5f * c);
                Fill.circle(fx, fy, 3f + 8f * c);
                Draw.color(Color.white, 0.6f * c);
                Fill.circle(fx, fy, 1.5f + 3f * c);
            }
            Draw.blend();
            Draw.reset();
            if (halo) Drawf.light(x, y, base * (3f + 3f * a), p.light, (0.35f + 0.3f * a) * h);

            for (int i = 0; i < blades.size; i++) blades.get(i).draw();
            for (int i = 0; i < spirits.size; i++) spirits.get(i).draw();
            Draw.reset();
        }
    }
}