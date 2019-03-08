package com.skgadi.cit;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.mikepenz.fontawesome_typeface_library.FontAwesome;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.equation.Equation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;


import static android.os.Environment.DIRECTORY_PICTURES;


enum SIMULATION_STATUS {
    DISABLED,
    OFF,
    ON
}

public class MainActivity extends AppCompatActivity {

    private LinearLayout.LayoutParams DefaultLayoutParams;

    private boolean CloseApp;
    protected String[] ScreensList;
    MenuItem SettingsButton;
    MenuItem SimulateButton;
    MenuItem DocumentationButton;

    LinearLayout ModelView;
    EditText[] ModelParams;
    TableLayout InstantaneousValues;
    EditText ModelSamplingTime;
    //GraphView[] ModelGraphs;
    LineChart[] LineCharts;
    LinearLayout[] ZoomOptions;
    int[] ColorTable = {
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.rgb(128,0,0),
            Color.rgb(128,128,0),
            Color.rgb(0,128,0),
            Color.rgb(128,0,128),
            Color.rgb(0,128,128),
            Color.rgb(0,0,128),
            Color.YELLOW,
            Color.MAGENTA
    };
    ScrollView MainScrollView;
    //--- Screenshot related
    private LinearLayout RootLayout;
    private TextView TextForImageSharing;
    Bitmap bitmap;

    double[] AnalogOutLimits = {0, 5};
    double[] TrajectoryLimits = {-10000, 10000};

    Toolbar AppToolbar;
    Drawer AppNavDrawer;
    //Communication
    public Arduino arduino;
    boolean DeviceConnected = false;
    SIMULATION_STATUS SimulationState;

    com.skgadi.cit.Model Model;
    FunctionGenerator[] GeneratedSignals;

    Simulate SimHandle;

    int LinesColor = Color.rgb(150, 65, 165);



    //--- Back button handling
    @Override
    public void onBackPressed() {
        if (AppNavDrawer.isDrawerOpen())
            AppNavDrawer.closeDrawer();
        else if (SimulationState == SIMULATION_STATUS.ON)
            Toast.makeText(getApplicationContext(),
                    getResources().getStringArray(R.array.TOASTS)[9],
                    Toast.LENGTH_SHORT).show();
        else
            super.onBackPressed();
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //--- Var vals
        MainScrollView = (ScrollView) findViewById(R.id.MainScrollView);
        RootLayout = (LinearLayout) findViewById(R.id.RootLayout);
        ModelView = (LinearLayout)findViewById(R.id.ModelView);
        DefaultLayoutParams =  new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        SimulationState = SIMULATION_STATUS.DISABLED;
        AppToolbar =  findViewById(R.id.AppToolbar);
        ScreensList = getResources().getStringArray(R.array.SCREENS_LIST);
        //--- ShowToolBar
        SetupToolbar();
        //--- Navigation Menu (https://github.com/mikepenz/MaterialDrawer)
        CustomNavigationBuilder();
        //---- Setup default home screen
        GenerateViewFromModel(-1);
        //--- USB Connection
        arduino = new Arduino(getApplicationContext(), 115200);
        //arduino.addVendorId(4799);

        arduino.setArduinoListener(new ArduinoListener() {
            @Override
            public void onArduinoAttached(UsbDevice device) {
                arduino.open(device);
                DeviceConnected = false;
            }

            @Override
            public void onArduinoDetached() {
                DeviceConnected = false;
                if(SimHandle != null) {
                    SimHandle.cancel(true);
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[13],
                            Toast.LENGTH_SHORT).show();
                }
                SetProperSimulationStatus();
            }

            @Override
            public void onArduinoMessage(byte[] bytes) {
                DoThisWhenReceivedDataFromUSB(bytes);
            }

            @Override
            public void onArduinoOpened() {
                DeviceConnected = true;
                SetProperSimulationStatus();
            }

            @Override
            public void onUsbPermissionDenied() {
                arduino.reopen();
                DeviceConnected = false;
            }

        });

    }
    public void DoThisWhenReceivedDataFromUSB (final byte[] bytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //DataRecUpdateForHex(bytes);
                DataRecUpdate(bytes);
            }
        });
    }
    public void SendToUSB (final byte[] bytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                arduino.send(bytes);
            }
        });
    }

    private void SetupToolbar () {
        setSupportActionBar(AppToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name_full);
        }
        //getSupportActionBar().setSubtitle("Time domain");
    }

    private void CustomNavigationBuilder () {
        AccountHeader headerResult = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.logo)
                .withOnAccountHeaderSelectionViewClickListener(new AccountHeader.OnAccountHeaderSelectionViewClickListener() {
                    @Override
                    public boolean onClick(View view, IProfile profile) {
                        getSupportActionBar().setSubtitle("");
                        AppNavDrawer.setSelection(-1);
                        AppNavDrawer.closeDrawer();
                        GenerateViewFromModel(-1);
                        Model = null;
                        SetProperSimulationStatus();
                        return false;
                    }
                })
                .build();
        AppNavDrawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(AppToolbar)
                .withAccountHeader(headerResult)
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        // do something with the clicked item :D
                        AppNavDrawer.closeDrawer();
                        if (GenerateViewFromModel((int)drawerItem.getIdentifier()))
                            getSupportActionBar().setSubtitle(Model.ModelName);
                        SetProperSimulationStatus();
                        return true;
                    }
                })
                .build();
        AppNavDrawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        String[] ScreensList = getResources().getStringArray(R.array.SCREENS_LIST);

        //AddAFolderToNavigation(getResources().getStringArray(R.array.NAV_HEADS)[0]);
        AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_0)[0], 0);
        AppNavDrawer.addItem(new DividerDrawerItem());

        AddAFolderToNavigation(getResources().getStringArray(R.array.NAV_HEADS)[1]);
        AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_1)[0], 1);
        AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_1)[1], 2);
        //AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_1)[2], 3);
        AppNavDrawer.addItem(new DividerDrawerItem());

        //AddAFolderToNavigation(getResources().getStringArray(R.array.NAV_HEADS)[2]);
        AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_2)[0], 4);
        AppNavDrawer.addItem(new DividerDrawerItem());

        /*AddAFolderToNavigation(getResources().getStringArray(R.array.NAV_HEADS)[3]);
        AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_3)[0], 5);
        AddItemToNavigation(getResources().getStringArray(R.array.NAV_ITEMS_3)[1], 6);
        AppNavDrawer.addItem(new DividerDrawerItem());*/


        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            AppNavDrawer.addItem(new PrimaryDrawerItem()
                    .withIdentifier(-1)
                    .withName("Version " + pInfo.versionName + " (" + pInfo.versionCode + ")")
                    .withEnabled(false));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void AddAFolderToNavigation(String Name) {
        AppNavDrawer.addItem(new PrimaryDrawerItem()
                .withIdentifier(-1)
                .withName(Name)
                .withEnabled(false)
        );
    }

    private void AddItemToNavigation(String Name, int Key) {
        AppNavDrawer.addItem(new PrimaryDrawerItem()
                        .withIdentifier(Key)
                        .withName(Name)
                .withIcon(FontAwesome.Icon.faw_star)
        );
    }

    private void DisableDrawer(){
        AppNavDrawer.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        AppNavDrawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(false);
    }
    private void EnableDrawer() {
        AppNavDrawer.getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        AppNavDrawer.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
    }
    /**
     * This method converts dp unit to equivalent pixels, depending on device density.
     *
     * @param dp A value in dp (density independent pixels) unit. Which we need to convert into pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent px equivalent to dp depending on device density
     */
    public static float convertDpToPixel(float dp, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float px = dp * ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return px;
    }

    /**
     * This method converts device specific pixels to density independent pixels.
     *
     * @param px A value in px (pixels) unit. Which we need to convert into db
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent dp equivalent to px value
     */
    public static float convertPixelsToDp(float px, Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        float dp = px / ((float)metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT);
        return dp;
    }

    private void DrawAVLine(LinearLayout ParentView) {
        View TempVSep = new View(getApplicationContext());
        TempVSep.setLayoutParams(new TableRow.LayoutParams(2, TableRow.LayoutParams.MATCH_PARENT));
        TempVSep.setBackgroundColor(LinesColor);
        ParentView.addView(TempVSep);
    }
    private void DrawAHLine(LinearLayout ParentView) {
        View TempView = new View(getApplicationContext());
        TempView.setMinimumHeight(2);
        TempView.setBackgroundColor(LinesColor);
        ParentView.addView(TempView);
    }

    private void ClearTheModelView () {
        if (ModelView.getChildCount() >0 )
            ModelView.removeAllViews();
    }

    public void putPref(String key, String value) {
        Context context = this.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.commit();
    }

    public int getPrefInt(String key, int DefaultValue) {
        Context context = this.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.valueOf(preferences.getString(key, String.valueOf(DefaultValue)));
    }

    public boolean getPrefBool(String key, boolean DefaultValue) {
        Context context = this.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean(key, DefaultValue);
    }

    public String getPrefString(String key, String DefaultValue) {
        Context context = this.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(key, DefaultValue);
    }

    private boolean GenerateViewFromModel (int i) {
        boolean FoundItem = true;
        switch (i) {
            case 0:
                PrepareOpenLoopModel();
                break;
            case 1:
                PrepareGeneralFirstOrderIdentification();
                break;
            case 2:
                PrepareGeneralSecondOrderIdentification();
                break;
            case 3:
                PrepareFirstOrderWithControllerIdentification();
                break;
            case 4:
                PreparePIDModel();
                break;
            case 5:
                PrepareFirstOrderAdaptiveControlModel();
                break;
            case 6:
                PrepareSecondOrderAdaptiveControlModel();
                break;
            default:
                FoundItem = false;
                SetHomeScreenToDefaultState();
                break;
        }
        if (FoundItem)
            GenerateViewFromModel();
        return FoundItem;
    }

    private void SetHomeScreenToDefaultState() {
        ClearTheModelView ();
        SubsamplingScaleImageView TempImgView = (SubsamplingScaleImageView) getLayoutInflater().inflate(R.layout.gsk_imageview, null);
        TempImgView.setImage(ImageSource.resource(R.drawable.background));
        ModelView.addView(TempImgView);

    }

    private void GenerateViewFromModel() {
        //Removing previous view
        ClearTheModelView ();
        ModelView.setBackgroundColor(Color.WHITE);
        TextView TempTextView;
        LinearLayout TempLayout;
        Switch TempSwitchForLayout;
        DrawAHLine(ModelView);
        //Add an Image
        DrawAHLine(ModelView);
        for (int i=0; i<Model.Images.length; i++) {
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[0]
                    + ": " + Model.ImageNames[i]);
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

            SubsamplingScaleImageView TempImgView = (SubsamplingScaleImageView) getLayoutInflater().inflate(R.layout.gsk_imageview, null);
            TempImgView.setImage(ImageSource.resource(Model.Images[i]));
            //ImageView TempImgView = new ImageView(getApplicationContext());
            //TempImgView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            //TempImgView.setImageResource(Model.Images[i]);
            //TempImgView.setAdjustViewBounds(true);
            TempLayout.addView(TempImgView);


            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawAHLine(ModelView);
        }
        // Sampling Time
        DrawAHLine(ModelView);
        TempLayout = new LinearLayout(getApplicationContext());
        TempLayout.setOrientation(LinearLayout.VERTICAL);
        TempSwitchForLayout = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
        TempSwitchForLayout.setTextColor(Color.BLACK);
        TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
        TempSwitchForLayout.setChecked(true);
        TempSwitchForLayout.setText(
                "T_s = "
                        + String.valueOf(getPrefInt("sim_sampling_time", 100))
                        + " ms"
        );
        TempSwitchForLayout.setTextSize(18);
        TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
        TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

        TempLayout.setOrientation(LinearLayout.HORIZONTAL);
        TempTextView = new TextView(getApplicationContext());
        TempTextView.setTextColor(Color.BLACK);
        TempTextView.setText(getString(R.string.SAMPLING_TIME)+" :");
        TempTextView.setTypeface(null, Typeface.BOLD);
        TempLayout.addView(TempTextView);
        ModelSamplingTime = (EditText) getLayoutInflater().inflate(R.layout.gsk_text_editor, null);
        ModelSamplingTime.setSelectAllOnFocus(true);
        ModelSamplingTime.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_CLASS_NUMBER);
        ModelSamplingTime.setText(String.valueOf(getPrefInt("sim_sampling_time", 100)));
        ModelSamplingTime.setTextColor(Color.BLACK);
        TempTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ModelSamplingTime.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ModelSamplingTime.addTextChangedListener(new ListenerForSamplingTimeEditText(TempSwitchForLayout));
        TempLayout.addView(ModelSamplingTime);

        ModelView.addView(TempSwitchForLayout);
        ModelView.addView(TempLayout);
        TempSwitchForLayout.setChecked(false);
        DrawAHLine(ModelView);
        //Parameters
        if (Model.Parameters.length>0) {
            DrawAHLine(ModelView);
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[2]);
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

            ModelParams = new EditText[Model.Parameters.length];
            for (int i = 0; i < Model.Parameters.length; i++) {
                LinearLayout TempHorizontalLayout = new LinearLayout(getApplicationContext());
                TempHorizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
                TempHorizontalLayout.setLayoutParams(new
                        LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));

                TempTextView = new TextView(getApplicationContext());
                TempTextView.setTextColor(Color.BLACK);
                if (Model.Parameters[i].Name.contains(">>")) {
                    String[] TempTitles = Model.Parameters[i].Name.split(">>");
                    TempTextView.setText(TempTitles[0]);
                    TempTextView.setTypeface(null, Typeface.BOLD);
                    TempLayout.addView(TempTextView);
                    TempTextView = new TextView(getApplicationContext());
                    TempTextView.setTextColor(Color.BLACK);
                    TempTextView.setText(TempTitles[1]);
                } else
                    TempTextView.setText(Model.Parameters[i].Name);
                TempTextView.setText(TempTextView.getText() + ": ");
                ModelParams[i] = (EditText) getLayoutInflater().inflate(R.layout.gsk_text_editor, null);
                ModelParams[i].setSelectAllOnFocus(true);
                ModelParams[i].setText(String.valueOf(Model.Parameters[i].DefaultValue));
                ModelParams[i].setTextColor(Color.BLACK);
                TempTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ModelParams[i].setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TempHorizontalLayout.addView(TempTextView);
                TempHorizontalLayout.addView(ModelParams[i]);
                TempLayout.addView(TempHorizontalLayout);
            }
            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawAHLine(ModelView);
        }
        //Function generator
        DrawAHLine(ModelView);
        GeneratedSignals = new FunctionGenerator[Model.SignalGenerators.length];
        for (int i=0; i<Model.SignalGenerators.length; i++) {
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            /*TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[3]
                    + ": "
                    + Model.SignalGenerators[i]
                    + "=0"
            );*/
            TempSwitchForLayout.setText(Model.SignalGenerators[i] + "=0");
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));
            //SignalType
            Spinner TempFunctionsView = (Spinner) getLayoutInflater().inflate(R.layout.gsk_spinner, null);
            TempFunctionsView.setLayoutParams(new Spinner.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final List<String> SignalsList =
                    new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.AVAILABLE_SIGNALS)));
            final ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
                    this,R.layout.support_simple_spinner_dropdown_item,SignalsList);
            TempFunctionsView.setAdapter(spinnerArrayAdapter);
            //TempFunctionsView.getBackground().setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            TempLayout.addView(TempFunctionsView);
            GeneratedSignals[i] = new FunctionGenerator();
            TempFunctionsView.setOnItemSelectedListener(
                    new SignalTypeListener(GeneratedSignals[i], TempSwitchForLayout));
            //Floats
            LinearLayout TempHorizontalLayout = new LinearLayout(getApplicationContext());
            TempHorizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
            TempHorizontalLayout.setLayoutParams(new
                    LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            for (int j=0; j<5; j++) {
                TempTextView = new TextView(getApplicationContext());
                TempTextView.setTextColor(Color.BLACK);
                TempTextView.setText(getResources().getStringArray(R.array.SIGNAL_GENERATOR_PARAMETERS)[j]+": ");
                EditText TempEditText = (EditText) getLayoutInflater().inflate(R.layout.gsk_text_editor, null);
                TempEditText.setSelectAllOnFocus(true);
                TempEditText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                /*
                //This feature can be used when the bridge circuit is ready to handle the negative voltages
                if (j==4)
                    TempEditText.setInputType(TempEditText.getInputType() | InputType.TYPE_NUMBER_FLAG_SIGNED);*/
                TempEditText.setText(String.valueOf(GeneratedSignals[i].MinMaxDefaultsForFloats[j][2]));
                TempEditText.setTextColor(Color.BLACK);
                TempEditText.addTextChangedListener(new ListenerForFunctionGenerator(
                        GeneratedSignals[i], j, TempSwitchForLayout
                ));
                TempTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TempEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TempHorizontalLayout.addView(TempTextView);
                TempHorizontalLayout.addView(TempEditText);
                //TempHorizontalLayout.setGravity(Gravity.CENTER);
            }
            TempLayout.addView(TempHorizontalLayout);
            //Compliment
            Switch TempSwitchForCompliment = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
            TempSwitchForCompliment.setChecked(false);
            TempSwitchForCompliment.setText(R.string.INVERT_SIGNAL);
            TempSwitchForCompliment.setTextColor(Color.BLACK);
            TempSwitchForCompliment.setOnCheckedChangeListener(
                    new SignalComplimentListener(GeneratedSignals[i], TempSwitchForLayout));
            TempLayout.addView(TempSwitchForCompliment);


            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawAHLine(ModelView);
        }

        //Graphs
        DrawAHLine(ModelView);
        //ModelGraphs = new GraphView[Model.Figures.length];
        LineCharts = new LineChart[Model.Figures.length];
        ZoomOptions = new LinearLayout[Model.Figures.length];
        for (int i=0; i<Model.Figures.length; i++) {
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[4]
                    + " " +((int)i+1) + ": "
                    + Model.Figures[i].Name);
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));
            LineCharts[i] = new LineChart(getApplicationContext());
            TempLayout.addView(LineCharts[i]);


            ZoomOptions[i] = new LinearLayout(getApplicationContext());
            ZoomOptions[i].setOrientation(LinearLayout.HORIZONTAL);
            ZoomOptions[i].setGravity(Gravity.CENTER);
            ZoomOptions[i].setVisibility(View.GONE);
            ImageButton TempButton;
            Integer[] TempImages= {
                    R.drawable.ic_zoom_out_map_black_24dp,
                    R.drawable.ic_zoom_in_black_24dp,
                    R.drawable.ic_zoom_out_black_24dp,
                    R.drawable.ic_keyboard_arrow_up_black_24dp,
                    R.drawable.ic_keyboard_arrow_down_black_24dp,
                    R.drawable.ic_keyboard_arrow_right_black_24dp,
                    R.drawable.ic_keyboard_arrow_left_black_24dp
            };
            for (ZOOM_AND_MOVE ZO: ZOOM_AND_MOVE.values()) {
                TempButton = (ImageButton) getLayoutInflater().inflate(R.layout.gsk_button, null);
                TempButton.setImageResource(TempImages[ZO.ordinal()]);
                TempButton.setOnClickListener(new ListenerGraphZoomButton(LineCharts[i], ZO));
                ZoomOptions[i].addView(TempButton);
            }
            TempLayout.addView(ZoomOptions[i]);
            CheckBox TempCheckBox= (CheckBox) getLayoutInflater().inflate(R.layout.gsk_checkbox, null);
            TempCheckBox.setOnCheckedChangeListener( new ListenerForGraphZoomCheck(LineCharts[i]));
            ZoomOptions[i].addView(TempCheckBox);

            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawAHLine(ModelView);
        }
        DrawAHLine(ModelView);

        //Instantaneous Values
        TempLayout = new LinearLayout(getApplicationContext());
        TempLayout.setOrientation(LinearLayout.VERTICAL);
        TempSwitchForLayout = (Switch) getLayoutInflater().inflate(R.layout.gsk_switch, null);
        TempSwitchForLayout.setTextColor(Color.BLACK);
        TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
        TempSwitchForLayout.setChecked(true);
        TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[5]);
        TempSwitchForLayout.setTextSize(18);
        TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
        TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));
        InstantaneousValues = (TableLayout) getLayoutInflater().inflate(R.layout.gsk_table_layout, null);
        TempLayout.addView(InstantaneousValues);
        ModelView.addView(TempSwitchForLayout);
        ModelView.addView(TempLayout);
        TempSwitchForLayout.setChecked(false);
        DrawAHLine(ModelView);
        DrawAHLine(ModelView);

        //InstantaneousValues
    }

    private void PrepareOpenLoopModel() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[2];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[0]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_0)[0];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=1;
        Model.NoOfPastInputsRequired = 0;
        Model.NoOfPastOuputsRequired = 0;
        Model.NoOfPastGeneratedValuesRequired = 0;
        Model.OutPut = new double[0];
        //Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.openloop;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Open loop system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u_1(t)";
        Model.SignalGenerators[1] = "u_2(t)";
        Model.SignalGenerators[2] = "u_3(t)";
        Model.Figures = new Figure[1];
        String[] TempTrajectories = new String[2];
        TempTrajectories[0]= "Input u(t)";
        TempTrajectories[1]= "Output y(t)";
        Model.Figures[0] = new Figure("Input u(t) and Output y(t)", TempTrajectories);
        Model.Parameters = new Parameter [0];
    }

    private void PreparePIDModel() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                double K_P = Parameters[0];
                double K_I = Parameters[1];
                double K_D = Parameters[2];
                double a = K_P + K_I* T_SForModel /2.0 + K_D/ T_SForModel;
                double b = -K_P + K_I* T_SForModel /2.0 - 2.0*K_D/ T_SForModel;
                double c = K_D/ T_SForModel;
                double[] E = new double[3];
                for (int i=0; i<3; i++)
                    E[i] = ((Generated[0][i] + Generated[1][i] + Generated[2][i]) - Input[0][i]);
                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[1] = Output[1][0] + a * E[0] + b * E[1] + c * E[2];
                OutSignals[0] = OutSignals[1] + Parameters[3] + Parameters[4] * (1-2*Math.random());
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[4];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Trajectories[0]-Input[0][0];
                Trajectories[3] = Output[0][0];
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[2]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_2)[0];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=2;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.pid;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Closed loop system with PID  controller";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "r_1(t)";
        Model.SignalGenerators[1] = "r_2(t)";
        Model.SignalGenerators[2] = "r_3(t)";
        Model.Figures = new Figure[2];
        String[] TempTrajectories = new String[2];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        Model.Figures[0] = new Figure("Reference r(t) and Output y(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Error e(t)";
        TempTrajectories[1]= "Control u(t)";
        Model.Figures[1] = new Figure("Error e(t) and Control u(t)", TempTrajectories);
        Model.Parameters = new Parameter [5];
        Model.Parameters[0] = new Parameter("Controller parameters>>K_P", 0, 100, 1);
        Model.Parameters[1] = new Parameter("K_I", 0, 10, 10);
        Model.Parameters[2] = new Parameter("K_D", 0, 1, 0);
        Model.Parameters[3] = new Parameter("Disturbance parameters>>Constant perturbation (d_1)", -1, 1, 0);
        Model.Parameters[4] = new Parameter("Noise constant (d_2)", 0, 1, 0);
    }

    private void PrepareFirstOrderAdaptiveControlModel() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> a_r
                    Output[2] --> a_y
                    Output[3] --> y_m
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                double Gamma = Parameters[0];
                double A_m = Parameters[1];
                double B_m = Parameters[2];

                double[] E = new double[3];
                double[] R = new double[3];
                for (int i=0; i<3; i++)
                    R[i] = Generated[0][i] + Generated[1][i] + Generated[2][i];
                for (int i=0; i<2; i++)
                    E[i] = (Input[0][i] - Output[3][i]);
                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[3] = Output[3][0]*Math.exp(-A_m*Model.T_SForModel)
                        + B_m/A_m*(1-Math.exp(-A_m*Model.T_SForModel))* R[0];
                OutSignals[1] = Output[1][0] - Gamma*Model.T_SForModel *(E[0]*R[0] + E[1]*R[1])/2.0;
                OutSignals[2] = Output[2][0] - Gamma*Model.T_SForModel *(E[0]*Input[0][0] + E[1]*Input[0][1])/2.0;
                OutSignals[0] = OutSignals[1]*R[0] + OutSignals[2]*Input[0][0];
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[3][0];
                Trajectories[3] = Trajectories[0]-Input[0][0];
                Trajectories[4] = Output[0][0];
                Trajectories[5] = Output[1][0];
                Trajectories[6] = Output[2][0];
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[3]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_3)[0];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=4;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.mrac1;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Adaptive controller";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "r_1(t)";
        Model.SignalGenerators[1] = "r_2(t)";
        Model.SignalGenerators[2] = "r_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        TempTrajectories[2]= "Reference model output y_m(t)";
        Model.Figures[0] = new Figure("Reference r(t) and Outputs y(t) & y_m(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Error e_m(t)";
        TempTrajectories[1]= "Control u(t)";
        Model.Figures[1] = new Figure("Error e_m(t) and Control u(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "\u03B3_r cap";
        TempTrajectories[1]= "\u03B3_y cap";
        Model.Figures[2] = new Figure("Controller parameters", TempTrajectories);

        Model.Parameters = new Parameter [3];
        Model.Parameters[0] = new Parameter("Adaptation gain>>\u03F1", 0, 1000, 1);
        Model.Parameters[1] = new Parameter("Reference Model Parameters>>\u03B1_0m", 0, 100, 4);
        Model.Parameters[2] = new Parameter("\u03B1_1m", 0, 100, 4);
    }

    private void PrepareSecondOrderAdaptiveControlModel() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[00] --> u
                    Output[01] --> x_m_11
                    Output[02] --> x_m_21
                    Output[03] --> K_c_11
                    Output[04] --> K_c_12
                    Output[05] --> L_11
                    Output[06] --> E_11
                    Output[07] --> E_21
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                double[] R = new double[3];
                for (int i=0; i<3; i++)
                    R[i] = Generated[0][i] + Generated[1][i] + Generated[2][i];
                double Gamma = Parameters[0];
                double a_m1 = Parameters[1];
                double a_m2 = Parameters[2];
                DMatrixRMaj A_m = new DMatrixRMaj(2,2);
                DMatrixRMaj A_mT_s = new DMatrixRMaj(2,2);
                DMatrixRMaj B_m = new DMatrixRMaj(2,1);
                A_m.set(0,0, 0);
                A_m.set(0,1, 1);
                A_m.set(1,0, -a_m2);
                A_m.set(1,1, -a_m1);
                B_m.set(0,0, 0);
                B_m.set(1,0, Parameters[3]);
                DMatrixRMaj A_m_d = new DMatrixRMaj(2,2);
                DMatrixRMaj B_m_d = new DMatrixRMaj(2,1);
                CommonOps_DDRM.scale(T_SForModel, A_m, A_mT_s);
                Equation eq = new Equation();
                eq.alias(A_m_d,"A_m_d", B_m_d,"B_m_d", A_m,"A_m", A_mT_s,"A_mT_s", B_m,"B_m");
                eq.process("A_mT_s2 = A_mT_s*A_mT_s");
                eq.process("A_mT_s3 = A_mT_s2*A_mT_s");
                eq.process("A_mT_s4 = A_mT_s3*A_mT_s");
                eq.process("A_mT_s5 = A_mT_s4*A_mT_s");
                eq.process("A_mT_s6 = A_mT_s5*A_mT_s");
                eq.process("A_m_d = eye(2) + A_mT_s + 1/2.0*A_mT_s2 + 1/6.0*A_mT_s3 + 1/24.0*A_mT_s4 + 1/120.0*A_mT_s5 + 1/720.0*A_mT_s6");
                eq.process("B_m_d = inv(A_m)*(A_m_d-eye(2))*B_m");
                DMatrixRMaj X_m_1 = new DMatrixRMaj(2,1);
                DMatrixRMaj X_m = new DMatrixRMaj(2,1);
                X_m_1.set(0,0, Output[1][0]);
                X_m_1.set(1,0, Output[2][0]);
                eq = new Equation();
                eq.alias(X_m_1, "X_m_1", X_m, "X_m", A_m_d, "A_m_d", B_m_d, "B_m_d", R[0], "R");
                eq.process("X_m = A_m_d*X_m_1 + B_m_d * R");

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                P.set(0, 1, 1/(2*a_m2));
                P.set(1, 0, P.get(0,1));
                P.set(1, 1, (2*P.get(0,1)+1)/(2*a_m1));
                P.set(0, 0, a_m1*P.get(0,1) + a_m2*P.get(1,1));
                DMatrixRMaj X = new DMatrixRMaj(2,1);
                DMatrixRMaj X_1 = new DMatrixRMaj(2,1);
                X_1.set(0, 0, Input[0][1]);
                X_1.set(1, 0, (Input[0][1]-Input[0][2])/ T_SForModel);
                X.set(0, 0, Input[0][0]);
                X.set(1, 0, (Input[0][0]-Input[0][1])/ T_SForModel);
                DMatrixRMaj E = new DMatrixRMaj(2,1);
                DMatrixRMaj E_1 = new DMatrixRMaj(2,1);
                CommonOps_DDRM.subtract(X, X_m, E);
                E_1.set(0, 0, Output[6][0]);
                E_1.set(1, 0, Output[7][0]);

                DMatrixRMaj K_c_1 = new DMatrixRMaj(1,2);
                DMatrixRMaj K_c = new DMatrixRMaj(1,2);
                DMatrixRMaj L_1 = new DMatrixRMaj(1,1);
                DMatrixRMaj L = new DMatrixRMaj(1,1);
                K_c_1.set(0, 0, Output[3][0]);
                K_c_1.set(0, 1, Output[4][0]);
                L_1.set(0, 0, Output[5][0]);

                DMatrixRMaj U = new DMatrixRMaj(1,1);
                eq = new Equation();
                eq.alias(K_c, "K_c", L, "L", K_c_1, "K_c_1", L_1, "L_1", Gamma, "Gamma", T_SForModel, "T_S", B_m, "B_m", P, "P", E, "E", E_1, "E_1", X, "X", X_1, "X_1", R[0], "R", R[1], "R_1", U, "U");
                eq.process("K_c = K_c_1 + Gamma*T_S/2.0*(B_m'*P*E*X' + B_m'*P*E_1*X_1')");
                eq.process("L = L_1 - Gamma*T_S/2.0*(B_m'*P*E*R + B_m'*P*E_1*R_1)");
                eq.process("U = -K_c*X + L*R");
                /*Log.i("Algorithm", "A_mT_s: " + A_mT_s.toString());
                Log.i("Algorithm", "A_m: " + A_m.toString());
                Log.i("Algorithm", "B_m: " + B_m.toString());
                Log.i("Algorithm", "A_m_d: " + A_m_d.toString());
                Log.i("Algorithm", "B_m_d: " + B_m_d.toString());
                Log.i("Algorithm", "P: " + P.toString());*/

                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = U.get(0,0);
                OutSignals[1] = X_m.get(0,0);
                OutSignals[2] = X_m.get(1,0);
                OutSignals[3] = K_c.get(0,0);
                OutSignals[4] = K_c.get(0,1);
                OutSignals[5] = L.get(0,0);
                OutSignals[6] = E.get(0,0);
                OutSignals[7] = E.get(1,0);
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[8];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[1][0];
                Trajectories[3] = Trajectories[0]-Input[0][0];
                Trajectories[4] = Output[6][0];
                Trajectories[5] = Output[3][0];
                Trajectories[6] = Output[4][0];
                Trajectories[7] = Output[5][0];
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[3]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_3)[1];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=8;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.mrac2;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Adaptive controller";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "r_1(t)";
        Model.SignalGenerators[1] = "r_2(t)";
        Model.SignalGenerators[2] = "r_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        TempTrajectories[2]= "Reference model output y_m(t)";
        Model.Figures[0] = new Figure("Reference r(t) and Outputs y(t) & y_m(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Error e_m(t)";
        TempTrajectories[1]= "Control u(t)";
        Model.Figures[1] = new Figure("Error e_m(t) and Control u(t)", TempTrajectories);
        TempTrajectories = new String[3];
        TempTrajectories[0]= "K_c(1,1)";
        TempTrajectories[1]= "K_c(2,1)";
        TempTrajectories[2]= "L";
        Model.Figures[2] = new Figure("Controller parameters", TempTrajectories);

        Model.Parameters = new Parameter [4];
        Model.Parameters[0] = new Parameter("Adaptation gain>>\u03B3", 0, 1000, 0.1);
        Model.Parameters[1] = new Parameter("Reference Model Parameters>>\u03B2_0m", 0, 1000, 40);
        Model.Parameters[2] = new Parameter("\u03B2_1m", 0, 1000, 100);
        Model.Parameters[3] = new Parameter("\u03B2_2m", 0, 1000, 120);
    }

    private void PrepareFirstOrderIdentificationOld() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> P11
                    Output[2] --> P12
                    Output[3] --> P21
                    Output[4] --> P22
                    Output[5] --> Theta_1
                    Output[6] --> Theta_2
                    Output[7] --> K
                    Output[8] --> Z Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> z
                    E --> e
                */
                double Lambda = Parameters[1];
                double K = Output[7][0];

                DMatrixRMaj z = new DMatrixRMaj(1,1);
                z.set(0, 0, Input[0][0]);
                DMatrixRMaj Phi = new DMatrixRMaj(2,1);
                Phi.set(0, 0, Input[0][1]);
                Phi.set(1, 0, Output[0][1]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(2,1);
                Theta_1.set(0,0, Output[5][0]);
                Theta_1.set(1,0, Output[6][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(2,2);
                if (K==0) {
                    P_1.set(0, 0, Parameters[0]);
                    P_1.set(0, 1, 0);
                    P_1.set(1, 0, 0);
                    P_1.set(1, 1, Parameters[0]);
                } else {
                    P_1.set(0, 0, Output[1][0]);
                    P_1.set(0, 1, Output[2][0]);
                    P_1.set(1, 0, Output[3][0]);
                    P_1.set(1, 1, Output[4][0]);
                }

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                DMatrixRMaj Theta = new DMatrixRMaj(2,1);
                DMatrixRMaj TempMatrix0, TempMatrix1, TempMatrix2, TempMatrix3;
                DMatrixRMaj e = new DMatrixRMaj(1,1);
                DMatrixRMaj PhiTranspose = new DMatrixRMaj(1,2);
                // Calculation of e
                CommonOps_DDRM.transpose(Phi, PhiTranspose);
                CommonOps_DDRM.mult(PhiTranspose, Theta_1, e);
                CommonOps_DDRM.changeSign(e);
                CommonOps_DDRM.addEquals(e,z);
                // Calculation of P
                TempMatrix0 = new DMatrixRMaj(2,1);
                TempMatrix1 = new DMatrixRMaj(1,1);
                TempMatrix2 = new DMatrixRMaj(1,2);
                TempMatrix3 = new DMatrixRMaj(2,1);
                CommonOps_DDRM.mult(P_1,Phi,TempMatrix0);
                CommonOps_DDRM.mult(PhiTranspose, TempMatrix0, TempMatrix1);
                CommonOps_DDRM.add(TempMatrix1, Lambda);
                CommonOps_DDRM.invert(TempMatrix1);

                CommonOps_DDRM.mult(PhiTranspose, P_1, TempMatrix2);
                CommonOps_DDRM.mult(P_1, Phi, TempMatrix3);
                CommonOps_DDRM.mult(TempMatrix3, TempMatrix2, P);
                CommonOps_DDRM.changeSign(P);
                CommonOps_DDRM.scale(TempMatrix1.get(0,0), P);
                CommonOps_DDRM.addEquals(P, P_1);
                CommonOps_DDRM.scale(1/Lambda, P);
                // Calculations of Theta
                CommonOps_DDRM.mult(P, Phi, Theta);
                CommonOps_DDRM.mult(Theta, e, Theta);
                CommonOps_DDRM.addEquals(Theta, Theta_1);


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                OutSignals[1] = P.get(0,0);
                OutSignals[2] = P.get(0,1);
                OutSignals[3] = P.get(1,0);
                OutSignals[4] = P.get(1,1);
                OutSignals[5] = Theta.get(0,0);
                OutSignals[6] = Theta.get(1,0);
                OutSignals[7] = K+1;
                OutSignals[8] =  Output[8][1]*Theta.get(0,0) + Theta.get(1,0)*Output[0][1];
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[8][0];
                Trajectories[3] = Output[5][0];
                Trajectories[4] = Output[6][0];
                Trajectories[5] = -Math.log(Output[5][0])/Model.T_SForModel;
                Trajectories[6] = Output[6][0]*Trajectories[5]/(1-Output[5][0]);
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[1]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_1)[0];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=9;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.estimates1;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of a first-order system";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u_1(t)";
        Model.SignalGenerators[1] = "u_2(t)";
        Model.SignalGenerators[2] = "u_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "u(t)";
        TempTrajectories[1]= "y(t)";
        TempTrajectories[2]= "Validation y(t) cap";
        Model.Figures[0] = new Figure("Input, output and validation", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Estimate of \u03B8_1";
        TempTrajectories[1]= "Estimate of \u03B8_2";
        Model.Figures[1] = new Figure("Estimate of \u03B8", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Estimate of \u03B1_0";
        TempTrajectories[1]= "Estimate of \u03B1_1";
        Model.Figures[2] = new Figure("Estimates of \u03B1_0 and \u03B1_1", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("Parameters of the Least Squares method>>\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03B3", 0, 1, 0.9);
    }

    private void PrepareGeneralFirstOrderIdentification() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> P11
                    Output[2] --> P12
                    Output[3] --> P13
                    Output[4] --> P21
                    Output[5] --> P22
                    Output[6] --> P23
                    Output[7] --> P31
                    Output[8] --> P32
                    Output[9] --> P33
                    Output[10] --> Theta_1
                    Output[11] --> Theta_2
                    Output[12] --> Theta_3
                    Output[13] --> K
                    Output[14] --> Z Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> z = y
                    E --> e
                */
                DMatrixRMaj Gamma = new DMatrixRMaj(1,1);
                Gamma.set(0,0,Parameters[1]);
                double K = Output[7][0];
                DMatrixRMaj y = new DMatrixRMaj(1,1);
                y.set(0,0,Input[0][0]);
                DMatrixRMaj y_hat = new DMatrixRMaj(1,1);
                y_hat.set(0,0,0);

                DMatrixRMaj z = new DMatrixRMaj(1,1);
                z.set(0, 0, Input[0][0]);
                DMatrixRMaj Phi = new DMatrixRMaj(3,1);
                Phi.set(0, 0, Output[0][0]);
                Phi.set(1, 0, Output[0][1]);
                Phi.set(2, 0, Input[0][1]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(3,1);
                Theta_1.set(0,0, Output[10][0]);
                Theta_1.set(1,0, Output[11][0]);
                Theta_1.set(2,0, Output[12][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(3,3);
                int MatrixSize = 3;
                if (K==0) {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, 0);
                    for (int i=0; i<MatrixSize; i++)
                        P_1.set(i, i, Parameters[0]);
                } else {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, Output[(i*MatrixSize+j+1)][0]);
                }


                DMatrixRMaj P = new DMatrixRMaj(3,3);
                DMatrixRMaj Theta = new DMatrixRMaj(3,1);


                DMatrixRMaj L = new DMatrixRMaj(3,1);
                Equation eq = new Equation();
                eq.alias(Theta_1, "Theta_1", Theta, "Theta", P, "P", Phi, "Phi", P_1, "P_1", y, "y", Gamma, "Gamma", L, "L", y_hat, "y_hat");
                eq.process("y_hat = Phi'*Theta_1");
                eq.process("Epsilon = y - y_hat");
                eq.process("P_den = Gamma + Phi'*P_1*Phi");
                eq.process("L = P_1*Phi/P_den(0,0)");
                eq.process("P = (P_1 - P_1*Phi*Phi'*P_1/P_den(0,0))/Gamma(0,0)");
                eq.process("Theta = Theta_1 + L*Epsilon");




                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                OutSignals[1] = P.get(0,0);
                OutSignals[2] = P.get(0,1);
                OutSignals[3] = P.get(0,2);
                OutSignals[4] = P.get(1,0);
                OutSignals[5] = P.get(1,1);
                OutSignals[6] = P.get(1,2);
                OutSignals[7] = P.get(2,0);
                OutSignals[8] = P.get(2,1);
                OutSignals[9] = P.get(2,2);
                OutSignals[10] = Theta.get(0,0);
                OutSignals[11] = Theta.get(1,0);
                OutSignals[12] = Theta.get(2,0);
                OutSignals[13] = K+1;
                OutSignals[14] = y_hat.get(0,0);
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[9];
                double Theta_3_star = (Output[12][0]>0.01?Output[12][0]:0.01);
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[14][0];
                Trajectories[3] = Output[10][0];
                Trajectories[4] = Output[11][0];
                Trajectories[5] = Output[12][0];
                Trajectories[6] = Output[10][0];
                Trajectories[8] = -Math.log(Theta_3_star)/Model.T_SForModel;
                Trajectories[7] = Trajectories[8]*((Output[11][0]+Output[10][0]*Output[12][0])/(1-Output[12][0])+Output[10][0]);
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[1]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_1)[0];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=15;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 2;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.estimates1;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of a first-order system";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u_1(t)";
        Model.SignalGenerators[1] = "u_2(t)";
        Model.SignalGenerators[2] = "u_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "u(t)";
        TempTrajectories[1]= "y(t)";
        TempTrajectories[2]= "Validation y(t) cap";
        Model.Figures[0] = new Figure("Input, output and validation", TempTrajectories);
        TempTrajectories = new String[3];
        TempTrajectories[0]= "Estimate of \u03B8_1";
        TempTrajectories[1]= "Estimate of \u03B8_2";
        TempTrajectories[2]= "Estimate of \u03B8_3";
        Model.Figures[1] = new Figure("Estimate of \u03B8", TempTrajectories);
        TempTrajectories = new String[3];
        TempTrajectories[0]= "Estimate of \u03B1_1";
        TempTrajectories[1]= "Estimate of \u03B1_2";
        TempTrajectories[2]= "Estimate of \u03B1_3";
        Model.Figures[2] = new Figure("Estimates of \u03B1_1, \u03B1_2 and \u03B1_3", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("Parameters of the Least Squares method>>\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03B3", 0, 1, 0.99);
    }

    private void PrepareFirstOrderIdentification() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> P11
                    Output[2] --> P12
                    Output[3] --> P21
                    Output[4] --> P22
                    Output[5] --> Theta_1
                    Output[6] --> Theta_2
                    Output[7] --> K
                    Output[8] --> Z Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> z = y
                    E --> e
                */
                DMatrixRMaj Gamma = new DMatrixRMaj(1,1);
                Gamma.set(0,0,Parameters[1]);
                double K = Output[7][0];
                DMatrixRMaj y = new DMatrixRMaj(1,1);
                y.set(0,0,Input[0][0]);
                DMatrixRMaj y_hat = new DMatrixRMaj(1,1);
                y_hat.set(0,0,0);

                DMatrixRMaj z = new DMatrixRMaj(1,1);
                z.set(0, 0, Input[0][0]);
                DMatrixRMaj Phi = new DMatrixRMaj(2,1);
                Phi.set(0, 0, Input[0][1]);
                Phi.set(1, 0, Output[0][1]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(2,1);
                Theta_1.set(0,0, Output[5][0]);
                Theta_1.set(1,0, Output[6][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(2,2);
                if (K==0) {
                    P_1.set(0, 0, Parameters[0]);
                    P_1.set(0, 1, 0);
                    P_1.set(1, 0, 0);
                    P_1.set(1, 1, Parameters[0]);
                } else {
                    P_1.set(0, 0, Output[1][0]);
                    P_1.set(0, 1, Output[2][0]);
                    P_1.set(1, 0, Output[3][0]);
                    P_1.set(1, 1, Output[4][0]);
                }

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                DMatrixRMaj Theta = new DMatrixRMaj(2,1);


                DMatrixRMaj L = new DMatrixRMaj(2,1);
                Equation eq = new Equation();
                eq.alias(Theta_1, "Theta_1", Theta, "Theta", P, "P", Phi, "Phi", P_1, "P_1", y, "y", Gamma, "Gamma", L, "L", y_hat, "y_hat");
                eq.process("y_hat = Phi'*Theta_1");
                eq.process("Epsilon = y - y_hat");
                eq.process("P_den = Gamma + Phi'*P_1*Phi");
                eq.process("L = P_1*Phi/P_den(0,0)");
                eq.process("P = (P_1 - P_1*Phi*Phi'*P_1/P_den(0,0))/Gamma(0,0)");
                eq.process("Theta = Theta_1 + L*Epsilon");




                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                OutSignals[1] = P.get(0,0);
                OutSignals[2] = P.get(0,1);
                OutSignals[3] = P.get(1,0);
                OutSignals[4] = P.get(1,1);
                OutSignals[5] = Theta.get(0,0);
                OutSignals[6] = Theta.get(1,0);
                OutSignals[7] = K+1;
                OutSignals[8] = y_hat.get(0,0);
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[8][0];
                Trajectories[3] = Output[5][0];
                Trajectories[4] = Output[6][0];
                Trajectories[5] = -Math.log(Output[5][0])/Model.T_SForModel;
                Trajectories[6] = Output[6][0]*Trajectories[5]/(1-Output[5][0]);
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[1]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_1)[0];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=9;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.estimates1;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of a first-order system";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u_1(t)";
        Model.SignalGenerators[1] = "u_2(t)";
        Model.SignalGenerators[2] = "u_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "u(t)";
        TempTrajectories[1]= "y(t)";
        TempTrajectories[2]= "Validation y(t) cap";
        Model.Figures[0] = new Figure("Input, output and validation", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Estimate of \u03B8_1";
        TempTrajectories[1]= "Estimate of \u03B8_2";
        Model.Figures[1] = new Figure("Estimate of \u03B8", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Estimate of \u03B1_0";
        TempTrajectories[1]= "Estimate of \u03B1_1";
        Model.Figures[2] = new Figure("Estimates of \u03B1_0 and \u03B1_1", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("Parameters of the Least Squares method>>\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03B3", 0, 1, 0.99);
    }

    private void PrepareSecondOrderIdentification() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[00] --> u
                    Output[01] --> P11
                    Output[02] --> P12
                    Output[03] --> P13
                    Output[04] --> P14
                    Output[05] --> P21
                    Output[06] --> P22
                    Output[07] --> P23
                    Output[08] --> P24
                    Output[09] --> P31
                    Output[10] --> P32
                    Output[11] --> P33
                    Output[12] --> P34
                    Output[13] --> P41
                    Output[14] --> P42
                    Output[15] --> P43
                    Output[16] --> P44
                    Output[17] --> Theta_1
                    Output[18] --> Theta_2
                    Output[19] --> Theta_3
                    Output[20] --> Theta_4
                    Output[21] --> K
                    Output[22] --> Y Cap Calculated
                    Generated[0] --> u_1
                    Generated[1] --> u_2
                    Generated[2] --> u_3
                    u = u_1 + u_2 + u_3
                    Input[0] --> y
                    E --> e
                */
                int MatrixSize = 4;
                double K = Output[21][0];
                /*DMatrixRMaj Gamma = new DMatrixRMaj(1,1);
                Gamma.set(0,0, Parameters[1]);*/
                DMatrixRMaj Gamma = new DMatrixRMaj(1,1);
                Gamma.set(0,0,Parameters[1]);
                DMatrixRMaj Phi = new DMatrixRMaj(MatrixSize,1);
                Phi.set(0, 0, Input[0][1]);
                Phi.set(1, 0, Input[0][2]);
                Phi.set(2, 0, Output[0][1]);
                Phi.set(3, 0, Output[0][2]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(MatrixSize,1);
                for (int i=0; i<MatrixSize; i++)
                    Theta_1.set(i,0, Output[i+17][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(MatrixSize,MatrixSize);
                if (K==0) {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, 0);
                    for (int i=0; i<MatrixSize; i++)
                        P_1.set(i, i, Parameters[0]);
                } else {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, Output[(i*MatrixSize+j+1)][0]);
                }
                DMatrixRMaj P = new DMatrixRMaj(MatrixSize,MatrixSize);
                DMatrixRMaj Epsilon = new DMatrixRMaj(1,1);
                DMatrixRMaj y = new DMatrixRMaj(1,1);
                y.set(0,0,Input[0][1]);
                DMatrixRMaj y_hat = new DMatrixRMaj(1,1);

                DMatrixRMaj Theta = new DMatrixRMaj(MatrixSize,1);
                Equation eq = new Equation();
                eq.alias(Theta_1, "Theta_1", Theta, "Theta", P, "P", Phi, "Phi", Epsilon, "Epsilon", P_1, "P_1", y, "y", Gamma, "Gamma", y_hat, "y_hat");
                eq.process("y_hat = Phi'*Theta_1");
                eq.process("Epsilon = y - y_hat");
                eq.process("P_den = Gamma + Phi'*P_1*Phi");
                eq.process("L = P_1*Phi/P_den(0,0)");
                eq.process("P = (P_1 - P_1*Phi*Phi'*P_1/P_den(0,0))/Gamma(0,0)");
                eq.process("Theta = Theta_1 + L*Epsilon");


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                for (int i=0; i<MatrixSize; i++)
                    for (int j=0; j<MatrixSize; j++)
                        OutSignals[1+i*MatrixSize+j] = P.get(i, j);
                for (int i=0; i<MatrixSize; i++)
                    OutSignals[i+17] = Theta.get(i,0);
                OutSignals[21] = K+1;
                OutSignals[22] = y_hat.get(0,0);//.get(0, 0);
                Log.i("Algorithm", "Phi: " + Phi.toString());
                Log.i("Algorithm", "Theta: "  + Theta.toString());
                Log.i("Algorithm", "z: "  + y);
                Log.i("Algorithm", "e: "  + Epsilon);
                Log.i("Algorithm", "P_1: " + P_1.toString());
                Log.i("Algorithm", "P: "  + P.toString());
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[10];
                DMatrixRMaj A_D, B_D, C, D, A_C, B_C, R;
                Equation eq = new Equation();
                A_D = new DMatrixRMaj(2,2);
                B_D = new DMatrixRMaj(2,1);
                C = new DMatrixRMaj(1,2);
                A_C = new DMatrixRMaj(2,2);
                B_C = new DMatrixRMaj(2,1);

                A_D.set(0, 0, 0);
                A_D.set(0, 1, 1);
                A_D.set(1, 0, -Output[18][0]);
                A_D.set(1, 1, -Output[17][0]);

                B_D.set(0,0,0);
                B_D.set(1,0,1);

                C.set(0,0, Output[20][0]);
                C.set(0,1, Output[19][0]);

                eq.alias(A_D, "A_D", B_D, "B_D", A_C, "A_C", B_C, "B_C");
                eq.process("R = (A_D - eye(2))*inv(A_D + eye(2))");
                eq.process("A_C = 2*R*(eye(2) - 8/21*R*R - 4/105*R*R*R*R)*inv(eye(2) - 5/7*R*R)");
                CommonOps_DDRM.scale(1/Model.T_SForModel, A_C);
                eq.process("B_C = A_C * inv(A_D-eye(2)) * B_D");

                /*Log.i("Algorithm", "A_C: " + A_C);
                Log.i("Algorithm", "B_C: " + B_C);
                Log.i("Algorithm", "C_C: " + C);
                Log.i("Algorithm", "D_C: " + D);*/



                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[22][0];
                for (int i=0; i<4; i++)
                    Trajectories[3+i] = Output[i+17][0];
                Trajectories[7] = A_C.get(0,0)*A_C.get(1,1)-A_C.get(0,1)*A_C.get(1,0);
                Trajectories[8] = -(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[9] = C.get(0,0)*(B_C.get(1,0)*A_C.get(0,1) - B_C.get(0,0)*A_C.get(1,1))
                        + C.get(0,1)*(B_C.get(0,0)*A_C.get(1,0) - B_C.get(1,0)*A_C.get(0,0));
                /*Trajectories[8] = D.get(0,0);
                Trajectories[9] = C.get(0,0)*B_C.get(0,0)
                        + C.get(0,1)*B_C.get(1,0)
                        - D.get(0,0)*(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[10] =
                        C.get(0,0)*(B_C.get(1,0)*A_C.get(0,1) - B_C.get(0,0)*A_C.get(1,1))
                        + C.get(0,1)*(B_C.get(0,0)*A_C.get(1,0) - B_C.get(1,0)*A_C.get(0,0))
                        + D.get(0,0)*(A_C.get(0,0)*A_C.get(1,1) - A_C.get(0,1)*A_C.get(1,0));
                Trajectories[11] = -(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[12] = A_C.get(0,0)*A_C.get(1,1) - A_C.get(0,1)*A_C.get(1,0);*/
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[1]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_1)[1];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=23;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 2;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.estimates2;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of a second-order system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u_1(t)";
        Model.SignalGenerators[1] = "u_2(t)";
        Model.SignalGenerators[2] = "u_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "u(t)";
        TempTrajectories[1]= "y(t)";
        TempTrajectories[2]= "Validation y(t) cap";
        Model.Figures[0] = new Figure("Input, output and validation", TempTrajectories);
        TempTrajectories = new String[4];
        TempTrajectories[0]= "Estimate of \u03B8_1";
        TempTrajectories[1]= "Estimate of \u03B8_2";
        TempTrajectories[2]= "Estimate of \u03B8_3";
        TempTrajectories[3]= "Estimate of \u03B8_4";
        Model.Figures[1] = new Figure("Estimate of \u03B8", TempTrajectories);
        TempTrajectories = new String[3];
        TempTrajectories[0]= "Estimate of \u03B2_0 cap";
        TempTrajectories[1]= "Estimate of \u03B2_1 cap";
        TempTrajectories[2]= "Estimate of \u03B2_2 cap";
        Model.Figures[2] = new Figure("Estimates of \u03B2_0, \u03B2_1, and \u03B2_2", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("Parameters of the Least Squares method>>\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03B3", 0, 1, 0.9);
    }

    private void PrepareGeneralSecondOrderIdentification() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[00] --> u
                    Output[01] --> P11
                    Output[02] --> P12
                    Output[03] --> P13
                    Output[04] --> P14
                    Output[05] --> P15
                    Output[06] --> P21
                    Output[07] --> P22
                    Output[08] --> P23
                    Output[09] --> P24
                    Output[10] --> P25
                    Output[11] --> P31
                    Output[12] --> P32
                    Output[13] --> P33
                    Output[14] --> P34
                    Output[15] --> P35
                    Output[16] --> P41
                    Output[17] --> P42
                    Output[18] --> P43
                    Output[19] --> P44
                    Output[20] --> P45
                    Output[21] --> P51
                    Output[22] --> P52
                    Output[23] --> P53
                    Output[24] --> P54
                    Output[25] --> P55
                    Output[26] --> Theta_1
                    Output[27] --> Theta_2
                    Output[28] --> Theta_3
                    Output[29] --> Theta_4
                    Output[30] --> Theta_5
                    Output[31] --> K
                    Output[32] --> Y Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                int MatrixSize = 5;
                DMatrixRMaj Gamma = new DMatrixRMaj(1,1);
                Gamma.set(0, 0, Parameters[1]);
                DMatrixRMaj y_hat = new DMatrixRMaj(1,1);
                //double Lambda = Parameters[1];
                double K = Output[31][0];
                double[] E = new double[3];
                for (int i=0; i<3; i++)
                    E[i] = ((Generated[0][i] + Generated[1][i] + Generated[2][i]) - Input[0][i]);



                DMatrixRMaj y = new DMatrixRMaj(1,1);
                y.set(0, 0, Input[0][0]);
                DMatrixRMaj Phi = new DMatrixRMaj(MatrixSize,1);
                Phi.set(0, 0, Output[0][0]);
                Phi.set(1, 0, Output[0][1]);
                Phi.set(2, 0, Output[0][2]);
                Phi.set(3, 0, -Input[0][1]);
                Phi.set(4, 0, -Input[0][2]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(MatrixSize,1);
                for (int i=0; i<MatrixSize; i++)
                    Theta_1.set(i,0, Output[i+26][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(MatrixSize,MatrixSize);
                if (K==0) {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, 0);
                    for (int i=0; i<MatrixSize; i++)
                        P_1.set(i, i, Parameters[0]);
                } else {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, Output[(i*MatrixSize+j+1)][0]);
                }
                DMatrixRMaj P = new DMatrixRMaj(MatrixSize,MatrixSize);
                DMatrixRMaj Theta = new DMatrixRMaj(MatrixSize,1);
                DMatrixRMaj TempMatrix0, TempMatrix1, TempMatrix2, TempMatrix3;
                DMatrixRMaj e = new DMatrixRMaj(1,1);
                DMatrixRMaj PhiTranspose = new DMatrixRMaj(1,MatrixSize);


                DMatrixRMaj L = new DMatrixRMaj(2,1);
                Equation eq = new Equation();
                eq.alias(Theta_1, "Theta_1", Theta, "Theta", P, "P", Phi, "Phi", P_1, "P_1", y, "y", Gamma, "Gamma", L, "L", y_hat, "y_hat");
                eq.process("y_hat = Phi'*Theta_1");
                eq.process("Epsilon = y - y_hat");
                eq.process("P_den = Gamma + Phi'*P_1*Phi");
                eq.process("L = P_1*Phi/P_den(0,0)");
                eq.process("P = (P_1 - P_1*Phi*Phi'*P_1/P_den(0,0))/Gamma(0,0)");
                eq.process("Theta = Theta_1 + L*Epsilon");

                /*
                // Calculation of e
                CommonOps_DDRM.transpose(Phi, PhiTranspose);
                CommonOps_DDRM.mult(PhiTranspose, Theta_1, e);
                CommonOps_DDRM.changeSign(e);
                CommonOps_DDRM.addEquals(e,y);
                // Calculation of P
                TempMatrix0 = new DMatrixRMaj(MatrixSize,1);
                TempMatrix1 = new DMatrixRMaj(1,1);
                TempMatrix2 = new DMatrixRMaj(1,MatrixSize);
                TempMatrix3 = new DMatrixRMaj(MatrixSize,1);
                CommonOps_DDRM.mult(P_1,Phi,TempMatrix0);
                CommonOps_DDRM.mult(PhiTranspose, TempMatrix0, TempMatrix1);
                CommonOps_DDRM.add(TempMatrix1, Lambda);
                CommonOps_DDRM.invert(TempMatrix1);
                CommonOps_DDRM.mult(PhiTranspose, P_1, TempMatrix2);
                CommonOps_DDRM.mult(P_1, Phi, TempMatrix3);
                CommonOps_DDRM.mult(TempMatrix3, TempMatrix2, P);
                CommonOps_DDRM.changeSign(P);
                CommonOps_DDRM.scale(TempMatrix1.get(0,0), P);
                CommonOps_DDRM.addEquals(P, P_1);
                CommonOps_DDRM.scale(1/Lambda, P);
                // Calculations of Theta
                CommonOps_DDRM.mult(P, Phi, Theta);
                CommonOps_DDRM.mult(Theta, e, Theta);
                CommonOps_DDRM.addEquals(Theta, Theta_1);
                */


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                for (int i=0; i<MatrixSize; i++)
                    for (int j=0; j<MatrixSize; j++)
                        OutSignals[1+i*MatrixSize+j] = P.get(i, j);
                for (int i=0; i<MatrixSize; i++)
                    OutSignals[i+26] = Theta.get(i,0);
                OutSignals[31] = K+1;
                OutSignals[32]
                        = Theta.get(0,0 ) * Output[0][0]
                        + Theta.get(1,0 ) * Output[0][1]
                        + Theta.get(2,0 ) * Output[0][2]
                        - Theta.get(3,0 ) * Output[32][0]
                        - Theta.get(4,0 ) * Output[32][1];
                /*Log.i("Algorithm", "Phi: " + Phi.toString());
                Log.i("Algorithm", "Theta: "  + Theta.toString());
                Log.i("Algorithm", "z: "  + z);
                Log.i("Algorithm", "e: "  + e);
                Log.i("Algorithm", "P_1: " + P_1.toString());
                Log.i("Algorithm", "P: "  + P.toString());*/
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[13];
                DMatrixRMaj A_D, B_D, C, D, A_C, B_C, R;
                Equation eq = new Equation();
                A_D = new DMatrixRMaj(2,2);
                B_D = new DMatrixRMaj(2,1);
                C = new DMatrixRMaj(1,2);
                D = new DMatrixRMaj(1,1);
                A_C = new DMatrixRMaj(2,2);
                B_C = new DMatrixRMaj(2,1);
                //R = new DMatrixRMaj(2,2);

                A_D.set(0, 0, 0);
                A_D.set(0, 1, 1);
                A_D.set(1, 0, -Output[25+5][0]);
                A_D.set(1, 1, -Output[25+4][0]);

                B_D.set(0,0,0);
                B_D.set(1,0,1);

                C.set(0,0, Output[25+3][0] - Output[25+5][0]*Output[25+1][0]);
                C.set(0,1, Output[25+2][0] - Output[25+4][0]*Output[25+1][0]);

                D.set(0,0, Output[25+1][0]);

                eq.alias(A_D, "A_D", B_D, "B_D", A_C, "A_C", B_C, "B_C");
                eq.process("R = (A_D - eye(2))*inv(A_D + eye(2))");
                eq.process("A_C = 2*R*(eye(2) - 8/21.0*R*R - 4/105.0*R*R*R*R)*inv(eye(2) - 5/7.0*R*R)");
                CommonOps_DDRM.scale(1/Model.T_SForModel, A_C);
                eq.process("B_C = A_C * inv(A_D-eye(2)) * B_D");

                /*Log.i("Algorithm", "A_C: " + A_C);
                Log.i("Algorithm", "B_C: " + B_C);
                Log.i("Algorithm", "C_C: " + C);
                Log.i("Algorithm", "D_C: " + D);*/



                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[32][0];
                for (int i=0; i<5; i++)
                    Trajectories[3+i] = Output[i+26][0];
                Trajectories[8] = D.get(0,0);
                Trajectories[9] = C.get(0,0)*B_C.get(0,0)
                        + C.get(0,1)*B_C.get(1,0)
                        - D.get(0,0)*(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[10] =
                        C.get(0,0)*(B_C.get(1,0)*A_C.get(0,1) - B_C.get(0,0)*A_C.get(1,1))
                                + C.get(0,1)*(B_C.get(0,0)*A_C.get(1,0) - B_C.get(1,0)*A_C.get(0,0))
                                + D.get(0,0)*(A_C.get(0,0)*A_C.get(1,1) - A_C.get(0,1)*A_C.get(1,0));
                Trajectories[11] = -(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[12] = A_C.get(0,0)*A_C.get(1,1) - A_C.get(0,1)*A_C.get(1,0);
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[1]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_1)[1];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=33;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 2;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.estimates2;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of a second-order system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u_1(t)";
        Model.SignalGenerators[1] = "u_2(t)";
        Model.SignalGenerators[2] = "u_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "u(t)";
        TempTrajectories[1]= "y(t)";
        TempTrajectories[2]= "Validation y(t) cap";
        Model.Figures[0] = new Figure("Input, output and validation", TempTrajectories);
        TempTrajectories = new String[5];
        TempTrajectories[0]= "Estimate of \u03F4_1";
        TempTrajectories[1]= "Estimate of \u03F4_2";
        TempTrajectories[2]= "Estimate of \u03F4_3";
        TempTrajectories[3]= "Estimate of \u03F4_4";
        TempTrajectories[4]= "Estimate of \u03F4_5";
        Model.Figures[1] = new Figure("Estimate of \u03F4", TempTrajectories);
        TempTrajectories = new String[5];
        TempTrajectories[0]= "Estimate of \u03B2_1";
        TempTrajectories[1]= "Estimate of \u03B2_2";
        TempTrajectories[2]= "Estimate of \u03B2_3";
        TempTrajectories[3]= "Estimate of \u03B2_4";
        TempTrajectories[4]= "Estimate of \u03B2_5";
        Model.Figures[2] = new Figure("Estimate of \u03B2", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("Parameters of the least squares method>>\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03B3", 0, 1, 0.99);
    }

    private void PrepareFirstOrderWithControllerIdentification() {
        Model = new Model() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> P11
                    Output[2] --> P12
                    Output[3] --> P21
                    Output[4] --> P22
                    Output[5] --> Theta_1
                    Output[6] --> Theta_2
                    Output[7] --> K
                    Output[8] --> Y Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                DMatrixRMaj Gamma = new DMatrixRMaj(1,1);
                Gamma.set(0, 0, Parameters[2]);
                double Lambda = Parameters[2];
                double K_I = Parameters[0];
                double K = Output[7][0];
                double[] E = new double[3];
                for (int i=0; i<3; i++)
                    E[i] = ((Generated[0][i] + Generated[1][i] + Generated[2][i]) - Input[0][i]);



                DMatrixRMaj y = new DMatrixRMaj(1,1);
                y.set(0, 0, Input[0][0] - Input[0][1]);
                DMatrixRMaj Phi = new DMatrixRMaj(2,1);
                Phi.set(0, 0, E[1] + E[2]);
                Phi.set(1, 0, Input[0][1] - Input[0][2]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(2,1);
                Theta_1.set(0,0, Output[5][0]);
                Theta_1.set(1,0, Output[6][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(2,2);
                if (K==0) {
                    P_1.set(0, 0, Parameters[1]);
                    P_1.set(0, 1, 0);
                    P_1.set(1, 0, 0);
                    P_1.set(1, 1, Parameters[1]);
                } else {
                    P_1.set(0, 0, Output[1][0]);
                    P_1.set(0, 1, Output[2][0]);
                    P_1.set(1, 0, Output[3][0]);
                    P_1.set(1, 1, Output[4][0]);
                }

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                DMatrixRMaj Theta = new DMatrixRMaj(2,1);
                DMatrixRMaj TempMatrix0, TempMatrix1, TempMatrix2, TempMatrix3;
                DMatrixRMaj e = new DMatrixRMaj(1,1);
                DMatrixRMaj PhiTranspose = new DMatrixRMaj(1,2);

                DMatrixRMaj y_hat = new DMatrixRMaj(1,1);
                DMatrixRMaj L = new DMatrixRMaj(2,1);
                Equation eq = new Equation();
                eq.alias(Theta_1, "Theta_1", Theta, "Theta", P, "P", Phi, "Phi", P_1, "P_1", y, "y", Gamma, "Gamma", L, "L", y_hat, "y_hat");
                eq.process("y_hat = Phi'*Theta_1");
                eq.process("Epsilon = y - y_hat");
                eq.process("P_den = Gamma + Phi'*P_1*Phi");
                eq.process("L = P_1*Phi/P_den(0,0)");
                eq.process("P = (P_1 - P_1*Phi*Phi'*P_1/P_den(0,0))/Gamma(0,0)");
                eq.process("Theta = Theta_1 + L*Epsilon");

                /*
                // Calculation of e
                CommonOps_DDRM.transpose(Phi, PhiTranspose);
                CommonOps_DDRM.mult(PhiTranspose, Theta_1, e);
                CommonOps_DDRM.changeSign(e);
                CommonOps_DDRM.addEquals(e,y);
                // Calculation of P
                TempMatrix0 = new DMatrixRMaj(2,1);
                TempMatrix1 = new DMatrixRMaj(1,1);
                TempMatrix2 = new DMatrixRMaj(1,2);
                TempMatrix3 = new DMatrixRMaj(2,1);
                CommonOps_DDRM.mult(P_1,Phi,TempMatrix0);
                CommonOps_DDRM.mult(PhiTranspose, TempMatrix0, TempMatrix1);
                CommonOps_DDRM.add(TempMatrix1, Lambda);
                CommonOps_DDRM.invert(TempMatrix1);
                CommonOps_DDRM.mult(PhiTranspose, P_1, TempMatrix2);
                CommonOps_DDRM.mult(P_1, Phi, TempMatrix3);
                CommonOps_DDRM.mult(TempMatrix3, TempMatrix2, P);
                CommonOps_DDRM.changeSign(P);
                CommonOps_DDRM.scale(TempMatrix1.get(0,0), P);
                CommonOps_DDRM.addEquals(P, P_1);
                CommonOps_DDRM.scale(1/Lambda, P);
                // Calculations of Theta
                CommonOps_DDRM.mult(P, Phi, Theta);
                CommonOps_DDRM.mult(Theta, e, Theta);
                CommonOps_DDRM.addEquals(Theta, Theta_1);
                */


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Output[0][0] + K_I*Model.T_SForModel /2.0 * (E[0] + E[1]);
                OutSignals[1] = P.get(0,0);
                OutSignals[2] = P.get(0,1);
                OutSignals[3] = P.get(1,0);
                OutSignals[4] = P.get(1,1);
                OutSignals[5] = Theta.get(0,0);
                OutSignals[6] = Theta.get(1,0);
                OutSignals[7] = K+1;
                OutSignals[8] = Output[8][0]
                        + Theta.get(0,0) * (
                        (Generated[0][1] + Generated[1][1] + Generated[2][1])
                                + (Generated[0][2] + Generated[1][2] + Generated[2][2])
                                - Output[8][0] - Output[8][1]
                )
                        + Theta.get(1,0) * (Output[8][0] - Output[8][1]);
                /*Log.i("Algorithm", "Phi: " + Phi.toString());
                Log.i("Algorithm", "Theta: "  + Theta.toString());
                Log.i("Algorithm", "z: "  + z);
                Log.i("Algorithm", "e: "  + e);
                Log.i("Algorithm", "P: "  + P.toString());*/
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[8][0];
                Trajectories[3] = Output[5][0];
                Trajectories[4] = Output[6][0];
                Trajectories[5] = -Math.log(Output[6][0])/Model.T_SForModel;
                Trajectories[6] = 2*Output[5][0]*Trajectories[5]/((1-Output[6][0])*Model.T_SForModel *Parameters[0]);
                return Trajectories;
            }
        };
        Model.ModelName = getResources().getStringArray(R.array.NAV_HEADS)[1]
                + ": "
                +getResources().getStringArray(R.array.NAV_ITEMS_1)[2];
        Model.NoOfInputs=1;
        Model.NoOfOutputs=9;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.estimates3;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of a first-order system with integral action";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "r_1(t)";
        Model.SignalGenerators[1] = "r_2(t)";
        Model.SignalGenerators[2] = "r_3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "u(t)";
        TempTrajectories[1]= "y(t)";
        TempTrajectories[2]= "Validation of y(t)";
        Model.Figures[0] = new Figure("Input, output and validation", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Estimate of \u03B8_1";
        TempTrajectories[1]= "Estimate of \u03B8_2";
        Model.Figures[1] = new Figure("Estimate of \u03B8", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Estimate of \u03B1_0";
        TempTrajectories[1]= "Estimate of \u03B1_1";
        Model.Figures[2] = new Figure("Estimates of \u03B1_0 and \u03B1_1", TempTrajectories);

        Model.Parameters = new Parameter [3];
        Model.Parameters[0] = new Parameter("Integral gain>>K_I", 0, 1000, 10);
        Model.Parameters[1] = new Parameter("Parameters of the Least Squares method>>\u03C1", 0, 10000, 1000);
        Model.Parameters[2] = new Parameter("\u03B3", 0, 1, 0.99);
    }



    private void shareImage(Bitmap bitmap){
        // save bitmap to cache directory
        try {
            File cachePath = new File(this.getCacheDir(), "images");
            cachePath.mkdirs(); // don't forget to make the directory
            FileOutputStream stream = new FileOutputStream(cachePath + "/image.png"); // overwrites this image every time
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        File imagePath = new File(this.getCacheDir(), "images");
        File newFile = new File(imagePath, "image.png");
        Uri contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", newFile);

        if (contentUri != null) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
            shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setType("image/png");
            startActivity(Intent.createChooser(shareIntent, "Choose an app"));
        }
    }
    private void DownloadImage (Bitmap bm) {
        String root = Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES).toString();
        File myDir = new File(root + "/CIT");
        myDir.mkdirs();
        Calendar c = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String n = df.format(c.getTime());
        String fname = n + ".png";
        File file = new File(myDir, fname);
        if (file.exists())
            file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            MediaScannerConnection.scanFile(this,
                    new String[] { file.toString() }, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public  boolean isStoragePermissionGranted() {
        String TAG = "Permission";
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }
    private Bitmap CaptureCompleteScreen () {
        ScrollView MainScroll = (ScrollView) findViewById(R.id.MainScrollView);
        bitmap = Bitmap.createBitmap(
                MainScroll.getChildAt(0).getWidth(),
                MainScroll.getChildAt(0).getHeight(),
                Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bitmap);
        if (MainScroll.getChildCount()>0) {
            MainScroll.draw(c);

            View RootView = MainScroll.getRootView();
            RootView.setDrawingCacheEnabled(true);
            Bitmap bitmap1 = RootView.getDrawingCache();


            int actionBarHeight = 56;
            TypedValue tv = new TypedValue();
            if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
            }
            return combineImagesVertical(
                    Bitmap.createBitmap(bitmap1, 0, 0, bitmap1.getWidth(), actionBarHeight)
                    , bitmap);
        } else {
            return bitmap;
        }

    }
    public Bitmap combineImagesVertical(Bitmap one, Bitmap two) { // can add a 3rd parameter 'String loc' if you want to save the new image - left some code to do that at the bottom
        Bitmap cs = null;

        int width, height = 0;

        if(one.getWidth() > two.getWidth()) {
            width = two.getWidth();
        } else {
            width = one.getWidth();
        }
        height = one.getHeight() + two.getHeight();

        cs = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);

        Canvas comboImage = new Canvas(cs);

        comboImage.drawBitmap(one, 0f, 0f, null);
        comboImage.drawBitmap(two   , 0f, one.getHeight(), null);


        return cs;
    }

    public static Bitmap getBitmapFromView(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.draw(canvas);
        return bitmap;
    }
    //--- Menu handling

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.con_sim_menu, menu);

        DocumentationButton = menu.getItem(2);
        SettingsButton = menu.getItem(1);
        SimulateButton = menu.getItem(4);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_documentation:
                if (SimulationState == SIMULATION_STATUS.ON)
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[10],
                            Toast.LENGTH_SHORT).show();
                else
                    startActivity(new Intent(MainActivity.this, DocumentationActivity.class));
                break;
            case R.id.download:
                if (isStoragePermissionGranted()) {
                    Thread thread = new Thread(new Thread() {
                        public void run() {
                            DownloadImage(CaptureCompleteScreen());
                        }
                    });
                    thread.start();
                } else
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[15],
                            Toast.LENGTH_SHORT).show();

                break;
            case R.id.share:
                Thread thread = new Thread(new Thread() {
                    public void run() {
                        shareImage(CaptureCompleteScreen());
                    }
                });
                thread.start();
                break;
            case R.id.settings:
                if (SimulationState == SIMULATION_STATUS.ON)
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[10],
                            Toast.LENGTH_SHORT).show();
                else
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.simulate:
                if(SimulationState == SIMULATION_STATUS.ON) {
                    SimHandle.cancel(true);
                }
                if (SimulationState == SIMULATION_STATUS.DISABLED) {
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[8],
                            Toast.LENGTH_SHORT).show();
                }
                if (SimulationState == SIMULATION_STATUS.OFF) {
                    if (DeviceConnected) {
                        ChangeStateToSimulating();
                        SimHandle = new Simulate();
                        SimHandle.execute(0);
                    }
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void SetProperSimulationStatus() {
        if (DeviceConnected && (Model!=null))
            ChangeStateToNotSimulating();
        else
            ChangeStateToSimulateDisabled();
    }
    void ChangeStateToSimulateDisabled () {
        SimulationState = SIMULATION_STATUS.DISABLED;
        if (SimulateButton != null) SimulateButton.setIcon(R.drawable.icon_simulate_disabled);
    }
    void ChangeStateToSimulating () {
        SimulationState = SIMULATION_STATUS.ON;
        if (SimulateButton != null) SimulateButton.setIcon(R.drawable.icon_simulate_stop);
        if (SettingsButton != null) SettingsButton.setIcon(R.drawable.icon_settings_disabled);
        if (DocumentationButton != null) DocumentationButton.setIcon(R.drawable.icon_documentation_disable);
    }
    void ChangeStateToNotSimulating () {
        SimulationState = SIMULATION_STATUS.OFF;
        if (SimulateButton != null) SimulateButton.setIcon(R.drawable.icon_simulate_start);
        if (SettingsButton != null) SettingsButton.setIcon(R.drawable.icon_settings);
        if (DocumentationButton != null) DocumentationButton.setIcon(R.drawable.icon_documentation);
    }
    double[] RecData = new double[3];
    boolean Purged = false;
    boolean isValidRead=false;
    String PrevString="";
    private void PurgeReceivedBuffer() {
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Purged = true;
    }
    private void DataRecUpdateAdio (byte[] data) {

    }
    private void DataRecUpdate (byte[] data) {
        String Rec = PrevString + new String(data);
        //Log.i("Timing", "Found New data:" + new String(data));
        if (Rec.contains("\n") && Rec.contains("\r")) {
            PrevString = "";
            try {
                /*Log.i("Timing", "Obtained: "+Rec);
                Log.i("Timing", "Extracted: "+Rec);*/


                switch (getPrefString("bridge_circuit_type", "ARD")) {
                    case "ARD":
                        RecData[0] = Double.parseDouble(Rec) / 1024 * 5;
                        break;
                    case "PIC":
                        RecData[0] = Double.parseDouble(Rec);
                        Log.i("Timing", "PIC data rec:" + RecData[0]);
                        break;
                }
                isValidRead = true;
            } catch (Exception e) {
                //Log.i("Timing", "Error in parse");
            }
        } else if (Purged)
            PrevString = Rec;
    }

    private void RequestAIAdio() {
        byte[] OutBytes= {'3', 'a'};
        OutBytes[1] += getPrefInt("bridge_ai_port",0);
        arduino.send(OutBytes);
    }
    private void RequestAI() {
        byte[] OutBytes= {(byte)0x32,0,0,};
        arduino.send(OutBytes);
    }
    private void WriteToUSB(double Value) {
        arduino.send(ConvertToIntTSendBytesForAdio(ConvertFloatToIntForAO(Value)));
    }

    private long ConvertFloatToIntForAO (double OutFloat) {
        switch (getPrefString("bridge_circuit_type", "ARD")) {
            case "ARD":
                return Math.round(OutFloat * 51.0);
            case "PIC":
                Log.i("Timing", "PWM: "+Math.round(OutFloat * 204.6));
                return Math.round(OutFloat * 204.6);
        }
        return 0;
    }

    private byte[] ConvertToIntTSendBytesForAdio (long Out) {
        byte[] OutBytes= {'4', 'a', 0, 0};
        OutBytes[1] += getPrefInt("bridge_ao_port",5);
        switch (getPrefString("bridge_circuit_type", "ARD")) {
            case "ARD":
                OutBytes[2] = (byte) (Out & 0x0ff);
                break;
            case "PIC":
                OutBytes[2] = (byte) (Out & 0x0ff);
                OutBytes[3] = (byte) ((Out>>8) & 0x0ff);
                Log.i("PIC", "Out ..."+OutBytes[3]);
                break;
        }

        return OutBytes;
    }
    private byte[] ConvertToIntTSendBytes (long Out) {
        byte[] OutBytes= {(byte)0x31, 0,0};
        if (Math.abs(Out)>=255)
            OutBytes[1] = (byte) 0xff;
        else
            OutBytes[1] = (byte) (Math.abs(Out) & 0x0ff);
        if (Out>0)
            OutBytes[2] = 0x00;
        else
            OutBytes[2] = 0x01;
        return OutBytes;
    }
    public double PutBetweenRange (double value, double MinValue, double MaxValue) {
        if (value>MaxValue)
            return MaxValue;
        if (value<MinValue)
            return MinValue;
        return value;
    }

    /*
    Async task for implementing algorithms in real time
    */
    private class Simulate extends AsyncTask <Integer, Integer, Integer> {
        double[][] Input;
        double[][] Output;
        double[][] PreparedSignals;
        double Time;
        double[] ReadTimes = {0,0,0,0};
        int WeightForWMA = getPrefInt("sim_wma_weight",100);
        int DenForWMA = WeightForWMA*(WeightForWMA+1)/2;
        long Iteration = 1;
        int DataPointsForMA = getPrefInt("sim_ma_data_points",100);
        double[] ActualT_S = new double[DataPointsForMA];
        double TotalForWMA, NumForWMA;
        double EMASum, EMACount = 0;
        double EMAAlpha = 2/(WeightForWMA+1);

        boolean RequestSend = true;
        boolean IsFirstProgressOutput=true;
        boolean TimeOutError = false;

        double PlotValues[][];
        int GraphRefreshAfter=0;

        int OutputSignalsCount =0;
        TextView TextViewForInstantValues[];
        String CurrentOutputValuesToDisplay[];
        @Override
        protected Integer doInBackground(Integer... Params) {
            long StartTime = System.currentTimeMillis();
            isValidRead = true;
            while(!this.isCancelled()) {
                if (!Purged)
                    PurgeReceivedBuffer();
                Time = (System.currentTimeMillis()-StartTime)/1000.0;
                if ((Math.round((Time-ReadTimes[0]-Model.PlannedT_S)*1000) >= 0) && RequestSend) {
                    PutElementToFIFO(ReadTimes, Time);
                    PutElementToFIFO(ActualT_S, ReadTimes[0] - ReadTimes[1]);
                    /*DataPointsForMA = (Iteration <= getPrefInt("sim_ma_data_points",100))
                            ? (int) Iteration
                            : getPrefInt("sim_ma_data_points",100);*/
                    switch (getPrefString("sim_actual_sampling_time_type", "SIM")) {
                        case "SIM":
                            Model.T_SForModel = ReadTimes[0] - ReadTimes[1];
                            break;
                        case "SMA":
                            if (DataPointsForMA>Iteration) {
                                if (Iteration>1)
                                    Model.T_SForModel = (ActualT_S[0] + Model.T_SForModel*(Iteration-1))/Iteration;
                                else
                                    Model.T_SForModel = ActualT_S[0];
                            } else {
                                Model.T_SForModel = Model.T_SForModel
                                        + (ActualT_S[0] - ActualT_S[ActualT_S.length - 1]) / (DataPointsForMA * 1.0);
                            }
                            break;
                        case "CMA":
                            if (DataPointsForMA>Iteration) {
                                if (Iteration>1)
                                    Model.T_SForModel = (ActualT_S[0] + Model.T_SForModel*(Iteration-1))/Iteration;
                                else
                                    Model.T_SForModel = ActualT_S[0];
                            } else {
                                Model.T_SForModel = Model.T_SForModel
                                        + (ActualT_S[0] - Model.T_SForModel) / Iteration;
                            }
                            break;
                        case "WMA":
                            if (DataPointsForMA>Iteration) {
                                if (Iteration>1)
                                    Model.T_SForModel = (ActualT_S[0] + Model.T_SForModel*(Iteration-1))/Iteration;
                                else
                                    Model.T_SForModel = ActualT_S[0];
                            } else {
                                NumForWMA = NumForWMA + WeightForWMA * ActualT_S[0] - TotalForWMA;
                                TotalForWMA = TotalForWMA + ActualT_S[0] - ActualT_S[ActualT_S.length - 1];
                                Model.T_SForModel = NumForWMA / DenForWMA;
                            }
                            break;
                        case "EMA":
                            if (DataPointsForMA>Iteration) {
                                if (Iteration>1)
                                    Model.T_SForModel = (ActualT_S[0] + Model.T_SForModel*(Iteration-1))/Iteration;
                                else
                                    Model.T_SForModel = ActualT_S[0];
                            } else {
                                EMASum = ActualT_S[0] + (1 - EMAAlpha) * EMASum;
                                EMACount = 1 + (1 - EMAAlpha) * EMACount;
                                Model.T_SForModel = EMASum / EMACount;
                            }
                            break;
                        default:
                            break;
                    }


                    //Model.T_SForModel = ReadTimes[0] - ReadTimes[1];
                    RequestSend = false;
                    RequestAIAdio();
                }
                if (isValidRead) {
                    RequestSend = true;
                    isValidRead  = false;
                    for (int i=0; i<Input.length; i++)
                        Input[i] = PutElementToFIFO(Input[i], RecData[i]);
                    for (int i = 0; i< PreparedSignals.length; i++)
                        PreparedSignals[i] = PutElementToFIFO(PreparedSignals[i],
                                GeneratedSignals[i].GetValue(Time));
                    if (Model.T_SForModel > 0) {
                        Model.SimulationTime = Time;
                        double[] TempOutput = Model.RunAlgorithms(
                                GetParameters(),
                                PreparedSignals,
                                Input,
                                Output
                        );
                        for (int i=0; i<TempOutput.length; i++)
                            Output[i] = PutElementToFIFO(Output[i], TempOutput[i]);
                    }
                    WriteToUSB(PutBetweenRange(Output[0][0], AnalogOutLimits[0], AnalogOutLimits[1]));

                    //Preparing output display
                    CurrentOutputValuesToDisplay[0] = String.valueOf(Time);
                    CurrentOutputValuesToDisplay[1] = String.format(Locale.US,"%G",(Model.T_SForModel*1000));
                    double[] SignalsToPlot = Model.OutGraphSignals(
                            GetParameters(),
                            PreparedSignals,
                            Input,
                            Output
                    );
                    for (int i=0; i<OutputSignalsCount; i++)
                        CurrentOutputValuesToDisplay[2+i] = String.format(Locale.US,"%G",SignalsToPlot[i]);
                    int IterationGraphs=0;
                    for (int i = 0; i < Model.Figures.length; i++) {
                        for (int j = 0; j < Model.Figures[i].Trajectories.length; j++) {
                            if (LineCharts[i].getLineData().getDataSetByIndex(j).getEntryCount()
                                    > getPrefInt("graph_collect_size", 200))
                                LineCharts[i].getLineData().getDataSetByIndex(j).removeFirst();
                            LineCharts[i].getLineData().getDataSetByIndex(j).addEntry(new Entry(
                                    (float) Model.SimulationTime, (float) PutBetweenRange(SignalsToPlot[IterationGraphs], TrajectoryLimits[0], TrajectoryLimits[1]))
                            );
                            IterationGraphs++;
                        }
                    }
                    if ((Iteration)%GraphRefreshAfter == 0)
                        publishProgress(0);

                    //Increasing the iteration
                    Iteration++;
                }
                if ((Time-ReadTimes[0]) >
                        (Model.PlannedT_S + getPrefInt("sim_sampling_tolerance", 1000)/1000.0)) {
                    TimeOutError = true;
                    SimHandle.cancel(true);
                    break;
                }
                if (getPrefInt("sim_stop_sim_after",-1)>0)
                    if (Model.SimulationTime>1.0*getPrefInt("sim_stop_sim_after",-1)/1000.0) {
                        SimHandle.cancel(true);
                        break;
                    }
                try {
                    Model.PlannedT_S = Double.parseDouble(ModelSamplingTime.getText().toString())/1000.0;
                } catch (Exception e) {
                    Model.PlannedT_S = getPrefInt("sim_sampling_time", 100)/1000.0;
                }
                GraphRefreshAfter = (int) Math.round(getPrefInt("graph_refresh_after",200)/1000.0/Model.PlannedT_S);
                GraphRefreshAfter = GraphRefreshAfter<1?1:GraphRefreshAfter;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... Params) {
            FillLinePoints(GraphRefreshAfter);
        }

        @Override
        protected void onPreExecute () {
            TimeOutError = false;
            DisableDrawer();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Purged = false;
            IsFirstProgressOutput = true;
            AnalogOutLimits[0] =  getPrefInt("bridge_out_limit_lower", 0);
            AnalogOutLimits[1] =  getPrefInt("bridge_out_limit_upper", 5);
            TrajectoryLimits[0] = -getPrefInt("graph_vertical_upper_lower_limit", 10000);
            TrajectoryLimits[1] = getPrefInt("graph_vertical_upper_lower_limit", 10000);
            try {
                Model.PlannedT_S = Double.parseDouble(ModelSamplingTime.getText().toString())/1000.0;
            } catch (Exception e) {
                Model.PlannedT_S = getPrefInt("sim_sampling_time", 100)/1000.0;
            }
            Input = new double[Model.NoOfInputs][Model.NoOfPastInputsRequired+1];
            Output = new double[Model.NoOfOutputs][Model.NoOfPastOuputsRequired+1];
            PreparedSignals = new double[Model.SignalGenerators.length][Model.NoOfPastGeneratedValuesRequired+1];
            for (int i=0; i<Input.length; i++) {
                for (int j=0; j<Input[i].length; j++)
                    Input[i][j] = 0;
            }
            for (int i=0; i<Output.length; i++) {
                for (int j=0; j<Output[i].length; j++)
                    Output[i][j] = 0;
            }
            for (int i = 0; i< PreparedSignals.length; i++) {
                for (int j = 0; j< PreparedSignals[i].length; j++)
                    PreparedSignals[i][j] = 0;
            }


            for (int i=0; i<Model.Figures.length; i++) {
                OutputSignalsCount +=Model.Figures[i].Trajectories.length;
                AddPlots(i);
                if (getPrefBool("graph_zoom_options", false))
                    ZoomOptions[i].setVisibility(View.VISIBLE);
                else
                    ZoomOptions[i].setVisibility(View.GONE);
            }
            GraphRefreshAfter = (int) Math.round(getPrefInt("graph_refresh_after",200)/1000.0/Model.PlannedT_S);
            GraphRefreshAfter = GraphRefreshAfter<1?1:GraphRefreshAfter;
            PlotValues = new double[GraphRefreshAfter][OutputSignalsCount +1];

            //Generating view for Instantaneous Values
            CurrentOutputValuesToDisplay = new String[OutputSignalsCount +2];
            TextViewForInstantValues = new TextView[OutputSignalsCount +2];
            InstantaneousValues.removeAllViews();
            DrawAHLine(InstantaneousValues);
            DrawAHLine(InstantaneousValues);
            TableRow TempTableRow;
            TextView TempTextView;
            //Adding Time
            TempTableRow = (TableRow) getLayoutInflater().inflate(R.layout.gsk_table_row, null);
            TempTextView = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
            TempTextView.setText(R.string.TIME);
            TempTableRow.addView(TempTextView);
            DrawAVLine(TempTableRow);
            TextViewForInstantValues[0] = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
            TempTableRow.addView(TextViewForInstantValues[0]);
            DrawAVLine(TempTableRow);
            TempTextView = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
            TempTextView.setText("s");
            TempTableRow.addView(TempTextView);
            InstantaneousValues.addView(TempTableRow);
            //Adding Sampling Time
            DrawAHLine(InstantaneousValues);
            TempTableRow = (TableRow) getLayoutInflater().inflate(R.layout.gsk_table_row, null);
            TempTextView = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
            TempTextView.setText(R.string.ACTUAL_SAMPLING_TIME);
            TempTableRow.addView(TempTextView);
            DrawAVLine(TempTableRow);
            TextViewForInstantValues[1] = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
            TempTableRow.addView(TextViewForInstantValues[1]);
            DrawAVLine(TempTableRow);
            TempTextView = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
            TempTextView.setText("ms");
            TempTableRow.addView(TempTextView);
            InstantaneousValues.addView(TempTableRow);
            //Adding Other value
            int TempIteration = 0;
            for (int i = 0; i < Model.Figures.length; i++) {
                for (int j = 0; j < Model.Figures[i].Trajectories.length; j++) {
                    DrawAHLine(InstantaneousValues);
                    TempTableRow = (TableRow) getLayoutInflater().inflate(R.layout.gsk_table_row, null);
                    TempTextView = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
                    TempTextView.setText(Model.Figures[i].Trajectories[j]);
                    TempTableRow.addView(TempTextView);
                    DrawAVLine(TempTableRow);
                    TextViewForInstantValues[TempIteration+2] = (TextView) getLayoutInflater().inflate(R.layout.gsk_text_view, null);
                    TempTableRow.addView(TextViewForInstantValues[TempIteration+2]);
                    DrawAVLine(TempTableRow);
                    InstantaneousValues.addView(TempTableRow);
                    TempIteration++;
                }
            }
        }
        protected double[] GetParameters () {
            double[] ParameterValues = new double[Model.Parameters.length];
            for (int i=0; i<ParameterValues.length; i++) {
                try {
                    ParameterValues[i] = Double.parseDouble(ModelParams[i].getText().toString());
                } catch (Exception e){
                    ParameterValues[i] = 0;
                }
            }
            return ParameterValues;
        }
        protected double[] PutElementToFIFO (double[] array, double element){
            for (int i=(array.length-1); i>0; i--) {
                array[i] = array[i-1];
            }
            array[0] = element;
            return array;
        }
        protected void onCancelled() {
            FillLinePoints((int)Iteration%GraphRefreshAfter);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            SetProperSimulationStatus();
            EnableDrawer();
            if (TimeOutError)
                Toast.makeText(MainActivity.this,
                        getResources().getStringArray(R.array.TOASTS)[14],
                        Toast.LENGTH_LONG).show();
        }
        void AddPlots(int i) {
            LineData lineData = new LineData();
            for (int j=0; j<Model.Figures[i].Trajectories.length; j++) {
                List<Entry> entries = new ArrayList<Entry>();
                LineDataSet dataSet = new LineDataSet(entries, Model.Figures[i].Trajectories[j]);
                lineData.addDataSet(dataSet);
                dataSet.setDrawCircles(false);
                dataSet.setDrawCircleHole(false);
                dataSet.setDrawValues(false);
                dataSet.setColor(ColorTable[j]);
                dataSet.setLineWidth(Float.valueOf(getPrefInt("graph_line_thickness",1)));
                if (getPrefBool("graph_cubic",false))
                    dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
            }
            LineCharts[i].setData(lineData);
        }
        void ConfigFigure(int i) {
            LineCharts[i].setMinimumHeight(
                    Math.round(convertDpToPixel(Float.valueOf(getPrefInt("graph_window_height",200)),getApplicationContext()))
            );
            LineCharts[i].getDescription().setEnabled(false);
            LineCharts[i].invalidate();
            LineCharts[i].getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
            int GraphFontSize = getPrefInt("graph_font_size", 15);
            LineCharts[i].getXAxis().setTextSize(GraphFontSize);
            LineCharts[i].getAxisLeft().setTextSize(GraphFontSize);
            LineCharts[i].getAxisRight().setTextSize(GraphFontSize);
            LineCharts[i].getLegend().setTextSize(GraphFontSize);
            LineCharts[i].getLegend().setFormSize(GraphFontSize);
            LineCharts[i].getLegend().setForm(Legend.LegendForm.valueOf(getPrefString("graph_legend_form", "DEFAULT")));
        }
        void FillLinePoints(int NumbOfPoints) {
            for (int i=0; i<(OutputSignalsCount + 2); i++)
                TextViewForInstantValues[i].setText(CurrentOutputValuesToDisplay[i]);
            if (IsFirstProgressOutput) for (int i = 0; i < Model.Figures.length; i++) {
                IsFirstProgressOutput = false;
                ConfigFigure(i);
            }
            for (int i = 0; i < Model.Figures.length; i++) {
                LineCharts[i].getLineData().notifyDataChanged();
                LineCharts[i].notifyDataSetChanged();
                LineCharts[i].invalidate();
            }
        }
    }
}