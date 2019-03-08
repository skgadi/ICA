package com.skgadi.cit;

/**
 * Created by gadis on 13-Feb-18.
 */

public abstract class Model {
    public String ModelName;
    public int[] Images;
    public String[] ImageNames;
    //public String[] Ports; // 0-Out others are input
    public String[] SignalGenerators;
    public Figure Figures [];
    public Parameter[] Parameters;
    public double PlannedT_S;
    public double T_SForModel;
    public double[] OutPut;
    public int NoOfInputs;
    public int NoOfOutputs;
    public int NoOfPastInputsRequired;
    public int NoOfPastOuputsRequired;
    public int NoOfPastGeneratedValuesRequired;
    public double SimulationTime;
    //public double OutputTime;
    public abstract double[] RunAlgorithms(
            double[] Parameters,
            double[][] Generated,
            double[][] Input,
            double[][] Output
    );
    public abstract double[] OutGraphSignals (
            double[] Parameters,
            double[][] Generated,
            double[][] Input,
            double[][] Output
    );
}