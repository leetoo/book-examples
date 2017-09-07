package sample.chirper.friend.impl

import akka.Done
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity

class FriendEntity extends PersistentEntity {
  override type Command = FriendCommand
  override type Event = FriendEvent
  override type State = FriendState

  override def initialState: FriendState = FriendState(None)

  override def behavior = {
    case FriendState(None) => userNotCreated

    case FriendState(Some(x)) =>
      Actions()
        .onCommand[CreateUserCommand, Done] {
        case (CreateUserCommand(user), ctx, state) =>
          ctx.invalidCommand(s"User $x is already created")
          ctx.done
      }
        .orElse(addFriend).orElse(getUserCommand)
  }

  private val getUserCommand = Actions().onReadOnlyCommand[GetUser, GetUserReply] {
    case (GetUser(), ctx, state) => ctx.reply(GetUserReply(state.user))
  }


  val userNotCreated = {
    Actions()
      .onCommand[CreateUserCommand, Done] {
      case (CreateUserCommand(user), ctx, state) =>
        ctx.thenPersist(UserCreated(user.userId, user.name))(x => ctx.reply(Done))
    }
      .onEvent {
        case (UserCreated(userId, name, ts), state) => state
      }
  }

  val addFriend = {
    Actions().onCommand[AddFriend, Done] {
      case (AddFriend(id), ctx, FriendState(None)) =>
        ctx.invalidCommand(s"User $entityId is not  created")
        ctx.done
      case (AddFriend(id), ctx, FriendState(Some(user))) if user.friends.contains(id) =>
        ctx.reply(Done)
        ctx.done
      case (AddFriend(friendUserId), ctx, FriendState(Some(user))) =>
        ctx.thenPersist(FriendAdded(user.userId, friendUserId))(evt => ctx.reply(Done))
    }.onEvent {
      case (FriendAdded(userId, friendId, ts), state) => state.addFriend(friendId)
    }
  }

}
