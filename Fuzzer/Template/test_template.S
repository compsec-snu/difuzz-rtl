# Assembly file template for fuzzing

#*********************************************************************
# test_template.S
#---------------------------------------------------------------------

#include "riscv_test.h"
#include "test_macros.h"

#define TESTEND gp

RVTEST_RV64UF

// RVTEST_CODE_BEGIN
        .section .text.init;
        .align  6;
        .weak stvec_handler;
        .weak mtvec_handler;
        .global _start;
_start:
        /* reset vector */
        addi x1,zero,0;
        addi x2,zero,0;
        addi x3,zero,0;
        addi x4,zero,0;
        addi x5,zero,0;
        addi x6,zero,0;
        addi x7,zero,0;
        addi x8,zero,0;
        addi x9,zero,0;
        addi x10,zero,0;
        addi x11,zero,0;
        addi x12,zero,0;
        addi x13,zero,0;
        addi x14,zero,0;
        addi x15,zero,0;
        addi x16,zero,0;
        addi x17,zero,0;
        addi x18,zero,0;
        addi x19,zero,0;
        addi x20,zero,0;
        addi x21,zero,0;
        addi x22,zero,0;
        addi x23,zero,0;
        addi x24,zero,0;
        addi x25,zero,0;
        addi x26,zero,0;
        addi x27,zero,0;
        addi x28,zero,0;
        addi x29,zero,0;
        addi x30,zero,0;
        addi x31,zero,0;
        j reset_vector;
        .align;
init_freg:
        fcvt.s.lu f0,zero
        fcvt.s.lu f1,zero
        fcvt.s.lu f2,zero
        fcvt.s.lu f3,zero
        fcvt.s.lu f4,zero
        fcvt.s.lu f5,zero
        fcvt.s.lu f6,zero
        fcvt.s.lu f7,zero
        fcvt.s.lu f8,zero
        fcvt.s.lu f9,zero
        fcvt.s.lu f10,zero
        fcvt.s.lu f11,zero
        fcvt.s.lu f12,zero
        fcvt.s.lu f13,zero
        fcvt.s.lu f14,zero
        fcvt.s.lu f15,zero
        fcvt.s.lu f16,zero
        fcvt.s.lu f17,zero
        fcvt.s.lu f18,zero
        fcvt.s.lu f19,zero
        fcvt.s.lu f20,zero
        fcvt.s.lu f21,zero
        fcvt.s.lu f22,zero
        fcvt.s.lu f23,zero
        fcvt.s.lu f24,zero
        fcvt.s.lu f25,zero
        fcvt.s.lu f26,zero
        fcvt.s.lu f27,zero
        fcvt.s.lu f28,zero
        fcvt.s.lu f29,zero
        fcvt.s.lu f30,zero
        fcvt.s.lu f31,zero
        ret
        .align;
trap_stvec:
        ecall
trap_mtvec: 
_fuzz_trap:
        /* Fuzz trap */
        /**/

  csr_dump:
        la x1, csr_output_data
        // csrr x2, ustatus
        // sd x2, 0(x1)   
        // csrr x2, uie
        // sd x2, 8(x1)       
        // csrr x2, utvec
        // sd x2, 16(x1)     
        // csrr x2, uscratch
        // sd x2, 24(x1)  
        // csrr x2, uepc
        // sd x2, 32(x1)      
        // csrr x2, ucause
        // sd x2, 40(x1)    
        // csrr x2, utval
        // sd x2, 48(x1)     
        // csrr x2, uip
        // sd x2, 56(x1)       
        // csrr x2, fflags
        // sd x2, 64(x1)     
        // csrr x2, frm
        // sd x2, 72(x1)       
        // csrr x2, fcsr
        // sd x2, 80(x1)      
        csrr x2, sstatus
        sd x2, 88(x1)   
        # csrr x2, sedeleg
        # sd x2, 96(x1)   
        # csrr x2, sideleg
        # sd x2, 104(x1)   
        csrr x2, sie
        sd x2, 112(x1)       
        csrr x2, stvec
        sd x2, 120(x1)     
        csrr x2, scounteren
        sd x2, 128(x1)
        csrr x2, sscratch
        sd x2, 136(x1)  
        csrr x2, sepc
        sd x2, 144(x1)      
        csrr x2, scause
        sd x2, 152(x1)    
        csrr x2, stval
        sd x2, 160(x1)     
        csrr x2, sip
        andi x2, x2, -0x81
        sd x2, 168(x1)       
        csrr x2, satp
        sd x2, 176(x1)      
        csrr x2, mhartid
        sd x2, 184(x1)   
        csrr x2, mstatus
        sd x2, 192(x1)   
        csrr x2, medeleg
        sd x2, 200(x1)   
        csrr x2, mideleg
        sd x2, 208(x1)   
        csrr x2, mie
        sd x2, 216(x1)       
        csrr x2, mtvec
        sd x2, 224(x1)     
        csrr x2, mcounteren
        sd x2, 232(x1)
        csrr x2, mscratch
        sd x2, 240(x1)  
        csrr x2, mepc
        sd x2, 248(x1)      
        csrr x2, mcause
        sd x2, 256(x1)    
        csrr x2, mtval
        sd x2, 264(x1)     
        csrr x2, mip
        andi x2, x2, -0x81
        sd x2, 272(x1)       
        csrr x2, pmpcfg0
        sd x2, 280(x1)   
        // csrr x2, pmpcfg1
        // sd x2, 288(x1)   
        // csrr x2, pmpcfg2
        // sd x2, 296(x1)   
        // csrr x2, pmpcfg3
        // sd x2, 304(x1)   
        csrr x2, pmpaddr0
        sd x2, 312(x1)  
        csrr x2, pmpaddr1
        sd x2, 320(x1)  
        csrr x2, pmpaddr2
        sd x2, 328(x1)  
        csrr x2, pmpaddr3
        sd x2, 336(x1)  
        csrr x2, pmpaddr4
        sd x2, 344(x1)  
        csrr x2, pmpaddr5
        sd x2, 352(x1)  
        csrr x2, pmpaddr6
        sd x2, 360(x1)  
        csrr x2, pmpaddr7
        sd x2, 368(x1)  
        // csrr x2, pmpaddr8
        // sd x2, 376(x1)  
        // csrr x2, pmpaddr9
        // sd x2, 384(x1)  
        // csrr x2, pmpaddr10
        // sd x2, 392(x1) 
        // csrr x2, pmpaddr11
        // sd x2, 400(x1) 
        // csrr x2, pmpaddr12
        // sd x2, 408(x1) 
        // csrr x2, pmpaddr13
        // sd x2, 416(x1) 
        // csrr x2, pmpaddr14
        // sd x2, 424(x1) 
        // csrr x2, pmpaddr15
        // sd x2, 432(x1) 
        // csrr x2, mcycle
        // sd x2, 440(x1) 
        // csrr x2, minstret    
        // sd x2, 448(x1) 
        // csrr x2, mcycleh
        // sd x2, 456(x1)
        // csrr x2, minstreth   
        // sd x2, 464(x1)

        li a0, (MSTATUS_FS & (MSTATUS_FS >> 0)) 
        csrs mstatus, a0

  fcsrs_dump:
        csrr x2, fflags
        sd x2, 64(x1)     
        csrr x2, frm
        sd x2, 72(x1)       
        csrr x2, fcsr
        sd x2, 80(x1)      

  reg_dump:
        la x1, xreg_output_data
        sd x0, 0(x1)
        sd x2, 16(x1)
        sd x3, 24(x1)
        sd x4, 32(x1)
        sd x5, 40(x1)
        sd x6, 48(x1)
        sd x7, 56(x1)
        sd x8, 64(x1)
        sd x9, 72(x1)
        sd x10, 80(x1)
        sd x11, 88(x1)
        sd x12, 96(x1)
        sd x13, 104(x1)
        sd x14, 112(x1)
        sd x15, 120(x1)
        sd x16, 128(x1)
        sd x17, 136(x1)
        sd x18, 144(x1)
        sd x19, 152(x1)
        sd x20, 160(x1)
        sd x21, 168(x1)
        sd x22, 176(x1)
        sd x23, 184(x1)
        sd x24, 192(x1)
        sd x25, 200(x1)
        sd x27, 216(x1)
        sd x28, 224(x1)
        sd x29, 232(x1)
        sd x30, 240(x1)
  
  freg_dump:
        la x1, freg_output_data
        fsw f1, 8(x1)
        fsw f2, 16(x1)
        fsw f7, 56(x1)
        fsw f9, 72(x1)
        fsw f10, 80(x1)
        fsw f12, 96(x1)
        fsw f13, 104(x1)
        fsw f21, 168(x1)
        fsw f22, 176(x1)
        fsw f25, 200(x1)
        fsw f26, 208(x1)
        fsw f28, 224(x1)
        fsw f29, 232(x1)
        fsw f30, 240(x1)
        fsw f31, 248(x1)
        
        la x1, freg_output_data
        fsd f0, 0(x1)
        fsd f3, 24(x1)
        fsd f4, 32(x1)
        fsd f5, 40(x1)
        fsd f6, 48(x1)
        fsd f8, 64(x1)
        fsd f11, 88(x1)
        fsd f14, 112(x1)
        fsd f15, 120(x1)
        fsd f16, 128(x1)
        fsd f17, 136(x1)
        fsd f18, 144(x1)
        fsd f19, 152(x1)
        fsd f20, 160(x1)
        fsd f23, 184(x1)
        fsd f24, 192(x1)
        fsd f27, 216(x1)
        
  write_tohost:
        li TESTEND, 1
        sw TESTEND, tohost, t5;
  _test_end:
        j _test_end;
reset_vector:
        RISCV_MULTICORE_DISABLE;
        INIT_SATP;
        INIT_PMP;
        DELEGATE_NO_TRAPS;
        li TESTEND, 0;
        la t0, trap_mtvec;
        csrw mtvec, t0;
        CHECK_XLEN;
        la t0, trap_stvec;
        beqz t0, 1f;
        csrw stvec, t0;
        li t0, (1 << CAUSE_LOAD_PAGE_FAULT) | \
                (1 << CAUSE_STORE_PAGE_FAULT) | \
                (1 << CAUSE_FETCH_PAGE_FAULT) | \
                (1 << CAUSE_MISALIGNED_FETCH) | \
                (1 << CAUSE_USER_ECALL) | \
                (1 << CAUSE_BREAKPOINT);
  1:    csrwi mstatus, 0;
        init;
        EXTRA_INIT;
        EXTRA_INIT_TIMER;
        la t0, main;
        csrw mepc, t0
        csrr a0, mhartid;
        addi t0,zero,0;
        li a0, (MSTATUS_FS & (MSTATUS_FS >> 0)) | \
                (MSTATUS_MPP & (MSTATUS_MPP >> 0));
        csrs mstatus, a0;
        csrwi fcsr, 0;
        call init_freg
        csrw minstret,zero
        mret;
  1:
      
      .align 6;

main:
_fuzz_main:
        /* Fuzz main */
        /**/
// RVTEST_CODE_END
        ecall
        unimp
_end_fuzz:
        
    .data

RVTEST_DATA_BEGIN

        .align 8
xreg_output_data:
reg_x0_output:  .dword 0x0000000000000000
reg_x1_output:  .dword 0x0000000000000000
reg_x2_output:  .dword 0x0000000000000000
reg_x3_output:  .dword 0x0000000000000000
reg_x4_output:  .dword 0x0000000000000000
reg_x5_output:  .dword 0x0000000000000000
reg_x6_output:  .dword 0x0000000000000000
reg_x7_output:  .dword 0x0000000000000000
reg_x8_output:  .dword 0x0000000000000000
reg_x9_output:  .dword 0x0000000000000000
reg_x10_output: .dword 0x0000000000000000
reg_x11_output: .dword 0x0000000000000000
reg_x12_output: .dword 0x0000000000000000
reg_x13_output: .dword 0x0000000000000000
reg_x14_output: .dword 0x0000000000000000
reg_x15_output: .dword 0x0000000000000000
reg_x16_output: .dword 0x0000000000000000
reg_x17_output: .dword 0x0000000000000000
reg_x18_output: .dword 0x0000000000000000
reg_x19_output: .dword 0x0000000000000000
reg_x20_output: .dword 0x0000000000000000
reg_x21_output: .dword 0x0000000000000000
reg_x22_output: .dword 0x0000000000000000
reg_x23_output: .dword 0x0000000000000000
reg_x24_output: .dword 0x0000000000000000
reg_x25_output: .dword 0x0000000000000000
reg_x26_output: .dword 0x0000000000000000
reg_x27_output: .dword 0x0000000000000000
reg_x28_output: .dword 0x0000000000000000
reg_x29_output: .dword 0x0000000000000000
reg_x30_output: .dword 0x0000000000000000
reg_x31_output: .dword 0x0000000000000000

        .align 8
freg_output_data:
reg_f0_output:  .dword 0x0000000000000000
reg_f1_output:  .dword 0x0000000000000000
reg_f2_output:  .dword 0x0000000000000000
reg_f3_output:  .dword 0x0000000000000000
reg_f4_output:  .dword 0x0000000000000000
reg_f5_output:  .dword 0x0000000000000000
reg_f6_output:  .dword 0x0000000000000000
reg_f7_output:  .dword 0x0000000000000000
reg_f8_output:  .dword 0x0000000000000000
reg_f9_output:  .dword 0x0000000000000000
reg_f10_output: .dword 0x0000000000000000
reg_f11_output: .dword 0x0000000000000000
reg_f12_output: .dword 0x0000000000000000
reg_f13_output: .dword 0x0000000000000000
reg_f14_output: .dword 0x0000000000000000
reg_f15_output: .dword 0x0000000000000000
reg_f16_output: .dword 0x0000000000000000
reg_f17_output: .dword 0x0000000000000000
reg_f18_output: .dword 0x0000000000000000
reg_f19_output: .dword 0x0000000000000000
reg_f20_output: .dword 0x0000000000000000
reg_f21_output: .dword 0x0000000000000000
reg_f22_output: .dword 0x0000000000000000
reg_f23_output: .dword 0x0000000000000000
reg_f24_output: .dword 0x0000000000000000
reg_f25_output: .dword 0x0000000000000000
reg_f26_output: .dword 0x0000000000000000
reg_f27_output: .dword 0x0000000000000000
reg_f28_output: .dword 0x0000000000000000
reg_f29_output: .dword 0x0000000000000000
reg_f30_output: .dword 0x0000000000000000
reg_f31_output: .dword 0x0000000000000000

        .align 8
csr_output_data:
ustatus_output:        .dword 0x0000000000000000                   
uie_output:            .dword 0x0000000000000000               
utvec_output:          .dword 0x0000000000000000                 
uscratch_output:       .dword 0x0000000000000000                    
uepc_output:           .dword 0x0000000000000000                
ucause_output:         .dword 0x0000000000000000                  
utval_output:          .dword 0x0000000000000000                 
uip_output:            .dword 0x0000000000000000               
fflags_output:         .dword 0x0000000000000000                 
frm_output:            .dword 0x0000000000000000               
fcsr_output:           .dword 0x0000000000000000                
sstatus_output:        .dword 0x0000000000000000                   
sedeleg_output:        .dword 0x0000000000000000                   
sideleg_output:        .dword 0x0000000000000000                   
sie_output:            .dword 0x0000000000000000               
stvec_output:          .dword 0x0000000000000000                 
scounteren_output:     .dword 0x0000000000000000                      
sscratch_output:       .dword 0x0000000000000000                    
sepc_output:           .dword 0x0000000000000000                
scause_output:         .dword 0x0000000000000000                  
stval_output:          .dword 0x0000000000000000                 
sip_output:            .dword 0x0000000000000000               
satp_output:           .dword 0x0000000000000000                
mhartid_output:        .dword 0x0000000000000000                   
mstatus_output:        .dword 0x0000000000000000                   
medeleg_output:        .dword 0x0000000000000000                   
mideleg_output:        .dword 0x0000000000000000                   
mie_output:            .dword 0x0000000000000000               
mtvec_output:          .dword 0x0000000000000000                 
mcounteren_output:     .dword 0x0000000000000000                      
mscratch_output:       .dword 0x0000000000000000                    
mepc_output:           .dword 0x0000000000000000                
mcause_output:         .dword 0x0000000000000000                  
mtval_output:          .dword 0x0000000000000000                 
mip_output:            .dword 0x0000000000000000               
pmpcfg0_output:        .dword 0x0000000000000000                   
pmpcfg1_output:        .dword 0x0000000000000000                   
pmpcfg2_output:        .dword 0x0000000000000000                   
pmpcfg3_output:        .dword 0x0000000000000000                   
pmpaddr0_output:       .dword 0x0000000000000000                    
pmpaddr1_output:       .dword 0x0000000000000000                    
pmpaddr2_output:       .dword 0x0000000000000000                    
pmpaddr3_output:       .dword 0x0000000000000000                    
pmpaddr4_output:       .dword 0x0000000000000000                    
pmpaddr5_output:       .dword 0x0000000000000000                    
pmpaddr6_output:       .dword 0x0000000000000000                    
pmpaddr7_output:       .dword 0x0000000000000000                    
pmpaddr8_output:       .dword 0x0000000000000000                    
pmpaddr9_output:       .dword 0x0000000000000000                    
pmpaddr10_output:      .dword 0x0000000000000000                     
pmpaddr11_output:      .dword 0x0000000000000000                     
pmpaddr12_output:      .dword 0x0000000000000000                     
pmpaddr13_output:      .dword 0x0000000000000000                     
pmpaddr14_output:      .dword 0x0000000000000000                     
pmpaddr15_output:      .dword 0x0000000000000000                     
mcycle_output:         .dword 0x0000000000000000                         
minstret_output:       .dword 0x0000000000000000                    
mcycleh_output:        .dword 0x0000000000000000                   
minstreth_output:      .dword 0x0000000000000000                     

RVTEST_DATA_END  

    .section .data.random0;
    .align  8;

_random_data0:
        /* Random data */
_end_data0:
                 

    .section .data.random1;
    .align  8;

_random_data1:
        /* Random data */
_end_data1:


    .section .data.random2;
    .align  8;

_random_data2:
         /* Random data */
_end_data2:
                

    .section .data.random3;
    .align  8;

_random_data3:
        /* Random data */
_end_data3:


    .section .data.random4;
    .align  8;

_random_data4:
        /* Random data */
_end_data4:


    .section .data.random5;
    .align  8;

_random_data5:
         /* Random data */
_end_data5:
              
                 
