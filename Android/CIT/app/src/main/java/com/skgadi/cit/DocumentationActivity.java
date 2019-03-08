package com.skgadi.cit;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.skgadi.cit.R;

public class DocumentationActivity extends AppCompatActivity {

    private LinearLayout LLayout00;
    private LinearLayout LLayout01;
    private LinearLayout LLayout02;
    private LinearLayout LLayout03;

    private Switch DSwitch00;
    private Switch DSwitch01;
    private Switch DSwitch02;
    private Switch DSwitch03;

    SubsamplingScaleImageView ImageView01;
    SubsamplingScaleImageView ImageView02;
    SubsamplingScaleImageView ImageView03;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_documentation);
        setTitle(R.string.Documentation_Title);


        //Added by SKGadi
        ImageView02 = findViewById(R.id.imageView2);
        ImageView03 = findViewById(R.id.imageView3);

        ImageView02.setImage(ImageSource.resource(R.drawable.bridge_circuit_first_order_bb));
        ImageView03.setImage(ImageSource.resource(R.drawable.bridge_circuit_second_order_bb));

        LLayout00 = findViewById(R.id.DocumentLayout00);
        LLayout02 = findViewById(R.id.DocumentLayout02);
        LLayout03 = findViewById(R.id.DocumentLayout03);

        DSwitch00 = findViewById(R.id.Switch00);
        DSwitch02 = findViewById(R.id.Switch02);
        DSwitch03 = findViewById(R.id.Switch03);

        DSwitch00.setOnCheckedChangeListener(new LayoutSwitch(LLayout00));
        DSwitch02.setOnCheckedChangeListener(new LayoutSwitch(LLayout02));
        DSwitch03.setOnCheckedChangeListener(new LayoutSwitch(LLayout03));

        DSwitch00.setTextSize(18);
        DSwitch02.setTextSize(18);
        DSwitch03.setTextSize(18);

    }

    public void OpenFirmwareURL (View v) {
        Uri uri = Uri.parse("https://raw.githubusercontent.com/skgadi/ControlToolbox/master/Bridge/Arduino/latest.pde");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);
    }
}
