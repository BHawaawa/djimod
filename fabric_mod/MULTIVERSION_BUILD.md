# 多版本构建（V1.0.33）

发布矩阵唯一来源：`release/matrix.json`。当前按最新要求包含 13 个加载器/游戏版本组合。

| 加载器 | 游戏版本 | 目标 Java | 加载器版本 | 当前状态 |
| --- | --- | --- | --- | --- |
| Forge | 1.16.5 | 8 | 36.2.42 | 移植中 |
| Forge | 1.17.1 | 16 | 37.1.1 | 移植中 |
| Forge | 1.18.2 | 17 | 40.3.12 | 移植中 |
| Forge | 1.19.2 | 17 | 43.5.2 | 移植中 |
| Forge | 1.20.1 | 17 | 47.4.29 | 已编译打包，未运行测试 |
| NeoForge | 1.21.1 | 21 | 21.1.266 | 已编译打包，未运行测试 |
| Fabric | 1.16.5 | 8 | 0.16.10 | 移植中 |
| Fabric | 1.17.1 | 16 | 0.16.10 | 移植中 |
| Fabric | 1.18.2 | 17 | 0.16.10 | 移植中 |
| Fabric | 1.19.2 | 17 | 0.16.10 | 移植中 |
| Fabric | 1.20.1 | 17 | 0.16.10 | 可发布 |
| Fabric | 1.21.1 | 21 | 0.16.10 | 可发布 |
| NeoForge | 26.3 | 25 | 26.3.0.103 | 移植中 |

`移植中`表示已固定依赖、配置独立工程，但源码编译尚未通过。`compiled` 表示已编译打包、没有运行测试；`ready` 保留已有运行验收记录。按用户最新要求，构建默认跳过测试。Fabric 26.3 不在最新发布矩阵中，之前的实验目录保留，不参加批量构建。

## 常用命令

在 `fabric_mod` 目录运行：

```powershell
# 显示所有目标，检查游戏版本与加载器组合
.\tools\build_matrix.ps1 -AllTargets -List

# 对所有 13 个目标进行构建：单个目标失败后继续，结束时返回非零退出码
.\tools\build_matrix.ps1 -AllTargets -SkipTests

# 只构建已完成编译适配的四个目标，默认不运行测试
.\tools\build_matrix.ps1

# 指定目标，仅编译源码，不打包、不运行测试
.\tools\build_matrix.ps1 -Loader NeoForge -Minecraft 26.3 -CompileOnly

# 只验证跨平台核心逻辑：编译为 Java 8 字节码
.\gradlew.bat -p platforms\common check

# 执行发布工具校验测试
python tools\test_release_tools.py
```

输出目录默认为工程根目录的 `mod/V1.0.33`。命名为：

```text
DronesJoyrideInterface-<游戏版本>+<加载器>-V1.0.33.jar
```

`build-report.json` 记录最近一次尝试、成功产物的 SHA-256、未完成移植的原因。`reports/<runId>/` 保留每次运行的独立报告，`logs/<runId>/<游戏版本>+<加载器>.log` 保留对应 Gradle 原始日志。`-CompileOnly` 写入独立 `compile-report.json`，不覆盖发布报告。进行中的报告在每个目标完成后原子更新。

## 工程结构

- 既有 `src/main` 与根 Gradle 工程继续负责 Fabric 1.20.1，保留 `djimod` 注册 ID。
- `platforms/common` 从唯一源码位置编译覆盖范围、返航规划、充电指示、姿态计算和绘图接口；无需 Minecraft 或加载器依赖，字节码为 Java 8。
- `platforms/<loader>/<minecraft>` 固定各平台独立 Wrapper、Java、映射、GeckoLib 和加载器依赖。
- `platform-conventions.gradle` 统一版本号、文件名、资源处理、共享核心和发布门槛。
- `fabric-platform.gradle` / `forge-platform.gradle` / `neoforge-platform.gradle` 分别处理加载器构建 API。
- 版本专用适配写到各平台 `src/main/java`。相同路径的文件自动覆盖共用平台源码，避免复制整套工程；`build/generated/portSources` 为生成目录，不手改。
- `source-migrations.gradle` 记录机械 API 更名、逐行转换与文件级替换；文件级适配找不到原文时构建失败，要求人工审阅源代码变化。

当前配置以完整既有源码执行编译诊断。未实现的加载器/游戏 API 适配会真实产生错误；没有用空入口或删除功能来绕过编译。

## Fabric 1.21.1 适配与验收

1.21.1 使用 GeckoLib 4.6.6、Fabric API 0.116.18、Java 21。平台适配包含不可变物品组件、旧嵌套 NBT 电池升级、新版网络 Payload、实体数据跟踪、区块流、新版渲染矩阵和数据包资源目录。

已通过开发环境的电池数据/名称保存恢复、充电、遥控器、客户端和独立服务器测试。生产命名空间候选包已通过高空地面像素检查、320 格远程区块图传、窗口缩放、退出相机恢复、独立服务器和生存模式实际 C2S 配对/解锁/上升/急停悬停/耗电测试。Sodium、Iris 和 Xaero 在 1.21.1 上尚未专项验收；不代表已验证所有组合或没有未知 BUG。

平台目录内可运行 `runPortQa`、`runPortChargingQa`、`runPortRc2Qa`、`runPortSurvivalQa`、`runPortServerQa`。QA 模组位于独立 sourceSet，不进入发布包。独立服务器测试只在用户接受 EULA 后运行。

生产环境候选包验证：

```powershell
# 在 platforms/fabric/1.21.1 内构建候选与独立 QA 包，输出到 build/qa-candidate
.\gradlew.bat check remapJar remapPortQaJar portLaunchFiles -PportCandidate

# 在 fabric_mod 内启动 production/intermediary 命名空间测试
python tools/run_fabric_port_qa.py --minecraft 1.21.1 --java '<JDK21目录>' --scenario survival
# scenario 可选 render / charging / rc2 / survival / server
```

`portCandidate` 将产物放在隔离目录。根工程 `exportMojmapSources` 导出 Yarn → Mojang 命名源码；Forge 1.20.1 和 NeoForge 1.21.1 在此基础上适配原生入口、Mixin 和物品渲染接口，两者均已编译打包。它们需要对应加载器版本的 GeckoLib 4 和 Forgified Fabric API（不是 Fabric API，也不需要 Connector）；串口库已包含在模组 JAR 中。

## 工具链与网络

构建脚本按目标自动选择本机 JDK 17、21、25，并在退出时还原 JAVA_HOME。JDK 从 `.cache/toolchains`、Program Files 中的 Microsoft / Eclipse Adoptium、用户 Gradle jdks 目录查找；Java 25 也支持 `-Java25Home` 或 `DJI_JAVA25_HOME`。1.16.5/1.17.1 使用 JDK 17 编译器的 `--release 8/16`，对应运行验收仍需目标 Java 运行时。

旧 Forge 的 MCP 步骤还要求 Java 8/16 本身存在。可用以下命令从固定 URL 下载到工程缓存并校验 SHA-256，无需修改系统 Java 或 PATH：

```powershell
python tools/bootstrap_toolchains.py
```

工具链锁文件位于 `release/toolchains.json`；已存在的本机 JDK 17/21 和工程缓存 JDK 25 继续复用。

NeoForge 工程使用 Gradle 9.8.2，其他当前平台使用 8.10；Wrapper 配置包含官方 SHA-256。脚本优先复用 `.cache/toolchains/gradle-<版本>`。不自动升级根工程。

可通过 `-ProxyUrl http://host:port` 或 HTTPS_PROXY 指定网络代理。代理仅作用于本次构建子进程，不保存凭据。Forge 官方仓库当前网络返回 403 时，使用限定到 Forge 坐标组的 BMCLAPI 镜像；官方仓库仍作后备。

## 发布与新增目标

每个目标使用自己的工程、Gradle 任务和产物路径。打包校验检查嵌入的模组 ID、游戏版本、加载器、入口类、版本号、QA 类和 Java 字节码；错误即停止该目标复制。按当前用户要求，批量构建使用 `-SkipTests`：只编译、重新映射并打包，不启动游戏、不运行测试；编译成功的目标允许输出 JAR，报告的 `runtimeVerified` 独立记录是否已有运行验收。编译失败不会生成替代包。

新增目标时建立独立平台目录，在矩阵中填写 project、buildTasks、artifact、java、compilerJava、gradleJava、加载器和 GeckoLib 依赖。先标记 porting，再完成注册、网络、存档数据、渲染/Mixin 和资源适配。编译打包成功标记 compiled；已有运行验收的目标可标记 ready。构建成功不会自动宣称运行验收通过。

26.3 详情见 [platforms/26.3-PORTING.md](platforms/26.3-PORTING.md)。

GitHub Actions 配置位于工程根目录 `.github/workflows/drones-matrix.yml`。常规提交构建 ready/compiled 目标；手动勾选 include_ports 会编译所有 13 个目标，未完成移植的目标会真实失败并保留日志。工作流仅编译打包，尚未在远端执行。
