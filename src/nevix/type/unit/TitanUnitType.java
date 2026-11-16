package nevix.type.unit;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
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

    /** Tỉ lệ phản đạn (0 = chỉ chặn, 1 = luôn phản). Đạn không phản được thì luôn bị chặn. */
    public float auraReflectChance = 0.5f;
    /** Đạn dưới mức này không tốn điểm aura. */
    public float auraMinDamage = 100f;
    /** Mỗi bậc sát thương tốn thêm 1 điểm. */
    public float auraDamageStep = 100f;
    /** Sát thương tối đa tính cost (1000 / 100 = tối đa 10 điểm). */
    public float auraMaxDamage = 1000f;
    /** Thời gian hiện lưới lục giác khi trúng đạn (tick). */
    public float auraHitTime = 45f;
    /** Cỡ ô lục giác so với bán kính aura. */
    public float auraHexScale = 0.16f;
    /** Độ rộng vùng hiện lưới quanh hướng trúng đạn (so với bán kính aura). */
    public float auraHitRadius = 0.85f;

    /**
     * 1 = nền mờ, 2 = viền aura mờ, 3 = đường lưới lục giác,
     * 4 = nền ô lục giác, 5 = cung sáng ở rìa chỗ trúng, 6 = chớp khi trúng
     */
    public Color
    auraColor1, auraColor2, auraColor3,
    auraColor4, auraColor5, auraColor6;

    public TitanUnitType(String name) {
        super(name);
        deathShake = 50f;
        stepShake = 10f;

        auraColor1 = Color.valueOf("3f58ff");
        auraColor2 = Color.valueOf("6f86ff");
        auraColor3 = Color.valueOf("8fa4ff");
        auraColor4 = Color.valueOf("4a63ff");
        auraColor5 = Color.valueOf("d7e0ff");
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
        static final int maxHits = 8;
        static final float sqrt3 = (float) Math.sqrt(3);

        public int health;
        public float recharge, scale;
        boolean started;

        // Cấp phát lazy để mỗi unit có mảng riêng (clone chỉ copy tham chiếu)
        float[] hitAngle, hitLife;
        int nextHit;

        public boolean active() {
            return !started || health > 0;
        }

        void addHit(float angle) {
            if (hitAngle == null) {
                hitAngle = new float[maxHits];
                hitLife = new float[maxHits];
            }
            // Trúng gần chỗ cũ thì làm mới thay vì thêm lưới chồng lên
            for (int i = 0; i < maxHits; i++) {
                if (hitLife[i] > 0.3f && Math.abs(Angles.angleDist(hitAngle[i], angle)) < 14f) {
                    hitAngle[i] = angle;
                    hitLife[i] = 1f;
                    return;
                }
            }
            hitAngle[nextHit] = angle;
            hitLife[nextHit] = 1f;
            nextHit = (nextHit + 1) % maxHits;
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

            if (hitLife != null) {
                float dec = Time.delta / Math.max(t.auraHitTime, 1f);
                for (int i = 0; i < maxHits; i++) {
                    if (hitLife[i] > 0f) hitLife[i] = Math.max(0f, hitLife[i] - dec);
                }
            }

            if (health > 0) {
                // Hồi máu %/giây
                unit.heal(unit.maxHealth * t.auraHealPercent * Time.delta / 60f);

                final float r = t.auraSize;
                Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, b -> {
                    if (health <= 0 || b.team == unit.team) return;

                    boolean reflect = b.type.reflectable && Mathf.chance(t.auraReflectChance);
                    if (!reflect && !b.type.absorbable) return;
                    if (!b.within(unit, r + b.hitSize / 2f)) return;

                    health -= t.auraCost(b.damage);

                    // Hướng từ tâm unit ra viên đạn: chỗ này sẽ hiện lưới
                    float normal = unit.angleTo(b);
                    addHit(normal);

                    if (reflect) {
                        // phản xạ qua pháp tuyến
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
                    Fx.shockwave.at(unit.x, unit.y);
                }
            }
        }

        @Override
        public void draw(Unit unit) {
            if (!started || scale < 0.01f) return;
            TitanUnitType t = (TitanUnitType) unit.type;

            float x = unit.x, y = unit.y;
            float r = t.auraSize * scale;
            float z = Draw.z();
            Draw.z(Layer.shields);

            // Đường tròn mờ bao quanh unit
            Draw.color(t.auraColor1, 0.06f * scale);
            Fill.circle(x, y, r);
            Draw.color(t.auraColor2, (0.28f + Mathf.sin(Time.time, 40f, 0.05f)) * scale);
            Lines.stroke(1.5f);
            Lines.circle(x, y, r);

            // Lưới lục giác + cung sáng ở hướng bị bắn
            if (hitLife != null) {
                for (int i = 0; i < maxHits; i++) {
                    if (hitLife[i] > 0.01f) drawHit(t, x, y, r, hitAngle[i], hitLife[i]);
                }
            }

            Draw.reset();
            Draw.z(z);
        }

        void drawHit(TitanUnitType t, float x, float y, float r, float angle, float life) {
            float fade = life * life * scale; // tắt dần mượt
            float px = x + Angles.trnsx(angle, r), py = y + Angles.trnsy(angle, r);
            float s = r * t.auraHexScale;
            float reach = r * t.auraHitRadius;

            // Cung sáng ở rìa chỗ trúng
            float span = 80f;
            Draw.color(t.auraColor3, 0.35f * fade);
            Lines.stroke(5f);
            Lines.arc(x, y, r, span / 360f, angle - span / 2f);
            Draw.color(t.auraColor5, 0.9f * fade);
            Lines.stroke(2f);
            Lines.arc(x, y, r, span / 360f, angle - span / 2f);

            // Lưới lục giác (flat-top) chỉ hiện gần điểm trúng, nằm trong aura
            int n = Mathf.ceil(r / (s * 1.5f)) + 1;
            for (int col = -n; col <= n; col++) {
                float cx = x + col * s * 1.5f;
                float yOff = (col & 1) == 0 ? 0f : sqrt3 * s * 0.5f;
                for (int row = -n; row <= n; row++) {
                    float cy = y + row * sqrt3 * s + yOff;

                    float dc = Mathf.dst(cx - x, cy - y);
                    if (dc > r - s * 0.3f) continue;

                    float dp = Mathf.dst(cx - px, cy - py);
                    if (dp > reach) continue;

                    float k = 1f - dp / reach;
                    float a = k * k * fade;

                    Draw.color(t.auraColor4, 0.22f * a);
                    Fill.poly(cx, cy, 6, s * 0.92f);
                    Draw.color(t.auraColor3, 0.85f * a);
                    Lines.stroke(1f);
                    Lines.poly(cx, cy, 6, s * 0.92f);
                }
            }

            // Chớp nhẹ tại điểm trúng
            Draw.color(t.auraColor6, 0.5f * fade);
            Fill.circle(px, py, s * 0.6f * life);
        }
    }
}