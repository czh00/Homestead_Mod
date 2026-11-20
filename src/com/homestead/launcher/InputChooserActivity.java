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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Floating side-sheet activity for selecting and switching TV video inputs (HDMI 1-4, Live TV),
 * styled identically to native Fire TV panels.
 * Inputs are sorted with connected sources prioritized at the top, and ordered by HDMI 1, 2, 3, 4.
 */
public class InputChooserActivity extends Activity {

    public static class InputItem {
        public final TvInputInfo info;
        public final String inputId;
        public final String label;
        public final boolean connected;
        public final int state;
        public final int portNumber;

        public InputItem(TvInputInfo info, String inputId, String label, int state, int portNumber) {
            this.info = info;
            this.inputId = inputId;
            this.label = label;
            this.state = state;
            this.connected = (state == TvInputManager.INPUT_STATE_CONNECTED
                    || state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY);
            this.portNumber = portNumber;
        }
    }

    public static void open(Context c) {
        Intent it = new Intent(c, InputChooserActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(it);
    }

    public static int extractPortNumber(String label, String inputId) {
        String l = (label == null) ? "" : label.toUpperCase(Locale.US);
        String id = (inputId == null) ? "" : inputId.toUpperCase(Locale.US);

        for (int n = 1; n <= 8; n++) {
            if (l.contains("HDMI " + n) || l.contains("HDMI" + n) || l.contains("PORT " + n) || l.contains("PORT" + n)) {
                return n;
            }
        }

        for (int n = 1; n <= 8; n++) {
            if (id.endsWith("HW" + n) || id.contains("/HW" + n + "/") || id.contains("HDMI" + n) || id.contains("HDMI_" + n)) {
                return n;
            }
        }

        if (l.contains("AV") || l.contains("COMPOSITE") || id.contains("COMPOSITE") || id.contains("AV")) return 20;
        if (l.contains("COMPONENT") || id.contains("COMPONENT")) return 21;
        if (l.contains("VGA") || id.contains("VGA")) return 22;

        return 50;
    }

    public static List<InputItem> getSortedInputs(Context c) {
        List<InputItem> items = new ArrayList<InputItem>();
        try {
            TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> list = tim.getTvInputList();
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        TvInputInfo info = list.get(i);
                        if (info == null || !info.isPassthroughInput()) continue;

                        CharSequence lb = null;
                        try { lb = info.loadLabel(c); } catch (Throwable ignored) { }
                        String rawLabel = (lb == null || lb.length() == 0) ? "" : lb.toString();
                        int port = extractPortNumber(rawLabel, info.getId());

                        String label = rawLabel;
                        if (label.isEmpty() || label.equalsIgnoreCase("HDMI") || label.equals(c.getString(R.string.home_input))) {
                            if (port >= 1 && port <= 8) {
                                label = "HDMI " + port;
                            } else {
                                label = c.getString(R.string.home_input);
                            }
                        }

                        String custom = Prefs.customInputLabel(c, info.getId());
                        if (custom != null && !custom.isEmpty() && !custom.equalsIgnoreCase(label)) {
                            label = custom + " (" + label + ")";
                        }

                        int state = TvInputManager.INPUT_STATE_DISCONNECTED;
                        if (Build.VERSION.SDK_INT >= 24) {
                            try {
                                state = tim.getInputState(info.getId());
                            } catch (Throwable ignored) { }
                        }

                        items.add(new InputItem(info, info.getId(), label, state, port));
                    }
                }
            }
        } catch (Throwable ignored) { }

        Collections.sort(items, new Comparator<InputItem>() {
            @Override
            public int compare(InputItem a, InputItem b) {
                // 1. Connected first
                if (a.connected != b.connected) {
                    return a.connected ? -1 : 1;
                }
                // 2. By port number (HDMI 1 before HDMI 2 before HDMI 3...)
                if (a.portNumber != b.portNumber) {
                    return Integer.compare(a.portNumber, b.portNumber);
                }
                // 3. By label
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        return items;
    }

    public static void switchToInput(Context c, String inputId, String label) {
        int type = HdmiActivity.kaminoInputType(c, inputId);
        if (type >= 0) {
            try {
                Intent kaminoIntent = new Intent().setComponent(new ComponentName(
                        "com.amazon.tv.inputpreference.service",
                        "com.amazon.tv.inputpreference.service.KaminoInputSwitchService"));
                kaminoIntent.putExtra("inputType", type);
                if (c.startService(kaminoIntent) != null) {
                    return;
                }
            } catch (Throwable ignored) { }
        }

        Intent it = new Intent(c, HdmiActivity.class);
        it.putExtra(HdmiActivity.EXTRA_INPUT_ID, inputId);
        it.putExtra(HdmiActivity.EXTRA_LABEL, label);
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(it);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.apply(this);

        final Panel p = new Panel(this, getString(R.string.input_chooser_title),
                getString(R.string.input_chooser_help), Panel.SHEET);

        // Click outside on the dimmed area dismisses the sheet
        p.root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        Panel.Row firstRow = null;
        List<InputItem> items = getSortedInputs(this);

        boolean hasConnected = false;
        boolean hasDisconnected = false;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).connected) hasConnected = true;
            else hasDisconnected = true;
        }

        // Section 1: Connected Inputs (Priority on top, sorted by port number)
        if (hasConnected) {
            p.addHeader(getString(R.string.input_sec_connected));
            for (int i = 0; i < items.size(); i++) {
                final InputItem it = items.get(i);
                if (!it.connected) continue;

                String status = (it.state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY)
                        ? getString(R.string.input_standby)
                        : getString(R.string.input_connected);

                Panel.Row r = p.addRow(it.label, status, new Runnable() {
                    @Override
                    public void run() {
                        switchToInput(InputChooserActivity.this, it.inputId, it.label);
                        finish();
                    }
                });
                r.setValueColor(0xFFA7D28F);
                if (firstRow == null) firstRow = r;
            }
        }

        // Section 2: Other Inputs (Disconnected / No signal, sorted by port number)
        if (hasDisconnected || AppModel.liveTvAvailable(this)) {
            if (hasConnected) {
                p.addHeader(getString(R.string.input_sec_other));
            }
            for (int i = 0; i < items.size(); i++) {
                final InputItem it = items.get(i);
                if (it.connected) continue;

                Panel.Row r = p.addRow(it.label, getString(R.string.input_disconnected), new Runnable() {
                    @Override
                    public void run() {
                        switchToInput(InputChooserActivity.this, it.inputId, it.label);
                        finish();
                    }
                });
                r.setValueColor(0xFF6A7F94);
                if (firstRow == null) firstRow = r;
            }

            // Live TV (Antenna / Cable tuner)
            if (AppModel.liveTvAvailable(this)) {
                Panel.Row r = p.addRow(getString(R.string.home_livetv), "", new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("livetv://showLiveTVButtonContent/"));
                            i.setComponent(new ComponentName(AppModel.LIVETV_PKG, AppModel.LIVETV_SVC));
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                            else startService(i);
                        } catch (Throwable t) {
                            Toast.makeText(InputChooserActivity.this, R.string.home_livetv_failed, Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    }
                });
                r.setHelp(getString(R.string.home_sec_inputs));
                if (firstRow == null) firstRow = r;
            }
        }

        // Close row
        p.addRow(getString(R.string.set_close), "", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });

        setContentView(p.root);

        // Autofocus the first input row
        if (firstRow != null) {
            final View target = firstRow.cell;
            p.root.post(new Runnable() {
                @Override
                public void run() {
                    target.requestFocus();
                }
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
