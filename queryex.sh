#!/bin/sh

round=$1
output=""
for line in `grep -E "(report id: |app package:|$2)" log-*-$round/err_*.log | grep "$2" -B 2 | grep -i "$3" -B 1 | grep report\ id | sed -e 's/log-\([a-z0-9]\+\)-\([0-9]\+\)\/\(err_[0-9]\+\)\.log:\s*report id:\s\+\([0-9]\+\)/\1:\2:\3:\4/'`; do
#	echo $line
	cmd_file=`echo $line | sed -e 's/\(.*\):\(.*\):\(.*\):\(.*\)/log-\1-\2\/\3\/\4_cmd_log/'`
#	echo $cmd_file
	if [ ! -e $cmd_file ]; then
		continue
	fi
	cmdcnt=`grep -cE '^[0-9A-Za-z]+ Crawl' $cmd_file`
	cmd=`echo $line | sed -e 's/\(.*\):\(.*\):\(.*\):\(.*\)/analyze.py replay -s \1 -n \2 \3 -i \4 -r 10 -o/'`
#	echo "$cmdcnt  :  $cmd"
	result=`echo "$cmdcnt $cmd_file  :  $cmd"`
	if [ "$output" = "" ]; then
		output=$result
	else
		output=`echo $output\NEWLINE$result`
	fi
done
echo $output | sed -e 's/NEWLINE/\n/g' | sort -h | awk '{s+=$1;print} END {print s/NR}'
#grep -E "(report id: |app package:|$1)" log-*-26/err_*.log | grep "$1" -B 2 | grep "$2" -B 1 | grep report\ id | sed -e 's/log-[a-z]\+//'
