package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads the small, versioned FAQ document and keeps the last valid copy locally. */
final class FaqClient {
    private static final String FAQ_URL = "https://lyrics-companion.zuoqirun.top/faq.json";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private FaqClient() { }

    static FaqDocument cached(Context context) {
        String value = AppPreferences.get(context).getString(AppPreferences.KEY_FAQ_CACHE, "");
        if (value == null || value.trim().isEmpty()) return null;
        try { return parse(new JSONObject(value)); }
        catch (Throwable ignored) { return null; }
    }

    static void fetchAsync(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            FaqDocument cached = cached(appContext);
            try {
                FaqDocument remote = parse(new JSONObject(LyricHttp.get(FAQ_URL, null)));
                AppPreferences.get(appContext).edit()
                        .putString(AppPreferences.KEY_FAQ_CACHE, remote.rawJson).apply();
                complete(callback, new Result(remote, false, true, ""));
            } catch (Throwable error) {
                complete(callback, new Result(cached, true, false, safeMessage(error)));
            }
        });
    }

    private static FaqDocument parse(JSONObject root) throws Exception {
        JSONArray rawItems = root.optJSONArray("items");
        if (rawItems == null || rawItems.length() == 0) {
            throw new IllegalStateException("FAQ 暂无内容");
        }
        List<Item> items = new ArrayList<>();
        for (int index = 0; index < rawItems.length(); index++) {
            JSONObject raw = rawItems.optJSONObject(index);
            if (raw == null) continue;
            String question = raw.optString("question", "").trim();
            if (question.isEmpty()) continue;
            List<Instruction> instructions = new ArrayList<>();
            JSONArray rawInstructions = raw.optJSONArray("instructions");
            if (rawInstructions != null) {
                for (int step = 0; step < rawInstructions.length(); step++) {
                    JSONObject rawInstruction = rawInstructions.optJSONObject(step);
                    if (rawInstruction == null) continue;
                    String title = rawInstruction.optString("title", "").trim();
                    String command = rawInstruction.optString("command", "").trim();
                    if (!title.isEmpty() && !command.isEmpty()) {
                        instructions.add(new Instruction(title, command));
                    }
                }
            }
            items.add(new Item(question, raw.optString("answer", "").trim(),
                    instructions, raw.optString("note", "").trim()));
        }
        if (items.isEmpty()) throw new IllegalStateException("FAQ 暂无有效内容");
        return new FaqDocument(root.optString("updatedAt", "").trim(), items, root.toString());
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }

    private static void complete(Callback callback, Result result) {
        if (callback != null) callback.onResult(result);
    }

    interface Callback { void onResult(Result result); }

    static final class Result {
        final FaqDocument document;
        final boolean fromCache;
        final boolean refreshed;
        final String error;

        Result(FaqDocument document, boolean fromCache, boolean refreshed, String error) {
            this.document = document;
            this.fromCache = fromCache;
            this.refreshed = refreshed;
            this.error = error == null ? "" : error;
        }
    }

    static final class FaqDocument {
        final String updatedAt;
        final List<Item> items;
        final String rawJson;

        FaqDocument(String updatedAt, List<Item> items, String rawJson) {
            this.updatedAt = updatedAt;
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.rawJson = rawJson;
        }
    }

    static final class Item {
        final String question;
        final String answer;
        final List<Instruction> instructions;
        final String note;

        Item(String question, String answer, List<Instruction> instructions, String note) {
            this.question = question;
            this.answer = answer;
            this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
            this.note = note;
        }
    }

    static final class Instruction {
        final String title;
        final String command;

        Instruction(String title, String command) {
            this.title = title;
            this.command = command;
        }
    }
}
