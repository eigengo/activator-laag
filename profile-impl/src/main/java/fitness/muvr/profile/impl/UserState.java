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

/**
 * The state of the user entity
 */
@Immutable
@JsonDeserialize
public final class UserState implements CompressedJsonable {
    /** Empty state */
    static final UserState EMPTY = new UserState();

    /** Login failed */
    static class LoginFailedException extends TransportException {
        LoginFailedException() {
            super(TransportErrorCode.NotFound, "Not found");
        }
    }

    /**
     * Hashes the {{password}} with the tiven {{passwordHashSalt}}, returning the hash
     *
     * @param passwordHashSalt the hash salt
     * @param password the clear-text password
     * @return the digested password
     */
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
     * Checks the password, returning a valid login token.
     * @param password the given password
     * @return the login token
     * @throws LoginFailedException if the passwords do not match
     */
    String login(String password) throws LoginFailedException {
        if (Arrays.equals(hashPassword(this.passwordHashSalt, password), this.passwordHash)) {
            return UUID.randomUUID().toString();
        } else {
            throw new LoginFailedException();
        }
    }

    /**
     * Gets the public profile
     * @return the public profile
     */
    UserService.PublicProfile getPublicProfile() {
        return this.publicProfile;
    }

    /**
     * Copies this instance, setting the {{publicProfile}}
     * @param publicProfile the public profile to be set
     * @return copy of this with {@link #publicProfile} set
     */
    UserState withPublicProfile(UserService.PublicProfile publicProfile) {
        return new UserState(this.passwordHash, this.passwordHashSalt, publicProfile);
    }
}
