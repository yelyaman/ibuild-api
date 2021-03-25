package chat.coders.calculation.service

import akka.actor.ActorSystem
import akka.stream.Materializer
import chat.coders.calculation.service.actors.amqp.{ AmqpListenerActor, AmqpPublisherActor }
import chat.coders.calculation.service.actors.bots.RequesterActor
import com.typesafe.config.{ Config, ConfigFactory }
import kz.amqp.library.{ AmqpConsumer, RabbitMqConnection }

import scala.util.{ Failure, Success }

object Boot extends App {

  implicit val system: ActorSystem        = ActorSystem("chat-gateway")
  implicit val materializer: Materializer = Materializer(system)
  val config: Config                      = ConfigFactory.load()

  val username    = config.getString("rabbitmq.username")
  val password    = config.getString("rabbitmq.password")
  val host        = config.getString("rabbitmq.host")
  val port        = config.getInt("rabbitmq.port")
  val virtualHost = config.getString("rabbitmq.virtualHost")

  val gatewayInExchange  = config.getString("rabbitmq.gatewayInExchange")
  val gatewayOutExchange = config.getString("rabbitmq.gatewayOutExchange")
  val calcRequestQueue   = config.getString("rabbitmq.calcResponseQueue")

  val calcRequestRoutingKey = config.getString("rabbitmq.calcRequestRoutingKey")

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

  RabbitMqConnection.declareAndBindQueue(channel, calcRequestQueue, gatewayInExchange, calcRequestRoutingKey)

  val publisher = system.actorOf(AmqpPublisherActor.props(channel, config))
  val requester = system.actorOf(RequesterActor.props(publisher, config))
  val listener  = system.actorOf(AmqpListenerActor.props(requester))

  channel.basicConsume(calcRequestQueue, AmqpConsumer(listener))
}
