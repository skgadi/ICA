package com.skgadi.cit;

import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.github.mikephil.charting.charts.LineChart;

public class ListenerForGraphZoomCheck implements CheckBox.OnCheckedChangeListener {

    private LineChart lineChart;
    ListenerForGraphZoomCheck(LineChart LC) {
        lineChart = LC;
    }
    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        lineChart.getAxisLeft().setStartAtZero(b);
        lineChart.getAxisRight().setStartAtZero(b);
    }
}
