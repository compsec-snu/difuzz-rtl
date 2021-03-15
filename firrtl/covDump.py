#!/usr/bin/python3.6

""" 
FIRRTL Compiler can not instrument system task
To continue after fuzz end, coverage map has to be saved.
Also, coverage map has to be restored.

It simply instruments store, and restore coverage map to
input verilog file using hierarchy.txt.
"""

import sys
import argparse

""" Recursively find paths to all covMap in module """
def findCovPath(modInst, modCovSize, module):

    if module not in list(modInst.keys()):
        return []

    covPaths = []
    covSize = modCovSize[module]
    if not modInst[module] and covSize == 0:
        covPaths = []
    elif not modInst[module]:
        covPaths = [ module + '_cov']
    else:
        if covSize != 0:
            covPaths.append(module + '_cov')
        for (instance, instModule) in modInst[module]:
            paths = findCovPath(modInst, modCovSize, instModule)
            for path in paths:
                covPaths.append(instance + '.' + path)
    
    return covPaths

def main():
    parser = argparse.ArgumentParser(description='Argparser for save/restore coverage map instrument')
    
    parser.add_argument('--vfile', required=True, help='Input verilog file')
    parser.add_argument('--top', required=True, help='Top level module')
    parser.add_argument('--hier', required=True, help='Hierarchy file')
    
    args = parser.parse_args()
    
    vfile = args.vfile
    new_vfile = vfile[:-2] + '_tmp.v'
    hierarchy = args.hier
    toplevel = args.top
    
    modInst = {}
    modCovSize = {}
    
    fd = open(hierarchy, 'r')
    while True:
        line = fd.readline()
        if not line: break
        splits = line.split('\t')
        if splits[0] != '':
            module = splits[0]
            numInst = splits[1]
            covSize = int(splits[2][:-1])
            instances = []
            for i in range(int(numInst)):
                inLine = fd.readline()
                if not inLine: break
                inSplits = inLine.split('\t')
    
                instModule = inSplits[1]
                instance = inSplits[2][:-1]
                instances.append((instance, instModule))
    
            modInst[module] = instances
            modCovSize[module] = covSize
    fd.close()
    
    covPaths = findCovPath(modInst, modCovSize, toplevel)

    covSizes = []
    for path in covPaths:
        last = path.split('.')[-1]
        lastModule = last[:-4]
        covSizes.append(modCovSize[lastModule])

    covPathSize = list(zip(covPaths, covSizes))

    topPaths = []
    for path in covPaths:
        topPaths.append(toplevel + '.' + path)
        print(toplevel + '.' + path)

    fd = open(vfile, 'r')
    nfd = open(new_vfile, 'w')

    nfd.write('`define toAscii(x) { x / 8\'ha + 8\'h30, x % 8\'ha + 8\'h30 }\n\n')
    while True:
        line = fd.readline()
        if not line: break
        if 'module ' + toplevel in line:
            nfd.write(line)
            nfd.write('`ifdef MULTICORE\n')
            nfd.write('  input         cov_store,\n')
            nfd.write('  input         cov_restore,\n')
            nfd.write('  input [7:0]   proc_num,\n')
            nfd.write('`endif\n')
            while True:
                inLine = fd.readline()
                if not inLine: break
                if ');' in inLine:
                    nfd.write(');\n')

                    nfd.write('`ifdef MULTICORE\n')
                    nfd.write('  integer i;\n')
                    nfd.write('  integer fd;\n')
                    nfd.write('  integer c;\n')
                    nfd.write('  reg [8*100:1] out;\n')
                    nfd.write('  initial begin\n')
                    nfd.write('    if ($value$plusargs("OUT=%s", out)) begin\n')
                    nfd.write('      $display("Output directory: %0s\\n", out);\n')
                    nfd.write('    end\n')
                    nfd.write('  end\n')
                    nfd.write('  always @(posedge clock) begin\n')
                    nfd.write('    if (cov_restore) begin\n')
                    
                    for (path, size) in covPathSize:
                        if size != 0:
                            nfd.write('      fd = $fopen({out, "/covmap/%s.dat"}, "r");\n' % path)
                            nfd.write('      if (fd == 0)\n')
                            nfd.write('        $display("No saved %s, starting from zero", "{}");\n'.format(path))
                            nfd.write('      else begin\n')
                            nfd.write('        for (i=0; i<%d; i=i+1) begin\n' % size)
                            nfd.write('          c = $fgetc(fd);\n');
                            nfd.write('          %s[i] = c[0];\n' % path)
                            nfd.write('        end\n')
                            nfd.write('        $fclose(fd);\n')
                            nfd.write('      end\n')

                    nfd.write('    end\n')
                    nfd.write('    if (cov_store) begin\n')

                    for (path, size) in covPathSize:
                        if size != 0:
                            nfd.write('      fd = $fopen({{{out, "/covmap-"}, `toAscii(proc_num)}, "/%s.dat"}, "w");\n' % path)
                            nfd.write('      for (i=0; i<%d; i=i+1)\n' % size)
                            nfd.write('        $fwrite(fd, "%0b", {}[i]);\n'.format(path))
                            nfd.write('      $fclose(fd);\n')
                    nfd.write('    end\n')
                    nfd.write('  end\n')
                    nfd.write('`endif\n')
                    break
                else:
                    nfd.write(inLine)
        else:
            nfd.write(line)

    fd.close()
    nfd.close()


if __name__ == '__main__':
    main()
