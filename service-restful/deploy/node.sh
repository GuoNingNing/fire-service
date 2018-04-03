#!/bin/bash -eu

usage="Usage: node.sh nodeType (start|restart|stop|status)"

if [ $# -eq 1 ]; then
  echo $usage
  exit 1
fi

### 脚本所在目录
appdir=$(cd $(dirname $0);pwd)

nodeType=$1
shift
command=$1

LIB_DIR=$appdir/lib
PID_DIR=$appdir/pid
LOG_DIR=$appdir/log
SCALA_HOME=/home/hdfs/gn/scala-2.11.8

CDH_HOME=/opt/cloudera/parcels/CDH/lib
HADOOP_CONF=/etc/hive/conf:/etc/hadoop/conf

pid=$PID_DIR/$nodeType.pid

CP=.:$LIB_DIR/*:$SCALA_HOME/lib/*


_start(){
if [ -f $pid ]; then
      if kill -0 `cat $pid` > /dev/null 2>&1; then
        echo $nodeType node running as process `cat $pid`.  Stop it first.
        exit 1
      fi
    fi
    if [ ! -d "$PID_DIR" ]; then mkdir -p $PID_DIR; fi
    if [ ! -d "$LOG_DIR" ]; then mkdir -p $LOG_DIR; fi
    nohup /usr/java/jdk/bin/java -cp $CP org.fire.service.restful.FireService $nodeType >> $LOG_DIR/$nodeType.log &
    nodeType_PID=$!
    echo $nodeType_PID > $pid
    echo "Started $nodeType node ($nodeType_PID)"
}
_stop(){
    if [ -f $pid ]; then
      TARGET_PID=`cat $pid`
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo Stopping process `cat $pid`...
        kill $TARGET_PID
      else
        echo No $nodeType node to stop
      fi
      rm -f $pid
    else
      echo No $nodeType node to stop
    fi
}

_status(){
  if [ -f $pid ]; then
      if kill -0 `cat $pid` > /dev/null 2>&1; then
        echo RUNNING
        exit 0
      else
        echo STOPPED
      fi
    else
      echo STOPPED
    fi
}

case $command in

  (start)
   _start
    ;;

  (restart)
    _stop
    _start
  ;;

  (stop)
    _stop
    ;;

   (status)
    _status
    ;;

  (*)
    echo $usage
    exit 1
    ;;
esac