package nevix.type.unit;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.Rand;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

public class TitanUnitType extends UnitType {
    /** Máu của aura (điểm). Mỗi viên đạn >= auraMinDamage trừ 1..10 điểm. */
    public int auraHealth = 100;
    /** Thời gian hồi aura sau khi vỡ (tick). */
    public float auraRechargeTime = 600f;
    /** % máu tối đa hồi mỗi giây khi aura còn (0.01 = 1%/s). */
    public float auraHealPercent = 0.01f;
    /** BÁN KÍNH aura. <= 0 thì tự tính theo hitSize. */
    public float auraSize;

    /** true: phản đạn (nếu đạn phản được), false: chỉ chặn. */
    public boolean auraReflect = true;
    /** Đạn dưới mức này không tốn điểm aura. */
    public float auraMinDamage = 100f;
    /** Mỗi bậc sát thương tốn thêm 1 điểm. */
    public float auraDamageStep = 100f;
    /** Sát thương tối đa tính cost (1000 / 100 = tối đa 10 điểm). */
    public float auraMaxDamage = 1000f;

    /**
     * 1 = nền, 2 = viền sáng mờ, 3 = vòng tròn,
     * 4 = tia sét (glow), 5 = tia sét (lõi), 6 = chớp khi trúng đạn
     */
    public Color
    auraColor1, auraColor2, auraColor3,
    auraColor4, auraColor5, auraColor6;

    public TitanUnitType(String name) {
        super(name);
        deathShake = 50f;
        stepShake = 10f;

        auraColor1 = Color.valueOf("4a5cff");
        auraColor2 = Color.valueOf("6f7dff");
        auraColor3 = Color.valueOf("b9c2ff");
        auraColor4 = Color.valueOf("7a86ff");
        auraColor5 = Color.valueOf("f0f2ff");
        auraColor6 = Color.white.cpy();
    }

    @Override
    public void init() {
        super.init();
        if (auraSize <= 0f) auraSize = hitSize * 1.3f + 4f;
        abilities.add(new TitanAura());
    }

    /** Điểm aura bị trừ cho 1 viên đạn có sát thương `damage`. */
    public int auraCost(float damage) {
        if (damage < auraMinDamage) return 0;
        return (int) Mathf.clamp(damage / auraDamageStep, 1f, auraMaxDamage / auraDamageStep);
    }

    @Override
    public boolean killable(Unit unit) {
        // Còn aura thì không nhận sát thương
        for (Ability a : unit.abilities) {
            if (a instanceof TitanAura) return !((TitanAura) a).active();
        }
        return true;
    }

    /** Trạng thái aura nằm riêng cho từng unit (Ability được copy cho mỗi unit). */
    public static class TitanAura extends Ability {
        static final Rand rand = new Rand();
        static final float[] pts = new float[64];

        public int health;
        public float recharge, scale, flash;
        boolean started;

        public boolean active() {
            return !started || health > 0;
        }

        @Override
        public void update(Unit unit) {
            final TitanUnitType t = (TitanUnitType) unit.type;
            if (!started) {
                started = true;
                health = t.auraHealth;
                scale = 0f;
            }

            scale = Mathf.lerpDelta(scale, health > 0 ? 1f : 0f, 0.08f);
            flash = Mathf.lerpDelta(flash, 0f, 0.12f);

            if (health > 0) {
                // Hồi máu %/giây
                unit.heal(unit.maxHealth * t.auraHealPercent * Time.delta / 60f);

                final float r = t.auraSize;
                Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, b -> {
                    if (health <= 0 || b.team == unit.team) return;

                    boolean reflect = t.auraReflect && b.type.reflectable;
                    if (!reflect && !b.type.absorbable) return;
                    if (!b.within(unit, r + b.hitSize / 2f)) return;

                    health -= t.auraCost(b.damage);
                    flash = 1f;

                    if (reflect) {
                        // phản xạ qua pháp tuyến (hướng từ tâm unit ra viên đạn)
                        float normal = unit.angleTo(b);
                        float out = 2f * normal - b.rotation() + 180f;
                        b.vel.setAngle(out);
                        b.rotation(out);
                        b.team = unit.team;
                        b.owner = unit;
                        b.time += 1f;
                    } else {
                        b.absorb();
                    }
                    Fx.absorb.at(b.x, b.y);

                    if (health <= 0) {
                        health = 0;
                        recharge = t.auraRechargeTime;
                        Fx.shockwave.at(unit.x, unit.y);
                    }
                });
            } else {
                recharge -= Time.delta;
                if (recharge <= 0f) {
                    health = t.auraHealth;
                    flash = 1f;
                    Fx.shockwave.at(unit.x, unit.y);
                }
            }
        }

        @Override
        public void draw(Unit unit) {
            if (!started || scale < 0.01f) return;
            TitanUnitType t = (TitanUnitType) unit.type;

            float x = unit.x, y = unit.y;
            float r = t.auraSize * scale * (1f + Mathf.sin(Time.time, 14f, 0.012f));
            float z = Draw.z();
            Draw.z(Layer.shields);

            // Nền trong mờ + viền trong sáng hơn ở rìa
            Draw.color(t.auraColor1, (0.10f + 0.10f * flash) * scale);
            Fill.circle(x, y, r);
            Draw.color(t.auraColor2, (0.12f + 0.15f * flash) * scale);
            Lines.stroke(r * 0.22f);
            Lines.circle(x, y, r * 0.89f);

            // Vòng tròn chính
            Draw.color(t.auraColor3, (0.7f + 0.3f * flash) * scale);
            Lines.stroke(2.4f);
            Lines.circle(x, y, r);
            Draw.color(t.auraColor5, scale);
            Lines.stroke(1f);
            Lines.circle(x, y, r);

            // Tia sét: đổi hình mỗi ~3 tick để nhấp nháy
            rand.setSeed(unit.id * 31L + (long) (Time.time / 3f));

            // Các tia chạy dọc bên trong quả cầu
            int arcs = 6;
            for (int i = 0; i < arcs; i++) {
                float a1 = rand.random(360f);
                float a2 = a1 + rand.random(70f, 160f) * (rand.chance(0.5) ? 1 : -1);
                int n = 8;
                float sx = x + Angles.trnsx(a1, r), sy = y + Angles.trnsy(a1, r);
                float ex = x + Angles.trnsx(a2, r), ey = y + Angles.trnsy(a2, r);

                pts[0] = sx;
                pts[1] = sy;
                for (int k = 1; k <= n; k++) {
                    float f = k / (float) n;
                    float bx = Mathf.lerp(sx, ex, f), by = Mathf.lerp(sy, ey, f);
                    float pull = Mathf.sin(f * Mathf.pi) * 0.22f; // cong vào tâm
                    bx = Mathf.lerp(bx, x, pull);
                    by = Mathf.lerp(by, y, pull);
                    if (k < n) {
                        bx += rand.range(r * 0.07f);
                        by += rand.range(r * 0.07f);
                    }
                    pts[k * 2] = bx;
                    pts[k * 2 + 1] = by;
                }
                drawBolt(t, n + 1, 1f);
            }

            // Các tia nhỏ tỏa ra ngoài rìa
            for (int i = 0; i < 12; i++) {
                float ang = rand.random(360f);
                float len = r * rand.random(0.1f, 0.3f);
                int n = 4;
                float cx = x + Angles.trnsx(ang, r), cy = y + Angles.trnsy(ang, r);
                pts[0] = cx;
                pts[1] = cy;
                for (int k = 1; k <= n; k++) {
                    float a = ang + rand.range(35f);
                    cx += Angles.trnsx(a, len / n);
                    cy += Angles.trnsy(a, len / n);
                    pts[k * 2] = cx;
                    pts[k * 2 + 1] = cy;
                }
                drawBolt(t, n + 1, 0.6f);
            }

            // Chớp sáng khi bắt đạn
            if (flash > 0.01f) {
                Draw.color(t.auraColor6, flash * 0.35f * scale);
                Fill.circle(x, y, r);
            }

            Draw.reset();
            Draw.z(z);
        }

        void drawBolt(TitanUnitType t, int count, float width) {
            // glow
            Draw.color(t.auraColor4, 0.4f * scale);
            Lines.stroke(2.4f * width);
            for (int i = 0; i < count - 1; i++) {
                Lines.line(pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]);
            }
            // lõi
            Draw.color(t.auraColor5, scale);
            Lines.stroke(0.9f * width);
            for (int i = 0; i < count - 1; i++) {
                Lines.line(pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]);
            }
        }
    }
}
