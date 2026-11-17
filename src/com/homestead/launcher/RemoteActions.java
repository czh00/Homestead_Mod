/*
 * Homestead - a replacement home screen for Fire TV.
 * Copyright (C) 2026 Homestead contributors
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version. It is distributed in
 * the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details. You should have received a copy of the GNU General
 * Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.homestead.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Handles remote control key shortcuts:
 * 1. Channel Up / Down keys for screen brightness adjustment (+/- 10%) with in-app/overlay dimmer & OSD.
 * 2. Red button for sleep / power switch.
 */
public final class RemoteActions {

    private static final String TAG = "Homestead";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View dimmerView;
    private static View hudView;
    private static TextView hudText;
    private static View hudBarFill;
    private static View sleepView;

    private static int lastKeyCode = 0;
    private static String lastKeyName = "";
    private static Runnable keyObserver;

    private static final Runnable HIDE_HUD = new Runnable() {
        @Override
        public void run() {
            hideHud();
        }
    };

    private RemoteActions() { }

    public static void setKeyObserver(Runnable r) {
        keyObserver = r;
    }

    public static String getLastKeyDescription(Context c) {
        if (lastKeyCode == 0) {
            return c.getString(R.string.set_remote_none);
        }
        return lastKeyName + " (" + lastKeyCode + ")";
    }

    private static void recordKey(final Context c, final KeyEvent event) {
        lastKeyCode = event.getKeyCode();
        lastKeyName = KeyEvent.keyCodeToString(lastKeyCode);
        if (lastKeyName.startsWith("KEYCODE_")) {
            lastKeyName = lastKeyName.substring(8);
        }
        if (keyObserver != null) {
            MAIN.post(keyObserver);
        }
    }

    public static boolean isChannelUp(int code) {
        return code == KeyEvent.KEYCODE_CHANNEL_UP
                || code == KeyEvent.KEYCODE_PAGE_UP
                || code == KeyEvent.KEYCODE_MEDIA_NEXT
                || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || code == KeyEvent.KEYCODE_BUTTON_R1;
    }

    public static boolean isChannelDown(int code) {
        return code == KeyEvent.KEYCODE_CHANNEL_DOWN
                || code == KeyEvent.KEYCODE_PAGE_DOWN
                || code == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                || code == KeyEvent.KEYCODE_MEDIA_REWIND
                || code == KeyEvent.KEYCODE_BUTTON_L1;
    }

    public static boolean isRedKey(int code) {
        return code == KeyEvent.KEYCODE_PROG_RED
                || code == KeyEvent.KEYCODE_BUTTON_1
                || code == KeyEvent.KEYCODE_BUTTON_A
                || code == KeyEvent.KEYCODE_F1
                || code == KeyEvent.KEYCODE_F9
                || code == KeyEvent.KEYCODE_GUIDE
                || code == KeyEvent.KEYCODE_CAPTIONS
                || code == KeyEvent.KEYCODE_TV_TELETEXT;
    }

    public static boolean isHandledKey(Context c, int keyCode) {
        if (Prefs.channelBrightness(c)) {
            if (isChannelUp(keyCode) || isChannelDown(keyCode)) {
                return true;
            }
        }
        if (Prefs.redKeySleep(c)) {
            if (isRedKey(keyCode)) {
                return true;
            }
        }
        return false;
    }

    public static boolean handleKeyEvent(final Context c, KeyEvent event) {
        recordKey(c, event);

        int code = event.getKeyCode();
        if (!isHandledKey(c, code)) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (Prefs.channelBrightness(c)) {
            if (isChannelUp(code)) {
                adjustBrightness(c, 10);
                return true;
            }
            if (isChannelDown(code)) {
                adjustBrightness(c, -10);
                return true;
            }
        }

        if (Prefs.redKeySleep(c)) {
            if (isRedKey(code)) {
                triggerSleep(c);
                return true;
            }
        }

        return false;
    }

    public static void updateKeyFilter(final Context c) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                HomeRedirectService s = HomeRedirectService.getInstance();
                if (s == null) return;
                try {
                    AccessibilityServiceInfo info = s.getServiceInfo();
                    if (info == null) {
                        info = new AccessibilityServiceInfo();
                    }
                    boolean filter = Prefs.channelBrightness(c) || Prefs.redKeySleep(c);
                    int flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                    if (filter) {
                        info.flags |= flag;
                    } else {
                        info.flags &= ~flag;
                    }
                    s.setServiceInfo(info);
                    Log.i(TAG, "updateKeyFilter success: filter=" + filter);
                } catch (Throwable t) {
                    Log.w(TAG, "updateKeyFilter failed: " + t);
                }
            }
        });
    }

    public static void adjustBrightness(Context c, int delta) {
        int cur = Prefs.screenBrightness(c);
        int next = Math.max(10, Math.min(100, cur + delta));
        Prefs.setScreenBrightness(c, next);
        applyBrightness(c);
        showHud(c, next);
    }

    public static void applyBrightness(final Context c) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                int level = Prefs.screenBrightness(c);

                // 1. If an Activity is live, adjust window brightness directly
                MainActivity act = getActiveActivity(c);
                if (act != null && !act.isFinishing()) {
                    try {
                        WindowManager.LayoutParams lp = act.getWindow().getAttributes();
                        lp.screenBrightness = level / 100f;
                        act.getWindow().setAttributes(lp);
                    } catch (Throwable t) {
                        Log.w(TAG, "set window screenBrightness failed: " + t);
                    }
                }

                // 2. Try Settings.System.SCREEN_BRIGHTNESS
                try {
                    int sysVal = Math.round(level * 255f / 100f);
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, sysVal);
                } catch (Throwable ignored) { }

                // 3. Try ADB if available
                if (AdbBridge.isReady(c)) {
                    int sysVal = Math.round(level * 255f / 100f);
                    AdbBridge.run("settings put system screen_brightness " + sysVal, null);
                }

                // 4. Update Dimmer View
                updateDimmer(c, level);
            }
        });
    }

    private static MainActivity getActiveActivity(Context c) {
        if (c instanceof MainActivity) return (MainActivity) c;
        MainActivity live = HomeRedirectService.getLiveActivity();
        if (live != null && !live.isFinishing()) return live;
        return null;
    }

    private static void updateDimmer(Context c, int level) {
        MainActivity act = getActiveActivity(c);
        HomeRedirectService s = HomeRedirectService.getInstance();

        if (level >= 100) {
            if (dimmerView != null) {
                try {
                    ViewGroup parent = (ViewGroup) dimmerView.getParent();
                    if (parent != null) parent.removeView(dimmerView);
                    else if (s != null) {
                        WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeView(dimmerView);
                    }
                } catch (Throwable ignored) { }
                dimmerView = null;
            }
            return;
        }

        int alpha = Math.min(210, Math.max(0, Math.round((100 - level) * 2.2f)));

        if (act != null && !act.isFinishing()) {
            ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
            if (dimmerView == null || dimmerView.getParent() != decor) {
                if (dimmerView != null && dimmerView.getParent() != null) {
                    ((ViewGroup) dimmerView.getParent()).removeView(dimmerView);
                }
                dimmerView = new View(act);
                dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
                FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                decor.addView(dimmerView, flp);
            } else {
                dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
                dimmerView.setVisibility(View.VISIBLE);
            }
        } else if (s != null) {
            WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
            if (dimmerView == null) {
                dimmerView = new View(s);
                dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                try {
                    wm.addView(dimmerView, lp);
                } catch (Throwable t) {
                    Log.w(TAG, "add overlay dimmer failed: " + t);
                }
            } else {
                dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
            }
        }
    }

    public static void showHud(final Context c, final int level) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                MainActivity act = getActiveActivity(c);
                HomeRedirectService s = HomeRedirectService.getInstance();

                if (act != null && !act.isFinishing()) {
                    showInActivityHud(act, level);
                } else if (s != null) {
                    showInServiceHud(s, level);
                } else {
                    // Safe toast fallback
                    try {
                        Toast.makeText(c.getApplicationContext(),
                                c.getString(R.string.hud_brightness, level),
                                Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) { }
                }
            }
        });
    }

    private static void showInActivityHud(MainActivity act, int level) {
        ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
        if (hudView == null || hudView.getParent() != decor) {
            hideHud();
            hudView = createHudView(act);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.topMargin = dp(act, 48);
            decor.addView(hudView, lp);
        }

        updateHudContent(act, level);
    }

    private static void showInServiceHud(HomeRedirectService s, int level) {
        WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        if (hudView == null) {
            hudView = createHudView(s);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = dp(s, 48);
            try {
                wm.addView(hudView, lp);
            } catch (Throwable t) {
                Log.w(TAG, "add hudView in service failed: " + t);
                return;
            }
        }

        updateHudContent(s, level);
    }

    private static void updateHudContent(Context c, int level) {
        if (hudText != null) {
            hudText.setText(c.getString(R.string.hud_brightness, level));
        }
        if (hudBarFill != null) {
            ViewGroup.LayoutParams p = hudBarFill.getLayoutParams();
            int maxW = dp(c, 140);
            p.width = Math.max(dp(c, 4), Math.round(maxW * level / 100f));
            hudBarFill.setLayoutParams(p);
        }

        MAIN.removeCallbacks(HIDE_HUD);
        MAIN.postDelayed(HIDE_HUD, 1800L);
    }

    private static void hideHud() {
        if (hudView != null) {
            try {
                ViewGroup parent = (ViewGroup) hudView.getParent();
                if (parent != null) {
                    parent.removeView(hudView);
                } else {
                    HomeRedirectService s = HomeRedirectService.getInstance();
                    if (s != null) {
                        WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeView(hudView);
                    }
                }
            } catch (Throwable ignored) { }
            hudView = null;
            hudText = null;
            hudBarFill = null;
        }
    }

    private static View createHudView(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        int padH = dp(c, 24);
        int padV = dp(c, 14);
        root.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6102C4A);
        bg.setCornerRadius(dp(c, 16));
        bg.setStroke(dp(c, 1), 0x55FFFFFF);
        root.setBackground(bg);

        hudText = new TextView(c);
        hudText.setTextColor(Color.WHITE);
        hudText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hudText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hudText.setGravity(Gravity.CENTER);
        root.addView(hudText);

        FrameLayout barTrack = new FrameLayout(c);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(dp(c, 140), dp(c, 8));
        trackLp.topMargin = dp(c, 10);
        barTrack.setLayoutParams(trackLp);

        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(0x40FFFFFF);
        trackBg.setCornerRadius(dp(c, 4));
        barTrack.setBackground(trackBg);

        hudBarFill = new View(c);
        FrameLayout.LayoutParams fillLp = new FrameLayout.LayoutParams(dp(c, 4), FrameLayout.LayoutParams.MATCH_PARENT);
        hudBarFill.setLayoutParams(fillLp);

        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setColor(0xFFA3BDED);
        fillBg.setCornerRadius(dp(c, 4));
        hudBarFill.setBackground(fillBg);

        barTrack.addView(hudBarFill);
        root.addView(barTrack);

        return root;
    }

    public static void triggerSleep(final Context c) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "triggerSleep started");
                boolean locked = false;
                HomeRedirectService s = HomeRedirectService.getInstance();

                // 1. AccessibilityService lock screen (API 28+)
                if (s != null && Build.VERSION.SDK_INT >= 28) {
                    try {
                        locked = s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                        Log.i(TAG, "GLOBAL_ACTION_LOCK_SCREEN: " + locked);
                    } catch (Throwable t) {
                        Log.w(TAG, "GLOBAL_ACTION_LOCK_SCREEN failed: " + t);
                    }
                }

                // 2. ADB keyevent 223 (KEYCODE_SLEEP)
                if (!locked && AdbBridge.isReady(c)) {
                    AdbBridge.run("input keyevent 223", null);
                    locked = true;
                    Log.i(TAG, "AdbBridge sent sleep");
                }

                // 3. AccessibilityService power dialog (API 21+)
                if (!locked && s != null) {
                    try {
                        locked = s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                        Log.i(TAG, "GLOBAL_ACTION_POWER_DIALOG: " + locked);
                    } catch (Throwable t) {
                        Log.w(TAG, "GLOBAL_ACTION_POWER_DIALOG failed: " + t);
                    }
                }

                // 4. In-Activity sleep screen (turns screen completely black)
                MainActivity act = getActiveActivity(c);
                if (act != null && !act.isFinishing()) {
                    showSleepScreen(act);
                } else {
                    try {
                        Toast.makeText(c.getApplicationContext(),
                                c.getString(R.string.sleep_hint),
                                Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) { }
                }
            }
        });
    }

    private static void showSleepScreen(final MainActivity act) {
        if (sleepView != null) {
            wakeFromSleep(act);
            return;
        }

        final ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
        final FrameLayout sleep = new FrameLayout(act);
        sleep.setBackgroundColor(Color.BLACK);
        sleep.setFocusable(true);
        sleep.setFocusableInTouchMode(true);
        sleep.setClickable(true);

        TextView hint = new TextView(act);
        hint.setText(act.getString(R.string.sleep_hint));
        hint.setTextColor(0x80FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = Gravity.CENTER;
        sleep.addView(hint, hintLp);

        sleep.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    wakeFromSleep(act);
                    return true;
                }
                return true;
            }
        });
        sleep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wakeFromSleep(act);
            }
        });

        sleepView = sleep;
        decor.addView(sleep, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sleep.requestFocus();

        // Dim brightness to minimum
        try {
            WindowManager.LayoutParams lp = act.getWindow().getAttributes();
            lp.screenBrightness = 0.01f;
            act.getWindow().setAttributes(lp);
        } catch (Throwable ignored) { }
    }

    private static void wakeFromSleep(MainActivity act) {
        if (sleepView != null) {
            try {
                ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
                decor.removeView(sleepView);
            } catch (Throwable ignored) { }
            sleepView = null;
        }
        applyBrightness(act);
    }

    private static int dp(Context c, int val) {
        return Math.round(val * c.getResources().getDisplayMetrics().density);
    }
}
