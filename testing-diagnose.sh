#!/bin/bash

#  Need to test...
#
#   Trig.:  gPlazma   IP address
# Door:
#
# SRM
# xrootd
# FTP
# gsiftp
# WebDAV
# dcap
# gsidcap
# NFS v3
# NFS v4.1

set -e

rc=0
voms-proxy-info >/dev/null 2>&1 || rc=1
if [ $rc -eq 1 ]; then
    voms-proxy-init
fi

DN=$(voms-proxy-info|awk 'BEGIN{FS=": "}/identity/{print $2}')

base=$(cd $(dirname $0)/packages/system-test/target/dcache; pwd)

dcache=$base/bin/dcache



function notListening() # $1 list of ports that should be open
{
    declare -a ports
    ports=($1)

    openPorts=$(netstat --tcp -nl | awk '{print $4}'|grep ':[0-9]*$' | sed -n 's/.*:\([0-9]*\)$/\1/p')

    for openPort in $openPorts; do
	i=0
	while [ $i -lt ${#ports[*]} ]; do
	    if [ "$openPort" = "${ports[$i]}" ]; then
		ports[$i]=""
		break
	    fi
	    i=$(( $i + 1 ))
	done
    done

    i=0
    while [ $i -lt ${#ports[*]} ]; do
	if [ "${ports[$i]}" != "" ]; then
	    echo -n "${ports[$i]} "
	fi
	i=$(( $i + 1 ))
    done
}

function ensureDcacheRunning()
{
    rc=0
    $dcache status >/dev/null || rc=1

    if [ $rc -eq 1 ]; then
	$dcache start
    fi

    declaredPorts=$($dcache ports | grep TCP | awk '/TCP *[0-9]* *$/{print $5}')

    msg=0
    while [ "$(notListening "$declaredPorts")" != "" ]; do
	if [ $msg -eq 0 ]; then
	    echo -n Waiting for dCache to start up
	    msg=1
	else
	    echo -n '.'
	fi
	sleep 3
    done
    [ $msg -eq 1 ] && echo || :
}



function addToGplazmaDiagnose() # $1 principal
{
    ssh -T -p 22224 admin@localhost <<EOF >/dev/null
cd gPlazma
diagnose add "$1"
..
logoff
EOF
}

function lsGplazmaDiagnose()
{
    ssh -T -p 22224 admin@localhost <<EOF
cd gPlazma
diagnose ls
..
logoff
EOF
}

function exerciseSrm()
{
    echo "#"
    echo "#  exerciseSrm"
    cd /home/paul/Hg/lua-srm/
    lua exercise-srm.lua
    cd - >/dev/null
    echo "#"
    echo "#"
}


function preLog()
{
    START=$(wc -l $base/var/log/dCacheDomain.log | awk '{print $1}')
}

function postLog()
{
    tail -n +$START $base/var/log/dCacheDomain.log 
    echo "#"
    echo "#"
}

ensureDcacheRunning


preLog
exerciseSrm
postLog

addToGplazmaDiagnose "dn:$DN"

preLog
exerciseSrm
postLog

preLog
exerciseSrm
postLog
