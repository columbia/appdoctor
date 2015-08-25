#!/bin/sh

if [ "$1" = "" ]; then
    echo "please enter round number"
    exit 1
fi

./cleanapp.sh

for i in `cat hosts.txt | grep -v ^#`; do
    echo "on $i:"
    ssh $i andchecker/check.sh $1
done
