package fitness.muvr.profile.api;

import akka.NotUsed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Objects;
import com.lightbend.lagom.javadsl.api.Descriptor;
import com.lightbend.lagom.javadsl.api.Service;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.transport.Method;

import javax.annotation.concurrent.Immutable;

import static com.lightbend.lagom.javadsl.api.Service.named;
import static com.lightbend.lagom.javadsl.api.Service.restCall;

public interface UserService extends Service {

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

    @Immutable
    @JsonDeserialize
    @JsonSerialize
    class PublicProfile {
        public final String firstName;
        public final String lastName;
        public final int age;

        @JsonCreator
        public PublicProfile(String firstName, String lastName, int age) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PublicProfile that = (PublicProfile) o;
            return age == that.age &&
                    Objects.equal(firstName, that.firstName) &&
                    Objects.equal(lastName, that.lastName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(firstName, lastName, age);
        }
    }

    ServiceCall<NotUsed, LoginMessage, String> login();

    ServiceCall<NotUsed, RegisterMessage, String> register();

    ServiceCall<String, NotUsed, PublicProfile> getPublicProfile();

    ServiceCall<String, PublicProfile, NotUsed> setPublicProfile();

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
