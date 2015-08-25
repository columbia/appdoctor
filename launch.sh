#!/bin/sh
count=$1

log_dir=log/

for i in `seq $count`; do
    ./rep.sh $i -1 -c > $log_dir/output_$i.log 2>&1 &
done
