package kz.coders.telegram

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.util.Timeout
import com.bot4s.telegram.api.RequestHandler
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.clients.FutureSttpClient
import com.bot4s.telegram.future.{ Polling, TelegramBot }
import com.bot4s.telegram.models.{ Chat, ChatType, Message}
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import kz.coders.telegram.actors.AmqpPublisherActor.SendMessage
import kz.domain.library.messages.{ Sender, TelegramSender }

import scala.concurrent.Future
import scala.concurrent.duration._

object TelegramService {
  def getSenderDetails(msg: Message): TelegramSender = {
    val chatId    = msg.chat.id
    val userId    = msg.from.map(_.id)
    val firstName = msg.from.map(_.firstName)
    val lastName  = msg.from.flatMap(_.lastName)
    val userName  = msg.from.flatMap(_.username)

    TelegramSender(chatId, userId, firstName, lastName, userName)
  }
}

class TelegramService(token: String, publisherActor: ActorRef, log: LoggingAdapter)
    extends TelegramBot
    with Polling
    with Commands[Future] {

  implicit val timeout: Timeout                      = 5.seconds
  implicit val backend: SttpBackend[Future, Nothing] = OkHttpFutureBackend()
  override val client: RequestHandler[Future]        = new FutureSttpClient(token)

  onMessage { implicit msg =>
    if (msg.location.isDefined) {
      val location = msg.location.getOrElse("").toString.split("\\(")(1).init.split(",").mkString("a")
      log.info(s"User sent location $location")
      val sender = TelegramService.getSenderDetails(msg)
      publisherActor ! SendMessage(sender, location)
      Future()
    } else {
      val sender = TelegramService.getSenderDetails(msg)
      publisherActor ! SendMessage(sender, msg.text.getOrElse(""))
      Future()
    }
  }

  def replyToUser(msg: String, sender: Sender): Unit = {
    val telegramSender = sender.asInstanceOf[TelegramSender]
    reply(msg)(
      Message(
        1,
        date = (new java.util.Date().getTime / 1000).toInt,
        chat = Chat(telegramSender.chatId, ChatType.Private)
      )
    )
  }
}
