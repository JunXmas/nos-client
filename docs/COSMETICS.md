# Bundled cosmetic try-on

Minecraft Java 1.21.11 / Fabric / Java 21. Menu: Cosmetics; in a world: Right Shift → Cosmetics. The native preview works at the main menu and on the local player in-world. Drag to rotate, choose Angel/Red demon, toggle wings/cape/headband separately. Try on applies to the current local session; Clear or disconnect removes it. The screen pauses single-player while the cosmetic animation continues on its monotonic clock.

This is a local visual try-on, not an ownership grant or a paid inventory. No commerce API, premium flag, Google bearer token, remote account lookup or multiplayer appearance sync was added. Remote players are not assigned the local selection. A later ownership/equipment provider can replace the session selection without changing the mesh renderer.

## Assets and renderer

`tools/compile_cosmetics.py --angel PACK --crimson PACK` reads the finalized individual GLBs and copies their original texture atlases. No GLB dependency or runtime download is needed. It preserves the exported triangulation and quaternion keys. Each triangle becomes a degenerate quad for Minecraft's entity vertex format. Six generated JSON rigs are under `assets/nosclient/cosmetics/`; provenance accompanies each collection.

The feature layer registers for both wide/slim avatar renderers. Geometry is batched by item/material, not submitted per bone. Rig matrices are sampled and cached at up to 120 Hz near the camera / 15 Hz beyond 24 blocks; animation speed is independent of frame rate. The deferred collector captures an immutable matrix snapshot; static vertex arrays are reused, avoiding a full copied vertex buffer each frame. No resource reads or network operations run in the render loop. All models reload on resource reload (F3+T); a malformed resource disables that item and logs the cause.

Source GLBs use block units, feet origin and Y up. The renderer attaches to the Minecraft body/head joint, translates by +1.5 Y and reflects Y. Body animation/crouching and head yaw/pitch are inherited from the actual player model. Helmets expand the head accessory slightly; chest armor moves back accessories outward. Cosmetic back slots hide with a glider/elytra, fall flying, visual swimming and sleep. Invisible players/spectators are skipped. Beyond 64 blocks the local trial layer is skipped. A valid active trial cape suppresses the vanilla cape for the same local player only.

The showroom uses a dedicated cutout shader with neutral colors, no scene fog or lightmap. World rendering uses Minecraft's normal cutout lighting and fog. Depth testing/writes and alpha discard keep the surface opaque and avoid transparent sorting artifacts.

## Build and validation

Build with JDK21: `./gradlew build`. The normal `nosclient-0.2.0.jar` is remapped for Fabric/intermediary; sources are also built. Do not ship the development probe or `run/` files.

JVM tests cover delivered triangle budgets (Angel904 / Red demon676), finite UV/rig data, cycle repetition after hours, immutable deferred snapshots, invalid rig cycles and the emitted Minecraft coordinate/normal transform. Native verification and artifact hashes are recorded with the delivered build. Scope is the reference cosmetics and supported Minecraft version; other shader mods/armor animations still require compatibility testing.
