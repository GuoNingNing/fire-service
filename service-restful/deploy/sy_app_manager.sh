#!/usr/bin/env bash
base=$(cd $(dirname $0);pwd)

test -f $HOME/.bash_profile && . $HOME/.bash_profile

function get_abs_file(){
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
	test "x$data" != "x" && data="-d $data"
	local curl_param="-s -X $method -H Content-Type:application/json"
	echo "$_curl_cmd $curl_param $url $data"
	$_curl_cmd $curl_param $url $data
	echo
}

function submit(){
	test "x$1" == "x" && return
	local data='{"conf":"'$(get_abs_file $1)'"}'

	curl_cmd "$http_server/submit" "POST" "$data"
}

function scheduled(){
	test $# -ne 2 && return
	local data='{"conf":"'$(get_abs_file $1)'","interval":"'$2'"}'

	curl_cmd "$http_server/scheduled" "POST" "$data"
}

#function waitSubmit(){}

function sendHeartbeat(){
	test $# -ne 3 && return
	local app_name=$1
	local app_id=$2
	local period=$3
	let period=$period*3

	curl_cmd "$http_server/heartbeat/$app_name/$app_id/$period" "GET"
}

function killApp(){
	test $# -lt 1 && return
	local app_id=$1

	curl_cmd "$http_server/kill/$app_id" "GET"
}

function getMonitors(){
	curl_cmd "$http_server/monitors" "GET"
}

function printHelp(){
	echo  "Usage: $0 -h server_ip:port parameter"
	echo -e "Parameter:\n\tsubmit conf"
	echo -e "\tscheduled conf interval"
	echo -e "\tkill appid"
	echo -e "\tmonitor"
	echo -e "\theartbeat appid period"
}

function _check_(){
	test "x$1" == "x" && {
		printHelp;
		exit;
	}
}

function main(){
	local args=($@)
	local http_server=${1:-"127.0.0.1:8920"}
	
	local i=0
	for ((i=0;i<$#;i++))
	do
		case ${args[i]} in
			"-h")
				http_server=${args[i+1]}
				_check_ $http_server
				let i=$i+1;;
			"submit")
				submit_conf=${args[i+1]}
				_check_ $submit_conf
				submit $submit_conf
				exit;;
			"scheduled")
				submit_conf=${args[i+1]}
				interval_time=${args[i+2]}
				_check_ $submit_conf
				_check_ $interval_time
				scheduled $submit_conf $interval_time
				exit;;
			"kill")
				app_id=${args[i+1]}
				_check_ $app_id
				killApp $app_id
				exit;;
			"monitors")
				getMonitors
				exit;;
			"heartbeat")
				app_id=${args[i+1]}
				period=${args[i+2]}
				_check_ $app_id
				sendHeartbeat $app_id $period
				exit;;
			*)
				printHelp
				exit;;
		esac
	done
	printHelp
}

main "$@"
