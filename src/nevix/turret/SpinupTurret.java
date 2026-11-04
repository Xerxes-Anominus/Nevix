package nevix.turret;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.event.Touchable;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.entities.Mover;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Icon;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.ItemTurret;

import static mindustry.Vars.*;

public class SpinupTurret extends ItemTurret {
    public float rampShots = 10f;

    public static final float extendedLifetime = 30f * 8 * 4f / 3f / 5;

    public Seq<TierUpgrade> tierUpgrades = new Seq<>();

    public static class TierUpgrade {
        public int fromTier, toTier;
        public String name, description;
        public ItemStack[] cost;

        public TierUpgrade(int fromTier, int toTier, String name, String description, ItemStack[] cost) {
            this.fromTier = fromTier;
            this.toTier = toTier;
            this.name = name;
            this.description = description;
            this.cost = cost;
        }
    }

    public SpinupTurret(String name) {
        super(name);
        health = 850;
        size = 2;
        range = 30f * 8;
        reload = 0.5f * 60;
        inaccuracy = 5f;
        shoot.shots = 5;
        shootSound = Sounds.shootDisperse;
        targetGround = false;
        configurable = true;
        ammoTypes.put(Items.lead, spinup_lead_bullet);
        ammoTypes.put(Items.metaglass, spinup_metaglass_bullet);
        
        requirements = ItemStack.with(
        Items.copper, 50,
        Items.lead, 30
        );

        config(Integer.class, (SpinupBuild b, Integer i) -> b.upgrade(i));

        tierUpgrades.add(new TierUpgrade(0, 1, "Spinup II",
            "Tầm bắn +33%.\nTốc bắn tối đa +200%.",
            ItemStack.with(Items.copper, 250, Items.lead, 100)));
        tierUpgrades.add(new TierUpgrade(0, 2, "SpinupM",
            "Tầm bắn +33%.\nTốc bắn tối đa +100%.\nFrag gây thêm sát thương lan bằng 300% sát thương frag, phạm vi 3 tile.",
            ItemStack.with(Items.graphite, 100, Items.copper, 400, Items.lead, 350)));
    }

    public static BasicBulletType spinup_metaglass_bullet_frag = new BasicBulletType() {{
        damage = 45;
        speed = 2.5f;
        lifetime = 6;
        collidesGround = false;
        width = 2f;
        height = 2f;
        trailWidth = 0.4f;
        trailLength = 4;
        frontColor = Color.white;
        backColor = Items.metaglass.color;
        trailColor = Items.metaglass.color;
    }};
    
    public static BasicBulletType spinupm_metaglass_bullet_frag = new BasicBulletType() {{
        damage = 45;
        speed = 2.5f;
        lifetime = 6;
        collidesGround = false;
        width = 2f;
        height = 2f;
        trailWidth = 0.4f;
        trailLength = 4;
        frontColor = Color.white;
        backColor = Items.metaglass.color;
        trailColor = Items.metaglass.color;
        splashDamage = damage * 3f;
        splashDamageRadius = 3f * 8f;
    }};

    public static BasicBulletType spinup_lead_bullet = new BasicBulletType() {{
        damage = 24;
        speed = 5;
        lifetime = 30f * 8 / 5;
        ammoMultiplier = 3;
        collidesGround = false;
        width = 5f;
        height = 5f;
        trailWidth = 1f;
        trailLength = 10;
        frontColor = Color.white;
        backColor = Items.lead.color;
        trailColor = Items.lead.color;
    }};

    public static BasicBulletType spinup_metaglass_bullet = new BasicBulletType() {{
        damage = 18;
        speed = 5;
        lifetime = 30f * 8 / 5;
        ammoMultiplier = 3;
        collidesGround = false;
        width = 5f;
        height = 5f;
        trailWidth = 1f;
        trailLength = 10;
        frontColor = Color.white;
        backColor = Items.metaglass.color;
        trailColor = Items.metaglass.color;
        fragBullets = 3;
        fragBullet = spinup_metaglass_bullet_frag;
    }};

    public static BasicBulletType spinupii_lead_bullet = new BasicBulletType() {{
        damage = 24;
        speed = 5;
        lifetime = extendedLifetime;
        ammoMultiplier = 3;
        collidesGround = false;
        width = 5f;
        height = 5f;
        trailWidth = 1f;
        trailLength = 10;
        frontColor = Color.white;
        backColor = Items.lead.color;
        trailColor = Items.lead.color;
    }};

    public static BasicBulletType spinupii_metaglass_bullet = new BasicBulletType() {{
        damage = 18;
        speed = 5;
        lifetime = extendedLifetime;
        ammoMultiplier = 3;
        collidesGround = false;
        width = 5f;
        height = 5f;
        trailWidth = 1f;
        trailLength = 10;
        frontColor = Color.white;
        backColor = Items.metaglass.color;
        trailColor = Items.metaglass.color;
        fragBullets = 3;
        fragBullet = spinup_metaglass_bullet_frag;
    }};

    public static BasicBulletType spinupm_lead_bullet = new BasicBulletType() {{
        damage = 24;
        speed = 5;
        lifetime = extendedLifetime;
        ammoMultiplier = 3;
        collidesGround = false;
        width = 5f;
        height = 5f;
        trailWidth = 1f;
        trailLength = 10;
        frontColor = Color.white;
        backColor = Items.lead.color;
        trailColor = Items.lead.color;
    }};

    public static BasicBulletType spinupm_metaglass_bullet = new BasicBulletType() {{
        damage = 18;
        speed = 5;
        lifetime = extendedLifetime;
        ammoMultiplier = 3;
        collidesGround = false;
        width = 5f;
        height = 5f;
        trailWidth = 1f;
        trailLength = 10;
        frontColor = Color.white;
        backColor = Items.metaglass.color;
        trailColor = Items.metaglass.color;
        fragBullets = 3;
        fragBullet = spinupm_metaglass_bullet_frag;
    }};

    public class SpinupBuild extends ItemTurretBuild {
        public int tier;
        public int shootCount;
        public float noneShootTimer;

        int selected = 0;
        int shownTier = -1;

        public float maxRateBonus() {
            return tier == 1 ? 2f : 1f;
        }

        public float rateMultiplier() {
            return 1f + maxRateBonus() * Mathf.clamp(shootCount / rampShots);
        }

        @Override
        public float range() {
            if(tier == 0) return range;
            return range * 4f / 3f;
        }

        @Override
        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, Mover mover) {
            if(tier == 1) {
                if(peekAmmo() == spinup_lead_bullet) {
                    type = spinupii_lead_bullet;
                }else if(peekAmmo() == spinup_metaglass_bullet) {
                    type = spinupii_metaglass_bullet;
                }
            }else if(tier == 2) {
                if(peekAmmo() == spinup_lead_bullet) {
                    type = spinupm_lead_bullet;
                }else if(peekAmmo() == spinup_metaglass_bullet) {
                    type = spinupm_metaglass_bullet;
                }
            }
            super.bullet(type, xOffset, yOffset, angleOffset, mover);
        }

        @Override
        protected void updateShooting(){
            float currentReload = reload / rateMultiplier();

            if(reloadCounter >= currentReload && !charging() && shootWarmup >= minWarmup){
                BulletType type = peekAmmo();

                shoot(type);
                if(shootCount < rampShots) shootCount++;

                reloadCounter %= currentReload;
            }
        }

        public void resetShootCount() {
            if(isShooting || shootCount == 0) {
                noneShootTimer = 0f;
                return;
            }

            noneShootTimer += Time.delta;
            if(noneShootTimer < 180f) return;
            noneShootTimer = 0f;
            shootCount = 0;
        }

        @Override
        public void updateTile() {
            super.updateTile();
            resetShootCount();
        }

        @Override
        public String getDisplayName() {
            if(tier == 1) {
                return "Spinup II";
            }else if(tier == 2) {
                return "SpinupM";
            }
            return "Spinup";
        }

        @Override
        public void buildConfiguration(Table table) {
            table.button(Icon.settings, Styles.cleari, () -> {
                openDialog();
                control.input.config.hideConfig();
            }).size(40f);
        }

        void openDialog() {
            BaseDialog dialog = new BaseDialog(getDisplayName());
            dialog.addCloseButton();

            dialog.cont.table(left -> {
                left.image(block.uiIcon).size(128f).padBottom(16f).row();
                left.button("Thông số", Icon.info, this::showStats).size(200f, 50f);
            }).grow().uniformX();

            dialog.cont.table(right -> {
                buildUpgrades(right);

                right.update(() -> {
                    if(shownTier != tier) buildUpgrades(right);
                });
            }).grow().uniformX();

            dialog.show();
        }

        void buildUpgrades(Table root) {
            shownTier = tier;
            root.clear();

            root.add("Nâng cấp").color(Pal.accent).row();
            root.image().color(Pal.accent).height(3f).growX().pad(4f).row();

            if(tierUpgrades.size == 0) {
                root.add("Chưa có nâng cấp").row();
                return;
            }
            selected = Math.max(0, Math.min(selected, tierUpgrades.size - 1));

            root.pane(grid -> {
                grid.top();
                for(int i = 0; i < tierUpgrades.size; i++) {
                    final int idx = i;
                    TierUpgrade u = tierUpgrades.get(i);

                    Table cell = new Table(Styles.black6);
                    cell.margin(8f);
                    cell.top().left();

                    cell.add("[accent]" + u.name).left().row();
                    cell.add(u.description).wrap().width(190f).left().padTop(4f).row();

                    if(tier == u.toTier) {
                        cell.add("[green]Đang dùng").left().padTop(4f);
                    }else if(tier == u.fromTier) {
                        cell.table(c -> {
                            for(ItemStack s : u.cost) {
                                c.image(s.item.uiIcon).size(iconSmall).padRight(2f);
                                c.add(s.amount + "").padRight(8f);
                            }
                        }).left().padTop(4f);
                    }else{
                        cell.add("[lightgray]Không khả dụng").left().padTop(4f);
                    }

                    cell.touchable = Touchable.enabled;
                    cell.clicked(() -> {
                        selected = idx;
                        buildUpgrades(root);
                    });

                    grid.add(cell).size(220f, 190f).pad(4f);
                    if((i + 1) % 2 == 0) grid.row(); // 2 ô mỗi hàng
                }
            }).grow().row();

            root.button("Nâng cấp", Icon.up, () -> configure(selected))
                .growX().height(50f).pad(4f)
                .disabled(b -> !canUpgrade(selected));
        }

        void showStats() {
            BaseDialog d = new BaseDialog("Thông số");
            d.addCloseButton();
            d.cont.pane(t -> {
                t.top().left();
                t.defaults().left().pad(3f);
                t.add("[accent]" + getDisplayName()).row();
                t.add("Máu: " + (int)health + " / " + (int)maxHealth()).row();
                t.add("Tầm bắn: " + Strings.fixed(range() / 8f, 1) + " tile").row();
                t.add("Số đạn mỗi lượt: " + shoot.shots).row();
                t.add("Tốc bắn tối đa: +" + (int)(maxRateBonus() * 100f) + "%").row();
                t.label(() -> "Tốc bắn hiện tại: x" + Strings.fixed(rateMultiplier(), 2)).row();
                if(tier == 2) {
                    t.add("Frag: sát thương lan " + (int)spinupm_metaglass_bullet_frag.splashDamage
                        + ", phạm vi " + (int)(spinupm_metaglass_bullet_frag.splashDamageRadius / 8f) + " tile").row();
                }
            }).grow();
            d.show();
        }

        boolean canUpgrade(int i) {
            if(i < 0 || i >= tierUpgrades.size) return false;
            TierUpgrade u = tierUpgrades.get(i);
            if(tier != u.fromTier) return false;
            return state.rules.infiniteResources
                || (core() != null && core().items.has(u.cost));
        }

        void upgrade(int i) {
            if(!canUpgrade(i)) return;
            TierUpgrade u = tierUpgrades.get(i);
            if(!state.rules.infiniteResources) core().items.remove(u.cost);
            tier = u.toTier;
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(tier);
            write.i(shootCount);
            write.f(noneShootTimer);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            tier = read.i();
            shootCount = read.i();
            noneShootTimer = read.f();
        }
    }
}
