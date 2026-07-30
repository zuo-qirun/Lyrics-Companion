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
import java.util.Locale;

/** Stores a user-selected lyric font in private app storage so overlays outlive URI grants. */
final class CustomFontStore {
    private static final String DIRECTORY = "lyrics-fonts";
    private static final long MAX_FONT_BYTES = 24L * 1024L * 1024L;
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
        String targetName = "lyrics-custom-font." + extension;
        File temporary = new File(directory, targetName + ".tmp");
        File target = new File(directory, targetName);
        try {
            copyWithLimit(context.getContentResolver().openInputStream(uri), temporary);
            Typeface typeface = Typeface.createFromFile(temporary);
            if (target.exists() && !target.delete()) {
                throw new IOException("无法替换原来的字体文件");
            }
            if (!temporary.renameTo(target)) throw new IOException("无法保存字体文件");
            removeOtherFonts(directory, targetName);
            AppPreferences.setCustomFontFile(context, targetName);
            cachedFileName = targetName;
            cachedTypeface = typeface;
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
        if (!file.isFile()) return null;
        try {
            Typeface typeface = Typeface.createFromFile(file);
            cachedFileName = storedName;
            cachedTypeface = typeface;
            return typeface;
        } catch (RuntimeException ignored) {
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

    private static String safeFileName(String value) {
        if (value == null || value.isEmpty()) return "";
        return new File(value).getName().equals(value) ? value : "";
    }
}
