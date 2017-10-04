package io.surfkit.derpyhoves.flows

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.stream.Materializer
import akka.NotUsed
import akka.actor.{ActorSystem, Cancellable}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.joda.time.DateTimeZone
import org.joda.time.DateTime
import play.api.libs.json.{Json, Writes}
import scala.util.Try

/**
  * Created by suroot on 03/10/17.
  */


class QuestradePoller(url: String, interval: Questrade.Interval, fuzz: Double, hoursOpt: Option[DateTimeZone] = None)(implicit system: ActorSystem, materializer: Materializer) {
  import scala.concurrent.duration._
  def params = {
    val endTime = DateTime.now().secondOfMinute().setCopy(0)
    val startTime = endTime.plusSeconds(-interval.toDuration.toSeconds.toInt)
    s"?startTime=${startTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&endTime=${endTime.withZone(DateTimeZone.forID("US/Eastern")).toString("yyyy-MM-dd'T'HH:mm:ssZZ")}&interval=${interval.period}"
  }
  def request: akka.http.scaladsl.model.HttpRequest = RequestBuilding.Get(Uri(url+params))
  val initialDelat = (60.0-DateTime.now.getSecondOfMinute.toDouble) + (Math.random() * fuzz + 1.0)    // set to the end of the minute plus some fuzzy
  val source: Source[HttpRequest, Cancellable] = Source.tick(initialDelat.seconds, interval.toDuration, request).filter{ _ =>
    hoursOpt.map{ timezone =>
      val dt = new DateTime(timezone)
      dt.getHourOfDay >= 8 && dt.getHourOfDay <= 16 && dt.getDayOfWeek() >= org.joda.time.DateTimeConstants.MONDAY && dt.getDayOfWeek() <= org.joda.time.DateTimeConstants.FRIDAY
    }.getOrElse(true)
  }
  val sourceWithDest: Source[Try[HttpResponse], Cancellable] = source.map(req ⇒ (req, NotUsed)).via(Http().superPool[NotUsed]()).map(_._1)

  def apply(): Source[Try[HttpResponse], Cancellable] = sourceWithDest

  def shutdown = {
    Http().shutdownAllConnectionPools()
  }
}




class QuestradeSignedRequester(baseUrl: String, accessToken: String)(implicit system: ActorSystem, materializer: Materializer){
  def get(path: String) = {
    println(s"url: ${baseUrl}${path}")
    Http().singleRequest(HttpRequest(uri = s"${baseUrl}${path}").addHeader(Authorization(OAuth2BearerToken(accessToken))))
  }

  def post[T <: Questrade.QT](path: String, post: T)(implicit uw: Writes[T]) = {
    val data = ByteString(Json.stringify(uw.writes(post) ))
    println(s"url: ${baseUrl}${path}")
    Http().singleRequest(HttpRequest(method=HttpMethods.POST, uri = s"${baseUrl}${path}", entity=data).addHeader(Authorization(OAuth2BearerToken(accessToken))))
  }

  def delete(path: String) = {
    println(s"url: ${baseUrl}${path}")
    Http().singleRequest(HttpRequest(method=HttpMethods.DELETE, uri = s"${baseUrl}${path}").addHeader(Authorization(OAuth2BearerToken(accessToken))))
  }
}