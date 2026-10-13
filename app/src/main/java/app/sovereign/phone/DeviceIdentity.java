package app.sovereign.phone;

import android.security.keystore.*;
import android.util.Base64;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class DeviceIdentity {
    private static final String ALIAS = "GhostwolfDeviceKey";
    private final PrivateKey privateKey;
    private final ECPublicKey publicKey;
    final String fingerprint;
    final JSONObject jwk;
    DeviceIdentity() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).build());
            generator.generateKeyPair();
        }
        privateKey = (PrivateKey) store.getKey(ALIAS, null);
        KeyInfo info = KeyFactory.getInstance("EC", "AndroidKeyStore").getKeySpec(privateKey, KeyInfo.class);
        if (!info.isInsideSecureHardware()) throw new GeneralSecurityException("Hardware-backed device key unavailable.");
        publicKey = (ECPublicKey) store.getCertificate(ALIAS).getPublicKey();
        String x = encode(coordinate(publicKey.getW().getAffineX().toByteArray()));
        String y = encode(coordinate(publicKey.getW().getAffineY().toByteArray()));
        jwk = new JSONObject().put("kty", "EC").put("crv", "P-256").put("x", x).put("y", y);
        // Matches live backend: hashes encoded JWK coordinate strings, not raw coordinates.
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((x + ":" + y).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 8; i++) hex.append(String.format(java.util.Locale.ROOT, "%02X", digest[i] & 255));
        fingerprint = "GW-HW-" + hex;
    }
    private static byte[] coordinate(byte[] value) { byte[] fixed = new byte[32]; int n = Math.min(32, value.length); System.arraycopy(value, value.length - n, fixed, 32 - n, n); return fixed; }
    private static String encode(byte[] bytes) { return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP); }
    String sign(String challenge) throws Exception {
        if (!challenge.matches("[0-9a-fA-F]{64}")) throw new GeneralSecurityException("Invalid challenge format.");
        return signMessage(challenge);
    }
    String signMessage(String message) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA"); signer.initSign(privateKey);
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        byte[] der = signer.sign();
        // P-256 DER sequence fits short-form lengths; convert to the server's 64-byte P1363 format.
        if (der.length < 8 || der[0] != 0x30 || (der[1] & 255) != der.length - 2 || der[2] != 2) throw new GeneralSecurityException("Invalid ECDSA encoding.");
        int rSize = der[3] & 255;
        int sTag = 4 + rSize;
        if (sTag + 2 > der.length || der[sTag] != 2) throw new GeneralSecurityException("Invalid ECDSA integer.");
        int sSize = der[sTag + 1] & 255;
        if (rSize < 1 || rSize > 33 || sSize < 1 || sSize > 33 || sTag + 2 + sSize != der.length) throw new GeneralSecurityException("Invalid ECDSA size.");
        byte[] raw = new byte[64];
        System.arraycopy(coordinate(Arrays.copyOfRange(der, 4, sTag)), 0, raw, 0, 32);
        System.arraycopy(coordinate(Arrays.copyOfRange(der, sTag + 2, der.length)), 0, raw, 32, 32);
        return Base64.encodeToString(raw, Base64.NO_WRAP);
    }
}
