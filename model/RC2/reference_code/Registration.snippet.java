// ===== 注册片段：放进 ModInitializer（例如 AvataDrone.java）=====
import com.example.avatadrone.item.RC2Item;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public static final Item RC2 = Registry.register(
        Registries.ITEM,
        new Identifier(MOD_ID, "rc2"),
        new RC2Item(new Item.Settings().maxCount(1)));

// onInitialize() 中：
ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(RC2));

// 客户端无需额外注册：GeoItem.makeRenderer + createRenderer 会自动挂接 GeoItemRenderer。

// ===== 语言文件 =====
// en_us.json
//   "item.avatadrone.rc2": "DJI RC 2",
//   "item.avatadrone.rc2.antenna_open": "Antennas unfolded",
//   "item.avatadrone.rc2.antenna_close": "Antennas folded"
// zh_cn.json
//   "item.avatadrone.rc2": "大疆 RC 2 遥控器",
//   "item.avatadrone.rc2.antenna_open": "天线已展开",
//   "item.avatadrone.rc2.antenna_close": "天线已收起"
