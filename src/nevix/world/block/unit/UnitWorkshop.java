package nevix.world.block.unit;

import arc.Core;
import arc.Events;
import arc.func.Boolp;
import arc.func.Cons;
import arc.func.Prov;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.CheckBox;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Items;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.ContentType;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.abilities.Ability;
import mindustry.entities.units.WeaponMount;
import mindustry.game.EventType.UnitCreateEvent;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Planet;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.blocks.payloads.UnitPayload;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.consumers.Consume;
import mindustry.world.consumers.ConsumeItems;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static mindustry.Vars.*;

/**
 * Xưởng chế tạo unit tuỳ biến (viết cho Mindustry v7 / build 146).
 *
 * Unit đầu ra = unit gốc của "Mẫu" (máu, giáp, tốc độ, thân) nhưng vũ khí và abilities
 * được thay bằng những linh kiện người chơi chọn.
 *
 * Giá / thời gian của từng linh kiện được suy ra từ công thức thật của unit nguồn
 * (UnitFactory.plans + Reconstructor):
 *  - Mẫu = templateShare (50%) giá và thời gian của unit đó.
 *  - Mỗi vũ khí = weaponShare (20%), mỗi ability = abilityShare (20%) của unit sở hữu nó.
 *  - Linh kiện thừa thứ x so với sức chứa của mẫu: 100 + 20x %.
 *  - Mỗi chỉnh sửa khác bản gốc của một vũ khí: +5%.
 */
public class UnitWorkshop extends UnitFactory{
    /** Phần giá / thời gian của unit gốc được tính cho Mẫu. */
    public float templateShare = 0.5f;
    /** Phần giá / thời gian của unit sở hữu được tính cho MỖI vũ khí lấy từ nó. */
    public float weaponShare = 0.2f;
    /** Phần giá / thời gian của unit sở hữu được tính cho MỖI ability lấy từ nó. */
    public float abilityShare = 0.2f;
    /** Linh kiện thừa thứ x (so với sức chứa của mẫu) có giá 100 + overflowStep * x * 100 %. */
    public float overflowStep = 0.2f;
    /** Mỗi chỉnh sửa khác bản gốc của một vũ khí làm giá / thời gian của vũ khí đó tăng thêm chừng này. */
    public float modifyStep = 0.05f;
    /** Item dùng tính giá khi không tìm thấy công thức của unit (unit của mod chẳng hạn). */
    public Item fallbackItem = Items.silicon;
    /** Số linh kiện tối đa mỗi loại (tối đa 255). */
    public int maxParts = 64;

    protected final ObjectMap<UnitType, Cost> costCache = new ObjectMap<>();
    protected final ObjectMap<Planet, ObjectSet<UnlockableContent>> treeCache = new ObjectMap<>();
    protected ObjectSet<UnlockableContent> anyTree;

    public UnitWorkshop(String name){
        super(name);
        configurable = true;

        //thiết kế được đồng bộ qua config byte[] (server nhận, rồi gửi lại cho mọi người chơi)
        config(byte[].class, (UnitWorkshopBuild b, byte[] data) -> b.setDesign(Design.read(this, data, true)));
        //Integer: id unit cần hủy, -1 = hủy toàn bộ
        config(Integer.class, (UnitWorkshopBuild b, Integer id) -> b.killRequest(id));
    }

    static String tr(String key, String def){
        return Core.bundle.get(key, def);
    }

    /* ------------------------------------------------------------------ */
    /*  Vùng build & lọc linh kiện                                         */
    /* ------------------------------------------------------------------ */

    /** Cạnh vùng build (pixel của game) = size của khối * 8. */
    public int areaSize(){
        return size * tilesize;
    }

    /** Tâm ô -> toạ độ (pixel) so với tâm unit. */
    public float cellToPos(int cell){
        return cell + 0.5f - areaSize() / 2f;
    }

    public boolean canUse(UnitType t){
        return t != null && !t.isHidden() && !t.isBanned() && t.unlockedNow() && onCurrentPlanet(t);
    }

    /**
     * Unit thuộc planet hiện tại nếu nó nằm trong tech tree của planet đó.
     * Unit không nằm trong tech tree của planet nào (unit của mod) được xem là thuộc mọi planet.
     */
    public boolean onCurrentPlanet(UnlockableContent c){
        Planet p = state.rules.sector != null ? state.rules.sector.planet : state.rules.planet;
        if(p == null || p.techTree == null) return true;

        if(anyTree == null){
            anyTree = new ObjectSet<>();
            for(Planet o : content.planets()){
                if(o.techTree != null) collect(o.techTree, anyTree);
            }
        }

        ObjectSet<UnlockableContent> mine = treeCache.get(p);
        if(mine == null){
            mine = new ObjectSet<>();
            collect(p.techTree, mine);
            treeCache.put(p, mine);
        }
        return mine.contains(c) || !anyTree.contains(c);
    }

    protected void collect(TechNode node, ObjectSet<UnlockableContent> out){
        if(node.content != null) out.add(node.content);
        for(TechNode child : node.children) collect(child, out);
    }

    public Seq<UnitType> usableUnits(){
        return content.units().select(this::canUse);
    }

    /* ------------------------------------------------------------------ */
    /*  Giá & thời gian                                                    */
    /* ------------------------------------------------------------------ */

    /** Tổng giá + thời gian để làm nguyên một unit (qua factory, và các reconstructor nếu là T2+). */
    public Cost baseCost(UnitType type){
        return baseCost(type, 0);
    }

    protected Cost baseCost(UnitType type, int depth){
        Cost cached = costCache.get(type);
        if(cached != null) return cached;

        Cost c = new Cost();
        boolean found = false;

        search:
        for(Block b : content.blocks()){
            if(b instanceof UnitWorkshop) continue;

            if(b instanceof UnitFactory){
                for(UnitFactory.UnitPlan plan : ((UnitFactory)b).plans){
                    if(plan.unit == type){
                        c.add(plan.requirements, 1f);
                        c.time += plan.time;
                        found = true;
                        break search;
                    }
                }
            }else if(b instanceof Reconstructor && depth < 6){
                Reconstructor r = (Reconstructor)b;
                for(UnitType[] pair : r.upgrades){
                    if(pair.length >= 2 && pair[1] == type){
                        c.add(baseCost(pair[0], depth + 1), 1f);
                        for(Consume cons : r.consumers){
                            if(cons instanceof ConsumeItems) c.add(((ConsumeItems)cons).items, 1f);
                        }
                        c.time += r.constructTime;
                        found = true;
                        break search;
                    }
                }
            }
        }

        if(!found){
            float hp = Math.max(type.health, 10f);
            c.items[fallbackItem.id] += Math.max(2f, hp / 20f);
            c.time += Math.max(60f, hp * 0.5f);
        }

        costCache.put(type, c);
        return c;
    }

    public Cost bodyCost(UnitType t){
        return baseCost(t).scaled(templateShare);
    }

    public Cost weaponCost(UnitType t){
        return baseCost(t).scaled(weaponShare);
    }

    public Cost abilityCost(UnitType t){
        return baseCost(t).scaled(abilityShare);
    }

    /* ------------------------------------------------------------------ */
    /*  Thuộc tính chỉnh sửa của từng vũ khí                               */
    /* ------------------------------------------------------------------ */

    public static final int PROP_ROTATE = 0, PROP_ROTATE_SPEED = 1, PROP_ROTATION_LIMIT = 2, PROP_BASE_ROTATION = 3,
        PROP_ALTERNATE = 4, PROP_RELOAD = 5, PROP_INACCURACY = 6, PROP_SHOOT_CONE = 7;

    public static class Prop{
        public final String key, def;
        public final boolean bool, mirror;
        public final float min, max, step;

        public Prop(String key, String def, boolean bool, boolean mirror, float min, float max, float step){
            this.key = key;
            this.def = def;
            this.bool = bool;
            this.mirror = mirror;
            this.min = min;
            this.max = max;
            this.step = step;
        }
    }

    /** mirror = true: khi sửa cả cặp đối xứng thì bản bên kia nhận giá trị đảo dấu (góc). */
    public static final Prop[] props = {
        new Prop("workshop.p.rotate", "Quay theo mục tiêu", true, false, 0, 1, 1),
        new Prop("workshop.p.rotatespeed", "Tốc độ quay", false, false, 1, 60, 1),
        new Prop("workshop.p.rotationlimit", "Giới hạn góc quay", false, false, 0, 360, 15),
        new Prop("workshop.p.baserotation", "Góc gốc", false, true, -180, 180, 5),
        new Prop("workshop.p.alternate", "Bắn luân phiên (cần cặp đối xứng)", true, false, 0, 1, 1),
        new Prop("workshop.p.reload", "Thời gian nạp", false, false, 5, 600, 5),
        new Prop("workshop.p.inaccuracy", "Độ lệch đạn", false, false, 0, 45, 1),
        new Prop("workshop.p.shootcone", "Góc cho phép bắn", false, false, 1, 180, 1)
    };

    public static float getValue(Weapon w, int prop){
        switch(prop){
            case PROP_ROTATE: return w.rotate ? 1f : 0f;
            case PROP_ROTATE_SPEED: return w.rotateSpeed;
            case PROP_ROTATION_LIMIT: return w.rotationLimit;
            case PROP_BASE_ROTATION: return w.baseRotation;
            case PROP_ALTERNATE: return w.alternate ? 1f : 0f;
            case PROP_RELOAD: return w.reload;
            case PROP_INACCURACY: return w.inaccuracy;
            default: return w.shootCone;
        }
    }

    public static void setValue(Weapon w, int prop, float v){
        switch(prop){
            case PROP_ROTATE: w.rotate = v >= 0.5f; break;
            case PROP_ROTATE_SPEED: w.rotateSpeed = v; break;
            case PROP_ROTATION_LIMIT: w.rotationLimit = v >= 360f ? 361f : v; break;
            case PROP_BASE_ROTATION: w.baseRotation = v; break;
            case PROP_ALTERNATE: w.alternate = v >= 0.5f; break;
            case PROP_RELOAD: w.reload = v; break;
            case PROP_INACCURACY: w.inaccuracy = v; break;
            default: w.shootCone = v; break;
        }
    }

    public static boolean sameValue(int prop, float a, float b){
        if(prop == PROP_ROTATION_LIMIT){
            a = Math.min(a, 360f);
            b = Math.min(b, 360f);
        }
        return Math.abs(a - b) < 0.001f;
    }

    public static String formatProp(int prop, float v){
        switch(prop){
            case PROP_ROTATE:
            case PROP_ALTERNATE: return v >= 0.5f ? "bật" : "tắt";
            case PROP_ROTATION_LIMIT: return v >= 360f ? "360°+" : Strings.fixed(v, 0) + "°";
            case PROP_BASE_ROTATION:
            case PROP_SHOOT_CONE: return Strings.fixed(v, 0) + "°";
            case PROP_INACCURACY: return Strings.fixed(v, 1) + "°";
            case PROP_RELOAD: return Strings.fixed(v / 60f, 2) + "s";
            default: return Strings.fixed(v, 0);
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Vẽ                                                                 */
    /* ------------------------------------------------------------------ */

    static void drawRegion(TextureRegion r, float x, float y, float scale, boolean flip, float rotation){
        if(r == null || !r.found()) return;
        Draw.rect(r, x, y, r.width * Draw.scl * scale * (flip ? -1f : 1f), r.height * Draw.scl * scale, rotation);
    }

    /** Vẽ thân mẫu + vũ khí đã đặt. 1 pixel của game = scale đơn vị UI. */
    public void drawDesign(Design d, float cx, float cy, float scale){
        UnitType t = d.template;
        if(t == null) return;

        drawRegion(t.outlineRegion, cx, cy, scale, false, 0f);
        drawRegion(t.region, cx, cy, scale, false, 0f);
        drawRegion(t.cellRegion, cx, cy, scale, false, 0f);

        for(WeaponPart p : d.weapons){
            Weapon w = Design.weaponOf(p);
            float wx = cx + cellToPos(p.cx) * scale;
            float wy = cy + cellToPos(p.cy) * scale;
            boolean fl = w.flipSprite != p.flip;
            float rot = p.has(PROP_BASE_ROTATION) ? p.over[PROP_BASE_ROTATION] : p.defs[PROP_BASE_ROTATION];
            drawRegion(w.outlineRegion, wx, wy, scale, fl, rot);
            drawRegion(w.region, wx, wy, scale, fl, rot);
        }
    }

    static String abilityName(Ability a){
        Class<?> c = a.getClass();
        if(c.isAnonymousClass()) c = c.getSuperclass();
        String n = c.getSimpleName().replace("Ability", "");
        if(n.isEmpty()) n = "Ability";
        return Core.bundle.get("ability." + n.toLowerCase(), n);
    }

    /* ================================================================== */
    /*  Dữ liệu                                                            */
    /* ================================================================== */

    /** Giá item (float, chưa làm tròn) + thời gian (tick). */
    public static class Cost{
        public final float[] items = new float[content.items().size];
        public float time;

        public void add(ItemStack[] stacks, float mul){
            for(ItemStack s : stacks) items[s.item.id] += s.amount * mul;
        }

        public void add(Cost o, float mul){
            for(int i = 0; i < items.length; i++) items[i] += o.items[i] * mul;
            time += o.time * mul;
        }

        public Cost scaled(float f){
            Cost c = new Cost();
            c.add(this, f);
            return c;
        }

        public ItemStack[] stacks(float mul){
            Seq<ItemStack> out = new Seq<>();
            for(int i = 0; i < items.length; i++){
                if(items[i] > 0.0001f){
                    out.add(new ItemStack(content.item(i), Math.max(1, Mathf.ceil(items[i] * mul - 0.0001f))));
                }
            }
            return out.toArray(ItemStack.class);
        }
    }

    /** Tham chiếu tới một ability / vũ khí của một unitType. */
    public static class PartRef{
        public UnitType unit;
        public int index;

        public PartRef(UnitType unit, int index){
            this.unit = unit;
            this.index = index;
        }
    }

    /** Vũ khí đã đặt: ô (cx, cy) trong vùng build, flip = lật sprite (bên trái). */
    public static class WeaponPart extends PartRef{
        public int cx, cy;
        public boolean flip;
        /** Các vũ khí đặt cùng lúc bằng "Đối xứng" có chung số này (>= 0). */
        public int pair = -1;
        /** Bit i bật = thuộc tính i bị chỉnh khác bản gốc, giá trị nằm ở over[i]. */
        public int mask;
        public final float[] over = new float[props.length];
        /** Giá trị gốc của từng thuộc tính (đã tính cả việc lật bên trái). */
        public final float[] defs = new float[props.length];
        public boolean donorAlternate;

        public WeaponPart(UnitType unit, int index, int cx, int cy, boolean flip){
            super(unit, index);
            this.cx = cx;
            this.cy = cy;
            this.flip = flip;

            Weapon b = Design.weaponOf(this).copy();
            if(flip) b.flip();
            for(int i = 0; i < props.length; i++) defs[i] = getValue(b, i);
            donorAlternate = b.alternate;
        }

        public boolean has(int prop){
            return (mask & (1 << prop)) != 0;
        }

        public void set(int prop, float v){
            mask |= 1 << prop;
            over[prop] = v;
        }

        public void clear(int prop){
            mask &= ~(1 << prop);
        }

        public WeaponPart copy(){
            WeaponPart c = new WeaponPart(unit, index, cx, cy, flip);
            c.pair = pair;
            c.mask = mask;
            System.arraycopy(over, 0, c.over, 0, over.length);
            return c;
        }
    }

    /** Một unit đã được workshop tạo ra (lưu cả thiết kế lúc tạo để áp lại sau khi load save). */
    public static class Spawned{
        public int id;
        public byte[] data;
        public Design parsed;
        public boolean seen;
        public int missing;
    }

    public static class Design{
        /** Phiên bản định dạng lưu; thiết kế lưu bằng bản cũ sẽ bị bỏ qua. */
        public static final int FORMAT = 2;

        public UnitType template;
        public final Seq<PartRef> abilities = new Seq<>();
        public final Seq<WeaponPart> weapons = new Seq<>();

        public static Ability abilityOf(PartRef p){
            return p.unit.abilities.get(p.index);
        }

        public static Weapon weaponOf(PartRef p){
            return p.unit.weapons.get(p.index);
        }

        public Design copy(){
            Design d = new Design();
            d.template = template;
            for(PartRef a : abilities) d.abilities.add(new PartRef(a.unit, a.index));
            for(WeaponPart w : weapons) d.weapons.add(w.copy());
            return d;
        }

        /** Vũ khí đối xứng với vũ khí idx (cùng pair), -1 nếu không có. */
        public int partnerIndex(int idx){
            int pr = weapons.get(idx).pair;
            if(pr < 0) return -1;
            for(int j = 0; j < weapons.size; j++){
                if(j != idx && weapons.get(j).pair == pr) return j;
            }
            return -1;
        }

        public int nextPair(){
            int m = -1;
            for(WeaponPart p : weapons) m = Math.max(m, p.pair);
            return m + 1;
        }

        public int partAt(int cx, int cy){
            for(int i = 0; i < weapons.size; i++){
                if(weapons.get(i).cx == cx && weapons.get(i).cy == cy) return i;
            }
            return -1;
        }

        public float defaultValue(int idx, int prop){
            WeaponPart p = weapons.get(idx);
            //luân phiên chỉ có nghĩa khi có cặp đối xứng
            if(prop == PROP_ALTERNATE) return p.donorAlternate && partnerIndex(idx) >= 0 ? 1f : 0f;
            return p.defs[prop];
        }

        public float value(int idx, int prop){
            WeaponPart p = weapons.get(idx);
            return p.has(prop) ? p.over[prop] : defaultValue(idx, prop);
        }

        /** Số chỉnh sửa khác bản gốc của vũ khí idx. */
        public int modCount(int idx){
            return Integer.bitCount(weapons.get(idx).mask);
        }

        /** Bỏ các chỉnh sửa mà giá trị đã trùng bản gốc (để không bị tính giá oan). */
        public void normalize(){
            for(int i = 0; i < weapons.size; i++){
                WeaponPart p = weapons.get(i);
                for(int k = 0; k < props.length; k++){
                    if(p.has(k) && sameValue(k, p.over[k], defaultValue(i, k))) p.clear(k);
                }
            }
        }

        public void setProp(int idx, int prop, float v, boolean both){
            weapons.get(idx).set(prop, v);
            if(both){
                int j = partnerIndex(idx);
                if(j >= 0) weapons.get(j).set(prop, props[prop].mirror ? -v : v);
            }
            normalize();
        }

        public void resetProp(int idx, int prop, boolean both){
            weapons.get(idx).clear(prop);
            if(both){
                int j = partnerIndex(idx);
                if(j >= 0) weapons.get(j).clear(prop);
            }
        }

        public byte[] write(){
            try{
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bo);
                out.writeByte(FORMAT);
                out.writeUTF(template == null ? "" : template.name);
                out.writeByte(abilities.size);
                for(PartRef a : abilities){
                    out.writeUTF(a.unit.name);
                    out.writeShort(a.index);
                }
                out.writeByte(weapons.size);
                for(WeaponPart w : weapons){
                    out.writeUTF(w.unit.name);
                    out.writeShort(w.index);
                    out.writeShort(w.cx);
                    out.writeShort(w.cy);
                    out.writeBoolean(w.flip);
                    out.writeShort(w.pair);
                    out.writeShort(w.mask);
                    for(int k = 0; k < props.length; k++){
                        if(w.has(k)) out.writeFloat(w.over[k]);
                    }
                }
                return bo.toByteArray();
            }catch(IOException e){
                throw new RuntimeException(e);
            }
        }

        static UnitType unitByName(String name){
            if(name == null || name.isEmpty()) return null;
            return content.getByName(ContentType.unit, name);
        }

        /** filter = true: loại linh kiện bị khoá / không thuộc planet (dùng cho dữ liệu từ mạng). */
        public static Design read(UnitWorkshop shop, byte[] data, boolean filter){
            Design d = new Design();
            if(data == null || data.length == 0) return d;

            try{
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                if(in.readUnsignedByte() != FORMAT) return d;
                UnitType tpl = unitByName(in.readUTF());
                d.template = (!filter || shop.canUse(tpl)) ? tpl : null;

                int limit = Math.min(shop.maxParts, 255);
                int n = shop.areaSize();

                int na = in.readUnsignedByte();
                for(int i = 0; i < na; i++){
                    UnitType u = unitByName(in.readUTF());
                    int idx = in.readUnsignedShort();
                    if(u == null || idx >= u.abilities.size || d.abilities.size >= limit) continue;
                    if(filter && !shop.canUse(u)) continue;
                    d.abilities.add(new PartRef(u, idx));
                }

                int nw = in.readUnsignedByte();
                for(int i = 0; i < nw; i++){
                    UnitType u = unitByName(in.readUTF());
                    int idx = in.readUnsignedShort();
                    int cx = in.readUnsignedShort();
                    int cy = in.readUnsignedShort();
                    boolean flip = in.readBoolean();
                    int pair = in.readShort();
                    int mask = in.readUnsignedShort();
                    float[] vals = new float[props.length];
                    for(int k = 0; k < props.length; k++){
                        if((mask & (1 << k)) != 0) vals[k] = in.readFloat();
                    }
                    if(u == null || idx >= u.weapons.size || cx >= n || cy >= n || d.weapons.size >= limit) continue;
                    if(filter && !shop.canUse(u)) continue;

                    WeaponPart wp = new WeaponPart(u, idx, cx, cy, flip);
                    wp.pair = pair;
                    for(int k = 0; k < props.length; k++){
                        if((mask & (1 << k)) != 0) wp.set(k, Mathf.clamp(vals[k], props[k].min, k == PROP_ROTATION_LIMIT ? 361f : props[k].max));
                    }
                    d.weapons.add(wp);
                }
                d.normalize();
            }catch(Exception e){
                Log.err(e);
            }
            return d;
        }

        /**
         * Tổng giá. Linh kiện (đặt trước = dựng trước) thứ x vượt sức chứa của mẫu có giá 100 + 20x %;
         * mỗi chỉnh sửa khác bản gốc của vũ khí cộng thêm 5% (cộng dồn với phần vượt, cùng tính trên giá gốc).
         */
        public Cost cost(UnitWorkshop shop){
            Cost total = new Cost();
            if(template == null) return total;

            total.add(shop.bodyCost(template), 1f);

            int capA = template.abilities.size;
            int capW = template.weapons.size;

            for(int i = 0; i < abilities.size; i++){
                int x = Math.max(0, i + 1 - capA);
                total.add(shop.abilityCost(abilities.get(i).unit), 1f + shop.overflowStep * x);
            }
            for(int i = 0; i < weapons.size; i++){
                int x = Math.max(0, i + 1 - capW);
                total.add(shop.weaponCost(weapons.get(i).unit), 1f + shop.overflowStep * x + shop.modifyStep * modCount(i));
            }
            return total;
        }

        /** Gắn vũ khí + abilities của thiết kế vào unit (máu, giáp, tốc độ giữ nguyên theo mẫu). */
        public void apply(UnitWorkshop shop, Unit unit){
            Seq<Weapon> list = new Seq<>();
            for(WeaponPart p : weapons){
                Weapon c = weaponOf(p).copy();
                if(p.flip) c.flip();
                c.mirror = false;
                c.otherSide = -1;
                c.alternate = false;
                for(int k = 0; k < props.length; k++){
                    if(k != PROP_ALTERNATE && p.has(k)) setValue(c, k, p.over[k]);
                }
                c.x = shop.cellToPos(p.cx);
                c.y = shop.cellToPos(p.cy);
                list.add(c);
            }

            //bắn luân phiên chỉ hoạt động khi hai vũ khí của cặp đối xứng cùng bật; không thì bắn đồng thời
            //(alternate phải tắt, nếu không vũ khí lật nửa trái sẽ không bao giờ bắn)
            for(int i = 0; i < list.size; i++){
                int j = partnerIndex(i);
                if(j >= 0 && value(i, PROP_ALTERNATE) >= 0.5f && value(j, PROP_ALTERNATE) >= 0.5f){
                    list.get(i).alternate = true;
                    list.get(i).otherSide = j;
                }
            }

            unit.mounts = new WeaponMount[list.size];
            for(int i = 0; i < list.size; i++){
                Weapon w = list.get(i);
                unit.mounts[i] = w.mountType.get(w);
            }

            unit.abilities = new Ability[abilities.size];
            for(int i = 0; i < abilities.size; i++){
                unit.abilities[i] = abilityOf(abilities.get(i)).copy();
            }
        }

        /** Unit đã mang đúng thiết kế này chưa (vị trí vũ khí + số lượng). */
        public boolean matches(UnitWorkshop shop, Unit u){
            if(u.mounts.length != weapons.size || u.abilities.length != abilities.size) return false;
            for(int i = 0; i < weapons.size; i++){
                WeaponPart p = weapons.get(i);
                Weapon w = u.mounts[i].weapon;
                if(Math.abs(w.x - shop.cellToPos(p.cx)) > 0.01f || Math.abs(w.y - shop.cellToPos(p.cy)) > 0.01f) return false;
            }
            return true;
        }
    }

    /** Xem trước ngoại hình unit đầu ra (tự co giãn cho vừa khung). */
    public static class Preview extends Element{
        protected final UnitWorkshop shop;
        protected final Prov<Design> source;

        public Preview(UnitWorkshop shop, Prov<Design> source){
            this.shop = shop;
            this.source = source;
            touchable = Touchable.disabled;
        }

        @Override
        public void draw(){
            Design d = source.get();
            if(d == null || d.template == null) return;

            float ext = Math.max(d.template.hitSize * 0.75f, 6f);
            for(WeaponPart p : d.weapons){
                ext = Math.max(ext, Math.max(Math.abs(shop.cellToPos(p.cx)), Math.abs(shop.cellToPos(p.cy))) + 5f);
            }
            float scale = Math.min(width, height) / 2f / ext;

            Draw.color(Color.white);
            shop.drawDesign(d, x + width / 2f, y + height / 2f, scale);
            Draw.reset();
        }
    }

    /* ================================================================== */
    /*  Building                                                           */
    /* ================================================================== */

    public class UnitWorkshopBuild extends UnitFactoryBuild{
        public Design design = new Design();
        public byte[] designBytes = design.write();
        public final Seq<Spawned> spawned = new Seq<>();
        public int designVersion;

        protected ItemStack[] req = ItemStack.empty;
        protected float buildTime = 1f;
        protected boolean ready;
        protected float refreshTimer = 9999f, checkTimer;

        //trạng thái UI (không lưu)
        protected BaseDialog dialog;
        protected int tab; //0: chính, 1: chế tạo, 2: unit đã tạo
        protected int selectedUnit = -1;
        protected int selectedPart = -1;
        protected boolean editPair = true;
        protected Runnable listFill;
        protected int tabCategory;
        protected PartRef weaponBrush;
        protected boolean mirrorPlace = true;

        /* ------------------------- logic ------------------------- */

        public void setDesign(Design d){
            byte[] bytes = d.write();
            boolean same = Arrays.equals(bytes, designBytes);
            design = d;
            designBytes = bytes;
            if(!same) progress = 0f;
            designVersion++;
            refreshCost();
        }

        protected void refreshCost(){
            refreshTimer = 0f;
            ready = design.template != null && canUse(design.template);
            Cost c = design.cost(UnitWorkshop.this);
            float mult = team == null ? 1f : state.rules.unitCost(team);
            req = design.template == null ? ItemStack.empty : c.stacks(mult);
            buildTime = Math.max(1f, c.time);
        }

        @Override
        public void updateTile(){
            refreshTimer += Time.delta;
            if(refreshTimer >= 90f) refreshCost();

            if(efficiency > 0f && ready && payload == null){
                float spd = edelta() * state.rules.unitBuildSpeed(team);
                time += spd * speedScl;
                progress += spd;
                speedScl = Mathf.lerpDelta(speedScl, 1f, 0.05f);
            }else{
                speedScl = Mathf.lerpDelta(speedScl, 0f, 0.05f);
            }

            moveOutPayload();

            if(ready && payload == null){
                if(progress >= buildTime){
                    progress %= 1f;

                    Unit unit = design.template.create(team);
                    design.apply(UnitWorkshop.this, unit);
                    payload = new UnitPayload(unit);
                    payVector.setZero();
                    items.remove(req);
                    if(!net.client()) trackUnit(unit);
                    Events.fire(new UnitCreateEvent(payload.unit, this));
                }
                progress = Mathf.clamp(progress, 0f, buildTime);
            }else{
                progress = 0f;
            }

            checkTimer += Time.delta;
            if(checkTimer >= 15f){
                checkTimer = 0f;
                maintainUnits();
            }
        }

        protected void trackUnit(Unit unit){
            Spawned s = new Spawned();
            s.id = unit.id;
            s.data = designBytes;
            s.parsed = design;
            s.seen = false;
            spawned.add(s);
        }

        /** Dọn unit đã chết + áp lại thiết kế cho unit bị reset (load save, đồng bộ mạng). */
        protected void maintainUnits(){
            for(int i = spawned.size - 1; i >= 0; i--){
                Spawned s = spawned.get(i);
                if(payload != null && payload.unit != null && payload.unit.id == s.id) continue;

                Unit u = Groups.unit.getByID(s.id);
                if(u != null && u.isValid()){
                    s.seen = true;
                    s.missing = 0;
                    if(s.parsed == null) s.parsed = Design.read(UnitWorkshop.this, s.data, false);
                    if(s.parsed.template == u.type && !s.parsed.matches(UnitWorkshop.this, u)){
                        s.parsed.apply(UnitWorkshop.this, u);
                    }
                }else if(!net.client()){
                    s.missing++;
                    //đã từng thấy mà giờ mất = đã chết; chưa từng thấy quá 30s = bỏ
                    if(s.seen || s.missing > 120) spawned.remove(i);
                }
            }
        }

        /** id < 0: hủy toàn bộ; ngược lại hủy đúng unit đó (chỉ unit của workshop này). */
        public void killRequest(int id){
            if(net.client()) return;

            for(Spawned s : spawned){
                if(id >= 0 && s.id != id) continue;
                Unit u = Groups.unit.getByID(s.id);
                if(u != null && u.isValid()) u.kill();
                if(id >= 0) break;
            }
        }

        @Override
        public boolean shouldConsume(){
            return ready && enabled && payload == null && items.has(req);
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return ready && items.get(item) < getMaximumAccepted(item);
        }

        @Override
        public int getMaximumAccepted(Item item){
            for(ItemStack s : req){
                if(s.item == item) return s.amount * 2;
            }
            return 0;
        }

        //(không @Override: tuỳ bản, UnitFactoryBuild có/không có hai hàm này)
        public float fraction(){
            return ready ? Mathf.clamp(progress / buildTime) : 0f;
        }

        public UnitType unit(){
            return ready ? design.template : null;
        }

        /** Để schematic / dựng lại khối giữ nguyên thiết kế. */
        @Override
        public Object config(){
            return designBytes;
        }

        @Override
        public void draw(){
            super.draw();

            if(ready && payload == null && progress > 0f){
                final UnitType t = design.template;
                final float f = progress / buildTime;
                Draw.draw(Layer.blockOver, () -> Drawf.construct(this, t, rotdeg() - 90f, f, speedScl, time));
            }
        }

        /* ------------------------- lưu / đồng bộ ------------------------- */

        static void writeBytes(Writes w, byte[] b){
            w.i(b.length);
            for(byte x : b) w.b(x);
        }

        static byte[] readBytes(Reads r){
            byte[] b = new byte[Math.max(0, r.i())];
            for(int i = 0; i < b.length; i++) b[i] = r.b();
            return b;
        }

        @Override
        public void write(Writes write){
            super.write(write);

            writeBytes(write, designBytes);

            Seq<byte[]> table = new Seq<>();
            for(Spawned s : spawned){
                if(table.indexOf(s.data, true) == -1) table.add(s.data);
            }
            write.i(table.size);
            for(byte[] b : table) writeBytes(write, b);

            write.i(spawned.size);
            for(Spawned s : spawned){
                write.i(s.id);
                write.i(table.indexOf(s.data, true));
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            byte[] d = readBytes(read);

            int tn = read.i();
            Seq<byte[]> table = new Seq<>();
            for(int i = 0; i < tn; i++) table.add(readBytes(read));

            Seq<Spawned> old = new Seq<>(spawned);
            spawned.clear();
            int sn = read.i();
            for(int i = 0; i < sn; i++){
                int id = read.i();
                int di = read.i();

                Spawned s = null;
                for(Spawned o : old){
                    if(o.id == id){
                        s = o;
                        break;
                    }
                }
                if(s == null){
                    s = new Spawned();
                    s.id = id;
                }

                byte[] data = di >= 0 && di < table.size ? table.get(di) : d;
                if(s.data == null || !Arrays.equals(s.data, data)){
                    s.data = data;
                    s.parsed = null;
                }
                spawned.add(s);
            }

            //read() cũng được gọi khi đồng bộ; chỉ đụng tới thiết kế khi nó thật sự đổi
            if(!Arrays.equals(d, designBytes)){
                setDesign(Design.read(UnitWorkshop.this, d, false));
            }
        }

        /* ------------------------- UI ------------------------- */

        @Override
        public void buildConfiguration(Table table){
            table.button(Icon.wrench, Styles.cleari, this::openDialog).size(40f);
        }

        protected void openDialog(){
            if(dialog == null){
                dialog = new BaseDialog(tr("workshop.title", "Xưởng Unit"));
                dialog.addCloseButton();
                dialog.update(() -> {
                    if(!isValid()) dialog.hide();
                });
            }
            tab = 0;
            rebuildDialog();
            dialog.show();
        }

        protected void rebuildDialog(){
            dialog.cont.clear();
            Table content = new Table();
            if(tab == 1) buildBuildTab(content);
            else if(tab == 2) buildUnitsTab(content);
            else buildMainTab(content);
            dialog.cont.add(new ScrollPane(content)).grow();
        }

        /** Dựng lại bảng t mỗi khi thiết kế đổi. */
        protected void watch(Table t, Runnable fill){
            final int[] seen = {designVersion};
            fill.run();
            t.update(() -> {
                if(seen[0] != designVersion){
                    seen[0] = designVersion;
                    t.clear();
                    fill.run();
                }
            });
        }

        protected void edit(Cons<Design> change){
            Design d = design.copy();
            change.get(d);
            d.normalize();
            configure(d.write());
        }

        /* ---------- tab chính ---------- */

        protected void buildMainTab(Table root){
            //cột trái: vàng (xem trước) - xanh lá (nút sang tab build) - nút sang tab unit
            root.table(left -> {
                left.top();
                left.table(Styles.black6, t -> t.add(new Preview(UnitWorkshop.this, () -> design)).size(200f)).growX().row();

                left.button(tr("workshop.build", "Chế tạo"), Icon.hammer, () -> {
                    tab = 1;
                    rebuildDialog();
                }).growX().height(52f).padTop(6f).row();

                left.button(tr("workshop.units", "Unit đã tạo"), Icon.units, () -> {
                    tab = 2;
                    rebuildDialog();
                }).growX().height(52f).padTop(6f);
            }).width(230f).top().padRight(8f);

            //cột phải: xanh dương (giá + thời gian build)
            root.table(Styles.black6, right -> {
                right.margin(10f);
                watch(right, () -> costTable(right));
            }).size(260f, 300f).top();
        }

        protected void costTable(Table t){
            t.left().top();

            if(design.template == null){
                t.add(tr("workshop.notemplate", "Chưa chọn mẫu.\nVào tab chế tạo để chọn.")).color(Color.lightGray).left();
                return;
            }

            t.add(tr("workshop.cost", "Giá chế tạo")).color(Pal.accent).left().row();
            for(ItemStack s : req){
                final Item item = s.item;
                final int amount = s.amount;
                t.table(r -> {
                    r.left();
                    r.image(item.uiIcon).size(iconSmall).padRight(4f);
                    r.label(() -> (items.get(item) >= amount ? "[lightgray]" : "[scarlet]") + items.get(item) + "[]/" + amount);
                }).left().row();
            }
            t.add(tr("workshop.time", "Thời gian: ") + Strings.fixed(buildTime / 60f, 1) + "s").left().padTop(4f).row();

            if(!ready){
                t.add(tr("workshop.locked", "[scarlet]Mẫu không dùng được ở đây.")).left().padTop(4f);
            }
        }

        protected int aliveSignature(){
            int h = 17;
            for(int i = 0; i < spawned.size; i++){
                int id = spawned.get(i).id;
                Unit u = Groups.unit.getByID(id);
                if(u != null && u.isValid()) h = h * 31 + id + 1;
            }
            return h;
        }

        protected void fillAlive(Table t){
            boolean any = false;
            for(Spawned s : spawned){
                final int id = s.id;
                final Unit u = Groups.unit.getByID(id);
                if(u == null || !u.isValid()) continue;
                any = true;

                Button btn = t.button(b -> {
                    b.left();
                    b.image(u.type.uiIcon).size(36f).padRight(8f);
                    b.table(info -> {
                        info.left();
                        info.add("#" + id + "  " + u.type.localizedName).left().row();
                        Bar hp = new Bar(() -> "", () -> Pal.health, () -> {
                            Unit x = Groups.unit.getByID(id);
                            return x == null ? 0f : x.healthf();
                        });
                        hp.touchable = Touchable.disabled;
                        info.add(hp).height(12f).growX();
                    }).growX();
                }, Styles.clearTogglet, () -> selectedUnit = id).growX().minHeight(56f).pad(2f).get();
                btn.update(() -> btn.setChecked(selectedUnit == id));
                t.row();
            }

            if(!any){
                t.add(tr("workshop.nounit", "Chưa có unit nào.")).color(Color.lightGray).pad(10f);
            }
        }

        protected String unitStat(int id, int which){
            Unit u = Groups.unit.getByID(id);
            if(u == null) return "";
            switch(which){
                case 0: return tr("workshop.armor", "Giáp: ") + Strings.fixed(u.armor, 1);
                case 1: return tr("workshop.speed", "Tốc độ: ") + Strings.fixed(u.speed() * 60f / tilesize, 1) + " ô/s";
                case 2: return tr("workshop.weapons", "Vũ khí") + ": " + u.mounts.length;
                case 3: return tr("workshop.abilities", "Ability") + ": " + u.abilities.length;
                default: return tr("workshop.pos", "Vị trí: ") + u.tileX() + ", " + u.tileY();
            }
        }

        /* ---------- tab unit ---------- */

        protected void buildUnitsTab(Table root){
            selectedUnit = -1;

            //trái: trắng (danh sách unit sống) - tím (hủy toàn bộ) - quay lại
            root.table(left -> {
                left.top();
                left.table(Styles.black6, t -> {
                    Table inner = new Table();
                    inner.top().left();
                    final int[] sig = {Integer.MIN_VALUE};
                    inner.update(() -> {
                        int now = aliveSignature();
                        if(now != sig[0]){
                            sig[0] = now;
                            inner.clear();
                            fillAlive(inner);
                        }
                    });
                    ScrollPane sp = new ScrollPane(inner);
                    sp.setScrollingDisabled(true, false);
                    t.add(sp).grow();
                }).size(260f, 300f).row();

                left.button(tr("workshop.killall", "Hủy toàn bộ unit"), Icon.trash, () ->
                    ui.showConfirm(tr("workshop.killall", "Hủy toàn bộ unit"),
                        tr("workshop.killall.confirm", "Hủy toàn bộ unit do xưởng này tạo ra?"),
                        () -> {
                            configure(Integer.valueOf(-1));
                            selectedUnit = -1;
                        })
                ).growX().height(52f).padTop(6f).row();

                left.button(tr("workshop.back", "Tab chính"), Icon.left, () -> {
                    tab = 0;
                    rebuildDialog();
                }).growX().height(52f).padTop(6f);
            }).top().padRight(8f);

            //phải: thông tin unit đang chọn, nút tự hủy ở cuối
            root.table(Styles.black6, right -> {
                right.margin(10f);
                final int[] sig = {Integer.MIN_VALUE};
                right.update(() -> {
                    Unit u = selectedUnit < 0 ? null : Groups.unit.getByID(selectedUnit);
                    int now = u != null && u.isValid() ? selectedUnit : -1;
                    if(now < 0) selectedUnit = -1;
                    if(now != sig[0]){
                        sig[0] = now;
                        right.clear();
                        fillStatus(right, now);
                    }
                });
            }).size(260f, 300f).top();
        }

        protected void fillStatus(Table t, final int id){
            t.top();

            Unit first = id < 0 ? null : Groups.unit.getByID(id);
            if(first == null){
                t.add(tr("workshop.pick", "Chọn một unit trong danh sách\nđể xem thông tin.")).color(Color.lightGray).center();
                return;
            }

            t.table(head -> {
                head.left();
                head.image(first.type.uiIcon).size(48f).padRight(8f);
                head.add("#" + id + "\n" + first.type.localizedName).left();
            }).growX().row();

            Bar hp = new Bar(() -> {
                Unit u = Groups.unit.getByID(id);
                return u == null ? "" : Strings.fixed(Math.max(u.health, 0f), 0) + " / " + Strings.fixed(u.maxHealth, 0);
            }, () -> Pal.health, () -> {
                Unit u = Groups.unit.getByID(id);
                return u == null ? 0f : u.healthf();
            });
            t.add(hp).height(24f).growX().padTop(8f).row();

            for(int i = 0; i < 5; i++){
                final int which = i;
                t.label(() -> unitStat(id, which)).left().padTop(2f).row();
            }

            //đẩy nút tự hủy xuống cuối
            t.add().growY().row();

            t.button(tr("workshop.selfdestruct", "Tự hủy"), Icon.trash, () -> {
                configure(Integer.valueOf(id));
                selectedUnit = -1;
            }).growX().height(52f);
        }

        /* ---------- tab build ---------- */

        protected void buildBuildTab(Table root){
            final int n = areaSize();
            float avail = Math.min(Core.graphics.getWidth(), Core.graphics.getHeight()) / Scl.scl(1f) * 0.6f;
            final float cell = Mathf.clamp(Mathf.floor(avail / n), 4f, 10f);
            final Seq<UnitType> donors = usableUnits();

            //trái: vùng build (đỏ)
            root.table(left -> {
                left.top();
                left.add(new BuildGrid(cell)).size(n * cell).row();
                left.table(t -> watch(t, () -> capacityTable(t))).growX().padTop(6f).row();
                left.check(tr("workshop.mirror", "Đối xứng khi đặt vũ khí"), mirrorPlace, v -> mirrorPlace = v).left().padTop(4f).row();
                left.table(btns -> {
                    btns.defaults().growX().height(44f).pad(2f);
                    btns.button(tr("workshop.clear", "Xóa vũ khí"), Icon.trash, () -> {
                        selectedPart = -1;
                        edit(d -> d.weapons.clear());
                    });
                    btns.button(tr("workshop.back", "Tab chính"), Icon.left, () -> {
                        tab = 0;
                        rebuildDialog();
                    });
                }).growX().padTop(4f);
            }).top().padRight(8f);

            //phải: danh sách linh kiện (trắng)
            root.table(right -> {
                right.top();

                final Table list = new Table();
                list.top().left();
                final Runnable fill = () -> fillCategory(list, donors);
                listFill = fill;

                right.table(tabs -> {
                    String[] names = {
                        tr("workshop.template", "Mẫu"),
                        tr("workshop.abilities", "Ability"),
                        tr("workshop.weapons", "Vũ khí"),
                        tr("workshop.tweak", "Sửa")
                    };
                    for(int i = 0; i < names.length; i++){
                        final int idx = i;
                        TextButton b = tabs.button(names[i], Styles.clearTogglet, () -> {
                            tabCategory = idx;
                            fill.run();
                        }).growX().height(40f).get();
                        b.update(() -> b.setChecked(tabCategory == idx));
                    }
                }).growX().row();

                right.table(Styles.black6, holder -> {
                    ScrollPane sp = new ScrollPane(list);
                    sp.setScrollingDisabled(true, false);
                    holder.add(sp).grow();
                }).size(260f, 300f).padTop(4f);

                fill.run();
            }).top();
        }

        protected void fillCategory(Table t, Seq<UnitType> donors){
            t.clear();

            if(donors.isEmpty()){
                t.add(tr("workshop.empty", "Không có mục nào dùng được.")).color(Color.lightGray).pad(10f);
                return;
            }

            if(tabCategory == 0){
                for(UnitType u : donors){
                    listEntry(t, u.uiIcon, u.localizedName, () -> design.template == u, () -> edit(d -> d.template = u));
                }
            }else if(tabCategory == 1){
                for(UnitType u : donors){
                    if(u.abilities.isEmpty()) continue;
                    donorHeader(t, u);
                    for(int i = 0; i < u.abilities.size; i++){
                        final int index = i;
                        listEntry(t, u.uiIcon, abilityName(u.abilities.get(i)), () -> false,
                            () -> edit(d -> d.abilities.add(new PartRef(u, index))));
                    }
                }
            }else if(tabCategory == 3){
                final Table body = new Table();
                watch(body, () -> fillEdit(body));
                t.add(body).growX().top();
            }else{
                for(UnitType u : donors){
                    boolean head = false;
                    for(int i = 0; i < u.weapons.size; i++){
                        Weapon w = u.weapons.get(i);
                        //bỏ bản lật của cặp vũ khí đối xứng (đã có "Đối xứng" khi đặt)
                        if(w.otherSide != -1 && w.flipSprite) continue;

                        if(!head){
                            donorHeader(t, u);
                            head = true;
                        }

                        final int index = i;
                        TextureRegion icon = w.region != null && w.region.found() ? w.region : u.uiIcon;
                        String name = w.name == null || w.name.isEmpty() ? "#" + (i + 1) : w.name;
                        listEntry(t, icon, name,
                            () -> weaponBrush != null && weaponBrush.unit == u && weaponBrush.index == index,
                            () -> weaponBrush = new PartRef(u, index));
                    }
                }
            }
        }

        /** Bảng chỉnh sửa chi tiết vũ khí đang chọn (mỗi chỉnh sửa khác bản gốc: +5% giá và thời gian). */
        protected void fillEdit(Table t){
            t.top().left();

            final int idx = selectedPart;
            if(idx < 0 || idx >= design.weapons.size){
                t.add(tr("workshop.pickweapon", "Bấm vào ô có vũ khí trong vùng\nbuild để chọn và chỉnh sửa.")).color(Color.lightGray).pad(10f).left();
                return;
            }

            final WeaponPart p = design.weapons.get(idx);
            Weapon w = Design.weaponOf(p);
            TextureRegion icon = w.region != null && w.region.found() ? w.region : p.unit.uiIcon;
            String name = w.name == null || w.name.isEmpty() ? "#" + (p.index + 1) : w.name;

            t.table(h -> {
                h.left();
                h.image(icon).size(36f).padRight(8f);
                h.add(p.unit.localizedName + "\n" + name).left();
            }).growX().padBottom(4f).row();

            final boolean hasPair = design.partnerIndex(idx) >= 0;
            if(hasPair){
                t.check(tr("workshop.editpair", "Sửa cả cặp đối xứng"), editPair, v -> editPair = v).left().row();
            }

            for(int i = 0; i < props.length; i++) propRow(t, idx, i, hasPair);

            int m = design.modCount(idx);
            t.add(tr("workshop.mods", "Điều chỉnh: ") + m + (m > 0 ? "  (+" + Math.round(modifyStep * 100f * m) + "%)" : ""))
                .color(m > 0 ? Pal.accent : Color.lightGray).left().padTop(8f).row();

            t.button(tr("workshop.delpart", "Xóa vũ khí này"), Icon.trash, () -> {
                selectedPart = -1;
                edit(d -> d.weapons.remove(idx));
            }).growX().height(44f).padTop(6f);
        }

        protected void propRow(Table t, final int idx, final int prop, boolean hasPair){
            final Prop pr = props[prop];
            final float cur = design.value(idx, prop);
            final boolean modified = design.weapons.get(idx).has(prop);
            String label = (modified ? "[accent]" : "") + tr(pr.key, pr.def);

            if(pr.bool){
                CheckBox cb = t.check(label, cur >= 0.5f, v -> edit(d -> d.setProp(idx, prop, v ? 1f : 0f, editPair))).left().padTop(2f).get();
                if(prop == PROP_ALTERNATE && !hasPair) cb.setDisabled(true);
                t.row();
                return;
            }

            t.add(label).left().padTop(6f).row();
            t.table(r -> {
                r.left();
                r.button("-", Styles.flatt, () -> stepProp(idx, prop, -1)).size(40f);
                r.add(formatProp(prop, cur)).width(86f).center();
                r.button("+", Styles.flatt, () -> stepProp(idx, prop, 1)).size(40f);
                if(modified){
                    r.button(Icon.refresh, Styles.cleari, () -> edit(d -> d.resetProp(idx, prop, editPair))).size(40f);
                }
            }).left().row();
        }

        protected void stepProp(int idx, int prop, int dir){
            Prop pr = props[prop];
            float cur = design.value(idx, prop);
            if(prop == PROP_ROTATION_LIMIT) cur = Math.min(cur, 360f);
            final float next = Mathf.clamp(cur + dir * pr.step, pr.min, pr.max);
            edit(d -> d.setProp(idx, prop, next, editPair));
        }

        protected void donorHeader(Table t, UnitType u){
            t.table(r -> {
                r.left();
                r.image(u.uiIcon).size(24f).padRight(6f);
                r.add(u.localizedName).color(Pal.accent);
            }).growX().padTop(6f).row();
        }

        protected void listEntry(Table t, TextureRegion icon, String text, Boolp selected, Runnable click){
            Button b = t.button(bb -> {
                bb.left();
                bb.image(icon).size(32f).padRight(8f);
                bb.add(text).left().width(170f).wrap();
            }, Styles.clearTogglet, click).growX().minHeight(44f).pad(1f).get();
            b.update(() -> b.setChecked(selected.get()));
            t.row();
        }

        protected String countText(String label, int n, int cap){
            boolean over = n > cap;
            return (over ? "[orange]" : "") + label + ": " + n + "/" + cap
                + (over ? "  (vượt " + (n - cap) + ")" : "");
        }

        protected void capacityTable(Table t){
            t.left();
            Design d = design;
            final int capW = d.template == null ? 0 : d.template.weapons.size;
            final int capA = d.template == null ? 0 : d.template.abilities.size;

            t.add(countText(tr("workshop.weapons", "Vũ khí"), d.weapons.size, capW)).left().row();
            t.add(countText(tr("workshop.abilities", "Ability"), d.abilities.size, capA)).left().row();

            t.table(r -> {
                r.left();
                for(ItemStack s : req){
                    r.image(s.item.uiIcon).size(iconSmall);
                    r.add(" " + s.amount + "  ").padRight(4f);
                }
                r.add(Strings.fixed(buildTime / 60f, 1) + "s").color(Color.lightGray);
            }).left().row();

            //ability đã gắn (bấm X để gỡ)
            for(int i = 0; i < d.abilities.size; i++){
                final int index = i;
                final PartRef a = d.abilities.get(i);
                t.table(r -> {
                    r.left();
                    r.image(a.unit.uiIcon).size(24f).padRight(4f);
                    r.add((index >= capA ? "[orange]" : "") + abilityName(Design.abilityOf(a))).left().growX();
                    r.button(Icon.cancel, Styles.cleari, () -> edit(c -> c.abilities.remove(index))).size(28f);
                }).growX().row();
            }
        }

        /** Tạo / gỡ vũ khí tại ô (cx, cy). */
        protected void tapCell(float lx, float ly, float cell){
            final int n = areaSize();
            final int cx = (int)(lx / cell);
            final int cy = (int)(ly / cell);
            if(cx < 0 || cy < 0 || cx >= n || cy >= n) return;

            //bấm đúng ô có vũ khí = chọn để chỉnh sửa
            int hit = design.partAt(cx, cy);
            if(hit >= 0){
                selectedPart = hit;
                tabCategory = 3;
                if(listFill != null) listFill.run();
                return;
            }

            if(weaponBrush == null) return;
            final PartRef brush = weaponBrush;
            final boolean flip = cx < n / 2; //nửa trái: lật sprite

            edit(d -> {
                int mx = n - 1 - cx;
                boolean pair = mirrorPlace && mx != cx && d.partAt(mx, cy) < 0;
                int pid = pair ? d.nextPair() : -1;

                WeaponPart a = new WeaponPart(brush.unit, brush.index, cx, cy, flip);
                a.pair = pid;
                d.weapons.add(a);

                if(pair){
                    WeaponPart b = new WeaponPart(brush.unit, brush.index, mx, cy, !flip);
                    b.pair = pid;
                    d.weapons.add(b);
                }
            });
        }

        /** Vùng build: 1 ô = 1 pixel của game, đúng bằng size của workshop. */
        protected class BuildGrid extends Element{
            protected final int n = areaSize();
            protected final float cell;

            public BuildGrid(float cell){
                this.cell = cell;
                addListener(new ClickListener(){
                    @Override
                    public void clicked(InputEvent event, float x, float y){
                        tapCell(x, y, BuildGrid.this.cell);
                    }
                });
            }

            @Override
            public void draw(){
                float w = n * cell;

                Draw.color(Pal.darkestGray);
                Fill.crect(x, y, w, w);

                Lines.stroke(1f);
                for(int i = 0; i <= n; i++){
                    Draw.color(Color.white, i % tilesize == 0 ? 0.22f : 0.05f);
                    Lines.line(x + i * cell, y, x + i * cell, y + w);
                    Lines.line(x, y + i * cell, x + w, y + i * cell);
                }

                if(design.template != null){
                    Draw.color(Color.white, 0.85f);
                    drawDesign(design, x + w / 2f, y + w / 2f, cell);
                }

                //ô của từng vũ khí (ô đang chọn sáng hơn)
                for(int i = 0; i < design.weapons.size; i++){
                    WeaponPart p = design.weapons.get(i);
                    boolean sel = i == selectedPart;
                    Draw.color(sel ? Pal.accent : Color.white, sel ? 1f : 0.5f);
                    Lines.stroke(sel ? 2f : 1f);
                    Lines.rect(x + p.cx * cell, y + p.cy * cell, cell, cell);
                }

                Draw.color(Pal.remove);
                Lines.stroke(2f);
                Lines.rect(x, y, w, w);
                Draw.reset();
            }
        }
    }
}