#!/bin/sh

if [ "$1" = "" ]; then
    hosts=`cat hosts.txt | grep -v ^#`
else
    hosts=$1
fi

for i in $hosts; do
    echo "on $i:"
    ssh $i "touch ~/andchecker-stop; ps aux | grep emulator64-arm | grep -v grep;
    pkill -9 emulator64-arm;
    pkill adb;"
#    pkill -f reproduce.py;
#    pkill -f controller.py"
done
