package com.zuoqirun.lyricscompanion;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a sidecar .lrc without uploading local paths, song names or file contents. */
final class LocalLyricClient {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Pattern AWLRC_TAG = Pattern.compile("(?m)^\\[awlrc:([^]\\r\\n]+)]\\s*$");
    private static final int MAX_DOCUMENTS = 2_000;
    /** Unreadable directories and files are named a few times, then only counted. */
    private static final int MAX_UNREADABLE_LOGS = 3;
    /**
     * Same-name audio files opened by one directory scan when the player gave no usable path. One
     * song can sit in the library twice (two albums, two formats); the cap keeps a weak head unit
     * from reading several 8 MB files in a row.
     */
    private static final int MAX_AUDIO_FILES = 3;
    private final Context context;

    LocalLyricClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /** The lyric found locally, plus which link of the chain produced it. */
    static final class Hit {
        static final Hit EMPTY = new Hit(LrcTimeline.EMPTY, false);
        final LrcTimeline timeline;
        /** True when the text came from an audio file's embedded tag instead of a .lrc file. */
        final boolean embedded;

        Hit(LrcTimeline timeline, boolean embedded) {
            this.timeline = timeline == null ? LrcTimeline.EMPTY : timeline;
            this.embedded = embedded;
        }
    }

    /**
     * Whether a directory path typed by the user can be read on this system right now.
     * Android 4.4..5.1 grants storage at install time, Android 6..10 needs the runtime
     * permission, and Android 11+ blocks non-media files such as .lrc on a raw path.
     */
    static boolean canReadManualDirectory(Context context) {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Build.VERSION.SDK_INT >= 30) return hasAllFilesAccess();
        return hasReadPermission(context);
    }

    /** One Chinese line explaining the current manual directory state, for hints and diagnostics. */
    static String manualDirectoryAccessReason(Context context) {
        if (Build.VERSION.SDK_INT < 23) return "Android 5.1 及以下安装时已授予存储读取权限";
        if (Build.VERSION.SDK_INT >= 30) {
            return hasAllFilesAccess()
                    ? "已授予所有文件访问"
                    : "Android 11 及以上按路径读取 .lrc 需要“所有文件访问”，建议改用“选择本地音乐目录”";
        }
        if (!hasReadPermission(context)) return "未授予存储读取权限";
        return "已授予存储读取权限" + legacyStorageNote();
    }

    /** Dialog note for the manual path entry: what this system still needs before a path works. */
    static String manualDirectoryRequirementNote(Context context) {
        if (canReadManualDirectory(context)) return "";
        if (Build.VERSION.SDK_INT >= 30) {
            return "\n\n当前系统：" + manualDirectoryAccessReason(context)
                    + "；也可以把 .lrc 与歌曲放在同一目录。";
        }
        return "\n\n当前系统：" + manualDirectoryAccessReason(context)
                + "，保存后会向系统申请；若被拒绝，该目录将无法读取，可改用“选择本地音乐目录”。";
    }

    /** Result prompt after saving a path: an unreadable directory must never look usable. */
    static String manualDirectorySaveMessage(Context context) {
        if (canReadManualDirectory(context)) return "已保存手动歌词目录";
        if (Build.VERSION.SDK_INT >= 30) {
            return "已保存，但 Android 11 及以上不允许按路径读取 .lrc 等非媒体文件"
                    + "（需要“所有文件访问”）。请改用“选择本地音乐目录”，或把 .lrc 与歌曲放在同一目录。";
        }
        return "已保存，但当前系统不允许按路径读取该目录（存储读取权限被拒绝）。"
                + "请授予存储读取权限，或改用“选择本地音乐目录”。";
    }

    /**
     * Asks the system for the storage permission the manual lyric directory needs.
     *
     * @return true when a runtime permission dialog was shown; false when this system grants the
     *         permission at install time, already holds it, or is Android 11+ where the dialog
     *         cannot help and the caller only shows an explanation
     */
    static boolean requestManualDirectoryAccess(Activity activity, int requestCode) {
        if (activity == null) return false;
        if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 30) return false;
        if (hasReadPermission(activity)) return false;
        activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                requestCode);
        return true;
    }

    private static boolean hasReadPermission(Context context) {
        if (context == null || Build.VERSION.SDK_INT < 23) return true;
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @android.annotation.TargetApi(30)
    private static boolean hasAllFilesAccess() {
        try {
            return Environment.isExternalStorageManager();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @android.annotation.TargetApi(29)
    private static boolean isLegacyStorage() {
        try {
            return Environment.isExternalStorageLegacy();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Android 10 keeps legacy storage only while requestLegacyExternalStorage is honoured. */
    private static String legacyStorageNote() {
        if (Build.VERSION.SDK_INT != 29) return "";
        return isLegacyStorage() ? "，分区存储已关闭（传统模式）" : "，分区存储已开启";
    }

    /**
     * Reads the local links in order: the .lrc next to the playing file, the lyric tag inside that
     * file, the manual lyric directory, then the authorised SAF directory.
     *
     * <p>Players such as Poweramp never give other apps a readable path to the playing file. When
     * that happens - no mediaUri at all, or one this app may not open - the two directory scans
     * accept audio files next to the .lrc candidates and read their embedded tag, which is the
     * same text the player itself shows.
     */
    Hit load(String mediaUri, String title, String artist) {
        try {
            File sidecar = sidecarFile(mediaUri);
            if (sidecar == null && (title.startsWith("/") || title.startsWith("file://"))) {
                sidecar = sidecarFile(title);
            }
            if (sidecar != null && sidecar.isFile()) {
                LrcTimeline found = parse(new FileInputStream(sidecar));
                if (!found.isEmpty()) return new Hit(found, false);
            }
            if (sidecar != null) {
                File krc = new File(sidecar.getPath().substring(0,
                        sidecar.getPath().length() - 4) + ".krc");
                if (krc.isFile()) {
                    LrcTimeline found = parse(new FileInputStream(krc));
                    if (!found.isEmpty()) return new Hit(found, false);
                }
            }
        } catch (Throwable error) {
            record("歌曲同目录 .lrc 不可读=" + failureCause(error)
                    + " mediaUri=" + mediaUri + " " + accessState());
        }
        EmbeddedProbe probe = readEmbeddedLyric(mediaUri, title);
        if (probe.error != null) {
            record("内嵌歌词读取失败=" + failureCause(probe.error) + " mediaUri=" + mediaUri);
        }
        if (!probe.text.isEmpty()) {
            LrcTimeline found = parseEmbeddedText(probe.text);
            if (!found.isEmpty()) return new Hit(found, true);
        }
        // Only a path we could actually open makes the by-name audio search pointless: a readable
        // file without a lyric tag gains nothing from being found again under its own name.
        boolean findAudioByName = !probe.opened;
        if (findAudioByName) {
            record(TextUtils.isEmpty(mediaUri)
                    ? "播放器未提供 mediaUri，改为在已授权目录里按文件名查找音频文件"
                    : "播放器音频路径不可用 mediaUri=" + mediaUri
                            + "，改为在已授权目录里按文件名查找音频文件");
        }
        Set<String> candidates = candidateNames(mediaUri, title, artist);
        String path = AppPreferences.localLyricDirectoryPath(context);
        if (!path.isEmpty()) {
            try {
                Hit found = searchDirectory(new File(path), candidates, findAudioByName);
                if (!found.timeline.isEmpty()) return found;
            } catch (Throwable error) {
                record("本地歌词目录搜索失败=" + failureCause(error) + " dir=" + path);
            }
        }
        String tree = AppPreferences.localLyricDirectoryUri(context);
        if (tree.isEmpty() || Build.VERSION.SDK_INT < 21) return Hit.EMPTY;
        try {
            return searchTree(Uri.parse(tree), candidates, findAudioByName);
        } catch (Throwable error) {
            record("已授权歌词目录不可用=" + failureCause(error) + " tree=" + tree
                    + " 请重新“选择本地音乐目录”授权");
            return Hit.EMPTY;
        }
    }

    private File sidecarFile(String mediaUri) throws Exception {
        if (TextUtils.isEmpty(mediaUri)) return null;
        Uri uri = Uri.parse(mediaUri);
        String path = null;
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) path = uri.getPath();
        else if (uri.getScheme() == null || uri.getScheme().isEmpty()) path = mediaUri;
        if (path == null) return null;
        // Uri.getPath already decodes file URIs. Raw paths must preserve '+' and literal '%'.
        int dot = path.lastIndexOf('.');
        return new File((dot > path.lastIndexOf(File.separatorChar) ? path.substring(0, dot) : path)
                + ".lrc");
    }

    private Set<String> candidateNames(String mediaUri, String title, String artist) {
        Set<String> names = new LinkedHashSet<>();
        addCandidate(names, title);
        addCandidate(names, LocalTrackQueryRules.cleanFileTitle(title));
        addCandidate(names, artist + " - " + title);
        addCandidate(names, title + " - " + artist);
        try {
            Uri uri = Uri.parse(mediaUri);
            String displayName = queryName(uri);
            int dot = displayName.lastIndexOf('.');
            addCandidate(names, dot > 0 ? displayName.substring(0, dot) : displayName);
        } catch (Throwable ignored) { }
        return names;
    }

    private static void addCandidate(Set<String> names, String value) {
        String normalized = normalize(value);
        if (!normalized.isEmpty()) {
            names.add(normalized + ".lrc");
            names.add(normalized + ".krc");
        }
    }

    /**
     * Walks one authorised tree for a candidate .lrc. When {@code findAudioByName} is set - the
     * player gave no path this app could open - same-name audio files are collected as well and
     * only opened once the whole tree turned out to hold no usable .lrc.
     */
    @android.annotation.TargetApi(21)
    private Hit searchTree(Uri tree, Set<String> candidates, boolean findAudioByName)
            throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ArrayDeque<String> directories = new ArrayDeque<>();
        directories.add(DocumentsContract.getTreeDocumentId(tree));
        List<AudioDocument> audioDocuments = new ArrayList<>(MAX_AUDIO_FILES);
        int visited = 0;
        int failedQueries = 0;
        int unreadableDocuments = 0;
        int matched = 0;
        while (!directories.isEmpty() && visited < MAX_DOCUMENTS) {
            String parentId = directories.removeFirst();
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
            try (Cursor cursor = resolver.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
                if (cursor == null) {
                    failedQueries++;
                    if (failedQueries <= MAX_UNREADABLE_LOGS) {
                        record("已授权歌词目录查询失败=系统未返回目录内容 tree=" + tree
                                + " 请重新“选择本地音乐目录”授权");
                    }
                    continue;
                }
                while (cursor.moveToNext() && visited++ < MAX_DOCUMENTS) {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        directories.addLast(id);
                        continue;
                    }
                    String normalized = normalize(name);
                    if (candidates.contains(normalized)) {
                        matched++;
                        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                        try (InputStream input = resolver.openInputStream(document)) {
                            LrcTimeline timeline = parse(input);
                            if (!timeline.isEmpty()) return new Hit(timeline, false);
                        } catch (Exception error) {
                            unreadableDocuments++;
                            if (unreadableDocuments <= MAX_UNREADABLE_LOGS) {
                                record("已授权歌词目录文件不可读=" + failureCause(error)
                                        + " tree=" + tree);
                            }
                        }
                    } else if (findAudioByName && audioDocuments.size() < MAX_AUDIO_FILES
                            && candidates.contains(AudioFileCandidates.lyricCandidateKey(normalized))) {
                        // Extension and name only: the file is opened after the scan, and only
                        // when no .lrc in the whole tree matched.
                        audioDocuments.add(new AudioDocument(id, name));
                    }
                }
            } catch (Exception error) {
                failedQueries++;
                if (failedQueries <= MAX_UNREADABLE_LOGS) {
                    record("已授权歌词目录不可读=" + failureCause(error) + " tree=" + tree
                            + " 请重新“选择本地音乐目录”授权");
                }
            }
        }
        if (!directories.isEmpty() || visited >= MAX_DOCUMENTS) {
            record("已授权歌词目录扫描达到上限 " + MAX_DOCUMENTS + " 项，可能漏掉歌词 tree=" + tree);
        }
        if (!audioDocuments.isEmpty()) {
            Hit embedded = readEmbeddedFromDocuments(tree, audioDocuments);
            if (!embedded.timeline.isEmpty()) return embedded;
        }
        record(matched == 0
                ? "已授权歌词目录未匹配到候选歌词 tree=" + tree + " 候选=" + candidates.size()
                        + " 已扫描=" + visited + " 查询失败=" + failedQueries
                        + " 不可读文件=" + unreadableDocuments
                        + audioCandidateNote(audioDocuments)
                : "已授权歌词目录候选歌词均无有效内容 tree=" + tree + " 已命中候选=" + matched
                        + " 不可读文件=" + unreadableDocuments);
        return Hit.EMPTY;
    }

    /** One child document kept for the by-name audio read. */
    private static final class AudioDocument {
        final String id;
        final String name;

        AudioDocument(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** Reads the embedded tag of at most {@link #MAX_AUDIO_FILES} same-name audio documents. */
    @android.annotation.TargetApi(21)
    private Hit readEmbeddedFromDocuments(Uri tree, List<AudioDocument> documents) {
        ContentResolver resolver = context.getContentResolver();
        for (AudioDocument document : documents) {
            String fileLabel = "file=" + document.name;
            try (InputStream input = resolver.openInputStream(
                    DocumentsContract.buildDocumentUriUsingTree(tree, document.id))) {
                if (input == null) {
                    record("已授权歌词目录音频文件不可读=系统未返回内容 tree=" + tree + " " + fileLabel);
                    continue;
                }
                Hit hit = embeddedAudioHit(EmbeddedLyricReader.read(input, document.name),
                        "已授权歌词目录", fileLabel);
                if (!hit.timeline.isEmpty()) return hit;
            } catch (Exception error) {
                record("已授权歌词目录音频文件不可读=" + failureCause(error)
                        + " tree=" + tree + " " + fileLabel);
            }
        }
        return Hit.EMPTY;
    }

    /**
     * One audio file's embedded tag. A file without usable lines is logged in the wording the
     * diagnostics need ("找到音频文件但无内嵌歌词 file=…"), so a report says which link failed.
     */
    private Hit embeddedAudioHit(String text, String where, String fileLabel) {
        if (text != null && !text.isEmpty()) {
            LrcTimeline found = parseEmbeddedText(text);
            if (!found.isEmpty()) {
                record(where + "音频文件内嵌歌词命中 " + fileLabel + " lines=" + found.lineCount());
                return new Hit(found, true);
            }
        }
        record(where + "找到音频文件但无内嵌歌词 " + fileLabel);
        return Hit.EMPTY;
    }

    private static String audioCandidateNote(List<?> audioCandidates) {
        return audioCandidates.isEmpty() ? "" : " 音频候选=" + audioCandidates.size();
    }

    private String queryName(Uri uri) {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) return "";
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : "";
        } catch (Throwable ignored) { return ""; }
    }

    /** What the player's own audio path gave us: the tag text, and whether it opened at all. */
    private static final class EmbeddedProbe {
        static final EmbeddedProbe UNREADABLE = new EmbeddedProbe("", false, null);
        final String text;
        final boolean opened;
        /** Why a readable stream still yielded no text, or null when the path never opened. */
        final Throwable error;

        EmbeddedProbe(String text, boolean opened, Throwable error) {
            this.text = text == null ? "" : text;
            this.opened = opened;
            this.error = error;
        }

        static EmbeddedProbe unreadable(Throwable error) {
            return new EmbeddedProbe("", false, error);
        }
    }

    /**
     * The lyric tag inside the audio file the player pointed at, if this app can read that file.
     *
     * <p>{@code opened} is what decides whether searching the folder for the same file by name is
     * still worth doing: a path we may not open (Poweramp publishes a private content:// URI) never
     * showed us the tags, while a file we read without finding a lyric cannot gain one by being
     * found again.
     */
    private EmbeddedProbe readEmbeddedLyric(String mediaUri, String title) {
        if (TextUtils.isEmpty(mediaUri)) return EmbeddedProbe.UNREADABLE;
        Uri uri = Uri.parse(mediaUri);
        String name = queryName(uri);
        if (name.isEmpty()) name = title;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            InputStream input;
            try {
                input = context.getContentResolver().openInputStream(uri);
            } catch (Throwable error) {
                return EmbeddedProbe.unreadable(error);
            }
            if (input == null) return EmbeddedProbe.UNREADABLE;
            return readProbe(input, name);
        }
        String path = null;
        if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) path = uri.getPath();
        else if (uri.getScheme() == null || uri.getScheme().isEmpty()) path = mediaUri;
        if (path == null || path.isEmpty()) return EmbeddedProbe.UNREADABLE;
        try {
            return readProbe(new FileInputStream(new File(path)),
                    name.isEmpty() ? path : name);
        } catch (Throwable error) {
            return EmbeddedProbe.unreadable(error);
        }
    }

    /** One already-open audio stream; a tag that fails to parse still means the path was usable. */
    private static EmbeddedProbe readProbe(InputStream input, String displayName) {
        try {
            return new EmbeddedProbe(EmbeddedLyricReader.read(input, displayName), true, null);
        } catch (Throwable error) {
            return new EmbeddedProbe("", true, error);
        }
    }

    /**
     * Walks one manual directory for a candidate .lrc. When {@code findAudioByName} is set - the
     * player gave no path this app could open - same-name audio files are collected as well and
     * only opened once the whole directory turned out to hold no usable .lrc.
     */
    private Hit searchDirectory(File root, Set<String> candidates, boolean findAudioByName)
            throws Exception {
        if (root == null) return Hit.EMPTY;
        String path = root.getAbsolutePath();
        if (!root.exists()) {
            record("本地歌词目录不存在 dir=" + path + " " + accessState());
            return Hit.EMPTY;
        }
        if (!root.isDirectory()) {
            record("本地歌词目录不是文件夹 dir=" + path);
            return Hit.EMPTY;
        }
        ArrayDeque<File> directories = new ArrayDeque<>();
        directories.add(root);
        List<File> audioFiles = new ArrayList<>(MAX_AUDIO_FILES);
        int visited = 0;
        int unreadableDirectories = 0;
        int unreadableFiles = 0;
        int matched = 0;
        while (!directories.isEmpty() && visited < MAX_DOCUMENTS) {
            File directory = directories.removeFirst();
            File[] children;
            try {
                children = directory.listFiles();
            } catch (SecurityException ignored) {
                children = null;
            }
            if (children == null) {
                unreadableDirectories++;
                if (unreadableDirectories <= MAX_UNREADABLE_LOGS) {
                    record("本地目录不可读=" + unreadableCause()
                            + " dir=" + directory.getAbsolutePath());
                }
                continue;
            }
            for (File child : children) {
                if (visited++ >= MAX_DOCUMENTS) break;
                if (child.isDirectory()) {
                    directories.addLast(child);
                } else if (candidates.contains(normalize(child.getName()))) {
                    matched++;
                    try {
                        LrcTimeline timeline = parse(new FileInputStream(child));
                        if (!timeline.isEmpty()) return new Hit(timeline, false);
                    } catch (Exception error) {
                        unreadableFiles++;
                        if (unreadableFiles <= MAX_UNREADABLE_LOGS) {
                            record("本地歌词文件不可读=" + failureCause(error)
                                    + " file=" + child.getAbsolutePath());
                        }
                    }
                } else if (findAudioByName && audioFiles.size() < MAX_AUDIO_FILES
                        && candidates.contains(
                                AudioFileCandidates.lyricCandidateKey(child.getName()))) {
                    // Extension and name only: the file is opened after the scan, and only when no
                    // .lrc in the whole directory matched.
                    audioFiles.add(child);
                }
            }
        }
        if (!directories.isEmpty() || visited >= MAX_DOCUMENTS) {
            record("本地歌词目录扫描达到上限 " + MAX_DOCUMENTS + " 项，可能漏掉歌词 dir=" + path);
        }
        if (!audioFiles.isEmpty()) {
            Hit embedded = readEmbeddedFromFiles(audioFiles);
            if (!embedded.timeline.isEmpty()) return embedded;
        }
        record(matched == 0
                ? "本地目录未匹配到候选歌词 dir=" + path + " 候选=" + candidates.size()
                        + " 已扫描=" + visited + " 不可读目录=" + unreadableDirectories
                        + " 不可读文件=" + unreadableFiles + audioCandidateNote(audioFiles)
                        + " " + accessState()
                : "本地目录候选歌词均无有效内容 dir=" + path + " 已命中候选=" + matched
                        + " 不可读文件=" + unreadableFiles + " " + accessState());
        return Hit.EMPTY;
    }

    /** Reads the embedded tag of at most {@link #MAX_AUDIO_FILES} same-name audio files. */
    private Hit readEmbeddedFromFiles(List<File> audioFiles) {
        for (File file : audioFiles) {
            String fileLabel = "file=" + file.getAbsolutePath();
            try {
                Hit hit = embeddedAudioHit(
                        EmbeddedLyricReader.read(new FileInputStream(file), file.getName()),
                        "本地目录", fileLabel);
                if (!hit.timeline.isEmpty()) return hit;
            } catch (Throwable error) {
                record("本地目录音频文件不可读=" + failureCause(error) + " " + fileLabel);
            }
        }
        return Hit.EMPTY;
    }

    /** Why a directory listing came back empty on this system. */
    private String unreadableCause() {
        if (!canReadManualDirectory(context)) {
            return "权限不足(" + manualDirectoryAccessReason(context) + ")";
        }
        return "系统拒绝访问";
    }

    /** Short Chinese cause for one failed read, so the log never hides behind a stack trace. */
    private String failureCause(Throwable error) {
        if (isAccessDenied(error)) return "权限不足(" + manualDirectoryAccessReason(context) + ")";
        if (error == null) return "未知原因";
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 120) message = message.substring(0, 120) + "…";
        return error.getClass().getSimpleName() + ": " + message;
    }

    private static boolean isAccessDenied(Throwable error) {
        if (error instanceof SecurityException) return true;
        if (error instanceof FileNotFoundException) {
            // Scoped storage reports an unauthorised file as EACCES "Permission denied".
            String message = error.getMessage();
            return message != null && (message.contains("EACCES") || message.contains("EPERM"));
        }
        return false;
    }

    private String accessState() {
        return "读取权限=" + manualDirectoryAccessReason(context);
    }

    private void record(String message) {
        DiagnosticLog.record(context, "Lyrics", message);
    }

    private static LrcTimeline parse(InputStream input) throws Exception {
        if (input == null) return LrcTimeline.EMPTY;
        byte[] bytes;
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (output.size() + count > MAX_BYTES) return LrcTimeline.EMPTY;
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
        }
        return parseText(bytes);
    }

    private static LrcTimeline parseText(String text) {
        Matcher tag = AWLRC_TAG.matcher(text);
        if (tag.find()) {
            String original = "", translated = "", romaji = "", enhanced = "";
            for (String field : tag.group(1).split(",")) {
                int separator = field.indexOf(':');
                if (separator <= 0 || separator == field.length() - 1) continue;
                String key = field.substring(0, separator);
                String payload = field.substring(separator + 1);
                if (!payload.matches("[A-Za-z0-9+/=]+")) continue;
                try {
                    String decoded = new String(Base64.decode(payload, Base64.DEFAULT),
                            StandardCharsets.UTF_8);
                    if ("lrc".equals(key)) original = decoded;
                    else if ("tlrc".equals(key)) translated = decoded;
                    else if ("rlrc".equals(key)) romaji = decoded;
                    else if ("awlrc".equals(key)) enhanced = AwlrcCodec.toYrc(decoded);
                } catch (IllegalArgumentException ignored) { }
            }
            LrcTimeline parsed = LrcTimeline.parse(original, translated, enhanced, romaji);
            if (!parsed.isEmpty()) return parsed;
        }
        return LrcTimeline.parse(text, "");
    }

    /** Unsynchronised lyric tags still have useful line breaks; show them at a safe fixed pace. */
    private static LrcTimeline parseEmbeddedText(String text) {
        LrcTimeline timed = parseText(text);
        if (!timed.isEmpty()) return timed;
        String[] rawLines = text.replace('\r', '\n').split("\\n+");
        List<LrcTimeline.Line> lines = new ArrayList<>();
        long timeMs = 0L;
        for (String rawLine : rawLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            lines.add(new LrcTimeline.Line(timeMs, 5_000L, line));
            timeMs += 5_000L;
        }
        return LrcTimeline.fromTimedLines(lines);
    }

    private static LrcTimeline parseText(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 'k' && bytes[1] == 'r'
                && bytes[2] == 'c' && bytes[3] == '1') {
            try {
                String decrypted = KrcLyricCodec.decrypt(bytes);
                String encoded = KrcLyricCodec.encodedLanguage(decrypted);
                String translated = encoded.isEmpty() ? "" : KrcLyricCodec.toTranslationLrc(
                        decrypted, new String(Base64.decode(encoded, Base64.DEFAULT),
                        StandardCharsets.UTF_8));
                return LrcTimeline.parse("", translated,
                        KrcLyricCodec.toEnhancedTimeline(decrypted));
            } catch (Exception ignored) {
                return LrcTimeline.EMPTY;
            }
        }
        return parseText(decodeLyricText(bytes));
    }

    static String decodeLyricText(byte[] bytes) {
        if (bytes.length >= 2) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            if (first == 0xff && second == 0xfe) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            }
            if (first == 0xfe && second == 0xff) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            }
        }
        // Many older .lrc files omit the UTF-16 BOM. An alternating zero-byte pattern is a
        // stronger signal than UTF-8's replacement character check for these files.
        int evenZeroes = 0;
        int oddZeroes = 0;
        int pairs = Math.min(bytes.length / 2, 256);
        for (int index = 0; index < pairs; index++) {
            if (bytes[index * 2] == 0) evenZeroes++;
            if (bytes[index * 2 + 1] == 0) oddZeroes++;
        }
        if (pairs >= 8 && oddZeroes > pairs / 3 && evenZeroes < pairs / 10) {
            return new String(bytes, StandardCharsets.UTF_16LE);
        }
        if (pairs >= 8 && evenZeroes > pairs / 3 && oddZeroes < pairs / 10) {
            return new String(bytes, StandardCharsets.UTF_16BE);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.indexOf('\uFFFD') >= 0) text = new String(bytes, Charset.forName("GB18030"));
        return text.replace("\uFEFF", "");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ");
        return normalized;
    }
}
