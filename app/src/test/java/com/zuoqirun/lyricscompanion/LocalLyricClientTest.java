package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;

public final class LocalLyricClientTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void findsNestedLyricBeyondTwoThousandEntriesAndRereadsCachedFile() throws Exception {
        File root = temporary.newFolder("usb");
        File music = new File(root, "album");
        org.junit.Assert.assertTrue(music.mkdir());
        for (int i = 0; i < 2100; i++) {
            org.junit.Assert.assertTrue(new File(root, "unrelated-" + i + ".txt").createNewFile());
        }
        File lyric = new File(music, "song.lrc");
        Files.write(lyric.toPath(), "[00:01.000]第一句".getBytes(StandardCharsets.UTF_8));
        LocalLyricClient client = new LocalLyricClient(new android.content.ContextWrapper(null));
        Method search = LocalLyricClient.class.getDeclaredMethod("searchDirectory",
                File.class, Set.class, boolean.class);
        search.setAccessible(true);
        LocalLyricClient.clearLocationCache();
        LocalLyricClient.Hit hit = (LocalLyricClient.Hit) search.invoke(client, root,
                Collections.singleton("song.lrc"), false);
        org.junit.Assert.assertFalse(hit.timeline.isEmpty());
        Files.write(lyric.toPath(), "[00:01.000]更新歌词".getBytes(StandardCharsets.UTF_8));
        hit = (LocalLyricClient.Hit) search.invoke(client, root,
                Collections.singleton("song.lrc"), false);
        org.junit.Assert.assertEquals("更新歌词", hit.timeline.at(1500).lyric);
        File moved = new File(root, "other-album");
        org.junit.Assert.assertTrue(moved.mkdir());
        Files.move(lyric.toPath(), new File(moved, "song.lrc").toPath());
        hit = (LocalLyricClient.Hit) search.invoke(client, root,
                Collections.singleton("song.lrc"), false);
        org.junit.Assert.assertEquals("更新歌词", hit.timeline.at(1500).lyric);
        LocalLyricClient.clearLocationCache();
    }

    @Test public void readsUtf16SidecarsWithAndWithoutBom() {
        String lyric = "[00:01.000]第一句\n[00:03.000]第二句";
        byte[] littleEndian = lyric.getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[littleEndian.length + 2];
        withBom[0] = (byte) 0xff;
        withBom[1] = (byte) 0xfe;
        System.arraycopy(littleEndian, 0, withBom, 2, littleEndian.length);
        assertEquals(lyric, LocalLyricClient.decodeLyricText(withBom));
        assertEquals(lyric, LocalLyricClient.decodeLyricText(littleEndian));
        assertEquals(lyric, LocalLyricClient.decodeLyricText(
                lyric.getBytes(StandardCharsets.UTF_16BE)));
    }
}
