/**
 *    ||          ____  _ __
 * +------+      / __ )(_) /_______________ _____  ___
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package se.bitcraze.crazyfliecontrol2;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import lightingtheway.MovementRecorder;
import lightingtheway.PacketControl;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import se.bitcraze.crazyflie.lib.BleLink;
import se.bitcraze.crazyflie.lib.crazyflie.ConnectionAdapter;
import se.bitcraze.crazyflie.lib.crazyflie.Crazyflie;
import se.bitcraze.crazyflie.lib.crazyradio.ConnectionData;
import se.bitcraze.crazyflie.lib.crazyradio.Crazyradio;
import se.bitcraze.crazyflie.lib.crazyradio.RadioDriver;
import se.bitcraze.crazyflie.lib.crtp.CrtpDriver;
import se.bitcraze.crazyflie.lib.log.LogAdapter;
import se.bitcraze.crazyflie.lib.log.LogConfig;
import se.bitcraze.crazyflie.lib.log.Logg;
import se.bitcraze.crazyflie.lib.toc.Toc;
import se.bitcraze.crazyflie.lib.toc.VariableType;
import se.bitcraze.crazyfliecontrol.controller.Controls;
import se.bitcraze.crazyfliecontrol.controller.IController;
import se.bitcraze.crazyfliecontrol.prefs.PreferencesActivity;

public class MainActivity extends Activity {

    private static final String LOG_TAG = "CrazyflieControl";

    private PacketControl mPacketControl;
    private MovementRecorder mMovementRecorder = new MovementRecorder();

    private Crazyflie mCrazyflie;
    private CrtpDriver mDriver;
    private Toc mParamToc;
    private Toc mLogToc;

    private Logg mLogg;
    private LogConfig mLogConfigStandard = new LogConfig("Standard", 200);

    private SharedPreferences mPreferences;

    private String mRadioChannelDefaultValue;
    private String mRadioDatarateDefaultValue;

    private boolean mDoubleBackToExitPressedOnce = false;


    // thread for background processes.
    // such as BLE connection pinging
    private Thread mBackgroundThread;

    private Controls mControls;

    private SoundPool mSoundPool;
    private boolean mLoaded;
    private int mSoundConnect;
    private int mSoundDisconnect;

    private ImageButton mToggleConnectButton;

    private boolean mHeadlightToggle = false;
    private boolean mSoundToggle = false;
    private boolean mRampToggle = false;
    private int mRingEffect = 0;
    private int mNoRingEffect = 0;
    private int mCpuFlash = 0;

    private ImageButton mBuzzerSoundButton;
    private ImageButton mRampButton;

    private ImageButton mDeltaXUpButton;
    private ImageButton mDeltaXDownButton;
    private ImageButton mDeltaYUpButton;
    private ImageButton mDeltaYDownButton;
    private ImageButton mLiftOffThreshUpButton;
    private ImageButton mLiftOffThreshDownButton;
    private ImageButton mHoverThreshUpButton;
    private ImageButton mHoverThreshDownButton;

    private File mCacheDir;

    private TextView mTextView_battery;
    private TextView mTextView_linkQuality;

    private Button mapButton;
    private String mapResult;
    private TextView mapResultText;

    private ImageButton manualButton;

    private Button goButton;

    private SeekBar distBar;
    private TextView distText;
    private float minDist = 0.1f;
    private float maxDist = 0.2f;
    private float distance = minDist;

    private SeekBar speedBar;
    private TextView speedText;
    private float minSpeed = 0.1f;
    private float maxSpeed = 1.0f;
    private float speed = minSpeed;

    //toggle testing buttons
    boolean testing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setDefaultPreferenceValues();

        mTextView_battery = (TextView) findViewById(R.id.battery_text);
        mTextView_linkQuality = (TextView) findViewById(R.id.linkQuality_text);

        setBatteryLevel(-1.0f);
        setLinkQualityText("N/A");

        mControls = new Controls(this, mPreferences);
        mControls.setDefaultPreferenceValues(getResources());

        mPacketControl = new PacketControl();
        mPacketControl.setMovementRecorder(mMovementRecorder);

        //initialize buttons
        mToggleConnectButton = (ImageButton) findViewById(R.id.imageButton_connect);
        mRampButton = (ImageButton) findViewById(R.id.button_ramp);
        initializeMenuButtons();

        // testing tweak buttons

        mDeltaXUpButton = (ImageButton) findViewById(R.id.button_deltaXUp);
        mDeltaXDownButton = (ImageButton) findViewById(R.id.button_deltaXDown);
        mDeltaYUpButton = (ImageButton) findViewById(R.id.button_deltaYUp);
        mDeltaYDownButton = (ImageButton) findViewById(R.id.button_deltaYDown);
        mLiftOffThreshUpButton = (ImageButton) findViewById(R.id.button_LiftOffUp);
        mLiftOffThreshDownButton = (ImageButton) findViewById(R.id.button_LiftOffDown);
        mHoverThreshUpButton = (ImageButton) findViewById(R.id.button_HoverUp);
        mHoverThreshDownButton = (ImageButton) findViewById(R.id.button_HoverDown);
        initializeTestingButtons();


        //action buttons
        //mRingEffectButton = (ImageButton) findViewById(R.id.button_ledRing);
        //mHeadlightButton = (ImageButton) findViewById(R.id.button_headLight);
        mBuzzerSoundButton = (ImageButton) findViewById(R.id.button_buzzerSound);

        //distance bar
        distBar = (SeekBar) findViewById(R.id.distBar);
        distText = (TextView) findViewById(R.id.distText);
        //speed bar
        speedBar = (SeekBar) findViewById(R.id.speedBar);
        speedText = (TextView) findViewById(R.id.speedText);
        initializeSeekBar();

        //Activity switch button
        mapButton = (Button) findViewById(R.id.button_map);
        mapButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                Intent intent = new Intent(MainActivity.this, MappingActivity.class);
                startActivityForResult(intent, 0);
            }
        });
        mapResultText = (TextView) findViewById(R.id.mapResult);

        manualButton = (ImageButton) findViewById(R.id.button_manual);
        manualButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(testing){
                    initializeTestingButtons();
                }
                else {
                    enableTestingButtons();
                }
                testing = !testing;
            }
        });

        goButton = (Button) findViewById(R.id.button_go);
        goButton.setVisibility(View.INVISIBLE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(this.getPackageName()+".USB_PERMISSION");
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        initializeSounds();

        setCacheDir();

    }

    @Override
    // resultCode : 0 = user returned from setting src/dest
    //              1 = drone was detected too far from user so begin backtracking
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == 0 || requestCode == 1){
            if(resultCode == RESULT_OK){
                mapResult = data.getStringExtra("result");

                /* testing text box and passing string between activities, remove later */
                mapResultText.setText(mapResult);
                // resultCode 1 is set for drone to backtrack user. This should be automatic
                if (resultCode == 1) {
                    instructCrazyFlie(mapResult);
                    return;
                }
                /* if map result has instructions, reveal GO button */
                if(mapResult.length() > 1 && mapResult.contains("->")){
                    goButton.setVisibility(View.VISIBLE);
                    goButton.setOnClickListener(new View.OnClickListener(){
                        @Override
                        public void onClick(View v){
                            /* ramp sequence */
                            Log.d(LOG_TAG, "RampButton clicked");
                            if (mCrazyflie != null) {
                                if (!mRampToggle) {
                                    Log.d(LOG_TAG, "Ramp - start");
                                    mRampButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button_connected_ble));
                                    startRampSequence();
                                    mRampToggle = !mRampToggle;
                                }

                                /* send commands to crazyflie */
                                instructCrazyFlie(mapResult);
                            } else {
                                Log.d(LOG_TAG, "Thinks crazyflie is null or not connected");
                            }
                            goButton.setVisibility(View.INVISIBLE);
                            goButton.setEnabled(false);
                        }
                    });
                    goButton.setEnabled(true);
                }
                else{
                    goButton.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private void instructCrazyFlie(String instructions){
        /* east = 0
        north = 90
        west = 180
        south = 270 */

        // wait for crazyflie to lift off before giving instructions
        try{
            Thread.sleep(200);
        } catch (Exception e) {}

        float orientation = (float)mMovementRecorder.getCurrentAngle();
        String[] allCommands = instructions.split("\n");
        String[] command; /* 2 elements, nodes and vector */
        String[] nodes; /* 2 elements, 0=src, 1=dest */
        String[] magDir; /*2 elements, 0=magnitude, 1=direction */
        float turn;
        float newOrientation;
        float magnitude = .5f;

        for(int i=1; i<allCommands.length; i++){
            command = allCommands[i].split(" : ");
            nodes = command[0].split(" -> ");
            magDir = command[1].split(", ");
            magnitude = Float.parseFloat(magDir[0]);
            newOrientation = Float.parseFloat(magDir[1]);
            turn = newOrientation - orientation;
            //System.out.println(turn);
            if(turn>0 && turn<=180){
                mPacketControl.turnLeft(turn, 18,nodes[0]);
                //System.out.println("Turn left: " + turn);
            }
            else if(turn>180 && turn<360){
                mPacketControl.turnRight(180.0f-turn, 18,nodes[0]);
                turn = 180.0f-turn;
                //System.out.println("Turn right: " + turn);
            }
            else{
                turn *= -1;
                if(turn>0 && turn<=180){
                    mPacketControl.turnRight(turn, 18,nodes[0]);
                    //System.out.println("Turn right: " + turn);
                }
                else if(turn>180 && turn<360){
                    mPacketControl.turnLeft(180.0f-turn, 18,nodes[0]);
                    turn = 180.0f-turn;
                    //System.out.println("Turn left: " + turn);
                }
            }
            orientation = newOrientation;

            int ratio = 1;
            // instruct crazyflie to go forward
            // for now to keep drone for going off, set to magnitude/ratio and set velocity of .1 m/s
            // 2 input Strings for src Node and dest Node names
            mPacketControl.goForward(magnitude/ratio, .1f,nodes[0],nodes[1]);

        }



    }

    private void initializeSounds() {
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        // Load sounds
        mSoundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                mLoaded = true;
            }
        });
        mSoundConnect = mSoundPool.load(this, R.raw.proxima, 1);
        mSoundDisconnect = mSoundPool.load(this, R.raw.tejat, 1);
    }

    private void setCacheDir() {
        if (isExternalStorageWriteable()) {
            Log.d(LOG_TAG, "External storage is writeable.");
            if (mCacheDir == null) {
                File appDir = getApplicationContext().getExternalFilesDir(null);
                mCacheDir = new File(appDir, "TOC_cache");
                mCacheDir.mkdirs();
            }
        } else {
            Log.d(LOG_TAG, "External storage is not writeable.");
            mCacheDir = null;
        }
    }

    private boolean isExternalStorageWriteable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private void setDefaultPreferenceValues(){
        // Set default preference values
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        // Initialize preferences
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mRadioChannelDefaultValue = getString(R.string.preferences_radio_channel_defaultValue);
        mRadioDatarateDefaultValue = getString(R.string.preferences_radio_datarate_defaultValue);
    }

    private void checkScreenLock() {
        boolean isScreenLock = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_SCREEN_ROTATION_LOCK_BOOL, false);
        /*
        if(isScreenLock){
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }else{
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
        */
    }

    private void initializeMenuButtons() {
        mToggleConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(LOG_TAG, "Connect Button Clicked");
                try {
                    if (mCrazyflie != null && mCrazyflie.isConnected()) {
                        disconnect();
                    } else {
                        connect();
                    }
                } catch (IllegalStateException e) {
                    Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        ImageButton settingsButton = (ImageButton) findViewById(R.id.imageButton_settings);
        settingsButton.setVisibility(View.INVISIBLE);
        settingsButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
              Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
              startActivity(intent);
            }
        });

        mRampButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(LOG_TAG, "RampButton clicked");
                if (mCrazyflie != null) {
                    if (!mRampToggle) {
                        Log.d(LOG_TAG, "Ramp - start");
                        mRampButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button_connected_ble));
                        startRampSequence();
                    } else {
                        Log.d(LOG_TAG, "Ramp - land");
                        mRampButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button));
                        startLandSequence();
                    }
                    mRampToggle = !mRampToggle;
                } else {
                    Log.d(LOG_TAG, "Thinks crazyflie is null or not connected");
                }

            }
        });
    }

    private void initializeSeekBar(){
        distBar.setProgress(0);
        distText.setText(String.format("%.2f", distance));
        distBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float percentage = (float)progress/100;
                distance = percentage*(maxDist-minDist);
                distance += minDist;
                mPacketControl.setmZdistance(distance);
                distText.setText(String.format("%.2f", distance));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        speedBar.setProgress(0);
        speedText.setText(String.format("%.2f", speed));
        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float percentage = (float)progress/100;
                speed = percentage*(maxSpeed-minSpeed);
                speed += minSpeed;
                speedText.setText(String.format("%.2f", speed));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }


    public void initializeTestingButtons(){

        // testing tweak buttons
        mDeltaXUpButton.setVisibility(View.INVISIBLE);
        mDeltaXDownButton.setVisibility(View.INVISIBLE);
        mDeltaYUpButton.setVisibility(View.INVISIBLE);
        mDeltaYDownButton.setVisibility(View.INVISIBLE);
        mLiftOffThreshUpButton.setVisibility(View.INVISIBLE);
        mLiftOffThreshDownButton.setVisibility(View.INVISIBLE);
        mHoverThreshUpButton.setVisibility(View.INVISIBLE);
        mHoverThreshDownButton.setVisibility(View.INVISIBLE);

        mDeltaXUpButton.setEnabled(false);
        mDeltaXDownButton.setEnabled(false);
        mDeltaYUpButton.setEnabled(false);
        mDeltaYDownButton.setEnabled(false);
        mLiftOffThreshUpButton.setEnabled(false);
        mLiftOffThreshDownButton.setEnabled(false);
        mHoverThreshUpButton.setEnabled(false);
        mHoverThreshDownButton.setEnabled(false);
    }

    public void enableTestingButtons(){
        mDeltaXUpButton.setVisibility(View.VISIBLE);
        mDeltaXUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // mPacketControl.incrementVx(0.1f);
                mPacketControl.goRight(.1f,.1f);
            }
        });
        mDeltaXDownButton.setVisibility(View.VISIBLE);
        mDeltaXDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mPacketControl.incrementVx(-0.1f);
                mPacketControl.goLeft(.1f,.1f);
            }
        });
        mDeltaYUpButton.setVisibility(View.VISIBLE);
        mDeltaYUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mPacketControl.incrementVy(0.1f);
                mPacketControl.goForward(.1f,.1f);
            }
        });
        mDeltaYDownButton.setVisibility(View.VISIBLE);
        mDeltaYDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mPacketControl.incrementVy(-0.1f);
                mPacketControl.goBack(.1f,.1f);
            }
        });
        mLiftOffThreshUpButton.setVisibility(View.VISIBLE);
        mLiftOffThreshUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPacketControl.turnLeft(5,1);
            }
        });
        mLiftOffThreshDownButton.setVisibility(View.VISIBLE);
        mLiftOffThreshDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPacketControl.turnRight(5,1);
            }
        });
        mHoverThreshUpButton.setVisibility(View.VISIBLE);
        mHoverThreshUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPacketControl.incrementZDistance(.1f);
            }
        });
        mHoverThreshDownButton.setVisibility(View.VISIBLE);
        mHoverThreshDownButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPacketControl.incrementZDistance(-.1f);
            }
        });

        mDeltaXUpButton.setEnabled(true);
        mDeltaXDownButton.setEnabled(true);
        mDeltaYUpButton.setEnabled(true);
        mDeltaYDownButton.setEnabled(true);
        mLiftOffThreshUpButton.setEnabled(true);
        mLiftOffThreshDownButton.setEnabled(true);
        mHoverThreshUpButton.setEnabled(true);
        mHoverThreshDownButton.setEnabled(true);
    }


    @Override
    public void onResume() {
        System.out.println("resuming");
        super.onResume();
        //TODO: improve
        PreferencesActivity.setDefaultJoystickSize(this);
        mControls.setControlConfig();
        resetInputMethod();
        checkScreenLock();
        //disable action buttons
        //mRingEffectButton.setEnabled(false);
        //mHeadlightButton.setEnabled(false);
        mBuzzerSoundButton.setEnabled(false);
        mRampButton.setEnabled(false);
        if (mPreferences.getBoolean(PreferencesActivity.KEY_PREF_IMMERSIVE_MODE_BOOL, false)) {
            setHideyBar();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mControls.resetAxisValues();
        //disconnect();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        mSoundPool.release();
        mSoundPool = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mDoubleBackToExitPressedOnce) {
            super.onBackPressed();
            return;
        }
        this.mDoubleBackToExitPressedOnce = true;
        Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                mDoubleBackToExitPressedOnce = false;

            }
        }, 2000);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(mPreferences.getBoolean(PreferencesActivity.KEY_PREF_IMMERSIVE_MODE_BOOL, false) && hasFocus){
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    setHideyBar();
                }
            }, 2000);
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void setHideyBar() {
        Log.i(LOG_TAG, "Activating immersive mode");
        int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
        int newUiOptions = uiOptions;

        if(Build.VERSION.SDK_INT >= 14){
            newUiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if(Build.VERSION.SDK_INT >= 16){
            newUiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        if(Build.VERSION.SDK_INT >= 18){
            newUiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(newUiOptions);
    }

    public void updateFlightData(){
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return false;
    }

    // this workaround is necessary because DPad buttons are not considered to be "Gamepad buttons"
    private static boolean isJoystickButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return KeyEvent.isGamepadButton(keyCode);
        }
    }

    private void resetInputMethod() {
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(LOG_TAG, "mUsbReceiver action: " + action);
            if ((MainActivity.this.getPackageName()+".USB_PERMISSION").equals(action)) {
                //reached only when USB permission on physical connect was canceled and "Connect" or "Radio Scan" is clicked
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Toast.makeText(MainActivity.this, "Crazyradio attached", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.d(LOG_TAG, "permission denied for device " + device);
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && UsbLinkAndroid.isUsbDevice(device, Crazyradio.CRADIO_VID, Crazyradio.CRADIO_PID)) {
                    Log.d(LOG_TAG, "Crazyradio detached");
                    Toast.makeText(MainActivity.this, "Crazyradio detached", Toast.LENGTH_SHORT).show();
                    playSound(mSoundDisconnect);
                    if (mCrazyflie != null) {
                        Log.d(LOG_TAG, "linkDisconnect()");
                        disconnect();
                    }
                }
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && UsbLinkAndroid.isUsbDevice(device, Crazyradio.CRADIO_VID, Crazyradio.CRADIO_PID)) {
                    Log.d(LOG_TAG, "Crazyradio attached");
                    Toast.makeText(MainActivity.this, "Crazyradio attached", Toast.LENGTH_SHORT).show();
                    playSound(mSoundConnect);
                }
            }
        }
    };

    private void playSound(int sound){
        if (mLoaded) {
            float volume = 1.0f;
            mSoundPool.play(sound, volume, volume, 1, 0, 1f);
        }
    }

    private ConnectionAdapter crazyflieConnectionAdapter = new ConnectionAdapter() {

        @Override
        public void connectionRequested(String connectionInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Connecting ...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public void connected(String connectionInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mCrazyflie != null && mCrazyflie.getDriver() instanceof BleLink) {
                        Toast.makeText(getApplicationContext(), "Connected With BLE", Toast.LENGTH_SHORT).show();
                        mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button_connected_ble));
                        mRampButton.setEnabled(true);

                        // THOMAS: We connected so we need to connect our control to the CF
                        mPacketControl.setCF(mCrazyflie);
                        mPacketControl.setControl(mControls);
                    } else {
                        Toast.makeText(getApplicationContext(), "Connected With Radio", Toast.LENGTH_SHORT).show();
                        mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button_connected));
                        mRampButton.setEnabled(true);
                    }
                }
            });
        }

        @Override
        public void setupFinished(String connectionInfo) {
            // begin param toc fetch
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Parameters TOC fetch begin", Toast.LENGTH_SHORT).show();
                }
            });
           final Toc paramToc = mCrazyflie.getParam().getToc();

           // begin Log toc fetch
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Log TOC fetch begin: " + paramToc.getTocSize(), Toast.LENGTH_SHORT).show();
                }
            });
           final Toc logToc = mCrazyflie.getLogg().getToc();
           if (paramToc != null) {
               mParamToc = paramToc;
               runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Parameters TOC fetch finished: " + paramToc.getTocSize(), Toast.LENGTH_SHORT).show();
                    }
                });
               mCrazyflie.getParam().setValue("sound.effect",10);
// THOMAS: These cause a crash when trying to start-up. This is because the parameters are named wrong and may not be active.
/*
                //activate buzzer sound button when a CF2 is recognized (a buzzer can not yet be detected separately)
                mCrazyflie.getParam().addParamListener(new ParamListener("cpu", "flash") {
                    @Override
                    public void updated(String name, Number value) {
                        mCpuFlash = mCrazyflie.getParam().getValue("cpu.flash").intValue();
                        //enable buzzer action button when a CF2 is found (cpu.flash == 1024)
                        if (mCpuFlash == 1024) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mBuzzerSoundButton.setEnabled(true);
                                }
                            });
                        }
                        Log.d(LOG_TAG, "CPU flash: " + mCpuFlash);
                    }
                });
                mCrazyflie.getParam().requestParamUpdate("cpu.flash");
                //set number of LED ring effects
                mCrazyflie.getParam().addParamListener(new ParamListener("ring", "neffect") {
                    @Override
                    public void updated(String name, Number value) {
                        mNoRingEffect = mCrazyflie.getParam().getValue("ring.neffect").intValue();
                        //enable LED ring action buttons only when ring.neffect parameter is set correctly (=> hence it's a CF2 with a LED ring)
                        if (mNoRingEffect > 0) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //mRingEffectButton.setEnabled(true);
                                    //mHeadlightButton.setEnabled(true);
                                }
                            });
                        }
                        Log.d(LOG_TAG, "No of ring effects: " + mNoRingEffect);
                    }
                });
*/
            }

            if (logToc != null) {
                mLogToc = logToc;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Log TOC fetch finished: " + logToc.getTocSize(), Toast.LENGTH_SHORT).show();
                    }
                });
                createLogConfigs();
                startLogConfigs();
            }
        }

        @Override
        public void connectionLost(String connectionInfo, final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                    mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button));
                }
            });
            disconnect();
        }

        @Override
        public void connectionFailed(String connectionInfo, final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                }
            });
            disconnect();
        }

        @Override
        public void disconnected(String connectionInfo) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Disconnected", Toast.LENGTH_SHORT).show();
                    mToggleConnectButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button));
                    //disable action buttons after disconnect
                    //mRingEffectButton.setEnabled(false);
                    //mHeadlightButton.setEnabled(false);
                    mBuzzerSoundButton.setEnabled(false);
                    mRampButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.custom_button));
                    mRampButton.setEnabled(false);
                    mRampToggle = false;
                    setBatteryLevel(-1.0f);
                    mPacketControl.setCF(null);
                    mPacketControl.setControl(null);
                }
            });
            stopLogConfigs();
        }

        @Override
        public void linkQualityUpdated(final int quality) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setLinkQualityText(quality + "%");
                }
            });
        }
    };

    private void connect() {
        Log.d(LOG_TAG, "connect()");
        // ensure previous link is disconnected
        disconnect();

        int radioChannel = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_RADIO_CHANNEL, mRadioChannelDefaultValue));
        int radioDatarate = Integer.parseInt(mPreferences.getString(PreferencesActivity.KEY_PREF_RADIO_DATARATE, mRadioDatarateDefaultValue));

        mDriver = null;

        if(isCrazyradioAvailable(this)) {
            try {
                mDriver = new RadioDriver(new UsbLinkAndroid(this));
            } catch (IllegalArgumentException e) {
                Log.d(LOG_TAG, e.getMessage());
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            //use BLE
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) &&
                    getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
                boolean writeWithResponse = mPreferences.getBoolean(PreferencesActivity.KEY_PREF_BLATENCY_BOOL, true);
                Log.d(LOG_TAG, "Using bluetooth write with response - " + writeWithResponse);
                mDriver = new BleLink(this, writeWithResponse);
            } else {
                // TODO: improve error message
                Log.e(LOG_TAG, "No BLE support available.");
            }
        }

        if (mDriver != null) {

            // add listener for connection status
            mDriver.addConnectionListener(crazyflieConnectionAdapter);

            mCrazyflie = new Crazyflie(mDriver, mCacheDir);

            // connect
            mCrazyflie.connect(new ConnectionData(radioChannel, radioDatarate));

//            mCrazyflie.addDataListener(new DataListener(CrtpPort.CONSOLE) {
//
//                @Override
//                public void dataReceived(CrtpPacket packet) {
//                    Log.d(LOG_TAG, "Received console packet: " + packet);
//                }
//
//            });
        } else {
            Toast.makeText(this, "Cannot connect: Crazyradio not attached and Bluetooth LE not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void startRampSequence() {
        mPacketControl.liftOff();
    };

    private void startLandSequence() {
        mPacketControl.land();
    };

    // extra method for onClick attribute in XML
    public void switchLedRingEffect(View view) {
        runAltAction("ring.effect");
    }

    // extra method for onClick attribute in XML
    public void toggleHeadlight(View view) {
        runAltAction("ring.headlightEnable");
    }

    // extra method for onClick attribute in XML
    public void playBuzzerSound(View view) {
        runAltAction("sound.effect:10");
    }

    //TODO: make runAltAction more universal
    public void runAltAction(String action) {
        Log.d(LOG_TAG, "runAltAction: " + action);
        if (mCrazyflie != null) {
            if ("ring.headlightEnable".equalsIgnoreCase(action)) {
                // Toggle LED ring headlight
                mHeadlightToggle = !mHeadlightToggle;
                mCrazyflie.setParamValue(action, mHeadlightToggle ? 1 : 0);
                //mHeadlightButton.setColorFilter(mHeadlightToggle ? Color.parseColor("#00FF00") : Color.BLACK);
            } else if ("ring.effect".equalsIgnoreCase(action)) {
                // Cycle through LED ring effects
                Log.i(LOG_TAG, "Ring effect: " + mRingEffect);
                mCrazyflie.setParamValue(action, mRingEffect);
                mRingEffect++;
                mRingEffect = (mRingEffect > mNoRingEffect) ? 0 : mRingEffect;
            } else if (action.startsWith("sound.effect")) {
                // Toggle buzzer deck sound effect
                String[] split = action.split(":");
                Log.i(LOG_TAG, "Sound effect: " + split[1]);
                mCrazyflie.setParamValue(split[0], mSoundToggle ? Integer.parseInt(split[1]) : 0);
                mSoundToggle = !mSoundToggle;
            }
        } else {
            Log.d(LOG_TAG, "runAltAction - crazyflie is null");
        }
    }

    public void enableAltHoldMode(boolean hover) {
        Log.d(LOG_TAG, "Alt Hold Mode: " + hover);
        mCrazyflie.setParamValue("flightmode.althold", hover ? 1 : 0);
    }

    public Crazyflie getCrazyflie(){
        return mCrazyflie;
    }

    public void disconnect() {
        Log.d(LOG_TAG, "disconnect()");
        if (mCrazyflie != null) {
            mCrazyflie.disconnect();
            mCrazyflie = null;
        }

        if (mDriver != null) {
            mDriver.removeConnectionListener(crazyflieConnectionAdapter);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // link quality is not available when there is no active connection
                setLinkQualityText("N/A");
            }
        });
    }

    public IController getController(){
        return null;
    }

    public static boolean isCrazyradioAvailable(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            throw new IllegalArgumentException("UsbManager == null!");
        }
        List<UsbDevice> usbDeviceList = UsbLinkAndroid.findUsbDevices(usbManager, (short) Crazyradio.CRADIO_VID, (short) Crazyradio.CRADIO_PID);
        return !usbDeviceList.isEmpty();
    }

    // THOMAS: The log reader?
    private LogAdapter standardLogAdapter = new LogAdapter() {

        public void logDataReceived(LogConfig logConfig, Map<String, Number> data, int timestamp) {
            super.logDataReceived(logConfig, data, timestamp);
            Log.d(LOG_TAG, "Log recieved: " + logConfig.getName());
            if ("Standard".equals(logConfig.getName())) {
                final float battery = data.get("pm.vbat").floatValue();
                final int deltaX = data.get("motion.deltaX").intValue();
                final int deltaY = data.get("motion.deltaY").intValue();
                final int zrange = data.get("range.zrange").intValue();
                final float stateX = data.get("kalman.stateX").floatValue();
                final float stateY = data.get("kalman.stateY").floatValue();
                final float stateZ = data.get("kalman.stateZ").floatValue();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setBatteryLevel(battery);
                        Log.d(LOG_TAG, "\t deltaX: " + deltaX);
                        Log.d(LOG_TAG, "\t deltaY: " + deltaY);
                        Log.d(LOG_TAG, "\t stateX: " + stateX);
                        Log.d(LOG_TAG, "\t stateY: " + stateY);
                        Log.d(LOG_TAG, "\t stateZ: " + stateZ);
                    }
                });
            }
            for (Entry<String, Number> entry : data.entrySet()) {
                Log.d(LOG_TAG, "\t Name: " + entry.getKey() + ", data: " + entry.getValue());
            }
        }

    };

    private void createLogConfigs() {
        mLogConfigStandard.addVariable("pm.vbat", VariableType.FLOAT);
        mLogConfigStandard.addVariable("motion.deltaX", VariableType.INT16_T);
        mLogConfigStandard.addVariable("motion.deltaY", VariableType.INT16_T);
        mLogConfigStandard.addVariable("kalman.stateX", VariableType.FLOAT);
        mLogConfigStandard.addVariable("kalman.stateY", VariableType.FLOAT);
        mLogConfigStandard.addVariable("kalman.stateZ", VariableType.FLOAT);
        mLogg = mCrazyflie.getLogg();

        if (mLogg != null) {
            mLogg.addConfig(mLogConfigStandard);
            mLogg.addLogListener(standardLogAdapter);
        } else {
            Log.e(LOG_TAG, "Logg was null!!");
        }
    }

    /**
     * Start basic logging config
     */
    private void startLogConfigs() {
        if (mLogg != null) {
            mLogg.start(mLogConfigStandard);
        }
    }

    /**
     * Stop basic logging config
     */
    private void stopLogConfigs() {
        if (mLogg != null) {
            mLogg.stop(mLogConfigStandard);
            mLogg.delete(mLogConfigStandard);
            mLogg.removeLogListener(standardLogAdapter);
        }
    }

    public void setBatteryLevel(float battery) {
        float normalizedBattery = battery - 3.0f;
        int batteryPercentage = (int) (normalizedBattery * 100);
        if (battery == -1f) {
            batteryPercentage = 0;
        } else if (normalizedBattery < 0f && normalizedBattery > -1f) {
            batteryPercentage = 0;
        } else if (normalizedBattery > 1f) {
            batteryPercentage = 100;
        }
        mTextView_battery.setText(format(R.string.battery_text, batteryPercentage));
    }

    public void setLinkQualityText(String quality){
        mTextView_linkQuality.setText(format(R.string.linkQuality_text, quality));
    }

    private String format(int identifier, Object o){
        return String.format(getResources().getString(identifier), o);
    }
}
