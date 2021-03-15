#!/usr/bin/python

import sys
import os
import re
from collections import defaultdict

# Takes firrtl text, returns a dict from module name to module definition
def split_firrtl(firrtl_lines):
    modules = defaultdict(list)
    current_mod = ""

    pattern = re.compile('\s*(?:ext)?module\s+(\S+)\s*:\s*')
    for line in firrtl_lines:
        m = pattern.match(line)
        if m:
            current_mod = m.group(1)
        if current_mod:
            modules[current_mod].append(line)
    return modules

if __name__ == "__main__":
    def error_out():
        usage = "Usage: {} infile outdir".format(os.path.basename(sys.argv[0]))
        print(usage)
        sys.exit(-1)
    # Check number of arguments
    if len(sys.argv) != 3:
        error_out()
    infile = sys.argv[1]
    outdir = sys.argv[2]
    if not(os.path.isfile(infile)) :
        print("infile must be a valid file!")
        error_out()
    if not(os.path.isdir(outdir)) :
        print("outdir must be a valid directory!")
        error_out()

    with open(infile, "r") as f:
        modules = split_firrtl(f.readlines())
    for name, body in modules.iteritems():
        with open(os.path.join(outdir, name + ".fir"), 'w') as w:
            for line in body:
                w.write(line)
