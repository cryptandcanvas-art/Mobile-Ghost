package app.sovereign.phone;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout layout, cards;
    private EditText endpoint, input;
    private DeviceIdentity identity;
    private TextView status, answer;
    private boolean busy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 48, 24, 32); layout.setBackgroundColor(Color.rgb(5, 11, 12));
        scroll.addView(layout); setContentView(scroll);
        label("GHOSTWOLF SYSTEM", 13);
        label("GRIM", 38);
        label("ACTION STREAM", 20);
        label("Connect your service to load live actions", 14);
        endpoint = field("HTTPS service URL", false);
        endpoint.setText("https://10.0.0.122:8443");
        button("Pair this phone", () -> new android.app.AlertDialog.Builder(this)
            .setTitle("Bind desktop vault to this phone?")
            .setMessage("The service permits one paired phone. Confirm that this is your intended Creator device.")
            .setNegativeButton("Cancel", null).setPositiveButton("Pair", (d, w) -> execute(() -> {
                JSONObject challenge = new JSONObject(client().request("/api/challenge", null));
                String nonce = challenge.getString("challenge");
                JSONObject result = new JSONObject(client().request("/api/pair", new JSONObject()
                    .put("device_name", "Ghostwolf Handset").put("public_key_jwk", identity.jwk)
                    .put("telemetry", new JSONObject().put("model", android.os.Build.MODEL).put("android_version", android.os.Build.VERSION.RELEASE))
                    .put("challenge", nonce).put("signature", identity.sign(nonce))));
                if (!"PAIRED".equals(result.getString("status")) || !identity.fingerprint.equals(result.getString("fingerprint")))
                    throw new java.io.IOException("Pairing was not confirmed for this device.");
            })).show());
        button("Refresh stream", () -> execute(() -> {
            JSONObject feed = new JSONObject(client().request("/api/inbox/feed", null));
            JSONArray items = feed.getJSONArray("items");
            runOnUiThread(() -> render(items));
        }));
        status = label("Disconnected · no sample data", 14);
        cards = new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL); layout.addView(cards);
        label("CAPTURE / ASK", 20);
        input = field("Paste text or write a message", false); input.setMinLines(3);
        button("Notification access settings", () -> startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        button("Review next captured notification", () -> {
            String capture = ActionNotificationListener.takeNext();
            if (capture == null) status.setText("No captured notification. Enable access in Android Settings to capture new notifications.");
            else { input.setText(capture); status.setText("Captured locally · review before sending to your service"); }
        });
        button("Ingest text", () -> execute(() -> {
            String result = client().request("/api/inbox/ingest", new JSONObject().put("text", text(input)).put("source", "ANDROID_MANUAL_REVIEW").put("sender", "CREATOR"));
            JSONObject item = new JSONObject(result).getJSONObject("item");
            runOnUiThread(() -> { try { addCard(item); } catch (Exception e) { status.setText("Ingest response does not match proposed contract."); } });
        }));
        answer = label("", 16);
        button("Ask sovereign brain", () -> {
            if (busy) return;
            answer.setText("");
            execute(() -> {
                JSONObject result = new JSONObject(client().request("/api/omega/chat", new JSONObject().put("message", text(input)).put("session_id", identity.fingerprint).put("stream", false)));
                if (!"SUCCESS".equals(result.getString("status"))) throw new java.io.IOException("Chat operation was not confirmed.");
                String reply = result.getString("reply");
                runOnUiThread(() -> answer.setText(reply));
            });
        });
    }
    private ApiClient client() { return new ApiClient(text(endpoint), identity); }
    private String text(EditText field) {
        // Values are read on the UI thread before network work through execute's captured snapshot below.
        return values.get(field);
    }
    private final java.util.Map<EditText,String> values = new java.util.HashMap<>();
    private interface Work { void run() throws Exception; }
    private void execute(Work operation) {
        if (busy) return;
        values.put(endpoint, endpoint.getText().toString().trim());
        values.put(input, input.getText().toString());
        busy = true; status.setText("Working…");
        worker.submit(() -> {
            try { if (identity == null) identity = new DeviceIdentity(); operation.run(); runOnUiThread(() -> status.setText("Service operation completed")); }
            catch (Exception e) { runOnUiThread(() -> status.setText("Unable to confirm operation: " + safeError(e))); }
            finally { runOnUiThread(() -> busy = false); }
        });
    }
    private String safeError(Exception e) {
        if (e instanceof java.io.IOException || e instanceof IllegalArgumentException) return e.getMessage();
        return "Response does not match the proposed contract.";
    }
    private void render(JSONArray items) {
        cards.removeAllViews();
        try { for (int i = 0; i < items.length(); i++) addCard(items.getJSONObject(i)); }
        catch (JSONException e) { cards.removeAllViews(); status.setText("Feed does not match proposed contract."); }
    }
    private void addCard(JSONObject item) throws JSONException {
        String id = item.getString("id");
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(16, 20, 16, 20);
        card.setBackgroundColor(Color.rgb(13, 24, 25));
        TextView text = new TextView(this); text.setTextColor(Color.WHITE); text.setTextSize(17);
        text.setText(item.getString("priority_label") + " · " + item.getDouble("score") + "/10\n" + item.getString("text") + "\n" + item.getString("origin") + "\n" + item.optString("meta"));
        card.addView(text); cards.addView(card);
        for (String action : new String[]{"commit", "purge"}) {
            Button button = new Button(this); button.setText(action);
            button.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle(action.equals("commit") ? "Commit this action?" : "Purge this action?")
                .setMessage(item.optString("text")).setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm", (dialog, which) -> execute(() -> {
                    JSONObject result = new JSONObject(client().request("/api/inbox/" + action, new JSONObject().put("id", id)));
                    String expected = action.equals("commit") ? "COMMITTED" : "PURGED";
                    if (!expected.equals(result.getString("status"))) throw new java.io.IOException("Service did not confirm " + action + ".");
                    runOnUiThread(() -> cards.removeView(card));
                })).show());
            card.addView(button);
        }
    }
    private TextView label(String value, int size) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(size >= 20 ? Color.rgb(243, 247, 247) : Color.rgb(189, 201, 202)); if (size >= 20) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); t.setPadding(0, 12, 0, 12); layout.addView(t); return t; }
    private EditText field(String hint, boolean secret) { EditText f = new EditText(this); f.setHint(hint); f.setTextColor(Color.WHITE); f.setHintTextColor(Color.GRAY); f.setSaveEnabled(false); f.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); if (secret) f.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); layout.addView(f); return f; }
    private void button(String title, Runnable action) { Button b = new Button(this); b.setText(title); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(26, 161, 163))); b.setTextColor(Color.rgb(2, 16, 18)); b.setOnClickListener(v -> action.run()); layout.addView(b); }
    @Override protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
