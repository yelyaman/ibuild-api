package kz.coders.chat.gateway.actors.dialogflow

import java.io.FileInputStream
import java.util.UUID

import akka.actor.{ Actor, ActorLogging, ActorRef, Props }
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.{ GoogleCredentials, ServiceAccountCredentials }
import com.google.cloud.dialogflow.v2._
import com.typesafe.config.Config
import kz.coders.chat.gateway.actors.amqp.AmqpPublisherActor.SendResponse
import kz.coders.chat.gateway.actors.dialogflow.DialogFlowActor.ProcessMessage
import kz.domain.library.messages.citybus.CitybusDomain.{ GetRoutes, GetRoutesWithStreet, GetVehInfo }
import kz.domain.library.messages.github.GithubDomain.{ GetUserDetails, GetUserRepos }
import kz.domain.library.messages.{ ChatResponse, TelegramRequest, TelegramResponse, TelegramSender, UserMessages }

object DialogFlowActor {

  def props(publisher: ActorRef, requesterActor: ActorRef, config: Config): Props =
    Props(new DialogFlowActor(publisher, requesterActor, config))

  case class ProcessMessage(
    routingKey: String,
    message: String,
    sender: TelegramSender
  )

}

class DialogFlowActor(publisher: ActorRef, requesterActor: ActorRef, config: Config) extends Actor with ActorLogging {

  val credentialsPath: String = config.getString("dialogflow.credentialsPath")
  val languageCode: String    = config.getString("dialogflow.languageCode")

  val credentials: GoogleCredentials = GoogleCredentials.fromStream(
    new FileInputStream(credentialsPath)
  )

  val projectId: String =
    credentials.asInstanceOf[ServiceAccountCredentials].getProjectId

  val client: SessionsClient = SessionsClient.create(
    SessionsSettings
      .newBuilder()
      .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
      .build()
  )

  val session: SessionName =
    SessionName.of(projectId, UUID.randomUUID().toString)

  override def receive: Receive = {
    case command: UserMessages =>
      val message = command.message match {
        case Some(value) =>
          value match {
            case TelegramRequest(message) => message
          }
        case None => "a"
      }
      val response = getDialogflowResponse(message)
      log.info(s"Received intent name is ${response.getIntent.getDisplayName}")
      response.getIntent.getDisplayName match {
        case "get-github-account-details" =>
          val params = getValueByParameter(response, "git-account")
          log.info(s"I must give you details of $params")
          requesterActor ! GetUserDetails(command.replyTo.getOrElse(""), params, command.sender)
        case "get-github-repos-details" =>
          val params = getValueByParameter(response, "git-account")
          requesterActor ! GetUserRepos(command.replyTo.getOrElse(""), params, command.sender)

        case "get-bus-details" | "get-troll-details" =>
          val vehNum  = response.getQueryText.split(" ").last
          val vehType = response.getIntent.getDisplayName.split("-")(1)
          log.info(s"I must give you details of $vehType with busNum $vehNum")
          requesterActor ! GetVehInfo(command.replyTo.getOrElse(""), command.sender, vehType, vehNum)
        case "route-second-coord" =>
          val firstAddress  = getValueByParameter(response, "first-coordinates")
          val secondAddress = getValueByParameter(response, "second-coordinates")
          requesterActor ! GetRoutes(command.replyTo.getOrElse(""), command.sender, firstAddress, secondAddress)
        case "route-by-street-coord" =>
          val firstCoordinates = getValueByParameter(response, "first-coordinates")
          val secondAddress    = getValueByParameter(response, "second-address")
          requesterActor ! GetRoutesWithStreet(
            command.replyTo.getOrElse(""),
            command.sender,
            firstCoordinates,
            secondAddress
          )
        case _ =>
          log.info(s"Last case ... => ${response.getFulfillmentText}, SENDER => ${command.sender}")
          publisher ! SendResponse(
            command.replyTo.getOrElse(""),
            ChatResponse(command.sender, TelegramResponse(response.getFulfillmentText))
          )
      }
  }

  def getDialogflowResponse(request: String): QueryResult =
    client
      .detectIntent(
        DetectIntentRequest
          .newBuilder()
          .setQueryInput(
            QueryInput
              .newBuilder()
              .setText(
                TextInput
                  .newBuilder()
                  .setText(request)
                  .setLanguageCode(languageCode)
                  .build()
              )
          )
          .setSession(session.toString)
          .build()
      )
      .getQueryResult

  def getValueByParameter(response: QueryResult, param: String): String =
    response.getParameters.getFieldsMap
      .get(param)
      .getStringValue

}
