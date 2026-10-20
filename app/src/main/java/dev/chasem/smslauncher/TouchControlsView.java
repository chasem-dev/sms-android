package dev.chasem.smslauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.DisplayCutout;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A GameCube controller drawn over the game. It sends what it is pressed
 * with as a controller (see org.libsdl.app.TouchController), hides while a
 * real controller is used, and comes back when the screen is touched.
 */
final class TouchControlsView extends View {
    interface Output { void button(int keycode, boolean down); void axis(int axis, float value); }
    private static final int BUTTON = 0, TRIGGER = 1, STICK = 2, DPAD = 3;
    // Android's D-pad keys, in the bit order of Control.directions.
    private static final int[] DPAD_KEYS = {KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT};
    private static final int IDLE_ALPHA = 140, HELD_ALPHA = 230, GATE_ALPHA = 70;
    // A button is held at least this long, so the game, which looks once a
    // frame, sees the quickest tap.
    private static final long SHORTEST_PRESS_MS = 50;

    private static final class Control {
        final int kind, code, axisY;
        final Bitmap image;
        float x, y, w, h; // centre and size
        int holders, directions;
        long pressedAt;
        float dx, dy; // a stick's position, -1 to 1
        Control(int kind, int code, int axisY, Bitmap image) { this.kind = kind; this.code = code; this.axisY = axisY; this.image = image; }
        float reach() { return Math.max(w, h) * (kind == STICK ? 0.62f : 0.58f); }
    }
    private final Output out;
    private final List<Control> controls = new ArrayList<>();
    private final SparseArray<Control> pointers = new SparseArray<>();
    private final Control l, r, z, a, b, x, y, start, stick, cStick, dpad;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int insetLeft, insetRight;
    private boolean hidden;

    TouchControlsView(Context context, Output out) {
        super(context);
        this.out = out;
        l = add(TRIGGER, 4, R.drawable.touch_l);
        r = add(TRIGGER, 5, R.drawable.touch_r);
        z = add(BUTTON, KeyEvent.KEYCODE_BUTTON_R1, R.drawable.touch_z);
        stick = add(STICK, 0, R.drawable.touch_stick);
        cStick = add(STICK, 2, R.drawable.touch_cstick);
        dpad = add(DPAD, 0, R.drawable.touch_dpad);
        start = add(BUTTON, KeyEvent.KEYCODE_BUTTON_START, R.drawable.touch_start);
        y = add(BUTTON, KeyEvent.KEYCODE_BUTTON_Y, R.drawable.touch_y);
        x = add(BUTTON, KeyEvent.KEYCODE_BUTTON_X, R.drawable.touch_x);
        b = add(BUTTON, KeyEvent.KEYCODE_BUTTON_B, R.drawable.touch_b);
        a = add(BUTTON, KeyEvent.KEYCODE_BUTTON_A, R.drawable.touch_a);
    }
    private Control add(int kind, int code, int drawable) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
        Control c = new Control(kind, code, code + 1, BitmapFactory.decodeResource(getResources(), drawable, options));
        controls.add(c);
        return c;
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        DisplayCutout cut = insets.getDisplayCutout();
        insetLeft = cut == null ? 0 : cut.getSafeInsetLeft(); insetRight = cut == null ? 0 : cut.getSafeInsetRight();
        place(getWidth(), getHeight());
        return super.onApplyWindowInsets(insets);
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { place(w, h); }
    // Like a GameCube controller held in both hands: sizes go by the screen's
    // height, unless it is too narrow for both halves.
    private void place(int width, int height) {
        if (width == 0 || height == 0) return;
        float s = Math.min(height, width / 1.9f), m = 0.05f * s;
        float left = insetLeft + m, right = width - insetRight - m, top = m, bottom = height - m;
        set(l, left + 0.11f * s, top + 0.065f * s, 0.22f * s);
        set(r, right - 0.11f * s, top + 0.065f * s, 0.22f * s);
        set(z, right - 0.11f * s, top + 0.2f * s, 0.24f * s);
        set(stick, left + 0.2f * s, bottom - 0.45f * s, 0.36f * s);
        set(dpad, left + 0.47f * s, bottom - 0.15f * s, 0.26f * s);
        set(a, right - 0.25f * s, bottom - 0.4f * s, 0.18f * s);
        set(b, a.x - 0.15f * s, a.y + 0.12f * s, 0.12f * s);
        set(x, a.x + 0.155f * s, a.y - 0.03f * s, 0.15f * s);
        set(y, a.x - 0.02f * s, a.y - 0.155f * s, 0.17f * s);
        set(cStick, right - 0.55f * s, bottom - 0.13f * s, 0.25f * s);
        set(start, width / 2f, bottom - 0.07f * s, 0.15f * s);
        // Keep edge swipes over the controls from going Back (out of the game).
        List<Rect> exclusions = new ArrayList<>();
        for (Control c : controls) exclusions.add(new Rect(Math.round(c.x - c.reach()), Math.round(c.y - c.reach()),
            Math.round(c.x + c.reach()), Math.round(c.y + c.reach())));
        setSystemGestureExclusionRects(exclusions);
        invalidate();
    }
    private static void set(Control c, float x, float y, float width) {
        c.x = x; c.y = y; c.w = width; c.h = width * c.image.getHeight() / c.image.getWidth();
    }

    /** A real controller was used: hide until the screen is touched. */
    void controllerUsed() {
        if (hidden) return;
        releaseAll();
        hidden = true;
        invalidate();
    }
    void releaseAll() {
        for (Control c : controls) if (c.holders > 0) { c.holders = 1; release(c); }
        pointers.clear();
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), index = event.getActionIndex();
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN: {
            hidden = false;
            Control c = at(event.getX(index), event.getY(index));
            if (c != null && press(c)) {
                pointers.put(event.getPointerId(index), c);
                move(c, event.getX(index), event.getY(index));
            }
            break;
        }
        case MotionEvent.ACTION_MOVE:
            for (int i = 0; i < event.getPointerCount(); i++) {
                Control c = pointers.get(event.getPointerId(i));
                if (c == null) continue;
                if (c.kind == STICK || c.kind == DPAD) { move(c, event.getX(i), event.getY(i)); continue; }
                // A thumb slides from one button to the next (A to B);
                // off the buttons, it keeps holding the last.
                Control next = at(event.getX(i), event.getY(i));
                if (next != null && next != c && (next.kind == BUTTON || next.kind == TRIGGER)) {
                    release(c); press(next); pointers.put(event.getPointerId(i), next);
                }
            }
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP: {
            Control c = pointers.get(event.getPointerId(index));
            pointers.remove(event.getPointerId(index));
            if (c != null) release(c);
            break;
        }
        case MotionEvent.ACTION_CANCEL:
            releaseAll();
            break;
        }
        invalidate();
        return true;
    }
    // The control nearest a touch, for its size, within reach.
    private Control at(float px, float py) {
        Control best = null; float bestDistance = 1;
        for (Control c : controls) {
            float d = (float)Math.hypot(px - c.x, py - c.y) / c.reach();
            if (d < bestDistance) { best = c; bestDistance = d; }
        }
        return best;
    }
    private boolean press(Control c) {
        if ((c.kind == STICK || c.kind == DPAD) && c.holders > 0) return false;
        if (c.holders++ > 0) return true;
        if (c.kind == BUTTON || c.kind == TRIGGER) {
            c.pressedAt = SystemClock.uptimeMillis();
            send(c, true);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } else if (c.kind == STICK) { out.axis(c.code, 0); out.axis(c.axisY, 0); }
        return true;
    }
    private void release(Control c) {
        if (c.holders == 0 || --c.holders > 0) return;
        if (c.kind == BUTTON || c.kind == TRIGGER) {
            long wait = c.pressedAt + SHORTEST_PRESS_MS - SystemClock.uptimeMillis();
            if (wait <= 0) send(c, false);
            else postDelayed(() -> { if (c.holders == 0) send(c, false); }, wait);
        } else if (c.kind == STICK) { c.dx = c.dy = 0; out.axis(c.code, 0); out.axis(c.axisY, 0); }
        else directions(c, 0);
    }
    private void send(Control c, boolean down) {
        if (c.kind == BUTTON) out.button(c.code, down);
        // SDL takes an axis's first value as its resting one, so the rest
        // goes first.
        else if (down) { out.axis(c.code, -1); out.axis(c.code, 1); }
        else out.axis(c.code, -1);
    }
    private void move(Control c, float px, float py) {
        float dx = px - c.x, dy = py - c.y;
        if (c.kind == STICK) {
            // Fully pushed a little inside the edge, which a thumb finds.
            float full = c.w * 0.4f, length = (float)Math.hypot(dx, dy) / full;
            if (length > 1) { dx /= length; dy /= length; }
            c.dx = dx / full; c.dy = dy / full;
            out.axis(c.code, c.dx); out.axis(c.axisY, c.dy);
        } else if (c.kind == DPAD) {
            // Eight ways: the diagonals are the 45 degrees between each pair.
            float length = (float)Math.hypot(dx, dy), band = 0.38f * length;
            int dirs = 0;
            if (length > 0.12f * c.w) {
                if (dy < -band) dirs |= 1; else if (dy > band) dirs |= 2;
                if (dx < -band) dirs |= 4; else if (dx > band) dirs |= 8;
            }
            directions(c, dirs);
        }
    }
    private void directions(Control c, int dirs) {
        for (int i = 0; i < 4; i++) if (((c.directions ^ dirs) >> i & 1) != 0) out.button(DPAD_KEYS[i], (dirs >> i & 1) != 0);
        c.directions = dirs;
    }

    @Override protected void onDraw(Canvas canvas) {
        if (hidden) return;
        for (Control c : controls) {
            boolean held = c.holders > 0;
            if (c.kind == STICK) {
                // The gate stays put; the stick's top moves within it.
                draw(canvas, c.image, c.x, c.y, c.w, c.h, GATE_ALPHA);
                float travel = c.w * 0.25f;
                draw(canvas, c.image, c.x + c.dx * travel, c.y + c.dy * travel, c.w * 0.62f, c.h * 0.62f, held ? HELD_ALPHA : IDLE_ALPHA);
            } else if (c.kind == DPAD) {
                draw(canvas, c.image, c.x, c.y, c.w, c.h, held ? HELD_ALPHA : IDLE_ALPHA);
                // A pressed arm darkens, as in the artwork's pressed D-pads.
                paint.setColor(0xff000000); paint.setAlpha(150);
                float arm = c.w / 6, reach = c.w * 0.44f, round = c.w * 0.04f;
                if ((c.directions & 1) != 0) { rect.set(c.x - arm, c.y - reach, c.x + arm, c.y - arm); canvas.drawRoundRect(rect, round, round, paint); }
                if ((c.directions & 2) != 0) { rect.set(c.x - arm, c.y + arm, c.x + arm, c.y + reach); canvas.drawRoundRect(rect, round, round, paint); }
                if ((c.directions & 4) != 0) { rect.set(c.x - reach, c.y - arm, c.x - arm, c.y + arm); canvas.drawRoundRect(rect, round, round, paint); }
                if ((c.directions & 8) != 0) { rect.set(c.x + arm, c.y - arm, c.x + reach, c.y + arm); canvas.drawRoundRect(rect, round, round, paint); }
            } else {
                float scale = held ? 0.92f : 1;
                draw(canvas, c.image, c.x, c.y, c.w * scale, c.h * scale, held ? HELD_ALPHA : IDLE_ALPHA);
            }
        }
    }
    private void draw(Canvas canvas, Bitmap image, float cx, float cy, float w, float h, int alpha) {
        rect.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        paint.setAlpha(alpha);
        canvas.drawBitmap(image, null, rect, paint);
    }
}
