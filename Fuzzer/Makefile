# Verilator COCOTB Makefile

CWD=$(shell pwd)

SIM=verilator
CMD=verilator

PYTHONPATH := $(CWD)/src:$(CWD)/RTLSim/src:$(PYTHONPATH)

TOPLEVEL_LAGN ?= verilog

TOPLEVEL ?= $(VFILE)
VERILOG_SOURCES = $(CWD)/../Benchmarks/Verilog/$(VFILE).v
COCOTB_HDL_TIMEUNIT=1us
COCOTB_HDL_TIMEPRECISION=1us

OUT ?= output

ifdef DEBUG
    PRINT=$(DEBUG)
    PRINTF_COND=0
else
    PRINT=0
endif

ifdef MULTICORE
    MULTI=-DMULTICORE
    PLUSARGS_OUT=+OUT=$(OUT)
else
    MULTI=
    PLUSARGS_OUT=
endif

ifeq ($(VFILE), RocketTile_VHarness)
    INCLUDE=-I $(CWD)/../Benchmarks/Verilog/RocketTile_mux.v
else
    INCLUDE=
endif

PRINTF_COND ?= 0
STOP_COND ?= 0

COMPILE_ARGS=$(MULTI) $(INCLUDE) -DPRINTF_COND=$(PRINTF_COND) -DSTOP_COND=$(STOP_COND) -Wno-PINMISSING
PLUSARGS=+DEBUG=$(PRINT) $(PLUSARGS_OUT)

MODULE=DifuzzRTL

CUSTOM_SIM_DEPS=$(CWD)/Makefile

include $(shell cocotb-config --makefiles)/Makefile.inc
include $(shell cocotb-config --makefiles)/Makefile.sim

sim: $(MODULE).py
