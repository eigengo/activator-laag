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
    public ServiceCall<NotUsed, RegisterMessage, String> register() {
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
    public ServiceCall<String, PublicProfile, Done> setPublicProfile() {
        return (id, request) -> {
            PersistentEntityRef<UserCommand> ref = 
              persistentEntityRegistry.refFor(User.class, id);
            return ref.ask(new UserCommand.SetPublicProfile(request));
        };
    }
}
```
