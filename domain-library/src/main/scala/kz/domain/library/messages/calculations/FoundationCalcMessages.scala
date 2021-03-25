package kz.domain.library.messages.calculations

import kz.domain.library.messages.{ Request, Response }

case class SlabFoundationRequest(
  a_length: Double,
  b_length: Double,
  c_length: Double,
  cost: Double
) extends Request

case class SlabFoundationResponse(
  slab_area: Double,
  concrete_volume: Double,
  perimeter: Double,
  side_plate_area: Double,
  weight: Double,
  soil_load: Double,
  total_cost: Double
) extends Response
