#!/bin/sh

APKTOOL=apktool1.5.0
APKTOOL_PACK=$APKTOOL.tar.bz2

wget "http://android-apktool.googlecode.com/files/$APKTOOL_PACK"
tar -xvf $APKTOOL_PACK
mv $APKTOOL/apktool.jar .
rm -f $APKTOOL_PACK
rm -fr $APKTOOL
