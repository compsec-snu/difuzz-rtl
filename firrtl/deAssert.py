#!/usr/bin/env python3

"""
To facilitate DifuzzRTL debugging

It turn off PRINTF_COND, STOP_COND of specified modules
"""

import sys
import argparse

def isTarget(targets, line):
    istarget = False
    for target in targets:
        if 'module ' + target in line:
            istarget = True

    return istarget

def main():
    parser = argparse.ArgumentParser(description='Argparser for deAssert')

    parser.add_argument('--vfile', required=True, help='Input verilog file')
    parser.add_argument('--modules', required=True, help='Modules to deassert')

    args = parser.parse_args()

    vfile = args.vfile
    new_vfile = vfile[:-2] + '_deassert.v'
    tModules = args.modules.split(',')

    print('tModules: {}'.format(tModules))
    fd = open(vfile, 'r')
    nfd = open(new_vfile, 'w')

    while True:
        line = fd.readline()
        if not line: break
        if isTarget(tModules, line):
            nfd.write(line)
            while True:
                inLine = fd.readline()
                if 'assign metaAssert' in inLine:
                    newLine = "  assign metaAssert = 1'h0;\n"
                else:
                    newLine = inLine.replace('`PRINTF_COND', "1'h0")
                    newLine = newLine.replace('`STOP_COND', "1'h0")
                nfd.write(newLine)

                if 'endmodule' in inLine: break
        else:
            nfd.write(line)

if __name__ == '__main__':
    main()
