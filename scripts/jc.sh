#!/bin/sh


host=$1
proxy_port=${2:-31337}

# tunnel the jconsole port over ssh
ssh -f -D$proxy_port -i ~/certs/birdman-deploy.pem $host 'while true; do sleep 1; done'

jconsole -J-DsocksProxyHost=localhost -J-DsocksProxyPort=$proxy_port service:jmx:rmi:///jndi/rmi://localhost:$proxy_port/jmxrmi

pkill "ssh -f -D$proxy_port"
