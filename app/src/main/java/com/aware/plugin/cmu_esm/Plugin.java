package com.aware.plugin.cmu_esm;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Applications_Provider;
import com.aware.providers.Scheduler_Provider;
import com.aware.ui.PermissionsHandler;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.Scheduler;

import org.json.JSONException;

public class Plugin extends Aware_Plugin {

    private static final String ACTION_AWARE_CAMPUS_QUESTION_UPDATE = "ACTION_AWARE_CAMPUS_QUESTION_UPDATE";

    public static final String SHARED_PREF_NAME = "CMU_ESM_SHARED_PREF";
    public static final String SHARED_PREF_KEY_VERSION_CODE = "SHARED_PREF_KEY_VERSION_CODE";
    public static final String SHARED_PREF_KEY_STORED_SCHEDULE_IDS = "STORED_SCHEDULE_IDS";

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE::"+getResources().getString(R.string.app_name);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        //Add permissions you need (Support for Android M). By default, AWARE asks access to the #Manifest.permission.WRITE_EXTERNAL_STORAGE
        //REQUIRED_PERMISSIONS.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        REQUIRED_PERMISSIONS.add(Manifest.permission.VIBRATE);

        //To sync data to the server, you'll need to set this variables from your ContentProvider
//        DATABASE_TABLES = Provider.DATABASE_TABLES;
//        TABLES_FIELDS = Provider.TABLES_FIELDS;
//        CONTEXT_URIS = new Uri[]{ Provider.TableOne_Data.CONTENT_URI }; //this syncs dummy TableOne_Data to server

        IntentFilter filter = new IntentFilter(ACTION_AWARE_CAMPUS_QUESTION_UPDATE);
        registerReceiver(questionsListener, filter);

        //Activate plugin -- do this ALWAYS as the last thing (this will restart your own plugin and apply the settings)
        Aware.startPlugin(this, "com.aware.plugin.cmu_esm");
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        boolean permissions_ok = true;
        for (String p : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                permissions_ok = false;
                break;
            }
        }

        if (permissions_ok) {
            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_CMU_ESM, true);

            SharedPreferences sp = getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE);
            int previousVersionCode = sp.getInt(SHARED_PREF_KEY_VERSION_CODE, -1);

            if (previousVersionCode == -1){
                int versionCode = recordFirstOperationInDatabase();
                sp.edit().putInt(SHARED_PREF_KEY_VERSION_CODE, versionCode).commit();
            }else{
                try{
                    PackageInfo pInfo = getApplicationContext().getPackageManager().getPackageInfo(getPackageName(), 0);
                    int currentVersionCode = pInfo.versionCode;

                    if (currentVersionCode > previousVersionCode){
                        int versionCode = recordFirstOperationInDatabase();
                        sp.edit().putInt(SHARED_PREF_KEY_VERSION_CODE, versionCode).commit();
                    }
                }catch (PackageManager.NameNotFoundException e){
                    if(Aware.DEBUG) Log.e(TAG,e.getMessage());
                }
            }

            try {
                Scheduler.Schedule questionSchedule = Scheduler.getSchedule(getApplicationContext(), "question_updater");
                if (questionSchedule == null) {
                    //Schedule fetching the questions from server every day at 2-4 AM
                    questionSchedule = new Scheduler.Schedule("question_updater");
                    questionSchedule.addHour(2);
                    questionSchedule.addHour(3);
                    questionSchedule.addHour(4);
                    questionSchedule.setActionType(Scheduler.ACTION_TYPE_BROADCAST);
                    questionSchedule.setActionClass(ACTION_AWARE_CAMPUS_QUESTION_UPDATE);

                    Scheduler.saveSchedule(this, questionSchedule);
                }
            } catch (JSONException e) {}

            //Check if we already have schedules downloaded, if not, try again every 5 minutes until we do
            Cursor schedules = getContentResolver().query(Scheduler_Provider.Scheduler_Data.CONTENT_URI, null, Scheduler_Provider.Scheduler_Data.PACKAGE_NAME + " LIKE 'com.aware.plugin.cmu_esm'", null, null);
            if( schedules != null && schedules.getCount() <= 1 ) {
                Intent scheduleDownload = new Intent(this, QuestionUpdater.class);
                startService(scheduleDownload);
            }
            if( schedules != null && ! schedules.isClosed() ) schedules.close();

        } else {
            Intent permissions = new Intent(this, PermissionsHandler.class);
            permissions.putExtra(PermissionsHandler.EXTRA_REQUIRED_PERMISSIONS, REQUIRED_PERMISSIONS);
            permissions.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(permissions);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    private static final FetchQuestions questionsListener = new FetchQuestions();
    public static class FetchQuestions extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(ACTION_AWARE_CAMPUS_QUESTION_UPDATE) ) {
                Intent scheduleDownload = new Intent(context, QuestionUpdater.class);
                context.startService(scheduleDownload);
            }
        }
    }

    private int recordFirstOperationInDatabase(){
        String FLAG_PLUGIN_UPDATE = "[PLUGIN UPDATE INSTALL]";
        int versionCode = 0;
        try{
            PackageInfo pInfo = getApplicationContext().getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            versionCode = pInfo.versionCode;
            //As a temporary solution, we will record plugin update status in the application history database
            ContentValues rowData = new ContentValues();
            rowData.put(Applications_Provider.Applications_History.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Applications_Provider.Applications_History.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Applications_Provider.Applications_History.PACKAGE_NAME, getPackageName());
            rowData.put(Applications_Provider.Applications_History.APPLICATION_NAME, FLAG_PLUGIN_UPDATE + "CMU ESM" + ", VersionName:" + versionName +", VersionCode:" + versionCode);
            rowData.put(Applications_Provider.Applications_History.PROCESS_IMPORTANCE, 0);
            rowData.put(Applications_Provider.Applications_History.PROCESS_ID, 0);
            rowData.put(Applications_Provider.Applications_History.END_TIMESTAMP, pInfo.firstInstallTime); //Installation Time;
            rowData.put(Applications_Provider.Applications_History.IS_SYSTEM_APP, 0);
            try {
                getContentResolver().insert(Applications_Provider.Applications_History.CONTENT_URI, rowData);
            }catch( SQLiteException e ) {
                if(Aware.DEBUG) Log.e(TAG, e.getMessage());
            }catch( SQLException e ) {
                if(Aware.DEBUG) Log.e(TAG,e.getMessage());
            }
        }catch (PackageManager.NameNotFoundException e){
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
        }catch (Exception e){
            if(Aware.DEBUG) Log.e(TAG,e.getMessage());
        }

        return versionCode;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(questionsListener);
        Aware.setSetting(this, Settings.STATUS_PLUGIN_CMU_ESM, false);
        Aware.stopPlugin(this, "com.aware.plugin.cmu_esm");

        //Stop AWARE
        Aware.stopAWARE();
    }
}
