package org.fire.service.restful.actor.naja

import java.util.concurrent.ConcurrentHashMap
import java.util.{HashMap => JHashMap}

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import org.fire.service.restful.route.naja.CollectRouteConstantConfig._
import org.fire.service.restful.route.naja._
import org.fire.service.restful.route.naja.DataFormat._
import spray.json._
import org.slf4j.LoggerFactory
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}

import scala.collection.JavaConversions._
import scala.reflect.ClassTag
import scala.util.Try

/**
  * Created by cloud on 18/3/9.
  */
class CacheActor(val config: Config) extends Actor with ActorLogging {

  import CacheDataStruct._

  private val jedisConnect = JedisConnect(config.getConfig("redis.connect"))

  private val nameMapId = new ConcurrentHashMap[String, String]()
  private val nameMapIdKey = "collect_cache_actor_name_to_id"
  private val hostTimeout = config.getInt(HOST_TIMEOUT,HOST_TIMEOUT_DEF)
  private def nowTime = System.currentTimeMillis() - hostTimeout

  override def preStart(): Unit = {
    JedisConnect.redisSafe { jedis =>
      val idToName = jedis.hgetAll(nameMapIdKey)
      idToName.foreach {
        case (id, name) => nameMapId.put(id, name)
      }
    }(jedisConnect.connect())
  }

  override def receive: Receive = {
    // 缓存host信息
    case host: Host => putHost(host)

    // 获取hosts信息,None时返回当前所有主机信息
    case HostRead(hostRow) => fetchHosts(hostRow)

    // 获取所有主机id,None时返回当前所有主机id
    case GetHostID(hostName) => fetchHostId(hostName)

    // redis 原始命令操作
    // redis String
    case rSet: RSet => cmdSet(rSet)
    case rGet: RGet => cmdGet(rGet)

    // redis Hash
    case rHSet: RHSet => cmdHSet(rHSet)
    case rHGet: RHGet => cmdHGet(rHGet)
    case rHDel: RHDel => cmdHDel(rHDel)
    case rHGetAll: RHGetAll => cmdHGetAll(rHGetAll)
    case rHExists: RHExists => cmdHExists(rHExists)
    case rHLen: RHLen => cmdHLen(rHLen)

    // redis List
    case rRPush: RRPush => cmdRPush(rRPush)
    case rLPush: RLPush => cmdLPush(rLPush)
    case rLindex: RLindex => cmdLindex(rLindex)
    case rRPop: RRPop => cmdRPop(rRPop)
    case rLPop: RLPop => cmdLPop(rLPop)

    // redis Key
    case rDel: RDel => cmdDel(rDel)
  }

  private def putHost(host: Host): Unit = {
    JedisConnect.redisSafe { jedis =>
      val setRedis = () => {
        val hostJson = host.toJson.compactPrint
        jedis.lpush(host.hostId,hostJson)
      }
      nameMapId.containsKey(host.hostId) match {
        case true => setRedis()
        case false =>
          nameMapId.put(host.hostId, host.hostName)
          jedis.hset(nameMapIdKey, host.hostId, host.hostName)
          setRedis()
      }
    }(jedisConnect.connect())
  }

  private def fetchHosts(hostRow: Option[HostRow]): Unit = {
    val id = hostRow match {
      case Some(hr) =>
        nameMapId.containsKey(hr.id) match {
          case true => List(hr.id)
          case false => nameMapId.filter(_._2 == hr.hostName).keys.toList
        }
      case None => nameMapId.keys.toList
    }
    val host = rCmd { redis =>
        id.map(i => redis.lindex(s"list_$i",0)).map(_.parseJson.convertTo[Host])
    }(List.empty[Host])
    sender ! host
  }

  private def fetchHostId(hostName: Option[String]): Unit = {
    hostName match {
      case Some(n) => sender ! nameMapId.filter(_._2 == n).keys.toList
      case None => sender ! nameMapId.keys.toList
    }
  }

  private def rCmd[R: ClassTag](f: (Jedis) => R)(default: => R = null.asInstanceOf[R]): R = {
    Try {
      JedisConnect.redisSafe(jedis => f(jedis))(jedisConnect.connect())
    }.getOrElse(default)
  }

  // redis String function
  private def cmdSet(rSet: RSet): Unit = sender ! rCmd(redis => redis.set(rSet.key, rSet.value))("")
  private def cmdGet(rGet: RGet): Unit = sender ! rCmd(redis => redis.get(rGet.key))("")

  // redis Hash function
  private def cmdHSet(rHSet: RHSet): Unit = sender ! rCmd(redis => redis.hset(rHSet.key, rHSet.filed, rHSet.value))(0L)
  private def cmdHGet(rHGet: RHGet): Unit = sender ! rCmd(redis => redis.hget(rHGet.key, rHGet.filed))("")
  private def cmdHDel(rHDel: RHDel): Unit = sender ! rCmd(redis => redis.hdel(rHDel.key, rHDel.filed))(0L)
  private def cmdHGetAll(rHGetAll: RHGetAll): Unit =
    sender ! rCmd(redis => redis.hgetAll(rHGetAll.key))(new JHashMap[String, String]())
  private def cmdHExists(rHExists: RHExists): Unit =
    sender ! rCmd(redis => redis.hexists(rHExists.key, rHExists.filed))(false)
  private def cmdHLen(rHLen: RHLen): Unit = sender ! rCmd(r => r.hlen(rHLen.key))(0L)

  // redis List function
  private def cmdRPush(rRPush: RRPush): Unit = sender ! rCmd(redis => redis.rpush(rRPush.key, rRPush.value: _*))(0L)
  private def cmdLPush(rLPush: RLPush): Unit = sender ! rCmd(redis => redis.lpush(rLPush.key, rLPush.value: _*))(0L)
  private def cmdLindex(rLindex: RLindex): Unit = sender ! rCmd(redis => redis.lindex(rLindex.key, rLindex.index))("")
  private def cmdRPop(rRPop: RRPop): Unit = sender ! rCmd(redis => redis.rpop(rRPop.key))("")
  private def cmdLPop(rLPop: RLPop): Unit = sender ! rCmd(redis => redis.lpop(rLPop.key))("")
  private def cmdRPopLPush(rRPopLPush: RRPopLPush): Unit =
    sender ! rCmd(redis => redis.rpoplpush(rRPopLPush.sourceKey, rRPopLPush.targetKey))("")

  // redis Key function
  private def cmdDel(rDel: RDel): Unit = sender ! rCmd(redis => redis.del(rDel.key))(0L)

}

object CacheActor {
  val NAME = "cache-fire-service"

  def apply(config: Config): CacheActor = new CacheActor(config)

  def props(config: Config): Props = Props(apply(config))
}

object CacheDataStruct {

  sealed trait RedisCMD extends Serializable

  // String
  case class RSet(key: String, value: String) extends RedisCMD
  case class RGet(key: String) extends RedisCMD

  // Hash
  case class RHSet(key: String, filed: String, value: String) extends RedisCMD
  case class RHGet(key: String, filed: String) extends RedisCMD
  case class RHDel(key: String, filed: String) extends RedisCMD
  case class RHExists(key: String, filed: String) extends RedisCMD
  case class RHGetAll(key: String) extends RedisCMD
  case class RHLen(key: String) extends RedisCMD

  // List
  case class RRPush(key: String, value: List[String]) extends RedisCMD
  case class RLPush(key: String, value: List[String]) extends RedisCMD
  case class RLindex(key: String, index: Long) extends RedisCMD
  case class RRPop(key: String) extends RedisCMD
  case class RLPop(key: String) extends RedisCMD
  case class RRPopLPush(sourceKey: String, targetKey: String) extends RedisCMD

  // Key
  case class RDel(key: String) extends RedisCMD

  case class GetHostID(hostName: Option[String])
}



class JedisConnect(val config: Config,
                   val poolConfig: Option[JedisPoolConfig]) {

  private val logger = LoggerFactory.getLogger(getClass)

  val jedisPool = createJedisPool()

  def connect(): Jedis = {
    jedisPool.getResource
  }

  def createJedisPool() = {
    val defaultPoolConfig: JedisPoolConfig = new JedisPoolConfig()
    /*最大连接数*/
    defaultPoolConfig.setMaxTotal(1000)
    /*最大空闲连接数*/
    defaultPoolConfig.setMaxIdle(64)
    /*在获取连接的时候检查有效性, 默认false*/
    defaultPoolConfig.setTestOnBorrow(true)
    defaultPoolConfig.setTestOnReturn(false)
    /*在空闲时检查有效性, 默认false*/
    defaultPoolConfig.setTestWhileIdle(false)
    /*逐出连接的最小空闲时间 默认1800000毫秒(30分钟)*/
    defaultPoolConfig.setMinEvictableIdleTimeMillis(1800000)
    /*逐出扫描的时间间隔(毫秒) 如果为负数,则不运行逐出线程, 默认-1*/
    defaultPoolConfig.setTimeBetweenEvictionRunsMillis(30000)
    defaultPoolConfig.setNumTestsPerEvictionRun(-1)

    val host = config.getString("host")
    val port = config.getInt("port")
    val db = config.getInt("db")
    val auth = if(config.hasPath("auth")) config.getString("auth") else null
    val timeout = config.getInt("timeout")

    new JedisPool(poolConfig.getOrElse(defaultPoolConfig), host, port, timeout, auth, db)
  }

}
object JedisConnect {
  private val logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config, poolConfig: Option[JedisPoolConfig]): JedisConnect = new JedisConnect(config,poolConfig)
  def apply(config: Config):JedisConnect = apply(config,None)

  def redisSafe[R](f: Jedis => R)(implicit jedis: Jedis): R = {
    val res = f(jedis)
    try{
      jedis.close()
    }catch {
      case e: Exception => logger.error("close jedis failed. ",e)
    }
    res
  }
}
