#!/bin/sh

WORKDIR=`cd $(dirname $0); pwd`
SDK_HOME=$HOME/android-sdk-linux
PLATFORM=android-15
ANDROID_JAR=$SDK_HOME/platforms/$PLATFORM/android.jar
ACIDIR=`cd $WORKDIR/../instrument_server; pwd`

rm -r /tmp/dc-classes/ 2>/dev/null
rm -r /tmp/dc-gen/ 2>/dev/null

mkdir /tmp/dc-classes 2>/dev/null
mkdir /tmp/dc-gen 2>/dev/null

[ -d $ACIDIR/bin/classes ] || (echo "Compile ACInstrumentation" 1>&2; cd $ACIDIR; ant release 1>/dev/null)

javac -cp $ACIDIR/bin/classes:$ANDROID_JAR -d /tmp/dc-classes -s /tmp/dc-gen $*
dx --dex --output=classes.apk /tmp/dc-classes

base64 -w 0 classes.apk > classes.apk.base64
echo "Done. result put to classes.apk and classes.apk.base64"
