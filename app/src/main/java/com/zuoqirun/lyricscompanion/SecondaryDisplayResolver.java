package com.zuoqirun.lyricscompanion;

import android.hardware.display.DisplayManager;
import android.view.Display;

/** Keeps a remembered display preferred, but survives virtual display ID replacement. */
final class SecondaryDisplayResolver {
    private SecondaryDisplayResolver() { }

    static Display resolve(DisplayManager manager, int preferredId) {
        if (manager == null) return null;
        Display[] presentations = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        Display[] all = manager.getDisplays();
        int selected = selectId(preferredId, ids(presentations), ids(all));
        return selected < 0 ? null : manager.getDisplay(selected);
    }

    static int selectId(int preferredId, int[] presentations, int[] all) {
        if (preferredId > 0) for (int id : all) if (id == preferredId) return id;
        for (int id : presentations) if (id > 0) return id;
        for (int id : all) if (id > 0) return id;
        return -1;
    }

    private static int[] ids(Display[] displays) {
        int[] result = new int[displays.length];
        for (int i = 0; i < displays.length; i++) {
            result[i] = displays[i] == null || !displays[i].isValid() ? -1 : displays[i].getDisplayId();
        }
        return result;
    }
}
