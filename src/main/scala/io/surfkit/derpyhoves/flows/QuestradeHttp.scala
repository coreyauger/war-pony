package io.surfkit.derpyhoves.flows

import java.util.UUID

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.{KillSwitches, Materializer}
import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString

import scala.concurrent.Future
import org.joda.time.DateTimeZone
import org.joda.time.DateTime
import play.api.libs.json.{Json, Writes}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/**
  * Created by suroot on 03/10/17.
  */


class QuestradePoller(creds: () => Questrade.Login, path: String, interval: FiniteDuration, fuzz: Double, hoursOpt: Option[DateTimeZone] = None, params: FiniteDuration => String, alignMinute: Boolean = true)(implicit system: ActorSystem, materializer: Materializer) {
  import scala.concurrent.duration._

  def request: akka.http.scaladsl.model.HttpRequest = {
    val url = s"${creds().api_server}${path}${params(interval)}"
    val accessToken = creds().access_token
    println(s"curl -XGET '${url}' -H 'Authorization: Bearer ${accessToken}'")
    RequestBuilding.Get(Uri(url)).addHeader(Authorization(OAuth2BearerToken(creds().access_token)))
  }
  val initialDelay =
    if(alignMinute)(60.0-DateTime.now.getSecondOfMinute.toDouble) + (Math.random() * fuzz + 1.0)    // set to the end of the minute plus some fuzzy
    else 0
  val source: Source[() => HttpRequest, Cancellable] = Source.tick(initialDelay.seconds, interval, request _).filter{ _ =>
    hoursOpt.map{ timezone =>
      val dt = new DateTime(timezone)
      dt.getHourOfDay >= 8 && dt.getHourOfDay <= 16 && dt.getDayOfWeek() >= org.joda.time.DateTimeConstants.MONDAY && dt.getDayOfWeek() <= org.joda.time.DateTimeConstants.FRIDAY
    }.getOrElse(true)
  }
  val sharedKillSwitch = KillSwitches.shared(UUID.randomUUID().toString)

  val sourceWithDest: Source[Try[HttpResponse], Cancellable] =
    source.map(req ⇒ (req(), NotUsed)).via(Http().superPool[NotUsed]()).map(_._1)

  def apply(): Source[Try[HttpResponse], Cancellable] = sourceWithDest.via(sharedKillSwitch.flow)
  def shutdown = sharedKillSwitch.shutdown()

}


class QuestradeSignedRequester(creds: () => Future[Questrade.Login])(implicit system: ActorSystem, materializer: Materializer){
  import system.dispatcher

  def get(path: String) = {
    creds().flatMap { login =>
      val baseUrl = s"${login.api_server}v1/"
      println(s"curl -XGET '${baseUrl}${path}' -H 'Authorization: Bearer ${login.access_token}'")
      Http().singleRequest(HttpRequest(uri = s"${baseUrl}${path}").addHeader(Authorization(OAuth2BearerToken(login.access_token))))
    }
  }

  def post[T <: Questrade.QT](path: String, post: T)(implicit uw: Writes[T]) = {
    creds().flatMap { login =>
      val baseUrl = s"${login.api_server}v1/"
      val json = Json.stringify(uw.writes(post))
      val jsonEntity = HttpEntity(ContentTypes.`application/json`, json)
      println(s"curl -XPOST '${baseUrl}${path}' -H 'Authorization: Bearer ${login.access_token}' -d '${json}'")
      Http().singleRequest(HttpRequest(method = HttpMethods.POST, uri = s"${baseUrl}${path}", entity = jsonEntity).addHeader(Authorization(OAuth2BearerToken(login.access_token))))
    }
  }

  def delete(path: String) = {
    creds().flatMap { login =>
      val baseUrl = s"${login.api_server}v1/"
      println(s"url: ${baseUrl}${path}")
      Http().singleRequest(HttpRequest(method = HttpMethods.DELETE, uri = s"${baseUrl}${path}").addHeader(Authorization(OAuth2BearerToken(login.access_token))))
    }
  }
}