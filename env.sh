#!/bin/bash

export RISCV=/home/centos/DifuzzRTL/chipyard/riscv-tools-install
export SPIKE=/home/centos/DifuzzRTL/Fuzzer/ISASim/riscv-isa-sim/build/spike
export PATH=$RISCV/bin:$PATH
export PYTHONPATH=/home/centos/DifuzzRTL/Fuzzer/src:/home/centos/DifuzzRTL/Fuzzer/RTLSim/src:$PYTHONPATH

