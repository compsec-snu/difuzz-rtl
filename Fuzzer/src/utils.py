import os
import shutil
import psutil
import signal

from ISASim.host import rvISAhost
from RTLSim.host import rvRTLhost

from src.preprocessor import rvPreProcessor
from src.signature_checker import sigChecker
from src.mutator import simInput, rvMutator
from src.multicore_manager import proc_state, procManager

ISA_TIME_LIMIT = 1

def save_err(out: str, proc_num: int, manager: procManager, stop_code: int):

    if stop_code == proc_state.NORMAL:
        return

    status = proc_state.tpe[stop_code]

    manager.P('state')
    fd = open(out + '/fuzz_log', 'a')
    fd.write('[DifuzzRTL] Thread {}: {} occurred\n'.format(proc_num, status))
    fd.close()

    if not os.path.isdir(out + '/err'):
        os.makedirs(out + '/err')
    manager.V('state')

    shutil.copyfile(out + '/.input_{}.si'.format(proc_num),
                    out + '/err/err_{}_{}.si'.format(status, proc_num))


def isa_timeout(out, stop, proc_num):
    if not os.path.isdir(out + '/isa_timeout'):
        os.makedirs(out + '/isa_timeout')

    shutil.copy(out + '/.input_{}.elf'.format(proc_num), out + '/isa_timeout/timeout.elf')
    shutil.copy(out + '/.input_{}.S'.format(proc_num), out + '/isa_timeout/timeout.S')

    ps = psutil.Process()
    children = ps.children(recursive=True)
    for child in children:
        os.kill(child.pid, signal.SIGKILL) # SIGKILL

    stop[0] = proc_state.ERR_ISA_TIMEOUT

def debug_print(message, debug, highlight=False):
    if highlight:
        print('\x1b[1;31m' + message + '\x1b[1;m')
    elif debug:
        print(message)

def save_file(file_name, mode, line):
    fd = open(file_name, mode)
    fd.write(line)
    fd.close()

def save_mismatch(base, proc_num, out, sim_input: simInput, data: list, num): #, elf, asm, hexfile, mNum):
    sim_input.save(out + '/sim_input/id_{}.si'.format(num), data)

    rtl_sig = base + '/.rtl_sig_{}.txt'.format(proc_num)
    isa_sig = base + '/.isa_sig_{}.txt'.format(proc_num)
    # elf = base + '/.input_{}.elf'.format(proc_num)
    # asm = base + '/.input_{}.S'.format(proc_num)
    # hexfile = base + '/.input_{}.hex'.format(proc_num)

    shutil.copy(rtl_sig, out + '/sig/rtl_sig_{}.txt'.format(num))
    shutil.copy(isa_sig, out + '/sig/isa_sig_{}.txt'.format(num))
    # shutil.copy(elf, out + '/elf/id_{}.elf'.format(num))
    # shutil.copy(asm, out + '/asm/id_{}.S'.format(num))
    # shutil.copy(hexfile, out + '/hex/id_{}.hex'.format(num))

def setup(dut, toplevel, template, out, proc_num, debug, minimizing=False, no_guide=False):
    mutator = rvMutator(no_guide=no_guide)

    cc = 'riscv64-unknown-elf-gcc'
    elf2hex = 'riscv64-unknown-elf-elf2hex'
    preprocessor = rvPreProcessor(cc, elf2hex, template, out, proc_num)

    spike = os.environ['SPIKE']
    isa_sigfile = out + '/.isa_sig_{}.txt'.format(proc_num)
    rtl_sigfile = out + '/.rtl_sig_{}.txt'.format(proc_num)

    if debug: spike_arg = ['-l']
    else: spike_arg = []

    isaHost = rvISAhost(spike, spike_arg, isa_sigfile)
    rtlHost = rvRTLhost(dut, toplevel, rtl_sigfile, debug=debug)

    checker = sigChecker(isa_sigfile, rtl_sigfile, debug, minimizing)

    return (mutator, preprocessor, isaHost, rtlHost, checker)

def setup_fpga(template, out, proc_num, debug, minimizing=False, no_guide=False):
    mutator = rvMutator(no_guide=no_guide)

    cc = 'riscv64-unknown-elf-gcc'
    elf2hex = 'riscv64-unknown-elf-elf2hex'
    preprocessor = rvPreProcessor(cc, elf2hex, template, out, proc_num)

    spike = os.environ['SPIKE']
    isa_sigfile = out + '/.isa_sig_{}.txt'.format(proc_num)
    rtl_sigfile = out + '/.rtl_sig_{}.txt'.format(proc_num)

    if debug: spike_arg = ['-l']
    else: spike_arg = []

    isaHost = rvISAhost(spike, spike_arg, isa_sigfile)

    checker = sigChecker(isa_sigfile, rtl_sigfile, debug, minimizing)

    return (mutator, preprocessor, isaHost, checker)
