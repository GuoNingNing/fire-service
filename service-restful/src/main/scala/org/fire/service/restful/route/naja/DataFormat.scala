package org.fire.service.restful.route.naja

import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by cloud on 18/3/2.
  */
object DataFormat extends DefaultJsonProtocol with SprayJsonSupport{
  implicit val echoFormat = jsonFormat2(Echo)
  implicit val memFormat = jsonFormat6(Memory)
  implicit val cpuFormat = jsonFormat3(Cpu)
  implicit val netCardFormat = jsonFormat4(NetCard)
  implicit val processInfoFormat = jsonFormat1(ProcessInfo)
  implicit val roleFormat = jsonFormat3(Role)
  implicit val diskFormat = jsonFormat8(Disk)
  implicit val hostFormat = jsonFormat9(Host)
  implicit val colorFormat = jsonFormat2(Color.apply)
}

case class Echo(info : String,time : Int)
case class Memory(total : Long,
                  used : Long,
                  free : Long,
                  buffer : Long,
                  shared : Long,
                  cached : Long)
case class Cpu(idle : Float,
               user : Float,
               sys : Float)
case class NetCard(name : String,
                   ip : String,
                   link : Int,
                   totalLink : Int)
case class Disk(mount : String,
                device : String,
                fsType : String,
                total : Long,
                used : Long,
                percent : Float,
                read : Double,
                write : Double)
case class ProcessInfo(total : Int)
case class Role(name : String,
                status : Int,
                info : String)
case class Host(hostId : String,
                hostName : String,
                user : String,
                mem : Memory,
                cpu : Cpu,
                net : List[NetCard],
                disk : List[Disk],
                proc : ProcessInfo,
                role : List[Role])

case class Color(a : String,b : List[Echo])


