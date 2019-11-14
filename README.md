# RECE-232
Recommended Error Correction Encoding (232)

RECE-232 is a data encoding scheme that encodes binary data to ASCII while maximizing error detection and correctability. It's intended for use in ASCII RS-232 streams where bitflips and dropped characters may be common.

## Format

By default, the encoder will output bytes in ASCII range 32 through 127.  This range consists of all printable characters, except for the non-printable DEL (127) character, which may be replaced with TAB (9).  Newlines are recommended as a delimiter between messages.  

A message contains a 6-bit header followed by one or more longwords (i.e. uint32 or float).  The resultant ASCII length is equal to `3 + 8*N` where `N` is the number of encoded longwords.

### Example Encoder Output
| Header | Longword 0 | Longword 1 | Longword 2 |  Length  |         Message Output         |
|------- | ---------- | ---------- | ---------- | -------- | ------------------------------ |
| 0x00   | 0x00000000 | -          | -          | 11 bytes | ` @ @ @ »2E2`                  |
| 0x1F   | 0x01234567 | 0x89ABCDEF | -          | 19 bytes | `'k(_#I N/o9»+M1n1L'`          |
| 0x3F   | 0xFFFFFFFF | 0xFFFFFFFF | 0xFFFFFFFF | 35 bytes | `?»?»?»?@?»?J?»?u?»?J?»?u y4`  |


## Performance

Example benchmark:
 - 1-7 random longwords per message
 - 0.1% chance of bit error per bit
 - 0.25% chance of dropped byte per byte
```
Messages with generated errors:  29788492 / 100000000 (29.78849%)
Average message length: 35.00 bytes

Flipped bits:  27930063
Dropped bytes: 8753389

Recovered errors:     26708257 / 29788492 (89.65965%)
Unrecoverable errors:  3080234 / 29788492 (10.34035%)
Undetected errors:           1 / 29788492 (0.000003%)
```

## Versions
- 0.1.0: Initial release with Java encoder/decoder and C encoder
- 0.1.1: Improved decoder success rates

- 0.2.0: Modified trailing checksum for better error detection rates (breaks compatibility)
