#!/bin/bash

#  Need to test...
#
#   Trig.:  gPlazma   IP address
# Door:
#
# srm        done      done
# dcap        --       done
# gsidcap    done      done
# webdav      --       done
# webdavs    done      done

set -e

rc=0
voms-proxy-info -exists >/dev/null 2>&1 || rc=1
if [ $rc -eq 1 ]; then
    voms-proxy-init
fi

rc=0
ssh-add -l >/dev/null || rc=1
if [ $rc -eq 1 ]; then
    ssh-add || :
fi

DN=$(voms-proxy-info|awk 'BEGIN{FS=": "}/identity/{print $2}')

base=$(cd $(dirname $0)/packages/system-test/target/dcache; pwd)

dcache=$base/bin/dcache


#  Issue an SRM ls operation using curl
function srmLs() # $1 host, $2 port, $3 abs. path
{
    curl -s https://$1:$2/srm/managerv2 -E /tmp/x509up_u1000 --insecure -H "Content-Type: text/xml; charset=utf-8" -H 'SOAPAction: ""' -X POST --data-binary @- <<EOF >/dev/null
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:SOAP-ENC="http://schemas.xmlsoap.org/soap/encoding/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:srm22="http://srm.lbl.gov/StorageResourceManager">
    <SOAP-ENV:Body SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
        <srm22:srmLs>
            <srmLsRequest>
                <arrayOfSURLs>
                    <urlArray>srm://localhost$3</urlArray>
                </arrayOfSURLs>
                <storageSystemInfo/>
            </srmLsRequest>
        </srm22:srmLs>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
EOF
}

#  Issue an SRM ls operation using curl
function srmPing() # $1 host, $2 port
{
    curl -s https://$1:$2/srm/managerv2 -E /tmp/x509up_u1000 --insecure -H "Content-Type: text/xml; charset=utf-8" -H 'SOAPAction: ""' -X POST --data-binary @- <<EOF >/dev/null
<?xml version="1.0" encoding="UTF-8"?>
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
        xmlns:SOAP-ENC="http://schemas.xmlsoap.org/soap/encoding/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema"
        xmlns:srm22="http://srm.lbl.gov/StorageResourceManager">
    <SOAP-ENV:Body SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
        <srm22:srmPing>
            <srmPingRequest/>
        </srm22:srmPing>
    </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
EOF
}


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
	i_started_dcache=1
    fi

    declaredPorts=$($dcache ports | grep TCP | awk '/TCP *[0-9]* *$/{print $5}')

    msg=0
    while [ "$(notListening "$declaredPorts")" != "" ]; do
	if [ $msg -eq 0 ]; then
	    echo -n "Waiting for dCache to start up: "
	    msg=1
	else
	    echo -n '.'
	fi
	sleep 3
    done
    [ $msg -eq 1 ] && echo || :

    if [ -n "$i_started_dcache" ]; then
	#  SRM initialises some components when the first client
	#  connects.  This can print some crap, which upsets the
	#  tests.  Therefore, we do a simple SRM operation to avoid
	#  this.
	srmPing localhost 8443
    fi
}



function addToGplazmaDiagnose() # $1 principal
{
    TRIGGER="$1 in gPlazma"
    ssh -T -p 22224 admin@localhost <<EOF >/dev/null
cd gPlazma
diagnose add "$1"
..
logoff
EOF
}

function addLocalhostToCell() # $1 cell name
{
    TRIGGER="localhost in $1"
    ssh -T -p 22224 admin@localhost <<EOF >/dev/null
cd $1
diagnose add localhost
..
logoff
EOF
}

function lsGplazmaDiagnose()
{
    ssh -T -p 22224 admin@localhost <<EOF | sed -n '/diagnose ls/,/\.\./p' | grep ':'
cd gPlazma
diagnose ls
..
logoff
EOF
}


function lsCellDiagnose() # $1 cell name
{
    ssh -T -p 22224 admin@localhost <<EOF | sed -n '/diagnose ls/,/\.\./p' | grep '\.[0-9][0-9]*\.'
cd $1
diagnose ls
..
logoff
EOF
}


function assertGplazmaDiagnoseEmpty
{
    count=$(lsGplazmaDiagnose | wc -l)

    if [ $count -ne 0 ]; then
	echo "##"
	echo "## ERROR: gPlazma still has principals:"
	lsGplazmaDiagnose | while read p; do echo "##   $p"; done
	echo "##"
	clearGplazmaDiagnose
    fi
}


function assertCellDiagnoseEmpty # $1 cell name
{
    count=$(lsCellDiagnose "$1" | wc -l)

    if [ $count -ne 0 ]; then
	echo "##"
	echo "## ERROR: cell $1 still has principals:"
	lsCellDiagnose "$1" | while read p; do echo "##   $p"; done
	echo "##"
	clearCellDiagnose "$1"
    fi
}


function clearGplazmaDiagnose()
{
    ssh -T -p 22224 admin@localhost <<EOF >/dev/null
cd gPlazma
diagnose clear
..
logoff
EOF
}

function clearCellDiagnose() # $1 cell name
{
    ssh -T -p 22224 admin@localhost <<EOF >/dev/null
cd $1
diagnose clear
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



##
##  Do 'something' with the dCache via stated protocol.  Ideally this
##  something exercises multiple cells within the dCache instance.
##
function exercise() # $1 protocol
{
    if [ -n "$TRIGGER" ]; then
	label="$TRIGGER"
    else
	label="no triggers"
    fi

    START=$(wc -l $base/var/log/dCacheDomain.log | awk '{print $1}')
    echo "##  exercise $1 ($label)"
    case $1 in
	SRM)	
	    srmLs localhost 8443 /
	    ;;

	SRM-over-SSL)	
	    srmLs localhost 8445 /
	    ;;

	dcap)
	    buildURI dcap
	    dccp  /etc/profile $URI >/dev/null 2>&1 || :

	    # The abortCacheProtocol method waits 10 seconds then logs
	    # something. We need to allow for this so the log files
	    # are reasonable.
	    sleep 10
	    ;;

	gsidcap)
	    buildURI gsidcap
	    dccp  /etc/profile $URI >/dev/null 2>&1 || :

	    # The abortCacheProtocol method waits 10 seconds then logs
	    # something. We need to allow for this so the captured log
	    # files don't misplace this message.
	    sleep 10
	    ;;

	webdav)
	    buildURI http 2880
	    curl -so/dev/null -X PROPFIND $URI
	    ;;

	webdavs)
	    buildURI https 2881
	    curl -so/dev/null -u admin:dickerelch --insecure -X PROPFIND $URI
	    # curl --cert ~/.globus/usercert.pem --key ~/.globus/userkey.pem -so/dev/null --insecure -X PROPFIND $URI
	    ;;

	ftp)
	    curl -so/dev/null -l -u admin:dickerelch ftp://localhost:22126/
	    ;;

	*)
	    echo ERROR: unknown protocol $1
	    ;;
    esac

    # Try to reduce race condition with exit logging
    sleep 0.1

    echo "##"
    echo "#"
    tail -n +$(( $START + 1 )) $base/var/log/dCacheDomain.log 
    echo "#"
    echo "##"

    unset TRIGGER
}



function testProtocol() # $1 protocol, $2 cell name, $3 gPlazma principal (optional)
{
    exercise $1
    addLocalhostToCell $2
    exercise $1
    assertCellDiagnoseEmpty $2
    exercise $1

    if [ -n "$3" ]; then
	addToGplazmaDiagnose "$3"

	exercise $1

	assertGplazmaDiagnoseEmpty

	exercise $1
    fi
}


host=$(uname -n)

ensureDcacheRunning

testProtocol SRM          SRM-$host      "dn:$DN"
testProtocol SRM-over-SSL SRM-$host      "dn:$DN"
testProtocol dcap         DCap-$host
testProtocol gsidcap      DCap-gsi-$host "dn:$DN"
testProtocol webdav       WebDAV-$host
testProtocol webdavs      WebDAV-S-$host "org.dcache.auth.UidPrincipal:0"
testProtocol ftp          FTP-$host      "org.dcache.auth.UidPrincipal:0"

#  TODO
#
# gsiftp
# xrootd
# NFS v3
# NFS v4.1
