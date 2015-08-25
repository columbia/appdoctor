#!/usr/bin/env python

import subprocess
import sys

clsname = sys.argv[1]

ret = []
try:
    ret += subprocess.check_output("grep -r %s * | grep const-class" % clsname, shell = True).split('\n')
except:
    pass

try:
    ret += subprocess.check_output("grep -r '%s;-' * | grep init" % clsname, shell = True).split('\n')
except:
    pass

try:
    ret += subprocess.check_output("grep -r '%s\"' --exclude AndroidManifest.xml *" % clsname, shell = True).split('\n')
except:
    pass

for line in ret:
    if ':' in line:
        print line.split(':')[0].split('.')[0].split('/', 1)[1]

#if p:
#    print p
#if q:
#    print q
