import base64

class WidgetNotFound(Exception):
    pass

class DecodeFailure(Exception):
    pass

class InternalError(Exception):
    pass

class ExternalError(Exception):
    pass

class MalformedResult(Exception):
    pass

class ActionFailure(Exception):
    def __init__(self, event_id, result):
        self.event_id = event_id
        self.result = result
        Exception.__init__(self, "result is not OK for event %s: %s" % (event_id, result))

class RemoteException(Exception):
    def __init__(self, classname, message):
        Exception.__init__(self, "%s: %s" % (classname, message))
        self.classname = classname
        self.msg = message

class InstException(Exception):
    def __init__(self, args):
        self.level = args[0]
        self.token = args[1]
        self.msg = base64.b64decode(args[2])
        self.file_cnt = args[3]
        self.files = []
        for file_no in range(int(self.file_cnt)):
            f = base64.b64decode(args[4+file_no])
            self.files.append(f)

        Exception.__init__(self, "Instrumentation Exception: %s [level %s,token %s,file count %s]"
                % (self.msg, self.level, self.token, self.file_cnt))

class ConnectionBroken(Exception):
    pass

class StoppedException(Exception):
    pass

class TimeoutException(Exception):
    pass
