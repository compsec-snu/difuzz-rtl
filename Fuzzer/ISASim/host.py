import sys
import os
import subprocess

class isaInput():
    def __init__(self, binary, ints):
        self.binary = binary
        self.ints = ints

class rvISAhost():
    def __init__(self, spike, spike_args, isa_sigfile, debug=False):
        self.spike = spike
        self.spike_args = spike_args
        self.isa_sigfile = isa_sigfile

        self.debug= debug

    def debug_print(self, message):
        if self.debug:
            print(message)

    def run_test(self, isa_input: isaInput):
        binary = isa_input.binary
        ints = isa_input.ints
        args = [ self.spike ] + self.spike_args + \
            [ '+signature={}'.format(self.isa_sigfile), binary ]

        self.debug_print('[ISAHost] Start ISA simulation')
        return subprocess.call(args)
