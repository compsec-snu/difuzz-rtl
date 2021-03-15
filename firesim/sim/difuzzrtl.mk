# See LICENSE for license details

# Variables for DifuzzRTL simulation

TARGET_CONFIG ?= DifuzzRTLRocketConfig
PLATFORM_CONFIG ?= DifuzzRTLF1Config

ifeq (, $(findstring DifuzzRTL, $(TARGET_CONFIG)))
    $(error Bad TARGET_CONFIG, please set DifuzzRTL configuration)
else
    ifeq (, $(findstring DifuzzRTL, $(PLATFORM_CONFIG)))
    	$(error Bad PLATFORM_CONFIG, please set DifuzzRTL configuration)
    endif
endif

override CXXFLAGS += -D DIFUZZRTL

OUTPUT ?= $(PWD)/fuzz_output
FUZZ_ARGS := +output=$(OUTPUT)
