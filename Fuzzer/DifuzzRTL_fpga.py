#!/usr/bin/env python3

import os
import time
import signal
from datetime import datetime

from src.utils import save_file
from src.env_parser import envParser
from src.multicore_manager import proc_state, procManager
from src.fpga_scheduler import fpgaScheduler

from Fuzzer import Run
from Minimizer import Minimize

""" Fuzzer entry """
parser = envParser()

parser.add_option('num_iter', 1, 'The number of fuzz iterations')
parser.add_option('template', 'Template', 'Template test file location')
parser.add_option('in_file', None, 'SimInput to replay')
parser.add_option('out', 'output', 'Directory to save the result')
parser.add_option('record', 0, 'Record the result')
parser.add_option('debug', 0, 'Debugging?')
parser.add_option('minimize', 0, 'Minimizing?')
parser.add_option('no_guide', 0, 'Only random testing?')
parser.add_option('key', 0, 'Key for semaphore')

parser.print_help()
parser.parse_option()

out = parser.arg_map['out'][0]
record = parser.arg_map['record'][0]
minimize = parser.arg_map['minimize'][0]
parser.arg_map.pop('minimize', None)
key = parser.arg_map['key'][0]
parser.arg_map.pop('key', None)

template = parser.arg_map['template'][0]
debug = parser.arg_map['debug'][0]


if not os.path.isdir(out):
    os.makedirs(out)

if not os.path.isdir(out + '/mismatch'):
    os.makedirs(out + '/mismatch')
    os.makedirs(out + '/mismatch/sim_input')
    os.makedirs(out + '/mismatch/sig')
    # os.makedirs(out + '/mismatch/elf')
    # os.makedirs(out + '/mismatch/asm')
    # os.makedirs(out + '/mismatch/hex')

if not os.path.isdir(out + '/illegal'):
    os.makedirs(out + '/illegal')
    os.makedirs(out + '/illegal/sim_input')
    os.makedirs(out + '/illegal/elf')
    os.makedirs(out + '/illegal/asm')
    os.makedirs(out + '/illegal/hex')

if not os.path.isdir(out + '/corpus'):
    os.makedirs(out + '/corpus')

date = datetime.today().strftime('%Y%m%d')
cov_log = out + '/cov_log_{}.txt'.format(date)
if record and not os.path.isfile(cov_log):
    save_file(cov_log, 'w', '{:<10}\t{:<10}\t{:<10}\n'.
              format('time', 'iter', 'coverage'))

start_time = time.time()

scheduler = fpgaScheduler(key)

arg_map = {}
for k, v in parser.arg_map.items():
    arg_map[k] = v[0]

arg_map['start_time'] = start_time
arg_map['cov_log'] = cov_log
arg_map['scheduler'] = scheduler


for k, v in arg_map.items():
    print('{}: {}'.format(k, v))

if minimize:
    Minimize(template, out, 1, 0, scheduler, debug)
else:
    Run(**arg_map)
    Minimize(template, out, 1, 0, scheduler, debug)
