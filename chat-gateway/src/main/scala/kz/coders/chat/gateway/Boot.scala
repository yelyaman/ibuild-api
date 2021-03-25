package kz.coders.chat.gateway

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import kz.amqp.library.{AmqpConsumer, RabbitMqConnection}
import kz.coders.chat.gateway.actors.amqp.{AmqpListenerActor, AmqpPublisherActor}
import kz.coders.chat.gateway.actors.bots.RequesterActor
import kz.coders.chat.gateway.actors.dialogflow.DialogFlowActor

import scala.util.Failure
import scala.util.Success

object Boot extends App {

  implicit val system: ActorSystem        = ActorSystem("chat-gateway")
  implicit val materializer: Materializer = Materializer(system)
  val config: Config                      = ConfigFactory.load()

  val username    = config.getString("rabbitmq.username")
  val password    = config.getString("rabbitmq.password")
  val host        = config.getString("rabbitmq.host")
  val port        = config.getInt("rabbitmq.port")
  val virtualHost = config.getString("rabbitmq.virtualHost")

  val gatewayInExchange = config.getString("rabbitmq.gatewayInExchange")
  val gatewayOutExchange = config.getString("rabbitmq.gatewayOutExchange")
  val telegramRequestQueue = config.getString("rabbitmq.telegramRequestQueue")
  val httpQueue = config.getString("rabbitmq.httpRequestQueue")

  val telegramRequestRoutingKey = config.getString("rabbitmq.telegramRequestRoutingKey")
  val httpRequestRoutingKey = config.getString("rabbitmq.httpRequestRoutingKey")

  val connection =
    RabbitMqConnection.getRabbitMqConnection(username, password, host, port, virtualHost)

  val channel = connection.createChannel()

  RabbitMqConnection.declareExchange(channel, gatewayInExchange, "topic") match {
    case Success(_) => system.log.info("successfully declared 'X:chat.in.gateway' exchange")
    case Failure(exception) =>
      system.log.error(s"couldn't declare 'X:chat.in.gateway' exchange ${exception.getMessage}")
  }

  RabbitMqConnection.declareExchange(channel, gatewayOutExchange, "topic") match {
    case Success(_) => system.log.info("successfully declared 'X:chat.out.gateway' exchange")
    case Failure(exception) =>
      system.log.error(s"couldn't declare 'X:chat.out.gateway' exchange ${exception.getMessage}")
  }

  RabbitMqConnection.declareAndBindQueue(channel, telegramRequestQueue, gatewayInExchange, telegramRequestRoutingKey)
  RabbitMqConnection.declareAndBindQueue(channel, httpQueue, gatewayInExchange, httpRequestRoutingKey)

  val publisher = system.actorOf(AmqpPublisherActor.props(channel, config))
  val requester = system.actorOf(RequesterActor.props(publisher, config))
  val dialogflowRef = system.actorOf(DialogFlowActor.props(publisher, requester, config))
  val listener      = system.actorOf(AmqpListenerActor.props(dialogflowRef))

  channel.basicConsume(telegramRequestQueue, AmqpConsumer(listener))
  channel.basicConsume(httpQueue, AmqpConsumer(listener))

}
