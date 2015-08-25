#!/bin/sh

if [ -z "$ANDCHECKER_KEYSTORE" ]; then
    echo "Error! Please set ANDCHECKER_KEYSTORE!"
    exit 1
fi

if [ -z "$ANDCHECKER_ALIAS" ]; then
    echo "Error! Please set ANDCHECKER_ALIAS!"
    exit 1
fi

ndk-build

ant release

package_file=bin/andchecker-release-unsigned.apk
target_file=bin/andchecker-release.apk
if [ ! -e $package_file ]; then
    echo "Error! Can't find output file!"
    exit 1
fi

cp $package_file $target_file

jarsigner -keystore $ANDCHECKER_KEYSTORE $target_file $ANDCHECKER_ALIAS
