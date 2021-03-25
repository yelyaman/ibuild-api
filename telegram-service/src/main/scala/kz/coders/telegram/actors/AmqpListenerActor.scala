package kz.coders.telegram.actors

import akka.actor.{ Actor, ActorLogging, Props }
import kz.coders.telegram.TelegramService
import kz.domain.library.messages.{ ChatResponse, Serializers }
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

object AmqpListenerActor {
  def props(service: TelegramService) = Props(new AmqpListenerActor(service))
}

class AmqpListenerActor(service: TelegramService) extends Actor with ActorLogging with Serializers {

  override def receive: Receive = {
    case msg: String =>
      log.info(s"Consumed Message $msg")
      val message = parse(msg).extract[ChatResponse]
      service.replyToUser(message.response, message.sender)
  }
}
