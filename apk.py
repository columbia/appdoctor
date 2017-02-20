import subprocess
import tempfile
import shutil
import os
import re
import sys
import logging
import copy
from apptest.exception import *

apktool_path = "tools/apktool.jar"
logger = logging.getLogger("apk")

class APK:
    def __init__(self, path):
        self.path = path
        self.tempdir = None
        self.details = None

    def resign(self, keystore, keystore_pass, keystore_alias):
        subprocess.call("zip -d %s META-INF*" % self.path, shell=True)
        subprocess.check_call("jarsigner -keystore %s -storepass %s %s -sigalg MD5withRSA -digestalg SHA1 %s " % (keystore, keystore_pass, self.path, keystore_alias), shell=True)

    def get_perms(self):
        perms = subprocess.check_output("aapt d permissions %s" % self.path, shell=True)
        perms = perms.split('\n')
        ret = set()
        perm_re = re.compile('uses-permission: (.*)')
        for perm in perms:
            if perm_re.match(perm):
                item = perm_re.match(perm).group(1)
                ret.add(item)
        return ret

    def add_permission(self, perms):
        curr_perms = self.get_perms()
        perm_to_add = []
        for perm in perms:
            if not perm in curr_perms:
                perm_to_add += [perm]

        shared_uid = self.get_shared_uid()

        if not perm_to_add and (not shared_uid or not "com.google.android.apps.maps" in shared_uid):
            return

        self.tempdir = tempfile.mkdtemp(prefix='andchecker', suffix='perm')
        workdir = self.tempdir + "/mod"
        output_apk = self.tempdir + "/modified.apk"

        subprocess.check_call("java -jar %s d -s %s -o %s" % (apktool_path, self.path, workdir), shell = True)
#            subprocess.check_call("unzip %s AndroidManifest.xml" % self.path, shell = True)
#            subprocess.check_call("java -cp axml-0.9.jar:. AddPermission AndroidManifest.xml android.permission.INTERNET", shell = True)
#            shutil.copy(self.path, "modified.apk")
#            subprocess.check_call("zip -u modified.apk AndroidManifest.xml", shell = True)
#            subprocess.check_call("java -jar AXMLPrinter2.jar AndroidManifest.xml > AndroidManifest.unzip.xml", shell = True)
        with open("%s/AndroidManifest.net.xml" % workdir, "w") as newf:
            with open("%s/AndroidManifest.xml" % workdir) as f:
                line = f.readline()
                while True:
                    if line == '':
                        break
                    if "android:sharedUserId=" in line:
                        startmark = "android:sharedUserId=\""
                        pos = line.find(startmark)
                        if pos != -1:
                            endpos = line.find("\"", pos + len(startmark))
                            if endpos != -1:
                                line = line[:pos + len(startmark)] + "com.andchecker" + line[endpos:]
                    if re.match("</manifest>", line):
                        for perm in perm_to_add:
                            newf.write("<uses-permission android:name=\"%s\"></uses-permission>" % perm)
                    newf.write(line)
                    line = f.readline()
        os.remove("%s/AndroidManifest.xml" % workdir)
        shutil.move("%s/AndroidManifest.net.xml" % workdir, "%s/AndroidManifest.xml" % workdir)
        subprocess.check_call("java -jar %s b %s -o %s" % (apktool_path, workdir, output_apk), shell = True)
        self.path = output_apk
        try:
            shutil.rmtree(workdir)
        except:
            pass

    def get_details(self):
        if not self.details:
            self.details = subprocess.check_output("aapt l -a %s" % self.path, shell = True)
        return self.details

    def get_package(self):
        details = self.get_details()
        in_manifest = False
        for line in details.split('\n'):
            if 'E: manifest' in line:
                in_manifest = True
            elif 'A: package' in line and in_manifest:
                match = re.search("A:\s+package=\"([^\"]+)\"", line)
                if match:
                    logger.info("package detected: %s" % match.group(1))
                    return match.group(1)
        raise InternalException("can't find package name")

    def get_level(self, line):
        match = re.match(" *", line)
        return len(match.group(0))

    def get_default_activity_full(self, package_name):
        act_name = self.get_default_activity()
        if act_name[:1] == '.':
            act_name = package_name + act_name
        elif not '.' in act_name:
            act_name = package_name + "." + act_name
        logger.info("default activity detected: %s" % act_name)
        return act_name

    def process_details(self, elem_cb = None, attr_cb = None):
        details = self.get_details()
        element_stack = []
        element_level = []
        element_re = re.compile("\s+E:\s+([a-zA-Z-]+)\s+.*")
        attrib_re = re.compile("\s+A:\s+([a-zA-Z-:]+)\(([0-9a-fx]+)\)\s*=\s*\"([^\"]+)\"\s*.*")
        attrib_withtype_re = re.compile("\s+A:\s+([a-zA-Z-:]+)\(([0-9a-fx]+)\)\s*=\s*\(type\s+([^\)]+)\)([^\s]+)\s*.*")
        for line in details.split('\n'):
            level = self.get_level(line)
            for i in range(len(element_stack)-1, -1, -1):
                if element_level[i] >= level:
                    element_stack.pop()
                    element_level.pop()
                else:
                    break
            if element_re.match(line):
                match = element_re.match(line)
                etype = match.group(1)
                element_stack += [etype]
                element_level += [level]
                if elem_cb:
                    elem_cb(element_stack, element_level, etype)

            if attr_cb:
                if attrib_re.match(line):
                    match = attrib_re.match(line)
                    attr_cb(element_stack, element_level, match.group(1), match.group(2), match.group(3), None)
                if attrib_withtype_re.match(line):
                    match = attrib_withtype_re.match(line)
                    attr_cb(element_stack, element_level, match.group(1), match.group(2), match.group(4), match.group(3))

    def get_shared_uid(self):
        shared_uid = []
        def attr_cb(stack, level, name, id_, value, type_):
            if stack[-1] == "manifest":
                if name == "android:sharedUserId":
                    shared_uid.append(value)
        self.process_details(attr_cb = attr_cb)
        if shared_uid:
            return shared_uid[0]
        else:
            return None

    def has_something(self, sth):
        info = {sth: False}
        def elem_cb(stack, level, name):
            if name == sth:
                info[sth] = True

        self.process_details(elem_cb = elem_cb)
        return info[sth]

    def get_intent_filters(self):
        current_filters = []
        intent_filters = []
        current_intent_filter = {}
        current_act = {}
        current_recv = {}
        state = {}
        def attr_cb(stack, level, name, id_, value, type_):
            if len(stack) >= 2 and stack[-2] == 'intent-filter':
                if stack[-1] == 'action':
                    if name == 'android:name':
                        if not 'SEND_MULTIPLE' in value:
                            current_intent_filter['action'] = value
                elif stack[-1] == 'category':
                    if name == 'android:name':
                        if not 'category' in current_intent_filter:
                            current_intent_filter['category'] = []
                        current_intent_filter['category'].append(value)
                elif stack[-1] == 'data':
                    if not 'data' in current_intent_filter:
                        current_intent_filter['data'] = {}
                    if name == 'android:scheme':
                        if not 'scheme' in current_intent_filter['data']:
                            current_intent_filter['data']['scheme'] = []
                        current_intent_filter['data']['scheme'].append(value)
                    elif name == 'android:mimeType':
                        if not 'mime' in current_intent_filter['data']:
                            current_intent_filter['data']['mime'] = []
                        current_intent_filter['data']['mime'].append(value)

            if len(stack) >= 1 and stack[-1] == 'activity':
                if name == 'android:name':
                    current_act['name'] = value
            if len(stack) >= 1 and stack[-1] == 'receiver':
                if name == 'android:name':
                    current_recv['name'] = value

        def elem_cb(stack, level, name):
            if "intent_level" in state:
                if level[-1] <= state['intent_level']:
                    # finish current intent!
                    del state['intent_level']
                    if 'action' in current_intent_filter and len(current_intent_filter) < 3:
                        current_filters.append(copy.deepcopy(current_intent_filter))
                    current_intent_filter.clear()
            if "activity_level" in state:
                if level[-1] <= state['activity_level']:
                    # finish current activity!
                    del state['activity_level']
                    for flt in current_filters:
                        flt['activity'] = copy.deepcopy(current_act)
                        intent_filters.append(copy.deepcopy(flt))
                    current_act.clear()
                    del current_filters[:]
            if "receiver_level" in state:
                if level[-1] <= state['receiver_level']:
                    # finish current receiver
                    del state['receiver_level']
                    for flt in current_filters:
                        flt['receiver'] = copy.deepcopy(current_recv)
                        intent_filters.append(copy.deepcopy(flt))
                    current_recv.clear()
                    del current_filters[:]

            if name == 'intent-filter':
                # new intent-filter
                state['intent_level'] = level[-1]
            elif name == 'activity':
                # new activity
                state['activity_level'] = level[-1]
            elif name == 'receiver':
                state['receiver_level'] = level[-1]

        self.process_details(attr_cb = attr_cb, elem_cb = elem_cb)
        for intent in intent_filters:
            print intent

        return intent_filters

    def has_service(self):
        return self.has_something("service")

    def has_receiver(self):
        return self.has_something("receiver")

    def has_provider(self):
        return self.has_something("provider")

    def has_activity(self):
        return self.has_something("activity")

    def get_receivers(self):
        def attr_cb(stack, level, name, id_, value, type_):
            if stack[-1] == "action" and stack[-2] == "intent-filter" and stack[-3] == "receiver":
                if name == "android:name":
                    print value
        self.process_details(attr_cb = attr_cb)

    def get_used_libs(self):
        used_libs = []
        einfo = {}

        def attr_cb(stack, level, name, id_, value, type_):
            if stack[-1] == "uses-library":
                if name == "android:name":
                    if einfo['required']:
                        used_libs.append(value)
                        einfo['added'] = True
                    else:
                        logger.debug("ignored optional dep %s" % value)

                if name == "android:required":
                    if value == "0x0":
                        if einfo['added']:
                            removed = used_libs.pop()
                            logger.debug("removed optional dep %s" % removed)
                        else:
                            einfo['required'] = False

        def elem_cb(stack, level, name):
            if name == "uses-library":
                einfo['required'] = True
                einfo['added'] = False

        self.process_details(elem_cb = elem_cb, attr_cb = attr_cb)
        return used_libs

    def files(self):
        return subprocess.check_output("aapt l %s" % self.path, shell = True).split("\n")

    def get_abis(self):
        abis = set()
        for f in self.files():
            if f.startswith("lib/"):
                if "arm" in f:
                    abis.add("arm")
                elif "x86" in f:
                    abis.add("x86")
                elif "mips" in f:
                    abis.add("mips")
                else:
                    abi = f.split('/')[1]
                    if abi:
                        abis.add(abi)

        return abis

    def get_total_activities(self):
        details = self.get_details()
        element_stack = []
        element_level = []
        act_name = ''
        act_set = set()
        element_re = re.compile("\s+E:\s+([a-zA-Z-]+)\s+.*")
        for line in details.split('\n'):
            level = self.get_level(line)
            for i in range(len(element_stack)-1, -1, -1):
                if element_level[i] >= level:
                    element_stack.pop()
                    element_level.pop()
                else:
                    break
            if element_re.match(line):
                match = element_re.match(line)
                etype = match.group(1)
                element_stack += [etype]
                element_level += [level]

            if 'activity' in element_stack or 'activity-alias' in element_stack:
                if ('activity' == element_stack[-1] or 'activity-alias' == element_stack[-1]) and 'A: android:name' in line:
                    match = re.search("A:\s+android:name\([^)]+\)\s*=\s*\"([^\"]+)\"", line)
                    if match:
                        act_name = match.group(1)
                        act_set.add(act_name)
        return act_set

    def get_default_activity(self):
        details = self.get_details()
        element_stack = []
        element_level = []
        act_name = ''
        is_launcher = False
        element_re = re.compile("\s+E:\s+([a-zA-Z-]+)\s+.*")
        for line in details.split('\n'):
            level = self.get_level(line)
            for i in range(len(element_stack)-1, -1, -1):
                if element_level[i] >= level:
                    element_stack.pop()
                    element_level.pop()
                else:
                    break
            if element_re.match(line):
                match = element_re.match(line)
                etype = match.group(1)
                element_stack += [etype]
                element_level += [level]
#                print "element %s at %d" % (etype, level)
                if etype == "activity" or etype == 'activity-alias':
                    is_launcher = False
                    act_name = ''

            if 'activity' in element_stack or 'activity-alias' in element_stack:
                if ('activity' == element_stack[-1] or 'activity-alias' == element_stack[-1]) and 'A: android:name' in line:
                    match = re.search("A:\s+android:name\([^)]+\)\s*=\s*\"([^\"]+)\"", line)
                    if match:
                        act_name = match.group(1)
#                        print "got act name: %s" % act_name
                        if is_launcher:
                            return act_name
                elif 'A: android:name' in line and element_stack[-1] == 'category':
                    match = re.search("A:\s+android:name\([^)]+\)\s*=\s*\"([^\"]+)\"", line)
                    if match:
                        cat_name = match.group(1)
#                        print "got cat name: %s" % cat_name
                        if "android.intent.category.LAUNCHER" in cat_name:
                            if act_name:
                                return act_name
                            else:
                                is_launcher = True
        raise InternalException("can't find default activity name")

    def cleanup(self):
        try:
            if self.tempdir:
                shutil.rmtree(self.tempdir)
        except:
            pass

    def get_path(self):
        return self.path

def identify_apk(apk):
    this_apk = APK(apk)
    print "%s: " % apk
    print "   deps: ", this_apk.get_used_libs()
    print "   perms: ", this_apk.get_perms()

if __name__ == "__main__":
    for apk in sys.argv[1:]:
        identify_apk(apk)
