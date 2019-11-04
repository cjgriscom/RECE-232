# Details
WIP

# Message Encoding
	  Message length is always nLongwords * 8 + 3
    
    typ is the 5-bit typecode for the message
    fletC is a 5-bit 'current' abbreviated fletcher/crc sum to aid in progressive error correction
    msg is a 32-bit message with a 6-bit XOR checksum that may recover from one corrupted or dropped 6-bit character
    fletF is a full 16-bit fletcher/crc sum to confirm the entire message (pulls from pre-ascii 5,6-bit chars)

    We always alternate between 5- and 6-bit characters so that positional error recovery is easily performed (esp. dropped chars)

    5-bit character ranges is ascii 32-63
    6-bit character ranges is ascii 64-127
    These ranges avoid overlap so that the even and odd characters may be distinguished

    1x longword example
    (typ 5)(msg 6565656)(fletF 565)
    x longword example
    (typ 5)(msg 6565656)(fletC 5)(msg 6565656)(fletF 565)

