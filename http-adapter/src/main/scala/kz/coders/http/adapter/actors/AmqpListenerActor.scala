package kz.coders.http.adapter.actors

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.util.Timeout
import kz.domain.library.messages.{ ChatResponse, HttpSender, Serializers, UserMessages }
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

object AmqpListenerActor {
  def props()(implicit system: ActorSystem): Props = Props(new AmqpListenerActor())
}

class AmqpListenerActor()(implicit val system: ActorSystem) extends Actor with ActorLogging with Serializers {

  implicit val ex: ExecutionContext = context.dispatcher
  implicit val timeout: Timeout     = 20.seconds

  override def receive: Receive = {
    case msg: String =>
      val gatewayMessage = parse(msg).extract[ChatResponse]
      val sender         = gatewayMessage.sender.asInstanceOf[HttpSender]
      system.actorSelection(sender.actorPath).resolveOne.onComplete {
        case Success(ref) =>
          ref ! gatewayMessage
          log.info(s"Listener received message $gatewayMessage")
        case Failure(exception) => log.warning(s"actor by path ${sender.actorPath} not fount: ${exception.getMessage}")
      }

  }
}
