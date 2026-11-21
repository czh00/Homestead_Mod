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
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class RenameDialogActivity extends Activity {

    public static final String EXTRA_INPUT_ID = "input_id";
    public static final String EXTRA_ORIG_NAME = "orig_name";
    public static final String EXTRA_CURRENT_NAME = "current_name";

    private String inputId;
    private String origName;
    private String currentName;
    private EditText edit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Lang.apply(this);

        inputId = getIntent().getStringExtra(EXTRA_INPUT_ID);
        origName = getIntent().getStringExtra(EXTRA_ORIG_NAME);
        currentName = getIntent().getStringExtra(EXTRA_CURRENT_NAME);

        if (origName == null || origName.isEmpty()) origName = "HDMI";
        if (currentName == null || currentName.isEmpty()) currentName = origName;
        if (inputId == null) {
            finish();
            return;
        }

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xB0000000);
        root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(28);
        card.setPadding(pad, pad, pad, pad);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1E2630);
        cardBg.setCornerRadius(dp(12));
        cardBg.setStroke(dp(1), 0x40FFFFFF);
        card.setBackground(cardBg);
        card.setClickable(true);

        TextView title = new TextView(this);
        title.setText(R.string.rename_input_title);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(Color.WHITE);
        card.addView(title);

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.rename_port_info, origName));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setTextColor(0xFF8E9BAE);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(18);
        card.addView(sub, subLp);

        edit = new EditText(this);
        edit.setText(currentName);
        edit.setHint(origName);
        edit.setSingleLine(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT);
        edit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0xFF7C8FA4);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        edit.setPadding(dp(16), dp(12), dp(16), dp(12));

        final GradientDrawable editBg = new GradientDrawable();
        editBg.setCornerRadius(dp(8));
        editBg.setColor(0x26FFFFFF);
        editBg.setStroke(dp(1), 0x50FFFFFF);
        edit.setBackground(editBg);

        edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                editBg.setStroke(dp(2), hasFocus ? 0xFFFFFFFF : 0x50FFFFFF);
                editBg.setColor(hasFocus ? 0x40FFFFFF : 0x26FFFFFF);
            }
        });

        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    saveAndFinish();
                    return true;
                }
                return false;
            }
        });

        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        card.addView(edit, editLp);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnRowLp.topMargin = dp(24);

        Button btnCancel = makeButton(getString(R.string.ftv_cancel), false);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });

        Button btnSave = makeButton(getString(R.string.rename_save), true);
        btnSave.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                saveAndFinish();
            }
        });

        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cancelLp.rightMargin = dp(12);
        btnRow.addView(btnCancel, cancelLp);
        btnRow.addView(btnSave);

        card.addView(btnRow, btnRowLp);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                dp(480), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.CENTER;
        root.addView(card, cardLp);

        setContentView(root);

        if (edit.getText() != null) {
            edit.setSelection(0, edit.getText().length());
        }
        edit.postDelayed(new Runnable() {
            public void run() {
                if (isFinishing()) return;
                edit.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 150);
    }

    private Button makeButton(String text, final boolean isPrimary) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(dp(20), dp(10), dp(20), dp(10));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);

        final GradientDrawable bgNormal = new GradientDrawable();
        bgNormal.setCornerRadius(dp(8));
        bgNormal.setColor(isPrimary ? 0xFF0E5A63 : 0x20FFFFFF);
        bgNormal.setStroke(dp(1), isPrimary ? 0xFF147D8A : 0x30FFFFFF);

        final GradientDrawable bgFocused = new GradientDrawable();
        bgFocused.setCornerRadius(dp(8));
        bgFocused.setColor(isPrimary ? 0xFF1B808D : 0x45FFFFFF);
        bgFocused.setStroke(dp(2), 0xFFFFFFFF);

        b.setBackground(bgNormal);
        b.setTextColor(Color.WHITE);

        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(hasFocus ? bgFocused : bgNormal);
            }
        });

        return b;
    }

    private void saveAndFinish() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && edit != null) {
            imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        }
        String val = (edit != null && edit.getText() != null) ? edit.getText().toString().trim() : "";
        if (val.isEmpty()) val = origName;
        boolean resetting = val.equalsIgnoreCase(origName);
        HomeGrid.onInputRenamed(this, inputId, resetting ? null : val);
        try {
            int msgId = resetting ? R.string.rename_reset_toast : R.string.rename_saved_toast;
            Toast.makeText(this, getString(msgId, val), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) { }
        finish();
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }
}
