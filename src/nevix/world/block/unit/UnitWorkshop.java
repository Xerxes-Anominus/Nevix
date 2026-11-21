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
import mindustry.gen.Building;
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
 * (UnitFactory.plans + Reconstructor), rồi chia theo tỉ lệ weaponShare / abilityShare.
 */
public class UnitWorkshop extends UnitFactory{
    /** Phần giá trị của một unit nguồn được tính cho toàn bộ vũ khí của nó. */
    public float weaponShare = 0.4f;
    /** Phần giá trị của một unit nguồn được tính cho toàn bộ abilities của nó. */
    public float abilityShare = 0.2f;
    /** Hệ số cho linh kiện vượt sức chứa của mẫu (1.2 = +20%). */
    public float overflowPenalty = 1.2f;
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

    public float bodyShare(UnitType t){
        return 1f - (t.weapons.size > 0 ? weaponShare : 0f) - (t.abilities.size > 0 ? abilityShare : 0f);
    }

    public Cost bodyCost(UnitType t){
        return baseCost(t).scaled(bodyShare(t));
    }

    public Cost weaponCost(UnitType t){
        return baseCost(t).scaled(weaponShare / Math.max(1, t.weapons.size));
    }

    public Cost abilityCost(UnitType t){
        return baseCost(t).scaled(abilityShare / Math.max(1, t.abilities.size));
    }

    /* ------------------------------------------------------------------ */
    /*  Vẽ                                                                 */
    /* ------------------------------------------------------------------ */

    static void drawRegion(TextureRegion r, float x, float y, float scale, boolean flip){
        if(r == null || !r.found()) return;
        Draw.rect(r, x, y, r.width * Draw.scl * scale * (flip ? -1f : 1f), r.height * Draw.scl * scale);
    }

    /** Vẽ thân mẫu + vũ khí đã đặt. 1 pixel của game = scale đơn vị UI. */
    public void drawDesign(Design d, float cx, float cy, float scale){
        UnitType t = d.template;
        if(t == null) return;

        drawRegion(t.outlineRegion, cx, cy, scale, false);
        drawRegion(t.region, cx, cy, scale, false);
        drawRegion(t.cellRegion, cx, cy, scale, false);

        for(WeaponPart p : d.weapons){
            Weapon w = Design.weaponOf(p);
            float wx = cx + cellToPos(p.cx) * scale;
            float wy = cy + cellToPos(p.cy) * scale;
            boolean fl = w.flipSprite != p.flip;
            drawRegion(w.outlineRegion, wx, wy, scale, fl);
            drawRegion(w.region, wx, wy, scale, fl);
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

        public WeaponPart(UnitType unit, int index, int cx, int cy, boolean flip){
            super(unit, index);
            this.cx = cx;
            this.cy = cy;
            this.flip = flip;
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
            for(WeaponPart w : weapons) d.weapons.add(new WeaponPart(w.unit, w.index, w.cx, w.cy, w.flip));
            return d;
        }

        public byte[] write(){
            try{
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bo);
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
                    if(u == null || idx >= u.weapons.size || cx >= n || cy >= n || d.weapons.size >= limit) continue;
                    if(filter && !shop.canUse(u)) continue;
                    d.weapons.add(new WeaponPart(u, idx, cx, cy, flip));
                }
            }catch(Exception e){
                Log.err(e);
            }
            return d;
        }

        /** Tổng giá: linh kiện thừa so với sức chứa của mẫu bị nhân overflowPenalty (đặt trước = dựng trước). */
        public Cost cost(UnitWorkshop shop){
            Cost total = new Cost();
            if(template == null) return total;

            total.add(shop.bodyCost(template), 1f);

            int capA = template.abilities.size;
            int capW = template.weapons.size;

            for(int i = 0; i < abilities.size; i++){
                total.add(shop.abilityCost(abilities.get(i).unit), i >= capA ? shop.overflowPenalty : 1f);
            }
            for(int i = 0; i < weapons.size; i++){
                total.add(shop.weaponCost(weapons.get(i).unit), i >= capW ? shop.overflowPenalty : 1f);
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
                c.x = shop.cellToPos(p.cx);
                c.y = shop.cellToPos(p.cy);
                list.add(c);
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
        protected boolean buildTab;
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
            buildTab = false;
            rebuildDialog();
            dialog.show();
        }

        protected void rebuildDialog(){
            dialog.cont.clear();
            Table content = new Table();
            if(buildTab) buildBuildTab(content); else buildMainTab(content);
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
            configure(d.write());
        }

        /* ---------- tab chính ---------- */

        protected void buildMainTab(Table root){
            //cột trái: xanh dương (giá/thời gian) - vàng (xem trước) - xanh lá (nút sang tab build)
            root.table(left -> {
                left.top();
                left.table(Styles.black6, t -> {
                    t.margin(8f);
                    watch(t, () -> costTable(t));
                }).growX().minHeight(120f).row();

                left.table(Styles.black6, t -> t.add(new Preview(UnitWorkshop.this, () -> design)).size(160f)).growX().padTop(6f).row();

                left.button(tr("workshop.build", "Chế tạo"), Icon.hammer, () -> {
                    buildTab = true;
                    rebuildDialog();
                }).growX().height(52f).padTop(6f);
            }).width(230f).top().padRight(8f);

            //cột phải: trắng (danh sách unit sống) - tím (hủy toàn bộ)
            root.table(right -> {
                right.top();
                right.table(Styles.black6, t -> {
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

                right.button(tr("workshop.killall", "Hủy toàn bộ unit"), Icon.trash, () ->
                    ui.showConfirm(tr("workshop.killall", "Hủy toàn bộ unit"),
                        tr("workshop.killall.confirm", "Hủy toàn bộ unit do xưởng này tạo ra?"),
                        () -> configure(Integer.valueOf(-1)))
                ).growX().height(52f).padTop(6f);
            }).top();
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

                t.button(b -> {
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
                }, Styles.flatt, () -> showUnit(id)).growX().minHeight(56f).pad(2f).row();
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

        protected void showUnit(int id){
            final Unit first = Groups.unit.getByID(id);
            if(first == null || !first.isValid()) return;

            BaseDialog d = new BaseDialog(first.type.localizedName);
            d.addCloseButton();

            d.cont.table(Styles.black6, t -> {
                t.margin(10f);
                t.image(first.type.uiIcon).size(72f).padRight(12f);
                t.table(info -> {
                    info.left();
                    Bar hp = new Bar(() -> {
                        Unit u = Groups.unit.getByID(id);
                        return u == null ? "" : Strings.fixed(Math.max(u.health, 0f), 0) + " / " + Strings.fixed(u.maxHealth, 0);
                    }, () -> Pal.health, () -> {
                        Unit u = Groups.unit.getByID(id);
                        return u == null ? 0f : u.healthf();
                    });
                    info.add(hp).height(24f).width(220f).row();
                    for(int i = 0; i < 5; i++){
                        final int which = i;
                        info.label(() -> unitStat(id, which)).left().padTop(2f).row();
                    }
                });
            }).row();

            //nút tự hủy ở cuối
            d.cont.button(tr("workshop.selfdestruct", "Tự hủy"), Icon.trash, () -> {
                configure(Integer.valueOf(id));
                d.hide();
            }).size(240f, 56f).padTop(10f);

            d.update(() -> {
                Unit u = Groups.unit.getByID(id);
                if(u == null || !u.isValid()) d.hide();
            });
            d.show();
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
                    btns.button(tr("workshop.clear", "Xóa vũ khí"), Icon.trash, () -> edit(d -> d.weapons.clear()));
                    btns.button(tr("workshop.back", "Tab chính"), Icon.left, () -> {
                        buildTab = false;
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

                right.table(tabs -> {
                    String[] names = {
                        tr("workshop.template", "Mẫu"),
                        tr("workshop.abilities", "Ability"),
                        tr("workshop.weapons", "Vũ khí")
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
                + (over ? "  (+" + Math.round((overflowPenalty - 1f) * 100f) + "%)" : "");
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

            //bấm vào vũ khí đã đặt = gỡ
            for(int i = 0; i < design.weapons.size; i++){
                WeaponPart p = design.weapons.get(i);
                if(p.cx == cx && p.cy == cy){
                    final int index = i;
                    edit(d -> d.weapons.remove(index));
                    return;
                }
            }

            if(weaponBrush == null) return;
            final PartRef brush = weaponBrush;
            final boolean flip = cx < n / 2; //nửa trái: lật sprite

            edit(d -> {
                d.weapons.add(new WeaponPart(brush.unit, brush.index, cx, cy, flip));
                int mx = n - 1 - cx;
                if(mirrorPlace && mx != cx){
                    d.weapons.add(new WeaponPart(brush.unit, brush.index, mx, cy, !flip));
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

                Draw.color(Pal.remove);
                Lines.stroke(2f);
                Lines.rect(x, y, w, w);
                Draw.reset();
            }
        }
    }
}