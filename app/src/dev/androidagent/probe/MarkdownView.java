package dev.androidagent.probe;

import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.text.style.*;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.widget.*;
import org.commonmark.node.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import java.util.concurrent.*;
import java.util.function.*;

/** Native selectable Markdown. No HTML or scripts are executed in chat. */
final class MarkdownView extends LinearLayout {
    private static final ExecutorService PARSER=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"markdown-parser");t.setDaemon(true);return t;});
    private final AgentActivity activity;
    private final Consumer<String> link;
    private final BooleanSupplier keepBottom;
    private final Runnable layoutChanged;
    private String source="";
    private int generation;
    private Future<?> pending;
    MarkdownView(AgentActivity activity,Consumer<String> link,BooleanSupplier keepBottom,Runnable layoutChanged){
        super(activity);this.activity=activity;this.link=link;this.keepBottom=keepBottom;this.layoutChanged=layoutChanged;setOrientation(VERTICAL);setPadding(dp(12),dp(8),dp(12),dp(8));
    }
    void release(){generation++;if(pending!=null)pending.cancel(false);}
    void setMarkdown(String text){
        if(text.equals(source)&&getChildCount()>0)return;source=text;final int request=++generation;if(pending!=null)pending.cancel(false);
        if(text.length()>MarkdownDocument.RENDER_LIMIT){removeAllViews();TextView hint=label(13);hint.setText("큰 문서는 원문으로 표시합니다");addView(hint);TextView raw=label(17);raw.setText(text);addView(raw);return;}
        pending=PARSER.submit(()->{Node document;try{document=MarkdownDocument.parse(text);}catch(Exception e){document=null;}final Node parsed=document;
            activity.runOnUiThread(()->{if(activity.isDestroyed()||request!=generation)return;boolean follow=keepBottom.getAsBoolean();removeAllViews();
                if(parsed==null){TextView raw=label(17);raw.setText(text);addView(raw);}else blocks(parsed,this,0);
                if(follow)layoutChanged.run();
            });
        });
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(int size){TextView t=new TextView(activity);t.setTextSize(size);t.setTypeface(activity.fileFont());t.setTextColor(activity.fileColor("text"));t.setLinkTextColor(activity.fileColor("accent"));t.setTextIsSelectable(true);t.setLineSpacing(0,1.35f);t.setPadding(0,dp(4),0,dp(6));return t;}
    private GradientDrawable surface(){GradientDrawable bg=new GradientDrawable();bg.setColor(activity.fileColor("surface"));bg.setCornerRadius(dp(10));return bg;}
    private void textBlock(Node node,LinearLayout target,int size,boolean bold){TextView text=label(size);text.setText(inline(node));if(bold){text.setTypeface(activity.fileFont(),Typeface.BOLD);text.setAccessibilityHeading(true);}text.setMovementMethod(LinkMovementMethod.getInstance());target.addView(text,new LinearLayout.LayoutParams(-1,-2));}
    private void blocks(Node parent,LinearLayout target,int depth){
        if(depth>32){TextView t=label(17);t.setText("중첩이 깊은 내용은 원문에서 확인하세요");target.addView(t);return;}
        for(Node n=parent.getFirstChild();n!=null;n=n.getNext()){
            if(n instanceof Heading)textBlock(n,target,Math.max(18,28-((Heading)n).getLevel()*2),true);
            else if(n instanceof Paragraph)textBlock(n,target,17,false);
            else if(n instanceof FencedCodeBlock)code(((FencedCodeBlock)n).getLiteral(),((FencedCodeBlock)n).getInfo(),target);
            else if(n instanceof IndentedCodeBlock)code(((IndentedCodeBlock)n).getLiteral(),"",target);
            else if(n instanceof BulletList||n instanceof OrderedList){int number=n instanceof OrderedList?((OrderedList)n).getMarkerStartNumber():0;
                for(Node item=n.getFirstChild();item!=null;item=item.getNext()){
                    LinearLayout row=new LinearLayout(activity);TextView bullet=label(17);bullet.setText(n instanceof OrderedList?(number++)+".":"•");bullet.setMinWidth(dp(28));row.addView(bullet);
                    LinearLayout body=new LinearLayout(activity);body.setOrientation(VERTICAL);row.addView(body,new LinearLayout.LayoutParams(0,-2,1));target.addView(row,new LinearLayout.LayoutParams(-1,-2));blocks(item,body,depth+1);
                }
            }else if(n instanceof BlockQuote){LinearLayout row=new LinearLayout(activity);View bar=new View(activity);bar.setBackgroundColor(activity.fileColor("accent"));row.addView(bar,new LinearLayout.LayoutParams(dp(3),-1));LinearLayout body=new LinearLayout(activity);body.setOrientation(VERTICAL);body.setPadding(dp(12),dp(4),0,dp(4));row.addView(body,new LinearLayout.LayoutParams(0,-2,1));target.addView(row);blocks(n,body,depth+1);}
            else if(n instanceof TableBlock)table(n,target);
            else if(n instanceof ThematicBreak){View line=new View(activity);line.setBackgroundColor(activity.fileColor("muted"));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(0,dp(12),0,dp(12));target.addView(line,p);}
            else if(n instanceof HtmlBlock){TextView t=label(15);t.setText(((HtmlBlock)n).getLiteral());target.addView(t);}
            else blocks(n,target,depth+1);
        }
    }
    private void code(String value,String language,LinearLayout target){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(VERTICAL);box.setPadding(dp(12),dp(4),dp(12),dp(8));box.setBackground(surface());LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView type=label(13);type.setText(language.isEmpty()?"코드":language);header.addView(type,new LinearLayout.LayoutParams(0,-2,1));Button copy=new Button(activity);copy.setText("복사");copy.setTextSize(14);copy.setMinHeight(dp(48));copy.setContentDescription("코드 복사");copy.setOnClickListener(v->{((android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("코드",value));Toast.makeText(activity,"코드를 복사했습니다",Toast.LENGTH_SHORT).show();});header.addView(copy);box.addView(header);
        HorizontalScrollView scroller=new HorizontalScrollView(activity);TextView text=label(15);text.setTypeface(Typeface.MONOSPACE);text.setText(value);scroller.addView(text);box.addView(scroller);target.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }
    private void table(Node node,LinearLayout target){
        HorizontalScrollView scroller=new HorizontalScrollView(activity);TableLayout grid=new TableLayout(activity);scroller.addView(grid);
        for(Node section=node.getFirstChild();section!=null;section=section.getNext())for(Node row=section.getFirstChild();row!=null;row=row.getNext()){
            android.widget.TableRow tr=new android.widget.TableRow(activity);for(Node cell=row.getFirstChild();cell!=null;cell=cell.getNext()){
                TextView t=label(16);t.setPadding(dp(12),dp(10),dp(12),dp(10));t.setMaxWidth(dp(260));t.setMinWidth(dp(88));t.setText(inline(cell));t.setMovementMethod(LinkMovementMethod.getInstance());
                if(section instanceof TableHead){t.setTypeface(activity.fileFont(),Typeface.BOLD);t.setBackgroundColor(activity.fileColor("surface"));}
                if(cell instanceof TableCell){TableCell.Alignment alignment=((TableCell)cell).getAlignment();if(alignment==TableCell.Alignment.RIGHT)t.setGravity(Gravity.END);else if(alignment==TableCell.Alignment.CENTER)t.setGravity(Gravity.CENTER);}
                tr.addView(t,new android.widget.TableRow.LayoutParams(-2,-2));
            }grid.addView(tr);View divider=new View(activity);divider.setBackgroundColor(activity.fileColor("surface"));grid.addView(divider,new TableLayout.LayoutParams(-1,dp(1)));
        }target.addView(scroller,new LinearLayout.LayoutParams(-1,-2));
    }
    private SpannableStringBuilder inline(Node node){SpannableStringBuilder out=new SpannableStringBuilder();children(node,out,0);return out;}
    private void children(Node node,SpannableStringBuilder out,int depth){if(depth>64)return;for(Node n=node.getFirstChild();n!=null;n=n.getNext()){
        int start=out.length();
        if(n instanceof Text)out.append(((Text)n).getLiteral());
        else if(n instanceof Code){out.append(((Code)n).getLiteral());span(out,new TypefaceSpan("monospace"),start);span(out,new BackgroundColorSpan(activity.fileColor("surface")),start);}
        else if(n instanceof SoftLineBreak||n instanceof HardLineBreak)out.append('\n');
        else if(n instanceof HtmlInline)out.append(((HtmlInline)n).getLiteral());
        else {if(n instanceof Image)out.append("이미지: ");children(n,out,depth+1);
            if(n instanceof StrongEmphasis)span(out,new StyleSpan(Typeface.BOLD),start);
            else if(n instanceof Emphasis)span(out,new StyleSpan(Typeface.ITALIC),start);
            else if(n instanceof Strikethrough)span(out,new StrikethroughSpan(),start);
            else if(n instanceof Link||n instanceof Image){String url=n instanceof Link?((Link)n).getDestination():((Image)n).getDestination();span(out,new ClickableSpan(){public void onClick(View widget){link.accept(url);}},start);}
        }
    }}
    private void span(SpannableStringBuilder value,Object span,int start){if(value.length()>start)value.setSpan(span,start,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
}
