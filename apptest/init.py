import config
import logging
import widget
import interface
import connection

def init_testlib(apk_path, r_path = None, config_file = None, err_handler = None, skip_decode = False, skip_start = False, event_handler = None):
    if err_handler:
        connection.err_handler = err_handler
    if event_handler:
        connection.event_handler = event_handler
    if config_file is None:
        config.load_config()
    else:
        config.load_config(config_file = config_file)
    if not skip_decode:
        if r_path is None:
            widget.load_widgets_id(apk_path)
        else:
            widget.load_widgets_id_from_r(r_path)
    if not skip_start:
        return interface.start()
    else:
        return None

def load_config(config_file):
    config.load_config(config_file = config_file)

def set_err_handler(err_handler):
    connection.err_handler = err_handler

def finish():
    interface.finish()
