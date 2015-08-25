#!/bin/sh

run=$1

for host in `cat hosts.txt | grep -v ^#`; do
    echo "Collecting from $host"
    ssh $host "cd andchecker/logs; tar -zcvf log-$host-$run.tar.gz log-*-$run log-*-$run.txt"
    scp $host:andchecker/logs/log-$host-$run.tar.gz remote_logs/
    pushd remote_logs
    tar -xvf log-$host-$run.tar.gz
    popd
done
