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

import java.util.List;

/**
 * Floating side-sheet activity for selecting and switching TV video inputs (HDMI 1-4, Live TV),
 * styled identically to native Fire TV panels.
 */
public class InputChooserActivity extends Activity {

    public static void open(Context c) {
        Intent it = new Intent(c, InputChooserActivity.class);
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

        // 1. Live TV if available on this TV
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
        }

        // 2. Hardware Passthrough Inputs (HDMI 1, HDMI 2, HDMI 3, HDMI 4, Composite, etc.)
        try {
            TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (tim != null) {
                List<TvInputInfo> list = tim.getTvInputList();
                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        final TvInputInfo info = list.get(i);
                        if (info == null || !info.isPassthroughInput()) continue;

                        CharSequence lb = null;
                        try { lb = info.loadLabel(this); } catch (Throwable ignored) { }
                        final String label = (lb == null || lb.length() == 0)
                                ? getString(R.string.home_input) : lb.toString();

                        boolean connected = false;
                        if (Build.VERSION.SDK_INT >= 24) {
                            try {
                                connected = (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED);
                            } catch (Throwable ignored) { }
                        }

                        String status = connected ? getString(R.string.input_connected) : getString(R.string.input_disconnected);
                        final String inputId = info.getId();

                        Panel.Row r = p.addRow(label, status, new Runnable() {
                            @Override
                            public void run() {
                                Intent it = new Intent(InputChooserActivity.this, HdmiActivity.class);
                                it.putExtra(HdmiActivity.EXTRA_INPUT_ID, inputId);
                                it.putExtra(HdmiActivity.EXTRA_LABEL, label);
                                startActivity(it);
                                finish();
                            }
                        });
                        r.setValueColor(connected ? 0xFFA7D28F : 0xFF6A7F94);
                    }
                }
            }
        } catch (Throwable ignored) { }

        // 3. Option to invoke Native TV Menu
        p.addRow(getString(R.string.input_open_native), "", new Runnable() {
            @Override
            public void run() {
                finish();
                RemoteActions.triggerNativeInputs(InputChooserActivity.this);
            }
        });

        // 4. Cancel / Close row
        p.addRow(getString(R.string.set_close), "", new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });

        setContentView(p.root);
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
