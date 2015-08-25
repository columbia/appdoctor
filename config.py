import device
import urllib
import parser
import copy

class Config:
    def __init__(self, version = '4.0', sdcard = '16M', ram = None, width = None, height = None, density = None, config_line = None, extra_ident = None, based = None, abi = None, camera = 'b', libs = ''):
        if config_line:
            line = urllib.unquote(config_line)
            parse = parser.Parser()
            self.__init__(**parse.parse_line(line))
        else:
            self.version = version
            self.sdcard = sdcard
            self.ram = ram
            self.width = width
            self.height = height
            self.density = density
            self.abi = abi
            self.camera = camera
            self.extra_ident = extra_ident
            self.libs = libs.split(',')

            self.libs.append("android.test.runner")

    def encode(self):
        parse = parser.Parser()
        line = parse.add_element("", "version", self.version)
        line = parse.add_element(line, "sdcard", self.sdcard)
        line = parse.add_element(line, "ram", self.ram)
        line = parse.add_element(line, "width", self.width)
        line = parse.add_element(line, "height", self.height)
        line = parse.add_element(line, "density", self.density)
        line = parse.add_element(line, "abi", self.abi)
        line = parse.add_element(line, "camera", self.camera)
        line = parse.add_element(line, "extra_ident", self.extra_ident)
        return urllib.quote(line)

    @staticmethod
    def decode(line):
        line = urllib.unquote(line)
        parse = parser.Parser()
        return Config(**parse.parse_line(line))

    def obtain_device(self):
        return device.Device(self.version, self.sdcard, self.ram, self.width, self.height, self.density, extra_ident = self.extra_ident, abi = self.abi)

    def obtain_app_specific_config(self, app_ident):
        clone = copy.copy(self)
        clone.extra_ident = "APPTEMP-" + app_ident
        return clone

    def get_libs(self):
        return self.libs
