import dev.androidagent.probe.*;
import org.commonmark.node.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.gfm.strikethrough.*;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DocumentFeaturesTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 interface Checked{void run()throws Exception;}
 static void reject(Checked task)throws Exception{try{task.run();throw new AssertionError("accepted unsafe operation");}catch(java.io.IOException expected){}}
 static boolean contains(Node node,Class<?> type){if(type.isInstance(node))return true;for(Node n=node.getFirstChild();n!=null;n=n.getNext())if(contains(n,type))return true;return false;}
 public static void main(String[] args)throws Exception{
  String text="# 제목\n\n**굵게** *기울임* ~~취소~~ [링크](https://example.com)\n\n> 인용\n\n1. 첫째\n2. 둘째\n\n| 항목 | 값 |\n| --- | ---: |\n| 가 | 1 |\n\n```java\n<script>not executable</script>\n```\n";
  Node doc=MarkdownDocument.parse(text);for(Class<?> type:new Class<?>[]{Heading.class,StrongEmphasis.class,Emphasis.class,Strikethrough.class,Link.class,BlockQuote.class,OrderedList.class,TableBlock.class,FencedCodeBlock.class})check(contains(doc,type));
  for(int end=1;end<text.length();end++)check(MarkdownDocument.parse(text.substring(0,end))!=null);
  check(MarkdownDocument.webLink("https://example.com/a?q=b"));check(!MarkdownDocument.webLink("javascript:alert(1)"));check(!MarkdownDocument.webLink("intent://foo"));check(!MarkdownDocument.webLink("https://user:password@example.com"));check(!MarkdownDocument.webLink("file:///data/data/auth.json"));
  check(PreviewPolicy.path(PreviewPolicy.url("문서/a b.html")).equals("문서/a b.html"));check(PreviewPolicy.linkedPath("docs/a.md","../photo.png").equals("photo.png"));
  reject(()->PreviewPolicy.path("https://workspace.invalid.evil/a"));reject(()->PreviewPolicy.path("https://workspace.invalid@evil/a"));reject(()->PreviewPolicy.path("https://workspace.invalid/%2e%2e/auth.json"));reject(()->PreviewPolicy.path("https://workspace.invalid/%2f..%2fauth.json"));reject(()->PreviewPolicy.path("file:///data/auth.json"));reject(()->PreviewPolicy.path("https://workspace.invalid:443/a"));reject(()->PreviewPolicy.linkedPath("docs/a.md","//evil/image"));
  Path root=Files.createTempDirectory("entry-delete-");WorkspaceFiles files=new WorkspaceFiles(root.resolve("workspaces"),root.resolve("cache"));WorkspaceStore store=new WorkspaceStore(root.resolve("state.json"));String a=store.create("a"),b=store.create("b");store.attach(a,"session");
  files.write(a,"dir/nested/file.md","keep?",false);files.write(a,"dir-other/keep.md","keep",false);files.write(b,"keep.md","other workspace",false);
  Path outside=root.resolve("outside");Files.createDirectory(outside);Files.write(outside.resolve("keep"),new byte[]{1});Files.createSymbolicLink(files.resolve(a,"dir/link"),outside);
  reject(()->files.deleteEntry(a,""));reject(()->files.deleteEntry(a,"dir/.."));reject(()->files.deleteEntry(a,"../"+b));reject(()->files.deleteEntry(a,outside.toString()));reject(()->files.deleteEntry(a,"dir/link"));
  store.appendAttachment(a,"session",new JSONObject().put("path","dir/nested/file.md"));store.appendAttachment(a,"session",new JSONObject().put("path","dir-other/keep.md"));
  store.markDeletedPath(a,"dir");files.deleteEntry(a,"dir");store.removeAttachmentPath(a,"dir");
  check(!Files.exists(files.resolve(a,"dir")));check(Files.exists(outside.resolve("keep")));check(files.read(b,"keep.md").equals("other workspace"));check(files.read(a,"dir-other/keep.md").equals("keep"));
  check(store.attachments(a,"session").length()==1);WorkspaceStore restored=new WorkspaceStore(root.resolve("state.json"));check(restored.deletedPath(a,"dir/nested/file.md"));check(!restored.deletedPath(a,"dir-other/keep.md"));
  check(files.deletionPaths(a,Arrays.asList("dir-other/keep.md","dir-other","dir-other")).equals(Arrays.asList("dir-other")));
  reject(()->files.deletionPaths(a,Arrays.asList("dir-other/keep.md","../escape")));check(Files.exists(files.resolve(a,"dir-other/keep.md")));
  reject(()->files.deletionPaths(a,Arrays.asList("dir-other/keep.md","")));reject(()->files.deletionPaths(a,Arrays.asList("dir-other/keep.md","missing")));
  files.deleteEntry(a,"dir-other/keep.md");check(Files.isDirectory(files.resolve(a,"dir-other")));reject(()->files.deleteEntry(a,"missing"));
  System.out.println("PASS Markdown AST/table/incomplete streams, URL and local-preview boundaries, file/folder deletion without following symlinks, root/escape denial, attachment cleanup and persistent deletion markers");
 }
}
