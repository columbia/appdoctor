import apptest
import base64
import hashlib
import logging
import log

logger = logging.getLogger("executor")

class Executor(object):
    def __init__(self, launcher):
        self.launcher = launcher
        self.ins_path = None

    def collect(self):
        results = apptest.interface.collect()
        state = base64.b64decode(results[0])
        op_count = int(results[1])
        ops = []
        for i in xrange(op_count):
            op_val = base64.b64decode(results[2+i])
            ops.append(op_val)

        return (state + ':' + str(op_count), ops)

    def get_sig(self, data):
        m = hashlib.md5()
        m.update(data)
        return m.digest()

    def state_match(self, x, y_sig):
        x_sig = self.get_sig(x)
        return x_sig == y_sig

    def clean_state(self):
        try:
            try:
                apptest.interface.finish()
            except:
                pass
            self.record_exception(apptest.exception.RemoteException("__DUMMY__", "Record coverage data"))
            apptest.interface.close()
            self.launcher.clear_app_data()
        except Exception as e:
            logger.warn("cleanup error: %r" % e)

    def restart(self):
        self.launcher.restart()
        self.start()

    def err_handler(self, exc):
        if (isinstance(exc, apptest.exception.RemoteException)
                and "INJECT_EVENT" in exc.msg):
            return
        if isinstance(exc, apptest.exception.StoppedException):
            return
        self.record_exception(exc)

    def setup_err_handler(self):
        apptest.init.set_err_handler(self.err_handler)

    def record_exception(self, e):
        log.record_exception(self.launcher.dev, e, self.ins_path, self.launcher, self.launcher.device_conf)

    def start(self):
        self.ins_path = apptest.interface.start()

    def similar(self, op1, op2):
        p1 = op1.split(',')
        p2 = op2.split(',')
        if len(p1) != len(p2):
            return False
        diff = 0
        for i in xrange(len(p1)):
            if p1[i] != p2[i]:
                diff += 1
        if diff <= 1:
            return True
        else:
            return False

