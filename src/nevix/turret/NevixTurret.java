package nevix.turret;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;
import nevix.world.NevixStat;

import java.util.Arrays;

public class NevixTurret extends ItemTurret {
    private static final int CONFIRM_BUY = 0, CONFIRM_DELETE = 1;

    public int upgradeColumns = 3;

    public float deleteCostRatio = 0.5f;

    public Seq<Upgrade> upgrades = new Seq<>();

    public static class Upgrade {
        public final String name, desc;
        public final ItemStack[] cost;
        public final Cons<NevixTurretBuild> effect;

        public Cons<NevixTurretBuild> remove;

        public int id = -1;
        public int level = 1;
        public Upgrade prevLevel, nextLevel;
        public final Seq<Upgrade> reqs = new Seq<>();
        public final Seq<Upgrade> lockedBy = new Seq<>();
        public final Seq<Upgrade> removesTargets = new Seq<>();

        public Upgrade(String name, String desc, ItemStack[] cost, Cons<NevixTurretBuild> effect) {
            this.name = Core.bundle.has("upgrade-branch." + name + ".name") ? Core.bundle.get("upgrade-branch." + name + ".name") : name;
            this.desc = Core.bundle.has("upgrade-branch." + name + ".desc") ? Core.bundle.get("upgrade-branch." + name + ".desc") : desc;
            this.cost = cost;
            this.effect = effect;
        }

        public Upgrade requires(Upgrade... list) {
            for (Upgrade u : list) {
                if (u != null && u != this && !reqs.contains(u, true)) reqs.add(u);
            }
            return this;
        }

        public Upgrade levelAfter(Upgrade prev) {
            prevLevel = prev;
            prev.nextLevel = this;
            level = prev.level + 1;
            return this;
        }

        
        public Upgrade removes(Upgrade... list) {
            for (Upgrade u : list) {
                if (u == null || u == this) continue;
                if (!u.lockedBy.contains(this, true)) u.lockedBy.add(this);
                if (!removesTargets.contains(u, true)) removesTargets.add(u);
            }
            return this;
        }

        public Upgrade remove(Cons<NevixTurretBuild> remove) {
            this.remove = remove;
            return this;
        }

        public <T extends NevixTurretBuild> Upgrade remove(Class<T> type, Cons<T> remove) {
            this.remove = b -> remove.get(type.cast(b));
            return this;
        }
    }

    public NevixTurret(String name) {
        super(name);
        configurable = true;
        
        config(Integer.class, (NevixTurretBuild b, Integer v) -> {
            if (v >= 0) b.requestUpgrade(v);
            else b.requestDelete(~v);
        });

        config(Point2.class, (NevixTurretBuild b, Point2 p) -> b.applyConfirm(p.x, p.y));
    }
    
    @SuppressWarnings("unchecked")
    public <T extends NevixTurretBuild> Upgrade addUpgrade(String name, String desc, ItemStack[] cost, Cons<T> effect) {
        return register(new Upgrade(name, desc, cost, b -> effect.get((T) b)));
    }

    
    public <T extends NevixTurretBuild> Upgrade addUpgrade(String name, String desc, ItemStack[] cost, Class<T> type, Cons<T> effect) {
        return register(new Upgrade(name, desc, cost, b -> effect.get(type.cast(b))));
    }
    
    @SuppressWarnings("unchecked")
    public <T extends NevixTurretBuild> Upgrade addUpgrade(String name, String desc, ItemStack[] cost, Cons<T> effect, Cons<T> remove) {
        return register(new Upgrade(name, desc, cost, b -> effect.get((T) b))
            .remove(b -> remove.get((T) b)));
    }
    
    public <T extends NevixTurretBuild> Upgrade addUpgrade(String name, String desc, ItemStack[] cost, Class<T> type, Cons<T> effect, Cons<T> remove) {
        return register(new Upgrade(name, desc, cost, b -> effect.get(type.cast(b)))
            .remove(type, remove));
    }

    private Upgrade register(Upgrade u) {
        u.id = upgrades.size;
        upgrades.add(u);
        return u;
    }

    public NevixTurretBuild openPreview(int tab) {
        if (Vars.headless) return null;

        NevixTurretBuild b = (NevixTurretBuild) newBuilding();
        b.create(this, Team.sharded);
        b.preview = true;
        b.openDialog(tab);
        return b;
    }

    public NevixTurretBuild openPreview() {
        return openPreview(0);
    }

    public class NevixTurretBuild extends ItemTurretBuild {
        protected int currentTab = 0;
        protected BaseDialog dialog;

        protected boolean preview = false;
        
        protected boolean[] done = new boolean[0];

        private boolean confirming = false;

        protected void ensureDone() {
            if (done.length != upgrades.size) done = Arrays.copyOf(done, upgrades.size);
        }

        public boolean isDone(int id) {
            if (id < 0 || id >= upgrades.size) return false;
            ensureDone();
            return done[id];
        }

        public boolean isDone(Upgrade up) {
            return up != null && isDone(up.id);
        }
        
        protected boolean isVisible(Upgrade up) {
            if (isDone(up) || isLocked(up)) return false;
            return up.prevLevel == null || isDone(up.prevLevel);
        }
        
        protected boolean isLocked(Upgrade up) {
            return isLocked(up, 0);
        }

        private boolean isLocked(Upgrade up, int depth) {
            if (depth > upgrades.size) return false;
            for (Upgrade a : up.lockedBy) {
                if (isDone(a)) return true;
            }
            if (up.prevLevel != null && !isDone(up.prevLevel) && isLocked(up.prevLevel, depth + 1)) return true;
            for (Upgrade r : up.reqs) {
                if (!isDone(r) && isLocked(r, depth + 1)) return true;
            }
            return false;
        }
        
        protected boolean requirementsMet(Upgrade up) {
            if (up.prevLevel != null && !isDone(up.prevLevel)) return false;
            for (Upgrade r : up.reqs) {
                if (!isDone(r)) return false;
            }
            for (Upgrade r : up.removesTargets) {
                if (isDone(r)) return false;
            }
            return true;
        }
        
        protected Seq<Upgrade> conflictsOf(Upgrade up) {
            Seq<Upgrade> out = new Seq<>();
            for (Upgrade r : up.removesTargets) {
                if (isDone(r)) out.addUnique(topLevel(r));
            }
            return out;
        }
        
        protected Upgrade topLevel(Upgrade up) {
            while (up.nextLevel != null && isDone(up.nextLevel)) up = up.nextLevel;
            return up;
        }
        
        protected void markDone(int id) {
            if (id < 0 || id >= upgrades.size) return;
            ensureDone();
            if (done[id]) return;
            done[id] = true;
            upgrades.get(id).effect.get(this);
            rebuild();
        }
        
        protected void unmarkDone(int id) {
            if (id < 0 || id >= upgrades.size) return;
            ensureDone();
            if (!done[id]) return;
            for (Upgrade u = topLevel(upgrades.get(id)); u != null; u = u.prevLevel) {
                if (!done[u.id]) continue;
                done[u.id] = false;
                if (u.remove != null) u.remove.get(this);
            }
            rebuild();
        }

        @Override
        public byte version() {
            return (byte)(super.version() + 2);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            ensureDone();
            write.i(done.length);
            for (boolean d : done) write.bool(d);
        }

        @Override
        public void read(Reads read, byte revision) {
            byte base = super.version();
            super.read(read, (byte)Math.min(revision, base));

            if (revision == base + 1) {
                read.i();
            } else if (revision > base + 1) {
                int n = read.i();
                boolean[] loaded = new boolean[Math.max(n, upgrades.size)];
                for (int i = 0; i < n; i++) loaded[i] = read.bool();
                done = Arrays.copyOf(loaded, upgrades.size);
            }
        }

        protected void requestUpgrade(int id) {
            if (Vars.net.client()) return;
            if (id < 0 || id >= upgrades.size) return;
            if (!canBuy(id)) return;

            payCost(upgrades.get(id).cost);
            sendConfirm(id, CONFIRM_BUY);
        }

        protected void requestDelete(int id) {
            if (Vars.net.client()) return;
            if (!canDelete(id)) return;

            payCost(deleteCost(upgrades.get(id)));
            sendConfirm(id, CONFIRM_DELETE);
        }

        protected void applyConfirm(int id, int type) {
            if (Vars.net.server() && !confirming) return;
            if (type == CONFIRM_DELETE) unmarkDone(id);
            else markDone(id);
        }

        protected void sendConfirm(int id, int type) {
            confirming = true;
            try {
                configureAny(new Point2(id, type));
            } finally {
                confirming = false;
            }
        }

        protected boolean canUpgrade(Upgrade up) {
            return true;
        }

        @Override
        public void buildConfiguration(Table table) {
            table.button(Icon.settings, Styles.cleari, () -> {
                openDialog();
                if (!Vars.headless) Vars.control.input.config.hideConfig();
            }).size(40f);
        }

        public void openMain() {
            openDialog(0);
        }

        public void openUpgrade() {
            openDialog(1);
        }

        public void openStorage() {
            openDialog(2);
        }

        public void openDialog(int tab) {
            currentTab = tab;
            openDialog();
        }

        public void openDialog() {
            if (Vars.headless) return;

            if (dialog == null) {
                dialog = new BaseDialog("");
                dialog.titleTable.remove();
                dialog.buttons.remove();
                dialog.margin(0f);
                dialog.cont.margin(0f);

                if (dialog.getCell(dialog.cont) != null) dialog.getCell(dialog.cont).pad(0f);
                dialog.closeOnBack();
            }
            rebuildDialog();
            dialog.show();
        }

        protected void rebuildDialog() {
            if (dialog == null) return;

            Table cont = dialog.cont;
            cont.clear();
            cont.top();

            Table tabs = new Table();
            tabs.background(Styles.black6);

            tabs.button(Core.bundle.get("upgrade.main.name"), Styles.togglet, () -> {
                currentTab = 0;
                rebuild();
            }).size(180f, 52f).checked(currentTab == 0);

            tabs.button(Core.bundle.get("upgrade.upgrade.name"), Styles.togglet, () -> {
                currentTab = 1;
                rebuild();
            }).size(180f, 52f).checked(currentTab == 1);

            tabs.button(Core.bundle.get("upgrade.storage.name", "Storage"), Styles.togglet, () -> {
                currentTab = 2;
                rebuild();
            }).size(180f, 52f).checked(currentTab == 2);

            Table topBar = new Table();
            topBar.add().size(52f);
            topBar.add(tabs).expandX().center();
            topBar.button(Icon.cancel, Styles.cleari, () -> dialog.hide()).size(52f);
            cont.add(topBar).growX().pad(8f).row();

            Table content = new Table();
            if (currentTab == 0) {
                buildMainTab(content);
            } else if (currentTab == 1) {
                buildUpgradeTab(content);
            } else {
                buildStorageTab(content);
            }
            cont.add(content).grow();
        }

        protected void buildMainTab(Table table) {
            float half = Core.scene.getWidth() / 2f;

            Table iconSection = new Table();
            iconSection.image(getBlockImage()).scaling(Scaling.fit).grow();

            Table stats = new Table();
            stats.top().left();
            getStats(stats);

            ScrollPane statsPane = new ScrollPane(stats);
            statsPane.setScrollingDisabled(true, false);
            statsPane.setFadeScrollBars(false);

            Table statsBox = new Table();
            statsBox.background(Tex.pane);
            statsBox.margin(12f);
            statsBox.add(statsPane).grow();

            table.add(iconSection).width(half).growY();
            table.add(statsBox).grow().pad(16f);
        }

        protected TextureRegion getBlockImage() {
            TextureRegion region = Core.atlas.find(block.name + "-icon");
            if (region.found()) return region;

            region = block.fullIcon;
            if (region != null && region.found()) return region;

            return Core.atlas.find("error");
        }

        protected void buildUpgradeTab(Table table) {
            buildCardTab(table, this::getUpgrades);
        }

        protected void buildStorageTab(Table table) {
            buildCardTab(table, this::getStorage);
        }
        
        protected void buildCardTab(Table table, Cons<Table> filler) {
            float paneW = upgradeColumns * (upgradeCardWidth() + 10f) + 24f;

            if (isFree()) {
                table.add(Core.bundle.get("upgrade.free")).color(Pal.accent).padBottom(8f).row();
            }
            if (!preview && !Vars.state.isMenu()) {
                table.add(buildCoreResources()).width(paneW).padBottom(10f).row();
            }

            Table grid = new Table();
            grid.top();
            filler.get(grid);

            ScrollPane pane = new ScrollPane(grid);
            pane.setScrollingDisabled(true, false);
            pane.setFadeScrollBars(false);

            table.add(pane).width(paneW).growY().padBottom(8f).row();
        }

        protected float upgradeCardWidth() {
            return Mathf.clamp(Core.scene.getWidth() * 0.85f / upgradeColumns - 12f, 200f, 320f);
        }

        protected Table buildCoreResources() {
            Table coreResTable = new Table();
            coreResTable.background(Styles.black6);
            coreResTable.margin(5f);

            coreResTable.update(() -> {
                coreResTable.clear();
                coreResTable.add(Core.bundle.get("upgrade.core-resources.name")).color(Pal.accent).left().padRight(15f);

                if (team != null && team.core() != null) {
                    int count = 0;
                    for (Item item : mindustry.content.Items.serpuloItems) {
                        int amount = team.core().items.get(item);
                        if (amount > 0) {
                            coreResTable.image(item.uiIcon).size(20f).padRight(3f);
                            coreResTable.add(String.valueOf(amount)).color(Color.white).padRight(10f);
                            count++;
                            if (count % 6 == 0) coreResTable.row();
                        }
                    }
                    if (count == 0) {
                        coreResTable.add(Core.bundle.get("upgrade.core-resources.empty")).color(Color.lightGray);
                    }
                } else {
                    coreResTable.add(Core.bundle.get("upgrade.core-resources.coreless")).color(Color.lightGray);
                }
            });

            return coreResTable;
        }

        protected void getStats(Table table) {
        }
        
        protected void getUpgrades(Table table) {
            int shown = 0;
            for (int i = 0; i < upgrades.size; i++) {
                if (!isVisible(upgrades.get(i))) continue;
                buildUpgradeCard(table, i);
                if (++shown % upgradeColumns == 0) table.row();
            }

            if (shown == 0) {
                table.add(Core.bundle.get("upgrade.all-done", "All upgrades completed")).color(Color.lightGray).pad(24f);
            }
        }
        
        protected void getStorage(Table table) {
            int shown = 0;
            for (int i = 0; i < upgrades.size; i++) {
                Upgrade up = upgrades.get(i);
                if (!isDone(up) || topLevel(up) != up) continue;
                buildStorageCard(table, i);
                if (++shown % upgradeColumns == 0) table.row();
            }

            if (shown == 0) {
                table.add(Core.bundle.get("upgrade.storage.empty", "No completed upgrades")).color(Color.lightGray).pad(24f);
            }
        }
        
        protected void addStat(Table table, Stat stat, String value, Color color) {
            Table line = new Table();
            line.left();
            line.add(stat.localized() + (stat == NevixStat.none ? "" : ": ")).color(color).left();
            line.add(Core.bundle.has("block." + block.name + ".custom-stat." + stat.name + ".value")
            ? Core.bundle.get("block." + block.name + ".custom-stat." + stat.name + ".value")
            : value).color(color).left().padLeft(5f);
            table.add(line).growX().left().row();
        }
        
        protected void addStat(Table table, Stat stat, String value) {
            addStat(table, stat, value, Color.white);
        }
        
        protected Table addBulletStatTable(Table table, Cons<Table> content) {
            Table bt = new Table();
            bt.left().defaults().padRight(3f).left();
            bt.background(Tex.underline);
            content.get(bt);
            
            table.add(bt).growX().left().row();
            return bt;
        }
        
        protected void addBulletStat(Table bt, Stat stat, String value, int deep, Color color) {
            String spaces = "";
            for(int i = 0; i < deep; i++) {
                spaces += "    ";
            }
            
            bt.add(spaces + stat.localized() + ": ").color(color);
            bt.add(value).color(color);
            bt.row();
        }
        
        protected void addBulletStat(Table bt, Stat stat, String value, int deep) {
            addBulletStat(bt, stat, value, deep, Color.white);
        }
        
        protected void addBulletStat(Table table, Stat stat, String value) {
            addBulletStat(table, stat, value, 0);
        }

        protected void buildUpgradeCard(Table table, int id) {
            Upgrade up = upgrades.get(id);
            float cardW = upgradeCardWidth();

            Table uTable = new Table();
            uTable.background(Styles.black6);
            uTable.margin(12f);

            uTable.add(up.name).color(Pal.accent).left().colspan(2).row();

            uTable.add(up.desc).color(Color.lightGray).left().colspan(2).wrap().width(cardW - 24f).padTop(4f).row();

            Seq<Upgrade> conflicts = conflictsOf(up);
            if (up.reqs.size > 0 || conflicts.size > 0) {
                Table req = new Table();
                req.left();
                req.add(Core.bundle.get("upgrade.requires", "Requires:")).color(Pal.accent).left().row();
                for (Upgrade r : up.reqs) {
                    req.add("- " + r.name).color(isDone(r) ? Pal.heal : Color.scarlet)
                       .left().wrap().width(cardW - 24f).row();
                }
                for (Upgrade c : conflicts) {
                    req.add("- " + Core.bundle.get("upgrade.delete-first", "Delete first:") + " " + c.name).color(Color.scarlet)
                       .left().wrap().width(cardW - 24f).row();
                }
                uTable.add(req).left().colspan(2).padTop(6f).row();
            }

            Table costTable = new Table();
            costTable.left();
            for (ItemStack stack : up.cost) {
                costTable.image(stack.item.uiIcon).size(28f).padRight(5f);
                costTable.add(String.valueOf(stack.amount)).color(Color.white).padRight(15f);
            }
            uTable.add(costTable).left().growX().padTop(8f);

            uTable.button(Icon.up, Styles.clearNonei, () -> {
                if (!canBuy(id)) return;
                if (preview) previewUpgrade(id);
                else configure(id);
            }).size(48f).padTop(8f)
              .disabled(b -> !canBuy(id));

            table.add(uTable).width(cardW).minHeight(150f).top().pad(5f);
        }
        
        protected void buildStorageCard(Table table, int id) {
            Upgrade up = upgrades.get(id);
            float cardW = upgradeCardWidth();

            Table uTable = new Table();
            uTable.background(Styles.black6);
            uTable.margin(12f);

            uTable.add(up.name).color(Pal.accent).left().colspan(2).row();

            uTable.add(up.desc).color(Color.lightGray).left().colspan(2).wrap().width(cardW - 24f).padTop(4f).row();
            
            Seq<Upgrade> dependents = dependentsOf(up);
            if (dependents.size > 0) {
                Table need = new Table();
                need.left();
                need.add(Core.bundle.get("upgrade.needed-by", "Needed by:")).color(Pal.accent).left().row();
                for (Upgrade d : dependents) {
                    need.add("- " + d.name).color(Color.scarlet).left().wrap().width(cardW - 24f).row();
                }
                uTable.add(need).left().colspan(2).padTop(6f).row();
            }

            Table costTable = new Table();
            costTable.left();
            for (ItemStack stack : deleteCost(up)) {
                costTable.image(stack.item.uiIcon).size(28f).padRight(5f);
                costTable.add(String.valueOf(stack.amount)).color(Color.white).padRight(15f);
            }
            uTable.add(costTable).left().growX().padTop(8f);

            uTable.button(Icon.trash, Styles.clearNonei, () -> {
                if (!canDelete(id)) return;
                if (preview) previewDelete(id);
                else configure(~id);
            }).size(48f).padTop(8f)
              .disabled(b -> !canDelete(id));

            table.add(uTable).width(cardW).minHeight(150f).top().pad(5f);
        }

        protected void previewUpgrade(int id) {
            markDone(id);
        }

        protected void previewDelete(int id) {
            unmarkDone(id);
        }

        protected boolean canBuy(int id) {
            if (id < 0 || id >= upgrades.size) return false;
            Upgrade up = upgrades.get(id);
            return !isDone(id) && !isLocked(up) && requirementsMet(up) && canUpgrade(up) && canAfford(up.cost);
        }
        
        protected boolean canDelete(int id) {
            if (id < 0 || id >= upgrades.size) return false;
            Upgrade up = upgrades.get(id);
            return isDone(up) && topLevel(up) == up && dependentsOf(up).size == 0 && canAfford(deleteCost(up));
        }
        
        protected Seq<Upgrade> dependentsOf(Upgrade top) {
            Seq<Upgrade> out = new Seq<>();
            for (Upgrade d : upgrades) {
                if (!isDone(d)) continue;
                Upgrade dTop = topLevel(d);
                if (dTop == top) continue;
                for (Upgrade u = top; u != null; u = u.prevLevel) {
                    if (d.reqs.contains(u, true)) {
                        out.addUnique(dTop);
                        break;
                    }
                }
            }
            return out;
        }
        
        protected ItemStack[] deleteCost(Upgrade top) {
            Seq<ItemStack> total = new Seq<>();
            for (Upgrade u = top; u != null; u = u.prevLevel) {
                if (!isDone(u)) continue;
                for (ItemStack s : u.cost) {
                    ItemStack found = total.find(t -> t.item == s.item);
                    if (found == null) total.add(new ItemStack(s.item, s.amount));
                    else found.amount += s.amount;
                }
            }
            for (ItemStack s : total) {
                s.amount = Mathf.ceil(s.amount * deleteCostRatio);
            }
            return total.toArray(ItemStack.class);
        }

        protected boolean isFree() {
            return preview || Vars.state.isMenu() || Vars.state.rules.editor || Vars.state.rules.infiniteResources;
        }

        protected boolean canAfford(ItemStack[] cost) {
            if (isFree()) return true;
            if (team == null || team.core() == null) return false;
            for (ItemStack stack : cost) {
                if (team.core().items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        protected void payCost(ItemStack[] cost) {
            if (isFree()) return;
            if (team == null || team.core() == null) return;
            for (ItemStack stack : cost) {
                team.core().items.remove(stack.item, stack.amount);
            }
        }

        protected void rebuild() {
            if (Vars.headless || dialog == null || !dialog.isShown()) return;
            Core.app.post(this::rebuildDialog);
        }

        @Override
        public void onRemoved() {
            super.onRemoved();
            if (dialog != null && dialog.isShown()) dialog.hide();
        }
    }
}