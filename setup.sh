#!/bin/bash

git submodule update --init

sudo yum install -y python3 python-devel python3-devel
sudo pip3 install cocotb sysv_ipc psutil

source env_setup.sh

# Install latest git
sudo yum remove -y git*
sudo yum install -y https://packages.endpoint.com/rhel/7/os/x86_64/endpoint-repo-1.7-1.x86_64.rpm
sudo yum install -y git

# Install riscv toolchains
sudo yum install -y dtc
pushd chipyard
./scripts/build-toolchains.sh ec2fast || true # Ignore QEMU build failure
popd

# Install riscv64-unknown-elf-elf2hex 
git clone https://github.com/sifive/elf2hex.git
pushd elf2hex
autoreconf -i
./configure --target=riscv64-unknown-elf --prefix=$RISCV
make
make install
popd

# Install riscv-isa-sim for DifuzzRTL
pushd Fuzzer/ISASim/riscv-isa-sim
mkdir build
pushd build
../configure --prefix=$pwd
make -j4
popd
popd

# Change privilege and source environment variables
echo "Do 'source env.sh'"
sudo su

