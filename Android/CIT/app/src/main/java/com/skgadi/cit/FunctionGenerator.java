package com.skgadi.cit;

import android.util.Log;

/**
 * Created by gadis on 14-Feb-18.
 */
enum SignalType {
    STEP,
    SINE,
    SAWTOOTH,
    TRIANGLE,
    SQUARE,
    RECTANGLE
}
public class FunctionGenerator {
    public SignalType Type;
    public double Frequency;
    public double MaximumAmplitude;
    public double StartAt;
    public double DutyCycle;
    public double OffSet;
    public double Time;
    public boolean Compliment;
    public double[][] MinMaxDefaultsForFloats = {
            {0, 20, 1}, {0, 5, 0}, {0, 10, 0}, {0, 100, 50}, {-5, 5, 0}
    };
    public FunctionGenerator() {
        Type = SignalType.STEP;
        Frequency = MinMaxDefaultsForFloats[0][2];
        MaximumAmplitude = MinMaxDefaultsForFloats[1][2];
        StartAt = MinMaxDefaultsForFloats[2][2];
        DutyCycle = MinMaxDefaultsForFloats[3][2];
        OffSet = MinMaxDefaultsForFloats[4][2];
        Time = 0;
        Compliment = false;
    }
    public void SetType (SignalType type) {
        Type = type;
    }
    public void SetType (int i) {
        Type = SignalType.values()[i];
    }
    public String GetSignalDescription () {
        switch (Type) {
            case STEP:
                return ((Compliment) ? "-":"")
                        + ((MaximumAmplitude == 0.0) ? ((OffSet==0)? "0":"") : (
                                + MaximumAmplitude
                                + "H(t"
                                + ((StartAt!=0) ? -StartAt:"")
                                + ")"
                        ))
                        + ((OffSet!=0) ? (OffSet>0?((MaximumAmplitude != 0.0)?"+":"")+OffSet:OffSet) :"");
            case SINE:
                return ((Compliment) ? "-":"")
                        + ((MaximumAmplitude == 0.0) ? ((OffSet==0)? "0":"") : (
                            + MaximumAmplitude
                            + "sin(2\u03C0"
                            + Frequency
                            + ((StartAt!=0) ? "(":"")
                            + "t"
                            + ((StartAt!=0) ? -StartAt+")":"")
                            + ")"
                        ))
                        + ((OffSet!=0) ? (OffSet>0?((MaximumAmplitude != 0.0)?"+":"")+OffSet:OffSet) :"");
            case SAWTOOTH:
                return ((Compliment) ? "-":"")
                        + ((MaximumAmplitude == 0.0) ? ((OffSet==0)? "0":"") : (
                            + MaximumAmplitude
                            + "\u00D72["+
                            + Frequency
                            + ((StartAt!=0) ? "(":"")
                            + "t"
                            + ((StartAt!=0) ? -StartAt+")":"")
                            + "-floor(0.5 + "
                            + Frequency
                            + ((StartAt!=0) ? "(":"")
                            + "t"
                            + ((StartAt!=0) ? -StartAt+")":"")
                            + ")]"
                        ))
                        + ((OffSet!=0) ? (OffSet>0?((MaximumAmplitude != 0.0)?"+":"")+OffSet:OffSet) :"");
            case TRIANGLE:
                return ((Compliment) ? "-":"")
                        + ((MaximumAmplitude == 0.0) ? ((OffSet==0)? "0":"") : (
                            + MaximumAmplitude
                            + "\u00D7\u222B{sgn[sin(2π"
                            + Frequency
                            + ((StartAt!=0) ? "(":"")
                            + "t"
                            + ((StartAt!=0) ? -StartAt+")":"")
                            + ")]}dt"
                        ))
                        + ((OffSet!=0) ? (OffSet>0?((MaximumAmplitude != 0.0)?"+":"")+OffSet:OffSet) :"");
            case SQUARE:
                return  ((Compliment) ? "-":"")
                        + ((MaximumAmplitude == 0.0) ? ((OffSet==0)? "0":"") : (
                            + MaximumAmplitude
                            + "sgn[sin(2π"
                            + Frequency
                            + ((StartAt!=0) ? "(":"")
                            + "t"
                            + ((StartAt!=0) ? -StartAt+")":"")
                            + ")]"
                        ))
                        + ((OffSet!=0) ? (OffSet>0?((MaximumAmplitude != 0.0)?"+":"")+OffSet:OffSet) :"");
            case RECTANGLE:
                return
                        ((MaximumAmplitude == 0.0) ? ((OffSet==0)? "0":"") : (
                            "A pulse train with: Amplitude = "
                            + ((Compliment) ? "-":"")
                            + 2*MaximumAmplitude + " V, "
                            + ((StartAt!=0) ? "Starts at t="+StartAt+" s, ":"")
                            + "Frequency =" + Frequency + " Hz,"
                            + ((OffSet==0)?" and ":"") +" Duty factor = " + DutyCycle +" %"
                        ))
                        + ((OffSet!=0) ? ((MaximumAmplitude != 0.0)?", and Offset =":"") + OffSet  + ((MaximumAmplitude != 0.0)?" V":"") :"");
        }
        return "";
    }
    public double GetValue (double time) {
        Time = time;
        switch (Type) {
            case STEP:
                return GenStep();
            case SINE:
                return GenSine();
            case SAWTOOTH:
                return GenSawTooth();
            case TRIANGLE:
                return GenTriangle();
            case SQUARE:
                return GetSquare();
            case RECTANGLE:
                return GetRectangle(DutyCycle);
        }
        return 0.0f;
    }
    public double GenStep() {
        if (Time>=StartAt)
            return ((!Compliment) ? 1.0 : -1.0) * MaximumAmplitude + OffSet;
        else
            return 0;
    }
    public double GenSine() {
        if (Time>=StartAt)
            return ((Compliment) ? -1.0 : 1.0) * (MaximumAmplitude * Math.sin(2 * Math.PI * Frequency * (Time-StartAt))) + OffSet;
        else
            return 0;
    }
    public double GenSawTooth() {
        double TimePeriod = 1/Frequency;
        if (Time>=StartAt)
            return ((Compliment) ? -1.0 : 1.0) * (((Time - StartAt)%TimePeriod)*2*MaximumAmplitude/TimePeriod - MaximumAmplitude) + OffSet;
        else
            return 0;
    }
    public double GenTriangle() {
        double TimePeriod = 1/Frequency;
        if (Time>=StartAt) {
            return ((!Compliment) ? 1.0 : -1.0) * (MaximumAmplitude * Math.asin(Math.sin(2 * Math.PI * Frequency * (Time-StartAt))))*2/Math.PI + OffSet;
            /*if (((Time-StartAt)%TimePeriod)<(TimePeriod/2))
                return ((!Compliment) ? -1.0 : 1.0) * (4.0*MaximumAmplitude/TimePeriod*(((Time - StartAt)%TimePeriod) - TimePeriod/2.0) + MaximumAmplitude) + OffSet;
            else
                return ((!Compliment) ? -1.0 : 1.0) * (-4.0*MaximumAmplitude/TimePeriod*(((Time - StartAt)%TimePeriod) - TimePeriod/2.0) + MaximumAmplitude) + OffSet;*/
        } else
            return 0;
    }
    public double GetSquare() {
        return GetRectangle(50);
    }
    public double GetRectangle(double DutyCycle) {
        double TimePeriod = 1.0/Frequency;
        Log.i("FunctionGenerator", "Val: "+(Time-StartAt)%TimePeriod);
        if (Time>=StartAt) {
            return ((Compliment) ? -1.0 : 1.0)*MaximumAmplitude*Math.signum(Math.sin(2*Math.PI*Frequency*((Time-StartAt)
                    + 0.5*TimePeriod*(0.5-DutyCycle/100.0)))
                    - Math.sin(2*Math.PI*Frequency*0.5*TimePeriod*(0.5-DutyCycle/100.0)))
                    + OffSet;
            /*if (((Time-StartAt)%TimePeriod)<TimePeriod*(DutyCycle/100f))
                return ((Compliment) ? -1.0 : 1.0) * MaximumAmplitude + OffSet;
            else
                return ((Compliment) ? 1.0 : -1.0) * MaximumAmplitude + OffSet;*/
        } else
            return 0;
    }

}
