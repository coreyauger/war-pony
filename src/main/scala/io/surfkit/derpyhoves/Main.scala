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
      val json =
        """
          |{"symbols":[{"symbol":"BABA","symbolId":7422546,"description":"ALIBABA GROUP HOLDINGS","securityType":"Stock","listingExchange":"NYSE","isTradable":true,"isQuotable":true,"currency":"USD"},{"symbol":"BABA","symbolId":1775254,"description":"BABY ALL CORP","securityType":"Stock","listingExchange":"OTCBB","isTradable":false,"isQuotable":false,"currency":"USD"}]}
        """.stripMargin

      val test = Json.parse(json).as[Questrade.Symbols]
      println(s"test: ${test}")

      val refeshToken = "QnCMVfVqqQCyUCqH1c7ruFdzECiuzp_G0"

      val api = new QuestradeApi(refeshToken, false)
      import Questrade._

      val fx = api.accounts()
      val f= Await.result(fx, 10 seconds)
      println(s"fx: ${f}")

      val account = f.accounts.head

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

      val sx = api.search("BABA")
      val s =  Await.result(sx, 10 seconds)
      println(s"sx: ${s}")

      val qx = api.quote(Set(s.symbols.head.symbolId))
      val q =  Await.result(qx, 10 seconds)
      println(s"qx: ${q}")

      val end = DateTime.now.secondOfMinute().setCopy(0)
      val nowMinus1 = end.plusMinutes(-2)
      val cx = api.candles(9292, nowMinus1, end, Questrade.Interval.OneMinute)
      val c =  Await.result(cx, 10 seconds)
      println(s"cx: ${c}")

      val ticker = QuestradeOneMinuteTicker(9292)
      ticker.json.runForeach(i => i.foreach(println) )(materializer)

      Thread.currentThread.join()
    }catch{
      case t:Throwable =>
        t.printStackTrace()
    }

  }

}
