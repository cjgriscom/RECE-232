# RECE-232
Recommended Error Correction Encoding (232)

RECE-232 is a data encoding scheme that encodes longwords/floats to ASCII while maximizing error detection and correctability. It's intended for use in ASCII RS-232 streams where bitflips and dropped characters may be common.

# Versions
- 0.1.0: Initial release with Java encoder/decoder and C encoder
- 0.1.1: Improved decoder success rates

- 0.2.0: Modified trailing checksum for better error detection rates (breaks compatibility)
