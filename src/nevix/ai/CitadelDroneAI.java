package nevix.ai;

import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Call;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

public class CitadelDroneAI extends AIController {
    public Unit owner;
    public float searchRange = 100f * Vars.tilesize;

    public CitadelDroneAI() {}

    public CitadelDroneAI(Unit owner) {
        this.owner = owner;
    }

    @Override
    public void updateMovement() {
        if (owner == null || !owner.isValid()) {
            if (!Vars.net.client()) Call.unitDespawn(unit);
            return;
        }
        
        unit.health = unit.maxHealth;
        unit.shield = 0f;

        if (target != null) {
            moveTo(target, unit.type.range * 0.7f);
            unit.lookAt(target);
        } else {
            moveTo(owner, owner.hitSize * 0.8f);
        }
    }

    @Override
    public Teamc findMainTarget(float x, float y, float range, boolean air, boolean ground) {
        if (owner == null || !owner.isValid()) return null;
        
        for (WeaponMount m : owner.mounts) {
            Teamc t = m.target;
            if (t != null && !Units.invalidateTarget(t, unit.team, owner.x, owner.y, searchRange)) {
                return t;
            }
        }
        
        if (owner.isPlayer() && owner.isShooting()) {
            Teamc t = Units.closestTarget(unit.team, owner.aimX(), owner.aimY(), 120f,
            u -> u.checkTarget(air, ground), b -> ground);
            if (t != null) return t;
        }
        
        return Units.closestTarget(unit.team, owner.x, owner.y, searchRange,
        u -> u.checkTarget(air, ground), b -> ground);
    }
}