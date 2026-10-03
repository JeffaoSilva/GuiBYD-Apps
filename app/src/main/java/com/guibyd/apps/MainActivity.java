package com.guibyd.apps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String AURORA_PACKAGE = "com.aurora.store";
    private static final int STORAGE_PERMISSION_REQUEST = 901;
    private static final long OFFLINE_GRACE_MS = 30L * 24L * 60L * 60L * 1000L;

    private static final String PREFS = "guibyd_client";
    private static final String PREF_LICENSE_CODE = "license_code";
    private static final String PREF_LAST_VALIDATED = "last_validated_at";

    private static final int DOWNLOAD_YOUTUBE = 1;
    private static final int DOWNLOAD_GUIBYD = 2;
    private static final int DOWNLOAD_GBOX = 3;
    private static final int DOWNLOAD_YOUTUBE_MUSIC = 4;

    private SupabaseApi api;
    private String installationId;
    private String publicKey;
    private JSONObject youtubeRelease;
    private JSONObject appRelease;
    private JSONObject gboxRelease;
    private JSONObject youtubeMusicRelease;
    private TextView youtubeVersionView;
    private TextView appVersionView;
    private TextView gboxVersionView;
    private TextView youtubeMusicVersionView;
    private ProgressBar youtubeDownloadProgress;
    private ProgressBar appDownloadProgress;
    private ProgressBar gboxDownloadProgress;
    private ProgressBar youtubeMusicDownloadProgress;
    private TextView youtubeProgressText;
    private TextView appProgressText;
    private TextView gboxProgressText;
    private TextView youtubeMusicProgressText;
    private ImageView bannerView;
    private LinearLayout transfersContainer;
    private boolean selfUpdatePromptShown = false;
    private boolean mainAppOpen = false;
    private boolean revalidating = false;

    private final Map<Long, DownloadTarget> downloads = new HashMap<>();
    private final Handler downloadProgressHandler = new Handler(Looper.getMainLooper());

    private static final AppItem[] USB_APPS = new AppItem[] {
            new AppItem("MicroG", "MicroG_RE", R.drawable.icon_microg, AppType.USB, "usb_microg"),
            new AppItem("Electro", "Electro", R.drawable.icon_electro, AppType.USB, "usb_electro"),
            new AppItem("Radarbot", "Radarbot", R.drawable.icon_radarbot, AppType.USB, "usb_radarbot"),
            new AppItem("Spark", "Spark", R.drawable.icon_spark, AppType.USB, "usb_spark"),
            new AppItem("Aurora Store", "Aurora_Store", R.drawable.icon_aurora, AppType.USB, "usb_aurora_store")
    };

    private static final AppItem[] AURORA_APPS = new AppItem[] {
            new AppItem("Chrome", "com.android.chrome", R.drawable.icon_chrome, AppType.AURORA, "aurora_chrome"),
            new AppItem("Disney Plus", "com.disney.disneyplus", R.drawable.icon_disney, AppType.AURORA, "aurora_disney_plus"),
            new AppItem("HBO Max", "com.wbd.stream", R.drawable.icon_max, AppType.AURORA, "aurora_hbo_max"),
            new AppItem("Netflix", "com.netflix.mediaclient", R.drawable.icon_netflix, AppType.AURORA, "aurora_netflix"),
            new AppItem("Prime Video", "com.amazon.avod.thirdpartyclient", R.drawable.icon_prime, AppType.AURORA, "aurora_prime_video"),
            new AppItem("VLC", "org.videolan.vlc", R.drawable.icon_vlc, AppType.AURORA, "aurora_vlc"),
            new AppItem("Waze", "com.waze", R.drawable.icon_waze, AppType.AURORA, "aurora_waze"),
            new AppItem("WhatsApp", "com.whatsapp", R.drawable.icon_whatsapp, AppType.AURORA, "aurora_whatsapp"),
            new AppItem("WhatsApp Business", "com.whatsapp.w4b", R.drawable.icon_whatsapp_business, AppType.AURORA, "aurora_whatsapp_business")
    };

    private static final AppItem[] GAME_APPS = new AppItem[] {
            new AppItem("Angry Birds 2", "com.rovio.baba", R.drawable.icon_angry_birds_2, AppType.AURORA, "game_angry_birds_2"),
            new AppItem("Subway Surfers", "com.kiloo.subwaysurf", R.drawable.icon_subway_surfers, AppType.AURORA, "game_subway_surfers"),
            new AppItem("Candy Crush Saga", "com.king.candycrushsaga", R.drawable.icon_candy_crush, AppType.AURORA, "game_candy_crush"),
            new AppItem("Fruit Ninja", "com.halfbrick.fruitninjafree", R.drawable.icon_fruit_ninja, AppType.AURORA, "game_fruit_ninja"),
            new AppItem("8 Ball Pool", "com.miniclip.eightballpool", R.drawable.icon_8_ball_pool, AppType.AURORA, "game_8_ball_pool"),
            new AppItem("Block Blast!", "com.block.juggle", R.drawable.icon_block_blast, AppType.AURORA, "game_block_blast"),
            new AppItem("Hill Climb Racing 2", "com.fingersoft.hcr2", R.drawable.icon_hill_climb_2, AppType.AURORA, "game_hill_climb_2"),
            new AppItem("UNO!", "com.matteljv.uno", R.drawable.icon_uno, AppType.AURORA, "game_uno"),
            new AppItem("Crossy Road", "com.yodo1.crossyroad", R.drawable.icon_crossy_road, AppType.AURORA, "game_crossy_road")
    };

    private static final AppItem[] ADDITIONAL_APPS = new AppItem[] {
            new AppItem("ChatGPT", "com.openai.chatgpt", R.drawable.icon_chatgpt, AppType.AURORA, "additional_chatgpt"),
            new AppItem("Claude", "com.anthropic.claude", R.drawable.icon_claude, AppType.AURORA, "additional_claude"),
            new AppItem("NotebookLM", "com.google.android.apps.labs.language.tailwind", R.drawable.icon_notebooklm, AppType.AURORA, "additional_notebooklm")
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            DownloadTarget target = downloads.remove(id);
            if (target != null) verifyDownloadAndInstall(id, target);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        api = new SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY);
        cleanupInstallerCache();

        try {
            installationId = DeviceIdentity.getOrCreateInstallationId(this);
            publicKey = DeviceIdentity.getOrCreatePublicKey();
        } catch (Exception e) {
            showFatal("Não foi possível criar a identidade segura desta multimídia.");
            return;
        }

        startLicenseFlow();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mainAppOpen) {
            revalidateCurrentLicense();
        }
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void startLicenseFlow() {
        if (!api.isConfigured()) {
            showFatal("Esta versão foi compilada sem a configuração do servidor Gui.BYD.");
            return;
        }

        String code = prefs().getString(PREF_LICENSE_CODE, null);
        if (code == null || code.trim().isEmpty()) {
            showActivationScreen(null);
            return;
        }

        showLoading("Validando licença…");
        new Thread(() -> {
            try {
                JSONObject result = api.validateLicense(
                        installationId,
                        publicKey,
                        BuildConfig.VERSION_CODE,
                        BuildConfig.VERSION_NAME
                );

                if (validationAccepted(result)) {
                    markValidatedNow();
                    runOnUiThread(this::openMainApp);
                } else {
                    String message = serverMessage(result, "Esta licença não está ativa nesta multimídia.");
                    runOnUiThread(() -> showActivationScreen(message));
                }
            } catch (Exception e) {
                if (isWithinOfflineGrace()) {
                    runOnUiThread(() -> {
                        openMainApp();
                        Toast.makeText(
                                this,
                                "Sem conexão com o servidor. Licença validada em modo offline.",
                                Toast.LENGTH_LONG
                        ).show();
                    });
                } else {
                    runOnUiThread(() -> showActivationScreen(
                            "Não foi possível validar a licença. Verifique a conexão com a internet."
                    ));
                }
            }
        }).start();
    }

    private void showActivationScreen(String message) {
        mainAppOpen = false;
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 13, 18));

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER);
        outer.setPadding(dp(24), dp(24), dp(24), dp(24));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(22), dp(24), dp(22));
        box.setBackgroundResource(R.drawable.update_tile);

        TextView title = makeText("Ativar Aplicativos Gui.BYD", portrait ? 24 : 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title, matchWrap());

        TextView subtitle = makeText(
                "Digite o código de 6 dígitos fornecido pela Gui.BYD.",
                portrait ? 14 : 16,
                Color.rgb(183, 192, 205)
        );
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(10), 0, dp(16));
        box.addView(subtitle, matchWrap());

        EditText codeInput = new EditText(this);
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(Color.rgb(125, 138, 154));
        codeInput.setHint("000000");
        codeInput.setTextSize(26);
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setSingleLine(true);
        String previous = prefs().getString(PREF_LICENSE_CODE, "");
        if (previous != null) codeInput.setText(previous);
        box.addView(codeInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(64)
        ));

        TextView status = makeText(message == null ? "" : message, 14, Color.rgb(255, 169, 169));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, dp(8));
        box.addView(status, matchWrap());

        TextView activate = makeText("Ativar", 18, Color.WHITE);
        activate.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        activate.setGravity(Gravity.CENTER);
        activate.setBackgroundResource(R.drawable.tile);
        activate.setPadding(dp(12), dp(15), dp(12), dp(15));
        activate.setClickable(true);
        box.addView(activate, matchWrap());

        activate.setOnClickListener(v -> {
            String code = codeInput.getText().toString().trim();
            if (!code.matches("\\d{6}")) {
                status.setText("Digite um código válido de 6 dígitos.");
                return;
            }

            activate.setEnabled(false);
            activate.setText("Ativando…");
            status.setText("");

            new Thread(() -> {
                try {
                    JSONObject result = api.activateLicense(
                            code,
                            installationId,
                            publicKey,
                            BuildConfig.VERSION_CODE,
                            BuildConfig.VERSION_NAME,
                            Build.MANUFACTURER + " " + Build.MODEL
                    );

                    boolean ok = result.optBoolean("success", false)
                            || "active".equalsIgnoreCase(result.optString("status"));

                    if (ok) {
                        prefs().edit().putString(PREF_LICENSE_CODE, code).apply();
                        markValidatedNow();
                        runOnUiThread(this::openMainApp);
                    } else {
                        String msg = serverMessage(result, "Não foi possível ativar esta licença.");
                        runOnUiThread(() -> {
                            status.setText(msg);
                            activate.setEnabled(true);
                            activate.setText("Ativar");
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        status.setText("Falha ao conectar ao servidor. Verifique a internet e tente novamente.");
                        activate.setEnabled(true);
                        activate.setText("Ativar");
                    });
                }
            }).start();
        });

        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                portrait ? LinearLayout.LayoutParams.MATCH_PARENT : dp(560),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        outer.addView(box, boxLp);
        scroll.addView(outer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.MATCH_PARENT
        ));
        setContentView(scroll);
    }

    private void showLoading(String text) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(9, 13, 18));

        ProgressBar bar = new ProgressBar(this);
        root.addView(bar, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView label = makeText(text, 16, Color.rgb(210, 216, 224));
        label.setPadding(0, dp(16), 0, 0);
        root.addView(label, matchWrap());
        setContentView(root);
    }

    private void showFatal(String message) {
        showActivationScreen(message);
    }

    private void openMainApp() {
        mainAppOpen = true;
        render();
        loadBanner();
        refreshOnlineInfo();
        loadTransfers();
    }

    private void render() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 13, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(portrait ? 14 : 24), dp(14), dp(portrait ? 14 : 24), dp(22));

        TextView title = makeText("Aplicativos Gui.BYD", portrait ? 25 : 29, Color.rgb(238,241,245));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title, matchWrap());

        bannerView = new ImageView(this);
        bannerView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bannerView.setAdjustViewBounds(false);
        bannerView.setBackgroundResource(R.drawable.update_tile);
        bannerView.setVisibility(View.GONE);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(portrait ? 120 : 130)
        );
        bannerLp.setMargins(dp(5), 0, dp(5), dp(12));
        root.addView(bannerView, bannerLp);

        addSectionTitle(root, "Aplicativos do pendrive");
        addGrid(root, USB_APPS, portrait);

        addSectionTitle(root, "Aplicativos do Aurora");
        addGrid(root, AURORA_APPS, portrait);

        addSectionTitle(root, "Jogos");
        addGrid(root, GAME_APPS, portrait);

        addSectionTitle(root, "Adicionais");
        addGrid(root, ADDITIONAL_APPS, portrait);

        addSectionTitle(root, "Atualizações");

        addUpdatesRow(root, DOWNLOAD_GUIBYD, DOWNLOAD_GBOX, portrait);
        addUpdatesRow(root, DOWNLOAD_YOUTUBE, DOWNLOAD_YOUTUBE_MUSIC, portrait);

        addSectionTitle(root, "Transferências");

        TextView refreshTransfers = makeText("Atualizar transferências", portrait ? 14 : 15, Color.WHITE);
        refreshTransfers.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        refreshTransfers.setGravity(Gravity.CENTER);
        refreshTransfers.setPadding(dp(12), dp(13), dp(12), dp(13));
        refreshTransfers.setBackgroundResource(R.drawable.tile);
        refreshTransfers.setClickable(true);
        refreshTransfers.setOnClickListener(v -> loadTransfers());
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        refreshLp.setMargins(dp(5), 0, dp(5), dp(10));
        root.addView(refreshTransfers, refreshLp);

        transfersContainer = new LinearLayout(this);
        transfersContainer.setOrientation(LinearLayout.VERTICAL);
        TextView initialTransfers = makeText("Carregando transferências…", 13, Color.rgb(151, 168, 184));
        initialTransfers.setPadding(dp(7), dp(4), dp(7), dp(12));
        transfersContainer.addView(initialTransfers, matchWrap());
        root.addView(transfersContainer, matchWrap());

        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scroll);
    }

    private void addUpdatesRow(LinearLayout root, int leftKind, int rightKind, boolean portrait) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, dp(portrait ? 175 : 165), 1f
        );
        leftLp.setMargins(dp(5), 0, dp(5), dp(10));
        row.addView(makeOnlineUpdateCard(leftKind, portrait), leftLp);

        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                0, dp(portrait ? 175 : 165), 1f
        );
        rightLp.setMargins(dp(5), 0, dp(5), dp(10));
        row.addView(makeOnlineUpdateCard(rightKind, portrait), rightLp);

        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void addSectionTitle(LinearLayout root, String text) {
        TextView tv = makeText(text, 17, Color.rgb(172,184,198));
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setPadding(dp(5), dp(9), 0, dp(9));
        root.addView(tv, matchWrap());
    }

    private void addGrid(LinearLayout root, AppItem[] apps, boolean portrait) {
        final int columns = 3;
        final int cardHeight = dp(portrait ? 120 : 130);
        final int gap = dp(5);

        for (int i = 0; i < apps.length; i += columns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            rowLp.setMargins(0, 0, 0, dp(10));
            root.addView(row, rowLp);

            for (int c = 0; c < columns; c++) {
                int index = i + c;
                if (index >= apps.length) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, cardHeight, 1f);
                    sp.setMargins(gap, 0, gap, 0);
                    row.addView(spacer, sp);
                    continue;
                }

                AppItem item = apps[index];
                View card = makeCard(item, portrait);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, cardHeight, 1f);
                lp.setMargins(gap, 0, gap, 0);
                row.addView(card, lp);
            }
        }
    }

    private View makeCard(AppItem item, boolean portrait) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(7), dp(9), dp(7), dp(7));
        card.setBackgroundResource(R.drawable.tile);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(item.iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = dp(portrait ? 52 : 58);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, 0, dp(6));
        card.addView(icon, iconLp);

        TextView label = makeText(item.label, portrait ? 14 : 15, Color.rgb(237,240,244));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        card.addView(label, matchWrap());

        card.setOnClickListener(v -> {
            recordUsage(item.eventName);
            if (item.type == AppType.AURORA) openInAurora(item.key);
            else installUsbApp(item.key, item.label);
        });

        return card;
    }

    private View makeOnlineUpdateCard(int kind, boolean portrait) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.update_tile);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(updateIconForKind(kind));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = dp(portrait ? 40 : 44);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, 0, dp(6));
        card.addView(icon, iconLp);

        TextView title = makeText(updateTitleForKind(kind), portrait ? 14 : 15, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        card.addView(title, matchWrap());

        TextView version = makeText(
                "Consultando versão…",
                portrait ? 11 : 12,
                Color.rgb(151, 168, 184)
        );
        version.setGravity(Gravity.CENTER);
        version.setPadding(0, dp(4), 0, dp(4));
        card.addView(version, matchWrap());

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressLp.setMargins(0, dp(4), 0, 0);
        card.addView(progress, progressLp);

        TextView progressText = makeText("", portrait ? 10 : 11, Color.rgb(183, 210, 201));
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(0, dp(3), 0, 0);
        progressText.setVisibility(View.GONE);
        card.addView(progressText, matchWrap());

        bindUpdateViews(kind, version, progress, progressText);

        card.setOnClickListener(v -> {
            recordUsage(updateEventForKind(kind));
            if (kind == DOWNLOAD_GUIBYD) {
                startSelfUpdate();
            } else {
                startManagedAppUpdate(kind);
            }
        });

        return card;
    }

    private void bindUpdateViews(int kind, TextView version, ProgressBar progress, TextView progressText) {
        if (kind == DOWNLOAD_GUIBYD) {
            appVersionView = version;
            appDownloadProgress = progress;
            appProgressText = progressText;
        } else if (kind == DOWNLOAD_GBOX) {
            gboxVersionView = version;
            gboxDownloadProgress = progress;
            gboxProgressText = progressText;
        } else if (kind == DOWNLOAD_YOUTUBE) {
            youtubeVersionView = version;
            youtubeDownloadProgress = progress;
            youtubeProgressText = progressText;
        } else if (kind == DOWNLOAD_YOUTUBE_MUSIC) {
            youtubeMusicVersionView = version;
            youtubeMusicDownloadProgress = progress;
            youtubeMusicProgressText = progressText;
        }
    }

    private int updateIconForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return R.drawable.icon_gbox;
        if (kind == DOWNLOAD_YOUTUBE) return R.drawable.icon_youtube;
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return R.drawable.icon_youtube_music;
        return R.drawable.icon_update;
    }

    private String updateTitleForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return "Instalar / Atualizar GBox";
        if (kind == DOWNLOAD_YOUTUBE) return "Instalar / Atualizar YouTube";
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return "Instalar / Atualizar YouTube Music";
        return "Atualizar Aplicativos Gui.BYD";
    }

    private String updateAppKeyForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return "gbox";
        if (kind == DOWNLOAD_YOUTUBE) return "youtube";
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return "youtube_music";
        return "guibyd";
    }

    private String updateEventForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return "gbox_install_update";
        if (kind == DOWNLOAD_YOUTUBE) return "youtube_install_update";
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return "youtube_music_install_update";
        return "guibyd_self_update";
    }

    private String updateDisplayNameForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return "GBox";
        if (kind == DOWNLOAD_YOUTUBE) return "YouTube";
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return "YouTube Music";
        return "Aplicativos Gui.BYD";
    }

    private void refreshOnlineInfo() {
        new Thread(() -> {
            try {
                youtubeRelease = api.getRelease("youtube");
                appRelease = api.getRelease("guibyd");
                gboxRelease = api.getRelease("gbox");
                youtubeMusicRelease = api.getRelease("youtube_music");

                runOnUiThread(() -> {
                    updateReleaseLabels();
                    checkSelfUpdate(appRelease);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setReleaseLabelError(youtubeVersionView);
                    setReleaseLabelError(appVersionView);
                    setReleaseLabelError(gboxVersionView);
                    setReleaseLabelError(youtubeMusicVersionView);
                });
            }
        }).start();
    }

    private void setReleaseLabelError(TextView view) {
        if (view != null) view.setText("Sem conexão para consultar a versão");
    }

    private void updateReleaseLabels() {
        updateManagedReleaseLabel(youtubeVersionView, youtubeRelease);
        updateManagedReleaseLabel(gboxVersionView, gboxRelease);
        updateManagedReleaseLabel(youtubeMusicVersionView, youtubeMusicRelease);

        if (appVersionView != null) {
            if (appRelease != null) {
                String version = appRelease.optString("version_name", "").trim();
                if (version.isEmpty()) {
                    appVersionView.setText("Versão indisponível");
                } else if (isVersionNewer(version, BuildConfig.VERSION_NAME)) {
                    appVersionView.setText("Versão disponível: " + version);
                } else {
                    appVersionView.setText("Você já está na versão mais recente");
                }
            } else {
                appVersionView.setText("Versão indisponível");
            }
        }
    }

    private void updateManagedReleaseLabel(TextView view, JSONObject release) {
        if (view == null) return;
        if (release == null) {
            view.setText("Versão indisponível");
            return;
        }
        String version = release.optString("version_name", "").trim();
        view.setText(
                version.isEmpty() || "não definido".equalsIgnoreCase(version)
                        ? "Versão ainda não publicada"
                        : "Versão disponível: " + version
        );
    }

    private void checkSelfUpdate(JSONObject release) {
        if (release == null || selfUpdatePromptShown) return;

        String version = release.optString("version_name", "").trim();
        if (version.isEmpty() || !isVersionNewer(version, BuildConfig.VERSION_NAME)) return;

        selfUpdatePromptShown = true;

        new AlertDialog.Builder(this)
                .setTitle("Atualização do Aplicativos Gui.BYD")
                .setMessage("Versão " + version + " disponível. Atualize para usar a versão mais recente.")
                .setPositiveButton("Atualizar", (d, w) -> startReleaseDownload(release, DOWNLOAD_GUIBYD))
                .setNegativeButton("Depois", null)
                .setCancelable(true)
                .show();
    }

    private void startManagedAppUpdate(int kind) {
        JSONObject release = releaseForKind(kind);
        if (release != null) {
            startReleaseDownload(release, kind);
            return;
        }

        String displayName = updateDisplayNameForKind(kind);
        Toast.makeText(this, "Consultando a versão do " + displayName + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject r = api.getRelease(updateAppKeyForKind(kind));
                setReleaseForKind(kind, r);
                runOnUiThread(() -> {
                    updateReleaseLabels();
                    if (r == null || r.optString("file_path", "").isEmpty()) {
                        showMessage(displayName, "Ainda não há uma versão publicada no servidor.");
                    } else {
                        startReleaseDownload(r, kind);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showMessage(
                        "Falha na conexão",
                        "Não foi possível consultar a versão do " + displayName + "."
                ));
            }
        }).start();
    }

    private JSONObject releaseForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return gboxRelease;
        if (kind == DOWNLOAD_YOUTUBE) return youtubeRelease;
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return youtubeMusicRelease;
        return appRelease;
    }

    private void setReleaseForKind(int kind, JSONObject release) {
        if (kind == DOWNLOAD_GBOX) gboxRelease = release;
        else if (kind == DOWNLOAD_YOUTUBE) youtubeRelease = release;
        else if (kind == DOWNLOAD_YOUTUBE_MUSIC) youtubeMusicRelease = release;
        else appRelease = release;
    }

    private void startSelfUpdate() {
        if (appRelease != null) {
            String version = appRelease.optString("version_name", "").trim();

            if (version.isEmpty()) {
                showMessage(
                        "Aplicativos Gui.BYD",
                        "Ainda não há uma versão válida publicada no servidor."
                );
                return;
            }

            if (!isVersionNewer(version, BuildConfig.VERSION_NAME)) {
                showMessage(
                        "Aplicativos Gui.BYD",
                        "Você já está usando a versão mais recente."
                );
                return;
            }

            startReleaseDownload(appRelease, DOWNLOAD_GUIBYD);
            return;
        }

        Toast.makeText(this, "Consultando atualização do Aplicativos Gui.BYD…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject r = api.getRelease("guibyd");
                appRelease = r;
                runOnUiThread(() -> {
                    updateReleaseLabels();
                    if (r == null || r.optString("file_path", "").isEmpty()) {
                        showMessage(
                                "Aplicativos Gui.BYD",
                                "Ainda não há uma atualização publicada no servidor."
                        );
                        return;
                    }

                    String version = r.optString("version_name", "").trim();
                    if (version.isEmpty()) {
                        showMessage(
                                "Aplicativos Gui.BYD",
                                "A atualização publicada não possui uma versão válida."
                        );
                    } else if (!isVersionNewer(version, BuildConfig.VERSION_NAME)) {
                        showMessage(
                                "Aplicativos Gui.BYD",
                                "Você já está usando a versão mais recente."
                        );
                    } else {
                        startReleaseDownload(r, DOWNLOAD_GUIBYD);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showMessage(
                        "Falha na conexão",
                        "Não foi possível consultar a atualização do Aplicativos Gui.BYD."
                ));
            }
        }).start();
    }

    private void loadBanner() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(api.bannerUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(18000);
                connection.setUseCaches(false);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return;
                }

                try (InputStream in = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(in);
                    if (bitmap != null) {
                        runOnUiThread(() -> {
                            if (bannerView != null) {
                                bannerView.setImageBitmap(bitmap);
                                bannerView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
                // Sem banner ou sem conexão: a tela continua funcionando normalmente.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private boolean isVersionNewer(String availableVersion, String currentVersion) {
        return compareVersions(availableVersion, currentVersion) > 0;
    }

    private int compareVersions(String a, String b) {
        int[] av = parseVersion(a);
        int[] bv = parseVersion(b);
        int max = Math.max(av.length, bv.length);

        for (int i = 0; i < max; i++) {
            int ai = i < av.length ? av[i] : 0;
            int bi = i < bv.length ? bv[i] : 0;
            if (ai != bi) return ai > bi ? 1 : -1;
        }
        return 0;
    }

    private int[] parseVersion(String version) {
        String clean = version == null ? "" : version.trim();
        clean = clean.replaceFirst("^[vV]", "");
        clean = clean.split("[-+\\s]", 2)[0];

        String[] parts = clean.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                result[i] = 0;
            }
        }
        return result;
    }

    private void startReleaseDownload(JSONObject release, int kind) {
        if (isDownloadActive(kind)) {
            Toast.makeText(this, "Este download já está em andamento.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ensureInstallPermission()) return;

        String path = release.optString("file_path", "").trim();
        if (path.isEmpty()) {
            showMessage("Arquivo indisponível", "Esta versão não possui um APK publicado.");
            return;
        }

        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                showMessage("Erro", "Não foi possível acessar a pasta de downloads do aplicativo.");
                return;
            }

            String filename = updateFilenameForKind(kind);
            File targetFile = new File(dir, filename);
            if (targetFile.exists()) targetFile.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(api.storageDownloadUrl(path)));
            api.addAuthHeaders(request::addRequestHeader);
            request.setTitle(updateDisplayNameForKind(kind) + " Gui.BYD");
            request.setDescription("Baixando " + filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            long id = dm.enqueue(request);
            DownloadTarget target = new DownloadTarget(kind, targetFile);
            downloads.put(id, target);
            showDownloadProgress(kind, 0, "Iniciando download…");
            monitorDownloadProgress(id, target);
        } catch (Exception e) {
            showMessage("Falha no download", "Não foi possível iniciar o download. Verifique a internet.");
        }
    }

    private String updateFilenameForKind(int kind) {
        if (kind == DOWNLOAD_GBOX) return "GBox.apk";
        if (kind == DOWNLOAD_YOUTUBE) return "Youtube_Morphe.apk";
        if (kind == DOWNLOAD_YOUTUBE_MUSIC) return "YouTube_Music.apk";
        return "Aplicativos_GuiBYD.apk";
    }

    private boolean isDownloadActive(int kind) {
        for (DownloadTarget target : downloads.values()) {
            if (target.kind == kind) return true;
        }
        return false;
    }

    private void showDownloadProgress(int kind, int percent, String text) {
        ProgressBar bar;
        TextView label;
        if (kind == DOWNLOAD_GBOX) {
            bar = gboxDownloadProgress;
            label = gboxProgressText;
        } else if (kind == DOWNLOAD_YOUTUBE) {
            bar = youtubeDownloadProgress;
            label = youtubeProgressText;
        } else if (kind == DOWNLOAD_YOUTUBE_MUSIC) {
            bar = youtubeMusicDownloadProgress;
            label = youtubeMusicProgressText;
        } else {
            bar = appDownloadProgress;
            label = appProgressText;
        }

        if (bar != null) {
            bar.setVisibility(View.VISIBLE);
            bar.setIndeterminate(percent < 0);
            if (percent >= 0) bar.setProgress(Math.max(0, Math.min(100, percent)));
        }

        if (label != null) {
            label.setVisibility(View.VISIBLE);
            label.setText(text);
        }
    }

    private void monitorDownloadProgress(long downloadId, DownloadTarget target) {
        final DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (!downloads.containsKey(downloadId)) return;

                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                Cursor cursor = dm.query(query);

                try {
                    if (cursor == null || !cursor.moveToFirst()) {
                        downloadProgressHandler.postDelayed(this, 500);
                        return;
                    }

                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        showDownloadProgress(target.kind, 100, "Download concluído · 100%");
                        return;
                    }

                    if (status == DownloadManager.STATUS_FAILED) {
                        showDownloadProgress(target.kind, 0, "Falha no download");
                        return;
                    }

                    if (total > 0) {
                        int percent = (int) Math.min(100L, (downloaded * 100L) / total);
                        showDownloadProgress(target.kind, percent, "Baixando… " + percent + "%");
                    } else {
                        showDownloadProgress(target.kind, -1, "Baixando…");
                    }

                    downloadProgressHandler.postDelayed(this, 500);
                } finally {
                    if (cursor != null) cursor.close();
                }
            }
        };

        downloadProgressHandler.post(poll);
    }

    private boolean ensureInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permitir instalação")
                    .setMessage(
                            "Permita que Aplicativos Gui.BYD instale aplicativos. " +
                            "Depois volte e toque novamente no botão."
                    )
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Abrir configuração", (d, w) -> {
                        Intent i = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName())
                        );
                        startActivity(i);
                    })
                    .show();
            return false;
        }
        return true;
    }

    private void verifyDownloadAndInstall(long downloadId, DownloadTarget target) {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = dm.query(q);
        if (cursor == null) return;

        try {
            if (!cursor.moveToFirst()) return;
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            int status = cursor.getInt(statusIndex);

            if (status == DownloadManager.STATUS_SUCCESSFUL && target.file.exists()) {
                showDownloadProgress(target.kind, 100, "Download concluído · 100%");
                try {
                    File cached = copyForInstall(target.file);
                    launchInstaller(cached);
                } catch (Exception e) {
                    showMessage("Download concluído", "O APK foi baixado, mas não consegui abrir o instalador.");
                }
            } else {
                showDownloadProgress(target.kind, 0, "Falha no download");
                showMessage("Falha no download", "Não foi possível baixar o APK.");
            }
        } finally {
            cursor.close();
        }
    }

    private void recordUsage(String eventName) {
        new Thread(() -> {
            try {
                api.recordUsage(
                        installationId,
                        publicKey,
                        eventName,
                        BuildConfig.VERSION_CODE,
                        BuildConfig.VERSION_NAME
                );
            } catch (Exception ignored) {}
        }).start();
    }

    // ---------------- Transferências ----------------

    private void loadTransfers() {
        if (transfersContainer == null) return;

        transfersContainer.removeAllViews();
        TextView loading = makeText("Carregando transferências…", 13, Color.rgb(151, 168, 184));
        loading.setPadding(dp(7), dp(4), dp(7), dp(12));
        transfersContainer.addView(loading, matchWrap());

        String licenseCode = prefs().getString(PREF_LICENSE_CODE, "");
        if (licenseCode == null || licenseCode.trim().isEmpty()) return;

        new Thread(() -> {
            try {
                JSONArray rows = api.listTransfers(licenseCode, installationId, publicKey);
                runOnUiThread(() -> renderTransfers(rows));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (transfersContainer == null) return;
                    transfersContainer.removeAllViews();
                    TextView error = makeText("Não foi possível carregar as transferências.", 13, Color.rgb(255, 169, 169));
                    error.setPadding(dp(7), dp(4), dp(7), dp(12));
                    transfersContainer.addView(error, matchWrap());
                });
            }
        }).start();
    }

    private void renderTransfers(JSONArray rows) {
        if (transfersContainer == null) return;
        transfersContainer.removeAllViews();

        if (rows == null || rows.length() == 0) {
            TextView empty = makeText("Nenhum arquivo disponível para esta multimídia.", 13, Color.rgb(151, 168, 184));
            empty.setPadding(dp(7), dp(4), dp(7), dp(12));
            transfersContainer.addView(empty, matchWrap());
            return;
        }

        for (int i = 0; i < rows.length(); i++) {
            JSONObject transfer = rows.optJSONObject(i);
            if (transfer == null) continue;
            transfersContainer.addView(makeTransferCard(transfer));
        }
    }

    private View makeTransferCard(JSONObject transfer) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackgroundResource(R.drawable.update_tile);

        String originalName = transfer.optString("original_filename", "arquivo");
        String displayName = transfer.optString("display_name", "").trim();
        String titleText = displayName.isEmpty() ? originalName : displayName;
        String description = transfer.optString("description", "").trim();
        long size = transfer.optLong("file_size", 0L);
        String expiresAt = transfer.optString("expires_at", "");

        TextView title = makeText(titleText, 16, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, matchWrap());

        if (!titleText.equals(originalName)) {
            TextView original = makeText(originalName, 12, Color.rgb(151, 168, 184));
            original.setPadding(0, dp(3), 0, 0);
            card.addView(original, matchWrap());
        }

        if (!description.isEmpty()) {
            TextView desc = makeText(description, 13, Color.rgb(210, 216, 224));
            desc.setPadding(0, dp(8), 0, 0);
            card.addView(desc, matchWrap());
        }

        String metaText = "Expira em " + formatTransferExpiry(expiresAt);
        if (size > 0) metaText += "  ·  " + humanFileSize(size);
        TextView meta = makeText(metaText, 12, Color.rgb(151, 168, 184));
        meta.setPadding(0, dp(8), 0, dp(8));
        card.addView(meta, matchWrap());

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        card.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        ));

        TextView progressText = makeText("", 11, Color.rgb(183, 210, 201));
        progressText.setPadding(0, dp(4), 0, 0);
        progressText.setVisibility(View.GONE);
        card.addView(progressText, matchWrap());

        boolean installable = isInstallableTransfer(originalName);
        TextView action = makeText(
                installable ? "Baixar / Instalar" : "Baixar / Salvar no dispositivo",
                14,
                Color.WHITE
        );
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(13), dp(12), dp(13));
        action.setBackgroundResource(R.drawable.tile);
        action.setClickable(true);
        LinearLayout.LayoutParams actionLp = matchWrap();
        actionLp.setMargins(0, dp(10), 0, 0);
        card.addView(action, actionLp);

        action.setOnClickListener(v -> downloadTransfer(transfer, progress, progressText, action));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardLp.setMargins(dp(5), 0, dp(5), dp(10));
        card.setLayoutParams(cardLp);
        return card;
    }

    private void downloadTransfer(
            JSONObject transfer,
            ProgressBar progress,
            TextView progressText,
            TextView action
    ) {
        String originalName = safeFilename(transfer.optString("original_filename", "arquivo"));
        String transferId = transfer.optString("transfer_id", transfer.optString("id", ""));
        String licenseCode = prefs().getString(PREF_LICENSE_CODE, "");

        if (transferId.isEmpty() || licenseCode == null || licenseCode.trim().isEmpty()) {
            showMessage("Transferência indisponível", "Não foi possível identificar esta transferência.");
            return;
        }

        boolean installable = isInstallableTransfer(originalName);
        if (!installable && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
            Toast.makeText(this, "Permita salvar arquivos e toque novamente.", Toast.LENGTH_LONG).show();
            return;
        }

        action.setClickable(false);
        action.setAlpha(0.6f);
        progress.setVisibility(View.VISIBLE);
        progressText.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        progressText.setText("Iniciando download…");

        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                File dir = getExternalFilesDir("Transferencias");
                if (dir == null) throw new IOException("Pasta indisponível");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("Pasta indisponível");

                File target = new File(dir, originalName);
                if (target.exists()) target.delete();

                URL url = new URL(api.transferDownloadUrl());
                c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(60000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Accept", "application/octet-stream");

                JSONObject body = new JSONObject();
                body.put("licenseCode", licenseCode);
                body.put("installationId", installationId);
                body.put("publicKey", publicKey);
                body.put("transferId", transferId);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(bytes);
                }

                int status = c.getResponseCode();
                if (status < 200 || status >= 300) {
                    String err = "HTTP " + status;
                    try {
                        InputStream es = c.getErrorStream();
                        if (es != null) {
                            byte[] buf = new byte[4096];
                            int n = es.read(buf);
                            if (n > 0) err = new String(buf, 0, n, StandardCharsets.UTF_8);
                            es.close();
                        }
                    } catch (Exception ignored) {}
                    throw new IOException(err);
                }

                long total = c.getContentLengthLong();
                long done = 0L;
                try (InputStream in = c.getInputStream();
                     FileOutputStream out = new FileOutputStream(target)) {
                    byte[] buffer = new byte[128 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                        done += n;
                        if (total > 0) {
                            final int pct = (int) Math.min(100L, (done * 100L) / total);
                            runOnUiThread(() -> {
                                progress.setProgress(pct);
                                progressText.setText("Baixando… " + pct + "%");
                            });
                        }
                    }
                }

                runOnUiThread(() -> {
                    progress.setProgress(100);
                    progressText.setText("Download concluído · 100%");
                });

                if (installable) {
                    File cached = copyForInstall(target);
                    target.delete();
                    runOnUiThread(() -> {
                        if (originalName.toLowerCase(Locale.ROOT).endsWith(".apkm")) {
                            launchApkmInstaller(cached);
                        } else {
                            launchInstaller(cached);
                        }
                    });
                } else {
                    saveToPublicDownloads(target, originalName);
                    target.delete();
                    runOnUiThread(() -> showMessage(
                            "Arquivo salvo",
                            originalName + " foi salvo na pasta Downloads do dispositivo."
                    ));
                }

                recordUsage("transfer_download");
            } catch (Exception e) {
                runOnUiThread(() -> showMessage(
                        "Falha na transferência",
                        "Não foi possível baixar este arquivo. Verifique a conexão e tente novamente."
                ));
            } finally {
                if (c != null) c.disconnect();
                runOnUiThread(() -> {
                    action.setClickable(true);
                    action.setAlpha(1f);
                });
            }
        }).start();
    }

    private boolean isInstallableTransfer(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return n.endsWith(".apk") || n.endsWith(".apkm");
    }

    private String safeFilename(String name) {
        String n = name == null ? "arquivo" : name.trim();
        n = n.replace("/", "_").replace("\\", "_");
        while (n.contains("..")) n = n.replace("..", "_");
        if (n.isEmpty()) n = "arquivo";
        return n;
    }

    private String formatTransferExpiry(String iso) {
        try {
            String clean = iso == null ? "" : iso.trim();
            if (clean.length() >= 10) {
                String[] p = clean.substring(0, 10).split("-");
                if (p.length == 3) return p[2] + "/" + p[1] + "/" + p[0];
            }
        } catch (Exception ignored) {}
        return "—";
    }

    private String humanFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0);
    }

    private void saveToPublicDownloads(File source, String filename) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver resolver = getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GuiBYD");
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Não foi possível criar o arquivo");

            try (FileInputStream in = new FileInputStream(source);
                 OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) throw new IOException("Não foi possível abrir o destino");
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
        } else {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists() && !downloadsDir.mkdirs()) throw new IOException("Pasta Downloads indisponível");
            File outFile = new File(downloadsDir, filename);
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[128 * 1024];
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
        }
    }

    private void launchApkmInstaller(File apkm) {
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(getPackageName() + ".files")
                .appendPath(apkm.getName())
                .build();

        String[] mimeTypes = new String[]{
                "application/octet-stream",
                "application/zip",
                "*/*"
        };

        for (String mime : mimeTypes) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) != null) {
                try {
                    startActivity(intent);
                    return;
                } catch (Exception ignored) {}
            }
        }

        showMessage(
                "Instalador de APKM necessário",
                "O arquivo foi baixado, mas não encontrei um aplicativo compatível para instalar APKM. " +
                        "Instale novamente o AppManager e tente de novo."
        );
    }

    private void cleanupInstallerCache() {
        try {
            File dir = new File(getCacheDir(), "installer");
            File[] files = dir.listFiles();
            if (files == null) return;
            long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
            for (File f : files) {
                if (f.isFile() && f.lastModified() < cutoff) f.delete();
            }
        } catch (Exception ignored) {}
    }

    private void revalidateCurrentLicense() {
        if (revalidating) return;
        String code = prefs().getString(PREF_LICENSE_CODE, null);
        if (code == null || code.trim().isEmpty()) {
            showActivationScreen(null);
            return;
        }

        revalidating = true;
        new Thread(() -> {
            try {
                JSONObject result = api.validateLicense(
                        installationId,
                        publicKey,
                        BuildConfig.VERSION_CODE,
                        BuildConfig.VERSION_NAME
                );
                if (validationAccepted(result)) {
                    markValidatedNow();
                    runOnUiThread(() -> {
                        selfUpdatePromptShown = false;
                        refreshOnlineInfo();
                        loadBanner();
                        loadTransfers();
                    });
                } else {
                    String message = serverMessage(result, "Esta licença não está ativa nesta multimídia.");
                    runOnUiThread(() -> showActivationScreen(message));
                }
            } catch (Exception e) {
                if (isWithinOfflineGrace()) {
                    runOnUiThread(() -> {
                        refreshOnlineInfo();
                        loadBanner();
                        loadTransfers();
                    });
                } else {
                    runOnUiThread(() -> showActivationScreen(
                            "Não foi possível validar a licença. Verifique a conexão com a internet."
                    ));
                }
            } finally {
                revalidating = false;
            }
        }).start();
    }

    // ---------------- Aurora ----------------

    private void openInAurora(String targetPackage) {
        Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=" + targetPackage);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(AURORA_PACKAGE);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Aurora Store necessário")
                    .setMessage("O Aurora Store não foi encontrado. Instale o Aurora Store para acessar os aplicativos online.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    // ---------------- Pendrive ----------------

    private void installUsbApp(String prefix, String friendlyName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST);
            Toast.makeText(this, "Permita o acesso aos arquivos e toque novamente em " + friendlyName + ".", Toast.LENGTH_LONG).show();
            return;
        }

        File guiFolder = findGuiBydFolder();
        if (guiFolder == null) {
            showMessage("Pendrive não encontrado", "Não encontrei a pasta GuiBYD na mídia removível.\n\nEstrutura esperada:\nUSB/GuiBYD/");
            return;
        }

        File match = newestMatchingApk(guiFolder, prefix);
        if (match == null) {
            showMessage("Arquivo não encontrado", "Não encontrei um APK começando com:\n\n" + prefix + "*.apk\n\nna pasta GuiBYD do pendrive.");
            return;
        }

        try {
            File cached = copyForInstall(match);
            launchInstaller(cached);
        } catch (Exception e) {
            showMessage("Não foi possível instalar", "Encontrei " + match.getName() + ", mas não consegui preparar o arquivo para instalação.");
        }
    }

    private File findGuiBydFolder() {
        File storage = new File("/storage");
        File[] volumes = storage.listFiles();
        if (volumes == null) return null;

        for (File volume : volumes) {
            String name = volume.getName().toLowerCase();
            if ("emulated".equals(name) || "self".equals(name)) continue;
            if (!volume.isDirectory() || !volume.canRead()) continue;

            File folder = new File(volume, "GuiBYD");
            if (folder.isDirectory() && folder.canRead()) return folder;
        }
        return null;
    }

    private File newestMatchingApk(File folder, String prefix) {
        File[] files = folder.listFiles();
        if (files == null) return null;

        List<File> matches = new ArrayList<>();
        for (File f : files) {
            String n = f.getName();
            if (f.isFile() && n.toLowerCase().endsWith(".apk") && n.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matches.add(f);
            }
        }
        if (matches.isEmpty()) return null;

        matches.sort((a, b) -> {
            int cmp = compareVersionLikeNames(a.getName(), b.getName());
            if (cmp != 0) return -cmp;
            return Long.compare(b.lastModified(), a.lastModified());
        });
        return matches.get(0);
    }

    private int compareVersionLikeNames(String a, String b) {
        List<Integer> va = extractNumbers(a);
        List<Integer> vb = extractNumbers(b);
        int max = Math.max(va.size(), vb.size());

        for (int i = 0; i < max; i++) {
            int x = i < va.size() ? va.get(i) : 0;
            int y = i < vb.size() ? vb.get(i) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return a.compareToIgnoreCase(b);
    }

    private List<Integer> extractNumbers(String name) {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile("(\\d+)").matcher(name);
        while (m.find()) {
            try { out.add(Integer.parseInt(m.group(1))); }
            catch (NumberFormatException ignored) { out.add(0); }
        }
        return out;
    }

    // ---------------- Instalador ----------------

    private File copyForInstall(File source) throws IOException {
        File dir = new File(getCacheDir(), "installer");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create installer cache");

        File target = new File(dir, source.getName());
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024 * 128];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        return target;
    }

    private void launchInstaller(File apk) {
        if (!ensureInstallPermission()) return;

        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(getPackageName() + ".files")
                .appendPath(apk.getName())
                .build();

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showMessage("Instalador não encontrado", "O Android não encontrou um instalador de APK disponível.");
        }
    }

    // ---------------- Licença / utilidades ----------------

    private boolean validationAccepted(JSONObject o) {
        return o != null && (
                o.optBoolean("valid", false)
                        || o.optBoolean("success", false)
                        || "active".equalsIgnoreCase(o.optString("status"))
        );
    }

    private String serverMessage(JSONObject o, String fallback) {
        if (o == null) return fallback;
        String m = o.optString("message", "").trim();
        return m.isEmpty() ? fallback : m;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void markValidatedNow() {
        prefs().edit().putLong(PREF_LAST_VALIDATED, System.currentTimeMillis()).apply();
    }

    private boolean isWithinOfflineGrace() {
        long last = prefs().getLong(PREF_LAST_VALIDATED, 0L);
        return last > 0L && System.currentTimeMillis() - last <= OFFLINE_GRACE_MS;
    }

    private TextView makeText(String text, float size, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        return tv;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private enum AppType { USB, AURORA }

    private static class AppItem {
        final String label;
        final String key;
        final int iconRes;
        final AppType type;
        final String eventName;

        AppItem(String label, String key, int iconRes, AppType type, String eventName) {
            this.label = label;
            this.key = key;
            this.iconRes = iconRes;
            this.type = type;
            this.eventName = eventName;
        }
    }

    private static class DownloadTarget {
        final int kind;
        final File file;

        DownloadTarget(int kind, File file) {
            this.kind = kind;
            this.file = file;
        }
    }
}
