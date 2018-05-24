#!/usr/bin/env bash
base=$(cd $(dirname $0);pwd)

test -f $HOME/.bash_profile && . $HOME/.bash_profile

function get_abs_file(){
	test ! -f $1 && {
		echo $1;
		return;
	}
	local d=$(cd $(dirname $1);pwd)
	echo "$d/$(basename $1)"
}

function curl_cmd(){
	local url=$1
	local method=${2:-"POST"}
	local data=$3
	local _curl_cmd=$(which curl)
	test "x$_curl_cmd" == "x" && {
		echo "curl command not found." >&2;
		return;
	}
	if [ "x$method" == "xUPLOAD" ];then
		test -f "$data" && {
			echo "$_curl_cmd -F f1=@$(get_abs_file $data) $url";
            $_curl_cmd -F f1=@$(get_abs_file $data) $url;
            echo
        }
        return
    fi
	test "x$data" != "x" && data="-d $data"
	local curl_param="-s -X $method -H Content-Type:application/json"
	echo "$_curl_cmd $curl_param $url $data"
	$_curl_cmd $curl_param $url $data
	echo
}

function upload(){
	test "x$1" == "x" && return

	curl_cmd "$http_server/naja/upload" "UPLOAD" "$1"
}

function app_json(){
	test $# -ne 3 && return
	echo '{"packageName":"'$1'","conf":"'$(get_abs_file $2)'","decompression":'$3'}'
}

function once(){
	test $# -ne 3 && return
	local data='{"app":'$(app_json $1 $2 $3)'}'

	curl_cmd "$http_server/app/execute" "POST" "$data"
}

function submit(){
	test $# -ne 3 && return
	local data=$(app_json "$@")

	curl_cmd "$http_server/app/submit" "POST" "$data"
}

function scheduled(){
	test $# -ne 4 && return
	local data='{"app":'$(app_json $1 $2 $3)',"interval":'$4'}'

	curl_cmd "$http_server/app/scheduled" "POST" "$data"
}

#function waitSubmit(){}

function sendHeartbeat(){
	test $# -ne 3 && return
	local app_name=$1
	local app_id=$2
	local period=$3
	let period=$period*3

	curl_cmd "$http_server/app/heartbeat/$app_name/$app_id/$period" "GET"
}

function killApp(){
	test $# -lt 1 && return
	local app_id=$1

	curl_cmd "$http_server/app/kill/$app_id" "GET"
}

function getMonitors(){
	local app_name=${1:-"0x00001"}
	curl_cmd "$http_server/app/monitors/$app_name" "GET"
}

function printHelp(){
	echo  "Usage: $0 -h server_ip:port parameter"
	echo -e "Parameter:"
	echo -e "\tupload file"
	echo -e "\tonce package conf decompress"
	echo -e "\tsubmit package conf decompress"
	echo -e "\tscheduled pakcage conf decompress interval"
	echo -e "\tkill appid"
	echo -e "\tmonitors app_name"
	echo -e "\theartbeat app_name appid period"
}

function _check_(){
	test "x$1" == "x" && {
		printHelp;
		exit;
	}
}

function main(){
	local args=($@)
	local http_server="http://127.0.0.1:8920"
	
	local i=0
	for ((i=0;i<$#;i++))
	do
		case ${args[i]} in
			"-h")
				http_server=${args[i+1]}
				_check_ $http_server
				let i=$i+1;;
			"upload")
				file=${args[i+1]}
				_check_ $file
				upload $file
				exit;;
			"once")
				package=${args[i+1]}
				submit_conf=${args[i+2]}
				decompress=${args[i+3]}
				_check_ $package
				_check_ $submit_conf
				_check_ $decompress
				once $package $submit_conf $decompress
				exit;;
			"submit")
				package=${args[i+1]}
				submit_conf=${args[i+2]}
				decompress=${args[i+3]}
				_check_ $package
				_check_ $submit_conf
				_check_ $decompress
				submit $package $submit_conf $decompress
				exit;;
			"scheduled")
				package=${args[i+1]}
				submit_conf=${args[i+2]}
				decompress=${args[i+3]}
				interval_time=${args[i+4]}
				_check_ $package
				_check_ $submit_conf
				_check_ $decompress
				_check_ $interval_time
				scheduled $package $submit_conf $decompress $interval_time
				exit;;
			"kill")
				app_id=${args[i+1]}
				_check_ $app_id
				killApp $app_id
				exit;;
			"monitors")
				app_name=${args[i+1]}
				getMonitors $app_name
				exit;;
			"heartbeat")
				app_name=${args[i+1]}
				app_id=${args[i+2]}
				period=${args[i+3]}
				_check_ $app_id
				sendHeartbeat $app_name $app_id $period
				exit;;
			*)
				printHelp
				exit;;
		esac
	done
	printHelp
}

main "$@"
