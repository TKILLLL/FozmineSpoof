package org.phantam.fozminespoofcore.utils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaVerifier {

    private static final String PUBLIC_KEY_PEM =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqmoKSZ0AmkldK7GJQ2s8\n" +
                    "Qrz6lhuBQHJxVIPd6vmg5RS0Qt1BckiYlmLhG95lL4JFxjyh8Frwp/CSrCMLiKfQ\n" +
                    "eCsP9Llp+3mx0Jd/U+Vjl37eozI+bEpXCtLcj+VP1uTZA4cx4OKLj7v9p8qBqc1h\n" +
                    "4zlPz7NI7llj3z5Ir3ofddZ1XxjXvhXgNunf4xOt6iwkCnrqdNKnfkA5eLu/IJsz\n" +
                    "5Zn0OrMIKlPg9o2qidpXwR/elwM57g/7twlqTE5saUL+2JmPfI3pQY7VqIFzSrQt\n" +
                    "Oob3XRF9HZrQ4ZVGQDcTbzCaUYrudezR27Bq5VoBdZsRbueLh9T7rz60gh1I15oT\n" +
                    "NQIDAQAB";

    private RsaVerifier() {}

    public static boolean verify(String payload, String signatureBase64) {
        try {
            String cleanKey = PUBLIC_KEY_PEM
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");

            byte[] keyBytes = Base64.getDecoder().decode(cleanKey);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey publicKey = kf.generatePublic(spec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));

            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}