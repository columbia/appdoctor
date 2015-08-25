import apptest
import logging
import buglib
import log
import executor
import time
import copy

logger = logging.getLogger("replay")

class Event(object):
    def __init__(self, line, may_remove):
        self.lines = [line]
        self.may_remove = may_remove
        self.ops = []
        self.op = None
        self.op_id = -1
        self.acts = set()
        self.fake = False

    def add_line(self, line):
        self.lines.append(line)

    def get_fake_clone(self):
        result = copy.copy(self)
        result.fake = True
        return result

    def dump(self):
        logger.debug("=== event begin ===")
        for line in self.lines:
            if self.fake:
                logger.debug("FAKE" + line)
            else:
                logger.debug(line)

    def set_fake(self, fake):
        self.fake = fake

    def is_fake(self):
        return self.fake

    def need_fake(self):
        if not self.op:
            return True
        if ("MoveSeekBar" in self.op or "SetNumberPicker" in self.op
                or "SetEditText" in self.op or "EnterText" in self.op
                or "ListSelect" in self.op or "ListScroll" in self.op):
            return True
        return False

    def get_desc(self):
        return "event(%d): %s %r" % (len(self.lines), self.lines[0].split(' ', 2)[1], self.op)

    def write_to(self, f):
        for line in self.lines:
            if self.fake:
                f.write("FAKE");
            f.write(line)
            f.write("\n")

    def can_perform(self, event):
        return event.op in self.ops

    def has_op(self):
        return self.op is not None

    def same_ops(self, other):
        if len(self.ops) != len(other.ops):
            return False
        for i in xrange(len(self.ops)):
            if self.ops[i] != other.ops[i]:
                return False
        return True

    def non_trivial(self):
        if not self.op:
            return False
        if (self.op.startswith("KeyPress(") or self.op.startswith("PauseAndResume")
            or self.op.startswith("StopAndRestart") or self.op.startswith("Relaunch")
            or self.op.startswith("Rotate") or self.op.startswith("Roll")):
                return False
        return True

    def append_to(self, lines):
        if not self.is_fake():
            lines.extend(self.lines)
        else:
            for line in self.lines:
                lines.append("FAKE" + line)

    def depend_on_act(self):
        if not self.op:
            return False
        if self.op.startswith("KeyPress("):
            if self.op.endswith("(KEYCODE_SEARCH)") or self.op.endswith("(KEYCODE_MENU)"):
                return True
        return False

    def depend_on_interface(self):
        if not self.op:
            return False
        if self.op.startswith("KeyPress(") and self.op.endswith("(KEYCODE_MENU)"):
            return True
        return False

    def realize(self):
        cmd_id = None
        cmd = None
        for line in self.lines:
            info = line.split(' ', 2)
            if line.startswith('#'):
                if info[0] == '#RESP#':
                    event_id = info[1]
                    if event_id == cmd_id:
                        if cmd == "Crawl":
                            parts = line.split(' ')
                            result = parts[2]
                            if result == "OK":
                                if parts[3] == "Replaying":
                                    self.ops = parts[4:]
                                    self.op = None
                                else:
                                    self.ops = parts[7:]
                                    self.op = parts[6]
                            else:
                                self.ops = []
                                self.op = ""
                        elif cmd == "Select":
                            parts = line.split(' ')
                            op = parts[3]
                            if not self.op:
                                # replaying?
                                self.op = op
                            elif self.op != op:
                                # also unlikely
                                logger.warn("select's op %s does not match crawl's %s" % (self.op, op))
            else:
                cmd_id = info[0]
                cmd = info[1]

    def parse_act_info(self, current):
        self.act = copy.copy(current)
        for line in self.lines:
            if line.startswith('#'):
                eventid = line.split(' ', 2)[1]
                if eventid == "__Event__":
                    event_name = line.split(' ', 3)[2]
                    if event_name == "ActivityEnter":
                        act = line.split(' ')[3]
                        self.act.add(act)
                    elif event_name == "ActivityLeave":
                        act = line.split(' ')[3]
                        if act in self.act:
                            self.act.remove(act)
                        else:
                            logger.warn("inconsistent activity info: can't find activity %s" % act)
        return self.act

    def get_curr_acts(self):
        return self.act

    def get_deps(self, cur_event_id, events, ignore_state):
        if not self.has_op():
            return []
        logger.debug("event: %s" % self.op)
        if self.non_trivial() and ignore_state:
            for i in xrange(cur_event_id):
                if events[i].can_perform(self):
                    logger.debug("event %d depend on: %d" % (cur_event_id, i-1))
                    return [events[i-1]]
        else:
            if self.depend_on_act() or not ignore_state:
                for i in xrange(cur_event_id):
                    if events[i].get_curr_acts() == self.get_curr_acts():
                        if self.depend_on_interface() or not ignore_state:
                            if events[i].same_ops(self):
                                logger.debug("event %d depend on: %d" % (cur_event_id, i-1))
                                return [events[i-1]]
                            else:
                                continue
                        logger.debug("event %d depend on: %d" % (cur_event_id, i))
                        return [events[i]]
        return [events[cur_event_id - 1]]

class Replayer(object):
    def __init__(self, launcher, replay_file, interactive, target, retry_count, msg_log, faithful, cont_after_succ, cont_after_branch):
        self.launcher = launcher
        self.replay_file = replay_file
        self.interactive = interactive
        self.target = target
        self.states = {}
        self.retry_count = retry_count
        self.executor = executor.Executor(launcher)
        self.executor.setup_err_handler()
        self.msg_log = msg_log
        self.faithful = faithful
        self.ops = []
        self.cont_after_succ = cont_after_succ
        self.cont_after_branch = cont_after_branch

    def replay(self):
        lines = open(self.replay_file).read().strip().split('\n')
        succ_cnt = 0
        replay_cnt = 0

        for i in xrange(self.retry_count):
            logger.info("=== try No. %d ===" % i)
            log.clear()
            self.replay_lines(lines)
            replay_cnt += 1

            if self.target:
                target_buginfo = self.get_target_info()
                logger.info("target bug to replay: " + buglib.get_bug_info(target_buginfo))
                if self.check_target(target_buginfo):
                    logger.info("successfully replayed the bug at retry %d" % i)
                    succ_cnt += 1
                    if not self.cont_after_succ:
                        break
                else:
                    logger.info("failed to replay the bug")
            else:
                logger.info("no replay target specified. maybe succeeded")
                break

        logger.info("=== succ count / replay count / retry count: %d/%d/%d" % (succ_cnt, replay_cnt, self.retry_count))

    def start_replay(self):
        self.launcher.restart()
        if self.msg_log:
            self.launcher.push_file(self.msg_log, "/data/data/%s/__instrument_msg_log" % self.launcher.app_package)
        apptest.widget.read_hint(self.launcher.app_apk_path)

    def replay_line_with_eventid(self, line, event_id, fake):
#                apptest.interface.dump_views()
        if fake:
            line = "FAKE" + line
            event_id = "FAKE" + event_id
        apptest.connection.get_conn().del_result(event_id)
        apptest.connection.get_conn().send_line(line)
        return apptest.connection.get_conn().wait_result(event_id)

    def compare_ops(self):
        if len(self.oldops) != len(self.ops):
            logger.warn("POSSIBLE BRANCH: %d -> %d", len(self.oldops), len(self.ops))
            self.ops_diff = True
            return False;
        else:
            for i in xrange(len(self.ops)):
                if self.ops[i] != self.oldops[i]:
                    logger.warn("POSSIBLE BRANCH: [%d] %s -> %s", i, self.oldops[i], self.ops[i])
                    self.ops_diff = True
                    return False;
        self.ops_diff = False
        return True

    def crawl_again(self):
        apptest.interface.wait_for_idle()
        (state, self.ops) = self.executor.collect()

    def replay_line(self, line, resp = None, fake = False):
        if line.startswith('#'):
            return True
        event_id = line.split(' ', 1)[0]
        cmd = line.split(' ', 2)[1]
        try:
            try:
                if cmd == "Start":
                    self.executor.start()
                    apptest.widget.send_hint(self.launcher.app_package, True)
                    apptest.connection.get_conn().send_line("- EnterReplay")
                    apptest.connection.get_conn().send_line("- BeFaithful %r" % self.faithful)
    #               apptest.connection.get_conn().send_line("- EnableChecker")
                elif cmd == "Crawl":
                    result = self.replay_line_with_eventid(line, event_id, fake)
                    self.ops = result[1:]
                    self.ops_diff = False
                    if resp:
                        parts = resp.split(' ')
                        if parts[1] == "OK":
                            if parts[2] == "Replaying":
                                self.oldops = parts[3:]
                            else:
                                self.oldops = resp.split(' ')[6:]
                        else:
                            self.oldops = []
                        self.compare_ops()
                elif cmd == "Select":
                    oldsel = int(line.split(' ', 2)[2])
    #                if oldsel == 0 and self.ops_diff:
    #                    for i in xrange(5):
    #                        self.crawl_again()
    #                        self.compare_ops()
    #                        if not self.ops_diff:
    #                            break

                    if resp and not fake:
                        # for SetEditText, they mismatch
    #                    oldop = resp.split(' ', 2)[2]
                        oldop = self.oldops[oldsel]
                        if oldsel >= len(self.ops):
                            logger.warn("branched: out of range: %d < %d", oldsel, len(self.ops))
                            branched = True
                        else:
                            newop = self.ops[oldsel]
                            if newop != oldop:
                                logger.warn("branched: op mismatch: %s -> %s" % (oldop, newop))
                                branched = True
                            else:
                                logger.debug("not branched")
                                branched = False

                        if branched:
                            for i in xrange(len(self.ops)):
                                if self.ops[i] == oldop:
                                    logger.debug("branch recovered: new pos %d" % i)
                                    line = "%s Select %d" % (event_id, i)
                                    branched = False
                                    break
                            if branched:
                                for i in xrange(len(self.ops)):
                                    if self.executor.similar(self.ops[i], oldop):
                                        logger.debug("almost recovered: new pos %d" % i)
                                        line = "%s Select %d" % (event_id, i)
                                        branched = False
                                        break
                            if branched:
                                logger.error("can't find operation %s" % oldop)
                                for i in xrange(5):
                                    time.sleep(0.2)
                                    self.crawl_again()
                                    for j in xrange(len(self.ops)):
                                        if self.ops[j] == oldop:
                                            logger.debug("branch recovered: new pos %d" % j)
                                            line = "%s Select %d" % (event_id, j)
                                            branched = False
                                            break
                                if branched and not self.cont_after_branch:
                                    raise apptest.exception.ActionFailure(event_id, "Branched")
                    if self.faithful and not fake:
                        if oldsel == 3:
                            # PauseAndResume
                            self.launcher.dev.execute("am start -n com.android.settings/.Settings")
                            time.sleep(1)
                            self.launcher.dev.execute("input keyevent 4")
                            time.sleep(5)
                        elif oldsel == 4:
                            # StopAndRestart
                            self.launcher.dev.execute("am start -n com.android.settings/.Settings")
                            time.sleep(10)
                            self.launcher.dev.execute("input keyevent 4")
                            time.sleep(5)
                    self.replay_line_with_eventid(line, event_id, fake)
                else:
                    self.replay_line_with_eventid(line, event_id, fake)
                self.hit_nothing = False
            except apptest.exception.ActionFailure as ex:
                logger.error("got exception: %r" % ex)
                if "NothingToDo" == ex.result:
                    if self.hit_nothing:
                        logger.error("application exited unexceptedly")
                        return False
                    else:
                        self.hit_nothing = True
                        self.launcher.dev.execute("input keyevent 4")
                        apptest.interface.wait_for_idle()
                elif "Branched" == ex.result or "OutOfRange" == ex.result:
                    if not self.cont_after_branch:
                        logger.error("replay branched: %s. abort" % ex.result)
                        return False
                else:
                    self.executor.err_handler(ex)
        except Exception as e:
            logger.error("got exception: %r" % e)
            self.executor.err_handler(e)
            if isinstance(e, apptest.exception.ConnectionBroken):
                logger.error("connection broken")
                return False
        return True

    def replay_event(self, event):
        for line in event.lines:
            line = line.strip()
            if not self.replay_line(line):
                break

    def replay_lines(self, lines):
        self.start_replay()
        self.hit_nothing = False

        num = 0
        total = len(lines)
        lineno = 0
        while lineno < total:
            line = lines[lineno].strip()
            fake = False
            if line.startswith("FAKE"):
                fake = True
                line = line[4:]
            num += 1
            if self.interactive:
                raw_input("press enter to replay command: %s" % line)
            logger.debug("replay line (%d/%d/%d): %s" % (num, lineno, total, line))

            eventid = line.split(' ', 1)[0]
            resp = None
            # grab resp line
            nextlineno = lineno + 1
            while nextlineno < total:
                nextline = lines[nextlineno]
                if nextline[:4] == "FAKE":
                    nextline = nextline[4:]
                if not nextline.startswith('#'):
                    break
                if nextline.startswith('#RESP#'):
                    respeid = nextline.split(' ', 2)[1]
                    if respeid == eventid:
                        resp = nextline[7:]
                lineno += 1
                nextlineno = lineno + 1

            if not self.replay_line(line, resp, fake):
                break

            lineno += 1

        time.sleep(5);
        self.executor.clean_state()

    def get_target_info(self):
        if self.target:
            (target_log_file, target_bug_id) = self.target.split(':')
            target_bug_id = int(target_bug_id)
            bugs = buglib.collect([target_log_file])
            target_buginfo = bugs[target_bug_id - 1]
            buglib.fix_type(target_buginfo)

            return target_buginfo

        return None

    def dump_events(self, events):
        for event in events:
            event.dump()

    def pickup_events(self, events, ids):
        result = []
        for i in range(len(events)):
            if i in ids:
                result += [events[i]]
            elif events[i].need_fake():
                result += [events[i].get_fake_clone()]
        return result

    def get_nofake_len(self, events):
        count = 0
        for event in events:
            if not event.is_fake():
                count += 1
        return count

    def adv_simp(self, events, target_buginfo, ignore_state):
        try_event_ids = []
        cur_event_id = len(events) - 1
        dep_events = set([events[cur_event_id]])
        if cur_event_id - 1 > 0:
            dep_events.add(events[cur_event_id - 1])
        while cur_event_id >= 0:
            cur_event = events[cur_event_id]
            if cur_event.has_op() and not cur_event in dep_events:
                cur_event_id -= 1
                continue

            try_event_ids = [cur_event_id] + try_event_ids
            logger.info("added event %d" % cur_event_id)

            cur_dep = cur_event.get_deps(cur_event_id, events, ignore_state)
            for event in cur_dep:
                dep_events.add(event)

            cur_event_id -= 1

        try_events = self.pickup_events(events, try_event_ids)

        logger.info("try advanced simplifaction 1: %d -> %d" % (len(events), len(try_event_ids)))
        self.dump_events(try_events)
        succ = self.try_to_replay(try_events, target_buginfo)
        if succ:
            logger.info("advanced simplifaction 1 succ: %d -> %d" % (len(events), len(try_event_ids)))
            return try_events
        else:
            logger.info("advanced simplifaction 1 fail")
            return events

    def simplify(self, simplify_result):
        lines = open(self.replay_file).read().strip().split('\n')
        target_buginfo = self.get_target_info()
        if not target_buginfo:
            logger.error("please specify target when doing simplification")
            return

        logger.info("target bug: " + buglib.get_bug_info(target_buginfo))

        # split lines into "events"

        events = self.lines_to_events(lines)
        orig_events = len(events)

        logger.info("== initial events: ==")
        self.dump_events(events)

        acts = set()
        for event in events:
            event.realize()
            acts = event.parse_act_info(acts)

        events = self.adv_simp(events, target_buginfo, True)
        events = self.adv_simp(events, target_buginfo, False)

        # first, try to remove single elements

        i = 0
        while i < len(events):
            if not events[i].may_remove:
                i += 1
                continue
            if events[i].need_fake():
                events[i].set_fake(True)
                removed = events[i]
            else:
                removed = events.pop(i)
            logger.info("trying to remove event %d/%d: %s" % (i, len(events), removed.get_desc()))
            succ = self.try_to_replay(events, target_buginfo)
            if succ:
                # yes! we can remove it!
                logger.info("successfully removed it")
                self.save_simplify_result(events, simplify_result)
                if removed.need_fake():
                    i += 1
            else:
                # no... keep it
                logger.info("keep it")
                if removed.need_fake():
                    events[i].set_fake(False)
                else:
                    events.insert(i, removed)
                i += 1

        # second: try to remove loop in the state graph

        logger.info("== events after first step: ==")
        self.dump_events(events)

        self.save_simplify_result(events, simplify_result)

        logger.info("== get states for loop detection/removal ==")
        states = self.get_states(events)

        logger.info("== loop detection/removal ==")
        self.sel_events = []
        self.sel_states = []

        i = 0
        while i < len(events):
            logger.debug("= trying to check event %d =" % i)
            state = states[i]
            state_poses = self.find_state(state)
            dont_add = False
            if events[i].may_remove and state_poses and not events[i].is_fake():
                logger.debug("= state duplication at: %r =" % state_poses)
                # state repeated
                for state_pos in state_poses:
                    tmp_event_ids = self.sel_events[:state_pos + 1] + range(i+1, len(events))
                    tmp_events = self.pickup_events(events, tmp_event_ids)
                    succ = self.try_to_replay(tmp_events, target_buginfo)
                    if succ:
                        # yes! let's remove it!
                        logger.debug("= after event %d, selected state %d duplicated. =" % (i, state_pos))
                        logger.debug("= remove loop from %d to %d =" % (state_pos + 1, len(self.sel_states) - 1))
                        for j in xrange(state_pos + 1, len(self.sel_states)):
                            self.remove_state(self.sel_states[j], j)
                        self.sel_states = self.sel_states[:state_pos + 1]
                        self.sel_events = self.sel_events[:state_pos + 1]
                        dont_add = True
                        break
                    else:
                        # we should not remove it
                        pass
            if not dont_add and not events[i].is_fake():
                # record new state & event
                self.record_state(state, len(self.sel_states))
                self.sel_states += [state]
                self.sel_events += [i]

            i += 1

        events = self.pickup_events(events, self.sel_events)
        logger.info("== events after second step: ==")
        self.dump_events(events)

        logger.info("simplification finished!")
        logger.info("before / after simplification: %d / %d" % (orig_events, self.get_nofake_len(events)))

        self.save_simplify_result(events, simplify_result)

    def save_simplify_result(self, events, simplify_result_file):
        with open(simplify_result_file, "w") as f:
            for event in events:
                event.write_to(f)

    def lines_to_events(self, lines):
        events = []
        last_event = None
        for line in lines:
            if line.startswith('#'):
                #(type_, eventid) = line.split(' ', 2)[0:1]
                if last_event:
                    last_event.add_line(line)
                continue
            tag = line.split(' ', 1)[0]
            cmd = line.split(' ', 2)[1]
            if tag == "-" or cmd == "WaitForIdle":
                # should combine
                if last_event:
                    last_event.add_line(line)
                else:
                    # this should not happen..
                    logger.error("illegal log: begins with anonymous command")
                    raise Exception("illegal log")
            else:
                may_remove = True
                if cmd.startswith("Hint"):
                    may_remove = False
                elif cmd == "Start":
                    may_remove = False
                elif cmd == "DisableChecker":
                    may_remove = False

                event = Event(line, may_remove)
                events.append(event)
                last_event = event

        return events

    def try_to_replay(self, events, target):
        lines = []
        for event in events:
            event.append_to(lines)

        for i in xrange(self.retry_count):
            logger.info("=== try No. %d ===" % i)
            log.clear()
            try:
                self.replay_lines(lines)
            except apptest.exception.StoppedException as e:
                raise e
            except Exception as e:
                if not self.launcher.dev_running():
                    logger.info("restart device since it's not running")
                    apptest.init.load_config(self.launcher.restart_dev())

            if self.check_target(target):
                return True

        return False

    def check_target(self, target):
        if not log.get_log_file():
            return False
        bugs = buglib.collect([log.get_log_file()])
        for bug in bugs:
            logger.debug("bug found: " + buglib.get_bug_info(bug))
            if buglib.same_bug(bug, target):
                return True

        return False

    def get_states(self, events):
        self.start_replay()

        states = []
        for event in events:
            try:
                self.replay_event(event)
                (state, ops) = self.executor.collect()
            except:
                state = "Out of range:0"

            states.append(state)

        self.executor.clean_state()

        return states

    def find_state(self, state):
        state_sig = self.executor.get_sig(state)
        if state_sig in self.states:
            return self.states[state_sig]
        else:
            return None

    def remove_state(self, state, pos):
        state_sig = self.executor.get_sig(state)
        self.states[state_sig].remove(pos)

    def record_state(self, state, pos):
        state_sig = self.executor.get_sig(state)
        if state_sig in self.states:
            self.states[state_sig].append(pos)
        else:
            self.states[state_sig] = [pos]
