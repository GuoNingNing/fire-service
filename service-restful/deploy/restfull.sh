#!/usr/bin/env bash
bin_dir=$(cd $(dirname $0);pwd)
base=$(cd $bin_dir/..;pwd)
. $bin_dir/node.sh


function help(){
	echo "Usage: $0 (start|restart|stop|status)" >&2
}


main "$@"
