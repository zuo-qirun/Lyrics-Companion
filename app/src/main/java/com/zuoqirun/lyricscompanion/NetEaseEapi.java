package com.zuoqirun.lyricscompanion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Encodes the public NetEase lyric request used by its desktop web client. */
final class NetEaseEapi {
    private static final String PATH = "/api/song/lyric/v1";
    private static final String SEPARATOR = "-36cd479b6b5-";
    private static final byte[] KEY = "e82ckenh8dichen8".getBytes(StandardCharsets.US_ASCII);
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private NetEaseEapi() {}

    static String formBody(long songId) throws Exception {
        return formBody(songId, PATH);
    }

    static String formBody(long songId, String apiPath) throws Exception {
        if (apiPath == null || !apiPath.matches("/api/[A-Za-z0-9/_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid EAPI path");
        }
        String payload = "{\"id\":" + songId + ",\"cp\":false,\"tv\":0,\"lv\":0,"
                + "\"rv\":0,\"kv\":0,\"yv\":0,\"ytv\":0,\"yrv\":0}";
        byte[] digestBytes = MessageDigest.getInstance("MD5").digest(
                ("nobody" + apiPath + "use" + payload + "md5forencrypt")
                        .getBytes(StandardCharsets.UTF_8));
        String digest = hex(digestBytes).toLowerCase(Locale.ROOT);
        String plaintext = apiPath + SEPARATOR + payload + SEPARATOR + digest;
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"));
        return "params=" + hex(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    private static String hex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = HEX[value >>> 4];
            output[index * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(output);
    }
}
