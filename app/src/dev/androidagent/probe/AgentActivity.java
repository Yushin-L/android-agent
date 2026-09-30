package dev.androidagent.probe;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** GUI workspaces contain Codex sessions; navigation does not control execution. */
public final class AgentActivity extends Activity {
    private AgentService service;
    private FileUi fileUi;
    private AlertDialog approvalDialog;
    private final java.util.Set<String> expandedCommands=new java.util.HashSet<>();
    private LinearLayout attachmentRows;
    private String shownAttachments="";
    private String workspace="",session="",screen="home",shownItems="",shownQuestion="";
    private LinearLayout root,body,messages,suggestions;
    private ScrollView scroll;
    private EditText input;
    private TextView status,accountView,modelView;
    private ImageButton send,stop;
    private int bg,surface,ink,muted,accent,onAccent,errorColor;
    private boolean bound,restoring,sending;
    private String notificationSession="";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable persistDraft=this::saveUi;
    private Typeface regular=Typeface.DEFAULT,medium=Typeface.DEFAULT,semibold=Typeface.DEFAULT;
    private final Runnable refresh=this::refresh;
    private final ServiceConnection binding=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){
            service=((AgentService.LocalBinder)binder).service();service.observe(refresh);fileUi.bind(service);
            restoreLocation();render();
            service.submit(service::refreshAccount,e->{if(e!=null)toast("계정 연결을 확인하지 못했습니다");});
            if(!session.isEmpty())loadSession();
        }
        public void onServiceDisconnected(ComponentName name){service=null;render();}
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);if(saved!=null){java.util.ArrayList<String> expanded=saved.getStringArrayList("expandedCommands");if(expanded!=null)expandedCommands.addAll(expanded);}fileUi=new FileUi(this,saved,this::refresh);
        if(saved!=null){workspace=saved.getString("workspace","");session=saved.getString("session","");screen=saved.getString("screen","home");}
        if(saved==null){SharedPreferences last=getSharedPreferences("navigation",0);workspace=last.getString("workspace","");session=last.getString("session","");if(!workspace.isEmpty())screen="chat";}
        notificationSession=getIntent().getStringExtra("session");
        boolean dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        bg=dark?0xff151c18:0xfff8faf9;surface=dark?0xff202C25:0xffedf3f0;ink=dark?0xffe9f2ec:0xff18221D;
        muted=dark?0xffa8bab0:0xff5e6e65;accent=dark?0xff80d9ae:0xff087f5b;onAccent=dark?0xff151c18:0xfff8faf9;errorColor=dark?0xffffb4ab:0xffa12d2d;
        try{regular=Typeface.createFromAsset(getAssets(),"fonts/Pretendard-Regular.otf");medium=Typeface.createFromAsset(getAssets(),"fonts/Pretendard-Medium.otf");semibold=Typeface.createFromAsset(getAssets(),"fonts/Pretendard-SemiBold.otf");}catch(Exception ignored){}
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        render();Intent intent=new Intent(this,AgentService.class).setAction(AgentLifetime.START);startForegroundService(intent);bound=bindService(intent,binding,BIND_AUTO_CREATE);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED
            &&!getSharedPreferences("lifetime",0).getBoolean("notificationAsked",false)){
            getSharedPreferences("lifetime",0).edit().putBoolean("notificationAsked",true).apply();
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},71);
        }
    }
    private void restoreLocation(){
        try{
            if(notificationSession!=null&&!notificationSession.isEmpty()){
                String owner=service.store.owner(notificationSession);service.store.select(owner,notificationSession);
                workspace=owner;session=notificationSession;screen="chat";
            }else if(!workspace.isEmpty()){
                JSONObject record=service.store.get(workspace);
                if(session.isEmpty()||!workspace.equals(service.store.owner(session)))session=record.getString("activeSession");
            }
            if(!session.isEmpty())service.dismissNotice(session);
        }catch(Exception unavailable){workspace="";session="";screen="home";}
        notificationSession="";
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);saveUi();setIntent(intent);notificationSession=intent.getStringExtra("session");
        if(service!=null){restoreLocation();render();if(!session.isEmpty())loadSession();}
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(ink);t.setTypeface(size>=22?semibold:regular);t.setPadding(0,dp(6),0,dp(6));return t;}
    private Button button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setTextSize(16);b.setTypeface(medium);b.setTextColor(states(muted,accent));b.setBackgroundTintList(states(surface,surface));b.setAllCaps(false);b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));
        b.setOnFocusChangeListener((v,focused)->{v.setForeground(edgeOutline(focused));});
        b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(-1,-2));return b;
    }
    private android.content.res.ColorStateList states(int disabled,int enabled){return new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{disabled,enabled});}
    private android.graphics.drawable.Drawable edgeOutline(boolean focused){GradientDrawable edge=new GradientDrawable();edge.setColor(android.graphics.Color.TRANSPARENT);edge.setCornerRadius(dp(8));if(focused)edge.setStroke(dp(2),accent);return edge;}
    private ImageButton iconButton(LinearLayout parent,String label,String icon,Runnable action){
        ImageButton button=new ImageButton(this);button.setContentDescription(label);button.setTooltipText(label);button.setImageDrawable(new ChatIcon(icon));button.setImageTintList(states(muted,accent));button.setPadding(dp(12),dp(12),dp(12),dp(12));
        android.util.TypedValue attr=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,attr,true);button.setBackgroundResource(attr.resourceId);
        button.setOnFocusChangeListener((view,focused)->view.setForeground(edgeOutline(focused)));button.setOnClickListener(v->action.run());parent.addView(button,new LinearLayout.LayoutParams(dp(48),dp(48)));return button;
    }
    private static final class ChatIcon extends android.graphics.drawable.Drawable {
        private android.content.res.ColorStateList tint;
        private final String kind;private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        ChatIcon(String kind){this.kind=kind;paint.setColor(android.graphics.Color.WHITE);}
        @Override public void draw(android.graphics.Canvas canvas){
            android.graphics.Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);paint.setStrokeWidth(1.8f);paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setStyle(android.graphics.Paint.Style.STROKE);
            if(kind.equals("list")){canvas.drawLine(4,6,20,6,paint);canvas.drawLine(4,12,20,12,paint);canvas.drawLine(4,18,20,18,paint);}
            else if(kind.equals("more")){paint.setStyle(android.graphics.Paint.Style.FILL);for(int y=5;y<=19;y+=7)canvas.drawCircle(12,y,1.5f,paint);}
            else if(kind.equals("plus")){canvas.drawLine(5,12,19,12,paint);canvas.drawLine(12,5,12,19,paint);}
            else if(kind.equals("send")){canvas.drawLine(12,19,12,5,paint);canvas.drawLine(6,11,12,5,paint);canvas.drawLine(12,5,18,11,paint);}
            else {paint.setStyle(android.graphics.Paint.Style.FILL);canvas.drawRoundRect(6,6,18,18,2,2,paint);}canvas.restore();
        }
        @Override public void setTintList(android.content.res.ColorStateList value){tint=value;onStateChange(getState());}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] state){if(tint!=null)paint.setColor(tint.getColorForState(state,tint.getDefaultColor()));invalidateSelf();return true;}
        @Override public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
        @Override public void setColorFilter(android.graphics.ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private void render(){
        if(approvalDialog!=null){approvalDialog.dismiss();approvalDialog=null;}
        input=null;attachmentRows=null;shownAttachments="";suggestions=null;messages=null;status=null;accountView=null;modelView=null;scroll=null;shownItems="";shownQuestion="";
        root=column();root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else root.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
        });
        setContentView(root);body=column();int gutter=getResources().getConfiguration().screenWidthDp>=600?48:16;body.setPadding(dp(gutter),dp(8),dp(gutter),dp(8));root.addView(body,new LinearLayout.LayoutParams(-1,-1));
        if(service==null){body.addView(text("연결 준비 중…",17));return;}
        if(service.store==null){body.addView(text(service.error,17));return;}
        try {if(screen.equals("settings"))settings();else if(screen.equals("chat")&&!workspace.isEmpty())chat();else home();}
        catch(Exception e){body.addView(text("화면을 불러오지 못했습니다. 목록으로 돌아가 다시 열어 주세요.",17));button(body,"쓰레드 목록",()->{screen="home";render();});}
    }
    private void home()throws Exception{
        body.addView(text("쓰레드",22));TextView hint=text("하고 싶은 일마다 대화를 모아두세요.",14);hint.setTextColor(muted);body.addView(hint);
        button(body,"새로운 쓰레드 생성",()->{
            EditText name=new EditText(this);name.setHint("쓰레드 이름");name.setSingleLine(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("새로운 쓰레드").setView(name).setNegativeButton("취소",null).setPositiveButton("만들기",null).create();
            dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("이름을 입력해 주세요");return;}
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                service.submit(()->workspace=service.createWorkspace(value),e->{if(e!=null){name.setError("생성 실패 · 연결과 로그인을 확인해 주세요");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}else{dialog.dismiss();openWorkspace(workspace);}});
            }));dialog.show();
        });
        ScrollView list=new ScrollView(this);LinearLayout rows=column();list.addView(rows);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        JSONArray all=service.store.list();if(all.length()==0)rows.addView(text("아직 쓰레드가 없습니다",17));
        for(int i=0;i<all.length();i++){JSONObject w=all.getJSONObject(i);String id=w.getString("id");LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);rows.addView(row);Button open=button(row,w.getString("name"),()->openWorkspace(id));open.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));ImageButton menu=iconButton(row,w.getString("name")+" 메뉴","more",()->{});menu.setOnClickListener(v->{PopupMenu popup=new PopupMenu(this,menu);popup.getMenu().add("쓰레드 삭제");popup.setOnMenuItemClickListener(item->{deleteWorkspace(id);return true;});popup.show();});}
        button(body,"설정",()->{screen="settings";render();});
    }
    private void deleteWorkspace(String id){
        try {
            String name=service.store.get(id).getString("name");
            new AlertDialog.Builder(this).setTitle("“"+name+"” 삭제")
                .setMessage("이 쓰레드의 작업 파일·세션 연결·입력 초안을 삭제합니다. 폰에 따로 저장한 사본과 Codex 원본 대화 기록은 보존됩니다.")
                .setNegativeButton("취소",null).setPositiveButton("삭제",(dialog,which)->{
                    saveUi();service.submit(()->service.deleteWorkspace(id),e->{
                        if(e!=null){toast("WORKSPACE_BUSY".equals(e.getMessage())?"실행 중인 대화를 먼저 중단하거나 완료해 주세요":"쓰레드를 삭제하지 못했습니다");return;}
                        if(id.equals(workspace)){ui.removeCallbacks(persistDraft);input=null;workspace="";session="";screen="home";}
                        if(screen.equals("home"))render();toast("쓰레드를 삭제했습니다");
                    });
                }).show();
        }catch(Exception e){toast("쓰레드를 찾지 못했습니다");}
    }
    private String modelLabel(){
        try{JSONObject w=service.store.get(workspace);String model=w.optString("model"),effort=w.optString("effort");return (model.isEmpty()?"Codex 기본 모델":model)+" · "+(effort.isEmpty()?"기본 강도":effort);}
        catch(Exception e){return "모델 설정";}
    }
    private void chooseModel(){
        final String owner=workspace;final JSONArray[] catalog={null};toast("모델 목록을 불러오는 중");
        service.submit(()->catalog[0]=service.models(),e->{
            if(e!=null){toast("모델 목록을 불러오지 못했습니다. 연결을 확인해 주세요");return;}
            if(!owner.equals(workspace)||!screen.equals("chat")||isFinishing()||isDestroyed())return;
            try{
                JSONArray list=catalog[0];String[] labels=new String[list.length()+1];labels[0]="Codex 기본 모델 · 기본 추론 강도";
                String selected=service.store.get(owner).optString("model");int checked=selected.isEmpty()?0:-1;
                for(int i=0;i<list.length();i++){JSONObject item=list.getJSONObject(i);String id=item.getString("model");labels[i+1]=item.optString("displayName",id)+(item.optBoolean("isDefault")?" · 기본":"");if(id.equals(selected))checked=i+1;}
                new AlertDialog.Builder(this).setTitle("이 쓰레드의 모델").setSingleChoiceItems(labels,checked,(dialog,n)->{
                    dialog.dismiss();if(n==0){saveModel(owner,"","");return;}
                    try{chooseEffort(owner,list.getJSONObject(n-1));}catch(Exception ex){toast("모델 설정을 읽지 못했습니다");}
                }).setNegativeButton("취소",null).show();
            }catch(Exception ex){toast("쓰레드를 찾지 못했습니다");}
        });
    }
    private void chooseEffort(String owner,JSONObject model)throws Exception{
        final String id=model.getString("model");JSONArray efforts=model.optJSONArray("supportedReasoningEfforts");
        if(efforts==null||efforts.length()==0){toast("이 모델의 추론 설정을 확인할 수 없습니다");return;}
        String[] labels=new String[efforts.length()+1],values=new String[efforts.length()+1];values[0]="";labels[0]="모델 기본값 · "+model.optString("defaultReasoningEffort");
        JSONObject w=service.store.get(owner);String selected=id.equals(w.optString("model"))?w.optString("effort"):"";int checked=0;
        for(int i=0;i<efforts.length();i++){values[i+1]=efforts.getJSONObject(i).getString("reasoningEffort");labels[i+1]=values[i+1];if(selected.equals(values[i+1]))checked=i+1;}
        new AlertDialog.Builder(this).setTitle("추론 강도 · "+model.optString("displayName",id)).setSingleChoiceItems(labels,checked,(dialog,n)->{dialog.dismiss();saveModel(owner,id,values[n]);}).setNegativeButton("취소",null).show();
    }
    private void saveModel(String owner,String model,String effort){
        service.submit(()->service.setModel(owner,model,effort),e->{if(e!=null){toast("설정하지 못했습니다. 모델 목록을 다시 확인해 주세요");return;}toast("이 쓰레드의 다음 메시지부터 적용됩니다");refresh();});
    }
    private void completeCommand(){
        if(suggestions==null||input==null)return;
        suggestions.removeAllViews();String value=input.getText().toString();
        if(value.startsWith("/")){
            String[] commands={"/new","/resume","/model"},labels={"새 대화 시작","이전 대화 이어가기","모델·추론 강도 선택"};
            for(int i=0;i<commands.length;i++){final String command=commands[i];if(command.startsWith(value)&&!command.equals(value)){
                Button choice=button(suggestions,command+"  ·  "+labels[i],()->{input.setText(command);input.setSelection(command.length());input.requestFocus();});choice.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            }}
        }
        suggestions.setVisibility(suggestions.getChildCount()==0?View.GONE:View.VISIBLE);
    }
    private void openWorkspace(String id){
        saveUi();try{workspace=id;session=service.store.get(id).getString("activeSession");screen="chat";rememberLocation();service.dismissNotice(session);render();
            if(session.isEmpty())newSession();else loadSession();
        }catch(Exception e){toast("쓰레드를 열지 못했습니다");}
    }
    private void loadSession(){final String target=session;service.submit(()->service.loadSession(target),e->{if(e!=null)toast("대화 기록을 불러오지 못했습니다");refresh();});}
    private void newSession(){
        saveUi();final String owner=workspace;
        service.submit(()->service.newSession(owner),e->{if(e!=null){toast("새 대화를 만들지 못했습니다");return;}if(owner.equals(workspace))openWorkspace(owner);});
    }
    private void resume(){
        saveUi();try{JSONArray list=service.store.get(workspace).getJSONArray("sessions");String[] labels=new String[list.length()];
            for(int i=0;i<labels.length;i++)labels[i]="대화 "+(i+1)+(list.getJSONObject(i).getString("id").equals(session)?" · 현재":"");
            new AlertDialog.Builder(this).setTitle("이전 대화 이어가기").setItems(labels,(d,n)->{try{service.store.select(workspace,list.getJSONObject(n).getString("id"));openWorkspace(workspace);}catch(Exception e){toast("대화를 선택하지 못했습니다");}}).setNegativeButton("닫기",null).show();
        }catch(Exception e){toast("대화 목록을 불러오지 못했습니다");}
    }
    private void chat()throws Exception{
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);body.addView(toolbar);
        iconButton(toolbar,"쓰레드 목록","list",()->{saveUi();screen="home";render();});
        TextView title=text(service.store.get(workspace).getString("name"),18);title.setTypeface(semibold);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setPadding(dp(8),0,dp(8),0);LinearLayout heading=column();heading.addView(title);modelView=text(modelLabel(),13);modelView.setTextColor(muted);modelView.setPadding(dp(8),0,dp(8),0);modelView.setSingleLine(true);modelView.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(modelView);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton menu=iconButton(toolbar,"대화 메뉴","more",()->{});
        menu.setOnClickListener(v->{PopupMenu popup=new PopupMenu(this,menu);popup.getMenu().add(0,1,0,"새 대화 · /new");popup.getMenu().add(0,2,1,"이전 대화 · /resume");popup.getMenu().add(0,4,2,"모델 설정 · /model");popup.getMenu().add(0,3,3,"쓰레드 삭제");popup.getMenu().add(0,5,4,"쓰레드 파일");popup.setOnMenuItemClickListener(item->{if(item.getItemId()==1)newSession();else if(item.getItemId()==2)resume();else if(item.getItemId()==4)chooseModel();else if(item.getItemId()==5)fileUi.browse(workspace,"");else deleteWorkspace(workspace);return true;});popup.show();});
        status=text("대화를 불러오는 중",14);status.setTextColor(muted);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(status);
        scroll=new ScrollView(this);messages=column();scroll.addView(messages);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        suggestions=column();suggestions.setVisibility(View.GONE);body.addView(suggestions);
        attachmentRows=column();body.addView(attachmentRows);
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.BOTTOM);body.addView(composer);iconButton(composer,"사진·파일 첨부","plus",()->fileUi.choose(workspace,session));input=new EditText(this);input.setId(View.generateViewId());input.setContentDescription("메시지");input.setTextSize(17);input.setTypeface(regular);input.setTextColor(ink);input.setHintTextColor(muted);input.setHint("메시지를 입력하세요");input.setMinLines(1);input.setMaxLines(5);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8000)});composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        restoring=true;JSONArray entries=service.store.get(workspace).getJSONArray("sessions");int position=0;
        for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).getString("id").equals(session)){input.setText(entries.getJSONObject(i).optString("draft"));position=entries.getJSONObject(i).optInt("scrollY");}
        restoring=false;final int y=position;ScrollView current=scroll;current.post(()->current.scrollTo(0,y));
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(!restoring){completeCommand();ui.removeCallbacks(persistDraft);ui.postDelayed(persistDraft,400);}}public void afterTextChanged(Editable e){}});
        completeCommand();
        LinearLayout controls=new LinearLayout(this);LinearLayout.LayoutParams controlSpace=new LinearLayout.LayoutParams(-2,-2);controlSpace.setMarginStart(dp(8));composer.addView(controls,controlSpace);
        send=iconButton(controls,"보내기","send",this::send);
        stop=iconButton(controls,"응답 중단","stop",()->{final String target=session;service.submit(()->service.sessions.stop(target),e->{if(e!=null)toast("중단 요청에 실패했습니다");});});
        GradientDrawable sendSurface=new GradientDrawable();sendSurface.setCornerRadius(dp(24));sendSurface.setColor(states(surface,accent));send.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf((onAccent&0x00ffffff)|0x33000000),sendSurface,null));send.setImageTintList(states(muted,onAccent));refresh();
    }
    private void send(){if(sending)return;String value=input.getText().toString();if(value.trim().equals("/new")){input.setText("");newSession();return;}if(value.trim().equals("/resume")){input.setText("");resume();return;}if(value.trim().equals("/model")){input.setText("");chooseModel();return;}if(value.trim().isEmpty()){try{if(service.store.attachments(workspace,session).length()==0)return;}catch(Exception e){return;}}
        final JSONArray submittedAttachments;try{submittedAttachments=service.store.attachments(workspace,session);}catch(Exception e){toast("첨부를 읽지 못했습니다");return;}
        final String target=session,owner=workspace;final EditText submittedInput=input;sending=true;send.setEnabled(false);service.submit(()->{service.sendMessage(owner,target,value,submittedAttachments);JSONObject w=service.store.get(owner);JSONArray entries=w.getJSONArray("sessions");for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);if(entry.getString("id").equals(target)&&entry.optString("draft").equals(value))service.store.saveUi(owner,target,"",entry.optInt("scrollY"));}},e->{sending=false;if(e!=null){toast("MODEL_UNAVAILABLE".equals(e.getMessage())||"EFFORT_UNAVAILABLE".equals(e.getMessage())?"/model에서 사용할 수 있는 모델·추론 강도를 다시 선택해 주세요":"전송 실패 · 연결 상태와 실행 중인 대화를 확인해 주세요");}else if(target.equals(session)&&input==submittedInput&&input.getText().toString().equals(value)){input.setText("");dismissComposerKeyboard();}refresh();});
    }
    private void dismissComposerKeyboard(){
        android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(input.getWindowToken(),0);
        input.clearFocus();root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void refresh(){
        if(service==null)return;if(accountView!=null)accountView.setText(service.account);if(!screen.equals("chat")||messages==null)return;
        try{
            if(modelView!=null)modelView.setText(modelLabel());refreshAttachments();
            boolean busy=service.sessions.busy(session);send.setEnabled(!busy&&!sending&&!session.isEmpty());stop.setEnabled(busy);send.setVisibility(busy?View.GONE:View.VISIBLE);stop.setVisibility(busy?View.VISIBLE:View.GONE);
            String phase=service.sessions.status(session);status.setTextColor(phase.equals("failed")||phase.equals("error")||(phase.equals("disconnected")||phase.equals("unknown")||phase.equals("interrupted"))?errorColor:muted);status.setText(busy?(phase.equals("stopping")?"중단 중…":"응답 작성 중…"):(phase.equals("failed")||phase.equals("error")||(phase.equals("disconnected")||phase.equals("unknown")||phase.equals("interrupted"))?"중단되었거나 결과를 확인하지 못했습니다 · 기록을 확인한 뒤 다시 요청하세요":"메시지를 입력하세요"));
            if(!service.fileStatus(session).isEmpty())status.setText(service.fileStatus(session));
            boolean failed=phase.equals("failed")||phase.equals("error")||(phase.equals("disconnected")||phase.equals("unknown")||phase.equals("interrupted"));status.setVisibility(busy||failed?View.VISIBLE:View.GONE);
            JSONArray items=service.sessions.items(session);String stamp=items.toString();
            if(!stamp.equals(shownItems)){boolean bottom=scroll.getChildAt(0).getHeight()-scroll.getHeight()-scroll.getScrollY()<dp(80);messages.removeAllViews();
                for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);String type=item.optString("type"),value="";
                    if(type.equals("commandExecution")){commandCard(item);continue;}
                    if(type.equals("agentMessage"))value=item.optString("text");
                    else if(type.equals("userMessage")){JSONArray content=item.optJSONArray("content");if(content!=null)for(int j=0;j<content.length();j++)value+=content.getJSONObject(j).optString("text")+"\n";}

                    else if(type.equals("fileChange"))value="파일 변경 · "+item.optString("status");
                    else if(type.equals("imageGeneration"))value="이미지 생성 · "+item.optString("status")+(item.has("fileError")?"\n"+item.optString("fileError"):"");
                    else value=(item.optString("tool","도구"))+" · "+(item.optBoolean("success")?"완료":item.optString("status"));
                    TextView message=text(value.trim(),type.endsWith("Message")?17:14);message.setTextIsSelectable(true);message.setLineSpacing(0,1f);message.setLineHeight(Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,27,getResources().getDisplayMetrics())));message.setPadding(dp(12),dp(12),dp(12),dp(12));
                    if(type.equals("userMessage")){GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(dp(12));message.setBackground(shape);}messages.addView(message);
                    if(type.equals("imageGeneration")&&item.optJSONObject("workspaceFile")!=null){JSONObject file=item.getJSONObject("workspaceFile");String path=file.getString("path"),owner=workspace;button(messages,"이미지 · "+path,()->fileUi.actions(owner,path));}
                    if(type.equals("fileChange")){String owner=workspace;button(messages,"쓰레드 파일 보기",()->fileUi.browse(owner,""));}
                    if(type.equals("dynamicToolCall")&&item.optBoolean("success")){
                        JSONArray outputs=item.optJSONArray("contentItems");if(outputs!=null)for(int j=0;j<outputs.length();j++){try{JSONObject file=new JSONObject(outputs.getJSONObject(j).optString("text"));if(file.has("path")&&file.has("size")&&!file.optBoolean("directory")){String path=file.getString("path"),owner=workspace;button(messages,"파일 · "+path+" · "+FileUi.size(file.getLong("size")),()->fileUi.actions(owner,path));}}catch(Exception ignored){}}
                    }
                }shownItems=stamp;if(bottom)scroll.post(()->{if(scroll!=null)scroll.fullScroll(View.FOCUS_DOWN);});
            }
            AgentService.Question q=service.question(session);if(q==null&&approvalDialog!=null){approvalDialog.dismiss();approvalDialog=null;}if(q!=null&&!q.key.equals(shownQuestion)){shownQuestion=q.key;showQuestion(q);}
        }catch(Exception e){status.setText("대화 표시 오류 · 다시 열어 주세요");}
    }
    private void commandCard(JSONObject item)throws Exception{
        final String key=session+":"+item.getString("id"),owner=workspace;
        String state=item.optString("status");
        final String label="명령 실행 · "+(state.equals("completed")?"완료":state.equals("failed")?"실패":state.equals("declined")?"거절됨":state.equals("inProgress")?"실행 중":state);
        LinearLayout card=column(),details=column();messages.addView(card);
        Button toggle=button(card,"",()->{});toggle.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);toggle.setTextSize(14);
        card.addView(details);
        Runnable update=()->{
            boolean expanded=expandedCommands.contains(key);toggle.setText(label+(expanded?" · 접기":" · 펼치기"));
            toggle.setContentDescription(label+(expanded?", 펼쳐짐, 누르면 접기":", 접힘, 누르면 펼치기"));
            if(Build.VERSION.SDK_INT>=30)toggle.setStateDescription(expanded?"펼쳐짐":"접힘");
            details.removeAllViews();details.setVisibility(expanded?View.VISIBLE:View.GONE);
            if(expanded){
                String value=item.optString("command")+"\n\n"+item.optString("aggregatedOutput")+(item.has("exitCode")&&!item.isNull("exitCode")?"\n종료 코드: "+item.optInt("exitCode"):"");
                TextView output=text(value.trim(),14);output.setTextIsSelectable(true);output.setPadding(dp(12),dp(8),dp(12),dp(12));details.addView(output);
                button(details,"쓰레드 파일 보기",()->fileUi.browse(owner,""));
            }
        };
        toggle.setOnClickListener(v->{if(!expandedCommands.remove(key))expandedCommands.add(key);update.run();});update.run();
    }
    private void refreshAttachments()throws Exception{
        if(attachmentRows==null)return;JSONArray files=service.store.attachments(workspace,session);String stamp=files.toString();if(stamp.equals(shownAttachments))return;shownAttachments=stamp;attachmentRows.removeAllViews();
        if(files.length()==0){attachmentRows.setVisibility(View.GONE);return;}attachmentRows.setVisibility(View.VISIBLE);
        HorizontalScrollView strip=new HorizontalScrollView(this);LinearLayout row=new LinearLayout(this);strip.addView(row);attachmentRows.addView(strip);
        final String owner=workspace,target=session;
        for(int i=0;i<files.length();i++){JSONObject file=files.getJSONObject(i);String path=file.getString("path");
            Button chip=button(row,path+" · "+FileUi.size(file.getLong("size")),()->fileUi.actions(owner,path));chip.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(48)));chip.setMaxWidth(dp(220));chip.setSingleLine(true);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            Button remove=button(row,"×",()->{try{JSONArray current=service.store.attachments(owner,target),remaining=new JSONArray();for(int j=0;j<current.length();j++)if(!path.equals(current.getJSONObject(j).getString("path")))remaining.put(current.getJSONObject(j));service.store.attachments(owner,target,remaining);refresh();}catch(Exception e){toast("첨부를 제거하지 못했습니다");}});remove.setContentDescription(path+" 첨부 제거");remove.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);fileUi.result(request,result,data);}
    private void showQuestion(AgentService.Question q)throws Exception{
        if(q.approval){
            String detail=q.params.optString("reason","이 작업을 실행할까요?")+"\n\n"+q.params.optString("command",q.params.optString("grantRoot","파일 변경"))+"\n\n작업 위치: "+q.params.optString("cwd","")+"\n"+q.params.optString("changesPreview","");
            TextView content=text(detail,16);content.setTextIsSelectable(true);ScrollView view=new ScrollView(this);view.addView(content);
            AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("작업 실행 확인").setView(view).setNegativeButton("거절",(d,n)->approval(q,"decline")).setCancelable(false);
            JSONArray choices=q.params.optJSONArray("availableDecisions");boolean accept=choices==null; if(choices!=null)for(int i=0;i<choices.length();i++)if("accept".equals(choices.optString(i)))accept=true;
            if(accept)dialog.setPositiveButton("이번 작업 허용",(d,n)->approval(q,"accept"));approvalDialog=dialog.create();approvalDialog.setOnDismissListener(d->approvalDialog=null);approvalDialog.show();return;
        }
        LinearLayout fields=column();JSONArray questions=q.params.getJSONArray("questions");EditText[] answers=new EditText[questions.length()];
        for(int i=0;i<answers.length;i++){JSONObject item=questions.getJSONObject(i);fields.addView(text(item.optString("question"),17));JSONArray options=item.optJSONArray("options");if(options!=null){String choices="";for(int j=0;j<options.length();j++)choices+=options.getJSONObject(j).optString("label")+"  ";fields.addView(text(choices,14));}answers[i]=new EditText(this);answers[i].setHint("답변");fields.addView(answers[i]);}
        ScrollView form=new ScrollView(this);form.addView(fields);new AlertDialog.Builder(this).setTitle("입력이 필요합니다").setView(form).setPositiveButton("전달",(d,n)->{try{JSONObject out=new JSONObject();for(int i=0;i<answers.length;i++)out.put(questions.getJSONObject(i).getString("id"),new JSONObject().put("answers",new JSONArray().put(answers[i].getText().toString())));service.answer(q,new JSONObject().put("answers",out));}catch(Exception e){toast("답변 전달 실패");}}).setNegativeButton("취소",(d,n)->{try{service.answer(q,new JSONObject().put("answers",new JSONObject()));}catch(Exception ignored){}}).setCancelable(false).show();
    }
    private void approval(AgentService.Question q,String decision){try{service.answer(q,new JSONObject().put("decision",decision));}catch(Exception e){toast("응답을 전달하지 못했습니다");}}
    private void settings(){
        LinearLayout content=column();ScrollView settingsScroll=new ScrollView(this);settingsScroll.addView(content);body.addView(settingsScroll,new LinearLayout.LayoutParams(-1,-1));body=content;
        button(body,"‹ 쓰레드 목록",()->{screen="home";render();});body.addView(text("설정",22));accountView=text(service.account,17);body.addView(accountView);body.addView(text("셸 점검 · "+service.shellProbe,14));
        body.addView(text(service.backgroundRunning()?"백그라운드 에이전트 · 실행 중":"백그라운드 에이전트 · 꺼짐",17));
        body.addView(text("화면을 꺼도 작업을 이어갑니다. 서비스 종료 시 에이전트 연결을 닫으며, 이미 완료된 파일 작업은 유지됩니다.",14));
        button(body,service.backgroundRunning()?"백그라운드 서비스 종료":"백그라운드 서비스 시작",()->{
            if(service.backgroundRunning()){service.stopBackground();render();}
            else{startForegroundService(new Intent(this,AgentService.class).setAction(AgentLifetime.START));ui.postDelayed(this::render,300);}
        });
        android.os.PowerManager power=getSystemService(android.os.PowerManager.class);
        body.addView(text(power.isIgnoringBatteryOptimizations(getPackageName())?"배터리 최적화 · 제한 제외됨":"화면 꺼짐 중 작업을 위해 배터리 제한을 해제해 주세요",14));
        button(body,"배터리 사용 설정",()->{
            try{Intent intent=new Intent(power.isIgnoringBatteryOptimizations(getPackageName())?android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS:android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                if(!power.isIgnoringBatteryOptimizations(getPackageName()))intent.setData(android.net.Uri.parse("package:"+getPackageName()));startActivity(intent);
            }catch(Exception unavailable){toast("시스템 앱 설정에서 배터리 제한을 해제해 주세요");}
        });
        body.addView(text(getSystemService(NotificationManager.class).areNotificationsEnabled()?"작업 알림 · 허용됨":"작업 완료와 승인 요청 알림이 꺼져 있습니다",14));
        button(body,"알림 설정",()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())));
        button(body,"ChatGPT 로그인",()->login(false));button(body,"기기 코드로 로그인",()->login(true));
        button(body,"계정 새로고침",()->service.submit(service::refreshAccount,e->{if(e!=null)toast("계정 확인 실패");render();}));
        button(body,"로그아웃",()->service.submit(service::logout,e->{if(e!=null)toast("진행 중인 대화를 마친 뒤 다시 시도해 주세요");render();}));
        button(body,"진단 정보 복사",()->{String report="{\"appVersion\":\"0.9.0\",\"androidApi\":"+Build.VERSION.SDK_INT+",\"runtime\":\"0.156.1-termux.1\",\"shellProbe\":\""+service.shellProbe+"\"}";((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Android Agent 진단",report));toast("계정·대화 내용을 제외한 진단을 복사했습니다");});
    }
    private void login(boolean device){service.submit(()->service.login(device),e->{if(e!=null){toast("로그인을 시작하지 못했습니다");return;}if(!service.loginCode.isEmpty())new AlertDialog.Builder(this).setTitle("로그인 코드").setMessage(service.loginCode).setPositiveButton("복사",(d,n)->((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("로그인 코드",service.loginCode))).show();try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(service.loginUrl)));}catch(Exception ex){toast("브라우저를 열지 못했습니다");}});}
    private void rememberLocation(){if(!workspace.isEmpty())getSharedPreferences("navigation",0).edit().putString("workspace",workspace).putString("session",session).apply();}
    private void saveUi(){rememberLocation();ui.removeCallbacks(persistDraft);if(service==null||input==null||session.isEmpty())return;try{service.store.saveUi(workspace,session,input.getText().toString(),scroll==null?0:scroll.getScrollY());}catch(Exception e){toast("입력 초안을 저장하지 못했습니다");}}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_SHORT).show();}
    @Override protected void onSaveInstanceState(Bundle state){saveUi();state.putString("workspace",workspace);state.putString("session",session);state.putString("screen",screen);state.putStringArrayList("expandedCommands",new java.util.ArrayList<>(expandedCommands));fileUi.save(state);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){if(!screen.equals("home")){saveUi();screen="home";render();}else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(service!=null&&screen.equals("settings"))render();}
    @Override protected void onPause(){saveUi();super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacks(persistDraft);if(service!=null)service.unobserve(refresh);if(bound)unbindService(binding);super.onDestroy();}
}
