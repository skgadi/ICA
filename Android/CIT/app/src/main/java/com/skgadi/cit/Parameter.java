package com.skgadi.cit;

/**
 * Created by gadis on 14-Feb-18.
 */


public class Parameter {
    public String Name;
    public double Min;
    public double Max;
    public double DefaultValue;
    Parameter (String name, double min, double max, double defaultValue) {
        Name = name;
        Min = min;
        Max = max;
        DefaultValue = defaultValue;
    }
}