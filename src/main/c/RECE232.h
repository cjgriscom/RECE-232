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
// Protocol Version 0.1.x
// 
// Author: cjgriscom
// Repository: https://github.com/cjgriscom/RECE-232

#ifndef RECE232_h
#define RECE232_h

#include <stddef.h>
#include <stdint.h>

size_t rece232_size(uint8_t nLongwords) {
    return 3 + 8*nLongwords;
}

uint8_t rece232_crc8_lookup(uint8_t src) {
    static const uint8_t CRC8[256] = {
        0, 94,188,226, 97, 63,221,131,194,156,126, 32,163,253, 31, 65,
        157,195, 33,127,252,162, 64, 30, 95,  1,227,189, 62, 96,130,220,
        35,125,159,193, 66, 28,254,160,225,191, 93,  3,128,222, 60, 98,
        190,224,  2, 92,223,129, 99, 61,124, 34,192,158, 29, 67,161,255,
        70, 24,250,164, 39,121,155,197,132,218, 56,102,229,187, 89,  7,
        219,133,103, 57,186,228,  6, 88, 25, 71,165,251,120, 38,196,154,
        101, 59,217,135,  4, 90,184,230,167,249, 27, 69,198,152,122, 36,
        248,166, 68, 26,153,199, 37,123, 58,100,134,216, 91,  5,231,185,
        140,210, 48,110,237,179, 81, 15, 78, 16,242,172, 47,113,147,205,
        17, 79,173,243,112, 46,204,146,211,141,111, 49,178,236, 14, 80,
        175,241, 19, 77,206,144,114, 44,109, 51,209,143, 12, 82,176,238,
        50,108,142,208, 83, 13,239,177,240,174, 76, 18,145,207, 45,115,
        202,148,118, 40,171,245, 23, 73,  8, 86,180,234,105, 55,213,139,
        87,  9,235,181, 54,104,138,212,149,203, 41,119,244,170, 72, 22,
        233,183, 85, 11,136,214, 52,106, 43,117,151,201, 74, 20,246,168,
        116, 42,200,150, 21, 75,169,247,182,232, 10, 84,215,137,107, 53};
    
    src = CRC8[src];
    return src;
}

struct rece232_state {
    uint8_t cur_spacer;

    uint16_t sum1; // Fletcher low
    uint16_t sum2; // Fletcher high
};

void rece232_init(struct rece232_state *state, uint8_t header_6b) {
    state->sum1 = 0;
    state->sum2 = 0;
    
    state->cur_spacer = header_6b & 0b111111; // First 6-bit spacer is the header
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
    
    // Fletcher rounds
    state->sum1 += rece232_crc8_lookup(b0 | 0x00); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(b1 | 0x40); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(b2 | 0x80); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(bS | 0xC0); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(b3 | 0x00); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(b4 | 0x40); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(b5 | 0x80); state->sum2 += state->sum1;
    state->sum1 += rece232_crc8_lookup(xr | 0xC0); state->sum2 += state->sum1;
    
    // Append to stream
    stream_out(b0 | 0x20);
    stream_out(b1 | 0x40);
    stream_out(b2 | 0x20);
    stream_out(bS | 0x40);
    stream_out(b3 | 0x20);
    stream_out(b4 | 0x40);
    stream_out(b5 | 0x20);
    stream_out(xr | 0x40);
    
    // Normalize fletcher
    state->sum1 = state->sum1 % 255;
    state->sum2 = state->sum2 % 255;
    
    // Set current spacer to fletC
    state->cur_spacer = (uint8_t)(state->sum2 & 0b111111);
}

void rece232_finish(struct rece232_state *state, void (*stream_out)(char)) {
    uint16_t fletcher = (uint16_t)((state->sum2 << 8) | state->sum1);
    stream_out((uint8_t)(fletcher & 0b011111) | 0x20);
    fletcher >>= 5;
    stream_out((uint8_t)(fletcher & 0b111111) | 0x40);
    fletcher >>= 6;
    stream_out((uint8_t)(fletcher & 0b011111) | 0x20);
}

#endif // RECE232_h
