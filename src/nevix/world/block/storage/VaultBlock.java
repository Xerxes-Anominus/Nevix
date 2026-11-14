package nevix.world.block.storage;

import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import mindustry.world.meta.BlockFlag;
import mindustry.world.modules.ItemModule;

public class VaultBlock extends StorageBlock {
    public VaultBlock(String name) {
        super(name);
        coreMerge = false;
        update = true;
    }
    
    public float linkRange = -1f;
    
    public static void refreshCore(CoreBuild core, Building ignore) {
        int cap = core.block.itemCapacity;
        
        for(Building build : core.proximity) {
            if(build != ignore && core.owns(build)) {
                cap += core.block.itemCapacity;
            }
        }
        
        for(Building build : Vars.indexer.getFlagged(core.team, BlockFlag.storage)) {
            if(build != ignore && build instanceof VaultBuild vault && vault.remoteCore == core) {
                cap += core.block.itemCapacity;
            }
        }
        core.storageCapacity = cap;
    }
    
    public class VaultBuild extends StorageBuild {
        public CoreBuild remoteCore;
        public ItemModule ownItems;
        
        public float checkTimer;
        
        @Override
        public void updateTile() {
            if (ownItems == null) ownItems = items;

            if ((checkTimer += Time.delta) >= 30f) {
                checkTimer = 0f;
                relink();
            }
        }

        public void relink() {
            CoreBuild core = Vars.state.teams.closestCore(x, y, team);
            if (core != null && linkRange >= 0f && !within(core, linkRange * Vars.tilesize)) {
                core = null;
            }

            if (core != remoteCore) {
                CoreBuild old = remoteCore;
                remoteCore = core;
                if (old != null) refreshCore(old, null);
            }

            if (core != null) {
                linkedCore = core;
                items = core.items;
                refreshCore(core, null);
            } else {
                linkedCore = null;
                if (ownItems != null) items = ownItems;
            }
        }

        public void detach() {
            if (ownItems != null) items = ownItems;
            linkedCore = null;
        }

        @Override
        public void onRemoved() {
            detach();
            super.onRemoved();
            if (remoteCore != null) refreshCore(remoteCore, this);
        }

        @Override
        public void onDestroyed() {
            detach();
            super.onDestroyed();
        }
    }
}