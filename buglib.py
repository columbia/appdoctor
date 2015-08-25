import logging
import os
import sys

logger = logging.getLogger('buglib')

def collect(log_files):
    bugs = []
    files_count = len(log_files)
    files_read = 0
    sys.stderr.write('collecting: ')
    last_mark = 0

    for f in log_files:
        in_bugrep = False
        in_multi = False
        multi_name = ''
        multi_content = ''
        currlist = []
        currlist_name = ''
        for line in open(f):
            if '=== ANDCHECKER BUG REPORT' in line:
                in_bugrep = True
                buginfo = {'err log': f}
            elif '=== END OF ANDCHECKER BUG REPORT' in line:
                in_bugrep = False
                bugs.append(buginfo)
                if 'command log' in buginfo:
                    cmd_log = buginfo['command log']
                    (first, last) = os.path.split(cmd_log)
                    (_, dirname) = os.path.split(first)
                    real_cmd_log = os.path.join(os.path.dirname(f), dirname, last)
                    if os.path.exists(real_cmd_log):
                        buginfo['local command log'] = real_cmd_log
                        cmd_cnt = 0
                        for cmd in open(real_cmd_log):
                            cmd = cmd.strip()
                            if cmd.startswith('#'):
                                continue
                            cmd_cnt += 1
                        buginfo['command count'] = cmd_cnt
            elif in_bugrep:
                if in_multi:
                    if '=== END OF %s ===' % multi_name in line:
                        buginfo[multi_name] = multi_content
                        in_multi = False
                    else:
                        multi_content += line
                else:
                    line = line.rstrip()
#                    print line
                    (name, val) = line.split(":", 1)
                    val = val.lstrip()
                    if val == '|':
#                        print "in_multi"
                        in_multi = True
                        multi_content = ''
                        multi_name = name
                    elif name[0] == '-':
                        name = name[2:]
                        currlist.append((name, val))
                        buginfo[currlist_name] = currlist
                    elif name == 'attached file':
                        currlist = []
                        currlist_name = name
                    else:
                        buginfo[name] = val
        files_read += 1
        if int(files_read * 100 / files_count) > last_mark:
            last_mark = int(files_read * 100 / files_count)
            if last_mark % 25 == 0:
                sys.stderr.write('=')
            elif last_mark % 5 == 0:
                sys.stderr.write('#')
            else:
                sys.stderr.write('*')


    sys.stderr.write('\n')
    return bugs

def dump(bugs):
    num = 0
    for buginfo in bugs:
        dump_bug(num, buginfo)
        num += 1

def dump_bug(num, buginfo):
    if num is not None:
        print "%d: " % num,

    print get_bug_info(buginfo)

def get_bug_info(buginfo):
    result = ''
    result += "{id: %s}" % buginfo['report id']
    if 'checker' in buginfo:
        result += '{type: %s}' % buginfo['checker']
    else:
        result += '{type: %s}' % buginfo['exception type']

    result += '{apk: %s}' % buginfo['apk path']
    if 'device config' in buginfo:
        result += '{config: %s}' % buginfo['device config']
    if 'command log' in buginfo:
        result += '{command log: %s}' % buginfo['command log']
    if 'message log' in buginfo:
        result += '{message log: %s}' % buginfo['message log']
    if 'remote exception' in buginfo:
        result += '{remote exception: %s}' % buginfo['remote exception']
    result += '{err log: %s}' % buginfo['err log']

    return result

def dump_bug_detailed(buginfo):
    print "id: %s" % buginfo['report id']
    if 'checker' in buginfo:
        print 'type: %s' % buginfo['checker']
    else:
        print 'type: %s' % buginfo['exception type']

    print 'apk: %s' % buginfo['apk path']
    print 'config: %s' % buginfo['device config']
    if 'command log' in buginfo:
        print 'command log: %s' % buginfo['command log']
    if 'message log' in buginfo:
        print 'message log: %s' % buginfo['message log']

    print "logcat: %s" % buginfo['recent logcat']
    print "exception: %s" % buginfo['exception']
    print "version: %s" % buginfo['device version']

def check_field(bug1, bug2, field, firstword=False):
    if field in bug1:
        if field in bug2:
            same = True
            if firstword:
                if bug1[field].split(' ')[0] != bug2[field].split(' ')[0]:
                    same = False
            else:
                if bug1[field] != bug2[field]:
                    same = False
            if not same:
                logger.debug('field "%s" mismatch' % field)
                return False
        else:
            return False
    elif field in bug2:
        return False
    return True

def same_bug(bug1, bug2):
    if not check_field(bug1, bug2, 'checker'):
        return False
    if not check_field(bug1, bug2, 'exception type'):
        return False
#    if not check_field(bug1, bug2, 'exception'):
#        return False
    if not check_field(bug1, bug2, 'remote exception', firstword=True):
        return False

    return True

def fix_type(buginfo):
    if 'recent logcat' in buginfo:
        if 'exception type' in buginfo and buginfo['exception type'] == 'apptest.exception.ConnectionBroken':
            import log
            (exc_class, stack) = log.find_exc_in_logcat(buginfo['recent logcat'])
            if exc_class:
                buginfo['remote exception'] = exc_class
            if stack:
                buginfo['exception stack'] = stack

def find_stack(bug):
    if not 'remote exception' in bug:
        return
    if not 'recent logcat' in bug:
        return
    if 'exception stack' in bug:
        return
    lines = bug['recent logcat'].split('\n')
    lines.reverse()
    err = bug['remote exception']
    prevline = ''
    for line in reversed(bug['recent logcat'].split('\n')):
        if err in line:
            if 'at' in prevline:
                if ':' in prevline:
                    prevline = prevline.split(':', 1)[1]
                prevline = prevline.strip()
                bug['exception stack'] = prevline
        if '\tat ' in line:
            prevline = line
