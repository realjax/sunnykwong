package com.sunnykwong.omwpp;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
public class OMWPPFinishReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (OMWPP.DEBUG) Log.i("OMWPPRcvr","Wallpaper changed.");
		OMWPP.WALLPAPERDONE=true;
	}
}