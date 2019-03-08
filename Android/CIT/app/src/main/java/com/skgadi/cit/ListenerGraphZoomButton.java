package com.skgadi.cit;

import android.view.View;
import android.widget.Button;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;

enum ZOOM_AND_MOVE {
    RESET,
    ZOOM_IN,
    ZOOM_OUT,
/*    UP,
    DOWN,
    RIGHT,
    LEFT*/
}

public class ListenerGraphZoomButton implements Button.OnClickListener {

    private LineChart lineChart;
    private ZOOM_AND_MOVE ZOOM_AND_MOVE;

    ListenerGraphZoomButton(LineChart LC, ZOOM_AND_MOVE ZO) {
        lineChart = LC;
        ZOOM_AND_MOVE = ZO;
    }
    @Override
    public void onClick(View view) {
        switch (ZOOM_AND_MOVE){
            case RESET:
                lineChart.fitScreen();
                break;
            case ZOOM_IN:
                lineChart.zoomIn();
                break;
            case ZOOM_OUT:
                lineChart.zoomOut();
                break;
/*            case UP:
                break;
            case DOWN:
                break;
            case RIGHT:
                break;
            case LEFT:
                break;*/
        }
    }
}
