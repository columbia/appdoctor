import subprocess
import tempfile
import shutil
import random
import logging
import os
import flags
import apk
import settings
import ConfigParser
import apptest
import parser

logger = logging.getLogger("inst")
template_path = "instrument_server"
inst_apks_dir = "inst_apks"
inst_apks_lock_dir = "inst_lock"

class Instrumenter:
    def __init__(self, package, activity):
        self.package = package
        self.activity = activity
        self.temppath = None
        self.fullname = "%s-%s" % (package, activity)
        self.force_build = bool(os.getenv("ANDCHECKER_FORCE_BUILD"))

    def make_repo(self):
        if not os.path.exists(inst_apks_dir):
            try:
                os.makedirs(inst_apks_dir)
                logger.info("instrumenter apk repo created at %s" % inst_apks_dir)
            except:
                pass
        if not os.path.exists(inst_apks_lock_dir):
            try:
                os.makedirs(inst_apks_lock_dir)
            except:
                pass

    def lock_repo(self):
        logger.debug("grab inst repo lock: %s" % self.fullname)
        self.make_repo()
        return flags.grab_lock("%s/%s.lock" % (inst_apks_lock_dir, self.fullname))

    def unlock_repo(self, lockf):
        logger.debug("release inst repo lock: %s" % self.fullname)
        flags.release_lock("%s/%s.lock" % (inst_apks_lock_dir, self.fullname), lockf)

    def in_repo(self):
        return os.path.exists(self.get_apk_path())

    def make_apk(self):
        lockf = self.lock_repo()
        try:
            if self.force_build or not self.in_repo():
                self.prepare_instance()
                self.config()
                self.build()
                self.resign()
        finally:
            self.unlock_repo(lockf)

    def make_temp(self):
        if not self.temppath:
            self.temppath = tempfile.mkdtemp(prefix='andchecker', suffix='inst')

    def prepare_instance(self):
        self.make_temp()
        self.instance_path = self.temppath + '/instance_inst'
        shutil.copytree(template_path, self.instance_path)

    def config(self):
        subprocess.check_call("cd %s && ./config.sh %s %s" % (self.instance_path, self.package, self.activity), shell = True)

    def build(self):
        self.make_repo()
        subprocess.check_call("cd %s && ndk-build && ant release" % self.instance_path, shell = True)
        shutil.copy("%s/bin/andchecker-release-unsigned.apk" % self.instance_path, self.get_apk_path())
        try:
            shutil.rmtree(self.instance_path)
        except:
            pass

    def resign(self):
        instapk = apk.APK(self.get_apk_path())
        instapk.resign(settings.keystore, settings.keystore_pass, settings.keystore_alias)

    def get_apk_path(self):
        return "%s/%s.apk" % (inst_apks_dir, self.fullname)

    def install_on(self, dev):
        dev.uninstall("com.andchecker")
        dev.install(self.get_apk_path())

    def start(self, dev, use_msg_proxy, inst_args):
        dev.execute("input keyevent 3")
        cmd = "am instrument"
        if use_msg_proxy:
            cmd += " -e message_proxy_type " + use_msg_proxy
        if inst_args:
            p = parser.Parser()
            args = p.parse_line(inst_args)
            for key in args:
                cmd += " -e %s %s" % (key, args[key])

        cmd += " com.andchecker/.ACInstrumentation"
        dev.execute(cmd)
        self.forward_port(dev)
        self.modify_config()

        dev.execute("input keyevent 82")
        dev.execute("input keyevent 4")

    def forward_port(self, dev):
        self.localport = random.randint(1024, 65535)
        while True:
            try:
                dev.forward("tcp:%d" % self.localport, "tcp:22228")
                break
            except apptest.exception.ExternalError as e:
                logger.warn("adb forward error: %r" % e)
                self.localport = random.randint(1024, 65535)

    def restart(self, dev):
        self.forward_port(dev)
        self.modify_config()

    def modify_config(self):
        self.make_temp()
        self.inst_conf = self.temppath + '/android_test.conf'
        conf = ConfigParser.ConfigParser()
        conf.read('android_test.conf')
        conf.set('conn', 'port', str(self.localport))
        conf.write(open(self.inst_conf, 'w'))

    def get_config_path(self):
        return self.inst_conf

    def cleanup(self):
        try:
            if self.temppath:
                shutil.rmtree(self.temppath)
        except:
            pass

    def get_forwarded_port(self):
        return self.localport

    def parse_intent(self, action, category = "DEFAULT", data = None, component = None, mime = None):
        if not "." in action:
            action = "android.intent.action." + action
        if category:
            if not "." in category:
                category = "android.intent.category." + category
        cmdline = "-a %s" % action
        if category:
            cmdline += " -c %s" % category
        if data:
            cmdline += " -d %s" % data
        if extras:
            for extra in extras:
                if extra['type'] == "int":
                    cmdline += " --ei %s %s" % (extra['key'], extra['value'])
                elif extra['type'] == "str":
                    cmdline += " -e %s %s" % (extra['key'], extra['value'])
                elif extra['type'] == "uri":
                    cmdline += " --eu %s %s" % (extra['key'], extra['value'])
        if mime:
            cmdline += " -t %s" % mime
        if component:
            cmdline += " %s" % component

    def inject_intent(self, dev, **kwargs):
        dev.run_cmd_nowait("shell am start %s" % self.parse_intent(**kwargs))

    def inject_broadcast(self, dev, action, data = None):
        dev.run_cmd_nowait("shell am broadcast %s" % self.parse_intent(**kwargs))


