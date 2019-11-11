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
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.chandler.rece232.RECE232.RECE232Decoder;
import io.chandler.rece232.RECE232.RECE232Encoder;

/**
 * Multithreaded transmission simulator with statistics
 * @author cjgriscom
 *
 */
public class RECE232Benchmarks {
	private static final boolean CHECK_UNMODIFIED = false;
	
	// Class to run benchmark thread and store result
	static class Benchmarker {
		private static final ConcurrentHashMap<String, String> corruptedList = new ConcurrentHashMap<>();
		
		private final RECE232Encoder encoder = RECE232.getEncoder();
		private final RECE232Decoder decoder = RECE232.getDecoder();

		private final int minIntsInMessage, maxIntsInMessage;
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
		
		public Benchmarker(int minIntsInMessage, int maxIntsInMessage, int totalMsgs, double probBitFlip, double probDroppedByte,
				Random rand, boolean skipRecoveryOnCorruptedChecksum, boolean failCorruptedChecksum) {
			this.minIntsInMessage = minIntsInMessage;
			this.maxIntsInMessage = maxIntsInMessage;
			this.totalMsgs = totalMsgs;
			this.probBitFlip = probBitFlip;
			this.probDroppedByte = probDroppedByte;
			this.rand = rand;
			decoder.setSkipRecoveryOnCorruptedChecksum(skipRecoveryOnCorruptedChecksum);
			decoder.setFailOnCorruptedChecksum(failCorruptedChecksum);
		}
		
		public Benchmarker run() {

			long msgLength = 0;

			int[] buffer = new int[maxIntsInMessage];
			
			for (int i = 0; i < totalMsgs; i++) {
				int code = rand.nextInt(64); // Message code from 0-63
				int n = rand.nextInt(maxIntsInMessage + 1 - minIntsInMessage) + minIntsInMessage;
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
				
				boolean loaded, correct;
				
				byte[] result_mod = result;
				if (modified || CHECK_UNMODIFIED) {
					if (modified) {
						modifiedMessageCount++;
						result_mod = errEncoder.toByteArray();
					}
					
					loaded = decoder.load(result_mod);
					correct = true;
					
					if (loaded) {
						correct = decoder.getHeader6Bit() == code;
						if (decoder.nLongwords() != n) correct = false;
						else {
							for (int j = 0; j < n; j++) {
								if (decoder.getLongword(j) != buffer[j]) correct = false;
							}
						}
					}
				} else {
					loaded = true; correct = true;
				}
				
				if (loaded && correct && modified) {
					// Recovered from errors
					recoveredCount++;
				} else if ((!loaded || !correct) && !modified) {
					// Should never happen
					throw new RuntimeException("Decoder failed for " + new String(result_mod));
				} else if (!loaded && modified) {
					unrecoverableCount++;
				}else if (loaded && !correct && modified) {
					wrongCount++;
					corruptedList.put(new String(result), new String(result_mod));
				}
			}
			this.avgMsgLength = msgLength / (double)totalMsgs;
			return this;
		}
		
	}
	
	@ParameterizedTest
	@ValueSource(strings = {
			"1-7, 0.1%, 0.25%, skipifnochk", "1-7, 0.1%, 0.25%",
			
			"1-1, 0.1%, 0%,    skipifnochk", "1-1, 0.1%, 0%",
			"2-2, 0.1%, 0%,    skipifnochk", "2-2, 0.1%, 0%",
			"4-4, 0.1%, 0%,    skipifnochk", "4-4, 0.1%, 0%",
			"6-6, 0.1%, 0%,    skipifnochk", "6-6, 0.1%, 0%",
			"1-1, 0.1%, 0.25%, skipifnochk", "1-1, 0.1%, 0.25%",
			"2-2, 0.1%, 0.25%, skipifnochk", "2-2, 0.1%, 0.25%",
			"4-4, 0.1%, 0.25%, skipifnochk", "4-4, 0.1%, 0.25%",
			"6-6, 0.1%, 0.25%, skipifnochk", "6-6, 0.1%, 0.25%",
			"1-1, 0.2%, 0.5%,  skipifnochk", "1-1, 0.2%, 0.5%",
			"2-2, 0.2%, 0.5%,  skipifnochk", "2-2, 0.2%, 0.5%",
			"4-4, 0.2%, 0.5%,  skipifnochk", "4-4, 0.2%, 0.5%",
			"6-6, 0.2%, 0.5%,  skipifnochk", "6-6, 0.2%, 0.5%",
			"1-1, 0.4%, 1%,    skipifnochk", "1-1, 0.4%, 1%",
			"2-2, 0.4%, 1%,    skipifnochk", "2-2, 0.4%, 1%",
			"4-4, 0.4%, 1%,    skipifnochk", "4-4, 0.4%, 1%",
			"6-6, 0.4%, 1%,    skipifnochk", "6-6, 0.4%, 1%",
			})
	public void testErrorRates(String param) throws InterruptedException, ExecutionException {
		final String[] spl = param.split("[, \\-\\%]+");
		
		/* Test Variables */
		final int RUN_TOTAL_MSGS = 100_000_000;
		final int INSTANCES = 8;
		final int THREADS   = 8;
		
		double probBitFlip     = Double.parseDouble(spl[2]) / 100.; // Computed for each bit
		double probDroppedByte = Double.parseDouble(spl[3]) / 100.; // Computed for each byte
		int minIntsInMessage =   Integer.parseInt  (spl[0]);
		int maxIntsInMessage =   Integer.parseInt  (spl[1]);
		
		boolean skipRecoveryOnCorruptedChecksum = param.contains("skipifnochk"),
				failCorruptedChecksum = false;
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
				Benchmarker bch = new Benchmarker(
						minIntsInMessage, maxIntsInMessage, msgsForEachInstance, probBitFlip, probDroppedByte,
						rand,
						skipRecoveryOnCorruptedChecksum, failCorruptedChecksum);
				bch.run();
				//System.out.println("Completed benchmark " + i);
				return bch;
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
		
		String intfrm = "%"+(totalMsgs + "").length()+"d / " + totalMsgs + " (%.5f%%)\n";
		System.out.println("RECE-232 Test Summary: " + param);
		System.out.println();
		//System.out.println("  Bit flip probability:     " + probBitFlip);
		//System.out.println("  Dropped byte probability: " + probDroppedByte);
		//System.out.println();
		System.out.printf ("  Messages with generated errors: " + intfrm, modifiedMessageCount, 100. * modifiedMessageCount / totalMsgs);
		System.out.printf ("  Average message length: %.2f bytes\n", avgMsgLength);
		int et = modifiedMessageCount;
		String intfrm_e = "%"+(et + "").length()+"d / " + et + " (%.5f%%)\n";
		System.out.println();
		System.out.println("  Flipped bits:  " + bitFlipCount);
		System.out.println("  Dropped bytes: " + droppedByteCount);
		System.out.println();
		System.out.printf ("  Recovered errors:     " + intfrm_e, recoveredCount, 100. * recoveredCount / et);
		System.out.printf ("  Unrecoverable errors: " + intfrm_e, unrecoverableCount, 100. * unrecoverableCount / et);
		System.out.printf ("  Undetected errors:    " + intfrm_e, wrongCount, 100. * wrongCount / et);
		
		System.out.println();
		for (Entry<String, String> corrupted : Benchmarker.corruptedList.entrySet()) {
			//System.out.println(corrupted.getKey() + " -> " + corrupted.getValue());
		}
	}
	
}
