package nova.zendrive;

import com.google.gson.Gson;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.content.Context;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.Intent;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zendrive.sdk.*;


public class NRCore extends CordovaPlugin {

  static final String ZENDRIVE_SDK_KEY = "1moSkgjnEDSQbqjTaA5VyJRd6SWbvdX0";
//  private final Context context;
//  private final CallbackContext callbackContext;


  public NRCore() {

  }


  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

    if ("setUp".equals(action)) {
      this.setUp(args.getString(0), callbackContext);
    }

    if("isSDKSetup".equals(action)) {
      this.isSDKSetup(callbackContext);
    }

    return true;
  }

  public void setUp(String driverID, final CallbackContext callbackContext){
    Context context = this.cordova.getActivity().getApplicationContext();

    Toast toast = Toast.makeText(context, driverID, Toast.LENGTH_SHORT);

    ZendriveConfiguration zendriveConfiguration = new ZendriveConfiguration(ZENDRIVE_SDK_KEY, driverID);
    Zendrive.setup(
            context,
            zendriveConfiguration,
            ZendriveBroadcastReceiver.class,
            ZendriveNotificationProvider.class,
            new ZendriveOperationCallback() {
              @Override
              public void onCompletion(ZendriveOperationResult result) {
                if (result.isSuccess()) {
                  callbackContext.success("zendrive is running...!");
                  toast.show();
                }else {
                  callbackContext.error("zendrive is not running...!");
                }
              }
            }
    );
  }


  public void isSDKSetup(final CallbackContext callbackContext) {
    Boolean response = Zendrive.isSDKSetup(this.cordova.getActivity().getApplicationContext());

    callbackContext.success(response ? "The sdk is running" : "The sdk is not running");
  }

   public void onDriveStart(DriveStartInfo driveStartInfo) {
     LocalBroadcastManager.getInstance(this.cordova.getActivity().getApplicationContext())
             .sendBroadcast(new Intent(Constants.REFRESH_UI));
   }

   public void onDriveEnd(DriveInfo driveInfo) {
     TripListDetails tripListDetails = loadTripDetails();
     tripListDetails.addOrUpdateTrip(driveInfo);
     saveTripDetails(tripListDetails);
     Intent intent = new Intent(Constants.REFRESH_UI);
     intent.putExtra(Constants.DRIVE_DISTANCE, driveInfo.distanceMeters);
     LocalBroadcastManager.getInstance(this.cordova.getActivity().getApplicationContext()).sendBroadcast(intent);
   }

   private TripListDetails loadTripDetails() {
     String tripDetailsJsonString = SharedPreferenceManager.getStringPreference(
             this.cordova.getActivity().getApplicationContext(),
             SharedPreferenceManager.TRIP_DETAILS_KEY, null);
     if (null == tripDetailsJsonString) {
       return new TripListDetails();
     }
     return new Gson().fromJson(tripDetailsJsonString, TripListDetails.class);
   }

   private void saveTripDetails(TripListDetails tripListDetails) {
     String tripListDetailsJsonString = new Gson().toJson(tripListDetails);
     SharedPreferenceManager.setPreference(this.cordova.getActivity().getApplicationContext(),
             SharedPreferenceManager.TRIP_DETAILS_KEY,
             tripListDetailsJsonString);
   }

    public void onDriveResume(DriveResumeInfo driveInfo, final CallbackContext callbackContext) {
      Context context = this.cordova.getActivity().getApplicationContext();
      LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Constants.REFRESH_UI));
      callbackContext.success("onDriveResume events trigger");
    }

    public void onDriveAnalyzed(DriveInfo driveInfo, final CallbackContext callbackContext) {
      Context context = this.cordova.getActivity().getApplicationContext();
      TripListDetails tripListDetails = loadTripDetails();
      tripListDetails.addOrUpdateTrip(driveInfo);
      saveTripDetails(tripListDetails);
      Intent intent = new Intent(Constants.REFRESH_UI);
      LocalBroadcastManager.getInstance(context).sendBroadcast(intent);

      callbackContext.success("onDriveAnalyzed events trigger");
    }

    public void checkZendriveSettings() {

         Context context = this.cordova.getActivity().getApplicationContext();

        NotificationUtility.cancelErrorAndWarningNotifications(context);
        Zendrive.getZendriveSettings(context, zendriveSettings -> {
            if (zendriveSettings == null) {

                return;
            }

            NotificationManager notificationManager =
                    (NotificationManager) context.
                            getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                return;
            }

            List<ZendriveIssueType> deniedPermissions = new ArrayList<>();
            for (ZendriveSettingError error : zendriveSettings.errors) {
                switch (error.type) {
                    case POWER_SAVER_MODE_ENABLED: {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            notificationManager.notify(NotificationUtility.
                                            PSM_ENABLED_NOTIFICATION_ID,
                                    NotificationUtility.
                                            createPSMEnabledNotification(context, true));
                        } else {
                            throw new RuntimeException("Power saver mode " +
                                    "error on OS version < Lollipop.");
                        }
                        break;
                    }
                    case BACKGROUND_RESTRICTION_ENABLED: {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            notificationManager.notify(NotificationUtility.
                                            BACKGROUND_RESTRICTION_NOTIFICATION_ID,
                                    NotificationUtility.
                                            createBackgroundRestrictedNotification(context));
                        } else {
                            throw new RuntimeException("Background restricted " +
                                    "callback on OS version < P.");
                        }
                        break;
                    }
                    case GOOGLE_PLAY_SETTINGS_ERROR: {
                        GooglePlaySettingsError e = (GooglePlaySettingsError) error;
                        Notification notification =
                                NotificationUtility.
                                        createGooglePlaySettingsNotification(context,
                                                e.googlePlaySettingsResult);
                        if (notification != null) {
                            notificationManager.
                                    notify(NotificationUtility.
                                            GOOGLE_PLAY_SETTINGS_NOTIFICATION_ID, notification);
                        }
                        break;
                    }
                    case LOCATION_PERMISSION_DENIED: {
                        deniedPermissions.add(ZendriveIssueType.LOCATION_PERMISSION_DENIED);
                        break;
                    }
                    case BATTERY_OPTIMIZATION_ENABLED: {
                        Notification batteryOptNotification = NotificationUtility.
                                getBatteryOptimizationEnabledNotification(context);
                        notificationManager.notify(NotificationUtility.
                                        BATTERY_OPTIMIZATION_NOTIFICATION_ID,
                                batteryOptNotification);
                        break;
                    }
                    case ONE_PLUS_DEEP_OPTIMIZATION: {
                        ZendriveResolvableError e = (ZendriveResolvableError) error;
                        Notification onePlusOptNotification = NotificationUtility.
                                getOnePlusDeepOptimizationEnabledNotification(context,
                                        e.navigableIntent);
                        notificationManager.notify(NotificationUtility.
                                ONE_PLUS_DEEP_OPTIMIZATION_NOTIFICATION_ID, onePlusOptNotification);
                        break;
                    }
                    case ACTIVITY_RECOGNITION_PERMISSION_DENIED: {
                        deniedPermissions.add(ZendriveIssueType.
                                ACTIVITY_RECOGNITION_PERMISSION_DENIED);
                        break;
                    }
                    case OVERLAY_PERMISSION_DENIED: {
                        notificationManager.notify(NotificationUtility.
                                OVERLAY_PERMISSION_DENIED_NOTIFICATION_ID, NotificationUtility.
                                createOverlayPermissionDeniedNotification(context));
                        break;
                    }
                    case AIRPLANE_MODE_ENABLED: {
                        notificationManager.notify(NotificationUtility.
                                AIRPLANE_MODE_ENABLED_NOTIFICATION_ID,
                                NotificationUtility.createAirplaneModeNotification(context));
                        break;
                    }
                }
            }

            for (ZendriveSettingWarning warning : zendriveSettings.warnings) {
                switch (warning.type) {
                    case POWER_SAVER_MODE_ENABLED: {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                            notificationManager.notify(NotificationUtility.
                                            PSM_ENABLED_NOTIFICATION_ID,
                                    NotificationUtility.createPSMEnabledNotification(context, false));
                        } else {
                            throw new RuntimeException("Power saver mode " +
                                    "warning on OS version < Lollipop.");
                        }
                        break;
                    }
                }
            }
            if (!deniedPermissions.isEmpty()) {
                showPermissionDeniedNotification(context, notificationManager,
                        deniedPermissions);
            }
        });
    }

  public void onAccident(AccidentInfo accidentInfo) {
    if (accidentInfo.confidence == ZendriveAccidentConfidence.INVALID) {
      Log.d(Constants.LOG_TAG_DEBUG, "False Accident detected");
      onFalseAccident();
      return;
    }
    NotificationUtility.showCollisionNotification(
            this.cordova.getActivity().getApplicationContext(),
            accidentInfo);
  }

  public void onFalseAccident() {
    NotificationUtility.removePotentialCollisionNotification(this.cordova.getActivity().getApplicationContext());
  }

  private void showPermissionDeniedNotification(Context context,
                                                NotificationManager notificationManager,
                                                List<ZendriveIssueType> deniedPermission) {

    if (deniedPermission.size() == 1) {
      ZendriveIssueType issueType = deniedPermission.get(0);
      if (issueType == ZendriveIssueType.LOCATION_PERMISSION_DENIED) {
        notificationManager.notify(
                NotificationUtility.LOCATION_PERMISSION_DENIED_NOTIFICATION_ID,
                NotificationUtility.createLocationPermissionDeniedNotification(context));
      } else if (issueType == ZendriveIssueType.ACTIVITY_RECOGNITION_PERMISSION_DENIED) {
        notificationManager.notify(
                NotificationUtility.ACTIVITY_PERMISSION_DENIED_NOTIFICATION_ID,
                NotificationUtility.createActivityPermissionDeniedNotification(context)
        );
      }
    } else {
      ArrayList<String> missingPermissions = new ArrayList<>();
      for (ZendriveIssueType issueType: deniedPermission) {
        if (issueType == ZendriveIssueType.LOCATION_PERMISSION_DENIED) {
          Collections.addAll(missingPermissions, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (issueType == ZendriveIssueType.ACTIVITY_RECOGNITION_PERMISSION_DENIED) {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // SDK should not flag this pre Q
            throw new RuntimeException("Activity permission " +
                    "error on OS version < Q.");
          }
          missingPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }
      }

      notificationManager.cancel(
              NotificationUtility.LOCATION_PERMISSION_DENIED_NOTIFICATION_ID);
      notificationManager.cancel(
              NotificationUtility.ACTIVITY_PERMISSION_DENIED_NOTIFICATION_ID);
      notificationManager.notify(
              NotificationUtility.MULTIPLE_PERMISSION_DENIED_NOTIFICATION_ID,
              NotificationUtility.createMultiplePermissionsDeniedNotification(context,
                      missingPermissions)
      );
    }
  }

  /**
   * Query the Zendrive SDK for errors and warnings that affect its normal operation.
   */
  public void checkZendriveSettings(final Context context) {
    NotificationUtility.cancelErrorAndWarningNotifications(context);
    Zendrive.getZendriveSettings(context, zendriveSettings -> {
      if (zendriveSettings == null) {
        // The callback returns NULL if SDK is not setup.
        return;
      }

      NotificationManager notificationManager =
              (NotificationManager) context.
                      getSystemService(Context.NOTIFICATION_SERVICE);

      if (notificationManager == null) {
        return;
      }

      List<ZendriveIssueType> deniedPermissions = new ArrayList<>();
      for (ZendriveSettingError error : zendriveSettings.errors) {
        switch (error.type) {
          case POWER_SAVER_MODE_ENABLED: {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
              notificationManager.notify(NotificationUtility.
                              PSM_ENABLED_NOTIFICATION_ID,
                      NotificationUtility.
                              createPSMEnabledNotification(context, true));
            } else {
              throw new RuntimeException("Power saver mode " +
                      "error on OS version < Lollipop.");
            }
            break;
          }
          case BACKGROUND_RESTRICTION_ENABLED: {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
              notificationManager.notify(NotificationUtility.
                              BACKGROUND_RESTRICTION_NOTIFICATION_ID,
                      NotificationUtility.
                              createBackgroundRestrictedNotification(context));
            } else {
              throw new RuntimeException("Background restricted " +
                      "callback on OS version < P.");
            }
            break;
          }
          case GOOGLE_PLAY_SETTINGS_ERROR: {
            GooglePlaySettingsError e = (GooglePlaySettingsError) error;
            Notification notification =
                    NotificationUtility.
                            createGooglePlaySettingsNotification(context,
                                    e.googlePlaySettingsResult);
            if (notification != null) {
              notificationManager.
                      notify(NotificationUtility.
                              GOOGLE_PLAY_SETTINGS_NOTIFICATION_ID, notification);
            }
            break;
          }
          case LOCATION_PERMISSION_DENIED: {
            deniedPermissions.add(ZendriveIssueType.LOCATION_PERMISSION_DENIED);
            break;
          }
          case BATTERY_OPTIMIZATION_ENABLED: {
            Notification batteryOptNotification = NotificationUtility.
                    getBatteryOptimizationEnabledNotification(context);
            notificationManager.notify(NotificationUtility.
                            BATTERY_OPTIMIZATION_NOTIFICATION_ID,
                    batteryOptNotification);
            break;
          }
          case ONE_PLUS_DEEP_OPTIMIZATION: {
            ZendriveResolvableError e = (ZendriveResolvableError) error;
            Notification onePlusOptNotification = NotificationUtility.
                    getOnePlusDeepOptimizationEnabledNotification(context,
                            e.navigableIntent);
            notificationManager.notify(NotificationUtility.
                    ONE_PLUS_DEEP_OPTIMIZATION_NOTIFICATION_ID, onePlusOptNotification);
            break;
          }
          case ACTIVITY_RECOGNITION_PERMISSION_DENIED: {
            deniedPermissions.add(ZendriveIssueType.
                    ACTIVITY_RECOGNITION_PERMISSION_DENIED);
            break;
          }
          case OVERLAY_PERMISSION_DENIED: {
            notificationManager.notify(NotificationUtility.
                    OVERLAY_PERMISSION_DENIED_NOTIFICATION_ID, NotificationUtility.
                    createOverlayPermissionDeniedNotification(context));
            break;
          }
          case AIRPLANE_MODE_ENABLED: {
            notificationManager.notify(NotificationUtility.
                            AIRPLANE_MODE_ENABLED_NOTIFICATION_ID,
                    NotificationUtility.createAirplaneModeNotification(context));
            break;
          }
        }
      }

      for (ZendriveSettingWarning warning : zendriveSettings.warnings) {
        switch (warning.type) {
          case POWER_SAVER_MODE_ENABLED: {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
              notificationManager.notify(NotificationUtility.
                              PSM_ENABLED_NOTIFICATION_ID,
                      NotificationUtility.createPSMEnabledNotification(context, false));
            } else {
              throw new RuntimeException("Power saver mode " +
                      "warning on OS version < Lollipop.");
            }
            break;
          }
        }
      }
      if (!deniedPermissions.isEmpty()) {
        showPermissionDeniedNotification(context, notificationManager,
                deniedPermissions);
      }
    });
  }

}
