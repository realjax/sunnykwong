package com.sunnykwong.omc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.text.format.Time;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class OMCPrefActivity extends PreferenceActivity { 
    /** Called when the activity is first created. */
    static int appWidgetID;
    static AlertDialog mAD;
    AlertDialog mTTL;
	Button[] btnCompass = new Button[9];

    final Time timeTemp = new Time(), timeTemp2 = new Time();
    Handler mHandler;
    CheckBox mCheckBox;
    TextView mTextView;
    Thread mRefresh;
    boolean isInitialConfig=false, mTempFlag=false;
    Preference prefUpdWeatherNow, prefWeather, prefWeatherDisplay, prefWeatherProvider;
    Preference prefloadThemeFile, prefclearCache, prefbSkinner, prefTimeZone;
    Preference prefsUpdateFreq, prefwidgetPersistence, prefemailMe, preftweakTheme;
    int iTTLArea=0;

    final Runnable mUpdatePrefs = new Runnable() {
    	@Override
    	public void run() {								
    		try {
        		String sWSetting = OMC.PREFS.getString("weathersetting", "bylatlong");
    			JSONObject jsonWeather = new JSONObject(OMC.PREFS.getString("weather", "{}"));
    			String sCity = jsonWeather.optString("city","Unknown");
    			timeTemp.set(OMC.NEXTWEATHERREFRESH);
    			timeTemp2.set(OMC.LASTWEATHERTRY);
        		if (sWSetting.equals("bylatlong")) {
        			prefWeather.setTitle(OMC.RString("location") + sCity + OMC.RString("detected"));
        			if (OMC.PREFS.getString("sWeatherFreq", "60").equals("0")) {
        				prefWeather.setSummary(OMC.RString("lastTry")+timeTemp2.format("%R") + OMC.RString("manualRefreshOnly"));
        			} else {
        				prefWeather.setSummary(OMC.RString("lastTry")+timeTemp2.format("%R") + OMC.RString("nextRefresh")+timeTemp.format("%R"));
        			}
        		} else if (sWSetting.equals("specific")) {
        			prefWeather.setTitle("Location: "+OMC.jsonFIXEDLOCN.optString("city","Unknown")+OMC.RString("fixed"));
        			if (OMC.PREFS.getString("sWeatherFreq", "60").equals("0")) {
        				prefWeather.setSummary(OMC.RString("lastTry")+timeTemp2.format("%R") + OMC.RString("manualRefreshOnly"));
        			} else {
        				prefWeather.setSummary(OMC.RString("lastTry")+timeTemp2.format("%R") + OMC.RString("nextRefresh")+timeTemp.format("%R"));
        			}
        		} else {
        			prefWeather.setTitle(OMC.RString("weatherFunctionalityDisabled"));
        			prefWeather.setSummary(OMC.RString("tapToEnable"));
        		} 
    		} catch (JSONException e) {
    			e.printStackTrace();
    			prefWeather.setTitle(OMC.RString("weatherFunctionalityDisabled"));
    			prefWeather.setSummary(OMC.RString("tapToEnable"));
    		}
			return;
    	}
    };
    
    
    @Override
    protected void onNewIntent(Intent intent) {
    	if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Pref","NewIntent");
    	super.onNewIntent(intent);

    	// If action is null, we are coming from an existing widget - 
    	// we want both the home and back buttons to apply changes,
    	// So we set default result to OK.
    	if (getIntent().getAction()==null) {
        	setResult(Activity.RESULT_OK, intent);
        	isInitialConfig=false;
    	} else if (getIntent().getAction().equals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)) {
    		// but if we're from a configure action, that means the widget hasn't been added yet -
    		// the home button must not be used or we'll get zombie widgets.
        	setResult(Activity.RESULT_CANCELED, intent);
        	isInitialConfig=true;
    	}
    	getPreferenceScreen().removeAll();
		if (intent.getData() == null) {
			appWidgetID = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -999);
		} else {
			appWidgetID = Integer.parseInt(intent.getData().getSchemeSpecificPart());
			OMC.PREFS.edit().putBoolean("newbie"+appWidgetID, false).commit();
		}
		setupPrefs(appWidgetID);
		
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
		if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Pref","OnCreate");

    	super.onCreate(savedInstanceState);
    	mHandler = new Handler();

    	// Refresh list of installed Launcher Apps.
		List<ResolveInfo> launcherlist = OMC.PKM.queryIntentActivities(OMC.FINDLAUNCHERINTENT, 0);
		OMC.INSTALLEDLAUNCHERAPPS = new ArrayList<String>();
		OMC.INSTALLEDLAUNCHERAPPS.add("com.teslacoilsw.widgetlocker");
		OMC.INSTALLEDLAUNCHERAPPS.add("com.jiubang.goscreenlock");
		
		for (ResolveInfo info : launcherlist) {
			OMC.INSTALLEDLAUNCHERAPPS.add(info.activityInfo.packageName);
		}

    	
    	// FIX FOR NOMEDIA and BADTHEME
		if (OMC.checkSDPresent()) {
			((OMC)(this.getApplication())).fixnomedia();
			((OMC)(this.getApplication())).fixKnownBadThemes();
		}

    	// If action is null, we are coming from an existing widget - 
    	// we want both the home and back buttons to apply changes,
    	// So we set default result to OK.
    	if (getIntent().getAction()==null) {
        	setResult(Activity.RESULT_OK, getIntent());
        	isInitialConfig=false;
    	} else if (getIntent().getAction().equals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)) {
    		// but if we're from a configure action, that means the widget hasn't been added yet -
    		// the home button must not be used or we'll get zombie widgets.
        	setResult(Activity.RESULT_CANCELED, getIntent());
        	isInitialConfig=true;
    	}
		if (getIntent().getData() == null) {
			appWidgetID = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -999);
		} else {
			appWidgetID = Integer.parseInt(getIntent().getData().getSchemeSpecificPart());
			OMC.PREFS.edit().putBoolean("newbie"+appWidgetID, false).commit();
		}
		setupPrefs(appWidgetID);

    }

    public void setupPrefs(final int appWidgetID) {
		if (appWidgetID >= 0) {
			// We are called by the user tapping on a widget - bring up prefs screen
			if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Pref"," Called by Widget " + appWidgetID);
			if (OMC.SINGLETON) setTitle(OMC.SINGLETONNAME + OMC.RString("dashPreferences"));

			// Load the proper prefs into the generic prefs set
			OMC.getPrefs(appWidgetID);

			// Setting foreground options, and making sure we have at least one widget (4x2) enabled
			Editor ed = OMC.PREFS.edit();
			ed.putBoolean("widgetPersistence", OMC.FG);
			ed.putBoolean("bFourByTwo", true);
        	
			// Depending on free ed or not, enable/disable the widgets
        	if (OMC.FREEEDITION) {
        		ed
        		.putBoolean("bFiveByFour", false)
        		.putBoolean("bFiveByTwo", false)
        		.putBoolean("bFiveByOne", false)
        		.putBoolean("bFourByFour", false)
    			.putBoolean("bFourByOne", false)
    			.putBoolean("bThreeByThree", false)
    			.putBoolean("bThreeByOne", false)
    			.putBoolean("bTwoByTwo", false)
    			.putBoolean("bTwoByOne", false)
    			.putBoolean("bOneByThree", false);
    		}
        	ed.commit();

        	// Load generic prefs into the prefscreen. 
    		this.getPreferenceManager().setSharedPreferencesName(OMC.SHAREDPREFNAME);
        	addPreferencesFromResource(OMC.RXmlId("omcprefs"));

        	// ID specific preferences.
        	// "Set Widget Theme".
        	prefloadThemeFile = findPreference("loadThemeFile");
        	prefloadThemeFile.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
		        	prefloadThemeFile.setSummary(OMC.RString("preselected") + newValue + OMC.RString("postselected"));
					return true;
				}
			});
        	prefloadThemeFile.setSummary(OMC.RString("preselected") + OMC.PREFS.getString("widgetThemeLong", OMC.DEFAULTTHEMELONG) + OMC.RString("postselected"));
        	
    		// "Personalize Clock".
        	preftweakTheme = findPreference("tweakTheme");
        	
        	// "Use 24 Hour Clock".
        	findPreference("widget24HrClock").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if ((Boolean)newValue==true) {
						preference.setSummary(OMC.RString("use24HourTrue"));
					} else {
						preference.setSummary(OMC.RString("use24HourFalse"));
					}
					return true;
				}
			});
			if (OMC.PREFS.getBoolean("widget24HrClock", true)) {
	        	findPreference("widget24HrClock").setSummary(OMC.RString("use24HourTrue"));
			} else {
	        	findPreference("widget24HrClock").setSummary(OMC.RString("use24HourFalse"));
			}
        	
        	// "Show Leading Zero".
        	findPreference("widgetLeadingZero").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					if ((Boolean)newValue==true) {
						preference.setSummary(OMC.RString("showLeadingZTrue"));
					} else {
						preference.setSummary(OMC.RString("showLeadingZFalse"));
					}
					return true;
				}
			});
			if (OMC.PREFS.getBoolean("widgetLeadingZero", true)) {
	        	findPreference("widgetLeadingZero").setSummary(OMC.RString("showLeadingZTrue"));
			} else {
	        	findPreference("widgetLeadingZero").setSummary(OMC.RString("showLeadingZFalse"));
			}
        	
        	// "Change Time Zone".
        	prefTimeZone = findPreference("timeZone");
        	if (OMC.PREFS.getString("sTimeZone", "default").equals("default")) {
        		findPreference("timeZone").setSummary(OMC.RString("followingDeviceTimeZone"));
    		} else {
    			findPreference("timeZone").setSummary(OMC.PREFS.getString("sTimeZone", "default"));
    		}

        	// "Language".
        	Preference prefLocale = findPreference("appLocale"); 
        	prefLocale.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				
				@Override
				public boolean onPreferenceClick(final Preference preference) {
					final CharSequence[] items = OMC.LOCALENAMES;
					new AlertDialog.Builder(OMCPrefActivity.this)
						.setTitle(OMC.RString("changeAppLocale"))
						.setItems(items, new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog, int item) {
									Locale selectedLocale = OMC.LOCALES[item];
									OMC.PREFS.edit()
											.putString("appLocaleName", OMC.LOCALENAMES[item])
											.commit();

									// Determine locale.
									Configuration config = new Configuration();
									config.locale=selectedLocale;
									OMC.RES.updateConfiguration(config, 
											OMC.RES.getDisplayMetrics());
									preference.setSummary(items[item]);
									OMCPrefActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
									OMCPrefActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
									OMCPrefActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
									OMCPrefActivity.this.finish();
								}
						})
						.show();					
					return true;
				}
			});
        	prefLocale.setSummary(OMC.PREFS.getString("appLocaleName", "English (US)"));
        	
        	// "Update Weather Now".
        	prefUpdWeatherNow = findPreference("updweathernow");
        	prefUpdWeatherNow.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
		    		OMC.updateWeather(true);
					return true;
				}
			});
        	
        	// "Location: [Location] (status)".
        	prefWeather = findPreference("weather");

        	// "Weather Provider".
        	prefWeatherProvider = findPreference("weatherProvider");
        	prefWeatherProvider.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
		    		if (newValue.equals("yr")) {
		        		preference.setSummary(OMC.RString("wpyr"));
		    		} else if (newValue.equals("ig")) {
		        		preference.setSummary(OMC.RString("wpig"));
		    		} else if (newValue.equals("7timer")) {
		    			preference.setSummary(OMC.RString("wp7timer"));
		    		} else {
		    			preference.setSummary(OMC.RString("wpowm"));
		    		}
			    	return true;
				}
			});
        	String sWProvider = OMC.PREFS.getString("weatherProvider", "7timer");
    		if (sWProvider.equals("yr")) {
    			prefWeatherProvider.setSummary(OMC.RString("wpyr"));
    		} else if (sWProvider.equals("ig")) {
    			prefWeatherProvider.setSummary(OMC.RString("wpig"));
    		} else if (sWProvider.equals("7timer")) {
    			prefWeatherProvider.setSummary(OMC.RString("wp7timer"));
    		} else {
    			prefWeatherProvider.setSummary(OMC.RString("wpowm"));
    		}

        	
        	// "Weather Display Units".
        	prefWeatherDisplay = findPreference("weatherDisplay");
        	prefWeatherDisplay.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
		    		if (newValue.equals("c")) {
		        		preference.setSummary(OMC.RString("usingCelsius"));
		    		} else {
		        		preference.setSummary(OMC.RString("usingFahrenheit"));
		    		}
			    	return true;
				}
			});
        	if (OMC.PREFS.getString("weatherDisplay", "f").equals("c"))
        		prefWeatherDisplay.setSummary(OMC.RString("usingCelsius"));
        	else prefWeatherDisplay.setSummary(OMC.RString("usingFahrenheit"));

    		// "Clock Update Interval"
        	prefsUpdateFreq = findPreference("sUpdateFreq");
        	prefsUpdateFreq.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
		    		preference.setSummary(OMC.RString("redrawEvery") + (String)newValue + OMC.RString("seconds"));
		    		getApplicationContext().sendBroadcast(OMC.WIDGETREFRESHINTENT);
					return true;
				}
			});
        	prefsUpdateFreq.setSummary(OMC.RString("redrawEvery") + OMC.PREFS.getString("sUpdateFreq", "30") + OMC.RString("seconds"));

        	// "Weather Update Interval"
        	findPreference("sWeatherFreq").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
		    		int newHrs = Integer.parseInt((String)newValue)/60;
		    		long newMillis = newHrs * 3600000l; 
		        	switch (newHrs) {
		    		case 0:
		    			findPreference("sWeatherFreq").setSummary(OMC.RString("manualWeatherUpdatesOnly"));
		    			OMC.NEXTWEATHERREFRESH = Long.MAX_VALUE;
		    			break;
		    		case 1:
		    			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEveryHour"));
		            	// if the last try was unsuccessful, reset the next weather
		            	if (OMC.LASTWEATHERTRY > OMC.LASTWEATHERREFRESH) 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERTRY + newMillis/4l;
		            	else 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERREFRESH + newMillis;
		    			break;
		    		case 4:
		    			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEvery4Hours"));
		            	// if the last try was unsuccessful, reset the next weather
		            	if (OMC.LASTWEATHERTRY > OMC.LASTWEATHERREFRESH) 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERTRY + newMillis/4l;
		            	else 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERREFRESH + newMillis;
		    			break;
		    		case 8:
		    			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEvery8Hours"));
		            	// if the last try was unsuccessful, reset the next weather
		            	if (OMC.LASTWEATHERTRY > OMC.LASTWEATHERREFRESH) 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERTRY + newMillis/4l;
		            	else 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERREFRESH + newMillis;
		    			break;
		    		default:
		    			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEveryHour"));
		            	// if the last try was unsuccessful, reset the next weather
		            	if (OMC.LASTWEATHERTRY > OMC.LASTWEATHERREFRESH) 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERTRY + newMillis/4l;
		            	else 
		            		OMC.NEXTWEATHERREFRESH = OMC.LASTWEATHERREFRESH + newMillis;
		        	}
		    		return true;
				}
			});
        	switch (Integer.parseInt(OMC.PREFS.getString("sWeatherFreq", "60"))/60) {
        		case 0:
        			findPreference("sWeatherFreq").setSummary(OMC.RString("manualWeatherUpdatesOnly"));
        			break;
        		case 1:
        			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEveryHour"));
        			break;
        		case 4:
        			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEvery4Hours"));
        			break;
        		case 8:
        			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEvery8Hours"));
        			break;
        		default:
        			findPreference("sWeatherFreq").setSummary(OMC.RString("refreshWeatherEveryHour"));
        	}
        	
        	// "Set Foreground Mode".
    		prefwidgetPersistence = findPreference("widgetPersistence");

        	if (Build.VERSION.SDK_INT <  5) {
    			OMC.PREFS.edit().putBoolean("widgetPersistence", false).commit();
				((PreferenceCategory)findPreference("allClocks")).removePreference(prefwidgetPersistence);
			}
				
        	// "Enable Theme Tester".
        	prefbSkinner = findPreference("bSkinner");

        	// "Clear Render Caches".
        	prefclearCache = findPreference("clearCache");

        	// "Weather Diagnostics".
        	Preference prefWeatherDiag = findPreference("weatherDebug");
        	prefWeatherDiag.setOnPreferenceClickListener(new OnPreferenceClickListener() {
				
				@Override
				public boolean onPreferenceClick(Preference preference) {
					(new Thread() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							try {
								// Build weather debug data.
								String sBody = "";
								sBody+="Location:\n";
								sBody+="Lat: " + OMC.LASTKNOWNLOCN.getLatitude()+ "\n";
								sBody+="Lon: " + OMC.LASTKNOWNLOCN.getLongitude()+ "\n";
								sBody+="Reverse Geocode:\n";
								sBody+=GoogleReverseGeocodeService.updateLocation(OMC.LASTKNOWNLOCN)+"\n";
								sBody+="WeatherProvider: " + OMC.PREFS.getString("weatherProvider", "NONE")+ "\n";
								sBody+="Weather:\n";
								sBody+=OMC.PREFS.getString("weather", "Weather JSON Missing!")+"\n";
								Intent it = new Intent(android.content.Intent.ACTION_SEND)
					   					.setType("plain/text")
					   					.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"skwong@consultant.com"})
					   					.putExtra(android.content.Intent.EXTRA_SUBJECT, OMC.APPNAME + " WeatherDebug v" + OMC.THISVERSION)
										.putExtra(android.content.Intent.EXTRA_TEXT, sBody);
								startActivity(Intent.createChooser(it, "Contact Xaffron for issues, help & support."));  
								finish();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}).start();
					return true;
				}
			});
        	
        	// "Contact Xaffron".
        	prefemailMe = findPreference("emailMe");

        	// Version text.
        	if (OMC.FREEEDITION) {
        		findPreference("sVersion").setTitle("Version " + OMC.THISVERSION + " Free");
        		findPreference("sVersion").setSummary("Tap me to get the full version!");
        		findPreference("sVersion").setSelectable(true);
        	} else {
        		findPreference("sVersion").setTitle("Version " + OMC.THISVERSION);
        		findPreference("sVersion").setSummary("Thanks for your support!");
        		findPreference("sVersion").setSelectable(false);
        	}

        	// We really don't need this in the prefs screen since we don't allow users to disable the freeware widget size
    		((PreferenceScreen)findPreference("widgetPrefs")).removePreference(findPreference("bFourByTwo"));

    		
    		// If the app is in singleton mode, don't allow themes!
    		if (OMC.SINGLETON) {
        		((PreferenceCategory)findPreference("thisClock")).removePreference(prefloadThemeFile);
        		((PreferenceCategory)findPreference("allClocks")).removePreference(prefclearCache);
        		((PreferenceScreen)findPreference("widgetPrefs")).removePreference(prefbSkinner);
        	}
    		
    		// If it's free, 
    		if (OMC.FREEEDITION) {
        		findPreference("bFiveByFour").setEnabled(false);
        		findPreference("bFiveByFour").setSelectable(false);
        		findPreference("bFiveByTwo").setEnabled(false);
        		findPreference("bFiveByTwo").setSelectable(false);
        		findPreference("bFiveByOne").setEnabled(false);
        		findPreference("bFiveByOne").setSelectable(false);
        		findPreference("bFourByFour").setEnabled(false);
        		findPreference("bFourByFour").setSelectable(false);
        		findPreference("bFourByOne").setEnabled(false);
        		findPreference("bFourByOne").setSelectable(false);
        		findPreference("bThreeByThree").setEnabled(false);
        		findPreference("bThreeByThree").setSelectable(false);
        		findPreference("bThreeByOne").setEnabled(false);
        		findPreference("bThreeByOne").setSelectable(false);
        		findPreference("bTwoByTwo").setEnabled(false);
        		findPreference("bTwoByTwo").setSelectable(false);
        		findPreference("bTwoByOne").setEnabled(false);
        		findPreference("bTwoByOne").setSelectable(false);
        		findPreference("bOneByThree").setEnabled(false);
        		findPreference("bOneByThree").setSelectable(false);
    		}

    		// This is the free/paid version conflict dialog.
    		String sOtherEd = OMC.FREEEDITION? "com.sunnykwong.omc":"com.sunnykwong.freeomc";
    		try {
    			OMC.PKM.getApplicationInfo(sOtherEd, PackageManager.GET_META_DATA);
    			if (!OMC.FREEEDITION) {
	            	mAD = new AlertDialog.Builder(this)
	        		.setTitle(OMC.RString("warningConflictFree"))
	        		.setMessage(OMC.RString("warningConflictText"))
	        	    .setCancelable(true)
	        	    .setIcon(OMC.RDrawableId(OMC.APPICON))
	        	    .setOnKeyListener(new OnKeyListener() {
	        	    	@Override
						public boolean onKey(DialogInterface arg0, int arg1, android.view.KeyEvent arg2) {
	        	    		dialogCancelled();
	        	    		return true;
	        	    	};
	        	    })
	        	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
	        	    		dialogCancelled();
						}
					})
	        	    .create();
	            	mAD.show();
    			} else {
	            	mAD = new AlertDialog.Builder(this)
	        		.setTitle(OMC.RString("warningConflictPaid"))
	        		.setMessage(OMC.RString("warningConflictText"))
	        	    .setCancelable(true)
	        	    .setIcon(OMC.RDrawableId(OMC.APPICON))
	        	    .setOnKeyListener(new OnKeyListener() {
	        	    	@Override
						public boolean onKey(DialogInterface arg0, int arg1, android.view.KeyEvent arg2) {
	        	    		dialogCancelled();
	        	    		return true;
	        	    	};
	        	    })
	        	    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
	        	    		dialogCancelled();
						}
					})
	        	    .create();
	            	mAD.show();
    			}
    		} catch (NameNotFoundException e) {
    			// If we can't find the conflicting package, we're all good - no need to show warning
    		}

    		mRefresh = (new Thread() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					try {
						while(true) {
							mHandler.post(mUpdatePrefs);
							Thread.sleep(1000l);
						}
					} catch (InterruptedException e) {
						// interrupted; stop gracefully
					}
				}
			});
    		mRefresh.start();
    		
    		// This is the help/FAQ dialog.
    		
    		if (OMC.SHOWHELP) {
    			OMC.FAQS = OMC.RStringArray("faqs");
				LayoutInflater li = LayoutInflater.from(this);
				LinearLayout ll = (LinearLayout)(li.inflate(OMC.RLayoutId("faqdialog"), null));
				mTextView = (TextView)ll.findViewById(OMC.RId("splashtext"));
				mTextView.setAutoLinkMask(Linkify.ALL);
				mTextView.setMinLines(8);
				mTextView.setText(OMC.FAQS[OMC.faqtoshow++]);
				OMC.faqtoshow = OMC.faqtoshow==OMC.FAQS.length?0:OMC.faqtoshow;
				
				mCheckBox = (CheckBox)ll.findViewById(OMC.RId("splashcheck"));
				mCheckBox.setChecked(!OMC.SHOWHELP);
				mCheckBox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
					
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						// TODO Auto-generated method stub
						OMC.SHOWHELP = !isChecked;
					}
				});
	
				((Button)ll.findViewById(OMC.RId("faqOK"))).setOnClickListener(new Button.OnClickListener() {
					
					@Override
					public void onClick(android.view.View v) {
						OMC.PREFS.edit().putBoolean("showhelp", OMC.SHOWHELP).commit();
						mAD.dismiss();
					}
				});
				((Button)ll.findViewById(OMC.RId("faqNeutral"))).setOnClickListener(new Button.OnClickListener() {
					
					@Override
					public void onClick(android.view.View v) {
						mTextView.setText(OMC.FAQS[OMC.faqtoshow++]);
						mTextView.invalidate();
						OMC.faqtoshow = OMC.faqtoshow==OMC.FAQS.length?0:OMC.faqtoshow;
					}
				});;
				
				mAD = new AlertDialog.Builder(this)
				.setTitle(OMC.RString("usefulTip"))
			    .setCancelable(true)
			    .setView(ll)
			    .setOnKeyListener(new OnKeyListener() {
			    	@Override
					public boolean onKey(DialogInterface arg0, int arg1, android.view.KeyEvent arg2) {
			    		if (arg2.getKeyCode()==android.view.KeyEvent.KEYCODE_BACK) mAD.cancel();
			    		return true;
			    	};
			    })
			    .show();
    		}

		} else {
            // If they gave us an intent without the widget id, just bail.
        	if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Pref","Called by Launcher - do nothing");
        	OMCPrefActivity.mAD = new AlertDialog.Builder(this)
        		.setTitle(OMC.RString("thanksForDownloading"))
        		.setMessage(OMC.RString("widgetDir1") + OMC.APPNAME + OMC.RString("widgetDir2"))
        	    .setCancelable(true)
        	    .setIcon(OMC.RDrawableId(OMC.APPICON))
        	    .setOnKeyListener(new OnKeyListener() {
        	    	@Override
					public boolean onKey(DialogInterface arg0, int arg1, android.view.KeyEvent arg2) {
        	    		dialogCancelled();
        	    		return true;
        	    	};
        	    }).setOnCancelListener(new DialogInterface.OnCancelListener() {
					
					@Override
					public void onCancel(DialogInterface dialog) {
						dialogCancelled();
					}
				})
        	    .create();
        	OMCPrefActivity.mAD.setCanceledOnTouchOutside(true);
        	OMCPrefActivity.mAD.show();

        	((OMC)getApplication()).widgetClicks();
        	
        }

    }

    // If user clicks on a preference...
    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
    		Preference preference) {
    	if (preference == findPreference("deleteOMCThemes")){
			final CharSequence[] items = {OMC.RString("yesDelete"), OMC.RString("yesRestore"), OMC.RString("no")};
			new AlertDialog.Builder(this)
				.setTitle(OMC.RString("deleteAllThemesFromSD"))
				.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int item) {
							switch (item) {
								case 0: //Yes
									OMC.removeDirectory(
											new File(
													Environment.getExternalStorageDirectory().getAbsolutePath()+"/.OMCThemes"));
						    		OMC.purgeTypefaceCache();
						    		OMC.purgeBitmapCache();
						    		OMC.purgeImportCache();
						    		OMC.purgeEmailCache();
						    		OMC.THEMEMAP.clear();
						        	OMC.WIDGETBMPMAP.clear();
						    		Toast.makeText(OMCPrefActivity.this, OMC.RString("omcThemesFolderDeleted"), Toast.LENGTH_SHORT).show();
									break;
								case 1: //Yes but restore
									File omcroot = new File(
											Environment.getExternalStorageDirectory().getAbsolutePath()+"/.OMCThemes");
									OMC.removeDirectory(omcroot);
									omcroot.mkdirs();
						        	OMC.setupDefaultTheme();
									startActivity(OMC.GETSTARTERPACKINTENT);
						    		OMC.purgeTypefaceCache();
						    		OMC.purgeBitmapCache();
						    		OMC.purgeImportCache();
						    		OMC.purgeEmailCache();
						    		OMC.THEMEMAP.clear();
						        	OMC.WIDGETBMPMAP.clear();
						        	OMCThemePickerActivity.THEMEROOT.mkdir();
						    		Toast.makeText(OMCPrefActivity.this, OMC.RString("defaultClockPackRestored"), Toast.LENGTH_SHORT).show();
									break;
								case 2: //No
									//do nothing
									break;
								default:
									//do nothing
							}
						}
				})
				.show();
    	}
    	if (preference == findPreference("weather")){
			final CharSequence[] items = {OMC.RString("disableLocationAndWeather"), OMC.RString("followDevice"), OMC.RString("setFixedLocation")};
			new AlertDialog.Builder(this)
				.setTitle(OMC.RString("weatherLocation"))
				.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int item) {

							switch (item) {
								case 0: //Disabled (default)
									OMC.PREFS.edit().putString("weathersetting", "disabled").commit();
									break;
								case 1: //Follow Device
									OMC.PREFS.edit().putString("weathersetting", "bylatlong").commit();
						    		OMC.updateWeather(true);
									break;
								case 2: //Set Location
									startActivityForResult(new Intent(OMCPrefActivity.this, OMCFixedLocationActivity.class), 0);
									break;
								default:
									//do nothing
							}
						}
				})
				.show();
    	}
    	if (preference == findPreference("tweakTheme")){
    		getPreferenceScreen().setEnabled(false);
    		Intent tweakIntent = new Intent(this, OMCThemeTweakerActivity.class);
    		tweakIntent.putExtra("aWI", OMCPrefActivity.appWidgetID);
    		tweakIntent.putExtra("theme", OMC.PREFS.getString("widgetTheme", OMC.DEFAULTTHEME));
    		tweakIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    		startActivityForResult(tweakIntent,0);
    	}
    	if (preference == findPreference("emailMe")) {
			final CharSequence[] items = {OMC.RString("email"), OMC.RString("donate"), OMC.RString("facebook")};
			new AlertDialog.Builder(this)
				.setTitle(OMC.RString("contactXaffron"))
				.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int item) {
							switch (item) {
								case 0: //Email
									Intent it = new Intent(android.content.Intent.ACTION_SEND)
		    		   					.setType("plain/text")
		    		   					.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{"skwong@consultant.com"})
		    		   					.putExtra(android.content.Intent.EXTRA_SUBJECT, OMC.APPNAME + " Feedback v" + OMC.THISVERSION);
					    		   	startActivity(Intent.createChooser(it, OMC.RString("contactXaffronForIssues"))); 
					    		   	
					    		   	finish();
					    		   	break;
								case 1: //Donate
						    		it = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=S9VEL3WFGXK48"));
						    		startActivity(it);
						    		finish();
									break;
								case 2: //Facebook
						    		it = new Intent(Intent.ACTION_VIEW, Uri.parse("fb://page/389054721147516"));
						    		
						    		// Add try and catch because the scheme could be changed in an update! 
						    		// Another reason is that the Facebook-App is not installed 
						    		try {
							    		startActivity(it);
						    		} catch (ActivityNotFoundException ex) {      
						    		// start web browser and the facebook mobile page as fallback    
						    			it = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.facebook.com/XaffronSoftware"));    
						    			startActivity(it); 
						    		}
						    		finish();
						    		break;
								default:
									//do nothing
							}
						}
				})
				.show();
    	}
    	if (preference == getPreferenceScreen().findPreference("widgetPrefs") && OMC.FREEEDITION) {
    		final CharSequence TitleCS = OMC.RString("areThereOtherWidgetSizes");
    		final CharSequence MessageCS = OMC.RString("actuallyThePaidVersion");
    		final CharSequence PosButtonCS = OMC.RString("takeMeToPaid");
        	OMCPrefActivity.mAD = new AlertDialog.Builder(this)
			.setCancelable(true)
			.setTitle(TitleCS)
			.setMessage(MessageCS)
			.setPositiveButton(PosButtonCS, new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// TODO Auto-generated method stub
						OMCPrefActivity.mAD.dismiss();
						OMCPrefActivity.this.startActivity(OMC.OMCMARKETINTENT);
						OMCPrefActivity.this.finish();
					}
				}).create();
        	OMCPrefActivity.mAD.show();
    	}
    	if (preference == getPreferenceScreen().findPreference("widgetCredits")) {
    		startActivityForResult(OMC.CREDITSINTENT,0);
    	}
    	if (preference == getPreferenceScreen().findPreference("sVersion")) {
			this.startActivity(OMC.OMCMARKETINTENT);
        	this.finish();
    	}
    	if (preference == getPreferenceScreen().findPreference("loadThemeFile")) {
    		getPreferenceScreen().setEnabled(false);
    		OMC.PICKTHEMEINTENT.putExtra("appWidgetID", appWidgetID);
    		OMC.PICKTHEMEINTENT.putExtra("default", OMC.PREFS.getString("widgetTheme", OMC.DEFAULTTHEME));
    		startActivityForResult(OMC.PICKTHEMEINTENT,0);
    	}
    	if (preference == getPreferenceScreen().findPreference("oTTL")) {
    		if (OMC.SINGLETON && OMC.FREEEDITION) {
            	OMCPrefActivity.mAD = new AlertDialog.Builder(this)
    			.setCancelable(true)
    			.setTitle(OMC.RString("thinkOfThePossibilities"))
    			.setMessage(OMC.RString("prePaidTTL") + OMC.APPNAME + OMC.RString("postPaidTTL"))
    			.setPositiveButton(OMC.RString("takeMeToPaid"), new DialogInterface.OnClickListener() {
    					
    					@Override
    					public void onClick(DialogInterface dialog, int which) {
    						// TODO Auto-generated method stub
    						OMCPrefActivity.mAD.dismiss();
    						OMCPrefActivity.this.startActivity(OMC.OMCMARKETINTENT);
    						OMCPrefActivity.this.finish();
    						
    					}
    				}).create();
            	OMCPrefActivity.mAD.show();
    		} else {
    			final CharSequence[] items = {
    					OMC.RString("openOptionsDefault"), 
    					OMC.RString("doNothing"), 
    					OMC.RString("weatherForecast"), 
    					OMC.RString("viewAlarms"), 
    					OMC.RString("otherActivity")};
    			final String[] values = {
    					"default", 
    					"noop", 
    					"weather", 
    					"alarms", 
    					"activity"};
				
				final AlertDialog dlgTTL  =  new AlertDialog.Builder(this)
				.setTitle(OMC.RString("chooseAction"))
				.setItems(items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int item) {
							if (values[item].equals("default")) {
								OMC.PREFS.edit().putString("URI"+OMC.COMPASSPOINTS[iTTLArea], "")
									.putString("URIDesc"+OMC.COMPASSPOINTS[iTTLArea], OMC.RString("widgetPrefsTTL")).commit();
							}
							if (values[item].equals("noop")) {
								OMC.PREFS.edit().putString("URI"+OMC.COMPASSPOINTS[iTTLArea], "noop")
									.putString("URIDesc"+OMC.COMPASSPOINTS[iTTLArea], OMC.RString("doNothingTTL")).commit();
							}
							if (values[item].equals("weather")) {
								OMC.PREFS.edit().putString("URI"+OMC.COMPASSPOINTS[iTTLArea], "weather")
									.putString("URIDesc"+OMC.COMPASSPOINTS[iTTLArea], OMC.RString("weatherForecastTTL")).commit();
							}
							if (values[item].equals("alarms")) {
								OMC.PREFS.edit().putString("URI"+OMC.COMPASSPOINTS[iTTLArea], "alarms")
									.putString("URIDesc"+OMC.COMPASSPOINTS[iTTLArea], OMC.RString("viewAlarmsTTL")).commit();
							}
							if (values[item].equals("activity")) {
					    		getPreferenceScreen().setEnabled(false);
					    		cancelTTL();
					    		Intent mainIntent = new Intent(Intent.ACTION_MAIN,
					        			null);
								mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
					
								Intent pickIntent = new	Intent(Intent.ACTION_PICK_ACTIVITY);
								pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
								startActivityForResult(pickIntent, iTTLArea);
								mainIntent=null;
								pickIntent=null;
							}
							for (int iCompass = 0; iCompass < 9; iCompass++) {
								btnCompass[iCompass].setText(OMC.PREFS.getString("URIDesc"+OMC.COMPASSPOINTS[iCompass],OMC.RString("widgetPrefsTTL")));
							}
						}
				}).create();

				LayoutInflater li = LayoutInflater.from(this);
				LinearLayout ll = (LinearLayout)(li.inflate(OMC.RLayoutId("ttlpreview"), null));

				for (int iCompass = 0; iCompass < 9; iCompass++) {
					btnCompass[iCompass] = (Button)ll.findViewById(OMC.RId("button" + OMC.COMPASSPOINTS[iCompass] + "Prv"));
					btnCompass[iCompass].setText(OMC.PREFS.getString("URIDesc"+OMC.COMPASSPOINTS[iCompass],OMC.RString("widgetPrefsTTL")));
					final int iTTL = iCompass;
					btnCompass[iCompass].setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							iTTLArea=iTTL;
							dlgTTL.show();
						}
					});
				}
    			mTTL = new AlertDialog.Builder(this)
    					.setView(ll)
    					.setTitle(OMC.RString("areaToCustomize"))
    					.show();
    		}
    	}
    	if (preference == getPreferenceScreen().findPreference("clearCache")) {
    		OMC.purgeTypefaceCache();
    		OMC.purgeBitmapCache();
    		OMC.purgeImportCache();
    		OMC.purgeEmailCache();
    		OMC.THEMEMAP.clear();
        	OMC.WIDGETBMPMAP.clear();
    		Toast.makeText(this, OMC.RString("cachesCleared"), Toast.LENGTH_SHORT).show();
    	}
    	if (preference == getPreferenceScreen().findPreference("timeZone")) {
    		getPreferenceScreen().setEnabled(false);
    		cancelTTL();
    		Intent mainIntent = new Intent(Intent.ACTION_MAIN,
        			null);
			mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

			Intent pickIntent = new Intent(OMC.CONTEXT, ZoneList.class);
			startActivityForResult(pickIntent, OMCPrefActivity.appWidgetID);
			mainIntent=null;
			pickIntent=null;
    	}
    	return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    // The result is obtained in onActivityResult:
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	prefloadThemeFile.setSummary(OMC.RString("preselected")  +OMC.PREFS.getString("widgetThemeLong", OMC.DEFAULTTHEMELONG)+ OMC.RString("postselected"));
		if (OMC.PREFS.getString("sTimeZone", "default").equals("default")) {
			getPreferenceScreen().findPreference("timeZone").setSummary(OMC.RString("followingDeviceTimeZone"));
		} else {
			getPreferenceScreen().findPreference("timeZone").setSummary(OMC.PREFS.getString("sTimeZone", "default"));
		}
		getPreferenceScreen().setEnabled(true);

		mRefresh = (new Thread() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				try {
					while(true) {
						mHandler.post(mUpdatePrefs);
						Thread.sleep(1000l);
					}
				} catch (InterruptedException e) {
					// interrupted; stop gracefully
				}
			}
		});
		mRefresh.start();
		// If it's an independent child activity, do nothing
		if (requestCode == 0) return;
		if (data != null) {
			String s = data.toUri(MODE_PRIVATE).toString();
			
			OMC.PREFS.edit().putString("URI"+OMC.COMPASSPOINTS[requestCode], s)
				.putString("URIDesc"+OMC.COMPASSPOINTS[requestCode], OMC.RString("otherActivityTTL")).commit();
		}
	}
    
	public void cancelTTL() {
       	if (mTTL!=null) { // && mAD.isShowing()
       		mTTL.dismiss();
       		mTTL = null;
       	}
	}
	
    public void dialogCancelled() {
       	if (OMCPrefActivity.mAD!=null) { // && mAD.isShowing()
       		OMCPrefActivity.mAD.dismiss();
       		OMCPrefActivity.mAD = null;
       	}
       	if (mTTL!=null) { // && mAD.isShowing()
       		mTTL.dismiss();
       		mTTL = null;
       	}
    	finish();
    }

    @Override
    public void onPause() {
    	super.onPause();
		if (appWidgetID >= 0) {

			if (OMC.DEBUG) Log.i(OMC.OMCSHORT + "Pref","Saving Prefs for Widget " + OMCPrefActivity.appWidgetID);
			OMC.FG = OMC.PREFS.getBoolean("widgetPersistence", true)? true : false;
			OMC.UPDATEFREQ = Integer.parseInt(OMC.PREFS.getString("sUpdateFreq", "30")) * 1000;
	    	OMC.setPrefs(OMCPrefActivity.appWidgetID);
	    	if (OMC.WIDGETBMPMAP.containsKey(OMCPrefActivity.appWidgetID)) {
	    		if (!OMC.WIDGETBMPMAP.get(OMCPrefActivity.appWidgetID).isRecycled()) OMC.WIDGETBMPMAP.get(OMCPrefActivity.appWidgetID).recycle();
	        	OMC.WIDGETBMPMAP.remove(OMCPrefActivity.appWidgetID);
	    	}
	
	    	OMC.toggleWidgets(getApplicationContext());
	
			// Set the alarm for next tick first, so we don't lose sync
    		getApplicationContext().sendBroadcast(OMC.WIDGETREFRESHINTENT);
			OMC.setServiceAlarm(System.currentTimeMillis()+10500l, (System.currentTimeMillis()+10500l)/1000l*1000l);

		}
		if (mRefresh!=null) mRefresh.interrupt();
    }

    @Override
    public void onDestroy() {
      super.onDestroy();
    }
} 