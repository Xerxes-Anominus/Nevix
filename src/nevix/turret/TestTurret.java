package nevix.turret;

import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;

/** Turret test cho NevixTurret: có bảng stat, 6 nhánh nâng cấp (5 nhánh rỗng + 1 nhánh +tier để thử đồng bộ). */
public class TestTurret extends NevixTurret {

    public TestTurret(String name) {
        super(name);
        
        size = 2;
        health = 400;
        range = 160f;
        reload = 30f;
        maxTier = 99;
        
        ammo(
            Items.copper, new BasicBulletType(3f, 9f) {{
                lifetime = 60f;
                width = 7f;
                height = 9f;
            }},
            Items.graphite, new BasicBulletType(3.5f, 18f) {{
                lifetime = 60f;
                width = 9f;
                height = 12f;
            }}
        );

        // Nhánh 1: +1 tier, để nhìn thấy đồng bộ giữa các máy
        addUpgrade("Nhánh 1", "Test đồng bộ: +1 tier",
            ItemStack.with(Items.copper, 10),
            b -> b.setTier(b.getTier() + 1));

        // Nhánh 2-6: rỗng, chỉ để test giao diện (lưới 2 cột, cuộn, nút mua, trừ tài nguyên)
        for (int i = 2; i <= 6; i++) {
            addUpgrade("Nhánh " + i, "Nhánh rỗng để test",
                ItemStack.with(Items.copper, 10),
                b -> {});
        }
    }

    public class TestTurretBuild extends NevixTurretBuild {

        @Override
        protected void getStats(Table table) {
            addStat(table, "Tier", tier + "/" + maxTier);
            addStat(table, "Tầm bắn", (range / Vars.tilesize) + " ô");

            // Hiện stat từng loại đạn của turret, y như bảng stat vanilla
            for (ObjectMap.Entry<Item, BulletType> e : ammoTypes.entries()) {
                addBulletStatTable(table, e.key, e.value);
            }
        }

        @Override
        protected boolean canUpgrade(Upgrade up) {
            // Nhánh 1 chỉ mua được khi chưa đạt tier tối đa
            return up != upgrades.first() || tier < maxTier;
        }
    }
}