package com.geocerca.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FenceView extends View {
    public interface PointTapListener { void onPointTapped(int index); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<GeoPoint> allPoints = new ArrayList<>();
    private List<Integer> order = new ArrayList<>();
    private FenceConfig config = new FenceConfig();
    private boolean closed = false;
    private PointTapListener listener;

    private double minX, maxX, minY, maxY;
    private float scale = 1f, offX = 0, offY = 0;
    private final float pad = 62f;

    public FenceView(Context context) { super(context); setBackgroundColor(Color.rgb(250,250,250)); }

    public void setPointTapListener(PointTapListener l) { listener = l; }
    public void setConfig(FenceConfig c) { config = c; invalidate(); }
    public void setData(List<GeoPoint> points, List<Integer> selectedOrder, boolean isClosed) {
        allPoints = points == null ? new ArrayList<>() : points;
        order = selectedOrder == null ? new ArrayList<>() : selectedOrder;
        closed = isClosed;
        computeTransform(getWidth(), getHeight());
        invalidate();
    }

    private void computeTransform(int w, int h) {
        if (allPoints.isEmpty() || w <= 0 || h <= 0) return;
        minX = maxX = allPoints.get(0).x;
        minY = maxY = allPoints.get(0).y;
        for (GeoPoint p : allPoints) {
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
        }
        double dx = Math.max(1e-9, maxX - minX);
        double dy = Math.max(1e-9, maxY - minY);
        float sx = (float)((w - 2 * pad) / dx);
        float sy = (float)((h - 2 * pad) / dy);
        scale = Math.max(0.00001f, Math.min(sx, sy));
        float drawW = (float)(dx * scale);
        float drawH = (float)(dy * scale);
        offX = (w - drawW) / 2f;
        offY = (h - drawH) / 2f;
    }

    private float px(GeoPoint p) { return offX + (float)((p.x - minX) * scale); }
    private float py(GeoPoint p) { return getHeight() - (offY + (float)((p.y - minY) * scale)); }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { computeTransform(w, h); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (allPoints.isEmpty()) {
            paint.setColor(Color.DKGRAY); paint.setTextSize(34); paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("Importe um KML ou TXT", getWidth()/2f, getHeight()/2f, paint);
            return;
        }

        paint.setTextSize(26); paint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < allPoints.size(); i++) {
            GeoPoint p = allPoints.get(i);
            float x = px(p), y = py(p);
            paint.setColor(order.contains(i) ? Color.rgb(25,95,55) : Color.GRAY);
            c.drawCircle(x, y, order.contains(i) ? 8 : 6, paint);
            paint.setColor(Color.DKGRAY);
            c.drawText(p.name, x + 10, y - 10, paint);
        }

        if (order.size() < 2) return;

        paint.setStrokeWidth(5); paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.rgb(45,45,45));
        for (List<Integer> polygonOrder : selectedPolygonOrders()) {
            for (int i = 0; i < polygonOrder.size() - 1; i++) {
                drawSegment(c, allPoints.get(polygonOrder.get(i)), allPoints.get(polygonOrder.get(i + 1)), true);
            }
            if (closed && polygonOrder.size() >= 3) {
                drawSegment(c,
                        allPoints.get(polygonOrder.get(polygonOrder.size() - 1)),
                        allPoints.get(polygonOrder.get(0)),
                        true);
            }
        }
        paint.setStyle(Paint.Style.FILL);

        if (closed && order.size() >= 3) drawCornerStructures(c);
    }

    private void drawSegment(Canvas c, GeoPoint a, GeoPoint b, boolean posts) {
        float ax = px(a), ay = py(a), bx = px(b), by = py(b);
        paint.setColor(Color.rgb(35,35,35)); paint.setStrokeWidth(4); paint.setStyle(Paint.Style.STROKE);
        c.drawLine(ax, ay, bx, by, paint);
        paint.setStyle(Paint.Style.FILL);

        double d = FenceCalculator.distanceM(a,b);
        paint.setTextSize(24); paint.setColor(Color.rgb(20,70,120)); paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(String.format(Locale.getDefault(), "%.2f m", d), (ax+bx)/2f, (ay+by)/2f - 8, paint);

        if (!posts) return;
        int intervals = Math.max(1, (int)Math.ceil(d / Math.max(0.1, config.postSpacingM)));
        paint.setColor(Color.rgb(80,80,80));
        for (int j=1; j<intervals; j++) {
            float t = j/(float)intervals;
            float x = ax + (bx-ax)*t, y = ay + (by-ay)*t;
            c.drawCircle(x,y,5.5f,paint);
        }
    }

    private List<List<Integer>> selectedPolygonOrders() {
        Map<Integer, List<Integer>> grouped = new LinkedHashMap<>();
        for (int idx : order) {
            if (idx < 0 || idx >= allPoints.size()) continue;
            GeoPoint p = allPoints.get(idx);
            grouped.computeIfAbsent(p.polygonId, k -> new ArrayList<>()).add(idx);
        }
        return new ArrayList<>(grouped.values());
    }

    private void drawCornerStructures(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        for (List<Integer> polygonOrder : selectedPolygonOrders()) {
            if (polygonOrder.size() < 3) continue;

            List<GeoPoint> polygon = new ArrayList<>();
            for (int idx : polygonOrder) polygon.add(allPoints.get(idx));

            for (int pos = 0; pos < polygon.size(); pos++) {
                if (!FenceCalculator.isCorner(polygon, pos)) continue;

                GeoPoint corner = polygon.get(pos);
                GeoPoint prev = polygon.get((pos - 1 + polygon.size()) % polygon.size());
                GeoPoint next = polygon.get((pos + 1) % polygon.size());
                float cx = px(corner), cy = py(corner);

                paint.setColor(Color.rgb(120,65,20));
                c.drawRect(new RectF(cx-9,cy-9,cx+9,cy+9),paint);

                if (config.bracesPerCorner > 0) drawBrace(c, cx, cy, px(prev), py(prev));
                if (config.bracesPerCorner > 1) drawBrace(c, cx, cy, px(next), py(next));
            }
        }
    }

    private void drawBrace(Canvas c, float cx, float cy, float tx, float ty) {
        float dx = tx-cx, dy = ty-cy;
        float len = (float)Math.hypot(dx,dy);
        if (len < 1) return;
        float ux=dx/len, uy=dy/len;
        float dist=Math.min(34f, len*0.25f);
        float bx=cx+ux*dist, by=cy+uy*dist;
        paint.setColor(Color.rgb(180,90,20)); paint.setStrokeWidth(3); paint.setStyle(Paint.Style.STROKE);
        c.drawLine(cx,cy,bx,by,paint);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(bx,by,7,paint);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP || allPoints.isEmpty()) return true;
        int best=-1; float bestD=55f;
        for (int i=0;i<allPoints.size();i++) {
            float dx=e.getX()-px(allPoints.get(i)), dy=e.getY()-py(allPoints.get(i));
            float d=(float)Math.hypot(dx,dy);
            if (d<bestD) { bestD=d; best=i; }
        }
        if (best>=0 && listener!=null) listener.onPointTapped(best);
        return true;
    }

    public Bitmap snapshot() {
        Bitmap b = Bitmap.createBitmap(Math.max(1,getWidth()), Math.max(1,getHeight()), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b); draw(c); return b;
    }
}
