package io.surfkit.derpyhoves.flows

import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import java.io.{File, PrintWriter}
import java.net.URL

import com.typesafe.config._

import scala.util.Try

/**
  * Created by suroot on 03/10/17.
  */
object Questrade {

  val pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZZ"
  implicit val dateFormat = Format[DateTime](Reads.jodaDateReads(pattern), Writes.jodaDateWrites(pattern))

  sealed trait QT

  case class Login(access_token: String, token_type: String, expires_in: Int, refresh_token: String, api_server: String) extends QT
  implicit val LoginWrites = Json.writes[Login]
  implicit val LoginReads = Json.reads[Login]

  //http://www.questrade.com/api/documentation/rest-operations/account-calls/accounts
  case class Account(`type`: String, number: String, status: String, isPrimary: Boolean, isBilling: Boolean, clientAccountType: String) extends QT
  implicit val AccountWrites = Json.writes[Account]
  implicit val AccountReads = Json.reads[Account]
  case class Accounts(accounts: Seq[Account], userId: Int) extends QT
  implicit val AccountsWrites = Json.writes[Accounts]
  implicit val AccountsReads = Json.reads[Accounts]


  //http://www.questrade.com/api/documentation/rest-operations/account-calls/accounts-id-positions
  case class Position(symbol: String,
                      symbolId: Int,
                      openQuantity: Double,
                      closedQuantity: Double,
                      currentMarketValue: Option[Double],
                      currentPrice: Double,
                      averageEntryPrice: Option[Double],
                      closedPnl: Option[Double],
                      openPnl: Option[Double],
                      totalCost: Option[Double],
                      isRealTime: Boolean,
                      isUnderReorg: Boolean) extends QT
  implicit val PositionWrites = Json.writes[Position]
  implicit val PositionReads = Json.reads[Position]
  case class Positions(positions: Seq[Position]) extends QT
  implicit val PositionsWrites = Json.writes[Positions]
  implicit val PositionsReads = Json.reads[Positions]


  //http://www.questrade.com/api/documentation/rest-operations/account-calls/accounts-id-balances
  case class Balance(currency: String, cash: Double, marketValue: Double, totalEquity: Double, buyingPower: Double, maintenanceExcess: Double, isRealTime: Boolean) extends QT
  implicit val BalanceWrites = Json.writes[Balance]
  implicit val BalanceReads = Json.reads[Balance]
  case class Balances(perCurrencyBalances: Set[Balance], combinedBalances: Seq[Balance], sodPerCurrencyBalances: Seq[Balance], sodCombinedBalances: Seq[Balance]) extends QT
  implicit val BalancesWrites = Json.writes[Balances]
  implicit val BalancesReads = Json.reads[Balances]

  //http://www.questrade.com/api/documentation/rest-operations/account-calls/accounts-id-executions
  case class Execution(
                        symbol: String,
                        symbolId: Int,
                        quantity: Int,
                        side: String,
                        price: Double,
                        id: Int,
                        orderId: Int,
                        orderChainId: Int,
                        exchangeExecId: String,
                        timestamp: DateTime,
                        notes: String,
                        venue: String,
                        totalCost: Double,
                        orderPlacementCommission: Double,
                        commission: Double,
                        executionFee: Double,
                        secFee: Double,
                        canadianExecutionFee: Double,
                        parentId: Int
                      ) extends QT
  implicit val ExecutionWrites = Json.writes[Execution]
  implicit val ExecutionReads = Json.reads[Execution]
  case class Executions(executions: Seq[Execution]) extends QT
  implicit val ExecutionsWrites = Json.writes[Executions]
  implicit val ExecutionsReads = Json.reads[Executions]


  case class QuantityInfo(totalQuantity: Int,
                          openQuantity: Int,
                          filledQuantity: Option[Int],
                          canceledQuantity: Int,
                          icebergQty: Option[Int],
                          minQuantity: Option[Int])
  implicit val QuantityInfoWrites = Json.writes[QuantityInfo]
  implicit val QuantityInfoReads = Json.reads[QuantityInfo]

  case class PriceInfo(
                        limitPrice: Option[Double],
                        stopPrice: Option[Double],
                        comissionCharged: Option[Double],
                        placementCommission: Option[Double],
                        triggerStopPrice: Option[Double],
                        avgExecPrice: Option[Double],
                        lastExecPrice: Option[Double]
                      )
  implicit val PriceInfoWrites = Json.writes[PriceInfo]
  implicit val PriceInfoReads = Json.reads[PriceInfo]

  case class Routing(primaryRoute: String,
                     secondaryRoute: String,
                     legs: Seq[String],
                     orderRoute: String,
                     venueHoldingOrder: String,
                     exchangeOrderId: String,
                     orderGroupId: Int,
                     orderClass: Option[String])
  implicit val RoutingWrites = Json.writes[Routing]
  implicit val RoutingReads = Json.reads[Routing]

  case class Extra(notes: String,
                   isSignificantShareHolder: Boolean,
                   isInsider: Boolean,
                   isLimitOffsetInDollar: Boolean)
  implicit val ExtraWrites = Json.writes[Extra]
  implicit val ExtraReads = Json.reads[Extra]

  case class OrderAction(action: String)
  object OrderAction{
    val Buy = OrderAction("Buy")
    val Sell = OrderAction("Sell")
  }

  case class OrderType(name: String)
  object OrderType{
    val Market = OrderType("Market")
    val Limit = OrderType("Limit")
    val Stop = OrderType("Stop")
    val StopLimit = OrderType("StopLimit")
    val TrailStopInPercentage = OrderType("TrailStopInPercentage")
    val TrailStopInDollar = OrderType("TrailStopInDollar")
    val TrailStopLimitInPercentage = OrderType("TrailStopLimitInPercentage")
    val TrailStopLimitInDollar = OrderType("TrailStopLimitInDollar")
    val LimitOnOpen = OrderType("LimitOnOpen")
    val LimitOnClose = OrderType("LimitOnClose")
  }

  case class OrderTimeInForce(name: String)
  object OrderTimeInForce{
    val Day = OrderTimeInForce("Day")
    val GoodTillCanceled = OrderTimeInForce("GoodTillCanceled")
    val GoodTillExtendedDay = OrderTimeInForce("GoodTillExtendedDay")
    val GoodTillDate = OrderTimeInForce("GoodTillDate")
    val ImmediateOrCancel = OrderTimeInForce("ImmediateOrCancel")
    val FillOrKill = OrderTimeInForce("FillOrKill")
  }

  case class OrderState(state: String)
  object OrderState{
    val Failed = OrderState("Failed")
    val Pending = OrderState("Pending")
    val Accepted = OrderState("Accepted")
    val Rejected = OrderState("Rejected")
    val CancelPending = OrderState("CancelPending")
    val Canceled = OrderState("Canceled")
    val PartialCanceled = OrderState("PartialCanceled")
    val Partial = OrderState("Partial")
    val Executed = OrderState("Executed")
    val ReplacePending = OrderState("ReplacePending")
    val Replaced = OrderState("Replaced")
    val Stopped = OrderState("Stopped")
    val Suspended = OrderState("Suspended")
    val Expired = OrderState("Expired")
    val Queued = OrderState("Queued")
    val Triggered = OrderState("Triggered")
    val Activated = OrderState("Activated")
    val PendingRiskReview = OrderState("PendingRiskReview")
    val ContingentOrder = OrderState("ContingentOrder")
  }

  case class Order(
                  id: Int,
                  symbol: String,
                  symbolId: Int,
                  quantityInfo: QuantityInfo,
                  priceInfo: PriceInfo,
                  routing: Routing,
                  extra: Extra,
                  side: String,
                  orderType: String,
                  isAllOrNone: Boolean,
                  isAnonymous: Boolean,
                  source: String,
                  timeInForce: String,
                  gtdDate: Option[DateTime],
                  state: String,
                  chainId: Int,
                  creationTime: DateTime,
                  updateTime: DateTime,
                  userId: Int,
                  strategyType: String
                  ) extends QT
  import play.api.libs.functional.syntax._
  import play.api.libs.json._
  implicit val OrderReads: Reads[Order] = (
        (JsPath \ "id").read[Int] and
        (JsPath \ "symbol").read[String] and
        (JsPath \ "symbolId").read[Int] and
        (JsPath).read[QuantityInfo] and
        (JsPath).read[PriceInfo] and
        (JsPath).read[Routing] and
        (JsPath).read[Extra] and
        (JsPath \ "side").read[String] and
        (JsPath \ "orderType").read[String] and
        (JsPath \ "isAllOrNone").read[Boolean] and
        (JsPath \ "isAnonymous").read[Boolean] and
        (JsPath \ "source").read[String] and
        (JsPath \ "timeInForce").read[String] and
        (JsPath \ "gtdDate").readNullable[DateTime] and
        (JsPath \ "state").read[String] and
        (JsPath \ "chainId").read[Int] and
        (JsPath \ "creationTime").read[DateTime] and
        (JsPath \ "updateTime").read[DateTime] and
        (JsPath \ "userId").read[Int] and
        (JsPath \ "strategyType").read[String]
    )(Order.apply _)
  implicit val OrderWrites = Json.writes[Order]
  //implicit val OrderReads = Json.reads[Order]
  case class Orders(orders: Seq[Order]) extends QT
  implicit val OrdersWrites = Json.writes[Orders]
  implicit val OrdersReads = Json.reads[Orders]

  case class OrderStateFilter(state: String)
  object OrderStateFilter{
    val All = OrderStateFilter("All")
    val Open = OrderStateFilter("Open")
    val Closed = OrderStateFilter("Closed")
  }

  //http://www.questrade.com/api/documentation/rest-operations/account-calls/accounts-id-activities
  case class AccountActivity(
                              tradeDate: DateTime,
                              transactionDate: DateTime,
                              settlementDate: DateTime,
                              action: String,
                              symbol: String,
                              symbolId: Int,
                              description: String,
                              currency: String,
                              quantity: Int,
                              price: Double,
                              grossAmount: Double,
                              commission: Double,
                              netAmount: Double,
                              `type`: String
                            ) extends QT
  implicit val AccountActivityWrites = Json.writes[AccountActivity]
  implicit val AccountActivityReads = Json.reads[AccountActivity]
  case class AccountActivities(activities: Seq[AccountActivity]) extends QT
  implicit val AccountActivitiesWrites = Json.writes[AccountActivities]
  implicit val AccountActivitiesReads = Json.reads[AccountActivities]

  case class Interval(period: String){
    def toDuration: FiniteDuration = this match{
      case Interval.OneMinute => 1 minute
      case Interval.TwoMinutes => 2 minute
      case Interval.ThreeMinutes => 3 minute
      case Interval.FourMinutes => 4 minute
      case Interval.FiveMinutes => 5 minute
      case Interval.TenMinutes => 10 minute
      case Interval.FifteenMinutes => 15 minute
      case Interval.TwentyMinutes => 20 minute
      case Interval.HalfHour => 30 minute
      case Interval.OneHour => 1 hour
      case Interval.OneDay => 1 day
      case Interval.OneWeek => 7 day
      case _ => 1 minute
    }
  }
  object Interval{
    val OneMinute	= Interval("OneMinute")
    val TwoMinutes	= Interval("TwoMinutes")
    val ThreeMinutes	= Interval("ThreeMinutes")
    val FourMinutes	= Interval("FourMinutes")
    val FiveMinutes	= Interval("FiveMinutes")
    val TenMinutes	= Interval("TenMinutes")
    val FifteenMinutes	= Interval("FifteenMinutes")
    val TwentyMinutes	= Interval("TwentyMinutes")
    val HalfHour= Interval("HalfHour")
    val OneHour	= Interval("OneHour")
    val TwoHours	= Interval("TwoHours")
    val FourHours	= Interval("FourHours")
    val OneDay	= Interval("OneDay")
    val OneWeek	= Interval("OneWeek")
    val OneMonth	= Interval("OneMonth")
    val OneYear	= Interval("OneYear")
  }

  //http://www.questrade.com/api/documentation/rest-operations/market-calls/markets-candles-id
  case class Candle(start: DateTime, end: DateTime, low: Double, high: Double, open: Double, close: Double, volume: Int) extends QT
  implicit val CandleWrites = Json.writes[Candle]
  implicit val CandleReads = Json.reads[Candle]
  case class Candles(candles: Seq[Candle]) extends QT
  implicit val CandlesWrites = Json.writes[Candles]
  implicit val CandlesReads = Json.reads[Candles]

  //http://www.questrade.com/api/documentation/rest-operations/market-calls/symbols-search
  case class Symbol(symbol: String, symbolId: Int, description: String, securityType: String, listingExchange: String, isTradable: Boolean, isQuotable: Boolean, currency: String) extends QT
  implicit val SymbolWrites = Json.writes[Symbol]
  implicit val SymbolReads = Json.reads[Symbol]
  case class Symbols(symbols: Seq[Symbol]) extends QT
  implicit val SymbolsWrites = Json.writes[Symbols]
  implicit val SymbolsReads = Json.reads[Symbols]

  case class Quote(
                    symbol: String,
                    symbolId: Int,
                    tier: String,
                    bidPrice: Option[Double],
                    bidSize: Int,
                    askPrice: Option[Double],
                    askSize: Int,
                    lastTradePriceTrHrs: Option[Double],
                    lastTradePrice: Option[Double],
                    lastTradeSize: Int,
                    lastTradeTick: Option[String],
                    lastTradeTime: Option[DateTime],
                    volume: Long,
                    openPrice: Option[Double],
                    highPrice: Option[Double],
                    lowPrice: Option[Double],
                    delay: Int,
                    isHalted: Boolean
                  ) extends QT
  implicit val QuoteWrites = Json.writes[Quote]
  implicit val QuoteReads = Json.reads[Quote]
  case class Quotes(quotes: Seq[Quote]) extends QT
  implicit val QuotesWrites = Json.writes[Quotes]
  implicit val QuotesReads = Json.reads[Quotes]

  case class PostOrder(orderId: Option[Int] = None,
                       symbolId: Int,
                       quantity: Int,
                       timeInForce: String,
                       icebergQuantity: Option[Int],
                       limitPrice: Option[Double],
                       stopPrice: Option[Double],
                       isAllOrNone: Boolean,
                       isAnonymous: Boolean,
                       orderType: String,
                       action: String,
                       primaryRoute: String = "AUTO",
                       secondaryRoute: String = "NYSE") extends QT
  implicit val PostOrderWrites = Json.writes[PostOrder]
  implicit val PostOrderReads = Json.reads[PostOrder]
  case class OrderResponse(orderId: Int, orders: Seq[Order]) extends QT
  implicit val OrderResponseWrites = Json.writes[OrderResponse]
  implicit val OrderResponseReads = Json.reads[OrderResponse]


  case class OrderCancelConfirm(orderId: Int) extends QT
  implicit val OrderCancelConfirmWrites = Json.writes[OrderCancelConfirm]
  implicit val OrderCancelConfirmReads = Json.reads[OrderCancelConfirm]

  case class StreamPort(streamPort: Int) extends QT
  implicit val StreamPortWrites = Json.writes[StreamPort]
  implicit val StreamPortReads = Json.reads[StreamPort]

  def intervalParams(interval:Questrade.Interval) = {
    val endTime = DateTime.now().secondOfMinute().setCopy(0)
    val startTime = endTime.plusSeconds(-interval.toDuration.toSeconds.toInt)
    s"?startTime=${startTime.toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&endTime=${endTime.toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&interval=${interval.period}"
  }
}

class QuestradeTicker[T <: Questrade.QT](creds: () => Questrade.Login, symbolId: Int, interval: Questrade.Interval, tz: DateTimeZone, params: Questrade.Interval => String, fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[T]) extends QuestradePoller(
  creds = creds, path = s"v1/markets/candles/${symbolId}", interval = interval, fuzz = fuzz, params = params, hoursOpt = Some(tz)) with PlayJsonSupport{

  def json(): Source[Future[T], Cancellable] = super.apply().map{
    case scala.util.Success(response) => Unmarshal(response.entity).to[T]
    case scala.util.Failure(ex) => Future.failed(ex)
  }
}

case class QuestradeOneMinuteTicker(creds: () => Questrade.Login, symbolId: Int, tz: DateTimeZone = DateTimeZone.forID("US/Eastern"), fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Questrade.Candles])
  extends QuestradeTicker[Questrade.Candles](creds, symbolId, Questrade.Interval.OneMinute, tz, params = Questrade.intervalParams, fuzz)

case class QuestradeFifteenMinuteTicker(creds: () => Questrade.Login, symbolId: Int, tz: DateTimeZone = DateTimeZone.forID("US/Eastern"), fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Questrade.Candles])
  extends QuestradeTicker[Questrade.Candles](creds, symbolId, Questrade.Interval.FifteenMinutes, tz, params = Questrade.intervalParams, fuzz)

case class QuestradeThirtyMinuteTicker(creds: () => Questrade.Login, symbolId: Int, tz: DateTimeZone = DateTimeZone.forID("US/Eastern"), fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Questrade.Candles])
  extends QuestradeTicker[Questrade.Candles](creds, symbolId, Questrade.Interval.HalfHour, tz, params = Questrade.intervalParams, fuzz)

case class QuestradeOneHourTicker(creds: () => Questrade.Login, symbolId: Int, tz: DateTimeZone = DateTimeZone.forID("US/Eastern"), fuzz: Double = 6.66)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Questrade.Candles])
  extends QuestradeTicker[Questrade.Candles](creds, symbolId, Questrade.Interval.OneHour, tz, params = Questrade.intervalParams, fuzz)


case class QuestradeRefresh(creds: () => Questrade.Login, practice: Boolean)(implicit system: ActorSystem, materializer: Materializer, um: Reads[Questrade.Candles]) extends QuestradePoller(
  creds = () => { Questrade.Login("","",0, "", "") },
  path = if(practice) s"https://practicelogin.questrade.com/oauth2/token" else s"https://login.questrade.com/oauth2/token",
  interval = Questrade.Interval.OneHour, fuzz = 0.0, params = {i:Questrade.Interval =>
    s"?grant_type=refresh_token&refresh_token=${creds().refresh_token}"
  }, hoursOpt = None) with PlayJsonSupport {

  def json(): Source[Future[Questrade.Login], Cancellable] = super.apply().map {
    case scala.util.Success(response) => Unmarshal(response.entity).to[Questrade.Login]
    case scala.util.Failure(ex) => Future.failed(ex)
  }
}

case class QuestradeLogin(refreshToken: String, practice: Boolean = false)(implicit system: ActorSystem, materializer: Materializer, ex: ExecutionContext) extends PlayJsonSupport {
  def unmarshal[T <: Questrade.QT](response: HttpResponse)(implicit um: Reads[T]):Future[T] = Unmarshal(response.entity).to[T]
  def loginUrl =
    if(practice) s"https://practicelogin.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=${refreshToken}"
    else s"https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=${refreshToken}"
  def login()(implicit um: Reads[Questrade.Login]) =
    Http().singleRequest(HttpRequest(uri = loginUrl)).flatMap(x => unmarshal(x))
}

class QuestradeApi(practice: Boolean = false, tokenProvider: Option[() => Future[Questrade.Login]] = None)(implicit system: ActorSystem, materializer: Materializer, ex: ExecutionContext) extends PlayJsonSupport {

  val temp: File = File.createTempFile("just-need-the-path", ".tmp")
  val teamPath = temp.getAbsolutePath
  val tokenFile = s"${teamPath.substring(0,teamPath.lastIndexOf(File.separator))}/war-pony-refresh.token"
  val config = ConfigFactory.load()
  var refreshToken = Try(scala.io.Source.fromFile(tokenFile)).toOption.map(_.mkString).getOrElse(config.getString("refreshToken"))
  println(s"refreshToken: ${refreshToken}")

  def baseUrl = s"${baseHost}v1/"

  def loginUrl =
    if(practice) s"https://practicelogin.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=${refreshToken}"
    else s"https://login.questrade.com/oauth2/token?grant_type=refresh_token&refresh_token=${refreshToken}"

  def refresh(when: FiniteDuration): Cancellable = system.scheduler.scheduleOnce(when) {
    def updateToken(l: Questrade.Login): Unit = {
      refresh(l.expires_in seconds)
      creds = l
    }
    tokenProvider match{
      case Some(tp) => tp().foreach(updateToken)
      case _ => login().foreach(updateToken)
    }
  }

  var creds = Await.result(tokenProvider match{
    case Some(tp) => tp()
    case _ => login()
  } , 5 seconds)
  refresh(creds.expires_in seconds)

  def getCreds = creds

  def storeLogin(login: Questrade.Login): Questrade.Login ={
    println(s"beep: ${login}")
    creds = login
    val writer = new PrintWriter(new File(tokenFile))
    writer.write(login.refresh_token)
    writer.close()
    println(s"creds: ${creds}")
    login
  }

  var baseHost = creds.api_server
  object httpApi extends QuestradeSignedRequester(baseUrl, getCreds _)

  def unmarshal[T <: Questrade.QT](response: HttpResponse)(implicit um: Reads[T]):Future[T] = Unmarshal(response.entity).to[T]

  def login()(implicit um: Reads[Questrade.Login]) =
    Http().singleRequest(HttpRequest(uri = loginUrl)).flatMap(x => unmarshal(x)).map(storeLogin)

  def accounts()(implicit um: Reads[Questrade.Accounts]) =
    httpApi.get("accounts").flatMap(x => unmarshal(x) )

  def positions(account: String)(implicit um: Reads[Questrade.Positions]) =
    httpApi.get(s"accounts/${account}/positions").flatMap(x => unmarshal(x) )

  def balances(account: String)(implicit um: Reads[Questrade.Balances]) =
    httpApi.get(s"accounts/${account}/balances").flatMap(x => unmarshal(x) )

  def executions(account: String, startTime: DateTime, endTime: DateTime)(implicit um: Reads[Questrade.Executions]) =
    httpApi.get(s"accounts/${account}/executions?startTime=${startTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&endTime=${endTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}").flatMap(x => unmarshal(x))

  def orders(account: String, startTime: DateTime, endTime: DateTime, orderId: Option[Int] = None, stateFilter: Questrade.OrderStateFilter = Questrade.OrderStateFilter.All)(implicit um: Reads[Questrade.Orders]) =
    httpApi.get(s"accounts/${account}/orders${orderId.map(x => s"/${x}").getOrElse("")}?startTime=${startTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&endTime=${endTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&stateFilter=${stateFilter.state}").flatMap(x => unmarshal(x))

  def candles(symbolId: Int, startTime: DateTime, endTime: DateTime, interval: Questrade.Interval)(implicit um: Reads[Questrade.Candles]) =
    httpApi.get(s"markets/candles/${symbolId}?startTime=${startTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&endTime=${endTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&interval=${interval.period}").flatMap(x => unmarshal(x))

  def search(prefix: String, offset: Int = 0)(implicit um: Reads[Questrade.Symbols]) =
    httpApi.get(s"symbols/search?prefix=${prefix}&offset=${offset}").flatMap(x => unmarshal(x))

  def quote(ids: Set[Int])(implicit um: Reads[Questrade.Quotes]) =
    httpApi.get(s"markets/quotes?ids=${ids.mkString("",",","")}").flatMap(x => unmarshal(x))

  def order(account: String, post: Questrade.PostOrder)(implicit um: Reads[Questrade.OrderResponse],uw: Writes[Questrade.PostOrder]) =
    httpApi.post[Questrade.PostOrder](s"accounts/${account}/orders${post.orderId.map(x => s"/${x}").getOrElse("")}", post).flatMap(x => unmarshal(x))

  def cancel(account: String, order: String)(implicit um: Reads[Questrade.OrderCancelConfirm]) =
    httpApi.delete(s"accounts/${account}/orders/${order}").flatMap(x => unmarshal(x))

  private[this] def notificationStreamPort()(implicit um: Reads[Questrade.StreamPort]) =
    httpApi.get("notifications?mode=WebSocket").flatMap(x => unmarshal(x) )

  private[this] def l1StreamPort(ids: Set[Int])(implicit um: Reads[Questrade.StreamPort]) =
    httpApi.get(s"markets/quotes?ids=${ids.mkString("",",","")}&stream=true&mode=WebSocket").flatMap(x => unmarshal(x) )

  def notifications(implicit um: Reads[Questrade.Orders]) =
    new QuestradeWebSocket[Questrade.Orders](() => notificationStreamPort.map(sp =>  s"wss://${new URL(baseHost).getHost}:${sp.streamPort}"), getCreds _ )

  //GET https://api01.iq.questrade.com/v1/markets/quotes?ids=9291,8049&stream=true&mode=WebSocket
  def l1Stream(ids: Set[Int]) =
    new QuestradeWebSocket[Questrade.Quotes]( () => l1StreamPort(ids).map(sp => s"wss://${new URL(baseHost).getHost}:${sp.streamPort}"), getCreds _ )



}