package io.surfkit.derpyhoves.actors

import akka.actor.Props
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.ActorPublisher
import io.surfkit.derpyhoves.flows.{Questrade, QuestradeWebSocket}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{Json, Writes}

import scala.collection.mutable


object WsActor{
  def props(wsBot: QuestradeWebSocket): Props = Props(classOf[WsActor], wsBot)
}

class WsActor(wsBot: QuestradeWebSocket)(implicit um: Writes[Questrade.QT]) extends ActorPublisher[TextMessage] {

  val requestQueue = mutable.Queue[Questrade.QT]()

  def receive = {
    case x: Questrade.QT=>
      requestQueue.enqueue(x)
      sendReq
    case OnComplete =>
      onComplete()
      context.stop(self)
    case x: Request =>
      sendReq
    case x: Cancel =>
      context.stop(self)
      context.system.scheduler.scheduleOnce(10 seconds) {
        wsBot.connect // FIXME: this is cheesy as hell !!!
      }

    case x =>
      println(s"ERROR: Unknown message for BotActor: ${x}")
  }

  def sendReq() {
    while(isActive && totalDemand > 0 && !requestQueue.isEmpty) {
      val x = requestQueue.dequeue()
      val json = Json.stringify(um.writes(x))
      try {
        onNext(TextMessage(json))
      }catch{
        case t: Throwable =>
          t.printStackTrace()
      }
    }
  }

}