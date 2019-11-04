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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.chandler.rece232.RECE232.RECE232Decoder;
import io.chandler.rece232.RECE232.RECE232Encoder;

/**
 * Multithreaded transmission simulator with statistics
 * @author cjgriscom
 *
 */
public class RECE232Benchmarks {
	
	// Class to run benchmark thread and store result
	class Benchmarker {
		private final RECE232Encoder encoder = RECE232.getEncoder();
		private final RECE232Decoder decoder = RECE232.getDecoder();

		private final int maxIntsInMessage;
		private final int totalMsgs;
		private final double probBitFlip;     // Computed for each bit
		private final double probDroppedByte; // Computed for each byte
		private final Random rand;

		public int bitFlipCount = 0;
		public int droppedByteCount = 0;
		
		public int modifiedMessageCount = 0;
		public int recoveredCount = 0;
		public int unrecoverableCount = 0;
		public int wrongCount = 0;
		
		public double avgMsgLength = 0;
		
		public Benchmarker(int maxIntsInMessage, int totalMsgs, double probBitFlip, double probDroppedByte, Random rand) {
			this.maxIntsInMessage = maxIntsInMessage;
			this.totalMsgs = totalMsgs;
			this.probBitFlip = probBitFlip;
			this.probDroppedByte = probDroppedByte;
			this.rand = rand;
		}
		
		public Benchmarker run() {

			long msgLength = 0;

			int[] buffer = new int[maxIntsInMessage];
			
			for (int i = 0; i < totalMsgs; i++) {
				int code = rand.nextInt(64); // Message code from 0-63
				int n = rand.nextInt(maxIntsInMessage) + 1; // 1-maxIntsInMessage encoded values
				encoder.init((byte)code, n);
				for (int j = 0; j < n; j++) {
					int v = rand.nextInt();
					buffer[j] = v; // Store for checks
					encoder.appendLongword(v);
				}
				byte[] result = encoder.finish();
				msgLength += result.length;
				
				
				boolean modified = false;
				// Reencode result with errors to simulate serial transmission
				ByteArrayOutputStream errEncoder = new ByteArrayOutputStream(result.length);
				for (byte b : result) {
					// Drop the byte?
					if (rand.nextDouble() < probDroppedByte) {
						droppedByteCount++;
						modified = true;
					} else {
						// Do bit flips
						for (int x = 0; x < 8; x++) {
							// Flip bit x?
							if (rand.nextDouble() < probBitFlip) {
								bitFlipCount++;
								modified = true;
								b ^= (1<<x);
							}
						}
						errEncoder.write(b);
					}
				}
				
				if (modified) {
					modifiedMessageCount++;
					result = errEncoder.toByteArray();
				}
				
				boolean loaded = decoder.load(result);
				boolean correct = true;
				
				if (loaded) {
					correct = decoder.getHeader6Bit() == code;
					if (decoder.nLongwords() != n) correct = false;
					else {
						for (int j = 0; j < n; j++) {
							if (decoder.getLongword(j) != buffer[j]) correct = false;
						}
					}
				}
				
				if (loaded && correct && modified) {
					// Recovered from errors
					recoveredCount++;
				} else if ((!loaded || !correct) && !modified) {
					// Should never happen
					throw new RuntimeException("Decoder failed for " + new String(result));
				} else if (!loaded && modified) {
					unrecoverableCount++;
				}else if (loaded && !correct && modified) {
					wrongCount++;
				}
			}
			this.avgMsgLength = msgLength / (double)totalMsgs;
			return this;
		}
		
	}
	
	
	@Test
	public void testErrorRates() throws InterruptedException, ExecutionException {
		
		/* Test Variables */
		final int RUN_TOTAL_MSGS = 10_000_000;
		final int INSTANCES = 8;
		final int THREADS   = 8;
		
		double probBitFlip     = 0.002; // Computed for each bit
		double probDroppedByte = 0.005; // Computed for each byte
		int maxIntsInMessage = 7;
		/******************/
		
		
		
		int msgsForEachInstance = RUN_TOTAL_MSGS / INSTANCES;
		
		int totalMsgs = 0;
		int bitFlipCount = 0;
		int droppedByteCount = 0;
		
		int modifiedMessageCount = 0;
		int recoveredCount = 0;
		int unrecoverableCount = 0;
		int wrongCount = 0;
		
		double avgMsgLength = 0;

		Random seedGen = new Random(6720522);
		
		ExecutorService executorService = Executors.newFixedThreadPool(THREADS);
		Collection<Callable<Benchmarker>> callables = new ArrayList<>();
		IntStream.rangeClosed(1, INSTANCES).forEach(i -> {
			callables.add(() -> {
				Random rand = new Random(seedGen.nextLong());
				Benchmarker bch = new Benchmarker(maxIntsInMessage, msgsForEachInstance, probBitFlip, probDroppedByte, rand);
				System.out.println("Completed benchmark " + i);
				return bch.run();
			});
		});
		List<Future<Benchmarker>> taskFutureList = executorService.invokeAll(callables);
		for (Future<Benchmarker> future : taskFutureList) {
			Benchmarker bch = future.get();
			totalMsgs += bch.totalMsgs;
			bitFlipCount += bch.bitFlipCount;
			droppedByteCount += bch.droppedByteCount;
			modifiedMessageCount += bch.modifiedMessageCount;
			recoveredCount += bch.recoveredCount;
			unrecoverableCount += bch.unrecoverableCount;
			wrongCount += bch.wrongCount;
			avgMsgLength += bch.avgMsgLength / INSTANCES;
		}
		
		String intfrm = "%"+(totalMsgs + "").length()+"d / " + totalMsgs + " (%.4f%%)\n";
		System.out.println("RECE-232 Test Summary:");
		System.out.println();
		System.out.println("  Bit flip probability:     " + probBitFlip);
		System.out.println("  Dropped byte probability: " + probDroppedByte);
		System.out.println();
		System.out.printf ("  Messages with generated errors: " + intfrm, modifiedMessageCount, 100. * modifiedMessageCount / totalMsgs);
		System.out.printf ("  Average message length: %.2f bytes\n", avgMsgLength);
		int et = modifiedMessageCount;
		String intfrm_e = "%"+(et + "").length()+"d / " + et + " (%.4f%%)\n";
		System.out.println();
		System.out.println("  Flipped bits:  " + bitFlipCount);
		System.out.println("  Dropped bytes: " + droppedByteCount);
		System.out.println();
		System.out.printf ("  Recovered messages: " + intfrm_e, recoveredCount, 100. * recoveredCount / et);
		System.out.printf ("  Lost messages:      " + intfrm_e, unrecoverableCount, 100. * unrecoverableCount / et);
		System.out.printf ("  Corrupted messages: " + intfrm_e, wrongCount, 100. * wrongCount / et);
	}
	
}
