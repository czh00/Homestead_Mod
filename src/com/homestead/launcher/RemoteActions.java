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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles remote control key shortcuts:
 * 1. Channel Up / Down keys for global screen brightness adjustment (+/- 10%) with overlay dimmer & OSD.
 * 2. Four color keys (Red, Green, Yellow, Blue) and number keys (0-9) mapped to custom actions.
 */
public final class RemoteActions {

    private static final String TAG = "Homestead";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static View dimmerView;
    private static View hudView;
    private static TextView hudText;
    private static View hudBarFill;

    private static int lastKeyCode = 0;
    private static String lastKeyName = "";
    private static Runnable keyObserver;

    private static long lastAdjustTime = 0L;
    private static final long ADJUST_DEBOUNCE_MS = 160L;

    private static final Runnable HIDE_HUD = new Runnable() {
        @Override
        public void run() {
            hideHud();
        }
    };

    private RemoteActions() { }

    private static volatile boolean keyTesting = false;

    public static void setKeyTesting(boolean testing) {
        keyTesting = testing;
    }

    public static boolean isKeyTesting() {
        return keyTesting;
    }

    public static int getLastKeyCode() {
        return lastKeyCode;
    }

    public static String getLastKeyName() {
        return lastKeyName;
    }

    public static void setKeyObserver(Runnable r) {
        keyObserver = r;
    }

    public static boolean isNavKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
                return true;
            default:
                return false;
        }
    }

    public static String findKeyId(int keyCode) {
        if (isVidInfoKey(keyCode)) return "vid_info";
        if (isChannelUp(keyCode)) return "ch_up";
        if (isChannelDown(keyCode)) return "ch_down";
        if (isRedKey(keyCode)) return "red";
        if (isGreenKey(keyCode)) return "green";
        if (isYellowKey(keyCode)) return "yellow";
        if (isBlueKey(keyCode)) return "blue";
        int appIdx = getAppKeyIndex(keyCode);
        if (appIdx >= 1 && appIdx <= 4) return "app_" + appIdx;
        int num = getNumberFromKey(keyCode);
        if (num >= 0 && num <= 9) return "num_" + num;
        return null;
    }

    public static String getKeyMappingDescription(Context c) {
        if (lastKeyCode == 0) return "-";
        String keyId = findKeyId(lastKeyCode);
        if (keyId != null) {
            return keyTitle(c, keyId);
        }
        return c.getString(R.string.key_test_unmapped);
    }

    public static String getKeyActionDescription(Context c) {
        if (lastKeyCode == 0) return "-";
        String keyId = findKeyId(lastKeyCode);
        if (keyId != null) {
            String act = Prefs.keyAction(c, keyId);
            return act.isEmpty() ? c.getString(R.string.key_act_none) : actionLabel(c, act);
        }
        return c.getString(R.string.key_act_none);
    }

    public static String getLastKeyDescription(Context c) {
        if (lastKeyCode == 0) {
            return c.getString(R.string.set_remote_none);
        }
        String keyId = findKeyId(lastKeyCode);
        if (keyId != null) {
            String title = keyTitle(c, keyId);
            String act = Prefs.keyAction(c, keyId);
            String actLabel = act.isEmpty() ? c.getString(R.string.key_act_none) : actionLabel(c, act);
            return lastKeyName + " (" + lastKeyCode + ")  ✔ " + title + "  [" + actLabel + "]";
        }
        return lastKeyName + " (" + lastKeyCode + ")  ✔ " + c.getString(R.string.set_remote_key_captured);
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

    public static String keyTitle(Context c, String keyId) {
        if ("ch_up".equals(keyId)) return c.getString(R.string.key_ch_up);
        if ("ch_down".equals(keyId)) return c.getString(R.string.key_ch_down);
        if ("app_1".equals(keyId)) return c.getString(R.string.key_app_1);
        if ("app_2".equals(keyId)) return c.getString(R.string.key_app_2);
        if ("app_3".equals(keyId)) return c.getString(R.string.key_app_3);
        if ("app_4".equals(keyId)) return c.getString(R.string.key_app_4);
        if ("vid_info".equals(keyId)) return c.getString(R.string.key_vid_info);
        if ("red".equals(keyId)) return c.getString(R.string.key_color_red);
        if ("green".equals(keyId)) return c.getString(R.string.key_color_green);
        if ("yellow".equals(keyId)) return c.getString(R.string.key_color_yellow);
        if ("blue".equals(keyId)) return c.getString(R.string.key_color_blue);
        if (keyId != null && keyId.startsWith("num_")) {
            return c.getString(R.string.key_num_title, keyId.substring(4));
        }
        return keyId;
    }

    public static String getAppLabel(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    public static String actionLabel(Context c, String action) {
        if (action == null || action.isEmpty()) return c.getString(R.string.key_act_none);
        if ("sleep".equals(action)) return c.getString(R.string.key_act_sleep);
        if ("inputs".equals(action) || "input_picker".equals(action)) return c.getString(R.string.key_act_inputs);
        if ("cycle_inputs".equals(action)) return c.getString(R.string.key_act_cycle_inputs);
        if ("hdmi_1".equals(action)) return c.getString(R.string.key_act_hdmi_1);
        if ("hdmi_2".equals(action)) return c.getString(R.string.key_act_hdmi_2);
        if ("hdmi_3".equals(action)) return c.getString(R.string.key_act_hdmi_3);
        if ("hdmi_4".equals(action)) return c.getString(R.string.key_act_hdmi_4);
        if ("livetv".equals(action)) return c.getString(R.string.key_act_livetv);
        if ("mute".equals(action)) return c.getString(R.string.key_act_mute);
        if ("home".equals(action)) return c.getString(R.string.key_act_home);
        if ("back".equals(action)) return c.getString(R.string.key_act_back);
        if ("vol_up".equals(action)) return c.getString(R.string.key_act_vol_up);
        if ("vol_down".equals(action)) return c.getString(R.string.key_act_vol_down);
        if ("brightness_up".equals(action)) return c.getString(R.string.key_act_bright_up);
        if ("brightness_down".equals(action)) return c.getString(R.string.key_act_bright_down);
        if ("picture_settings".equals(action)) return c.getString(R.string.key_act_picture_settings);
        if ("screen_display".equals(action)) return c.getString(R.string.key_act_screen_display);
        if ("info_hud".equals(action)) return c.getString(R.string.key_act_info_hud);
        if ("set_homestead".equals(action)) return c.getString(R.string.key_act_set_homestead);
        if ("set_health".equals(action)) return c.getString(R.string.key_act_set_health);
        if ("set_debloat".equals(action)) return c.getString(R.string.key_act_set_debloat);
        if ("set_firetv".equals(action)) return c.getString(R.string.key_act_set_firetv);
        if ("sys_settings".equals(action)) return c.getString(R.string.key_act_sys_settings);
        if ("sys_display".equals(action)) return c.getString(R.string.key_act_sys_display);
        if ("sys_network".equals(action)) return c.getString(R.string.key_act_sys_network);
        if ("sys_apps".equals(action)) return c.getString(R.string.key_act_sys_apps);
        if ("sys_controllers".equals(action)) return c.getString(R.string.key_act_sys_controllers);
        if ("sys_preferences".equals(action)) return c.getString(R.string.key_act_sys_preferences);
        if ("sys_device".equals(action)) return c.getString(R.string.key_act_sys_device);
        if ("sys_accessibility".equals(action)) return c.getString(R.string.key_act_sys_accessibility);
        if ("sys_equipment".equals(action)) return c.getString(R.string.key_act_sys_equipment);
        if (action.startsWith("app:")) {
            String pkg = action.substring(4);
            return c.getString(R.string.key_act_open_app, getAppLabel(c, pkg));
        }
        return action;
    }

    public static boolean isChannelUp(int code) {
        return code == KeyEvent.KEYCODE_CHANNEL_UP
                || code == KeyEvent.KEYCODE_PAGE_UP;
    }

    public static boolean isChannelDown(int code) {
        return code == KeyEvent.KEYCODE_CHANNEL_DOWN
                || code == KeyEvent.KEYCODE_PAGE_DOWN;
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

    public static boolean isGreenKey(int code) {
        return code == KeyEvent.KEYCODE_PROG_GREEN
                || code == KeyEvent.KEYCODE_BUTTON_2
                || code == KeyEvent.KEYCODE_BUTTON_B
                || code == KeyEvent.KEYCODE_F2
                || code == KeyEvent.KEYCODE_F10;
    }

    public static boolean isYellowKey(int code) {
        return code == KeyEvent.KEYCODE_PROG_YELLOW
                || code == KeyEvent.KEYCODE_BUTTON_3
                || code == KeyEvent.KEYCODE_BUTTON_X
                || code == KeyEvent.KEYCODE_BUTTON_C
                || code == KeyEvent.KEYCODE_F3
                || code == KeyEvent.KEYCODE_F11;
    }

    public static boolean isBlueKey(int code) {
        return code == KeyEvent.KEYCODE_PROG_BLUE
                || code == KeyEvent.KEYCODE_BUTTON_4
                || code == KeyEvent.KEYCODE_BUTTON_Y
                || code == KeyEvent.KEYCODE_BUTTON_Z
                || code == KeyEvent.KEYCODE_F4
                || code == KeyEvent.KEYCODE_F12;
    }

    public static int getNumberFromKey(int code) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) {
            return code - KeyEvent.KEYCODE_0;
        }
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) {
            return code - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    public static int getAppKeyIndex(int code) {
        // Android KeyEvent.KEYCODE_APP_1 (287) to KEYCODE_APP_4 (290)
        if (code >= 287 && code <= 290) {
            return code - 286;
        }
        return -1;
    }

    public static boolean isVidInfoKey(int code) {
        return code == 334;
    }

    public static boolean isHandledKey(Context c, int keyCode) {
        if (isVidInfoKey(keyCode) && !Prefs.keyAction(c, "vid_info").isEmpty()) return true;
        if (isChannelUp(keyCode) && !Prefs.keyAction(c, "ch_up").isEmpty()) return true;
        if (isChannelDown(keyCode) && !Prefs.keyAction(c, "ch_down").isEmpty()) return true;
        if (isRedKey(keyCode) && !Prefs.keyAction(c, "red").isEmpty()) return true;
        if (isGreenKey(keyCode) && !Prefs.keyAction(c, "green").isEmpty()) return true;
        if (isYellowKey(keyCode) && !Prefs.keyAction(c, "yellow").isEmpty()) return true;
        if (isBlueKey(keyCode) && !Prefs.keyAction(c, "blue").isEmpty()) return true;

        int appIdx = getAppKeyIndex(keyCode);
        if (appIdx >= 1 && !Prefs.keyAction(c, "app_" + appIdx).isEmpty()) return true;

        int num = getNumberFromKey(keyCode);
        if (num >= 0 && !Prefs.keyAction(c, "num_" + num).isEmpty()) return true;

        return false;
    }

    public static boolean handleKeyEvent(final Context c, KeyEvent event) {
        int code = event.getKeyCode();

        // In test mode, allow UI navigation keys (D-pad & Back) to pass through to the UI
        if (keyTesting && isNavKey(code)) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            recordKey(c, event);
        }

        // In test mode, consume all non-nav keys so they never trigger actions or system default apps
        if (keyTesting) {
            return true;
        }

        if (!isHandledKey(c, code)) {
            return false;
        }

        // Consume UP events so they never leak to native Live TV or other apps
        if (event.getAction() == KeyEvent.ACTION_UP) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        // 1. Channel buttons
        if (isChannelUp(code)) {
            String act = Prefs.keyAction(c, "ch_up");
            if (!act.isEmpty()) {
                long now = SystemClock.uptimeMillis();
                if (now - lastAdjustTime >= ADJUST_DEBOUNCE_MS) {
                    lastAdjustTime = now;
                    executeAction(c, act);
                }
                return true;
            }
        }
        if (isChannelDown(code)) {
            String act = Prefs.keyAction(c, "ch_down");
            if (!act.isEmpty()) {
                long now = SystemClock.uptimeMillis();
                if (now - lastAdjustTime >= ADJUST_DEBOUNCE_MS) {
                    lastAdjustTime = now;
                    executeAction(c, act);
                }
                return true;
            }
        }

        // 2. Color buttons
        if (isRedKey(code)) {
            String act = Prefs.keyAction(c, "red");
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }
        if (isGreenKey(code)) {
            String act = Prefs.keyAction(c, "green");
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }
        if (isYellowKey(code)) {
            String act = Prefs.keyAction(c, "yellow");
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }
        if (isBlueKey(code)) {
            String act = Prefs.keyAction(c, "blue");
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }

        // 3. Dedicated app buttons (APP 1 ~ 4 / Keycodes 287 ~ 290)
        int appIdx = getAppKeyIndex(code);
        if (appIdx >= 1) {
            String act = Prefs.keyAction(c, "app_" + appIdx);
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }

        // 4. Number buttons (0 ~ 9)
        int num = getNumberFromKey(code);
        if (num >= 0) {
            String act = Prefs.keyAction(c, "num_" + num);
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }

        // 5. Video Info key (VID_SRC_INFO / 334)
        if (isVidInfoKey(code)) {
            String act = Prefs.keyAction(c, "vid_info");
            if (!act.isEmpty()) {
                executeAction(c, act);
                return true;
            }
        }

        return false;
    }

    public static void executeAction(Context c, String action) {
        if (action == null || action.isEmpty()) return;
        if ("sleep".equals(action)) {
            triggerSleep(c);
            return;
        }
        if ("inputs".equals(action) || "input_picker".equals(action)) {
            InputChooserActivity.open(c);
            return;
        }
        if ("cycle_inputs".equals(action)) {
            cycleInputs(c);
            return;
        }
        if ("hdmi_1".equals(action)) {
            switchToHdmiDirect(c, 1);
            return;
        }
        if ("hdmi_2".equals(action)) {
            switchToHdmiDirect(c, 2);
            return;
        }
        if ("hdmi_3".equals(action)) {
            switchToHdmiDirect(c, 3);
            return;
        }
        if ("hdmi_4".equals(action)) {
            switchToHdmiDirect(c, 4);
            return;
        }
        if ("livetv".equals(action)) {
            switchLiveTv(c);
            return;
        }
        if ("mute".equals(action)) {
            toggleMute(c);
            return;
        }
        if ("home".equals(action)) {
            goHome(c);
            return;
        }
        if ("back".equals(action)) {
            goBack(c);
            return;
        }
        if ("vol_up".equals(action)) {
            adjustVolume(c, AudioManager.ADJUST_RAISE);
            return;
        }
        if ("vol_down".equals(action)) {
            adjustVolume(c, AudioManager.ADJUST_LOWER);
            return;
        }
        if ("brightness_up".equals(action)) {
            adjustBrightness(c, 10);
            return;
        }
        if ("brightness_down".equals(action)) {
            adjustBrightness(c, -10);
            return;
        }
        if ("picture_settings".equals(action)) {
            openPictureSettings(c);
            return;
        }
        if ("screen_display".equals(action)) {
            showScreenDisplay(c);
            return;
        }
        if ("info_hud".equals(action)) {
            InfoHud.toggle(c);
            return;
        }
        if ("set_homestead".equals(action)) {
            openHomesteadPanel(c, "settings");
            return;
        }
        if ("set_health".equals(action)) {
            openHomesteadPanel(c, "health");
            return;
        }
        if ("set_debloat".equals(action)) {
            openHomesteadPanel(c, "debloat");
            return;
        }
        if ("set_firetv".equals(action)) {
            openHomesteadPanel(c, "firetv");
            return;
        }
        if (action.startsWith("sys_")) {
            launchSystemSetting(c, action);
            return;
        }
        if (action.startsWith("app:")) {
            launchApp(c, action.substring(4));
            return;
        }
    }

    public static void switchToHdmiDirect(Context c, int hdmiNumber) {
        int inputType = 2;
        switch (hdmiNumber) {
            case 1: inputType = 2; break;
            case 2: inputType = 3; break;
            case 3: inputType = 4; break;
            case 4: inputType = 10; break;
        }

        // 1. Try KaminoInputSwitchService (Native Fire OS HDMI input switcher)
        try {
            Intent kaminoIntent = new Intent().setComponent(new ComponentName(
                    "com.amazon.tv.inputpreference.service",
                    "com.amazon.tv.inputpreference.service.KaminoInputSwitchService"));
            kaminoIntent.putExtra("inputType", inputType);
            if (c.startService(kaminoIntent) != null) {
                Log.i(TAG, "Switched to HDMI " + hdmiNumber + " via Kamino, type=" + inputType);
                return;
            }
        } catch (Throwable ignored) { }

        // 2. Fallback: Search sorted inputs and launch
        try {
            List<InputChooserActivity.InputItem> items = InputChooserActivity.getSortedInputs(c);
            for (int i = 0; i < items.size(); i++) {
                InputChooserActivity.InputItem it = items.get(i);
                if (it.portNumber == hdmiNumber) {
                    InputChooserActivity.switchToInput(c, it.inputId, it.label);
                    return;
                }
            }
        } catch (Throwable ignored) { }
    }

    private static int lastInputIndex = -1;

    public static void cycleInputs(Context c) {
        try {
            List<InputChooserActivity.InputItem> items = InputChooserActivity.getSortedInputs(c);
            if (items.isEmpty()) return;

            // Prioritize connected inputs
            List<InputChooserActivity.InputItem> list = new ArrayList<InputChooserActivity.InputItem>();
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).connected) list.add(items.get(i));
            }
            if (list.isEmpty()) list = items;

            lastInputIndex = (lastInputIndex + 1) % list.size();
            InputChooserActivity.InputItem target = list.get(lastInputIndex);
            InputChooserActivity.switchToInput(c, target.inputId, target.label);
        } catch (Throwable ignored) { }
    }

    public static void switchLiveTv(Context c) {
        if (!AppModel.liveTvAvailable(c)) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("livetv://showLiveTVButtonContent/"));
            i.setComponent(new ComponentName(AppModel.LIVETV_PKG, AppModel.LIVETV_SVC));
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable t) {
            Toast.makeText(c, R.string.home_livetv_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static void toggleMute(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                if (Build.VERSION.SDK_INT >= 23) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI);
                } else {
                    boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
                    am.setStreamMute(AudioManager.STREAM_MUSIC, !muted);
                }
            }
        } catch (Throwable ignored) { }
        if (AdbBridge.isReady(c)) {
            AdbBridge.run("input keyevent 164", null);
        }
    }

    private static void adjustVolume(Context c, int direction) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            }
        } catch (Throwable ignored) { }
        if (AdbBridge.isReady(c)) {
            int key = (direction == AudioManager.ADJUST_RAISE) ? 24 : 25;
            AdbBridge.run("input keyevent " + key, null);
        }
    }

    private static void goHome(Context c) {
        HomeRedirectService s = HomeRedirectService.getInstance();
        if (s != null) {
            try {
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                return;
            } catch (Throwable ignored) { }
        }
        MainActivity.open(c);
    }

    private static void goBack(Context c) {
        HomeRedirectService s = HomeRedirectService.getInstance();
        if (s != null) {
            try {
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                return;
            } catch (Throwable ignored) { }
        }
        if (AdbBridge.isReady(c)) {
            AdbBridge.run("input keyevent 4", null);
        }
    }

    private static void launchApp(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent it = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (it == null) it = pm.getLaunchIntentForPackage(pkg);
            if (it != null) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(it);
            }
        } catch (Throwable t) {
            Log.w(TAG, "launchApp failed for " + pkg + ": " + t);
        }
    }

    public static boolean hasAnyCustomKey(Context c) {
        if (!Prefs.keyAction(c, "vid_info").isEmpty()) return true;
        if (!Prefs.keyAction(c, "ch_up").isEmpty()) return true;
        if (!Prefs.keyAction(c, "ch_down").isEmpty()) return true;
        if (!Prefs.keyAction(c, "red").isEmpty()) return true;
        if (!Prefs.keyAction(c, "green").isEmpty()) return true;
        if (!Prefs.keyAction(c, "yellow").isEmpty()) return true;
        if (!Prefs.keyAction(c, "blue").isEmpty()) return true;
        for (int i = 1; i <= 4; i++) {
            if (!Prefs.keyAction(c, "app_" + i).isEmpty()) return true;
        }
        for (int i = 0; i <= 9; i++) {
            if (!Prefs.keyAction(c, "num_" + i).isEmpty()) return true;
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
                    boolean filter = hasAnyCustomKey(c);
                    int flag = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
                    if (filter) {
                        info.flags |= flag;
                    } else {
                        info.flags &= ~flag;
                    }
                    s.setServiceInfo(info);
                    Log.i(TAG, "updateKeyFilter success: filter=" + filter);
                    if (filter && AdbBridge.isReady(c)) {
                        AdbBridge.connect();
                    }
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
                int sysVal = Math.round(level * 255f / 100f);

                // 1. Try system-wide SCREEN_BRIGHTNESS setting
                try {
                    Settings.System.putInt(c.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                    Settings.System.putInt(c.getContentResolver(),
                            Settings.System.SCREEN_BRIGHTNESS, sysVal);
                } catch (Throwable ignored) { }

                // 2. Try ADB if available
                if (AdbBridge.isReady(c)) {
                    AdbBridge.run(new String[] {
                            "pm grant " + c.getPackageName() + " android.permission.WRITE_SETTINGS",
                            "settings put system screen_brightness_mode 0",
                            "settings put system screen_brightness " + sysVal,
                            "settings put global picture_backlight " + level
                    }, null);
                }

                // 3. Update window brightness for active launcher activity
                MainActivity act = HomeRedirectService.getLiveActivity();
                if (act != null && !act.isFinishing()) {
                    try {
                        WindowManager.LayoutParams lp = act.getWindow().getAttributes();
                        lp.screenBrightness = (level >= 100)
                                ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                : (level / 100f);
                        act.getWindow().setAttributes(lp);
                    } catch (Throwable ignored) { }
                }

                // 4. Update or clear dimmer overlay based on brightness mode
                // At 100% (max brightness), the mask is completely removed to protect screenshots and HDR/Dolby Vision playback!
                if (level >= 100 || !Prefs.isBrightnessModeOverlay(c)) {
                    removeGlobalDimmer();
                } else {
                    updateGlobalDimmer(c, level);
                }
            }
        });
    }

    public static void removeGlobalDimmer() {
        if (dimmerView != null) {
            try {
                dimmerView.setVisibility(View.GONE);
                HomeRedirectService s = HomeRedirectService.getInstance();
                if (s != null) {
                    WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        try {
                            wm.removeView(dimmerView);
                        } catch (Throwable ignored) { }
                    }
                }
                ViewParent parent = dimmerView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(dimmerView);
                }
            } catch (Throwable ignored) { }
            dimmerView = null;
        }

        // Also ensure active launcher activity window attributes have no override
        MainActivity act = HomeRedirectService.getLiveActivity();
        if (act != null && !act.isFinishing()) {
            try {
                WindowManager.LayoutParams lp = act.getWindow().getAttributes();
                if (lp.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                    act.getWindow().setAttributes(lp);
                }
            } catch (Throwable ignored) { }
        }
    }

    public static void openPictureSettings(Context c) {
        // 1. If ADB is ready or tunnel can run, KEYCODE_SETTINGS (176) opens the TV's native quick settings overlay
        if (AdbBridge.isReady(c) || AdbBridge.canRun(c)) {
            AdbBridge.run("input keyevent 176", null);
            return;
        }

        // 2. Try Fire TV Display & Sounds activity
        try {
            Intent it = new Intent();
            it.setComponent(new ComponentName("com.amazon.tv.settings.v2",
                    "com.amazon.tv.settings.v2.tv.display_sounds.DisplayAndSoundsActivity"));
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(it);
            return;
        } catch (Throwable ignored) { }

        // 3. Try standard Android display settings
        try {
            Intent it = new Intent(Settings.ACTION_DISPLAY_SETTINGS);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(it);
            return;
        } catch (Throwable ignored) { }

        // 4. Try Accessibility Quick Settings
        HomeRedirectService s = HomeRedirectService.getInstance();
        if (s != null && Build.VERSION.SDK_INT >= 28) {
            try {
                s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
            } catch (Throwable ignored) { }
        }
    }

    public static void showScreenDisplay(final Context c) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                // Automate opening TV Picture Settings (176), waiting for overlay window focus (0.28s),
                // moving down 7 times in batch (20*7), settling focus (0.08s), and clicking OK (23).
                // Total execution time ~0.5s (down from 3.5s in v1 while ensuring no dropped keys).
                final String cmd = "input keyevent 176 && sleep 0.28 && input keyevent 20 20 20 20 20 20 20 && sleep 0.08 && input keyevent 23";

                if (AdbBridge.isReady(c) || AdbBridge.canRun(c)) {
                    AdbBridge.run(cmd, null);
                    return;
                }

                // Fallback: try AdbBridge run directly (which will auto-connect)
                AdbBridge.run(cmd, null);
            }
        });
    }

    public static void openHomesteadPanel(Context c, String panelType) {
        try {
            Intent it = new Intent(c, MainActivity.class);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            it.putExtra("show_panel", panelType);
            c.startActivity(it);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to start MainActivity for panel " + panelType, t);
        }
        MainActivity act = HomeRedirectService.getLiveActivity();
        if (act != null && !act.isFinishing()) {
            act.openPanelDirect(panelType);
        }
    }

    public static void launchSystemSetting(Context c, String actionId) {
        if ("sys_settings".equals(actionId)) {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            if (!SetupPanel.start(c, intent)) {
                intent = new Intent();
                intent.setComponent(new ComponentName("com.amazon.tv.settings.v2",
                        "com.amazon.tv.settings.v2.RootSettings"));
                if (!SetupPanel.start(c, intent)) {
                    intent = new Intent();
                    intent.setComponent(new ComponentName("com.amazon.tv.settings",
                            "com.amazon.tv.settings.RootSettings"));
                    if (!SetupPanel.start(c, intent) && AdbBridge.isReady(c)) {
                        AdbBridge.run("input keyevent 176", null);
                    }
                }
            }
            return;
        }
        if ("sys_display".equals(actionId)) {
            Intent intent = FireTvSettingsPanel.intentFor("com.amazon.device.settings.action.DISPLAY_AND_SOUNDS");
            if (!SetupPanel.start(c, intent)) {
                SetupPanel.start(c, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
            }
            return;
        }
        if ("sys_network".equals(actionId)) {
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            if (!SetupPanel.start(c, intent)) {
                SetupPanel.start(c, FireTvSettingsPanel.intentFor("android.settings.WIFI_SETTINGS"));
            }
            return;
        }
        if ("sys_apps".equals(actionId)) {
            Intent intent = FireTvSettingsPanel.intentFor("com.amazon.device.settings.action.APPLICATIONS");
            if (!SetupPanel.start(c, intent)) {
                SetupPanel.start(c, new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
            }
            return;
        }
        if ("sys_controllers".equals(actionId)) {
            Intent intent = FireTvSettingsPanel.intentFor("com.amazon.device.settings.action.CONTROLLERS");
            if (!SetupPanel.start(c, intent)) {
                SetupPanel.start(c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            }
            return;
        }
        if ("sys_preferences".equals(actionId)) {
            Intent intent = FireTvSettingsPanel.intentFor("com.amazon.device.settings.action.PREFERENCES");
            SetupPanel.start(c, intent);
            return;
        }
        if ("sys_device".equals(actionId)) {
            Intent intent = FireTvSettingsPanel.intentFor("com.amazon.device.settings.action.DEVICE");
            if (!SetupPanel.start(c, intent)) {
                SetupPanel.start(c, new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
            }
            return;
        }
        if ("sys_accessibility".equals(actionId)) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if (!SetupPanel.start(c, intent)) {
                SetupPanel.start(c, FireTvSettingsPanel.intentFor("com.amazon.device.settings.action.ACCESSIBILITY"));
            }
            return;
        }
        if ("sys_equipment".equals(actionId)) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.amazon.tv.devicecontrolsettings",
                    "com.amazon.tv.devicecontrolsettings.screens.main_menu.OzOobeSettingsActivity"));
            SetupPanel.start(c, intent);
            return;
        }
    }

    private static void updateGlobalDimmer(Context c, int level) {
        HomeRedirectService s = HomeRedirectService.getInstance();
        if (s == null) {
            // Service not yet connected: fall back to active activity window if present
            MainActivity act = HomeRedirectService.getLiveActivity();
            if (act != null && !act.isFinishing()) {
                try {
                    WindowManager.LayoutParams lp = act.getWindow().getAttributes();
                    lp.screenBrightness = level / 100f;
                    act.getWindow().setAttributes(lp);
                } catch (Throwable ignored) { }
            }
            return;
        }

        WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        if (level >= 100) {
            if (dimmerView != null) {
                try {
                    wm.removeView(dimmerView);
                } catch (Throwable ignored) { }
                dimmerView = null;
            }
            return;
        }

        // Alpha calculation: from 0 (at 100%) to ~215 (at 10%)
        int alpha = Math.min(215, Math.max(0, Math.round((100 - level) * 2.3f)));

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
                Log.w(TAG, "add global dimmer failed: " + t);
            }
        } else {
            dimmerView.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
            dimmerView.setVisibility(View.VISIBLE);
        }
    }

    public static void showHud(final Context c, final int level) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                HomeRedirectService s = HomeRedirectService.getInstance();
                if (s != null) {
                    showOverlayHud(s, level);
                } else {
                    MainActivity act = HomeRedirectService.getLiveActivity();
                    if (act != null && !act.isFinishing()) {
                        showActivityHud(act, level);
                    } else {
                        try {
                            Toast.makeText(c.getApplicationContext(),
                                    c.getString(R.string.hud_brightness, level),
                                    Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignored) { }
                    }
                }
            }
        });
    }

    private static void showOverlayHud(HomeRedirectService s, int level) {
        WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

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
                Log.w(TAG, "add overlay hudView failed: " + t);
                return;
            }
        }

        updateHudContent(s, level);
    }

    private static void showActivityHud(MainActivity act, int level) {
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

    private static void updateHudContent(Context c, int level) {
        if (hudText != null) {
            if (level >= 100) {
                hudText.setText(c.getString(R.string.hud_brightness_max));
            } else {
                hudText.setText(c.getString(R.string.hud_brightness, level));
            }
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
                HomeRedirectService s = HomeRedirectService.getInstance();
                if (s != null) {
                    WindowManager wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) {
                        try {
                            wm.removeView(hudView);
                        } catch (Throwable ignored) { }
                    }
                }
                ViewParent parent = hudView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(hudView);
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

    /**
     * Puts the TV hardware to sleep. No blocking overlays or messages; wake up is clean
     * and immediate as handled by the TV's native power management.
     */
    public static void triggerSleep(final Context c) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "triggerSleep started");
                boolean locked = false;
                HomeRedirectService s = HomeRedirectService.getInstance();

                // 1. AccessibilityService lock screen (API 28+, Android 9 / Fire OS 7 & 8)
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

                // 3. Fallback: Accessibility power dialog (API 21+)
                if (!locked && s != null) {
                    try {
                        locked = s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                        Log.i(TAG, "GLOBAL_ACTION_POWER_DIALOG: " + locked);
                    } catch (Throwable t) {
                        Log.w(TAG, "GLOBAL_ACTION_POWER_DIALOG failed: " + t);
                    }
                }
            }
        });
    }

    private static int dp(Context c, int val) {
        return Math.round(val * c.getResources().getDisplayMetrics().density);
    }
}
