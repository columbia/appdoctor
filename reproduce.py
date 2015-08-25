#!/usr/bin/env python

import device
import apptest
import launch
import log
import argparse
import os
import config
import base64
import logging
import crawl
import replay
import traceback

#bugname = "oix1"
#bugname = "oi451"
#bugname = "oi403"
#bugname = "kpd130"
#bugname = "kpd61"
#bugname = "kpdx2"
#bugname = "cb166"
#bugname = "kpd250"
#bugname = "oi435"
#bugname = "testcase1"
#bugname = "kpd2"
#bugname = "oi439"
#bugname = "oi452"
#bugname = "oi207"
#bugname = "crawl"
#bugname = "manual"

LOG_FORMAT = "%(asctime)-15s [%(process)d] %(name)s:%(levelname)s %(message)s"
logging.basicConfig(level=logging.DEBUG, format=LOG_FORMAT)
logger = logging.getLogger("reproduce")

argparser = argparse.ArgumentParser()
argparser.add_argument("bugname",
        help="the bug you want to reproduce, or 'crawl' for random testing")
argparser.add_argument("-c", "--console-mode", help="no window mode",
        action="store_true")
argparser.add_argument("-l", "--show-logcat", help="show logcat in another window",
        action="store_true")
argparser.add_argument("-w", "--wait-before-exit", help="wait for key press before exit",
        action="store_true")
argparser.add_argument("-m", "--manual-mode", help="manual mode, wait for exit",
        action="store_true")
argparser.add_argument("-e", "--error-file", help="error report file name",
        type=str)
argparser.add_argument("-f", "--apk-file", help="apk of application to be tested",
        type=str)
argparser.add_argument("-a", "--activity", help="activity to start",
        type=str)
argparser.add_argument("-d", "--device-conf", help="device configuration",
        type=str)
argparser.add_argument("-s", "--skip-install", help="skip the install phase",
        action="store_true")
argparser.add_argument("-n", "--num-of-operations", help="max number of operations to crawl",
        type=int, default=1000)
argparser.add_argument("-r", "--replay-file", help="command log to replay",
        type=str)
argparser.add_argument("-i", "--interactive", help="interactive mode, wait for key press in replay",
        action="store_true")
argparser.add_argument("-b", "--batch-number", help="number of rounds to execute",
        type=int, default=1)
argparser.add_argument("-t", "--target", help="target we want to reproduce",
        type=str)
argparser.add_argument("-p", "--use-msg-proxy", help="use message proxy",
        type=str)
argparser.add_argument("-g", "--instrumenter-arguments", help="instrumenter arguments in 'am instrument'",
        type=str)
argparser.add_argument("-u", "--faithful", help="faithful replay events",
        action="store_true")
argparser.add_argument("-y", "--simplify-result", help="result file of simplification",
        type=str, default="simple_cmd_log")
argparser.add_argument("-o", "--cont-after-succ", help="continue after replay success",
        action="store_true")
argparser.add_argument("-x", "--cont-after-branch", help="continue after replay branched",
        action="store_true")
argparser.add_argument("-k", "--console-port", help="specifies console port. adb port = console + 1",
        type=int, default=0)
args = argparser.parse_args()

bugname = args.bugname
r_path = None
app_package = None
app_start_activity = None
skip_decode = False
skip_start = False
msg_proxy_mode = None

# mode-specific settings

if args.manual_mode:
    args.wait_before_exit = True
    skip_start = True

if bugname == "oi403":
    app_apk_path = 'ShoppingList-1.4.1.apk'
    app_package = 'org.openintents.shopping'
    app_start_activity = 'org.openintents.shopping.ShoppingActivity'
    dev = device.Device(version = '4.0')
    msg_proxy_mode = "record"
elif bugname == "kpdx2":
    app_apk_path = "KeePassDroid-1.8.6.apk"
    app_package = "com.android.keepass"
    app_start_activity = "com.android.keepass.KeePass"
    dev = device.Device(version = '2.1')
elif bugname == "oix1":
    app_apk_path = "FileManager-1.0.0.apk"
    app_package = "org.openintents.filemanager"
    app_start_activity = "org.openintents.filemanager.FileManagerActivity"
    dev = device.Device(version = '2.2', sdcard = None)
elif bugname == "oi451":
    app_apk_path = "FileManager-r3841.apk"
    app_package = "org.openintents.filemanager"
    app_start_activity = "org.openintents.filemanager.FileManagerActivity"
    dev = device.Device(version = '1.5')
elif bugname == "oi439":
    app_apk_path = "FileManager-r3764.apk"
    app_package = "org.openintents.filemanager"
    app_start_activity = "org.openintents.filemanager.FileManagerActivity"
    dev = device.Device(version = '2.1')
elif bugname == "kpd130":
    app_apk_path = "KeePassDroid-1.8.6.apk"
    app_package = "com.android.keepass"
    app_start_activity = "com.android.keepass.KeePass"
    dev = device.Device(version = '1.6')
elif bugname == "kpd61":
    app_apk_path = "KeePass-1.0.6.apk"
    app_package = "com.android.keepass"
    app_start_activity = "com.android.keepass.KeePass"
    dev = device.Device(version = '2.2', sdcard = None)
elif bugname == "cb166":
    app_apk_path = "ConnectBot-svn-r512-all.apk"
    app_package = "org.connectbot"
    app_start_activity = "org.connectbot.HostListActivity"
#    r_path = "cb_R.java"
    dev = device.Device(version = '4.0')
elif bugname == "kpd250":
    app_apk_path = "KeePass-1.9.6.apk"
    app_package = "com.android.keepass"
    app_start_activity = "com.android.keepass.KeePass"
    dev = device.Device(version = '2.3')
elif bugname == "oi435":
    app_apk_path = 'Notepad-1.2.3.apk'
    app_package = 'org.openintents.notepad'
    app_start_activity = 'org.openintents.notepad.noteslist.NotesList'
    dev = device.Device(version = '4.0')
elif bugname == "testcase1":
    app_apk_path = 'textoverlap.apk'
    app_package = 'com.example.textoverlaptest'
    app_start_activity = 'com.example.textoverlaptest.MainActivity'
    dev = device.Device(version = '4.0')
elif bugname == "kpd2":
    app_apk_path = "KeePassDroid-0.1.8.apk"
    app_package = "com.android.keepass"
    app_start_activity = "com.android.keepass.KeePass"
    dev = device.Device(version = '2.3')
elif bugname == "oi452":
    app_apk_path = 'ShoppingList-1.5.apk'
    app_package = 'org.openintents.shopping'
    app_start_activity = 'org.openintents.shopping.ShoppingActivity'
    dev = device.Device(version = '4.0')
elif bugname == "oi207":
    app_apk_path = 'Notepad-1.1.2.apk'
    app_package = 'org.openintents.notepad'
    app_start_activity = 'org.openintents.notepad.noteslist.NotesList'
    dev = device.Device(version = '4.0')
elif bugname == "crawl" or bugname == "replay" or bugname == "manual" or bugname == "search" or bugname == "simplify":
    app_apk_path = 'Notepad-1.1.2.apk'
#    app_package = 'org.openintents.notepad'
#    app_start_activity = 'org.openintents.notepad.noteslist.NotesList'
    if not args.device_conf:
        dev = device.Device(version = '4.2')
    skip_decode = True
    if bugname == "replay" or bugname == "simplify":
        args.wait_before_exit = True
        skip_start = True
        msg_proxy_mode = "replay"
    if bugname == "crawl" or bugname == "search":
        skip_start = True
        msg_proxy_mode = "record"
    if bugname == "manual":
        args.wait_before_exit = True
        if args.use_msg_proxy == "record":
            msg_proxy_mode = "record"
        else:
            msg_proxy_mode = "replay"
elif bugname == "kpd183":
    app_apk_path = "KeePassDroid-1.9.2.apk"
    app_package = "com.android.keepass"
    app_start_activity = "com.android.keepass.KeePass"
    dev = device.Device(version = '2.3')
elif bugname == "oi298":
    app_apk_path = "Safe-1.2.3.apk"
    app_package = "org.openintents.safe"
    app_start_activity = "org.openintents.safe.FrontDoor"
    dev = device.Device(version = '2.3')
elif bugname == "oi518":
    app_apk_path = 'Notepad-1.3.apk'
    app_package = 'org.openintents.notepad'
    app_start_activity = 'org.openintents.notepad.noteslist.NotesList'
    dev = device.Device(version = '2.2')

ins_path = None

if args.apk_file:
    app_apk_path = args.apk_file
if args.activity:
    app_start_activity = args.activity
if args.device_conf:
    conf = config.Config.decode(args.device_conf)
    if not args.skip_install:
        conf.extra_ident = None
    dev = conf.obtain_device()

if args.show_logcat:
    os.system("xterm -e \"adb -e logcat | less\" &")

activity_entered = set()
activity_created = set()
activity_total = set()

log.set_log_file(args.error_file)

if not args.use_msg_proxy:
    msg_proxy_mode = None

try:
    launcher = launch.Launch(dev, app_apk_path, app_package, app_start_activity, args.device_conf, msg_proxy_mode, args.instrumenter_arguments)
    config_path = launcher.prepare_to_run(args, skip_start)
    activity_total = launcher.get_total_activities()

    def event_handler(result, args):
        if result == "ActivityEnter":
            classname = args[0]
            logger.info("enter activity: %s" % classname)
            activity_entered.add(classname)
            activity_created.add(classname)
        elif result == "ActivityLeave":
            classname = args[0]
            logger.info("leave activity: %s" % classname)
        elif result == "ActivityCreate":
            classname = args[0]
            activity_created.add(classname)
        elif result == "ActivityStart":
            classname = args[0]
            activity_created.add(classname)
        elif result == "TriggerBroadcast":
            action = args[0]
            cmdline = base64.b64decode(args[1])
            logger.info("triggering broadcast %s: %s" % (action, cmdline))
            launcher.run_cmd(cmdline)
        else:
            logger.info("unknown event: %s %r" % (result, args))

    if args.manual_mode:
        apptest.init.init_testlib(app_apk_path, r_path, config_path, None, skip_decode, skip_start, event_handler)
        apptest.widget.read_hint(app_apk_path)
        if launcher.app_package in apptest.widget.hints:
            for hint in apptest.widget.hints[launcher.app_package]:
                hint.dump()
        print "instrumenter is listening on %d" % launcher.get_forwarded_port()
        print "press enter when done"
    elif bugname == "replay" or bugname == "simplify":
        apptest.init.init_testlib(app_apk_path, r_path, config_path, None, skip_decode, skip_start, event_handler)
        replayer = replay.Replayer(launcher, args.replay_file, args.interactive, args.target, args.batch_number, args.use_msg_proxy, args.faithful, args.cont_after_succ, args.cont_after_branch)
        if bugname == "replay":
            replayer.replay()
        else:
            replayer.simplify(args.simplify_result)
    else:
        def err_handler(exc):
            if (isinstance(exc, apptest.exception.RemoteException)
                    and "INJECT_EVENTS" in exc.msg):
                return
            if isinstance(exc, apptest.exception.StoppedException):
                return
            log.record_exception(dev, exc, ins_path, launcher, args.device_conf)

        logger.info("initialize test lib...")
        ins_path = apptest.init.init_testlib(app_apk_path, r_path, config_path, err_handler, skip_decode, skip_start, event_handler)
        apptest.interface.set_auto_wait(True)

        if bugname == "oi403":
            apptest.widget.click("button1")
            apptest.widget.click("button1")
        elif bugname == "oi451":
            pass
        elif bugname == "oi439":
            # long-click and press detail
            apptest.widget.click("button1")
            apptest.widget.click("button1")
            list_id = apptest.interface.get_view_by_class("ListView")
            child_id = apptest.interface.get_view_child(list_id, 0)
            apptest.interface.long_click(child_id)
            apptest.widget.select_dialog_click(3)
        elif bugname == "kpd130":
            apptest.widget.click("create")
            apptest.widget.set_text("pass_password", "pass")
            apptest.widget.set_text("pass_conf_password", "pass")
            apptest.widget.click("ok")
        elif bugname == "kpd61":
            apptest.widget.set_text("file_filename", "/data/data/com.android.keepass/keepass.kpd")
            apptest.widget.click("create")
            apptest.widget.set_text("pass_password", "pass")
            apptest.widget.set_text("pass_conf_password", "pass")
            apptest.widget.click("ok")
            apptest.menu.click_by_id(2)
            apptest.widget.set_text("search_src_text", "test")
            apptest.widget.enter("search_src_text", "\n")
        elif bugname == "oix1":
            pass
        elif bugname == "cb166":
            apptest.widget.click("action_next")
            apptest.widget.click("action_next")
            apptest.widget.click("action_next")
            apptest.widget.click("action_next")
            apptest.widget.click("transport_selection")
            apptest.widget.select_dialog_click(2)
            apptest.widget.enter("front_quickconnect", "test\n")
        elif bugname == "kpd250":
            pass
        elif bugname == "oi435":
            conn = apptest.connection.get_conn()
            conn.send_event("Crawl")
            conn.send_event("Crawl")
        elif bugname == "testcase1":
            print apptest.widget.get_text("textView1")
        elif bugname == "kpd2":
            pass
        elif bugname == "oi452":
            apptest.widget.click("button1") # Accept
            apptest.widget.click("button1") # Continue
            apptest.menu.click(3)
            list_id = apptest.widget.get_widget_id("list1")
            classic_id = apptest.interface.get_view_child(list_id, 1)
            apptest.interface.click(classic_id)
            apptest.widget.click("android:button1") # OK
        elif bugname == "oi207":
            apptest.widget.click("button1") # Accept
            # add a note
            apptest.menu.click_icon(0)
            apptest.widget.enter("note", "test")
            apptest.keyboard.press_back()
            apptest.keyboard.press_back()
            # click it
            child_id = apptest.widget.get_list_item_id("android:list", 0)
            apptest.interface.long_click(child_id)
            apptest.widget.select_dialog_click(0)
            apptest.interface.rotate()
        elif bugname == "crawl" or bugname == "search":
            logger.info("start crawler...")
            crawler = crawl.Crawler(args.batch_number, args.num_of_operations, launcher)
            try:
                while True:
                    try:
                        if bugname == "crawl":
                            crawler.crawl()
                        else:
                            crawler.search()
                        break
                    except Exception as e:
                        if isinstance(e, apptest.exception.StoppedException):
                            raise e
                        logger.info("got exception from crawler: %r" % e)
                        traceback.print_exc()
                        if crawler.should_restart():
                            logger.info("restarting emulator")
                            apptest.connection.close_conn()
                            apptest.init.load_config(launcher.restart_dev())
                        else:
                            logger.info("should not restart, give up")
                            break

            except apptest.exception.StoppedException:
                logger.info("got stop signal. stop")
        elif bugname == "manual":
            apptest.widget.read_hint(app_apk_path)
            apptest.widget.send_hint(launcher.app_package)
            launcher.notify_external_events()
            apptest.connection.set_timeout(None)
            num = 0
            while True:
                cmd = raw_input("> ")
                if cmd == "exit":
                    break

                cmd = cmd.strip()
                num += 1
                logger.debug("run cmd %d: %s" % (num, cmd))
                try:
                    apptest.interface.dump_views(wait = False)
                    event_id = apptest.connection.get_conn().send_event(cmd)
                    result = apptest.connection.get_conn().wait_result(event_id)
                    logger.info("id: %r result: %r" % (event_id, result))
                    if cmd == "Collect":
                        for i in range(len(result) - 2):
                            op = result[2+i]
                            print "Available operation %d: %s" % (i, base64.b64decode(op))
                except Exception as e:
                    logger.error("got exception: %r" % e)
                    if isinstance(e, apptest.exception.ConnectionBroken):
                        logger.error("connection broken")
                        break

        elif bugname == "kpd183":
            raw_input()

    if args.wait_before_exit:
        raw_input("press enter to exit")
except apptest.exception.ConnectionBroken as e:
    logger.error("connection broken: %r" % e)
    log.record_exception(dev, e, ins_path, launcher, args.device_conf)

except Exception as e:
    logger.error("exception occoured: %r" % e)
    traceback.print_exc()
finally:
    #apptest.init.finish()

    dev.get_recent_logcat()
    launcher.cleanup()
    dev.cleanup()

    logger.info("=== activities created / total: %d/%d" % (len(activity_created), len(activity_total)))
    logger.info("=== activities created:")
    for act in activity_created:
        logger.info("=== - %s" % act)

    logger.info("=== all activities:")
    for act in activity_total:
        created = False
        for act_ent in activity_created:
            if act_ent.endswith(act):
                created = True
                break
        if created:
            logger.info("=== - %s" % act)
        else:
            logger.info("=== - %s (not created)" % act)
