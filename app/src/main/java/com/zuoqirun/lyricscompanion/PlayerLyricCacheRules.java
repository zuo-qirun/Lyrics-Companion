package com.zuoqirun.lyricscompanion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure rules for reading a music player's own lyric cache (issue #154).
 *
 * <p>The probe is opt-in and always silent on failure. Everything here is plain Java so the
 * candidate paths, file-name matching and work budgets can be unit tested on the JVM:
 * {@link LocalLyricClient#loadPlayerCache} only adds the Context-dependent directory walk and
 * the actual file parse around these rules.
 *
 * <p>Cached file names are matched against the song metadata the player gave us, never against
 * the file modification time: a cache directory can hold dozens of unrelated lyrics, and the
 * newest file is often from the previous song.
 */
final class PlayerLyricCacheRules {
    /** 候选目录总数上限。只对真实存在的目录做扫描，所以它比扫描预算大一些。 */
    static final int MAX_CANDIDATE_DIRECTORIES = 96;
    /** 一次探测最多真正列出多少个目录，防止切歌时被一个巨大的缓存目录拖慢。 */
    static final int MAX_DIRECTORIES = 32;
    /** 一次探测最多检查多少个目录项（含子目录项）；文件名打分本身很便宜。 */
    static final int MAX_FILES = 800;
    /** 单个目录最多检查多少项，避免第一个大目录吃掉全部预算、后面的播放器没机会。 */
    static final int MAX_FILES_PER_DIRECTORY = 200;
    /** 候选目录下最多再进一层子目录（缓存常把歌词放在 lyric/、cache/ 这类子目录里）。 */
    static final int MAX_DEPTH = 1;
    /** 一次探测最多打开多少个同名候选文件，避免命中多个名字时连续读大文件。 */
    static final int MAX_CANDIDATES = 6;

    static final int SCORE_NONE = 0;
    /** 只有半边名字（通常是歌名）能对上：歌名对上了，但歌手字段缺失。 */
    static final int SCORE_TITLE_PARTIAL = 50;
    /** 去掉 (Live)/无损 等括号后缀后才匹配上时的最低分。 */
    static final int SCORE_CLEANED_TITLE = 60;
    /** 文件名就是播放器给出的歌曲 ID，例如网易云的 186016.lrc。 */
    static final int SCORE_TRACK_ID = 75;
    /** 文件名只有歌名。 */
    static final int SCORE_TITLE = 80;
    /** 文件名是「歌名 - 歌手」或「歌手 - 歌名」拆开后精确匹配。 */
    static final int SCORE_ARTIST_TITLE = 90;
    /** 文件名整体去标点后等于「歌名 + 歌手」（含紧凑写法 晴天-周杰伦）。 */
    static final int SCORE_COMBINED = 100;

    private static final Pattern LYRIC_EXTENSION = Pattern.compile("(?i)\\.(?:lrc|krc)$");
    private static final Pattern SPACED_SEPARATOR = Pattern.compile("\\s+[-–—－_]\\s+");
    private static final Pattern USABLE_TRACK_ID = Pattern.compile("(?=.*\\d)[A-Za-z0-9_-]{3,40}");
    private static final char[] COMPACT_SEPARATORS = {'-', '_', '–', '—', '－'};

    /** 五大在线歌词目录对应的主包名，顺序即探测优先级。 */
    private static final String[] PRIMARY_PACKAGES = {
            "com.netease.cloudmusic",
            "com.tencent.qqmusic",
            "com.kugou.android",
            "cn.kuwo.player",
            "com.luna.music",
    };

    /** 同一播放器的车机版、Lite 版或旧包名，只再试最常见的 files/cache 两级。 */
    private static final String[] VARIANT_PACKAGES = {
            "com.netease.cloudmusic.iot",
            "com.tencent.qqmusiccar",
            "com.kugou.android.auto",
            "com.kugou.android.lite",
            "com.kugou.auto",
            "cn.kuwo.kwmusiccar",
            "cn.kuwo.autolite",
            "cn.kuwo.kwmusic",
            "cn.kuwo.car",
            "com.shaiban.audioplayer.mplayer",
            "com.luna.music.car",
    };

    /**
     * 各大播放器真正存放歌词的目录名并不公开，也不保证跨版本稳定；这里列出能观察到的常见写法，
     * 真正的候选靠歌曲元数据打分，目录猜错了也不会误用别人的歌词。
     */
    private static final String[] PUBLIC_DIRECTORIES = {
            "网易云音乐",
            "网易云音乐/Lyric",
            "网易云音乐/lyric",
            "netease/cloudmusic/Cache/Lyric",
            "Netease/cloudmusic/Cache/Lyric",
            "QQMusic",
            "QQMusic/Lyric",
            "QQMusic/lyric",
            "kugou",
            "kugou/lyric",
            "kuwo",
            "kuwo/lyric",
            "KuwoMusic",
            "汽水音乐",
            "汽水音乐/Lyric",
            "Download/Lyrics",
            "Download/Lyric",
            "Music/Lyrics",
            "Music/Lyric",
            "Lyrics",
    };

    private static volatile List<String> relativeDirectoriesCache;

    private PlayerLyricCacheRules() {}

    /**
     * Expands storage roots into the cache directories worth one probe, most likely players first.
     * A root is usually the primary external storage; a second one is an SD card if present. The
     * result is capped at {@link #MAX_CANDIDATE_DIRECTORIES}, so a second root only gets a share
     * when the first one does not already fill the list.
     */
    static List<String> candidateDirectories(List<String> storageRoots) {
        List<String> result = new ArrayList<>();
        if (storageRoots == null || storageRoots.isEmpty()) {
            return Collections.unmodifiableList(result);
        }
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        for (String root : storageRoots) {
            String clean = cleanRoot(root);
            if (!clean.isEmpty()) roots.add(clean);
        }
        List<String> relative = relativeDirectories();
        for (String root : roots) {
            for (String path : relative) {
                if (result.size() >= MAX_CANDIDATE_DIRECTORIES) {
                    return Collections.unmodifiableList(result);
                }
                result.add(root + "/" + path);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** True for the two lyric file types this app can parse: .lrc and Kugou's encrypted .krc. */
    static boolean isLyricFile(String fileName) {
        return fileName != null && LYRIC_EXTENSION.matcher(fileName).find();
    }

    /**
     * How well one cache file name matches the playing song.
     *
     * @param trackId the player's own song id, used only when the cache directory belongs to that
     *                same player; pass an empty string when ownership cannot be verified
     * @return one of the {@code SCORE_*} constants, or {@link #SCORE_NONE} when it does not match
     */
    static int scoreFileName(String fileName, String title, String artist, String trackId) {
        if (!isLyricFile(fileName)) return SCORE_NONE;
        String base = stem(fileName);
        List<String> titleVariants = variants(title);
        List<String> artistVariants = variants(artist);
        if (titleVariants.isEmpty()) {
            // 没有歌名时不猜：只有「纯歌曲 ID」这类名字还能验证一下。
            return trackIdScore(identity(base), trackId);
        }
        int direct = scoreCore(base, titleVariants, artistVariants);
        if (direct > SCORE_NONE) return direct;
        int directTrackId = trackIdScore(identity(base), trackId);
        if (directTrackId > SCORE_NONE) return directTrackId;
        // 缓存名常带 (Live)、无损、音质标签或 03 之类的前缀；去掉后再试一次，但降一档分数。
        String cleaned = LocalTrackQueryRules.cleanFileTitle(base);
        if (!identity(cleaned).equals(identity(base))) {
            int cleanedScore = scoreCore(cleaned, titleVariants, artistVariants);
            if (cleanedScore > SCORE_NONE) {
                return Math.max(SCORE_CLEANED_TITLE, cleanedScore - 20);
            }
        }
        return SCORE_NONE;
    }

    /** True when this id looks like a player song id and not like a title fragment. */
    static boolean isUsableTrackId(String trackId) {
        return !usableTrackId(trackId).isEmpty();
    }

    private static int scoreCore(String base, List<String> titleVariants,
                                 List<String> artistVariants) {
        String baseIdentity = identity(base);
        if (baseIdentity.isEmpty()) return SCORE_NONE;
        for (String titleVariant : titleVariants) {
            String titleIdentity = identity(titleVariant);
            if (titleIdentity.isEmpty()) continue;
            for (String artistVariant : artistVariants) {
                String artistIdentity = identity(artistVariant);
                if (artistIdentity.isEmpty()) continue;
                if (baseIdentity.equals(titleIdentity + artistIdentity)
                        || baseIdentity.equals(artistIdentity + titleIdentity)) {
                    return SCORE_COMBINED;
                }
            }
        }
        String[] parts = splitArtistTitle(base);
        if (parts != null) {
            String left = identity(parts[0]);
            String right = identity(parts[1]);
            for (String titleVariant : titleVariants) {
                String titleIdentity = identity(titleVariant);
                if (titleIdentity.isEmpty()) continue;
                if (!artistVariants.isEmpty()) {
                    for (String artistVariant : artistVariants) {
                        String artistIdentity = identity(artistVariant);
                        if (artistIdentity.isEmpty()) continue;
                        if ((left.equals(titleIdentity) && right.equals(artistIdentity))
                                || (right.equals(titleIdentity) && left.equals(artistIdentity))) {
                            return SCORE_ARTIST_TITLE;
                        }
                    }
                } else if (left.equals(titleIdentity) || right.equals(titleIdentity)) {
                    // 歌手字段为空（车机常常这样）时只能确认一半，降档但依然可用。
                    return SCORE_TITLE_PARTIAL;
                }
            }
        }
        for (String titleVariant : titleVariants) {
            if (baseIdentity.equals(identity(titleVariant))) return SCORE_TITLE;
        }
        return SCORE_NONE;
    }

    /** 把「歌手 - 歌名」「歌名 - 歌手」拆成两半；拆不开或分隔符本身是歌名一部分时返回 null。 */
    private static String[] splitArtistTitle(String value) {
        Matcher spaced = SPACED_SEPARATOR.matcher(value);
        if (spaced.find()) {
            return validParts(value.substring(0, spaced.start()), value.substring(spaced.end()));
        }
        int separator = -1;
        char found = 0;
        for (char candidate : COMPACT_SEPARATORS) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (separator < 0 || index < separator)) {
                separator = index;
                found = candidate;
            }
        }
        if (separator <= 0 || separator >= value.length() - 1) return null;
        // 同一个分隔符出现多次说明它属于歌名本身（例如 "Mojito - Live - 周杰伦"），不硬拆。
        if (value.indexOf(found, separator + 1) >= 0) return null;
        return validParts(value.substring(0, separator), value.substring(separator + 1));
    }

    private static String[] validParts(String left, String right) {
        String first = left.trim();
        String second = right.trim();
        if (first.isEmpty() || second.isEmpty()) return null;
        return new String[]{first, second};
    }

    private static int trackIdScore(String baseIdentity, String trackId) {
        String usable = usableTrackId(trackId);
        return !usable.isEmpty() && usable.equals(baseIdentity) ? SCORE_TRACK_ID : SCORE_NONE;
    }

    private static String usableTrackId(String trackId) {
        if (trackId == null) return "";
        String trimmed = trackId.trim();
        return USABLE_TRACK_ID.matcher(trimmed).matches() ? identity(trimmed) : "";
    }

    /** 歌名、歌手各自再补一个去掉括号标签/文件扩展名/音轨号后的变体。 */
    private static List<String> variants(String value) {
        List<String> result = new ArrayList<>(2);
        addVariant(result, value);
        addVariant(result, LocalTrackQueryRules.cleanFileTitle(value));
        return result;
    }

    private static void addVariant(List<String> variants, String value) {
        if (value == null) return;
        String text = value.trim();
        if (text.isEmpty()) return;
        String textIdentity = identity(text);
        if (textIdentity.isEmpty()) return;
        for (String existing : variants) {
            if (textIdentity.equals(identity(existing))) return;
        }
        variants.add(text);
    }

    /** 去掉路径、扩展名和所有标点/空白后的比较键；NFKC 先把全角字符折成半角。 */
    private static String identity(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.trim();
    }

    private static List<String> relativeDirectories() {
        List<String> cached = relativeDirectoriesCache;
        if (cached != null) return cached;
        List<String> directories = new ArrayList<>();
        for (String packageName : PRIMARY_PACKAGES) {
            directories.add("Android/data/" + packageName + "/files");
            directories.add("Android/data/" + packageName + "/files/cache");
            directories.add("Android/data/" + packageName + "/files/lyric");
            directories.add("Android/data/" + packageName + "/cache");
            directories.add("Android/media/" + packageName);
        }
        for (String packageName : VARIANT_PACKAGES) {
            directories.add("Android/data/" + packageName + "/files");
            directories.add("Android/data/" + packageName + "/files/cache");
            directories.add("Android/data/" + packageName + "/cache");
        }
        Collections.addAll(directories, PUBLIC_DIRECTORIES);
        List<String> unmodifiable = Collections.unmodifiableList(directories);
        relativeDirectoriesCache = unmodifiable;
        return unmodifiable;
    }

    private static String cleanRoot(String root) {
        if (root == null) return "";
        String clean = root.trim().replace('\\', '/');
        while (clean.length() > 1 && clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    /**
     * Bounded work for one probe: how many directories may be listed and how many entries may be
     * inspected. Tests build small budgets; the scanner uses the {@code MAX_*} defaults.
     */
    static final class Budget {
        private final int maxDirectories;
        private final int maxFiles;
        private int directories;
        private int files;

        Budget() {
            this(MAX_DIRECTORIES, MAX_FILES);
        }

        Budget(int maxDirectories, int maxFiles) {
            this.maxDirectories = Math.max(0, maxDirectories);
            this.maxFiles = Math.max(0, maxFiles);
        }

        boolean takeDirectory() {
            if (directories >= maxDirectories) return false;
            directories++;
            return true;
        }

        boolean takeFile() {
            if (files >= maxFiles) return false;
            files++;
            return true;
        }

        int directories() {
            return directories;
        }

        int files() {
            return files;
        }

        int maxDirectories() {
            return maxDirectories;
        }

        int maxFiles() {
            return maxFiles;
        }

        boolean directoriesExhausted() {
            return directories >= maxDirectories;
        }

        boolean filesExhausted() {
            return files >= maxFiles;
        }
    }
}
