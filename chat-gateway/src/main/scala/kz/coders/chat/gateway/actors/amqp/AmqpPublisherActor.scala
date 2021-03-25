package kz.coders.chat.gateway.actors.amqp

import akka.actor.{ Actor, ActorLogging, Props }
import com.rabbitmq.client.{ Channel, MessageProperties }
import com.typesafe.config.Config
import kz.coders.chat.gateway.actors.amqp.AmqpPublisherActor.SendResponse
import kz.domain.library.messages.{ ChatResponse, Serializers }
import org.json4s.jackson.Serialization.write

object AmqpPublisherActor {
  def props(channel: Channel, config: Config): Props = Props(new AmqpPublisherActor(channel, config))

  case class SendResponse(routingKey: String, response: ChatResponse)
}

class AmqpPublisherActor(channel: Channel, config: Config) extends Actor with ActorLogging with Serializers {

  val gatewayOutExchange: String = config.getString("rabbitmq.gatewayOutExchange")

  override def receive: Receive = {
    case resp: SendResponse =>
      log.info(s"Publisher received resp => ${resp.response}")
      val response     = resp.response
      val jsonResponse = write(response)
      channel.basicPublish(
        gatewayOutExchange,
        resp.routingKey,
        MessageProperties.TEXT_PLAIN,
        jsonResponse.getBytes()
      )
  }

}
