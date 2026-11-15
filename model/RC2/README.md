# RC2 — 大疆 RC 2 遥控器物品模型（供 AI / 开发者调用）

Minecraft 1.20.1，GeckoLib 4.x 物品（GeoItem）。机器可读信息见 `manifest.json`（含文件 MD5、骨骼、动画）。

## 目录
```
rc2.bbmodel                       Blockbench 工程（GeckoLib 物品类型，贴图与 display 已内嵌）
geo/rc2.geo.json                  几何，identifier = geometry.rc2，UV 空间 2048x1024
animations/rc2.animation.json     动画
textures/rc2.png                  贴图 2048x1024（每面独立 UV，无透明）
models/item/rc2.json              物品模型：parent builtin/entity + 手持/背包/展示框 display 姿态
preview/                          多角度预览、天线展开预览、控件特写
reference_code/                   Fabric 1.20.1 (Yarn) 参考实现：GeoItem / GeoModel / GeoItemRenderer / 注册片段
manifest.json                     机器可读描述
```

## 放入模组
```
geo/rc2.geo.json              -> assets/<modid>/geo/rc2.geo.json
animations/rc2.animation.json -> assets/<modid>/animations/rc2.animation.json
textures/rc2.png              -> assets/<modid>/textures/item/rc2.png
models/item/rc2.json          -> assets/<modid>/models/item/rc2.json     （必须，否则物品不显示/姿态错误）
```
物品注册 id 必须是 `rc2`（与 models/item/rc2.json 对应）。参考代码包名 `com.example.avatadrone`、modid `avatadrone`，与 avata360 同一工程。

## 关键约定
- 屏幕朝 -Z（north）；X 为宽度，操作者视角左侧 = +X（录像键、左摇杆、左天线），右侧 = -X（拍照键）。
- 模型已整体下移 11 像素，中心对准 GeckoLib 物品渲染枢轴；尺寸 24 x 22.2 x 约 9 像素（含摇杆）。
- 手持/背包姿态完全由 `models/item/rc2.json` 的 `display` 控制；GUI 已校验（斜 15° 正面朝向玩家），第一/第三人称为估计值，如不合适请在 Blockbench「Display」页调整后用 GeckoLib 插件重新导出 display 文件。
- 骨骼：root > {body, screen, buttons, stick_l, stick_r, antenna_l, antenna_r}。manifest 里 pivot 为 geo.json 坐标（X 与 Blockbench 显示相反）。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.rc2.idle | loop | 静止 |
| animation.rc2.sticks | 播放一次 | 双摇杆演示（2 秒） |
| animation.rc2.antenna_open | hold_on_last_frame | 两根天线绕铰链向上展开 90° |
| animation.rc2.antenna_close | hold_on_last_frame | 收起天线 |

参考代码用两个可触发控制器：`antenna`（open / close）和 `sticks`（sticks）。右键切换天线，潜行右键播放摇杆动画；服务端用 `triggerAnim`，需在构造中调用 `SingletonGeoAnimatable.registerSyncedAnimatable(this)`。

## 修改
Blockbench 安装 GeckoLib Animation Utils 插件后打开 `rc2.bbmodel`；修改后用 Export GeckoLib Model 导出 geo/animation，用 Export Display Settings 导出 `models/item/rc2.json`，并更新 `manifest.json` 中的 MD5。生成脚本在 `DJI/tools/gen_rc2.py`。
