package br.com.experimento.autododge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.nio.ByteBuffer;

/**
 * Captures screen frames locally using Android's MediaProjection consent dialog.
 * Processes at most ~14 fps (throttled), never writes images to storage/network.
 */
public class CaptureService extends Service {
    public static final String START="capture.start", STOP="capture.stop";
    public static final String RESULT="capture.result", DATA="capture.data";
    public static volatile CaptureService instance;
    private static final String CHANNEL="capture_status";
    private final ProjectileDetector detector = new ProjectileDetector();
    private HandlerThread frameThread;
    private Handler frameHandler;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader imageReader;
    private DisplayManager displayManager;
    private int imageW,imageH,density;
    private volatile boolean enabled=false;
    private long lastFrame=0;
    private long lastModelSave=0, lastInfo=0;
    private DisplayManager.DisplayListener displayListener;

    @Override public void onCreate() {
        super.onCreate();
        instance=this;
        detector.importModel(Prefs.loadModel(this));
        NotificationManager manager=getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,"AutoDodge - captura",NotificationManager.IMPORTANCE_LOW));
        frameThread=new HandlerThread("AutododgeFrames");
        frameThread.start();
        frameHandler=new Handler(frameThread.getLooper());
        displayManager=(DisplayManager)getSystemService(DISPLAY_SERVICE);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null)return START_NOT_STICKY;
        if(STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if(!START.equals(intent.getAction()) || projection!=null)return START_NOT_STICKY;
        Notification note=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("AutoDodge Experimental")
                .setContentText("Captura de tela ativa • apenas análise local")
                .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)
            startForeground(1543,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1543,note);
        int code=intent.getIntExtra(RESULT,0);
        Intent data;
        if(Build.VERSION.SDK_INT>=33) data=intent.getParcelableExtra(DATA,Intent.class);
        else data=intent.getParcelableExtra(DATA);
        if(code==0 || data==null) { stopSelf();return START_NOT_STICKY; }
        MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        try {
            projection=manager.getMediaProjection(code,data);
            if(projection==null){stopSelf();return START_NOT_STICKY;}
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            },frameHandler);
            resize(true);
            displayListener=new DisplayManager.DisplayListener(){
                @Override public void onDisplayAdded(int id){}
                @Override public void onDisplayRemoved(int id){}
                @Override public void onDisplayChanged(int id){
                    if(id==android.view.Display.DEFAULT_DISPLAY)
                        frameHandler.post(()->resize(false));
                }
            };
            displayManager.registerDisplayListener(displayListener,frameHandler);
        } catch (Exception e) {stopSelf();}
        return START_NOT_STICKY;
    }

    public void setEnabled(boolean value) {
        enabled=value;
        if(!value)frameHandler.post(detector::reset);
        else frameHandler.post(()->{detector.reset();lastFrame=0;});
    }

    /** Called by optional overlay buttons: accurate manual examples improve learning. */
    public void feedback(boolean isProjectile) {
        if(frameHandler==null)return;
        frameHandler.post(()->{
            boolean ok=detector.feedback(isProjectile);
            if(ok)Prefs.saveModel(this,detector.exportModel());
            DodgeAccessibilityService a=DodgeAccessibilityService.instance;
            if(a!=null)a.onFeedbackResult(ok,isProjectile,detector.stats());
        });
    }

    private void resize(boolean isFirst) {
        if(projection==null)return;
        DisplayMetrics m=new DisplayMetrics();
        WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(m);
        int w=m.widthPixels,h=m.heightPixels;
        if(!isFirst && imageReader!=null && w==imageW && h==imageH)return;
        ImageReader old=imageReader;
        imageW=w;imageH=h;density=m.densityDpi;
        ImageReader reader=ImageReader.newInstance(imageW,imageH,PixelFormat.RGBA_8888,2);
        reader.setOnImageAvailableListener(this::readImage,frameHandler);
        imageReader=reader;
        if(isFirst) {
            display=projection.createVirtualDisplay("AD-screen",w,h,density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(),null,frameHandler);
        } else if(display!=null){
            display.resize(w,h,density);
            display.setSurface(reader.getSurface());
        }
        detector.reset();
        if(old!=null)old.close();
    }

    private void readImage(ImageReader reader) {
        Image frame=null;
        try {
            frame=reader.acquireLatestImage();
            if(frame==null || !enabled)return;
            DodgeAccessibilityService a=DodgeAccessibilityService.instance;
            if(a==null || !a.isGameOpen())return;
            long now=SystemClock.elapsedRealtime();
            if(now-lastFrame<70)return;
            lastFrame=now;
            int sw=frame.getWidth(),sh=frame.getHeight();
            if(sw<200 || sh<140)return;
            Image.Plane plane=frame.getPlanes()[0];
            int stride=plane.getRowStride();
            int pixelStride=plane.getPixelStride();
            ByteBuffer buf=plane.getBuffer();
            if(pixelStride<4)return;
            int[] pixels=new int[ProjectileDetector.W*ProjectileDetector.H];
            for(int y=0;y<ProjectileDetector.H;y++) {
                int sy=(y*sh)/ProjectileDetector.H;
                int row=sy*stride;
                for(int x=0;x<ProjectileDetector.W;x++) {
                    int sx=(x*sw)/ProjectileDetector.W;
                    int pos=row+sx*pixelStride;
                    if(pos+2>=buf.limit())continue;
                    int r=buf.get(pos)&255,g=buf.get(pos+1)&255,b=buf.get(pos+2)&255;
                    pixels[y*ProjectileDetector.W+x]=0xff000000|(r<<16)|(g<<8)|b;
                }
            }
            float px=Prefs.read(this,"px",.50f)*ProjectileDetector.W;
            float py=Prefs.read(this,"py",.52f)*ProjectileDetector.H;
            ProjectileDetector.Direction dodge=detector.analyze(pixels,px,py);
            if(dodge!=null)a.onThreat(dodge.dx,dodge.dy);
            if(now-lastInfo>1600){
                lastInfo=now;
                a.updateLearningStats(detector.stats());
            }
            if(now-lastModelSave>9500){
                lastModelSave=now;
                Prefs.saveModel(this,detector.exportModel());
            }
        } catch(Exception e){
            // A rotated/closed ImageReader may deliver an invalid old buffer; ignore.
        } finally {
            if(frame!=null)frame.close();
        }
    }

    @Override public void onDestroy(){
        enabled=false;
        Prefs.saveModel(this,detector.exportModel());
        if(displayManager!=null && displayListener!=null)
            displayManager.unregisterDisplayListener(displayListener);
        if(display!=null){display.release();display=null;}
        if(imageReader!=null){imageReader.close();imageReader=null;}
        if(projection!=null){
            MediaProjection old=projection;projection=null;
            try{old.stop();}catch(Exception ignored){}
        }
        if(frameThread!=null)frameThread.quitSafely();
        if(instance==this)instance=null;
        if(DodgeAccessibilityService.instance!=null)
            DodgeAccessibilityService.instance.onCaptureStopped();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
