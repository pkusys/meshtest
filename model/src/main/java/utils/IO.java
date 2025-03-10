package utils;

public class IO {
    public static void Highlight(String str) {
        // Highlight should be bold and in yellow
        System.out.println("\033[1;33m" + "Note: " + str + "\033[0m");
    }

    public static void Info(String str) {
        // Info should be shallower and in default color
        System.out.println("\033[0m" + "Info: " + str + "\033[0m");
    }

    public static void Error(String str) {
        // Error should be bold and in red
        System.out.println("\033[1;31m" + "Error: " + str + "\033[0m");
    }
}
