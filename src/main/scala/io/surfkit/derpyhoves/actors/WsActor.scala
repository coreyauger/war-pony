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
  def props[T <: Questrade.QT](wsBot: QuestradeWebSocket[T]): Props = Props(classOf[WsActor], wsBot)
}

class WsActor(wsBot: QuestradeWebSocket[Questrade.QT]) extends ActorPublisher[TextMessage] {

  val requestQueue = mutable.Queue[Questrade.Login]()

  def receive = {
    case x: Questrade.Login =>
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
      val json = x.access_token
      try {
        println(s"ws send: ${json}")
        onNext(TextMessage(json))
      }catch{
        case t: Throwable =>
          t.printStackTrace()
      }
    }
  }

}