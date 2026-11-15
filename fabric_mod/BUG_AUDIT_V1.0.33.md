# V1.0.33 BUG audit

The supported target for this release is Fabric 1.20.1. The following checks
passed on the release sources:

- `gradlew check`: Java compilation, unit tests and access-widener validation.
- Coverage geometry: 45-degree directional sectors, three-dimensional 500-block
  range, all four facings and 14,400 angular samples.
- Return-route planner: direct, obstacle detour, lateral detour, blocked and
  search-budget cases.
- Gimbal attitude, key binding, charging boundary and persistence regressions.
- Release remapping and JAR metadata (`djimod`, version `1.0.33`, Minecraft
  `1.20.1`).

No reproducible crash or logic regression remains in those checks. The existing
shader compatibility limitation remains documented: MakeUp UltraFast can render
the secondary feed too dark or black because its shader-owned framebuffer is not
part of Minecraft's public rendering API. Vanilla rendering and Iris/Sodium with
shaders disabled pass the feed checks. A future shader-specific adapter is
required before claiming full shader-pack compatibility.
