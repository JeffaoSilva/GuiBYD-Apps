package com.guibyd.apps;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PublicKey;
import java.util.UUID;

public final class DeviceIdentity {

    private static final String PREFS = "guibyd_client";
    private static final String PREF_INSTALLATION_ID = "installation_id";
    private static final String KEY_ALIAS = "guibyd_install_key";

    private DeviceIdentity() {}

    public static String getOrCreateInstallationId(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = p.getString(PREF_INSTALLATION_ID, null);
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
            p.edit().putString(PREF_INSTALLATION_ID, id).apply();
        }
        return id;
    }

    public static String getOrCreatePublicKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);

        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,
                    "AndroidKeyStore"
            );

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
            )
                    .setDigests(
                            KeyProperties.DIGEST_SHA256,
                            KeyProperties.DIGEST_SHA512
                    )
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .build();

            generator.initialize(spec);
            KeyPair pair = generator.generateKeyPair();
            if (pair == null) throw new IllegalStateException("Falha ao gerar chave do dispositivo");
        }

        PublicKey key = ks.getCertificate(KEY_ALIAS).getPublicKey();
        return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
    }
}
