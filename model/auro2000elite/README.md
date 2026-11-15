# DJI Power AURO 2000 ELITE（大疆户外电源）— GeckoLib 实体

Minecraft 1.20.1 · Fabric（Yarn）· GeckoLib 4.x。可放置的实体，自带 **140 W 伸缩 USB‑C 线**（独立骨骼链 + 动画）。
参考图在 `reference/`（官网 5 张图），比例 1 模型像素 = 4 mm（实物 456 × 225 × 245 mm → 114 × 56.2 × 61.25 px）。

## 文件

| 文件 | 说明 |
|---|---|
| `auro2000elite.bbmodel` | Blockbench 工程（GeckoLib Animated Entity），贴图已内嵌，8 个动画 |
| `geo/auro2000elite.geo.json` | 几何（`geometry.auro2000elite`） |
| `animations/auro2000elite.animation.json` | 伸缩线动画 |
| `textures/auro2000elite.png` / `_glowmask.png` | 4096×1024 逐面 UV 贴图；屏幕、AC 键、电源灯、电量条发光 |
| `models/item/auro2000elite.json` | 物品模型（builtin/entity + display） |
| `preview/` | 渲染预览（`overview.png` 总览，`closeup_*` 局部，`cable_*` 线缆状态） |
| `../gif/auro2000elite/cable/` | 取出 / 插入 / 拔出 / 收回 动画 GIF 与静帧 |
| `reference_code/` | 已接入 `fabric_mod` 的 Java 代码副本 |
| `manifest.json` / `cable_info.json` | 骨骼、动画、尺寸、线缆参数 |

## 外观（按参考图）

正面上部深色面板：左边托盘 3 个国标五孔插座 + 下方绿色 **AC** 键；中间面板是 **伸缩线收纳槽**（插头平躺在槽内，“MAX 140W”字样、金属 Type‑C 头朝右），下面 SDC 方形盖、2 个橙色 USB‑A（MAX 12W）+ 2 个 USB‑C（MAX 65W）；右边 LCD（100%、电量条、INPUT/OUTPUT W）+ 两个 SDC 防尘盖。四角螺丝、下部浅灰外壳印 “AURO 2000 ELITE”。顶盖右前方电源键（绿灯）。两端为上方外伸的提手 + 内凹手扣（竖向散热格栅、两个小挂钩），侧面上前方螺纹孔、下部两个扩展安装孔、腰线。背面（官网无图）补了 AC IN、扩展电池口、SDC IN、散热槽与铭牌。

## 伸缩线（骨骼链）

`root → plug_yaw → plug → cab_01 → … → cab_48`，每节 3 px 的扁平线（2.4 × 0.55 px）。

* 静止姿态（无动画）= 收纳状态：插头在槽里，线从槽左端的缝进入机身，内部绕在卷线盘上（被外壳挡住）。
* 线段方块按“竖直直链”建模，每个骨骼带**静止旋转**把它折成卷线盘形状；因此动画值都是相邻两节之间的小弯折，不会万向节锁死。GeckoLib 的规则是 *最终旋转 = 骨骼静止旋转 + 动画值*（已核对 4.4.9 源码），Blockbench 预览与游戏一致。
* 动画由 `tools/auro_cable.py` 求解：插头位姿 → 贝塞尔曲线 + 重力下垂，贴地、不穿过面板 → 从缝进入机身 → 内部卷线盘；按精确 3 px 弦长取关节，扁线的扭转分配到外露段，使它竖直穿过线缝。每秒 40 个关键帧；正向运动学校验：关键帧误差 < 0.004 px，帧间线性插值误差 < 0.55 px。

| 动画 | 时长 | 用途 |
|---|---|---|
| `cable_stowed` / `cable_out` / `cable_plugged` | 静态 | 三种状态 |
| `cable_extract` | 1.6 s | 从槽里拔出、拉到前方地面 |
| `cable_plug` | 1.1 s | 拿起插头，对准设备接口插入 |
| `cable_unplug` | 0.95 s | 从设备拔出放回地面 |
| `cable_retract` | 1.35 s | 沿地面回卷、抬起卡回槽内 |
| `cable_unplug_retract` | 2.3 s | 充电中直接 Shift+右键：拔出 + 收回 |

设备位置（模型 px）：`(-14, 0, -85.5)`，插头插入后尖端在 Z ≈ -76.6。

## 游戏内玩法（已写进 fabric_mod）

* **放置**：手持“DJI Power AURO 2000 ELITE 户外电源”右键方块，正面朝向玩家（15° 对齐）。**左键**收起回背包，保留电量（创造模式需潜行左键才给物品）。
* **Shift + 右键**：取出 / 收回 140 W 伸缩线（播放动画，动画期间不响应）。
* **线已取出时手持可充电设备右键**电源：插入充电（`cable_plug`）。支持 Avata 360 / Mini 4 Pro / TB100 电池，以及**已装电池**的无人机物品（给机内电池充电，写入 `BatteryCharge`，放飞后读取）。
* **空手右键**：充电中则拔出并还给玩家；否则在动作栏显示电量 / 输入 / 输出 / 线缆状态。
* 充电功率 = min(140 W, 设备上限)（TB100 140 W、Avata 65 W、Mini 4 Pro 45 W），90% 后涓流；电源 2016 Wh、效率 92%；`TIME_SCALE = 20` 倍速。充满会响提示音。
* **红石信号**（电源所在格或下方一格）当作市电输入 1440 W，80% 以上降功率。
* 屏幕实时显示：电量 %、10 格电量条（充电时闪烁）、INPUT / OUTPUT 瓦数、“USB-C xx%”。

Java：`entity/PowerStationEntity`、`item/PowerStationItem`、`item/DeviceCharging`、`client/PowerStationModel`、`client/PowerStationRenderer`（实体缩放 0.125，正面朝向实体 yaw，渲染插着的设备与实时 LCD）、`client/PowerStationItemRenderer`；注册在 `AvataDrone.AURO2000 / AURO2000_ITEM`，客户端 `AvataDroneClient`。

## 重新生成

```
python3 tools/gen_auro2000.py <out>          # bbmodel + geo + animation + texture + glowmask（约 1.5 分钟）
python3 tools/preview_auro.py <out>/auro2000elite.bbmodel <preview_dir>
python3 tools/make_auro_gifs.py <bbmodel> <gif_dir> extract,plug   # 也可 unplug,retract,stills
```
