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
package io.chandler.rece232;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import io.chandler.rece232.RECE232;
import io.chandler.rece232.RECE232.RECE232Decoder;
import io.chandler.rece232.RECE232.RECE232Encoder;

public class RECE232Tests {
	@Test public void testC() {
		RECE232Decoder dec = RECE232.getDecoder().setConvertTabs(true);
		assertTrue(dec.load("?	?	?	?@?	?J?	?u*J ".getBytes(StandardCharsets.US_ASCII)));
		assertEquals(0x3f, dec.getHeader6Bit());
		assertEquals(-1, dec.getLongword(0));
		assertEquals(-1, dec.getLongword(1));
		assertTrue(dec.load(" @ @ @ \t2E2".getBytes(StandardCharsets.US_ASCII)));
		assertEquals(0, dec.getHeader6Bit());
		assertEquals(0, dec.getLongword(0));
	}
	
	@Test
	public void testOrdinaryDecode() {
		RECE232Encoder encoder = RECE232.getEncoder();
		
		int[][] datasets = new int[][] {
			{0x01, 425364522, 425364522, 425364522},
			{0x00, Integer.MAX_VALUE, Integer.MIN_VALUE, 0},
			{0x3F, 1111111111, 1111111110, -1111111111, -1111111110},
			{0x01, 1},
			{0x02, 1, 2},
			{0x25, 1, 2, 3, 4, 5},
		};
		
		for (int[] dataset : datasets) {
			encoder.init((byte)dataset[0], dataset.length - 1);
			for (int i = 1; i < dataset.length; i++) {
				encoder.appendLongword(dataset[i]);
			}
			byte[] fin = encoder.finish();

			RECE232Decoder dec = RECE232.getDecoder();
			assertTrue(dec.load(fin));
			assertEquals((byte)dataset[0], dec.getHeader6Bit());
			assertEquals(dataset.length - 1, dec.nLongwords());
			for (int i = 0; i < dec.nLongwords(); i++) {
				assertEquals(dataset[i+1], dec.getLongword(i));
			}
		}
	}
	
	@Test
	public void testErrors() {
		RECE232Encoder encoder = RECE232.getEncoder();
		RECE232Decoder dec = RECE232.getDecoder();
		
		int[][] datasets = new int[][] {
			{0x3F, 1243546544, 121, 145687},
			{0x01, 425364522, 425364522, 425364522},
			{0x01, 1, 5, -234567865},
		};
		
		// Drop drops a character
		// Corrupt makes an obvious high bit flip
		// Flip makes a non-obvious bit flip 
		String[] corruptionTests = new String[] {
			"Pass, Drop 4, Corrupt 10",
			"Fail, Drop 9, Corrupt 10",
			"Fail, Drop 0, Drop 1",
			"Fail, Drop 0, Drop 1, Drop 2",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3, Drop 4",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3, Drop 4, Drop 5",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3, Drop 4, Drop 5, Drop 6",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3, Drop 4, Drop 5, Drop 6, Drop 7",
			"Fail, Drop 0, Drop 1, Drop 2, Drop 3, Drop 4, Drop 5, Drop 6, Drop 7, Drop 8",
			"Pass, Drop 24",
			"Pass, Drop 25",
			"Pass, Drop 26",
			"Pass, Corrupt 24",
			"Pass, Corrupt 25",
			"Pass, Corrupt 26",
			"Fail, Flip0 26",
			"Pass, Flip0 1",
			"Pass, Flip0 9",
			"Pass, Flip0 17",
			"Pass, Flip0 1, Flip0 9, Flip0 17",
			"Fail, Flip0 1, Flip0 9, Flip0 17, Flip0 18",
			"Pass, Flip0 1, Flip0 8, Flip0 17",
			"Pass, Flip3 1, Flip3 8, Flip3 17",
			"Pass, Flip2 9, Flip3 9, Flip4 9",
			"Pass, Flip6 6", // This case should only work with the recursive gap computation
		};
		
		for (int[] dataset : datasets) { for (String corr : corruptionTests) {
			System.out.println("Test " + corr);
			TreeSet<Integer> corrupts = new TreeSet<>();
			TreeSet<Integer> drops = new TreeSet<>();
			TreeMap<Integer, Integer> flips = new TreeMap<>();
			
			String[] spl = corr.toLowerCase().split(",");
			for (String s : spl) {
				if (s.contains("corrupt")) corrupts.add(Integer.parseInt(s.trim().substring(s.trim().indexOf(' ')).trim()));
				if (s.contains(   "drop"))    drops.add(Integer.parseInt(s.trim().substring(s.trim().indexOf(' ')).trim()));
				if (s.contains(   "flip"))    flips.put(Integer.parseInt(s.trim().substring(s.trim().indexOf(' ')).trim()), Integer.parseInt(s.substring(s.indexOf("flip") + 4,s.indexOf("flip") + 5)));
			}
			encoder.init((byte)dataset[0], dataset.length - 1);
			for (int i = 1; i < dataset.length; i++) {
				encoder.appendLongword(dataset[i]);
			}
			byte[] fin = encoder.finish();
			//System.out.println(new String(fin, StandardCharsets.US_ASCII));
			ByteBuffer mod = ByteBuffer.allocate(fin.length - drops.size());
			
			int pos = 0;
			for (byte b : fin) {
				if (drops.contains(pos)) {
					// Skip
				} else if (corrupts.contains(pos)) {
					mod.put((byte)(b ^ 0b10000000));
				} else if (flips.containsKey(pos)) {
					mod.put((byte)(b ^ (1 << flips.get(pos))));
				} else {
					mod.put(b);
				}
				pos++;
			}
			
			boolean corrected = dec.load(mod.array());
			if (corr.toLowerCase().contains("pass")) {
				assertTrue(corrected);
			} else if (corr.toLowerCase().contains("fail")) {
				assertFalse(corrected);
			}
			if (corrected) {
				assertEquals((byte)dataset[0], dec.getHeader6Bit());
				assertEquals(dataset.length - 1, dec.nLongwords());
				for (int i = 0; i < dec.nLongwords(); i++) {
					assertEquals(dataset[i+1], dec.getLongword(i));
				}
			}
			
			
		}}
	}
}
