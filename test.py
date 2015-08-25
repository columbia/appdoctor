#!/usr/bin/env python

import device
import apptest
import launch

app_apk_path = 'ShoppingList-1.4.1.apk'
app_package = 'org.openintents.shopping'
app_start_activity = 'org.openintents.shopping.ShoppingActivity'
dev = device.Device(version = '4.0')

launcher = launch.Launch()
launcher.prepare_to_run(dev, app_apk_path, app_package, app_start_activity)
