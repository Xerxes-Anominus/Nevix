package nevix.type.unit;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.UnitType;
import nevix.ai.CitadelDroneAI;

public class CitadelTitanUnitType extends TitanUnitType {
    public CitadelTitanUnitType(String name) {
        super(name);
    }
    
    public UnitType droneType;
    public int droneCount;
    
    protected Color[] haloColors = {
        Color.valueOf("ff7a1a"),
        Color.valueOf("ffd43b"),
        Color.valueOf("fff6c2"),
        Color.white
    };
    
    protected final ObjectMap<Unit, Seq<Unit>> drones = new ObjectMap<>();
    
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
        drawAura(unit);
    }
    
    private final float[]
    angles = {90f, 270f},
    triRot = {90f, 180f, 270f, 360f};
    
    protected void drawAura(Unit unit) {
        float
        cenX = unit.x,
        cenY = unit.y,
        rot = unit.rotation;
        
        for(int i = 0; i < 4; i++) {
            Draw.z(Layer.flyingUnit + 1 + i);
            Draw.color(haloColors[i]);
            
            Fill.circle(cenX, cenY, 90f - i * 2);
        }
        
        for(int i = 0; i < 2; i++) {
            for(int j = 0; j < 4; j++) {
                Draw.z(Layer.flyingUnitLow+ 1 + j);
                Draw.color(haloColors[j]);
                
                float
                triX = cenX + 90f * Mathf.cosDeg(rot + angles[i]),
                triY = cenY + 90f * Mathf.sinDeg(rot + angles[i]);
                
                Drawf.tri(triX, triY, 45f, 135f - 1.5f * j, rot + angles[i]);
            }
        }
        
        for(int i = 0; i < 4; i++) {
            Lines.stroke(3f - 0.3f * i, haloColors[i]);
            Lines.circle(cenX, cenY, 180f);
        }
        
        for(int i = 0; i < 4; i++) {
            for(int j = 0; j < 4; j++) {
                Draw.z(Layer.flyingUnit+ 1 + j);
                Draw.color(haloColors[j]);
                
                float
                triR = (Time.time + 10 * unit.id + triRot[i]) % 360f,
                triX = cenX + 185f * Mathf.cosDeg(triR),
                triY = cenY + 185f * Mathf.sinDeg(triR);
                
                Drawf.tri(triX, triY, 60f - 1f * j, 60f - 1f * j, triR);
            }
        }
        Draw.reset();
    }
}