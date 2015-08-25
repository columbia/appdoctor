#!/bin/sh

for i in `cat storehosts.txt | grep -v ^#`; do
    echo "on $i:"
    ssh $i "rm -r ~/.android/avd/*APPTEMP*; rm -r ~/andchecker/mod_apks/*; rm ~/andchecker/inst_apks/*; rm ~/andchecker/mod_lock/*; rm ~/andchecker/inst_lock/*"
done
