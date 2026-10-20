package dev.chasem.smslauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The launcher's look, after the desktop SMS Launcher: seaside art, cream
 * cards, teal and sun yellow. Everything a controller can reach shows a
 * ring when it has focus, as touch never gives focus.
 */
final class Ui {
    static final int INK = 0xff17343d, BODY = 0xff334b51, MUTED = 0xff62767c, FAINT = 0xff7a8b8f,
        PAGE = 0xfff7f5ef, CARD = 0xfffffdf9, BORDER = 0xffe7e2d7, DIVIDER = 0xffe6ebea,
        TEAL = 0xff158188, TEAL_DARK = 0xff125e66, TEAL_SOFT = 0xffe7f1f0, TEAL_PRESSED = 0xffd9eae8,
        SUN = 0xffffc75a, SUN_LIGHT = 0xffffdb8c, SUN_SHADOW = 0xffb87f25, SUN_TEXT = 0xffffda91,
        NAVY = 0xff153c4d, NIGHT = 0xff0f2a3f, MIST = 0xffd1e9e8, GOOD = 0xff3ed598;
    private static final int[] FOCUSED = {android.R.attr.state_focused}, PRESSED = {android.R.attr.state_pressed},
        CHECKED = {android.R.attr.state_checked}, SELECTED = {android.R.attr.state_selected},
        DISABLED = {-android.R.attr.state_enabled}, ANY = {};

    private final Context context;
    private final float density;
    Ui(Context context) { this.context = context; density = context.getResources().getDisplayMetrics().density; }
    int dp(float value) { return Math.round(value * density); }

    static Typeface weight(int weight) { return Typeface.create(Typeface.DEFAULT, weight, false); }
    GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    GradientDrawable round(int color, float radius, int stroke, float strokeWidth) {
        GradientDrawable d = round(color, radius); d.setStroke(Math.max(1, dp(strokeWidth)), stroke); return d;
    }
    static StateListDrawable states(Drawable normal, Drawable pressed, Drawable focused) {
        StateListDrawable d = new StateListDrawable();
        d.addState(PRESSED, pressed); d.addState(FOCUSED, focused); d.addState(ANY, normal);
        return d;
    }

    TextView text(String value, float sp, int color, int weight) {
        TextView v = new TextView(context);
        v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(weight(weight));
        v.setIncludeFontPadding(false);
        return v;
    }
    /** Small spaced capitals over a heading, as PREFERENCES is. */
    TextView eyebrow(String value, int color) {
        TextView v = text(value, 11, color, 800); v.setLetterSpacing(0.12f); v.setAllCaps(true); return v;
    }
    TextView badge(String value, int background, int color) {
        TextView v = text(value, 10, color, 750);
        v.setPadding(dp(7), dp(3), dp(7), dp(3)); v.setBackground(round(background, 5));
        return v;
    }
    void setBadge(TextView v, String value, String tone) {
        int bg = "good".equals(tone) ? 0xffe0f2eb : "warn".equals(tone) ? 0xfffff0d7 : 0xffeef1f0;
        int fg = "good".equals(tone) ? 0xff187657 : "warn".equals(tone) ? 0xff95671a : 0xff66777d;
        v.setText(value); v.setTextColor(fg); v.setBackground(round(bg, 5));
    }

    private TextView button(String label, Drawable background, ColorStateList color, float sp) {
        TextView b = text(label, sp, Color.WHITE, 700);
        b.setTextColor(color); b.setGravity(Gravity.CENTER); b.setBackground(background);
        b.setFocusable(true); b.setClickable(true); b.setSingleLine(true);
        b.setPadding(dp(16), dp(9), dp(16), dp(9));
        return b;
    }
    private ColorStateList textColors(int normal, int disabled) {
        return new ColorStateList(new int[][]{DISABLED, ANY}, new int[]{disabled, normal});
    }
    /** The sun-yellow Play button, raised on a darker edge. */
    TextView primary(String label) {
        TextView b = button(label, primaryBackground(), textColors(NAVY, 0x99153c4d), 21);
        b.setTypeface(weight(800));
        return b;
    }
    Drawable primaryBackground() { return states(raised(SUN, 0), raised(SUN_LIGHT, 0), raised(SUN, 3)); }
    Drawable glassBackground() {
        return states(round(0x1fffffff, 12, 0x80a9d6d5, 1), round(0x40ffffff, 12, 0xffa9d6d5, 1), round(0x33ffffff, 12, Color.WHITE, 2.5f));
    }
    Drawable subtleBackground() {
        return states(round(Color.WHITE, 8, 0xffd5dfde, 1), round(0xfff0f6f5, 8, 0xffbbcecc, 1), round(0xfff0f6f5, 8, TEAL, 2));
    }
    /** An icon that works as a button. */
    android.widget.ImageView iconButton(int icon, int tint, Drawable background, float padding) {
        android.widget.ImageView b = new android.widget.ImageView(context);
        b.setImageResource(icon); b.setImageTintList(ColorStateList.valueOf(tint));
        b.setBackground(background); b.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        b.setFocusable(true); b.setClickable(true);
        return b;
    }
    private Drawable raised(int face, float ring) {
        GradientDrawable top = round(face, 12);
        if (ring > 0) top.setStroke(dp(ring), Color.WHITE);
        LayerDrawable d = new LayerDrawable(new Drawable[]{round(SUN_SHADOW, 12), top});
        d.setLayerInset(1, 0, 0, 0, dp(3));
        return d;
    }
    /** See-through, for the dark panels over the art. */
    TextView glass(String label) {
        return button(label, glassBackground(), textColors(Color.WHITE, 0x80ffffff), 14);
    }
    /** An outlined button on a card. */
    TextView subtle(String label) {
        TextView b = button(label, subtleBackground(), textColors(0xff31545b, 0x6631545b), 13);
        b.setPadding(dp(13), dp(8), dp(13), dp(8));
        return b;
    }
    /** A filled teal-tinted button on a card. */
    TextView solid(String label) {
        TextView b = button(label, states(round(TEAL_SOFT, 8), round(TEAL_PRESSED, 8), round(TEAL_SOFT, 8, TEAL, 2)),
            textColors(TEAL_DARK, 0x66125e66), 13);
        b.setPadding(dp(13), dp(8), dp(13), dp(8));
        return b;
    }
    /** The look of a row a controller can select: a teal ring when focused. */
    Drawable row() {
        return states(new GradientDrawable(), round(0xfff0f6f5, 8), round(TEAL_SOFT, 8, TEAL, 2));
    }

    /** A switch drawn as the desktop launcher's: teal track, white knob. */
    Switch toggle() {
        Switch s = new Switch(context);
        StateListDrawable track = new StateListDrawable();
        GradientDrawable on = round(TEAL, 11), off = round(0xffc9d2d1, 11);
        on.setSize(dp(42), dp(22)); off.setSize(dp(42), dp(22));
        track.addState(CHECKED, on); track.addState(ANY, off);
        GradientDrawable knob = new GradientDrawable(); knob.setShape(GradientDrawable.OVAL); knob.setColor(Color.WHITE);
        knob.setSize(dp(16), dp(16));
        // Inset as a layer: the switch would take an InsetDrawable's inset
        // as padding and push the knob off the track.
        GradientDrawable room = new GradientDrawable(); room.setSize(dp(22), dp(22));
        LayerDrawable thumb = new LayerDrawable(new Drawable[]{room, knob});
        thumb.setLayerInset(1, dp(3), dp(3), dp(3), dp(3));
        s.setTrackDrawable(track); s.setThumbDrawable(thumb);
        s.setTrackTintList(null); s.setThumbTintList(null);
        s.setShowText(false); s.setBackground(null);
        // The row it sits in takes the press and the focus.
        s.setClickable(false); s.setFocusable(false);
        return s;
    }

    /** Choices side by side, the chosen one raised in white. */
    LinearLayout segmented(String[] labels, int selected, java.util.function.IntConsumer chosen) {
        LinearLayout group = new LinearLayout(context);
        group.setBackground(round(0xffeef1f0, 9)); group.setPadding(dp(3), dp(3), dp(3), dp(3));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView option = text(labels[i], 13, MUTED, 650);
            option.setGravity(Gravity.CENTER); option.setSingleLine(true);
            option.setPadding(dp(12), dp(7), dp(12), dp(7));
            option.setFocusable(true); option.setClickable(true);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected}, round(Color.WHITE, 7, TEAL, 2));
            bg.addState(FOCUSED, round(0xfff8fbfa, 7, TEAL, 2));
            bg.addState(SELECTED, round(Color.WHITE, 7, 0xffdde6e4, 1));
            bg.addState(PRESSED, round(0xfff8fbfa, 7));
            bg.addState(ANY, new GradientDrawable());
            option.setBackground(bg);
            option.setTextColor(new ColorStateList(new int[][]{SELECTED, ANY}, new int[]{TEAL_DARK, MUTED}));
            option.setOnClickListener(v -> {
                for (int j = 0; j < group.getChildCount(); j++) group.getChildAt(j).setSelected(j == index);
                chosen.accept(index);
            });
            option.setSelected(i == selected);
            group.addView(option, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return group;
    }

    /** A thin bar: sun yellow on a pale track, for the dark panels or cards. */
    ProgressBar progress(int track) {
        ProgressBar bar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        LayerDrawable d = new LayerDrawable(new Drawable[]{round(track, 3), new ClipDrawable(round(SUN, 3), Gravity.START, ClipDrawable.HORIZONTAL)});
        d.setId(0, android.R.id.background); d.setId(1, android.R.id.progress);
        bar.setProgressDrawable(d);
        bar.setIndeterminateTintList(ColorStateList.valueOf(SUN));
        bar.setMax(1000);
        return bar;
    }

    View divider(int color) { View v = new View(context); v.setBackgroundColor(color); return v; }
    static LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    static LinearLayout.LayoutParams fill() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    static LinearLayout.LayoutParams share(float weight) { return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight); }
    LinearLayout.LayoutParams margins(LinearLayout.LayoutParams p, float left, float top, float right, float bottom) {
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom)); return p;
    }
}
