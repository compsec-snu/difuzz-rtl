import sys
import os
from pydoc import locate

class envParser():
    def __init__(self):
        self.arg_map = {}
        self.help_msg = ''

        try:
            self.help = os.environ['HELP']
        except Exception:
            self.help = False

    def add_option(self, option, val, info=''):
        if option.__class__.__name__ != 'str':
            raise Exception('envParser::add_option must get string')

        self.arg_map[option] = (val, option.upper(), info)
        self.help_msg = self.help_msg + \
            '{:<10} (default: {}) :: {}\n'.format(option, val, info)
            

    def parse_option(self):
        for opt in list(self.arg_map.keys()):

            arg_tuple = list(self.arg_map[opt])

            if arg_tuple[0] is None:
                type = 'str'
            else:
                type = arg_tuple[0].__class__.__name__
            cast = locate(type)
            try:
                input = os.environ[arg_tuple[1]]
            except Exception:
                input = arg_tuple[0]
                pass

            try:
                if input is None:
                    val = None
                else:
                    val = cast(input)
                arg_tuple[0] = val
            except ValueError:
                print('{:<10} value {:<10} can not be converted to {}'. \
                      format(arg_tuple[1], input, type))
                print(self.help_msg)

            self.arg_map[opt] = tuple(arg_tuple)

    def register_option(self, factory):
        for opt in list(self.arg_map.keys()):
            arg_tuple = self.arg_map[opt]

            factory.add_option(opt, [arg_tuple[0]])

    def print_help(self):
        if self.help:
            print(self.help_msg)
            exit(0)
        else:
            return
