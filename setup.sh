#!/bin/bash

# Install elf2hex
git clone https://github.com/sifive/elf2hex.git
pushd elf2hex > /dev/null
autoreconf -i
./configure --target=riscv64-unknown-elf
make
sudo make install
popd > /dev/null

# Build riscv-isa-sim
pushd Fuzzer/ISASim/riscv-isa-sim > /dev/null
mkdir build
pushd build > /dev/null
echo $PWD
../configure --prefix=$PWD
make -j4
popd > /dev/null
popd > /dev/null

source env.sh
