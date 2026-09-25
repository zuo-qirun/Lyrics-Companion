package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public final class LocalLyricClientTest {
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
