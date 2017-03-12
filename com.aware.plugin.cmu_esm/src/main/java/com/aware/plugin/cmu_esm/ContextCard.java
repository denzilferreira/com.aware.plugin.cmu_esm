package com.aware.plugin.cmu_esm;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aware.Aware;
import com.aware.providers.ESM_Provider;
import com.aware.utils.IContextCard;

import java.util.Timer;
import java.util.TimerTask;

public class ContextCard implements IContextCard {

    TextView counter_txt;

    //Constructor used to instantiate this card
    public ContextCard() {
    }

    @Override
    public View getContextCard(Context context) {
        //Load card information to memory
        LayoutInflater sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = sInflater.inflate(R.layout.card, null);

        //Initialize UI elements from the card
        counter_txt = (TextView) card.findViewById(R.id.counter);

        Cursor esmsDistribution = context.getContentResolver().query(ESM_Provider.ESM_Data.CONTENT_URI, new String[]{ESM_Provider.ESM_Data.STATUS, "count(*) as total_esms"}, "1 ) GROUP BY ( esm_status ", null, null);
        Log.d(Aware.TAG, DatabaseUtils.dumpCursorToString(esmsDistribution));

        String output = "";
        //the status of the ESM (0-new, 1-dismissed, 2-answered, 3-expired)
        if (esmsDistribution != null && esmsDistribution.moveToFirst()) {
            do {
                int status = esmsDistribution.getInt(0);
                int total = esmsDistribution.getInt(1);

                switch (status) {
                    case 0:
                        output += " New: " + total;
                        break;
                    case 1:
                        output += " Dismissed: " + total;
                        break;
                    case 2:
                        output += " Answered: " + total;
                        break;
                    case 3:
                        output += " Expired: " + total;
                        break;
                }
            } while (esmsDistribution.moveToNext());
        }
        if (esmsDistribution != null && ! esmsDistribution.isClosed()) esmsDistribution.close();

        counter_txt.setText(output);

        //Return the card to AWARE/apps
        return card;
    }
}
