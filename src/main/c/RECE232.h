/*
This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.

In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to <https://unlicense.org>
 */

// RECE-232 C Header
// Protocol Version 0.2.x
// 
// Author: cjgriscom
// Repository: https://github.com/cjgriscom/RECE-232

#ifndef RECE232_h
#define RECE232_h

#include <stddef.h>
#include <stdint.h>

size_t rece232_size(uint8_t nLongwords) {
    return 3 + 8*(size_t) nLongwords;
}

uint16_t rece232_crc16dnp_bit_4(uint16_t crc, uint32_t lw) {
    lw ^= crc;
    for (int k = 0; k < 32; ++k) lw = lw & 1 ? (lw >> 1) ^ 0xa6bc : lw >> 1;
    
    return (uint16_t)lw;
}

uint16_t rece232_crc16dnp_bit_1(uint16_t crc, uint32_t byt) {
    crc ^= byt & 0xff;
    for (int k = 0; k < 8; ++k) crc = crc & 1 ? (crc >> 1) ^ 0xa6bc : crc >> 1;
    return crc;
}

struct rece232_state {
    uint16_t chk; // CRC-16
    uint8_t cur_spacer;
};

void rece232_init(struct rece232_state *state, uint8_t header_6b) {
    state->cur_spacer = header_6b & 0b111111; // First 6-bit spacer is the header
    state->chk = rece232_crc16dnp_bit_1(0x1af7, state->cur_spacer);
}

void rece232_stream_longword(struct rece232_state *state, uint32_t longword, void (*stream_out)(char)) {
    // Calculate alternating 5,6-bit characters
    uint8_t b0 = (longword >>   0) & 0b011111; // 5
    uint8_t b1 = (longword >>   5) & 0b111111; // 6
    uint8_t b2 = (longword >>  11) & 0b011111; // 5
    uint8_t bS = state->cur_spacer & 0b111111; // 6
    uint8_t b3 = (longword >>  16) & 0b011111; // 5
    uint8_t b4 = (longword >>  21) & 0b111111; // 6
    uint8_t b5 = (longword >>  27) & 0b011111; // 5
    uint8_t xr = (b0^b1^b2^bS^b3^b4^b5) ^ 0b111111; // 6
    
    // Append to stream
    stream_out(b0 | 0x20);
    stream_out(b1 | 0x40);
    stream_out(b2 | 0x20);
    stream_out(bS | 0x40);
    stream_out(b3 | 0x20);
    stream_out(b4 | 0x40);
    stream_out(b5 | 0x20);
    stream_out(xr | 0x40);
    
    // Fletcher rounds
    state->chk = rece232_crc16dnp_bit_4(state->chk, longword);
    
    // Set current spacer to fletC
    state->cur_spacer = (uint8_t)(((state->chk >> 0) & 0b000011) | ((state->chk >> 5) & 0b001100) | ((state->chk >> 10) & 0b110000));
}

void rece232_finish(struct rece232_state *state, void (*stream_out)(char)) {
    stream_out((uint8_t)(state->chk & 0b011111) | 0x20);
    state->chk >>= 5;
    stream_out((uint8_t)(state->chk & 0b111111) | 0x40);
    state->chk >>= 6;
    stream_out((uint8_t)(state->chk & 0b011111) | 0x20);
}

#endif // RECE232_h
