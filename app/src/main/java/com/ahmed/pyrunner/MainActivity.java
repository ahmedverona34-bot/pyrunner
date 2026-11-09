package com.ahmed.pyrunner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_CODE =
            "import sys\nprint(\"Hello from Python\", sys.version.split()[0])\n\nfor i in range(3):\n    print(i)\n";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText editor;
    private TextView output;
    private ScrollView outputScroll;
    private Button runButton;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("pyrunner", MODE_PRIVATE);
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(getApplicationContext()));
        }

        int bg = Color.rgb(18, 18, 22);
        int panel = Color.rgb(28, 28, 34);
        int pad = dp(10);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setFitsSystemWindows(true);

        // toolbar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(pad, dp(4), pad, dp(4));
        TextView title = new TextView(this);
        title.setText("PyRunner");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setAllCaps(false);
        bar.addView(resetButton);
        Button clearButton = new Button(this);
        clearButton.setText("Clear");
        clearButton.setAllCaps(false);
        bar.addView(clearButton);
        runButton = new Button(this);
        runButton.setText("▶ Run");
        runButton.setAllCaps(false);
        bar.addView(runButton);
        root.addView(bar);

        // editor
        editor = new EditText(this);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextSize(14);
        editor.setTextColor(Color.rgb(230, 230, 235));
        editor.setHintTextColor(Color.GRAY);
        editor.setHint("# Python code");
        editor.setBackgroundColor(panel);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(pad, pad, pad, pad);
        editor.setTextDirection(View.TEXT_DIRECTION_LTR);
        editor.setHorizontallyScrolling(true);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setText(prefs.getString("code", DEFAULT_CODE));
        root.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // quick keys
        HorizontalScrollView keysScroll = new HorizontalScrollView(this);
        LinearLayout keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"TAB", ":", "(", ")", "[", "]", "{", "}", "\"", "'", "=", "_", "#", ".", ",", "<", ">", "+", "-", "*", "/"};
        for (final String label : labels) {
            Button key = new Button(this);
            key.setText(label);
            key.setAllCaps(false);
            key.setMinWidth(0);
            key.setMinimumWidth(0);
            key.setTypeface(Typeface.MONOSPACE);
            key.setOnClickListener(v -> insert(label.equals("TAB") ? "    " : label));
            keys.addView(key, new LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        keysScroll.addView(keys);
        root.addView(keysScroll);

        // output
        outputScroll = new ScrollView(this);
        outputScroll.setBackgroundColor(Color.BLACK);
        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(13);
        output.setTextColor(Color.rgb(200, 255, 200));
        output.setPadding(pad, pad, pad, pad);
        output.setTextIsSelectable(true);
        output.setTextDirection(View.TEXT_DIRECTION_LTR);
        outputScroll.addView(output);
        root.addView(outputScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);

        runButton.setOnClickListener(v -> runCode());
        clearButton.setOnClickListener(v -> output.setText(""));
        resetButton.setOnClickListener(v -> worker.execute(() -> {
            Python.getInstance().getModule("runner").callAttr("reset");
            append("[session reset]\n", false);
        }));
    }

    private void insert(String text) {
        int start = Math.max(editor.getSelectionStart(), 0);
        int end = Math.max(editor.getSelectionEnd(), 0);
        editor.getText().replace(Math.min(start, end), Math.max(start, end), text);
    }

    private void runCode() {
        final String code = editor.getText().toString();
        prefs.edit().putString("code", code).apply();
        runButton.setEnabled(false);
        final Bridge bridge = new Bridge(new Bridge.Listener() {
            @Override
            public void output(String text, boolean error) {
                append(text, error);
            }

            @Override
            public String input(String prompt) {
                return askInput(prompt);
            }
        });
        worker.execute(() -> {
            try {
                Python.getInstance().getModule("runner").callAttr("run", code, bridge);
            } catch (Throwable t) {
                append(t.toString() + "\n", true);
            } finally {
                ui.post(() -> runButton.setEnabled(true));
            }
        });
    }

    private void append(final String text, final boolean error) {
        ui.post(() -> {
            if (error) {
                SpannableString s = new SpannableString(text);
                s.setSpan(new ForegroundColorSpan(Color.rgb(255, 110, 110)), 0, s.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                output.append(s);
            } else {
                output.append(text);
            }
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private String askInput(final String prompt) {
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] result = {""};
        ui.post(() -> {
            final EditText field = new EditText(this);
            field.setSingleLine(true);
            new AlertDialog.Builder(this)
                    .setTitle(prompt.isEmpty() ? "input()" : prompt)
                    .setView(field)
                    .setCancelable(false)
                    .setPositiveButton("OK", (d, w) -> {
                        result[0] = field.getText().toString();
                        latch.countDown();
                    })
                    .show();
        });
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        append(prompt + result[0] + "\n", false);
        return result[0];
    }

    @Override
    protected void onPause() {
        super.onPause();
        prefs.edit().putString("code", editor.getText().toString()).apply();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
