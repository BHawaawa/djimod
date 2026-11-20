# djimod · 无人机：悦飞交互

Drones: Joyride Interface — Minecraft Java Edition 无人机模组。

包含 Avata 360、Mini 4 Pro、Matrice 400、RC 2 遥控器及电池、充电设备等交互内容，支持实时图传、键鼠和虚拟摇杆操作、USB RC-N1 输入、避障、返航与 4G 基站覆盖。

## 开发与构建

主要开发版本为 **Minecraft 1.20.1 / Fabric**，需要 **JDK 17**。Gradle Wrapper 已包含在仓库中，首次构建需要联网下载依赖。

```powershell
cd fabric_mod
.\gradlew.bat build
.\gradlew.bat runClient
```

Windows 系统代理环境可使用 `.\dev.ps1 build`；脚本仅向当前 Gradle 进程传递系统代理。其他系统使用 `./gradlew build`。

Fabric 1.20.1 的依赖版本固定在 `fabric_mod/gradle.properties`，包括 Fabric API 和 GeckoLib。安装、操作方式与已知限制见 [开发说明](fabric_mod/README.md)。

## 多版本支持

除主要开发版本外，仓库还保留 Fabric 1.21.1、Forge 1.20.1、NeoForge 1.21.1 及其他版本的适配工程。部分目标仍在移植中，不能视为已完成或已通过游戏内测试。

版本状态以 [发布矩阵](fabric_mod/release/matrix.json) 和 [多版本构建说明](fabric_mod/MULTIVERSION_BUILD.md) 为准。

```powershell
# 在仓库根目录列出全部构建目标
.\build.ps1 -AllTargets -List

# 构建一个指定目标
.\build.ps1 -Loader Fabric -Minecraft 1.20.1
```

## 仓库结构

| 路径 | 内容 |
| --- | --- |
| `fabric_mod/src/` | 模组源码、运行资源、现有回归测试与开发验收场景 |
| `fabric_mod/platforms/` | 共享逻辑及各加载器、游戏版本适配 |
| `fabric_mod/gradle/`、`fabric_mod/release/` | 构建逻辑与发布矩阵配置 |
| `fabric_mod/tools/` | 多版本构建、工具链准备与产物验证工具 |
| `model/`、`avata360*.bbmodel` | 可编辑模型和相关资源 |
| `tools/` | 模型、贴图、预览生成与开发辅助脚本 |
| `preview/` | 模型预览图 |
| `.github/workflows/` | 多版本构建工作流 |

历史备份、外部参考工程、Gradle 缓存、游戏存档、运行日志及编译后的模组 JAR 不提交到源码仓库。辅助脚本中部分路径为开发时的本机路径，使用前需按自己的环境调整。

## 功能边界

拍照、录像、回看和部分设置界面尚未形成完整文件输出或任务系统。物理遥控器的兼容性取决于设备、驱动与固件。各版本的实际支持情况和已知问题请参见开发文档。

## 许可证与来源

Copyright (c) 2026 BHawaawa。本项目原创代码采用 [GNU GPL v3.0](LICENSE)（`GPL-3.0-only`）。第三方代码、依赖、字体、图标、素材与商标仍适用各自的权利和许可，详见 [第三方说明](THIRD_PARTY_NOTICES.md)。

本项目为非官方 Minecraft 模组，与 DJI、Mojang 或 Microsoft 无隶属或背书关系。

更多内容：[English overview](PROJECT_DESCRIPTION_EN.md) · [Avata 模型说明](docs/AVATA_MODEL.md)
