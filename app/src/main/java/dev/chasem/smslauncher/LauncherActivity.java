package dev.chasem.smslauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

/**
 * The launcher, after the desktop SMS Launcher: the seaside art with one
 * Play button, which does the next setup step until the game is ready, and
 * settings in a panel over it. Android's document picker keeps disc images
 * and saves private without storage permissions.
 */
public class LauncherActivity extends Activity {
    private static final int DISC = 1, IMPORT_SAVE = 2, EXPORT_SAVE = 3, EXPORT_LOG = 4;
    private static final int GAMEPLAY = 0, CONTROLS = 1, GRAPHICS = 2, PERFORMANCE = 3, DATA = 4, ABOUT = 5;
    private static final String DISCORD = "https://discord.gg/97sVPsbxsY", SOURCE = "https://github.com/chasem-dev/sms-android";
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%"), AMOUNT = Pattern.compile("([\\d.,]+) of ([\\d.,]+)");
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Ui ui;
    private boolean busy;
    private SharedPreferences prefs;
    private File root, disc, card, game;
    private TexturePack textures;
    private boolean hdWorking;
    // The Eclipse app's: the disc it is made from, and its download and patch.
    private EclipseDisc eclipse;
    private boolean eclipseWorking;
    // The language packs the game plays (its tools/mods/language-packs.json).
    private final List<LanguagePack> languages = new ArrayList<>();

    // The home screen
    private FrameLayout scene;
    private LinearLayout home, topBar, hero, statusBar, play, stepBox, taskBox, hints, update;
    private TextView device, updateTitle, updateText, heroText, playLabel, stepLabel, stepText, stepLink, statusText, taskTitle, taskPercent, taskDetail, taskStop, notice;
    private ImageView playIcon;
    private View statusDot;
    private ProgressBar taskBar;
    private Runnable taskStopAction;
    private final Runnable hideNotice = () -> notice.animate().alpha(0).setDuration(250).withEndAction(() -> notice.setVisibility(View.GONE));
    // Settings
    private FrameLayout settingsLayer;
    private LinearLayout settingsCard;
    private TextView pageTitle;
    private ScrollView pageScroll;
    private final List<TextView> railItems = new ArrayList<>();
    private final List<View> pages = new ArrayList<>();
    private final List<String> pageNames = new ArrayList<>();
    private LinearLayout titleLine;
    private TextView rowDetail;
    private Switch hdSwitch;
    private TextView hdBadge, hdDetail, hdButton, discDetail, discButton, languageDetail;
    private ProgressBar hdBar;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = getFilesDir(); disc = new File(root, "disc/game.iso");
        card = new File(root, "saves/card-a"); game = new File(root, "game");
        disc.getParentFile().mkdirs(); card.mkdirs(); game.mkdirs(); new File(game, "cache").mkdirs();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        textures = new TexturePack(game);
        if (BuildConfig.ECLIPSE) eclipse = new EclipseDisc(disc);
        for (int i = 0; i < BuildConfig.LANGUAGE_CODES.length; i++)
            languages.add(new LanguagePack(BuildConfig.LANGUAGE_CODES[i], BuildConfig.LANGUAGE_NAMES[i], BuildConfig.LANGUAGE_RELEASE,
                BuildConfig.LANGUAGE_URLS[i], BuildConfig.LANGUAGE_FILES[i], BuildConfig.LANGUAGE_MD5S[i], BuildConfig.LANGUAGE_SIZES[i]));
        ui = new Ui(this);

        // The art runs under the system bars.
        getWindow().setStatusBarColor(Color.TRANSPARENT); getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.NIGHT);
        scene = new FrameLayout(this);
        frame.addView(scene, matchParent());
        ImageView art = new ImageView(this);
        art.setImageResource(R.drawable.launcher_background); art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.addView(art, matchParent());
        // Shade the sky behind the title and the sea behind the panel.
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xa60b2236, 0x260b2236, 0x000b2236, 0x590b2236}));
        scene.addView(shade, matchParent());
        home = new LinearLayout(this); home.setOrientation(LinearLayout.VERTICAL);
        scene.addView(home, matchParent());
        home.addView(topBar = topBar(), Ui.fill());
        home.addView(hero = hero(), Ui.fill());
        home.addView(new View(this), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout panel = launchPanel();
        home.addView(panel, ui.margins(new LinearLayout.LayoutParams(panelWidth(), ViewGroup.LayoutParams.WRAP_CONTENT), 0, 0, 0, 14));
        ((LinearLayout.LayoutParams)panel.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        home.addView(statusBar = statusBar(), Ui.fill());

        notice = ui.text("", 13, Color.WHITE, 600);
        notice.setBackground(ui.round(0xf2173f59, 10, 0x4dffffff, 1));
        notice.setPadding(ui.dp(16), ui.dp(11), ui.dp(16), ui.dp(11)); notice.setMaxWidth(ui.dp(560));
        notice.setLineSpacing(0, 1.2f); notice.setElevation(ui.dp(10)); notice.setVisibility(View.GONE);
        notice.setOnClickListener(v -> hideNotice.run());
        FrameLayout.LayoutParams noticeAt = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        frame.addView(notice, noticeAt);

        settingsLayer = settings();
        frame.addView(settingsLayer, matchParent());

        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            // The bars run under the system bars and the cutout; what is on
            // them, and everything between, stays clear of both.
            topBar.setPadding(safe.left + ui.dp(22), safe.top, safe.right + ui.dp(22), 0);
            topBar.getLayoutParams().height = safe.top + ui.dp(56);
            statusBar.setPadding(safe.left + ui.dp(22), 0, safe.right + ui.dp(22), safe.bottom);
            statusBar.getLayoutParams().height = safe.bottom + ui.dp(34);
            hero.setPadding(safe.left + ui.dp(30), ui.dp(16), safe.right + ui.dp(30), 0);
            home.requestLayout();
            settingsLayer.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            noticeAt.topMargin = safe.top + ui.dp(66); notice.setLayoutParams(noticeAt);
            return insets;
        });
        setContentView(frame);
        refresh();
    }
    private static FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private int panelWidth() { return Math.min(ui.dp(620), getResources().getDisplayMetrics().widthPixels - ui.dp(48)); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        View panel = play == null ? null : (View)play.getParent().getParent();
        if (panel != null) { panel.getLayoutParams().width = panelWidth(); panel.requestLayout(); }
    }

    private LinearLayout topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(ui.dp(22), 0, ui.dp(22), 0);
        bar.setMinimumHeight(ui.dp(56));
        bar.setBackgroundColor(0x990f2a3f);
        TextView mark = ui.text("✦", 18, Ui.NAVY, 800);
        mark.setGravity(Gravity.CENTER); mark.setBackground(ui.round(Ui.SUN, 9));
        bar.addView(mark, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL);
        String brand = BuildConfig.ECLIPSE ? "SMS Eclipse" : "SMS Launcher";
        SpannableString title = new SpannableString(brand);
        title.setSpan(new StyleSpan(Typeface.BOLD), 4, brand.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView name = ui.text("", 15, Color.WHITE, 600); name.setText(title);
        names.addView(name);
        TextView version = ui.text("Version " + BuildConfig.VERSION_NAME + " · Game " + BuildConfig.GAME_REVISION, 11, 0xffd2eaf0, 500);
        names.addView(version, ui.margins(Ui.wrap(), 0, 3, 0, 0));
        bar.addView(names, ui.margins(Ui.wrap(), 10, 0, 0, 0));
        bar.addView(new View(this), Ui.share(1));
        device = ui.text(Build.MODEL, 12, 0xffb7cdd6, 500);
        device.setSingleLine(true);
        bar.addView(device);
        // In place of the device's name while a newer version is out.
        bar.addView(update = updateBanner(), ui.margins(Ui.wrap(), 12, 0, 0, 0));
        return bar;
    }
    private LinearLayout hero() {
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(ui.dp(30), ui.dp(16), ui.dp(30), 0);
        hero.addView(shadowed(ui.eyebrow("Super Mario Sunshine", 0xf2ffffff)));
        TextView title = shadowed(ui.text(BuildConfig.ECLIPSE ? "Super Mario Eclipse" : "Super Mario Sunshine", 33, Color.WHITE, 800));
        title.setLetterSpacing(-0.02f);
        hero.addView(title, ui.margins(Ui.wrap(), 0, 7, 0, 0));
        heroText = shadowed(ui.text("", 15, 0xf2ffffff, 500));
        heroText.setMaxWidth(ui.dp(520)); heroText.setLineSpacing(0, 1.15f);
        hero.addView(heroText, ui.margins(Ui.wrap(), 0, 8, 0, 0));
        return hero;
    }
    private static TextView shadowed(TextView v) { v.setShadowLayer(10, 0, 2, 0x99000000); return v; }

    private LinearLayout launchPanel() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xeb173f59, 0xeb116e77});
        glass.setCornerRadius(ui.dp(16)); glass.setStroke(ui.dp(1), 0x4dffffff);
        panel.setBackground(glass); panel.setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14));
        panel.setElevation(ui.dp(8));

        stepBox = new LinearLayout(this); stepBox.setOrientation(LinearLayout.VERTICAL);
        stepBox.setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(12));
        LinearLayout stepLine = new LinearLayout(this); stepLine.setGravity(Gravity.CENTER_VERTICAL);
        stepLabel = ui.eyebrow("", Ui.SUN_TEXT);
        stepLine.addView(stepLabel);
        stepText = ui.text("", 13, Color.WHITE, 600); stepText.setLineSpacing(0, 1.15f);
        stepLine.addView(stepText, ui.margins(Ui.share(1), 10, 0, 0, 0));
        stepBox.addView(stepLine);
        stepLink = ui.text("", 12, Ui.MIST, 600);
        stepLink.setPaintFlags(stepLink.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        stepLink.setBackground(ui.states(new GradientDrawable(), ui.round(0x26ffffff, 6), ui.round(0x26ffffff, 6, Color.WHITE, 2)));
        stepLink.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        stepLink.setFocusable(true); stepLink.setClickable(true);
        stepLink.setOnClickListener(v -> { if (!busy) pick(DISC); });
        stepBox.addView(stepLink, ui.margins(Ui.wrap(), -4, 6, 0, 0));
        panel.addView(stepBox, Ui.fill());

        LinearLayout buttons = new LinearLayout(this);
        play = new LinearLayout(this); play.setGravity(Gravity.CENTER);
        play.setBackground(ui.primaryBackground()); play.setFocusable(true); play.setClickable(true);
        play.setPadding(0, 0, 0, ui.dp(3));
        playIcon = new ImageView(this); playIcon.setImageResource(R.drawable.ic_play);
        playIcon.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.NAVY));
        play.addView(playIcon, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)));
        playLabel = ui.text("Play", 21, Ui.NAVY, 800); playLabel.setSingleLine(true);
        play.addView(playLabel, ui.margins(Ui.wrap(), 4, 0, 0, 0));
        play.setOnClickListener(v -> playAction());
        buttons.addView(play, new LinearLayout.LayoutParams(0, ui.dp(58), 1));
        ImageView gear = ui.iconButton(R.drawable.ic_settings, Color.WHITE, ui.glassBackground(), 16);
        gear.setContentDescription("Settings");
        gear.setOnClickListener(v -> openSettings(GAMEPLAY));
        buttons.addView(gear, ui.margins(new LinearLayout.LayoutParams(ui.dp(58), ui.dp(58)), 10, 0, 0, 0));
        panel.addView(buttons, Ui.fill());

        taskBox = new LinearLayout(this); taskBox.setOrientation(LinearLayout.VERTICAL);
        taskBox.setVisibility(View.GONE);
        View rule = ui.divider(0x42ffffff);
        taskBox.addView(rule, ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1), 0, 14, 0, 12));
        LinearLayout taskLine = new LinearLayout(this); taskLine.setGravity(Gravity.CENTER_VERTICAL);
        taskTitle = ui.text("", 13, Color.WHITE, 750); taskTitle.setSingleLine(true);
        taskLine.addView(taskTitle, Ui.share(1));
        taskPercent = ui.text("", 13, Ui.SUN_TEXT, 750);
        taskLine.addView(taskPercent);
        taskStop = ui.glass("Stop"); taskStop.setTextSize(12); taskStop.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5));
        taskStop.setOnClickListener(v -> { if (taskStopAction != null) taskStopAction.run(); });
        taskLine.addView(taskStop, ui.margins(Ui.wrap(), 12, 0, 0, 0));
        taskBox.addView(taskLine, Ui.fill());
        taskDetail = ui.text("", 11, Ui.MIST, 500); taskDetail.setSingleLine(true);
        taskDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        taskBox.addView(taskDetail, ui.margins(Ui.fill(), 0, 5, 0, 9));
        taskBar = ui.progress(0x38ffffff);
        taskBox.addView(taskBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(5)));
        panel.addView(taskBox, Ui.fill());
        return panel;
    }

    /** Says when a newer version is on the releases page, which Android won't let the launcher install itself. */
    private LinearLayout updateBanner() {
        LinearLayout banner = new LinearLayout(this); banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setBackground(ui.round(0x33ffc75a, 10, Ui.SUN, 1));
        banner.setPadding(ui.dp(12), ui.dp(4), ui.dp(4), ui.dp(4));
        banner.setVisibility(View.GONE);
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        updateTitle = ui.text("", 13, Ui.SUN_TEXT, 750); updateTitle.setSingleLine(true);
        words.addView(updateTitle);
        updateText = ui.text("", 11, Ui.MIST, 500); updateText.setSingleLine(true);
        updateText.setEllipsize(android.text.TextUtils.TruncateAt.END); updateText.setMaxWidth(ui.dp(380));
        words.addView(updateText, ui.margins(Ui.wrap(), 0, 3, 0, 0));
        banner.addView(words);
        TextView get = ui.glass("Get update"); get.setTextSize(12); get.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(5));
        get.setOnClickListener(v -> open(prefs.getString("update_url", SOURCE + "/releases")));
        banner.addView(get, ui.margins(Ui.wrap(), 12, 0, 0, 0));
        ImageView hide = ui.iconButton(R.drawable.ic_close, Color.WHITE, ui.glassBackground(), 7);
        hide.setContentDescription("Hide until the next version");
        hide.setOnClickListener(v -> {
            prefs.edit().putString("update_dismissed", prefs.getString("update_tag", "")).apply();
            showUpdate();
            play.requestFocus();
        });
        banner.addView(hide, ui.margins(new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30)), 6, 0, 0, 0));
        return banner;
    }
    /** Looks for a newer release at most every few hours, in the background; offline, it tries again next time. */
    private void checkForUpdate() {
        showUpdate();
        long now = System.currentTimeMillis();
        if (Math.abs(now - prefs.getLong("update_checked", 0)) < UpdateCheck.INTERVAL_MS) return;
        new Thread(() -> {
            try {
                UpdateCheck.Release newer = UpdateCheck.newer(releases(), BuildConfig.VERSION_CODE, UpdateCheck.isPreview(BuildConfig.VERSION_NAME));
                SharedPreferences.Editor edit = prefs.edit().putLong("update_checked", now);
                if (newer == null) edit.remove("update_tag").remove("update_url");
                else edit.putString("update_tag", newer.tag).putString("update_url", newer.url);
                edit.apply();
                runOnUiThread(this::showUpdate);
            } catch (Exception ignored) {}
        }, "update-check").start();
    }
    private static List<UpdateCheck.Release> releases() throws IOException, org.json.JSONException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(UpdateCheck.RELEASES).openConnection();
        try {
            c.setConnectTimeout(15000); c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "SMS Launcher for Android");
            if (c.getResponseCode() != 200) throw new IOException("GitHub answered " + c.getResponseCode());
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { copy(in, body, 4L << 20); }
            org.json.JSONArray list = new org.json.JSONArray(body.toString("UTF-8"));
            List<UpdateCheck.Release> releases = new ArrayList<>();
            for (int i = 0; i < list.length(); i++) {
                org.json.JSONObject r = list.getJSONObject(i);
                if (!r.optBoolean("draft"))
                    releases.add(new UpdateCheck.Release(r.optString("tag_name"), r.optString("html_url"), r.optBoolean("prerelease")));
            }
            return releases;
        } finally { c.disconnect(); }
    }
    private void showUpdate() {
        String tag = prefs.getString("update_tag", null);
        boolean show = tag != null && UpdateCheck.versionCode(tag) > BuildConfig.VERSION_CODE
            && !tag.equals(prefs.getString("update_dismissed", null));
        update.setVisibility(show ? View.VISIBLE : View.GONE);
        device.setVisibility(show ? View.GONE : View.VISIBLE);
        if (!show) return;
        String version = tag.substring(1);
        updateTitle.setText("Version " + version + " is out");
        updateText.setText("Install " + (BuildConfig.ECLIPSE ? "sms-android-eclipse-" : "sms-android-") + version
            + "-arm64.apk over this one: your disc and saves stay.");
    }

    private LinearLayout statusBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL); bar.setPadding(ui.dp(22), 0, ui.dp(22), 0);
        bar.setMinimumHeight(ui.dp(34));
        bar.setBackgroundColor(0xb30f2a3f);
        statusDot = new View(this);
        bar.addView(statusDot, new LinearLayout.LayoutParams(ui.dp(8), ui.dp(8)));
        statusText = ui.text("", 12, 0xf2ffffff, 600); statusText.setSingleLine(true);
        statusText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(statusText, ui.margins(Ui.share(1), 9, 0, 12, 0));
        hints = new LinearLayout(this); hints.setGravity(Gravity.CENTER_VERTICAL);
        hint("A", "Select"); hint("B", "Back"); hint("Start", "Settings");
        bar.addView(hints);
        TextView own = ui.text("Bring your own disc · No game data included", 11, 0xffa9c3cc, 500);
        own.setSingleLine(true);
        bar.addView(own, ui.margins(Ui.wrap(), 14, 0, 0, 0));
        return bar;
    }
    private void hint(String key, String action) {
        TextView chip = ui.text(key, 10, Color.WHITE, 750);
        chip.setBackground(ui.round(0x1affffff, 4, 0x55ffffff, 1)); chip.setPadding(ui.dp(5), ui.dp(2), ui.dp(5), ui.dp(2));
        hints.addView(chip, ui.margins(Ui.wrap(), hints.getChildCount() == 0 ? 0 : 10, 0, 5, 0));
        hints.addView(ui.text(action, 11, 0xffd2eaf0, 500));
    }
    private void status(String text, int dot) {
        statusText.setText(text);
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(dot);
        statusDot.setBackground(d);
    }
    /** A message over the art for a few seconds, for what just happened. */
    private void notice(String text) {
        notice.setText(text == null ? "Something went wrong." : text);
        notice.removeCallbacks(hideNotice); notice.animate().cancel();
        if (notice.getVisibility() != View.VISIBLE) { notice.setAlpha(0); notice.setTranslationY(-ui.dp(8)); notice.setVisibility(View.VISIBLE); }
        notice.animate().alpha(1).translationY(0).setDuration(200);
        notice.postDelayed(hideNotice, 6000);
    }

    // ----- Settings

    private FrameLayout settings() {
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0x8c071a2a); layer.setVisibility(View.GONE);
        layer.setClickable(true); layer.setOnClickListener(v -> closeSettings());
        settingsCard = new LinearLayout(this);
        settingsCard.setBackground(ui.round(Ui.PAGE, 18)); settingsCard.setClipToOutline(true);
        settingsCard.setElevation(ui.dp(24)); settingsCard.setClickable(true);
        FrameLayout.LayoutParams at = matchParent();
        at.setMargins(ui.dp(26), ui.dp(12), ui.dp(26), ui.dp(12));
        layer.addView(settingsCard, at);

        LinearLayout side = new LinearLayout(this); side.setOrientation(LinearLayout.VERTICAL);
        side.setBackgroundColor(Ui.CARD); side.setPadding(ui.dp(16), ui.dp(18), ui.dp(16), ui.dp(14));
        side.addView(ui.eyebrow("Preferences", Ui.TEAL), ui.margins(Ui.wrap(), 6, 0, 0, 0));
        side.addView(ui.text("Settings", 24, Ui.INK, 800), ui.margins(Ui.wrap(), 6, 6, 0, 0));
        side.addView(new View(this), new LinearLayout.LayoutParams(1, ui.dp(10)));
        ScrollView railScroll = new ScrollView(this); railScroll.setVerticalScrollBarEnabled(false);
        LinearLayout rail = new LinearLayout(this); rail.setOrientation(LinearLayout.VERTICAL);
        railScroll.addView(rail);
        side.addView(railScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        settingsCard.addView(side, new LinearLayout.LayoutParams(ui.dp(200), ViewGroup.LayoutParams.MATCH_PARENT));
        settingsCard.addView(ui.divider(Ui.BORDER), new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout main = new LinearLayout(this); main.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(ui.dp(24), ui.dp(14), ui.dp(14), ui.dp(8));
        pageTitle = ui.text("", 21, Ui.INK, 800);
        head.addView(pageTitle, Ui.share(1));
        head.addView(ui.text("Changes save automatically", 12, Ui.FAINT, 500), ui.margins(Ui.wrap(), 12, 0, 14, 0));
        ImageView close = ui.iconButton(R.drawable.ic_close, 0xff31545b, ui.subtleBackground(), 8);
        close.setContentDescription("Close settings");
        close.setOnClickListener(v -> closeSettings());
        head.addView(close, new LinearLayout.LayoutParams(ui.dp(38), ui.dp(38)));
        main.addView(head, Ui.fill());
        pageScroll = new ScrollView(this);
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(ui.dp(24), ui.dp(2), ui.dp(24), ui.dp(20));
        pageScroll.addView(holder);
        main.addView(pageScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        settingsCard.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        page(rail, holder, "Gameplay", gameplayPage());
        page(rail, holder, "Controls", controlsPage());
        page(rail, holder, "Graphics", graphicsPage());
        page(rail, holder, "Performance", performancePage());
        page(rail, holder, "Saves & data", dataPage());
        page(rail, holder, "About", aboutPage());
        return layer;
    }
    private void page(LinearLayout rail, FrameLayout holder, String name, View content) {
        final int index = pages.size();
        TextView item = ui.text(name, 14, Ui.BODY, 650);
        item.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        item.setFocusable(true); item.setClickable(true);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, ui.round(Ui.TEAL_SOFT, 9, Ui.TEAL, 2));
        bg.addState(new int[]{android.R.attr.state_selected}, ui.round(Ui.TEAL_SOFT, 9));
        bg.addState(new int[]{android.R.attr.state_pressed}, ui.round(0xfff0f6f5, 9));
        bg.addState(new int[]{}, new GradientDrawable());
        item.setBackground(bg);
        item.setTextColor(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_selected}, {}},
            new int[]{Ui.TEAL_DARK, Ui.BODY}));
        // A controller moves through the pages by moving through this list,
        // and A goes into the page.
        item.setOnFocusChangeListener((v, focused) -> { if (focused) showPage(index); });
        item.setOnClickListener(v -> {
            showPage(index);
            if (!v.isInTouchMode()) content.requestFocus(View.FOCUS_RIGHT);
        });
        rail.addView(item, ui.margins(Ui.fill(), 0, 0, 0, 2));
        railItems.add(item); pages.add(content); pageNames.add(name);
        content.setVisibility(View.GONE);
        holder.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void showPage(int index) {
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setVisibility(i == index ? View.VISIBLE : View.GONE);
            railItems.get(i).setSelected(i == index);
        }
        pageTitle.setText(pageNames.get(index));
        pageScroll.scrollTo(0, 0);
    }
    private boolean settingsOpen() { return settingsLayer.getVisibility() == View.VISIBLE; }
    private void openSettings(int page) {
        showPage(page);
        if (!settingsOpen()) {
            settingsLayer.setVisibility(View.VISIBLE); settingsLayer.setAlpha(0);
            settingsLayer.animate().alpha(1).setDuration(180);
            settingsCard.setScaleX(0.97f); settingsCard.setScaleY(0.97f);
            settingsCard.animate().scaleX(1).scaleY(1).setDuration(220);
            if (Build.VERSION.SDK_INT >= 31)
                scene.setRenderEffect(RenderEffect.createBlurEffect(ui.dp(10), ui.dp(10), Shader.TileMode.CLAMP));
            scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            setFocusable(home, false);
        }
        railItems.get(page).requestFocus();
    }
    private void closeSettings() {
        if (!settingsOpen()) return;
        settingsLayer.animate().alpha(0).setDuration(150).withEndAction(() -> settingsLayer.setVisibility(View.GONE));
        if (Build.VERSION.SDK_INT >= 31) scene.setRenderEffect(null);
        scene.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        setFocusable(home, true);
        play.requestFocus();
    }
    // Keeps a controller's focus inside settings while they are open.
    private static void setFocusable(ViewGroup group, boolean on) {
        group.setDescendantFocusability(on ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private LinearLayout pageColumn() { LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); return page; }
    /** A white card of settings, with an optional heading. */
    private LinearLayout section(LinearLayout page, String title) {
        LinearLayout section = new LinearLayout(this); section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(ui.round(Ui.CARD, 12, Ui.BORDER, 1));
        section.setPadding(ui.dp(8), ui.dp(title == null ? 4 : 12), ui.dp(8), ui.dp(4));
        if (title != null) section.addView(ui.text(title, 15, Ui.INK, 750), ui.margins(Ui.wrap(), 10, 0, 0, 4));
        page.addView(section, ui.margins(Ui.fill(), 0, 0, 0, 12));
        return section;
    }
    /** A setting: its name and what it does, and its control on the right. */
    private LinearLayout row(LinearLayout section, String title, String detail, View control) {
        if (section.getChildCount() > (section.getChildAt(0) instanceof LinearLayout ? 0 : 1))
            section.addView(ui.divider(Ui.DIVIDER), ui.margins(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1), 10, 0, 10, 0));
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(10), ui.dp(11), ui.dp(10), ui.dp(11)); row.setMinimumHeight(ui.dp(54));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL);
        titleLine = new LinearLayout(this); titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.addView(ui.text(title, 14, Ui.BODY, 650));
        words.addView(titleLine);
        rowDetail = ui.text(detail == null ? "" : detail, 12, Ui.FAINT, 500);
        rowDetail.setLineSpacing(0, 1.2f); rowDetail.setVisibility(detail == null ? View.GONE : View.VISIBLE);
        words.addView(rowDetail, ui.margins(Ui.fill(), 0, 4, 0, 0));
        row.addView(words, Ui.share(1));
        if (control != null) row.addView(control, ui.margins(Ui.wrap(), 16, 0, 0, 0));
        section.addView(row, Ui.fill());
        return row;
    }
    private Switch switchRow(LinearLayout section, String title, String detail, String key, boolean fallback) {
        Switch toggle = ui.toggle();
        toggle.setChecked(prefs.getBoolean(key, fallback));
        LinearLayout row = row(section, title, detail, toggle);
        row.setBackground(ui.row()); row.setFocusable(true); row.setClickable(true);
        row.setOnClickListener(v -> { toggle.toggle(); prefs.edit().putBoolean(key, toggle.isChecked()).apply(); });
        return toggle;
    }
    private void choiceRow(LinearLayout section, String title, String detail, String key, String[] values, String[] labels, String fallback) {
        String value = prefs.getString(key, fallback);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) selected = i;
        row(section, title, detail, ui.segmented(labels, selected, i -> prefs.edit().putString(key, values[i]).apply()));
    }
    private TextView actionRow(LinearLayout section, String title, String detail, String label, Runnable action) {
        TextView button = ui.subtle(label);
        button.setOnClickListener(v -> { if (!busy) action.run(); });
        row(section, title, detail, button);
        return button;
    }

    private View gameplayPage() {
        LinearLayout page = pageColumn();
        LinearLayout play = section(page, null);
        choiceRow(play, "Frame rate", "60 is smoother; 30 is the GameCube's own speed and easier on the battery.",
            "frame_rate", new String[]{"30", "60"}, new String[]{"30 FPS", "60 FPS"}, "60");
        choiceRow(play, "Volume", null, "volume", new String[]{"0", "25", "50", "75", "100"},
            new String[]{"Off", "25", "50", "75", "100"}, "100");
        // Super Mario Eclipse has no language packs; its own text is English.
        if (!BuildConfig.ECLIPSE) languageRow(play);
        LinearLayout camera = section(page, "Camera");
        switchRow(camera, "Invert left/right", "Turns the camera the other way when you push the C-stick sideways.", "camera_invert_x", false);
        switchRow(camera, "Invert up/down", "Tilts the camera the other way when you push the C-stick up or down.", "camera_invert_y", false);
        return page;
    }
    /** Language: English, the disc's own, or one of the game's language packs, each in its own name. */
    private void languageRow(LinearLayout section) {
        String[] labels = new String[languages.size() + 1];
        labels[0] = "English";
        int selected = 0;
        for (int i = 0; i < languages.size(); i++) {
            labels[i + 1] = languages.get(i).name;
            if (languages.get(i).code.equals(prefs.getString("language", "en"))) selected = i + 1;
        }
        row(section, "Language", "", ui.dropdown(labels, selected, i -> {
            prefs.edit().putString("language", i == 0 ? "en" : languages.get(i - 1).code).apply();
            refreshLanguage();
        }));
        languageDetail = rowDetail; languageDetail.setVisibility(View.VISIBLE);
    }
    /** The chosen language's pack, or null for English. */
    private LanguagePack chosenLanguage() {
        if (BuildConfig.ECLIPSE) return null;
        String code = prefs.getString("language", "en");
        for (LanguagePack pack : languages) if (pack.code.equals(code)) return pack;
        return null;
    }
    private void refreshLanguage() {
        if (languageDetail == null) return;
        LanguagePack pack = chosenLanguage();
        languageDetail.setText(pack == null
            ? "Plays the game's text, menus and pictures in another language. Its pack downloads before the game starts. Your saves work in every language."
            : pack.installed(game) ? pack.name + " is downloaded. Your saves work in every language."
            : "The " + pack.name + " language pack (about " + pack.sizeText() + ") downloads before the game starts.");
    }
    private View controlsPage() {
        LinearLayout page = pageColumn();
        LinearLayout pads = section(page, null);
        actionRow(pads, "Controller configuration", "Assign buttons for a controller the game doesn't set up well by itself.",
            "Configure", () -> startActivity(new Intent(this, ControllerConfigActivity.class)));
        switchRow(pads, "Touch screen controls", "Shows a GameCube controller on the screen. It hides while a controller is used, and comes back when you touch the screen.",
            "touch_controls", false);
        actionRow(pads, "Input test", "Shows what your controllers and keyboards send, to find out why a button does nothing.",
            "Open", () -> startActivity(new Intent(this, InputMonitorActivity.class)));
        return page;
    }
    private View graphicsPage() {
        LinearLayout page = pageColumn();
        LinearLayout picture = section(page, null);
        choiceRow(picture, "Picture sharpness", "Sharper settings render the game at a higher resolution and need more of the graphics chip.",
            "resolution", new String[]{"1", "2", "3", "4"}, new String[]{"1×", "2×", "3×", "4×"}, "1");
        choiceRow(picture, "Aspect ratio", "16:9 fills a wide screen; 4:3 is the GameCube's picture.",
            "widescreen", new String[]{"off", "16:9"}, new String[]{"4:3", "16:9"}, "16:9");
        LinearLayout hd = section(page, null);
        hdSwitch = switchRow(hd, "HD textures", "", "hd_textures", false);
        hdBadge = ui.badge("", 0, 0);
        titleLine.addView(hdBadge, ui.margins(Ui.wrap(), 8, 0, 0, 0));
        hdDetail = rowDetail; hdDetail.setVisibility(View.VISIBLE);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(ui.dp(10), 0, ui.dp(10), ui.dp(12));
        hdBar = ui.progress(Ui.DIVIDER); hdBar.setVisibility(View.INVISIBLE);
        actions.addView(hdBar, new LinearLayout.LayoutParams(0, ui.dp(5), 1));
        hdButton = ui.solid("");
        hdButton.setOnClickListener(v -> hdAction());
        actions.addView(hdButton, ui.margins(Ui.wrap(), 16, 0, 0, 0));
        hd.addView(actions, Ui.fill());
        return page;
    }
    private View performancePage() {
        LinearLayout page = pageColumn();
        LinearLayout speed = section(page, "Speed");
        switchRow(speed, "Android performance hints", "Lets supported devices schedule the CPU for each frame's work. It doesn't force clock speeds.", "performance_hints", true);
        switchRow(speed, "Use performance CPU cores", "Keeps the game on the fastest group of CPU cores. Turn it off to save battery.", "performance_cores", true);
        switchRow(speed, "Request performance headroom", "Asks Android to finish each frame's work with time to spare. An experiment; off by default.", "performance_headroom", false);
        switchRow(speed, "Match display refresh to game FPS", "Asks Android to run the screen at the game's frame rate, on Android 11 or newer.", "match_display_rate", false);
        LinearLayout measure = section(page, "Measuring");
        switchRow(measure, "Show performance overlay", "Shows the frame rate and frame times over the game.", "performance_overlay", false);
        switchRow(measure, "Log performance timings", "Writes frame timings to the game log, to export from Saves & data.", "timing_log", false);
        return page;
    }
    private View dataPage() {
        LinearLayout page = pageColumn();
        LinearLayout discs = section(page, "Game disc");
        discButton = actionRow(discs, "Disc image", "", "Import", () -> pick(DISC));
        discDetail = rowDetail; discDetail.setVisibility(View.VISIBLE);
        String saveName = BuildConfig.ECLIPSE ? "eclipse" : "sunshine";
        LinearLayout saves = section(page, "Saves");
        actionRow(saves, "Back up your save", "Saves a .zip of your memory card where you choose.",
            "Export", () -> create(EXPORT_SAVE, "application/zip", saveName + "-save.zip"));
        actionRow(saves, "Restore a backup", "Replaces your save with a backup .zip. The save it replaces is kept in a local backup.",
            "Import", () -> pick(IMPORT_SAVE));
        LinearLayout help = section(page, "Problems");
        actionRow(help, "Game log", "Saves the last game's log, to share when reporting a problem.",
            "Export", () -> create(EXPORT_LOG, "text/plain", saveName + "-game.log"));
        TextView privacy = ui.text("Disc images and save backups are copied into the app's private storage; uninstalling removes them.", 12, Ui.MUTED, 500);
        privacy.setLineSpacing(0, 1.2f);
        page.addView(privacy, ui.margins(Ui.fill(), 4, 0, 4, 0));
        return page;
    }
    private View aboutPage() {
        LinearLayout page = pageColumn();
        LinearLayout about = section(page, null);
        row(about, BuildConfig.ECLIPSE ? "SMS Eclipse" : "SMS Launcher", BuildInfo.summary(), null);
        if (BuildConfig.ECLIPSE)
            row(about, "Super Mario Eclipse", EclipseDisc.NAME + " by the Eclipse Team, from GameBanana, patched into your own disc.", null);
        row(about, "Super Mario Sunshine PC port", "The game runs natively, built from the Super Mario Sunshine decompilation. No game data is included: it plays your own disc image.", null);
        LinearLayout links = section(page, null);
        actionRow(links, "Discord", "Talk with other players, and report problems.", "Open", () -> open(DISCORD));
        actionRow(links, "Source code", "The launcher and its release notes.", "Open", () -> open(SOURCE));
        return page;
    }
    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException e) { notice("No app here can open " + url); }
    }

    // ----- Controllers: Start opens settings, and B (Back) closes them.

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_MENU) && event.getRepeatCount() == 0) {
            if (settingsOpen()) closeSettings(); else openSettings(GAMEPLAY);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B && settingsOpen()) { closeSettings(); return true; }
        return super.onKeyDown(keyCode, event);
    }
    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { if (settingsOpen()) closeSettings(); else super.onBackPressed(); }
    private boolean controllerConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual() && (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) return true;
        }
        return false;
    }

    @Override protected void onResume() {
        super.onResume();
        hints.setVisibility(controllerConnected() ? View.VISIBLE : View.GONE);
        if (!busy) refresh();
        checkForUpdate();
        if (!settingsOpen() && getCurrentFocus() == null) play.requestFocus();
    }
    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }

    // ----- What Play does: the next setup step, until the game is ready.

    private void playAction() {
        if (busy) return;
        if (disc.isFile()) launch();
        else if (BuildConfig.ECLIPSE && eclipse.sunshineImported()) eclipseAction();
        else pick(DISC);
    }
    private void launch() {
        if (prefs.getBoolean("hd_textures", false) && !textures.installed()) {
            notice("HD textures are on but not downloaded. Download them, or turn them off.");
            openSettings(GRAPHICS);
            return;
        }
        LanguagePack language = chosenLanguage();
        if (language != null && !language.installed(game)) { installLanguage(language); return; }
        try {
            writeSettings();
            Intent launch = new Intent(this, GameActivity.class);
            if (BuildConfig.DEBUG && getIntent().hasExtra("testEnvironment"))
                launch.putExtra("testEnvironment", getIntent().getStringArrayExtra("testEnvironment"));
            if (BuildConfig.DEBUG) launch.putExtra("gxSelftest", getIntent().getBooleanExtra("gxSelftest", false));
            startActivity(launch);
        }
        catch (IOException e) { notice(e.getMessage()); }
    }
    /** Downloads the chosen language's pack, then starts the game in it. */
    private void installLanguage(LanguagePack pack) {
        startTask("Installing " + pack.name, null);
        taskProgress("Downloading " + pack.name + "…");
        io.execute(() -> {
            String failure = null;
            try { pack.install(game, text -> runOnUiThread(() -> taskProgress(text))); }
            catch (Exception e) { failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replaceAll("\\.$", ""); }
            final String error = failure;
            runOnUiThread(() -> {
                endTask();
                if (error == null) launch();
                else notice("The " + pack.name + " language pack could not be downloaded (" + error
                    + "). Choose Play to try again, or choose English under Settings → Gameplay.");
            });
        });
    }
    private void writeSettings() throws IOException {
        ControllerProfiles.writeGameFile(this);
        String text = "frame_rate=" + prefs.getString("frame_rate", "60") + "\nresolution=" + prefs.getString("resolution", "1")
            + "\nwidescreen=" + prefs.getString("widescreen", "16:9") + "\nvolume=" + prefs.getString("volume", "100")
            + "\nmsaa=0\nfullscreen=on\nvsync=on\n"
            + "language=" + (chosenLanguage() == null ? "en" : chosenLanguage().code) + "\n"
            // The touch controls are a GameCube controller, so the game shows its own buttons.
            + "button_prompts=" + (prefs.getBoolean("touch_controls", false) ? "gamecube" : "auto") + "\n"
            // A GPU without BC7 gets the pack decoded to RGBA; images held so
            // are halved past 1024x1024 texels, keeping Delfino Plaza near 560
            // MiB (1.1 GiB at 2048). Blocks the GPU takes stay whole.
            + "texture_packs=" + (prefs.getBoolean("hd_textures", false)
                ? "on\ntexture_pack_max_size=1024\ntexture_pack_mb=1024" : "off") + "\n"
            + "camera_invert_x=" + (prefs.getBoolean("camera_invert_x", false) ? "1" : "0")
            + "\ncamera_invert_y=" + (prefs.getBoolean("camera_invert_y", false) ? "1" : "0") + "\n"
            + "SMS_ANDROID_PERFORMANCE_HINTS=" + (prefs.getBoolean("performance_hints", true) ? "1" : "0")
            + "\nSMS_ANDROID_PERFORMANCE_CORES=" + (prefs.getBoolean("performance_cores", true) ? "1" : "0")
            + "\nSMS_ANDROID_PERFORMANCE_HEADROOM=" + (prefs.getBoolean("performance_headroom", false) ? "1" : "0")
            + "\nSMS_TIMING_LOG=" + (prefs.getBoolean("timing_log", false) ? "1" : "0")
            + "\nSMS_OVERLAY=" + (prefs.getBoolean("performance_overlay", false) ? "1" : "0") + "\n";
        try (FileOutputStream out = new FileOutputStream(new File(game, "settings.txt"))) { out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private void refresh() {
        boolean ready = disc.isFile();
        long size = ready ? disc.length() / 1048576 : 0;
        stepBox.setVisibility(ready ? View.GONE : View.VISIBLE);
        playIcon.setVisibility(ready ? View.VISIBLE : View.GONE);
        if (ready) {
            heroText.setText("Ready when you are.");
            playLabel.setText("Play");
            status("Ready to play · disc " + size + " MB", Ui.GOOD);
        } else if (BuildConfig.ECLIPSE && eclipse.sunshineImported()) {
            long partial = eclipse.partialBytes();
            heroText.setText("Your Super Mario Sunshine disc is in. Download Super Mario Eclipse, and the launcher patches your copy.");
            stepLabel.setText("Step 2 of 2");
            stepText.setText(partial > 0
                ? "Continue the download · " + TexturePack.mb(partial) + " MB so far"
                : "Download Super Mario Eclipse · about " + TexturePack.mb(EclipseDisc.SIZE) + " MB; allow "
                    + (EclipseDisc.SIZE + EclipseDisc.RESULT_SIZE + (1L << 30) - 1) / (1L << 30) + " GB of free space");
            stepLink.setText("Or import an Eclipse disc image made with Eclipse's own patcher");
            stepLink.setVisibility(View.VISIBLE);
            playLabel.setText(partial > 0 ? "Continue download" : "Download Eclipse");
            status("Almost ready: download Super Mario Eclipse", Ui.SUN);
        } else {
            heroText.setText(BuildConfig.ECLIPSE
                ? "Import your Super Mario Sunshine disc, then download Super Mario Eclipse: the launcher patches your copy into Eclipse."
                : "Import a disc image made from your own copy of the game to begin.");
            stepLabel.setText(BuildConfig.ECLIPSE ? "Step 1 of 2" : "Setup");
            stepText.setText("Import your US revision 0 Super Mario Sunshine disc image (ISO or GCM).");
            stepLink.setText("Already have the Eclipse v1.1.0 disc image? Import it instead");
            stepLink.setVisibility(BuildConfig.ECLIPSE ? View.VISIBLE : View.GONE);
            playLabel.setText("Import disc image");
            status("Import a disc image to begin", 0xff9fb3bc);
        }
        discDetail.setText(ready
            ? (BuildConfig.ECLIPSE ? "Super Mario Eclipse, made from your disc · " : "Super Mario Sunshine (US) · ") + size + " MB"
            : BuildConfig.ECLIPSE && eclipse.sunshineImported() ? "Super Mario Sunshine (US), waiting for the Eclipse download" : "Not imported yet");
        discButton.setText(ready ? "Replace" : "Import");
        refreshTextures();
        refreshLanguage();
    }

    // ----- Long work (imports, downloads), shown under Play.

    private void startTask(String title, Runnable stop) {
        busy = true;
        taskTitle.setText(title); taskDetail.setText(""); taskPercent.setText("");
        taskBar.setIndeterminate(true);
        taskStopAction = stop; taskStop.setVisibility(stop == null ? View.GONE : View.VISIBLE);
        taskBox.setVisibility(View.VISIBLE);
        play.setEnabled(false); play.setAlpha(0.55f);
        status(title, Ui.SUN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private void taskProgress(String text) {
        taskDetail.setText(text); status(text, Ui.SUN);
        float done = fraction(text);
        for (ProgressBar bar : hdWorking ? new ProgressBar[]{taskBar, hdBar} : new ProgressBar[]{taskBar}) {
            bar.setIndeterminate(done < 0);
            if (done >= 0) bar.setProgress(Math.round(done * 1000));
        }
        taskPercent.setText(done < 0 ? "" : Math.round(done * 100) + "%");
        if (hdWorking) hdDetail.setText(text);
    }
    private void endTask() {
        busy = false;
        taskBox.setVisibility(View.GONE); taskStopAction = null;
        play.setEnabled(true); play.setAlpha(1);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        refresh();
    }
    /** How far along a progress message is ("43%", "120 of 851 MB"), or -1. */
    static float fraction(String text) {
        Matcher m = PERCENT.matcher(text);
        if (m.find()) return Math.min(1, Integer.parseInt(m.group(1)) / 100f);
        m = AMOUNT.matcher(text);
        if (m.find()) try {
            float have = Float.parseFloat(m.group(1).replace(",", "")), total = Float.parseFloat(m.group(2).replace(",", ""));
            if (total > 0) return Math.min(1, have / total);
        } catch (NumberFormatException ignored) {}
        return -1;
    }

    private void eclipseAction() {
        if (eclipseWorking) { eclipse.stop(); taskTitle.setText("Stopping…"); return; }
        if (busy) return;
        if (!eclipse.sunshineImported()) { notice("Import your Super Mario Sunshine disc image first."); return; }
        // The release's 7z needs a 256 MB dictionary in the launcher's memory.
        if (Runtime.getRuntime().maxMemory() < (320L << 20)) {
            notice("This device gives the launcher too little memory to unpack Super Mario Eclipse. Make the Eclipse disc image with Eclipse's own patcher on a computer, and import it instead.");
            return;
        }
        eclipseWorking = true;
        startTask("Setting up Super Mario Eclipse", this::eclipseAction);
        taskProgress("Starting the download… Keep the launcher open.");
        io.execute(() -> {
            String failure = null;
            try { eclipse.install(text -> runOnUiThread(() -> taskProgress(text))); }
            catch (Exception e) { failure = e.getMessage() == null ? "Super Mario Eclipse could not be set up." : e.getMessage(); }
            final String error = failure;
            runOnUiThread(() -> {
                eclipseWorking = false;
                endTask();
                notice(error != null ? error : "Super Mario Eclipse is ready. Choose Play.");
            });
        });
    }
    private void refreshTextures() {
        if (hdWorking) return;
        long partial = textures.partialBytes();
        hdBar.setVisibility(View.INVISIBLE);
        if (textures.installed()) {
            ui.setBadge(hdBadge, "Installed", "good");
            hdDetail.setText("Downloaded · " + TexturePack.mb(textures.installedBytes()) + " MB. They look best with a sharper picture, and take longer to load.");
            hdButton.setText("Remove HD textures");
        } else if (partial > 0) {
            ui.setBadge(hdBadge, "Partly downloaded", "warn");
            hdDetail.setText(TexturePack.mb(partial) + " MB downloaded so far. Download again to continue where it stopped.");
            hdButton.setText("Continue download");
        } else {
            ui.setBadge(hdBadge, "Not downloaded", "");
            hdDetail.setText("The Super Mario Sunshine UHD Texture Pack and the port's extras. About "
                + TexturePack.mb(TexturePack.DOWNLOAD_BYTES) + " MB to download; allow "
                + (TexturePack.FREE_BYTES_NEEDED + (1L << 30) - 1) / (1L << 30) + " GB of free space.");
            hdButton.setText("Download HD textures");
        }
    }
    private void setHd(boolean on) { hdSwitch.setChecked(on); prefs.edit().putBoolean("hd_textures", on).apply(); }
    private void hdAction() {
        if (hdWorking) { textures.stop(); hdButton.setText("Stopping…"); taskTitle.setText("Stopping…"); return; }
        if (busy) return;
        boolean remove = textures.installed();
        hdWorking = true;
        startTask(remove ? "Removing HD textures" : "Downloading HD textures", remove ? null : this::hdAction);
        hdButton.setText(remove ? "Removing…" : "Stop");
        ui.setBadge(hdBadge, remove ? "Removing" : "Downloading", "warn");
        hdBar.setVisibility(View.VISIBLE);
        taskProgress(remove ? "Removing HD textures…" : "Starting the download… Keep the launcher open.");
        io.execute(() -> {
            String failure = null;
            try {
                if (remove) textures.remove();
                else textures.install(text -> runOnUiThread(() -> taskProgress(text)));
            } catch (Exception e) { failure = e.getMessage() == null ? "HD textures could not be set up." : e.getMessage(); }
            final String error = failure;
            runOnUiThread(() -> {
                hdWorking = false;
                if (remove) setHd(false);
                else if (textures.installed()) setHd(true);
                endTask();
                if (error != null) hdDetail.setText(error + "\n" + hdDetail.getText());
                notice(error != null ? error : remove ? "HD textures removed." : "HD textures are ready.");
            });
        });
    }
    private void pick(int request) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(i, request);
    }
    private void create(int request, String type, String title) {
        startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type).putExtra(Intent.EXTRA_TITLE, title), request);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        startTask(request == DISC ? "Importing your disc image" : request == IMPORT_SAVE ? "Restoring your save"
            : request == EXPORT_SAVE ? "Backing up your save" : "Exporting the game log", null);
        taskProgress("Working… Keep the launcher open.");
        io.execute(() -> {
            String done;
            try {
                if (request == DISC) done = importDisc(uri);
                else if (request == IMPORT_SAVE) { importSave(uri); done = "Save imported. The previous save is kept in the local backup."; }
                else if (request == EXPORT_SAVE) { exportSave(uri); done = "Save backup exported."; }
                else { exportLog(uri); done = "Game log exported."; }
            } catch (Exception e) { done = "Could not finish: " + e.getMessage(); }
            final String text = done; runOnUiThread(() -> { endTask(); notice(text); });
        });
    }
    private InputStream input(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Document could not be opened."); return in;
    }
    private OutputStream output(Uri uri) throws IOException {
        OutputStream out = getContentResolver().openOutputStream(uri, "wt");
        if (out == null) throw new IOException("Destination could not be opened."); return out;
    }
    private static long copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buf = new byte[262144]; long total = 0; int n;
        while ((n = in.read(buf)) != -1) { total += n; if (total > limit) throw new IOException("File is too large."); out.write(buf, 0, n); }
        return total;
    }
    private String importDisc(Uri uri) throws IOException {
        File temp = new File(disc.getParentFile(), "import.part");
        try {
            try (InputStream in = input(uri); OutputStream out = new FileOutputStream(temp)) { copy(in, out, 2L << 30); }
            String id;
            try (RandomAccessFile in = new RandomAccessFile(temp, "r")) {
                byte[] header = new byte[32]; in.readFully(header);
                id = new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII);
                boolean eclipseDisc = BuildConfig.ECLIPSE && id.equals("GMSE04");
                if (!(id.equals("GMSE01") || eclipseDisc) || header[7] != 0
                    || header[28] != (byte)0xc2 || header[29] != 0x33 || header[30] != (byte)0x9f || header[31] != 0x3d)
                    throw new IOException(BuildConfig.ECLIPSE ? "Use a US revision 0 Sunshine ISO or GCM image, or a Super Mario Eclipse v1.1.0 image."
                        : "Use a US revision 0 Sunshine ISO or GCM image.");
            }
            if (!BuildConfig.ECLIPSE) {
                Files.move(temp.toPath(), disc.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return "Disc imported. Ready to play.";
            }
            // Eclipse's patch makes the expected image only from the exact disc it was made for.
            String md5 = TexturePack.md5(temp);
            if (id.equals("GMSE04")) {
                if (!md5.equals(EclipseDisc.RESULT_MD5))
                    throw new IOException("This is not the Super Mario Eclipse v1.1.0 image Eclipse's patcher makes.");
                Files.move(temp.toPath(), disc.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                eclipse.sunshineDisc().delete();
                return "Super Mario Eclipse disc imported. Ready to play.";
            }
            if (!md5.equals(EclipseDisc.SOURCE_MD5))
                throw new IOException("Eclipse patches only the unmodified US Sunshine ISO (a 1:1 copy; compressed or trimmed images do not work).");
            if (disc.isFile()) return "Super Mario Eclipse is already set up, so this disc was not kept.";
            Files.move(temp.toPath(), eclipse.sunshineDisc().toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return "Disc imported. Now choose Download Super Mario Eclipse.";
        } finally { temp.delete(); }
    }
    private void importSave(Uri uri) throws IOException {
        File staged = new File(root, "saves/import"); erase(staged); staged.mkdirs();
        try {
            try (ZipInputStream zip = new ZipInputStream(input(uri))) {
                ZipEntry e; int count = 0; long total = 0;
                while ((e = zip.getNextEntry()) != null) {
                    String name = e.getName(); if (name.startsWith("card-a/")) name = name.substring(7);
                    if (e.isDirectory()) continue;
                    if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..") || ++count > 32)
                        throw new IOException("Backup must contain the card-a files, without nested folders.");
                    File f = new File(staged, name);
                    if (f.exists()) throw new IOException("Duplicate file in backup.");
                    try (OutputStream out = new FileOutputStream(f)) { total += copy(zip, out, (16L << 20) - total); }
                }
            }
            if (BuildConfig.ECLIPSE) {
                // The game keeps Eclipse's (GMSE04) card files under its ID.
                String[] names = staged.list();
                boolean any = false;
                if (names != null) for (String name : names) any |= name.startsWith("GMSE04_") && name.endsWith(".dat");
                if (!new File(staged, "index.txt").isFile() || !any) throw new IOException("Backup is missing Eclipse card files.");
            } else if (!new File(staged, "index.txt").isFile() || !new File(staged, "super_mario_sunshine.dat").isFile()
                || !new File(staged, "super_mario_sunshine.stat").isFile()) throw new IOException("Backup is missing Sunshine card files.");
            File backup = new File(root, "saves/previous"); erase(backup);
            if (!card.renameTo(backup)) throw new IOException("Previous save could not be backed up.");
            if (!staged.renameTo(card)) { backup.renameTo(card); throw new IOException("Save could not be installed."); }
        } finally { erase(staged); }
    }
    private void exportSave(Uri uri) throws IOException {
        File[] files = card.listFiles(); if (files == null || files.length == 0) throw new IOException("No save yet. Save in the game first.");
        try (ZipOutputStream zip = new ZipOutputStream(output(uri))) {
            for (File f : files) if (f.isFile()) { zip.putNextEntry(new ZipEntry(f.getName())); try (InputStream in = new FileInputStream(f)) { copy(in, zip, 16L << 20); } zip.closeEntry(); }
        }
    }
    private void exportLog(Uri uri) throws IOException {
        try (InputStream in = new FileInputStream(new File(game, "game.log")); OutputStream out = output(uri)) { copy(in, out, 64L << 20); }
    }
    private static void erase(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory()) { File[] files = f.listFiles(); if (files == null) throw new IOException("Cannot read " + f.getName()); for (File child : files) erase(child); }
        if (!f.delete()) throw new IOException("Cannot remove " + f.getName());
    }
}
