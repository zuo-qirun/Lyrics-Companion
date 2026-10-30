package com.zuoqirun.lyricscompanion;

import android.view.ViewTreeObserver;
import android.widget.ScrollView;

/**
 * 在整页重建后把滚动位置放回原处，且必须在首帧绘制之前完成。
 *
 * <p>以前用 {@code scroll.post(() -> scroll.scrollTo(0, y))}：post 的 runnable 要等第一帧已经
 * 画完才执行，所以重建后的第一帧先按滚动位置 0（页面最顶部）绘制，下一帧才跳回原位置——这就是
 * “点按钮后界面闪一下顶部画面”的来源。{@code onPreDraw} 在 layout 之后、这一帧上屏之前执行，
 * 同一帧里就带着旧滚动位置绘制，看不到跳变。
 */
final class ScrollRestore {
    private ScrollRestore() { }

    static void beforeFirstDraw(ScrollView scroll, int scrollY) {
        if (scroll == null) return;
        final int target = Math.max(0, scrollY);
        final boolean[] done = {false};
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (done[0]) return true;
                done[0] = true;
                ViewTreeObserver observer = scroll.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                scroll.scrollTo(0, target);
                return true;
            }
        });
    }
}
