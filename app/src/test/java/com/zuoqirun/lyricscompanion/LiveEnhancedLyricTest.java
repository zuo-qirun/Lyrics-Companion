package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Opt-in live smoke tests: LIVE_LYRIC_TEST=1 gradlew testDebugUnitTest. */
public class LiveEnhancedLyricTest {
    @Test public void sodaPublicSharePageReturnsWordTiming() throws Exception {
        requireLive();
        String address = SodaLyricClient.shareAddress(
                "com.luna.music", "7359456860514666513");
        SodaLyricClient.ShareLyrics parsed = SodaLyricClient.parseSharePage(
                LyricHttp.get(address, "https://www.qishui.com/"));
        LrcTimeline timeline = LrcTimeline.parse(parsed.original, parsed.translated,
                parsed.enhanced);
        assertFalse(parsed.enhanced.isEmpty());
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.at(17_000L).wordTimed);
    }

    @Test public void qqQrcContentTsReturnsChineseTranslation() throws Exception {
        requireLive();
        String response = LyricHttp.request("POST",
                "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg",
                "https://y.qq.com/",
                "version=15&miniversion=100&lrctype=4&musicid=213086592");
        String translated = QrcLyricCodec.decryptToLrc(
                QrcLyricCodec.encryptedContent(response, "contentts"));
        assertFalse(translated.isEmpty());
        assertTrue(translated.matches("(?s).*[\\u4e00-\\u9fff].*"));
    }

    @Test public void netEaseYrcKeepsChineseTranslationWhenFirstWordStartsLater() throws Exception {
        requireLive();
        JSONObject response = new JSONObject(LyricHttp.get(
                "https://music.163.com/api/song/lyric?id=25657526"
                        + "&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1",
                "https://music.163.com/"));
        String original = response.optJSONObject("lrc").optString("lyric", "");
        String translated = response.optJSONObject("tlyric").optString("lyric", "");
        String enhanced = response.optJSONObject("yrc").optString("lyric", "");
        LrcTimeline timeline = LrcTimeline.parse(original, translated, enhanced);
        assertFalse(timeline.at(57_300L).translatedLyric.isEmpty());
        assertTrue(timeline.at(57_300L).translatedLyric.contains("正确"));
    }

    @Test public void qqQrcEndpointReturnsWordTiming() throws Exception {
        requireLive();
        String response = LyricHttp.request("POST",
                "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg",
                "https://y.qq.com/",
                "version=15&miniversion=100&lrctype=4&musicid=97773");
        String enhanced = QrcLyricCodec.decryptToTimedLyric(
                QrcLyricCodec.encryptedContent(response, "content"));
        LrcTimeline timeline = LrcTimeline.parse("", "", enhanced);
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.at(30_000L).wordTimed);
    }

    @Test public void kugouKrcEndpointReturnsWordTiming() throws Exception {
        requireLive();
        String hash = "B3A52A7A958BF0AED0EBFBA2E9A818B7";
        JSONObject search = new JSONObject(LyricHttp.get(
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=" + hash,
                "https://www.kugou.com/"));
        JSONArray candidates = search.optJSONArray("candidates");
        JSONObject candidate = candidates == null ? null : candidates.optJSONObject(0);
        assertTrue(candidate != null);
        String url = "https://lyrics.kugou.com/download?ver=1&client=pc&id="
                + LyricHttp.encode(candidate.optString("id")) + "&accesskey="
                + LyricHttp.encode(candidate.optString("accesskey"))
                + "&fmt=krc&charset=utf8";
        JSONObject download = new JSONObject(LyricHttp.get(url, "https://www.kugou.com/"));
        String decrypted = KrcLyricCodec.decrypt(Base64.getDecoder().decode(
                download.optString("content")));
        LrcTimeline timeline = LrcTimeline.parse("", "",
                KrcLyricCodec.toEnhancedTimeline(decrypted));
        assertFalse(timeline.isEmpty());
        assertTrue(timeline.at(30_000L).wordTimed);
    }

    @Test public void kugouKrcLanguageReturnsTranslation() throws Exception {
        requireLive();
        JSONObject root = new JSONObject(LyricHttp.get(
                "https://songsearch.kugou.com/song_search_v2?keyword="
                        + LyricHttp.encode("Lemon 米津玄師")
                        + "&page=1&pagesize=10&userid=-1&clientver=&platform=WebFilter",
                "https://www.kugou.com/"));
        JSONArray songs = root.optJSONObject("data").optJSONArray("lists");
        String hash = songs.optJSONObject(0).optString("FileHash", "");
        JSONObject search = new JSONObject(LyricHttp.get(
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash=" + hash,
                "https://www.kugou.com/"));
        JSONObject candidate = search.optJSONArray("candidates").optJSONObject(0);
        JSONObject download = new JSONObject(LyricHttp.get(
                "https://lyrics.kugou.com/download?ver=1&client=pc&id="
                        + LyricHttp.encode(candidate.optString("id")) + "&accesskey="
                        + LyricHttp.encode(candidate.optString("accesskey"))
                        + "&fmt=krc&charset=utf8", "https://www.kugou.com/"));
        String decrypted = KrcLyricCodec.decrypt(Base64.getDecoder().decode(
                download.optString("content")));
        String language = KrcLyricCodec.encodedLanguage(decrypted);
        assertFalse(language.isEmpty());
        String translated = KrcLyricCodec.toTranslationLrc(decrypted,
                new String(Base64.getDecoder().decode(language), StandardCharsets.UTF_8));
        assertFalse(translated.isEmpty());
    }

    @Test public void recordedCarTrackCanBeFoundOnNetEase() throws Exception {
        requireLive();
        String title = "Nothin' on Me";
        String artist = "Leah Marie Perez";
        JSONObject search = new JSONObject(LyricHttp.request("POST",
                "https://music.163.com/api/search/get/web",
                "https://music.163.com/",
                "s=" + LyricHttp.encode(title + " " + artist)
                        + "&type=1&limit=10&offset=0"));
        JSONArray songs = search.optJSONObject("result").optJSONArray("songs");
        long songId = -1L;
        for (int index = 0; index < songs.length(); index++) {
            JSONObject song = songs.optJSONObject(index);
            JSONArray artists = song.optJSONArray("artists");
            String candidateArtist = artists == null || artists.length() == 0 ? ""
                    : artists.optJSONObject(0).optString("name", "");
            if (NetEaseLyricClient.matchScore(title, artist, 217_000L,
                    song.optString("name", ""), candidateArtist,
                    song.optLong("duration", -1L)) >= 100) {
                songId = song.optLong("id", -1L);
                break;
            }
        }
        assertTrue(songId > 0L);
        JSONObject lyric = new JSONObject(LyricHttp.get(
                "https://music.163.com/api/song/lyric?id=" + songId
                        + "&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1",
                "https://music.163.com/"));
        assertFalse(LrcTimeline.parse(
                lyric.optJSONObject("lrc").optString("lyric", ""), "").isEmpty());
    }

    private static void requireLive() {
        Assume.assumeTrue("1".equals(System.getenv("LIVE_LYRIC_TEST")));
    }
}
