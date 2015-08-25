import ConfigParser
import logging

__all__ = ['get_str']

default_configuration = {}
configuration = {}

logger = logging.getLogger("config")

def get_str(section, name):
    if (section in configuration):
        if (name in configuration[section]):
            return configuration[section][name]
    return default_configuration[section][name]

def load_config(config_file = "android_test.conf", default_config_file = "default_android_test.conf"):
    logger.info("loading default configuration from %s", default_config_file)
    global default_configuration
    default_configuration = {}
    read_config(default_config_file, default_configuration)
    logger.info("loading configuration from %s", config_file)
    global configuration
    configuration = {}
    read_config(config_file, configuration)

def read_config(filename, configuration):
    parser = ConfigParser.ConfigParser()
    parser.read(filename)
    for section in parser.sections():
        configuration[section] = {}
        for item in parser.items(section):
            configuration[section][item[0]] = item[1]


