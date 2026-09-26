package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The extra screens the user asked to show lyrics on, in the order that fixes their slot.
 *
 * <p>Slots 0 and 1 are the screens this app has always had: the main overlay on the default
 * display, and the secondary one picked on the 副屏 page. Everything after that lives here, one
 * entry per additional display, so a driving display and a HUD can be configured separately and
 * shown at the same time (issue #17). Each entry's position in this list is its slot, which is
 * what its own preference file is named after — a screen that is temporarily unplugged therefore
 * keeps its settings and does not shift anyone else's.
 *
 * <p>Entries carry both the display name and its id: ids are reassigned across reboots and
 * re-plugs, while the name is what the user recognises in the picker.
 */
final class DisplaySlotRegistry {
    /** Slot of the overlay on the default display. */
    static final int MAIN_SLOT = 0;
    /** Slot of the single secondary display the app has always supported. */
    static final int SECONDARY_SLOT = 1;
    /** Slot of the first extra screen; the n-th entry in the list is {@code n + 2}. */
    static final int FIRST_EXTRA_SLOT = 2;
    private static final char FIELD_SEPARATOR = '\u0001';
    private static final String ENTRY_SEPARATOR = "\n";

    private DisplaySlotRegistry() {}

    static int slotFor(int index) {
        return FIRST_EXTRA_SLOT + Math.max(0, index);
    }

    /** Ordered list of extra screens; entry {@code i} owns {@link #slotFor(int) slot i + 2}. */
    static List<Entry> entries(Context context) {
        return decode(AppPreferences.get(context)
                .getString(AppPreferences.KEY_EXTRA_DISPLAYS, ""));
    }

    static void putEntries(Context context, List<Entry> entries) {
        AppPreferences.get(context).edit()
                .putString(AppPreferences.KEY_EXTRA_DISPLAYS, encode(entries)).apply();
    }

    static String encode(List<Entry> entries) {
        StringBuilder encoded = new StringBuilder();
        if (entries != null) {
            for (Entry entry : entries) {
                if (entry == null) continue;
                if (encoded.length() > 0) encoded.append(ENTRY_SEPARATOR);
                encoded.append(entry.encode());
            }
        }
        return encoded.toString();
    }

    static List<Entry> decode(String stored) {
        if (stored == null || stored.trim().isEmpty()) return Collections.emptyList();
        List<Entry> entries = new ArrayList<>();
        for (String line : stored.split(ENTRY_SEPARATOR)) {
            Entry entry = Entry.decode(line);
            if (entry != null) entries.add(entry);
        }
        return entries;
    }

    static Entry of(Display display) {
        return display == null ? null : new Entry(display.getName(), display.getDisplayId());
    }

    /**
     * The live display behind an entry, or {@code null} while that screen is not connected.
     *
     * <p>名字优先是有原因的（车机 HUD 输入的 id 重启后会变），但**同名屏**上名字没有区分能力：
     * 哈弗 H6 会同时挂两块都叫「HDMI 屏幕」的面板（id 1 / id 2），按名字找永远返回第一块，第二块
     * 便永远拿不到悬浮窗（issue #71）。所以名字唯一时才按名字走，同名时改用 id。
     */
    static Display resolve(Entry entry, DisplayManager manager) {
        if (entry == null || manager == null) return null;
        String[] names = {};
        int[] ids = {};
        java.util.List<Display> candidates = new ArrayList<>();
        for (Display display : manager.getDisplays()) {
            if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY) continue;
            candidates.add(display);
        }
        names = new String[candidates.size()];
        ids = new int[candidates.size()];
        for (int index = 0; index < candidates.size(); index++) {
            names[index] = candidates.get(index).getName();
            ids[index] = candidates.get(index).getDisplayId();
        }
        int chosen = chooseIndex(entry.name, entry.displayId, names, ids);
        return chosen < 0 ? null : candidates.get(chosen);
    }

    /**
     * 按「名字 + id」挑一块屏（纯函数，便于测试）：
     * 名字与 id 都对得上 → 就是它；名字唯一 → 按名字（老行为，id 不稳的车型不受影响）；
     * 名字重复（没有区分能力）→ 按 id；id 也不在 → 退回第一块同名的。
     *
     * @return 下标，找不到返回 {@code -1}
     */
    static int chooseIndex(String entryName, int entryId, String[] names, int[] ids) {
        if (names == null || ids == null || names.length == 0) return -1;
        String name = entryName == null ? "" : entryName;
        int sameNameCount = 0;
        int firstSameName = -1;
        int exact = -1;
        for (int index = 0; index < names.length && index < ids.length; index++) {
            if (!name.isEmpty() && name.equals(names[index])) {
                if (firstSameName < 0) firstSameName = index;
                sameNameCount++;
                if (ids[index] == entryId) exact = index;
            }
        }
        if (exact >= 0) return exact;
        if (sameNameCount == 1) return firstSameName;
        if (sameNameCount > 1) {
            // 同名多块：名字帮不上忙，按 id 找；找不到才退回第一块同名的。
            for (int index = 0; index < names.length && index < ids.length; index++) {
                if (ids[index] == entryId) return index;
            }
            return firstSameName;
        }
        for (int index = 0; index < names.length && index < ids.length; index++) {
            if (ids[index] == entryId) return index;
        }
        return -1;
    }

    /**
     * User-facing name of a slot's screen for the settings page title: 主屏, 副屏, or the extra
     * screen's own display name while it is connected.
     */
    static String slotLabel(Context context, int slot) {
        if (slot <= MAIN_SLOT) return "主屏";
        if (slot == SECONDARY_SLOT) return "副屏";
        int index = slot - FIRST_EXTRA_SLOT;
        List<Entry> entries = entries(context);
        String name = index >= 0 && index < entries.size() ? entries.get(index).name : "";
        return name.isEmpty() ? "屏幕 " + slot : "屏幕 " + slot + "（" + name + "）";
    }

    /** One extra screen, identified by the name the user sees and the id it had when picked. */
    static final class Entry {
        final String name;
        final int displayId;
        /** False keeps the entry — and therefore every other slot's number — while hiding it. */
        final boolean enabled;

        Entry(String name, int displayId) {
            this(name, displayId, true);
        }

        Entry(String name, int displayId, boolean enabled) {
            this.name = name == null ? "" : name.trim();
            this.displayId = displayId;
            this.enabled = enabled;
        }

        Entry withEnabled(boolean enabled) {
            return new Entry(name, displayId, enabled);
        }

        String describe() {
            return name.isEmpty() ? "ID " + displayId : name + "  ·  ID " + displayId;
        }

        private String encode() {
            return name.replace(FIELD_SEPARATOR, ' ') + FIELD_SEPARATOR + displayId
                    + FIELD_SEPARATOR + (enabled ? "1" : "0");
        }

        private static Entry decode(String line) {
            if (line == null) return null;
            String[] fields = line.split(String.valueOf(FIELD_SEPARATOR), -1);
            String name = fields.length > 0 ? fields[0].trim() : "";
            int id = fields.length > 1 ? parseInt(fields[1]) : -1;
            // Entries written before extra screens could be switched off have no third field.
            boolean enabled = fields.length < 3 || !"0".equals(fields[2].trim());
            if (name.isEmpty() && id < 0) return null;
            return new Entry(name, id, enabled);
        }

        private static int parseInt(String value) {
            try {
                return Integer.parseInt(value == null ? "" : value.trim());
            } catch (NumberFormatException error) {
                return -1;
            }
        }
    }
}
