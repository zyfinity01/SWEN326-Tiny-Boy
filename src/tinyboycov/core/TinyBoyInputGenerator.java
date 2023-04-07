package tinyboycov.core;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import tinyboy.core.ControlPad;
import tinyboy.core.ControlPad.Button;
import tinyboy.core.TinyBoyInputSequence;
import tinyboy.util.AutomatedTester;

/**
 * The TinyBoy Input Generator is responsible for generating and refining inputs
 * to try and ensure that sufficient branch coverage is obtained.
 *
 * @author David J. Pearce
 *
 */
public class TinyBoyInputGenerator implements AutomatedTester.InputGenerator<TinyBoyInputSequence> {
	/**
	 * Represents the number of buttons on the control pad.
	 */
	private final static int NUM_BUTTONS = ControlPad.Button.values().length;

	private int n; // length of the input sequence

	/**
	 * Current batch being processed
	 */
	private ArrayList<TinyBoyInputSequence> worklist = new ArrayList<>();

	private ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> recordedInputs = new ArrayList<>();
	
	private int numberOfInputs;

	public record Triple<T, U, V> (T first, U second, V Third) {
	}

	/**
	 * Create new input generator for the TinyBoy simulation.
	 */
	public TinyBoyInputGenerator() {
		// FIXME: this is a very simplistic and poor implementation. However, it
		// illustrates how to create input sequences!
		// this.worklist.add(new
		// TinyBoyInputSequence(ControlPad.Button.LEFT,ControlPad.Button.UP));
		// this.worklist.add(new
		// TinyBoyInputSequence(ControlPad.Button.LEFT,ControlPad.Button.LEFT));
//		for(int i = 0; i < NUM_BUTTONS; i++) {
//			for(int j = 0; j < NUM_BUTTONS; j++) {
//				this.worklist.add(new TinyBoyInputSequence(ControlPad.Button.values()[i],ControlPad.Button.values()[j]));
//			}
//		}
		this.n = 1;
		this.worklist.clear();

		for (int sequenceLength = n; sequenceLength > 0; sequenceLength--) {
			this.worklist.addAll(generateCombinations(NUM_BUTTONS, sequenceLength));
		}
		
		numberOfInputs = this.worklist.size();
		System.out.println("number of inputs: " + numberOfInputs);

//        for(TinyBoyInputSequence seq : worklist) {
//        	System.out.println(seq.toString());
//        }

	}

	/**
	 * Generates all possible combinations of button presses for a given number of
	 * buttons and sequence length.
	 *
	 * @param numButtons     the number of buttons available for pressing
	 * @param sequenceLength the length of each input sequence
	 * @return an ArrayList of TinyBoyInputSequence objects, each representing a
	 *         unique combination of button presses
	 */
	public ArrayList<TinyBoyInputSequence> generateCombinations(int numButtons, int sequenceLength) {
		ArrayList<TinyBoyInputSequence> combinations = new ArrayList<>();
		int[] indexes = new int[sequenceLength];

		while (true) {
			ControlPad.Button[] sequence = new ControlPad.Button[sequenceLength];
			for (int i = 0; i < sequenceLength; i++) {
				sequence[i] = ControlPad.Button.values()[indexes[i]];
			}
			combinations.add(new TinyBoyInputSequence(sequence));

			// Move to the next combination
			int i;
			for (i = sequenceLength - 1; i >= 0; i--) {
				if (indexes[i] < numButtons - 1) {
					indexes[i]++;
					break;
				} else {
					indexes[i] = 0;
				}
			}

			if (i < 0) {
				break;
			}
		}

		return combinations;
	}

	@Override
	public boolean hasMore() {
		return this.worklist.size() > 0;
	}

	@Override
	public @Nullable TinyBoyInputSequence generate() {
		if (!this.worklist.isEmpty()) {
			// remove last item from worklist
			return this.worklist.remove(this.worklist.size() - 1);
		}
		return null;
	}

	/**
	 * A record returned from the fuzzer indicating the coverage and final state
	 * obtained for a given input sequence.
	 */
	@Override
	public void record(TinyBoyInputSequence input, BitSet coverage, byte[] state) {
		// NOTE: this method is called when fuzzing has finished for a given input. It
		// produces three potentially useful items: firstly, the input sequence that was
		// used for fuzzing; second, the set of instructions which were covered when
		// executing that sequence; finally, the complete state of the machine's RAM at
		// the end of the run.
		//
		// At this point, you will want to use the feedback gained from fuzzing to help
		// prune the space of inputs to try next. A few helper methods are given below,
    	if(n >= 10) return;
    	System.out.println("N: " + n);
    	System.out.println("input: " + input.toString());
    	System.out.println("numberOfInputs: " + numberOfInputs);
    	System.out.println("recordedInputs: " + recordedInputs.size());
    	
	    if(numberOfInputs != recordedInputs.size()+1) {
	    	recordedInputs.add(new Triple<>(input, coverage, state));
	    } else {
	    	System.out.println("Before prune: " + recordedInputs.size());
	    	this.worklist = addOneToAllSequences(convertTripleToSequence(pruneInputs(recordedInputs))); 	
	    	System.out.println("After prune: " + worklist.size());
	    	numberOfInputs = worklist.size();
	    	System.out.println("Worklist size: " + this.worklist.size());
	    }

		

	}
	
	public ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> pruneInputs(ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> inputs) {
		ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> prunedInputs = new ArrayList<>();
		
	    for (Triple<TinyBoyInputSequence, BitSet, byte[]> recordedInput1 : inputs) {
	    	boolean sameState = false;
	    	boolean subsumed = false;
	        for (Triple<TinyBoyInputSequence, BitSet, byte[]> recordedInput2 : inputs) {
	        	//if comparing the same input
	        	if(recordedInput1 == recordedInput2) break;
	        	
//	        	//if same state
//	        	if(recordedInput1.Third().equals(recordedInput2.Third())) {
//	        		sameState = true;
//	        	}
//	        	
//	        	if(subsumedBy(recordedInput1.second(), recordedInput2.second())) {
//
//	        		subsumed = true;
//	        	}
	        	
	        	
	        }
	        if(!sameState && !subsumed) {
	        	prunedInputs.add(recordedInput1);
	        }
	    }
	    return prunedInputs;
	}
	
	public ArrayList<TinyBoyInputSequence> convertTripleToSequence(ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> inputs){
		ArrayList<TinyBoyInputSequence> output = new ArrayList<>();
		
		for (Triple<TinyBoyInputSequence, BitSet, byte[]> input : inputs) {
			output.add(input.first);
		}
		return output;
	}
	
	
	public ArrayList<TinyBoyInputSequence> addOneToAllSequences(ArrayList<TinyBoyInputSequence> inputs){
		n++;
		ArrayList<TinyBoyInputSequence> output = new ArrayList<>();
		
		for (TinyBoyInputSequence sequence : inputs) {
			for(int i = 0; i < NUM_BUTTONS; i++) {
				TinyBoyInputSequence newSequence = new TinyBoyInputSequence(sequence);
				newSequence.append(ControlPad.Button.values()[i]);
				output.add(newSequence);
				System.out.println("Button: "+ ControlPad.Button.values()[i]);
			}
		}
		
		for(TinyBoyInputSequence sequence : inputs) {
			System.out.println("old seq:" + sequence.toString());
			
		}
		for(TinyBoyInputSequence sequence : output) {
			System.out.println("new seq:" + sequence.toString());
			
		}

		return output;
	}

	/**
	 * Checks if every item in the two input integer arrays are equal to each other.
	 *
	 * @param arr1 the first integer array to compare
	 * @param arr2 the second integer array to compare
	 * @return true if every item in both arrays is equal, false otherwise
	 */
	public static boolean areArraysEqual(byte[] arr1, byte[] arr2) {
	    if (arr1.length != arr2.length) {
	        return false;
	    }
	    
	    for (int i = 0; i < arr1.length; i++) {
	        if (arr1[i] != arr2[i]) {
	            return false;
	        }
	    }
	    
	    return true;
	}

	/**
	 * Check whether a given input sequence is completely subsumed by another.
	 *
	 * @param lhs The one which may be subsumed.
	 * @param rhs The one which may be subsuming.
	 * @return True if lhs subsumed by rhs, false otherwise.
	 */
	public static boolean subsumedBy(BitSet lhs, BitSet rhs) {
		for (int i = lhs.nextSetBit(0); i >= 0; i = lhs.nextSetBit(i + 1)) {
			if (!rhs.get(i)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Reduce a given set of items to at most <code>n</code> inputs by randomly
	 * sampling.
	 *
	 * @param inputs List of inputs to sample from.
	 * @param n      Size of inputs after reduction.
	 */
	private static <T> void randomSample(List<T> inputs, int n) {
		// Randomly shuffle inputs
		Collections.shuffle(inputs);
		// Remove inputs until only n remain
		while (inputs.size() > n) {
			inputs.remove(inputs.size() - 1);
		}
	}
}
