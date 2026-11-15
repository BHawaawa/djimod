# mini4pro — 大疆 Mini 4 Pro 无人机实体（v2.1：带电池仓，可插入电池；顶部平滑外壳）（供 AI / 开发者调用）

Minecraft 1.20.1，GeckoLib 4.x 实体。机器可读信息见 `manifest.json`，内容包括文件 MD5、骨骼、动画、电池说明和建议尺寸。旧版 v1 备份在 `DJI/backup/mini4pro_v1/`。

## 目录
```
mini4pro.bbmodel                      Blockbench 工程（GeckoLib 实体格式，贴图内嵌，含 9 段动画；电池骨骼在编辑器中默认隐藏）
geo/mini4pro.geo.json                 几何，identifier = geometry.mini4pro，UV 空间 2048x1024
animations/mini4pro.animation.json    动画（含 battery_insert / battery_remove / battery_installed / battery_empty）
textures/mini4pro.png                 贴图 2048x1024（每面独立 UV；桨叶为透明 cutout；电池贴图已合并进来）
preview/                              预览图：不带后缀的是装好电池的样子，*_no_battery 是空电池仓
                                      旧版的特写和间隙检查图移到了 preview/legacy_v1/
reference_code/                       Fabric 1.20.1 (Yarn) 参考实现：实体（含插拔电池逻辑）/ GeoModel / 渲染器 / 注册片段
manifest.json                         机器可读描述
```
插入/取出动画的 GIF 在 `DJI/model/gif/avata360/batteryinmini4pro/`。

## 放入模组
```
geo/mini4pro.geo.json              -> assets/<modid>/geo/mini4pro.geo.json
animations/mini4pro.animation.json -> assets/<modid>/animations/mini4pro.animation.json
textures/mini4pro.png              -> assets/<modid>/textures/entity/mini4pro.png
```
电池物品本身的模型在 `DJI/model/battery/mini4pro/`，物品和无人机分开渲染。无人机里的电池是同一个模型按 0.5 倍缩小后合并进来的，所以两边外观一致。

## v2 电池仓（按实拍照片）
- **开口**：电池仓在机身尾部下方，后面和底部后段都敞开，对应照片里拿掉电池后的尾部和底部。
  - 尾部能看到深色的仓内壁；
  - 仓顶上方的尾面是 USB-C 口和 microSD 卡槽；
  - 仓内前壁有 6 针连接器和黄色 CLASS 1 激光标签；
  - 两侧内壁有导轨。
- **底部**：前段保留机身底板，上面有两排散热槽；Z 4.75 之后的底部敞开。
- **按键开窗**：电池尾部两侧的按压钮露在机身侧壁的开窗里。为了不挡住开窗，后机臂铰链罩缩短了一点。
- **从机身上移除的旧涂装**：原先画在机身侧面的电池卡扣，以及机身底部的 ULTRA LIGHT 249g 字样，现在都是电池模型自己的部件（印字在电池底面）。
- **电池骨骼** `battery`：放在 `drone` 下面，所以跟着落地时的抬头角度一起倾斜。
  - 子骨骼：`bat_body`、`bat_collar`、`bat_button_l`、`bat_button_r`。
  - 来源：从 `model/battery/mini4pro` 的 geo 和贴图合并进来（缩放 0.5，绕 Y 轴转 180°，让触点朝前）。
  - 默认状态没有电池：`battery_empty` 把电池缩放到 0；Blockbench 工程里这个骨骼也默认隐藏。

## v2.1 顶部平滑
- 原先机身顶部由 `bev1` / `bev2` 两层“梯田”台阶拼成，每个 Z 分段是平顶，所以表面有一圈圈台阶纹。现在这两层去掉了，换成沿 Z 放样的平滑外壳：
  - 每 0.55 像素一段；
  - 每段有一片整宽顶板，左右各有 4 片肩部圆角板（圆角半径 1.15）；
  - 顶板和肩部板都会绕 X 轴倾斜，跟着机身高度的起伏走；肩部板还会绕 Y 轴偏转，跟着机身轮廓的收窄走。
- 内层 `up` 实心层只做到顶面以下 1.15 像素，外壳正好包住它，从机尾端面看也没有缝。
- 顶部的 dji 标志改成按世界坐标贴上去的图案，因为外壳是旋转过的方块，原来的写字函数不支持旋转方块。散热窗、电量灯和电源键的位置都没变。
- 方块数变为 982，贴图仍是 2048x1024。v2（顶部台阶版）的生成脚本备份为 `DJI/backup/mini4pro_v1/gen_mini4pro_v2_pre_smooth.py`。

## 关键约定
- 机头朝 -Z（north），机身右侧 = +X；1 像素 ≈ 6 mm，桨尖到桨尖约 61 像素。
- **默认姿态 = 落地姿态**：`drone` 骨骼自带约 10.6° 抬头俯仰，轴心在前脚着地点，前脚和后电机脚都正好接触 Y=0。计算着地点时不算电池。
- 飞行动画 `fly` 在 `drone` 上叠加 +10.56°（Bedrock 值）把机身调平，并让四个桨叶以 5 转/秒旋转。
- `root` 骨骼不被动画使用，留给代码按速度做前倾/侧倾。
- 骨骼：root > drone > {body, frame, gimbal, battery > {bat_body, bat_collar, bat_button_l/r}, motor_xx > prop_xx}。
- 建议渲染缩放 0.4，碰撞箱 0.9 × 0.45。渲染类型需要带 alpha 裁切（GeckoLib 默认的 `entityCutoutNoCull`）。

## 动画
| 名称 | 循环 | 用途 |
|---|---|---|
| animation.mini4pro.idle | loop | 落地静止 |
| animation.mini4pro.fly | loop | 机身调平 + 悬停浮动 + 桨叶旋转 |
| animation.mini4pro.takeoff | hold_on_last_frame | 0.6 秒从落地姿态过渡到水平 |
| animation.mini4pro.gimbal_down / gimbal_forward | hold_on_last_frame | 云台俯拍 / 回到平视 |
| animation.mini4pro.battery_empty | hold_on_last_frame | 常态：无电池（battery 缩放 0） |
| animation.mini4pro.battery_installed | hold_on_last_frame | 常态：已装电池 |
| animation.mini4pro.battery_insert | hold_on_last_frame，1.6 s | 电池从机尾后上方（偏移 0,1.6,15）滑入；1.05–1.4 s 时两侧按压钮先被压入再弹出（咔哒），电池最后轻推到位 |
| animation.mini4pro.battery_remove | hold_on_last_frame，1.4 s | 先压下按压钮，电池向后滑出，1.32 s 时缩放到 0 |

`battery_insert` 和 `battery_remove` 的最后一帧分别和 `battery_installed`、`battery_empty` 完全一样。所以参考代码里，`battery` 控制器平时按状态播放这两个常态动画，插拔时用 `triggerAnim` 播放插入或取出动画，结束后能无缝接回常态。

## 参考代码交互（reference_code/entity/Mini4ProEntity.java）
- 手持 Mini 4 Pro 电池右键无人机：插入电池，消耗 1 个电池物品（创造模式不消耗）。
- 潜行加空手右键：取出电池，电池物品还给玩家；背包满时掉在地上。
- 空手右键：切换云台。
- 没装电池时无人机受重力落地，飞行 AI 不启动；死亡时如果装着电池会掉落电池。插拔状态用 DataTracker 同步，并存进 NBT（`HasBattery`）。
- 需要注册电池物品 `MINI4PRO_BATTERY`，见注册片段。

## 已知限制
- Blockbench 只能用立方体，机身外形用分段侧板拼成，个别位置有细小台阶。
- 电池仓开窗处的侧壁比较厚（约 2 像素），所以按压钮是凹在窗里的。
- GIF 和预览图是用自带的软件渲染器生成的，光照和游戏里不完全一样。

## 修改
- 先改电池物品，再生成无人机：`python3 DJI/tools/gen_battery_mini4pro.py <out>`，打包到 `model/battery/mini4pro` 后，运行 `python3 DJI/tools/gen_mini4pro.py <out> [电池模型目录]`。
- GIF：`python3 DJI/tools/make_m4p_battery_gifs.py <mini4pro.bbmodel> <out> insert_rear,insert_side,insert_bottom,remove,stills`。单次运行最好只生成一个 GIF，每个约 1.5–2 分钟。
- 用 Blockbench 手动改的话，导出后记得更新 manifest 里的 MD5。
