package kz.coders.telegram

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.stream.{ActorMaterializer, Materializer}
import com.typesafe.config.ConfigFactory
import kz.amqp.library.{AmqpConsumer, RabbitMqConnection}
import kz.coders.telegram.actors.{AmqpListenerActor, AmqpPublisherActor}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object Boot extends App {
  implicit val system: ActorSystem        = ActorSystem("telegram-demo")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ex: ExecutionContext       = system.dispatcher

  val config = ConfigFactory.load()

  val cityBusUrl = config.getString("application.cityBusUrlPrefix")
  val gitHubUrl  = config.getString("application.gitHubUrlPrefix")
  val tgToken    = config.getString("telegram.token")

  val username    = config.getString("rabbitmq.username")
  val password    = config.getString("rabbitmq.password")
  val host        = config.getString("rabbitmq.host")
  val port        = config.getInt("rabbitmq.port")
  val virtualHost = config.getString("rabbitmq.virtualHost")

  val gatewayInExchange     = config.getString("rabbitmq.gatewayInExchange")
  val gatewayOutExchange    = config.getString("rabbitmq.gatewayOutExchange")
  val telegramResponseQueue = config.getString("rabbitmq.telegramResponseQueue")

  val telegramResponseRoutingKey = config.getString("rabbitmq.telegramResponseRoutingKey")

  val connection = RabbitMqConnection.getRabbitMqConnection(username, password, host, port, virtualHost)
  val channel    = connection.createChannel()

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

  RabbitMqConnection.declareAndBindQueue(
    channel,
    telegramResponseQueue,
    gatewayOutExchange,
    telegramResponseRoutingKey
  )

  val logger: LoggingAdapter = system.log

  val publisherActor = system.actorOf(AmqpPublisherActor.props(channel, config))

  val service: TelegramService = new TelegramService(tgToken, publisherActor, logger)
  val listenerActor            = system.actorOf(AmqpListenerActor.props(service))

  channel.basicConsume(telegramResponseQueue, AmqpConsumer(listenerActor))

  service.run()
  system.log.debug("Started Boot.scala")
}
