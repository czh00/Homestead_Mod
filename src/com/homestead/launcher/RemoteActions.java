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

/**
 * Handles remote control key shortcuts:
 * 1. Channel Up / Down keys for screen brightness adjustment (+/- 10%) with overlay dimmer & OSD.
 * 2. Red button (KEYCODE_PROG_RED) for sleep / power switch.
 */
public final class RemoteActions {

    private static final String TAG = "Homestead";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View dimmerView;
    private static View hudView;
    private static TextView hudText;
    private static View hudBarFill;
    private static WindowManager wm;

    private static final Runnable HIDE_HUD = new Runnable() {
        @Override
        public void run() {
            hideHud();
        }
    };

    private RemoteActions() { }

    public static boolean isHandledKey(Context c, int keyCode) {
        if (Prefs.channelBrightness(c)) {
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP
                    || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN
                    || keyCode == KeyEvent.KEYCODE_PAGE_UP
                    || keyCode == KeyEvent.KEYCODE_PAGE_DOWN) {
                return true;
            }
        }
        if (Prefs.redKeySleep(c)) {
            if (keyCode == KeyEvent.KEYCODE_PROG_RED) {
                return true;
            }
        }
        return false;
    }

    public static boolean handleKeyEvent(final Context c, KeyEvent event) {
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
            if (code == KeyEvent.KEYCODE_CHANNEL_UP || code == KeyEvent.KEYCODE_PAGE_UP) {
                adjustBrightness(c, 10);
                return true;
            }
            if (code == KeyEvent.KEYCODE_CHANNEL_DOWN || code == KeyEvent.KEYCODE_PAGE_DOWN) {
                adjustBrightness(c, -10);
                return true;
            }
        }

        if (Prefs.redKeySleep(c)) {
            if (code == KeyEvent.KEYCODE_PROG_RED) {
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
                    if (info == null) return;
                    boolean filter = Prefs.channelBrightness(c) || Prefs.redKeySleep(c);
                    int flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                    if (filter) {
                        info.flags |= flag;
                    } else {
                        info.flags &= ~flag;
                    }
                    s.setServiceInfo(info);
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
                // 1. Try system brightness
                try {
                    int sysVal = Math.round(level * 255f / 100f);
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, sysVal);
                } catch (Throwable ignored) { }

                // 2. Try ADB if available
                if (AdbBridge.isReady(c)) {
                    int sysVal = Math.round(level * 255f / 100f);
                    AdbBridge.run("settings put system screen_brightness " + sysVal, null);
                }

                // 3. Update overlay dimmer for HDMI TVs/sticks
                updateDimmer(c, level);
            }
        });
    }

    private static WindowManager getWindowManager(Context c) {
        if (wm == null) {
            wm = (WindowManager) c.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        }
        return wm;
    }

    private static int overlayType() {
        if (Build.VERSION.SDK_INT >= 22) {
            HomeRedirectService s = HomeRedirectService.getInstance();
            if (s != null) {
                return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            }
        }
        if (Build.VERSION.SDK_INT >= 26) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
        return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    private static void updateDimmer(Context c, int level) {
        WindowManager m = getWindowManager(c);
        if (m == null) return;

        if (level >= 100) {
            if (dimmerView != null) {
                try {
                    m.removeView(dimmerView);
                } catch (Throwable ignored) { }
                dimmerView = null;
            }
            return;
        }

        // Alpha calculation: from 0 (at 100%) to ~210 (at 10%)
        int alpha = Math.min(210, Math.max(0, Math.round((100 - level) * 2.2f)));

        if (dimmerView == null) {
            dimmerView = new View(c.getApplicationContext());
            dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            try {
                m.addView(dimmerView, lp);
            } catch (Throwable t) {
                Log.w(TAG, "add dimmerView failed: " + t);
            }
        } else {
            dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        }
    }

    public static void showHud(final Context c, final int level) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                WindowManager m = getWindowManager(c);
                if (m == null) return;

                if (hudView == null) {
                    hudView = createHudView(c);
                    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            overlayType(),
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                    lp.y = dp(c, 48);
                    try {
                        m.addView(hudView, lp);
                    } catch (Throwable t) {
                        Log.w(TAG, "add hudView failed: " + t);
                        return;
                    }
                }

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
        });
    }

    private static void hideHud() {
        if (hudView != null && wm != null) {
            try {
                wm.removeView(hudView);
            } catch (Throwable ignored) { }
            hudView = null;
            hudText = null;
            hudBarFill = null;
        }
    }

    private static View createHudView(Context c) {
        LinearLayout root = new LinearLayout(c.getApplicationContext());
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

        hudText = new TextView(c.getApplicationContext());
        hudText.setTextColor(Color.WHITE);
        hudText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        hudText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hudText.setGravity(Gravity.CENTER);
        root.addView(hudText);

        FrameLayout barTrack = new FrameLayout(c.getApplicationContext());
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(dp(c, 140), dp(c, 8));
        trackLp.topMargin = dp(c, 10);
        barTrack.setLayoutParams(trackLp);

        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setColor(0x40FFFFFF);
        trackBg.setCornerRadius(dp(c, 4));
        barTrack.setBackground(trackBg);

        hudBarFill = new View(c.getApplicationContext());
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
                boolean locked = false;
                HomeRedirectService s = HomeRedirectService.getInstance();
                if (s != null && Build.VERSION.SDK_INT >= 28) {
                    try {
                        locked = s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN);
                    } catch (Throwable t) {
                        Log.w(TAG, "GLOBAL_ACTION_LOCK_SCREEN failed: " + t);
                    }
                }
                if (!locked && AdbBridge.isReady(c)) {
                    AdbBridge.run("input keyevent 223", null);
                    locked = true;
                }
                if (!locked) {
                    try {
                        Runtime.getRuntime().exec("input keyevent 223");
                        locked = true;
                    } catch (Throwable ignored) { }
                }
                if (!locked && s != null) {
                    try {
                        s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                    } catch (Throwable ignored) { }
                }
            }
        });
    }

    private static int dp(Context c, int val) {
        return Math.round(val * c.getResources().getDisplayMetrics().density);
    }
}
