#!/bin/bash

copy () {
    rsync -avzp -e 'ssh' $1 $2
}

run () {
    ssh -o "StrictHostKeyChecking no" -t $SERVER $@
}

run_script () {
    ssh -o "StrictHostKeyChecking no" -t $SERVER 'bash -s' < $1 "$2"
}

clean () {
    # remove remote work dir
    run "rm -rf $REMOTE_WORK_DIR"
}

# make parallelism
CI_MAKE_NPROC=8
# chosen based on a 24c system shared with 1 other project
REMOTE_MAKE_NPROC=4

# verilator version
VERILATOR_VERSION=v4.034

# remote variables
REMOTE_PREFIX=$CI_DIR/$CIRCLE_PROJECT_REPONAME-$CIRCLE_BRANCH
REMOTE_WORK_DIR=$REMOTE_PREFIX-$CIRCLE_SHA1-$CIRCLE_JOB
REMOTE_RISCV_DIR=$REMOTE_WORK_DIR/riscv-tools-install
REMOTE_ESP_DIR=$REMOTE_WORK_DIR/esp-tools-install
REMOTE_CHIPYARD_DIR=$REMOTE_WORK_DIR/chipyard
REMOTE_SIM_DIR=$REMOTE_CHIPYARD_DIR/sims/verilator
REMOTE_FIRESIM_DIR=$REMOTE_CHIPYARD_DIR/sims/firesim/sim
# Disable the supershell to greatly improve the readability of SBT output when captured by Circle CI
REMOTE_JAVA_ARGS="-Xmx9G -Xss8M -Dsbt.ivy.home=$REMOTE_WORK_DIR/.ivy2 -Dsbt.supershell=false -Dsbt.global.base=$REMOTE_WORK_DIR/.sbt -Dsbt.boot.directory=$REMOTE_WORK_DIR/.sbt/boot"
REMOTE_VERILATOR_DIR=$REMOTE_PREFIX-$CIRCLE_SHA1-verilator-install

# local variables (aka within the docker container)
LOCAL_CHECKOUT_DIR=$HOME/project
LOCAL_RISCV_DIR=$HOME/riscv-tools-install
LOCAL_ESP_DIR=$HOME/esp-tools-install
LOCAL_CHIPYARD_DIR=$LOCAL_CHECKOUT_DIR
LOCAL_SIM_DIR=$LOCAL_CHIPYARD_DIR/sims/verilator
LOCAL_FIRESIM_DIR=$LOCAL_CHIPYARD_DIR/sims/firesim/sim

# key value store to get the build strings
declare -A mapping
mapping["chipyard-rocket"]="SUB_PROJECT=chipyard"
mapping["chipyard-sha3"]="SUB_PROJECT=chipyard CONFIG=Sha3RocketConfig"
mapping["chipyard-streaming-fir"]="SUB_PROJECT=chipyard CONFIG=StreamingFIRRocketConfig"
mapping["chipyard-streaming-passthrough"]="SUB_PROJECT=chipyard CONFIG=StreamingPassthroughRocketConfig"
mapping["chipyard-hetero"]="SUB_PROJECT=chipyard CONFIG=LargeBoomAndRocketConfig"
mapping["chipyard-boom"]="SUB_PROJECT=chipyard CONFIG=SmallBoomConfig"
mapping["chipyard-blkdev"]="SUB_PROJECT=chipyard CONFIG=SimBlockDeviceRocketConfig"
mapping["chipyard-hwacha"]="SUB_PROJECT=chipyard CONFIG=HwachaRocketConfig"
mapping["chipyard-gemmini"]="SUB_PROJECT=chipyard CONFIG=GemminiRocketConfig"
mapping["chipyard-ariane"]="SUB_PROJECT=chipyard CONFIG=ArianeConfig"
mapping["chipyard-spiflashread"]="SUB_PROJECT=chipyard CONFIG=LargeSPIFlashROMRocketConfig"
mapping["chipyard-spiflashwrite"]="SUB_PROJECT=chipyard CONFIG=SmallSPIFlashRocketConfig"
mapping["tracegen"]="SUB_PROJECT=chipyard CONFIG=NonBlockingTraceGenL2Config TOP=TraceGenSystem"
mapping["tracegen-boom"]="SUB_PROJECT=chipyard CONFIG=BoomTraceGenConfig TOP=TraceGenSystem"
mapping["chipyard-nvdla"]="SUB_PROJECT=chipyard CONFIG=SmallNVDLARocketConfig"
mapping["firesim"]="SCALA_TEST=firesim.firesim.RocketNICF1Tests"
mapping["firesim-multiclock"]="SCALA_TEST=firesim.firesim.RocketMulticlockF1Tests"
mapping["fireboom"]="SCALA_TEST=firesim.firesim.BoomF1Tests"
mapping["icenet"]="SUB_PROJECT=icenet"
mapping["testchipip"]="SUB_PROJECT=testchipip"
