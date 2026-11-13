package nevix.ai;

import mindustry.ai.Pathfinder;
import mindustry.entities.units.AIController;

public class ReactoryAI extends AIController {
    @Override
    public void updateMovement() {
        pathfind(Pathfinder.fieldCore, true);
        faceMovement();
    }
}