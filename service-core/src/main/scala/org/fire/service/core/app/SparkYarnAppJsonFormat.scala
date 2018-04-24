package org.fire.service.core.app

import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, DeserializationException, JsObject, JsString, JsValue, JsonFormat}

/**
  * Created by cloud on 18/4/24.
  */
object SparkYarnAppJsonFormat extends DefaultJsonProtocol with SprayJsonSupport {
  import SparkYarnAppManager._
  import SparkYarnAppEnumeration.AppType
  import SparkYarnAppEnumeration.AppType.AppType
  implicit val appType = new JsonFormat[AppType] {
    override def write(obj: AppType): JsValue = JsObject("appType" -> JsString(obj.toString))

    override def read(json: JsValue): AppType = json match {
      case JsString(obj) => AppType.withName(obj)
      case _ => throw DeserializationException("appType excepted.")
    }
  }
  implicit val appFormat = jsonFormat1(App)
  implicit val appScheduledFormat = jsonFormat2(AppScheduled)
  implicit val appStatusFormat = jsonFormat9(AppStatus)
}