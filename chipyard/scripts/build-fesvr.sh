#!/usr/bin/env bash

#this script is used to build only libfesvr
set -e
set -o pipefail

RDIR=$(pwd)
CHIPYARD_DIR="${CHIPYARD_DIR:-$(git rev-parse --show-toplevel)}"

TOOLCHAIN="riscv-tools"
INSTALL_DIR="$TOOLCHAIN-install"

RISCV="$(pwd)/$INSTALL_DIR"

echo "Installing libfesvr to $RISCV"

export RISCV="$RISCV"

cd "${CHIPYARD_DIR}"

SRCDIR="$(pwd)/toolchains/${TOOLCHAIN}"
. ./scripts/build-util.sh

module_build riscv-isa-sim --prefix="${RISCV}"
echo "==> Installing libfesvr static library"
module_make riscv-isa-sim libfesvr.a
cp -p "${SRCDIR}/riscv-isa-sim/build/libfesvr.a" "${RISCV}/lib/"

echo "libfesvr Build Completed!"
