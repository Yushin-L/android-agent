package dev.androidagent.probe;

import android.app.*;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.File;
import java.util.*;

/** A single navigable workspace surface; late filesystem callbacks never reopen it. */
final class FileBrowser {
    interface Open {void open(String owner,String path);}
    private final AgentActivity activity;
    private final AgentService service;
    private final String owner;
    private final Open open,actions;
    private final Dialog dialog;
    private final LinearLayout crumbs;
    private final HorizontalScrollView trail;
    private final ListView list;
    private final EditText search;
    private final TextView summary,empty;
    private final Button retry;
    private final Rows adapter=new Rows();
    private final List<JSONObject> all=new ArrayList<>(),visible=new ArrayList<>();
    private final Map<String,Integer> positions=new HashMap<>();
    private String directory="",order="name";
    private int generation,restorePosition;
    private boolean closed,loading,loadError;
    private AlertDialog folderDialog;

    FileBrowser(AgentActivity activity,AgentService service,String owner,String path,Bundle saved,Open open,Open actions)throws Exception{
        this.activity=activity;this.service=service;this.owner=owner;this.open=open;this.actions=actions;
        directory=path;if(saved!=null){directory=saved.getString("directory",path);order=saved.getString("order","name");restorePosition=saved.getInt("position",0);}
        dialog=new Dialog(activity){@Override public void onBackPressed(){up();}};
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=column();root.setBackgroundColor(activity.fileColor("background"));root.setPadding(dp(16),dp(8),dp(16),dp(8));root.setFitsSystemWindows(true);
        LinearLayout toolbar=row();root.addView(toolbar);
        toolbar.addView(icon("back","상위 폴더 또는 대화로 돌아가기",this::up),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout heading=column();TextView title=text("쓰레드 파일",20,false);title.setAccessibilityHeading(true);heading.addView(title);
        TextView workspace=text(service.store.get(owner).getString("name"),13,true);workspace.setSingleLine(true);workspace.setEllipsize(TextUtils.TruncateAt.END);heading.addView(workspace);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(icon("refresh","현재 폴더 새로고침",()->load(false)),new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageButton more=icon("more","정렬 및 새 폴더",()->{});toolbar.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));more.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(activity,more);menu.getMenu().add(0,1,0,"새 폴더");
            menu.getMenu().add(1,2,1,"이름순").setCheckable(true).setChecked(order.equals("name"));
            menu.getMenu().add(1,3,2,"크기순 · 큰 파일부터").setCheckable(true).setChecked(order.equals("size"));
            menu.getMenu().setGroupCheckable(1,true,true);menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)newFolder();else{sort(item.getItemId()==2?"name":"size");}return true;});menu.show();
        });
        trail=new HorizontalScrollView(activity);trail.setHorizontalScrollBarEnabled(false);crumbs=row();trail.addView(crumbs);root.addView(trail,new LinearLayout.LayoutParams(-1,-2));
        search=new EditText(activity);search.setSingleLine(true);search.setTextSize(16);search.setTypeface(activity.fileFont());search.setTextColor(activity.fileColor("text"));search.setHintTextColor(activity.fileColor("muted"));search.setHint("현재 폴더에서 이름 검색");search.setContentDescription("현재 폴더에서 이름 검색");search.setMinHeight(dp(48));search.setPadding(dp(12),dp(8),dp(12),dp(8));search.setBackground(surface());root.addView(search,new LinearLayout.LayoutParams(-1,-2));
        summary=text("파일을 불러오는 중…",13,true);summary.setPadding(dp(8),dp(12),dp(8),dp(8));summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(summary);
        FrameLayout contents=new FrameLayout(activity);root.addView(contents,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(activity);list.setDivider(null);list.setAdapter(adapter);list.setClipToPadding(false);list.setPadding(0,0,0,dp(16));contents.addView(list,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout emptyBox=column();emptyBox.setGravity(Gravity.CENTER);emptyBox.setPadding(dp(24),dp(24),dp(24),dp(24));
        empty=text("",17,true);empty.setGravity(Gravity.CENTER);emptyBox.addView(empty);retry=new Button(activity);retry.setText("다시 불러오기");retry.setMinHeight(dp(48));retry.setOnClickListener(v->load(false));emptyBox.addView(retry);contents.addView(emptyBox,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(emptyBox);
        if(saved!=null)search.setText(saved.getString("query",""));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter();}public void afterTextChanged(Editable e){}});
        dialog.setContentView(root);dialog.setOnDismissListener(d->{closed=true;generation++;if(folderDialog!=null)folderDialog.dismiss();});
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    void reload(String workspace){if(owner.equals(workspace)&&showing())load(false);}
    void show(){dialog.show();Window window=dialog.getWindow();window.setBackgroundDrawable(new ColorDrawable(activity.fileColor("background")));window.setLayout(-1,-1);load(true);}
    boolean showing(){return !closed&&dialog.isShowing();}
    void close(){closed=true;generation++;if(folderDialog!=null)folderDialog.dismiss();dialog.dismiss();}
    Bundle save(){Bundle b=new Bundle();b.putString("owner",owner);b.putString("directory",directory);b.putString("order",order);b.putString("query",search.getText().toString());b.putInt("position",list.getFirstVisiblePosition());return b;}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String value,int size,boolean muted){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTypeface(activity.fileFont());t.setTextColor(activity.fileColor(muted?"muted":"text"));return t;}
    private Drawable surface(){GradientDrawable d=new GradientDrawable();d.setColor(activity.fileColor("surface"));d.setCornerRadius(dp(12));return d;}
    private void touch(View view){android.util.TypedValue v=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,v,true);view.setBackgroundResource(v.resourceId);}
    private ImageButton icon(String name,String label,Runnable action){ImageButton b=new ImageButton(activity);b.setImageDrawable(new Glyph(name,activity.fileColor("accent")));b.setPadding(dp(12),dp(12),dp(12),dp(12));b.setContentDescription(label);b.setTooltipText(label);touch(b);b.setOnClickListener(v->action.run());return b;}
    private void up(){if(directory.isEmpty())close();else{int slash=directory.lastIndexOf('/');navigate(slash<0?"":directory.substring(0,slash));}}
    private void navigate(String path){positions.put(directory,list.getFirstVisiblePosition());directory=path;search.setText("");restorePosition=positions.getOrDefault(path,0);load(true);}
    private void breadcrumbs(){crumbs.removeAllViews();addCrumb("파일 홈","");String path="";if(!directory.isEmpty())for(String part:directory.split("/")){TextView separator=text("›",17,true);separator.setPadding(dp(4),0,dp(4),0);crumbs.addView(separator);path=path.isEmpty()?part:path+"/"+part;addCrumb(part,path);}trail.post(()->trail.fullScroll(View.FOCUS_RIGHT));}
    private void addCrumb(String label,String path){Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTypeface(activity.fileFont());b.setTextSize(14);b.setTextColor(activity.fileColor(path.equals(directory)?"text":"accent"));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxWidth(dp(180));b.setSingleLine(true);b.setEllipsize(TextUtils.TruncateAt.MIDDLE);touch(b);b.setContentDescription(label+(path.equals(directory)?" · 현재 폴더":" · 폴더로 이동"));b.setOnClickListener(v->{if(!path.equals(directory))navigate(path);});crumbs.addView(b);}
    private void load(boolean restore){
        if(closed)return;final int request=++generation;final String path=directory;int position=restore?restorePosition:list.getFirstVisiblePosition();
        loading=true;loadError=false;all.clear();filter();empty.setText("폴더를 불러오는 중…");retry.setVisibility(View.GONE);breadcrumbs();
        final JSONArray[] result={null};service.submit(()->{service.store.get(owner);result[0]=service.files.list(owner,path);},error->{
            if(closed||activity.isDestroyed()||request!=generation)return;loading=false;
            if(error!=null){loadError=true;summary.setText("폴더를 읽지 못했습니다");empty.setText("폴더가 이동되었거나 삭제되었을 수 있습니다.\n상위 폴더로 이동하거나 다시 불러오세요.");retry.setVisibility(View.VISIBLE);return;}
            for(int i=0;i<result[0].length();i++)all.add(result[0].optJSONObject(i));filter();list.setSelection(position);
        });
    }
    private void sort(String value){order=value;filter();list.setSelection(0);}
    private void filter(){
        if(loadError)return;
        visible.clear();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int folders=0,files=0;
        for(JSONObject file:all)if(new File(file.optString("path")).getName().toLowerCase(Locale.ROOT).contains(query)){visible.add(file);if(file.optBoolean("directory"))folders++;else files++;}
        final java.text.Collator collator=java.text.Collator.getInstance(Locale.KOREAN);
        visible.sort((a,b)->{boolean ad=a.optBoolean("directory"),bd=b.optBoolean("directory");if(ad!=bd)return ad?-1:1;
            if(order.equals("size")&&!ad){int c=Long.compare(b.optLong("size"),a.optLong("size"));if(c!=0)return c;}
            return collator.compare(new File(a.optString("path")).getName(),new File(b.optString("path")).getName());});
        adapter.notifyDataSetChanged();
        summary.setText(loading?"파일을 불러오는 중…":"폴더 "+folders+" · 파일 "+files+"  ·  "+(order.equals("name")?"이름순":"크기순")+(all.size()>=1000?" · 최대 1,000개 표시":""));
        empty.setText(loading?"폴더를 불러오는 중…":query.isEmpty()?"아직 파일이 없습니다\n이 폴더에 저장한 파일이 여기에 나타납니다":"일치하는 이름이 없습니다");retry.setVisibility(View.GONE);
    }
    private void newFolder(){
        final String parent=directory;EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("폴더 이름");name.setContentDescription("새 폴더 이름");name.setTextSize(17);name.setTypeface(activity.fileFont());
        LinearLayout form=column();form.setPadding(dp(24),dp(8),dp(24),0);form.addView(name);
        AlertDialog create=new AlertDialog.Builder(activity).setTitle("새 폴더").setView(form).setNegativeButton("취소",null).setPositiveButton("만들기",null).create();folderDialog=create;create.show();
        create.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=name.getText().toString().trim();
            if(value.isEmpty()||value.equals(".")||value.equals("..")||!WorkspaceFiles.safeName(value).equals(value)){name.setError("경로 구분자 없이 짧은 폴더 이름을 입력하세요");return;}
            create.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);String path=parent.isEmpty()?value:parent+"/"+value;
            service.submit(()->{service.store.get(owner);java.nio.file.Files.createDirectory(service.files.resolve(owner,path));},error->{
                if(closed||activity.isDestroyed()||!create.isShowing()){create.dismiss();return;}if(error!=null){name.setError("같은 이름이 있거나 폴더를 만들 수 없습니다");create.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);}else{create.dismiss();if(directory.equals(parent))load(false);}
            });
        });
    }
    private final class Rows extends BaseAdapter {
        public int getCount(){return visible.size();}public Object getItem(int n){return visible.get(n);}public long getItemId(int n){return n;}
        public View getView(int n,View recycled,ViewGroup parent){
            Holder h;if(recycled==null){h=new Holder();recycled=h.row;recycled.setTag(h);}else h=(Holder)recycled.getTag();
            JSONObject file=visible.get(n);String path=file.optString("path"),name=new File(path).getName();boolean folder=file.optBoolean("directory");String mime=WorkspaceProvider.mime(path);
            String kind=folder?"폴더":mime.startsWith("image/")?"이미지":mime.startsWith("video/")?"동영상":mime.startsWith("audio/")?"오디오":name.lastIndexOf('.')>0?name.substring(name.lastIndexOf('.')+1).toUpperCase(Locale.ROOT)+" 파일":"파일";
            h.name.setText(name);h.detail.setText(folder?"폴더":kind+" · "+FileUi.size(file.optLong("size")));
            h.picture.setImageDrawable(new Glyph(folder?"folder":mime.startsWith("image/")?"image":"file",activity.fileColor(folder?"accent":"muted")));
            h.primary.setContentDescription(name+" · "+h.detail.getText());h.primary.setOnClickListener(v->{if(folder)navigate(path);else open.open(owner,path);});
            h.menu.setVisibility(View.VISIBLE);h.menu.setContentDescription(name+(folder?" · 폴더 메뉴":" · 저장·공유·삭제"));h.menu.setOnClickListener(v->actions.open(owner,path));return recycled;
        }
    }
    private final class Holder {
        final LinearLayout row=FileBrowser.this.row();
        final LinearLayout primary=new LinearLayout(activity){@Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}};
        final TextView name=text("",17,false),detail=text("",13,true);
        final ImageView picture=new ImageView(activity);
        final ImageButton menu=icon("more","파일 메뉴",()->{});
        Holder(){primary.setGravity(Gravity.CENTER_VERTICAL);primary.setMinimumHeight(dp(80));primary.setPadding(dp(8),dp(12),dp(8),dp(12));primary.setFocusable(true);touch(primary);
            picture.setPadding(dp(8),dp(8),dp(8),dp(8));picture.setBackground(surface());picture.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);primary.addView(picture,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout labels=column();labels.setPadding(dp(12),0,0,0);labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);name.setLineSpacing(dp(3),1);labels.addView(name);detail.setPadding(0,dp(4),0,0);detail.setSingleLine(true);detail.setEllipsize(TextUtils.TruncateAt.END);labels.addView(detail);primary.addView(labels,new LinearLayout.LayoutParams(0,-2,1));row.addView(primary,new LinearLayout.LayoutParams(0,-2,1));row.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        }
    }
    private static final class Glyph extends Drawable {
        private final String kind;private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(String kind,int color){this.kind=kind;pen.setColor(color);pen.setStrokeWidth(1.7f);pen.setStyle(Paint.Style.STROKE);pen.setStrokeCap(Paint.Cap.ROUND);pen.setStrokeJoin(Paint.Join.ROUND);}
        public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);Path p=new Path();
            if(kind.equals("folder")){p.moveTo(3,6);p.lineTo(9,6);p.lineTo(11,8);p.lineTo(21,8);p.lineTo(21,19);p.lineTo(3,19);p.close();c.drawPath(p,pen);}
            else if(kind.equals("back")){c.drawLine(19,12,5,12,pen);c.drawLine(5,12,11,6,pen);c.drawLine(5,12,11,18,pen);}
            else if(kind.equals("more")){for(int y=5;y<=19;y+=7)c.drawCircle(12,y,0.8f,pen);}
            else if(kind.equals("refresh")){c.drawArc(4,4,20,20,45,290,false,pen);c.drawLine(20,4,20,10,pen);c.drawLine(14,10,20,10,pen);}
            else if(kind.equals("image")){c.drawRoundRect(3,3,21,21,2,2,pen);c.drawCircle(8,8,1.5f,pen);p.moveTo(4,18);p.lineTo(10,12);p.lineTo(14,16);p.lineTo(17,12);p.lineTo(21,16);c.drawPath(p,pen);}
            else{p.moveTo(5,3);p.lineTo(14,3);p.lineTo(19,8);p.lineTo(19,21);p.lineTo(5,21);p.close();c.drawPath(p,pen);c.drawLine(14,3,14,8,pen);c.drawLine(14,8,19,8,pen);c.drawLine(8,12,16,12,pen);c.drawLine(8,16,14,16,pen);}c.restore();}
        public void setAlpha(int a){pen.setAlpha(a);}public void setColorFilter(ColorFilter f){pen.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
