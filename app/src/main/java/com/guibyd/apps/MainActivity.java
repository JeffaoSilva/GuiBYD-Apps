package com.guibyd.apps;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String AURORA_PACKAGE = "com.aurora.store";
    private static final int STORAGE_PERMISSION_REQUEST = 901;
    private static final long OFFLINE_GRACE_MS = 30L * 24L * 60L * 60L * 1000L;

    private static final String PREFS = "guibyd_client";
    private static final String PREF_LICENSE_CODE = "license_code";
    private static final String PREF_LAST_VALIDATED = "last_validated_at";

    private static final int DOWNLOAD_YOUTUBE = 1;
    private static final int DOWNLOAD_GUIBYD = 2;

    private SupabaseApi api;
    private String installationId;
    private String publicKey;
    private JSONObject youtubeRelease;
    private JSONObject appRelease;
    private TextView youtubeVersionView;
    private TextView appVersionView;
    private ProgressBar youtubeDownloadProgress;
    private ProgressBar appDownloadProgress;
    private TextView youtubeProgressText;
    private TextView appProgressText;
    private ImageView bannerView;
    private boolean selfUpdatePromptShown = false;

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
        if (bannerView != null) {
            selfUpdatePromptShown = false;
            refreshOnlineInfo();
            loadBanner();
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
        render();
        loadBanner();
        refreshOnlineInfo();
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

        addSectionTitle(root, "Atualizações");

        LinearLayout updatesRow = new LinearLayout(this);
        updatesRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams appLp = new LinearLayout.LayoutParams(
                0,
                dp(portrait ? 175 : 165),
                1f
        );
        appLp.setMargins(dp(5), 0, dp(5), 0);
        updatesRow.addView(makeOnlineUpdateCard(DOWNLOAD_GUIBYD, portrait), appLp);

        LinearLayout.LayoutParams youtubeLp = new LinearLayout.LayoutParams(
                0,
                dp(portrait ? 175 : 165),
                1f
        );
        youtubeLp.setMargins(dp(5), 0, dp(5), 0);
        updatesRow.addView(makeOnlineUpdateCard(DOWNLOAD_YOUTUBE, portrait), youtubeLp);

        root.addView(
                updatesRow,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        setContentView(scroll);
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
        final boolean isYoutube = kind == DOWNLOAD_YOUTUBE;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.update_tile);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(isYoutube ? R.drawable.icon_youtube : R.drawable.icon_update);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconSize = dp(portrait ? 40 : 44);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, 0, dp(6));
        card.addView(icon, iconLp);

        TextView title = makeText(
                isYoutube ? "Instalar / Atualizar YouTube" : "Atualizar Aplicativos Gui.BYD",
                portrait ? 14 : 15,
                Color.WHITE
        );
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

        ProgressBar progress = new ProgressBar(
                this,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
        );
        progressLp.setMargins(0, dp(4), 0, 0);
        card.addView(progress, progressLp);

        TextView progressText = makeText(
                "",
                portrait ? 10 : 11,
                Color.rgb(183, 210, 201)
        );
        progressText.setGravity(Gravity.CENTER);
        progressText.setPadding(0, dp(3), 0, 0);
        progressText.setVisibility(View.GONE);
        card.addView(progressText, matchWrap());

        if (isYoutube) {
            youtubeVersionView = version;
            youtubeDownloadProgress = progress;
            youtubeProgressText = progressText;
        } else {
            appVersionView = version;
            appDownloadProgress = progress;
            appProgressText = progressText;
        }

        card.setOnClickListener(v -> {
            if (isYoutube) {
                recordUsage("youtube_install_update");
                startYoutubeUpdate();
            } else {
                recordUsage("guibyd_self_update");
                startSelfUpdate();
            }
        });

        return card;
    }

    private void refreshOnlineInfo() {
        new Thread(() -> {
            try {
                youtubeRelease = api.getRelease("youtube");
                appRelease = api.getRelease("guibyd");

                runOnUiThread(() -> {
                    updateReleaseLabels();
                    checkSelfUpdate(appRelease);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (youtubeVersionView != null) youtubeVersionView.setText("Sem conexão para consultar a versão");
                    if (appVersionView != null) appVersionView.setText("Sem conexão para consultar a versão");
                });
            }
        }).start();
    }

    private void updateReleaseLabels() {
        if (youtubeVersionView != null) {
            if (youtubeRelease != null) {
                String version = youtubeRelease.optString("version_name", "").trim();
                youtubeVersionView.setText(
                        version.isEmpty() || "não definido".equalsIgnoreCase(version)
                                ? "Versão ainda não publicada"
                                : "Versão disponível: " + version
                );
            } else {
                youtubeVersionView.setText("Versão indisponível");
            }
        }

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

    private void startYoutubeUpdate() {
        if (youtubeRelease != null) {
            startReleaseDownload(youtubeRelease, DOWNLOAD_YOUTUBE);
            return;
        }

        Toast.makeText(this, "Consultando a versão do YouTube…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject r = api.getRelease("youtube");
                youtubeRelease = r;
                runOnUiThread(() -> {
                    if (r == null || r.optString("file_path", "").isEmpty()) {
                        showMessage("YouTube", "Ainda não há uma versão publicada no servidor.");
                    } else {
                        startReleaseDownload(r, DOWNLOAD_YOUTUBE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showMessage(
                        "Falha na conexão",
                        "Não foi possível consultar a versão do YouTube."
                ));
            }
        }).start();
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

            String filename = kind == DOWNLOAD_YOUTUBE ? "Youtube_Morphe.apk" : "Aplicativos_GuiBYD.apk";
            File targetFile = new File(dir, filename);
            if (targetFile.exists()) targetFile.delete();

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(api.storageDownloadUrl(path)));
            api.addAuthHeaders(request::addRequestHeader);
            request.setTitle(kind == DOWNLOAD_YOUTUBE ? "YouTube Gui.BYD" : "Aplicativos Gui.BYD");
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

    private boolean isDownloadActive(int kind) {
        for (DownloadTarget target : downloads.values()) {
            if (target.kind == kind) return true;
        }
        return false;
    }

    private void showDownloadProgress(int kind, int percent, String text) {
        ProgressBar bar = kind == DOWNLOAD_YOUTUBE
                ? youtubeDownloadProgress
                : appDownloadProgress;
        TextView label = kind == DOWNLOAD_YOUTUBE
                ? youtubeProgressText
                : appProgressText;

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
