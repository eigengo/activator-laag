package fitness.muvr.profile.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.lightbend.lagom.serialization.Jsonable;
import fitness.muvr.profile.api.UserService;

import javax.annotation.concurrent.Immutable;
import java.util.UUID;

public interface UserEvent extends Jsonable {

    @Immutable
    @JsonDeserialize
    class PublicProfileSet implements UserEvent {
        final UserService.PublicProfile publicProfile;

        public PublicProfileSet(UserService.PublicProfile publicProfile) {
            this.publicProfile = publicProfile;
        }
    }

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
