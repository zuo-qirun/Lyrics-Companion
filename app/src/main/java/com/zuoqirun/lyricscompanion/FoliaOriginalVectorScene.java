// SPDX-License-Identifier: AGPL-3.0-only
// Native Canvas execution of original Folia vector recordings. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Shared GPU-friendly path tree; all geometry is parsed once, outside the animation loop. */
final class FoliaOriginalVectorScene {
    interface Motion {
        /** Adds the node's animated local transform to its static transform and returns opacity. */
        float transform(Node node, Matrix local);
    }

    interface Visitor {
        void draw(Draw draw, Matrix transform, float opacity);
    }

    static final class Draw {
        final Path path;
        final Path silhouette;
        final int color;
        final float alpha;
        final boolean fill;
        final float width;

        Draw(JSONObject json) throws JSONException {
            path = new Path();
            commands(path, json.getJSONArray("path"));
            JSONArray holes = json.optJSONArray("holes");
            if (holes != null && holes.length() > 0) {
                commands(path, holes);
                path.setFillType(Path.FillType.EVEN_ODD);
            }
            color = json.optInt("color", 2);
            alpha = (float) json.optDouble("alpha", 1);
            fill = json.optBoolean("fill");
            width = (float) json.optDouble("width", 1);
            if (fill) {
                silhouette = path;
            } else {
                silhouette = new Path();
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(width);
                stroke.getFillPath(path, silhouette);
            }
        }
    }

    static final class Node {
        final float x, y, rotation, pivotX, pivotY, driftPhase;
        final JSONObject motion;
        final List<Node> children = new ArrayList<>();
        final List<Draw> draws = new ArrayList<>();
        final Matrix local = new Matrix();
        final Matrix world = new Matrix();

        Node(JSONObject json) throws JSONException {
            x = (float) json.optDouble("x");
            y = (float) json.optDouble("y");
            rotation = (float) json.optDouble("rotation");
            JSONObject pivot = json.optJSONObject("pivot");
            pivotX = pivot == null ? 0 : (float) pivot.optDouble("x");
            pivotY = pivot == null ? 0 : (float) pivot.optDouble("y");
            driftPhase = (float) json.optDouble("driftPhase");
            motion = json.optJSONObject("motion");
            JSONArray childJson = json.optJSONArray("children");
            if (childJson != null) for (int i = 0; i < childJson.length(); i++) {
                children.add(new Node(childJson.getJSONObject(i)));
            }
            JSONArray drawJson = json.optJSONArray("draws");
            if (drawJson != null) for (int i = 0; i < drawJson.length(); i++) {
                draws.add(new Draw(drawJson.getJSONObject(i)));
            }
        }
    }

    final List<Node> roots = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix identity = new Matrix();

    FoliaOriginalVectorScene(JSONArray json) throws JSONException {
        for (int i = 0; i < json.length(); i++) roots.add(new Node(json.getJSONObject(i)));
    }

    void draw(Canvas canvas, int[] palette, Motion motion) {
        visit(motion, (draw, transform, opacity) -> {
            int save = canvas.save();
            canvas.concat(transform);
            paint.setStyle(draw.fill ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(draw.width);
            paint.setColor(palette[Math.max(0, Math.min(palette.length - 1, draw.color))]);
            paint.setAlpha(Math.max(0, Math.min(255, (int) (255 * draw.alpha * opacity))));
            canvas.drawPath(draw.path, paint);
            canvas.restoreToCount(save);
        });
    }

    void visit(Motion motion, Visitor visitor) {
        for (Node node : roots) visit(node, identity, 1, motion, visitor);
    }

    private void visit(Node node, Matrix parent, float alpha, Motion motion, Visitor visitor) {
        node.local.reset();
        node.local.postTranslate(-node.pivotX, -node.pivotY);
        node.local.postRotate((float) Math.toDegrees(node.rotation));
        node.local.postTranslate(node.x, node.y);
        float ownAlpha = motion == null ? 1 : motion.transform(node, node.local);
        if (ownAlpha * alpha < 0.001f) return;
        node.world.setConcat(parent, node.local);
        for (Draw draw : node.draws) visitor.draw(draw, node.world, ownAlpha * alpha);
        for (Node child : node.children) visit(child, node.world, ownAlpha * alpha, motion, visitor);
    }

    private static void commands(Path path, JSONArray array) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            JSONArray item = array.getJSONArray(i);
            switch (item.getString(0)) {
                case "poly": {
                    JSONArray points = item.getJSONArray(1);
                    if (points.length() < 2) break;
                    path.moveTo((float) points.getDouble(0), (float) points.getDouble(1));
                    for (int p = 2; p + 1 < points.length(); p += 2) {
                        path.lineTo((float) points.getDouble(p), (float) points.getDouble(p + 1));
                    }
                    path.close();
                    break;
                }
                case "rect": {
                    float x = (float) item.getDouble(1), y = (float) item.getDouble(2);
                    path.addRect(x, y, x + (float) item.getDouble(3), y + (float) item.getDouble(4), Path.Direction.CW);
                    break;
                }
                case "circle":
                    path.addCircle((float) item.getDouble(1), (float) item.getDouble(2), (float) item.getDouble(3), Path.Direction.CW);
                    break;
                case "move":
                    path.moveTo((float) item.getDouble(1), (float) item.getDouble(2));
                    break;
                case "line":
                    path.lineTo((float) item.getDouble(1), (float) item.getDouble(2));
                    break;
                default:
                    throw new JSONException("Unsupported original Folia vector command: " + item.getString(0));
            }
        }
    }
}
