#Lagom eye for the Akka guy

In this post, I will attempt to translate a naïve _user service_ from Akka and 
Scala to Lagom. The (REST) API is going to very simple: it includes calls to

* register a user: POST ``/user``
* login a user: PUT ``/user``
* get and set public profile: GET and SET ``/user/:id`` 

In practice, imagine registering a user by 

```sh
# register
curl -H "Content-Type: application/json" -X POST -d '{"username":"xyz","password":"xyz"}' http://localhost:9000/user
# login
curl -H "Content-Type: application/json" -X PUT -d '{"username":"xyz","password":"xyz"}' http://localhost:9000/user
# get profile
curl -H "Content-Type: application/json" -X GET http://localhost:9000/user/xyz
# set profile
curl -H "Content-Type: application/json" -X POST -d '{"firstName":"John","lastName":"Smith"}' http://localhost:9000/user/xyz
# get profile
curl -H "Content-Type: application/json" -X GET http://localhost:9000/user/xyz
```

Naïvely simple, I know, but let this be a start. If one were modelling this
in Scala and Akka, one would have a ``User`` actor with ``UserState``. The actor
synchronizes and maintains access to the state; together they represent the
_user_ entity. The last thing to resolve is the identity of the _user_ actor
and its scaling. One might use the Akka cluster sharding, where the sharding
code routes messages to the individual _user_ actors. To be able to do that,
it needs to be able to extract the identity of the actor from the incoming message.

I shall begin by tackling the _user_ actor and the _user state_. 

```scala
/// The public profile 
case class PublicProfile(firstName: String, lastName: String)

/// The state of the user entity
case class UserState(
  passwordHash: Array[Byte], 
  passwordHashSalt: String, 
  publicProfile: PublicProfile = PublicProfile("", "")) 

/// A command envelope that directs messages to a particular entity 
case class CommandEnvelope(id: String, command: Any)

/// The companion
object User {
  
  /// Available commands
  object commands {
    /// Registere a user with the given password
    case class Register(password: String)
    /// Login the user with the given password
    case class Login(password: String)
    /// Set the user's public profile
    case class SetPublicProfile(publicProfile: PublicProfile)
    /// Get the user's public profile
    case object GetPublicProfile
  }
  
  /// The events
  object events {
    /// The user has been registered
    case class Registered(passwordHash: Array[Byte], passwordHashSalt: String)
    /// The public profile has been set
    case class PublicProfileSet(publicProfile: PublicProfile)
  }
  
  val shardName = "user-profile"
  val props = Props[UserProfile]
    
  val idExtractor: ShardRegion.IdExtractor = {
    case CommandEnvelope(id, _) ⇒ x 
  }
  
  val shardResolver: ShardRegion.ShardResolver = {
    case CommandEnvelope(id, _) ⇒ x
  }

}

/// User entity
class User extends PersistentActor with ActorLogging {
  import User._

  /// the state
  private var state: UserState = _

  /// persistence identity
  override def persistenceId: String = s"user-${self.path.name}"

  /// recover behaviour
  override def receiveRecover: Receive = {
    case SnapshotOffer(_, offeredSnapshot: UserState) ⇒
      state = offeredSnapshot
      context.become(registered)
   }

  /// initial behaviour
  override def receiveCommand: Receive = notRegistered

  /// not-yet-registred behaviour
  private def notRegistered: Receive = {
    case r@commands.Register(password) ⇒
      val salt = UUID.randomUUID().toString()
      val hash = UserState.hashPassword(salt, password)
      persist(events.Registered(hash, salt)) { _ ⇒
        sender() ! Done
      }
    case events.Registered(hash, salt) ⇒
      state = UserState(hash, salt)
      context.become(registered)
  }
  
  /// registered behaviour
  private def registered: Receive = {
    case commands.Login(password) ⇒
      // ...
    case commands.SetPublicProfile(publicProfile) ⇒
      persist(events.PublicProfileSet(publicProfile)) { _ ⇒
        sender() ! Done
      }
    case commands.GetPublicProfile ⇒
      sender() ! state.publicProfile

    case events.PublicProfileSet(publicProfile) ⇒
      state = state.copy(publicProfile = publicProfile)
  }

}
```

To make all of this "sing", it is necessary to configure the Akka cluster sharding

```scala
val user = ClusterSharding(system).start(
      typeName = User.shardName,
      entryProps = Some(User.props),
      idExtractor = User.idExtractor,
      shardResolver = User.shardResolver)
```

Now that this is done, one can use the ``user: ActorRef`` to send messages to the
_user_ entity. And by messages, we mean ``CommandEnvelope`` instances, which pack
together the identity of the user actor and the message to be sent to it.   
But that's not all: the calls to ``persist`` require Akka Persistence to be
configured, typically in the ``application.conf`` file. Together with the right
plugin (say using the excellent [Akka Persistence Cassandra](https://github.com/krasserm/akka-persistence-cassandra)).

Phew! This is a lot of work. And there's no sign of REST API, never mind the
required monitoring, service registry, ...! There are many other blog posts 
that explore Akka clustering, Spray APIs, monitoring and service registry at 
[Cake Solutions Blog](http://www.cakesolutions.net/teamblogs). 

# Enter Lagom
Though I could stick with Scala, I shall abandon the creature-comforts of Scala 
for Java in this first Lagom example. The implementation will do everything that
the Akka / Scala code above did, but with REST APIs, with monitoring, with
service registry. (Though I will stop at the REST APIs in this post.)

## The service
Lagom uses the term service to mean exposed, discoverable functionality; in this
particular case, it will be a REST API.

```java
public interface UserService extends Service {

    /**
     * The login message with {@link #username} and {@link #password}
     */
    @Immutable
    @JsonDeserialize
    class LoginMessage {
        public final String password;
        public final String username;
        // @JsonCreator constructor
        // equals & hashCode
        // toString
    }

    /**
     * Register message with desired {@link #username} and {@link #password}
     */
    @Immutable
    @JsonDeserialize
    class RegisterMessage {
        public final String password;
        public final String username;
        // ...
    }

    /**
     * A public profile message (both set and get) with all publicly-available profile fields.
     */
    @Immutable
    @JsonDeserialize
    @JsonSerialize
    class PublicProfile {
        public final String firstName;
        public final String lastName;
        // ...
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
    ServiceCall<NotUsed, RegisterMessage, NotUsed> register();

    /**
     * Get public profile service call
     * @return the service call
     */
    ServiceCall<String, NotUsed, PublicProfile> getPublicProfile();

    /**
     * Set public profile service call
     * @return the service call
     */
    ServiceCall<String, PublicProfile, NotUsed> setPublicProfile();

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
```

The ``UserService`` exposes four endpoints as REST calls. The endpoints
allow a user to be registered, to login, to set and get his or her public 
profile. The messages are serialized from JSON, hence the need for all
the [Jackson](https://github.com/FasterXML/jackson) annotations.   
Taking a closer look at the abstract service calls, one can see three
type parameters: the identity, the request, and the response. The request
and response types are clear; the values of the identity type identify
the entity the service is going to be "talking" to.

Crucially, the service interface defines the ``default Descriptor descriptor()`` 
method, which—ehm—describes the service. Moving on swiftly, then!

## Service implementation
The ``UserServiceImpl`` provides the implementation of the ``UserService``: at
startup, it needs to register the new ``User`` entity, and then it can implement
the service calls by looking up the appropriate entity and asking it for a response.

```java
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
            PersistentEntityRef<UserCommand> ref = 
              persistentEntityRegistry.refFor(User.class, request.username);
            return ref.ask(new UserCommand.Login(request.password));
        };
    }

    @Override
    public ServiceCall<NotUsed, RegisterMessage, NotUsed> register() {
        return (notUsed, request) -> {
            String id = request.username;
            PersistentEntityRef<UserCommand> ref = 
              persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.Register(request.password));
        };
    }

    @Override
    public ServiceCall<String, NotUsed, PublicProfile> getPublicProfile() {
        return (id, request) -> {
            PersistentEntityRef<UserCommand> ref = 
              persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.GetPublicProfile());
        };
    }

    @Override
    public ServiceCall<String, PublicProfile, NotUsed> setPublicProfile() {
        return (id, request) -> {
            PersistentEntityRef<UserCommand> ref = 
              persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.SetPublicProfile(request));
        };
    }
}
```

### The commands
The commands for the user entity will "borrow" the ones from the Akka / Scala
code above. Except they'll be translated to Java.

```java
public interface UserCommand extends Jsonable {

    final class GetPublicProfile implements UserCommand, CompressedJsonable, 
        PersistentEntity.ReplyType<UserService.PublicProfile> {

    }

    final class SetPublicProfile implements UserCommand, CompressedJsonable, 
        PersistentEntity.ReplyType<NotUsed> {
        final UserService.PublicProfile publicProfile;
    }

    final class Login implements UserCommand, CompressedJsonable, 
        PersistentEntity.ReplyType<String> {
        final String password;
    }

    final class Register implements UserCommand, CompressedJsonable, 
        PersistentEntity.ReplyType<NotUsed>  {
        final String password;
    }

}

```

I have left out the pesky annotationses, constructorses, equalses, hashCodeses
and other preciouses! Unlike the Scala code, the Lagom code adds the 
``PersistentEntity.ReplyType``, which marks the expected result of
_ask_-ing for a response. Consider the ``getPublicProfile()`` implementation again:
 
```java
public ServiceCall<String, NotUsed, PublicProfile> getPublicProfile() {
    return (id, request) -> {
        PersistentEntityRef<UserCommand> ref = 
          persistentEntityRegistry.refFor(User.class, id);
        return ref.ask(new UserCommand.GetPublicProfile());
    };
}
```

Here, ``ref.ask(new UserCommand.GetPublicProfile())`` means that the 
response's type is ``PublicProfile``, which matches the response type
of the service call. Happy days!
 
Before I can implement the ``User`` entity and its state, I will translate 
the Scala events to their Lagom / Java counterparts.

```java
public interface UserEvent extends Jsonable {

    class PublicProfileSet implements UserEvent {
        final UserService.PublicProfile publicProfile;
        // ...
    }

    class Registered implements UserEvent {
        final byte[] passwordHash;
        final String passwordHashSalt;
        // ...
    }

}
```

### The state
The ``UserState`` is a mechanical translation of its Scala counterpart.

```java
@Immutable
@JsonDeserialize
public final class UserState implements CompressedJsonable {

    /** Login failed */
    static class LoginFailedException extends TransportException {
        LoginFailedException() {
            super(TransportErrorCode.NotFound, "Not found");
        }
    }

    /**
     * Hashes the {{password}} with the tiven {{passwordHashSalt}}, returning the hash
     *
     * @param passwordHashSalt the hash salt
     * @param password the clear-text password
     * @return the digested password
     */
    static byte[] hashPassword(String passwordHashSalt, String password) {
        try {
            MessageDigest instance = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_512);
            return instance.digest((passwordHashSalt + password).getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private final byte[] passwordHash;
    private final String passwordHashSalt;
    private final UserService.PublicProfile publicProfile;

    @JsonCreator
    public UserState(byte[] passwordHash, String passwordHashSalt, UserService.PublicProfile publicProfile) {
        this.passwordHash = passwordHash;
        this.passwordHashSalt = passwordHashSalt;
        this.publicProfile = publicProfile;
    }

    /**
     * Checks the password, returning a valid login token.
     * @param password the given password
     * @return the login token
     * @throws LoginFailedException if the passwords do not match
     */
    String login(String password) throws LoginFailedException {
        if (Arrays.equals(hashPassword(this.passwordHashSalt, password), this.passwordHash)) {
            return UUID.randomUUID().toString();
        } else {
            throw new LoginFailedException();
        }
    }

    /**
     * Gets the public profile
     * @return the public profile
     */
    UserService.PublicProfile getPublicProfile() {
        return this.publicProfile;
    }

    /**
     * Copies this instance, setting the {{publicProfile}}
     * @param publicProfile the public profile to be set
     * @return copy of this with {@link #publicProfile} set
     */
    UserState withPublicProfile(UserService.PublicProfile publicProfile) {
        return new UserState(this.passwordHash, this.passwordHashSalt, publicProfile);
    }
}
```

With this in place, I can implement the ``User`` entity. It will follow
similar pattern to the ``PersistentActor`` in Akka and Scala. 

### The entity

In the entity, I define the two behaviours: ``registered()`` and 
``notRegistered()``. The behaviour defines the way in which the entity
reacts to the received commands and events. Notice that Lagom makes
a distinction between a command and a read-only command; though events
are handled in exactly the same way as Akka.

So, there is the ``notRegistered()`` behaviour, which reacts to the
``UserCommand.Register`` command by persisting the ``UserEvent.Registered``
event with the hashed and salted password and switches behaviour to
``registered()``.

In ``registered()``, the entity reacts to the ``Login`` command (by replying
with some token if the password matches), to the ``GetPublicProfile`` command
(by replying with the state's public profile). Finally, it allows the 
public profile to be set by reacting to the ``SetPublicProfile`` command,
then in no-op validating it and generating the ``PublicProfileSet`` event,
and handling it by updating the user state.

```java
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
            ctx.thenPersist(new UserEvent.PublicProfileSet(cmd.publicProfile), evt -> ctx.reply(NotUsed.getInstance()))
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
        BehaviorBuilder b = newBehaviorBuilder(null);
        b.setCommandHandler(UserCommand.Register.class, (cmd, ctx) ->
            ctx.thenPersist(new UserEvent.Registered(cmd.password), evt -> ctx.reply(NotUsed.getInstance()))
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
```

# Configuring
Now, to run the Akka cluster application, one would have to create an object
with the ``main`` method. (The ``public static void main(String[] args)`` beast.) 
In that method, the ``ActorSystem``, cluster, API would have to be started; the
node would then have to join the cluster, ..., a whole lot of palaver. 

In Lagom, all that is needed is to define a module, which defines service
bindings, together with a dependency injection framework—not 
[Spring Framework](https://projects.spring.io/spring-framework/), the _other_ one.

```java
public class UserServiceModule extends AbstractModule implements ServiceGuiceSupport {

    @Override
    protected void configure() {
        bindServices(serviceBinding(UserService.class, UserServiceImpl.class));
    }
}
```

# Running & summary
All that remains for this post is to run the service. To do so, one has
to step out of the Java comfort zone and use [sbt](http://www.scala-sbt.org/). 
(Muahahaha!) For today, run ``sbt runAll``, then explore the wonders at
``localhost:8000`` and ``localhost:9000``.
