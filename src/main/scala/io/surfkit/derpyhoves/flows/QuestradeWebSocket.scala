package io.surfkit.derpyhoves.flows

import akka.actor.ActorRef
import akka.Done
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.Http
import akka.stream._
import akka.http.scaladsl.model.ws._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.typesafe.sslconfig.akka.AkkaSSLConfig
import io.surfkit.derpyhoves.actors.WsActor

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect._
import java.io.Serializable

import play.api.libs.json.{Json, Reads}

class QuestradeWebSocket[T <: Questrade.QT](endpoint: (Questrade.Login) => Future[String], creds: () => Future[Questrade.Login])(implicit val system: ActorSystem, um: Reads[T]) extends Serializable {
  import system.dispatcher

  private[this] val decider: Supervision.Decider = {
    case _ => Supervision.Resume
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  var responsers = Map.empty[ String, List[T => Unit] ]
  var restart = true

  def callResponders(txt: String) = {
    println(s"socket: ${txt}")
    if(!txt.contains("success")){
      val model = Json.parse(txt).as[T]
      val key = model.getClass.getName
      responsers.get(key).map{ res =>
        res.foreach(_(model))
      }
    }
  }

  // Future[Done] is the materialized value of Sink.foreach,
  // emted when the stream completes
  val incoming: Sink[Message, Future[Done]] =
  Sink.foreach[Message] {
    case message: TextMessage.Strict =>
      callResponders(message.text)

    case TextMessage.Streamed(stream) =>
      stream
        .limit(100)                   // Max frames we are willing to wait for
        .completionTimeout(5 seconds) // Max time until last frame
        .runFold("")(_ + _)           // Merges the frames
        .flatMap{msg =>
        callResponders(msg)
        Future.successful(msg)
      }

    case other: BinaryMessage =>
      println(s"Got other binary...")
      other.dataStream.runWith(Sink.ignore)
  }

  // send this as a message over the WebSocket
  //val outgoing = Source.single(TextMessage("hello world!"))
  //val outgoing = Source.actorPublisher(WsActor.props[T](this))
  def outgoing(login: Questrade.Login) = Source(List(TextMessage( login.access_token ))).concatMat(Source.maybe[Message])(Keep.right)

  val defaultSSLConfig = AkkaSSLConfig.get(system)

  def getAccessToken =
    Await.result(creds(), 15 seconds).access_token

  // FIXME: uhg how to get away from this Await here.. :(
  def webSocketFlow(url: String) = Http().webSocketClientFlow(WebSocketRequest(url, extraHeaders =
    scala.collection.immutable.Seq(Authorization(OAuth2BearerToken(getAccessToken)))
  ),connectionContext = Http().createClientHttpsContext(AkkaSSLConfig()))

  def connect:Future[UniqueKillSwitch] = {
    for{
      login <- creds()
      url <- endpoint(login)
    }yield{
      println(s"ws calling connect: ${url} width login: ${login}")
      /*val ref = Flow[TextMessage]
        .keepAlive(30 seconds, () => TextMessage(" "))
        // http://stackoverflow.com/questions/37716218/how-to-keep-connection-open-for-all-the-time-in-websockets
        //.keepAlive(25 minutes, () => TextMessage(creds().access_token))
        .viaMat(webSocketFlow(url))(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
        .toMat(incoming)(Keep.both) // also keep the Future[Done]
        .runWith(outgoing)
      ref ! login
      ref*/
      val (killSwitch, closed) =
        outgoing(login)
          .viaMat(webSocketFlow(url))(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
          .viaMat(KillSwitches.single)(Keep.right)
          .toMat(incoming)(Keep.both) // also keep the Future[Done]
          .run()
      closed.map{_ =>
        println("Socket close...")
        if(restart) {
          println("reconnecting ..")
          killSwitchFuture = connect
        }
      }
      killSwitch
    }
  }

  println(s"ws call connect")
  var killSwitchFuture = connect

  def shutdown: Future[Unit] = {
    restart = false
    killSwitchFuture.map(_.shutdown())
  }

  def subscribe[T : ClassTag](handler: T => Unit ) = {
    val key = classTag[T].runtimeClass.getName
    responsers += (responsers.get(key) match{
      case Some(xs) => key -> (handler.asInstanceOf[Questrade.QT => Unit] :: xs)
      case None => key -> (handler.asInstanceOf[Questrade.QT => Unit] :: Nil)
    })
  }


  var onError = { t:Throwable => t.getStackTrace }

}
