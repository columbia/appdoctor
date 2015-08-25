#!/bin/sh

for i in `cat hosts.txt | grep -v ^#`; do
    echo "on $i:"
    ssh $i "rm -r ~/.android/avd/*.ini; rm -r ~/.android/avd/*.avd; rm ~/.android/*.lock; rm -r /tmp/andchecker*; rm ~/andchecker/inst_apks/*; rm ~/andchecker/mod_apks/*; rm ~/andchecker/local_avd_lock/*.lock; rm ~/andchecker/mod_lock/*; rm ~/andchecker/inst_lock/*;"
done
