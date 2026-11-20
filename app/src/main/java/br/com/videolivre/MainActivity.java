package br.com.videolivre;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.content.Context;
import android.content.ClipboardManager;
import android.net.Uri;
import android.graphics.Color;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import android.text.InputType;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import com.yausername.ffmpeg.FFmpeg;
import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  final int bg=Color.rgb(13,18,28), fg=Color.WHITE, accent=Color.rgb(90,235,189);
  final ExecutorService executor=Executors.newSingleThreadExecutor();
  final Handler ui=new Handler(Looper.getMainLooper());
  EditText field; TextView status; Button watch,save; ProgressBar spinner; VideoView player;
  volatile boolean busy=false, initialized=false, downloading=false;
  String current="",cachedFor=""; File cacheVideo; boolean retry=false;
  boolean saveWhenReady=false;
  static final int SAVE=1004;
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(fg);t.setTextSize(size);return t;}
  Button button(String title){Button b=new Button(this); b.setText(title);b.setAllCaps(false);return b;}
  @Override public void onCreate(Bundle state){super.onCreate(state); buildUI(); readIntent(getIntent());}
  @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);readIntent(i);}
  void buildUI(){
    ScrollView sv=new ScrollView(this);sv.setBackgroundColor(bg);
    LinearLayout root=new LinearLayout(this);root.setPadding(dp(18),dp(24),dp(18),dp(32));root.setOrientation(1);
    sv.addView(root);setContentView(sv);
    TextView title=label("Vídeo Livre",32);title.setTextColor(accent);root.addView(title);
    TextView sub=label("Vídeos públicos do Instagram e Facebook, sem conta nem aplicativos dessas redes.",14);root.addView(sub);
    gap(root,20);
    field=new EditText(this);field.setTextColor(fg);field.setHintTextColor(0xffaab5bd);field.setHint("Cole o link recebido");field.setTextSize(14);
    field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);field.setSingleLine(false);
    root.addView(field,new LinearLayout.LayoutParams(-1,dp(84)));
    LinearLayout row=new LinearLayout(this);row.setOrientation(0);root.addView(row);
    Button paste=button("Colar");watch=button("Assistir");row.addView(paste,new LinearLayout.LayoutParams(0,dp(54),1));row.addView(watch,new LinearLayout.LayoutParams(0,dp(54),2));
    paste.setOnClickListener(v->{ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(c!=null&&c.hasPrimaryClip())field.setText(c.getPrimaryClip().getItemAt(0).coerceToText(this));});
    watch.setOnClickListener(v->watch());
    gap(root,16);
    spinner=new ProgressBar(this);spinner.setVisibility(View.GONE);root.addView(spinner);
    status=label("Pronto para receber um link público.",14);root.addView(status);
    gap(root,12);
    player=new VideoView(this);player.setVisibility(View.GONE);root.addView(player,new LinearLayout.LayoutParams(-1,dp(330)));
    MediaController controller=new MediaController(this); controller.setAnchorView(player);player.setMediaController(controller);
    player.setOnPreparedListener(mp->{finish("Reproduzindo vídeo.");player.start();});
    player.setOnErrorListener((mp,a,b)->{if(!retry&&!downloading&&!current.isEmpty()){retry=true;download(current,false);}else finish("Este vídeo não pôde ser reproduzido.");return true;});
    save=button("Salvar vídeo");root.addView(save);save.setOnClickListener(v->save());
    Button update=button("Atualizar compatibilidade");root.addView(update);update.setOnClickListener(v->update());
    gap(root,16);
    root.addView(label("Somente vídeos acessíveis publicamente. A plataforma pode bloquear a reprodução de determinados links.",12));
  }
  void gap(LinearLayout root,int size){View v=new View(this);root.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
  String extract(String raw){
    if(raw==null)return null;
    Matcher m=Pattern.compile("https?://[^\\s<>\\\"']+",Pattern.CASE_INSENSITIVE).matcher(raw);
    while(m.find()){
      String s=m.group().replaceAll("[.,;!?)\\]}]+$","");
      try {
        URI uri=new URI(s);String host=uri.getHost();if(host==null||uri.getUserInfo()!=null)continue;
        host=host.toLowerCase(Locale.ROOT);
        if(host.equals("instagram.com")||host.endsWith(".instagram.com")||host.equals("facebook.com")||host.endsWith(".facebook.com")||host.equals("fb.watch"))return s;
      }catch(Exception ignored){}
    }
    return null;
  }
  void readIntent(Intent intent){
    if(intent==null)return;String data=null;
    if(Intent.ACTION_SEND.equals(intent.getAction()))data=intent.getStringExtra(Intent.EXTRA_TEXT);
    if(Intent.ACTION_VIEW.equals(intent.getAction())&&intent.getData()!=null)data=intent.getData().toString();
    String s=extract(data);if(s!=null){field.setText(s);ui.post(()->watch());}
  }
  void begin(String s){busy=true;watch.setEnabled(false);save.setEnabled(false);spinner.setVisibility(View.VISIBLE);status.setText(s);}
  void finish(String s){busy=false;watch.setEnabled(true);save.setEnabled(true);spinner.setVisibility(View.GONE);status.setText(s);}
  synchronized void init() throws Exception{
    if(initialized)return;
    YoutubeDL.getInstance().init(getApplicationContext());
    FFmpeg.getInstance().init(getApplicationContext());
    initialized=true;
  }
  void watch(){
    if(busy)return;
    String url=extract(field.getText().toString());
    if(url==null){status.setText("Cole um link do Instagram ou Facebook.");return;}
    if(!url.equals(current)){cacheVideo=null;cachedFor="";retry=false;player.stopPlayback();player.setVisibility(View.GONE);}
    current=url;
    if(cacheVideo!=null&&cacheVideo.isFile()&&url.equals(cachedFor)){playLocal(cacheVideo);return;}
    begin("Procurando vídeo público…");
    executor.execute(()->{
      try{
        init();
        YoutubeDLRequest req=new YoutubeDLRequest(url);req.addOption("--no-playlist");req.addOption("-f","best[ext=mp4]/best");
        VideoInfo info=YoutubeDL.getInstance().getInfo(req);
        String stream=info==null?null:info.getUrl();
        if(stream==null||!stream.startsWith("https://"))throw new Exception("Sem endereço direto.");
        ui.post(()->{
          if(!url.equals(current))return;
          status.setText("Abrindo vídeo…");
          player.setVisibility(View.VISIBLE);
          HashMap<String,String> h=new HashMap<>();
          h.put("User-Agent","Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
          h.put("Referer",url.contains("instagram")?"https://www.instagram.com/":"https://www.facebook.com/");
          player.setVideoURI(Uri.parse(stream),h);player.start();
        });
      }catch(Exception e){ui.post(()->{retry=true;download(url,false);});}
    });
  }
  void download(String url, boolean saveAfter){
    if(downloading)return;
    downloading=true;saveWhenReady=saveAfter;begin("Preparando vídeo temporário…");
    executor.execute(()->{
      try {
        init();
        File dir=new File(getCacheDir(),"videos");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Sem pasta temporária.");
        File[] old=dir.listFiles();if(old!=null)for(File f:old)f.delete();
        YoutubeDLRequest req=new YoutubeDLRequest(url);
        req.addOption("--no-playlist");req.addOption("--no-part");req.addOption("--no-mtime");
        req.addOption("-f","bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best");
        req.addOption("--merge-output-format","mp4");
        req.addOption("--recode-video","mp4");
        req.addOption("-o",new File(dir,"video.%(ext)s").getAbsolutePath());
        YoutubeDL.getInstance().execute(req);
        File[] found=dir.listFiles((d,n)->n.endsWith(".mp4"));if(found==null||found.length==0)throw new Exception("Vídeo indisponível.");
        File video=found[0];if(video.length()<1)throw new Exception("Arquivo vazio.");
        ui.post(()->{
          downloading=false;cacheVideo=video;cachedFor=url;
          finish("Vídeo obtido.");
          if(saveWhenReady)chooseDestination(); else playLocal(video);
        });
      }catch(Exception e){
        ui.post(()->{downloading=false;String msg=e.getMessage()==null?"Vídeo não disponível.":e.getMessage();
          if(msg.length()>110)msg=msg.substring(0,110);
          finish("Não foi possível acessar o vídeo sem login: "+msg);
        });
      }
    });
  }
  void playLocal(File file){player.setVisibility(View.VISIBLE);player.setVideoURI(Uri.fromFile(file));player.start();status.setText("Abrindo vídeo…");}
  void save(){
    if(busy)return;
    String url=extract(field.getText().toString());if(url==null){status.setText("Cole um link válido.");return;}
    current=url;
    if(cacheVideo!=null&&cacheVideo.isFile()&&url.equals(cachedFor))chooseDestination();
    else download(url,true);
  }
  void chooseDestination(){if(cacheVideo==null)return;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("video/mp4");intent.putExtra(Intent.EXTRA_TITLE,"VideoLivre-"+System.currentTimeMillis()+".mp4");startActivityForResult(intent,SAVE);
  }
  @Override protected void onActivityResult(int code,int result,Intent data){
    super.onActivityResult(code,result,data);
    if(code!=SAVE||result!=RESULT_OK||data==null||data.getData()==null||cacheVideo==null)return;
    Uri dest=data.getData();File source=cacheVideo;begin("Salvando vídeo…");
    executor.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(dest,"w")){
      if(out==null)throw new Exception("Destino não disponível.");
      byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)out.write(buf,0,n);
      ui.post(()->finish("Vídeo salvo."));
    }catch(Exception e){ui.post(()->finish("Erro ao salvar o vídeo."));}});
  }
  void update(){if(busy)return;begin("Atualizando mecanismo…");executor.execute(()->{
    try{init();YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(),YoutubeDL.UpdateChannel._STABLE);
      ui.post(()->finish("Atualização concluída."));}
    catch(Exception e){ui.post(()->finish("Falha na atualização. Verifique a conexão."));}
  });}
  @Override protected void onDestroy(){executor.shutdownNow();if(player!=null)player.stopPlayback();super.onDestroy();}
}
