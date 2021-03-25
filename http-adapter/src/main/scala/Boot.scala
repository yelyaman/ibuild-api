import actors.{ AmqpListenerActor, AmqpPublisherActor }
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory
import kz.amqp.library.{ AmqpConsumer, RabbitMqConnection }
import routes.Routes

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

object Boot extends App {
  implicit val system: ActorSystem        = ActorSystem("http-adapter")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ex: ExecutionContext       = system.dispatcher

  val logger: LoggingAdapter = system.log
  val config                 = ConfigFactory.load()

  val cityBusUrl = config.getString("application.cityBusUrlPrefix")
  val gitHubUrl  = config.getString("application.gitHubUrlPrefix")

  val host = config.getString("application.host")
  val port = config.getInt("application.port")

  val username    = config.getString("rabbitmq.username")
  val password    = config.getString("rabbitmq.password")
  val rmqHost     = config.getString("rabbitmq.host")
  val rmqPort     = config.getInt("rabbitmq.port")
  val virtualHost = config.getString("rabbitmq.virtualHost")

  val gatewayInExchange  = config.getString("rabbitmq.gatewayInExchange")
  val gatewayOutExchange = config.getString("rabbitmq.gatewayOutExchange")

  val httpResponseQueue = config.getString("rabbitmq.httpResponseQueue")
  val calcResponseQueue = config.getString("rabbitmq.calcResponseQueue")

  val httpResponseRoutingKey = config.getString("rabbitmq.httpResponseRoutingKey")
  val calcResponseRoutingKey = config.getString("rabbitmq.calcResponseRoutingKey")

  val connection = RabbitMqConnection.getRabbitMqConnection(username, password, rmqHost, rmqPort, virtualHost)
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
    httpResponseQueue,
    gatewayOutExchange,
    httpResponseRoutingKey
  )

  RabbitMqConnection.declareAndBindQueue(
    channel,
    calcResponseQueue,
    gatewayOutExchange,
    calcResponseRoutingKey
  )

  val publisherActor = system.actorOf(AmqpPublisherActor.props(channel, config))
  val listenerActor  = system.actorOf(AmqpListenerActor.props())
  channel.basicConsume(httpResponseQueue, AmqpConsumer(listenerActor))
  channel.basicConsume(calcResponseQueue, AmqpConsumer(listenerActor))

  val routes = new Routes(publisherActor, logger)

  system.log.debug("Started Boot.scala")

  Http().bindAndHandle(routes.handlers, host, port)
}
