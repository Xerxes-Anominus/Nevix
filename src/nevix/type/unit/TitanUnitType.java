package nevix.type.unit;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;

public class TitanUnitType extends UnitType {
    public int auraHealth = 100;
    public float auraRechargeTime = 600f;
    public float auraHealPercent = 0.01f;
    public float auraSize;
    
    public float auraReflectChance = 0.5f;
    public float auraMinDamage = 100f;
    public float auraDamageStep = 100f;
    public float auraMaxDamage = 1000f;
    public float auraHitTime = 45f;
    public float auraHexScale = 0.16f;
    public float auraHitRadius = 0.85f;
    
    public boolean pushFlying = true;
    public boolean pushFlyingAllies = true;
    public float pushFlyingStrength = 0.3f;
    public float pushFlyingPadding = 0f;
    public boolean immuneKnockback = true;
    
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

    public int auraCost(float damage) {
        if (damage < auraMinDamage) return 0;
        return (int) Mathf.clamp(damage / auraDamageStep, 1f, auraMaxDamage / auraDamageStep);
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        
        if (immuneKnockback) {
            float max = unit.speed();
            if (unit.vel.len2() > max * max) unit.vel.setLength(max);
        }

        if (pushFlying) pushFlyers(unit);
    }

    protected void pushFlyers(Unit unit) {
        final float rad = hitSize / 2f + pushFlyingPadding;
        final float range = rad + 48f;

        Units.nearby(unit.x - range, unit.y - range, range * 2f, range * 2f, other -> {
            if (other == unit || !other.isFlying() || !other.isValid()) return;
            if (!pushFlyingAllies && other.team == unit.team) return;

            float dx = other.x - unit.x, dy = other.y - unit.y;
            float dst = Mathf.dst(dx, dy);
            float min = rad + other.hitSize / 2f;
            if (dst >= min) return;

            if (dst < 0.001f) {
                Tmp.v1.trns(Mathf.random(360f), 1f);
            } else {
                Tmp.v1.set(dx, dy);
            }

            float push = Math.min((min - dst) * pushFlyingStrength * Time.delta, min - dst);
            Tmp.v1.setLength(push);
            other.x += Tmp.v1.x;
            other.y += Tmp.v1.y;
        });
    }

    @Override
    public boolean hittable(Unit unit) {
        for (Ability a : unit.abilities) {
            if (a instanceof TitanAura) return !((TitanAura) a).active();
        }
        return super.hittable(unit);
    }

    @Override
    public boolean killable(Unit unit) {
        for (Ability a : unit.abilities) {
            if (a instanceof TitanAura) return !((TitanAura) a).active();
        }
        return true;
    }
    
    public static class TitanAura extends Ability {
        static final int maxHits = 8;
        static final float sqrt3 = (float) Math.sqrt(3);

        public int health;
        public float recharge, scale;
        boolean started;
        
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
                unit.heal(unit.maxHealth * t.auraHealPercent * Time.delta / 60f);

                final float r = t.auraSize;
                Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, b -> {
                    if (health <= 0 || b.team == unit.team) return;

                    boolean reflect = b.type.reflectable && Mathf.chance(t.auraReflectChance);
                    if (!reflect && !b.type.absorbable) return;
                    if (!b.within(unit, r + b.hitSize / 2f)) return;

                    health -= t.auraCost(b.damage);
                    
                    float normal = unit.angleTo(b);
                    addHit(normal);

                    if (reflect) {
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
            
            Draw.color(t.auraColor1, 0.06f * scale);
            Fill.circle(x, y, r);
            Draw.color(t.auraColor2, (0.28f + Mathf.sin(Time.time, 40f, 0.05f)) * scale);
            Lines.stroke(1.5f);
            Lines.circle(x, y, r);
            
            if (hitLife != null) {
                for (int i = 0; i < maxHits; i++) {
                    if (hitLife[i] > 0.01f) drawHit(t, x, y, r, hitAngle[i], hitLife[i]);
                }
            }

            Draw.reset();
            Draw.z(z);
        }

        void drawHit(TitanUnitType t, float x, float y, float r, float angle, float life) {
            float fade = life * life * scale;
            float px = x + Angles.trnsx(angle, r), py = y + Angles.trnsy(angle, r);
            float s = r * t.auraHexScale;
            float reach = r * t.auraHitRadius;
            
            float span = 80f;
            Draw.color(t.auraColor3, 0.35f * fade);
            Lines.stroke(5f);
            Lines.arc(x, y, r, span / 360f, angle - span / 2f);
            Draw.color(t.auraColor5, 0.9f * fade);
            Lines.stroke(2f);
            Lines.arc(x, y, r, span / 360f, angle - span / 2f);
            
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
            
            Draw.color(t.auraColor6, 0.5f * fade);
            Fill.circle(px, py, s * 0.6f * life);
        }
    }
}