package org.fire.service.core

import akka.actor.Actor
import org.slf4j.{Logger, LoggerFactory}

/**
  * Created by guoning on 2018/1/25.
  *
  * preStart：在actor实例化后执行，重启时不会执行。
  * postStop：在actor正常终止后执行，异常重启时不会执行。
  * preRestart：在actor异常重启前保存当前状态。
  * postRestart：在actor异常重启后恢复重启前保存的状态。当异常引起了重启，新actor的postRestart方法被触发，默认情况下preStart方法被调用。
  *
  *
  */
trait BaseActor extends Actor {
  val logger: Logger = LoggerFactory.getLogger(getClass)
}