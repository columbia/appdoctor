#!/bin/sh

slotnum=2
jobnum=1
batch=3
depth=100

cd andchecker

rm -f ~/andchecker-stop

logname_base=logs/log-`hostname`

if [ "$1" = "" ]; then
    i=0
    while true; do
        if [ ! -e $logname_base-$i ]; then
            logname=$logname_base-$i
            break
        fi
        i=`expr $i + 1`
    done
else
    logname=$logname_base-$1
fi

nohup ./controller.py -n $depth -s $slotnum -b $batch -j $jobnum -l $logname > $logname.txt 2>&1 &
