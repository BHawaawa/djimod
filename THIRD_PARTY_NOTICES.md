# Third-party notices / 第三方说明

The root MIT license applies to this project's original contributions. It does not grant rights to third-party code, dependencies, fonts, artwork, trademarks or other materials owned by others. Existing third-party notices must be retained.

## Dependencies

Minecraft, Fabric, Forge, NeoForge, GeckoLib, jSerialComm, Xaero's World Map and other dependencies are obtained through the build configuration and remain subject to their respective licenses and terms. Publishing this repository does not relicense those dependencies. Gradle Wrapper files remain subject to the Gradle project's license.

## RC-N1 protocol reference

The local development workspace contained `DJI_RC-N1_SIMULATOR_FLY_DCL-master`, an external reference project carrying an Apache License 2.0. That checkout and its archive are excluded from this repository. The mod implements direct RC-N1 USB serial input; see `fabric_mod/src/main/java/com/example/avatadrone/client/RcnSerialInput.java`. If you incorporate code from the reference project, retain the applicable Apache license and attribution.

## Imported material and assets

`fabric_mod/YUEFEI_IMPORT_AUDIT.md` records material recovered and merged from a YueFei V1.0.32 mod JAR, including code and resources. The root MIT license covers only contributions for which the project author can grant that license; it does not establish or replace a license for the original imported material. This repository does not assert that all such material has been independently licensed under MIT.

Fonts, icons, logos, textures and models may include third-party material. Consult their existing metadata and source records before reuse outside this mod; no separate trademark or third-party asset rights are granted by the root MIT license.

DJI and product names are trademarks of their respective owners. Minecraft is a trademark of Mojang/Microsoft. This is an unofficial project and is not affiliated with or endorsed by those companies.
