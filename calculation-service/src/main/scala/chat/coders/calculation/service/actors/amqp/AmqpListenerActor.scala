package chat.coders.calculation.service.actors.amqp

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import kz.domain.library.messages.{ Serializers, UserMessages }
import org.json4s.jackson.JsonMethods.parse

object AmqpListenerActor {
  def props(dialogFlowActor: ActorRef): Props = Props(new AmqpListenerActor(dialogFlowActor))
}

class AmqpListenerActor(calculatorRef: ActorRef) extends Actor with ActorLogging with Serializers {

  override def receive: Receive = {
    case msg: String =>
      val userMessage = parse(msg).extract[UserMessages]
      log.info(s"Listener received message $userMessage")
      calculatorRef ! userMessage
  }
}
