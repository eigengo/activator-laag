package fitness.muvr.profile.impl;

import akka.NotUsed;
import com.lightbend.lagom.javadsl.api.ServiceCall;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRef;
import com.lightbend.lagom.javadsl.persistence.PersistentEntityRegistry;
import fitness.muvr.profile.api.UserService;

import javax.inject.Inject;

class UserServiceImpl implements UserService {
    private final PersistentEntityRegistry persistentEntityRegistry;

    @Inject
    UserServiceImpl(PersistentEntityRegistry persistentEntityRegistry) {
        this.persistentEntityRegistry = persistentEntityRegistry;
        persistentEntityRegistry.register(User.class);
    }

    @Override
    public ServiceCall<NotUsed, LoginMessage, String> login() {
        return (unused, request) -> {
            PersistentEntityRef<UserCommand> ref = persistentEntityRegistry.refFor(User.class, request.username);
            return ref.ask(new UserCommand.Login(request.password));
        };
    }

    @Override
    public ServiceCall<NotUsed, RegisterMessage, String> register() {
        return (notUsed, request) -> {
            String id = request.username;
            PersistentEntityRef<UserCommand> ref = persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.Register(request.password)).thenApply((x) -> id);
        };
    }

    @Override
    public ServiceCall<String, NotUsed, PublicProfile> getPublicProfile() {
        return (id, request) -> {
            PersistentEntityRef<UserCommand> ref = persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.GetPublicProfile());
        };
    }

    @Override
    public ServiceCall<String, PublicProfile, NotUsed> setPublicProfile() {
        return (id, request) -> {
            PersistentEntityRef<UserCommand> ref = persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.SetPublicProfile(request));
        };
    }
}
