package fitness.muvr.profile.api;

import akka.NotUsed;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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

    ServiceCall<NotUsed, LoginMessage, String> login();

    ServiceCall<NotUsed, RegisterMessage, String> register();

    @Override
    default Descriptor descriptor() {
        return named("user").with(
                restCall(Method.PUT,  "/user", login()),
                restCall(Method.POST, "/user", register())
        ).withAutoAcl(true);
    }
}
