# RECE-232
Recommended Error Correction Encoding (232)

RECE-232 is a data encoding scheme that encodes longwords/floats to ASCII while maximizing error detection and correctability. It's intended for use in ASCII RS-232 streams where bitflips and dropped characters may be common.

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
