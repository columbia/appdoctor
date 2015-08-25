#!/usr/bin/env python
import argparse
import subprocess
import os
import sys
import shutil
import flags
import buglib

def process_cmd():
    argparser = argparse.ArgumentParser()
    argparser.add_argument("cmd", type=str, help='command to run')
    argparser.add_argument("log_files", metavar='logfile', nargs="+", type=str, help="list of log files to collect")
    argparser.add_argument("-i", "--bug-id", type=int, help="the number of bug specified")
    argparser.add_argument("-s", "--hostname", type=str, help="hostname of the host of the report (or specify cmd_log)")
    argparser.add_argument("-n", "--round-number", type=int, help="global round number")
    argparser.add_argument("-c", "--cmd-log", type=str, help="command log")
    argparser.add_argument("-t", "--interactive", help="interactive replay mode (default=False)", action="store_true")
    argparser.add_argument("-o", "--console-mode", help="console mode (default=False)", action="store_true")
    argparser.add_argument("-r", "--retry-count", type=int, help="retry count (default=1)", default=1)
    argparser.add_argument("-m", "--msg-log", type=str, help="message log (optional)")
    argparser.add_argument("-f", "--faithful", help="faithful replay (default=False)", action="store_true")
    argparser.add_argument("-u", "--cont-after-succ", help="continue after successfully replay (default=False)", action="store_true")
    argparser.add_argument("-x", "--cont-after-branch", help="continue after replay branched (default=False)", action="store_true")
    argparser.add_argument("-l", "--local", help="log from local (default=False)", action="store_true")
    argparser.add_argument("-y", "--simplify-result", help="simplification result", type=str, default="")
    args = argparser.parse_args()

    if args.cmd == "dump":
        bugs = buglib.collect(args.log_files)
        buglib.dump(bugs)

    if args.cmd == "collect":
        bugs = buglib.collect(args.log_files)
        buginfo = bugs[args.bug_id - 1]
        shutil.copy(buginfo['err log'], "err_log")
        shutil.copy(buginfo['command log'], "cmd_log")
        print "please copy err_log and cmd_log into your machine, and run 'python analyze.py replay err_log -i %d -c cmd_log'" % int(buginfo['report id'])

    if args.cmd == "replay" or args.cmd == "simplify":
        if args.hostname:
            if not args.local:
                err_log = args.log_files[0]
                log_file = "logs/log-%s-%d/%s.log" % (args.hostname, args.round_number, err_log)
                print "copy error log"
                subprocess.check_call("scp %s:andchecker/%s err.log" % (args.hostname, log_file), shell=True)
                err_log = "err.log"
            else:
                err_log = "remote_logs/log-%s-%d/%s.log" % (args.hostname, args.round_number, args.log_files[0])
        else:
            err_log = args.log_files[0]
        bugs = buglib.collect([err_log])
        if args.bug_id is None:
            buginfo = bugs[0]
        else:
            buginfo = bugs[args.bug_id - 1]
            buglib.dump_bug_detailed(buginfo)

        # grab cmd_log, msg_log and other files
        if args.hostname:
            if not args.local:
                # copy command log
                if 'attached file' in buginfo:
                    try:
                        os.makedirs("log-details")
                    except:
                        pass
                    for (name, val) in buginfo['attached file']:
                        if name == "saved attachment":
                            filename = val
                        elif name == "attachment path":
                            filepath = val
                            subprocess.check_call("scp %s:andchecker/%s log-details/%s" % (args.hostname, filepath, filename), shell=True)

                if args.cmd_log:
                    cmd_log = args.cmd_log
                else:
                    cmd_log = buginfo['command log']
                    print "copy command log"
                    subprocess.check_call("scp %s:andchecker/%s cmd_log" % (args.hostname, cmd_log), shell=True)
                    cmd_log = "cmd_log"

                if args.msg_log:
                    msg_log = args.msg_log
                else:
                    if 'message log' in buginfo:
                        msg_log = buginfo['message log']
                        print "copy message log"
                        subprocess.check_call("scp %s:andchecker/%s msg_log" % (args.hostname, msg_log), shell=True)
                        msg_log = "msg_log"
                    else:
                        msg_log = None
            else:
                if args.cmd_log:
                    cmd_log = args.cmd_log
                else:
                    cmd_log = "remote_%s" % buginfo['command log']
                if args.msg_log:
                    msg_log = args.msg_log
                else:
                    msg_log = "remote_%s" % buginfo['message log']
        else:
            if not args.cmd_log:
                print "Missing command log or host name"
                argparser.print_help()
                sys.exit(1)
            cmd_log = args.cmd_log
            if not args.msg_log:
                msg_log = None
            else:
                msg_log = args.msg_log

        print "run reproduce"
        cmd = "python %s %s -f %s -d %s -r %s -t %s:%d %s -b %d" % (flags.get_base_path("reproduce.py"), "-i" if args.interactive else "", buginfo['apk path'], buginfo['device config'], cmd_log, err_log, args.bug_id, args.cmd, args.retry_count)
        if args.console_mode:
            cmd += " -c"
        # generally, old message log cannot be used in faithful mode
        if msg_log and not args.faithful and args.msg_log != "no":
            cmd += " -p %s" % msg_log
        if args.faithful:
            cmd += " -u"
        if args.cont_after_succ:
            cmd += " -o"
        if args.cont_after_branch:
            cmd += " -x"
        if args.simplify_result:
            cmd += " -y %s" % args.simplify_result
        print "cmd: %s" % cmd
        subprocess.call(cmd, shell=True)

    if args.cmd == "analyze":
        bugs = buglib.collect(args.log_files)
        ret = {}
        for bug in bugs:
            apk = bug['apk path']
            stack = None
            if 'checker' in bug:
                err = bug['checker']
            elif 'remote exception' in bug:
                err = bug['remote exception']
                buglib.find_stack(bug)
            else:
                err = bug['exception type']

            if 'exception stack' in bug:
                stack = bug['exception stack']
                err += " %s" % bug['exception stack']

            if stack and 'com.andchecker' in stack:
                # our bug!
                continue

            if err == "__DUMMY__":
                continue

            if "market://" in err:
                continue
            if "View not attached to window" in err:
                continue
            if "android.app.Instrument" in err:
                continue
            if "BadTokenException" in err:
                continue
            if err == 'KeyError':
                continue
            if not apk in ret:
                ret[apk] = {}
            if not err in ret[apk]:
                ret[apk][err] = {}
            if 'command count' in bug:
                cmd_cnt = bug['command count']
            else:
                cmd_cnt = 10000
            if not cmd_cnt in ret[apk][err]:
                ret[apk][err][cmd_cnt] = []
            ret[apk][err][cmd_cnt].append("%s:%s" % (bug['err log'], bug['report id']))

        for apk in ret:
            print "app: %s" % apk
            for err in ret[apk]:
                print "\terror: %s" % err
                for cmd_cnt in sorted(ret[apk][err].keys())[:3]:
                    print '\t\tcommands: %d' % cmd_cnt,
                    logs = '\t\t\tlogs: '
                    for err_log in ret[apk][err][cmd_cnt][:4]:
                        logs += ' ' + err_log
                    print logs

if __name__ == "__main__":
    process_cmd()
