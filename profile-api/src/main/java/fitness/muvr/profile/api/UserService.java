package fitness.muvr.profile.api;

import akka.Done;
import akka.NotUsed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;
import scala.xml.dtd.EMPTY;

import javax.annotation.concurrent.Immutable;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

/**
 * API for the User entity
 */
public interface UserService extends Service {

    /**
     * The login message with {@link #username} and {@link #password}
     */
    @Immutable
    @JsonDeserialize
    class LoginMessage {
        public final String password;
        public final String username;

        @JsonCreator
        public LoginMessage(String password, String username) {
            this.password = password;
            this.username = username;
        }
    }

    /**
     * Register message with desired {@link #username} and {@link #password}
     */
    @Immutable
    @JsonDeserialize
    class RegisterMessage {
        public final String password;
        public final String username;

        @JsonCreator
        public RegisterMessage(String password, String username) {
            this.password = password;
            this.username = username;
        }
    }

    /**
     * A public profile message (both set and get) with all publicly-available profile fields.
     */
    @Immutable
    @JsonDeserialize
    @JsonSerialize
    class PublicProfile {
        public static final PublicProfile EMPTY = new PublicProfile("", "");
        public final String firstName;
        public final String lastName;

        @JsonCreator
        public PublicProfile(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PublicProfile that = (PublicProfile) o;
            return Objects.equal(firstName, that.firstName) &&
                   Objects.equal(lastName, that.lastName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(firstName, lastName);
        }
    }

    /**
     * Login service call
     * @return the service call
     */
    ServiceCall<NotUsed, LoginMessage, String> login();

    /**
     * Register service call
     * @return the service call
     */
    ServiceCall<NotUsed, RegisterMessage, String> register();

    /**
     * Get public profile service call
     * @return the service call
     */
    ServiceCall<String, NotUsed, PublicProfile> getPublicProfile();

    /**
     * Set public profile service call
     * @return the service call
     */
    ServiceCall<String, PublicProfile, Done> setPublicProfile();

    /**
     * The service descriptor for the user service
     * @return the descriptor
     */
    @Override
    default Descriptor descriptor() {
        return named("user").with(
                restCall(Method.PUT,  "/user", login()),
                restCall(Method.POST, "/user", register()),
                restCall(Method.POST, "/user/:id", setPublicProfile()),
                restCall(Method.GET,  "/user/:id", getPublicProfile())
        ).withAutoAcl(true);
    }
}
