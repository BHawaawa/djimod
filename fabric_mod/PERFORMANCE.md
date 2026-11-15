# 性能优化记录（2026-11-14）

本轮重点降低 RC2 飞行界面、地图和测距的重复工作。未降低世界视距、纹理分辨率、服务器飞控更新频率或改变 4G 覆盖规则。

## 已修改

| 热点 | 优化 |
|---|---|
| 10000 格 RC 覆盖圆环 | CPU 抗锯齿扫描先裁剪到可见地图范围；此前只有 GPU 裁剪，屏幕外仍大量扫描 |
| 被全屏地图/姿态面板遮住的图传 | 保留最后一帧并跳过世界渲染，关闭面板立即恢复；遥控输入、遥测和区块同步继续 |
| 不显示的辅助图传 | 设置等面板遮住辅助窗口时跳过辅助世界渲染 |
| 高刷新率图传 | 主图传世界渲染上限 60 FPS，使用累计时间调度避免 90 Hz 显示时降为 45 Hz；不限制游戏 HUD 整体帧率 |
| HUD 字体 | 缓存字体样式、回退选择和文字宽度，最多 2048 项，资源重载后清理 |
| 备用地图绘制 | 同行相邻同色格子合并，减少矩形提交，保持采样网格与颜色 |
| 备用地图缓存 | 原生 long 坐标缓存，避免 Long 装箱；最多 60000 项，逐项淘汰，避免整张缓存清空引起重采样 |
| 客户端辅助测距 | 水平 8 向及上下测距统一最多 10 Hz；服务器的主动避障飞控继续原频率 |
| 客户端地面扫描 | 进入新方块位置立即更新，同位置最多每 250 ms 重扫，关闭界面清理状态 |
| 4G 判定分配 | 站点扫描不再临时创建两个 Vec3d；RC 范围有效时跳过额外覆盖扫描，判定结果相同 |
| 组合键读取 | 修饰键数组常量复用，移除每次轮询创建数组 |

## 测量与验证

游戏内使用同一客户端、同一组中文/数字 HUD 字符串，比较原测宽路径和缓存路径，预热后各运行 5 组、每组 10000 次，取中位数。最终单客户端运行结果（Sodium/Iris 关闭光影）：10.527 ms → 1.338 ms，缓存路径约快 7.87 倍，耗时减少约 87.3%。参考路径和原实现一样复用字体 Identifier，避免额外分配影响对照。此为文字布局局部测量，不能换算成整体 FPS 提升比例。

验收代码在独立 QA JAR 的 `UiPerformanceChecks` 中，生产包不包含它。检查所有样例文字宽度一致，并比较裁剪前后可见圆环像素；另检查 10000 像素圆环的扫描受视口限制。

构建与自动检查命令：

```powershell
.\dev.ps1 build remapRenderQaJar compatLaunchFiles --offline --console plain --max-workers=4
python ../tools/run_yuefei_qa.py vanilla --no-xaero
python ../tools/run_yuefei_qa.py iris
```

已有回归覆盖 4G 的 14400 个角度样本及边界、充电灯、组合键、云台姿态和返航。游戏内验收覆盖生存模式图传、地图、基站同步、六个辅助方向、设置页、第三人称恢复和退出缓冲清理。

最终运行结果见 `run-yuefei-qa-vanilla-no-xaero/launcher.log`、`run-yuefei-qa-iris/launcher.log` 和各自 `ui-performance-result.txt`。没有用不同世界/不同场景的 FPS 数字宣称提升。

开启 MakeUp UltraFast 光影时已有的图传偏暗/辅助黑屏问题仍见 `YUEFEI_IMPORT_AUDIT.md`；本轮运行验收使用关闭光影的 Iris。

发布包：`build/libs/dji-avata-drone-1.0.33.jar`。对外发布文件由
`tools/build_matrix.ps1` 复制为 `mod/V1.0.33/DronesJoyrideInterface-1.20.1+Fabric-V1.0.33.jar`。

最终发布 JAR SHA256：`4d8bb5e787a3df0eb27c7fc3ef89aa7ca1f220920d52d5a38cefc0aeb8ac2659`。注册 ID 与 QA 排除扫描通过。
