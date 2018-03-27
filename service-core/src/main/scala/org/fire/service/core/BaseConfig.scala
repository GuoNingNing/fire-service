package org.fire.service.core

import com.typesafe.config.Config

import scala.reflect.ClassTag
import scala.collection.JavaConversions._

/**
  * Created by cloud on 18/3/13.
  * 目前有些问题,感觉代码不是那么合理
  * 可以使用官网推荐的通过检查返回值来决定是否使用默认值
  */
trait BaseConfig {
  implicit class ConfigExtend(val config: Config) {
    def get[T: ClassTag](path: String,default: T)(f: String => T): T =
      if(config.hasPath(path)) f(path) else default


    def getString(path: String,default: String) = get(path,default)(config.getString)
    def getStringList(path: String,default: List[String]) = get(path,default){
      s => config.getStringList(s).toList
    }


    def getInt(path: String,default: Int) = get(path,default)(config.getInt)
    def getIntList(path: String,default: List[Int]) = get(path,default){
      s => config.getIntList(s).map(_.toInt).toList
    }

    def getBoolean(path: String,default: Boolean) = get(path,default)(config.getBoolean)
    def getBooleanList(path: String,default: List[Boolean]) = get(path,default){
      s => config.getBooleanList(s).map(_.booleanValue).toList
    }

    def getLong(path: String,default: Long) = get(path,default)(config.getLong)
    def getLongList(path: String,default: List[Long]) = get(path,default){
      s => config.getLongList(s).map(_.toLong).toList
    }

    def getDouble(path: String,default: Double) = get(path,default)(config.getDouble)
    def getDoubleList(path: String,default: List[Double]) = get(path,default){
      s => config.getDoubleList(s).map(_.toDouble).toList
    }
  }

}
