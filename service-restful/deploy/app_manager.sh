#!/usr/bin/env bash
base=$(cd $(dirname $0);pwd)

test -f $HOME/.bash_profile && . $HOME/.bash_profile

function get_abs_file(){
	local d=$(cd $(dirname $1);pwd)
	echo "$d/$(basename $1)"
}

function getCurl(){
	local method=${1:-"POST"}
	local curl_cmd="curl -s -X $method -H 'Content-Type: application/json'"
	echo -n "$curl_cmd"
}

function submit(){
	local data='{"command":"run.sh","args":["'$(get_abs_file $1)'"]}'
	local curl_cmd=$(getCurl "POST")

	$curl_cmd "$http_server/submit" -d "$data"
}

#function waitSubmit(){}

function sendHeartbeat(){
	local curl_cmd=$(getCurl "GET")
	test $# -ne 2 && return
	local app_id=$1
	local period=$2
	let period=$period*3

	$curl_cmd "$http_server/heartbeat/$app_id/$period"
}

function killApp(){
	local curl_cmd=$(getCurl "GET")
	test $# -lt 1 && return
	local app_id=$1

	$curl_cmd "$http_server/kill/$app_id"
}

function getMonitors(){
	local curl_cmd=$(getCurl "GET")
	$curl_cmd "$http_server/monitors"
}

function printHelp(){
	echo  "Usage: $0 -h server_ip:port parameter"
	echo -e "Parameter:\n\tsubmit conf"
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
