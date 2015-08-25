import os
import time
import errno
from apptest.exception import *

base_path = os.path.dirname(os.path.realpath(__file__))

def stopflag_set():
    return os.path.exists(os.path.expanduser("~/andchecker-stop"))

def detect_stop():
    if stopflag_set():
        raise StoppedException("stopped by user")

def grab_lock(path):
    while True:
        try:
            detect_stop()
            lockf = os.open(path, os.O_CREAT | os.O_EXCL)
            return lockf
        except OSError as e:
            if e.errno == errno.EEXIST:
                time.sleep(1)
            else:
                raise e

def release_lock(path, lockf):
    if lockf:
        os.close(lockf)
    os.remove(path)

def get_base_path(fname):
    return os.path.join(base_path, fname)

def make_dir(path):
    if not os.path.exists(path):
        try:
            os.makedirs(path)
        except:
            pass

