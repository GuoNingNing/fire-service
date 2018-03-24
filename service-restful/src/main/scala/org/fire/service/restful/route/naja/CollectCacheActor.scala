package org.fire.service.restful.route.naja

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
import CollectRouteConstantConfig._

import scala.collection.JavaConversions._

/**
  * Created by cloud on 18/3/9.
  */
class CollectCacheActor(val jedisConnect: JedisConnect,
                        val config: Config) extends Actor with ActorLogging {


  private val hostListKey = "hostIdList"
  private val nameListKey = "hostNameList"
  private val hostTimeout = config.getLong(HOST_TIMEOUT,HOST_TIMEOUT_DEF)
  private def nowTime = System.currentTimeMillis() - hostTimeout

  override def receive: Receive = {
    case host: Host => JedisConnect.redisSafe { jedis =>
      jedis.set(host.hostId,DataManager.generateJson(host))
      jedis.sadd(hostListKey,host.hostId)
      jedis.sadd(nameListKey,host.hostName)
    }(jedisConnect.connect())

    case HostRead(hostRow) =>
      sender ! JedisConnect.redisSafe {jedis =>
        val hostSeq = hostRow match {
          case Some(h) => Seq(jedis.get(h.id))
          case None =>
            val hostList = jedis.smembers(hostListKey)
            hostList.toList.map(jedis.get)
        }
        hostSeq.map(DataManager.parseHost).filter(_.mem.timestamp > nowTime)
      }(jedisConnect.connect())

    case RSet(k,v) => JedisConnect.redisSafe {
      jedis => jedis.set(k,v)
    }(jedisConnect.connect())

    case RGet(k) =>
      sender ! JedisConnect.redisSafe {
        jedis => jedis.get(k)
      }(jedisConnect.connect())
  }
}

object CollectCacheActor {
  val NAME = "cache-fire-service"

  def apply(jedisConnect: JedisConnect, config: Config): CollectCacheActor =
    new CollectCacheActor(jedisConnect, config)
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
