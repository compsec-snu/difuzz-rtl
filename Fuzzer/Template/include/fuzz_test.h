// Header file for fuzz testing

#ifdef INTERRUPT
#define init_mie \
  li a0, MSTATUS_MPIE | \
          MSTATUS_SPIE | \
          MSTATUS_UPIE; \
  csrs mstatus, a0; \
  li a0, MIP_MEIP | \
          MIP_SEIP | \
          MIP_MTIP | \
          MIP_MSIP; \
  csrwi mie, 0; \
  csrs mie, a0;

#define clear_sie \
  csrwi sie, 0;

#define clear_mie \
  csrwi mie, 0;
#else
#define init_mie  ;
#define clear_sie ;
#define clear_mie ;
#endif

#define INIT_XREGS              \
init_xreg:                      \
        la x31, _random_data0;  \
        ld x1 ,0(x31);         \
        ld x2 ,8(x31);         \
        ld x3 ,16(x31);        \
        ld x4 ,24(x31);        \
        ld x5 ,32(x31);        \
        ld x6 ,40(x31);        \
        ld x7 ,48(x31);        \
        ld x8 ,56(x31);        \
        ld x9 ,64(x31);        \
        ld x10,72(x31);        \
        ld x11,80(x31);        \
        ld x12,88(x31);        \
        ld x13,96(x31);        \
        ld x14,104(x31);        \
        ld x15,112(x31);        \
        ld x16,120(x31);        \
        ld x17,128(x31);        \
        ld x18,136(x31);        \
        ld x19,144(x31);        \
        ld x20,152(x31);        \
        ld x21,160(x31);        \
        ld x22,168(x31);        \
        ld x23,176(x31);        \
        ld x24,184(x31);        \
        ld x25,192(x31);        \
        ld x26,200(x31);        \
        ld x27,208(x31);        \
        ld x28,216(x31);        \
        ld x29,224(x31);        \
        ld x30,232(x31);        \
        ld x31,240(x31);        

#define INIT_FREGS          \
init_fregs:                 \
        la x31, _random_data1;  \
        flw f0, 0(x31);      \
        flw f1, 8(x31);      \
        flw f2, 16(x31);      \
        fld f3, 24(x31);      \
        fld f4, 32(x31);      \
        fld f5, 40(x31);      \
        fld f6, 48(x31);      \
        flw f7, 56(x31);      \
        fld f8, 64(x31);      \
        fld f9, 72(x31);      \
        flw f10,80(x31);      \
        fld f11,88(x31);     \
        fld f12,96(x31);     \
        fld f13,104(x31);     \
        flw f14,112(x31);     \
        fld f15,120(x31);     \
        fld f16,128(x31);     \
        flw f17,136(x31);     \
        flw f18,144(x31);     \
        flw f19,152(x31);     \
        fld f20,160(x31);     \
        flw f21,168(x31);     \
        fld f22,176(x31);     \
        fld f23,184(x31);     \
        fld f24,192(x31);     \
        fld f25,200(x31);     \
        fld f26,208(x31);     \
        fld f27,216(x31);     \
        fld f28,224(x31);     \
        flw f29,232(x31);     \
        flw f30,240(x31);     \
        flw f31,248(x31);     

#define DUMP_REGS           \
  csr_dump:     \
        la x1, csr_output_data;     \
        csrr x2, sstatus;       \
        sd x2, 88(x1);      \
        csrr x2, sie;       \
        sd x2, 112(x1);     \
        csrr x2, stvec;     \
        sd x2, 120(x1);     \
        csrr x2, scounteren;        \
        sd x2, 128(x1);     \
        csrr x2, sscratch;      \
        sd x2, 136(x1);     \
        csrr x2, sepc;      \
        sd x2, 144(x1);     \
        csrr x2, scause;        \
        sd x2, 152(x1);     \
        csrr x2, stval;     \
        sd x2, 160(x1);     \
        csrr x2, sip;       \
        andi x2, x2, -0x81;     \
        sd x2, 168(x1);     \
        csrr x2, satp;      \
        sd x2, 176(x1);     \
        csrr x2, mhartid;       \
        sd x2, 184(x1);     \
        csrr x2, mstatus;       \
        sd x2, 192(x1);     \
        csrr x2, medeleg;       \
        sd x2, 200(x1);     \
        csrr x2, mideleg;       \
        sd x2, 208(x1);     \
        csrr x2, mie;       \
        sd x2, 216(x1);     \
        csrr x2, mtvec;     \
        sd x2, 224(x1);     \
        csrr x2, mcounteren;        \
        sd x2, 232(x1);     \
        csrr x2, mscratch;      \
        sd x2, 240(x1);     \
        csrr x2, mepc;      \
        sd x2, 248(x1);     \
        csrr x2, mcause;        \
        sd x2, 256(x1);     \
        csrr x2, mtval;     \
        sd x2, 264(x1);     \
        csrr x2, mip;       \
        andi x2, x2, -0x81;     \
        sd x2, 272(x1);     \
        csrr x2, pmpcfg0;       \
        sd x2, 280(x1);     \
        csrr x2, pmpaddr0;      \
        sd x2, 312(x1);     \
        csrr x2, pmpaddr1;      \
        sd x2, 320(x1);     \
        csrr x2, pmpaddr2;      \
        sd x2, 328(x1);     \
        csrr x2, pmpaddr3;      \
        sd x2, 336(x1);     \
        csrr x2, pmpaddr4;      \
        sd x2, 344(x1);     \
        csrr x2, pmpaddr5;      \
        sd x2, 352(x1);     \
        csrr x2, pmpaddr6;      \
        sd x2, 360(x1);     \
        csrr x2, pmpaddr7;      \
        sd x2, 368(x1);     \
        li a0, (MSTATUS_FS & (MSTATUS_FS >> 0));        \
        csrs mstatus, a0;       \
  fcsrs_dump:       \
        csrr x2, fflags;        \
        sd x2, 64(x1);      \
        csrr x2, frm;       \
        sd x2, 72(x1);      \
        csrr x2, fcsr;      \
        sd x2, 80(x1);      \
  reg_dump:     \
        la x1, xreg_output_data;        \
        sd x0, 0(x1);       \
        sd x2, 16(x1);      \
        sd x3, 24(x1);      \
        sd x4, 32(x1);      \
        sd x5, 40(x1);      \
        sd x6, 48(x1);      \
        sd x7, 56(x1);      \
        sd x8, 64(x1);      \
        sd x9, 72(x1);      \
        sd x10, 80(x1);     \
        sd x11, 88(x1);     \
        sd x12, 96(x1);     \
        sd x13, 104(x1);        \
        sd x14, 112(x1);        \
        sd x15, 120(x1);        \
        sd x16, 128(x1);        \
        sd x17, 136(x1);        \
        sd x18, 144(x1);        \
        sd x19, 152(x1);        \
        sd x20, 160(x1);        \
        sd x21, 168(x1);        \
        sd x22, 176(x1);        \
        sd x23, 184(x1);        \
        sd x24, 192(x1);        \
        sd x25, 200(x1);        \
        sd x27, 216(x1);        \
        sd x28, 224(x1);        \
        sd x29, 232(x1);        \
        sd x30, 240(x1);        \
  freg_dump:        \
        la x1, freg_output_data;        \
        fsw f1, 8(x1);      \
        fsw f2, 16(x1);     \
        fsw f7, 56(x1);     \
        fsw f9, 72(x1);     \
        fsw f10, 80(x1);        \
        fsw f12, 96(x1);        \
        fsw f13, 104(x1);       \
        fsw f21, 168(x1);       \
        fsw f22, 176(x1);       \
        fsw f25, 200(x1);       \
        fsw f26, 208(x1);       \
        fsw f28, 224(x1);       \
        fsw f29, 232(x1);       \
        fsw f30, 240(x1);       \
        fsw f31, 248(x1);       \
        la x1, freg_output_data;        \
        fsd f0, 0(x1);      \
        fsd f3, 24(x1);     \
        fsd f4, 32(x1);     \
        fsd f5, 40(x1);     \
        fsd f6, 48(x1);     \
        fsd f8, 64(x1);     \
        fsd f11, 88(x1);        \
        fsd f14, 112(x1);       \
        fsd f15, 120(x1);       \
        fsd f16, 128(x1);       \
        fsd f17, 136(x1);       \
        fsd f18, 144(x1);       \
        fsd f19, 152(x1);       \
        fsd f20, 160(x1);       \
        fsd f23, 184(x1);       \
        fsd f24, 192(x1);       \
        fsd f27, 216(x1);       
 
#define PT_BASES    \
    .weak pt0;      \
    .weak pt1;      \
    .weak pt2;      \
    .weak pt3;      \
pt0:                \
pt1:                \
pt2:                \
pt3:                
