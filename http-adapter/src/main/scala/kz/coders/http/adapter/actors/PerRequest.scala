package kz.coders.http.adapter.actors

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.{ RequestContext, RouteResult }
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import kz.domain.library.messages.calculations.WallBrickCalcRequest
import kz.domain.library.messages.{ ChatResponse, HttpRequest, HttpSender, Request, Response, UserMessages }
import org.json4s.{ DefaultFormats, Serialization }
import org.json4s.native.Serialization

import scala.concurrent.{ ExecutionContext, Promise }

object PerRequest {
  class PerRequestActor(
    val request: Request,
    val publisherActor: ActorRef,
    val promise: Promise[RouteResult],
    val requestContext: RequestContext
  ) extends PerRequest
}

trait PerRequest extends Actor with ActorLogging with Json4sSupport {

  implicit val formats: DefaultFormats.type = DefaultFormats
  implicit val serialization: Serialization = Serialization
  implicit val ex: ExecutionContext         = context.dispatcher

  val request: Request
  val publisherActor: ActorRef
  val promise: Promise[RouteResult]
  val requestContext: RequestContext

  val httpSender: HttpSender = HttpSender(self.path.toStringWithoutAddress)
  val message = request match {
    case obj: HttpRequest => UserMessages(httpSender, Option(obj), Some("http.message.response"))
    case obj              => UserMessages(httpSender, Option(obj), Some("http.message.response"))
  }

  publisherActor ! message

  override def receive: Receive = {
    case obj: ChatResponse =>
      populateResponse(obj.response)
      context.stop(self)
  }

  def populateResponse(obj: ToResponseMarshallable): Unit =
    requestContext
      .complete(obj)
      .onComplete(resp => promise.complete(resp))
}
