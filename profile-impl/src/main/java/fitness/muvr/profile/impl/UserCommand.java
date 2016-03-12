package fitness.muvr.profile.impl;

import akka.Done;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.base.Objects;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import com.lightbend.lagom.serialization.CompressedJsonable;
import com.lightbend.lagom.serialization.Jsonable;

import javax.annotation.concurrent.Immutable;

public interface UserCommand extends Jsonable {

    @Immutable
    @JsonDeserialize
    final class Login implements UserCommand, CompressedJsonable, PersistentEntity.ReplyType<String> {
        final String password;

        @JsonCreator
        public Login(String password) {
            this.password = password;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Login login = (Login) o;
            return Objects.equal(password, login.password);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(password);
        }
    }

    @Immutable
    @JsonDeserialize
    final class Register implements UserCommand, CompressedJsonable, PersistentEntity.ReplyType<Done>  {
        final String password;

        @JsonCreator
        public Register(String password) {
            this.password = password;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Register that = (Register) o;
            return Objects.equal(password, that.password);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(password);
        }
    }

}
