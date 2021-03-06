package com.rabidllamastudios.avigate.helpers;

import android.os.Build;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Set;

/**
 * This class is identical to a JSONObject, except it can convert between JSON and Bundle objects
 * Created by Ryan Staatz on 11/12/15.
 */
public class BundleableJsonObject extends JSONObject {

    /** Identical constructor to JSONObject */
    public BundleableJsonObject(String json) throws JSONException {
        super(json);
    }

    /** Constructor that takes a bundle. Use toBundle() method to export to a Bundle. */
    public BundleableJsonObject(Bundle bundle) {
        Set<String> keys = bundle.keySet();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            for (String key : keys) {
                try {
                    this.put(key, JSONObject.wrap(bundle.get(key)));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else {
            for (String key : keys) {
                try {
                    this.put(key, bundle.get(key));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Converts the BundleableJsonObject to a Bundle */
    public Bundle toBundle() {
        //TODO find less awful method implementation if there is one
        Bundle bundle = new Bundle();
        Iterator<String> keys = this.keys();
        while (keys.hasNext()) {
            try {
                String key = keys.next();
                Object value = this.get(key);
                if (value instanceof String)
                    bundle.putString(key, (String) value);
                else if (value instanceof Boolean)
                    bundle.putBoolean(key, (Boolean) value);
                else if (value instanceof Integer)
                    bundle.putInt(key, (Integer) value);
                else if (value instanceof Long)
                    bundle.putLong(key, (Long) value);
                else if (value instanceof Float)
                    bundle.putFloat(key, (Float) value);
                else if (value instanceof Double)
                    bundle.putDouble(key, (Double) value);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return bundle;
    }

}