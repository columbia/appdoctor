import threading
import subprocess
import logging
import traceback
import Queue

logger = logging.getLogger("jobs")

class Watchman(threading.Thread):
    def __init__(self, jobs, job, slot):
        threading.Thread.__init__(self)
        self.job = job
        self.slot = slot
        self.cmd = job.format(slot)
        self.jobs = jobs

    def run(self):
        logger.debug("running subprocess: %s" % self.cmd)
        try:
            retcode = subprocess.call(self.cmd, shell=True, close_fds=True)
        except:
            logger.error("exception caught when calling subprocess")
            logger.error(traceback.format_exc())

        logger.debug("ret code: %d" % retcode)
        self.jobs.slots_lock.acquire()
        self.jobs.results[self.slot] = retcode
        self.jobs.slots.append(self.slot)
        self.jobs.slots_lock.release()
        self.jobs.remain.release()
        logger.debug("slot %d released" % self.slot)
        self.jobs.queue.task_done()

class JobAssigner(threading.Thread):
    def __init__(self, jobs):
        threading.Thread.__init__(self)
        self.jobs = jobs

    def run(self):
        logger.info("job assigner start")
        while True:
            self.jobs.todo.acquire()
            if self.jobs.stopped:
                return
            job = self.jobs.queue.get()
            logger.info("job picked up: %s" % job)

            if job == "DONE":
                logger.info("got end-of-jobs job, stop")
                break

            self.jobs.remain.acquire()
            self.jobs.slots_lock.acquire()
            slot = self.jobs.slots.pop()
            self.jobs.slots_lock.release()
            logger.info("slot picked up: %d" % slot)

            watchman = Watchman(self.jobs, job, slot)
            watchman.start()
        logger.info("job assigner stop")

class Jobs:
    def __init__(self, slot_count):
        self.slot_count = slot_count
        self.remain = threading.Semaphore(slot_count)
        self.todo = threading.Semaphore(0)
        self.queue = Queue.Queue()
        self.results = {}
        self.slots = range(slot_count)
        self.slots_lock = threading.Lock()
        self.stopped = False
        self.assigner = JobAssigner(self)
        self.assigner.start()

    def launch_job(self, cmd):
        self.queue.put(cmd)
        self.todo.release()
        logger.info("job added: %s" % cmd)

    def stop(self):
        self.stopped = True
        self.todo.release()

    def stop_when_done(self):
        self.launch_job("DONE")

