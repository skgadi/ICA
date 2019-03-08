package com.skgadi.cit;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Switch;

/**
 * Created by gadis on 15-Feb-18.
 */

public class SignalTypeListener implements AdapterView.OnItemSelectedListener {
    public FunctionGenerator Signal;
    public Switch LayoutSwitch;
    public SignalTypeListener(FunctionGenerator signal, Switch layoutSwitch) {
        Signal = signal;
        LayoutSwitch = layoutSwitch;
    }
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        Signal.SetType(i);
        LayoutSwitch.setText((((String) LayoutSwitch.getText()).split("="))[0] + "=" + Signal.GetSignalDescription());
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        Signal.Type = SignalType.values()[0];
    }
}
