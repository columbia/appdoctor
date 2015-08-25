import socket
import config
import logging
import base64
import time
import threading
from exception import *

logger = logging.getLogger("conn")

err_handler = None
event_handler = None
inst_conn = None
CONNECT_TIMEOUT = 30000
CONNECT_INTERVAL = 5
CONNECT_ATTEMPT = 6
READ_TIMEOUT = 300 # 60 is not enough
results = {}

def get_conn():
    global inst_conn
    if (inst_conn is None):
        inst_conn = Connection(config.get_str('conn', 'server'), config.get_str('conn', 'port'))
    return inst_conn

def close_conn():
    global inst_conn
    if inst_conn is not None:
        inst_conn.close()
    inst_conn = None
    results.clear()

def set_timeout(timeout):
    global inst_conn
    if inst_conn is not None:
        inst_conn.set_timeout(timeout)

use_receiver = True

class Receiver(threading.Thread):
    def __init__(self, conn):
        threading.Thread.__init__(self)
        self.conn = conn
        self.used = threading.Semaphore(0)
        self.running = True

    def run(self):
        while True:
            try:
                self.conn.process_input()
            except ConnectionBroken as e:
                self.conn.set_exc(e)
                break
            except Exception as e:
                self.conn.set_exc(e)
            self.used.release()
        logger.info("connection closed. Receiver exit")
        self.running = False
        self.used.release()

class Connection(object):
    def __init__(self, server, port):
        logger.info("connecting to %r:%r" % (server, port))
        for i in range(CONNECT_ATTEMPT):
            try:
                self._conn = socket.create_connection((server, port), CONNECT_TIMEOUT)
                break
            except socket.error as e:
                if e.errno == 61: # connection reset, maybe not started
                    time.sleep(CONNECT_INTERVAL)
                else:
                    raise e

        self._conn.settimeout(READ_TIMEOUT)
        self._event_count = 0
        self.exc = None
        if use_receiver:
            self.receiver = Receiver(self)
            self.receiver.start()

    def send_line(self, line):
        try:
            self._conn.send(line)
            self._conn.send("\n")
        except Exception as e:
            raise ConnectionBroken("connection broken during send_line, exception got: %r" % e)

    def send_event(self, event_name, *args):
        event_id = self._event_count
        logger.info("sending event %d name %s args %r" % (event_id, event_name, args))
        self._event_count += 1
        try:
            self._conn.send("%d %s" % (event_id, event_name))
            for arg in args:
                self._conn.send(' ')
                self._conn.send(str(arg))
            self._conn.send('\n')
        except Exception as e:
            raise ConnectionBroken("connection broken during send_event, exception got: %r" % e)
        return str(event_id)

    def send_event_sync(self, event_name, *args):
        event_id = self.send_event(event_name, *args)
        return self.wait_result(event_id)

    def set_exc(self, e):
        self.exc = e

    def wait_for_input(self):
        self.receiver.used.acquire()
        if self.exc:
            exc = self.exc
            self.exc = None
            self.receiver.used.release()
            raise exc
        if not self.receiver.running:
            self.receiver.used.release()
            raise ConnectionBroken("connection has already broken in wait_for_input(), reset")

    def wait_result(self, event_id):
        logger.info("waiting result for event %r" % event_id)
        global results
        while (not event_id in results):
            if use_receiver:
                self.wait_for_input()
            else:
                self.process_input()
        result = results[event_id]
        del results[event_id]
        return result

    def del_result(self, event_id):
        if event_id in results:
            del results[event_id]

    def process_input(self):
        global results
        line = readline(self._conn)
        logger.debug("got a new line: %s" % line)
        parts = line.split(' ')
        if (len(parts) < 2):
            raise MalformedResult("can't understand result: too few parts")
        event_id = parts[0]
        result = parts[1]
        other_result = parts[2:]
        if event_id == "__Event__":
            if event_handler:
                event_handler(result, other_result)
            return False
        try:
            if result == "ACIException":
                raise InstException(other_result)
            if result == "Exception":
                raise RemoteException(other_result[0], base64.b64decode(other_result[1]))
            if result != "OK":
                raise ActionFailure(event_id, result)
        except Exception as e:
            if isinstance(e, RemoteException):
                raise e
            if err_handler and (event_id == "__Exception__" or event_id == "-"):
                err_handler(e)
            else:
                raise e
        results[event_id] = other_result
        return True

    def close(self):
        try:
            self._conn.close()
        except:
            pass

    def set_timeout(self, timeout):
        self._conn.settimeout(timeout)

def readline(conn):
    line = ''
    while (True):
        try:
            ch = conn.recv(1)
        except Exception as e:
            raise ConnectionBroken("connection broken, exception got: %r" % e)
        if (not ch):
            raise ConnectionBroken("connection broken. possibly got uncaught exception")
        if (ch == '\n'):
            return line
        line += ch

