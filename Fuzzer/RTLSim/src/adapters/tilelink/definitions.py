""" Protocol """
TL_UL               = 0x0
TL_UH               = 0x1
TL_C                = 0x2

""" Ports """

TL_A_FIELDS = [ 'opcode', 'param', 'size', 'source', 'address', 'mask', 'data', 'valid', 'ready' ]
TL_B_FIELDS = [ 'opcode', 'param', 'size', 'source', 'address', 'mask', 'data', 'valid', 'ready' ]
TL_C_FIELDS = [ 'opcode', 'param', 'size', 'source', 'address', 'data', 'corrupt', 'valid', 'ready' ]
TL_D_FIELDS = [ 'opcode', 'param', 'size', 'source', 'sink', 'data', 'corrupt', 'denied', 'valid', 'ready' ]
TL_E_FIELDS = [ 'sink', 'valid', 'ready' ]


""" Messages """
""" Master """
""" A """
GET                 = 0x4
PUT_FULL_DATA       = 0x0
PUT_PARTIAL_DATA    = 0x1
ARITHMETIC_DATA     = 0x2
LOGICAL_DATA        = 0x3
INTENT              = 0x5
ACQUIRE_BLOCK       = 0x6
ACQUIRE_PERM        = 0x7

""" C """
PROBE_ACK           = 0x4
PROBE_ACK_DATA      = 0x5
RELEASE             = 0x6
RELEASE_DATA        = 0x7

""" E """
#GRANT_ACK

""" Slave """
""" D """
ACCESS_ACK          = 0x0
ACCESS_ACK_DATA     = 0x1
HINT_ACK            = 0x2
GRANT               = 0x4
GRANT_DATA          = 0x5
RELEASE_ACK         = 0x6

""" B """
PROBE_BLOCK         = 0x6
PROBE_PERM          = 0x7


""" Parameters """
""" Master """
MIN                 = 0x0
MAX                 = 0x1
MINU                = 0x2
MAXU                = 0x3
ADD                 = 0x4
XOR                 = 0x0
OR                  = 0x1
AND                 = 0x2
SWAP                = 0x3

PREFETCH_READ       = 0x0
PREFETCH_WRITE      = 0x1

""" Permission """
NONE                = 0x0 # None
BRANCH              = 0x1 # Read (GET)
TRUNK               = 0x2 # None
TIP                 = 0x3 # Read&Write (GET, PUT)

""" Permission Transmissions """
""" Cap """
toT                 = 0x0
toB                 = 0x1
toN                 = 0x2

""" Grow """
NtoB                = 0x0
NtoT                = 0x1
BtoT                = 0x2

""" Prune """
TtoB                = 0x0
TtoN                = 0x1
BtoN                = 0x2

""" Report """
TtoT                = 0x3
BtoB                = 0x4
NtoN                = 0x5

""" Width """
A_OPCODE_MASK = 0x7
A_PARAM_MASK  = 0x7
