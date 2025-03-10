package utils;

public class IPHelper {
    public static Integer getIPAddress(String address) {
       if (address == null) {
           return null;
       }

       Integer mask = getIPMask("0.0.0.0/32");
       if (address.contains("/")) {
           mask = getIPMask(address);
           address = address.split("/")[0];
       }

       int result = 0;
       String[] parts = address.split("\\.");
       for (int i = 0; i < 4; i++) {
           result += Integer.parseInt(parts[i]) << (8 * (3 - i));
       }
       return result & mask;
    }

    public static Integer getIPMask(String address) {
        if (address == null) {
            return null;
        }

        if (!address.contains("/")) {
            return null;
        }

        int length = Integer.parseInt(address.split("/")[1]);
        int result = 0;
        for (int i = 0; i < length; i++) {
            result += 1 << (31 - i);
        }
        return result;
    }

    public static String decodeIP(Integer address) {
        if (address == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            result.insert(0, (address & 0xff));
            if (i != 3) {
                result.insert(0, ".");
            }
            address = address >> 8;
        }
        return result.toString();
    }
}
