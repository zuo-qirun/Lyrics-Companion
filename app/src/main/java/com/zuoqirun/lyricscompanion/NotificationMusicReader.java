package com.zuoqirun.lyricscompanion;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** API 19 notification fallback. No playback clock is invented from a notification timestamp. */
final class NotificationMusicReader {
    private final Map<String, Entry> cache = new HashMap<>();

    void invalidate(StatusBarNotification sbn) {
        if (sbn != null) cache.remove(key(sbn));
    }

    Entry select(Context context, StatusBarNotification[] notifications) {
        Entry selected = null;
        Map<String, Entry> active = new HashMap<>();
        if (notifications == null) return null;
        for (StatusBarNotification sbn : notifications) {
            if (sbn == null || context.getPackageName().equals(sbn.getPackageName())) continue;
            String key = key(sbn);
            Entry entry = cache.get(key);
            if (entry == null || entry.postTime != sbn.getPostTime()) {
                try { entry = read(context, sbn); }
                catch (RuntimeException ignored) {
                    entry = new Entry(sbn.getPackageName(), "", sbn.getPostTime(), null);
                }
            }
            active.put(key, entry);
            if (entry.track != null && (selected == null || entry.postTime > selected.postTime)) {
                selected = entry;
            }
        }
        cache.clear(); cache.putAll(active);
        return selected;
    }

    private Entry read(Context context, StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        String label = pkg;
        try { label = context.getPackageManager().getApplicationLabel(
                context.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception ignored) { }
        Notification n = sbn.getNotification();
        boolean known = MusicAppRegistry.resolve(pkg, label).known;
        boolean controls = false;
        if (n.actions != null) for (Notification.Action action : n.actions) {
            if (action != null && action.title != null && action.title.toString().trim()
                    .matches("(?i).*(播放|暂停|上一首|下一首|play|pause|previous|next).*")) controls = true;
        }
        boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        NotificationTrackParser.Track track = null;
        if (controls || known && ongoing) {
            Bundle extras = n.extras;
            String title = string(extras, "android.title");
            String text = string(extras, "android.text");
            String sub = string(extras, "android.subText");
            if (title.isEmpty()) title = string(extras, "android.title.big");
            if (text.isEmpty()) text = string(extras, "android.bigText");
            if (text.isEmpty() && extras != null) {
                CharSequence[] lines = extras.getCharSequenceArray("android.textLines");
                if (lines != null && lines.length > 0 && lines[0] != null) text = lines[0].toString();
            }
            track = NotificationTrackParser.parse(title, text, sub, label);
            if (track == null) {
                List<String> texts = new ArrayList<>();
                collectCustom(context, n.bigContentView != null ? n.bigContentView : n.contentView,
                        texts, label);
                if (!texts.isEmpty()) track = NotificationTrackParser.parse(texts.get(0),
                        texts.size() > 1 ? texts.get(1) : "", "", label);
            }
            if (track == null && n.tickerText != null) {
                track = NotificationTrackParser.parse(n.tickerText.toString(), "", "", label);
            }
        }
        MusicPlaybackData playback = track != null && android.os.Build.VERSION.SDK_INT >= 21
                ? NotificationPlaybackReader.read(context, pkg, n, track) : null;
        return new Entry(pkg, label, sbn.getPostTime(), track, playback);
    }

    private static String string(Bundle extras, String key) {
        if (extras == null) return "";
        Object value = extras.get(key);
        return value instanceof CharSequence ? value.toString().trim() : "";
    }

    private static void collectCustom(Context context, RemoteViews remote,
                                      List<String> texts, String label) {
        if (remote == null) return;
        try { collect(remote.apply(context, null), texts, label, 0); }
        catch (RuntimeException ignored) { /* Some vendor layouts cannot inflate outside the player. */ }
    }

    private static void collect(View view, List<String> texts, String label, int depth) {
        if (depth > 12 || texts.size() >= 12 || view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString().trim();
            if (!NotificationTrackParser.isNoise(text, label) && !texts.contains(text)) texts.add(text);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), texts, label, depth + 1);
        }
    }

    private static String key(StatusBarNotification n) {
        return n.getPackageName() + ":" + n.getId() + ":" + n.getTag();
    }

    static final class Entry {
        final String packageName, label;
        final long postTime;
        final NotificationTrackParser.Track track;
        final MusicPlaybackData playback;
        Entry(String pkg, String label, long time, NotificationTrackParser.Track track) {
            this(pkg, label, time, track, null);
        }
        Entry(String pkg, String label, long time, NotificationTrackParser.Track track, MusicPlaybackData playback) {
            packageName = pkg; this.label = label; postTime = time; this.track = track;
            this.playback = playback;
        }
        MusicPlaybackData data() {
            if (playback != null) return playback;
            return new MusicPlaybackData("", track.title, track.artist, null, "", track.mediaUri,
                    -1L, false, MusicPlaybackData.STATE_NONE, -1L, 0L, 0f);
        }
    }
}
