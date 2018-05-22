package org.fire.service.restfull.actor

import org.fire.service.core.BaseSupervisor

/**
  * Created by guoning on 2018/3/8.
  *
  */
class HelloActor extends BaseSupervisor {

  override def receive = {
    case "hi" => sender() ! "hello"
    case _ => sender() ! "Sorry I don't know what are you talking about, Try to say hi to me。"
  }
}
