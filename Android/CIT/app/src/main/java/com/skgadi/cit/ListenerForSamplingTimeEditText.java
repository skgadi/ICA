package com.skgadi.cit;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Switch;

public class ListenerForSamplingTimeEditText implements TextWatcher {
    public Switch LayoutSwitch;

    public ListenerForSamplingTimeEditText (Switch layoutSwitch) {
        LayoutSwitch = layoutSwitch;
    }


    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        try {
            LayoutSwitch.setText("T_s = " + Double.parseDouble(editable.toString()) + " ms");
        } catch (Exception E) {
            LayoutSwitch.setText("T_s = 0.0 ms");
            editable.append("0");

        }

    }
}
