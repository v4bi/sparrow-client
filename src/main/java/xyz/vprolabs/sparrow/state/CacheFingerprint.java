package xyz.vprolabs.sparrow.state;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

/**
 * Atlas-cache validity fingerprint (2026-08-09). Deciding whether the
 * atlas disk cache may be reused must cost O(pack count), never O(texture
 * count): hashing the ~4000 texture files would be as expensive as loading
 * them, which defeats the cache entirely.
 *
 * A fingerprint is a list of per-pack stamps plus the invariants that
 * change atlas output without touching pack files (Minecraft version and
 * mipmap level). Two fingerprints equal -> the cached atlases are
 * byte-identical to what a fresh vanilla reload would produce, so the
 * reload can be skipped.
 *
 * Pack stamp semantics (built by AtlasCacheMixin, this class only
 * compares):
 *  - Zip packs: ONE stamp per pack file (path, size, mtime, crc32c).
 *    Any texture edit forces a re-zip -> size/mtime/crc change.
 *  - Folder packs: ONE stamp per file inside the pack (id = packId + "/" +
 *    relative path, size + mtime). Directory mtime does NOT bump on
 *    in-place byte edits, so per-file stamps are required for folders.
 *  - Mod jars (ModNioPackResources): ONE stamp per RESOURCE ENTRY, taken
 *    from the jar's zip central directory (name, size, crc). META-INF and
 *    .class entries are excluded: the manifest carries the per-build
 *    Build-Tag, which changes on every build without touching atlas output,
 *    and classes never affect textures. This replaced the buildTag
 *    fingerprint component (2026-08-09): the old tag-based stamp made every
 *    rebuild a cache miss and a re-capture ("always cache-miss").
 *  - Non-file packs (vanilla jar, unknown mod origins): an id-only stamp
 *    with size 0; vanilla is covered by mcVersion, unknown origins are a
 *    documented residual risk (mod content changes would not invalidate).
 *
 * Legacy note: pre-2026-08-09 fingerprints carried a buildTag field. The
 * field is dropped from this class; Gson ignores the stale JSON field and
 * the remaining components (mcVersion, mip, packs) still compare, so old
 * caches remain valid.
 */
public final class CacheFingerprint {

    public static final class PackStamp {
        public String id;      // pack id, or packId + "/" + relPath for folder pack files
        public String path;    // file path the stamp was taken from (empty for id-only)
        public long size;      // bytes, 0 for id-only stamps
        public long mtime;     // last-modified millis, 0 for id-only stamps
        public long crc32c;    // CRC32C of file bytes, 0 for folder files and id-only stamps

        public PackStamp() { } // Gson

        public PackStamp(String id, String path, long size, long mtime, long crc32c) {
            this.id = id;
            this.path = path;
            this.size = size;
            this.mtime = mtime;
            this.crc32c = crc32c;
        }
    }

    public String mcVersion;
    public int mipLevels;
    public List<PackStamp> packs = new ArrayList<>();

    private static final Gson GSON = new Gson();

    public CacheFingerprint() { } // Gson

    public CacheFingerprint(String mcVersion, int mipLevels) {
        this.mcVersion = mcVersion;
        this.mipLevels = mipLevels;
    }

    public void addPack(PackStamp stamp) {
        packs.add(stamp);
    }

    /** Two fingerprints match iff version, mip level AND every pack
     *  stamp (id, path, size, mtime, crc) are identical, in the same order. */
    public boolean matches(CacheFingerprint other) {
        if (other == null) return false;
        if (!mcVersion.equals(other.mcVersion)) return false;
        if (mipLevels != other.mipLevels) return false;
        if (packs.size() != other.packs.size()) return false;
        for (int i = 0; i < packs.size(); i++) {
            PackStamp a = packs.get(i);
            PackStamp b = other.packs.get(i);
            if (!a.id.equals(b.id)) return false;
            if (!a.path.equals(b.path)) return false;
            if (a.size != b.size) return false;
            if (a.mtime != b.mtime) return false;
            if (a.crc32c != b.crc32c) return false;
        }
        return true;
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static CacheFingerprint fromJson(String json) {
        try {
            return GSON.fromJson(json, CacheFingerprint.class);
        } catch (Exception e) {
            return null; // corrupted json -> cache invalid, vanilla reload
        }
    }
}
