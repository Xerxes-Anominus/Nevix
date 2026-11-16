package nevix.type.unit;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import nevix.ai.CitadelDroneAI;

public class CitadelTitanUnitType extends TitanUnitType {
    public UnitType droneType;
    public int droneCount = 2;

    public Color haloInnerColor = Color.valueOf("ff7a1a");
    public Color haloOuterColor = Color.valueOf("ffd43b");
    public Color haloCoreColor = Color.valueOf("fff6c2");
    public int haloLayers = 6;
    public float haloRadius = 90f;
    public float haloOuterRadius = 138f;
    public float haloStroke = 2.4f;
    public float haloTriangleLength = 36f;
    public float haloTriangleWidth = 18f;
    public float haloSpin = 0.8f;
    public int haloSegments = 16;

    protected final ObjectMap<Unit, Seq<Unit>> drones = new ObjectMap<>();

    public CitadelTitanUnitType(String name) {
        super(name);
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);

        if (droneType == null || Vars.net.client()) return;

        Seq<Unit> list = drones.get(unit);
        if (list == null) {
            list = new Seq<>();
            drones.put(unit, list);
        }

        list.removeAll(d -> !d.isValid());

        while (list.size < droneCount) {
            Unit d = droneType.create(unit.team);
            Tmp.v1.trns(Mathf.random(360f), unit.hitSize / 2f);
            d.set(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y);
            d.rotation = unit.rotation;
            d.controller(new CitadelDroneAI(unit));
            d.add();
            list.add(d);
        }

        if (Time.time % 300f < Time.delta) {
            Seq<Unit> keys = drones.keys().toSeq();
            for (Unit k : keys) {
                if (!k.isValid()) drones.remove(k);
            }
        }
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        drawHalo(unit);
    }

    protected void drawHalo(Unit unit) {
        float cx = unit.x;
        float cy = unit.y;
        float rot = unit.rotation;
        float pulse = Mathf.absin(Time.time, 60f, 1f);

        Draw.z(Layer.flyingUnit + 1f);

        for (int i = 0; i < haloLayers; i++) {
            float t = i / (haloLayers - 1f);
            Tmp.c1.set(haloInnerColor).lerp(haloOuterColor, Math.min(t * 2f, 1f)).lerp(haloCoreColor, Math.max(t * 2f - 1f, 0f));
            Draw.color(Tmp.c1);
            Draw.alpha(Mathf.lerp(0.08f, 0.4f, t) * (0.85f + pulse * 0.15f));
            Fill.circle(cx, cy, haloRadius * (1f - i / (float) haloLayers));
        }

        Draw.color(haloInnerColor);
        Lines.stroke(haloStroke);
        Lines.circle(cx, cy, haloRadius);

        for (int i = 0; i < 2; i++) {
            float a = rot + 90f + i * 180f;
            drawTriangle(cx, cy, a, haloRadius - 2f, haloTriangleLength, haloTriangleWidth, haloInnerColor);
            drawTriangle(cx, cy, a, haloRadius - 2f, haloTriangleLength * 0.66f, haloTriangleWidth * 0.66f, haloOuterColor);
            drawTriangle(cx, cy, a, haloRadius - 2f, haloTriangleLength * 0.33f, haloTriangleWidth * 0.33f, haloCoreColor);
        }

        float spin = rot - Time.time * haloSpin;
        Lines.stroke(haloStroke * 1.5f);
        for (int i = 0; i < haloSegments; i++) {
            float t = Math.abs(i * 2f / haloSegments - 1f);
            Draw.color(Tmp.c2.set(haloInnerColor).lerp(haloOuterColor, t));
            Lines.arc(cx, cy, haloOuterRadius, 0.5f / haloSegments, spin + i * 360f / haloSegments);
        }

        Draw.reset();
    }

    protected void drawTriangle(float cx, float cy, float angle, float baseRadius, float length, float halfWidth, Color color) {
        Draw.color(color);
        float bx = cx + Angles.trnsx(angle, baseRadius);
        float by = cy + Angles.trnsy(angle, baseRadius);
        float tx = cx + Angles.trnsx(angle, baseRadius + length);
        float ty = cy + Angles.trnsy(angle, baseRadius + length);
        float px = Angles.trnsx(angle + 90f, halfWidth);
        float py = Angles.trnsy(angle + 90f, halfWidth);
        Fill.tri(bx - px, by - py, bx + px, by + py, tx, ty);
    }
}