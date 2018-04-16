package org.fire.service.core

import org.fire.service.core.app.AppInfo
import org.fire.service.core.app.AppManager.Submit
import spray.httpx.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, JsArray, JsFalse, JsNull, JsNumber, JsObject, JsString, JsTrue, JsValue, JsonFormat, RootJsonFormat, deserializationError, _}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.language.reflectiveCalls

/**
  * Created by guoning on 2018/1/25.
  *
  * 封装统一返回结果
  *
  * @param code 返回码：0 -> 成功 非零 -> 失败
  * @param msg
  * @param data
  */
case class ResultMsg(code: Int, msg: String, data: Any = null)


case class User(name: String, age: Int)

object ResultJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {

  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue = x match {
      case n: Int => JsNumber(n)
      case l: Long => JsNumber(l)
      case d: Double => JsNumber(d)
      case f: Float => JsNumber(f.toDouble)
      case s: String => JsString(s)
      case x: Seq[_] => seqFormat[Any].write(x)
      case m: Map[_, _] if m.isEmpty => JsObject(Map[String, JsValue]())
      // Get the type of map keys from the first key, translate the rest the same way
      case m: Map[_, _] => m.keys.head match {
        case sym: Symbol =>
          val map = m.asInstanceOf[Map[Symbol, _]]
          val pairs = map.map { case (_sym, v) => _sym.name -> write(v) }
          JsObject(pairs)
        case s: String => mapFormat[String, Any].write(m.asInstanceOf[Map[String, Any]])
        case a: Any =>
          val map = m.asInstanceOf[Map[Any, _]]
          val pairs = map.map { case (_sym, v) => _sym.toString -> write(v) }
          JsObject(pairs)
      }
      case a: Array[_] => seqFormat[Any].write(a.toSeq)
      case true => JsTrue
      case false => JsFalse
      case p: Product => seqFormat[Any].write(p.productIterator.toSeq)
      case null => JsNull
      case m: java.util.Map[_, _] => AnyJsonFormat.write(m.asScala.toMap)
      case l: java.util.List[_] => seqFormat[Any].write(l.asScala)
      case str => JsString(str.toString)
    }

    def read(value: JsValue): Any = value match {
      case JsNumber(n) => n.intValue()
      case JsString(s) => s
      case a: JsArray => listFormat[Any].read(value)
      case o: JsObject => mapFormat[String, Any].read(value)
      case JsTrue => true
      case JsFalse => false
      case x => deserializationError("Do not understand how to deserialize " + x)
    }
  }

  def anyToJson(any: Any, compact: Boolean = true): String = {
    val jsonAst = any.toJson
    if (compact) jsonAst.compactPrint else jsonAst.prettyPrint
  }

  def mapToJson(map: Map[String, Any], compact: Boolean = true): String = {
    val jsonAst = map.toJson
    if (compact) jsonAst.compactPrint else jsonAst.prettyPrint
  }

  def listToJson(list: Seq[Any], compact: Boolean = true): String = {
    val jsonAst = list.toJson
    if (compact) jsonAst.compactPrint else jsonAst.prettyPrint
  }

  def mapFromJson(json: String): Map[String, Any] = json.toJson.convertTo[Map[String, Any]]

  def listFromJson(json: String): Seq[Any] = json.toJson.convertTo[Seq[Any]]

  implicit def resultFormats: RootJsonFormat[ResultMsg] = jsonFormat3(ResultMsg)

  implicit def userFormats: RootJsonFormat[User] = jsonFormat2(User)

  implicit def submitInfoFormats: RootJsonFormat[Submit] = jsonFormat2(Submit)

  implicit def appFormats: RootJsonFormat[AppInfo] = jsonFormat7(AppInfo)

  def success(data: Any) = ResultMsg(0, "success", data)

  def failure(content: String) = ResultMsg(1, content)

  // 失败
  def failure(code: Int, content: String) = ResultMsg(code, content)

}


