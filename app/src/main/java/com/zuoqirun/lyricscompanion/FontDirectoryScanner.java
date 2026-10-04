package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class FontDirectoryScanner {
    private static final int MAX_ENTRIES = 5000;
    private static final int MAX_FONTS = 200;

    private FontDirectoryScanner() { }

    static final class FontFile {
        final String name;
        final Uri uri;

        FontFile(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
        }
    }

    static boolean isFontName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc");
    }

    static List<File> findFiles(File root) throws IOException {
        List<File> result = new ArrayList<>();
        if (!root.isDirectory() || !root.canRead()) throw new IOException("目录不可读");
        ArrayDeque<File> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        String rootPath = root.getCanonicalPath();
        pending.add(root);
        int count = 0;
        while (!pending.isEmpty() && count < MAX_ENTRIES && result.size() < MAX_FONTS) {
            File directory = pending.removeFirst();
            String canonical = directory.getCanonicalPath();
            if (!visited.add(canonical)) continue;
            File[] children = directory.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (++count > MAX_ENTRIES || result.size() >= MAX_FONTS) break;
                String childPath = child.getCanonicalPath();
                if (!childPath.startsWith(rootPath + File.separator)) continue;
                if (child.isDirectory()) pending.addLast(child);
                else if (child.isFile() && child.canRead() && isFontName(child.getName())) result.add(child);
            }
        }
        return result;
    }

    static List<FontFile> scanFiles(File root) throws IOException {
        List<FontFile> result = new ArrayList<>();
        for (File file : findFiles(root)) result.add(new FontFile(file.getName(), Uri.fromFile(file)));
        return result;
    }

    static List<FontFile> scanTree(Context context, String storedUri) throws IOException {
        if (Build.VERSION.SDK_INT < 21 || storedUri == null || storedUri.isEmpty()) {
            throw new IOException("请先授权本地歌词目录");
        }
        Uri tree = Uri.parse(storedUri);
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(DocumentsContract.getTreeDocumentId(tree));
        List<FontFile> result = new ArrayList<>();
        int count = 0;
        while (!pending.isEmpty() && count < MAX_ENTRIES && result.size() < MAX_FONTS) {
            String id = pending.removeFirst();
            if (!visited.add(id)) continue;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
            try (Cursor cursor = context.getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor == null) continue;
                while (cursor.moveToNext() && ++count <= MAX_ENTRIES && result.size() < MAX_FONTS) {
                    String childId = cursor.getString(0);
                    String name = cursor.getString(1);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2))) {
                        pending.addLast(childId);
                    } else if (isFontName(name)) {
                        result.add(new FontFile(name, DocumentsContract.buildDocumentUriUsingTree(tree, childId)));
                    }
                }
            }
        }
        return result;
    }
}
