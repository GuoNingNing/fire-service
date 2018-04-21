#!/usr/bin/env bash
base=$(cd $(dirname $0);pwd)
. $base/node.sh


function help(){
	echo "Usage: $0 (start|restart|stop|status)" >&2
}


main "$@"
