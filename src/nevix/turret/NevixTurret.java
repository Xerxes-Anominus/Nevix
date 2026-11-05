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
    /** Tier tối đa, đặt lại trong constructor của class con nếu cần. */
    public int maxTier = 99;

    /** Số nhánh nâng cấp trên mỗi hàng ngang. */
    public int upgradeColumns = 3;

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

    /**
     * Mở bảng của turret này ở bất cứ đâu (menu, tutorial...), không cần đặt block.
     * tab: 0 = Main, 1 = Upgrade. Mỗi lần gọi tạo build giả mới nên tier luôn bắt đầu từ 0.
     * Trong preview mọi nâng cấp đều miễn phí và chỉ chạy cục bộ (không gửi mạng).
     */
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

        /** true nếu là build giả dùng cho preview (không nằm trong thế giới, không có tile). */
        protected boolean preview = false;

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

        /** Mở bảng ở tab Main. Dùng được ở bất cứ đâu. */
        public void openMain() {
            openDialog(0);
        }

        /** Mở bảng ở tab Upgrade. Dùng được ở bất cứ đâu. */
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
                // Bảng toàn màn hình: bỏ tiêu đề, bỏ thanh nút dưới, bỏ lề để nội dung sát mép
                dialog = new BaseDialog("");
                dialog.titleTable.remove();
                dialog.buttons.remove();
                dialog.margin(0f);
                dialog.cont.margin(0f);
                // Bỏ khoảng đệm mặc định quanh ô nội dung để đáy sát hẳn mép dưới màn hình
                if (dialog.getCell(dialog.cont) != null) dialog.getCell(dialog.cont).pad(0f);
                dialog.closeOnBack();
            }
            rebuildDialog();
            dialog.show();
        }

        /** Dựng lại nội dung bảng: thanh tab ở trên, nội dung tab chiếm toàn bộ phần còn lại. */
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

            // Thanh trên cùng: [ô trống] [tab ở giữa] [nút đóng]
            Table topBar = new Table();
            topBar.add().size(52f);
            topBar.add(tabs).expandX().center();
            topBar.button(Icon.cancel, Styles.cleari, () -> dialog.hide()).size(52f);
            cont.add(topBar).growX().pad(8f).row();

            // Nội dung chiếm hết phần còn lại, đáy sát mép dưới màn hình
            Table content = new Table();
            if (currentTab == 0) {
                buildMainTab(content);
            } else {
                buildUpgradeTab(content);
            }
            cont.add(content).grow();
        }

        /** Tab Main: mục icon chiếm trọn nửa trái (không viền), thông số ở nửa phải. */
        protected void buildMainTab(Table table) {
            float half = Core.scene.getWidth() / 2f;

            // Mục icon: không viền, rộng đúng nửa màn hình, cao hết phần còn lại.
            // Ảnh giữ tỉ lệ 1:1 và phóng to tối đa để lấp đầy mục.
            Table iconSection = new Table();
            iconSection.image(getBlockImage()).scaling(Scaling.fit).grow();

            // Ô thông số (cuộn được nếu dài)
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

        /** Ảnh block: name-icon -> preview mặc định của game (fullIcon) -> error.png của game. */
        protected TextureRegion getBlockImage() {
            TextureRegion region = Core.atlas.find(block.name + "-icon");
            if (region.found()) return region;

            region = block.fullIcon;
            if (region != null && region.found()) return region;

            return Core.atlas.find("error");
        }

        /** Tab Upgrade: tài nguyên trong core + lưới nâng cấp (mỗi hàng upgradeColumns nhánh). */
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

        /** Chiều rộng mỗi thẻ nâng cấp, tự co theo màn hình. */
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

        /** Mặc định vẽ tất cả nâng cấp đã đăng ký. Class con có thể override nếu muốn UI riêng. */
        protected void getUpgrades(Table table) {
            for (int i = 0; i < upgrades.size; i++) {
                buildUpgradeCard(table, i);
                if ((i + 1) % upgradeColumns == 0) table.row();
            }
        }

        protected void addStat(Table table, String name, String value) {
            // Mỗi stat là một hàng riêng (1 cột) để không dính cột với bảng stat đạn
            Table line = new Table();
            line.left();
            line.add(name + ": ").color(Color.lightGray).left();
            line.add(value).color(Color.white).left().padLeft(5f);
            table.add(line).growX().left().row();
        }

        /**
         * Hiển thị stat của đúng viên đạn được truyền vào, y như bảng stat vanilla
         * (dùng chính StatValues.ammo của game). Không hiện icon item.
         */
        protected void addBulletStatTable(Table table, BulletType bullet) {
            // Key là chính turret: StatValues bỏ qua tiêu đề (icon + tên) khi key là Turret.
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

            // Nút mua: chỉ GỬI YÊU CẦU lên server, không trừ tài nguyên / chạy effect ở client
            uTable.button(Icon.up, Styles.clearNonei, () -> {
                if (!canBuy(id)) return;
                if (preview) previewUpgrade(id);
                else configure(id);
            }).size(48f).padTop(8f)
              .disabled(b -> !canBuy(id));

            // Việc xuống hàng do getUpgrades() lo (mỗi hàng upgradeColumns nhánh)
            table.add(uTable).width(cardW).minHeight(150f).top().pad(5f);
        }

        /** Preview: không có server/tile, nên chạy effect trực tiếp trên build giả. */
        protected void previewUpgrade(int id) {
            upgrades.get(id).effect.get(this);
            rebuild();
        }

        protected boolean canBuy(int id) {
            Upgrade up = upgrades.get(id);
            return canUpgrade(up) && canAfford(up.cost);
        }

        // ==========================================
        // LOGIC HỖ TRỢ
        // ==========================================
        /** Nâng cấp miễn phí: preview, đang ở menu, editor hoặc sandbox (vô hạn tài nguyên). */
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

        /** Chỉ được gọi trên server/singleplayer. Client thấy số item mới qua snapshot của game. */
        protected void payCost(ItemStack[] cost) {
            if (isFree()) return;
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