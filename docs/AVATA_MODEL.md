# DJI Avata 360 — Minecraft 1.20.1 Fabric / GeckoLib 实体模型

## 文件说明

| 文件 | 用途 |
|---|---|
| `avata360_v3.bbmodel` | Blockbench 工程（GeckoLib Animated Model 格式，1733 个方块，贴图已内嵌，含 12 段动画；v3：四叶桨、可翻开的云台保护罩、顶部插拔电池） |
| `fabric_mod/src/main/resources/assets/djimod/geo/avata360.geo.json` | GeckoLib 几何模型 |
| `fabric_mod/.../animations/avata360.animation.json` | GeckoLib 动画 |
| `fabric_mod/.../textures/entity/avata360.png` | 贴图 2048×2048（每面独立 UV，含透明桨叶 cutout）+ avata360_glowmask.png（电池电量灯） |
| `fabric_mod/src/main/java/...` | 实体、GeoModel、渲染器、注册代码（Yarn 映射） |
| `fabric_mod/build.gradle` | 完整 Fabric Loom / Yarn / GeckoLib 构建配置 |
| `fabric_mod/README.md` | 开发环境、构建与游戏启动说明 |
| `preview/` | 离线渲染的多角度预览图 |
| `tools/gen_avata360_v3.py` | Avata 360 v3 模型、贴图和动画生成脚本（改参数后可重新生成） |

## 在 Blockbench 中打开

1. Blockbench → 文件 → 插件 → 安装 **GeckoLib Animation Utils**（打开 `animated_entity_model` 格式需要它）。
2. 直接打开 `avata360_v3.bbmodel`。
3. 如果在 Blockbench 里修改了模型：文件 → 导出 → **Export GeckoLib Model**，导出 geo.json 和 animation.json，
   覆盖 `assets/djimod/geo` 和 `animations` 下的同名文件即可；贴图改完记得另存为 `avata360.png`。

## 模型结构

坐标单位为 Blockbench 像素，机头朝北（-Z），整机约宽 30 × 长 25 × 高 6.3 像素（按 1 像素 ≈ 8 mm 换算真机 246×199×55 mm）。共 1733 个方块。

```
root                 ← 代码里按速度做前倾/侧倾
└─ drone             ← 动画：悬停上下浮动、微摆
   ├─ body           机身、电池仓(后部按钮+4格电量灯)、顶部 dji 标志与绿色状态灯、前肩激光雷达/视觉传感器、底部散热格栅/铭牌/红外传感器
   ├─ camera         360 相机模组（前后双鱼眼镜头），轴心在模组中心，可绕 X 轴翻转
   ├─ guards         四个涵道保护圈（24 段 × 3 层：内壁、顶部卷边、外侧下唇）+ 起落脚
   ├─ frame          机臂与涵道支撑辐条
   └─ motor_fl/fr/rl/rr     电机（底座三孔、铜色线圈、带橙色转向标记的钟罩）
      └─ prop_fl/fr/rl/rr   4 叶弯刀桨（单面贴图透明裁切 + 15° 桨距），对角同向旋转
```

## 动画

| 名称 | 说明 |
|---|---|
| `animation.avata360.idle` | 静止悬停，无位移 |
| `animation.avata360.fly` | 四桨 1440°/秒（对角反向）+ 上下浮动和轻微侧倾 |
| `animation.avata360.mode_360` | 相机旋转 90°，镜头朝上/下（360 模式），播放完停在最后一帧 |
| `animation.avata360.mode_single` | 回到单镜头朝前 |
| `animation.avata360.gimbal_open/close` | 云台保护罩 -100° 翻开 / 归位 |
| `animation.avata360.battery_insert/remove` | 顶部电池插入 / 取出，含卡扣和电源键细节 |
| `animation.avata360.gimbal_on/off`、`battery_installed/empty` | 静态状态姿态 |

## 接入模组

1. 直接在 IDE 打开 `fabric_mod` 目录，作为 Gradle 工程导入，Project SDK 选择 JDK 17。
2. 在 `fabric_mod` 目录执行 `./dev.ps1 build` 构建，执行 `./dev.ps1 runClient` 启动开发客户端。
3. 工程已接入 Yarn 1.20.1+build.10、Fabric API 和 GeckoLib 4.4.9，并配置 main/client 两个入口；详见 `fabric_mod/README.md`。
4. 进游戏后在创造模式「刷怪蛋」栏能找到「大疆 Avata 360 刷怪蛋」。空手右键无人机可切换 360/单镜头模式。

渲染缩放在 `AvataDroneRenderer` 里，当前是 `withScale(0.5f)`（约 0.94 格宽），改成 `0.25f` 更接近真机尺寸。
如果飞行时前倾方向反了，把 `AvataDroneModel#setCustomAnimations` 里的正负号对调。

## 注意

- 构建依赖版本固定在 `fabric_mod/gradle.properties`；更新 GeckoLib 时需要重新编译并在游戏内验证模型与动画。
- 桨叶用了透明 cutout，GeckoLib 默认的 `entityCutoutNoCull` 渲染类型可以直接正确显示，不要改成 solid。

