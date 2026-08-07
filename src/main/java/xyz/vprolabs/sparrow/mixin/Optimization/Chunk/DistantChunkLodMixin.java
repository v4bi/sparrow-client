package xyz.vprolabs.sparrow.mixin.Optimization.Chunk;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import xyz.vprolabs.sparrow.module.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.NormalizedRelativePos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Multi-tier block-LOD: two separate cancellation points with independent
 * distance thresholds per mode.
 *
 * <p>Inject 1 — {@code scheduleChunkTranslucencySort}: skips translucent face
 * sorting past the trans-skip distance. Pure CPU save, no visual change.
 *
 * <p>Inject 2 — {@code scheduleChunkRender}: cancels chunk section rendering
 * entirely past the render-cull distance. Aggressive FPS gain at the cost of
 * visible terrain cut-off.
 *
 * <p>Modes and thresholds (blocks, horizontal):
 * <table>
 *   <tr><th>Mode</th><th>Trans Skip</th><th>Render Cull</th></tr>
 *   <tr><td>OFF</td><td>–</td><td>–</td></tr>
 *   <tr><td>LOW</td><td>48</td><td>128</td></tr>
 *   <tr><td>PVP</td><td>24</td><td>64</td></tr>
 *   <tr><td>AGGRESSIVE</td><td>16</td><td>48</td></tr>
 * </table>
 *
 * <p>Render-cull is disabled in the Nether — {@link NetherRenderDistanceMixin}
 * already caps chunk rendering there. Translucency skip applies in all dimensions.
 *
 * <p>Distance is computed in the XZ plane only (horizontal). Y is ignored because
 * chunk-culling at different Y levels when directly under the player is a false
 * positive.
 */
@Mixin(WorldRenderer.class)
public class DistantChunkLodMixin {

	// ── Thresholds (blocks, horizontal) ──────────────────────────────

	@Unique private static final int OFF_TRANS          = 0;
	@Unique private static final int LOW_TRANS          = 48;
	@Unique private static final int PVP_TRANS          = 24;
	@Unique private static final int AGGRESSIVE_TRANS   = 16;

	@Unique private static final int OFF_CULL           = 0;
	@Unique private static final int LOW_CULL           = 128;
	@Unique private static final int PVP_CULL           = 64;
	@Unique private static final int AGGRESSIVE_CULL    = 48;

	@Unique private static boolean sparrow_lodLogged = false;
	@Unique private static boolean sparrow_cullLogged = false;

	// ── Inject 1: translucency sort cancel ──────────────────────────

	@Inject(method = "scheduleChunkTranslucencySort", at = @At("HEAD"), cancellable = true)
	private void sparrow_skipTranslucencySort(ChunkBuilder.BuiltChunk chunk, NormalizedRelativePos pos, Vec3d vec3d, boolean bl, boolean bl2, CallbackInfo ci) {
		int transSkip = getTransSkipBlocks();
		if (transSkip <= 0) return;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		BlockPos origin = chunk.getOrigin();
		double dx = origin.getX() - client.player.getX();
		double dz = origin.getZ() - client.player.getZ();
		double distSq = dx * dx + dz * dz;

		if (distSq >= (double) transSkip * transSkip) {
			if (!sparrow_lodLogged) {
				sparrow_lodLogged = true;
				SparrowLogger.debug("DistantChunkLodMixin: translucency skip at " + transSkip + " blocks (mode=" + Modules.blockLodMode.stringValue() + ")");
			}
			ci.cancel();
		}
	}

	// ── Inject 2: chunk render cancel (full cull) ───────────────────

	@Inject(method = "scheduleChunkRender(IIIZ)V", at = @At("HEAD"), cancellable = true)
	private void sparrow_cullDistantChunk(int x, int y, int z, boolean important, CallbackInfo ci) {
		int renderCull = getRenderCullBlocks();
		if (renderCull <= 0) {
			if (!sparrow_cullLogged) {
				sparrow_cullLogged = true;
				SparrowLogger.debug("DistantChunkLodMixin: cull disabled (mode=" + Modules.blockLodMode.stringValue() + ")");
			}
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;

		// Don't touch Nether — NetherRenderDistanceMixin owns that
		if (client.world != null && client.world.getRegistryKey() == World.NETHER) return;

		double cx = x * 16.0 + 8.0;
		double cz = z * 16.0 + 8.0;
		double dx = cx - client.player.getX();
		double dz = cz - client.player.getZ();
		double distSq = dx * dx + dz * dz;

		if (!sparrow_cullLogged) {
			sparrow_cullLogged = true;
			SparrowLogger.debug("DistantChunkLodMixin: active, cull=" + renderCull + " blocks, mode=" + Modules.blockLodMode.stringValue());
		}

		if (distSq >= (double) renderCull * renderCull) {
			ci.cancel();
		}
	}

	// ── Helpers ─────────────────────────────────────────────────────

	@Unique
	private static int getTransSkipBlocks() {
		return switch (Modules.blockLodMode.stringValue()) {
			case "LOW"       -> LOW_TRANS;
			case "PVP"       -> PVP_TRANS;
			case "AGGRESSIVE"-> AGGRESSIVE_TRANS;
			default          -> OFF_TRANS;
		};
	}

	@Unique
	private static int getRenderCullBlocks() {
		return switch (Modules.blockLodMode.stringValue()) {
			case "LOW"       -> LOW_CULL;
			case "PVP"       -> PVP_CULL;
			case "AGGRESSIVE"-> AGGRESSIVE_CULL;
			default          -> OFF_CULL;
		};
	}
}
