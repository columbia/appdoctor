#!/usr/bin/env python

import apk
import instrumenter
import os
import settings
import logging
import shutil
import flags
import libinfo
import apptest

mod_apks_dir = "mod_apks"
mod_apks_lock_dir = "mod_lock"
logger = logging.getLogger("launch")

class Launch:
    def __init__(self, dev, app_apk_path, app_package = None, app_start_activity = None, device_conf = None, msg_proxy_mode = None, inst_args = None):
        self.dev = dev
        self.app_apk_path = app_apk_path
        self.app_package = app_package
        self.app_start_activity = app_start_activity
        self.app_apk = apk.APK(self.app_apk_path)
        self.device_conf = device_conf
        self.msg_proxy_mode = msg_proxy_mode
        self.inst_args = inst_args

        if not self.app_package:
            self.app_package = self.app_apk.get_package()

        if not self.app_start_activity:
            self.app_start_activity = self.app_apk.get_default_activity_full(self.app_package)

        self.fullname = os.path.splitext(os.path.basename(app_apk_path))[0]
        self.inst = instrumenter.Instrumenter(self.app_package, self.app_start_activity)
        libinfo.load_lib_info()
        self.skip_install = False

    def make_repo(self):
        if not os.path.exists(mod_apks_dir):
            try:
                os.makedirs(mod_apks_dir)
                logger.info("mod apk repo created at %s" % mod_apks_dir)
            except:
                pass
        if not os.path.exists(mod_apks_lock_dir):
            try:
                os.makedirs(mod_apks_lock_dir)
            except:
                pass

    def lock_repo(self):
        logger.debug("grab mod repo lock: %s" % self.fullname)
        self.make_repo()
        return flags.grab_lock("%s/%s.lock" % (mod_apks_lock_dir, self.fullname))

    def unlock_repo(self, lockf):
        logger.debug("release mod repo lock: %s" % self.fullname)
        flags.release_lock("%s/%s.lock" % (mod_apks_lock_dir, self.fullname), lockf)

    def in_repo(self):
        return os.path.exists(self.get_mod_path())

    def get_mod_path(self):
        return "%s/%s-mod.apk" % (mod_apks_dir, self.fullname)

    def prepare_apks(self):
        lockf = self.lock_repo()
        try:
            if not self.in_repo():
#                self.app_apk.add_permission(["android.permission.INTERNET", "android.permission.READ_LOGS"])
                self.app_apk.add_permission(["android.permission.INTERNET"])
                shutil.copy(self.app_apk.get_path(), self.get_mod_path())
                self.app_apk.cleanup()

                self.app_apk = apk.APK(self.get_mod_path())
                self.app_apk.resign(settings.keystore, settings.keystore_pass, settings.keystore_alias)
            else:
                self.app_apk = apk.APK(self.get_mod_path())

        finally:
            self.unlock_repo(lockf)

        self.inst.make_apk()

    def install_apks(self):
        self.prepare_apks()
        self.dev.wait_for_ready()

        if self.app_package == "com.google.android.apps.maps":
            self.dev.execute("mount -o remount,rw /system")
            self.dev.push_file(self.app_apk.get_path(), "/system/app/Maps.apk")
            self.dev.push_file(self.app_apk.get_path(), "/data/app/maps.apk")
        else:
            self.dev.uninstall(self.app_package)
            self.dev.install(self.app_apk.get_path())

        self.inst.install_on(self.dev)

    def prepare_app_template(self):
        self.dev.set_console_mode(True)
        self.dev.start_self()
        try:
            try:
                self.install_apks()
                self.dev.save_snapshot()
            except Exception as e:
                self.dev.cleanup(True)
                raise e
            finally:
                self.dev.kill()
                self.dev.wait_dev_kill()
        finally:
            self.cleanup()

    def prepare_to_run(self, args, skip_start):
        self.dev.set_console_mode(args.console_mode)
        self.dev.start()
        self.skip_install = args.skip_install
        if not self.skip_install:
            self.install_apks()
        else:
            self.dev.wait_for_ready()

        if skip_start:
            return None

        self.restart()

        return self.inst.get_config_path()

    def cleanup(self):
        self.inst.cleanup()

    def get_forwarded_port(self):
        return self.inst.get_forwarded_port()

    def clear_app_data(self):
        logger.info("clear app data for %s" % self.app_package)
        self.dev.clear_app_data(self.app_package)
        self.dev.clear_sdcard()

    def restart(self):
        self.inst.start(self.dev, self.msg_proxy_mode, self.inst_args)
        self.dev.prepare_sdcard()
        apptest.init.load_config(self.inst.get_config_path())

    def get_total_activities(self):
        return self.app_apk.get_total_activities()

    def restart_dev(self):
        self.dev.cleanup()
        self.dev.start()
        if not self.skip_install:
            self.install_apks()
        else:
            self.dev.wait_for_ready()

        self.inst.restart(self.dev)

        return self.inst.get_config_path()

    def dev_running(self):
        return self.dev.running()

    def test_all_config(self):
        num_dpi = self.dev.get_num_dpi()
        if num_dpi > 1:
            if apptest.interface.should_change_dpi():
                init_dpi = self.dev.get_curr_dpi()
                while self.dev.next_dpi() != init_dpi:
                    curr_dpi = self.dev.next_dpi()
                    self.dev.iter_dpi()
                    apptest.interface.change_dpi(curr_dpi)

    def push_file(self, src, dst):
        self.dev.push_file(src, dst)

    def notify_external_events(self):
        intent_filters = self.app_apk.get_intent_filters()
        for flt in intent_filters:
            assert 'action' in flt
            # omit non-android intents
            # because it's hard to guess their arguments
            if flt['action'].startswith('android'):
                if 'activity' in flt:
                    apptest.interface.notify_intent(flt)
                elif 'receiver' in flt:
                    apptest.interface.notify_broadcast(flt)
                else:
                    assert False, 'unknown intentn filter returned'

    def run_cmd(self, cmd):
        return self.dev.run_cmd_nowait("shell %s" % cmd)

