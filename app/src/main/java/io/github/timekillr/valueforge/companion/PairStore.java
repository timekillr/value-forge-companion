package io.github.timekillr.valueforge.companion;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class PairStore {
    private final Context context;
    private static final String ALIAS = "value_forge_pairing_v1";
    PairStore(Context context) { this.context = context; }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    synchronized void save(JSONObject pairing) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        JSONObject blob = new JSONObject();
        blob.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        blob.put("data", Base64.encodeToString(cipher.doFinal(pairing.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP));
        if (!context.getSharedPreferences("pairing", 0).edit().putString("encrypted", blob.toString()).commit())
            throw new Exception("Could not remember this pairing.");
    }
    synchronized JSONObject load() throws Exception {
        String text = context.getSharedPreferences("pairing", 0).getString("encrypted", "");
        if (text.isEmpty()) return null;
        JSONObject blob = new JSONObject(text);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(blob.getString("iv"), Base64.NO_WRAP)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(blob.getString("data"), Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8));
    }
    synchronized void forget() { context.getSharedPreferences("pairing", 0).edit().clear().commit(); }
}
