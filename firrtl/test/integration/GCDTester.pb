
?"
??
DecoupledGCD
clock" 
reset
?
io?*?
TinL*J
ready

valid

$bits*
a
 
b
 
=out6*4
ready

valid

bits
 ?


io?
 81
busy
	

clock"	

reset*	

0?
 81
done
	

clock"	

reset*	

0?
 1*
x
 	

clock"	

0*

x?
 1*
y
 	

clock"	

0*

y?
 *2#
T_40R

busy	

0?
 ,z%
:
:


ioinready

T_40?
 -z&
:
:


iooutvalid

done?
 '2 
T_42R

y	

0?
 '2 
T_43R

busy

T_42?
 1:*


T_43z


done	

1?
 ?
 922
T_45*R(

done:
:


iooutready?
 1:*


T_45z


busy	

0?
 ?
 J2C
start:R8:
:


ioinvalid:
:


ioinready?
 R:K
	

startz


busy	

1?
 z


done	

0?
 ?
 !2
T_50R

x

y?
 r:k


T_50!2
T_51R

x

y?
 "2
T_52R

T_51
1?
 z


x

T_52?
 ?
 *2#
T_54R

T_50	

0?
 r:k


T_54!2
T_55R

y

x?
 "2
T_56R

T_55
1?
 z


y

T_56?
 ?
 t:m
	

start/z(


x:
:
:


ioinbitsa?
 /z(


y:
:
:


ioinbitsb?
 ?
 )z"
:
:


iooutbits

x?
 
??
	GCDTester
clock" 
reset


io* ?


io?
 *
dutDecoupledGCD?
 ?
:


dutio?
 &z
:


dutclock	

clock?
 &z
:


dutreset	

reset?
 92
count
	

clock"	

reset*	

9?
 

a2



?
 %z
B


a
0


46?
 %z
B


a
1


95?
 %z
B


a
2


26?
 %z
B


a
3


61?
 %z
B


a
4


18?
 %z
B


a
5


51?
 %z
B


a
6


45?
 %z
B


a
7


66?
 %z
B


a
8


71?
 %z
B


a
9


71?
 

b2



?
 %z
B


b
0


39?
 %z
B


b
1


44?
 %z
B


b
2


37?
 %z
B


b
3


96?
 %z
B


b
4


48?
 %z
B


b
5


39?
 %z
B


b
6


85?
 %z
B


b
7


84?
 $z
B


b
8	

8?
 %z
B


b
9


80?
 

z2



?
 $z
B


z
0	

1?
 $z
B


z
1	

1?
 $z
B


z
2	

1?
 $z
B


z
3	

1?
 $z
B


z
4	

6?
 $z
B


z
5	

3?
 $z
B


z
6	

5?
 $z
B


z
7	

6?
 $z
B


z
8	

1?
 $z
B


z
9	

1?
 9z2
#:!
:
:


dutiooutready	

0?
 6/
en
	

clock"	

reset*	

1?
 Gz@
(:&
!:
:
:


dutioinbitsaJ


a	

count?
 Gz@
(:&
!:
:
:


dutioinbitsbJ


b	

count?
 3z,
": 
:
:


dutioinvalid

en?
 ?28
T_800R.

en": 
:
:


dutioinready?
 /:(


T_80z


en	

0?
 ?
 (2!
T_83R

en	

0?
 B2;
T_843R1#:!
:
:


dutiooutvalid

T_83?
 ?:?


T_849z2
#:!
:
:


dutiooutready	

1?
 M2F
T_87>R<": 
:
:


dutiooutbitsJ


z	

count?
 +2$
T_89R	

reset	

0?
 ?:?


T_89*2#
T_91R

T_87	

0?
 ?:?


T_91+2$
T_93R	

reset	

0?
 ?:


T_93sRl
RAssertion failed
    at GCDTester.scala:38 assert( dut.io.out.bits === z(count) )
	

clock"	

1?
 ?
 !B	

clock	

1?
 ?
 ?
 +2$
T_95R	

count	

0?
 r:k


T_95+2$
T_97R	

reset	

0?
 2:+


T_97B	

clock	

1?
 ?
 ?
 *2#
T_99R

T_95	

0?
 ?:?


T_99z


en	

1?
 ,2%
T_102R	

count	

1?
 $2
T_103R	

T_102
1?
 z
	

count	

T_103?
 ?
 ?
 
	GCDTester