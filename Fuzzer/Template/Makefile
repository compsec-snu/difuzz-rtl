#===========================================================
# Makefile for riscv-tests/custom-test\
#-----------------------------------------------------------

XLEN ?= 64

src_dir := .

default: all

#-----------------------------------------------------------
# Build rules
#-----------------------------------------------------------

RISCV_PREFIX ?= riscv$(XLEN)-unknown-elf-
RISCV_GCC ?= $(RISCV_PREFIX)gcc
RISCV_GCC_OPTS ?= -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -march=rv64g -mabi=lp64
RISCV_OBJDUMP ?= $(RISCV_PREFIX)objdump --disassemble-all --disassemble-zeroes --section=.text --section=.text.startup --section=.text.init --section=.data --section=.text2 --section=.text3 --section=.text4
RISCV_SIM ?= spike

vpath %.S $(src_dir)

#-----------------------------------------------------------
# Build assembly tests

%.dump: %.riscv
	$(RISCV_OBJDUMP) $< > $@

%_p.riscv: %_p.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I./include -I./include/p -T include/link.ld $< -o $@

%_v.riscv: %_v.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -DENTROPY=0x$(shell echo \$@ | md5sum | cut -c 1-7) -std=gnu99 -O2 -I./include -I./include/v -T./include/link.ld ./include/v/*.c $< -o $@

#-----------------------------------------------------------
# Default

all: test_p.dump test_v.dump test_p.riscv test_v.riscv

#-----------------------------------------------------------
# Clean up

clean: 
	rm test_p.dump test_p.riscv test_v.dump test_v.riscv

