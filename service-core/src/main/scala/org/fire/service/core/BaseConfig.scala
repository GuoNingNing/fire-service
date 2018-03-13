package org.fire.service.core

import java.util.{List => JList}

import com.typesafe.config.Config

import scala.reflect.ClassTag

/**
  * Created by cloud on 18/3/13.
  */
trait BaseConfig {
  implicit class ConfigExtend(val config: Config) {
    def getValue[T: ClassTag](path: String,default: T): T =
      if(config.hasPath(path)) config.getAnyRef(path).asInstanceOf[T] else default

    def getValueList[T: ClassTag](path: String,default: T) =
      if(config.hasPath(path)) config.getAnyRefList(path).asInstanceOf[T] else default


    def getString(path: String,default: String) = getValue[String](path,default)
    def getStringList(path: String,default: JList[String]) = getValueList[JList[String]](path,default)


    def getInt(path: String,default: Int) = getValue[Int](path,default)
    def getIntList(path: String,default: JList[Integer]) = getValueList[JList[Integer]](path,default)

    def getBoolean(path: String,default: Boolean) = getValue[Boolean](path,default)
    def getBooleanList(path: String,default: JList[Boolean]) = getValueList[JList[Boolean]](path,default)

    def getLong(path: String,default: Long) = getValue[Long](path,default)
    def getLongList(path: String,default: JList[Long]) = getValueList[JList[Long]](path,default)

    def getDouble(path: String,default: Double) = getValue[Double](path,default)
    def getDoubleList(path: String,default: JList[Double]) = getValueList[JList[Double]](path,default)
  }

}
