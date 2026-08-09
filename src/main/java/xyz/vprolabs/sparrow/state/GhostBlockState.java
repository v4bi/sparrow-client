package xyz.vprolabs.sparrow.state;

import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

public final class GhostBlockState {
    public static final Map<BlockPos, Long> ghostBlocks = new HashMap<>();
    private static final long GHOST_DISPLAY_MS = 2000;

    private GhostBlockState() {}

    public static void markGhost(BlockPos pos) {
        ghostBlocks.put(pos, System.currentTimeMillis());
    }

    public static void tick() {
        if (ghostBlocks.isEmpty()) return;
        long now = System.currentTimeMillis();
        // Explicit iterator: removeIf's predicate lambda is a synthetic
        // allocation per call, and tick() runs EVERY HUD frame. An iterator
        // loop with iterator.remove() is allocation-free and performs the
        // same single-pass removal. Rejected: rebuilding the map — pointless
        // re-allocation of the whole HashMap for a few expired entries.
        Iterator<Entry<BlockPos, Long>> it = ghostBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Entry<BlockPos, Long> e = it.next();
            if (now - e.getValue() > GHOST_DISPLAY_MS) it.remove();
        }
    }

}
