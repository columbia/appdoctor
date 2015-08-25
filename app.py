#!/usr/bin/env python
import os
import logging
import apk
logger = logging.getLogger("app")

class App:
    def __init__(self, apk_path, name = 'app', version = '1.0', desc = 'application'):
        self.apk_path = apk_path
        self.name = name
        self.version = version
        self.desc = desc

    def get_apk_path(self):
        return self.apk_path

    def get_ident(self):
        basename = os.path.basename(self.get_apk_path())
        pair = os.path.splitext(basename)
        if pair[0]:
            return pair[0]
        else:
            return pair[1]

    def obtain_config(self, conf):
        logger.debug("app's ident: %s" % self.get_ident())
        return conf.obtain_app_specific_config(self.get_ident())

    def get_used_libs(self):
        myapk = apk.APK(self.get_apk_path())
        return myapk.get_used_libs()

    def has_libs(self, conf):
        for lib in self.get_used_libs():
            if not lib in conf.get_libs():
                return False
        return True
