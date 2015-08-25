#!/usr/bin/env python
import parser
import app
import config
import random
import launch
import jobs
import logging
import argparse
import traceback
import settings
import signal
import flags

LOG_FORMAT = "%(asctime)-15s [%(process)d] %(name)s:%(levelname)s %(message)s"

logger = logging.getLogger("controller")

class Controller:
    def __init__(self, apps_list='apps.csv', configs_list='configs.csv', log_dir = "log/", slot_count = 16, batch_count = 100, video_mode = False, num_of_operations=1000, reuse_emulator=True):
        self.apps_list = apps_list
        self.configs_list = configs_list
        self.slot_count = slot_count
        self.batch_count = batch_count
        self.video_mode = video_mode
        self.num_of_operations = num_of_operations
        self.reuse_emulator = reuse_emulator
        self.log_dir = log_dir
        self.load()
        self.jobs = jobs.Jobs(slot_count)

        signal.signal(signal.SIGUSR1, self.handle_reload_sig)

    def handle_reload_sig(self, signum, frame):
        self.load()

    def load(self):
        parse = parser.Parser()
        self.apps = parse.parse_file(app.App, self.apps_list)
        self.configs = parse.parse_file(config.Config, self.configs_list)

    def pick_random(self, list):
        index = random.randrange(len(list))
        return list[index]

    def pick_pair(self):
        app = self.pick_random(self.apps)
        conf = self.pick_random(self.configs)
        while not app.has_libs(conf):
            app = self.pick_random(self.apps)
            conf = self.pick_random(self.configs)

        return (app, conf)

    def pick_and_start(self, job_id):
        (app, conf) = self.pick_pair()
        apk_file = app.get_apk_path()

        dev = conf.obtain_device()
        dev.check_config_template(True)
        logger.debug("config dev: %s" % dev.get_avd_name())

        if settings.use_app_template:
            app_conf = app.obtain_config(conf)
            app_dev = app_conf.obtain_device()
            logger.debug("app dev: %s" % app_dev.get_avd_name())

            if not app_dev.exists():
                logger.info("app template does not exist, create it")
                app_dev.create_from_config_template()
                launcher = launch.Launch(app_dev, apk_file)
                logger.info("app template created. installing components")
                launcher.prepare_app_template()
                logger.info("app template ready")

            config_line = app_conf.encode()
        else:
            config_line = conf.encode()

        self.launch_job(apk_file, config_line, job_id)

    def launch_job(self, apk_file, config_line, job_id):
        logger.debug("going to launch job")
        logger.debug("apk: %s" % apk_file)
        logger.debug("config line: %s" % config_line)
        if self.reuse_emulator:
            logger.debug("reusing emulator")
            cmd = "%s {0} 1 %s %d -f %s -d %s -n %d -b %d -p record" % (flags.get_base_path("rep.sh"), self.log_dir, job_id, apk_file, config_line, self.num_of_operations, self.batch_count)
        else:
            cmd = "%s {0} %d %s %d -f %s -d %s -n %d -p record" % (flags.get_base_path("rep.sh"), self.batch_count, self.log_dir, job_id, apk_file, config_line, self.num_of_operations)
        if settings.use_app_template:
            cmd += " -s"
        if not self.video_mode:
            cmd += " -c"

        logger.debug("command: %s" % cmd)
        self.jobs.launch_job(cmd)

    def stop(self):
        self.jobs.stop()

    def stop_when_done(self):
        self.jobs.stop_when_done()

def run_once():
    argparser = argparse.ArgumentParser()
    argparser.add_argument("-b", "--batch-count", help="number of runs in one batch",
            type=int, default=1)
    argparser.add_argument("-j", "--jobs-to-run", help="number of jobs to run",
            type=int, default=1)
    argparser.add_argument("-a", "--apps-list", help="list of applications",
            type=str, default="apps.csv")
    argparser.add_argument("-c", "--configs-list", help="list of configurations",
            type=str, default="configs.csv")
    argparser.add_argument("-l", "--log-dir", help="directory to store logs",
            type=str, default="log/")
    argparser.add_argument("-v", "--video-mode", help="show emulator window",
            action="store_true")
    argparser.add_argument("-n", "--num-of-operations", help="max number of operations before exit",
            type=int, default=1000)
    argparser.add_argument("-s", "--slot-count", help="number of slots",
            type=int, default=8)
    argparser.add_argument("-r", "--no-reuse-emulator", help="don't reuse emulator, recreate it",
            action="store_true")
    args = argparser.parse_args()

    logging.basicConfig(level=logging.DEBUG, format=LOG_FORMAT)

    controller = Controller(batch_count=args.batch_count, apps_list=args.apps_list,
            configs_list=args.configs_list, log_dir=args.log_dir, slot_count=args.slot_count,
            video_mode=args.video_mode, num_of_operations=args.num_of_operations, reuse_emulator=not args.no_reuse_emulator)
    for i in range(args.jobs_to_run):
        try:
            controller.pick_and_start(i)
        except:
            logger.error("got exception picking job: %s" % traceback.format_exc())
    controller.stop_when_done()
#    raw_input('enter to stop\n')
#    controller.stop()

if __name__ == "__main__":
    run_once()
