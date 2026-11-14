package nevix.world.block.effect;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import arc.math.Interp;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Nullable;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock.ConstructBuild;
import mindustry.world.meta.Stat;
import nevix.world.meta.NevixStat;

import static mindustry.Vars.net;
import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.world;

public class LuckyBox extends Block {
    public Seq<LuckyReward> rewards = new Seq<>();
    
    public float openTime = 40f;
    public float lidSlide = 0.9f;
    public float lidTurn = 25f;
    public float spread = 1.5f;
    public int placeRadius = 6;
    public boolean respectUnitCap = true;

    public Effect openEffect = Fx.smoke;

    public TextureRegion lidRegion;
    public TextureRegion[] lidVariants;

    public LuckyBox(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = true;
        consumesTap = true;

        config(Boolean.class, (LuckyBuild build, Boolean open) -> {
            if (open && !state.isPaused()) build.open();
        });
    }

    public LuckyBox rewardBlock(Block block, int min, int max, float weight, float sameTeamChance) {
        rewards.add(new LuckyReward(block, null, min, max, weight, sameTeamChance));
        return this;
    }

    public LuckyBox rewardUnit(UnitType unit, int min, int max, float weight, float sameTeamChance) {
        rewards.add(new LuckyReward(null, unit, min, max, weight, sameTeamChance));
        return this;
    }

    @Override
    public void setStats() {
        super.setStats();

        if (rewards.isEmpty()) return;

        float total = 0f;
        for (int i = 0; i < rewards.size; i++) total += rewards.get(i).weight;
        final float totalWeight = total;

        stats.add(NevixStat.luckyRewards, table -> {
            table.row();
            for (int idx = 0; idx < rewards.size; idx++) {
                LuckyReward r = rewards.get(idx);
                String rewardName = r.block != null ? r.block.localizedName : r.unit.localizedName;
                TextureRegion icon = r.block != null ? r.block.uiIcon : r.unit.uiIcon;

                float chance = totalWeight <= 0f ? 0f : r.weight / totalWeight * 100f;
                String amount = r.minAmount == r.maxAmount ? "x" + r.minAmount : "x" + r.minAmount + "-" + r.maxAmount;
                int own = Mathf.round(r.sameTeamChance * 100f);

                table.table(Styles.grayPanel, t -> {
                    t.left().margin(6f);
                    t.image(icon).size(32f).scaling(Scaling.fit).padRight(8f);
                    t.add(rewardName + " " + amount).left().growX();
                    t.add(Mathf.round(chance * 10f) / 10f + "%").color(Pal.accent).padLeft(8f);
                    t.row();
                    t.add(Core.bundle.format("stat.nevix-lucky-team", own, 100 - own)).colspan(3).left().color(Pal.lightishGray).padTop(2f);
                }).growX().uniformX().pad(2f);

                if (idx % 2 == 1) table.row();
            }
        });
    }

    @Override
    public void load() {
        super.load();

        lidRegion = Core.atlas.find(name + "-lid");
        if (variants > 0) {
            lidVariants = new TextureRegion[variants];
            for (int i = 0; i < variants; i++) {
                lidVariants[i] = Core.atlas.find(name + "-lid" + (i + 1), lidRegion);
            }
        }
    }

    @Override
    public TextureRegion[] icons() {
        return Core.atlas.has(name + "-lid") ? new TextureRegion[]{region, lidRegion} : new TextureRegion[]{region};
    }
    
    public int maxOnMap = 5;

    public int countOnMap() {
        return Groups.build.count(b ->
        b.block instanceof LuckyBox ||
        (b instanceof ConstructBuild c && c.current instanceof LuckyBox)
        );
    }

    @Override
    public boolean canPlaceOn(Tile tile, Team team, int rotation) {
        return super.canPlaceOn(tile, team, rotation) && countOnMap() < maxOnMap;
    }

    public static class LuckyReward {
        public @Nullable Block block;
        public @Nullable UnitType unit;
        public int minAmount, maxAmount;
        public float weight;
        public float sameTeamChance;

        public LuckyReward(@Nullable Block block, @Nullable UnitType unit, int min, int max, float weight, float sameTeamChance) {
            this.block = block;
            this.unit = unit;
            this.minAmount = Math.max(1, min);
            this.maxAmount = Math.max(this.minAmount, max);
            this.weight = weight;
            this.sameTeamChance = sameTeamChance;
        }
    }

    public class LuckyBuild extends Building {
        public boolean opened, done;
        public float lid;

        int variant() {
            return variants > 1 ? Mathf.randomSeed(tile.pos(), 0, variants - 1) : 0;
        }

        float lidDir() {
            return Mathf.randomSeed(tile.pos() + 7, 0, 1) == 0 ? -1f : 1f;
        }

        @Override
        public void tapped() {
            if (state.isPaused()) return;
            if (!opened) configure(true);
        }

        public void open() {
            if (opened) return;
            opened = true;
            Sounds.door.at(x, y);
            openEffect.at(x, y);
        }

        
        @Override
        public void updateTile() {
            if (!opened || done) return;

            lid = Mathf.approachDelta(lid, 1f, 1f / openTime);

            if (lid >= 1f) {
                done = true;

                if (!net.client()) {
                    try {
                        giveReward();
                    } catch (Throwable e) {
                        Log.err("LuckyBox: failed to give reward", e);
                    }
                }
            }
        }

        void giveReward() {
            Tile origin = tile;
            Team own = team;
            float cx = x, cy = y;

            LuckyReward reward = pick();
            
            origin.setNet(Blocks.air);
            if (reward == null) return;

            Team target = Mathf.chance(reward.sameTeamChance) ? own : otherTeam(own);
            int amount = Mathf.random(reward.minAmount, reward.maxAmount);

            if (reward.block != null) {
                int placed = 0;
                for (int ring = 0; ring <= placeRadius && placed < amount; ring++) {
                    for (int dx = -ring; dx <= ring && placed < amount; dx++) {
                        for (int dy = -ring; dy <= ring && placed < amount; dy++) {
                            if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) continue;

                            int tx = origin.x + dx, ty = origin.y + dy;
                            if (Build.validPlace(reward.block, target, tx, ty, 0, false)) {
                                Tile at = world.tile(tx, ty);
                                if (at != null) {
                                    at.setNet(reward.block, target, 0);
                                    placed++;
                                }
                            }
                        }
                    }
                }
            } else if (reward.unit != null) {
                for (int i = 0; i < amount; i++) {
                    if (respectUnitCap && !Units.canCreate(target, reward.unit)) break;

                    Tmp.v1.trns(Mathf.random(360f), Mathf.random(spread * tilesize));
                    reward.unit.spawn(target, cx + Tmp.v1.x, cy + Tmp.v1.y);
                }
            }
        }

        @Nullable LuckyReward pick() {
            float total = 0f;
            for (int i = 0; i < rewards.size; i++) total += rewards.get(i).weight;
            if (total <= 0f) return null;

            float roll = Mathf.random(total);
            for (int i = 0; i < rewards.size; i++) {
                LuckyReward r = rewards.get(i);
                roll -= r.weight;
                if (roll <= 0f) return r;
            }
            return rewards.peek();
        }

        Team otherTeam(Team own) {
            Seq<Team> options = new Seq<>();
            for (int i = 0; i < state.teams.active.size; i++) {
                Team t = state.teams.active.get(i).team;
                if (t != own && t != Team.derelict) options.add(t);
            }
            if (options.any()) return options.random();

            return state.rules.waveTeam == own ? state.rules.defaultTeam : state.rules.waveTeam;
        }

        @Override
        public void draw() {
            int v = variant();

            TextureRegion base = variants > 0 && variantRegions != null && v < variantRegions.length ? variantRegions[v] : region;
            Draw.rect(base, x, y);

            TextureRegion lidReg = lidVariants != null && v < lidVariants.length ? lidVariants[v] : lidRegion;
            if (lidReg != null) {
                float p = Interp.pow2Out.apply(Mathf.clamp(lid));
                float dir = lidDir();
                Draw.rect(lidReg, x + dir * p * lidSlide * size * tilesize, y, dir * p * lidTurn);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(opened);
            write.f(lid);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            opened = read.bool();
            lid = read.f();
        }
    }
}