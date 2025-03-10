package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import static utils.AssertionHelper.Assert;

/**
 * Maps string values to integer values
 */
public class StringMap {

    // match var != NULL means the var exists
    // let var = NULL means the var is removed
    public static final Integer NONE = 65536;

    private static HashMap<String, ArrayList<String>> keys = new HashMap<>();


    public static Integer get(String field, String key) {
        if (key == null || field == null)
            return NONE;
        keys.computeIfAbsent(field, k -> new ArrayList<>());
        ArrayList<String> list = keys.get(field);
        Assert(list != null, "list should not be null");

        int index = list.indexOf(key);
        if (index == -1) {
            list.add(key);
            return list.size() - 1;
        } else {
            return index;
        }
    }

    public static String getStr(String field, Integer index) {
        if (Objects.equals(index, NONE))
            return "NONE";

        // I think this contain check can be removed
        // In current case, removing it won't lead to error
        if (!keys.containsKey(field)) {
            return "ANY";
        }
        ArrayList<String> list = keys.get(field);

        if (index >= 0 && index < list.size()) {
            return list.get(index);
        } else {
            return "NIM";
        }
    }

    public static int size(String field) {
        if (!keys.containsKey(field)) {
            return 0;
        }
        return keys.get(field).size();
    }
}
