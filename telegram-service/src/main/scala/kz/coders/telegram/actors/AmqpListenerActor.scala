package kz.coders.telegram.actors

import akka.actor.{Actor, ActorLogging, Props}
import kz.coders.telegram.TelegramService
import kz.domain.library.messages.{ChatResponse, Serializers, TelegramResponse}
import org.json4s.jackson.JsonMethods.parse

object AmqpListenerActor {
  def props(service: TelegramService) = Props(new AmqpListenerActor(service))
}

class AmqpListenerActor(service: TelegramService) extends Actor with ActorLogging with Serializers {

  override def receive: Receive = {
    case msg: String =>
      log.info(s"Consumed Message $msg")
      val message = parse(msg).extract[ChatResponse]
      val replyText = message.response match {
        case TelegramResponse(message) => message
      }
      service.replyToUser(replyText, message.sender)
  }
}
