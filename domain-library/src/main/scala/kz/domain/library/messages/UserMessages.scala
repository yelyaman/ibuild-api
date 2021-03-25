package kz.domain.library.messages

trait Sender
trait Request
trait Response

case class TelegramSender(
  chatId: Long,
  userId: Option[Int],
  firstName: Option[String],
  secondName: Option[String],
  userName: Option[String]
) extends Sender

case class HttpSender(actorPath: String) extends Sender

case class UserMessages(sender: Sender, message: Option[Request], replyTo: Option[String])

case class TelegramRequest(message: String)  extends Request
case class TelegramResponse(message: String) extends Response

case class ChatResponse(sender: Sender, response: Response)

case class HttpRequest(request: String) extends Request
