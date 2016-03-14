package fitness.muvr.profile.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.api.transport.TransportException;
import com.lightbend.lagom.serialization.CompressedJsonable;
import fitness.muvr.profile.api.UserService;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;

import javax.annotation.concurrent.Immutable;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Immutable
@JsonDeserialize
public final class UserState implements CompressedJsonable {
    static final UserState EMPTY = new UserState();

    static class LoginFailedException extends TransportException {
        LoginFailedException() {
            super(TransportErrorCode.NotFound, "Not found");
        }
    }

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
    private final UserService.PublicProfile publicProfile;

    private UserState() {
        this.passwordHash = null;
        this.passwordHashSalt = null;
        this.publicProfile = UserService.PublicProfile.EMPTY;
    }

    @JsonCreator
    public UserState(byte[] passwordHash, String passwordHashSalt, UserService.PublicProfile publicProfile) {
        this.passwordHash = passwordHash;
        this.passwordHashSalt = passwordHashSalt;
        this.publicProfile = publicProfile;
    }

    /**
     * Indicates whether the given {{password}} matches the registered one
     * @param password the password to check
     * @return true if passwords match
     */
    String login(String password) throws LoginFailedException {
        if (Arrays.equals(hashPassword(this.passwordHashSalt, password), this.passwordHash)) {
            return UUID.randomUUID().toString();
        } else {
            throw new LoginFailedException();
        }
    }

    UserService.PublicProfile getPublicProfile() {
        return this.publicProfile;
    }

    UserState withPublicProfile(UserService.PublicProfile publicProfile) {
        return new UserState(this.passwordHash, this.passwordHashSalt, publicProfile);
    }
}
