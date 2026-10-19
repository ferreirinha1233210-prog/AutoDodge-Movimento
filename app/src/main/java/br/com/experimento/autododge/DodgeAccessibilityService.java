package br.com.experimento.autododge;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** Overlay and touch gesture injector. No game memory inspection or hooking. */
public class DodgeAccessibilityService extends AccessibilityService {
    public static DodgeAccessibilityService instance;
    private static final String GAME = "com.supercell.brawlstars";
    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager window;
    private View overlay;
    private CheckBox autoCheck;
    private TextView stateText;
    private TextView learningText;
    private boolean enabled=false;
    private boolean gameOpen=false;
    private long lastGesture=0;

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        instance=this;
        window=(WindowManager)getSystemService(Context.WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
           event.getEventType()!=AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        if(event.getPackageName()==null) return;
        String pkg=event.getPackageName().toString();
        if(pkg.equals(getPackageName()) || pkg.equals("com.android.systemui")) return;
        if(pkg.equals(GAME)) {
            gameOpen=true;
            showOverlay();
        } else {
            gameOpen=false;
            hideOverlay();
        }
    }

    @Override public void onInterrupt() { disable(); }

    private void showOverlay() {
        if(overlay!=null || !Settings.canDrawOverlays(this))return;
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(10),dp(5),dp(10),dp(5));
        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xd8212430);
        bg.setCornerRadius(dp(12));
        panel.setBackground(bg);
        autoCheck=new CheckBox(this);
        autoCheck.setText("AutoDodge");
        autoCheck.setTextSize(16);
        autoCheck.setTextColor(Color.WHITE);
        panel.addView(autoCheck);
        stateText=new TextView(this);
        stateText.setText("Desligado");
        stateText.setTextColor(0xffdfdfdf);
        stateText.setTextSize(11);
        panel.addView(stateText);
        learningText=new TextView(this);
        learningText.setText("Treinamento local");
        learningText.setTextSize(10);
        learningText.setTextColor(0xffe7e7e7);
        panel.addView(learningText);
        // Optional correction buttons: hidden by default to preserve game space.
        Button toggle=new Button(this);
        toggle.setAllCaps(false);toggle.setText("Ensinar ▾");toggle.setTextSize(11);
        panel.addView(toggle);
        LinearLayout trainingRow=new LinearLayout(this);
        trainingRow.setOrientation(LinearLayout.HORIZONTAL);
        Button yes=new Button(this);yes.setText("✓ É tiro");yes.setTextSize(10);yes.setAllCaps(false);
        Button no=new Button(this);no.setText("✕ Não é");no.setTextSize(10);no.setAllCaps(false);
        trainingRow.addView(yes);trainingRow.addView(no);
        trainingRow.setVisibility(View.GONE);panel.addView(trainingRow);
        toggle.setOnClickListener(v -> {
            boolean shown=trainingRow.getVisibility()==View.VISIBLE;
            trainingRow.setVisibility(shown?View.GONE:View.VISIBLE);
            toggle.setText(shown?"Ensinar ▾":"Ocultar ▴");
        });
        yes.setOnClickListener(v -> {
            CaptureService s=CaptureService.instance;
            if(s!=null && enabled)s.feedback(true);
            else Toast.makeText(this,"Ative o AutoDodge",Toast.LENGTH_SHORT).show();
        });
        no.setOnClickListener(v -> {
            CaptureService s=CaptureService.instance;
            if(s!=null && enabled)s.feedback(false);
            else Toast.makeText(this,"Ative o AutoDodge",Toast.LENGTH_SHORT).show();
        });
        autoCheck.setOnCheckedChangeListener((view, checked)-> {
            if(checked && CaptureService.instance==null) {
                autoCheck.setChecked(false);
                Toast.makeText(this,"Abra o app e inicie a captura primeiro",Toast.LENGTH_LONG).show();
                return;
            }
            enabled=checked;
            CaptureService s=CaptureService.instance;
            if(s!=null)s.setEnabled(checked);
            if(stateText!=null)stateText.setText(checked?"Analisando a tela...":"Desligado");
        });
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        p.gravity=Gravity.TOP|Gravity.LEFT;
        p.x=dp(6);p.y=dp(40);
        try {
            window.addView(panel,p);
            overlay=panel;
        } catch(Exception ex) {overlay=null;}
    }
    private void hideOverlay() {
        enabled=false;
        CaptureService s=CaptureService.instance;
        if(s!=null)s.setEnabled(false);
        if(overlay!=null) {
            try{window.removeView(overlay);}catch(Exception ignore){}
            overlay=null;
            autoCheck=null;
            stateText=null;
            learningText=null;
        }
    }
    public void disable(){
        enabled=false;
        if(CaptureService.instance!=null)CaptureService.instance.setEnabled(false);
        if(autoCheck!=null)autoCheck.setChecked(false);
        hideOverlay();
    }
    public void onCaptureStopped() {
        main.post(()->{
            enabled=false;
            if(autoCheck!=null)autoCheck.setChecked(false);
        });
    }

    public void updateLearningStats(String stats){
        main.post(()->{if(learningText!=null)learningText.setText(stats);});
    }
    public void onFeedbackResult(boolean success,boolean yes,String stats){
        main.post(()->{
            if(learningText!=null)learningText.setText(stats);
            Toast.makeText(this,success
                ? (yes?"Exemplo marcado como tiro":"Exemplo marcado como outro efeito")
                : "Nenhum objeto recente para ensinar",Toast.LENGTH_SHORT).show();
        });
    }
    public void onThreat(float dx,float dy) {
        main.post(() -> {
            if(!enabled || !gameOpen || CaptureService.instance==null) return;
            long time=SystemClock.elapsedRealtime();
            if(time-lastGesture<380)return;
            lastGesture=time;
            DisplayMetrics m=new DisplayMetrics();
            window.getDefaultDisplay().getRealMetrics(m);
            float w=m.widthPixels, h=m.heightPixels;
            float ax=Prefs.read(this,"jx",.16f)*w;
            float ay=Prefs.read(this,"jy",.77f)*h;
            float norm=(float)Math.hypot(dx,dy);
            if(norm<.001f)return;
            float radius=Math.min(w,h)*.080f;
            float tx=ax+(dx/norm)*radius,ty=ay+(dy/norm)*radius;
            Path path=new Path();
            path.moveTo(ax,ay);
            path.lineTo(tx,ty);
            path.lineTo(tx+.01f,ty+.01f);
            GestureDescription.Builder builder=new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path,0,300));
            dispatchGesture(builder.build(),null,null);
            if(stateText!=null)stateText.setText("Possível tiro • desvio acionado");
        });
    }
    public boolean isGameOpen(){return gameOpen;}
    @Override public void onDestroy(){
        hideOverlay();
        if(instance==this)instance=null;
        super.onDestroy();
    }
    private int dp(int px){return Math.round(px*getResources().getDisplayMetrics().density);}
}
