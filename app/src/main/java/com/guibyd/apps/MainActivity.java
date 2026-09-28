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
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import java.io.IOException;
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
    private TextView youtubeVersionView;
    private boolean selfUpdatePromptShown = false;

    private final Map<Long, DownloadTarget> downloads = new HashMap<>();

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

        addSectionTitle(root, "Aplicativos do pendrive");
        addGrid(root, USB_APPS, portrait);

        addSectionTitle(root, "Aplicativos do Aurora");
        addGrid(root, AURORA_APPS, portrait);

        addSectionTitle(root, "YouTube");
        root.addView(
                makeYoutubeCard(portrait),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(portrait ? 125 : 135)
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

    private View makeYoutubeCard(boolean portrait) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackgroundResource(R.drawable.update_tile);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.icon_youtube);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = dp(portrait ? 54 : 62);
        card.addView(icon, new LinearLayout.LayoutParams(s, s));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);

        TextView t1 = makeText("Instalar / Atualizar o YouTube", portrait ? 17 : 19, Color.WHITE);
        t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView t2 = makeText(
                "Baixa a versão mais recente e instala ou atualiza o YouTube",
                portrait ? 12 : 13,
                Color.rgb(183, 210, 201)
        );
        youtubeVersionView = makeText("Consultando versão…", portrait ? 12 : 13, Color.rgb(151, 168, 184));
        youtubeVersionView.setPadding(0, dp(4), 0, 0);

        texts.addView(t1, matchWrap());
        texts.addView(t2, matchWrap());
        texts.addView(youtubeVersionView, matchWrap());

        card.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.setOnClickListener(v -> {
            recordUsage("youtube_install_update");
            startYoutubeUpdate();
        });
        return card;
    }

    private void refreshOnlineInfo() {
        new Thread(() -> {
            try {
                youtubeRelease = api.getRelease("youtube");
                JSONObject appRelease = api.getRelease("guibyd");

                runOnUiThread(() -> {
                    if (youtubeVersionView != null) {
                        if (youtubeRelease != null) {
                            String version = youtubeRelease.optString("version_name", "").trim();
                            youtubeVersionView.setText(version.isEmpty() || "não definido".equalsIgnoreCase(version)
                                    ? "Versão ainda não publicada"
                                    : "Versão disponível: " + version);
                        } else {
                            youtubeVersionView.setText("Versão indisponível");
                        }
                    }
                    checkSelfUpdate(appRelease);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (youtubeVersionView != null) youtubeVersionView.setText("Sem conexão para consultar a versão");
                });
            }
        }).start();
    }

    private void checkSelfUpdate(JSONObject release) {
        if (release == null || selfUpdatePromptShown) return;
        long available = release.optLong("version_code", 0);
        if (available <= BuildConfig.VERSION_CODE) return;

        String version = release.optString("version_name", "nova versão");
        boolean required = release.optBoolean("is_required", false);
        selfUpdatePromptShown = true;

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Atualização do Aplicativos Gui.BYD")
                .setMessage("Versão " + version + " disponível.")
                .setPositiveButton("Atualizar", (d, w) -> startReleaseDownload(release, DOWNLOAD_GUIBYD));

        if (!required) b.setNegativeButton("Depois", null);
        b.setCancelable(!required);
        b.show();
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

    private void startReleaseDownload(JSONObject release, int kind) {
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
            downloads.put(id, new DownloadTarget(kind, targetFile));

            Toast.makeText(this, "Baixando atualização…", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            showMessage("Falha no download", "Não foi possível iniciar o download. Verifique a internet.");
        }
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
                try {
                    File cached = copyForInstall(target.file);
                    launchInstaller(cached);
                } catch (Exception e) {
                    showMessage("Download concluído", "O APK foi baixado, mas não consegui abrir o instalador.");
                }
            } else {
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
