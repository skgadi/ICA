package com.skgadi.ica;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;
import app.akexorcist.bluetotohspp.library.BluetoothState;
import app.akexorcist.bluetotohspp.library.DeviceList;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {


    //--- Added by SKGadi
    enum TOOLBAR_STATES {
        BLUETOOTH_UNAVAILABLE,
        BLUETOOTH_DISCONNECTED,
        BLUETOOTH_CONNECTED,
        READY_TO_SIMULATE,
        SIMULATING,
    }
    enum BLUETOOTH_STATUS {
        UNAVAILABLE,
        DISABLED,
        ENABLED,
        CONNECTED,
        IN_USE
    }
    BLUETOOTH_STATUS BluetoothStatus;
    BluetoothSPP bt;
    android.content.Context Context;

    LinearLayout ModelView;

    String[] Toasts;

    MenuItem ShareButton;
    MenuItem GrabScreenshot;
    MenuItem BluetoothButton;
    MenuItem RunButton;
    MenuItem DocumentationButton;
    MenuItem SettingsButton;
    //---Original Code
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //Added by SKGadi
        ModelView = (LinearLayout)findViewById(R.id.MSRootLayout);
        Toasts = getResources().getStringArray(R.array.Toasts);
        Context = getApplicationContext();
        bt = new BluetoothSPP(Context);
        SetHomeScreenToDefaultState();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        ShareButton = menu.getItem(0);
        GrabScreenshot = menu.getItem(1);
        BluetoothButton = menu.getItem(2);
        RunButton = menu.getItem(3);
        DocumentationButton = menu.getItem(4);
        SettingsButton = menu.getItem(5);
        CheckBluetoothAvailable();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_share:
                SetToolBarIconsStates(TOOLBAR_STATES.SIMULATING);
                break;
            case R.id.action_image:
                SetToolBarIconsStates(TOOLBAR_STATES.BLUETOOTH_CONNECTED);
                break;
            case R.id.action_bluetooth:
                HandleBluetoothButton();
                break;
            case R.id.action_run_simulation:
                SetToolBarIconsStates(TOOLBAR_STATES.BLUETOOTH_UNAVAILABLE);
                break;
            case R.id.action_documentation:
                SetToolBarIconsStates(TOOLBAR_STATES.READY_TO_SIMULATE);
                break;
            case R.id.action_settings:
                break;
        }
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_pid:
                break;
            default:
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /*Added by SKGadi*/

    //---Handling Bluetooth start
    private void HandleBluetoothButton() {
        Intent intent;
        switch (BluetoothStatus) {
            case UNAVAILABLE:
                CheckBluetoothAvailable();
                break;
            case DISABLED:
                CheckBluetoothAvailable();
                intent = new Intent(getApplicationContext(), DeviceList.class);
                startActivityForResult(intent, BluetoothState..REQUEST_ENABLE_BT);
                Toast.makeText(Context, "hello", Toast.LENGTH_SHORT).show();
                break;
            case ENABLED:
                intent = new Intent(getApplicationContext(), DeviceList.class);
                startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
                break;
            case CONNECTED:
                break;
            case IN_USE:
                break;
        }
        /*bt.startService(BluetoothState.DEVICE_OTHER);
        bt.stopService();*/
    }
    private void CheckBluetoothAvailable() {
        if(bt.isBluetoothAvailable()) {
            if(bt.isBluetoothEnabled()) {
                BluetoothStatus = BLUETOOTH_STATUS.ENABLED;
                SetToolBarIconsStates(TOOLBAR_STATES.BLUETOOTH_DISCONNECTED);
                Toast.makeText(Context, Toasts[2], Toast.LENGTH_SHORT).show();
            } else {
                BluetoothStatus = BLUETOOTH_STATUS.DISABLED;
                SetToolBarIconsStates(TOOLBAR_STATES.BLUETOOTH_UNAVAILABLE);
                Toast.makeText(Context, Toasts[1], Toast.LENGTH_SHORT).show();
            }
        } else {
            BluetoothStatus = BLUETOOTH_STATUS.UNAVAILABLE;
            SetToolBarIconsStates(TOOLBAR_STATES.BLUETOOTH_UNAVAILABLE);
            Toast.makeText(Context, Toasts[0], Toast.LENGTH_SHORT).show();
        }
    }
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == BluetoothState.REQUEST_CONNECT_DEVICE) {
            if(resultCode == Activity.RESULT_OK)
                bt.connect(data);
        } else if(requestCode == BluetoothState.REQUEST_ENABLE_BT) {
            if(resultCode == Activity.RESULT_OK) {
                bt.setupService();
                bt.startService(BluetoothState.DEVICE_ANDROID);
            } else {
                // Do something if user doesn't choose any device (Pressed back)
                Toast.makeText(Context, Toasts[0], Toast.LENGTH_SHORT).show();
            }
        }
    }
    //---Handling Bluetooth end

    private void SetToolBarIconsStates (TOOLBAR_STATES TBState) {
        switch (TBState) {
            case BLUETOOTH_UNAVAILABLE:
                ShareButton.setVisible(true);
                GrabScreenshot.setVisible(true);
                BluetoothButton.setVisible(true);
                BluetoothButton.setIcon(R.drawable.ic_bluetooth_disabled_black_24dp);
                RunButton.setVisible(false);
                DocumentationButton.setVisible(true);
                SettingsButton.setVisible(true);
                break;
            case BLUETOOTH_DISCONNECTED:
                ShareButton.setVisible(true);
                GrabScreenshot.setVisible(true);
                BluetoothButton.setVisible(true);
                BluetoothButton.setIcon(R.drawable.ic_bluetooth_black_24dp);
                RunButton.setVisible(false);
                DocumentationButton.setVisible(true);
                SettingsButton.setVisible(true);
                break;
            case BLUETOOTH_CONNECTED:
                ShareButton.setVisible(true);
                GrabScreenshot.setVisible(true);
                BluetoothButton.setVisible(true);
                BluetoothButton.setIcon(R.drawable.ic_bluetooth_connected_black_24dp);
                RunButton.setVisible(false);
                DocumentationButton.setVisible(true);
                SettingsButton.setVisible(true);
                break;
            case READY_TO_SIMULATE:
                ShareButton.setVisible(true);
                GrabScreenshot.setVisible(true);
                BluetoothButton.setVisible(true);
                BluetoothButton.setIcon(R.drawable.ic_bluetooth_connected_black_24dp);
                RunButton.setVisible(true);
                RunButton.setIcon(R.drawable.ic_play_arrow_black_24dp);
                DocumentationButton.setVisible(true);
                SettingsButton.setVisible(true);
                break;
            case SIMULATING:
                ShareButton.setVisible(false);
                GrabScreenshot.setVisible(true);
                BluetoothButton.setVisible(false);
                RunButton.setIcon(R.drawable.ic_stop_black_24dp);
                RunButton.setVisible(true);
                DocumentationButton.setVisible(false);
                SettingsButton.setVisible(false);
                break;
        }
    }
    private void ClearTheModelView () {
        if (ModelView.getChildCount() >0 )
            ModelView.removeAllViews();
    }
    private void SetHomeScreenToDefaultState() {
        ClearTheModelView ();
        SubsamplingScaleImageView TempImgView = (SubsamplingScaleImageView) getLayoutInflater().inflate(R.layout.gsk_imageview, null);
        TempImgView.setImage(ImageSource.resource(R.drawable.background));
        ModelView.addView(TempImgView);
    }

}
