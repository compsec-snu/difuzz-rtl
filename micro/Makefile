# Verilator COCOTB Makefile

CWD=$(shell pwd)

SIM=verilator 
CMD=verilator

PYTHONPATH := /usr/local/bin:$(PYTHONPATH)

TOPLEVEL_LANG ?= verilog

TOPLEVEL=mem_ctrl
ifeq ($(COV),rand)
	VERILOG_SOURCES=$(CWD)/verilog/$(TOPLEVEL)_$(V)_reg.v
else
	VERILOG_SOURCES=$(CWD)/verilog/$(TOPLEVEL)_$(V)_$(COV).v
endif

COCOTB_HDL_TIMEUNIT=1us
COCOTB_HDL_TIMEPRECISION=1us

OUT ?= output

COMPILE_ARGS=--trace-underscore -DMULTICORE -Wno-PINMISSING
MODULE=fuzz_tb

CUSTOM_SIM_DEPS=$(CWD)/Makefile

include $(shell cocotb-config --makefiles)/Makefile.inc
include $(shell cocotb-config --makefiles)/Makefile.sim

sim: $(MODULE).py 


