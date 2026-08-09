<div align="center">

# Sparrow Client

[![Version](https://img.shields.io/badge/Version-#2026080958-24b47e)](https://github.com/stfulua/sparrow-client)
[![License](https://img.shields.io/badge/License-GPLv3-blue)](https://www.gnu.org/licenses/gpl-3.0.en.html)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/)
[![Platform](https://img.shields.io/badge/Platform-Fabric%201.21.11-red)](https://fabricmc.net)

<p>Performance client for Minecraft 1.21.11 — 40+ configurable features, 20+ always-on optimizations, HUD overlays, and visual enhancements.</p>

⚠️ <b>Expect bugs.</b> Found one? Report on our <a href="https://discord.gg/SNzUYWbc5Q">Discord</a>.

⚠️ <b>Mod Compatibility:</b> Incompatible with <b>Sodium</b>, <b>Iris</b>, <b>OptiFine</b>, and <b>Canvas</b> (rendering pipeline conflicts). Fully compatible with <b>Lithium</b>, <b>ReplayMod</b>, <b>ModernFix</b>, <b>FerriteCore</b>, <b>Krypton</b>, and <b>ImmediatelyFast</b>.

</div>

---

### Sparrow Menu (Click GUI)

Press **Right Shift** in-game to open the Sparrow Menu (configurable via the `ui` module: `ui menu` or `ui terminal`). Everything is clickable — no commands needed:

- Category **tabs** span the top edge; the active category's features fill the panel as a tile grid.
- **Left click** a tile toggles the feature (or opens its settings for numeric/string features).
- **Right click** a composite tile (gear icon) to open its settings popup: sliders, cycle options, hex color fields.
- Hover any row for a description tooltip. The **reset icon** on each row restores factory defaults.
- The **HUD Editor** button (top-right) opens drag-to-reposition mode for HUD elements.
- **Esc** closes the menu (a second Esc closes an open popup). The menu pauses the game.

The tile grid adapts to your window: 4 columns on wide screens, fewer on small windows, so rows never clip. `gui-scale` (Sparrow tab, 0.5 to 1.5) scales menu elements; `gui-fps` caps FPS while the menu is open.

### Features

<details open>
<summary><b>⚡ Sparrow</b> (3)</summary>

- **UI** — choose what Right Shift opens: the Sparrow Menu (click GUI) or the legacy terminal.
- **GUI FPS** — frame cap while the menu is open (5-240, default 60).
- **GUI Scale** — scales menu elements; the panel always fills the screen (0.5-1.5).

</details>

<details open>
<summary><b>🎨 Visual</b> (21)</summary>

- **Small Totem** — shrinks the totem pop animation to the corner of the screen.
- **No Totem Pop** — removes the totem pop animation entirely.
- **Old Potions** — restores the classic pre-1.9 potion color palette.
- **Custom Glint** — replaces the vanilla glint with your own RGB color.
- **No Misc Overlays** — removes nausea wobble, pumpkin blur and screen clutter.
- **Remove Shadows** — removes entity and block shadows.
- **Storage Tooltip** — preview chest/barrel/shulker contents on hover.
- **Coordinates** — real-time X/Y/Z in the bottom-left.
- **Ping** — latency display in the top-right.
- **Desync Detection** — alerts when the server rubberbands you while moving.
- **Hit Marker** — visual confirmation when an attack connects.
- **Shield Status** — shield charge and cooldown on the HUD.
- **Player Hit** — colors players you can hit (toggle / when it shows / color).
- **Crosshair** — custom crosshair style and color (off, plus, heart, tiny, dot, x, clover).
- **View Model** — X/Y/Z position and scale of the held item.
- **Utility Scale** — extra scale for held utility items (torches, etc.).
- **Glint** — custom glint toggle plus RGB channels.
- **Fire Timer** — attack-cooldown bar with HUD position option.
- **Particles** — particle mode: off, minimal, or vanilla.
- **Fullbright** — maximum brightness, day and night.
- **No Mining Fatigue** — removes the mining speed penalty.

</details>

<details open>
<summary><b>🌍 World</b> (3)</summary>

- **Always Day** — locks the sky to daytime (client-side).
- **Disable Entity AI** — stops all entity AI and goals (mobs stand still, big CPU win).
- **Nether Render Cap** — independent max render distance for the Nether (2-20, default 6).

</details>

<details open>
<summary><b>🔭 Camera</b> (1)</summary>

- **Zoom** — optifine-style zoom while holding the zoom key; scroll wheel adjusts the level on the fly. Settings popup: level (0.6-100), smoothness, scroll min/max, reset-on-activate, and the exact reset level.

</details>

<details open>
<summary><b>⚙️ Optimization</b> (16)</summary>

- **Block LOD** — distance-based LOD: OFF / LOW / PVP / AGGRESSIVE.
- **Block Model Optimization** — culls hidden block faces early.
- **Animation Culling** — skips animations for off-screen or distant entities.
- **Section Culling** — culls unseen chunk sections.
- **Debug Render Kill / Skip** — disables the per-frame F3 debug rendering.
- **Shader Removal** — removes expensive post-processing shader passes.
- **Dynamic UBO Prealloc** — pre-allocates dynamic uniform buffers.
- **Lighting Cull** — skips light recalc on block changes.
- **GL No-Error Context** — OpenGL context without error checking.
- **Pack Icon Scaling** — scales down large resource pack icons.
- **Item / Entity Culling Distance** — render distance for items and entities.
- **Particle Cull Distance** — distance beyond which particles are not ticked or rendered.
- **Adaptive Resolution** — shrinks render resolution when FPS drops below 60 (with a floor setting).

</details>

<details open>
<summary><b>🧰 Misc</b> (2)</summary>

- **Disable Mouse Wheel** — disables the scroll wheel entirely.
- **Ghost Block** — detects and fixes ghost blocks (client/server desync).

</details>

<details open>
<summary><b>⚡ Always-on optimizations</b> (no toggle)</summary>

- **Fog removal** — water, lava, powder snow, blindness, darkness and atmospheric fog.
- **Sky / Cloud / Weather kill** — disabled completely.
- **Beacon / Conduit culling** — no beams or conduit effects beyond a distance.
- **Experience orb culling** — distant orbs culled before render.
- **Entity render culling** — occlusion + distance culling, including player occlusion.
- **Chunk upload throttle + LOD radius** — fixes ghost blocks and speeds up chunk streaming.
- **Distant chunk LOD** — simplified rendering for far sections.
- **Goal selector bloat** — limits entity AI goal evaluations.
- **Section builder culling** — skips rebuilds for distant sections.

</details>

---

### Console Commands

The legacy terminal (opened with `ui terminal` or the `gui` command from the menu) accepts feature names directly — type any feature name to toggle it. No prefix needed.

| Command | Description |
|---------|-------------|
| `coords`, `ping`, `desync`, `hitmarker`, `zoom`, `sneak`, ... | Toggle any feature by name |
| `list` | List all features with current values, grouped by category |
| `help` | Show command help |
| `clear` | Clear console output |
| `gui` | Open the Sparrow Menu |
| `ui <menu\|terminal>` | Choose what Right Shift opens |
| `glint r\|g\|b <0-255>` | Set a glint color channel |
| `view x\|y\|z\|size <value>` | Adjust the view model |
| `zoom reset-level\|smoothness\|min\|max <value>` | Adjust zoom settings |
| `fire-timer on\|off\|pos` | Toggle the fire timer / set its HUD position |
| `particles off\|minimal\|on` | Set particle mode |
| `block-lod off\|low\|pvp\|aggressive` | Set block LOD mode |

Most toggles accept `on` / `off`; omitting the argument flips the current value. Tab completes commands and options.

---

### Configuration

All settings are saved to `modules.json` in the game directory and persist across restarts. On first run after an update, a legacy `config.json` (if present) is migrated automatically, so no settings are lost. Every feature can also be managed from the Sparrow Menu — the console and the menu read the same registry and can never disagree on valid values.

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
