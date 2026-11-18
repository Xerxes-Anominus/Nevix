package nevix.world.block.turret;

import arc.Core;
import arc.Events;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.util.Log;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import nevix.world.meta.NevixStat;
import nevix.world.meta.NevixStatUnit;

import static mindustry.Vars.indexer;
import static mindustry.Vars.net;
import static mindustry.Vars.netClient;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class SwirdyTurret extends NevixTurret {
    public static final int skillCost = 100;

    public float rangedTiles = 23f, meleeTiles = 9f;

    public float rangedChargeTicks = 45f, meleeChargeTicks = 30f;

    public float meleeLifesteal = 0.02f;

    public SwirdyWave leadAmmo, titaniumAmmo, thoriumAmmo, surgeAmmo;

    public static class SwirdyPalette {
        public final Color outer, mid, core, dustA, dustB, light;

        public SwirdyPalette(String outer, String mid, String core, String dustA, String dustB, String light){
            this.outer = Color.valueOf(outer);
            this.mid = Color.valueOf(mid);
            this.core = Color.valueOf(core);
            this.dustA = Color.valueOf(dustA);
            this.dustB = Color.valueOf(dustB);
            this.light = Color.valueOf(light);
        }
    }

    public static final SwirdyPalette[] palettes = {
        new SwirdyPalette("5B2BD6", "B58CFF", "F3EAFF", "C79BFF", "FFFFFF", "B58CFF"),
        new SwirdyPalette("2D6BFF", "6FD0FF", "FFFFFF", "9FE3FF", "FFFFFF", "7CC8FF"),
        new SwirdyPalette("D81B4A", "FF6FA8", "FFE9F0", "FF4466", "FFB0C8", "FF6A8E"),
        new SwirdyPalette("E0A100", "FFD84D", "FFF7D6", "FFC400", "FFFFFF", "FFD866")
    };

    public static SwirdyPalette palette(int i){
        return palettes[Mathf.clamp(i, 0, palettes.length - 1)];
    }

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

    public static void drawSlashLayers(SwirdyPalette p, float x, float y, float ang, float curR, float half, float fade, float time, float scale, boolean tear){
        if(fade <= 0.01f || half < 1f) return;
        Draw.blend(Blending.additive);

        if(tear){
            for(int d = -1; d <= 1; d += 2){
                float slashAng = ang + d * 34f;
                float len = curR * 0.92f;
                float cx = x + Angles.trnsx(ang, curR * 0.42f);
                float cy = y + Angles.trnsy(ang, curR * 0.42f);
                Draw.color(p.outer, 0.65f * fade);
                Drawf.tri(cx, cy, 14f * scale * fade, len * 0.55f, slashAng);
                Drawf.tri(cx, cy, 14f * scale * fade, len * 0.55f, slashAng + 180f);
                Draw.color(Color.white, 0.95f * fade);
                Drawf.tri(cx, cy, 4.5f * scale * fade, len * 0.52f, slashAng);
                Drawf.tri(cx, cy, 4.5f * scale * fade, len * 0.52f, slashAng + 180f);
            }
        }

        drawStarCrescent(x, y, ang, curR, half, 0.46f, p.outer, 0.75f * fade);
        drawStarCrescent(x, y, ang, curR * 0.96f, half * 0.92f, 0.34f, p.mid, 0.85f * fade);
        drawStarCrescent(x, y, ang, curR * 0.93f, half * 0.85f, 0.18f, p.core, 0.98f * fade);

        for(int q = 0; q < 10; q++){
            float a = ang - half + (half * 2f) * (q / 9f);
            float rr = curR * (0.85f + 0.18f * Mathf.sin(time + q));
            float px = x + Angles.trnsx(a, rr);
            float py = y + Angles.trnsy(a, rr);
            Draw.color(q % 2 == 0 ? p.dustA : p.dustB, fade);
            Drawf.tri(px, py, 3.2f * scale * fade, 14f * scale * fade, a);
            Drawf.tri(px, py, 3.2f * scale * fade, 14f * scale * fade, a + 180f);
        }

        Draw.blend();
        Draw.reset();
    }

    private static float sx(float x, float ang, float f, float s){
        return x + Angles.trnsx(ang, f) + Angles.trnsx(ang + 90f, s);
    }

    private static float sy(float y, float ang, float f, float s){
        return y + Angles.trnsy(ang, f) + Angles.trnsy(ang + 90f, s);
    }

    public static final float swordLength = 2f * tilesize, swordWidth = 1f * tilesize;

    public static void drawSword(float x, float y, float ang, float fade, SwirdyPalette p){
        if(fade <= 0.01f) return;
        float grip = swordLength * 0.19f;
        float blade = swordLength - grip;
        float w = swordWidth * 0.14f;
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

        float gt = 0.65f, gs = swordWidth / 2f;
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
    
    public static void drawSwordTrail(SwirdyPalette p, float x, float y, float start, float dir, float sweep,
                                      float handR, float baseR, float range, float expand, float tail, float fade){
        if(fade <= 0.01f || sweep < 0.5f || tail >= 0.98f) return;

        final int segs = 28;
        Draw.blend(Blending.additive);

        for(int layer = 0; layer < 3; layer++){
            Color col = layer == 0 ? p.outer : layer == 1 ? p.mid : p.core;
            float innerRatio = layer == 0 ? 0.70f : layer == 1 ? 0.84f : 0.94f;
            float aMax = layer == 0 ? 0.55f : layer == 1 ? 0.80f : 1f;
            float innerA = layer == 2 ? 0.35f : 0f;

            for(int i = 0; i < segs; i++){
                float g1 = i / (float)segs;
                float g2 = (i + 1) / (float)segs;
                float f1 = Mathf.lerp(tail, 1f, g1);
                float f2 = Mathf.lerp(tail, 1f, g2);

                float a1 = start - dir * sweep * f1;
                float a2 = start - dir * sweep * f2;

                float len1 = baseR + Math.max(0f, range - baseR) * expand * (0.6f + 0.4f * f1);
                float len2 = baseR + Math.max(0f, range - baseR) * expand * (0.6f + 0.4f * f2);
                
                float in1 = handR + (len1 - handR) * Mathf.lerp(0.99f, innerRatio, g1);
                float in2 = handR + (len2 - handR) * Mathf.lerp(0.99f, innerRatio, g2);

                float al1 = aMax * fade * g1 * g1;
                float al2 = aMax * fade * g2 * g2;

                float cOut1 = Tmp.c1.set(col).a(al1).toFloatBits();
                float cOut2 = Tmp.c1.set(col).a(al2).toFloatBits();
                float cIn1 = Tmp.c1.set(col).a(al1 * innerA).toFloatBits();
                float cIn2 = Tmp.c1.set(col).a(al2 * innerA).toFloatBits();

                Fill.quad(
                    x + Angles.trnsx(a1, in1), y + Angles.trnsy(a1, in1), cIn1,
                    x + Angles.trnsx(a1, len1), y + Angles.trnsy(a1, len1), cOut1,
                    x + Angles.trnsx(a2, len2), y + Angles.trnsy(a2, len2), cOut2,
                    x + Angles.trnsx(a2, in2), y + Angles.trnsy(a2, in2), cIn2
                );
            }
        }

        Draw.blend();
        Draw.reset();
    }

    public static final Effect slashFx = new Effect(24f, 400f, e -> {
        if(!(e.data instanceof float[])) return;
        float[] d = (float[])e.data;
        float dir = d[0], range = d[1], half = d[2];
        SwirdyPalette p = palette((int)d[3]);
        float size = d.length > 4 ? d[4] : 1f;

        float fin = e.fin();
        float swing = Interp.pow2Out.apply(Mathf.clamp(fin / 0.5f));
        float expand = Interp.pow3Out.apply(Mathf.clamp(fin / 0.7f));
        float swordFade = 1f - Mathf.clamp((fin - 0.55f) / 0.3f);
        float ghostFade = 1f - Interp.pow2In.apply(Mathf.clamp((fin - 0.25f) / 0.75f));

        float start = e.rotation + dir * half;
        float sweep = half * 2f * swing;
        float cur = start - dir * sweep;
        float handR = 9f;
        float baseR = handR + swordLength * 0.8f;
        float ws = Mathf.clamp(size, 0.6f, 1.5f);
        
        float tail = Interp.pow2In.apply(Mathf.clamp((fin - 0.4f) / 0.6f));
        Draw.z(Layer.bullet + 3f);
        drawSwordTrail(p, e.x, e.y, start, dir, sweep, handR, baseR, range, expand, tail, ghostFade);
        
        if(ghostFade > 0.02f){
            float len = baseR + Math.max(0f, range - baseR) * expand;
            float L = Math.max(1f, len - handR);
            float cx = e.x + Angles.trnsx(cur, handR + L * 0.3f);
            float cy = e.y + Angles.trnsy(cur, handR + L * 0.3f);

            Draw.z(Layer.bullet + 3f);
            Draw.blend(Blending.additive);
            Draw.color(p.mid, 0.7f * ghostFade);
            Drawf.tri(cx, cy, 5f * ws, L * 0.7f, cur);
            Drawf.tri(cx, cy, 5f * ws, L * 0.3f, cur + 180f);
            Draw.color(p.core, 0.9f * ghostFade);
            Drawf.tri(cx, cy, 2f * ws, L * 0.66f, cur);
            Drawf.tri(cx, cy, 2f * ws, L * 0.26f, cur + 180f);
            Draw.blend();
        }

        Draw.reset();

        Draw.z(Layer.bullet + 3.5f);
        drawSword(e.x + Angles.trnsx(cur, handR), e.y + Angles.trnsy(cur, handR), cur, swordFade, p);

        float lr = baseR + Math.max(0f, range - baseR) * expand;
        Drawf.light(e.x + Angles.trnsx(cur, lr * 0.5f), e.y + Angles.trnsy(cur, lr * 0.5f), lr, p.light, 0.8f * ghostFade);
    });

    public static final Effect lineSlashFx = new Effect(22f, 900f, e -> {
        if(!(e.data instanceof float[])) return;
        float[] d = (float[])e.data;
        float len = d[0];
        SwirdyPalette p = palette((int)d[1]);
        float ws = d.length > 2 ? d[2] : 1f;

        float fin = e.fin();
        float reach = len * Interp.pow3Out.apply(Mathf.clamp(fin / 0.35f));
        float fade = 1f - Interp.pow2In.apply(Mathf.clamp((fin - 0.25f) / 0.75f));
        float r0 = 12f;
        float L = Math.max(1f, reach - r0);
        float bx = e.x + Angles.trnsx(e.rotation, r0 + L * 0.22f);
        float by = e.y + Angles.trnsy(e.rotation, r0 + L * 0.22f);

        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);

        Draw.color(p.outer, 0.65f * fade);
        Drawf.tri(bx, by, 20f * ws * fade, L * 0.78f, e.rotation);
        Drawf.tri(bx, by, 20f * ws * fade, L * 0.22f, e.rotation + 180f);
        Draw.color(p.mid, 0.85f * fade);
        Drawf.tri(bx, by, 11f * ws * fade, L * 0.76f, e.rotation);
        Drawf.tri(bx, by, 11f * ws * fade, L * 0.2f, e.rotation + 180f);
        Draw.color(p.core, 0.95f * fade);
        Drawf.tri(bx, by, 4.5f * ws * fade, L * 0.74f, e.rotation);
        Drawf.tri(bx, by, 4.5f * ws * fade, L * 0.18f, e.rotation + 180f);

        for(int q = 0; q < 14; q++){
            float f = q / 13f;
            float px = e.x + Angles.trnsx(e.rotation, r0 + L * f);
            float py = e.y + Angles.trnsy(e.rotation, r0 + L * f);
            Draw.color(q % 2 == 0 ? p.dustA : p.dustB, fade);
            Drawf.tri(px, py, 3.2f * ws * fade, 14f * ws * fade, e.rotation + 90f);
            Drawf.tri(px, py, 3.2f * ws * fade, 14f * ws * fade, e.rotation + 270f);
        }

        Draw.blend();
        Draw.reset();
        Drawf.light(bx, by, len * 0.5f, p.light, 0.8f * fade);
    });

    public static void drawCharge(SwirdyPalette p, float x, float y, float rot, float fr){
        float inv = 1f - fr;
        float z = Draw.z();
        Draw.z(Layer.bullet + 3f);
        Draw.blend(Blending.additive);

        Draw.color(p.outer, 0.7f * fr);
        Lines.stroke(2.2f);
        Lines.circle(x, y, 14f + 26f * inv);

        for(int k = 0; k < 4; k++){
            float a = rot + k * 90f + fr * 180f;
            float len = (k == 0 ? 34f : 18f) * (0.4f + 0.8f * fr);
            Draw.color(k == 0 ? Color.white : p.light, 0.85f * fr);
            Drawf.tri(x, y, 3.8f * fr, len, a);
        }

        Draw.blend();
        Draw.reset();
        Drawf.light(x, y, 55f * fr, p.light, 0.8f * fr);
        Draw.z(z);
    }
    
    public static void drawHalo(SwirdyPalette p, float x, float y, float rot, float time, float fade){
        final float R = tilesize * 0.5f;
        final float spike = tilesize * 0.5f;

        float z = Draw.z();
        Draw.z(Layer.bullet + 2f);
        Draw.blend(Blending.additive);
        
        Draw.color(p.mid, 0.9f * fade);
        Fill.circle(x, y, R);
        
        Draw.color(p.light, 0.95f * fade);
        for(int k = 0; k < 4; k++){
            float a = rot + k * 90f;
            Drawf.tri(x + Angles.trnsx(a, R - 1f), y + Angles.trnsy(a, R - 1f), 2f, spike + 1f, a);
        }
        
        float orbit = R + spike + 0.5f;
        float spin = 45f - time * 1.6f;
        Draw.color(p.core, 0.9f * fade);
        for(int k = 0; k < 4; k++){
            float a = spin + k * 90f;
            Drawf.tri(x + Angles.trnsx(a, orbit), y + Angles.trnsy(a, orbit), 2f, spike * 0.75f, a);
        }

        Draw.blend();
        Draw.reset();
        Drawf.light(x, y, 28f, p.light, 0.6f * fade);
        Draw.z(z);
    }

    public static final Effect[] starHitFxs = new Effect[palettes.length];

    static{
        for(int i = 0; i < palettes.length; i++){
            final SwirdyPalette p = palettes[i];
            starHitFxs[i] = new Effect(26f, e -> {
                float fin = e.fin(), fout = e.fout();
                Draw.blend(Blending.additive);
                Draw.color(p.light, fout);
                Lines.stroke(2.5f * fout);
                Lines.circle(e.x, e.y, 6f + Interp.pow3Out.apply(fin) * 28f);

                for(int k = 0; k < 4; k++){
                    float a = e.rotation + k * 90f + fin * 35f;
                    float len = (k % 2 == 0 ? 32f : 18f) * Interp.pow2Out.apply(fin);
                    Draw.color(k % 2 == 0 ? Color.white : p.mid, fout);
                    Drawf.tri(e.x, e.y, 4.5f * fout, len, a);
                    Drawf.tri(e.x, e.y, 4.5f * fout, len * 0.45f, a + 180f);
                }
                Draw.blend();
                Draw.reset();
                Drawf.light(e.x, e.y, 42f, p.light, 0.8f * fout);
            });
        }
    }

    public static class SwirdyWave extends BasicBulletType {
        public float arcSize;
        public float half = 45f;
        public boolean tear;
        public int pal;

        public float dmgMul = 1f, sizeMul = 1f, reloadMul = 1f;

        public float slashHalf = 30f;

        public float rangeBonus = 0f;
        
        public float finalChance = 0f;
        public float bonusSlashChance = 0f;
        public SwirdyWave finalWave, knockWave;

        public SwirdyWave(float speed, float damage, float arcSize, float lifetime, boolean tear, int pal){
            super(speed, damage);
            this.arcSize = arcSize;
            this.tear = tear;
            this.pal = pal;
            this.lifetime = lifetime;

            collides = false;
            collidesTiles = false;
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
            hitColor = palette(pal).light;
            hitEffect = starHitFxs[Mathf.clamp(pal, 0, palettes.length - 1)];
            despawnEffect = Fx.none;
            shootEffect = Fx.none;
            smokeEffect = Fx.none;
            trailLength = 0;
        }

        protected boolean touches(float cx, float cy, float ang, float rad, float px, float py, float pr){
            float dx = px - cx, dy = py - cy;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if(d - pr > rad || d + pr < rad * 0.5f) return false;
            float slack = (float)Math.toDegrees(Math.atan2(pr, Math.max(d, 1f)));
            return Angles.within(Angles.angle(dx, dy), ang, half + slack);
        }

        @Override
        public void update(Bullet b){
            super.update(b);

            SwirdyTurretBuild owner = b.owner instanceof SwirdyTurretBuild ? (SwirdyTurretBuild)b.owner : null;
            float rad = arcSize * 2f;
            float ang = b.rotation();

            float cx = b.x - Angles.trnsx(ang, rad), cy = b.y - Angles.trnsy(ang, rad);
            float reach = rad + 24f;

            Units.nearbyEnemies(b.team, cx - reach, cy - reach, reach * 2f, reach * 2f, u -> {
                if(!u.isValid() || !u.checkTarget(collidesAir, collidesGround) || b.collided.contains(u.id)) return;
                if(!touches(cx, cy, ang, rad, u.x, u.y, u.hitSize / 2f)) return;

                b.collided.add(u.id);
                if(!net.client()){
                    if(b.damage > 0f) u.damage(b.damage);
                    if(knockback != 0f){
                        Tmp.v3.set(u).sub(b).nor().scl(knockback * 80f);
                        if(impact) Tmp.v3.setAngle(ang + (knockback < 0f ? 180f : 0f));
                        u.impulse(Tmp.v3);
                    }
                }
                hitEffect.at(u.x, u.y, ang, hitColor);
                if(owner != null) owner.gainPoint();
            });

            if(collidesGround){
                indexer.allBuildings(cx, cy, reach, bd -> {
                    if(bd.team == b.team || bd.team == mindustry.game.Team.derelict || b.collided.contains(bd.id)) return;
                    if(!touches(cx, cy, ang, rad, bd.x, bd.y, bd.block.size * tilesize / 2f)) return;

                    b.collided.add(bd.id);
                    float dmg = b.damage * buildingDamageMultiplier;
                    if(!net.client() && dmg > 0f) bd.damage(b.team, dmg);
                    hitEffect.at(bd.x, bd.y, ang, hitColor);
                    if(owner != null) owner.gainPoint();
                });
            }
        }

        @Override
        public void draw(Bullet b){
            SwirdyPalette p = palette(pal);
            float rad = arcSize * 2f;
            float fade = Mathf.clamp(b.fout() * 5f);

            Tmp.v1.trns(b.rotation(), -rad);
            float cx = b.x + Tmp.v1.x, cy = b.y + Tmp.v1.y;
            drawSlashLayers(p, cx, cy, b.rotation(), rad, half, fade, b.time, arcSize / 20f, tear);
            Drawf.light(b.x, b.y, rad * 1.4f, p.light, 0.6f * fade);
        }
    }

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

        leadAmmo = makeAmmo(0, 0.7f, 0.7f, 1f, 0f, 80f);
        titaniumAmmo = makeAmmo(1, 1f, 1f, 1f, 0f, 100f);
        thoriumAmmo = makeAmmo(2, 1.5f, 1.5f, 1.15f, 5f, 120f);
        surgeAmmo = makeAmmo(3, 2f, 1.5f, 1.25f, 7f, 140f, 1.3f, 0.02f, 0.15f);

        ammo(
            Items.lead, leadAmmo,
            Items.titanium, titaniumAmmo,
            Items.thorium, thoriumAmmo,
            Items.surgeAlloy, surgeAmmo
        );

        upgradeBranch();
    }

    protected SwirdyWave makeAmmo(int pal, float dmgMul, float sizeMul, float reloadMul, float rangeBonusTiles, float slashTotalDeg){
        return makeAmmo(pal, dmgMul, sizeMul, reloadMul, rangeBonusTiles, slashTotalDeg, 1f, 0f, 0f);
    }

    protected SwirdyWave makeAmmo(int pal, float dmgMul, float sizeMul, float reloadMul, float rangeBonusTiles, float slashTotalDeg,
                                  float lifetimeMul, float finalChance, float bonusSlashChance){
        SwirdyWave w = new SwirdyWave(9f, damage[0] * dmgMul, 12f * sizeMul, 38f * lifetimeMul, false, pal);
        w.knockback = 1f;
        w.dmgMul = dmgMul;
        w.sizeMul = sizeMul;
        w.reloadMul = reloadMul;
        w.rangeBonus = rangeBonusTiles * tilesize;
        w.slashHalf = slashTotalDeg / 2f;
        w.finalChance = finalChance;
        w.bonusSlashChance = bonusSlashChance;

        SwirdyWave f = new SwirdyWave(10f, 0f, 34f * sizeMul, 40f * lifetimeMul, true, pal);
        f.knockback = 2f;

        SwirdyWave k = new SwirdyWave(11f, 0f, 22f * sizeMul, 30f * lifetimeMul, false, pal);
        k.hitEffect = Fx.none;
        k.knockback = 8f;
        k.impact = true;
        k.buildingDamageMultiplier = 0f;

        w.finalWave = f;
        w.knockWave = k;
        return w;
    }
    
    public static final String promotionStarName = "nevix-promotion-star";

    protected static Item promotionStar(){
        Item i = Vars.content.item(promotionStarName);
        if(i == null) throw new IllegalStateException("Swirdy: không tìm thấy item '" + promotionStarName + "' (Promotion Star)");
        return i;
    }

    public static final String netChannel = "swirdy";
    protected static final int netSlash = 0, netLine = 1, netWave = 2, netCharge = 3, netState = 4, netAmmo = 5, netHit = 6;
    private static boolean netRegistered;

    protected static void registerNet(){
        if(netRegistered || netClient == null) return;
        netRegistered = true;
        netClient.addPacketHandler(netChannel, SwirdyTurret::onNetPacket);
    }

    protected static void onNetPacket(String msg){
        if(!net.client()) return;
        try{
            String[] t = msg.split(";");
            Building b = world.build(Integer.parseInt(t[0]));
            if(b instanceof SwirdyTurret.SwirdyTurretBuild build) build.swirdyNet(t);
        }catch(Throwable err){
            Log.err("Swirdy: gói tin lỗi", err);
        }
    }

    @Override
    public void init(){
        super.init();
        registerNet();
        Events.on(EventType.ClientLoadEvent.class, e -> registerNet());
        for(SwirdyWave a : new SwirdyWave[]{leadAmmo, titaniumAmmo, thoriumAmmo, surgeAmmo}){
            a.init();
            a.finalWave.init();
            a.knockWave.init();
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        addBar("swirdy-skill", (SwirdyTurretBuild e) -> new Bar(
        () -> Core.bundle.get("bar.swirdy-skill") + ": " + e.points + "/" + skillCost,
        () -> Pal.lancerLaser,
        () -> e.points / (float)skillCost
        ));
    }

    public void upgradeBranch(){
        swirdy_melee = addUpgrade("swirdy-melee", "", ItemStack.with(Items.copper, 100, Items.lead, 80, Items.graphite, 60, Items.silicon, 50, Items.titanium, 60), (SwirdyTurretBuild build) -> build.meleeStateUnlock = true, (SwirdyTurretBuild build) -> build.meleeStateUnlock = false);
        swirdy_melee_reload0 = addUpgrade("swirdy-melee-reload-0", "", ItemStack.with(Items.copper, 120, Items.lead, 80, Items.graphite, 80), (SwirdyTurretBuild build) -> build.meleeReloadTier++, (SwirdyTurretBuild build) -> build.meleeReloadTier = 0).requires(swirdy_melee);
        swirdy_melee_reload1 = addUpgrade("swirdy-melee-reload-1", "", ItemStack.with(Items.lead, 120, Items.graphite, 100, Items.metaglass, 60, Items.silicon, 80, Items.titanium, 80), (SwirdyTurretBuild build) -> build.meleeReloadTier++, (SwirdyTurretBuild build) -> build.meleeReloadTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_reload0);
        swirdy_melee_reload2 = addUpgrade("swirdy-melee-reload-2", "", ItemStack.with(Items.graphite, 150, Items.silicon, 120, Items.titanium, 150, Items.thorium, 80, Items.plastanium, 60), (SwirdyTurretBuild build) -> build.meleeReloadTier++, (SwirdyTurretBuild build) -> build.meleeReloadTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_reload1);
        swirdy_melee_tier0 = addUpgrade("swirdy-melee-tier-0", "", ItemStack.with(Items.copper, 150, Items.graphite, 120, Items.silicon, 100, Items.titanium, 100), (SwirdyTurretBuild build) -> build.meleeTier++, (SwirdyTurretBuild build) -> build.meleeTier = 0).requires(swirdy_melee);
        swirdy_melee_tier1 = addUpgrade("swirdy-melee-tier-1", "", ItemStack.with(Items.graphite, 150, Items.silicon, 150, Items.titanium, 200, Items.thorium, 100), (SwirdyTurretBuild build) -> build.meleeTier++, (SwirdyTurretBuild build) -> build.meleeTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_tier0);
        swirdy_melee_tier2 = addUpgrade("swirdy-melee-tier-2", "", ItemStack.with(Items.silicon, 200, Items.titanium, 300, Items.thorium, 200, Items.plastanium, 120), (SwirdyTurretBuild build) -> build.meleeTier++, (SwirdyTurretBuild build) -> build.meleeTier = 0).requires(swirdy_melee).levelAfter(swirdy_melee_tier1);
        swirdy_range_tier0 = addUpgrade("swirdy-range-tier-0", "", ItemStack.with(Items.copper, 100, Items.lead, 100, Items.graphite, 100), (SwirdyTurretBuild build) -> build.rangeTier++, (SwirdyTurretBuild build) -> build.rangeTier = 0);
        swirdy_range_tier1 = addUpgrade("swirdy-range-tier-1", "", ItemStack.with(Items.lead, 100, Items.graphite, 120, Items.silicon, 80, Items.titanium, 100), (SwirdyTurretBuild build) -> build.rangeTier++, (SwirdyTurretBuild build) -> build.rangeTier = 0).levelAfter(swirdy_range_tier0);
        swirdy_range_tier2 = addUpgrade("swirdy-range-tier-2", "", ItemStack.with(Items.graphite, 150, Items.silicon, 120, Items.titanium, 250), (SwirdyTurretBuild build) -> build.rangeTier++, (SwirdyTurretBuild build) -> build.rangeTier = 0).levelAfter(swirdy_range_tier1);
        swirdy_reload_tier0 = addUpgrade("swirdy-reload-tier-0", "", ItemStack.with(Items.copper, 100, Items.lead, 80, Items.graphite, 120), (SwirdyTurretBuild build) -> build.reloadTier++, (SwirdyTurretBuild build) -> build.reloadTier = 0);
        swirdy_reload_tier1 = addUpgrade("swirdy-reload-tier-1", "", ItemStack.with(Items.lead, 100, Items.graphite, 120, Items.silicon, 80, Items.titanium, 100), (SwirdyTurretBuild build) -> build.reloadTier++, (SwirdyTurretBuild build) -> build.reloadTier = 0).levelAfter(swirdy_reload_tier0);
        swirdy_reload_tier2 = addUpgrade("swirdy-reload-tier-2", "", ItemStack.with(Items.graphite, 150, Items.silicon, 120, Items.titanium, 250), (SwirdyTurretBuild build) -> build.reloadTier++, (SwirdyTurretBuild build) -> build.reloadTier = 0).levelAfter(swirdy_reload_tier1);
        swirdy_tier0 = addUpgrade("swirdy-tier-0", "", ItemStack.with(Items.copper, 150, Items.lead, 100, Items.graphite, 150), (SwirdyTurretBuild build) -> build.Tier++, (SwirdyTurretBuild build) -> build.Tier = 0);
        swirdy_tier1 = addUpgrade("swirdy-tier-1", "", ItemStack.with(Items.graphite, 150, Items.silicon, 100, Items.titanium, 150), (SwirdyTurretBuild build) -> build.Tier++, (SwirdyTurretBuild build) -> build.Tier = 0).levelAfter(swirdy_tier0);
        swirdy_tier2 = addUpgrade("swirdy-tier-2", "", ItemStack.with(Items.graphite, 150, Items.silicon, 150, Items.titanium, 250, Items.thorium, 150), (SwirdyTurretBuild build) -> build.Tier++, (SwirdyTurretBuild build) -> build.Tier = 0).levelAfter(swirdy_tier1);
        swirdy_melee_passive_skill = addUpgrade("swirdy-melee-passive-skill", "", ItemStack.with(promotionStar(), 15, Items.silicon, 200, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100), (SwirdyTurretBuild build) -> build.meleeSkillUnlock = true, (SwirdyTurretBuild build) -> build.meleeSkillUnlock = false).requires(swirdy_melee, swirdy_melee_tier0);
        swirdy_passive_skill = addUpgrade("swirdy-passive-skill", "", ItemStack.with(promotionStar(), 15, Items.silicon, 200, Items.titanium, 250, Items.thorium, 150, Items.plastanium, 100), (SwirdyTurretBuild build) -> build.skillUnlock = true, (SwirdyTurretBuild build) -> build.skillUnlock = false);
    }

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

        public int points;

        protected float atkCounter, modeTimer, refreshTimer, slashTimer, skillTimer, skillBase;
        protected int slashQueue, slashChain, skillStage, skillCount;
        protected boolean skillActive, skillMelee;

        protected boolean skillCharging, skillOriginMelee;
        protected float skillSwitchTimer;
        protected float chargeProgress, chargeNeed;

        protected SwirdyWave chainAmmo, skillAmmo;
        protected final ObjectSet<Object> skillHit = new ObjectSet<>();
        
        @Override
        public void getStats(Table table) {
            addStat(table, NevixStat.generals, "", Pal.accent);
            addStat(table, Stat.health, "" + Mathf.round(maxHealth()));

            SwirdyWave a = curAmmo();
            
            float rangedBase = rangedTiles * tilesize;
            float rangedBoostPx = range_boost[ti(rangeTier)] + (a != null ? a.rangeBonus : 0f);
            int rangedTotal = Mathf.round((rangedBase + rangedBoostPx) / tilesize);
            int rangedBonus = Mathf.round(rangedBoostPx / tilesize);
            addStat(table, Stat.range, rangedTotal + " " + NevixStatUnit.get(NevixStatUnit.tile)
            + (rangedBonus > 0 ? " [gray](+" + rangedBonus + " " + NevixStatUnit.get(NevixStatUnit.tile) + ")" : ""));
            
            float rl = reload_new[ti(reloadTier)];
            float rlDelta = (reload_new[0] - rl) / 60f;
            addStat(table, Stat.reload, (Mathf.round(rl / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds)
            + (rlDelta > 0.01f ? " [gray](-" + (Mathf.round(rlDelta * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds) + ")" : ""));
            
            if (meleeStateUnlock) {
                addStat(table, NevixStat.meleeRange, Mathf.round(meleeRange() / tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile));

                float ml = melee_reload[ti(meleeReloadTier)];
                float mlDelta = (melee_reload[0] - ml) / 60f;
                addStat(table, NevixStat.meleeReload, (Mathf.round(ml / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds)
                + (mlDelta > 0.01f ? " [gray](-" + (Mathf.round(mlDelta * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds) + ")" : ""));
            }
            
            addStat(table, NevixStat.none, "");
            addStat(table, NevixStat.ammo, "", Pal.accent);
            if (a != null) {
                float rangedDmg = damage[ti(Tier)] * a.dmgMul;
                float rangedDmgBase = damage[0] * a.dmgMul;
                float rangedDmgBonus = rangedDmg - rangedDmgBase;

                float meleeDmg = melee_damage[ti(meleeTier)] * a.dmgMul;
                float meleeDmgBase = melee_damage[0] * a.dmgMul;
                float meleeDmgBonus = meleeDmg - meleeDmgBase;

                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, Stat.damage, "" + (Mathf.round(rangedDmg * 10f) / 10f)
                    + (rangedDmgBonus > 0.05f ? " [gray](+" + (Mathf.round(rangedDmgBonus * 10f) / 10f) + ")" : ""));

                    if (meleeStateUnlock) {
                        addBulletStat(bt, NevixStat.meleeDamage, "" + (Mathf.round(meleeDmg * 10f) / 10f)
                        + (meleeDmgBonus > 0.05f ? " [gray](+" + (Mathf.round(meleeDmgBonus * 10f) / 10f) + ")" : ""));
                    }
                });
            }
            
            boolean hasSpecial = meleeStateUnlock || meleeSkillUnlock || skillUnlock || (a != null && a.finalChance > 0f);
            if (hasSpecial) {
                addStat(table, NevixStat.none, "");
                addStat(table, NevixStat.special, "", Pal.accent);

                if (meleeStateUnlock) {
                    addBulletStatTable(table, bt -> {
                        float bscTier = melee_bonus_slash_chance[ti(meleeTier)];
                        float bscAmmo = a != null ? a.bonusSlashChance : 0f;
                        float bscTotal = bscTier + bscAmmo;

                        if (bscTotal > 0f) {
                            boolean showPlus = bscAmmo > 0f && bscTier > 0f;
                            addBulletStat(bt, NevixStat.bonusSlashChance, Mathf.round(bscTotal * 100f) + "%"
                            + (showPlus ? " [gray](+" + Mathf.round(bscAmmo * 100f) + "%)" : ""));
                        }
                        addBulletStat(bt, NevixStat.meleeLifesteal, Mathf.round(meleeLifesteal * 100f) + "%");
                        addBulletStat(bt, NevixStat.none, "swirdyMelee");
                    });
                }
                
                if (a != null && a.finalChance > 0f) {
                    addBulletStatTable(table, bt -> {
                        addBulletStat(bt, NevixStat.finalWaveChance, Mathf.round(a.finalChance * 100f) + "%");
                        addBulletStat(bt, NevixStat.none, "swirdyFinalWave");
                    });
                }

                if (skillUnlock || meleeSkillUnlock) {
                    addBulletStatTable(table, bt -> {
                        addBulletStat(bt, NevixStat.skillCost, skillCost + " " + NevixStatUnit.get(NevixStatUnit.points));
                        if (skillUnlock) addBulletStat(bt, NevixStat.none, "swirdyRangedSkill");
                        if (meleeSkillUnlock) addBulletStat(bt, NevixStat.none, "swirdyMeleeSkill");
                    });
                }
            }
        }

        public float meleeRange(){
            return meleeTiles * tilesize;
        }

        protected int ti(int t){
            return Mathf.clamp(t, 0, 2);
        }

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

        protected SwirdyWave curAmmo(){
            if(!hasAmmo()) return null;
            BulletType t = peekAmmo();
            return t instanceof SwirdyWave ? (SwirdyWave)t : null;
        }

        @Override
        public float range(){
            return meleeState ? meleeRange() : rangedRange();
        }

        public float rangedRange(){
            SwirdyWave a = curAmmo();
            return rangedTiles * tilesize + range_boost[ti(rangeTier)] + (a != null ? a.rangeBonus : 0f);
        }

        @Override
        public void drawSelect(){
            float ranged = rangedRange();
            float melee = meleeRange();

            if(meleeState){
                Drawf.dashCircle(x, y, ranged, Color.green);
                Drawf.dashCircle(x, y, melee, Pal.accent);
            }else{
                if(meleeStateUnlock) Drawf.dashCircle(x, y, melee, Color.green);
                Drawf.dashCircle(x, y, ranged, Pal.accent);
            }
        }

        protected float curReload(){
            return meleeState ? melee_reload[ti(meleeReloadTier)] : reload_new[ti(reloadTier)];
        }

        @Override
        public void updateTile(){
            refreshTimer += Time.delta;
            haloFade = Mathf.lerpDelta(haloFade, target != null ? 1f : 0f, 0.12f);
            if(refreshTimer >= 30f){
                refreshTimer = 0f;
                refreshUpgrades();
            }
            
            if(net.client()){
                if(skillActive && skillCharging) chargeProgress = Math.min(chargeProgress + delta(), chargeNeed);
                if(skillCharging && target != null) skillBase = angleTo(target);
                super.updateTile();
                return;
            }

            float dt = delta() * efficiency;

            if(!skillActive) updateMode();

            SwirdyWave a = curAmmo();
            if(atkCounter < 10000f) atkCounter += dt * (a != null ? a.reloadMul : 1f);

            super.updateTile();

            updateSlashQueue(dt);
            updateSkill(dt);
            syncNet();
        }

        protected float netSyncTimer, netResendTimer;
        protected int lastNetSig = -1;
        
        protected void syncNet(){
            if(!net.server()) return;
            netSyncTimer += Time.delta;
            netResendTimer += Time.delta;
            if(netSyncTimer < 10f) return;
            netSyncTimer = 0f;

            int sig = (meleeState ? 1 : 0) | (skillActive ? 2 : 0) | (points << 2);
            if(sig != lastNetSig || netResendTimer >= 300f){
                lastNetSig = sig;
                netResendTimer = 0f;
                swirdySend(true, netState, meleeState ? 1 : 0, points, skillActive ? 1 : 0);
            }
        }

        protected void swirdySend(boolean reliable, int op, Object... args){
            if(!net.server()) return;
            StringBuilder sb = new StringBuilder();
            sb.append(pos()).append(';').append(op);
            for(Object o : args) sb.append(';').append(o);
            if(reliable) Call.clientPacketReliable(netChannel, sb.toString());
            else Call.clientPacketUnreliable(netChannel, sb.toString());
        }

        protected SwirdyWave[] ammoWaves(){
            return new SwirdyWave[]{leadAmmo, titaniumAmmo, thoriumAmmo, surgeAmmo};
        }
        
        protected int waveCode(BulletType t){
            SwirdyWave[] arr = ammoWaves();
            for(int i = 0; i < arr.length; i++){
                if(arr[i] == t) return i * 3;
                if(arr[i].finalWave == t) return i * 3 + 1;
                if(arr[i].knockWave == t) return i * 3 + 2;
            }
            return -1;
        }

        protected SwirdyWave waveOf(int code){
            SwirdyWave[] arr = ammoWaves();
            SwirdyWave a = arr[Mathf.clamp(code / 3, 0, arr.length - 1)];
            return switch(code % 3){
                case 1 -> a.finalWave;
                case 2 -> a.knockWave;
                default -> a;
            };
        }
        
        public void swirdyNet(String[] t){
            int op = Integer.parseInt(t[1]);
            switch(op){
                case netSlash -> {
                    int pal = Integer.parseInt(t[5]);
                    slashFx.at(x, y, Float.parseFloat(t[7]), palette(pal).light,
                        new float[]{Float.parseFloat(t[2]), Float.parseFloat(t[3]), Float.parseFloat(t[4]), pal, Float.parseFloat(t[6])});
                }
                case netLine -> {
                    int pal = Integer.parseInt(t[4]);
                    lineSlashFx.at(x, y, Float.parseFloat(t[2]), palette(pal).light,
                        new float[]{Float.parseFloat(t[3]), pal, Float.parseFloat(t[5])});
                    Effect.shake(6.5f, 14f, x, y);
                }
                case netWave -> {
                    int code = Integer.parseInt(t[2]);
                    float ang = Float.parseFloat(t[3]);
                    BulletType type = waveOf(code);
                    Tmp.v1.trns(ang, size * tilesize / 2f);
                    type.create(this, team, x + Tmp.v1.x, y + Tmp.v1.y, ang, 0f, 1f, 1f, null);
                    if(code % 3 == 1) Effect.shake(5.5f, 12f, x, y);
                }
                case netCharge -> {
                    if(Integer.parseInt(t[2]) == 1){
                        skillActive = true;
                        skillCharging = true;
                        chargeNeed = Math.max(1f, Float.parseFloat(t[3]));
                        chargeProgress = 0f;
                        skillAmmo = ammoWaves()[Mathf.clamp(Integer.parseInt(t[4]), 0, ammoWaves().length - 1)];
                        skillBase = Float.parseFloat(t[5]);
                    }else{
                        skillCharging = false;
                        chargeProgress = 0f;
                    }
                }
                case netState -> {
                    meleeState = Integer.parseInt(t[2]) == 1;
                    points = Integer.parseInt(t[3]);
                    skillActive = Integer.parseInt(t[4]) == 1;
                    if(!skillActive){
                        skillCharging = false;
                        chargeProgress = 0f;
                    }
                }
                case netAmmo -> {
                    if(hasAmmo()) useAmmo();
                }
                case netHit -> starHitFxs[Mathf.clamp(Integer.parseInt(t[5]), 0, palettes.length - 1)]
                    .at(Float.parseFloat(t[2]), Float.parseFloat(t[3]), Float.parseFloat(t[4]));
            }
        }

        protected void updateMode(){
            modeTimer += Time.delta;
            if(modeTimer < 4f) return;
            modeTimer = 0f;

            boolean was = meleeState;
            Teamc t = meleeStateUnlock ? closestMeleeEnemy() : null;

            if(t == null){
                meleeState = false;
                slashQueue = 0;
                if(was) target = null;
            }else{
                meleeState = true;
                if(target == null || !target.within(this, meleeRange() + 8f)){
                    target = t;
                }
            }

            if(was != meleeState){
                atkCounter = Math.min(atkCounter, curReload());
            }
        }

        @Override
        protected void updateShooting(){
            if(net.client() || skillActive || target == null || atkCounter < curReload()) return;

            SwirdyWave a = curAmmo();
            if(a == null) return;

            if(meleeState){
                if(slashQueue > 0 || !target.within(this, meleeRange() + 16f)) return;
                useAmmo();
                swirdySend(true, netAmmo);
                chainAmmo = a;
                atkCounter = 0f;
                slashQueue = 1;
                slashChain = 0;
                slashTimer = 0f;
            }else{
                useAmmo();
                swirdySend(true, netAmmo);
                atkCounter = 0f;
                float ang = angleTo(target);
                fireWave(a, ang, damage[ti(Tier)] * a.dmgMul);
                swingFx(ang, 56f * a.sizeMul, 40f, a);
                
                if(a.finalChance > 0f && Mathf.chance(a.finalChance)){
                    fireWave(a.finalWave, ang, damage[ti(Tier)] * 30f * a.dmgMul);
                    swingFx(ang, 96f * a.sizeMul, 55f, a);
                }
            }
        }

        protected void updateSlashQueue(float dt){
            if(slashQueue <= 0 || skillActive) return;
            slashTimer -= dt;
            if(slashTimer > 0f) return;

            if(target == null || chainAmmo == null){
                slashQueue = 0;
                return;
            }

            coneSlash(angleTo(target), melee_damage[ti(meleeTier)] * chainAmmo.dmgMul, chainAmmo);
            slashQueue--;

            if(slashChain < 10 && Mathf.chance(melee_bonus_slash_chance[ti(meleeTier)] + chainAmmo.bonusSlashChance)){
                slashQueue++;
                slashChain++;
            }
            slashTimer = 9f;
        }

        protected void coneSlash(float ang, float dmg, SwirdyWave a){
            float half = a.slashHalf;
            swingFx(ang, meleeRange(), half, a);
            coneDamage(ang, half, meleeRange(), dmg, a.pal);
        }

        protected void swingFx(float ang, float range, float half, SwirdyWave a){
            float dir = Mathf.randomSign();
            slashFx.at(x, y, ang, palette(a.pal).light, new float[]{dir, range, half, a.pal, a.sizeMul});
            swirdySend(false, netSlash, dir, range, half, a.pal, a.sizeMul, ang);
        }

        protected void fireWave(BulletType type, float ang, float dmg){
            Tmp.v1.trns(ang, size * tilesize / 2f);
            type.create(this, team, x + Tmp.v1.x, y + Tmp.v1.y, ang, dmg, 1f, 1f, null);
            int code = waveCode(type);
            if(code >= 0) swirdySend(false, netWave, code, ang);
        }

        protected void coneDamage(float ang, float half, float rng, float dmg, int pal){
            Units.nearbyEnemies(team, x - rng, y - rng, rng * 2f, rng * 2f, u -> {
                if(!u.isValid() || !u.checkTarget(targetAir, targetGround)) return;
                float d = Math.max(dst(u), 1f);
                if(d - u.hitSize / 2f > rng) return;
                float slack = (float)Math.toDegrees(Math.atan2(u.hitSize / 2f, d));
                if(!Angles.within(angleTo(u), ang, half + slack)) return;
                hitUnit(u, ang, dmg, pal);
            });

            if(targetGround){
                indexer.allBuildings(x, y, rng, b -> {
                    if(b.team == team || b.team == mindustry.game.Team.derelict) return;
                    float d = Math.max(dst(b), 1f);
                    float slack = (float)Math.toDegrees(Math.atan2(b.block.size * tilesize / 2f, d));
                    if(!Angles.within(angleTo(b), ang, half + slack)) return;
                    hitBuild(b, ang, dmg, pal);
                });
            }
        }

        protected void lineDamage(float ang, float len, float width, float dmg, boolean reduceRepeat, int pal){
            float dx = Mathf.cosDeg(ang), dy = Mathf.sinDeg(ang);

            Units.nearbyEnemies(team, x - len, y - len, len * 2f, len * 2f, u -> {
                if(!u.isValid() || !u.checkTarget(targetAir, targetGround)) return;
                if(!nearLine(u.x, u.y, dx, dy, len, width + u.hitSize / 2f)) return;
                float d = dmg;
                if(reduceRepeat && !skillHit.add(u)) d *= 0.5f;
                hitUnit(u, ang, d, pal);
            });

            if(targetGround){
                indexer.allBuildings(x, y, len, b -> {
                    if(b.team == team || b.team == mindustry.game.Team.derelict) return;
                    if(!nearLine(b.x, b.y, dx, dy, len, width + b.block.size * tilesize / 2f)) return;
                    float d = dmg;
                    if(reduceRepeat && !skillHit.add(b)) d *= 0.5f;
                    hitBuild(b, ang, d, pal);
                });
            }
        }

        protected boolean nearLine(float px, float py, float dx, float dy, float len, float rad){
            float along = Mathf.clamp((px - x) * dx + (py - y) * dy, 0f, len);
            float cx = x + dx * along, cy = y + dy * along;
            return Mathf.dst2(px, py, cx, cy) <= rad * rad;
        }

        protected void hitUnit(Unit u, float ang, float dmg, int pal){
            float before = u.health + u.shield;
            u.damage(dmg);
            float dealt = Math.min(before, Math.max(0f, before - (u.health + u.shield)));
            afterMeleeHit(u.x, u.y, ang, dealt, pal);
        }

        protected void hitBuild(Building b, float ang, float dmg, int pal){
            float before = b.health;
            b.damage(team, dmg);
            float dealt = Math.min(before, Math.max(0f, before - b.health));
            afterMeleeHit(b.x, b.y, ang, dealt, pal);
        }

        protected void afterMeleeHit(float hx, float hy, float ang, float dealt, int pal){
            starHitFxs[Mathf.clamp(pal, 0, palettes.length - 1)].at(hx, hy, ang);
            swirdySend(false, netHit, hx, hy, ang, pal);
            if(dealt > 0f) heal(dealt * meleeLifesteal);
            gainPoint();
        }

        public void gainPoint(){
            if(net.client() || skillActive || !(skillUnlock || meleeSkillUnlock)) return;
            points = Math.min(points + 1, skillCost);
        }

        protected void updateSkill(float dt){
            if(!skillActive){
                if(points >= skillCost && target != null && curAmmo() != null){
                    if(meleeState && meleeSkillUnlock) startSkill(true);
                    else if(!meleeState && skillUnlock) startSkill(false);
                }
                return;
            }

            updateSkillMode();

            if(skillCharging){
                updateCharge(dt);
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
            skillCharging = false;
            skillOriginMelee = melee;
            skillSwitchTimer = 0f;
            chargeProgress = 0f;
            skillAmmo = curAmmo();
            skillBase = angleTo(target);
            skillCount = melee ? Mathf.random(3, 15) : 5;
        }

        protected void endSkill(){
            skillActive = false;
            skillCharging = false;
            chargeProgress = 0f;
            swirdySend(true, netCharge, 0, 0, 0, 0);
            atkCounter = 0f;
            skillHit.clear();
        }

        protected Teamc closestMeleeEnemy(){
            return Units.closestTarget(team, x, y, meleeRange(),
                u -> u.checkTarget(targetAir, targetGround), b -> targetGround);
        }

        protected void rangedSkillStep(float ang){
            SwirdyWave a = skillAmmo;
            fireWave(a, ang, damage[ti(Tier)] * 3f * a.dmgMul);
            swingFx(ang, 56f * a.sizeMul, 40f, a);
            if(--skillCount <= 0){
                beginCharge(rangedChargeTicks);
            }else{
                skillTimer = 7f;
            }
        }

        protected void meleeSkillStep(float ang){
            SwirdyWave a = skillAmmo;
            switch(skillStage){
                case 0 -> {
                    coneSlash(ang, melee_damage[ti(meleeTier)] * 3f * a.dmgMul, a);
                    skillTimer = 4f;
                    if(--skillCount <= 0) skillStage = 1;
                }
                case 1 -> {
                    fireWave(a.knockWave, ang, 0f);
                    swingFx(ang, meleeRange() * 0.9f, 40f, a);
                    beginCharge(meleeChargeTicks);
                }
            }
        }

        protected void beginCharge(float need){
            skillCharging = true;
            chargeNeed = need;
            chargeProgress = 0f;
            swirdySend(true, netCharge, 1, need, skillAmmo != null ? skillAmmo.pal : 0, skillBase);
        }

        protected void updateSkillMode(){
            skillSwitchTimer -= Time.delta;
            modeTimer += Time.delta;
            if(modeTimer < 4f) return;
            modeTimer = 0f;
            if(skillSwitchTimer > 0f) return;

            Teamc t = meleeStateUnlock ? closestMeleeEnemy() : null;
            boolean wantMelee = t != null && meleeSkillUnlock;
            if(wantMelee == skillMelee) return;

            skillMelee = wantMelee;
            meleeState = wantMelee;
            target = wantMelee ? t : null;
            skillSwitchTimer = 15f;
            slashQueue = 0;
            beginCharge(wantMelee ? meleeChargeTicks : rangedChargeTicks);
        }

        protected void updateCharge(float dt){
            Posc en = skillMelee ? closestMeleeEnemy() : target;

            if(en == null){
                if(chargeProgress > 0f){
                    chargeProgress = 0f;
                    swirdySend(true, netCharge, 1, chargeNeed, skillAmmo != null ? skillAmmo.pal : 0, skillBase);
                }
                return;
            }

            skillBase = angleTo(en);
            chargeProgress += dt;
            if(chargeProgress >= chargeNeed){
                skillCharging = false;
                releaseFinal();
            }
        }

        protected void releaseFinal(){
            SwirdyWave a = skillAmmo;
            float ang = skillBase;

            if(skillMelee){
                float len = meleeRange() * 1.8f;
                float dmg = melee_damage[ti(meleeTier)] * 30f * a.dmgMul;

                for(int i = -1; i <= 1; i++){
                    float a2 = ang + i * 40f;
                    lineDamage(a2, len, 14f * a.sizeMul, dmg, true, a.pal);
                    lineSlashFx.at(x, y, a2, palette(a.pal).light, new float[]{len, a.pal, a.sizeMul});
                    swirdySend(false, netLine, a2, len, a.pal, a.sizeMul);
                }
                Effect.shake(6.5f, 14f, x, y);
            }else{

                float dmg = (skillOriginMelee ? melee_damage[ti(meleeTier)] * 45f : damage[ti(Tier)] * 30f) * a.dmgMul;
                fireWave(a.finalWave, ang, dmg);
                swingFx(ang, 96f * a.sizeMul, 55f, a);
                Effect.shake(5.5f, 12f, x, y);
            }
            endSkill();
        }

        protected float haloFade;

        @Override
        public void draw(){
            super.draw();
            if(haloFade > 0.01f){
                SwirdyWave ha = curAmmo();
                if(ha != null){
                    float hx = x - Angles.trnsx(rotation, tilesize * 0.5f);
                    float hy = y - Angles.trnsy(rotation, tilesize * 0.5f);
                    drawHalo(palette(ha.pal), hx, hy, rotation, Time.time, haloFade);
                }
            }
            if(skillActive && skillCharging && chargeProgress > 0f && skillAmmo != null){
                drawCharge(palette(skillAmmo.pal), x, y, skillBase, Mathf.clamp(chargeProgress / chargeNeed));
            }
        }

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