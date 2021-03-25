package kz.coders.chat.gateway.actors.bots

import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }
import akka.stream.Materializer
import com.themillhousegroup.scoup.ScoupImplicits
import com.typesafe.config.Config
import kz.coders.chat.gateway.utils.RestClientImpl
import kz.domain.library.messages.citybus.CitybusDomain._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

object CitybusActor {

  def props(
    config: Config
  )(implicit system: ActorSystem, materializer: Materializer): Props =
    Props(new CitybusActor(config))

}

class CitybusActor(config: Config)(
  implicit
  val system: ActorSystem,
  materializer: Materializer
) extends Actor
    with ActorLogging
    with ScoupImplicits {

  val cityBusUrlPrefix: String   = config.getString("application.cityBusUrlPrefix")
  val getStopsUrl: String        = config.getString("application.getStopsUrl")
  val findRoutesUrl: String      = config.getString("application.findRoutesUrl")
  val getRouteInfoUrl: String    = config.getString("application.getRouteInfoUrl")
  val getCandidatePoints: String = config.getString("application.getCandidatePoints")
  val stopEmoji                  = List(" 🔴 ", " 🟢 ", " 🟠 ", " 🟡 ", " 🔵 ", " 🟣 ", " 🟤 ", " ⚫ ")
  var state: Document            = Document.createShell(cityBusUrlPrefix)
  var stops: List[TransportStop] = List.empty

  implicit val executionContext: ExecutionContext  = context.dispatcher
  implicit val defaultFormats: DefaultFormats.type = DefaultFormats

  override def preStart(): Unit = {
    log.info("Начинаю престарт")
    self ! ParseWebPage()
    self ! GetAllStops()
    super.preStart()
  }

  override def receive: Receive = {
    case PopulateState(doc) =>
      state = doc
      log.info(s"Стейт перезаписан -> ${state.title}")
    case PopulateStops(allStops) =>
      stops = allStops
      log.info(s"Остановки записаны -> ${stops.head.Nm}")
    case ParseWebPage() =>
      log.info("Кейс ParseWebPage началось")
      parseWebPage.onComplete {
        case Success(value) =>
          log.info(s"Парсирование успешно")
          self ! PopulateState(value)
        case Failure(_) =>
          log.warning("Парсирование не удалось... Начинаю заново")
          self ! ParseWebPage()
      }
    case GetAllStops() =>
      log.info("Кейс GetAllStops началось")
      getAllStops.onComplete {
        case Success(value) =>
          log.info(s"Список остановок получен")
          self ! PopulateStops(value)
        case Failure(_) =>
          log.warning("Получение остановок не удалось... Начинаю заново")
          self ! GetAllStops()
      }

    case GetBusNum =>
      log.info("Пришла команда GetBusNum")
      val sender = context.sender
      sender ! BusNumResponse(getVehNumbers(state, "bus"))
    case GetTrollNum =>
      val sender = context.sender
      sender ! TrollNumResponse(getVehNumbers(state, "troll"))
    case GetVehInfo(_, _, vehType, busNum) =>
      log.info("Пришла команда GetVehInfo")
      val sender = context.sender
      val id     = getIdByNum(state, busNum, vehType)
      log.info(s"Vehicle id is $busNum -> $id")
      id match {
        case -1 =>
          sender ! VehInfoResponse("Автобуса с таким номером не существует, проверьте правильность запроса")
        case _ =>
          getBusInfo(id).onComplete {
            case Success(busses) =>
              val response = getInfoByNum(busses, state, busNum, vehType)
              sender ! VehInfoResponse(response)
            case Failure(_) =>
              sender ! GetBusError("Что то пошло не так, пожалуйста повторите позже")
          }
      }

    case GetRoutes(_, _, firstAddress, secondAddress) =>
      log.info(s"Пришел запрос маршрута $firstAddress ------>>>>> $secondAddress")
      val sender           = context.sender
      val firstCoordinate  = firstAddress.split("a").mkString("/")
      val secondCoordinate = secondAddress.split("a").mkString("/")

      getRoutesInfo(firstCoordinate, secondCoordinate).onComplete {
        case Success(value) => sender ! RoutesResponse(value)
        case Failure(_)     => sender ! RoutesResponse("Что то пошло не так, пожалуйста повторите позже")
      }

    case GetRoutesWithStreet(_, _, firstCoordinate, secondAddress) =>
      log.info(s"Пришел запрос маршрута $firstCoordinate ------>>>>> $secondAddress")
      val sender           = context.sender
      val firstCoordinates = firstCoordinate.split("a").mkString("/")
      getCandidatePoints(secondAddress).onComplete {
        case Success(candidatePoint) =>
          val x = candidatePoint.X
          val y = candidatePoint.Y
          getRoutesInfo(firstCoordinates, s"$x/$y").onComplete {
            case Success(value) => sender ! RoutesResponse(value)
            case Failure(_)     => sender ! RoutesResponse("Что то пошло не так, пожалуйста повторите позже")
          }
        case Failure(exception) => println(s"ОШИБКА ${exception.getMessage}")
      }

  }

  def parseWebPage: Future[Document] =
    Future(Jsoup.connect(cityBusUrlPrefix).timeout(3000).get())

  def getAllStops: Future[List[TransportStop]] =
    RestClientImpl
      .get(
        s"$getStopsUrl?_=${System.currentTimeMillis()}"
      )
      .map(x => parse(x).extract[Array[TransportStop]].toList)

  def getVehNumbers(webPage: Document, vehType: String): List[String] =
    getBlocks(webPage, vehType)
      .select("span")
      .filter(x => x.attr("style") == "vertical-align:top;margin-right:5px;float:right")
      .map(x => x.text().trim)
      .toList

  def getBlocks(webPage: Document, vehType: String): Elements =
    webPage.select(s".route-button-$vehType")

  def getInfoByNum(
    busses: Busses,
    webPage: Document,
    busNum: String,
    busType: String
  ): String = {
    val busAmount = busses.V.length
    val model     = busses.V.head.Md
    val year      = busses.V.head.Py
    val country   = busses.V.head.Pc
    val capacity  = busses.V.head.Cp
    val sits      = busses.V.head.Sc
    webPage
      .select(s".route-button-$busType")
      .find(x =>
        x.getElementsByAttributeValue("style", "vertical-align:top;margin-right:5px;float:right")
          .text() == busNum
      ) match {
      case Some(divBlock) =>
        val routeInfo = divBlock.select(".route-info")
        val parking   = routeInfo.select("span").remove().text
        val mainInfo = routeInfo.toString
          .split("\n")
          .toList
          .map(elem => elem.replace("<br>", ""))
        s"${mainInfo(1)}\n" +
          s"Стоянка: $parking\n" +
          s"${mainInfo(3).trim}\n" +
          s"${mainInfo(4).trim}\n" +
          s"${mainInfo(5).trim}\n" +
          s"Количество транспорта: $busAmount\n" +
          s"Модель: $model\n" +
          s"Год выпуска: $year\n" +
          s"Страна производства: $country\n" +
          s"Вместимость: $capacity\n" +
          s"Сидячих мест: $sits"
      case None => "Не смог найти информацию"
    }
  }

  def getRoutesInfo(firstCoordinate: String, secondCoordinate: String): Future[String] =
    getRoutes(firstCoordinate, secondCoordinate).map { routes =>
      @tailrec
      def innerRoutesInfo(count: Int, acc: String, routes: List[Routes]): String = routes match {
        case Nil => acc
        case route :: xs =>
          val transfers = {
            transportTransfers(route.R1.toList, route.R2.toList, route.R3.toList, route.R4.toList, route.R5.toList)
          }
          val transportVars = transportVariants(List.empty, transfers)
          val changesAmount = transportVars.length - 1 // Количество пересадок
          val routeString   = routeMkString(0, "", transportVars)
          val result = s"\n\nВариант маршрута - $count\n\n" +
            s"Количество пересадок - $changesAmount" +
            s"$routeString"
          innerRoutesInfo(count + 1, acc + result, xs)
      }
      innerRoutesInfo(1, "", routes)
    }

  def getRoutes(firstCoordinate: String, secondCoordinate: String): Future[List[Routes]] =
    RestClientImpl
      .get(
        s"$findRoutesUrl$firstCoordinate/$secondCoordinate?_=${System.currentTimeMillis()}"
      )
      .map(x => parse(x).extract[Array[Routes]].toList)

  def transportTransfers(
    t1: List[TransportChange],
    t2: List[TransportChange],
    t3: List[TransportChange],
    t4: List[TransportChange],
    t5: List[TransportChange]
  ): List[List[TransportChange]] =
    List(t1, t2, t3, t4, t5).filter(change => change.nonEmpty)

  def transportVariants(
    result: List[List[List[String]]],
    changes: List[List[TransportChange]]
  ): List[List[List[String]]] = changes match {
    case Nil => result
    case change :: xs =>
      val trVariants = variants(List.empty, change)
      transportVariants(result :+ trVariants, xs)
  }

  def routeMkString(count: Int, result: String, transfers: List[List[List[String]]]): String = transfers match {
    case Nil => result
    case transfer :: xs =>
      val trVarString = transportVariantsMkString("", "", "", "", transfer)
      val startEmoji  = stopEmoji(count)
      val endEmoji    = stopEmoji(count + 1)
      val startStation =
        if (result.split("\n").last == s"🏃$startEmoji${trVarString.head}") "\n"
        else s"\n$startEmoji${trVarString.head}\n"
      val endStation =
        if (count == transfers.length || transfers.length == 1) s"$endEmoji${trVarString(1)}"
        else s"🏃$endEmoji${trVarString(1)}"
      val busses = trVarString(2)
      val trolls = trVarString.last
      val info = if (busses.isEmpty) {
        s"$startStation" +
          s"      🚎Троллейбус: $trolls\n" +
          s"$endStation"
      } else if (trolls.isEmpty) {
        s"$startStation" +
          s"      🚌Автобус: $busses\n" +
          s"$endStation"
      } else {
        s"$startStation" +
          s"      🚌Автобус: $busses\n" +
          s"      🚎Троллейбус: $trolls\n" +
          s"$endStation"
      }
      routeMkString(count + 1, result + info, xs)

  }

  def transportVariantsMkString(
    startPoint: String,
    endPoint: String,
    busses: String,
    trolls: String,
    transfer: List[List[String]]
  ): List[String] = transfer match {
    case Nil => List(startPoint, endPoint, busses, trolls)
    case trVariant :: xs =>
      trVariant(1) match {
        case "Автобус" if startPoint.isEmpty =>
          transportVariantsMkString(trVariant.head, trVariant.last, busses + s" ${trVariant(2)}", trolls, xs)
        case "Троллейбус" if startPoint.isEmpty =>
          transportVariantsMkString(trVariant.head, trVariant.last, busses, trolls + s" ${trVariant(2)}", xs)
        case "Автобус"    => transportVariantsMkString(startPoint, endPoint, busses + s", ${trVariant(2)}", trolls, xs)
        case "Троллейбус" => transportVariantsMkString(startPoint, endPoint, busses, trolls + s", ${trVariant(2)}", xs)
      }
  }

  def variants(result: List[List[String]], change: List[TransportChange]): List[List[String]] = change match {
    case Nil => result
    case x :: xs =>
      val startPoint = x.Sa
      val endPoint   = x.Sb
      val vehNum     = x.Nm
      val vehType = x.Tp match {
        case 0 => "Автобус"
        case 1 => "Троллейбус"
      }
      val stations = getPlacesByIds(startPoint, endPoint)
      val asd      = List(stations.head, vehType, vehNum, stations.last)
      variants(result :+ asd, xs)
  }

  def getPlacesByIds(startId: Int, endId: Int): List[String] =
    stops
      .filter(stops => stops.Id == startId)
      .flatMap(firstStation =>
        stops
          .filter(stops => stops.Id == endId)
          .flatMap(secondStation =>
            List(firstStation.Nm.trim.replace("\\\'", ""), secondStation.Nm.trim.replace("\\\'", ""))
          )
      )

  def getIdByNum(webPage: Document, num: String, veh: String): Int =
    webPage
      .select(s".route-button-$veh")
      .find(x =>
        x.getElementsByAttributeValue("style", "vertical-align:top;margin-right:5px;float:right")
          .text() == num
      ) match {
      case Some(value) => value.attr("id").split("-").toList.last.toInt
      case None        => -1
    }

  def getBusInfo(busIndex: Int): Future[Busses] =
    RestClientImpl
      .get(s"$getRouteInfoUrl$busIndex?_=${System.currentTimeMillis()}")
      .map(x => parse(x).extract[Busses])

  def getCandidatePoints(address: String): Future[CandidatePoint] = {
    val editedAddress = address.split(" - ").mkString("%20-%20")
    RestClientImpl
      .get(s"$getCandidatePoints$editedAddress?_=${System.currentTimeMillis()}")
      //      .get(s"https://www.citybus.kz/almaty/Navigator/GetCandidatePoints/Шевченко%20-%20Байзакова?_=1597657341731")
      .map(x => parse(x).extract[Array[CandidatePoint]].head)
  }
}
