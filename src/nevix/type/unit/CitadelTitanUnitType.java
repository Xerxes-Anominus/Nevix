package nevix.type.unit;

import arc.graphics.Blending;
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
    
    public float auraScale = 0.4f;
    
    protected Color[] haloColors = {
        Color.valueOf("ff7a1a"),
        Color.valueOf("ffd43b"),
        Color.valueOf("fff6c2"),
        Color.white
    };
    
    protected float[]
    haloRatios = {1f, 0.85f, 0.7f, 0.55f},
    haloAlphas = {0.6f, 0.7f, 0.8f, 0.9f};
    
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
        
        Draw.blend(Blending.additive);
        
        for(int i = 0; i < 4; i++) {
            Draw.z(Layer.flyingUnit + 1 + i);
            Draw.color(haloColors[i], haloAlphas[i]);
            
            Fill.circle(cenX, cenY, 90f * auraScale * haloRatios[i]);
        }
        
        for(int i = 0; i < 2; i++) {
            for(int j = 0; j < 4; j++) {
                Draw.z(Layer.flyingUnitLow + 1 + j);
                Draw.color(haloColors[j], haloAlphas[j]);
                
                float
                triX = cenX + 90f * auraScale * Mathf.cosDeg(rot + angles[i]),
                triY = cenY + 90f * auraScale * Mathf.sinDeg(rot + angles[i]);
                
                Drawf.tri(triX, triY, 45f * auraScale * haloRatios[j], 135f * auraScale * haloRatios[j], rot + angles[i]);
            }
        }
        
        Draw.z(Layer.flyingUnit + 1);
        for(int i = 0; i < 4; i++) {
            Lines.stroke((3f - 0.3f * i) * auraScale, haloColors[i]);
            Draw.alpha(haloAlphas[i]);
            Lines.circle(cenX, cenY, 180f * auraScale);
        }
        
        for(int i = 0; i < 4; i++) {
            for(int j = 0; j < 4; j++) {
                Draw.z(Layer.flyingUnit + 1 + j);
                Draw.color(haloColors[j], haloAlphas[j]);
                
                float
                triR = (Time.time + 10 * unit.id + triRot[i]) % 360f,
                triX = cenX + 185f * auraScale * Mathf.cosDeg(triR),
                triY = cenY + 185f * auraScale * Mathf.sinDeg(triR);
                
                Drawf.tri(triX, triY, 60f * auraScale * haloRatios[j], 60f * auraScale * haloRatios[j], triR);
            }
        }
        
        Draw.blend();
        Draw.reset();
    }
}