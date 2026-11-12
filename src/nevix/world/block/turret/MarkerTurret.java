package nevix.world.block.turret;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;

public class MarkerTurret extends NevixTurret {
    public class MarkerBulletType extends BasicBulletType {
        public MarkerBulletType() {
            super();
            damage = 0f;
            speed = 5f;
            scaleLife = true;
            width = 9f;
            height = 9f;
            trailWidth = 9f / 5;
            trailLength = 18;
            frontColor = Color.white;
            backColor = Color.orange;
            trailColor = Color.orange;
        }

        @Override
        public void hitEntity(Bullet bullet, Hitboxc entity, float health) {
            super.hitEntity(bullet, entity, health);
            if(bullet.owner instanceof MarkerTurretBuild owner && entity instanceof Unit hitUnit) {
                if(owner.hasMark(hitUnit)) {
                    owner.activeMark(hitUnit);
                }
            }
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

    @Override
    public void init() {
        super.init();
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
        // mark_range phải tạo trước range vì range.requires(mark_range)
        marker_mark_range_0 = addUpgrade("marker-mark-range-0", "", ItemStack.with(Items.copper, 300, Items.lead, 300, Items.graphite, 50), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0);
        marker_mark_range_1 = addUpgrade("marker-mark-range-1", "", ItemStack.with(Items.copper, 1000, Items.lead, 1000, Items.graphite, 500, Items.silicon, 200), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0).levelAfter(marker_mark_range_0);
        marker_mark_range_2 = addUpgrade("marker-mark-range-2", "", ItemStack.with(Items.copper, 3000, Items.lead, 3000, Items.graphite, 2000, Items.silicon, 600, Items.titanium, 1400), (MarkerTurretBuild build) -> build.MarkRangeTier++, (MarkerTurretBuild build) -> build.MarkRangeTier = 0).levelAfter(marker_mark_range_1);

        marker_mark_0 = addUpgrade("marker-mark-0", "", ItemStack.with(Items.copper, 400, Items.lead, 400, Items.graphite, 100), (MarkerTurretBuild build) -> build.MarkTier++, (MarkerTurretBuild build) -> build.MarkTier = 0);
        marker_mark_1 = addUpgrade("marker-mark-1", "", ItemStack.with(Items.copper, 1200, Items.lead, 1200, Items.graphite, 500, Items.titanium, 150), (MarkerTurretBuild build) -> build.MarkTier++, (MarkerTurretBuild build) -> build.MarkTier = 0).levelAfter(marker_mark_0);
        marker_mark_2 = addUpgrade("marker-mark-2", "", ItemStack.with(Items.copper, 2500, Items.lead, 2500, Items.graphite, 1500, Items.titanium, 800, Items.thorium, 200), (MarkerTurretBuild build) -> build.MarkTier++, (MarkerTurretBuild build) -> build.MarkTier = 0).levelAfter(marker_mark_1);

        marker_range_0 = addUpgrade("marker-range-0", "", ItemStack.with(Items.copper, 200, Items.lead, 100), (MarkerTurretBuild build) -> build.RangeTier++, (MarkerTurretBuild build) -> build.RangeTier = 0).requires(marker_mark_range_0);
        marker_range_1 = addUpgrade("marker-range-1", "", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.silicon, 150), (MarkerTurretBuild build) -> build.RangeTier++, (MarkerTurretBuild build) -> build.RangeTier = 0).levelAfter(marker_range_0).requires(marker_mark_range_1);

        marker_marktime_0 = addUpgrade("marker-marktime-0", "", ItemStack.with(Items.copper, 500, Items.lead, 500, Items.graphite, 200), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0);
        marker_marktime_1 = addUpgrade("marker-marktime-1", "", ItemStack.with(Items.copper, 1500, Items.lead, 1500, Items.graphite, 1000, Items.silicon, 400, Items.titanium, 600), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0).levelAfter(marker_marktime_0);
        marker_marktime_2 = addUpgrade("marker-marktime-2", "", ItemStack.with(Items.copper, 4000, Items.lead, 4000, Items.graphite, 2500, Items.silicon, 1000, Items.titanium, 1500, Items.thorium, 400), (MarkerTurretBuild build) -> build.MarkTimeTier++, (MarkerTurretBuild build) -> build.MarkTimeTier = 0).levelAfter(marker_marktime_1);
    }

    public float[]
    markDamage = {100f, 250f, 500f, 1000f},       // theo MarkTier, nhân với số dấu ấn
    markChainRange = {16f, 28f, 40f, 56f},       // theo MarkTier
    markMarkRange = {160f, 240f, 320f, 400f},   // theo MarkRangeTier
    markRange = {80f, 120f, 160f},              // theo RangeTier (tầm bắn)
    markTime = {300f, 240f, 180f, 120f};        // theo MarkTimeTier

    private static float get(float[] arr, int tier) {
        return arr[Mathf.clamp(tier, 0, arr.length - 1)];
    }

    public class MarkerTurretBuild extends NevixTurretBuild {
        public int
        MarkTier, RangeTier,
        MarkRangeTier, MarkTimeTier;

        public Seq<Unit> markedUnitSeq = new Seq<>();
        public ObjectIntMap<Unit> marks = new ObjectIntMap<>(); // unit -> số dấu ấn

        public float markTimer;

        private final Seq<Unit> queue = new Seq<>(), hitTmp = new Seq<>();

        @Override
        public float range() {
            return get(markRange, RangeTier);
        }

        public boolean hasMark(Unit unit) {
            return marks.get(unit, 0) > 0;
        }

        public void addMark(Unit unit) {
            marks.put(unit, marks.get(unit, 0) + 1);
            if(!markedUnitSeq.contains(unit)) markedUnitSeq.add(unit);
        }

        @Override
        public void updateTile() {
            super.updateTile();

            // dọn unit đã chết / không còn hợp lệ
            markedUnitSeq.removeAll(u -> {
                boolean remove = u == null || !u.isValid();
                if(remove && u != null) marks.remove(u, 0);
                return remove;
            });

            markTimer += delta();
            if(markTimer >= get(markTime, MarkTimeTier)) {
                markTimer = 0f;
                applyMarks();
            }
        }

        /** Mỗi markTime: +1 dấu ấn cho toàn bộ kẻ địch trong markMarkRange. */
        public void applyMarks() {
            float r = get(markMarkRange, MarkRangeTier);
            Units.nearbyEnemies(team, x - r, y - r, r * 2f, r * 2f, u -> {
                if(u.isValid() && u.checkTarget(targetAir, targetGround) && within(u, r + u.hitSize / 2f)) {
                    addMark(u);
                }
            });
        }

        /** Kích nổ toàn bộ dấu ấn trên unit, lan sang các unit có dấu ấn trong chain range. */
        public void activeMark(Unit unit) {
            if(unit == null || unit.dead()) return;

            queue.clear();
            queue.add(unit);

            while(!queue.isEmpty()) {
                final Unit src = queue.pop();
                int stacks = marks.remove(src, 0); // gỡ dấu ấn trước => chuỗi nổ không lặp vô hạn
                markedUnitSeq.remove(src);
                if(stacks <= 0) continue;

                final float cx = src.x, cy = src.y;
                final float r = get(markChainRange, MarkTier);
                final float dmg = get(markDamage, MarkTier) * stacks;

                Fx.explosion.at(cx, cy);

                hitTmp.clear();
                if(src.isValid()) hitTmp.add(src);
                if(r > 0f) {
                    Units.nearbyEnemies(team, cx - r, cy - r, r * 2f, r * 2f, u -> {
                        if(u != src && u.isValid() && u.within(cx, cy, r + u.hitSize / 2f)) hitTmp.add(u);
                    });
                }

                for(Unit u : hitTmp) {
                    if(dmg > 0f) u.damage(dmg);
                    if(hasMark(u)) queue.addUnique(u); // dấu ấn của unit này nổ theo
                }
            }
        }

        /** NevixTurret chỉ lưu done[], nên phải áp lại hiệu ứng upgrade sau khi load. */
        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);

            MarkTier = RangeTier = MarkRangeTier = MarkTimeTier = 0;
            ensureDone();
            for(int i = 0; i < done.length; i++) {
                if(done[i]) upgrades.get(i).effect.get(this);
            }
        }

        @Override
        public void draw() {
            super.draw();

            Draw.z(Layer.flyingUnit + 1f);
            Draw.color(Pal.remove);
            for(Unit u : markedUnitSeq) {
                if(!u.isValid()) continue;
                int n = marks.get(u, 0);
                for(int i = 0; i < n; i++) {
                    Lines.stroke(1f);
                    Lines.circle(u.x, u.y, u.hitSize / 2f + 2f + i * 2f);
                }
            }
            Draw.reset();
        }
    }
}