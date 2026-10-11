package ai;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Salted PBKDF2 password hashing (the original stored plaintext passwords). */
final class Passwords {
    private static final int ITER = 65000;

    static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return ITER + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(password, salt, ITER));
    }

    static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split(":");
            byte[] expected = Base64.getDecoder().decode(p[2]);
            return MessageDigest.isEqual(expected, pbkdf2(password, Base64.getDecoder().decode(p[1]), Integer.parseInt(p[0])));
        } catch (Exception e) { return false; }
    }

    private static byte[] pbkdf2(String pw, byte[] salt, int iter) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(pw.toCharArray(), salt, iter, 256)).getEncoded();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
