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
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String AURORA_PACKAGE = "com.aurora.store";
    private static final int STORAGE_PERMISSION_REQUEST = 901;

    // O asset precisa se chamar exatamente Youtube_Morphe.apk na release mais recente.
    private static final String YOUTUBE_UPDATE_URL =
            "https://github.com/JeffaoSilva/GuiBYD-Apps/releases/latest/download/Youtube_Morphe.apk";

    private long youtubeDownloadId = -1;
    private File youtubeDownloadedFile;

    private static final AppItem[] USB_APPS = new AppItem[] {
            new AppItem("YouTube", "Youtube_Morphe", R.drawable.icon_youtube, AppType.USB),
            new AppItem("MicroG", "MicroG_RE", R.drawable.icon_microg, AppType.USB),
            new AppItem("Electro", "Electro", R.drawable.icon_electro, AppType.USB),
            new AppItem("Radarbot", "Radarbot", R.drawable.icon_radarbot, AppType.USB),
            new AppItem("Spark", "Spark", R.drawable.icon_spark, AppType.USB),
            new AppItem("Aurora Store", "Aurora_Store", R.drawable.icon_aurora, AppType.USB)
    };

    private static final AppItem[] AURORA_APPS = new AppItem[] {
            new AppItem("Chrome", "com.android.chrome", R.drawable.icon_chrome, AppType.AURORA),
            new AppItem("Disney Plus", "com.disney.disneyplus", R.drawable.icon_disney, AppType.AURORA),
            new AppItem("HBO Max", "com.wbd.stream", R.drawable.icon_max, AppType.AURORA),
            new AppItem("Netflix", "com.netflix.mediaclient", R.drawable.icon_netflix, AppType.AURORA),
            new AppItem("Prime Video", "com.amazon.avod.thirdpartyclient", R.drawable.icon_prime, AppType.AURORA),
            new AppItem("VLC", "org.videolan.vlc", R.drawable.icon_vlc, AppType.AURORA),
            new AppItem("Waze", "com.waze", R.drawable.icon_waze, AppType.AURORA),
            new AppItem("WhatsApp", "com.whatsapp", R.drawable.icon_whatsapp, AppType.AURORA),
            new AppItem("WhatsApp Business", "com.whatsapp.w4b", R.drawable.icon_whatsapp_business, AppType.AURORA)
    };

    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id != youtubeDownloadId) return;
            verifyYoutubeDownloadAndInstall();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        render();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    private void render() {
        boolean portrait =
                getResources().getConfiguration().orientation
                        == Configuration.ORIENTATION_PORTRAIT;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 13, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(portrait ? 14 : 24), dp(14),
                dp(portrait ? 14 : 24), dp(22));

        TextView title = makeText("Aplicativos Gui.BYD", portrait ? 25 : 29, Color.rgb(238,241,245));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title, matchWrap());

        addSectionTitle(root, "Aplicativos do pendrive");
        addGrid(root, USB_APPS, portrait);

        addSectionTitle(root, "Aplicativos do Aurora");
        addGrid(root, AURORA_APPS, portrait);

        addSectionTitle(root, "Atualização do YouTube");
        root.addView(
                makeUpdateCard(portrait),
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(portrait ? 102 : 112)
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

            LinearLayout.LayoutParams rowLp =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
            rowLp.setMargins(0, 0, 0, dp(10));
            root.addView(row, rowLp);

            for (int c = 0; c < columns; c++) {
                int index = i + c;
                if (index >= apps.length) {
                    View spacer = new View(this);
                    LinearLayout.LayoutParams sp =
                            new LinearLayout.LayoutParams(0, cardHeight, 1f);
                    sp.setMargins(gap, 0, gap, 0);
                    row.addView(spacer, sp);
                    continue;
                }

                AppItem item = apps[index];
                View card = makeCard(item, portrait);

                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(0, cardHeight, 1f);
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

        LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, 0, dp(6));
        card.addView(icon, iconLp);

        TextView label = makeText(item.label, portrait ? 14 : 15, Color.rgb(237,240,244));
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        card.addView(label, matchWrap());

        if (item.type == AppType.AURORA) {
            card.setOnClickListener(v -> openInAurora(item.key));
        } else {
            card.setOnClickListener(v -> installUsbApp(item.key, item.label));
        }

        return card;
    }

    private View makeUpdateCard(boolean portrait) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));
        card.setBackgroundResource(R.drawable.update_tile);
        card.setClickable(true);
        card.setFocusable(true);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.icon_update);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int s = dp(portrait ? 54 : 62);
        card.addView(icon, new LinearLayout.LayoutParams(s, s));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);

        TextView t1 = makeText("Atualizar YouTube", portrait ? 17 : 19, Color.WHITE);
        t1.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView t2 = makeText(
                "Baixa a versão mais recente e abre o instalador",
                portrait ? 12 : 13,
                Color.rgb(183, 210, 201)
        );

        texts.addView(t1, matchWrap());
        texts.addView(t2, matchWrap());

        card.addView(texts, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        card.setOnClickListener(v -> startYoutubeUpdate());
        return card;
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

    // ---------------- Aurora ----------------

    private void openInAurora(String targetPackage) {
        Uri uri = Uri.parse(
                "https://play.google.com/store/apps/details?id=" + targetPackage
        );

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(AURORA_PACKAGE);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(this)
                    .setTitle("Aurora Store necessário")
                    .setMessage(
                            "O Aurora Store não foi encontrado. " +
                            "Instale o Aurora Store para acessar os aplicativos online."
                    )
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    // ---------------- Pendrive ----------------

    private void installUsbApp(String prefix, String friendlyName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST
            );
            Toast.makeText(
                    this,
                    "Permita o acesso aos arquivos e toque novamente em " + friendlyName + ".",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        File guiFolder = findGuiBydFolder();
        if (guiFolder == null) {
            showMessage(
                    "Pendrive não encontrado",
                    "Não encontrei a pasta GuiBYD na mídia removível.\n\n" +
                    "Estrutura esperada:\nUSB/GuiBYD/"
            );
            return;
        }

        File match = newestMatchingApk(guiFolder, prefix);
        if (match == null) {
            showMessage(
                    "Arquivo não encontrado",
                    "Não encontrei um APK começando com:\n\n" +
                    prefix + "*.apk\n\nna pasta GuiBYD do pendrive."
            );
            return;
        }

        try {
            File cached = copyForInstall(match);
            launchInstaller(cached);
        } catch (Exception e) {
            showMessage(
                    "Não foi possível instalar",
                    "Encontrei " + match.getName() +
                    ", mas não consegui preparar o arquivo para instalação."
            );
        }
    }

    private File findGuiBydFolder() {
        File storage = new File("/storage");
        File[] volumes = storage.listFiles();
        if (volumes == null) return null;

        for (File volume : volumes) {
            String name = volume.getName().toLowerCase();

            // Evita procurar no armazenamento interno.
            if ("emulated".equals(name) || "self".equals(name)) continue;
            if (!volume.isDirectory() || !volume.canRead()) continue;

            File folder = new File(volume, "GuiBYD");
            if (folder.isDirectory() && folder.canRead()) {
                return folder;
            }
        }
        return null;
    }

    private File newestMatchingApk(File folder, String prefix) {
        File[] files = folder.listFiles();
        if (files == null) return null;

        List<File> matches = new ArrayList<>();
        for (File f : files) {
            String n = f.getName();
            if (f.isFile()
                    && n.toLowerCase().endsWith(".apk")
                    && n.regionMatches(true, 0, prefix, 0, prefix.length())) {
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
            try {
                out.add(Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                out.add(0);
            }
        }
        return out;
    }

    // ---------------- Atualizador YouTube ----------------

    private void startYoutubeUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permitir instalação")
                    .setMessage(
                            "Para instalar a atualização, permita que Aplicativos Gui.BYD " +
                            "instale aplicativos. Ative a permissão e depois toque novamente em Atualizar YouTube."
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
            return;
        }

        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) {
                showMessage("Erro", "Não foi possível acessar a pasta de downloads do aplicativo.");
                return;
            }

            youtubeDownloadedFile = new File(dir, "Youtube_Morphe.apk");
            if (youtubeDownloadedFile.exists()) {
                youtubeDownloadedFile.delete();
            }

            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(YOUTUBE_UPDATE_URL));

            request.setTitle("Atualização do YouTube");
            request.setDescription("Baixando Youtube_Morphe.apk");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    "Youtube_Morphe.apk"
            );

            DownloadManager dm =
                    (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

            youtubeDownloadId = dm.enqueue(request);

            Toast.makeText(
                    this,
                    "Baixando a atualização do YouTube…",
                    Toast.LENGTH_LONG
            ).show();

        } catch (Exception e) {
            showMessage(
                    "Falha no download",
                    "Não foi possível iniciar o download. Verifique a conexão com a internet."
            );
        }
    }

    private void verifyYoutubeDownloadAndInstall() {
        DownloadManager dm =
                (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        DownloadManager.Query q =
                new DownloadManager.Query().setFilterById(youtubeDownloadId);

        Cursor cursor = dm.query(q);
        if (cursor == null) return;

        try {
            if (!cursor.moveToFirst()) return;

            int statusIndex =
                    cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

            int status = cursor.getInt(statusIndex);

            if (status == DownloadManager.STATUS_SUCCESSFUL
                    && youtubeDownloadedFile != null
                    && youtubeDownloadedFile.exists()) {
                try {
                    File cached = copyForInstall(youtubeDownloadedFile);
                    launchInstaller(cached);
                } catch (Exception e) {
                    showMessage(
                            "Download concluído",
                            "O APK foi baixado, mas não consegui abrir o instalador."
                    );
                }
            } else {
                showMessage(
                        "Falha no download",
                        "Não foi possível baixar a atualização do YouTube."
                );
            }
        } finally {
            cursor.close();
        }
    }

    // ---------------- Instalador ----------------

    private File copyForInstall(File source) throws IOException {
        File dir = new File(getCacheDir(), "installer");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Could not create installer cache");
        }

        File target = new File(dir, source.getName());

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {

            byte[] buffer = new byte[1024 * 128];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }

        return target;
    }

    private void launchInstaller(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(settings);
            Toast.makeText(
                    this,
                    "Ative a permissão e toque novamente no aplicativo.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

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
            showMessage(
                    "Instalador não encontrado",
                    "O Android não encontrou um instalador de APK disponível."
            );
        }
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

    private enum AppType {
        USB,
        AURORA
    }

    private static class AppItem {
        final String label;
        final String key;
        final int iconRes;
        final AppType type;

        AppItem(String label, String key, int iconRes, AppType type) {
            this.label = label;
            this.key = key;
            this.iconRes = iconRes;
            this.type = type;
        }
    }
}
