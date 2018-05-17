#!/usr/bin/env bash
#如果想知道所有特性开关请使用set -o打印,是否需要各种特性请自行斟酌
#如果想开启errexit就打开下一行注释
#set -e
#如果想开启nounset就打开下一行注释
#set -u
bin_dir=$(cd $(dirname $0);pwd)
base=$(cd $bin_dir/..;pwd)


function help(){
	local usage="Usage: $base/$0 nodeType (start|restart|stop|status)"
	echo $usage
}

function create_dir(){
	test $# -ne 2 && return

	test ! -d $2 && mkdir -p $2
	test "${!1}" == "" && eval "$1=$2"
}

function include(){
	local f=${1:-"$HOME/.bash_profile"}
	test -f $f && {
		. $f;
	}
}

function build_classpath(){
	local lib_dir=${1:-"$base/lib"}
	test ! -d $lib_dir && {
		echo ".:..";
		return;
	}
	local classpath="."
	local jar=""
	for jar in $(ls $lib_dir)
	do
		classpath="$classpath:$lib_dir/$jar"
	done
	echo $classpath
}

function get_pid(){
	local pid_dir=${1:-"$base/run"}
	local node=$2
	local pid=""
	test ! -d $pid_dir && mkdir -p $pid_dir
	if [ -f "$pid_dir/${node}.pid" ];then
		pid=$(cat $pid_dir/${node}.pid)
		test "$pid" == "" && return
		if ! kill -0 $pid >/dev/null 2>&1;then
			local rpid=$(ps -ef | grep $main_class | grep $base | grep -v grep | awk '{print $2}')
			if [ "$rpid" != "" ];then
				test "$pid" != "$rpid" && { 
					pid=$rpid;
					echo $pid > $pid_dir/${node}.pid
				}
			fi
		fi
	fi
	echo $pid
}


function start(){
	local node=$1
	local pid=$(get_pid $run_dir $node)
	local java=$JAVA_HOME/bin/java
	local classpath=$(build_classpath $lib_dir)
	
	test ! -d $base/log && mkdir -p $base/log
	if [ "$pid" == "" ];then
		$java -cp $classpath org.fire.service.restful.FireService $node >$log_dir/$node.log.$(date +%Y%m%d%H%M%S) 2>&1 &
		echo $! > $run_dir/${node}.pid
		echo "$node start success."
	else
		echo "$node already started. $pid" >&2
	fi
}
function stop(){
    local node=$1
	local pid=$(get_pid $run_dir $node)
	if [ "$pid" == "" ];then
		echo "$node already stop." >&2
	else
		kill -9 $pid
		rm -rf $run_dir/${node}.pid
		echo "$node stop success."
	fi
}
function check_env(){
	local envp=$1
	test "$envp" == "" && {
		echo "$envp not set" >&2;
		exit;
	}
}

function status(){
  local node=$1
  local pid=$(get_pid $run_dir $node)
  if [ "$pid" == "" ];then
	  echo "STOP"
  else
	  echo "RUNNING"
  fi
}

function main(){
	local node=${2:-"restful"}
	local cmd=${1:-"help"}

	include
	check_env "JAVA_HOME"
	main_class="org.fire.service.restful.FireService"
	lib_dir=$base/lib
	run_dir=$base/run
	log_dir=$base/log

	case $cmd in
		"start")
			start $node;;
		"stop")
			stop $node;;
		"status")
			status $node;;
		"restart")
			stop $node
			start $node;;
		*)
			help
	esac
}

