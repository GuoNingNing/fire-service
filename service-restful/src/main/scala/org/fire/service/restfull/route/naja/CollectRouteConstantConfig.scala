package org.fire.service.restfull.route.naja

import org.fire.service.core.BaseConfig


/**
  * Created by cloud on 18/3/13.
  */
object CollectRouteConstantConfig extends BaseConfig {
  val CONFIG_PREFIX = "route.naja.collect"

  val FILE_SER_PATH = s"$CONFIG_PREFIX.file.base.path"
  val FILE_SER_PATH_DEF = "/Users/cloud/myfile/file"

  val HOST_TIMEOUT = s"$CONFIG_PREFIX.host.timeout"
  val HOST_TIMEOUT_DEF = 15000

  val DATA_MANAGER_REST = s"$CONFIG_PREFIX.data.manager.rest"
  val DATA_MANAGER_REST_DEF = true

}
