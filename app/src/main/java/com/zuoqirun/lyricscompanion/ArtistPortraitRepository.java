package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Opt-in Wikimedia portraits. Metadata/attribution stays local; requests never block lyrics. */
final class ArtistPortraitRepository {
    static final String SOURCE = "artist_portrait_source";
    static final String INTERVAL = "artist_portrait_interval";
    static final String FIT = "artist_portrait_fit";
    static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 20L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4));
    private static final Map<String, Result> CACHE = new LinkedHashMap<>();
    private static final Set<String> PENDING = new LinkedHashSet<>();
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    private static final String WIKIDATA = "https://www.wikidata.org/w/api.php?format=json&";
    private static final String COMMONS = "https://commons.wikimedia.org/w/api.php?format=json&";
    private ArtistPortraitRepository() { }

    static final class Photo {
        final String url, page, credit;
        Photo(String url, String page, String credit) { this.url = url; this.page = page; this.credit = credit; }
    }
    private static final class Result {
        final List<Photo> photos;
        final long until;
        Result(List<Photo> photos, long until) { this.photos = photos; this.until = until; }
    }

    static synchronized List<Photo> find(Context context, String artist) {
        Result result = CACHE.get(artist);
        if (result != null && result.until > System.currentTimeMillis()) return result.photos;
        if (!PENDING.add(artist)) return java.util.Collections.emptyList();
        Context app = context.getApplicationContext();
        try {
            EXECUTOR.execute(() -> {
                List<Photo> photos = java.util.Collections.emptyList();
                try { photos = loadMetadata(app, artist); } catch (Exception ignored) { }
                synchronized (ArtistPortraitRepository.class) {
                    PENDING.remove(artist);
                    if (CACHE.size() >= 8) CACHE.remove(CACHE.keySet().iterator().next());
                    CACHE.put(artist, new Result(photos, System.currentTimeMillis() + (photos.isEmpty() ? 300_000L : WEEK)));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) { PENDING.remove(artist); }
        return java.util.Collections.emptyList();
    }

    static synchronized List<Photo> cachedPhotos(String artist) {
        Result result = CACHE.get(artist);
        return result == null ? java.util.Collections.emptyList() : result.photos;
    }

    static boolean matches(String artist, JSONObject entity) {
        JSONObject labels = entity.optJSONObject("labels");
        if (labels != null) for (Iterator<String> it = labels.keys(); it.hasNext();) {
            JSONObject label = labels.optJSONObject(it.next());
            if (label != null && artist.trim().equalsIgnoreCase(label.optString("value"))) return true;
        }
        JSONObject aliases = entity.optJSONObject("aliases");
        if (aliases != null) for (Iterator<String> it = aliases.keys(); it.hasNext();) {
            JSONArray group = aliases.optJSONArray(it.next());
            if (group != null) for (int i = 0; i < group.length(); i++) {
                if (artist.trim().equalsIgnoreCase(group.optJSONObject(i).optString("value"))) return true;
            }
        }
        return false;
    }

    private static List<Photo> loadMetadata(Context context, String artist) throws Exception {
        File file = new File(directory(context), hash(artist) + ".json");
        if (file.isFile() && System.currentTimeMillis() - file.lastModified() < WEEK) {
            try (InputStream input = new FileInputStream(file)) { return decode(new JSONArray(new String(read(input, 256_000), "UTF-8"))); }
            catch (Exception ignored) { file.delete(); }
        }
        JSONArray search = get(WIKIDATA + "action=wbsearchentities&language=zh&uselang=en&type=item&limit=5&search=" + encode(artist)).optJSONArray("search");
        if (search == null || search.length() == 0) return java.util.Collections.emptyList();
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < search.length(); i++) {
            if (i > 0) ids.append('|');
            ids.append(search.getJSONObject(i).getString("id"));
        }
        JSONObject entities = get(WIKIDATA + "action=wbgetentities&props=claims%7Clabels%7Caliases%7Cdescriptions&languages=zh%7Czh-hans%7Czh-hant%7Cen&ids=" + encode(ids.toString())).getJSONObject("entities");
        JSONObject selected = null;
        for (Iterator<String> it = entities.keys(); it.hasNext();) {
            JSONObject entity = entities.getJSONObject(it.next());
            if (!matches(artist, entity) || !musician(entity)) continue;
            if (selected != null) return java.util.Collections.emptyList(); // Ambiguous identities need manual correction.
            selected = entity;
        }
        if (selected == null) return java.util.Collections.emptyList();
        List<Photo> photos = new ArrayList<>();
        JSONArray images = selected.getJSONObject("claims").optJSONArray("P18");
        if (images != null) {
            StringBuilder titles = new StringBuilder();
            for (int i = 0; i < Math.min(6, images.length()); i++) {
                String image = claim(images.getJSONObject(i));
                if (image.isEmpty()) continue;
                if (titles.length() > 0) titles.append('|');
                titles.append("File:").append(image);
            }
            if (titles.length() > 0) addPhotos(photos, get(COMMONS + "action=query&titles=" + encode(titles.toString()) + imageInfo()));
        }
        JSONArray categories = selected.getJSONObject("claims").optJSONArray("P373");
        if (categories != null && categories.length() > 0 && photos.size() < 6
                && !AppPreferences.lowResourceMode(context)) {
            String category = claim(categories.getJSONObject(0));
            if (!category.isEmpty()) addPhotos(photos, get(COMMONS + "action=query&generator=categorymembers&gcmnamespace=6&gcmtype=file&gcmlimit=12&gcmtitle="
                    + encode("Category:" + category) + imageInfo()));
        }
        JSONArray encoded = new JSONArray();
        for (Photo photo : photos) encoded.put(new JSONObject().put("url", photo.url).put("page", photo.page).put("credit", photo.credit));
        if (!photos.isEmpty()) try (FileOutputStream output = new FileOutputStream(file)) { output.write(encoded.toString().getBytes("UTF-8")); }
        trim(directory(context));
        return photos;
    }

    static boolean musician(JSONObject entity) {
        JSONObject claims = entity.optJSONObject("claims");
        JSONArray instances = claims == null ? null : claims.optJSONArray("P31");
        boolean personOrGroup = false;
        if (instances != null) for (int i = 0; i < instances.length(); i++) {
            JSONObject statement = instances.optJSONObject(i);
            JSONObject snak = statement == null ? null : statement.optJSONObject("mainsnak");
            JSONObject data = snak == null ? null : snak.optJSONObject("datavalue");
            JSONObject value = data == null ? null : data.optJSONObject("value");
            String id = value == null ? "" : value.optString("id");
            personOrGroup |= "Q5".equals(id) || "Q215380".equals(id);
        }
        if (!personOrGroup) return false;
        JSONObject descriptions = entity.optJSONObject("descriptions");
        if (descriptions == null) return false;
        for (Iterator<String> it = descriptions.keys(); it.hasNext();) {
            String value = descriptions.optJSONObject(it.next()).optString("value").toLowerCase(Locale.ROOT);
            if (value.matches(".*(singer|musician|rapper|songwriter|composer|musical group|rock band|pop band|歌手|歌唱家|音乐家|音樂家|乐团|樂團|乐队|樂隊).*")) return true;
        }
        return false;
    }

    private static String imageInfo() { return "&prop=imageinfo&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=700"; }
    private static String claim(JSONObject statement) {
        JSONObject snak = statement.optJSONObject("mainsnak");
        JSONObject value = snak == null ? null : snak.optJSONObject("datavalue");
        return value == null ? "" : value.optString("value", "");
    }
    private static void addPhotos(List<Photo> photos, JSONObject response) {
        JSONObject query = response.optJSONObject("query"), pages = query == null ? null : query.optJSONObject("pages");
        if (pages == null) return;
        for (Iterator<String> it = pages.keys(); it.hasNext() && photos.size() < 6;) {
            JSONArray information = pages.optJSONObject(it.next()).optJSONArray("imageinfo");
            if (information == null || information.length() == 0) continue;
            JSONObject info = information.optJSONObject(0), extra = info.optJSONObject("extmetadata");
            if (extra == null || !info.optString("mime").matches("image/(jpeg|png|webp)")) continue;
            String url = info.optString("thumburl", ""), page = info.optString("descriptionurl", "");
            String license = value(extra, "LicenseShortName");
            if (!safeImage(url) || !page.startsWith("https://commons.wikimedia.org/") || license.isEmpty()) continue;
            boolean duplicate = false;
            for (Photo photo : photos) duplicate |= photo.page.equals(page);
            if (!duplicate) photos.add(new Photo(url, page, value(extra, "Artist") + " · " + license));
        }
    }
    private static String value(JSONObject metadata, String key) {
        JSONObject value = metadata.optJSONObject(key);
        return value == null ? "" : value.optString("value").replaceAll("<[^>]*>", "").replace("&amp;", "&").trim();
    }
    static boolean safeImage(String url) {
        return url.startsWith("https://upload.wikimedia.org/") || url.startsWith("https://thumb.wikimedia.org/");
    }
    private static List<Photo> decode(JSONArray data) {
        List<Photo> photos = new ArrayList<>();
        for (int i = 0; i < Math.min(6, data.length()); i++) {
            JSONObject value = data.optJSONObject(i);
            if (value != null && safeImage(value.optString("url")) && value.optString("page").startsWith("https://commons.wikimedia.org/"))
                photos.add(new Photo(value.optString("url"), value.optString("page"), value.optString("credit")));
        }
        return photos;
    }

    static Bitmap loadImage(Context context, Photo photo) throws Exception {
        File file = new File(directory(context), hash(photo.url) + ".img");
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() >= WEEK) {
            byte[] bytes = download(photo.url, 2 * 1024 * 1024);
            File temp = new File(file.getPath() + ".tmp");
            try (FileOutputStream output = new FileOutputStream(temp)) { output.write(bytes); }
            if (!temp.renameTo(file)) { temp.delete(); return null; }
            trim(directory(context));
        }
        Bitmap bitmap = AlbumArtLoader.load(context, Uri.fromFile(file).toString());
        if (bitmap == null) file.delete();
        return bitmap;
    }
    private static JSONObject get(String url) throws Exception { return new JSONObject(new String(download(url, 512_000), "UTF-8")); }
    private static byte[] download(String url, int limit) throws Exception {
        HttpURLConnection connection = HttpCompat.open(url);
        connection.setConnectTimeout(3000); connection.setReadTimeout(4000); connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "Lyrics-Companion/1.0 (https://github.com/zuo-qirun/Lyrics-Companion)");
        try (InputStream input = connection.getInputStream()) { return read(input, limit); }
        finally { connection.disconnect(); }
    }
    private static byte[] read(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new java.io.IOException("Portrait response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private static File directory(Context context) {
        File directory = new File(context.getCacheDir(), "artist-portraits");
        directory.mkdirs(); return directory;
    }
    private static void trim(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        long size = 0;
        for (File file : files) size += file.length();
        for (File file : files) if (size > 32L * 1024 * 1024 || System.currentTimeMillis() - file.lastModified() > WEEK) {
            long bytes = file.length(); if (file.delete()) size -= bytes;
        }
    }
    private static String hash(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }
    private static String encode(String value) throws Exception { return URLEncoder.encode(value, "UTF-8"); }
}
