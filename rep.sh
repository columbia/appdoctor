#!/bin/sh

myslot=$1
run_count=$2
log_dir=$3
job_id=$4
shift
shift
shift
shift

log_prefix=err
log_postfix=.log
slotlog=$log_dir/slotlog_$myslot.log
base_path=`cd $(dirname $0); pwd`

number=0

if [ ! -e $log_dir ]; then
    mkdir -p $log_dir
fi

echo "slot log: $myslot started" >> $slotlog
echo "job id: $job_id" >> $slotlog
echo "run count: $run_count" >> $slotlog
echo -n "current time:" >> $slotlog
date >> $slotlog

while [ ! -e ~/andchecker-stop -a \( $number -lt $run_count -o $run_count -eq -1 \) ]; do
    if [ $run_count -eq 1 ]; then
        errlog=$log_dir/err_$job_id
        outfile=$log_dir/output_$job_id.log
        errfile=$log_dir/errout_$job_id.log
        echo "starting..." >> $slotlog
    else
        errlog=$log_dir/err_$job_id\_$number
        outfile=$log_dir/output_$job_id\_$number.log
        errfile=$log_dir/errout_$job_id\_$number.log
        ln -f -s $errfile $log_dir/errout_$myslot\_last.log
        ln -f -s $outfile $log_dir/output_$myslot\_last.log
        echo "starting run $number" >> $slotlog
    fi

    $base_path/reproduce.py -e $errlog $* crawl > $outfile  2> $errfile
    ret=$?
    if [ $ret -ne 0 -o -e $errlog.log ]; then
        echo "error detected at run $number" >> $slotlog
    else
        echo "run $number finished without error" >> $slotlog
#        cat $outfile >> $log_dir/slotout_$myslot.log
#        cat $errfile >> $log_dir/sloterr_$myslot.log
#        rm $outfile
#        rm $errfile
    fi
    if [ $run_count -ne 1 ]; then
        rm -f $log_dir/errout_$myslot\_last.log
        rm -f $log_dir/output_$myslot\_last.log
    fi
    number=`expr $number + 1`
done

echo "slot log: $myslot stopped" >> $slotlog
echo -n "current time:" >> $slotlog
date >> $slotlog
