#!/bin/bash

#  Need to test...
#
#   Trig.:  gPlazma   IP address
# Door:
#
# SRM        done      done
# dcap        --       done
# gsidcap    done      done
# WebDAV
# xrootd
# FTP
# gsiftp
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

function addLocalhostToCell() # $1 cell name
{
    ssh -T -p 22224 admin@localhost <<EOF >/dev/null
cd $1
diagnose add localhost
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


function buildURI() # $1 schema-type, $2 port number
{
    if [ "$COUNTER" = "" ]; then
	COUNTER=0
    else
	COUNTER=$(( $COUNTER + 1 ))
    fi

    if [ -n "$2" ]; then
	port=":$2"
    fi

    URI=$1://localhost${port}/public/$$-$COUNTER
}



function exercise() # $1 protocol, $2 label
{
    START=$(wc -l $base/var/log/dCacheDomain.log | awk '{print $1}')
    echo "##  exercise $1 ($2)"
    case $1 in
	SRM)
	    cd /home/paul/Hg/lua-srm/
	    lua exercise-srm.lua
	    cd - >/dev/null
	    ;;

	dcap)
	    buildURI dcap
	    dccp  /etc/profile $URI >/dev/null 2>&1 || :
	    ;;

	gsidcap)
	    buildURI gsidcap
	    dccp  /etc/profile $URI >/dev/null 2>&1 || :
	    ;;

	WebDAV)
	    buildURI http 2880
	    curl -X PROPFIND $URI
	    ;;
    esac
    echo "##"
    echo "#"
    tail -n +$(( $START + 1 )) $base/var/log/dCacheDomain.log 
    echo "#"
    echo "##"
}

host=$(uname -n)

ensureDcacheRunning

exercise SRM "no triggers"

addToGplazmaDiagnose "dn:$DN"

exercise SRM "DN in gPlazma"
exercise SRM "no triggers"

addLocalhostToCell SRM-$host

exercise SRM "locahost in SRM"
exercise SRM "no triggers"

exercise dcap "no triggers"

addLocalhostToCell DCap-$host

exercise dcap "localhost in dcap door"
exercise dcap "no triggers"

exercise gsidcap "no triggers"

addToGplazmaDiagnose "dn:$DN"

exercise gsidcap "DN in gPlazma"
exercise gsidcap "no triggers"

addLocalhostToCell DCap-gsi-$host

exercise gsidcap "localhost in dcap door"
exercise gsidcap "no triggers"

exercise WebDAV "no triggers"
