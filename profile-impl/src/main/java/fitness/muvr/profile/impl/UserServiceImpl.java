package fitness.muvr.profile.impl;

import akka.NotUsed;
import akka.japi.Pair;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.api.deser.ExceptionMessage;
import com.lightbend.lagom.javadsl.api.transport.MessageProtocol;
import com.lightbend.lagom.javadsl.api.transport.ResponseHeader;
import com.lightbend.lagom.javadsl.api.transport.TransportErrorCode;
import com.lightbend.lagom.javadsl.api.transport.TransportException;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import com.lightbend.lagom.javadsl.server.HeaderServiceCall;
import fitness.muvr.profile.api.UserService;
import org.pcollections.HashPMap;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Optional;

class UserServiceImpl implements UserService {
    private final PersistentEntityRegistry persistentEntityRegistry;

    @Inject
    UserServiceImpl(PersistentEntityRegistry persistentEntityRegistry) {
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(User.class);
    }

    @Override
    public ServiceCall<NotUsed, LoginMessage, Optional<String>> login() {
        return (unused, request) -> {
            // Look up the hello world entity for the given ID.
            PersistentEntityRef<UserCommand> ref = persistentEntityRegistry.refFor(User.class, request.username);
            // Ask the entity the Hello command.
            return ref.ask(new UserCommand.Login(request.password));
        };
    }

    @Override
    public ServiceCall<NotUsed, RegisterMessage, String> register() {
        return (notUsed, request) -> {
            String id = request.username;
            // Look up the hello world entity for the given ID.
            PersistentEntityRef<UserCommand> ref = persistentEntityRegistry.refFor(User.class, id);
            // Ask the entity the Hello command.
            return ref.ask(new UserCommand.Register(request.password)).thenApply((x) -> id);
        };
    }
}
