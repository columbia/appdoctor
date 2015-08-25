#!/usr/bin/env python

import init
import widget
import interface

#apk_path = "/home/henryhu/betterbatterystats.apk"
#apk_path = "/home/henryhu/KeePassDroid-1.9.10.apk"
apk_path = "/home/henryhu/signed.apk"

init.init_testlib(apk_path)
interface.set_auto_wait(True)
widget.click("button_add_item")
interface.wait_for_idle()
widget.click("button1")
interface.wait_for_idle()
widget.enter("autocomplete_add_item", 'test')
widget.click("button_add_item")
interface.wait_for_idle()
init.finish()
