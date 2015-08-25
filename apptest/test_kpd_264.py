#!/usr/bin/env python

import init
import widget
import interface

#apk_path = "/home/henryhu/betterbatterystats.apk"
#apk_path = "/home/henryhu/KeePassDroid-1.9.10.apk"
apk_path = "/home/henryhu/KeePass-1.9.6.apk"

init.init_testlib(apk_path)
interface.set_auto_wait(True)
widget.click("browse_button")
init.finish()
