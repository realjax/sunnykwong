package com.sunnykwong.omc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.text.format.Time;
import android.util.Log;

public class OMCAlarmReceiver extends BroadcastReceiver {
	
	@Override
	public void onReceive(Context context, Intent intent) {
		// Set the alarm for next tick first, so we don't lose sync
		OMC.setServiceAlarm((System.currentTimeMillis() - System.currentTimeMillis()%OMC.UPDATEFREQ + OMC.UPDATEFREQ + OMC.UPDATEFREQ ) - OMC.LEASTLAGMILLIS);

		if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Alarm","Rcvd " + intent.toString());
		
		
		// If we come back from a low memory state, all sorts of screwy stuff might happen.
		// If the Intent itself is null, let's create one.
		if (intent == null) {
			OMC.FG=true;
			OMC.SCREENON=true;
			OMC.SVCSTARTINTENT.setAction("com.sunnykwong.omc.FGSERVICE");
		}

		final String action = intent.getAction();

		// the Intent action might be blank.
		// In that case, we take an educated guess and say it's a foreground situation.
		if (action==null) {
			OMC.FG=true;
			OMC.SCREENON=true;
			OMC.SVCSTARTINTENT.setAction("com.sunnykwong.omc.FGSERVICE");
		}
		// If user taps on notification, cancel FG mode.
		if (action.equals("com.sunnykwong.omc.CANCEL_FG")) {
			OMC.FG=false;
			OMC.SVCSTARTINTENT.setAction("com.sunnykwong.omc.BGSERVICE");
			OMC.LASTRENDEREDTIME.set(((System.currentTimeMillis()+OMC.LEASTLAGMILLIS)/1000l)*1000l);
			context.startService(OMC.SVCSTARTINTENT);
		}
				
		// Battery-related responses.
		// If something about the battery changed, we need to record the changes.
		if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
			if (OMC.DEBUG) Log.i (OMC.OMCSHORT + "Alarm","Batt "+ intent.getIntExtra("level", 0) + "/" +intent.getIntExtra("scale", 10000));
			if (OMC.DEBUG) Log.i (OMC.OMCSHORT + "Alarm","ChargeStatus: "+ action);
			if (OMC.DEBUG) Log.i (OMC.OMCSHORT + "Alarm",""+intent.getIntExtra("plugged", -1));
			String sChargeStatus = "Discharging";
			switch (intent.getIntExtra("plugged", -1)) {
			case BatteryManager.BATTERY_PLUGGED_AC: 
				sChargeStatus="AC Charging";
				break;
			case BatteryManager.BATTERY_PLUGGED_USB:
				sChargeStatus="USB Charging";
				break;
			case -1:
				break;
			default:
				break;
			}
			OMC.PREFS.edit()
				.putInt("ompc_battlevel", intent.getIntExtra("level", 0))
				.putInt("ompc_battscale", intent.getIntExtra("scale", 100))
				.putInt("ompc_battpercent", (int)(100*intent.getIntExtra("level", 0)/(float)intent.getIntExtra("scale", 100)))
				.putString("ompc_chargestatus", sChargeStatus)
				.commit();
			
			// Note that especially when the charge status changes 
			// from plugged to unplugged, we want to update the widget asap.
		}

		// Weather-related responses.
		// If we just set the clock or switched timezones, we definitely want to refresh weather right now.
		if (action.equals(Intent.ACTION_TIME_CHANGED)
				|| action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
			OMC.NEXTWEATHERREFRESH=0l;
			OMC.LASTWEATHERTRY=0l;
		}

		// Otherwise, we can be more polite about updating weather.
		
		if (action.equals(Intent.ACTION_TIME_TICK)) {
			
			// First, are we due for a weather update?
			if (System.currentTimeMillis()>OMC.NEXTWEATHERREFRESH) {
				// If it has been less than 15 minutes after the last weather try, don't try yet
				if (System.currentTimeMillis()-OMC.LASTWEATHERTRY < 15l * 60000l) {
					// do nothing
				} else {
					// Get weather updates
					GoogleWeatherXMLHandler.updateWeather();
				}
			}
		}
		
		if (action.equals(OMC.FGINTENT)) OMC.SVCSTARTINTENT.setAction("com.sunnykwong.omc.FGSERVICE");
		else OMC.SVCSTARTINTENT.setAction("com.sunnykwong.omc.BGSERVICE");
		
		// Do nothing if the screen turns off, but
		// Start working again if the screen turns on.
		// This obviously saves CPU cycles (and battery).
		// Thanks to ralfoide's code at http://code.google.com/p/24clock/ for the idea
		if (action.equals(Intent.ACTION_SCREEN_ON)) {
			OMC.SCREENON=true;
			OMC.LASTUPDATEMILLIS=0l;
			if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Alarm","Scrn on - Refreshing");
		}
	
		if (action.equals(Intent.ACTION_SCREEN_OFF)) {
			OMC.SCREENON=false;
			if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Alarm","Scrn off - not refreshing");
		}
		
		
		// If the screen is on, honor the update frequency.
		if (OMC.SCREENON) {
			if ((action.equals(OMC.FGINTENT.getAction()) || action.equals(OMC.BGINTENT.getAction()) || action.equals(Intent.ACTION_TIME_TICK))) {
				if (((System.currentTimeMillis()+OMC.LEASTLAGMILLIS)/1000l)*1000l==OMC.LASTRENDEREDTIME.toMillis(false)) {
					// Prevent abusive updates - we've already rendered this target time.
					if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Alarm",OMC.LASTRENDEREDTIME.format2445() + " already rendered! Not redrawing clocks again.");
					return;
				}
			}
			OMC.LASTRENDEREDTIME.set(((System.currentTimeMillis()+OMC.LEASTLAGMILLIS)/1000l)*1000l);
			context.startService(OMC.SVCSTARTINTENT);
		// If the screen is off, update bare minimum to mimic foreground mode.
		} else if (action.equals(Intent.ACTION_TIME_TICK)
				||action.equals(Intent.ACTION_TIME_CHANGED)
				||action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
			OMC.LASTRENDEREDTIME.set(((System.currentTimeMillis()+OMC.LEASTLAGMILLIS)/1000l)*1000l);
			context.startService(OMC.SVCSTARTINTENT);
		} else {
			OMC.LASTUPDATEMILLIS = System.currentTimeMillis();
			if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Alarm","I think scrn is off... no refresh");
		}
	}
}
