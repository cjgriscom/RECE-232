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

/**
 * RECE-232 is a data encoding scheme that encodes longwords/floats to ASCII while maximizing error detection and correctability.
 * It's intended for use in ASCII RS-232 streams where bitflips and dropped characters may be common.
 * 
 * Guarantees:
 *   Will recover from any dropped character
 *   Will recover from any corrupted byte (except possibly in the 1-byte header and the 3-byte footer)
 * 
 * @author cjgriscom
 *
 */
public final class RECE232 {
	private static final boolean DEBUG = false;
	
	private static final int[] CRC_8_TABLE = new int[256];
	
	// DOW CRC
	private static final short CRC8_POLYNOMIAL = (short)Integer.parseInt(new StringBuilder(
							Integer.toBinaryString(0x131)).reverse().toString(), 2); //0b100011001 or 0x131;
	
	// Calculate lookup table
	static {
		for (int i = 0; i < 256; i++) {
			int rem = i;
			for (int j = 0; j < 8; j++) {
				if ((rem & 1) == 1) rem = ((rem ^ CRC8_POLYNOMIAL) >> 1);
				else rem >>= 1;
			}
			CRC_8_TABLE[i] = rem;
			/*
			System.out.printf("%3d,", CRC_8_TABLE[i] ); // Print out table
			if ((i+1)%16 == 0) System.out.println();
			*/
		}
	}
	
	private RECE232() { }
	
	public static RECE232Encoder getEncoder() {
		return new RECE232Encoder();
	}
	
	public static RECE232Decoder getDecoder() {
		return new RECE232Decoder();
	}
	
	/**
	 * Stateful RECE232 Encoder
	 * @author cjgriscom
	 */
	public static final class RECE232Encoder {
		
		private boolean useTabs = false;
		
		private int nLongwords;
		
		private int i = 0;
		private byte[] ascii;
		
		private byte curSpacer;

		int sum1 = 0; // Fletcher low
		int sum2 = 0; // Fletcher high
		
		private RECE232Encoder() { }
		
		/**
		 * Use tab character instead of non-printable ASCII 127 (DEL)
		 * @param useTabs true to use tabs.  Default is false.
		 * @return
		 */
		public RECE232Encoder setUseTabs(boolean useTabs) {
			this.useTabs = useTabs;
			return this;
		}
		
		public RECE232Encoder init(byte header6Bit, int nLongwords) {
			if (nLongwords <= 0) throw new IllegalStateException("Must encode at least one longword");
			this.i = 0;
			this.sum1 = 0;
			this.sum2 = 0;
			this.nLongwords = nLongwords;
			this.ascii = new byte[8*nLongwords + 3]; // ~, message/spacers, fletcher
			this.curSpacer = (byte)(header6Bit & 0b111111); // First 6-bit spacer is the header
			return this;
		}
		
		public byte[] finish() {
			int fletcher = ((sum2 << 8) | sum1);
			ascii[i++] = (byte)((fletcher & 0b011111) | 0x20);
			fletcher >>>= 5;
			ascii[i++] = (byte)((fletcher & 0b111111) | 0x40);
			fletcher >>>= 6;
			ascii[i++] = (byte)((fletcher & 0b011111) | 0x20);
			if (nLongwords != 0) throw new IllegalStateException("Expected " + nLongwords + " more longwords");
			if (useTabs) {
				for (int j = 0; j < ascii.length; j++) {
					if (ascii[j] == 127) ascii[j] = (byte)'\t';
				}
			}
			return ascii;
		}
		
		public RECE232Encoder appendLongword(int bytes) {
			if (nLongwords-- == 0) throw new IllegalStateException("Exceeded max longwords");
			
			// Calculate alternating 5,6-bit characters
			int b0 = (bytes >>>  0) & 0b011111; // 5
			int b1 = (bytes >>>  5) & 0b111111; // 6
			int b2 = (bytes >>> 11) & 0b011111; // 5
			int bS = curSpacer      & 0b111111; // 6
			int b3 = (bytes >>> 16) & 0b011111; // 5
			int b4 = (bytes >>> 21) & 0b111111; // 6
			int b5 = (bytes >>> 27) & 0b011111; // 5
			int xor = (b0^b1^b2^bS^b3^b4^b5) ^ 0b111111; // 6
			
			// Fletcher rounds
			sum1 += CRC_8_TABLE[b0 | 0x00]; sum2 += sum1;
			sum1 += CRC_8_TABLE[b1 | 0x40]; sum2 += sum1;
			sum1 += CRC_8_TABLE[b2 | 0x80]; sum2 += sum1;
			sum1 += CRC_8_TABLE[bS | 0xC0]; sum2 += sum1;
			sum1 += CRC_8_TABLE[b3 | 0x00]; sum2 += sum1;
			sum1 += CRC_8_TABLE[b4 | 0x40]; sum2 += sum1;
			sum1 += CRC_8_TABLE[b5 | 0x80]; sum2 += sum1;
			sum1 += CRC_8_TABLE[xor| 0xC0]; sum2 += sum1;
			
			// Append to byte array
			ascii[i++] = (byte)(b0 | 0x20);
			ascii[i++] = (byte)(b1 | 0x40);
			ascii[i++] = (byte)(b2 | 0x20);
			ascii[i++] = (byte)(bS | 0x40);
			ascii[i++] = (byte)(b3 | 0x20);
			ascii[i++] = (byte)(b4 | 0x40);
			ascii[i++] = (byte)(b5 | 0x20);
			ascii[i++] = (byte)(xor| 0x40);
			
			// Normalize fletcher
			sum1 = (sum1 & 0xffff) % 255;
			sum2 = (sum2 & 0xffff) % 255;
			
			// Set current spacer to abbreviated fletcher
			curSpacer = (byte)(sum2 & 0b111111);
			
			return this;
		}
	}
	
	/**
	 * Stateful RECE232 Decoder
	 * 
	 * @author cjgriscom
	 *
	 */
	public static final class RECE232Decoder {
		private int nLongwords;
		private int[] recon;
		private boolean skipRecoveryOnCorruptedChecksum = false;
		private boolean failOnCorruptedChecksum = false;
		private boolean convertTabs = false;
		
		private RECE232Decoder() { }
		
		/**
		 * By default, the decoder will attempt to detect a partially dropped or checksum and
		 *   continue anyway if only one of the 3 bytes is invalid. 
		 * 
		 * @param doFail true if the decoder should fail whenever the final checksum is corrupted. False by default.
		 * @return
		 */
		public RECE232Decoder setFailOnCorruptedChecksum(boolean doFail) {
			failOnCorruptedChecksum = doFail;
			return this;
		}
		
		/**
		 * The decoder performs error correction when the individual longword checksums don't match.
		 * Set this parameter to true to prevent error correction attempts when the final checksum is partially missing. 
		 * 
		 * @param doSkip true if the decoder should not attempt bit flip recovery when final checksum is corrupted. False by default.
		 * @return
		 */
		public RECE232Decoder setSkipRecoveryOnCorruptedChecksum(boolean doSkip) {
			skipRecoveryOnCorruptedChecksum = doSkip;
			return this;
		}
		
		/**
		 * Allow tab character in place of non-printable 127 (ASCII DEL)
		 * @param convertTabs True to detect and convert tabs. Default is false.
		 * @return
		 */
		public RECE232Decoder setConvertTabs(boolean convertTabs) {
			this.convertTabs = convertTabs;
			return this;
		}
		
		private static final int GOOD_MASK = 0b11111_111111_11111;
		public boolean load(byte[] src) {
			
			int len = src.length;
			len -= 3; // Subtract fletcher footer, remainder should be n*8b
			if (len < 7) return false; // below minimum recoverable bytes
			this.nLongwords = (len + 7) / 8;
			if (DEBUG) System.out.println(nLongwords + " longwords");
			
			// Extract fletF
			// Src is little endian, so these indices are really confusing
			//  They reflect the ascending array character order, OR the big endian register locations
			int fF2 = src[src.length - 1] & 0xff;
			int fF1 = src[src.length - 2] & 0xff;
			int fF0 = src[src.length - 3] & 0xff;
			// There are six signature possibilities for a recoverable fletF
			// ! (F) represents an out-of-range character
			// 5 (0) is a 5-bit character, 6 (1) is a 6-bit character
			// !65, 5!5, 56!, 6(G)65, 65(G)5, 656(G)
			
			if (convertTabs) {
				if (fF2 == (byte)'\t') fF2 = 127;
				if (fF1 == (byte)'\t') fF1 = 127;
				if (fF0 == (byte)'\t') fF0 = 127;
			}
			
			short fletFErrSig = 0x000;
			if (fF0 < 32 || fF0 >= 128) fletFErrSig |= 0xF00;
			else if (fF0 >= 64)         fletFErrSig |= 0x100;
			if (fF1 < 32 || fF1 >= 128) fletFErrSig |= 0x0F0;
			else if (fF1 >= 64)         fletFErrSig |= 0x010;
			if (fF2 < 32 || fF2 >= 128) fletFErrSig |= 0x00F;
			else if (fF2 >= 64)         fletFErrSig |= 0x001;
			if (DEBUG) System.out.println("ErrSig: " + Integer.toHexString(fletFErrSig));
			
			// Don't allow partial fletF matching
			if (fletFErrSig != 0x010 && failOnCorruptedChecksum) return false;

			int fletF = 0;   // Final value if good or recoverable
			int fletFMask = 0; // Final mask if recoverable
			switch(fletFErrSig) {
				case 0x010: // Good
					fletF = ((fF2-0x20) << 11) | ((fF1-0x40) << 5) | ((fF0-0x20) << 0);
					fletFMask = GOOD_MASK;
					break;
				case 0xF10: // First is corrupt
					fletF = ((fF2-0x20) << 11) | ((fF1-0x40) << 5);
					fletFMask = 0b11111_111111_00000;
					break;
				case 0x0F0: // Second is corrupt
					fletF = ((fF2-0x20) << 11) | ((fF0-0x20) << 0);
					fletFMask = 0b11111_000000_11111;
					break;
				case 0x01F: // Third is corrupt
					fletF = ((fF1-0x40) << 5) | ((fF0-0x20) << 0);
					fletFMask = 0b00000_111111_11111;
					break;
				case 0x110: // Gap in first position
					fletF = ((fF2-0x20) << 11) | ((fF1-0x40) << 5);
					fletFMask = 0b11111_111111_00000;
					break;
				//case 0x000:
				//case 0xF00:
				case 0x100: // Gap in second position
					fletF = ((fF2-0x20) << 11) | ((fF1-0x20) << 0);
					fletFMask = 0b11111_000000_11111;
					break;
				//case 0x001:
				//case 0xF01:
				case 0x101: // Gap in third position
					fletF = ((fF2-0x40) << 5) | ((fF1-0x20) << 0);
					fletFMask = 0b00000_111111_11111;
					break;
				default: // Not recoverable
					return false;
			}
			
			this.recon = new int[nLongwords * 8];
			int[] gaps = new int[nLongwords];
			boolean[] badChks = new boolean[nLongwords];
			
			int i = 0;
			for (int n = 0; n < nLongwords; n++) {
				int nRead = 0;
				int nGap = 0;
				gaps[n] = -1;
				// Loop through and find gaps, attempt reconstruction
				for (; ; i++) {
					if (i < src.length - 2) { // Allow pushing into the first fletcher char, in case there's a gap before there
						int byt = src[i] & 0xff;
						if (convertTabs && byt == (byte)'\t') byt = 127;
						
						if (byt < 32 || byt >= 128) {
							if (DEBUG) System.out.print("!");
							// Out of ascii range; consider this a corrupt character
							gaps[n] = nRead;
							nGap++;
							if (nGap >= 2) {
								if (DEBUG) System.out.println();
								return false; // Too many errors
							}
							recon[n * 8 + nRead] = 0;
							nRead++;
						} else if (nRead % 2 == 0 && byt < 64) {
							if (DEBUG) System.out.print("5");
							// Is expected 5-bit
							recon[n * 8 + nRead] = byt - 32;
							nRead++;
						} else if (nRead % 2 != 0 && byt >= 64) {
							if (DEBUG) System.out.print("6");
							// Is expected 6-bit
							recon[n * 8 + nRead] = byt - 64;
							nRead++;
						} else {
							if (DEBUG) System.out.print("G");
							// It's not in the expected range, must be a gap
							gaps[n] = nRead;
							nGap++;
							if (nGap >= 2) {
								if (DEBUG) System.out.println();
								return false; // Too many errors
							}
							recon[n * 8 + nRead] = 0;
							nRead++;
							i--; // Backtrack for next
							
						}
					} else {
						if (DEBUG) System.out.println();
						return false; // Ran through end w/o a complete 8-charset
					}
					if (nRead == 8) {i++; break;}
				}
				if (DEBUG) System.out.println("");
				
				if (nRead != 8) return false; // Ran out of bytes to src
				
				// Fill gaps using checksum
				int gapIdx = gaps[n];
				if (gapIdx == -1) {
					// No gaps; just verify checksum
					if (!verifyReconChk(n*8)) {
						badChks[n] = true;
						if (DEBUG) System.out.println("Bad checksum " + n);
					}
				} else {
					int chk = 0;
					for (int b = 0; b < 8; b++) {
						if (b == gapIdx) continue;
						if (DEBUG) System.out.println("Fill gap chk" + b);
						chk ^= recon[b + n*8];
					}
					recon[gapIdx + n*8] = chk ^ 0b111111;
				}
				
			}
			
			// Attempt to correct wrong checksums
			return correctChecksums(badChks, false, 0, fletF, fletFMask);
		}
		
		// Recursive correction
		private boolean correctChecksums(boolean[] badChks, boolean triedNextFletCRepl, int n, int fletF, int fletFMask) {
			// Base case, OR recovery is disabled w/ a partial fletF
			if ((skipRecoveryOnCorruptedChecksum && fletFMask != GOOD_MASK) || n == badChks.length) {
				if (DEBUG) System.out.println("Attempting full checksum verification");
				for (boolean b : badChks) if (b) return false; // Bad checksums still exist (skip recovery must be set)
				return verifyFletF(fletF, fletFMask);
			} else if (badChks[n]) {
				if (DEBUG) System.out.println("Processing bad checksum " + n);
				
				if (!triedNextFletCRepl && n != badChks.length - 1 && badChks[n+1]) {
					if (DEBUG) System.out.println("Try following fletC repl");
					// Next checksum is also bad, so the following fletC byte could be corrupt. Try replacing it.
					int chk = 0b111111;
					for (int b = 8; b < 16; b++) {
						chk ^= recon[b + n*8];
					}
					// Try to replace byte with the rest of the checksum
					if (DEBUG) System.out.print("NextFletC: " + recon[n*8 + 11]);
					recon[n*8 + 11] ^= chk;
					badChks[n+1] = false;
					if (DEBUG) System.out.println(" -> " + recon[n*8 + 11]);
					
					if (correctChecksums(badChks, true, n, fletF, fletFMask)) return true;
					
					// Revert
					recon[n*8 + 11] ^= chk;
					badChks[n+1] = true;
					if (correctChecksums(badChks, true, n, fletF, fletFMask)) return true;
					
					return false;
				} else {
					// Partial fletcher
					int chk = 0b111111;
					for (int b = 0; b < 8; b++) {
						chk ^= recon[b + n*8];
					}
					for (int b = 0; b < 8; b++) {
						// Try to replace byte with the rest of the checksum
						if (DEBUG) System.out.print(recon[b + n*8]);
						recon[b + n*8] ^= chk;
						badChks[n] = false;
						if (DEBUG) System.out.println(" -> " + recon[b + n*8]);
						//verifyFletF(fletF, fletFMask)
						
						// TODO verify fletC even if no error
						// Contains a partial fletcher followup
						if (n != badChks.length - 1) {
							int partial = calReconFletC((n+1)*8);
							int cmp = recon[n*8 + 11];
							if (DEBUG) System.out.println("FletC "+b+" " + partial);
							if (DEBUG) System.out.println("FletC "+b+" " + cmp);
							
							if (partial == cmp) {
								if (correctChecksums(badChks, false, n+1, fletF, fletFMask)) return true;
							}
						} else {
							if (correctChecksums(badChks, false, n+1, fletF, fletFMask)) return true;
						}
						// Revert
						recon[b + n*8] ^= chk;
						badChks[n] = true;
					}
					return false;
				}
			} else  {
				if (DEBUG) System.out.println("Good checksum " + n);
				return correctChecksums(badChks, false, n+1, fletF, fletFMask);
			}
		}
		
		private boolean verifyFletF(int fletF, int fletFMask) {
			if (DEBUG) System.out.println("MaskF " + Integer.toHexString(0xffff & fletFMask));
			if (DEBUG) System.out.println("ReadF " + Integer.toHexString(0xffff & fletF));
			int sum1 = 0;
			int sum2 = 0;
			for (int r = 0; r < recon.length; ++r) {
				sum1 = ((sum1 + (CRC_8_TABLE[recon[r] + ((0b11 & r) << 6)])) & 0xffff) % 255;
				sum2 = ((sum2 + sum1) & 0xffff) % 255;
				if (r % 8 == 7 && r+5 < recon.length) {
					if ((sum2 & 0b111111) != recon[r + 4]) return false;
				}
			}
			int extFletcher = (sum2 << 8) | sum1;
			if (DEBUG) System.out.println("MskdC " + Integer.toHexString(extFletcher & fletFMask));
			if (DEBUG) System.out.println("OrigC " + Integer.toHexString(extFletcher));
			return (extFletcher & fletFMask) == (fletF & 0xffff);
			
		}
		
		private boolean verifyReconChk(int offset) {
			int chk = 0;
			for (int b = 0; b < 8; b++) {
				chk ^= recon[b + offset];
			}
			return chk == 0b111111;
		}
		
		private int calReconFletC(int len) {
			int sum1 = 0;
			int sum2 = 0;
			for (int r = 0; r < len; ++r) {
				sum1 = ((sum1 + (CRC_8_TABLE[recon[r] + ((0b11 & r) << 6)])) & 0xffff) % 255;
				sum2 = ((sum2 + sum1) & 0xffff) % 255;
			}
			return (sum2 & 0b111111);
		}
		
		public int nLongwords() {
			return nLongwords;
		}
		
		public byte getHeader6Bit() {
			return (byte)recon[3];
		}
		
		public int getLongword(int i) {
			return  recon[i*8 + 0] <<  0 |
					recon[i*8 + 1] <<  5 |
					recon[i*8 + 2] << 11 |
					recon[i*8 + 4] << 16 |
					recon[i*8 + 5] << 21 |
					recon[i*8 + 6] << 27;
		}
	}

}
