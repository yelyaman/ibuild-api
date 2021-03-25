package kz.coders.http.adapter.actors

import akka.actor.{ Actor, ActorLogging, Props }
import com.rabbitmq.client.{ Channel, MessageProperties }
import com.typesafe.config.Config
import kz.domain.library.messages.{ ChatResponse, HttpRequest, Serializers, UserMessages }
import org.json4s.jackson.Serialization.write

import scala.util.{ Failure, Success, Try }

object AmqpPublisherActor {
  def props(channel: Channel, config: Config): Props = Props(new AmqpPublisherActor(channel, config))

  case class SendResponse(routingKey: String, response: ChatResponse)
}

class AmqpPublisherActor(channel: Channel, config: Config) extends Actor with ActorLogging with Serializers {

  val gatewayInExchange: String     = config.getString("rabbitmq.gatewayInExchange")
  val httpRequestRoutingKey: String = config.getString("rabbitmq.httpRequestRoutingKey")
  val calcRequestRoutingKey: String = config.getString("rabbitmq.calcRequestRoutingKey")

  override def receive: Receive = {
    case request: UserMessages =>
      log.info(s"Publisher received request => ${request.message}")
      val jsonRequest = write(request)
      request.message.get match {
        case obj: HttpRequest =>
          Try {
            channel.basicPublish(
              gatewayInExchange,
              httpRequestRoutingKey,
              MessageProperties.TEXT_PLAIN,
              jsonRequest.getBytes()
            )
          } match {
            case Success(_)  => log.info(s"successfully sent message to chat-gateway $jsonRequest")
            case Failure(ex) => log.warning(s"couldn't message ${ex.getMessage}")
          }
        case _ =>
          Try {
            channel.basicPublish(
              gatewayInExchange,
              calcRequestRoutingKey,
              MessageProperties.TEXT_PLAIN,
              jsonRequest.getBytes()
            )
          } match {
            case Success(_)  => log.info(s"successfully sent message to chat-gateway $jsonRequest")
            case Failure(ex) => log.warning(s"couldn't message ${ex.getMessage}")
          }
      }

  }

}
