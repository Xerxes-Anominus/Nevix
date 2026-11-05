package nevix.turret;

import arc.Core;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.bullet.BulletType;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.StatValues;

public class NevixTurret extends ItemTurret {
    public int maxTier = 99;
    
    public int upgradeColumns = 3;
    
    public Seq<Upgrade> upgrades = new Seq<>();

    public static class Upgrade {
        public final String name, desc;
        public final ItemStack[] cost;
        public final Cons<NevixTurretBuild> effect;

        public Upgrade(String name, String desc, ItemStack[] cost, Cons<NevixTurretBuild> effect) {
            this.name = Core.bundle.has("upgrade-branch." + name + ".name") ? Core.bundle.get("upgrade-branch." + name + ".name") : name;
            this.desc = Core.bundle.has("upgrade-branch." + name + ".desc") ? Core.bundle.get("upgrade-branch." + name + ".desc") : desc;
            this.cost = cost;
            this.effect = effect;
        }
    }

    public NevixTurret(String name) {
        super(name);
        configurable = true;
        
        config(Integer.class, (NevixTurretBuild b, Integer id) -> b.requestUpgrade(id));
        
        config(Point2.class, (NevixTurretBuild b, Point2 p) -> b.applyConfirm(p.x, p.y));
    }
    
    public Upgrade addUpgrade(String name, String desc, ItemStack[] cost, Cons<NevixTurretBuild> effect) {
        Upgrade u = new Upgrade(name, desc, cost, effect);
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
        protected int tier = 0;
        protected int currentTab = 0;
        protected BaseDialog dialog;
        
        protected boolean preview = false;
        
        private boolean confirming = false;
        
        public int getTier() {
            return tier;
        }
        
        public void setTier(int value) {
            value = Mathf.clamp(value, 0, maxTier);
            if (value == tier) return;
            int old = tier;
            tier = value;
            onTierChanged(old, value);
        }
        
        public void syncTier(int value) {
            if (Vars.net.client()) return;
            sendConfirm(-1, value);
        }
        
        protected void onTierChanged(int oldTier, int newTier) {
            rebuild();
        }

        @Override
        public byte version() {
            return (byte)(super.version() + 1);
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(tier);
        }

        @Override
        public void read(Reads read, byte revision) {
            byte base = super.version();
            super.read(read, (byte)Math.min(revision, base));
            if (revision > base) {
                tier = Mathf.clamp(read.i(), 0, maxTier);
            }
        }
        
        protected void requestUpgrade(int id) {
            if (Vars.net.client()) return;
            if (id < 0 || id >= upgrades.size) return;

            Upgrade up = upgrades.get(id);
            
            if (!canUpgrade(up, tier) || !canAfford(up.cost)) return;

            payCost(up.cost);
            sendConfirm(id, 0);
            
            if (Vars.net.active()) sendConfirm(-1, tier);
        }
        
        protected void applyConfirm(int id, int newTier) {
            if (Vars.net.server() && !confirming) return;

            if (id >= 0 && id < upgrades.size) {
                upgrades.get(id).effect.get(this);
                rebuild();
            } else if (id == -1) {
                setTier(newTier);
            }
        }

        protected void sendConfirm(int id, int value) {
            confirming = true;
            try {
                configureAny(new Point2(id, value));
            } finally {
                confirming = false;
            }
        }
        
        protected boolean canUpgrade(Upgrade up, int tier) {
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
            
            Table topBar = new Table();
            topBar.add().size(52f);
            topBar.add(tabs).expandX().center();
            topBar.button(Icon.cancel, Styles.cleari, () -> dialog.hide()).size(52f);
            cont.add(topBar).growX().pad(8f).row();
            
            Table content = new Table();
            if (currentTab == 0) {
                buildMainTab(content);
            } else {
                buildUpgradeTab(content);
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
            float paneW = upgradeColumns * (upgradeCardWidth() + 10f) + 24f;

            if (isFree()) {
                table.add(Core.bundle.get("upgrade.free")).color(Pal.accent).padBottom(8f).row();
            }
            if (!preview && !Vars.state.isMenu()) {
                table.add(buildCoreResources()).width(paneW).padBottom(10f).row();
            }

            Table grid = new Table();
            grid.top();
            getUpgrades(grid);

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
            for (int i = 0; i < upgrades.size; i++) {
                buildUpgradeCard(table, i);
                if ((i + 1) % upgradeColumns == 0) table.row();
            }
        }

        protected void addStat(Table table, String name, String value, Color color) {
            Table line = new Table();
            line.left();
            line.add(Core.bundle.has("block." + block.name + ".custom-stat." + name + ".name") ? Core.bundle.get("block." + block.name + ".custom-stat." + name + ".name") : name + ": ").color(color).left();
            line.add(Core.bundle.has("block." + block.name + ".custom-stat." + name + ".value") ? Core.bundle.get("block." + block.name + ".custom-stat." + name + ".value") : value).color(color).left().padLeft(5f);
            table.add(line).growX().left().row();
        }
        
        protected void addStat(Table table, String name, String value) {
            addStat(table, name, value, Color.white);
        }
        
        protected void addBulletStatTable(Table table, BulletType bullet) {
            ObjectMap<UnlockableContent, BulletType> map = new ObjectMap<>();
            map.put(block, bullet);

            Table wrap = new Table();
            wrap.left();
            StatValues.ammo(map).display(wrap);
            table.add(wrap).growX().left().row();
        }

        protected void buildUpgradeCard(Table table, int id) {
            Upgrade up = upgrades.get(id);
            float cardW = upgradeCardWidth();

            Table uTable = new Table();
            uTable.background(Styles.black6);
            uTable.margin(12f);

            uTable.add(up.name).color(Pal.accent).left().colspan(2).row();

            uTable.add(up.desc).color(Color.lightGray).left().colspan(2).wrap().width(cardW - 24f).padTop(4f).row();

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
        
        protected void previewUpgrade(int id) {
            upgrades.get(id).effect.get(this);
            rebuild();
        }

        protected boolean canBuy(int id) {
            Upgrade up = upgrades.get(id);
            return canUpgrade(up, tier) && canAfford(up.cost);
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