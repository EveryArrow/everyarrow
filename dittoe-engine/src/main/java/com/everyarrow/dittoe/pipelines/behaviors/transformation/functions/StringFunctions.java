package com.everyarrow.dittoe.pipelines.behaviors.transformation.functions;

import com.everyarrow.dittoe.pipelines.behaviors.transformation.util.FunctionUtils;

/**
 * Provides implementations for string functions.
 */
public class StringFunctions {
    public static Object concat(Object[] args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            sb.append(arg != null ? arg.toString() : "null");
        }
        return sb.toString();
    }

    public static Object lower(Object[] args) {
        if (args.length != 1) throw new RuntimeException("lower requires 1 argument");
        return ((String) args[0]).toLowerCase();
    }

    public static Object upper(Object[] args) {
        if (args.length != 1) throw new RuntimeException("upper requires 1 argument");
        return ((String) args[0]).toUpperCase();
    }

    public static Object length(Object[] args) {
        if (args.length != 1) throw new RuntimeException("length requires 1 argument");
        return ((String) args[0]).length();
    }

    public static Object substring(Object[] args) {
        if (args.length != 3) throw new RuntimeException("substring requires 3 arguments");
        String text = (String) args[0];
        int start = FunctionUtils.toInt(args[1]);
        int len = FunctionUtils.toInt(args[2]);
        if (start < 0 || start > text.length()) {
            throw new RuntimeException("substring: start index out of bounds");
        }
        int end = Math.min(start + len, text.length());
        return text.substring(start, end);
    }

    public static Object trim(Object[] args) {
        if (args.length != 1) throw new RuntimeException("trim requires 1 argument");
        return ((String) args[0]).trim();
    }

    public static Object ltrim(Object[] args) {
        if (args.length != 1) throw new RuntimeException("ltrim requires 1 argument");
        return ((String) args[0]).replaceAll("^\\s+", "");
    }

    public static Object rtrim(Object[] args) {
        if (args.length != 1) throw new RuntimeException("rtrim requires 1 argument");
        return ((String) args[0]).replaceAll("\\s+$", "");
    }

    public static Object replace(Object[] args) {
        if (args.length != 3) throw new RuntimeException("replace requires 3 arguments");
        return ((String) args[0]).replace((String) args[1], (String) args[2]);
    }

    public static Object lpad(Object[] args) {
        if (args.length != 3) throw new RuntimeException("lpad requires 3 arguments");
        StringBuilder str = new StringBuilder((String) args[0]);
        int length = FunctionUtils.toInt(args[1]);
        String pad = (String) args[2];
        while (str.length() < length) {
            str.insert(0, pad);
        }
        return str.toString();
    }

    public static Object rpad(Object[] args) {
        if (args.length != 3) throw new RuntimeException("rpad requires 3 arguments");
        StringBuilder str = new StringBuilder((String) args[0]);
        int length = FunctionUtils.toInt(args[1]);
        String pad = (String) args[2];
        while (str.length() < length) {
            str.append(pad);
        }
        return str.toString();
    }

    public static Object instr(Object[] args) {
        if (args.length != 2) throw new RuntimeException("instr requires 2 arguments");
        int index = ((String) args[0]).indexOf((String) args[1]);
        return index >= 0 ? index + 1 : 0;  // 1-indexed; 0 if not found.
    }

    public static Object initcap(Object[] args) {
        if (args.length != 1) throw new RuntimeException("initcap requires 1 argument");
        String str = (String) args[0];
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    public static Object ascii(Object[] args) {
        if (args.length != 1) throw new RuntimeException("ascii requires 1 argument");
        String str = (String) args[0];
        if (str.isEmpty()) throw new RuntimeException("ascii: empty string");
        return (int) str.charAt(0);
    }

    public static Object chr(Object[] args) {
        if (args.length != 1) throw new RuntimeException("chr requires 1 argument");
        int code = FunctionUtils.toInt(args[0]);
        return String.valueOf((char) code);
    }
}
