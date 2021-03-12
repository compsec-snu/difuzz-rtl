import os
import random

from riscv_definitions import *

NONE   = 0
CF_J   = 1
CF_BR  = 2
CF_RET = 3
MEM_R  = 4
MEM_W  = 5
CSR    = 6

PREFIX = '_p'
MAIN   = '_l'
SUFFIX = '_s'

class Word():
    def __init__(self, label: int, insts: list, tpe=NONE, xregs=[], fregs=[], imms=[], symbols=[], populated=False):
        self.label = label
        self.tpe = tpe
        self.insts = insts
        self.len_insts = len(insts)

        self.xregs = xregs
        self.fregs = fregs
        self.imms = imms
        self.symbols = symbols
        self.operands = xregs + fregs + [ imm[0] for imm in imms ] + symbols

        self.populated = populated
        self.ret_insts = []

    def pop_inst(self, inst, opvals):
        for (op, val) in opvals.items():
            inst = inst.replace(op, val)

        return inst

    def populate(self, opvals, part=MAIN):
        for op in self.operands:
            assert op in opvals.keys(), \
                '{} is not in label {} Word opvals'.format(op, self.label)

        pop_insts = []
        for inst in self.insts:
            p_inst = self.pop_inst(inst, opvals)
            pop_insts.append(p_inst)

        ret_insts = [ '{:<8}{:<42}'.format(part + str(self.label) + ':',
                                           pop_insts.pop(0)) ]

        for i in range(len(pop_insts)):
            ret_insts.append('{:8}{:<42}'.format('', pop_insts.pop(0)))

        self.populated = True
        self.ret_insts = ret_insts

    def reset_label(self, new_label, part):
        old_label = self.label
        self.label = new_label

        if self.populated:
            self.ret_insts[0] = '{:8}{:<42}'.format(part + str(self.label) + ':',
                                                    self.ret_insts[0][8:])
            return (old_label, new_label)
        else:
            return None

    def repop_label(self, label_map, max_label, part):
        if self.populated:
            for i in range(len(self.ret_insts)):
                inst = self.ret_insts[i]
                tmps = inst.split(', ' + part)

                if len(tmps) > 1:
                    label = tmps[1].split(' ')[0]

                    old = int(label)
                    new = label_map.get(old, random.randint(self.label + 1, max_label))

                    new_inst = inst[8:].replace(part + '{}'.format(old), part + '{}'.format(new))
                    inst = '{:<8}{:<50}'.format(inst[0:8], new_inst)

                    self.ret_insts[i] = inst
        else:
            return

    def get_insts(self):
        assert self.populated, \
            'Word is not populated'

        return self.ret_insts

def word_jal(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = CF_J
    insts = [ syntax ]
    return (tpe, insts)

def word_jalr(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = CF_J
    insts = [ 'la xreg1, symbol', syntax ]
    symbols.append('symbol')

    return (tpe, insts)

# Need to update
def word_branch(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = CF_BR
    insts = [ syntax ]

    return (tpe, insts)

def word_ret(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = CF_RET
    if syntax == 'mret': epc = 'mepc'
    elif syntax == 'sret': epc = 'sepc'
    else: epc = 'uepc'

    insts = [ 'la xreg0, symbol',
              'csrrw zero, {}, xreg0'.format(epc),
              syntax ]

    xregs.append('xreg0')
    symbols.append('symbol')

    return (tpe, insts)

def word_mem_r(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = MEM_R
    rand = random.random()
    if rand < 0.1:
        mask_addr = [ 'lui xreg2, 0xffe00',
                      'xor xreg1, xreg1, xreg2' ]
        xregs.append('xreg2')
    else:
        mask_addr = []

    insts = [ 'la xreg1, symbol' ] + mask_addr + [ syntax ]
    symbols.append('symbol')

    return (tpe, insts)

def word_mem_w(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = MEM_W
    rand = random.random()
    if rand < 0.1:
        mask_addr = [ 'lui xreg2, 0xffe00',
                      'xor xreg1, xreg1, xreg2' ]
        xregs.append('xreg2')
    else:
        mask_addr = []

    insts = [ 'la xreg1, symbol' ] + mask_addr + [ syntax ]
    symbols.append('symbol')

    return (tpe, insts)

def word_atomic(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = MEM_W
    rand = random.random()
    if rand < 0.1:
        mask_addr = [ 'lui xreg2, 0xffe00',
                      'xor xreg1, xreg1, xreg2' ]
        xregs.append('xreg2')
    else:
        mask_addr = []

    insts = [ 'la xreg1, symbol',
              'addi xreg1, xreg1, imm6' ] + \
              mask_addr + \
              [ syntax ]

    if opcode in rv64.keys():
        imms.append(('imm6', 8))
    else:
        imms.append(('imm6', 4))
    symbols.append('symbol')

    return (tpe, insts)

def word_csr_r(opcode, syntax, xregs, fregs, imms, symbols):
    csr = random.choice(csr_names)

    if 'pmpaddr' in csr:
        tpe = MEM_R
        insts = [ 'la xreg1, symbol',
                  'srai xreg1, xreg1, 1',
                  syntax.format(csr) ]
        symbols.append('symbol')
    else:
        tpe = CSR
        insts = [ 'xor xreg1, xreg1, xreg1']
        for i in range(random.randint(0, 3)):
            set_bits = random.choice([1, 3])
            offset = random.randint(0, 31)
            insts = insts + \
                ['addi xreg{}, zero, {}'.format(i+2, set_bits),
                 'slli xreg{}, xreg{}, {}'.format(i+2, i+2, offset),
                 'add xreg1, xreg1, xreg{}'.format(i+2)
                ]
            xregs.append('xreg{}'.format(i+2))
        insts.append(syntax.format(csr))

    return (tpe, insts)

def word_csr_i(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = CSR
    csr = random.choice(csr_names)

    insts = [ syntax.format(csr) ]

    return (tpe, insts)

def word_sfence(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = NONE
    pt_symbol = random.choice([ 'pt0', 'pt1', 'pt2', 'pt3' ])

    imms += [ ('uimm1', 1), ('uimm6', 8) ]
    insts = [ 'li xreg0, uimm1',
              'la xreg1, {}'.format(pt_symbol),
              'addi xreg1, xreg1, uimm6' ] + \
              [ syntax ]

    return (tpe, insts)

def word_fp(opcode, syntax, xregs, fregs, imms, symbols):
    tpe = NONE
    # rm = random.choice([ 'rne', 'rtz', 'rdn',
    #                      'rup', 'rmm', 'dyn'])
    # Unset rounding mode testing
    rm = 'rne'

    insts = [ syntax.format(rm) ]

    return (tpe, insts)


""" Opcodes_words
Dictionary of opcodes - word generation functions
to handle opcodes which need special instructions
"""
opcodes_words = {
    'jal': (['jal'], word_jal),
    'jalr': (['jalr'], word_jalr),
    'branch': (list(rv32i_btype.keys()), word_branch),
    'ret': (['mret', 'sret', 'uret'], word_ret),
    'mem_r': (['lb', 'lh', 'lw', 'ld', 'lbu', 'lhu', 'lwu', \
               'flw', 'fld', 'flq'], word_mem_r),
    'mem_w': (['sb', 'sh', 'sw', 'sd', 'fsw', 'fsd', 'fsq'], word_mem_w),
    'atomic': (list(rv32a.keys()) + list(rv64a.keys()), word_atomic),
    'csr_r': (['csrrw', 'csrrs', 'csrrc'], word_csr_r),
    'csr_i': (['csrrwi', 'csrrsi', 'csrrci'], word_csr_i),
    'sfence': (['sfence.vma'], word_sfence),
    'fp': (list(rv32f.keys()) + list(rv64f.keys()) + list(rv32d.keys()) + \
            list(rv64d.keys()) + list(rv32q.keys()) + list(rv64q.keys()),
           word_fp)
}
