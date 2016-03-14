package fitness.muvr.profile.impl;

import com.lightbend.lagom.javadsl.persistence.PersistentEntity;

import java.util.Optional;

public class User extends PersistentEntity<UserCommand, UserEvent, UserState> {

    private Behavior registeredBehavior(UserState userState) {
        BehaviorBuilder b = newBehaviorBuilder(userState);
        b.setReadOnlyCommandHandler(UserCommand.Login.class, (cmd, ctx) -> {
            try {
                ctx.reply(userState.login(cmd.password));
            } catch (UserState.LoginFailedException ex) {
                ctx.commandFailed(ex);
            }
        });
        b.setCommandHandler(UserCommand.SetPublicProfile.class, (cmd, ctx) ->
            ctx.thenPersist(new UserEvent.PublicProfileSet(cmd.publicProfile), evt -> ctx.done())
        );
        b.setEventHandler(UserEvent.PublicProfileSet.class, (evt) ->
            userState.withPublicProfile(evt.publicProfile)
        );
        b.setReadOnlyCommandHandler(UserCommand.GetPublicProfile.class, (cmd, ctx) ->
            userState.getPublicProfile().ifPresent(ctx::reply)
        );
        return b.build();
    }

    private Behavior notRegisteredBehavior() {
        BehaviorBuilder b = newBehaviorBuilder(UserState.EMPTY);
        b.setCommandHandler(UserCommand.Register.class, (cmd, ctx) ->
                ctx.thenPersist(new UserEvent.Registered(cmd.password), evt -> ctx.done())
        );
        b.setEventHandlerChangingBehavior(UserEvent.Registered.class, (evt) ->
                registeredBehavior(new UserState(evt.passwordHash, evt.passwordHashSalt, Optional.empty()))
        );
        return b.build();
    }

    @Override
    public Behavior initialBehavior(Optional<UserState> snapshotState) {
        return snapshotState.map(this::registeredBehavior).orElse(notRegisteredBehavior());
    }

}
