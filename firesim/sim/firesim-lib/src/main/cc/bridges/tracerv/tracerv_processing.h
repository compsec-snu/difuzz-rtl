#ifndef TRACERV_PROCESSING_GUARD
#define TRACERV_PROCESSING_GUARD



#include <inttypes.h>
#include <vector>
#include <string>

#include <iostream>
#include <fstream>

#include <ctype.h>

class Instr
{
    public:
    std::string instval;
    uint64_t addr;
    std::string label;
    std::string function_name;
    bool is_fn_entry;
    bool is_callsite;
    bool in_asm_sequence;

    Instr()
    {
        is_callsite = false;
        is_fn_entry = false;
        in_asm_sequence = false;
    }

    void printMe() {
        printf("%s, %" PRIx64 ", %s\n", label.c_str(), addr, instval.c_str());
    }

    void printMeFile(FILE * printfile, std::string prefix) {
    }

};


class ObjdumpedBinary
{
    // base address subtracted before lookup into array
    uint64_t baseaddr;
    std::vector<Instr *> progtext;
public:
    ObjdumpedBinary(std::string binaryWithDwarf);
    Instr* getInstrFromAddr(uint64_t lookupaddress);
};

#endif
