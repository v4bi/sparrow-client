<div align="center">

# Sparrow Client

[![Version](https://img.shields.io/badge/Version-#3006202616-24b47e)](https://github.com/stfulua/sparrow-client)
[![License](https://img.shields.io/badge/License-GPLv3-blue)](https://www.gnu.org/licenses/gpl-3.0.en.html)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![Platform](https://img.shields.io/badge/Platform-Fabric%201.21.11-red)](https://fabricmc.net)

<p>Performance client for Minecraft 1.21.11 — 45+ optimizations, 20+ bug fixes, HUD overlays, and visual enhancements.</p>

⚠️ <b>Expect bugs.</b> Found one? Report on our <a href="https://discord.gg/SNzUYWbc5Q">Discord</a>.

⚠️ <b>Mod Compatibility:</b> Incompatible with <b>Sodium</b>, <b>Iris</b>, <b>OptiFine</b>, and <b>Canvas</b> (rendering pipeline conflicts). Fully compatible with <b>Lithium</b>, <b>ReplayMod</b>, <b>ModernFix</b>, <b>FerriteCore</b>, <b>Krypton</b>, and <b>ImmediatelyFast</b>.

</div>

---

### Features

<details open>
<summary><b>⚡ Performance</b> (25 optimizations)</summary>

- **Lighting Kill** — disables all client-side lighting computation (lightmap, block light, sky light). Zero CPU cost for lighting.
- **Entity/Item/Orb Culling** — distance-culls items (40b) and entities (128b). Aggregates nearby items+orbs into single rendered entities.
- **Beacon / Conduit Culling** — don't render beams or conduit effects beyond a distance.
- **Experience Orb Culling** — culls distant orbs before they reach render.
- **Packet Distance Cull** — drops entity spawn packets for entities past render range.
- **Section Builder Culling** — skips rebuild for distant chunk sections.
- **Distant Chunk LOD** — simplified chunk rendering at distance.
- **Cloud / Weather / Sky Kill** — disables cloud rendering, rain/snow particles, and sky completely.
- **Shader Removal** — disables all post-processing shaders (blur, bloom, etc.).
- **Goal Selector Bloat** — limits entity AI goal evaluations per tick.
- **Dynamic Uniforms** — reduces uniform update frequency in render passes.
- **Frame Pacer Control** — frametime capping for consistent frame pacing.
- **No Error GL Context** — eliminates OpenGL error checking overhead.
- **Animation Culling** — skips animated texture updates for off-screen sprites.
- **Block Face Culling** — early-out culling for hidden block faces.
- **Particle Limiter** — caps particles per frame.
- **Fog Disable** — completely removes all fog (water, lava, powder snow, blindness, darkness, atmospheric).
- **Debug Renderer Skip** — eliminates debug renderer overhead.
- **Nether Render Cap** — separate configurable max render distance for the Nether.

</details>

<details open>
<summary><b>🎨 Visual</b> (20 enhancements)</summary>

- **Fullbright** — always max brightness, zero darkness.
- **Gamma Override** — forces gamma to 15.0 every frame (Sodium-compatible).
- **No Nausea** — removes portal nausea wobble and overlay.
- **No Hurt Cam** — removes damage screen shake.
- **No Fire Overlay** — removes fire overlay when burning.
- **No Vignette** — removes screen-edge darkening.
- **No Misc Overlays** — removes potion effect overlays, pumpkin blur, and screen clutter.
- **View Bob Disable** — disables head bobbing.
- **Custom Glint** — RGB-configurable enchantment glint color (default: green).
- **Old Potion Colors** — restores classic pre-1.20 potion color palette.
- **Small Totem** — reduced totem animation size.
- **Totem Pop** — subtle totem activation effect.
- **Clear Fluids** — fully transparent water and lava.
- **Old Fluid Rendering** — disables modern fluid translucency for clarity.
- **Entity Shadow Remover** — disables all entity shadows.
- **Floating Item Scale** — configurable scale for dropped item icons.
- **ViewModel** — X/Y/Z position + scale customization for first-person items.
- **Zoom** — optifine-style zoom with configurable sensitivity, min, max, and smoothness.
- **Boss Bar Skip** — hides the boss health bar overlay.
- **Block Break Overlay Removed** — removed for performance.
- **Disconnect Clear** — clean disconnect screen for faster reconnecting.

</details>

<details open>
<summary><b>🖥️ HUD</b> (13 overlays)</summary>

- **Coordinates** — real-time X/Y/Z in bottom-left.
- **Ping** — latency display in top-right.
- **Desync Detection** — alerts when server rubberbands you while moving.
- **Hit Marker** — visual + sound confirmation on hit land.
- **True Cooldown** — accurate attack cooldown bar that respects weapon swap.
- **Shield Status** — shield health remaining and active state.
- **Fire Timer** — remaining fire duration in readable format.
- **Attack Cooldown** — cooldown bar on the hotbar.
- **Cooldown Reset** — visual flash when item use cooldown resets.
- **Swap Cooldown** — cooldown indicator when switching items.
- **Storage Tooltip** — preview chest/barrel/shulker contents on hover.
- **Cooldown Reset Tracker** — server-side cooldown sync fix for hit detection.
- **Position Utility** — all HUD elements positionable via config.

</details>

<details open>
<summary><b>🔧 Bug Fixes</b> (14 fixes)</summary>

- **Sprint FOV Smoothing** — fixes MC-20302 (jumpy FOV on sprint toggle).
- **Shield Desync Fix** — auto-resync shield when right-click doesn't register server-side.
- **Shield Visibility** — properly displays shield in third-person player model.
- **Block Resync** — periodic block state sync to prevent ghost blocks.
- **Ghost Block Detect** — detects blocks that are desynced between client and server.
- **Inventory Desync Fix** — auto-corrects hotbar slot when inventory gets out of sync.
- **Rubberband Recovery** — restores sprint/sneak after server position correction.
- **Knockback Predictor** — predicts knockback for accurate hit confirmation.
- **Packet Error Ignore** — suppresses benign network errors that would disconnect you.
- **Paletted Container Fix** — prevents world corruption from palette bugs (MC-267913, MC-269572).
- **Double Consume Fix** — prevents eating/drinking twice from a single click.
- **Portal GUI Unlock** — allows interacting with containers while inside a nether portal.
- **Creative Feature Unlock** — enables operations on non-OP singleplayer worlds.
- **Sprite Animator Fix** — prevents texture animation memory leaks.

</details>

<details open>
<summary><b>⌨️ Movement & Input</b></summary>

- **Toggle Sneak** — toggle-crouch with intelligent interaction bypass (open chests while sneaking).
- **Smooth Elytra** — auto-stops elytra gliding on ground contact.
- **Click Relay** — queues item use clicks during cooldown, auto-executes when ready. Prevents missed pearls/food on slot switch.
- **Mouse Scroll** — scroll wheel customization for zoom and inventory interactions.

</details>

<details open>
<summary><b>🌍 World</b></summary>

- **No Mining Fatigue** — visual-only removal of mining fatigue screen effect.
- **Always Day** — forces constant daylight brightness level.
- **Disable Entity AI** — stops all entity AI processing (for singleplayer/custom maps).
- **Nether Render Cap** — independent render distance slider for the Nether.
- **Portal GUI Unlock** — interact with blocks/containers while in a portal.

</details>

<details open>
<summary><b>🛡️ Privacy</b></summary>

- **Telemetry Killer** — blocks all telemetry transmission to Mojang.
- **Telemetry Log Disable** — prevents telemetry log file creation.

</details>

---

### Console Commands

Press **Right Shift** in-game to open the Sparrow console. Type any feature name to toggle it. No prefix needed.

| Command | Description |
|---------|-------------|
| `coords` | Toggle coordinate display |
| `ping` | Toggle ping display |
| `desync` | Toggle desync detection |
| `hitmarker` | Toggle hit marker |
| `fire-timer` | Toggle fire timer / set position (`on\|off\|pos`) |
| `particles` | Set particle mode (`off\|minimal\|on`) |
| `block-lod` | Set block LOD mode (`off\|low\|pvp\|aggressive`) |
| `sneak` | Toggle sneak |
| `glint` | Custom glint (`on\|off\|r\|g\|b <0-255>`) |
| `view` | View model (`x\|y\|z\|size\|utility-scale <value>`) |
| `zoom` | Zoom (`<level>\|smoothness\|min\|max <value>`) |
| `help` | Show commands help |
| `list` | List all features with current values |
| `clear` | Clear console output |

Most toggles accept `on` / `off` as argument. Omitting the argument flips the current value.

---

### Configuration

<details>
<summary><b>View configurable features</b></summary>

**Visual toggles** (13):
`small-totem`, `old-potions`, `custom-glint`, `fire-timer`, `no-misc-overlays`, `remove-shadows`, `storage-tooltip`, `coords`, `ping`, `desync`, `hitmarker`, `shield-status`

**Visual settings** (11):
`view-x`, `view-y`, `view-z`, `view-size`, `utility-scale`, `glint-r`, `glint-g`, `glint-b`, `fire-timer-pos`, `particles`, `block-lod-mode`

**Movement** (0):

**Tweaks** (1):
`click-relay`

**Camera** (5):
`zoom`, `zoom-smoothness`, `zoom-min`, `zoom-max`, `disable-mouse-wheel`

**World** (4):
`fullbright`, `no-mining-fatigue`, `always-day`, `disable-entity-ai`, `nether-render-cap`

**Optimization** (2):
`item-culling-distance`, `entity-culling-distance`

**Console** (1):
`console-fps`

</details>

Configuration is saved to `config/sparrow/config.json` and persists across restarts.

---

### Server Safety

Sparrow fetches a blocklist from CDN on server join. When connected to a server listed in the blocklist, flagged features are automatically disabled — no manual toggling needed.

To request server-specific feature blocking, contact us on Discord.

---

### Building

```bash
gradle build                          # full build
gradle build -PdevMode=true           # dev build (faster)
```

Output: `client/build/libs/SparrowClient-Fabric-1.21.11.jar`

---

### Links

- 🌐 **Website:** https://vprolabs.xyz
- 💬 **Discord:** [Join](https://discord.gg/SNzUYWbc5Q)
- 📦 **GitHub:** https://github.com/stfulua/sparrow-client

---

### License

This project is licensed under the **GNU General Public License v3.0** (GPL v3).

- You may use, modify, and distribute this software.
- Using Sparrow Client in videos, streams, or recordings (monetized or not) is allowed.
- Modified versions must be distributed under the same license.
- Source code changes must be disclosed.
- No warranty or liability.

[View Full License](https://www.gnu.org/licenses/gpl-3.0.en.html)

---

<div align="center">
  <sub>Made with 🔥 by <strong>vProLabs</strong></sub>
</div>
