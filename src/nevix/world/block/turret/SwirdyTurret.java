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
import mindustry.gen.Posc;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;

import static mindustry.Vars.indexer;
import static mindustry.Vars.tilesize;

public class SwirdyTurret extends NevixTurret {
    public static final int skillCost = 100;

    /** Tầm đánh xa mặc định (tile) và tầm cận chiến (tile). */
    public float rangedTiles = 23f, meleeTiles = 9f;

    /** Tỉ lệ hồi máu theo sát thương gây ra bởi đòn cận chiến. */
    public float meleeLifesteal = 0.02f;

    /** Ba loại đạn: chì (tím), titanium (xanh), thorium (hồng đỏ). */
    public SwirdyWave leadAmmo, titaniumAmmo, thoriumAmmo;

    // ───────────────────────── Bảng màu theo đạn ─────────────────────────

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
        new SwirdyPalette("5B2BD6", "B58CFF", "F3EAFF", "C79BFF", "FFFFFF", "B58CFF"), // 0: chì - tím
        new SwirdyPalette("2D6BFF", "6FD0FF", "FFFFFF", "9FE3FF", "FFFFFF", "7CC8FF"), // 1: titanium - xanh
        new SwirdyPalette("D81B4A", "FF6FA8", "FFE9F0", "FF4466", "FFB0C8", "FF6A8E")  // 2: thorium - hồng/đỏ
    };

    public static SwirdyPalette palette(int i){
        return palettes[Mathf.clamp(i, 0, palettes.length - 1)];
    }

    // ───────────────────────── Vẽ kiếm & kiếm khí ─────────────────────────

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

    /** Lưỡi liềm 3 lớp + bụi sao (+ vệt rách chữ X nếu tear). (x, y) là tâm cung tròn. */
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

    /** Kích thước kiếm cố định cho mọi loại đạn: cao 2 tile (tổng chiều dài), rộng 1 tile (chắn kiếm). */
    public static final float swordLength = 2f * tilesize, swordWidth = 1f * tilesize;

    /**
     * Thanh kiếm mảnh, mọi chi tiết cùng một tông màu của bảng màu đạn.
     * (x, y) là vị trí chắn kiếm; lưỡi chỉ về hướng ang, chuôi nằm phía sau.
     */
    public static void drawSword(float x, float y, float ang, float fade, SwirdyPalette p){
        if(fade <= 0.01f) return;
        float grip = swordLength * 0.19f;
        float blade = swordLength - grip;
        float w = swordWidth * 0.14f;   // nửa bề rộng chân lưỡi (mảnh)
        float shoulder = blade * 0.82f;
        float tipW = w * 0.55f;

        // Hào quang mờ quanh lưỡi
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

        // Mặt sáng (trái)
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

        // Mặt tối (phải)
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

        // Sống kiếm
        Draw.color(p.core, fade);
        Lines.stroke(Math.max(0.8f, w * 0.25f));
        Lines.line(sx(x, ang, blade * 0.06f, 0f), sy(y, ang, blade * 0.06f, 0f), sx(x, ang, blade * 0.97f, 0f), sy(y, ang, blade * 0.97f, 0f));

        // Chắn kiếm: rộng đúng 1 tile, mỏng
        float gt = 0.65f, gs = swordWidth / 2f;
        Tmp.c3.set(p.mid).lerp(p.outer, 0.25f);
        Draw.color(Tmp.c3, fade);
        Fill.quad(
            sx(x, ang, -gt, -gs), sy(y, ang, -gt, -gs),
            sx(x, ang, -gt, gs), sy(y, ang, -gt, gs),
            sx(x, ang, gt, gs), sy(y, ang, gt, gs),
            sx(x, ang, gt, -gs), sy(y, ang, gt, -gs)
        );

        // Chuôi + đầu chuôi
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

    // ───────────────────────── Effects ─────────────────────────

    /**
     * Nhát vung kiếm: thanh kiếm cố định 1x2 tile quét quanh tháp và để lại các tàn ảnh.
     * Mỗi tàn ảnh được kéo thành một vệt sáng mảnh toả ra ngoài, nhiều vệt xếp thành hình quạt.
     * data = float[]{dir(+1/-1), range, halfAngle, paletteId, sizeMul}
     */
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

        int ghosts = 9;
        for(int i = 0; i <= ghosts; i++){
            float u = i / (float)ghosts; // 0 = tàn ảnh cũ nhất ... 1 = vị trí kiếm hiện tại
            float a = start - dir * sweep * u;
            float alpha = ghostFade * (0.2f + 0.8f * u * u);
            if(alpha <= 0.02f) continue;

            // Vệt sáng mảnh: hình thoi dài, rộng nhất ở khoảng 30% chiều dài, mảnh dần ở tàn ảnh cũ
            float len = baseR + Math.max(0f, range - baseR) * expand * (0.6f + 0.4f * u);
            float L = Math.max(1f, len - handR);
            float cx = e.x + Angles.trnsx(a, handR + L * 0.3f);
            float cy = e.y + Angles.trnsy(a, handR + L * 0.3f);
            float wid = (0.5f + 0.5f * u) * ws;

            Draw.z(Layer.bullet + 3f);
            Draw.blend(Blending.additive);
            Draw.color(p.mid, 0.7f * alpha);
            Drawf.tri(cx, cy, 5f * wid, L * 0.7f, a);
            Drawf.tri(cx, cy, 5f * wid, L * 0.3f, a + 180f);
            Draw.color(p.core, 0.9f * alpha);
            Drawf.tri(cx, cy, 2f * wid, L * 0.66f, a);
            Drawf.tri(cx, cy, 2f * wid, L * 0.26f, a + 180f);
            Draw.blend();

            // Tàn ảnh của thanh kiếm (mờ hơn kiếm thật)
            if(i < ghosts){
                Draw.z(Layer.bullet + 3.2f);
                drawSword(e.x + Angles.trnsx(a, handR), e.y + Angles.trnsy(a, handR), a, alpha * 0.55f, p);
            }
        }

        Draw.reset();

        // Kiếm thật
        Draw.z(Layer.bullet + 3.5f);
        drawSword(e.x + Angles.trnsx(cur, handR), e.y + Angles.trnsy(cur, handR), cur, swordFade, p);

        float lr = baseR + Math.max(0f, range - baseR) * expand;
        Drawf.light(e.x + Angles.trnsx(cur, lr * 0.5f), e.y + Angles.trnsy(cur, lr * 0.5f), lr, p.light, 0.8f * ghostFade);
    });

    /**
     * Đường kiếm khí thẳng dài (3 nhát cuối của cận chiến): lưỡi kiếm khổng lồ phóng ra rất nhanh.
     * data = float[]{length, paletteId, widthScale}
     */
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

        // Hình thoi dài như một lưỡi kiếm: rộng nhất gần chân, thu nhọn về mũi
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

    /** Vẽ hiệu ứng tụ lực theo tiến độ thật (fr = 0..1): ấn tròn co lại + sao 4 cánh xoay. */
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

    /** Bùng nổ sao 4 cánh khi chém trúng, một hiệu ứng cho mỗi bảng màu. */
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

    // ───────────────────────── Bullet ─────────────────────────

    /**
     * Luồng kiếm khí lưỡi liềm, xuyên không giới hạn (cả địch lẫn công trình).
     * Va chạm tính theo đúng hình lưỡi liềm đang vẽ: chạm vào bất kì phần nào của kiếm khí là trúng.
     * Đối tượng đăng ký làm đạn của ItemTurret mang thêm hệ số của loại đạn đó
     * và hai đạn kĩ năng (finalWave, knockWave) cùng màu, cùng cỡ.
     */
    public static class SwirdyWave extends BasicBulletType {
        public float arcSize;
        public float half = 45f;
        public boolean tear;
        public int pal;

        public float dmgMul = 1f, sizeMul = 1f, reloadMul = 1f;
        /** Nửa góc chém cận chiến của loại đạn này (tổng hai bên = 2 × giá trị). */
        public float slashHalf = 30f;
        /** Phạm vi cộng thêm (đơn vị thế giới) cho dạng đánh xa khi dùng loại đạn này. */
        public float rangeBonus = 0f;
        public SwirdyWave finalWave, knockWave;

        public SwirdyWave(float speed, float damage, float arcSize, float lifetime, boolean tear, int pal){
            super(speed, damage);
            this.arcSize = arcSize;
            this.tear = tear;
            this.pal = pal;
            this.lifetime = lifetime;

            // Va chạm tự xử lí trong update() theo hình lưỡi liềm, không dùng hitbox vuông của engine
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

        /** Vòng cung (bán kính rad quanh tâm cx,cy) có chạm vật thể tròn (px,py,pr) không. */
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
            // tâm cung nằm phía sau đạn, giống lúc vẽ
            float cx = b.x - Angles.trnsx(ang, rad), cy = b.y - Angles.trnsy(ang, rad);
            float reach = rad + 24f;

            Units.nearbyEnemies(b.team, cx - reach, cy - reach, reach * 2f, reach * 2f, u -> {
                if(!u.isValid() || !u.checkTarget(collidesAir, collidesGround) || b.collided.contains(u.id)) return;
                if(!touches(cx, cy, ang, rad, u.x, u.y, u.hitSize / 2f)) return;

                b.collided.add(u.id);
                if(b.damage > 0f) u.damage(b.damage);
                if(knockback != 0f){
                    Tmp.v3.set(u).sub(b).nor().scl(knockback * 80f);
                    if(impact) Tmp.v3.setAngle(ang + (knockback < 0f ? 180f : 0f));
                    u.impulse(Tmp.v3);
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
                    if(dmg > 0f) bd.damage(b.team, dmg);
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
            // tâm cung nằm phía sau đạn để đỉnh lưỡi liềm trùng vị trí đạn
            Tmp.v1.trns(b.rotation(), -rad);
            float cx = b.x + Tmp.v1.x, cy = b.y + Tmp.v1.y;
            drawSlashLayers(p, cx, cy, b.rotation(), rad, half, fade, b.time, arcSize / 20f, tear);
            Drawf.light(b.x, b.y, rad * 1.4f, p.light, 0.6f * fade);
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

        //                      màu  sát thương  độ lớn  tốc độ  +tầm (tile)  góc chém tổng (độ)
        leadAmmo = makeAmmo(0, 0.7f, 0.7f, 1f, 0f, 80f);
        titaniumAmmo = makeAmmo(1, 1f, 1f, 1f, 0f, 100f);
        thoriumAmmo = makeAmmo(2, 1.5f, 1.5f, 1.15f, 5f, 120f); // thorium: +5 tile phạm vi đánh xa

        ammo(
            Items.lead, leadAmmo,
            Items.titanium, titaniumAmmo,
            Items.thorium, thoriumAmmo
        );

        upgradeBranch();
    }

    protected SwirdyWave makeAmmo(int pal, float dmgMul, float sizeMul, float reloadMul, float rangeBonusTiles, float slashTotalDeg){
        SwirdyWave w = new SwirdyWave(9f, damage[0] * dmgMul, 12f * sizeMul, 38f, false, pal);
        w.knockback = 1f;
        w.dmgMul = dmgMul;
        w.sizeMul = sizeMul;
        w.reloadMul = reloadMul;
        w.rangeBonus = rangeBonusTiles * tilesize;
        w.slashHalf = slashTotalDeg / 2f;

        SwirdyWave f = new SwirdyWave(10f, 0f, 34f * sizeMul, 40f, true, pal);
        f.knockback = 2f;

        // Không gây sát thương nhưng đẩy lùi rất mạnh.
        SwirdyWave k = new SwirdyWave(11f, 0f, 22f * sizeMul, 30f, false, pal);
        k.hitEffect = Fx.none; // luồng đẩy lùi không gây sát thương nên không nổ sao
        k.knockback = 8f;
        k.impact = true;
        k.buildingDamageMultiplier = 0f;

        w.finalWave = f;
        w.knockWave = k;
        return w;
    }

    @Override
    public void init(){
        super.init();
        for(SwirdyWave a : new SwirdyWave[]{leadAmmo, titaniumAmmo, thoriumAmmo}){
            a.init();
            a.finalWave.init();
            a.knockWave.init();
        }
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
        /** Đang tụ lực đòn cuối; skillOriginMelee = kĩ năng được kích hoạt ở dạng cận chiến. */
        protected boolean skillCharging, skillOriginMelee;
        protected float skillSwitchTimer;
        protected float chargeProgress, chargeNeed;
        /** Loại đạn dùng cho chuỗi chém cận chiến hiện tại / kĩ năng hiện tại. */
        protected SwirdyWave chainAmmo, skillAmmo;
        protected final ObjectSet<Object> skillHit = new ObjectSet<>();

        public float meleeRange(){
            return meleeTiles * tilesize;
        }

        /** Chống tràn mảng chỉ số tier (mảng chỉ có 3 phần tử). */
        protected int ti(int t){
            return Mathf.clamp(t, 0, 2);
        }

        // ── upgrade ──

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

        // ── đạn ──

        /** Loại đạn đang dùng, null nếu hết đạn. */
        protected SwirdyWave curAmmo(){
            if(!hasAmmo()) return null;
            BulletType t = peekAmmo();
            return t instanceof SwirdyWave ? (SwirdyWave)t : null;
        }

        @Override
        public float range(){
            if(meleeState) return meleeRange();
            SwirdyWave a = curAmmo();
            return rangedTiles * tilesize + range_boost[ti(rangeTier)] + (a != null ? a.rangeBonus : 0f);
        }

        protected float curReload(){
            return meleeState ? melee_reload[ti(meleeReloadTier)] : reload_new[ti(reloadTier)];
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

            // Đạn thorium ra đòn nhanh hơn 15%
            SwirdyWave a = curAmmo();
            if(atkCounter < 10000f) atkCounter += dt * (a != null ? a.reloadMul : 1f);

            super.updateTile();

            updateSlashQueue(dt);
            updateSkill(dt);
        }

        /** Có địch trong tầm cận chiến → cận chiến; không còn địch nào → tự quay về đánh xa. */
        protected void updateMode(){
            modeTimer += Time.delta;
            if(modeTimer < 4f) return;
            modeTimer = 0f;

            boolean was = meleeState;
            Teamc t = meleeStateUnlock ? closestMeleeEnemy() : null;

            if(t == null){
                meleeState = false;
                slashQueue = 0;
                if(was) target = null; // bỏ mục tiêu cận chiến cũ để tìm lại theo tầm đánh xa
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

        // ── attacks ──

        @Override
        protected void updateShooting(){
            if(skillActive || target == null || atkCounter < curReload()) return;

            SwirdyWave a = curAmmo();
            if(a == null) return;

            if(meleeState){
                if(slashQueue > 0 || !target.within(this, meleeRange() + 16f)) return;
                useAmmo(); // mỗi chuỗi chém tốn 1 lượt đạn
                chainAmmo = a;
                atkCounter = 0f;
                slashQueue = 1;
                slashChain = 0;
                slashTimer = 0f;
            }else{
                useAmmo();
                atkCounter = 0f;
                float ang = angleTo(target);
                fireWave(a, ang, damage[ti(Tier)] * a.dmgMul);
                swingFx(ang, 56f * a.sizeMul, 40f, a);
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

            // Nhát bổ sung: cùng tỉ lệ, có thể nối tiếp (tối đa 10 nhát bổ sung)
            if(slashChain < 10 && Mathf.chance(melee_bonus_slash_chance[ti(meleeTier)])){
                slashQueue++;
                slashChain++;
            }
            slashTimer = 9f;
        }

        /** Vung kiếm cận chiến: hướng trái→phải hoặc phải→trái ngẫu nhiên. */
        protected void coneSlash(float ang, float dmg, SwirdyWave a){
            float half = a.slashHalf; // chì 80°, titan 100°, thorium 120° (tổng hai bên); sát thương lan trong cả hình quạt
            swingFx(ang, meleeRange(), half, a);
            coneDamage(ang, half, meleeRange(), dmg, a.pal);
        }

        protected void swingFx(float ang, float range, float half, SwirdyWave a){
            slashFx.at(x, y, ang, palette(a.pal).light, new float[]{Mathf.randomSign(), range, half, a.pal, a.sizeMul});
        }

        protected void fireWave(BulletType type, float ang, float dmg){
            Tmp.v1.trns(ang, size * tilesize / 2f);
            type.create(this, team, x + Tmp.v1.x, y + Tmp.v1.y, ang, dmg, 1f, 1f, null);
        }

        // ── damage helpers (chỉ dùng cho đòn cận chiến → có hồi máu) ──

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
                if(reduceRepeat && !skillHit.add(u)) d *= 0.5f; // trúng >1 đường: các nhát sau -50%
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

        /** Hiệu ứng trúng đòn, hồi máu bằng 2% sát thương thực gây ra, cộng điểm kĩ năng. */
        protected void afterMeleeHit(float hx, float hy, float ang, float dealt, int pal){
            starHitFxs[Mathf.clamp(pal, 0, palettes.length - 1)].at(hx, hy, ang);
            if(dealt > 0f) heal(dealt * meleeLifesteal);
            gainPoint();
        }

        public void gainPoint(){
            if(skillActive || !(skillUnlock || meleeSkillUnlock)) return;
            points = Math.min(points + 1, skillCost);
        }

        // ── skills ──

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
            skillAmmo = curAmmo(); // kĩ năng lấy màu/cỡ theo đạn lúc kích hoạt, không tốn đạn
            skillBase = angleTo(target);
            skillCount = melee ? Mathf.random(3, 15) : 5;
        }

        protected void endSkill(){
            skillActive = false;
            skillCharging = false;
            chargeProgress = 0f;
            atkCounter = 0f;
            skillHit.clear();
        }

        /** Địch gần nhất trong tầm cận chiến (null nếu không có). */
        protected Teamc closestMeleeEnemy(){
            return Units.closestTarget(team, x, y, meleeRange(),
                u -> u.checkTarget(targetAir, targetGround), b -> targetGround);
        }

        /** Bình thường: 5 luồng 300% → tụ lực 45 tick → 1 nhát 3000%. Sát thương kĩ năng không nhân hệ số đạn. */
        protected void rangedSkillStep(float ang){
            SwirdyWave a = skillAmmo;
            fireWave(a, ang, damage[ti(Tier)] * 3f);
            swingFx(ang, 56f * a.sizeMul, 40f, a);
            if(--skillCount <= 0){
                beginCharge(rangedChargeTicks);
            }else{
                skillTimer = 7f;
            }
        }

        /** Cận chiến: 3-15 nhát 300% → luồng đẩy lùi mạnh → tụ lực 30 tick → 3 đường kiếm 3000% chém cùng lúc. */
        protected void meleeSkillStep(float ang){
            SwirdyWave a = skillAmmo;
            switch(skillStage){
                case 0 -> {
                    coneSlash(ang, melee_damage[ti(meleeTier)] * 3f, a);
                    skillTimer = 4f; // nhanh hơn bình thường
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
        }

        /**
         * Trong lúc kĩ năng chưa tung đòn cuối: đổi dạng theo địch. Đổi dạng thì bỏ các đòn phụ còn lại,
         * chỉ tụ lực (từ đầu) rồi tung đòn cuối của dạng mới.
         */
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
            meleeState = wantMelee; // range() đổi theo dạng
            target = wantMelee ? t : null;
            skillSwitchTimer = 15f; // tránh đổi qua đổi lại liên tục ở rìa tầm cận chiến
            slashQueue = 0;
            beginCharge(wantMelee ? meleeChargeTicks : rangedChargeTicks);
        }

        /** Tụ lực đòn cuối: không còn địch (trong tầm của dạng hiện tại) thì tụ lực lại từ đầu. */
        protected void updateCharge(float dt){
            Posc en = skillMelee ? closestMeleeEnemy() : target;

            if(en == null){
                chargeProgress = 0f;
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
                float dmg = melee_damage[ti(meleeTier)] * 30f;
                // -40°, 0°, +40° — đường đầu tiên 100%, các đường sau trúng cùng mục tiêu chỉ còn 50%
                for(int i = -1; i <= 1; i++){
                    float a2 = ang + i * 40f;
                    lineDamage(a2, len, 14f * a.sizeMul, dmg, true, a.pal);
                    lineSlashFx.at(x, y, a2, palette(a.pal).light, new float[]{len, a.pal, a.sizeMul});
                }
                Effect.shake(6.5f, 14f, x, y);
            }else{
                // Kĩ năng bắt đầu ở cận chiến rồi đổi sang đánh xa: 50% tổng sát thương cả ba tia (3 × 3000% × 50%)
                float dmg = skillOriginMelee ? melee_damage[ti(meleeTier)] * 45f : damage[ti(Tier)] * 30f;
                fireWave(a.finalWave, ang, dmg);
                swingFx(ang, 96f * a.sizeMul, 55f, a);
                Effect.shake(5.5f, 12f, x, y);
            }
            endSkill();
        }

        @Override
        public void draw(){
            super.draw();
            if(skillActive && skillCharging && chargeProgress > 0f && skillAmmo != null){
                drawCharge(palette(skillAmmo.pal), x, y, skillBase, Mathf.clamp(chargeProgress / chargeNeed));
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