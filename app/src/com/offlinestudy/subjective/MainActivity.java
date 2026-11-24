package com.offlinestudy.subjective;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int INK = 0xff262626;
    private static final int MUTED = 0xff666666;
    private static final int PRIMARY = 0xff006fba;
    private static final int PRIMARY_FILL = 0xff0079cb;
    private static final int DISABLED_TEXT = 0xffa6a6a6;
    private static final int BORDER = 0xffe2e2e2;
    private static final int DIVIDER = 0xffeeeeee;
    private static final int WHITE = Color.WHITE;
    private static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    private static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;
    private static final String DEFAULT_WATERMARK = "本地练习";
    private static final int DEFAULT_MINUTES = 60;
    private static final int MAX_MINUTES = 240;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final String EXAMPLE = "解释 <p>{{ msg }}</p> 的作用。";

    private final ArrayList<Question> questions = new ArrayList<>();
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = this::updateClock;
    private SharedPreferences preferences;
    private String watermark = DEFAULT_WATERMARK;
    private int currentIndex;
    private boolean bindingAnswer;
    private boolean addingQuestion;
    private int durationMinutes;
    private long countdownEnd;

    private WatermarkLayout workspace;
    private ScrollView pageScroll;
    private TextView answerStatus;
    private CountdownButton countdown;
    private LinearLayout cardControl;
    private TextView progress;
    private TextView questionNumber;
    private TextView prompt;
    private TextView lastQuestionHint;
    private EditText answer;
    private Button previous;
    private Button next;
    private AlertDialog editor;
    private EditText promptDraft;
    private EditText watermarkDraft;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            getWindow().setNavigationBarColor(INK);
        }
        preferences = getSharedPreferences("practice", MODE_PRIVATE);
        durationMinutes = preferences.getInt("countdown_minutes", DEFAULT_MINUTES);
        countdownEnd = preferences.getLong("countdown_end", 0);
        if (countdownEnd == 0) {
            startCountdown(durationMinutes);
        }
        restorePaper();
        setContentView(createPage());
        showQuestion();
        if (state != null && state.containsKey("draft_prompt")) {
            openEditor(state.getBoolean("draft_adding"), state.getString("draft_prompt"),
                    state.getString("draft_watermark"));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        updateClock();
    }

    @Override
    protected void onStop() {
        clockHandler.removeCallbacks(clockTick);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (editor != null && editor.isShowing()) {
            state.putBoolean("draft_adding", addingQuestion);
            state.putString("draft_prompt", promptDraft.getText().toString());
            state.putString("draft_watermark", watermarkDraft.getText().toString());
        }
    }

    private View createPage() {
        LinearLayout page = column();
        page.setFocusableInTouchMode(true);
        page.setBackgroundColor(WHITE);
        addHeader(page);

        workspace = new WatermarkLayout(this);
        workspace.setOrientation(LinearLayout.VERTICAL);
        workspace.setBackgroundColor(WHITE);
        page.addView(workspace, new LinearLayout.LayoutParams(MATCH, 0, 1));

        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        workspace.addView(pageScroll, new LinearLayout.LayoutParams(MATCH, MATCH));

        LinearLayout content = column();
        content.setPadding(dp(15), dp(14), dp(15), dp(20));
        pageScroll.addView(content);

        TextView promptLabel = label("简答题", 14, INK, true);
        markHeading(promptLabel);
        append(content, promptLabel, 0);

        LinearLayout promptRow = row();
        promptRow.setGravity(Gravity.TOP);
        promptRow.setBaselineAligned(false);
        questionNumber = label("", 16, INK, false);
        LinearLayout.LayoutParams numberPosition = new LinearLayout.LayoutParams(WRAP, WRAP);
        numberPosition.setMarginEnd(dp(5));
        promptRow.addView(questionNumber, numberPosition);
        prompt = label("", 16, INK, false);
        prompt.setTextIsSelectable(true);
        prompt.setBreakStrategy(0);
        prompt.setLineSpacing(dp(2), 1);
        promptRow.addView(prompt, new LinearLayout.LayoutParams(0, WRAP, 1));
        append(content, promptRow, 10);

        LinearLayout answerBox = column();
        answerBox.setBackground(surface(0xbaffffff, BORDER, 7));
        answer = textField("请输入答案", 5, 12);
        answer.setMinHeight(dp(226));
        answer.setTextSize(15);
        answer.setPadding(dp(12), dp(10), dp(12), dp(10));
        answer.setBackgroundColor(Color.TRANSPARENT);
        answer.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable text) {
                if (!bindingAnswer) {
                    question().answer = text.toString();
                    savePaper();
                    updateAnswerStatus();
                }
            }
        });
        append(answerBox, answer, 0);
        answerStatus = label("", 12, MUTED, false);
        answerStatus.setPadding(dp(12), dp(6), dp(12), dp(11));
        append(answerBox, answerStatus, 0);
        append(content, answerBox, 23);
        lastQuestionHint = label("已到最后一题", 12, MUTED, false);
        lastQuestionHint.setGravity(Gravity.CENTER);
        append(content, lastQuestionHint, 10);

        addFooter(page);
        page.requestFocus();
        return page;
    }

    private void addHeader(LinearLayout page) {
        FrameLayout header = new FrameLayout(this);
        header.setBackgroundColor(WHITE);
        TextView title = label("主观题练习", 17, INK, false);
        markHeading(title);
        title.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams titlePosition = new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER);
        titlePosition.setMargins(dp(88), dp(11), dp(88), dp(11));
        header.addView(title, titlePosition);
        Button back = action("‹", INK, Color.TRANSPARENT);
        back.setTextSize(27);
        back.setTypeface(Typeface.DEFAULT);
        back.setContentDescription("返回");
        back.setOnClickListener(view -> finish());
        header.addView(back, new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.START | Gravity.CENTER_VERTICAL));

        Button edit = action("编辑题目", PRIMARY, Color.TRANSPARENT);
        edit.setTextSize(13);
        edit.setOnClickListener(view -> openEditor(false, question().prompt, watermark));
        FrameLayout.LayoutParams editPosition =
                new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.END | Gravity.CENTER_VERTICAL);
        editPosition.setMarginEnd(dp(4));
        header.addView(edit, editPosition);
        append(page, header, 0);
        addDivider(page);

        FrameLayout meta = new FrameLayout(this);
        meta.setBackgroundColor(WHITE);
        countdown = new CountdownButton();
        countdown.setOnClickListener(view -> openTimer());
        meta.addView(countdown, new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.START | Gravity.CENTER_VERTICAL));

        cardControl = column();
        cardControl.setGravity(Gravity.CENTER);
        cardControl.setMinimumHeight(dp(48));
        cardControl.setMinimumWidth(dp(78));
        cardControl.setPadding(dp(8), dp(4), dp(8), dp(4));
        TypedValue selectable = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true);
        cardControl.setBackgroundResource(selectable.resourceId);
        cardControl.setFocusable(true);
        cardControl.setOnClickListener(view -> openQuestionPicker());
        progress = label("", 14, PRIMARY, true);
        cardControl.addView(progress, new LinearLayout.LayoutParams(WRAP, WRAP));
        TextView picker = label("答题卡", 11, PRIMARY, false);
        cardControl.addView(picker, new LinearLayout.LayoutParams(WRAP, WRAP));
        meta.addView(cardControl, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        append(page, meta, 0);
        addDivider(page);
    }

    private void addFooter(LinearLayout page) {
        addDivider(page);
        LinearLayout footer = row();
        footer.setBackgroundColor(WHITE);
        footer.setPadding(dp(14), dp(4), dp(14), dp(4));
        previous = action("上一题", PRIMARY, Color.TRANSPARENT);
        previous.setTypeface(Typeface.DEFAULT);
        previous.setOnClickListener(view -> goTo(currentIndex - 1));
        next = action("下一题", WHITE, PRIMARY_FILL);
        next.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        next.setBackground(new InsetDrawable(actionBackground(PRIMARY_FILL), 0, dp(3), 0, dp(3)));
        next.setPadding(dp(14), dp(5), dp(14), dp(5));
        next.setOnClickListener(view -> {
            if (isLastQuestion()) {
                openQuestionPicker();
            } else {
                goTo(currentIndex + 1);
            }
        });
        footer.addView(previous, new LinearLayout.LayoutParams(0, WRAP, 1));
        LinearLayout.LayoutParams nextPosition = new LinearLayout.LayoutParams(0, WRAP, 2.5f);
        nextPosition.setMarginStart(dp(8));
        footer.addView(next, nextPosition);
        append(page, footer, 0);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        parent.addView(divider, new LinearLayout.LayoutParams(MATCH, dp(1)));
    }

    private Question question() {
        return questions.get(currentIndex);
    }

    private void showQuestion() {
        int number = currentIndex + 1;
        progress.setText(number + "/" + questions.size());
        cardControl.setContentDescription("答题卡，第 " + number + " 题，共 " + questions.size() + " 题");
        questionNumber.setText(number + ".");
        prompt.setText(question().prompt);
        bindingAnswer = true;
        answer.setText(question().answer);
        bindingAnswer = false;
        updateAnswerStatus();
        setAvailable(previous, currentIndex > 0);
        boolean last = isLastQuestion();
        lastQuestionHint.setVisibility(last ? View.VISIBLE : View.GONE);
        next.setText(last ? "下一步" : "下一题");
        workspace.invalidate();
        pageScroll.post(() -> pageScroll.scrollTo(0, 0));
    }

    private boolean isLastQuestion() {
        return currentIndex == questions.size() - 1;
    }

    private void updateAnswerStatus() {
        boolean answered = !question().answer.trim().isEmpty();
        answerStatus.setText(answered ? "已保存" : "未作答");
        answerStatus.setTextColor(answered ? PRIMARY : MUTED);
    }

    private void startCountdown(int minutes) {
        durationMinutes = minutes;
        countdownEnd = System.currentTimeMillis() + minutes * MILLIS_PER_MINUTE;
        preferences.edit().putInt("countdown_minutes", minutes)
                .putLong("countdown_end", countdownEnd).apply();
    }

    private void updateClock() {
        clockHandler.removeCallbacks(clockTick);
        long millis = Math.max(0, countdownEnd - System.currentTimeMillis());
        long seconds = (millis + 999) / 1000;
        String time = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
        countdown.showTime(time, "剩余 " + seconds / 60 + " 分 " + seconds % 60
                + " 秒，设置练习倒计时");
        if (millis > 0) {
            clockHandler.postDelayed(clockTick, (millis - 1) % 1000 + 1);
        }
    }

    private void openTimer() {
        dismissAnswerKeyboard();
        LinearLayout form = column();
        form.setPadding(dp(24), dp(12), dp(24), dp(8));
        TextView fieldLabel = label("练习时长（分钟）", 14, INK, true);
        append(form, fieldLabel, 0);
        EditText minutesInput = textField("1–240", 1, 1);
        minutesInput.setId(View.generateViewId());
        fieldLabel.setLabelFor(minutesInput.getId());
        minutesInput.setSingleLine(true);
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        minutesInput.setText(Integer.toString(durationMinutes));
        minutesInput.setSelectAllOnFocus(true);
        append(form, minutesInput, 8);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("练习倒计时")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("重新计时", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String input = minutesInput.getText().toString().trim();
            int minutes;
            try {
                minutes = Integer.parseInt(input);
            } catch (NumberFormatException invalidInput) {
                minutes = 0;
            }
            if (minutes < 1 || minutes > MAX_MINUTES) {
                minutesInput.setError("请输入 1 到 240 分钟");
                return;
            }
            startCountdown(minutes);
            updateClock();
            dialog.dismiss();
        });
    }

    private void goTo(int index) {
        if (index < 0 || index >= questions.size() || index == currentIndex) {
            return;
        }
        dismissAnswerKeyboard();
        currentIndex = index;
        savePaper();
        showQuestion();
    }

    private void dismissAnswerKeyboard() {
        if (answer.hasFocus()) {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(answer.getWindowToken(), 0);
            answer.clearFocus();
        }
    }

    private void openQuestionPicker() {
        dismissAnswerKeyboard();
        String[] items = new String[questions.size()];
        for (int i = 0; i < items.length; i++) {
            String status = questions.get(i).answer.trim().isEmpty() ? "未作答" : "已作答";
            items[i] = "第 " + (i + 1) + " 题 · " + status;
        }
        new AlertDialog.Builder(this)
                .setTitle("答题卡")
                .setSingleChoiceItems(items, currentIndex, (dialog, index) -> {
                    dialog.dismiss();
                    goTo(index);
                })
                .setNeutralButton("新增题目", (dialog, which) -> openEditor(true, "", watermark))
                .setNegativeButton("关闭", null)
                .show();
    }

    private void openEditor(boolean adding, String initialPrompt, String initialWatermark) {
        dismissAnswerKeyboard();
        addingQuestion = adding;

        LinearLayout form = column();
        form.setPadding(dp(24), dp(12), dp(24), dp(8));
        TextView promptLabel = label("题干", 14, INK, true);
        append(form, promptLabel, 0);
        promptDraft = textField("输入题干，支持直接粘贴 Vue 源码", 5, 9);
        promptDraft.setId(View.generateViewId());
        promptLabel.setLabelFor(promptDraft.getId());
        promptDraft.setText(initialPrompt);
        append(form, promptDraft, 8);

        TextView watermarkLabel = label("背景水印", 14, INK, true);
        append(form, watermarkLabel, 18);
        watermarkDraft = textField("留空可隐藏水印", 1, 1);
        watermarkDraft.setId(View.generateViewId());
        watermarkLabel.setLabelFor(watermarkDraft.getId());
        watermarkDraft.setSingleLine(true);
        watermarkDraft.setFilters(new InputFilter[]{new InputFilter.LengthFilter(30)});
        watermarkDraft.setText(initialWatermark);
        append(form, watermarkDraft, 8);

        ScrollView formScroll = new ScrollView(this);
        formScroll.setFocusableInTouchMode(true);
        formScroll.addView(form);
        formScroll.requestFocus();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(adding ? "新增题目" : "编辑题目")
                .setView(formScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null);
        boolean canDelete = !adding && questions.size() > 1;
        if (canDelete) {
            builder.setNeutralButton("删除本题", null);
        }
        editor = builder.create();
        editor.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        editor.show();
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> saveEditor());
        if (canDelete) {
            editor.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> confirmDelete());
        }
    }

    private void saveEditor() {
        String value = promptDraft.getText().toString();
        if (value.trim().isEmpty()) {
            promptDraft.requestFocus();
            promptDraft.setError("请输入题干");
            return;
        }
        if (addingQuestion) {
            questions.add(new Question(value, ""));
            currentIndex = questions.size() - 1;
        } else {
            question().prompt = value;
        }
        watermark = watermarkDraft.getText().toString().trim();
        savePaper();
        editor.dismiss();
        showQuestion();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除第 " + (currentIndex + 1) + " 题？")
                .setMessage("题干和该题的作答内容会一起删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    questions.remove(currentIndex);
                    currentIndex = Math.min(currentIndex, questions.size() - 1);
                    savePaper();
                    editor.dismiss();
                    showQuestion();
                })
                .show();
    }

    private void restorePaper() {
        String storedPaper = preferences.getString("paper", "");
        if (!storedPaper.isEmpty()) {
            try {
                JSONObject paper = new JSONObject(storedPaper);
                JSONArray rows = paper.getJSONArray("questions");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    questions.add(new Question(row.getString("prompt"), row.optString("answer")));
                }
                watermark = paper.optString("watermark", DEFAULT_WATERMARK);
                currentIndex = Math.max(0, Math.min(paper.optInt("current"), questions.size() - 1));
            } catch (JSONException malformedPaper) {
                questions.clear();
            }
        }
        if (questions.isEmpty()) {
            questions.add(new Question(EXAMPLE, ""));
            currentIndex = 0;
        }
    }

    private void savePaper() {
        JSONArray rows = new JSONArray();
        try {
            for (Question item : questions) {
                JSONObject row = new JSONObject();
                row.put("prompt", item.prompt);
                row.put("answer", item.answer);
                rows.put(row);
            }
            JSONObject paper = new JSONObject();
            paper.put("questions", rows);
            paper.put("watermark", watermark);
            paper.put("current", currentIndex);
            preferences.edit().putString("paper", paper.toString()).apply();
        } catch (JSONException impossible) {
            throw new IllegalStateException("Unable to serialize the local practice paper", impossible);
        }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private void append(LinearLayout parent, View view, int topMargin) {
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(MATCH, WRAP);
        position.topMargin = dp(topMargin);
        parent.addView(view, position);
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        if (bold) {
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return label;
    }

    private void markHeading(TextView label) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            label.setAccessibilityHeading(true);
        }
    }

    private EditText textField(String hint, int minLines, int maxLines) {
        EditText field = new EditText(this);
        field.setBackground(surface(0xe8ffffff, BORDER, 12));
        field.setTextColor(INK);
        field.setHintTextColor(MUTED);
        field.setHint(hint);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTextSize(16);
        field.setMinLines(minLines);
        field.setMaxLines(maxLines);
        if (minLines == 1) {
            field.setMinHeight(dp(48));
        }
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        return field;
    }

    private Button action(String value, int textColor, int fill) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinimumWidth(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), dp(5), dp(14), dp(5));
        button.setStateListAnimator(null);
        button.setBackground(actionBackground(fill));
        return button;
    }

    private RippleDrawable actionBackground(int fill) {
        return new RippleDrawable(ColorStateList.valueOf(0x24006fba),
                surface(fill, Color.TRANSPARENT, 8), surface(WHITE, Color.TRANSPARENT, 8));
    }

    private void setAvailable(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setTextColor(enabled ? PRIMARY : DISABLED_TEXT);
    }

    private GradientDrawable surface(int fill, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (border != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), border);
        }
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Question {
        String prompt;
        String answer;

        Question(String prompt, String answer) {
            this.prompt = prompt;
            this.answer = answer;
        }
    }

    private final class CountdownButton extends Button {
        private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String time = "";
        private String spokenTime = "设置练习倒计时";

        CountdownButton() {
            super(MainActivity.this);
            setMinHeight(dp(48));
            setStateListAnimator(null);
            setBackground(actionBackground(Color.TRANSPARENT));
            digits.setColor(MUTED);
            digits.setTypeface(Typeface.MONOSPACE);
            digits.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13,
                    getResources().getDisplayMetrics()));
            setMinimumWidth((int) Math.ceil(dp(40) + digits.measureText("240:00")));
            clock.setColor(MUTED);
            clock.setStyle(Paint.Style.STROKE);
            clock.setStrokeWidth(dp(1));
            clock.setStrokeCap(Paint.Cap.ROUND);
        }

        void showTime(String display, String description) {
            time = display;
            spokenTime = description;
            // Paint the next second without broadcasting a text change to accessibility services.
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float x = dp(20);
            float y = getHeight() / 2f;
            canvas.drawCircle(x, y, dp(6), clock);
            canvas.drawLine(x, y - dp(3), x, y, clock);
            canvas.drawLine(x, y, x + dp(3), y, clock);
            float baseline = y - (digits.ascent() + digits.descent()) / 2;
            canvas.drawText(time, dp(32), baseline, digits);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(time);
            info.setContentDescription(spokenTime);
        }
    }

    private final class WatermarkLayout extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        WatermarkLayout(Context context) {
            super(context);
            setWillNotDraw(false);
            paint.setColor(0x1b666666);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(12));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (watermark.isEmpty()) {
                return;
            }
            int width = getWidth();
            int height = getHeight();
            int span = Math.max(width, height);
            float stride = Math.max(dp(186), paint.measureText(watermark) + dp(48));
            int rowHeight = dp(108);
            canvas.save();
            canvas.rotate(-19, width / 2f, height / 2f);
            for (int row = -span / rowHeight; row <= (height + span) / rowHeight; row++) {
                float x = width / 2f + ((row & 1) == 0 ? 0 : stride / 2);
                while (x > -span) {
                    x -= stride;
                }
                for (; x < width + span; x += stride) {
                    canvas.drawText(watermark, x, row * rowHeight + dp(42), paint);
                }
            }
            canvas.restore();
        }
    }
}
