package fixture;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.content.Intent;
import android.widget.*;
import android.view.inputmethod.EditorInfo;

/** Disposable UI fixture. This is explicitly labelled as a test, not real WeChat. */
public class MainActivity extends Activity {
    android.content.SharedPreferences config;
    LinearLayout body;
    final Handler handler = new Handler();
    @Override public void onCreate(Bundle state) { super.onCreate(state); config = getSharedPreferences("fixture", MODE_PRIVATE); configure(getIntent()); home(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); configure(intent); home(); }
    private void configure(Intent intent) {
        if (intent.hasExtra("scenario")) config.edit().putString("scenario", intent.getStringExtra("scenario")).apply();
        if (intent.hasExtra("clipboard")) ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("fixture", intent.getStringExtra("clipboard")));
    }
    private void setup() {
        body = new LinearLayout(this); body.setOrientation(1); body.setPadding(24, 80, 24, 24); setContentView(body);
        TextView title = new TextView(this); title.setText("微信自动化测试夹具（非微信客户端）"); body.addView(title);
    }
    private void home() {
        handler.removeCallbacksAndMessages(null);
        setup(); String scenario = config.getString("scenario", "normal");
        if ("prepare".equals(scenario)) { search(); return; }
        if ("login".equals(scenario)) {
            EditText phone = new EditText(this); phone.setHint("手机号"); body.addView(phone);
            EditText password = new EditText(this); password.setInputType(129); password.setHint("密码"); body.addView(password);
            Button send = new Button(this); send.setText("发送"); send.setOnClickListener(v -> bad()); body.addView(send);
            Button pay = new Button(this); pay.setText("付款"); pay.setOnClickListener(v -> bad()); body.addView(pay);
            return;
        }
        if ("delayed".equals(scenario)) handler.postDelayed(this::searchButton, 4500);
        else searchButton();
    }
    private void bad() { TextView view = new TextView(this); view.setText("BAD_CLICK"); body.addView(view); }
    private void searchButton() {
        Button open = new Button(this); open.setText("搜索"); open.setContentDescription("搜索"); open.setOnClickListener(v -> search()); body.addView(open);
    }
    private void search() {
        setup(); EditText editor = new EditText(this); editor.setHint("搜索"); editor.setSingleLine(true);
        editor.setImeOptions(EditorInfo.IME_ACTION_SEARCH); editor.setImeActionLabel("搜索", EditorInfo.IME_ACTION_SEARCH); body.addView(editor);
        if (!"ime".equals(config.getString("scenario", "normal"))) {
            Button submit = new Button(this); submit.setText("搜一搜"); submit.setOnClickListener(v -> result(editor.getText().toString())); body.addView(submit);
        }
        editor.setOnEditorActionListener((v, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { result(editor.getText().toString()); return true; } return false; });
    }
    private void result(String query) {
        long completed = android.os.SystemClock.elapsedRealtime();
        setup(); TextView result = new TextView(this); result.setText("RESULT:" + query); body.addView(result);
        TextView timing = new TextView(this); timing.setText("FIXTURE_RESULT_UPTIME:" + completed); body.addView(timing);
    }
}
