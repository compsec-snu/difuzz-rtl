#1. Install ubuntu and default packages
FROM        ubuntu:20.04
MAINTAINER  subicura@subicura.com
RUN         apt update
RUN         apt install -y sudo vim gnupg

# Install scala
RUN         apt update
RUN         apt install -y scala 

# Install sbt
RUN sudo apt-get update
RUN sudo apt-get install apt-transport-https curl gnupg -yqq
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
RUN curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo -H gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import
RUN sudo chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg
RUN sudo apt-get update
RUN sudo apt-get install -y sbt

# # Install sbt
# RUN         apt update
# RUN         echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
# RUN         apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
# RUN         apt update
# RUN         apt install -y sbt

# Set root passwd and add user
RUN         adduser --disabled-password --gecos '' host
RUN         usermod -aG sudo host
RUN         sudo echo -e 'host:host' | chpasswd
RUN         su host

# Install cocotb-1.5.2
WORKDIR     /home/host
RUN         echo "host" | sudo -S apt install -y make gcc g++ python3 python3-dev python3-pip
RUN         sudo ln -s /usr/bin/python3 /usr/bin/python
# RUN         sudo ln -s /usr/bin/pip3 /usr/bin/pip
RUN         pip3 install cocotb==1.5.2

# Install verilator-v4.106
WORKDIR     /home/host
RUN         sudo apt install -y autoconf flex bison
RUN         git clone https://github.com/verilator/verilator.git
WORKDIR     /home/host/verilator
RUN         git checkout v4.106
RUN         autoconf
RUN         ./configure
RUN         make -j4
RUN         sudo make install

# Install riscv-toolchain
WORKDIR     /home/host
RUN         sudo apt-get install -y autoconf automake autotools-dev curl python3 libmpc-dev libmpfr-dev libgmp-dev gawk build-essential bison flex texinfo gperf libtool patchutils bc zlib1g-dev libexpat-dev
RUN         git clone https://github.com/riscv/riscv-gnu-toolchain.git
WORKDIR     /home/host/riscv-gnu-toolchain
RUN         git checkout 2021.04.23
RUN         ./configure --prefix=/opt/riscv
RUN         sudo make -j4
RUN         export PATH=/opt/riscv/bin:$PATH

# Run DiFuzzRTL
WORKDIR     /home/host
RUN         sudo apt install -y device-tree-compiler
RUN         pip3 install psutil sysv_ipc
RUN         git clone https://github.com/compsec-snu/difuzz-rtl.git
RUN         chown -R host /home/host/difuzz-rtl

ENV         PATH="/opt/riscv/bin:${PATH}"
