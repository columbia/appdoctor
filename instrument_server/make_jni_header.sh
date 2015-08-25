#!/bin/bash

ant release
pushd bin/classes
javah -jni com.andchecker.NativeHelper
mv com_andchecker_NativeHelper.h ../../jni/
popd
