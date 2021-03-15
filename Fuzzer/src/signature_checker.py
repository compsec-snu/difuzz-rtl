import os

from riscv_definitions import *

class sigChecker():
    def __init__(self, isa_sigfile, rtl_sigfile, debug=False, minimizing=False):
        self.isa_sigfile = isa_sigfile
        self.rtl_sigfile = rtl_sigfile

        self.debug = debug
        self.minimizing = minimizing

    def debug_print(self, message, highlight=False):
        if highlight and not self.minimizing:
            print('\x1b[1;31m' + message + '\x1b[1;m')
        elif self.debug:
            print(message)

    def read_symbols(self, symbols):
        symbol_start = symbols['begin_signature']
        symbol_end = symbols['end_signature']
        xreg_idxes = [ (symbols['reg_x{}_output'.format(i)] - symbol_start) // 8 \
                       for i in range(32) ]
        freg_idxes = [ (symbols['reg_f{}_output'.format(i)] - symbol_start) // 8 \
                       for i in range(32) ]
        csr_idxes = {}
        for csr_name in csr_names:
            csr_idxes[csr_name] = (symbols[csr_name + '_output'] - symbol_start) // 8

        data_symbols = []
        for i in range(6):
            data_start = symbols['_random_data{}'.format(i)]
            data_end = symbols['_end_data{}'.format(i)]

            data_symbols.append((data_start, data_end))

        data_idx_start = (symbol_end - symbol_start) // (2 * 8)

        return (xreg_idxes, freg_idxes, csr_idxes, data_symbols, data_idx_start)

    def read_sig(self, sigfile, xreg_idxes, freg_idxes,
                 csr_idxes, data_symbols, data_idx_start):

        fd = open(sigfile)
        lines = fd.readlines()
        fd.close()

        xreg_vals = []
        freg_vals = []
        csr_vals = {}

        for idx in xreg_idxes:
            val = lines[idx // 2][16 - 16 * (idx % 2):32 - 16 * (idx % 2)]
            xreg_vals.append(int(val, 16))

        for idx in freg_idxes:
            val = lines[idx // 2][16 - 16 * (idx % 2):32 - 16 * (idx % 2)]
            freg_vals.append(int(val, 16))

        for csr_name in csr_names:
            idx = csr_idxes[csr_name]
            val = lines[idx // 2][16 - 16 * (idx % 2):32 - 16 * (idx % 2)]
            csr_vals[csr_name] = int(val, 16)

        data_vals = {}
        for i in range(6):
            tup = data_symbols[i]
            data_start = tup[0]
            data_end = tup[1]
            section_size = data_end - data_start

            data = []
            for j in range(section_size // 16):
                words = lines[data_idx_start + j]
                data.append(int(words[16:32], 16))
                data.append(int(words[0:16], 16))

            data_vals['data{}'.format(i)] = data
            data_idx_start += (section_size // 16)

        return (xreg_vals, freg_vals, csr_vals, data_vals)

    def check_intr(self, symbols):
        (xreg_idxes, freg_idxes, csr_idxes, data_symbols, data_idx_start) = \
            self.read_symbols(symbols)

        (rtl_xreg_vals, rtl_freg_vals, rtl_csr_vals, rtl_data_vals) = \
            self.read_sig(self.rtl_sigfile, xreg_idxes, freg_idxes,
                          csr_idxes, data_symbols, data_idx_start)

        scause = rtl_csr_vals['scause']
        sepc = rtl_csr_vals['sepc']
        mcause = rtl_csr_vals['mcause']
        mepc = rtl_csr_vals['mepc']

        intr_prv = NONE
        epc = 0
        # TODO, implements multiple interrupt assertion and priviledges
        assert (scause >> 63) & (mcause >> 63) & 1 == 0, \
            "Only one of Supervisor or Machine interrupt can be asserted"

        if (scause >> 63) & 1:
            intr_prv = SUPERVISOR
            epc = sepc
        elif (mcause >> 63) & 1:
            intr_prv = MACHINE
            epc = mepc

        self.debug_print('[DifuzzRTL] {} interrupt handled -- epc {:016x}'.
                         format(prv[intr_prv], epc), intr_prv != NONE)

        return intr_prv, epc

    def check(self, symbols):

        (xreg_idxes, freg_idxes, csr_idxes, data_symbols, data_idx_start) = \
            self.read_symbols(symbols)

        (isa_xreg_vals, isa_freg_vals, isa_csr_vals, isa_data_vals) = \
            self.read_sig(self.isa_sigfile, xreg_idxes, freg_idxes,
                          csr_idxes, data_symbols, data_idx_start)

        (rtl_xreg_vals, rtl_freg_vals, rtl_csr_vals, rtl_data_vals) = \
            self.read_sig(self.rtl_sigfile, xreg_idxes, freg_idxes,
                          csr_idxes, data_symbols, data_idx_start)

        xreg_match = True
        freg_match = True
        csr_match = True
        data_match = True

        for (i, val) in enumerate(zip(isa_xreg_vals, rtl_xreg_vals)):
            match = (val[0] == val[1])
            if not match: xreg_match = False
            self.debug_print('(x{:02} |{:>5}) [ISA] {:016x} || [RTL] {:016x}'. \
                             format(i, xreg_names[i], val[0], val[1]), not match)

        for (i, val) in enumerate(zip(isa_freg_vals, rtl_freg_vals)):
            match = (val[0] == val[1])
            if not match: xreg_match = False
            self.debug_print('(f{:02} |{:>5}) [ISA] {:016x} || [RTL] {:016x}'. \
                             format(i, freg_names[i], val[0], val[1]), not match)

        for csr_name in csr_names:
            isa_val = isa_csr_vals[csr_name]
            rtl_val = rtl_csr_vals[csr_name]

            match = (isa_val == rtl_val)
            if not match: csr_match = False
            self.debug_print('({:>10}) [ISA] {:016x} || [RTL] {:016x}'. \
                             format(csr_name, isa_val, rtl_val), not match)

        for i in range(6): # TODO, max_sections = 6
            data_start = data_symbols[i][0]
            isa_data = isa_data_vals['data{}'.format(i)]
            rtl_data = rtl_data_vals['data{}'.format(i)]

            if isa_data != rtl_data:
                data_match = False
                self.debug_print('(_random_data{})'.format(i), not data_match)
                for (j, words) in enumerate(zip(isa_data, rtl_data)):
                    addr = data_start + 8 * j
                    isa_word = words[0]
                    rtl_word = words[1]

                    match = (isa_word == rtl_word)
                    self.debug_print('({:016x}) [ISA] {:016x} || [RTL] {:016x}'. \
                                     format(addr, isa_word, rtl_word), not match)

        return (xreg_match & freg_match & csr_match & data_match)
