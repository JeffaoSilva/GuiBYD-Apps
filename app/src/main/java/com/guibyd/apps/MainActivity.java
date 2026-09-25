package com.guibyd.apps;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final String AURORA_PACKAGE = "com.aurora.store";

    private static final AppItem[] APPS = new AppItem[] {
            new AppItem("Chrome", "com.android.chrome"),
            new AppItem("Disney Plus", "com.disney.disneyplus"),
            new AppItem("HBO Max", "com.wbd.stream"),
            new AppItem("Netflix", "com.netflix.mediaclient"),
            new AppItem("Prime Video", "com.amazon.avod.thirdpartyclient"),
            new AppItem("VLC", "org.videolan.vlc"),
            new AppItem("Waze", "com.waze"),
            new AppItem("WhatsApp", "com.whatsapp"),
            new AppItem("WhatsApp Business", "com.whatsapp.w4b")
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(22), dp(28), dp(20));
        root.setBackgroundColor(Color.rgb(13, 17, 23));

        TextView title = new TextView(this);
        title.setText("Aplicativos Gui.BYD");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(18));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);

        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        );
        root.addView(grid, gridLp);

        for (AppItem app : APPS) {
            Button b = new Button(this);
            b.setText(app.name);
            b.setTextColor(Color.WHITE);
            b.setTextSize(20);
            b.setAllCaps(false);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundResource(R.drawable.tile);
            b.setPadding(dp(10), dp(10), dp(10), dp(10));

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(8), dp(8), dp(8), dp(8));
            b.setLayoutParams(lp);

            b.setOnClickListener(v -> openInAurora(app.packageName));
            grid.addView(b);
        }

        TextView footer = new TextView(this);
        footer.setText("Toque em um aplicativo para abrir a página de instalação no Aurora Store");
        footer.setTextColor(Color.rgb(174, 184, 196));
        footer.setTextSize(14);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(12), 0, 0);
        root.addView(footer);

        setContentView(root);
    }

    private void openInAurora(String targetPackage) {
        // Envia um link padrão da Google Play explicitamente para o Aurora.
        // Não existe fallback para Play Store ou navegador.
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
                    .setMessage("O Aurora Store não foi encontrado nesta multimídia. Instale o Aurora Store para baixar os aplicativos.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private static class AppItem {
        final String name;
        final String packageName;

        AppItem(String name, String packageName) {
            this.name = name;
            this.packageName = packageName;
        }
    }
}
