package utils;

import java.util.Random;

public class Rand {
    public static Random generator;
    public static void setSeed(long seed) {
        generator = new Random(seed);
    }

    // Returns a random integer in the range [lower, upper)
    public static int RandInt(int lower, int upper) {
        return generator.nextInt(upper - lower) + lower;
    }

    public static int RandInt(int upper) {
        return RandInt(0, upper);
    }

    // Returns a random double in the range [0, 1)
    public static double RandDouble() {
        return generator.nextDouble();
    }

    /**
     * true ratio is trueRatio / 100.
     */
    public static boolean RandBool(int trueRatio) {
        return RandInt(100) < trueRatio;
    }
}
