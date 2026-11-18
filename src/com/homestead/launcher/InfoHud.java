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

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compact semi-transparent floating mini-HUD showing live video/audio playback statistics:
 * true foreground app, resolution, frame rate (FPS), live network download bitrate,
 * HDR/Dolby Vision status, and video/audio codecs.
 *
 * Toggled on/off with a single remote press (e.g. VID_SRC_INFO 334).
 */
public final class InfoHud {

    private static final String TAG = "Homestead";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context appContext;
    private static View hudView;
    private static TextView tvAppVal;
    private static TextView tvResVal;
    private static TextView tvFpsVal;
    private static TextView tvBitrateVal;
    private static TextView tvHdrVal;
    private static TextView tvVideoVal;
    private static TextView tvAudioVal;

    private static String detectedForegroundPkg = null;
    private static int detectedForegroundUid = -1;

    private static long lastRxBytes = -1L;
    private static long lastRxTime = -1L;
    private static int adbQueryCounter = 0;
    private static boolean adbQueryInProgress = false;

    private static String cachedVideoCodec = null;
    private static String cachedAudioCodec = null;

    private static final Pattern FOCUS_PKG_PATTERN =
            Pattern.compile("mCurrentFocus=Window\\{[^}]*\\s(?:u\\d+\\s+)?([a-zA-Z0-9._]+)/");

    private static final Pattern ACTIVITY_PKG_PATTERN =
            Pattern.compile("ACTIVITY\\s+([a-zA-Z0-9._]+)/");

    private InfoHud() { }

    public static boolean isShowing() {
        return hudView != null;
    }

    public static void toggle(final Context c) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (isShowing()) {
                    hide();
                } else {
                    show(c);
                }
            }
        });
    }

    public static void show(Context c) {
        if (c == null) return;
        appContext = c.getApplicationContext();

        final WindowManager wm = getWindowManager();
        if (wm == null) {
            Log.w(TAG, "InfoHud: WindowManager not available");
            return;
        }

        if (hudView != null) {
            hide();
        }

        // Detect initial foreground application before creating view
        detectedForegroundPkg = getInitialForegroundPackage(c);
        detectedForegroundUid = resolveUid(c, detectedForegroundPkg);

        hudView = createHudView(c);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = dp(c, 20);
        lp.y = dp(c, 20);

        hudView.setAlpha(0f);
        hudView.setScaleX(0.94f);
        hudView.setScaleY(0.94f);

        try {
            wm.addView(hudView, lp);
            hudView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start();
        } catch (Throwable t) {
            Log.w(TAG, "InfoHud: addView failed: " + t);
            hudView = null;
            return;
        }

        lastRxBytes = readRxBytes();
        lastRxTime = SystemClock.elapsedRealtime();
        adbQueryCounter = 0;

        updateMetrics();
        queryAdbMediaMetrics();

        MAIN.removeCallbacks(UPDATE_RUNNABLE);
        MAIN.postDelayed(UPDATE_RUNNABLE, 1000L);
    }

    public static void hide() {
        MAIN.removeCallbacks(UPDATE_RUNNABLE);
        if (hudView != null) {
            final View v = hudView;
            hudView = null;
            try {
                v.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(140)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    WindowManager wm = getWindowManager();
                                    if (wm != null) wm.removeView(v);
                                } catch (Throwable ignored) { }
                            }
                        }).start();
            } catch (Throwable t) {
                try {
                    WindowManager wm = getWindowManager();
                    if (wm != null) wm.removeView(v);
                } catch (Throwable ignored) { }
            }
        }
        tvAppVal = null;
        tvResVal = null;
        tvFpsVal = null;
        tvBitrateVal = null;
        tvHdrVal = null;
        tvVideoVal = null;
        tvAudioVal = null;
    }

    private static final Runnable UPDATE_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            if (hudView == null) return;
            updateMetrics();

            // Refresh deep ADB inspection every 2 seconds
            adbQueryCounter++;
            if (adbQueryCounter % 2 == 0) {
                queryAdbMediaMetrics();
            }

            MAIN.postDelayed(this, 1000L);
        }
    };

    private static void updateMetrics() {
        if (appContext == null || hudView == null) return;

        // 1. Foreground Application
        if (detectedForegroundPkg != null && !detectedForegroundPkg.isEmpty()) {
            tvAppVal.setText(formatAppDisplay(appContext, detectedForegroundPkg));
        } else {
            tvAppVal.setText("-");
        }

        // 2. Display Resolution & Refresh Rate (FPS)
        DisplayManager dm = (DisplayManager) appContext.getSystemService(Context.DISPLAY_SERVICE);
        Display display = (dm != null) ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (display != null) {
            Display.Mode mode = display.getMode();
            int w = mode.getPhysicalWidth();
            int h = mode.getPhysicalHeight();
            float refresh = mode.getRefreshRate();

            String resStr;
            if (w >= 3840 && h >= 2160) {
                resStr = "3840×2160 (4K)";
            } else if (w >= 2560 && h >= 1440) {
                resStr = "2560×1440 (2K)";
            } else if (w >= 1920 && h >= 1080) {
                resStr = "1920×1080 (FHD)";
            } else if (w >= 1280 && h >= 720) {
                resStr = "1280×720 (HD)";
            } else {
                resStr = w + "×" + h;
            }
            tvResVal.setText(resStr);

            String fpsStr;
            if (Math.abs(refresh - 23.976f) < 0.15f || Math.abs(refresh - 24.0f) < 0.15f) {
                fpsStr = String.format(Locale.US, "%.2f Hz (24p電影)", refresh);
            } else if (Math.abs(refresh - 59.94f) < 0.15f || Math.abs(refresh - 60.0f) < 0.15f) {
                fpsStr = String.format(Locale.US, "%.2f Hz (60p)", refresh);
            } else if (Math.abs(refresh - 50.0f) < 0.15f) {
                fpsStr = String.format(Locale.US, "%.2f Hz (50p)", refresh);
            } else if (Math.abs(refresh - 119.88f) < 0.2f || Math.abs(refresh - 120.0f) < 0.2f) {
                fpsStr = String.format(Locale.US, "%.2f Hz (120p)", refresh);
            } else {
                fpsStr = String.format(Locale.US, "%.2f Hz", refresh);
            }
            tvFpsVal.setText(fpsStr);

            // 3. HDR / Color Mode
            String hdrStr = "SDR (標準)";
            boolean isHdr = false;
            if (Build.VERSION.SDK_INT >= 26) {
                isHdr = display.isHdr();
            }
            if (isHdr) {
                hdrStr = "HDR (高動態)";
            }
            if (Build.VERSION.SDK_INT >= 24) {
                Display.HdrCapabilities hdrCap = display.getHdrCapabilities();
                if (hdrCap != null) {
                    int[] types = hdrCap.getSupportedHdrTypes();
                    if (types != null) {
                        for (int t : types) {
                            if (t == 1) { // HDR_TYPE_DOLBY_VISION
                                if (isHdr) hdrStr = "Dolby Vision (杜比視界)";
                                break;
                            }
                        }
                    }
                }
            }
            tvHdrVal.setText(hdrStr);
        } else {
            tvResVal.setText("-");
            tvFpsVal.setText("-");
            tvHdrVal.setText("SDR");
        }

        // 4. Live Bitrate (Network throughput)
        long nowRx = readRxBytes();
        long nowTime = SystemClock.elapsedRealtime();
        if (lastRxBytes >= 0 && lastRxTime > 0 && nowTime > lastRxTime) {
            long deltaBytes = nowRx - lastRxBytes;
            long deltaMs = nowTime - lastRxTime;
            if (deltaBytes >= 0 && deltaMs > 0) {
                double bps = (deltaBytes * 8.0 * 1000.0) / deltaMs;
                double mbps = bps / 1_000_000.0;
                double mbSec = (deltaBytes * 1000.0) / (deltaMs * 1024.0 * 1024.0);
                if (mbps >= 1.0) {
                    tvBitrateVal.setText(String.format(Locale.US, "%.1f Mbps (%.1f MB/s)", mbps, mbSec));
                } else {
                    double kbps = bps / 1_000.0;
                    tvBitrateVal.setText(String.format(Locale.US, "%.0f Kbps", kbps));
                }
            }
        } else {
            tvBitrateVal.setText("計算中...");
        }
        lastRxBytes = nowRx;
        lastRxTime = nowTime;

        // 5. Video & Audio Codecs
        if (cachedVideoCodec != null) {
            tvVideoVal.setText(cachedVideoCodec);
        } else {
            tvVideoVal.setText("硬體解碼就緒");
        }

        if (cachedAudioCodec != null) {
            tvAudioVal.setText(cachedAudioCodec);
        } else {
            tvAudioVal.setText(getAudioOutputSummary(appContext));
        }
    }

    private static long readRxBytes() {
        if (detectedForegroundUid > 0) {
            long uidRx = TrafficStats.getUidRxBytes(detectedForegroundUid);
            if (uidRx > 0) return uidRx;
        }
        return TrafficStats.getTotalRxBytes();
    }

    private static String getAudioOutputSummary(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && Build.VERSION.SDK_INT >= 23) {
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo d : devices) {
                int t = d.getType();
                if (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_HDMI_EARC) {
                    return "HDMI eARC (全景聲回傳)";
                } else if (t == AudioDeviceInfo.TYPE_HDMI_ARC) {
                    return "HDMI ARC";
                } else if (t == AudioDeviceInfo.TYPE_HDMI) {
                    return "HDMI 輸出";
                } else if (t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    return "藍牙音訊";
                }
            }
        }
        return "電視揚聲器";
    }

    private static String getInitialForegroundPackage(Context c) {
        // 1. Try UsageStatsManager (recent usage in last 120s)
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                long time = System.currentTimeMillis();
                List<UsageStats> list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 120000, time);
                if (list != null && !list.isEmpty()) {
                    UsageStats mostRecent = null;
                    for (UsageStats u : list) {
                        String pkg = u.getPackageName();
                        if (pkg == null || pkg.equals(c.getPackageName()) || pkg.equals("com.amazon.tv.launcher")) {
                            continue;
                        }
                        if (mostRecent == null || u.getLastTimeUsed() > mostRecent.getLastTimeUsed()) {
                            mostRecent = u;
                        }
                    }
                    if (mostRecent != null && mostRecent.getPackageName() != null) {
                        return mostRecent.getPackageName();
                    }
                }
            }
        } catch (Throwable ignored) { }

        // 2. Try ActivityManager running processes
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
                if (procs != null) {
                    for (ActivityManager.RunningAppProcessInfo p : procs) {
                        if (p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                                && p.pkgList != null && p.pkgList.length > 0) {
                            String pkg = p.pkgList[0];
                            if (!pkg.equals(c.getPackageName()) && !pkg.equals("com.amazon.tv.launcher")) {
                                return pkg;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }

        // 3. Fallback to HomeRedirectService.getLastForeground()
        HomeRedirectService s = HomeRedirectService.getInstance();
        if (s != null) {
            String p = s.getLastForeground();
            if (p != null && !p.isEmpty()) return p;
        }

        return c.getPackageName();
    }

    private static int resolveUid(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return -1;
        try {
            return c.getPackageManager().getPackageUid(pkg, 0);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static String formatAppDisplay(Context c, String pkg) {
        if (pkg == null || pkg.isEmpty()) return "-";
        if (pkg.equals(c.getPackageName())) return "Homestead (主畫面)";
        if (pkg.contains("HdmiActivity") || pkg.contains(".hdmi") || pkg.contains("livetv")) {
            return "電視訊號輸入 (HDMI)";
        }
        if ("com.netflix.ninja".equals(pkg)) return "Netflix";
        if ("com.amazon.firetv.youtube".equals(pkg) || "com.google.android.youtube.tv".equals(pkg)) return "YouTube";
        if ("com.amazon.amazonvideo.livingroom".equals(pkg)) return "Prime Video";
        if ("com.disney.disneyplus".equals(pkg)) return "Disney+";
        if ("org.xbmc.kodi".equals(pkg)) return "Kodi";
        if ("com.plexapp.android".equals(pkg)) return "Plex";
        if ("com.apple.atve.amazon.appletv".equals(pkg)) return "Apple TV";

        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null && !TextUtils.isEmpty(label)) {
                return label.toString();
            }
        } catch (Throwable ignored) { }
        return pkg;
    }

    private static void queryAdbMediaMetrics() {
        if (adbQueryInProgress) return;
        if (!AdbBridge.isReady(appContext) && !AdbBridge.canRun(appContext)) return;

        adbQueryInProgress = true;
        // Deep query: true active window focus, media session, Codec2/MediaCodec decoders, and AudioTrack
        final String cmd = "dumpsys window | grep -m 1 'mCurrentFocus'; " +
                "dumpsys activity top 2>/dev/null | grep -m 1 'ACTIVITY '; " +
                "dumpsys media_session 2>/dev/null | grep -E 'package=|state=PlaybackState' | head -n 8; " +
                "dumpsys media.codec 2>/dev/null | grep -E 'mime|codec|video/|audio/|width|height' | head -n 25; " +
                "dumpsys media.metrics 2>/dev/null | tail -n 25; " +
                "dumpsys audio 2>/dev/null | grep -E 'state=ACTIVE|format|channel|sample|eac3|atmos' | head -n 15";

        AdbBridge.run(cmd, new AdbBridge.Callback() {
            @Override
            public void onDone(boolean ok, String out) {
                adbQueryInProgress = false;
                if (!ok || out == null || out.isEmpty() || !isShowing()) return;

                parseAdbOutput(out);
            }
        });
    }

    private static void parseAdbOutput(String out) {
        String lower = out.toLowerCase(Locale.US);

        // 1. Parse true foreground window package
        String topPkg = null;
        Matcher focusMatcher = FOCUS_PKG_PATTERN.matcher(out);
        if (focusMatcher.find()) {
            topPkg = focusMatcher.group(1);
        }
        if (topPkg == null || topPkg.isEmpty()) {
            Matcher actMatcher = ACTIVITY_PKG_PATTERN.matcher(out);
            if (actMatcher.find()) {
                topPkg = actMatcher.group(1);
            }
        }

        if (topPkg != null && !topPkg.isEmpty() && !topPkg.equals("com.amazon.tv.launcher")) {
            if (!topPkg.equals(detectedForegroundPkg)) {
                detectedForegroundPkg = topPkg;
                detectedForegroundUid = resolveUid(appContext, topPkg);
                lastRxBytes = readRxBytes();
                lastRxTime = SystemClock.elapsedRealtime();
            }
            if (tvAppVal != null) {
                tvAppVal.setText(formatAppDisplay(appContext, topPkg));
            }
        }

        // 2. Parse Video Codec & Stream Format
        if (lower.contains("dolby-vision") || lower.contains("dvhe") || lower.contains("dvh1")) {
            cachedVideoCodec = "Dolby Vision (HEVC 10b)";
        } else if (lower.contains("video/hevc") || lower.contains("hevc") || lower.contains("h265")) {
            cachedVideoCodec = "HEVC / H.265 (4K硬解)";
        } else if (lower.contains("video/av01") || lower.contains("av01") || lower.contains("av1")) {
            cachedVideoCodec = "AV1 (次世代高效編碼)";
        } else if (lower.contains("video/x-vnd.on2.vp9") || lower.contains("vp9")) {
            cachedVideoCodec = "VP9 (YouTube 4K)";
        } else if (lower.contains("video/avc") || lower.contains("avc") || lower.contains("h264")) {
            cachedVideoCodec = "AVC / H.264 (硬解)";
        }

        // 3. Parse Audio Codec
        String dev = getAudioOutputSummary(appContext);
        if (lower.contains("eac3-joc") || lower.contains("e_ac3_joc") || lower.contains("atmos")) {
            cachedAudioCodec = "Dolby Atmos / " + dev;
        } else if (lower.contains("audio/eac3") || lower.contains("e_ac3") || lower.contains("ddp")) {
            cachedAudioCodec = "E-AC3 (5.1) / " + dev;
        } else if (lower.contains("audio/ac3") || lower.contains("ac3")) {
            cachedAudioCodec = "Dolby Digital (5.1) / " + dev;
        } else if (lower.contains("dts-hd") || lower.contains("dts")) {
            cachedAudioCodec = "DTS 環繞音效 / " + dev;
        } else if (lower.contains("audio/mp4a-latm") || lower.contains("audio/aac") || lower.contains("aac")) {
            cachedAudioCodec = "AAC 多聲道 / " + dev;
        } else if (lower.contains("audio/raw") || lower.contains("pcm")) {
            cachedAudioCodec = "PCM 無損 / " + dev;
        }

        if (cachedVideoCodec != null && tvVideoVal != null) {
            tvVideoVal.setText(cachedVideoCodec);
        }
        if (cachedAudioCodec != null && tvAudioVal != null) {
            tvAudioVal.setText(cachedAudioCodec);
        }
    }

    private static View createHudView(Context c) {
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);

        // Ultra-compact padding: 10dp horizontal, 8dp vertical
        int padH = dp(c, 10);
        int padV = dp(c, 8);
        root.setPadding(padH, padV, padH, padV);

        // Semi-transparent deep midnight slate card (~84% opacity)
        GradientDrawable card = new GradientDrawable();
        card.setColor(Color.argb(215, 12, 16, 24));
        card.setCornerRadius(dp(c, 12));
        card.setStroke(dp(c, 1), Color.argb(55, 255, 255, 255));
        if (Build.VERSION.SDK_INT >= 16) {
            root.setBackground(card);
        } else {
            root.setBackgroundDrawable(card);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            root.setElevation(dp(c, 8));
        }

        // Compact width: 240dp (down from 370dp)
        root.setLayoutParams(new ViewGroup.LayoutParams(dp(c, 240), ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Header Row
        LinearLayout header = new LinearLayout(c);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView dot = new TextView(c);
        dot.setText("● ");
        dot.setTextColor(0xFF10B981); // Emerald green live dot
        dot.setTextSize(11);
        header.addView(dot);

        TextView title = new TextView(c);
        title.setText(c.getString(R.string.info_hud_title));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(11);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title);

        View spacer = new View(c);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 1, 1f);
        header.addView(spacer, spLp);

        TextView closeHint = new TextView(c);
        closeHint.setText(c.getString(R.string.info_hud_hint));
        closeHint.setTextColor(0xFF94A3B8);
        closeHint.setTextSize(9);
        header.addView(closeHint);

        root.addView(header);

        // Subtle divider
        View div = new View(c);
        div.setBackgroundColor(0xFF2D3748);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1));
        divLp.topMargin = dp(c, 4);
        divLp.bottomMargin = dp(c, 4);
        root.addView(div, divLp);

        // 2. Data Rows
        tvAppVal = addRow(c, root, c.getString(R.string.info_hud_app), 0xFF38BDF8);       // Sky blue
        tvResVal = addRow(c, root, c.getString(R.string.info_hud_res), 0xFFFFFFFF);       // Crisp white
        tvFpsVal = addRow(c, root, c.getString(R.string.info_hud_fps), 0xFFFACC15);       // Amber gold
        tvBitrateVal = addRow(c, root, c.getString(R.string.info_hud_bitrate), 0xFF34D399); // Emerald green
        tvHdrVal = addRow(c, root, c.getString(R.string.info_hud_hdr), 0xFFF472B6);       // Pink / Dolby rose
        tvVideoVal = addRow(c, root, c.getString(R.string.info_hud_video), 0xFFA78BFA);   // Purple
        tvAudioVal = addRow(c, root, c.getString(R.string.info_hud_audio), 0xFF60A5FA);   // Royal blue

        return root;
    }

    private static TextView addRow(Context c, LinearLayout parent, String label, int valueColor) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = dp(c, 2.5f);
        row.setLayoutParams(rLp);

        TextView lbl = new TextView(c);
        lbl.setText(label);
        lbl.setTextColor(0xFF94A3B8);
        lbl.setTextSize(10);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                dp(c, 56), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(lbl, lLp);

        TextView val = new TextView(c);
        val.setText("-");
        val.setTextColor(valueColor);
        val.setTextSize(11);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setSingleLine(true);
        val.setEllipsize(TextUtils.TruncateAt.END);
        val.setGravity(Gravity.END);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(val, vLp);

        parent.addView(row);
        return val;
    }

    private static WindowManager getWindowManager() {
        HomeRedirectService s = HomeRedirectService.getInstance();
        if (s != null) {
            return (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        }
        MainActivity act = HomeRedirectService.getLiveActivity();
        if (act != null && !act.isFinishing()) {
            return (WindowManager) act.getSystemService(Context.WINDOW_SERVICE);
        }
        if (appContext != null) {
            return (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        }
        return null;
    }

    private static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
