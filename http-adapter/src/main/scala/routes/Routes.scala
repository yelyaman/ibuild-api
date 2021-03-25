package routes

import actors.PerRequest.PerRequestActor
import akka.actor.{ ActorLogging, ActorRef, ActorSystem, Props }
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ RequestContext, Route, RouteResult }
import akka.util.Timeout
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.typesafe.config.{ Config, ConfigFactory }
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import kz.domain.library.messages._
import kz.domain.library.messages.calculations.{ SlabFoundationRequest, WallBrickCalcRequest }
import org.json4s.native.Serialization
import org.json4s.{ DefaultFormats, Serialization }
import utils.CORSHandler

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.concurrent.duration._

class Routes(publisherActor: ActorRef, log: LoggingAdapter)(implicit val ex: ExecutionContext, system: ActorSystem)
    extends Json4sSupport
    with CORSHandler {

  implicit val formats: DefaultFormats      = DefaultFormats
  implicit val serialization: Serialization = Serialization
  implicit val timeout: Timeout             = 5.seconds

  val handlers: Route = pathPrefix("api") {
    pathPrefix("bot") {
      get {
        entity(as[HttpRequest])(body => ctx => completeRequest(ctx, body))
      }
    } ~ corsHandler(pathPrefix("calculations") {
      pathPrefix("e") {
        pathPrefix("e") {
          post {
            entity(as[SlabFoundationRequest])(body => ctx => completeRequest(ctx, body))
          }
        }
      } ~ pathPrefix("wall") {
        pathPrefix("brick") {
          post {
            entity(as[WallBrickCalcRequest])(body => ctx => completeRequest(ctx, body))
          }
        }
      } ~ pathPrefix("foundation") {
        pathPrefix("slab") {
          post {
            entity(as[SlabFoundationRequest])(body => ctx => completeRequest(ctx, body))
          }
        }
      }
    })
  }

  def completeRequest(ctx: RequestContext, body: Request): Future[RouteResult] = {
    log.info(s"Received to completeRequest object => $body")
    val promise = Promise[RouteResult]
    system.actorOf(
      Props(new PerRequestActor(body, publisherActor, promise, ctx))
    )
    promise.future
  }
}
