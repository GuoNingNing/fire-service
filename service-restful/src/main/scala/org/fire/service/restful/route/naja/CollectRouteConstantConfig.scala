package org.fire.service.restful.route.naja

import org.fire.service.core.BaseConfig


/**
  * Created by cloud on 18/3/13.
  */
object CollectRouteConstantConfig extends BaseConfig {
  val CONFIG_PREFIX = "route.naja.collect"

  val FILE_SER_PATH = s"$CONFIG_PREFIX.file.base.path"
  val FILE_SER_PATH_DEF = "/Users/cloud/myfile/file"

  val DATA_MANAGER_MODE = s"$CONFIG_PREFIX.data.manager.mode"
  val DATA_MANAGER_MODE_DEF = "restful"

  val DB_ACTOR_NAME = "db-file-service"
  val CACHE_ACTOR_NAME = "cache-fire-service"
  val LOAD_ACTOR_NAME = "load-fire-service"
}
