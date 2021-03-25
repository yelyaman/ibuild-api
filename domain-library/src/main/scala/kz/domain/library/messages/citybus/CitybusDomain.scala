package kz.domain.library.messages.citybus

import kz.domain.library.messages.Sender
import org.jsoup.nodes.Document

object CitybusDomain {

  trait CityBusResponse

  case class ParseWebPage()

  case class GetAllStops()

  case class PopulateState(doc: Document)

  case class PopulateStops(stops: List[TransportStop])

  case class GetBusNum()

  case class GetTrollNum()

  case class BusNumResponse(numbers: List[String]) extends CityBusResponse

  case class TrollNumResponse(numbers: List[String]) extends CityBusResponse

  case class GetVehInfo(
    routingKey: String,
    sender: Sender,
    vehType: String,
    busNum: String
  )

  case class VehInfoResponse(busses: String) extends CityBusResponse

  case class GetRoutes(
    routingKey: String,
    sender: Sender,
    firstAddress: String,
    secondAddress: String
  )

  case class GetRoutesWithStreet(
    routingKey: String,
    sender: Sender,
    firstCoordinate: String,
    secondAddress: String
  )

  case class RoutesResponse(
    routes: String
  ) extends CityBusResponse

  case class GetBusError(error: String) extends CityBusResponse

  /**
   * Base information of route
   *
   * @param I base id of route
   * @param N number of route
   */
  case class BaseInfo(
    I: Int,
    N: String
  )

  /**
   * Information about exact transport
   *
   * @param Id id of transport
   * @param Nm individual registration number
   * @param Tp type of transport. If Tp == 0, transport is bus, if Tp == 1, transport is trolleybus
   * @param Md model
   * @param Py year of issue
   * @param Pc country of origin
   * @param Cp capacity
   * @param Sc amount of sits
   */
  case class VehicleInfo(
    Id: Int,
    Nm: String,
    Tp: Int,
    Md: String,
    Py: Int,
    Pc: String,
    Cp: Int,
    Sc: Int
  )

  case class StationInfo(
    Id: Int,
    Nm: String
  )

  case class Station(
    Ss: Array[StationInfo]
  )

  case class Stations(
    Crs: Array[Station]
  )

  case class Busses(
    R: BaseInfo,
    V: Array[VehicleInfo],
    Sc: Stations
  )

  /**
   * Information about exact transport transfer(пересадка)
   *
   * @param Sa starting place
   * @param Sb place of arrival
   * @param Nm number of route
   * @param Tp type of transport. If Tp == 0, transport is bus, if Tp == 1, transport is trolleybus
   */
  case class TransportChange(
    Sa: Int,
    Sb: Int,
    Nm: String,
    Tp: Int
  )

  case class Routes(
    Sa: Int,
    Sb: Int,
    R1: Array[TransportChange],
    R2: Array[TransportChange],
    R3: Array[TransportChange],
    R4: Array[TransportChange],
    R5: Array[TransportChange]
  )

  case class TransportStop(
    Id: Int,
    Nm: String
  )

  case class CandidatePoint(
    Nm: String,
    X: Double,
    Y: Double
  )

}
