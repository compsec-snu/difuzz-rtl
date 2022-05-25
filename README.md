# DifuzzRTL: Differential Fuzz Testing to Find CPU Bugs 

## Introduction

DifuzzRTL is a differential fuzz testing approach for CPU verification. 
We introduce new coverage metric, *register-coverage*, which comprehensively captures the states of an RTL design and correctly guides the input generation.
DifuzzRTL automatically instruments *register-coverage*, randomly generates and mutates instructions defined in ISA, then cross-check against an ISA simulator to
detect bugs.
DiFuzzRTL is accepted at IEEE S&P 2021 ([paper][paperlink])

[paperlink]: https://www.computer.org/csdl/proceedings-article/sp/2021/893400b778/1t0x9G4Q5MI

## Setup

### Prerequisite
Please install the correct versions!

1. [sbt][sbtlink] for FIRRTL

[sbtlink]: https://www.scala-sbt.org/

2. [verilator][verilatorlink] for RTL simulation (v4.106)

[verilatorlink]: https://github.com/verilator/verilator

3. [cocotb][cocotblink] for RTL simulation (1.5.2)

[cocotblink]: https://docs.cocotb.org/en/stable/

4. [riscv][riscvlink] for RISC-V instruction mutation (2021.04.23)

[riscvlink]: https://github.com/riscv/riscv-gnu-toolchain.git

### Instructions

- For RTL simulation using verilator

```
git clone https://github.com/compsec-snu/difuzz-rtl
cd DifuzzRTL
git checkout sim

. ./setup.sh
```

## Instrumentation

```
cd firrtl
sbt compile; sbt assembly
./utils/bin/firrtl -td regress -i regress/<target_fir> -fct coverage.regCoverage -X verilog -o <output_verilog>
``` 

**target_fir**:     Firrtl file to instrument  
**output_verilog**: Output verilog file

## Run

```
cd Fuzzer
make SIM_BUILD=<build_dir> VFILE=<target> TOPLEVEL=<topmodule> NUM_ITER=<num_iter> OUT=<outdir>
```

**SIM_BUILD**: Directory for RTL simulation binary build by cocotb  
**VFILE**:     Target RTL design in DifuzzRTL/Benchmarks/Verilog/  
           (e.g., RocketTile_state, SmallBoomTile_v_1.2_state, SmallBoomTile_v1.3_state)  
**TOPLEVEL**:  Top-level module  
           (e.g., RocketTile or BoomTile)  
**NUM_ITER**:  Number of fuzzing iterations to run  
**OUT**:       Output directory  
**RECORD**:    Set 1 to record coverage log  
**DEBUG**:     Set 1 to print debug messages  




