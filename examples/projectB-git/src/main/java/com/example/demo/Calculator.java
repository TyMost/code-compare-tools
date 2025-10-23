package com.example.demo;

public class Calculator {

    public int add(int left, int right) {
        int result = left + right;
        if (Math.abs(result) > 100) {
            throw new IllegalArgumentException("Sum overflow");
        }
        return result;
    }

    public int subtract(int left, int right) {
        int result = left - right;
        if (result < 0) {
            return 0;
        }
        return result;
    }

    public int multiply(int left, int right) {
        return Math.multiplyExact(left, right);
    }

    public int safeDivide(int left, int right) {
        if (right == 0) {
            throw new IllegalArgumentException("Cannot divide by zero");
        }
        return left / right;
    }

    public String version() {
        return "2.0.0";
    }

    public String auditMessage() {
        return "calculator:v2";
    }
}
