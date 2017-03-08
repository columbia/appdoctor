#!/bin/sh

if [ $# != 2 -a $# != 1 ]; then
    echo "Insert instrumentation package and activity"
    echo "Usage: $0 <package> <activity> or $0 <application>"
    exit 1
fi

if [ $# -eq 1 ]; then
    if [ "$1"="keepass" ]; then
        activity=com.android.keepass.KeePass
        package=com.android.keepass
    else
        echo "Unknown application!"
        exit 1
    fi
else
    package=$1
    activity=$2
fi

sed -i -e "s/<string name=\"instrumentation_target_activity\">.*<\/string>/<string name=\"instrumentation_target_activity\">$activity<\/string>/" res/values/instrumentation.xml
rm -f res/values/instrumentation.xml-e
if [ $? -ne 0 ]; then
    exit $?
fi
sed -i -e "s/android:targetPackage=.*/android:targetPackage=\"$package\"/" AndroidManifest.xml
rm -f AndroidManifest.xml-e
if [ $? -ne 0 ]; then
    exit $?
fi

android update project -p . -n andchecker -t android-17
if [ $? -ne 0 ]; then
    exit $?
fi
