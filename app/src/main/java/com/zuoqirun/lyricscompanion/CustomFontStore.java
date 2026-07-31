package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;

/** Stores a user-selected lyric font in private app storage so overlays outlive URI grants. */
final class CustomFontStore {
    private static final String DIRECTORY = "lyrics-fonts";
    private static final long MAX_FONT_BYTES = 24L * 1024L * 1024L;
    private static final String FONT_FILE_NAME = "lyrics-custom-font";
    private static final int SFNT_TRUETYPE = 0x00010000;
    private static final int SFNT_TRUE = 0x74727565;
    private static final int SFNT_TYP1 = 0x74797031;
    private static final int SFNT_OTTO = 0x4F54544F;
    private static final int TTCF = 0x74746366;
    private static String cachedFileName = "";
    private static Typeface cachedTypeface;

    private CustomFontStore() {}

    static String importFont(Context context, Uri uri) throws IOException {
        String displayName = displayName(context, uri);
        String extension = extension(displayName);
        if (!isSupportedExtension(extension)) {
            throw new IOException("请选择 .ttf、.otf 或 .ttc 字体文件");
        }
        File directory = new File(context.getFilesDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建字体存储目录");
        }
        String targetName = FONT_FILE_NAME + "." + extension;
        File temporary = new File(directory, FONT_FILE_NAME + ".pending." + extension);
        File target = new File(directory, targetName);
        try {
            copyWithLimit(context.getContentResolver().openInputStream(uri), temporary);
            if (!isRecognizedFontFile(temporary)) {
                throw new IOException("该文件不是完整的 TrueType、OpenType 或字体集合文件");
            }
            if (target.exists() && !target.delete()) {
                throw new IOException("无法替换原来的字体文件");
            }
            if (!temporary.renameTo(target)) throw new IOException("无法保存字体文件");
            removeOtherFonts(directory, targetName);
            AppPreferences.setCustomFontFile(context, targetName);
            // Do not retain a Typeface created from a temporary path. Older Android builds may
            // defer native font reads until drawing, after that temporary file has been removed.
            cachedFileName = "";
            cachedTypeface = null;
            if (load(context) == null) {
                clear(context);
                throw new IOException("车机无法加载该字体，已恢复系统字体");
            }
            return displayName;
        } catch (RuntimeException error) {
            throw new IOException("该文件不是可用的 Android 字体", error);
        } finally {
            if (temporary.exists()) temporary.delete();
        }
    }

    static Typeface load(Context context) {
        String storedName = safeFileName(AppPreferences.customFontFile(context));
        if (storedName.isEmpty()) return null;
        if (storedName.equals(cachedFileName) && cachedTypeface != null) return cachedTypeface;
        File file = new File(new File(context.getFilesDir(), DIRECTORY), storedName);
        if (!isSupportedExtension(extension(storedName))
                || !file.isFile() || !isRecognizedFontFile(file)) {
            clearInvalidFont(context, file);
            return null;
        }
        try {
            Typeface typeface = Typeface.createFromFile(file);
            cachedFileName = storedName;
            cachedTypeface = typeface;
            return typeface;
        } catch (RuntimeException ignored) {
            clearInvalidFont(context, file);
            return null;
        }
    }

    static String selectedFontLabel(Context context) {
        String storedName = safeFileName(AppPreferences.customFontFile(context));
        return storedName.isEmpty() ? "系统默认字体" : storedName;
    }

    static void clear(Context context) {
        String storedName = safeFileName(AppPreferences.customFontFile(context));
        if (!storedName.isEmpty()) {
            File file = new File(new File(context.getFilesDir(), DIRECTORY), storedName);
            if (file.isFile()) file.delete();
        }
        AppPreferences.setCustomFontFile(context, "");
        cachedFileName = "";
        cachedTypeface = null;
    }

    /** Applies the selected typeface to ordinary Android text widgets in each settings screen. */
    static void applyToViewTree(Context context, View view) {
        Typeface typeface = load(context);
        if (typeface == null || view == null) return;
        if (view instanceof TextView) {
            Typeface existing = ((TextView) view).getTypeface();
            int style = existing == null ? Typeface.NORMAL : existing.getStyle();
            ((TextView) view).setTypeface(Typeface.create(typeface, style));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToViewTree(context, group.getChildAt(i));
            }
        }
    }

    private static void copyWithLimit(InputStream input, File destination) throws IOException {
        if (input == null) throw new IOException("无法读取所选字体文件");
        long copied = 0L;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream source = input; OutputStream output = new FileOutputStream(destination)) {
            int count;
            while ((count = source.read(buffer)) != -1) {
                copied += count;
                if (copied > MAX_FONT_BYTES) throw new IOException("字体文件不能超过 24 MB");
                output.write(buffer, 0, count);
            }
        }
    }

    private static String displayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (RuntimeException ignored) {
            // Fall through to the last URI segment.
        } finally {
            if (cursor != null) cursor.close();
        }
        String segment = uri.getLastPathSegment();
        return segment == null ? "font.ttf" : segment;
    }

    private static void removeOtherFonts(File directory, String keepName) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.getName().equals(keepName) && file.isFile()) file.delete();
        }
    }

    private static String extension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isSupportedExtension(String extension) {
        return "ttf".equals(extension) || "otf".equals(extension) || "ttc".equals(extension);
    }

    /**
     * Filters truncated or falsely named files before they reach Typeface. Android 11 supports
     * TrueType, OpenType/CFF and TrueType collections, including variable fonts.
     */
    static boolean isRecognizedFontFile(File file) {
        if (file == null || !file.isFile() || file.length() < 12 || file.length() > MAX_FONT_BYTES) {
            return false;
        }
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            int signature = input.readInt();
            if (signature != TTCF) return isSaneSfnt(input, 0L, file.length());
            input.readInt(); // TTC version
            long fontCount = input.readInt() & 0xFFFFFFFFL;
            if (fontCount < 1 || fontCount > 64) return false;
            for (int index = 0; index < fontCount; index++) {
                input.seek(12L + index * 4L);
                long offset = input.readInt() & 0xFFFFFFFFL;
                if (!isSaneSfnt(input, offset, file.length())) return false;
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isSaneSfnt(RandomAccessFile input, long start, long fileLength)
            throws IOException {
        if (start < 0 || start > fileLength - 12) return false;
        input.seek(start);
        int version = input.readInt();
        if (version != SFNT_TRUETYPE && version != SFNT_TRUE
                && version != SFNT_TYP1 && version != SFNT_OTTO) return false;
        int tableCount = input.readUnsignedShort();
        if (tableCount < 1 || tableCount > 256 || start > fileLength - 12L - tableCount * 16L) {
            return false;
        }
        input.skipBytes(6);
        for (int index = 0; index < tableCount; index++) {
            input.readInt(); // tag
            input.readInt(); // checksum
            long offset = input.readInt() & 0xFFFFFFFFL;
            long length = input.readInt() & 0xFFFFFFFFL;
            if (offset > fileLength || length > fileLength - offset) return false;
        }
        return true;
    }

    private static void clearInvalidFont(Context context, File file) {
        if (file.isFile()) file.delete();
        AppPreferences.setCustomFontFile(context, "");
        cachedFileName = "";
        cachedTypeface = null;
    }

    private static String safeFileName(String value) {
        if (value == null || value.isEmpty()) return "";
        return new File(value).getName().equals(value) ? value : "";
    }
}
