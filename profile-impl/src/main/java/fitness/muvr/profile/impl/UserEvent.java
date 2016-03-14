package fitness.muvr.profile.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import fitness.muvr.profile.api.UserService;

import javax.annotation.concurrent.Immutable;
import java.util.UUID;

/**
 * User events
 */
public interface UserEvent extends Jsonable {

    /**
     * User profile processed and set
     */
    @Immutable
    @JsonDeserialize
    class PublicProfileSet implements UserEvent {
        final UserService.PublicProfile publicProfile;

        public PublicProfileSet(UserService.PublicProfile publicProfile) {
            this.publicProfile = publicProfile;
        }
    }

    /**
     * Registered with hashed and salted password
     */
    @Immutable
    @JsonDeserialize
    class Registered implements UserEvent {
        final byte[] passwordHash;
        final String passwordHashSalt;

        @JsonCreator
        public Registered(String password) {
            this.passwordHashSalt = UUID.randomUUID().toString();
            this.passwordHash = UserState.hashPassword(this.passwordHashSalt, password);
        }
    }

}
