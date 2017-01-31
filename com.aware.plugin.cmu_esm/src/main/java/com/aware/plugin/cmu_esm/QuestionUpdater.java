package com.aware.plugin.cmu_esm;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.ESM;
import com.aware.utils.Http;
import com.aware.utils.Scheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Created by denzil on 1/8/16. Edited by Jung-wook 1/15/16
 */
public class QuestionUpdater extends IntentService {

    public QuestionUpdater() {
        super("QuestionUpdater");
    }

    private String[] parceScheduleID(SharedPreferences pref){
        String strIds = pref.getString(Plugin.SHARED_PREF_KEY_STORED_SCHEDULE_IDS, "");
        if (strIds.length() > 0){
            return strIds.split(",");
        }else{
            return new String[]{};
        }
    }

    private void insertScheduleID(SharedPreferences pref, String[] ids){
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            sb.append(ids[i]).append(",");
        }
        //remove last comma
        sb.deleteCharAt(sb.length() - 1);
        //Log.e("", "Insert to the database:" + sb.toString());
        pref.edit().putString(Plugin.SHARED_PREF_KEY_STORED_SCHEDULE_IDS, sb.toString()).commit();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        /**
         * URL for device ID
         */
        String deviceId = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
        String esmURL = "http://r2d2.hcii.cs.cmu.edu/esm/"+deviceId+"/master.json";

        try {
            String schedules = new Http(getApplicationContext()).dataGET(esmURL, false);

            if( Aware.DEBUG ) {
                Log.d(Plugin.TAG, "Request URL:" + esmURL);
                Log.d(Plugin.TAG, "Received Data:" + schedules);
            }

            if (schedules != null && schedules.length() > 0){
                JSONArray schedulesJSON = new JSONArray(schedules);

                //Remove Existing ESM Schedules
                SharedPreferences sharedPreferences = getSharedPreferences(Plugin.SHARED_PREF_NAME, Context.MODE_PRIVATE);
                String configuredScheduleIds = sharedPreferences.getString(Plugin.SHARED_PREF_KEY_STORED_SCHEDULE_IDS, "");
                if (configuredScheduleIds.length() > 0){
                    String[] schedulesIDs = parceScheduleID(sharedPreferences);
                    for (int i = 0; i < schedulesIDs.length; i++){
                        Scheduler.removeSchedule(this, schedulesIDs[i]);
                        Log.e("", "Remove Existing Schedule:" + schedulesIDs[i]);
                    }
                }

                //Store new schedule ids
                String[] new_schedule_ids = new String[schedulesJSON.length()];
                for (int i=0; i < schedulesJSON.length(); i++){
                    JSONObject todaysSchedule = schedulesJSON.getJSONObject(i);

                    String schedule_id = todaysSchedule.getString("schedule_id");
                    JSONArray hours = todaysSchedule.getJSONArray("hours");
                    JSONArray esms = todaysSchedule.getJSONArray("esms");

                    if( Aware.DEBUG ) Log.d(Plugin.TAG, "ESM hours Size" + hours.length()  + " Message:" + hours.toString());

                    new_schedule_ids[i] = schedule_id;

                    //Set today's questions
                    Scheduler.Schedule campus_schedule = new Scheduler.Schedule(schedule_id);
                    for(int j=0;j<hours.length();j++) {
                        campus_schedule.addHour(hours.getInt(j)); //set trigger hours
                    }
                    campus_schedule.setActionType(Scheduler.ACTION_TYPE_BROADCAST);
                    campus_schedule.setActionIntentAction(ESM.ACTION_AWARE_QUEUE_ESM);
                    campus_schedule.addActionExtra(ESM.EXTRA_ESM, esms.toString());

                    //Update previous schedule_id to this new definition
                    Scheduler.saveSchedule(this, campus_schedule);
                }

                insertScheduleID(sharedPreferences,new_schedule_ids);
            }

        } catch (JSONException e) {
            Log.e(Plugin.TAG, "Json Parsing Error: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.e(Plugin.TAG, "No data in the Url or exceptions except for Json parsing: " + e.getMessage());
        }

    }
}
