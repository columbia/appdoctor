import os
import re
import subprocess
import time
import sys
import random
import shutil
# import fcntl
import logging
import string
import threading
import flags
import shlex

import settings
from apptest.exception import *

logger = logging.getLogger("device")

PICTURE_FILE = "media/picture.jpg"
VIDEO_FILE = "media/video.mp4"

class SystemCrashWatcher(threading.Thread):
    def __init__(self, dev):
        threading.Thread.__init__(self)
        self.dev = dev

    def run(self):
        adb_proc = subprocess.Popen(shlex.split("adb -s emulator-%s logcat *:V" % (self.dev.console_port)), stdout = subprocess.PIPE, stderr = subprocess.STDOUT, close_fds=True)
        while True:
            line = adb_proc.stdout.readline()
            if not line:
                # logcat exits?
                # emulator closed itself
                return
            if not self.dev.running():
                return
            line = line.rstrip()
#            logger.debug("got logcat line: %s" % line)
#            elif "Activity Manager Crash" in line:
#                tokill = True
            tokill = False
            if "WATCHDOG KILLING SYSTEM PROCESS" in line:
                tokill = True
            elif "service 'power' died" in line:
                tokill = True
            elif "FATAL EXCEPTION IN SYSTEM PROCESS: main" in line:
                tokill = True

            if tokill:
                logger.info("killing emulator %s since system crashed: %s" % (self.dev.console_port, line))
                self.dev.kill()
                return

class ProcWatcher(threading.Thread):
    def __init__(self, proc_watching, proc_to_kill):
        threading.Thread.__init__(self)
        self.proc_watching = proc_watching
        self.proc_to_kill = proc_to_kill

    def run(self):
        while True:
            self.proc_to_kill.poll()
            if self.proc_to_kill.returncode is not None:
                # target already terminated
                return
            self.proc_watching.poll()
            if self.proc_watching.returncode is not None:
                # watching process terminated
                # kill target
                self.proc_to_kill.kill()
                return
            time.sleep(3)

class TimeoutWatcher(threading.Thread):
    def __init__(self, timeout, proc_to_kill, raise_exc):
        threading.Thread.__init__(self)
        self.timeout = timeout
        self.proc_to_kill = proc_to_kill
        self.raise_exc = raise_exc
        self.timedout = False

    def run(self):
        interval = 0
        while True:
            time.sleep(1)
            interval += 1
            self.proc_to_kill.poll()
            if self.proc_to_kill.returncode is not None:
                # target already terminated
                return
            if interval < self.timeout:
                continue
            # timeout!
            # kill target
            self.proc_to_kill.kill()
            self.timedout = True
            if self.raise_exc:
                raise TimeoutException("adb command timed out")
            break

class Device:
    targets = {'2.2': 8, '2.3': 10, '2.1': 7,
            '2.0': 5, '1.5': 3, '1.6': 4,
            '3.0': 11, '3.1': 12, '3.2': 13,
            '4.0': 15, '4.1': 16, '4.2': 17}

    def __init__(self, version='4.0', sdcard='64M', ram=None, width=None, height=None, density=None, console_mode=True, extra_ident = None, abi = None):
        logger.debug('init, ver = %s' % version)
        self.avd_inst = None
        self.version = version
        self.sdcard = sdcard
        self.ram = ram
        self.width = width
        self.height = height
        self.density = None if density is None else density.split(',')
        self.extra_ident = extra_ident
        self.abi = abi
        self.init_avd_name()
        self.curr_dpi = 0

        self.console_port = None
        self.adb_port = None
        self.console_mode = console_mode
        self.emu = None
        self.recent_logcat = ''
        self.avd_lock = None

    def init_avd_name(self):
        if self.version in self.targets:
            self.avd = 'Android%s' % self.version
        else:
            self.avd = self.version
        if self.sdcard:
            self.avd += '-%s' % self.sdcard
        if self.ram:
            self.avd += '-%s' % self.ram
        if self.width and self.height:
            self.avd += '-%sx%s' % (self.width, self.height)
        if self.density:
            self.avd += '-%sppi' % self.density[0]
        if self.abi:
            self.avd += "-%s" % self.abi

        if self.extra_ident:
            self.avd += "-%s" % self.extra_ident

        formal_avd = ''
        for ch in self.avd:
            if ch in string.letters or ch in string.digits or ch in ['.','_','-']:
                formal_avd += ch
            else:
                formal_avd += '_'
        self.avd = formal_avd

    def get_avd_name(self):
        return self.avd

    def lock_avds(self, avd_name):
        logger.debug("grab avd lock: %s" % avd_name)
        flags.make_dir(os.path.expanduser('~/.android'))
        return flags.grab_lock(os.path.expanduser("~/.android/%s.lock") % avd_name)
#        lockf = os.open(os.path.expanduser('~/.android/avd'), os.O_RDONLY)
#        fcntl.flock(lockf, fcntl.LOCK_EX)
#        self.avd_lock = lockf

    def lock_local_avds(self, avd_name):
        logger.debug("grab local avd lock: %s" % avd_name)
        flags.make_dir(settings.local_avd_lock_dir)
        return flags.grab_lock(os.path.join(settings.local_avd_lock_dir, "%s.lock" % avd_name))

    def unlock_avds(self, avd_name, lockf):
        logger.debug("release avd lock: %s" % avd_name)
        flags.release_lock(os.path.expanduser("~/.android/%s.lock") % avd_name, lockf)
#        fcntl.flock(self.avd_lock, fcntl.LOCK_UN)
#        os.close(self.avd_lock)

    def unlock_local_avds(self, avd_name, lockf):
        logger.debug("release local avd lock: %s" % avd_name)
        flags.release_lock(os.path.join(settings.local_avd_lock_dir, "%s.lock" % avd_name), lockf)

    def exists(self):
        return self.avd_exists(self.avd)

    def avd_exists(self, avd):
        return os.path.exists(os.path.expanduser('~/.android/avd/%s.avd' % avd))

    def template_exists(self):
        return os.path.exists(os.path.join(settings.avd_template_dir, "%s.ini" % self.avd))

    def check_config_template_locked(self):
        logger.info("check for template existance of %s" % self.avd)
        if not self.template_exists():
            logger.info("creating config template")
            self.create()
            self.move_to_template(self.avd)

    def move_to_template(self, avd):
        logger.info("moving avd %s to template storage" % avd)
        template_path_base = os.path.join(settings.avd_template_dir, avd)
        avd_path_base = self.get_avd_base(avd)

        flags.make_dir(settings.avd_template_dir)

        logger.debug("copy from %s -> %s" % (avd_path_base, template_path_base))
        shutil.copytree(avd_path_base + ".avd", template_path_base + ".avd")
        shutil.copy(avd_path_base + ".ini", template_path_base + ".ini")

        logger.debug("remove %s" % avd_path_base)
        shutil.rmtree(avd_path_base + ".avd")
        os.remove(avd_path_base + ".ini")

        logger.info("moved")

    def check_config_template(self, must_lock = False):
        if not self.template_exists() or must_lock:
            lockf = self.lock_avds(self.avd)
            try:
                self.check_config_template_locked()
            finally:
                self.unlock_avds(self.avd, lockf)

    def create_from_config_template(self):
        cur_avd = self.avd
        cur_ident = self.extra_ident
        self.extra_ident = None
        self.init_avd_name()
        template_avd = self.avd
        self.extra_ident = cur_ident
        self.avd = cur_avd

        logger.info("copy from template %s to %s" % (template_avd, cur_avd))
        lock_template = self.lock_avds(template_avd)
        try:
            lock_cur = self.lock_avds(cur_avd)
            try:
                self.create_instance_locked(template_avd, cur_avd)
            finally:
                self.unlock_avds(cur_avd, lock_cur)
        finally:
            self.unlock_avds(template_avd, lock_template)

    def get_avd_base(self, avd):
        return os.path.expanduser('~/.android/avd/%s' % avd)

    def get_template_base(self, avd):
        return os.path.join(settings.avd_template_dir, avd)

    def create_instance_locked(self, from_avd, to_avd, use_symlink = False):
        """create avd instance from template
        0. If template exists in ~/.android/avd (avd_orig_path), clean it
        1. Copy template from template storage (avd_path) to ~/.android/avd (avd_orig_path)
        2. Move this instance (avd_orig_path)  to our target (to_avd_path) use "android move avd"
        """
        logger.info("create avd instance: %s -> %s" % (from_avd, to_avd))

        to_avd_path = self.get_avd_base(to_avd)
        avd_path = self.get_template_base(from_avd)
        avd_orig_path = self.get_avd_base(from_avd)

        logger.debug("copy: %s -> %s" % (avd_path, avd_orig_path))

        if os.path.exists(avd_orig_path + ".avd"):
            shutil.rmtree(avd_orig_path + ".avd")
        if os.path.exists(avd_orig_path + ".ini"):
            os.remove(avd_orig_path + ".ini")

        if use_symlink:
            os.mkdir(avd_orig_path + ".avd")
            for f in os.listdir(avd_path + ".avd"):
                newf = os.path.join(avd_orig_path + ".avd", f)
                oldf = os.path.join(avd_path + ".avd", f)
                if f in ["snapshots.img", "userdata.img"]:
                    os.link(oldf, newf)
                elif f in ["sdcard.img"]:
                    if self.sdcard:
                        subprocess.call('mksdcard %s %s' % (self.sdcard, newf), shell=True)
                else:
                    shutil.copy(oldf, newf)
        else:
            shutil.copytree(avd_path + ".avd", avd_orig_path + ".avd")

        shutil.copy(avd_path + ".ini", avd_orig_path + ".ini")

        logger.debug("move avd")
        # specify -p for symbolic link issues
        subprocess.check_call('android -v move avd -n %s -r %s -p %s' % (from_avd, to_avd, to_avd_path + ".avd"), shell=True, close_fds=True)

        logger.info("instance created")

    def create_new_instance_locked(self):
        logger.debug("select the new instance's name")
        self.avd_inst = self.avd + "-" + str(random.randint(0, 1000000))
        while self.avd_exists(self.avd_inst):
            self.avd_inst = self.avd + "-" + str(random.randint(0, 1000000))

        logger.info("new instance : %s" % self.avd_inst)
        self.create_instance_locked(self.avd, self.avd_inst)

    def get_ini_path(self, avd_name):
        return os.path.expanduser('~/.android/avd/%s.ini' % avd_name)

    def create(self):
        logger.info("creating new avd")
        if self.version in self.targets:
            target = 'android-' + str(self.targets[self.version])
            sdkVer = self.targets[self.version]
        else:
            target = self.version
            sdkVer = -1
        cmd = 'echo | android create avd -n %s -t "%s" -a -f' % (self.avd, target)
        if self.abi:
            cmd += ' --abi %s' % self.abi
        elif sdkVer >= 14:
            cmd += ' --abi armeabi-v7a'
        elif sdkVer > 0:
            cmd += ' --abi armeabi'

        if self.sdcard:
            cmd += ' -c %s' % self.sdcard
        if self.width and self.height:
            cmd += ' -s %sx%s' % (self.width, self.height)
        else:
            cmd += ' -s 480x800'
        logger.debug("create avd: %s" % cmd)
        subprocess.call(cmd, shell=True, close_fds=True)
        avd_new_ini = os.path.expanduser('~/.android/avd/%s.avd/config.ini.new' % self.avd)
        avd_ini = os.path.expanduser('~/.android/avd/%s.avd/config.ini' % self.avd)
        with open(avd_new_ini, 'w') as f:
            for line in open(avd_ini, 'r'):
                if not self.sdcard:
                    if 'hw.sdcard=' in line:
                        continue
                if self.density:
                    if 'hw.lcd.density=' in line:
                        continue
                if 'vm.heapSize' in line:
                    continue
                else:
                    f.write(line)

                if self.ram:
                    if 'hw.ramSize=' in line:
                        continue

            if not self.sdcard:
                f.write('hw.sdcard=no\n')
            if self.density:
                f.write('hw.lcd.density=%s\n' % self.density[0])
            if self.ram:
                f.write('hw.ramSize=%s\n' % self.ram)
            f.write('vm.heapSize=48\n')
            # to hide the soft keyboard
            f.write('hw.keyboard=yes\n')

        os.rename(avd_new_ini, avd_ini)

        logger.debug("start new avd for snapshot")
        self.start(self.avd)
        self.wait_for_ready()
        logger.debug("wait before taking snapshot")
        time.sleep(300)
        self.save_snapshot()
        self.kill()
        self.wait_dev_kill()
        logger.debug("new avd created")

    def start_self(self):
        self.start(self.avd)

    def start(self, avd = None):
        if avd is None:
            self.check_config_template()
            lockf = self.lock_local_avds(self.avd)
            try:
                self.create_new_instance_locked()
            finally:
                self.unlock_local_avds(self.avd, lockf)
            logger.info("starting avd %s" % self.avd_inst)
        else:
            logger.info("starting avd from arg %s" % avd)
        cmd = 'nice emulator -avd %s -debug-init' % (self.avd_inst if avd is None else avd)
        #if self.console_mode:
        #    cmd += " -no-window"
        self.emu = subprocess.Popen(shlex.split(cmd), stdout=subprocess.PIPE, close_fds=True)
        pattern = re.compile(r'emulator: control console listening on port (\d+), ADB on port (\d+)')
        logger.info("waiting for console & emulator port")
        lines = []
        while True:
            line = self.emu.stdout.readline()
            if not line:
                for logline in lines:
                    logger.warn("emulator output: %s" % logline)
                raise Exception("fail to get console port from emulator output")
            line = line.rstrip()
            lines.append(line)
            match = re.match(pattern, line)
            if match:
                (self.console_port, self.adb_port) = match.group(1, 2)
                logger.info("emulator @ %s, adb port %s" % (self.console_port, self.adb_port))
                break
        self.setup_exception_handler()
        self.crash_watcher = SystemCrashWatcher(self)
        self.crash_watcher.start()
        self.curr_dpi = 0

    def kill(self):
        if not self.alive():
            return
        if not self.running():
            logger.warn("alive but not running!")
        logger.info("killing emulator %s" % self.console_port)
        try:
            self.run_cmd_nowait("emu kill")
        except:
            pass
        time.sleep(0.5)
        if self.alive():
            time.sleep(3)
        if not self.alive():
            time.sleep(1)
            self.emu.poll()
            return
        logger.warn("cannot kill emulator %s normally. using SIGKILL." % self.console_port)
        if self.emu:
            self.emu.kill()
            time.sleep(1)
            self.emu.poll()

    def save_snapshot(self):
        logger.info("saving snapshot for emulator %s" % self.console_port)
        conn = subprocess.Popen('nc localhost %s' % self.console_port,
                stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                stderr=subprocess.PIPE, shell=True, close_fds=True)
        conn.communicate("avd snapshot save default-boot\nquit\n")

    def run_cmd_nowait_nofail(self, cmd):
        (retcode, lines) = self.run_cmd_nowait(cmd)
        if retcode != 0 and retcode is not None:
            raise ExternalError("adb cmd return %r != 0: %s" % (retcode, cmd))
        for line in lines:
            if "Success" in line:
                return lines
        raise ExternalError("adb cmd failed: %s" % cmd)

    def run_cmd_nowait(self, cmd):
        proc = subprocess.Popen(shlex.split('adb -s emulator-%s %s' % (self.console_port, cmd)), stderr = subprocess.STDOUT, stdout = subprocess.PIPE, close_fds=True)
        timeout_watcher = TimeoutWatcher(settings.adb_cmd_timeout, proc, True)
        timeout_watcher.start()
        lines = []
        while True:
            line = proc.stdout.readline()
            if not line:
                break
            lines.append(line)
            if 'waiting for device' in line:
                raise ExternalError("adb should not wait for device")
            logger.debug("adb output: %s" % line.rstrip())
        proc.wait()
        if timeout_watcher.timedout:
            raise TimeoutException("adb command timeout")
        return (proc.returncode, lines)

    def check_cmd_nowait(self, cmd):
        ret = self.run_cmd_nowait(cmd)
        if ret[0] != 0:
            raise ExternalError("return code is not 0 but %r: %s" % (ret[0], cmd))
        return ret[1]

    def install(self, apk):
        logger.info('install: %s' % apk)
        self.assert_running()
        self.run_cmd_nowait_nofail("install %s" % apk)

    def uninstall(self, pack):
        logger.info('uninstall: %s' % pack)
        self.assert_running()
        self.run_cmd_nowait("uninstall %s" % pack)

    def execute(self, cmd):
        logger.info('execute: %s' % cmd)
        self.assert_running()
        self.check_cmd_nowait('shell %s' % cmd)

    def forward(self, devport, hostport):
        self.assert_running()
        self.check_cmd_nowait('forward %s %s' % (devport, hostport))

    def wait_for_ready(self):
        logger.info("waiting for pm ready")
        while True:
            adb_proc = subprocess.Popen(shlex.split("adb -s emulator-%s shell pm list packages" % self.console_port), stderr = subprocess.PIPE, stdout = subprocess.PIPE, close_fds=True)
            proc_watcher = ProcWatcher(self.emu, adb_proc)
            proc_watcher.start()
            output = adb_proc.communicate()[0]
            if 'settings' in output:
                return
            self.assert_alive()
            time.sleep(0.1)

    def wait_dev_kill(self):
        logger.info("waiting for device to be killed")
        while True:
            if not self.running():
                return True
            time.sleep(0.1)
        return False

    def wait_dev(self):
        logger.info("waiting for device to be running")
        while True:
            self.assert_alive()
            if self.running():
                return True
            time.sleep(0.1)
        return False

    def get_recent_logcat(self, level = 'I', lines = 100):
        logger.debug("get logcat for emulator %s" % self.console_port)
        if self.running():
            adb_proc = subprocess.Popen(shlex.split("adb -s emulator-%s logcat -d *:%s System.err:W" % (self.console_port, level)), stdout = subprocess.PIPE, stderr = subprocess.PIPE, close_fds=True)
            timeout_watcher = TimeoutWatcher(10, adb_proc, False)
            timeout_watcher.start()
            self.recent_logcat = adb_proc.communicate()[0]
        return self.recent_logcat

    def pull_file(self, src, dst):
        self.run_cmd_nowait("pull %s %s" % (src, dst))

    def push_file(self, src, dst):
        self.run_cmd_nowait("push %s %s" % (src, dst))

    def assert_running(self):
        if not self.running():
            raise InternalError("device not running!")

    def running(self):
        if not self.alive():
            return False
        output = subprocess.check_output('adb devices', shell = True, close_fds=True)
        return'emulator-%s' % self.console_port in output

    def assert_alive(self):
        if not self.alive():
            raise ExternalError("emulator terminated unexceptedly")

    def alive(self):
        # if not ever started
        if self.emu is None:
            return False
        # if already terminated
        if self.emu.returncode is not None:
            return False
        self.emu.poll()
        # if termnated now
        if self.emu.returncode is not None:
            return False
        return True

    def setup_exception_handler(self):
        _old_excepthook = sys.excepthook
        def my_handler(exctype, value, traceback):
            logger.error("recent logcat:")
            for line in self.get_recent_logcat().split('\n'):
                logger.error(line)
            _old_excepthook(exctype, value, traceback)
        sys.excepthook = my_handler

    def cleanup(self, is_template = False):
        if is_template:
            avd = self.avd
        else:
            avd = self.avd_inst

        if avd:
            logger.debug("cleanup for avd %s" % avd)
            self.kill()
            self.wait_dev_kill()
            avd_path = os.path.expanduser('~/.android/avd/%s.avd' % avd)
            avd_ini = os.path.expanduser('~/.android/avd/%s.ini' % avd)
            try:
                shutil.rmtree(avd_path)
                os.remove(avd_ini)
            except:
                pass

    def set_console_mode(self, console_mode):
        self.console_mode = console_mode

    def clear_app_data(self, package):
        try:
            self.run_cmd_nowait_nofail("shell pm clear %s" % package)
            logger.info("app data cleared through pm")
        except ExternalError:
            # in some versions, pm clear is not supported
            self.execute("pm disable %s" % package)
            self.run_cmd_nowait("shell rm -r /data/data/%s/*" % package)
            self.execute("pm enable %s" % package)

    def clear_sdcard(self):
        if self.sdcard:
            mounts = self.check_cmd_nowait("shell mount")
            cleared = 0
            for mount in mounts:
                if "sdcard" in mount and 'vfat' in mount:
                    match = re.match("^[^\\s]+\\s+([^\\s]+)\\s+", mount)
                    if match:
                        self.run_cmd_nowait("shell rm -r %s/*" % match.group(1))
                        cleared += 1

            if not cleared:
                self.run_cmd_nowait("shell rm -r -f /mnt/sdcard/*")

    def change_dpi(self, dpi):
        self.execute("setprop qemu.sf.lcd_density %r" % dpi)

    def next_dpi(self):
#        self.change_dpi(self.density[self.curr_dpi])
        return self.density[(self.curr_dpi + 1) % len(self.density)]

    def iter_dpi(self):
        self.curr_dpi = (self.curr_dpi + 1) % len(self.density)

    def get_curr_dpi(self):
        return self.density[self.curr_dpi]

    def get_num_dpi(self):
        if self.density:
            return len(self.density)
        else:
            return 0

    def prepare_sdcard(self):
        if not self.sdcard:
            return
        logger.debug("prepare sdcard")
        self.run_cmd_nowait("shell mkdir /sdcard/DCIM")
        self.run_cmd_nowait("shell mkdir /sdcard/DCIM/Camera")
        self.run_cmd_nowait("shell mkdir /sdcard/Movies")
        self.push_file(PICTURE_FILE, "/sdcard/DCIM/Camera/")
        self.push_file(VIDEO_FILE, "/sdcard/Movies/")
        self.run_cmd_nowait('shell "am broadcast -a android.intent.action.MEDIA_MOUNTED -d file:///mnt/sdcard/"')
