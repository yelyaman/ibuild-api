package kz.domain.library.messages.calculations

import kz.domain.library.messages.{ Request, Response }

case class WallBrickCalcRequest(
  wall_length: Double,
  wall_height: Double,
  thickness: Double,
  mortar_thickness: Double,
  brick_weight: Double,
  brick_cost: Double
) extends Request

case class WallBrickCalcResponse(
  result: Double
) extends Response
