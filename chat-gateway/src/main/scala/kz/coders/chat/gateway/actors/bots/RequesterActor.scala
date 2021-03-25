package kz.coders.chat.gateway.actors.bots

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.config.Config
import kz.coders.chat.gateway.actors.amqp.AmqpPublisherActor.SendResponse
import kz.domain.library.messages.{ ChatResponse, Sender, TelegramResponse }
import kz.domain.library.messages.citybus.CitybusDomain._
import kz.domain.library.messages.github.GithubDomain._

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
    with ActorLogging {
  implicit val ex: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout     = 8.seconds

  val cityBusUrl: String = config.getString("application.cityBusUrlPrefix")
  val gitHubUrl: String  = config.getString("application.gitHubUrlPrefix")

  val githubActor: ActorRef = {
    system.actorOf(GithubFetcherActor.props(gitHubUrl))
  }
  val citybusActor: ActorRef = {
    system.actorOf(CitybusActor.props(config))
  }

  override def receive: Receive = {
    case request: GetUserDetails =>
      log.info("Requester received GetUserDetails")
      (githubActor ? request)
        .mapTo[GetResponse]
        .map(x => gitProcessAndSend(x, request.routingKey, request.sender))
    case request: GetUserRepos =>
      log.info("Requester received GetUserRepos")
      (githubActor ? request)
        .mapTo[GetResponse]
        .map(x => gitProcessAndSend(x, request.routingKey, request.sender))
    case request: GetVehInfo =>
      log.info("Requester received GetVehInfo")
      (citybusActor ? request)
        .mapTo[CityBusResponse]
        .map(x => citybusProcessAndSend(x, request.routingKey, request.sender))
    case request: GetRoutes =>
      (citybusActor ? request)
        .mapTo[CityBusResponse]
        .map(x => citybusProcessAndSend(x, request.routingKey, request.sender))
    case request: GetRoutesWithStreet =>
      log.info("Requester received GetRoutesWithStreet")
      (citybusActor ? request)
        .mapTo[CityBusResponse]
        .map(x => citybusProcessAndSend(x, request.routingKey, request.sender))
    case obj => log.warning(s"request unhandled ${obj.getClass.getName}")
  }

  def gitProcessAndSend(request: Any, routingKey: String, sender: Sender): Unit =
    request match {
      case obj: GetUserDetailsResponse =>
        publisherActor ! SendResponse(routingKey, ChatResponse(sender, TelegramResponse(obj.details)))
      case obj: GetUserReposResponse =>
        publisherActor ! SendResponse(routingKey, ChatResponse(sender, TelegramResponse(obj.repos)))
      case err: GetFailure =>
        publisherActor ! SendResponse(routingKey, ChatResponse(sender, TelegramResponse(err.error)))
    }

  def citybusProcessAndSend(request: Any, routingKey: String, sender: Sender): Unit =
    request match {
      case obj: VehInfoResponse =>
        publisherActor ! SendResponse(routingKey, ChatResponse(sender, TelegramResponse(obj.busses)))
      case obj: RoutesResponse =>
        publisherActor ! SendResponse(routingKey, ChatResponse(sender, TelegramResponse(obj.routes)))
    }
}
