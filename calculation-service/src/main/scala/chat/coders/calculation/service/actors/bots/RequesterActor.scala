package chat.coders.calculation.service.actors.bots

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import chat.coders.calculation.service.actors.amqp.AmqpPublisherActor.SendResponse
import com.typesafe.config.Config
import kz.domain.library.messages.calculations._
import kz.domain.library.messages.citybus.CitybusDomain._
import kz.domain.library.messages.github.GithubDomain._
import kz.domain.library.messages.{ChatResponse, Sender, Serializers, UserMessages}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object RequesterActor {
  def props(publisherActor: ActorRef, config: Config)(implicit system: ActorSystem, materializer: Materializer): Props =
    Props(new RequesterActor(publisherActor, config))
}

class RequesterActor(publisherActor: ActorRef, config: Config)(
  implicit
  val system: ActorSystem,
  materializer: Materializer
) extends Actor
    with ActorLogging
    with Serializers {
  implicit val ex: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout     = 8.seconds

  override def receive: Receive = {
    case request: UserMessages =>
      request.message.get match {
        case obj: SlabFoundationRequest =>
          val aLength = obj.a_length
          val bLength = obj.b_length
          val cLength = obj.c_length
          val cost    = obj.cost

          val slabArea       = aLength * bLength
          val concreteVolume = aLength * bLength * cLength
          val perimeter      = (aLength + bLength) * 2
          val sidePlateArea  = aLength * cLength * 2 + bLength * cLength * 2
          val weight         = concreteVolume * 2400
          val soilLoad       = cLength * 0.24
          val totalCost      = concreteVolume * cost

          val message =
            SlabFoundationResponse(slabArea, concreteVolume, perimeter, sidePlateArea, weight, soilLoad, totalCost)
          publisherActor ! SendResponse(request.replyTo.get, ChatResponse(request.sender, message))
        case obj: WallBrickCalcRequest =>
          val wall_length      = obj.wall_length
          val wall_height      = obj.wall_height
          val thickness        = obj.thickness
          val mortar_thickness = obj.mortar_thickness
          val brick_weight     = obj.brick_weight
          val brick_cost       = obj.brick_cost

          val result = wall_length * wall_height * thickness * mortar_thickness * brick_weight * brick_cost
          println(result)

          publisherActor ! SendResponse(
            request.replyTo.get,
            ChatResponse(request.sender, WallBrickCalcResponse(result))
          )
      }

    case obj => log.warning(s"request unhandled ${obj.getClass.getName}")
  }
}
