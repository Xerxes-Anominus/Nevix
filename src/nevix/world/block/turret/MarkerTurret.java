package nevix.world.block.turret;

import java.util.ArrayList;
import java.util.List;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Damage;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.meta.Stat;
import nevix.world.meta.NevixStat;

public class MarkerTurret extends NevixTurret {
    public class MarkerBulletType extends BasicBulletType {
        public MarkerBulletType() {
            super();
            damage = 98.5f;
            speed = 5f;
            scaleLife = true;
            frontColor = Color.white;
            backColor = Color.orange;
            trailColor = Color.orange;
            width = 15;
            height = 15f;
            trailWidth = 3f;
            trailLength = 30;
            homingRange = 40f;
            homingPower = 0.01f;
            homingDelay = 30f;
        }
        
        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            if(bullet.owner instanceof MarkerTurretBuild owner) {
                if(entity instanceof Unit hitter) {
                    if(hitter.team != bullet.team) {
                        owner.markedUnitList.add(hitter);
                    }
                }
            }
            super.hitEntity(bullet, entity, health);
        }
    }
    
    public MarkerTurret(String name) {
        super(name);
        
        health = 2300;
        size = 3;
        
        range = 80f;
        reload = 70f;
        
        inaccuracy = 10f;
        
        shoot.shots = 2;
        shoot.shotDelay = 5f;
        
        ammoTypes.put(Items.pyratite, new MarkerBulletType());
        
        upgradeBranch();
    }
    
    public static Upgrade
    marker_mark_0,
    marker_mark_1,
    marker_mark_2,
    marker_range_0,
    marker_range_1,
    marker_mark_range_0,
    marker_mark_range_1,
    marker_mark_range_2,
    marker_marktime_0,
    marker_marktime_1,
    marker_marktime_2;

    public void upgradeBranch() {
        marker_mark_0 = addUpgrade("marker-mark-0", "", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 100), (MarkerTurretBuild build) -> build.MarkTier++, (MarkerTurretBuild build) -> build.MarkTier = 0);
        marker_mark_1 = addUpgrade("marker-mark-1", "", ItemStack.with(Items.copper, 1200, Items.lead, 1200, Items.graphite, 500, Items.titanium, 150), (MarkerTurretBuild build) -> build.MarkTier++, (MarkerTurretBuild build) -> build.MarkTier = 0).levelAfter(marker_mark_0);
        marker_mark_2 = addUpgrade("marker-mark-2", "", ItemStack.with(Items.copper, 2500, Items.lead, 2500, Items.graphite, 1500, Items.titanium, 800, Items.thorium, 200), (MarkerTurretBuild build) -> build.MarkTier++, (MarkerTurretBuild build) -> build.MarkTier = 0).levelAfter(marker_mark_1);

        marker_range_0 = addUpgrade("marker-range-0", "", ItemStack.with(Items.copper, 200, Items.lead, 100), (MarkerTurretBuild build) -> build.RangeTier++, (MarkerTurretBuild build) -> build.RangeTier = 0).requires(marker_mark_range_0);
        marker_range_1 = addUpgrade("marker-range-1", "", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.silicon, 150), (MarkerTurretBuild build) -> build.RangeTier++, (MarkerTurretBuild build) -> build.RangeTier = 0).levelAfter(marker_range_0).requires(marker_mark_range_1);
        
        marker_mark_range_0 = addUpgrade("marker-mark-range-0", "", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 50), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0);
        marker_mark_range_1 = addUpgrade("marker-mark-range-1", "", ItemStack.with(Items.copper, 1000, Items.lead, 1000, Items.graphite, 500, Items.silicon, 200), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0).levelAfter(marker_mark_range_0);
        marker_mark_range_2 = addUpgrade("marker-mark-range-2", "", ItemStack.with(Items.copper, 3000, Items.lead, 3000, Items.graphite, 2000, Items.silicon, 600, Items.titanium, 1400), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0).levelAfter(marker_mark_range_1);

        marker_marktime_0 = addUpgrade("marker-marktime-0", "", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 200), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0);
        marker_marktime_1 = addUpgrade("marker-marktime-1", "", ItemStack.with(Items.copper, 1500, Items.lead, 1500, Items.graphite, 1000, Items.silicon, 400, Items.titanium, 600), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0).levelAfter(marker_marktime_0);
        marker_marktime_2 = addUpgrade("marker-marktime-2", "", ItemStack.with(Items.copper, 4000, Items.lead, 4000, Items.graphite, 2500, Items.silicon, 1000, Items.titanium, 1500, Items.thorium, 400), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0).levelAfter(marker_marktime_1);
    }
    
    public float[]
    MarkDamage = {100f, 250f, 500f, 1000f},
    Range = {80f, 120f, 160f},
    MarkRange = {160f, 240f, 320f, 400f},
    MarkTime = {300f, 240f, 180f, 120f};
    
    public Effect markEffect = Fx.explosion;
    
    @Override
    public void setBars() {
        super.setBars();
        
        addBar("mark-time", build -> new Bar(
        () -> Core.bundle.get("bar.nevix-marker.mark-time"),
        () -> Pal.accent,
        () -> ((MarkerTurretBuild)build).markTimer / ((MarkerTurretBuild)build).markTime()
        ));
    }
    
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid) {
        super.drawPlace(x, y, rotation, valid);
        
        Drawf.dashCircle(x * Vars.tilesize, y * Vars.tilesize, 160f, Color.orange);
    }
    
    public class MarkerTurretBuild extends NevixTurretBuild {
        public int
        MarkTier, RangeTier,
        MarkRangeTier, MarkTimeTier;
        
        public float markTimer, activeTimer;
        
        public List<Unit> markedUnitList = new ArrayList<>();
        
        @Override
        public void getStats(Table table) {
            addStat(table, NevixStat.generals, "", Pal.accent);
            addStat(table, Stat.health, "" + this.maxHealth());
            addStat(table, Stat.range, "" + (range() / Vars.tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile));
            addStat(table, Stat.reload, "" + (Mathf.round(reload / 60f * 100f) / 100f) + " " + NevixStatUnit.get(StatUnit.seconds));

            addStat(table, NevixStat.none, "");

            addStat(table, NevixStat.ammo, "", Pal.accent);
            if(peekAmmo() != null) {
                addBulletStatTable(table, bt -> {
                    addBulletStat(bt, Stat.damage, "" + peekAmmo().damage);
                });
            }

            addStat(table, NevixStat.none, "");

            addStat(table, NevixStat.special, "", Pal.accent);
            addBulletStatTable(table, bt -> {
                addBulletStat(bt, NevixStat.markDamage, "" + markDamage());
                addBulletStat(bt, NevixStat.markRange, "" + (markRange() / Vars.tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile));
                addBulletStat(bt, NevixStat.markInterval, "" + Mathf.round(markTime() / 60f * 10f) / 10f + " " + NevixStatUnit.get(StatUnit.seconds));
                addBulletStat(bt, NevixStat.markSplash, "" + (40f / Vars.tilesize) + " " + NevixStatUnit.get(NevixStatUnit.tile));
                addBulletStat(bt, NevixStat.none, "markMechanic");
            });
        }
        
        @Override
        public void drawSelect() {
            super.drawSelect();
            
            Drawf.dashCircle(this.x, this.y, markRange(), Color.orange);
        }
        
        @Override
        public float range() {
            return Range[RangeTier];
        }
        
        public float markRange() {
            return MarkRange[MarkRangeTier];
        }
        
        public float markTime() {
            return MarkTime[MarkTimeTier];
        }
        
        public float markDamage() {
            return MarkDamage[MarkTier];
        }
        
        public void updateMark() {
            markTimer += Time.delta;
            if(markTimer < markTime()) return;
            markTimer -= markTime();
            
            Groups.unit.each(u -> {
                if(!markedUnitList.contains(u)) {
                    if(Mathf.dst(this.x, this.y, u.x, u.y) <= markRange() * markRange() && u.team != this.team) {
                        markedUnitList.add(u);
                    }
                }
            });
        }
        
        public void addMark(Unit u) {
            if(!u.dead() && u.team != this.team) {
                markedUnitList.add(u);
            }
        }
        
        public void activeMark() {
            activeTimer += Time.delta;
            if(activeTimer < 15f) return;
            activeTimer -= 15f;
            
            for(int i = markedUnitList.size() - 1; i >= 0; i--) {
                Unit u = markedUnitList.get(i);
                if(u.dead()) {
                    Damage.damage(this.team, u.x, u.y, markDamage(), 40f);
                    markEffect.at(u.x, u.y);
                    markedUnitList.remove(i);
                }
            }
        }
        
        @Override
        public void updateTile() {
            updateMark();
            activeMark();
            super.updateTile();
        }
        
        @Override
        public void write(Writes w) {
            super.write(w);
            w.i(MarkTier);
            w.i(RangeTier);
            w.i(MarkRangeTier);
            w.i(MarkTimeTier);
            w.f(markTimer);
            w.f(activeTimer);
            w.i(markedUnitList.size());
            for(int i = markedUnitList.size() - 1; i >= 0; i--) {
                w.i(markedUnitList.get(i).id);
            }
        }
        
        @Override
        public void read(Reads r, byte re) {
            super.read(r, re);
            MarkTier = r.i();
            RangeTier = r.i();
            MarkRangeTier = r.i();
            MarkTimeTier = r.i();
            markTimer = r.f();
            activeTimer = r.f();
            markedUnitList.clear();
            int size = r.i();
            for(int i = 0; i < size; i++) {
                Unit u = Groups.unit.getByID(r.i());
                markedUnitList.add(u);
            }
        }
    }
}