#!/usr/bin/env python

import argparse
import datetime
import os
import sys


argparser = argparse.ArgumentParser()
argparser.add_argument("log_files", metavar='logfile', nargs="+", type=str, help="list of log files to collect")
args = argparser.parse_args()

data = {}

last_per = -1
cur_log = 0.0
tper = 0
tmper = 0
tcnt = 0

for log_file in args.log_files:
    cur_log += 1
    exes = []
    first_exe = None
    for line in open(log_file):
        line = line.strip()
        timepart = line.split(' ', 2)[:2]
        try:
            timestr = ' '.join(timepart)
            secpart = timestr.split(',')[0]
            t = datetime.datetime.strptime(secpart, "%Y-%m-%d %H:%M:%S")
            mspart = timestr.split(',')[1]
            t += datetime.timedelta(microseconds=int(mspart) * 100)
        except:
            continue


        # startup phase
        if "device:DEBUG init" in line:
            start_time = t
        elif "create avd instance" in line:
            create_instance_start = t
        elif "instance created" in line:
            create_instance_finish = t
        elif "emulator @" in line:
            emulator_inited = t
        # build phase
        elif "grab mod repo" in line:
            mod_start = t
        elif "release mod repo" in line:
            mod_finish = t
        elif "grab inst repo" in line:
            inst_start = t
        elif "release inst repo" in line:
            inst_finish = t
        # execution phase
        elif "=== execution" in line and "start" in line:
            if not first_exe:
                first_exe = t
            execution_start = t
            extra_time = 0
            monkeyrunner_time = 20 # start activity takes 20 seconds
            start_ok = None
            cleanup_start = None
        elif "sending event" in line:
            if "Start" in line:
                send_start = t
            elif "DisableChecker" in line:
                start_ok = t
            elif "Finish" in line:
                cleanup_start = t
        elif "got a new line" in line:
            if " PauseAndResumeOp " in line:
                extra_time += 3
                monkeyrunner_time += 11
            elif " StopAndRestartOp " in line:
                extra_time += 13
                monkeyrunner_time += 31
            elif " RelaunchOp " in line:
                extra_time += 8
                monkeyrunner_time += 10
            elif " LongClickOp " in line:
                extra_time += 4
                monkeyrunner_time += 10
            elif " ListSelectOp " in line:
                extra_time += 5
                monkeyrunner_time += 20
            elif " SetEditTextOp " in line:
                extra_time += 3
                monkeyrunner_time += 10
            elif " MoveSeekBarOp " in line:
                extra_time += 1
                monkeyrunner_time += 7
            elif " SetNumberPickerOp " in line:
                extra_time += 5
                monkeyrunner_time += 29
            elif " RotateOp " in line:
                monkeyrunner_time += 20
            elif " RollOp " in line:
                monkeyrunner_time += 10
            elif "Op " in line:
                monkeyrunner_time += 7
        elif "=== execution" in line and "finish" in line:
            verify_start = t
#        elif "successfully" in line:
            if start_ok and cleanup_start:
                exe_info = [execution_start, send_start, start_ok, cleanup_start, verify_start, extra_time, monkeyrunner_time]
                exes.append(exe_info)
        # cleanup phase
        elif "succ count" in line:
            final_start = t
        elif "activities entered /" in line:
            final_finish = t

        last_time = t

    if len(exes) <= 1:
        continue

    s = 0
    s2 = 0
    s3 = 0
    es = []
    exts = []
    monkeyrunners = []
    for exe in exes:
        exe_time = exe[3] - exe[2]
        ext_time = exe[5]
        monkeyrunner_time = exe[6]
        exe_time_sec = exe_time.total_seconds()
        es.append(exe_time_sec)
        exts.append(ext_time)
        monkeyrunners.append(monkeyrunner_time)

    es.sort()
    exts.sort()
    monkeyrunners.sort()
    cnt = 0
#    print es
#    if 'abs' in log_file:
#        cnt = 2
#        s = sum(es[:2])
#    else:
#        cnt = 2
#        s = sum(es[-2:])
#    for i in xrange(len(exes) / 10 * 0, max(len(exes) / 10 * 1, 1)):
    for i in xrange(len(exes) / 3 * 1, len(exes) / 3 * 2):
#    for i in xrange(len(exes)):
        cnt += 1
        s += es[i]
        s2 += exts[i]
        s3 += monkeyrunners[i]

    if cnt == 0:
        continue

    avg = s / cnt
    avg2 = s2 / cnt
    avg3 = s3 / cnt

#    print log_file
#    print "Start: ", start_time
#    print "Instance: ", mod_start - start_time
#    print "Build: ", inst_finish - mod_start
#    print "Wait ready: ", first_exe - inst_finish
#    print "avg exe time: ", avg
#    print "cleanup time: ", final_finish - final_start

#    case = os.path.splitext(os.path.basename(log_file))[0]
#    (type_, output, num) = case.split('_')
#
#    if not int(num) in data:
#        data[int(num)] = {}
#    data[int(num)][type_] = avg
#
    print log_file, avg, avg2, avg3

    cur_per = cur_log / len(args.log_files) * 100
    if int(cur_per) > last_per:
        print >>sys.stderr, "\b\b\b\b\b", int(cur_per),
        last_per = cur_per

    percent = float(avg) / float(avg+avg2) * 100
    pmonkey = float(avg) / float(avg+avg3) * 100

#    print >> sys.stderr, "%f" % percent

    tper += percent
    tmper += pmonkey
    tcnt += 1


#print data

#for key in data:
#    if 'faith' in data[key] and 'abs' in data[key]:
#        print "%s %r %r" % (key, data[key]['faith'], data[key]['abs'])

print >>sys.stderr, "%f" % (tper / tcnt)
print >>sys.stderr, "%f" % (tmper / tcnt)
