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
      val api = new QuestradeApi(true)
      import Questrade._

      val sx = api.search("BABA")
      val s =  Await.result(sx, 10 seconds)
      println(s"sx: ${s}")


      val json =
        """
          |{"quotes":[{"symbol":"BABA","symbolId":7422546,"tier":"","bidPrice":null,"bidSize":0,"askPrice":null,"askSize":0,"lastTradePriceTrHrs":null,"lastTradePrice":null,"lastTradeSize":0,"lastTradeTick":null,"lastTradeTime":null,"volume":0,"openPrice":null,"highPrice":null,"lowPrice":null,"delay":0,"isHalted":false,"high52w":null,"low52w":null,"VWAP":null}]}
        """.stripMargin

      val test = Json.parse(json).as[Questrade.Quotes]
      println(s"test: ${test}")

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

      val l1 = api.l1Stream(Set(s.symbols.head.symbolId))
      l1.subscribe({ quote: Questrade.Quotes =>
        println(s"GOT QUOTE: ${quote}")
      })*/

      val notifications = api.notifications
      notifications.subscribe{ orders: Questrade.Orders =>
        println(s"GOT ORDER NOTIFICATION: ${orders}")
        orders.orders.foreach{ order =>
          order.state match{
            case Questrade.OrderState.Executed.state if order.orderType == OrderType.Market.name && order.side == OrderAction.Buy.action =>
              val price = order.priceInfo.avgExecPrice.getOrElse(0.0)
              // set the stops
              val stop = Questrade.PostOrder(
                orderId = None,
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

      }

      Thread.sleep(4000)

      val buyF = api.order(account.number, Questrade.PostOrder(
        orderId = None,
        timeInForce = Questrade.OrderTimeInForce.FillOrKill.name,
        symbolId = s.symbols.head.symbolId,
        quantity = 1,
        icebergQuantity = None,
        limitPrice = None,
        stopPrice = None,
        isAllOrNone = false,
        isAnonymous = true,
        orderType = Questrade.OrderType.Market.name,
        action = Questrade.OrderAction.Buy.action
      ))
      val buy =  Await.result(buyF, 10 seconds)
      println(s"buy: ${buy}")

      Thread.currentThread.join()
    }catch{
      case t:Throwable =>
        t.printStackTrace()
    }

  }

}
