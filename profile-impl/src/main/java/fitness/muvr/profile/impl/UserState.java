package fitness.muvr.profile.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.CompressedJsonable;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;

import javax.annotation.concurrent.Immutable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

@Immutable
@JsonDeserialize
public final class UserState implements CompressedJsonable {
    static final UserState EMPTY = new UserState();

    static byte[] hashPassword(String passwordHashSalt, String password) {
        try {
            MessageDigest instance = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_512);
            return instance.digest((passwordHashSalt + password).getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private final byte[] passwordHash;
    private final String passwordHashSalt;

    private UserState() {
        this.passwordHash = null;
        this.passwordHashSalt = null;
    }

    @JsonCreator
    public UserState(byte[] passwordHash, String passwordHashSalt) {
        this.passwordHash = passwordHash;
        this.passwordHashSalt = passwordHashSalt;
    }

    /**
     * Indicates whether the given {{password}} matches the registered one
     * @param password the password to check
     * @return true if passwords match
     */
    boolean passwordMatches(String password) {
        return Arrays.equals(hashPassword(this.passwordHashSalt, password), this.passwordHash);
    }
}
