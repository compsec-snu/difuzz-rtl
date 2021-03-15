# DifuzzRTL: Differential Fuzz Testing to Find CPU Bugs 

## Introduction

DifuzzRTL is a differential fuzz testing approach for CPU verification. 
We introduce new coverage metric, *register-coverage*, which comprehensively captures the states of an RTL design and correctly guides the input generation.
DifuzzRTL automatically instruments *register-coverage*, randomly generates and mutates instructions defined in ISA, then cross-check against an ISA simulator to
detect bugs.

## Setup

### Prerequisite
1. [sbt][sbtlink] for FIRRTL

[sbtlink]: https://www.scala-sbt.org/

2. [verilator][verilatorlink] for RTL simulation

[verilatorlink]: https://github.com/verilator/verilator

3. [cocotb][cocotblink] for RTL simulation

[cocotblink]: https://docs.cocotb.org/en/stable/

4. [riscv][riscvlink] for RISC-V instruction mutation

[riscvlink]: https://github.com/riscv/riscv-gnu-toolchain.git

### Instructions

- For FPGA emulation using firesim

DifuzzRTL FPGA emulation relies on Berkeley firesim framework, which uses Amazon AWS.
Please follow the setup instructions in [firesim][firesimlink] for setting up AWS instances and firesim directory.
Then follow the instructions below.

[firesimlink]: https://docs.fires.im/en/latest/

```
git clone https://github.com/compsec-snu/difuzz-rtl DifuzzRTL
cd DifuzzRTL
git checkout fpga

cp -rf firesim $HOME/firesim
cp -rf firrtl $HOME/firesim/target-design/chipyard/tools/
cd $HOME/firesim

./build-setup.sh fast
source sourceme-f1-manager.sh
```

## Build Amazon FPGA Image

```
cd $HOME/firesim
source sourceme-f1-manager.sh
export DIFUZZRTL=1
export TOPMODULE=RocketTile

Uncomment 'difuzzrtl-rocket-singlecore-no-nic-l2-llc4mb-ddr3' in deploy/config_build.ini (and Comment all other configurations)

firesim buildafi
...

After build completes, you can find AGFI number in deploy/results-build/difuzzrtl-rocket-singlecore-no-nic-l2-llc4mb-ddr3/AGFI_INFO
Copy the AGFI number into deploy/config_hwdb.ini, under [difuzzrtl-rocket-singlecore-no-nic-l2-llc4mb-ddr3]
```

## Run DifuzzRTL using FPGA Emulation

``` 
cd $HOME/firesim
source sourceme-f1-manager.sh
export DIFUZZRTL=1
export TOPMODULE=RocketTile

Set 'defaulthwconfig=difuzzrtl-rocket-singlecore-no-nic-l2-llc4mb-ddr3' in deploy/config_runtime.ini

firesim launchrunfarm
firesim infrasetup
firesim runworkload
...

Above steps will start one FPGA instance and show ip address of that instance.
ssh to the instance.
```

In the FPGA instance

```
git clone https://github.com/compsec-snu/difuzz-rtl DifuzzRTL
cd DifuzzRTL
./setup.sh

source env.sh

cd Fuzzer
cat /home/centos/sim_slot0/uartlog
...

The log contains the output directory (<out>) for fuzzing and semaphore key value (<key>). 
...

./DifuzzRTL_fpga.py OUT=<out> KEY=<key> NUM_ITER=<num_iter> RECORD=1
```

**NUM_ITER**:  Number of fuzzing iterations to run  
**RECORD**:    Set 1 to record coverage log  
**DEBUG**:     Set 1 to print debug messages  




