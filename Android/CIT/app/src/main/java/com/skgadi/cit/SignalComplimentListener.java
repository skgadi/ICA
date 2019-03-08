package com.skgadi.cit;

import android.widget.CompoundButton;
import android.widget.Switch;

/**
 * Created by gadis on 15-Feb-18.
 */

public class SignalComplimentListener implements CompoundButton.OnCheckedChangeListener {
    public FunctionGenerator Signal;
    public Switch LayoutSwitch;
    SignalComplimentListener (FunctionGenerator signal, Switch layoutSwitch) {
        Signal = signal;
        LayoutSwitch = layoutSwitch;
    }
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        Signal.Compliment = b;
        LayoutSwitch.setText((((String) LayoutSwitch.getText()).split("="))[0] + "=" + Signal.GetSignalDescription());
    }
}
