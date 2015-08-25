#!/usr/bin/env python
class Parser:
    def parse_line(self, line):
        in_escape = False
        in_str = False
        cur_part = ''
        name = ''
        value = ''
        ret = {}
        for ch in line:
            if in_escape:
                cur_part += ch
                in_escape = False
            else:
                if ch == '\\':
                    in_escape = True
                elif ch == '"':
                    in_str = not in_str
                elif in_str:
                    cur_part += ch
                else:
                    if ch == '=':
                        name = cur_part
                        cur_part = ''
                    elif ch == ',':
                        value = cur_part
                        cur_part = ''
                        if name:
                            ret[name] = value
                        name = ''
                        value = ''
                    else:
                        cur_part += ch

        if cur_part:
            value = cur_part
            cur_part = ''
        if name:
            ret[name] = value
        return ret

    def parse_file(self, cls, file):
        results = []
        for line in open(file):
            line = line.strip()
            if line[:1] == '#':
                line = ''
            if line:
                ret = self.parse_line(line)
                obj = cls(**ret)
                results.append(obj)
        return results

    def add_element(self, line, name, value):
        if value is not None:
            if line:
                line = line + ","
            return line + "%s=%s" % (name, self.escape(value))
        else:
            return line

    def escape(self, str):
        ret = '"'
        for ch in str:
            if ch == '\\':
                ret += '\\\\'
            elif ch == '"':
                ret += '\\"'
            else:
                ret += ch
        ret += '"'
        return ret

def test():
    p = Parser()
    print p.parse_line('a=b,c=d')
    print p.parse_line('a=b,c=d,e=')
    print p.parse_line('a=b,c=d,e=,f=g')
    print p.parse_line('a=b,c=d,e="a=b c=d e=",f=z')
    print p.parse_line('a=b,c=d,e="a=b c=d e=\\"",f=z')['e']
    print p.parse_line('a=b,c=d,e="a=b c=d e=\\\\",f=z')['e']

if __name__ == "__main__":
    test()
