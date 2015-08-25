#!/usr/bin/env python
import web
import analyze
import argparse
import subprocess
import re
import os
import sys
import shutil
import json

urls = (
    '/', 'entry',
    '/get_log_list', 'log_list',
    '/get_bug_list', 'bug_list',
    '/get_bug', 'show_bug',
    '/reproduce_bug', 'reproduce_bug'
)

app = web.application(urls, globals())

logs = []
bugs = []
selfdir = os.path.dirname(os.path.abspath(__file__))

class entry:
    def GET(self):
        raise web.seeother('/static/ui.html');

class log_list:
    def GET(self):
        global logs
        logs = []
        result_id = "";
        result_name = "";
        print subprocess.check_output("pwd");
        for log_fn_line in subprocess.check_output(selfdir + "/web_ui_scan_logs", stderr=subprocess.STDOUT, shell=True).split("\n"):
            log_fn = log_fn_line.rstrip()
            result_id += str(len(logs)) + ','
            result_name += '"' + log_fn + '",'
            logs.append(log_fn)
        result = '{ "log_id" : [' + result_id.rstrip(',') + '], "name" : [' + result_name.rstrip(',') + '] }'
        return result;

class bug_list:
    def GET(self):
        global bugs
        global logs
        input_data = web.input();
        log_id = int(input_data.log_id);
        result_id = "";
        result_name = "";
        bugs = analyze.collect([ "static/" + logs[log_id] ]);
        result_id = "";
        result_name = "";
        for i in range(0, len(bugs)):
            bug = bugs[i]
            result_id += str(i) + ','
            if 'checker' in bug:
                result_name += '"CHK:' + bug['checker'] + '",'
            else:
                result_name += '"EX:' + bug['exception type'] + '",'
        result = '{ "bug_id" : [' + result_id.rstrip(',') + '], "name" : [' + result_name.rstrip(',') + '] }'
        return result;

class show_bug:
    def GET(self):
        global bugs
        input_data = web.input()
        bug = bugs[int(input_data.bug_id)]
        if 'checker' in bug:
            result_title = 'CHK:' + bug['checker']
        else:
            result_title = 'EX:' + bug['exception type']
        return json.dumps(bug)

class reproduce_bug:
    def GET(self):
        input_data = web.input()
        return subprocess.check_output(selfdir + "/web_ui_reproduce_bug " + input_data.bug_id + " " + input_data.command_log_name + " " + input_data.log_name, stderr=subprocess.STDOUT, shell=True)

if __name__ == "__main__":
    # argparser = argparse.ArgumentParser()
    # argparser.add_argument("log_list", metavar='logfile', type=str, help="file that lists all log files to collect")
    # args = argparser.parse_args()
    app.run();
    
