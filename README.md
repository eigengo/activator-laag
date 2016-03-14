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
case class PublicProfile(firstName: String, lastName: String)

case class UserState(
  passwordHash: Array[Byte], 
  passwordHashSalt: String, 
  publicProfile: PublicProfile = PublicProfile("", "")) 

case class CommandEnvelope(id: String, command: Any)

object User {
  
  object commands {
    case class Register(password: String)
    case class Login(password: String)
    case class SetPublicProfile(publicProfile: PublicProfile)
    case object GetPublicProfile
  }
  
  object events {
    case class Registered(passwordHash: Array[Byte], passwordHashSalt: String)
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

class UserProfile extends PersistentActor with ActorLogging {

  private var state: UserState = _

  override def persistenceId: String = s"user-${self.path.name}"

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, offeredSnapshot: UserState) ⇒
      state = offeredSnapshot
      context.become(registered)
   }

  override def receiveCommand: Receive = notRegistered

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
required monitoring, service registry, ...! 

# Enter Lagom
