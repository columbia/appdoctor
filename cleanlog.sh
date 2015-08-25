#!/bin/sh

hostname_cmd='`hostname`'

for i in `cat storehosts.txt | grep -v ^#`; do
    echo "on $i:"
    ssh $i "rm -r ~/andchecker/logs/log-$hostname_cmd-$1; rm ~/andchecker/logs/log-$hostname_cmd-$1.txt"
done
