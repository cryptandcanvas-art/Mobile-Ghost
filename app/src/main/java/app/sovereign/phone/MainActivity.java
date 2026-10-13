package app.sovereign.phone;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff070c0e, PANEL=0xff101b1e, LINE=0xff233b40, TEAL=0xff1aa1a3, WHITE=0xfff3f7f7, MUTED=0xff91a9ad;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root, pages, stream, brain, settings, cards;
    private EditText endpoint, capture, prompt;
    private TextView status, count, defense, reply, empty;
    private ImageView radar;
    private DeviceIdentity identity;
    private boolean busy;
    private int appearance;
    private final Button[] navButtons=new Button[3];
    private String baseSnapshot, captureSnapshot, promptSnapshot;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        appearance=getPreferences(MODE_PRIVATE).getInt("appearance_mode",0)==1?1:0;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        root=column(); root.setBackgroundColor(BG); root.setPadding(dp(20),dp(12),dp(20),dp(12));
        setContentView(root);
        LinearLayout mast=row(); root.addView(mast);
        ImageView mark=new ImageView(this); mark.setImageResource(R.drawable.ic_omega_shield); mark.setContentDescription("Little Omega shield"); mast.addView(mark,new LinearLayout.LayoutParams(dp(32),dp(36)));
        TextView brand=label("GHOSTWOLF SYSTEM  /  GRIM",11,TEAL); brand.setLetterSpacing(.12f); mast.addView(brand,weighted());
        TextView edition=label("Ω  02",12,MUTED); mast.addView(edition);
        status=label("Link not checked · open Settings to connect",12,MUTED); status.setPadding(0,dp(12),0,dp(10)); root.addView(status);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        pages=column(); scroll.addView(pages);
        stream=column(); brain=column(); settings=column(); pages.addView(stream); pages.addView(brain); pages.addView(settings);
        buildStream(); buildBrain(); buildSettings();
        LinearLayout nav=row(); nav.setPadding(0,dp(12),0,0); root.addView(nav);
        String[] names={"STREAM","BRAIN","SETTINGS"};
        for(int i=0;i<names.length;i++){ final int index=i; Button b=button(names[i],false,()->select(index)); b.setTextSize(11); navButtons[i]=b; nav.addView(b,weighted()); }
        select(0);applyAppearance(root);
    }
    private void buildStream(){
        TextView eyebrow=label("YOUR EXECUTIVE INTERFACE",10,MUTED); eyebrow.setLetterSpacing(.18f); stream.addView(eyebrow);
        stream.addView(label("Little Omega",32,WHITE));
        stream.addView(label("Clarity. Control. Continuity.",14,MUTED));
        OrbitView orbit=new OrbitView();orbit.setTag(R.id.appearance_role,"orbit");stream.addView(orbit,new LinearLayout.LayoutParams(-1,dp(182)));
        LinearLayout metrics=row(); stream.addView(metrics);
        LinearLayout left=panel(); metrics.addView(left,weighted()); left.addView(label("ACTION BUFFER",10,MUTED)); count=label("—",28,WHITE); left.addView(count);
        LinearLayout right=panel(); LinearLayout.LayoutParams rp=weighted(); rp.leftMargin=dp(10); metrics.addView(right,rp);
        LinearLayout radarRow=row(); right.addView(radarRow); radar=new ImageView(this); radar.setImageResource(R.drawable.ic_sentry_ring); radar.setColorFilter(MUTED); radar.setContentDescription("Sentry state not checked"); radarRow.addView(radar,new LinearLayout.LayoutParams(dp(30),dp(30))); radarRow.addView(label("SENTRY",10,MUTED)); defense=label("UNKNOWN",13,MUTED); right.addView(defense);
        LinearLayout title=row(); title.setPadding(0,dp(20),0,dp(10)); stream.addView(title); title.addView(label("Action stream",21,WHITE),weighted()); title.addView(button("REFRESH",false,this::refresh));
        cards=column(); stream.addView(cards); showEmpty();
        LinearLayout box=panel(); marginTop(stream,box,16); box.addView(label("CAPTURE AN ACTION",11,TEAL));
        capture=field("Paste a task, deadline, or message…",3); box.addView(capture);
        box.addView(button("Add to action stream  ↗",true,this::ingest));
        box.addView(button("Review captured notification",false,()->{
            String text=ActionNotificationListener.takeNext();
            if(text==null) say("No captured notification · enable access in Settings",MUTED);
            else {capture.setText(text); say("Review this notification before sending",TEAL);}
        }));
    }
    private void buildBrain(){
        brain.addView(label("TACTICAL WORKSPACE",10,TEAL)); brain.addView(label("Talk to Omega",30,WHITE));
        brain.addView(label("Business briefs and system analysis",14,MUTED));
        LinearLayout chat=panel(); marginTop(brain,chat,20);
        chat.addView(label("OMEGA  /  DESKTOP BRIDGE",10,TEAL));
        reply=label("Your conversation starts here.",17,WHITE); reply.setPadding(0,dp(14),0,dp(16)); chat.addView(reply);
        prompt=field("What are we working on?",4); marginTop(brain,prompt,16);
        brain.addView(button("Send to Omega  ↗",true,()->execute(()->{
            if(promptSnapshot.trim().isEmpty()) throw new IllegalArgumentException("Write a message first.");
            JSONObject result=new JSONObject(client().request("/api/omega/chat",new JSONObject().put("message",promptSnapshot).put("session_id",identity.fingerprint).put("stream",false)));
            if(!"SUCCESS".equals(result.getString("status"))) throw new java.io.IOException("Chat was not confirmed.");
            String text=result.getString("reply"); ui(()->reply.setText(text));
        },"Reply received")));
        brain.addView(label("Replies come from your configured desktop service.",11,MUTED));
    }
    private void buildSettings(){
        settings.addView(label("DEVICE & CONNECTION",10,TEAL)); settings.addView(label("Control center",30,WHITE));
        LinearLayout appearancePanel=panel();marginTop(settings,appearancePanel,20);appearancePanel.addView(label("APPEARANCE",11,TEAL));
        RadioGroup modes=new RadioGroup(this);modes.setOrientation(RadioGroup.VERTICAL);
        String[] modeNames={"Sovereign Cards — clean & quiet","Tactical Cockpit — orbital dashboard"};
        for(int i=0;i<modeNames.length;i++){RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setText(modeNames[i]);option.setTextColor(WHITE);option.setTextSize(14);option.setMinHeight(dp(48));option.setTag(i);modes.addView(option);if(i==appearance)modes.check(option.getId());}
        modes.setOnCheckedChangeListener((group,id)->{View option=group.findViewById(id);if(option!=null){appearance=(Integer)option.getTag();getPreferences(MODE_PRIVATE).edit().putInt("appearance_mode",appearance).apply();applyAppearance(root);}});
        appearancePanel.addView(modes);appearancePanel.addView(label("Your choice is saved on this phone.",12,MUTED));
        LinearLayout connection=panel(); marginTop(settings,connection,20); connection.addView(label("DESKTOP NODE",11,TEAL));
        endpoint=field("HTTPS service URL",1); endpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI); endpoint.setText("https://10.0.0.122:8443"); connection.addView(endpoint);
        connection.addView(button("Check link & sentry",true,this::refresh));
        connection.addView(button("Pair this phone",false,()->new AlertDialog.Builder(this).setTitle("Bind vault to this phone?").setMessage("Only pair an unbound vault. If this phone is already enrolled, use Check link instead.").setNegativeButton("Cancel",null).setPositiveButton("Pair",(d,w)->pair()).show()));
        LinearLayout device=panel(); marginTop(settings,device,16); device.addView(label("LOCAL CAPTURE",11,TEAL));
        device.addView(label("Notification access stays under your control. Captured text is reviewed before upload.",14,MUTED));
        device.addView(button("Android notification settings",false,()->startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        LinearLayout identityPanel=panel(); marginTop(settings,identityPanel,16); identityPanel.addView(label("CREATOR",11,TEAL)); identityPanel.addView(label("GhostwolfArt",22,WHITE)); identityPanel.addView(label("Ghostwolf Systems",14,MUTED));
        identityPanel.addView(label("Public owner identity · biometric unlock integration pending",11,MUTED));
        settings.addView(label("Little Omega 0.3  /  Native Android",11,MUTED));
    }
    private void refresh(){ if(busy)return; defense.setText("CHECKING");defense.setTextColor(MUTED);radar.setColorFilter(MUTED); execute(()->{
        JSONObject feed=new JSONObject(client().request("/api/inbox/feed",null));
        JSONArray items=feed.getJSONArray("items");
        java.util.List<JSONObject> validated=new java.util.ArrayList<>();
        for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i); validate(item); validated.add(item);}
        ui(()->{ cards.removeAllViews(); for(JSONObject item:validated)addCard(item); updateCount(); if(validated.isEmpty())showEmpty(); });
        try { JSONObject telemetry=new JSONObject(client().request("/api/security/telemetry",null)); int level=telemetry.getInt("active_threat_level"); if(level<1||level>5)throw new JSONException("Invalid level"); ui(()->setDefense(level)); }
        catch(Exception unavailable){ ui(()->{defense.setText("UNAVAILABLE"); defense.setTextColor(MUTED);radar.setColorFilter(MUTED);radar.setContentDescription("Sentry telemetry unavailable");}); }
    },"Signed link verified · stream updated"); }
    private void pair(){ execute(()->{
        JSONObject challenge=new JSONObject(client().request("/api/challenge",null)); String nonce=challenge.getString("challenge");
        JSONObject result=new JSONObject(client().request("/api/pair",new JSONObject().put("device_name","Ghostwolf Handset").put("public_key_jwk",identity.jwk).put("telemetry",new JSONObject().put("model",android.os.Build.MODEL).put("android_version",android.os.Build.VERSION.RELEASE)).put("challenge",nonce).put("signature",identity.sign(nonce))));
        if(!"PAIRED".equals(result.getString("status"))||!identity.fingerprint.equals(result.getString("fingerprint")))throw new java.io.IOException("Pairing was not confirmed for this key.");
    },"Phone enrolled · refresh to verify link"); }
    private void ingest(){ execute(()->{
        if(captureSnapshot.trim().isEmpty())throw new IllegalArgumentException("Write or review some text first.");
        JSONObject result=new JSONObject(client().request("/api/inbox/ingest",new JSONObject().put("text",captureSnapshot).put("source","ANDROID_MANUAL_REVIEW").put("sender","CREATOR")));
        JSONObject item=result.getJSONObject("item"); validate(item);
        ui(()->{if(empty!=null){cards.removeView(empty);empty=null;}addCard(item);updateCount();capture.setText("");});
    },"Action added to your stream"); }
    private void validate(JSONObject item)throws JSONException{item.getString("id");item.getString("text");item.getString("priority_label");item.getString("origin");item.getDouble("score");}
    private void addCard(JSONObject item){
        LinearLayout card=panel(); marginTop(cards,card,10);
        String id=item.optString("id"); card.setTag(id);
        LinearLayout top=row();top.setTag(R.id.appearance_role,"taskHeader");card.addView(top); String priority=item.optString("priority_label").replace("PRIORITY ","");
        int accent=item.optDouble("score")>=9?0xffffd08a:TEAL;
        TextView chip=label(priority,10,accent); chip.setLetterSpacing(.08f); top.addView(chip,weighted()); top.addView(label(String.format(java.util.Locale.ROOT,"%.1f / 10",item.optDouble("score")),16,WHITE));
        TextView task=label(item.optString("text"),18,WHITE);task.setTypeface(Typeface.DEFAULT,Typeface.BOLD);task.setTag(R.id.appearance_role,"taskTitle");task.setPadding(0,dp(14),0,dp(10));card.addView(task);
        TextView subtitle=label(priority+" · "+String.format(java.util.Locale.ROOT,"%.1f / 10",item.optDouble("score"))+"\n"+item.optString("origin")+"\n"+item.optString("meta"),13,MUTED);subtitle.setTag(R.id.appearance_role,"taskSubtitle");card.addView(subtitle);
        LinearLayout actions=row();actions.setPadding(0,dp(10),0,0);card.addView(actions);
        for(String action:new String[]{"commit","purge"})actions.addView(button(action.equals("commit")?"COMMIT":"PURGE",action.equals("commit"),()->new AlertDialog.Builder(this).setTitle(action.equals("commit")?"Commit this action?":"Purge this action?").setMessage(item.optString("text")).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->execute(()->{
            JSONObject result=new JSONObject(client().request("/api/inbox/"+action,new JSONObject().put("id",id)));
            if(!(action.equals("commit")?"COMMITTED":"PURGED").equals(result.getString("status")))throw new java.io.IOException("Action was not confirmed.");
            ui(()->{cards.removeView(card);updateCount();if(cards.getChildCount()==0)showEmpty();});
        },action.equals("commit")?"Action committed":"Action purged")).show()),weighted());
        applyAppearance(card);
    }
    private void updateCount(){int n=0;for(int i=0;i<cards.getChildCount();i++)if(cards.getChildAt(i).getTag()!=null)n++;count.setText(Integer.toString(n));}
    private void showEmpty(){empty=label("Your stream is clear.\nRefresh to load actions or capture one below.",14,MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(16),dp(24),dp(16),dp(24));cards.addView(empty);}
    private void setDefense(int level){int color=level==1?0xff70d5a8:level<4?0xffffd08a:0xffff7e8c; String[] names={"","NOMINAL","GUARD","MITIGATING","CONTAINMENT","QUARANTINE"};defense.setText("L"+level+" · "+names[level]);defense.setTextColor(color);radar.setColorFilter(color);radar.setContentDescription("Server reported sentry level "+level);}
    private interface Work{void run()throws Exception;}
    private void execute(Work work,String success){
        if(busy)return;busy=true;baseSnapshot=endpoint.getText().toString().trim();captureSnapshot=capture.getText().toString();promptSnapshot=prompt.getText().toString();
        say("Connecting securely…",TEAL);setEnabled(false);
        worker.submit(()->{try{if(identity==null)identity=new DeviceIdentity();work.run();ui(()->say(success,TEAL));}catch(Exception error){ui(()->{say("Unable to confirm: "+safeError(error),0xffffa5ad);defense.setText("UNVERIFIED");defense.setTextColor(MUTED);radar.setColorFilter(MUTED);});}finally{ui(()->{busy=false;setEnabled(true);});}});
    }
    private ApiClient client(){return new ApiClient(baseSnapshot,identity);}
    private void ui(Runnable action){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())action.run();});}
    private String safeError(Exception error){if(error instanceof java.io.IOException||error instanceof IllegalArgumentException||error instanceof java.security.GeneralSecurityException)return error.getMessage();return "Response did not match the service contract.";}
    private void say(String text,int color){status.setText(text);status.setTextColor(color);}
    private void setEnabled(boolean enabled){enableButtons(root,enabled);endpoint.setEnabled(enabled);capture.setEnabled(enabled);prompt.setEnabled(enabled);}
    private void enableButtons(View view,boolean enabled){if(view instanceof Button)view.setEnabled(enabled);if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)enableButtons(group.getChildAt(i),enabled);}}
    private void select(int index){stream.setVisibility(index==0?View.VISIBLE:View.GONE);brain.setVisibility(index==1?View.VISIBLE:View.GONE);settings.setVisibility(index==2?View.VISIBLE:View.GONE);for(int i=0;i<navButtons.length;i++){navButtons[i].setBackground(surface(i==index?PANEL:Color.TRANSPARENT,i==index?TEAL:LINE));navButtons[i].setTextColor(i==index?WHITE:MUTED);}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams weighted(){return new LinearLayout.LayoutParams(0,-2,1);}
    private GradientDrawable surface(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));d.setStroke(dp(1),stroke);return d;}
    private LinearLayout panel(){LinearLayout v=column();v.setTag(R.id.appearance_role,"panel");v.setPadding(dp(16),dp(16),dp(16),dp(16));v.setBackground(surface(PANEL,LINE));return v;}
    private void applyAppearance(View view){Object role=view.getTag(R.id.appearance_role);boolean quiet=appearance==0;
        if("orbit".equals(role))view.setVisibility(quiet?View.GONE:View.VISIBLE);
        if("taskHeader".equals(role))view.setVisibility(quiet?View.GONE:View.VISIBLE);
        if("panel".equals(role)){int pad=dp(quiet?20:16);view.setPadding(pad,pad,pad,pad);view.setBackground(surface(quiet?0xff11171b:PANEL,quiet?0xff263137:LINE));}
        if("taskTitle".equals(role)){((TextView)view).setTextSize(quiet?16:18);view.setPadding(0,dp(quiet?0:14),0,dp(quiet?4:10));}
        if("taskSubtitle".equals(role)){((TextView)view).setTextColor(quiet?0x99ffffff:MUTED);((TextView)view).setTextSize(13);}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyAppearance(group.getChildAt(i));}
    }
    private void marginTop(LinearLayout parent,View view,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(view,p);}
    private TextView label(String text,int size,int color){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(dp(2),dp(3),dp(2),dp(3));if(size>=20)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private EditText field(String hint,int lines){EditText v=new EditText(this);v.setHint(hint);v.setTextColor(WHITE);v.setHintTextColor(MUTED);v.setTextSize(15);v.setMinLines(lines);v.setGravity(Gravity.TOP);v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(surface(BG,LINE));v.setSaveEnabled(false);v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);return v;}
    private Button button(String text,boolean primary,Runnable action){Button b=new Button(this);b.setText(text);b.setTextSize(12);b.setAllCaps(false);b.setMinHeight(dp(48));b.setTextColor(primary?BG:TEAL);b.setBackground(surface(primary?TEAL:Color.TRANSPARENT,primary?TEAL:LINE));b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setOnClickListener(v->action.run());b.setEnabled(!busy);return b;}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private final class OrbitView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        OrbitView(){super(MainActivity.this);setContentDescription("Omega shield with orbital contours");}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=dp(72);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(LINE);c.drawCircle(x,y,r,p);c.drawCircle(x,y,r+dp(12),p);p.setColor(TEAL);p.setStrokeWidth(dp(2));c.drawArc(x-r,y-r,x+r,y+r,220,82,false,p);c.drawArc(x-r,y-r,x+r,y+r,40,32,false,p);p.setStyle(Paint.Style.FILL);p.setColor(TEAL);c.drawCircle(x-r+dp(4),y+dp(22),dp(3),p);android.graphics.drawable.Drawable shield=getDrawable(R.drawable.ic_omega_shield);shield.setBounds((int)x-dp(48),(int)y-dp(48),(int)x+dp(48),(int)y+dp(48));shield.draw(c);}
    }
}
