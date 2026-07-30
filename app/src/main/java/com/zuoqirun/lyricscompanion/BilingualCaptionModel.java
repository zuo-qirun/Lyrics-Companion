package com.zuoqirun.lyricscompanion;

import android.content.Context;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads the four required zh-en INT8 model files directly, never the 511 MB release bundle. */
final class BilingualCaptionModel {
    private static final String VERSION = "zipformer-zh-en-2023-02-20-int8";
    private static final String BASE_URL = "https://huggingface.co/csukuangfj/"
            + "sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20/resolve/main/";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Asset[] ASSETS = new Asset[]{
            new Asset("encoder-epoch-99-avg-1.int8.onnx", 181895032L, "SHA-256",
                    "8fa764187a261844f859d7143ebaa563af5d10adfece4c18a8f414c88cba2a9b"),
            new Asset("decoder-epoch-99-avg-1.onnx", 13876452L, "SHA-256",
                    "2e3b5ec371f8899ee6acd829fd753ba45772df57a91bdf37cde3136354e7db7d"),
            new Asset("joiner-epoch-99-avg-1.int8.onnx", 3228404L, "SHA-256",
                    "1ed689c5ed19dbaa725d9d191bb4822b5f4855a39e1ffd28cbc1f340d25b2ee0"),
            new Asset("tokens.txt", 56317L, "SHA-1", "980dd6cd2d71532898b1eac4a4ac9a91302083b6")
    };

    interface Listener { void onComplete(); void onError(String message); }
    private BilingualCaptionModel() { }

    static File directory(Context context) {
        return new File(context.getFilesDir(), "caption-models/" + VERSION);
    }

    static boolean isInstalled(Context context) {
        File dir = directory(context);
        File ready = new File(dir, ".ready");
        if (!ready.isFile()) return false;
        for (Asset asset : ASSETS) {
            File file = new File(dir, asset.name);
            if (!file.isFile() || file.length() != asset.size) return false;
        }
        return true;
    }

    static void downloadAsync(final Context context, final Listener listener) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                File dir = directory(app);
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建模型目录");
                for (int index = 0; index < ASSETS.length; index++) {
                    Asset asset = ASSETS[index];
                    RealtimeCaptionStore.status(RealtimeCaptionState.Status.STARTING, "中英离线模型",
                            "正在下载 " + asset.name + "（" + (index + 1) + "/" + ASSETS.length + "）");
                    downloadAndVerify(dir, asset);
                }
                File ready = new File(dir, ".ready");
                try (FileOutputStream output = new FileOutputStream(ready)) {
                    output.write(VERSION.getBytes("UTF-8"));
                }
                RealtimeCaptionStore.status(RealtimeCaptionState.Status.OFF, "中英离线模型", "模型已下载，可开启实时字幕");
                listener.onComplete();
            } catch (Throwable error) {
                String message = message(error);
                RealtimeCaptionStore.status(RealtimeCaptionState.Status.ERROR, "中英离线模型", message);
                listener.onError(message);
            }
        });
    }

    private static void downloadAndVerify(File dir, Asset asset) throws Exception {
        File target = new File(dir, asset.name);
        if (target.isFile() && target.length() == asset.size && verify(target, asset)) return;
        File part = new File(dir, asset.name + ".part");
        long existing = part.isFile() ? part.length() : 0L;
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + asset.name).openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("User-Agent", "LyricsCompanion/1.0");
        if (existing > 0L) connection.setRequestProperty("Range", "bytes=" + existing + "-");
        int code = connection.getResponseCode();
        boolean append = existing > 0L && code == HttpURLConnection.HTTP_PARTIAL;
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            throw new IllegalStateException("模型下载失败（HTTP " + code + "）");
        }
        if (!append) existing = 0L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(part, append)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            long downloaded = existing;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                downloaded += read;
                if ((downloaded & ((1 << 20) - 1)) < read) {
                    int percent = (int) Math.min(100L, downloaded * 100L / asset.size);
                    RealtimeCaptionStore.status(RealtimeCaptionState.Status.STARTING, "中英离线模型",
                            "正在下载 " + asset.name + "：" + percent + "%");
                }
            }
        } finally { connection.disconnect(); }
        if (part.length() != asset.size || !verify(part, asset)) {
            throw new IllegalStateException("模型文件校验失败：" + asset.name);
        }
        if (target.exists() && !target.delete()) throw new IllegalStateException("无法替换旧模型文件");
        if (!part.renameTo(target)) throw new IllegalStateException("无法完成模型安装");
    }

    private static boolean verify(File file, Asset asset) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(asset.digestAlgorithm);
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
        }
        StringBuilder value = new StringBuilder();
        for (byte b : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", b));
        return asset.digest.equals(value.toString());
    }

    private static String message(Throwable error) {
        String value = error == null ? "" : error.getMessage();
        return value == null || value.trim().isEmpty() ? "模型下载失败" : value;
    }

    private static final class Asset {
        final String name; final long size; final String digestAlgorithm; final String digest;
        Asset(String name, long size, String digestAlgorithm, String digest) {
            this.name = name; this.size = size; this.digestAlgorithm = digestAlgorithm; this.digest = digest;
        }
    }
}
