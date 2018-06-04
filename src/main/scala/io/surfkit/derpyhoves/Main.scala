package io.surfkit.derpyhoves

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import io.surfkit.derpyhoves.flows._
import org.joda.time.DateTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.Json

import scala.concurrent.Await

object Main extends App{

  override def main(args: Array[String]) {

    val decider: Supervision.Decider = {
      case _ => Supervision.Resume
    }
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    try {
      val api = new QuestradeApi(false)
      import Questrade._


      val watchList = List(
        "FB", "BABA", "GOOG", "AAPL", "TSLA", "MSFT", "NVDA", "AMZN", "CRM", "GOOGL", "ADBE", "NFLX", "INTC", "BIDU",
        "ADP", "ADSK", "ATVI", "AVGO", "CSCO", "CTXS", "EA", "EXPE", "INFY", "ORCL", "QCOM", "NXPI"
      )

      val ids = watchList.flatMap{ sym =>
        val sx = api.search(sym)
        val s =  Await.result(sx, 10 seconds)
        println(s"sx: ${s.symbols.headOption.map(_.symbolId)}")
        s.symbols.headOption.map{ x =>
          (x.symbol, x.symbolId)
        }
      }

      println(s"ids: ${ids}")




      /*val json =
        """
          |{"quotes":[{"symbol":"BABA","symbolId":7422546,"tier":"","bidPrice":null,"bidSize":0,"askPrice":null,"askSize":0,"lastTradePriceTrHrs":null,"lastTradePrice":null,"lastTradeSize":0,"lastTradeTick":null,"lastTradeTime":null,"volume":0,"openPrice":null,"highPrice":null,"lowPrice":null,"delay":0,"isHalted":false,"high52w":null,"low52w":null,"VWAP":null}]}
        """.stripMargin

      val test = Json.parse(json).as[Questrade.Quotes]
      println(s"test: ${test}")*/

      val fx = api.accounts()
      val f= Await.result(fx, 10 seconds)
      println(s"fx: ${f}")

      val account = f.accounts.head

      /*
      val px = api.positions(account.number)
      val p =  Await.result(px, 10 seconds)
      println(s"px: ${p}")

      val bx = api.balances(account.number)
      val b =  Await.result(bx, 10 seconds)
      println(s"bx: ${b}")

      val ex = api.executions(account.number, DateTime.now.plusDays(-100), DateTime.now)
      val e =  Await.result(ex, 10 seconds)
      println(s"bx: ${e}")

      val ox = api.orders(account.number, DateTime.now.plusDays(-100), DateTime.now)
      val o =  Await.result(ox, 10 seconds)
      println(s"ox: ${o}")

      val qx = api.quote(Set(s.symbols.head.symbolId))
      val q =  Await.result(qx, 10 seconds)
      println(s"qx: ${q}")

      val end = DateTime.now.secondOfMinute().setCopy(0)
      val nowMinus1 = end.plusMinutes(-2)
      val cx = api.candles(s.symbols.head.symbolId, nowMinus1, end, Questrade.Interval.OneMinute)
      val c =  Await.result(cx, 10 seconds)
      println(s"cx: ${c}")

      val ticker = QuestradeOneMinuteTicker(api.getCreds _, s.symbols.head.symbolId)
      ticker.json.runForeach(i => i.foreach(x => println(s"meep: ${x}")) )(materializer)
*/
     /* val l1 = api.l1Stream(Set(16829065,11419766,2067121,9199,8674,40611,7410,6635,7161,4870386,27454,41084,24177,19879,11850217,33237,31867,16996,23591,8049,13648,44247,37125,14281,35327,40349,17173,7422546,24535,28768,24344,8689,29814,8531079,6280,29251,23205,30678,13004,6770,27426,11419765,11326,15012,38526,16142))
      l1.subscribe({ quote: Questrade.Quotes =>
        println(s"GOT QUOTE: ${quote}")
      })*/

     /* Set(16829065,11419766,2067121,9199).map{ sym =>
        api.candles(sym, DateTime.now.minusDays(30), DateTime.now, Questrade.Interval.OneHour ).map{ candles =>
          println(s"CANDLES: ${candles.candles.map(_.close)}")
        }
      }*/

      /*val notifications = api.notifications
      notifications.subscribe{ orders: Questrade.Orders =>
        println(s"GOT ORDER NOTIFICATION: ${orders}")
        orders.orders.foreach{ order =>
          order.state match{
            case Questrade.OrderState.Executed.state if order.orderType == OrderType.Market.name && order.side == OrderAction.Buy.action =>
              val price = order.priceInfo.avgExecPrice.getOrElse(0.0)
              // set the stops
              val stop = Questrade.PostOrder(
                symbolId = order.symbolId,
                timeInForce = Questrade.OrderTimeInForce.Day.name,
                quantity = order.quantityInfo.filledQuantity.getOrElse(0),
                icebergQuantity = None,
                limitPrice = None,
                stopPrice = Some( Math.round((price*0.0004)*1000.0).toDouble / 1000.0 ),
                isAllOrNone = false,
                isAnonymous = false,
                orderType = Questrade.OrderType.TrailStopInDollar.name,
                action = Questrade.OrderAction.Sell.action
              )
              api.order(account.number, stop)

            case Questrade.OrderState.Executed.state if order.side == OrderAction.Sell.action =>
              println("WE DID A SELL !!!!")
          }
        }

      }*/


      Thread.currentThread.join()
    }catch{
      case t:Throwable =>
        t.printStackTrace()
    }

  }

}
