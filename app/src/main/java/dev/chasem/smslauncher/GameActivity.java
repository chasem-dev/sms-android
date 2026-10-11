package dev.chasem.smslauncher;

import android.os.Bundle;
import java.io.File;
import java.util.ArrayList;
import org.libsdl.app.SDLActivity;

public class GameActivity extends SDLActivity {
    @Override protected String[] getLibraries() { return new String[]{"SDL2", "smsbootstrap"}; }
    @Override protected String[] getArguments() {
        File data = new File(getFilesDir(), "game");
        ArrayList<String> args = new ArrayList<>();
        args.add("--data"); args.add(data.getAbsolutePath());
        args.add("--libraries"); args.add(getApplicationInfo().nativeLibraryDir);
        args.add("--disc"); args.add(new File(getFilesDir(), "disc/game.iso").getAbsolutePath());
        args.add("--env"); args.add("SMS_SAVE_DIR=" + new File(getFilesDir(), "saves/card-a").getAbsolutePath());
        args.add("--env"); args.add("SMS_SETTINGS=" + new File(data, "settings.txt").getAbsolutePath());
        if (BuildConfig.DEBUG) {
            if (getIntent().getBooleanExtra("gxSelftest", false)) { args.add("--gx-selftest"); args.add("1"); }
            String[] testEnvironment = getIntent().getStringArrayExtra("testEnvironment");
            if (testEnvironment != null) for (String value : testEnvironment) {
                if (value.startsWith("SMS_")) { args.add("--env"); args.add(value); }
            }
        }
        return args.toArray(new String[0]);
    }
    @Override protected void onCreate(Bundle state) {
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::finish);
    }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onDestroy() {
        super.onDestroy();
        // The game owns global heaps, SDK threads, and caches. A fresh process
        // on each Play avoids carrying that state into the next session.
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
