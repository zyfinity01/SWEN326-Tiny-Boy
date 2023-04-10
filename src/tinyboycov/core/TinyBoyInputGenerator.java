package tinyboycov.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.eclipse.jdt.annotation.Nullable;
import tinyboy.core.ControlPad;
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

  /**
   * The global input sequence length.
   */
  private int seqLength;

  /**
   * Current batch being processed.
   */
  private ArrayList<TinyBoyInputSequence> worklist = new ArrayList<>();

  /**
   * Inputs that are recorded for pruning purposes.
   */
  private ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> recordedInputs =
      new ArrayList<>();

  /**
   * record what the number of inputs were before worklist is emptied.
   */
  private int numberOfInputs;

  /**
   * Allows for easy storing of three elements, overidden functions are to conform
   * to safety critical standards.
   *
   * @author niraj
   *
   * @param <T> element 1
   * @param <U> element 2
   * @param <V> element 3
   */
  public record Triple<T, U, V>(T first, U second, V third) {
    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Triple<?, ?, ?> other = (Triple<?, ?, ?>) obj;
      return Objects.equals(this.first, other.first) && Objects.equals(this.second, other.second)
          && Objects.equals(this.third, other.third);
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.first, this.second, this.third);
    }
  }

  /**
   * Create new input generator for the TinyBoy simulation.
   */
  public TinyBoyInputGenerator() {
    this.seqLength = 2;
    this.worklist.clear();

    for (int sequenceLength = this.seqLength; sequenceLength > 0; sequenceLength--) {
      this.worklist.addAll(generateCombinations(NUM_BUTTONS + 1, sequenceLength));
    }

    this.numberOfInputs = this.worklist.size();
  }

  /**
   * Retrieve all possible button presses, including no press.
   *
   * @return an array of ControlPad buttons, including null
   */
  private static ControlPad.Button[] getValues() {
    ControlPad.Button[] allButtons = ControlPad.Button.values();
    ControlPad.Button[] buttonsWithNull = Arrays.copyOf(allButtons, allButtons.length + 1);
    buttonsWithNull[allButtons.length] = null;
    return buttonsWithNull;
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
  public static ArrayList<TinyBoyInputSequence> generateCombinations(int numButtons,
      int sequenceLength) {
    ArrayList<TinyBoyInputSequence> combinations = new ArrayList<>();
    int[] indexes = new int[sequenceLength];

    while (true) {
      ControlPad.Button[] sequence = new ControlPad.Button[sequenceLength];
      for (int i = 0; i < sequenceLength; i++) {
        sequence[i] = getValues()[indexes[i]];
      }
      combinations.add(new TinyBoyInputSequence(sequence));

      // Move to the next combination
      int i;
      for (i = sequenceLength - 1; i >= 0; i--) {
        if (indexes[i] < numButtons - 1) {
          indexes[i]++;
          break;
        }
        indexes[i] = 0;
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

    this.recordedInputs.add(new Triple<>(input, coverage, state));
    if (this.numberOfInputs == this.recordedInputs.size()) {
      this.worklist = addOneToAllSequences(
          convertTripleToSequence(pruneInputs(this.recordedInputs)));
      if (this.worklist.size() > 300) {
        randomSample(this.worklist, 300);
      }
      this.recordedInputs.clear();
      this.numberOfInputs = this.worklist.size();

    }
  }

  /**
   * Prunes the given list of inputs by removing any input that has the same state
   * as another input in the list. The method compares the byte arrays (the third
   * element in each Triple) to check for equality. Inputs are considered
   * duplicates if their byte arrays are equal.
   *
   * @param inputs An ArrayList of Triple objects, where each Triple contains a
   *               TinyBoyInputSequence, a BitSet, and a byte array representing
   *               the input state.
   * @return An ArrayList of pruned Triple objects, where each Triple contains a
   *         TinyBoyInputSequence, a BitSet, and a byte array representing the
   *         input state with duplicates removed.
   */
  public static ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> pruneInputs(
      ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> inputs) {

    ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> prunedInputs = new ArrayList<>();
    outerLoop: for (Triple<TinyBoyInputSequence, BitSet, byte[]> input1 : inputs) {
      byte[] recordedState1 = input1.third();
      for (Triple<TinyBoyInputSequence, BitSet, byte[]> input2 : inputs) {
        if (input1 != input2) {
          byte[] recordedState2 = input2.third();
          if (Arrays.equals(recordedState1, recordedState2)) {
            continue outerLoop;
          }
        }
      }
      prunedInputs.add(input1);
    }

    // Subsumption
    Collections.sort(inputs, (a, b) -> {
      if (subsumedBy(a.second(), b.second())) {
        return -1;
      }
      return 1;
    });
    
    
    // Keep only the first 5 elements
    int elementsToKeep = 5;
    if (prunedInputs.size() > elementsToKeep) {
      prunedInputs = new ArrayList<>(prunedInputs.subList(0, elementsToKeep));
    }
    

    
    return prunedInputs;
  }

  /**
   * Converts a list of Triple objects containing TinyBoyInputSequence, BitSet,
   * and byte array into a list of TinyBoyInputSequence objects. The method
   * extracts the first element of each Triple (the TinyBoyInputSequence) and adds
   * it to a new ArrayList.
   *
   * @param inputs An ArrayList of Triple objects, where each Triple contains a
   *               TinyBoyInputSequence, a BitSet, and a byte array representing
   *               the input state.
   * @return An ArrayList of TinyBoyInputSequence objects extracted from the input
   *         list of Triple objects.
   */
  public static ArrayList<TinyBoyInputSequence> convertTripleToSequence(
      ArrayList<Triple<TinyBoyInputSequence, BitSet, byte[]>> inputs) {
    ArrayList<TinyBoyInputSequence> output = new ArrayList<>();

    for (Triple<TinyBoyInputSequence, BitSet, byte[]> input : inputs) {
      output.add(input.first);
    }
    return output;
  }

  /**
   * Appends one additional ControlPad.Button value to each input sequence in the
   * given list and returns a new list of TinyBoyInputSequence objects. This
   * method also increments the sequence length by 1. The appended button values
   * are taken from the ControlPad.Button enumeration.
   *
   * @param inputs An ArrayList of TinyBoyInputSequence objects to be extended by
   *               one additional button value.
   * @return An ArrayList of TinyBoyInputSequence objects with one additional
   *         button value appended to each input sequence.
   */
  public ArrayList<TinyBoyInputSequence> addOneToAllSequences(
      ArrayList<TinyBoyInputSequence> inputs) {
    this.seqLength++;
    ArrayList<TinyBoyInputSequence> output = new ArrayList<>();

    for (TinyBoyInputSequence sequence : inputs) {
      for (int i = 0; i < NUM_BUTTONS; i++) {
        output.add(sequence.append(ControlPad.Button.values()[i]));
      }
    }
    return output;
  }

  /**
   * Check whether a given input sequence is completely subsumed by another.
   *
   * @param lhs The one which may be subsumed.
   * @param rhs The one which may be subsuming.
   * @return True if lhs subsumed by rhs, false otherwise.
   */
  public static boolean subsumedBy(BitSet lhs, BitSet rhs) {
    BitSet rhsWithoutLhs = (BitSet) rhs.clone();
    rhsWithoutLhs.andNot(lhs);
    for (int i = lhs.nextSetBit(0); i >= 0; i = lhs.nextSetBit(i + 1)) {
      if (!rhsWithoutLhs.get(i)) {
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
