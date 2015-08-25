#!/usr/bin/env python
import os
import traceback
import apptest
import flags
import config
import errno
import logging

LOG_DIR = "log/"
LOG_PREFIX = "err"
LOG_SUFFIX = ".log"

logger = logging.getLogger("log")
curr_log_file = None
error_id = 0
log_file_name = None

def find_exc_in_logcat(logcat):
    if not logcat:
        return (None, None)
    exc_part = 0
    exc_class = None
    exc_start = -1
    stack = None
    lines = logcat.split('\n')
    for i in xrange(len(lines)):
        line = lines[i].strip()
        if "AndroidRuntime" in line:
            if "FATAL EXCEPTION:" in line:
                exc_part = 1
            elif exc_part == 1:
                parts = line.split(':', 1)
                if len(parts) > 1:
                    exc_class = parts[1].strip()
                    exc_start = i
                    stack = None
                exc_part = 2
            elif exc_part == 2:
                parts = line.split(':', 1)
                if len(parts) > 1:
                    stack = parts[1].strip()
                    if stack.startswith("at "):
                        exc_part = 0
    if exc_start >= 0:
        possible_stack = None
        back_len = 0
        for i in xrange(exc_start - 1, -1, -1):
            back_len += 1
            if back_len > 100:
                break
            line = lines[i].strip()
            if "System.err" in line:
                if exc_class in line:
                    if possible_stack:
                        stack = possible_stack
                    break
                parts = line.split(':', 1)
                if len(parts) > 1:
                    part = parts[1].strip()
                    if part.startswith("at "):
                        possible_stack = part
    return (exc_class, stack)

def create_log_file(log_file):
    if log_file:
        try:
            os.makedirs(os.path.dirname(log_file + LOG_SUFFIX))
        except:
            pass
        return (open(log_file + LOG_SUFFIX, "w+"), log_file)
    if not os.path.isdir(LOG_DIR):
        try:
            os.mkdir(LOG_DIR)
        except:
            pass
    if not os.path.isdir(LOG_DIR):
        raise Exception("fail to create log dir")
    i = 0
    while True:
        try:
            logf_name = "%s/%s%d%s" % (LOG_DIR, LOG_PREFIX, i, LOG_SUFFIX)
            fd = os.open(logf_name, os.O_RDWR | os.O_CREAT | os.O_EXCL)
            logf = os.fdopen(fd, "w+")
            set_log_file(logf_name)
            return (logf, "%s/%s%d" % (LOG_DIR, LOG_PREFIX, i))
        except OSError as e:
            if e.errno == errno.EEXIST:
                i += 1
            else:
                raise e

def record_exception(dev, e, ins_path = None, launcher = None, device_conf = None):
    if flags.stopflag_set():
        return
    global curr_log_file
    global error_id
    if curr_log_file is None:
        curr_log_file = create_log_file(log_file_name)
    error_id += 1
    logger.info("reporting error %d" % error_id)
    (logf, case_dir) = curr_log_file
    if isinstance(e, apptest.exception.RemoteException):
        logcat = dev.get_recent_logcat('D', 1000)
    else:
        logcat = dev.get_recent_logcat()
    logf.write("=== ANDCHECKER BUG REPORT ===\n")
    err_line = "ERROR: {id=%d}" % error_id
    logf.write("report id: %d\n" % error_id)
    if launcher:
        if launcher.app_apk_path:
            err_line += "{apk: %s}" % launcher.app_apk_path
            logf.write("apk path: %s\n" % launcher.app_apk_path)
        if launcher.app_package:
            logf.write("app package: %s\n" % launcher.app_package)
        if launcher.app_start_activity:
            logf.write("app activity: %s\n" % launcher.app_start_activity)

    if device_conf:
        err_line += "{config: %s}" % device_conf
        logf.write("device config: %s\n" % device_conf)
        conf = config.Config.decode(device_conf)
        logf.write("device version: %s\n" % conf.version)
        logf.write("device sdcard: %s\n" % conf.sdcard)
        logf.write("device width: %s\n" % conf.width)
        logf.write("device height: %s\n" % conf.height)
        logf.write("device density: %s\n" % conf.density)
        logf.write("device abi: %s\n" % conf.abi)
        logf.write("device camera: %s\n" % conf.camera)

    logf.write("exception type: %s\n" % e.__class__.__name__)
    if isinstance(e, apptest.exception.InstException):
        logf.write("level: %s\n" % e.level)
        err_line += "{type: %s}" % e.token
        logf.write("checker: %s\n" % e.token)
        logf.write("msg: %s\n" % e.msg)
        logf.write("attached file count: %s\n" % e.file_cnt)
    elif isinstance(e, apptest.exception.ActionFailure):
        err_line += "{type: ActionFailure}"
        logf.write("failed cmd id: %s\n" % e.event_id)
        logf.write("cmd result: %s\n" % e.result)
    elif isinstance(e, apptest.exception.RemoteException):
        err_line += "{type: ForceClose}{exception: %s}" % e.classname
        logf.write("remote exception: %s\n" % e.classname)
        logf.write("msg: %s\n" % e.msg)
    elif isinstance(e, apptest.exception.ConnectionBroken):
        if "timeout" in e.message:
            err_line += "{type: ConnectionTimeout}"
        elif "reset" in e.message:
            err_line += "{type: ConnectionReset}"
        else:
            err_line += "{type: ConnectionBroken}"
        (exc, exc_stack) = find_exc_in_logcat(logcat)
        if exc:
            logf.write("remote exception: %s\n" % exc)
            err_line += "{exception: %s}" % exc
        if exc_stack:
            logf.write("exception stack: %s\n" % exc_stack)
    else:
        err_line += "{type: %s}" % e.__class__.__name__
    logf.write("exception: %r\n" % e)
    logf.write("stack trace: |\n")
    traceback.print_stack(file=logf)
    logf.write("\n=== END OF stack trace ===\n")
    logf.write("recent logcat: |\n")
    logf.write(logcat)
    logf.write("\n=== END OF recent logcat ===\n")
    logf.flush()

    if ins_path:
        try:
            os.makedirs(case_dir)
        except:
            pass

        try:
            src_file = ins_path + "/cmd_log"
            target_file = os.path.join(case_dir, "%d_cmd_log" % error_id)
            dev.pull_file(src_file, target_file)
            if os.path.exists(target_file):
                logf.write("command log: %s\n" % target_file)
                err_line += "{log: %s}" % target_file
        except Exception as ex:
            logf.write("dump command log error: %r\n" % ex)

        try:
            src_file = ins_path + "/msg_log"
            target_file = os.path.join(case_dir, "%d_msg_log" % error_id)
            dev.pull_file(src_file, target_file)
            if os.path.exists(target_file):
                logf.write("message log: %s\n" % target_file)
                err_line += "{msg_log: %s}" % target_file
        except Exception as ex:
            logf.write("dump message log error: %r\n" % ex)

        try:
            src_file = ins_path + "/coverage"
            target_file = os.path.join(case_dir, "%d_coverage" % error_id)
            dev.pull_file(src_file, target_file)
            if os.path.exists(target_file):
                logf.write("coverage data: %s\n" % target_file)
                err_line += "{coverage: %s}" % target_file
        except Exception as ex:
            logf.write("dump coverage data error: %r\n" % ex)

        try:
            src_file = ins_path + "/monkeyrunner_script.py"
            target_file = os.path.join(case_dir, "%d_monkeyrunner_script.py" % error_id)
            dev.pull_file(src_file, target_file)
            if os.path.exists(target_file):
                logf.write("monkey runner script: %s\n" % target_file)
                err_line += "{log: %s}" % target_file
        except Exception as ex:
            logf.write("dump monkey runner script error: %r\n" % ex)

    if isinstance(e, apptest.exception.InstException):
        if e.file_cnt:
            logf.write("attached file:\n")
            try:
                os.makedirs(case_dir)
                err_line += "{dir: %s}" % case_dir
            except:
                pass
            try:
                for f in e.files:
                    logf.write("- attached file: %s\n" % f)
                    bname = os.path.basename(f)
                    (main, ext) = os.path.splitext(bname)
                    tname = "%d_%s%s" % (error_id, main, ext)

                    target_name = os.path.join(case_dir, tname)
                    dev.pull_file(f, target_name)
                    logf.write("- saved attachment: %s\n" % tname)
                    logf.write("- attachment path: %s\n" % target_name)
            except:
                pass

    err_line += "\n"
    logf.write(err_line)
    logf.write("=== END OF ANDCHECKER BUG REPORT ===\n")
    logf.write("============ error %d report end ===============\n" % error_id)
    logf.flush()

def clear():
    global error_id
    global curr_log_file
    if curr_log_file is None:
        curr_log_file = create_log_file(log_file_name)
    else:
        curr_log_file[0].seek(0)
        curr_log_file[0].truncate(0)
    error_id = 0

def set_log_file(log_file):
    global log_file_name
    log_file_name = log_file

def get_log_file():
    return log_file_name
