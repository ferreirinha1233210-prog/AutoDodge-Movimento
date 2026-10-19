package br.com.experimento.autododge;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int RC_CAPTURE = 101;
    private static final String GAME = "com.supercell.brawlstars";
    private LinearLayout layout;
    private MediaProjectionManager projectionManager;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        ScrollView scroll = new ScrollView(this);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(22), dp(22), dp(22));
        scroll.addView(layout);
        addText("AutoDodge v0.3 • calibração experimental", 23);
        addText("Versão 0.3: captura autorizada, rastreamento visual e aprendizado local. Experimental: pode confundir efeitos/animações e não garante desvios. Root não é necessário para esta versão; o controle usa Acessibilidade.", 15);
        addText("1) Permita sobreposição e acessibilidade. 2) Ajuste as posições. 3) Inicie a captura e autorize compartilhar a tela inteira. 4) Abra o jogo e marque AutoDodge. 5) Opcional: use Ensinar para corrigir o detector após ver um tiro ou falso positivo.", 15);
        addButton("1. Permitir aparecer sobre outros apps", () -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        addButton("2. Ativar serviço de acessibilidade", () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        addSlider("X do meu personagem", "px", 50);
        addSlider("Y do meu personagem", "py", 52);
        addSlider("X do analógico esquerdo", "jx", 16);
        addSlider("Y do analógico esquerdo", "jy", 77);
        addText("As porcentagens são da tela em paisagem. Ajuste-as usando uma captura do próprio jogo. Não deixe a checkbox sobre o analógico.", 13);
        addButton("3. Iniciar captura + abrir Brawl Stars", this::startCapture);
        addButton("Parar e remover overlay", () -> {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.STOP));
            if (DodgeAccessibilityService.instance != null)
                DodgeAccessibilityService.instance.disable();
        });
        addText("v0.3 • Aprendizado: um classificador pequeno aprende padrões visuais no próprio celular. Exemplos automáticos são estimativas, não certezas. Para melhorar, use os botões Ensinar > É tiro / Não é tiro. O treino salva apenas números, não imagens.", 13);
        addText("Privacidade: frames analisados apenas no aparelho, sem gravação ou transmissão. O Android exibirá um aviso de captura.", 13);
        addText("Aviso: automação de jogabilidade pode violar as regras da Supercell e trazer punições à conta.", 13);
        setContentView(scroll);
    }

    private void startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primeiro permita sobreposição", Toast.LENGTH_LONG).show();
            return;
        }
        if (DodgeAccessibilityService.instance == null) {
            Toast.makeText(this, "Primeiro ative a acessibilidade do app", Toast.LENGTH_LONG).show();
            return;
        }
        startActivityForResult(projectionManager.createScreenCaptureIntent(), RC_CAPTURE);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == RC_CAPTURE && result == RESULT_OK && data != null) {
            Intent svc = new Intent(this, CaptureService.class);
            svc.setAction(CaptureService.START);
            svc.putExtra(CaptureService.RESULT, result);
            svc.putExtra(CaptureService.DATA, data);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
            Intent launch = getPackageManager().getLaunchIntentForPackage(GAME);
            if (launch != null) {
                layout.postDelayed(() -> startActivity(launch), 600);
            } else Toast.makeText(this, "Abra Brawl Stars manualmente", Toast.LENGTH_LONG).show();
        }
    }

    private void addSlider(String label, String key, int defaultVal) {
        TextView title = addText("", 15);
        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        int v = Math.round(Prefs.read(this, key, defaultVal / 100f) * 100);
        seek.setProgress(v);
        title.setText(label + ": " + v + "%");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int val, boolean user) {
                title.setText(label + ": " + val + "%");
                if (user) Prefs.save(MainActivity.this, key, val / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        layout.addView(seek);
    }

    private TextView addText(String content, int size) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(0xff1b1b1b);
        t.setPadding(0, dp(6), 0, dp(10));
        layout.addView(t);
        return t;
    }
    private void addButton(String label, Runnable fn) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(v -> fn.run());
        layout.addView(b);
    }
    private int dp(int x) { return Math.round(getResources().getDisplayMetrics().density * x); }
}
