#!/usr/bin/python

import sys
import os
import re
from collections import defaultdict
from split_firrtl import split_firrtl

def get_submods(modules):
    submods = defaultdict(list)
    pattern = re.compile('\s*inst\s+\S+\s+of\s+(\S+)\s+.*')

    for mod, lines in modules.iteritems():
        for line in lines:
            m = pattern.match(line)
            if m:
                submods[mod].append(m.group(1))
    return submods

def submods_of(submodules, top):
    mods = [top]

    to_visit = submodules[top]
    while len(to_visit) > 0:
        head = to_visit.pop(0)
        if not head in mods:
            mods.append(head)
            to_visit.extend(submodules[head])
    return mods

if __name__ == "__main__":
    def error_out():
        usage = "Usage: {} newtop infile outfile".format(os.path.basename(sys.argv[0]))
        print(usage)
        sys.exit(-1)
    # Check number of arguments
    if len(sys.argv) != 4:
        error_out()
    newtop = sys.argv[1]
    infile = sys.argv[2]
    outfile = sys.argv[3]
    if not(os.path.isfile(infile)) :
        print("infile must be a valid file!")
        error_out()

    with open(infile, "r") as f:
        modules = split_firrtl(f.readlines())

    if not(newtop in modules):
        print("newtop must actually be a module!")
        error_out()

    submods = get_submods(modules)
    new_mods = submods_of(submods, newtop)

    with open(outfile, "w") as f:
        f.write('circuit {} :\n'.format(newtop))
        for mod in new_mods:
            for line in modules[mod]:
                f.write(line)
