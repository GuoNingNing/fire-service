#!/usr/bin/env bash
. /etc/profile
base=$(cd $(dirname $0);pwd)

flume_path=""

_get_pid() {
    test -f $1 && cat $1
}

_start() {
    local path=$1
    local conf="$path/conf"
    local proper="$path/conf/flume-conf.properties"
    local pid_file="$path/PID"
	local pid=$(_get_pid $pid_file)
	if test "x$pid" != "x" && kill -0 $pid >/dev/null 2>&1;then
		echo "flume-ng $(basename $path) already start. pid is $pid.";
		exit 1;
	fi
	$flume_path/flume/bin/flume-ng agent -c $conf -f $proper -n agent >/dev/null 2>&1 &
	echo $! > $pid_file
	echo "flume-ng $(basename $path) start"
}

_stop() {
    local path=$1
    local pid_file="$path/PID"
	local pid=$(_get_pid $pid_file)
	test "x$pid" == "x" && {
		echo "flume-ng $(basename $path) already stop.";
		exit 1;
	}
	kill -9 $pid >/dev/null 2>&1
	rm -rf $pid_file
	echo "flume-ng $(basename $path) stop.";
}

_status() {
	local path=$1
	local pid_file="$path/PID"
	local pid=$(_get_pid $pid_file)
	if "x$pid" != "x" && kill -0 $pid >/dev/null 2>&1;then
		echo $pid
		exit 0
	else
		echo -1
		exit 1
	fi
}

_main() {
    local path=$1
	local cmd=$2

	case $cmd in
		"start")
			_start $path;;
		"stop")
			_stop $path;;
		"restart")
			_stop $path
			_start $path;;
		"status")
			_status $path;;
		*)
			echo "Usage:bash $base/$(basename $0) instance start|stop|restart|status"
			exit 2;;
	esac
}

_main "$@"
