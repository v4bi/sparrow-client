package xyz.vprolabs.sparrow.state;

import xyz.vprolabs.sparrow.logging.SparrowLogger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32C;

/**
 * Disk IO for the atlas disk cache (DESIGN.md, Phase 2). Blobs are RAW RGBA
 * (custom .spa header + pixels): zero encode/decode cost, instant read. PNG
 * was rejected because encoding costs ~1-2s per atlas and decoding would eat
 * the win on this box (DESIGN.md "Cache format" decision).
 *
 * Commit protocol: blobs are written first, cache.json LAST (writeJson).
 * A partial write set can then never validate as complete, because readJson()
 * only succeeds when cache.json exists and allBlobsPresent() only checks the
 * atlases it declares. Every blob write goes to a .tmp file followed by an
 * atomic rename, so a half-written blob is never visible to a reader.
 *
 * Read-failure contract: EVERY read path returns null on any problem (missing
 * file, corrupt header, CRC mismatch, dim mismatch vs cache.json). The caller
 * then falls back to a full vanilla reload. Cache problems never crash the
 * game and never throw; writes log a warning and leave the old cache intact.
 */
public final class AtlasCache {

    /** Per-sprite metadata as stored in cache.json. */
    public static final class SpriteMeta {
        public String id;          // sprite Identifier string "namespace:path"
        public int x, y, w, h;     // UV rect within the atlas base image
        public int frameCount;     // animation frames (1 = static)
        public int frameTime;      // tick time per frame (0 for static)

        public SpriteMeta() { }    // Gson

        public SpriteMeta(String id, int x, int y, int w, int h, int frameCount, int frameTime) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.frameCount = frameCount;
            this.frameTime = frameTime;
        }
    }

    /** Per-atlas metadata as stored in cache.json. */
    public static final class AtlasMeta {
        public String atlasId;     // "textures/block" style id
        public int width, height;  // atlas base image size
        public List<SpriteMeta> sprites = new ArrayList<>();

        public AtlasMeta() { }     // Gson
    }

    /** The entire cache.json document. */
    public static final class CacheData {
        public CacheFingerprint fingerprint;
        public List<AtlasMeta> atlases = new ArrayList<>();

        public CacheData() { }     // Gson
    }

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final byte[] MAGIC = {'S', 'P', 'A', 'R'};
    private static final int BLOB_VERSION = 1;
    // magic(4) + version(4) + width(4) + height(4) + crc32c(8)
    private static final int HEADER_BYTES = 24; // 4 magic + 4 version + 4 w + 4 h + 8 crc
    private static final String JSON_NAME = "cache.json";
    private static final String BLOB_EXT = ".spa";

    // Vanilla never produces a base atlas larger than 16384 and only a corrupt
    // header could claim more. Cap dims before allocating so a damaged blob can
    // never trigger a giant allocation (never crash the game on cache problems).
    private static final int MAX_DIM = 32768;

    /** game/atlas-cache/v1 under the Minecraft run directory. */
    public static Path cacheDir() {
        // runDirectory is the authoritative game dir. user.dir is the same
        // directory at runtime, kept as a fallback so this path can never
        // NPE the game if the client is not constructed yet.
        MinecraftClient client = MinecraftClient.getInstance();
        File run = (client != null && client.runDirectory != null)
                ? client.runDirectory
                : new File(System.getProperty("user.dir"));
        return run.toPath().resolve("atlas-cache").resolve("v1");
    }

    /**
     * Reads cache.json. Null if the file is missing, unparseable, or parses
     * to a document without fingerprint/atlases (partial or hand-corrupted).
     */
    public static CacheData readJson() {
        Path json = cacheDir().resolve(JSON_NAME);
        if (!Files.isRegularFile(json)) return null;
        try {
            CacheData data = PRETTY_GSON.fromJson(Files.readString(json, StandardCharsets.UTF_8), CacheData.class);
            // A json that parses but carries no fingerprint is not a valid
            // cache: treat as missing so the caller does a vanilla reload.
            if (data == null || data.fingerprint == null || data.atlases == null) return null;
            return data;
        } catch (Exception e) {
            return null; // corrupt json -> cache invalid, vanilla reload
        }
    }

    /**
     * Writes cache.json. Callers MUST write all blobs first, then this last:
     * the file only exists once the whole set is on disk, so a partial write
     * set is never validated as complete by readJson/allBlobsPresent.
     */
    public static void writeJson(CacheData data) {
        if (data == null) return;
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(JSON_NAME);
            // temp + atomic move: a reader can never observe a half-written
            // cache.json (which would otherwise look like a valid cache).
            Path tmp = Files.createTempFile(dir, "cache", ".tmp");
            Files.writeString(tmp, PRETTY_GSON.toJson(data), StandardCharsets.UTF_8);
            moveAtomic(tmp, target);
        } catch (IOException e) {
            SparrowLogger.warn("AtlasCache: failed to write " + JSON_NAME + ": " + e.getMessage());
        }
    }

    /**
     * Reads and validates the .spa blob for an atlas id. Null on ANY problem:
     * missing file, wrong magic/version, size or CRC mismatch, dims that
     * contradict cache.json, or any exception while filling the image.
     */
    public static NativeImage readBlob(String atlasId) {
        Path file = blobPath(atlasId);
        if (!Files.isRegularFile(file)) return null;
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return null;
        }
        if (bytes.length < HEADER_BYTES) return null;
        if (bytes[0] != MAGIC[0] || bytes[1] != MAGIC[1] || bytes[2] != MAGIC[2] || bytes[3] != MAGIC[3]) return null;
        ByteBuffer header = ByteBuffer.wrap(bytes, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        int version = header.getInt(4);
        int width = header.getInt(8);
        int height = header.getInt(12);
        long crc = header.getLong(16);
        if (version != BLOB_VERSION) return null;
        if (width <= 0 || height <= 0 || width > MAX_DIM || height > MAX_DIM) return null;
        // long math: a corrupt header must not overflow int and bypass the
        // exact-size check (the only guard against a mismatched pixel block).
        long expected = HEADER_BYTES + (long) width * height * 4;
        if (bytes.length != expected) return null;
        CRC32C crc32c = new CRC32C();
        crc32c.update(bytes, HEADER_BYTES, bytes.length - HEADER_BYTES);
        if (crc32c.getValue() != crc) return null;
        // The blob's dims must agree with the metadata that describes its
        // sprites; a contradiction means blob and json came from different
        // cache generations, and the UVs would be wrong -> vanilla reload.
        if (!dimsMatchMeta(atlasId, width, height)) return null;

        NativeImage image = null;
        try {
            image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int o = HEADER_BYTES + (y * width + x) * 4;
                    int argb = ((bytes[o + 3] & 0xFF) << 24)   // A
                            | ((bytes[o] & 0xFF) << 16)        // R
                            | ((bytes[o + 1] & 0xFF) << 8)     // G
                            | (bytes[o + 2] & 0xFF);           // B
                    image.setColorArgb(x, y, argb);
                }
            }
            return image;
        } catch (Exception e) {
            if (image != null) image.close();
            return null;
        }
    }

    /**
     * Writes the .spa blob for an atlas id from the atlas base image.
     * width/height are the atlas base dims the caller captured; if they do not
     * match the image, nothing is written (the blob would be unreadable).
     */
    public static void writeBlob(String atlasId, NativeImage base, int width, int height) {
        // Header dims must match the pixels actually captured, or the blob
        // would fail its own size/CRC check on the next boot and waste the
        // read. A mismatch here means a caller bug: skip rather than persist
        // a guaranteed-corrupt blob.
        if (base.getWidth() != width || base.getHeight() != height) {
            SparrowLogger.warn("AtlasCache: skipping " + atlasId + ": image is " + base.getWidth() + "x"
                    + base.getHeight() + " but declared " + width + "x" + height);
            return;
        }
        int[] argb = base.copyPixelsArgb();
        int count = width * height;
        byte[] rgba = new byte[count * 4];
        for (int i = 0; i < count; i++) {
            int c = argb[i];
            int o = i * 4;
            rgba[o] = (byte) (c >> 16);      // R
            rgba[o + 1] = (byte) (c >> 8);   // G
            rgba[o + 2] = (byte) c;          // B
            rgba[o + 3] = (byte) (c >> 24);  // A
        }
        CRC32C crc = new CRC32C();
        crc.update(rgba, 0, rgba.length);
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
        header.put(MAGIC);
        header.putInt(BLOB_VERSION);
        header.putInt(width);
        header.putInt(height);
        header.putLong(crc.getValue());
        byte[] blob = new byte[HEADER_BYTES + rgba.length];
        System.arraycopy(header.array(), 0, blob, 0, HEADER_BYTES);
        System.arraycopy(rgba, 0, blob, HEADER_BYTES, rgba.length);
        try {
            Path dir = cacheDir();
            Files.createDirectories(dir);
            Path target = blobPath(atlasId);
            // temp + atomic move: a partially written blob must never be
            // readable by a concurrent or next-boot reader.
            Path tmp = Files.createTempFile(dir, "blob", ".tmp");
            Files.write(tmp, blob);
            moveAtomic(tmp, target);
        } catch (IOException e) {
            SparrowLogger.warn("AtlasCache: failed to write blob " + atlasId + ": " + e.getMessage());
        }
    }

    /** True iff every atlas declared in the cache has a blob file on disk. */
    public static boolean allBlobsPresent(CacheData data) {
        // An empty atlas list is NOT a valid cache: a capture that recorded
        // nothing would otherwise validate trivially (loop over zero entries
        // returns true), and prepareTail's sparrow_cacheData!=null early
        // return would then suppress all future captures forever. Reject the
        // empty state so a broken capture always re-runs (found 2026-08-09:
        // the first capture wrote a 0-atlas cache.json that self-locked the
        // cache into never capturing again).
        if (data == null || data.atlases == null || data.atlases.isEmpty()) return false;
        for (AtlasMeta meta : data.atlases) {
            if (meta == null || meta.atlasId == null) return false;
            if (!Files.isRegularFile(blobPath(meta.atlasId))) return false;
        }
        return true;
    }

    /** Blob file path for an atlas id. */
    private static Path blobPath(String atlasId) {
        // '/' in atlas ids ("textures/block") would otherwise create nested
        // directories; flatten to a single flat dir as in the DESIGN.md
        // layout (block.spa, item.spa). Colliding with a literal '_' id is
        // impossible in practice: every atlas id carries a "textures/" prefix.
        return cacheDir().resolve(atlasId.replace('/', '_') + BLOB_EXT);
    }

    /** True iff the blob dims agree with the cache.json entry for the id. */
    private static boolean dimsMatchMeta(String atlasId, int width, int height) {
        CacheData data = readJson();
        if (data == null) return false;
        for (AtlasMeta meta : data.atlases) {
            if (meta == null || meta.atlasId == null) continue;
            if (atlasId.equals(meta.atlasId)) {
                return meta.width == width && meta.height == height;
            }
        }
        return false;
    }

    private static void moveAtomic(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Filesystem without atomic rename (exotic). The tmp file is fully
            // written by now, so plain move still never exposes partial bytes.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
