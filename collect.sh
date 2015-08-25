num=$1

for f in `ls log-*-$num/err_*.log`; do
	echo -n $f >> packinfo-$num.txt
	head $f | grep package | cut -d : -f 2-2 >> packinfo-$num.txt
done

