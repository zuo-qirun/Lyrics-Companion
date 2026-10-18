package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.os.Debug;

/** Runtime limits only; stored layout, color and effect preferences stay intact. */
final class ResourceProfile {
    private ResourceProfile() { }
    static int artSide(boolean low) { return low ? 350 : 700; }
    static int artCacheKiB(boolean low) { return low ? 1024 : 4 * 1024; }
    static int maskCacheBytes(boolean low) { return low ? 512 * 1024 : 2 * 1024 * 1024; }
    static long frameDelay(boolean low, long requestedMs) { return low ? Math.max(84, requestedMs) : requestedMs; }

    static String describe(Context context) {
        boolean low = AppPreferences.lowResourceMode(context);
        Runtime runtime = Runtime.getRuntime();
        return "性能档位：" + (low ? "低配（全屏幕，约 12 fps 上限）" : "标准")
                + "\nJava 堆：" + (runtime.totalMemory() - runtime.freeMemory()) / 1048576
                + " / " + runtime.maxMemory() / 1048576 + " MiB；原生堆："
                + Debug.getNativeHeapAllocatedSize() / 1048576 + " MiB"
                + "\n封面缓存：" + AlbumArtLoader.cachedKiB() + " / " + artCacheKiB(low)
                + " KiB；Folia 遮罩预算：" + maskCacheBytes(low) / 1024 + " KiB/面板";
    }
}
