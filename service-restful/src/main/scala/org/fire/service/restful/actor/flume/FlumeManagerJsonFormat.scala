package org.fire.service.restful.actor.flume

import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by cloud on 18/8/31.
  */
object FlumeManagerJsonFormat extends DefaultJsonProtocol with SprayJsonSupport {
  import FlumeManager._

  implicit val responseFormat = jsonFormat2(FlumeResponse)
  implicit val flumeFormat = jsonFormat2(Flume)
  implicit val jarFormat = jsonFormat2(FlumeJar)
  implicit val instanceFormat = jsonFormat4(FlumeInstance)
  implicit val statusFormat = jsonFormat2(FlumeStatus)
  implicit val startFormat = jsonFormat1(StartInstance)
  implicit val stopFormat = jsonFormat1(StopInstance)
  implicit val stateFormat = jsonFormat1(StatusInstance)
}
