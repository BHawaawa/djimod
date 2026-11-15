# avata360 — 模型资源包 v2（供 AI / 开发者调用）

DJI Avata 360 无人机实体，Minecraft 1.20.1，GeckoLib 4.x。机器可读信息见 `manifest.json`（文件 MD5、骨骼、动画、电池仓尺寸）。

## v2 改动
- 桨叶改为**四叶弯刀桨**（更宽、后掠、圆头），每个电机 4 片。
- 外形更圆滑：机身脊梁、前肩、传感器罩、相机模组都是圆角截面；桨圈换成 36 段圆角截面圆环，前后桨圈之间有腰部连接块和三角支撑。比例按俯视实拍图调整（机身更宽、桨圈中心 ±9 / ±6.1）。
- 机身尾部是**中空电池仓**（仓口在机尾 +Z，仓底有 8 针金色触点）。
- 合并了 `model/battery/avata360` 的电池模型，作为 `battery` 骨骼（缩放 0.5，绕 Y 转 180°：触点端先进仓，电池帽带电源键和 4 颗电量灯，留在仓外作为机尾）。
- **默认不插电池**：Blockbench 里 `battery` 骨骼默认隐藏；参考代码在没有电池时 `setHidden(true)`；动画 `battery_empty` 也会把它缩放到 0。
- 新增插入 / 取出动画、电池物品贴图、电量灯发光遮罩。
- 旧版备份在 `DJI/backup/avata360_v1/`。
- **插入后一体化**：电池帽截面与机身脊梁完全相同（同一个圆角多边形，按 2:1 缩放），颜色、噪点也与机身外壳一致；卡扣握把是齐平的同色面板，电源键和卡槽只有细分模线。插入后电池帽直接延续机身成为机尾，接缝只剩一条细线，不再像要掉出来。

## 目录
```
avata360.bbmodel                      Blockbench 工程（animated_entity_model，贴图内嵌）
geo/avata360.geo.json                 几何，identifier = geometry.avata360，UV 2048x2048
animations/avata360.animation.json    动画
textures/avata360.png                 贴图 2048x2048（桨叶用 alpha 裁切）
textures/avata360_glowmask.png        发光遮罩（只有电池尾部 4 颗电量灯）
textures/item/avata360_battery.png    电池物品图标 64x64
models/item/avata360_battery.json     电池物品模型（item/generated）
preview/                              多角度预览；battery_bay_empty / battery_inserted 是电池仓特写
reference_code/                       Fabric 1.20.1 (Yarn) 参考实现：实体 / GeoModel / 渲染器 / 注册（含电池插拔）
../gif/avata360/batteryinavata360/    插入电池动画 GIF 和插入后的效果图
```

## 放入模组
```
geo/avata360.geo.json                -> assets/<modid>/geo/avata360.geo.json
animations/avata360.animation.json   -> assets/<modid>/animations/avata360.animation.json
textures/avata360.png                -> assets/<modid>/textures/entity/avata360.png
textures/avata360_glowmask.png       -> assets/<modid>/textures/entity/avata360_glowmask.png
textures/item/avata360_battery.png   -> assets/<modid>/textures/item/avata360_battery.png
models/item/avata360_battery.json    -> assets/<modid>/models/item/avata360_battery.json
```

## 关键约定
- 机头朝 -Z；原点 = 底部中心；尺寸约 30 x 6 x 25 像素（X/Y/Z），1 像素约 8 mm。
- 建议渲染缩放 0.5（约 0.94 格宽，碰撞箱 0.95 x 0.3）；接近真机尺寸用 0.25。
- 渲染类型要带 alpha 裁切（GeckoLib 默认的 `entityCutoutNoCull` 就可以）。
- `root` 骨骼不被动画使用，留给代码做姿态；`drone` 骨骼由 fly 动画控制浮动。
- 骨骼：root > drone > {body, guards, frame, camera, motor_xx > prop_xx, battery > {bat_body, bat_cap > bat_button, bat_latch_r, bat_latch_l}}。
  manifest 里的 pivot 是 Blockbench 坐标（geo.json 中 X 取反）。
- 电池仓（Blockbench 坐标）：X ±2.99，Y 0.96–4.34，Z −2.05 到 9.2，圆角 0.59。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.avata360.idle | loop | 静止 |
| animation.avata360.fly | loop | 四叶桨旋转 + 悬停浮动 |
| animation.avata360.mode_360 | hold | 相机转 90°，进入 360 模式 |
| animation.avata360.mode_single | hold | 相机回正 |
| animation.avata360.battery_insert | hold，2.0 s | 电池从机尾后上方飞入并对准仓口（0–0.7 s），沿仓推入（0.7–1.3 s），两侧卡扣先按住后弹回“咔哒”（1.36 s），最后按一下电源键（1.6–1.8 s） |
| animation.avata360.battery_remove | hold，1.5 s | 按住卡扣，把电池拉出机尾并抬起，最后缩放到 0 |
| animation.avata360.battery_installed | hold | 静态：电池在仓内 |
| animation.avata360.battery_empty | hold | 静态：没有电池（battery 缩放到 0） |

建议用 3 个控制器：flight（idle/fly）、camera（相机模式）、battery（状态跟随 hasBattery，插入/取出用 `triggerableAnim` 触发）。详见 `reference_code/entity/AvataDroneEntity.java`。

## 游戏内交互（参考代码 / fabric_mod）
- 刷出来的 Avata 360 默认**没有电池**，不能解锁电机（RC2 会提示“未安装电池”）。
- 手持 `avatadrone:avata360_battery` 右键无人机：插入电池，播放 battery_insert。
- 潜行 + 空手右键：取出电池（电机解锁时不允许），播放 battery_remove，电池回到背包。
- 状态保存在 NBT `HasBattery` 中。客户端在取出动画播完（32 tick）后才隐藏 battery 骨骼。

## 重新生成
```
python3 tools/gen_avata.py <输出目录> [model/battery/avata360]      # 生成 bbmodel/geo/animation/png/glowmask
python3 tools/preview_avata.py <输出目录>/avata360.bbmodel <预览目录>
python3 tools/make_battery_gifs.py <bbmodel> model/gif/avata360/batteryinavata360   # 支持断点续渲（GIF_BUDGET 秒）
```
电池几何和贴图直接读取 `model/battery/avata360` 的 geo.json 和贴图，所以改了电池模型后重新运行 gen_avata.py 就能同步。

## 已知限制
- 方块拼出来的圆角近看有细小台阶；桨圈用了约 1100 个方块，整机 1721 个，同屏放几十架会有性能开销。
- 电池在机内是 0.5 倍缩放，贴图降采样一半，标签小字比单独的电池模型糊一些。
