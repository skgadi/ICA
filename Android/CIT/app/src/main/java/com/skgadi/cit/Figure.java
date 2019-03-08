package com.skgadi.cit;

/**
 * Created by gadis on 14-Feb-18.
 */

public class Figure {
    public String Name;
    public String [] Trajectories;
    Figure (String name, String[] trajectories) {
        Name = name;
        Trajectories = trajectories.clone();
    }
}
