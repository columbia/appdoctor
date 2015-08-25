import tempfile
import subprocess
import logging
import re
import shutil
import time
import os.path
import base64

import parser
import config
import interface
from exception import *

widgets_id = {}
hints = {}

logger = logging.getLogger("widget")

def get_widget_id(name):
    if (name in widgets_id):
        return widgets_id[name]
    else:
        if name.startswith("android:"):
            name = name[8:]
        try:
            return interface.get_widget_id(name)
        except ActionFailure:
            raise WidgetNotFound("can't find widget id for %s" % name)

def check_line(line, regex):
    match = regex.match(line)
    if (match is None):
        return
    global widgets_id
    if (len(match.groups()) == 2):
        widget_name = match.group(1)
        widget_id = match.group(2)
        if (widget_name in widgets_id):
            if (widget_id != widgets_id[widget_name]):
                logger.warning("duplicated widget name with different id!")
                logger.warning("widget: %s id: %s -> %s" % (widget_name,
                    widgets_id[widget_name], widget_id))
#        logger.debug("%s -> %s" % (widget_name, widget_id))
        widgets_id[widget_name] = widget_id

def load_widgets_id(apk_path):
    logger.info("loading widget ids from %s", apk_path)
    temp_dir = tempfile.mkdtemp(prefix="apkdec")
    if (not temp_dir):
        raise InternalError("mkdtemp() fail")
    try:
        logger.info("decode %s", apk_path)
        ret = subprocess.call(["java", "-jar", config.get_str("tool", "apktool_path"), "d", "-f", apk_path, temp_dir])
        if (ret != 0):
            raise DecodeFailure("failed to decode with apktool")

        global widgets_id
        widgets_id = {}
        layout_files = subprocess.check_output(['find', temp_dir, '-name', 'R$id.smali'])
        id_regex = re.compile('.*public static final ([^ ]+):I = ([0-9a-fx]+)')
        for layout_file in layout_files.split('\n'):
            if (not layout_file):
                break
            logger.info("loading from layout %s", layout_file)
            with open(layout_file, "r") as layout:
                while (True):
                    line = layout.readline()
                    if (line == ''):
                        break
                    check_line(line, id_regex)

        public_file = temp_dir + "/res/values/public.xml"
        publicid_regex = re.compile('.*<public\s+(?:.*\s+)*type="id"\s+(?:.*\s+)*name="([^"]+)"\s+(?:.*\s+)*id="([0-9a-fx]+)".*/>.*')
        logger.info(public_file)
        if os.path.exists(public_file):
            logger.info("loading ids from public.xml %s" % public_file)
            with open(public_file, "r") as publicf:
                while (True):
                    line = publicf.readline()
                    if line == '':
                        break
                    check_line(line, publicid_regex)

        logger.info("finished loading widget ids")
    finally:
        shutil.rmtree(temp_dir)

def load_widgets_id_from_r(r_path):
    logger.info("loading widget ids from %s", r_path)
    global widgets_id
    widgets_id = {}
    id_regex = re.compile('.*public static final int ([^ ]+)\s*=\s*([0-9a-fx]+)')
    idclass_regex = re.compile('.*class id\s+.*')
    classend_regex = re.compile('.*}.*')
    inside_id = False
    with open(r_path, "r") as rfile:
        while (True):
            line = rfile.readline()
            if (line == ''):
                break
            if inside_id:
                if classend_regex.match(line):
                    break
                check_line(line, id_regex)
            else:
                if idclass_regex.match(line):
                    inside_id = True

    logger.info("finished loading widget ids")
    print widgets_id

def click(name):
    widget_id = get_widget_id(name)
    return interface.click(widget_id)

def enter(name, text):
    widget_id = get_widget_id(name)
    return interface.enter(widget_id, text)

def get_text(name):
    widget_id = get_widget_id(name)
    return interface.get_text(widget_id)

def set_text(name, text):
    widget_id = get_widget_id(name)
    return interface.set_text(widget_id, text)

def select_dialog_click(entry):
    list_id = interface.get_view_by_class("AlertController$RecycleListView")
    if list_id is None:
        raise WidgetNotFound("no selection dialog visible")
    child_id = interface.get_view_child(list_id, entry)
    if child_id is None:
        raise WidgetNotFound("no entry %d in selection dialog" % entry)
    interface.click(child_id)

def get_list_item_id(list, entry):
    list_id = get_widget_id(list)
    return interface.get_view_child(list_id, entry)

class Hint(object):
    def __init__(self, package, type, target, content):
        self.package = package
        self.type = type
        self.target = target
        self.content = content

    def dump(self):
        print "hint ->[%s]%s %s %s" % (self.type, self.target, self.content, base64.b64encode(self.content.encode('utf-8')))

def hint_edit(edit, contents, no_cmd):
    if no_cmd: return
    edit_id = get_widget_id(edit)
    return interface.hint_edit(edit_id, contents.split(','))

def hint_btn(btn, contents, no_cmd):
    if no_cmd: return
    btn_id = get_widget_id(btn)
    return interface.hint_btn(btn_id, contents.split(','))

def hint_app(target, contents, no_cmd):
    if target == "start":
        for hint in contents.split(","):
            (hint_name, hint_value) = hint.split(':', 1)
            if hint_name == "sleep":
                time.sleep(float(hint_value) / 1000)

def read_hint(apk_path, hint_file="hints.csv"):
    if os.path.exists(hint_file):
        if not widgets_id:
            logger.info("decode to get widget id first")
            load_widgets_id(apk_path)
        logger.info("reading hints from %s" % hint_file)
        p = parser.Parser()
        results = p.parse_file(Hint, hint_file)
        for result in results:
            if result.package in hints:
                hints[result.package].append(result)
            else:
                hints[result.package] = [result]

def send_hint(package, no_cmd = False):
    if package in hints:
        for hint in hints[package]:
            if hint.type == "edit":
                hint_edit(hint.target, hint.content, no_cmd)
            if hint.type == "btn":
                hint_btn(hint.target, hint.content, no_cmd)
            if hint.type == "app":
                hint_app(hint.target, hint.content, no_cmd)

