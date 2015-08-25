import parser
import logging
import os

libs = {}
logger = logging.getLogger("libinfo")

class LibInfo(object):
    def __init__(self, name, path):
        self.name = name
        self.path = path

def load_lib_info(libinfo_file = "libinfo.csv"):
    if os.path.exists(libinfo_file):
        logger.info("loading lib info from %s" % libinfo_file)
        p = parser.Parser()
        results = p.parse_file(LibInfo, libinfo_file)
        for result in results:
            libs[result.name] = result

def get_lib_path(lib):
    if lib in libs:
        return libs[lib].path
