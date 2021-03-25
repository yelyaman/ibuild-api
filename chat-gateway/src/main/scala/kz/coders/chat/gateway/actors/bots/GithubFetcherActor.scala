package kz.coders.chat.gateway.actors.bots

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.Materializer
import kz.coders.chat.gateway.utils.RestClientImpl
import kz.domain.library.messages.github.GithubDomain._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object GithubFetcherActor {

  def props(url: String)(implicit system: ActorSystem, materializer: Materializer): Props =
    Props(new GithubFetcherActor(url))

}

class GithubFetcherActor(val gitHubUrlPrefix: String)(
  implicit
  system: ActorSystem,
  materializer: Materializer
) extends Actor
    with ActorLogging {

  implicit val executionContext: ExecutionContext  = context.dispatcher
  implicit val defaultFormats: DefaultFormats.type = DefaultFormats

  override def receive: Receive = {
    case request: GetUserDetails =>
      log.debug(s"LOG: GetUserDetails... started, request -> $request")
      val sender = context.sender
      getGithubUser(request.login).onComplete {
        case Success(value)     => sender ! GetUserDetailsResponse(userDetailsResult(value))
        case Failure(exception) => sender ! GetFailure(s"Не смог получить информацию пользователя ${request.login}, проверьте правильность данных")
      }
    case request: GetUserRepos =>
      log.debug(s"LOG: GetUserRepos... started, request -> $request")
      val sender = context.sender
      getUserRepositories(request.login).onComplete {
        case Success(value)     => sender ! GetUserReposResponse(userReposResult("", value))
        case Failure(exception) => sender ! GetFailure(s"Не смог получить информацию пользователя ${request.login}, проверьте правильность данных")
      }
  }

  def getGithubUser(username: String): Future[GithubUser] =
    RestClientImpl
      .get(s"$gitHubUrlPrefix/$username")
      .map(x => parse(x).extract[GithubUser])

  def getUserRepositories(username: String): Future[List[GithubRepository]] =
    RestClientImpl
      .get(s"$gitHubUrlPrefix/$username/repos")
      .map(x => parse(x).extract[List[GithubRepository]])

  def userDetailsResult(obj: GithubUser): String =
    s"Полное имя: ${obj.name}\n" +
      s"Имя пользователя: ${obj.login}\n" +
      s"Местоположение: ${obj.location.getOrElse("Отсутствует")}\n" +
      s"Организация: ${obj.company.getOrElse("Отсутствует")}"

  def userReposResult(result: String, obj: List[GithubRepository]): String =
    obj match {
      case Nil => result
      case x :: xs =>
        userReposResult(result + s"Название: ${x.name}\n     Язык: ${x.language}\n     Описание: ${x.description
          .getOrElse("Отсутствует")}\n", xs)
    }

}
