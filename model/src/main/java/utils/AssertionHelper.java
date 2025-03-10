package utils;

public class AssertionHelper {
    static public void Assert(boolean condition, String msg) {
        if (!condition) {
            throw new RuntimeException(msg);
        }
    }
}
