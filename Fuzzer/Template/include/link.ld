OUTPUT_ARCH( "riscv" )
ENTRY(_start)

SECTIONS
{
  . = 0x80000000;
  .text.init : { *(.text.init) }
  . = ALIGN(0x1000);
  .tohost : { *(.tohost) }
  . = ALIGN(0x1000);
  .text : { *(.text) }
  . = ALIGN(0x1000);
  .data : { *(.data) }
  .bss : { *(.bss) }
  . = ALIGN(0x10000);
  .data.random0 : { *(.data.random0) }
  . = ALIGN(0x10000);
  .data.random1 : { *(.data.random1) }
  . = ALIGN(0x10000);
  .data.random2 : { *(.data.random2) }
  . = ALIGN(0x10000);
  .data.random3 : { *(.data.random3) }
  . = ALIGN(0x10000);
  .data.random4 : { *(.data.random4) }
  . = ALIGN(0x10000);
  .data.random5 : { *(.data.random5) }
  _end = .;
}

