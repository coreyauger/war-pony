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
import scala.concurrent.Future
import scala.reflect._
import java.io.Serializable

import play.api.libs.json.{Json, Reads}


class QuestradeWebSocket(endpoint: String = "ws://api01.iq.questrade.com:8080", accessToken: String)(implicit val system: ActorSystem, um: Reads[Questrade.QT]) extends Serializable {

  import system.dispatcher

  private[this] val decider: Supervision.Decider = {
    case _ => Supervision.Resume
  }

  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(decider))

  var responsers = Map.empty[ String, List[Questrade.QT => Unit] ]

  def callResponders(txt: String) = {
    //println(s"socket: ${txt}")
    //val event = mapper.readValue(txt,classOf[SocketEvent[m.Model]])
    val model = Json.parse(txt).as[Questrade.QT]
    val key = model.getClass.getName
    //println(s"looking up key: ${key}")
    responsers.get(key).map{ res =>
      //println("got socket event .. calling.")
      res.foreach(_(model))
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
  val outgoing = Source.actorPublisher(WsActor.props(this))

  val defaultSSLConfig = AkkaSSLConfig.get(system)

  def webSocketFlow = Http().webSocketClientFlow(WebSocketRequest(endpoint, extraHeaders =
    scala.collection.immutable.Seq(Authorization(OAuth2BearerToken(accessToken)))
  ),connectionContext = Http().createClientHttpsContext(AkkaSSLConfig()))

  def connect:ActorRef = {
    ref = Flow[TextMessage]
      // http://stackoverflow.com/questions/37716218/how-to-keep-connection-open-for-all-the-time-in-websockets
     /* .keepAlive(45.seconds, () => TextMessage(
      """
        |{
        | "correlationId": "hb",
        | "occurredAt": "2000-01-02 10:20:00",
        | "payload":{"$type": "m.State.Hb"}
        | }
      """.stripMargin))*/
      .viaMat(webSocketFlow)(Keep.right) // keep the materialized Future[WebSocketUpgradeResponse]
      .toMat(incoming)(Keep.both) // also keep the Future[Done]
      .runWith(outgoing)
    ref
  }

  var ref = connect
  var cancel: Option[Cancellable] = None
  var delay = 0

  def onNext(x: Questrade.QT): Unit = {
    ref ! x
  }

  def subscribe[T : ClassTag](handler: Questrade.QT => Unit ) = {
    val key = classTag[T].runtimeClass.getName
    responsers += (responsers.get(key) match{
      case Some(xs) => key -> (handler.asInstanceOf[Questrade.QT => Unit] :: xs)
      case None => key -> (handler.asInstanceOf[Questrade.QT => Unit] :: Nil)
    })
  }


  var onError = { t:Throwable => Unit }

}
