# Redmi K90 Pro Max（Sound by Bose）— GeckoLib 物品

Minecraft 1.20.1 · Fabric（Yarn）· GeckoLib 4.4.9 · modid `djimod`。按 `reference/` 里 20 张参考图建模（白色版）。
比例 1 模型像素 = 2 mm（实机 163.3 × 77.8 × 7.9 mm → 81.65 × 38.9 × 3.95 px），几何以物品原点为中心。
屏幕朝 -Z（north），背面/摄像头朝 +Z；从正面看按键在右侧（-X）。

## 给其他 AI（6astra）的接入说明

`manifest.json` → `install_map` 列出每个文件要复制到的位置：

| 文件 | 放到 |
|---|---|
| `geo/redmi_k90.geo.json` | `assets/djimod/geo/item/` |
| `animations/redmi_k90.animation.json` | `assets/djimod/animations/item/` |
| `textures/redmi_k90.png` + `_glowmask.png` | `assets/djimod/textures/item/` |
| `textures/apps/*.png`（20 个应用图标） | `assets/djimod/textures/item/phone/apps/` |
| `models/item/redmi_k90.json` | `assets/djimod/models/item/` |
| `reference_code/item/PhoneItem.java`、`PhoneApps.java` | `com/example/avatadrone/item/` |
| `reference_code/client/PhoneItemModel.java`、`PhoneItemRenderer.java` | `com/example/avatadrone/client/` |
| `patches/redmi_k90_integration.patch` | 在 DJI 根目录执行 `patch -p1 < model/phone/redmi_k90/patches/redmi_k90_integration.patch`（已 dry-run 通过）。改好的完整文件也放在 `reference_code/modified/` |

这个补丁会改动现有代码：注册 `REDMI_K90` 物品和 `/redmi app` 命令，让 `DeviceCharging` 认识手机（29.26 Wh，最大 100 W），让 `PowerStationEntity` 的“手持设备”充电支持手机，`ChargingCableRenderer` 会把线头接到手机底部的 USB‑C。另外设置客户端时钟，并加入中英文语言条目（`lang_entries.json`）。

## 模型

* **骨骼**：`phone > body / keys / screen`。`body` 包括前玻璃、铝中框（带天线断点）、白色背板、两级圆角摄像头平台（四个镜头带银色镜圈，第四个是闪光灯+传感器）、“SOUND BY BOSE”和“REDMI”标识、底部扬声器孔、USB‑C 口、麦克风和 SIM 卡托、顶部开孔、前置挖孔摄像头。`keys` 是音量键和电源键。
* **屏幕层**（都在 `screen` 下）：`scr_home` 壁纸（从参考图 8 做透视校正后裁出）、`boot_redmi`（含 `boot_android`）、`boot_hyperos`、`shutdown_spin`、`chg_ring` / `chg_bolt`、`chg_pop`、`screen_ui`（空骨骼，Java 在这里绘制状态栏、时钟、应用图标）。
* 叠加层静止时藏在机身内 2.2 px，动画需要时再移出到玻璃前面，所以静止姿态就是干净的桌面。屏幕使用 glowmask，会全亮显示。

## 屏幕状态与动画

| 状态 | 动画 | 说明 |
|---|---|---|
| 关机 | `screen_off` | 黑屏 |
| 开机 | `boot`（4 s） | REDMI → Xiaomi HyperOS → 桌面放大淡入 |
| 桌面 | `home` | 壁纸、状态栏（时间/信号/Wi‑Fi/电量）、时钟日期、4×5 应用网格、Dock |
| 熄屏 | `screen_off` | 系统仍在运行 |
| 关机中 | `power_off`（2 s） | 转圈动画 |
| 关机充电 | `charge_off`（循环） | 绿色充电环旋转、闪电脉动、实时百分比 |
| 插线提示 | `charge_plug`（2.2 s，`fx` 控制器） | 桌面底部弹出 HyperOS 充电胶囊，状态栏电池变绿并显示闪电 |

## 玩法

* **Shift+右键**：长按电源键，开机或关机（电量为 0 时无法开机）。**右键**：亮屏或熄屏。
* **充电**：先对 AURO 2000 ELITE Shift+右键取出伸缩线，再手持手机右键电源，就会插入并开始充电（播放电源的 `cable_plug` 动画，线头接到手机 USB‑C）。再次右键会拔出。开机时显示桌面充电提示，关机时显示充电环。
* **桌面应用**：图标是 `textures/item/phone/apps/<id>.png`，名称在语言键 `app.djimod.phone.<id>`。主手拿着手机时可以用命令管理：`/redmi app add <id> [dock]`、`remove <id>`、`list`、`reset`。新增应用的方法：放一张 PNG，加一条语言键，再调用 `PhoneApps.register("id")`。代码里也可以用 `PhoneApps.install(stack, id, dock)` 安装。

## 预览

* `preview/`：正面、背面、两个侧面、底部、摄像头特写、GUI 格子效果。
* `../../gif/redmi_k90/screen/screen_states.gif`：各屏幕状态。
* `../../gif/redmi_k90/charging/cable_plug.gif`、`charging_home.png`、`charging_off.png`：充电。

## 重新生成

```
python3 tools/gen_redmi_k90.py <build>         # 用到 tools/k90_art.py（屏幕/图标美术）
python3 tools/package_k90.py <build>
python3 tools/preview_k90.py model/phone/redmi_k90 model/phone/redmi_k90/preview --ui
python3 tools/make_k90_gifs.py <k90.bbmodel> <auro.bbmodel> <out> states|plug|still
```

注意：沙箱里只有 JDK 11，Java 代码没有编译过；所用 API 已和 GeckoLib 4.4.9 源码逐一核对过。
