package com.skgadi.cit;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.CompoundButton;

/**
 * Created by gadis on 15-Feb-18.
 */

public class LayoutSwitch  implements CompoundButton.OnCheckedChangeListener {
    LinearLayout Layout;
    public LayoutSwitch(LinearLayout layout) {
        Layout = layout;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked)
            Layout.setVisibility(View.VISIBLE);
        else
            Layout.setVisibility(View.GONE);
    }
}
