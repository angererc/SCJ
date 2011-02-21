/*
 * Copyright 2001-6 Program of Computer Graphics, Cornell University
 *     580 Rhodes Hall
 *     Cornell University
 *     Ithaca NY 14853
 * Web: http://www.graphics.cornell.edu/
 * 
 * Not for commercial use. Do not redistribute without permission.
 */

package clustering.main;


/**
 * Generate random numbers using the same algorithm as java.util.Random, except we don't
 * actually use it because its synchronized and thus more expensive.  This
 * version is faster.
 *
 * @author bjw
 * @version $Revision: 1.8 $ $Date: 2006/01/18 23:52:45 $
 */
public class RandomGenerator {

  private long seed;
  private final static long multiplier = 0x5DEECE66DL;
  private final static long addend = 0xBL;
  private final static long mask = (1L << 48) - 1;

  /**
   * Creates new RandomGenerator.  You really should set a seed before using it.
   */
  public RandomGenerator() {
  }

  public RandomGenerator(long inSeed) {
    setSeed(inSeed);
  }

  private int nextInt(int bits) {
    seed = (seed * multiplier + addend) & mask;
    return (int) (seed >>> (48 - bits));
  }

  public double nextDouble() {
    long l = ((long) (nextInt(26)) << 27) + nextInt(27);
    return l / (double) (1L << 53);
  }

  public void setSeed(long inSeed) {
    seed = (inSeed ^ multiplier) & mask;
  }

  /**
   * Check to see that we really are equivalent to the RandomGenerator
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    RandomGenerator us = new RandomGenerator(43573489234L);
    java.util.Random sys = new java.util.Random(43573489234L);

    for (int i = 0; i < 30; i++) {
      if (us.nextDouble() != sys.nextDouble()) {
        throw new RuntimeException("did not match!");
      }
    }
    us.setSeed(64358234986L);
    sys.setSeed(64358234986L);
    for (int i = 0; i < 30; i++) {
      if (us.nextDouble() != sys.nextDouble()) {
        throw new RuntimeException("did not match!");
      }
    }
    System.out.println("passed equivalence tests");
  }

}
