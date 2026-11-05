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
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.bullet.BulletType;
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
    /** Tier tối đa, đặt lại trong constructor của class con nếu cần. */
    public int maxTier = 99;

    /** Danh sách nâng cấp dùng chung cho mọi build của block này. Index trong Seq = id gửi qua mạng. */
    public Seq<Upgrade> upgrades = new Seq<>();

    public static class Upgrade {
        public final String name, desc;
        public final ItemStack[] cost;
        /** Chạy trên MỌI máy (server + client) khi server xác nhận mua. */
        public final Cons<NevixTurretBuild> effect;

        public Upgrade(String name, String desc, ItemStack[] cost, Cons<NevixTurretBuild> effect) {
            this.name = name;
            this.desc = desc;
            this.cost = cost;
            this.effect = effect;
        }
    }

    public NevixTurret(String name) {
        super(name);
        configurable = true;

        // Client -> Server: "tôi muốn mua nâng cấp số id". Chỉ server xử lý.
        config(Integer.class, (NevixTurretBuild b, Integer id) -> b.requestUpgrade(id));

        // Server -> tất cả: xác nhận. x = id nâng cấp (-1 = chỉ đồng bộ tier), y = tier.
        config(Point2.class, (NevixTurretBuild b, Point2 p) -> b.applyConfirm(p.x, p.y));
    }

    /** Gọi trong constructor của class con để đăng ký nâng cấp. */
    public Upgrade addUpgrade(String name, String desc, ItemStack[] cost, Cons<NevixTurretBuild> effect) {
        Upgrade u = new Upgrade(name, desc, cost, effect);
        upgrades.add(u);
        return u;
    }

    public class NevixTurretBuild extends ItemTurretBuild {
        protected int tier = 0;
        protected int currentTab = 0;
        protected BaseDialog dialog;

        /** true chỉ trong lúc server tự gửi gói xác nhận. */
        private boolean confirming = false;

        // ==========================================
        // TIER
        // ==========================================
        public int getTier() {
            return tier;
        }

        /** Đặt tier trên máy hiện tại (không gửi mạng). Dùng trong effect hoặc khi load. */
        public void setTier(int value) {
            value = Mathf.clamp(value, 0, maxTier);
            if (value == tier) return;
            int old = tier;
            tier = value;
            onTierChanged(old, value);
        }

        /** Server gọi để ép mọi client có cùng tier (ví dụ khi đổi tier bằng code/script). */
        public void syncTier(int value) {
            if (Vars.net.client()) return;
            sendConfirm(-1, value);
        }

        /** Override ở class con: tính lại chỉ số theo tier. Chạy trên mọi máy. */
        protected void onTierChanged(int oldTier, int newTier) {
            rebuild();
        }

        @Override
        public byte version() {
            // +1 để save cũ (chưa có tier) vẫn đọc được
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
                // Nếu có chỉ số phụ thuộc tier, tính lại ở đây.
            }
        }

        // ==========================================
        // ĐỒNG BỘ MẠNG
        // ==========================================
        /** Chạy trên server/singleplayer. Client bỏ qua gói này. */
        protected void requestUpgrade(int id) {
            if (Vars.net.client()) return;
            if (id < 0 || id >= upgrades.size) return;

            Upgrade up = upgrades.get(id);
            // Server tự kiểm tra, không tin client
            if (!canUpgrade(up) || !canAfford(up.cost)) return;

            payCost(up.cost);
            sendConfirm(id, 0);

            // Gửi lại tier chuẩn của server để tự sửa nếu client lệch
            if (Vars.net.active()) sendConfirm(-1, tier);
        }

        /** Chạy trên mọi máy khi nhận gói xác nhận từ server. */
        protected void applyConfirm(int id, int newTier) {
            // Server chỉ nhận gói do chính nó gửi (chặn client giả mạo)
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

        /** Override ở class con để thêm điều kiện (ví dụ yêu cầu tier). */
        protected boolean canUpgrade(Upgrade up) {
            return true;
        }

        // ==========================================
        // UI
        // ==========================================

        /** Bấm vào block chỉ hiện đúng một nút. Bấm nút đó mới mở bảng. */
        @Override
        public void buildConfiguration(Table table) {
            table.button(Icon.settings, Styles.cleari, () -> {
                openDialog();
                if (!Vars.headless) Vars.control.input.config.hideConfig();
            }).size(40f);
        }

        protected void openDialog() {
            if (Vars.headless) return;

            if (dialog == null) {
                dialog = new BaseDialog(block.localizedName);
                dialog.addCloseButton();
            }
            rebuildDialog();
            dialog.show();
        }

        /** Dựng lại nội dung bảng (tab, nội dung tab). */
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
            }).size(120f, 40f).checked(currentTab == 0);

            tabs.button(Core.bundle.get("upgrade.upgrade.name"), Styles.togglet, () -> {
                currentTab = 1;
                rebuild();
            }).size(120f, 40f).checked(currentTab == 1);

            cont.add(tabs).left().padBottom(5f).row();

            Table content = new Table();
            content.top().left();

            if (currentTab == 0) {
                buildMainTab(content);
            } else {
                buildUpgradeTab(content);
            }
            cont.add(content).grow().pad(5f);
        }

        /** Tab Main: ô Ảnh bên trái, ô Thông số bên phải. Không hiện tài nguyên trong core. */
        protected void buildMainTab(Table table) {
            // Ô ảnh
            Table imageBox = new Table();
            imageBox.background(Tex.pane);
            imageBox.image(getBlockImage()).size(128f).pad(16f);

            // Ô thông số (cuộn được nếu dài)
            Table stats = new Table();
            stats.top().left();
            getStats(stats);

            ScrollPane statsPane = new ScrollPane(stats);
            statsPane.setScrollingDisabled(true, false);
            statsPane.setFadeScrollBars(false);

            Table statsBox = new Table();
            statsBox.background(Tex.pane);
            statsBox.margin(8f);
            statsBox.add(statsPane).grow();

            table.add(imageBox).top().pad(5f);
            table.add(statsBox).top().pad(5f).width(320f).height(Math.min(260f, Core.scene.getHeight() * 0.5f));
        }

        /** Ảnh block: name-icon -> preview mặc định của game (fullIcon) -> error.png của game. */
        protected TextureRegion getBlockImage() {
            TextureRegion region = Core.atlas.find(block.name + "-icon");
            if (region.found()) return region;

            region = block.fullIcon;
            if (region != null && region.found()) return region;

            return Core.atlas.find("error");
        }

        /** Tab Upgrade: tài nguyên trong core + lưới nâng cấp. */
        protected void buildUpgradeTab(Table table) {
            table.add(buildCoreResources()).growX().padBottom(10f).row();

            Table grid = new Table();
            grid.top().left();
            getUpgrades(grid);

            ScrollPane pane = new ScrollPane(grid);
            pane.setScrollingDisabled(true, false);
            pane.setFadeScrollBars(false);

            table.add(pane).growX().maxHeight(Core.scene.getHeight() * 0.6f).row();
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

        /** Mặc định vẽ tất cả nâng cấp đã đăng ký. Class con có thể override nếu muốn UI riêng. */
        protected void getUpgrades(Table table) {
            for (int i = 0; i < upgrades.size; i++) {
                buildUpgradeCard(table, i);
            }
        }

        protected void addStat(Table table, String name, String value) {
            table.add(name + ": ").color(Color.lightGray).left();
            table.add(value).color(Color.white).left().padLeft(5f).row();
        }

        /**
         * Hiển thị stat của đúng viên đạn được truyền vào, y như bảng stat vanilla
         * (dùng chính StatValues.ammo của game). Không hiện icon item.
         */
        protected void addBulletStatTable(Table table, BulletType bullet) {
            // Key là chính turret: StatValues bỏ qua tiêu đề (icon + tên) khi key là Turret.
            ObjectMap<UnlockableContent, BulletType> map = new ObjectMap<>();
            map.put(block, bullet);
            StatValues.ammo(map).display(table);
        }

        protected void buildUpgradeCard(Table table, int id) {
            Upgrade up = upgrades.get(id);

            Table uTable = new Table();
            uTable.background(Styles.black6);
            uTable.margin(10f);
            uTable.setWidth(260f);

            uTable.add(up.name).color(Pal.accent).left().colspan(2).row();

            uTable.add(up.desc).color(Color.lightGray).left().colspan(2).wrap().width(240f).row();

            Table costTable = new Table();
            costTable.left();
            for (ItemStack stack : up.cost) {
                costTable.image(stack.item.uiIcon).size(24f).padRight(5f);
                costTable.add(String.valueOf(stack.amount)).color(Color.white).padRight(15f);
            }
            uTable.add(costTable).left().padTop(5f);

            // Nút mua: chỉ GỬI YÊU CẦU lên server, không trừ tài nguyên / chạy effect ở client
            uTable.button(Icon.up, Styles.clearNonei, () -> {
                if (canBuy(id)) configure(id);
            }).size(40f)
              .disabled(b -> !canBuy(id));

            table.add(uTable).pad(5f);

            // Tự động xuống dòng sau mỗi 2 nhánh (2 cột)
            if (table.getCells().size % 2 == 0) {
                table.row();
            }
        }

        protected boolean canBuy(int id) {
            Upgrade up = upgrades.get(id);
            return canUpgrade(up) && canAfford(up.cost);
        }

        // ==========================================
        // LOGIC HỖ TRỢ
        // ==========================================
        protected boolean canAfford(ItemStack[] cost) {
            if (Vars.state.rules.infiniteResources) return true;
            if (team == null || team.core() == null) return false;
            for (ItemStack stack : cost) {
                if (team.core().items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        /** Chỉ được gọi trên server/singleplayer. Client thấy số item mới qua snapshot của game. */
        protected void payCost(ItemStack[] cost) {
            if (Vars.state.rules.infiniteResources) return;
            if (team == null || team.core() == null) return;
            for (ItemStack stack : cost) {
                team.core().items.remove(stack.item, stack.amount);
            }
        }

        /** Dựng lại bảng nếu đang mở (đăng ký sang frame sau để không xóa UI giữa lúc xử lý click). */
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