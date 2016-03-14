package fitness.muvr.profile.impl;

import akka.Done;
import com.lightbend.lagom.javadsl.persistence.PersistentEntity;
import fitness.muvr.profile.api.UserService;

import java.util.Optional;

public class User extends PersistentEntity<UserCommand, UserEvent, UserState> {

    private Behavior registeredBehavior(final UserState initialState) {
        BehaviorBuilder b = newBehaviorBuilder(initialState);
        b.setReadOnlyCommandHandler(UserCommand.Login.class, (cmd, ctx) -> {
            try {
                ctx.reply(state().login(cmd.password));
            } catch (UserState.LoginFailedException ex) {
                ctx.commandFailed(ex);
            }
        });
        b.setCommandHandler(UserCommand.SetPublicProfile.class, (cmd, ctx) ->
            ctx.thenPersist(new UserEvent.PublicProfileSet(cmd.publicProfile), evt -> ctx.reply(Done.getInstance()))
        );
        b.setEventHandler(UserEvent.PublicProfileSet.class, (evt) ->
            state().withPublicProfile(evt.publicProfile)
        );
        b.setReadOnlyCommandHandler(UserCommand.GetPublicProfile.class, (cmd, ctx) ->
            ctx.reply(state().getPublicProfile())
        );
        return b.build();
    }

    private Behavior notRegisteredBehavior() {
        BehaviorBuilder b = newBehaviorBuilder(UserState.EMPTY);
        b.setCommandHandler(UserCommand.Register.class, (cmd, ctx) ->
                ctx.thenPersist(new UserEvent.Registered(cmd.password), evt -> ctx.reply(Done.getInstance()))
        );
        b.setEventHandlerChangingBehavior(UserEvent.Registered.class, (evt) ->
                registeredBehavior(new UserState(evt.passwordHash, evt.passwordHashSalt, UserService.PublicProfile.EMPTY))
        );
        return b.build();
    }

    @Override
    public Behavior initialBehavior(Optional<UserState> snapshotState) {
        return snapshotState.map(this::registeredBehavior).orElse(notRegisteredBehavior());
    }

}
