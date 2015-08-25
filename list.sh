#!/bin/sh

if [ "$1" = "" ]; then
    hosts=`cat hosts.txt | grep -v ^#`
else
    hosts=$1
fi

for i in $hosts; do
    echo "on $i:"
    ssh $i /bin/sh -c '
        echo controller:;
        ps aux | grep controller.py | grep -v grep;
        echo emulator:;
        ps aux | grep emulator64 | grep -v grep;
        echo reproduce:;
        ps aux | grep reproduce | grep -v grep;
        echo AVDs: ;
        ls -d ~/.android/avd/*.avd | wc -l;
        echo APPTEMPs: ;
        ls -d ~/.android/avd/*APPTEMP*.avd | wc -l;
        echo avd templates: ;
        ls -d ~/andchecker/avd_template/* | wc -l;
        echo avd/mod/inst locks:;
        ls -d ~/.android/*.lock | wc -l;
        ls -d ~/andchecker/mod_lock/*.lock | wc -l;
        ls -d ~/andchecker/inst_lock/*.lock | wc -l;
        echo avd/mod cache: ;
        ls -d ~/andchecker/inst_apks/* | wc -l
        ls -d ~/andchecker/mod_apks/* | wc -l' 2>/dev/null
done
