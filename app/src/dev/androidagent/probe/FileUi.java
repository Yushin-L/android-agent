package dev.androidagent.probe;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Picker/export UI. All content copies and image decoding run off the UI thread. */
final class FileUi {
    private final AgentActivity activity;
    private AgentService service;
    private final Runnable changed;
    private String owner="",session="",exportOwner="",exportPath="";
    private FileBrowser browser;private Bundle browserState;private int browserGeneration;
    private int pendingRequest;private Intent pendingData;
    private static final int PHOTOS=70,DOCUMENTS=71,EXPORT=72;
    FileUi(AgentActivity activity,Bundle saved,Runnable changed){this.activity=activity;this.changed=changed;if(saved!=null){browserState=saved.getBundle("fileBrowser");owner=saved.getString("fileOwner","");session=saved.getString("fileSession","");exportOwner=saved.getString("exportOwner","");exportPath=saved.getString("exportPath","");}}
    void bind(AgentService service){this.service=service;if(browserState!=null){Bundle restore=browserState;browserState=null;showBrowser(restore.getString("owner"),restore.getString("directory",""),restore);}if(pendingData!=null){Intent data=pendingData;pendingData=null;result(pendingRequest,Activity.RESULT_OK,data);}}
    void save(Bundle b){if(browser!=null&&browser.showing())b.putBundle("fileBrowser",browser.save());b.putString("fileOwner",owner);b.putString("fileSession",session);b.putString("exportOwner",exportOwner);b.putString("exportPath",exportPath);}
    private void toast(String value){if(!activity.isDestroyed())Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    void choose(String workspace,String target){
        new AlertDialog.Builder(activity).setTitle("첨부").setItems(new String[]{"갤러리에서 사진 선택","파일 선택"},(d,n)->{
            owner=workspace;session=target;Intent intent;
            if(n==0&&Build.VERSION.SDK_INT>=33){intent=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,Math.min(10,MediaStore.getPickImagesMaxLimit()));}
            else{intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(n==0?"image/*":"*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);}
            try{activity.startActivityForResult(intent,n==0?PHOTOS:DOCUMENTS);}catch(ActivityNotFoundException e){toast("파일 선택기를 열 수 없습니다");}
        }).show();
    }
    void result(int request,int result,Intent data){
        if(request!=PHOTOS&&request!=DOCUMENTS&&request!=EXPORT)return;
        if(result!=Activity.RESULT_OK||data==null)return;
        if(service==null){pendingRequest=request;pendingData=data;return;}
        if(request==EXPORT){Uri destination=data.getData();if(destination!=null)export(exportOwner,exportPath,destination,null);return;}
        final String workspace=owner,target=session;List<Uri> uris=new ArrayList<>();
        if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        if(uris.size()>10){toast("한 번에 최대 10개를 첨부할 수 있습니다");return;}
        AtomicBoolean cancelled=new AtomicBoolean();AlertDialog progress=progress("파일을 가져오는 중…",cancelled);
        service.submit(()->service.importFiles(workspace,target,uris,cancelled::get),e->{if(!activity.isDestroyed())progress.dismiss();if(activity.isDestroyed())return;if(e!=null)toast("가져오기를 완료하지 못했습니다. 파일당 50MB·최대 10개이며, 이미 가져온 파일은 유지됩니다.");changed.run();});
    }
    private AlertDialog progress(String label,AtomicBoolean cancelled){AlertDialog d=new AlertDialog.Builder(activity).setMessage(label).setNegativeButton("취소",(v,n)->cancelled.set(true)).setCancelable(false).create();d.show();return d;}
    void browse(String workspace,String directory){showBrowser(workspace,directory,null);}
    private void showBrowser(String workspace,String directory,Bundle saved){
        final int request=++browserGeneration;if(browser!=null)browser.close();
        try{browser=new FileBrowser(activity,service,workspace,directory,saved,(w,p)->preview(w,p,WorkspaceProvider.mime(p),()->request==browserGeneration&&browser!=null&&browser.showing()),this::actions);browser.show();}
        catch(Exception e){toast("쓰레드 파일을 열지 못했습니다");}
    }
    void destroy(){browserGeneration++;if(browser!=null)browser.close();}
    static String size(long bytes){return bytes<1024?bytes+" B":bytes>=1024*1024?String.format(Locale.ROOT,"%.1f MB",bytes/1048576.0):String.format(Locale.ROOT,"%.1f KB",bytes/1024.0);}
    void actions(String workspace,String path){
        String mime=WorkspaceProvider.mime(path);boolean media=mime.startsWith("image/")||mime.startsWith("video/");
        List<String> labels=new ArrayList<>(Arrays.asList("열기 / 미리보기","공유","다른 이름으로 저장","다운로드에 저장"));if(media)labels.add("갤러리에 저장");
        new AlertDialog.Builder(activity).setTitle(new File(path).getName()).setItems(labels.toArray(new String[0]),(d,n)->{
            if(n==0)preview(workspace,path,mime);else if(n==1)open(workspace,path,mime,true);else if(n==2){
                exportOwner=workspace;exportPath=path;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,new File(path).getName());try{activity.startActivityForResult(intent,EXPORT);}catch(ActivityNotFoundException e){toast("저장 위치 선택기를 열 수 없습니다");}
            }else export(workspace,path,null,n==4?(mime.startsWith("image/")?"image":"video"):"download");
        }).show();
    }
    private void open(String workspace,String path,String mime,boolean share){
        Uri uri=WorkspaceProvider.uri(workspace,path);Intent intent=new Intent(share?Intent.ACTION_SEND:Intent.ACTION_VIEW).setDataAndType(share?null:uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("파일",uri));if(share)intent.putExtra(Intent.EXTRA_STREAM,uri);
        try{activity.startActivity(Intent.createChooser(intent,share?"파일 공유":"파일 열기"));}catch(ActivityNotFoundException e){toast("이 파일을 열 수 있는 앱이 없습니다");}
    }
    private void preview(String workspace,String path,String mime){preview(workspace,path,mime,()->true);}
    private void preview(String workspace,String path,String mime,java.util.function.BooleanSupplier visible){
        boolean image=mime.startsWith("image/"),text=mime.startsWith("text/")||path.endsWith(".md")||path.endsWith(".json")||path.endsWith(".csv");if(!image&&!text){open(workspace,path,mime,false);return;}
        final Object[] value={null};service.submit(()->{service.store.get(workspace);Path file=service.files.resolve(workspace,path);if(image){android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.toString(),options);options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;options.inJustDecodeBounds=false;value[0]=android.graphics.BitmapFactory.decodeFile(file.toString(),options);if(value[0]==null)throw new IOException("PREVIEW_UNAVAILABLE");}else value[0]=service.files.read(workspace,path);},e->{
            if(activity.isDestroyed()||!visible.getAsBoolean())return;if(e!=null){toast("미리보기를 지원하지 않습니다. 다른 앱으로 엽니다.");open(workspace,path,mime,false);return;}View view;
            if(image){ImageView picture=new ImageView(activity);picture.setAdjustViewBounds(true);picture.setImageBitmap((android.graphics.Bitmap)value[0]);view=picture;}else{TextView content=new TextView(activity);content.setText((String)value[0]);content.setTextSize(17);content.setTextIsSelectable(true);content.setPadding(24,16,24,16);ScrollView scroll=new ScrollView(activity);scroll.addView(content);view=scroll;}
            new AlertDialog.Builder(activity).setTitle(new File(path).getName()).setView(view).setPositiveButton("닫기",null).setNeutralButton("다른 앱으로 열기",(d,n)->open(workspace,path,mime,false)).show();
        });
    }
    private void export(String workspace,String path,Uri chosen,String collection){
        AtomicBoolean cancelled=new AtomicBoolean();AlertDialog progress=progress("파일을 저장하는 중…",cancelled);
        service.submit(()->{
            service.store.get(workspace);Path source=service.files.resolve(workspace,path);ContentResolver resolver=activity.getContentResolver();Uri destination=chosen;boolean created=false;
            try{
                if(destination==null){Uri target;String location;if("image".equals(collection)){target=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;location=Environment.DIRECTORY_PICTURES+"/Android Agent";}else if("video".equals(collection)){target=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;location=Environment.DIRECTORY_MOVIES+"/Android Agent";}else{target=MediaStore.Downloads.EXTERNAL_CONTENT_URI;location=Environment.DIRECTORY_DOWNLOADS+"/Android Agent";}
                    ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,source.getFileName().toString());values.put(MediaStore.MediaColumns.MIME_TYPE,WorkspaceProvider.mime(path));values.put(MediaStore.MediaColumns.RELATIVE_PATH,location);values.put(MediaStore.MediaColumns.IS_PENDING,1);destination=resolver.insert(target,values);created=true;}
                if(destination==null)throw new IOException("EXPORT_FAILED");
                java.security.MessageDigest expected=java.security.MessageDigest.getInstance("SHA-256");
                try(InputStream in=Files.newInputStream(source);OutputStream out=resolver.openOutputStream(destination,"wt")){if(out==null)throw new IOException("EXPORT_FAILED");byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){if(cancelled.get())throw new IOException("CANCELLED");out.write(buffer,0,n);expected.update(buffer,0,n);}}
                if(cancelled.get())throw new IOException("CANCELLED");
                java.security.MessageDigest observed=java.security.MessageDigest.getInstance("SHA-256");try(InputStream verify=resolver.openInputStream(destination)){if(verify==null)throw new IOException("VERIFY_FAILED");byte[] buffer=new byte[32768];int n;while((n=verify.read(buffer))!=-1){if(cancelled.get())throw new IOException("CANCELLED");observed.update(buffer,0,n);}}
                if(!java.util.Arrays.equals(expected.digest(),observed.digest()))throw new IOException("VERIFY_FAILED");
                if(created){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.IS_PENDING,0);if(resolver.update(destination,values,null,null)!=1)throw new IOException("EXPORT_FAILED");}
            }catch(Exception e){if(destination!=null){try{if(created)resolver.delete(destination,null,null);else DocumentsContract.deleteDocument(resolver,destination);}catch(Exception ignored){}}throw e;}
        },e->{if(!activity.isDestroyed())progress.dismiss();toast(e==null?"폰에 파일을 저장했습니다":cancelled.get()?"저장을 취소했습니다":"저장하지 못했습니다. 저장 위치와 공간을 확인해 주세요");});
    }
}
