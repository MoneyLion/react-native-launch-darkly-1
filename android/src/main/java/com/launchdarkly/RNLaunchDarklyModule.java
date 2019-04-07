
package com.launchdarkly;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.common.collect.Sets;
import com.launchdarkly.android.FeatureFlagChangeListener;
import com.launchdarkly.android.LDClient;
import com.launchdarkly.android.LDConfig;
import com.launchdarkly.android.LDUser;
import com.launchdarkly.android.LaunchDarklyException;

import java.util.HashSet;

public class RNLaunchDarklyModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  private LDClient ldClient;
  private LDUser user;

  public RNLaunchDarklyModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNLaunchDarkly";
  }

  @ReactMethod
  public void configure(String apiKey, ReadableMap options, Promise promise) {
    LDConfig ldConfig = new LDConfig.Builder()
            .setMobileKey(apiKey)
            .build();

    LDUser.Builder userBuilder = new LDUser.Builder(options.getString("key"));

    if (options.hasKey("email")) {
      userBuilder = userBuilder.email(options.getString("email"));
    }

    if (options.hasKey("firstName")) {
      userBuilder = userBuilder.firstName(options.getString("firstName"));
    }

    if (options.hasKey("lastName")) {
      userBuilder = userBuilder.lastName(options.getString("lastName"));
    }

    if (options.hasKey("isAnonymous")) {
      userBuilder = userBuilder.anonymous(options.getBoolean("isAnonymous"));
    }

    HashSet<String> nonCustomFields = Sets.newHashSet("key", "email", "firstName", "lastName", "isAnonymous");

    ReadableMapKeySetIterator iterator = options.keySetIterator();
    while (iterator.hasNextKey()) {
      String key = iterator.nextKey();
      if (!nonCustomFields.contains(key)) {
        if (options.getType(key) == ReadableType.Number) {
          userBuilder = userBuilder.custom(key, options.getDouble(key));
        } else if (options.getType(key) == ReadableType.String) {
          userBuilder = userBuilder.custom(key, options.getString(key));
        }
        Log.d("RNLaunchDarklyModule", "Launch Darkly custom field: " + key);
      }
    }

    if (user != null && ldClient != null) {
      user = userBuilder.build();
      ldClient.identify(user);
      WritableMap map = Arguments.createMap();
      map.putString("email", options.getString("email"));
      promise.resolve(map);
      return;
    }

    user = userBuilder.build();

    Activity reactActivity = reactContext.getCurrentActivity();

    if (reactActivity != null && reactActivity.getApplication() != null) {
      ldClient = LDClient.init(reactActivity.getApplication(), ldConfig, user, 0);
      WritableMap map = Arguments.createMap();
      map.putString("email", options.getString("email"));
      promise.resolve(map);
    } else {
      Log.d("RNLaunchDarklyModule", "Couldn't init RNLaunchDarklyModule cause application was null");
      promise.reject(new Throwable("Couldn't init RNLaunchDarklyModule cause application was null"));
    }
  }

  @ReactMethod
  public void addFeatureFlagChangeListener (String flagName) {
    FeatureFlagChangeListener listener = new FeatureFlagChangeListener() {
      @Override
      public void onFeatureFlagChange(String flagKey) {
        WritableMap result = Arguments.createMap();
        result.putString("flagName", flagKey);

        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("FeatureFlagChanged", result);
      }
    };

    try {
      LDClient.get().registerFeatureFlagListener(flagName, listener);
    } catch (LaunchDarklyException e) {
      Log.d("RNLaunchDarklyModule", e.getMessage());
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void boolVariation(String flagName, Callback callback) {
    if (ldClient == null) {
      callback.invoke(false);
      return;
    }
    Boolean variationResult = ldClient.boolVariation(flagName, false);
    callback.invoke(variationResult);
  }

  @ReactMethod
  public void stringVariation(String flagName, String fallback, Callback callback) {
    if (ldClient == null) {
      callback.invoke("");
      return;
    }
    String variationResult = ldClient.stringVariation(flagName, fallback);
    callback.invoke(variationResult);
  }
}
