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
  import SparkYarnAppEnumeration.AppStateType
  import SparkYarnAppEnumeration.AppStateType.AppStateType
  implicit val appTypeFormat = new JsonFormat[AppType] {
    override def write(obj: AppType): JsValue = JsString(obj.toString)

    override def read(json: JsValue): AppType = json match {
      case JsString(obj) => AppType.withName(obj)
      case _ => throw DeserializationException("appType excepted.")
    }
  }
  implicit val appStateTypeFormat = new JsonFormat[AppStateType] {
    override def write(obj: AppStateType): JsValue = JsString(obj.toString)

    override def read(json: JsValue): AppStateType = json match {
      case JsString(obj) => AppStateType.withName(obj)
      case _ => throw DeserializationException("appState excepted.")
    }
  }
  implicit val appFormat = jsonFormat3(App)
  implicit val appOnceFormat = jsonFormat1(AppOnce)
  implicit val appScheduledFormat = jsonFormat2(AppScheduled)
  implicit val appStatusFormat = jsonFormat9(AppStatus)
}