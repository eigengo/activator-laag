package fitness.muvr.profile.impl;

import akka.Done;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import java.util.Optional;
import java.util.UUID;

public class User extends PersistentEntity<UserCommand, UserEvent, UserState> {

    private Behavior registeredBehavior(UserState userState) {
        BehaviorBuilder b = newBehaviorBuilder(userState);
        b.setReadOnlyCommandHandler(UserCommand.Login.class, (cmd, ctx) -> {
            if (userState.passwordMatches(cmd.password)) {
                ctx.reply(Optional.of(UUID.randomUUID().toString()));
            }
            ctx.reply(Optional.empty());
        }
        );
        return b.build();
    }

    private Behavior notRegisteredBehavior() {
        BehaviorBuilder b = newBehaviorBuilder(UserState.EMPTY);
        b.setCommandHandler(UserCommand.Register.class, (cmd, ctx) ->
                ctx.thenPersist(new UserEvent.Registered(cmd.password), evt -> ctx.reply(Done.getInstance()))
        );
        b.setEventHandlerChangingBehavior(UserEvent.Registered.class, (evt) ->
                registeredBehavior(new UserState(evt.passwordHash, evt.passwordHashSalt))
        );
        return b.build();
    }

    @Override
    public Behavior initialBehavior(Optional<UserState> snapshotState) {
        return snapshotState.map(this::registeredBehavior).orElse(notRegisteredBehavior());
    }

}
