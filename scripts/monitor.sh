#!/bin/sh

ssh_connection_string=$1
jmx_port=${2:-31337}
pemfile=${3:-"$HOME/certs/birdman-deploy.pem"}

if [ -z "$1" ]; then
    echo "Usage:  `basename $0` <user@remotehost.com> [JMX_PORT] [PEM_FILE]"
    exit 1
fi

# shut down subshell processes before exiting
# `kill 0` sends the signal to all processes in the current process group
# i.e. any subprocess we create in this script
trap "kill 0" SIGHUP SIGINT SIGTERM

# tunnel the jconsole port over ssh
ssh -D$jmx_port -i $pemfile $ssh_connection_string 'while true; do sleep 1; done' &

jconsole -J-DsocksProxyHost=localhost -J-DsocksProxyPort=$jmx_port service:jmx:rmi:///jndi/rmi://localhost:$jmx_port/jmxrmi

# kill ssh tunnel
kill 0
