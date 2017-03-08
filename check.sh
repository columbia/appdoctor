#!/bin/sh

slotnum=12
jobnum=600
batch=100
depth=100

cd andchecker
git pull
if [ $? != 0 ]; then
    echo "git merge failed. manual fix."
    exit 1
fi

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
