package nevix.world.block.turret;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.IntIntMap;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Groups;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;

public class MarkerTurret extends NevixTurret {
    public static final Effect markBlast = new Effect(24f, 160f, e -> {
        Draw.color(Pal.remove, Color.white, e.fin());
        Lines.stroke(e.fout() * 3f + 0.5f);
        Lines.circle(e.x, e.y, e.rotation * e.finpow());
        Draw.color(Pal.remove);
        Draw.alpha(e.fout() * 0.4f);
        Fill.circle(e.x, e.y, e.rotation * e.finpow());
        Draw.reset();
    });

    public static class MarkerBulletType extends BasicBulletType {
        public MarkerBulletType() {
            super();
            damage = 0;
            speed = 5f;
            width = 9f;
            height = 9f;
            lifetime = 30f;
            scaleLife = true;
            homingPower = 0.05f;
            homingRange = 40f;
            frontColor = Color.white;
            backColor = Color.orange;
            trailColor = Color.orange;
            trailLength = 45;
            trailWidth = 9f / 5;
        }

        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            if(bullet.owner instanceof MarkerTurretBuild owner && entity instanceof Unit hitUnit) {
                if(owner.hasMark(hitUnit)) {
                    owner.activeMark(hitUnit);
                }
            }
            super.hitEntity(bullet, entity, health);
        }
    }

    public MarkerTurret(String name) {
        super(name);

        health = 2300;
        size = 3;

        range = 10f * Vars.tilesize;
        reload = 600f;
        
        ammoTypes.put(Items.pyratite, new MarkerBulletType());
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
        
        marker_mark_range_0 = addUpgrade("marker-mark-range-0", "", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 50), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0);
        marker_mark_range_1 = addUpgrade("marker-mark-range-1", "", ItemStack.with(Items.copper, 1000, Items.lead, 1000, Items.graphite, 500, Items.silicon, 200), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0).levelAfter(marker_mark_range_0);
        marker_mark_range_2 = addUpgrade("marker-mark-range-2", "", ItemStack.with(Items.copper, 3000, Items.lead, 3000, Items.graphite, 2000, Items.silicon, 600, Items.titanium, 1400), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0).levelAfter(marker_mark_range_1);

        marker_range_0 = addUpgrade("marker-range-0", "", ItemStack.with(Items.copper, 200, Items.lead, 100), (MarkerTurretBuild build) -> build.RangeTier++, (MarkerTurretBuild build) -> build.RangeTier = 0).requires(marker_mark_range_0);
        marker_range_1 = addUpgrade("marker-range-1", "", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.silicon, 150), (MarkerTurretBuild build) -> build.RangeTier++, (MarkerTurretBuild build) -> build.RangeTier = 0).levelAfter(marker_range_0).requires(marker_mark_range_1);

        marker_marktime_0 = addUpgrade("marker-marktime-0", "", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 200), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0);
        marker_marktime_1 = addUpgrade("marker-marktime-1", "", ItemStack.with(Items.copper, 1500, Items.lead, 1500, Items.graphite, 1000, Items.silicon, 400, Items.titanium, 600), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0).levelAfter(marker_marktime_0);
        marker_marktime_2 = addUpgrade("marker-marktime-2", "", ItemStack.with(Items.copper, 4000, Items.lead, 4000, Items.graphite, 2500, Items.silicon, 1000, Items.titanium, 1500, Items.thorium, 400), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0).levelAfter(marker_marktime_1);
    }

    public float[]
    markDamage = {0f, 100f, 500f, 1000f},
    markChainRange = {0f, 16f, 28f, 40f},
    markMarkRange = {160f, 240f, 320f, 400f},
    markRange = {80f, 120f, 160f},
    markTime = {300f, 240f, 180f, 120f};

    protected static float pick(float[] arr, int tier) {
        return arr[Mathf.clamp(tier, 0, arr.length - 1)];
    }

    public class MarkerTurretBuild extends NevixTurretBuild {
        public int
        MarkTier, RangeTier,
        MarkRangeTier, MarkTimeTier;
        
        public Seq<Unit> markedUnitSeq = new Seq<>();
        public ObjectIntMap<Unit> marks = new ObjectIntMap<>();
        public float markTimer;
        
        public IntIntMap pendingMarks = new IntIntMap();
        
        @Override
        public byte version() {
            return (byte)(super.version() + 1);
        }

        @Override
        public void write(Writes write) {
            super.write(write);

            write.f(markTimer);

            int count = 0;
            for(int i = 0; i < markedUnitSeq.size; i++) {
                Unit u = markedUnitSeq.get(i);
                if(u != null && u.isValid() && marks.get(u, 0) > 0) count++;
            }
            write.i(count);
            for(int i = 0; i < markedUnitSeq.size; i++) {
                Unit u = markedUnitSeq.get(i);
                if(u != null && u.isValid() && marks.get(u, 0) > 0) {
                    write.i(u.id);
                    write.i(marks.get(u, 0));
                }
            }
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);

            if(revision >= version()) {
                markTimer = read.f();
                int count = read.i();
                pendingMarks.clear();
                for(int i = 0; i < count; i++) {
                    int id = read.i();
                    int stacks = read.i();
                    pendingMarks.put(id, stacks);
                }
            }
        }
        
        protected void restoreMarks() {
            for(IntIntMap.Entry e : pendingMarks.entries()) {
                Unit u = Groups.unit.getByID(e.key);
                if(u != null && u.isValid() && e.value > 0 && u.team != team) {
                    marks.put(u, e.value);
                    if(!markedUnitSeq.contains(u, true)) markedUnitSeq.add(u);
                }
            }
            pendingMarks.clear();
        }
        
        @Override
        public float range() {
            return pick(markRange, RangeTier);
        }

        public boolean hasMark(Unit unit) {
            return marks.get(unit, 0) > 0;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            
            if(MarkTier <= 0) {
                if(markedUnitSeq.size > 0) {
                    markedUnitSeq.clear();
                    marks.clear();
                }
                markTimer = 0f;
                pendingMarks.clear();
                return;
            }

            if(pendingMarks.size > 0) restoreMarks();

            cleanMarks();

            markTimer += delta();
            float interval = pick(markTime, MarkTimeTier);
            if(markTimer >= interval) {
                markTimer %= interval;
                applyMarks();
            }
        }
        
        public void cleanMarks() {
            for(int i = markedUnitSeq.size - 1; i >= 0; i--) {
                Unit u = markedUnitSeq.get(i);
                if(u == null || !u.isValid()) {
                    if(u != null) marks.remove(u, 0);
                    markedUnitSeq.remove(i);
                }
            }
        }
        
        public void applyMarks() {
            float r = pick(markMarkRange, MarkRangeTier);
            Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, u -> {
                if(!u.isValid() || !u.within(this, r + u.hitSize / 2f)) return;
                marks.increment(u, 0, 1);
                if(!markedUnitSeq.contains(u, true)) markedUnitSeq.add(u);
            });
        }
        
        public void activeMark(Unit origin) {
            if(MarkTier <= 0 || origin == null || !hasMark(origin)) return;

            float dmgPerMark = pick(markDamage, MarkTier);
            float chain = pick(markChainRange, MarkTier);

            Seq<Unit> queue = new Seq<>();
            ObjectSet<Unit> done = new ObjectSet<>();
            queue.add(origin);
            done.add(origin);

            for(int i = 0; i < queue.size; i++) {
                Unit src = queue.get(i);
                int stacks = marks.get(src, 0);
                marks.remove(src, 0);
                markedUnitSeq.remove(src, true);
                if(stacks <= 0) continue;

                float sx = src.x, sy = src.y;
                float total = dmgPerMark * stacks;
                markBlast.at(sx, sy, chain);

                Units.nearbyEnemies(team, sx - chain, sy - chain, chain * 2f, chain * 2f, u -> {
                    if(u.dead || !u.within(sx, sy, chain + u.hitSize / 2f)) return;
                    u.damage(total);
                    if(marks.get(u, 0) > 0 && done.add(u)) {
                        queue.add(u);
                    }
                });
            }
        }

        @Override
        public void draw() {
            super.draw();
            if(MarkTier <= 0) return;

            Draw.z(Layer.flyingUnit + 1f);
            Draw.color(Pal.remove);
            for(int i = 0; i < markedUnitSeq.size; i++) {
                Unit u = markedUnitSeq.get(i);
                if(u == null || !u.isValid()) continue;
                int n = Math.min(marks.get(u, 0), 8);
                float by = u.y + u.hitSize / 2f + 4f;
                for(int j = 0; j < n; j++) {
                    float bx = u.x + (j - (n - 1) / 2f) * 4f;
                    Fill.square(bx, by, 1.6f, 45f);
                }
            }
            Draw.reset();
        }
    }
}