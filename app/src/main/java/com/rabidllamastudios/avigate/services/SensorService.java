package com.rabidllamastudios.avigate.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.rabidllamastudios.avigate.AvigateApplication;
import com.rabidllamastudios.avigate.models.CraftStatePacket;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SensorService extends Service implements SensorEventListener {

    private static final String CLASS_NAME = SensorService.class.getSimpleName();
    private static final String PACKAGE_NAME = AvigateApplication.class.getPackage().getName();

    public static final String BROADCAST_RATE = PACKAGE_NAME + ".extra.SENSOR_BROADCAST_RATE";

    //Default sensor data broadcast rate in milliseconds (ms)
    private static final int DEFAULT_BROADCAST_RATE = 100;
    private static final int SENSOR_RATE = SensorManager.SENSOR_DELAY_UI;

    //Sensor broadcast rate in milliseconds (ms)
    private int mBroadcastRate = DEFAULT_BROADCAST_RATE;

    private CraftStatePacket craftStatePacket = null;
    private CraftStatePacket.AngularVelocity mAngularVelocity = null;
    private CraftStatePacket.BarometricPressure mBarometricPressure = null;
    private CraftStatePacket.LinearAcceleration mLinearAcceleration = null;
    private CraftStatePacket.MagneticField mMagneticField = null;
    private CraftStatePacket.Orientation mOrientation = null;
    private Location mLocation = null;

    private LocationListener mLocationListener;
    private LocationManager mLocationManager;
    private ScheduledExecutorService mScheduleBroadcastExecutor;
    private SensorManager mSensorManager;

    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mRotationVector;
    private Sensor mCompass;
    private Sensor mBarometer;

    public SensorService() {}

    public static Intent getConfiguredIntent(Context context, int broadcastRate) {
        Intent intent = new Intent(context, SensorService.class);
        intent.putExtra(BROADCAST_RATE, broadcastRate);
        return intent;
    }

    @Override
    public void onCreate() {
        //Initialize mScheduledBroadcastExecutor
        mScheduleBroadcastExecutor = Executors.newSingleThreadScheduledExecutor();

        //Initialize mSensorManager and associated sensors
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mRotationVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mCompass = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mBarometer = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        //Initialize LocationManager and LocationListener
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        mLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                mLocation = new Location(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            //Set sensor refresh rate and start sensors
            mSensorManager.registerListener(this, mAccelerometer, SENSOR_RATE);
            mSensorManager.registerListener(this, mGyroscope, SENSOR_RATE);
            mSensorManager.registerListener(this, mRotationVector, SENSOR_RATE);
            mSensorManager.registerListener(this, mCompass, SENSOR_RATE);
            mSensorManager.registerListener(this, mBarometer, SENSOR_RATE);

            //Start GPS using fastest rate (0)
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                    mLocationListener);
            if (intent.hasExtra(BROADCAST_RATE)) {
                mBroadcastRate = intent.getIntExtra(BROADCAST_RATE, DEFAULT_BROADCAST_RATE);
            }
            mScheduleBroadcastExecutor.scheduleAtFixedRate(new SensorDataBroadcaster(),
                    mBroadcastRate, mBroadcastRate, TimeUnit.MILLISECONDS);
        }
        Log.i(CLASS_NAME, "Service started");
        return START_STICKY;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
        //Store various sensor values to the appropriate inner class of CraftStatePacket
        if (sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            mAngularVelocity = new CraftStatePacket.AngularVelocity(event.values[0],
                    event.values[1], event.values[2]);
        } else if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            mLinearAcceleration = new CraftStatePacket.LinearAcceleration(event.values[0],
                    event.values[1], event.values[2]);
        } else if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mMagneticField = new CraftStatePacket.MagneticField(event.values[0], event.values[1],
                    event.values[2]);
        } else if (sensor.getType() == Sensor.TYPE_PRESSURE) {
            mBarometricPressure = new CraftStatePacket.BarometricPressure(event.values[0]);
        } else if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            mOrientation = new CraftStatePacket.Orientation(event.values[3], event.values[0],
                    event.values[1], event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mSensorManager.unregisterListener(this);
        mLocationManager.removeUpdates(mLocationListener);
        Log.i(CLASS_NAME, "Service stopped");
        super.onDestroy();
    }

    //Packages and broadcasts CraftSTatePacket contained in mThrottledServoValues (an ArduinoPacket)
    private class SensorDataBroadcaster implements Runnable {
        @Override
        public void run() {
            //Only broadcast sensor data if all sensor data is ready
            if (initialSensorDataReady()) {
                //If craftStatePacket has not yet been created, create a new CraftStatePacket
                if (craftStatePacket == null) {
                    craftStatePacket = new CraftStatePacket(mAngularVelocity, mBarometricPressure,
                            mLinearAcceleration, mMagneticField, mOrientation, mLocation);
                //If craftStatePacket is already created, update all sensor-related objects
                } else {
                    craftStatePacket.setAngularVelocity(mAngularVelocity);
                    craftStatePacket.setBarometricPressure(mBarometricPressure);
                    craftStatePacket.setLinearAcceleration(mLinearAcceleration);
                    craftStatePacket.setMagneticField(mMagneticField);
                    craftStatePacket.setOrientation(mOrientation);
                    craftStatePacket.setLocation(mLocation);
                }
                sendBroadcast(craftStatePacket.toIntent());
            }
        }

        //Checks whether all sensor data is ready to be packaged into a new CraftStatePacket
        private boolean initialSensorDataReady() {
            return mAngularVelocity != null && mBarometricPressure != null
                    && mLinearAcceleration != null && mLocation != null && mMagneticField != null
                    && mOrientation != null;
        }
    }
}