package me.iacn.biliroaming;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
public class ColorChooseDialog extends AlertDialog.Builder {

    private View sampleView;
    private EditText etColor;

    private SeekBar sbColorR;
    private SeekBar sbColorG;
    private SeekBar sbColorB;

    private TextView tvColorR;
    private TextView tvColorG;
    private TextView tvColorB;

    public ColorChooseDialog(Context context, int defColor) {
        super(context);
        View view = getView(context);

        if (view == null) return;

        setView(view);
        findView(view);
        setEditTextListener();
        setSeekBarListener();

        updateValue(defColor);
        etColor.setText(String.format("%06X", 0xFFFFFF & defColor));

        setTitle("自选颜色");
        setNegativeButton("取消", null);
    }

    public int getColor() {
        return Color.rgb(sbColorR.getProgress(), sbColorG.getProgress(), sbColorB.getProgress());
    }

    private View getView(Context context) {
        try {
            Context moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
            return View.inflate(moduleContext, R.layout.dialog_color_choose, null);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void findView(View view) {
        sampleView = view.findViewById(R.id.view_sample);
        etColor = view.findViewById(R.id.et_color);

        sbColorR = view.findViewById(R.id.sb_colorR);
        sbColorG = view.findViewById(R.id.sb_colorG);
        sbColorB = view.findViewById(R.id.sb_colorB);

        tvColorR = view.findViewById(R.id.tv_colorR);
        tvColorG = view.findViewById(R.id.tv_colorG);
        tvColorB = view.findViewById(R.id.tv_colorB);
    }

    private void setEditTextListener() {
        etColor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateValue(handleUnknownColor(s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setSeekBarListener() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int color = Color.rgb(
                            sbColorR.getProgress(),
                            sbColorG.getProgress(),
                            sbColorB.getProgress());

                    etColor.setText(String.format("%06X", 0xFFFFFF & color));
                }

                tvColorR.setText(String.valueOf(sbColorR.getProgress()));
                tvColorG.setText(String.valueOf(sbColorG.getProgress()));
                tvColorB.setText(String.valueOf(sbColorB.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };

        sbColorR.setOnSeekBarChangeListener(listener);
        sbColorG.setOnSeekBarChangeListener(listener);
        sbColorB.setOnSeekBarChangeListener(listener);
    }

    private void updateValue(int color) {
        sampleView.setBackgroundColor(color);

        int progressR = Color.red(color);
        int progressG = Color.green(color);
        int progressB = Color.blue(color);

        sbColorR.setProgress(progressR);
        sbColorG.setProgress(progressG);
        sbColorB.setProgress(progressB);

        tvColorR.setText(String.valueOf(progressR));
        tvColorG.setText(String.valueOf(progressG));
        tvColorB.setText(String.valueOf(progressB));
    }

    private int handleUnknownColor(String color) {
        try {
            return Color.parseColor("#" + color);
        } catch (IllegalArgumentException e) {
            return Color.BLACK;
        }
    }
}