#!/bin/sh

package_file=bin/andchecker-release.apk
adb -e uninstall com.andchecker
adb -e install -r $package_file
