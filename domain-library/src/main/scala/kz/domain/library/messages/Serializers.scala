package kz.domain.library.messages

import kz.domain.library.messages.calculations._
import org.json4s.ShortTypeHints
import org.json4s.native.Serialization

trait Serializers {
  implicit val formats = Serialization.formats(
    ShortTypeHints(
      List(
        classOf[TelegramSender],
        classOf[HttpSender],
        classOf[Response],
        classOf[Request],
        classOf[TelegramRequest],
        classOf[TelegramResponse],
        classOf[WallBrickCalcRequest],
        classOf[WallBrickCalcResponse],
        classOf[SlabFoundationRequest],
        classOf[SlabFoundationResponse],
        classOf[Response]
      )
    )
  )
}
