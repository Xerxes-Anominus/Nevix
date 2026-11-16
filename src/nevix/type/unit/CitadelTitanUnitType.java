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
    
    public Color haloColor = Color.valueOf("1a1830");
    public float haloRadius = 150f;
    public float haloStroke = 3f;
    public float haloRotation = 0f;
    public float haloSpin = 0f;

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
        float r = haloRadius;
        float ang = haloRotation + Time.time * haloSpin;
        float cx = unit.x, cy = unit.y;

        float spike = r * 0.6f;
        float halfW = r * 0.24f;

        Draw.z(Layer.flyingUnit + 1f);
        Draw.color(haloColor);
        
        Lines.stroke(haloStroke);
        Lines.circle(cx, cy, r);
        
        for (int i = 0; i < 4; i++) {
            float a = ang + i * 90f;
            float bx = cx + Angles.trnsx(a, r - 2f);
            float by = cy + Angles.trnsy(a, r - 2f);
            float tx = cx + Angles.trnsx(a, r + spike);
            float ty = cy + Angles.trnsy(a, r + spike);
            float px = Angles.trnsx(a + 90f, halfW);
            float py = Angles.trnsy(a + 90f, halfW);
            Fill.tri(bx - px, by - py, bx + px, by + py, tx, ty);
        }
        
        float c = Mathf.cosDeg(ang), s = Mathf.sinDeg(ang);
        float lx = -0.73f * r, rx = 0.70f * r, mx = 0.12f * r, my = 0.20f * r;

        float lX = cx + lx * c,        lY = cy + lx * s;
        float rX = cx + rx * c,        rY = cy + rx * s;
        float pX = cx + mx * c - my * s, pY = cy + mx * s + my * c;
        float qX = cx + mx * c + my * s, qY = cy + mx * s - my * c;

        Fill.tri(lX, lY, pX, pY, qX, qY);
        Fill.tri(rX, rY, pX, pY, qX, qY);

        Draw.reset();
    }
}