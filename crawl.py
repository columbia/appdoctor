#!/usr/bin/env python
import collections, logging
import Queue
import flags
import apptest
import executor
import traceback
import random

logger = logging.getLogger("reproduce")

RESTART_LIMIT = 0.2
TARGET_RESTART_LIMIT = 3

class Step(object):
    def __init__(self, state_sig, op, op_desc):
        self.state_sig = state_sig
        self.op = op
        self.op_desc = op_desc

class Target(object):

    count = 0

    def __init__(self):
        self.steps = []
        # random crawl
        self.priority = random.randrange(100)

    def add_step(self, step):
        self.steps.append(step)

    def get_depth(self):
        return len(self.steps)

    def get_priority(self):
        # Target.count = Target.count + 1
        # return Target.count
        # BFS
        # return self.get_depth()
        return self.priority


class Crawler(object):
    def __init__(self, batch_number, num_of_operations, launcher):
        self.batch_number = batch_number
        self.num_of_operations = num_of_operations
        self.launcher = launcher
        self.states = {}
        self.target_queue = Queue.PriorityQueue()
        self.cur_steps = []
        self.batch_no = -1
        self.replay_fail_count = 0
        self.target_restart_count = 0
        self.curr_target = None
        self.restart_count = 0
        self.executor = executor.Executor(launcher)
        self.executor.setup_err_handler()

    def find_state(self, state):
        state_sig = self.executor.get_sig(state)
        if state_sig in self.states:
            return self.states[state_sig]
        else:
            return None

    def target_visited(self, target):
        if target.get_depth() == 0:
            return False
        step = target.steps[-1]
        if step.state_sig in self.states:
            state_info = self.states[step.state_sig]
            if step.op >= len(state_info[2]):
                return True
            if not state_info[2][step.op]:
                state_info[2][step.op] = True
                return False
            else:
                return True
        else:
            return True

    def pick_target(self):
        if self.target_queue.qsize() == 0:
            return None
        (pri, target) = self.target_queue.get()
        while self.target_visited(target):
            if self.target_queue.qsize() == 0:
                return None
            (pri, target) = self.target_queue.get();

        logger.debug("pick target with priority %d and depth %d" % (pri, target.get_depth()))
        return target

    def perform_op(self, op):
        self.record_op(self.curr_state, self.curr_ops, op)
        try:
            flags.detect_stop()
            apptest.interface.select(op)
        except apptest.exception.RemoteException as e:
            if "INJECT_EVENT" in e.msg:
                pass
            else:
                raise e
        except apptest.exception.InstException as e:
            self.executor.err_handler(e)
        self.curr_state, self.curr_ops = self.executor.collect()

    def select_op(self, state, ops):
        if len(ops) == 0:
            return None
        state_info = self.find_state(self.curr_state)
        if state_info is None:
            # new state
            self.new_state(state, ops, [0])
            op = 0
            if len(ops) > 1:
                logger.debug("new state. added %d states into queue" % (len(ops) - 1))
                for i in xrange(1, len(ops)):
                    self.new_target(state, ops, i)
        else:
            logger.debug("state already visited.")
            op = -1
            for i in xrange(len(state_info[2])):
                if not state_info[2][i]:
                    state_info[2][i] = True
                    op = i
                    break

            if op == -1:
                logger.debug("all ops have been done")
                return None

        return op

    def new_state(self, state, ops, done = []):
        state_sig = self.executor.get_sig(state)
        self.states[state_sig] = (state, ops, [False] * len(ops))
        for op in done:
            self.states[state_sig][2][op] = True

    def new_target(self, state, ops, op):
        target = Target()

        for step in self.cur_steps:
            target.add_step(step)

        step = Step(self.executor.get_sig(state), op, ops[op])
        target.add_step(step)

        self.target_queue.put((target.get_priority(), target))

    def record_op(self, state, ops, op):
        step = Step(self.executor.get_sig(state), op, ops[op])
        self.cur_steps.append(step)

    def clear_history(self):
        self.cur_steps = []

    def crawl(self):
        try:
            apptest.widget.read_hint(self.launcher.app_apk_path)
        except Exception as e:
            logger.error("fail to read hints: %r" % e)
            return
        while self.batch_no + 1 < self.batch_number:
            self.batch_no += 1
            logger.info("=== execution %d started..." % self.batch_no)
            flags.detect_stop()
            hit_nothing = False
            try:
                self.executor.restart()
                apptest.interface.disable_checker()
                apptest.widget.send_hint(self.launcher.app_package)
                self.launcher.notify_external_events()
                for op_no in xrange(self.num_of_operations):
                    try:
                        flags.detect_stop()
                        apptest.interface.crawl()
                        self.launcher.test_all_config()
                        hit_nothing = False
                    except apptest.exception.RemoteException as e:
                        if "INJECT_EVENT" in e.msg:
                            pass
                        else:
                            raise e
                    except apptest.exception.InstException as e:
                        self.executor.err_handler(e)
                    except apptest.exception.ActionFailure as e:
                        if e.result == 'NothingToDo':
                            if hit_nothing:
                                break
                            else:
                                hit_nothing = True
                                self.launcher.dev.execute("input keyevent 4")
                                apptest.interface.wait_for_idle()
                        else:
                            self.executor.err_handler(e)

            except apptest.exception.StoppedException:
                logger.info("got stop signal. stop")
                break
            except apptest.exception.ConnectionBroken as e:
                if "timeout" in e.message:
                    raise e
                if not self.launcher.dev_running():
                    raise e
                self.executor.err_handler(e)
            except apptest.exception.TimeoutException as e:
                raise e
            except Exception as e:
                self.executor.err_handler(e)
                logger.info("got exception, check if we should restart")
                if not self.launcher.dev_running():
                    logger.info("should restart!")
                    raise e
            if self.batch_no != self.batch_number - 1:
                self.executor.clean_state()
            
            logger.info("=== execution %d finished..." % self.batch_no)
            if not self.launcher.dev_running():
                raise apptest.exception.InternalError("device not running!")

    def resolve_op(self, op, op_desc, curr_ops):
        if op < len(curr_ops):
            if curr_ops[op] == op_desc:
                return op
        similar_op = None
        for i in xrange(len(curr_ops)):
            if curr_ops[i] == op_desc:
                return i
            if self.executor.similar(op_desc, curr_ops[i]):
                similar_op = i
        return similar_op

    def search(self):
        try:
            apptest.widget.read_hint(self.launcher.app_apk_path)
        except Exception as e:
            logger.error("fail to read hints: %r" % e)
            return
        if self.batch_no == -1:
            init_target = Target()
            self.target_queue.put((init_target.get_priority(), init_target))
        while self.batch_no + 1 < self.batch_number:
            self.batch_no += 1
            logger.info("=== execution %d started..." % self.batch_no)
            flags.detect_stop()
            if self.curr_target is not None:
                self.target_restart_count += 1
                if self.target_restart_count > TARGET_RESTART_LIMIT:
                    self.curr_target = None
                    self.target_restart_count = 0

            if self.curr_target is None:
                target = self.pick_target()
            else:
                target = self.curr_target

            if target is None:
                logger.info("=== state queue empty. finish. ===")
                break

            self.clear_history()
            try:
                self.executor.restart()
                apptest.interface.disable_checker()
                apptest.widget.send_hint(self.launcher.app_package)
                self.launcher.notify_external_events()

                self.curr_state, self.curr_ops = self.executor.collect()

                branched = False
                logger.info("replay start")
                for step in target.steps:
#                    if self.executor.state_match(self.curr_state, step.state_sig) and step.op < len(self.curr_ops):
#                        logger.debug("replay op %r: %s" % (step.op, step.op_desc))
#                        self.perform_op(step.op)
                    realop = self.resolve_op(step.op, step.op_desc, self.curr_ops)
                    if realop is not None:
                        self.perform_op(realop)
                    else:
                        # replay failed!
                        # state branch!
                        logger.error("replay failed. fallback to exploration.")
                        self.replay_fail_count += 1
                        branched = True
                        break

                if not branched:
                    logger.info("replay finished. start exploration")
                for op_no in xrange(self.num_of_operations):
                    op = self.select_op(self.curr_state, self.curr_ops)
                    if op is None:
                        logger.info("nothing to do.")
                        break
                    logger.debug("selected op %r: %s" % (op, self.curr_ops[op]))
                    self.perform_op(op)

            except apptest.exception.ConnectionBroken as e:
                logger.info("connection broken: %r" % e)
                if "timeout" in e.message:
                    raise e
                self.executor.err_handler(e)
                if not self.launcher.dev_running():
                    raise e
            except apptest.exception.StoppedException as e:
                raise e
            except Exception as e:
                logger.error("unknown exception: %r" % e)
                traceback.print_exc()
                self.executor.err_handler(e)
                if not self.launcher.dev_running():
                    raise e

            if self.batch_no != self.batch_number - 1:
                self.executor.clean_state()

            logger.info("=== execution %d finished..." % self.batch_no)
            logger.info("=== state count: %5d" % len(self.states))
            logger.info("=== queue len:   %5d" % self.target_queue.qsize())
            logger.info("=== op count:    %5d" % len(self.cur_steps))
            logger.info("=== replay fail: %5d" % self.replay_fail_count)
            # if self.target_queue:
            #     logger.info("=== first target depth: %5d" % self.target_queue[0].get_depth())

            if not self.launcher.dev_running():
                raise apptest.exception.InternalError("device not running!")

            self.curr_target = None

    def should_restart(self):
        self.restart_count += 1
        if self.restart_count > RESTART_LIMIT * self.batch_number:
            return False
        return self.batch_no + 1 < self.batch_number
