#!/bin/bash -eu

usage="Usage: restfull.sh (start|restart|stop|status)"

if [ $# -ne 1 ]; then
  echo $usage
  exit 1
fi

#cp -r ../spark-server-1.0 modules/

#cp lib/inke-servlet-restfull-1.0.jar modules/spark-server-1.0/lib/

appdir=$(cd $(dirname $0);pwd)

sh $appdir/node.sh restfull $1

tailf log/restfull.log