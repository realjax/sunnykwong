package com.sunnykwong.aurorabulb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Collections;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.app.AlarmManager;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.graphics.BitmapFactory;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.view.Display;

/**
 * @author skwong01
 * Thanks to ralfoide's 24clock code; taught me quite a bit about broadcastreceivers
 * Thanks to the Open Font Library for a great resource.
 * 
 */
public class AB extends Application {
	
	static String THISVERSION;
	static final String PREFNAME = "com.sunnykwong.aurorabulb_preferences";
	static final boolean DEBUG = true;
	static final int BUFFERWIDTH = 240;
	static final int BUFFERHEIGHT = 320;
	
	static int TARGETFPS;
	static int COUNTDOWNSECONDS;
	
	static long LASTUPDATEMILLIS;
	static int UPDATEFREQ = 100;

	static SharedPreferences PREFS;
    
	static Matrix TEMPMATRIX, TEMPMATRIX2;
	static int SCRNWIDTH;
	static int SCRNHEIGHT;
	static int SCRNDPI;
	
	static final Time TIME = new Time();

	static String TXTBUF;
	
	static Bitmap SRCBUFFER;
	static Canvas SRCCANVAS;
	static Bitmap SRCBUFFER2;
	static Canvas SRCCANVAS2;
	static Bitmap ROLLBUFFER;
	static Canvas ROLLCANVAS;
	static Bitmap bmpTemp,bmpTemp2;

	static Paint PT1;
	static Paint PT2;
	
	static float fSCALEX, fSCALEY;

	@Override
	public void onCreate() {
		super.onCreate();

		try {
			THISVERSION = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA).versionName;
		} catch (NameNotFoundException e) {
			THISVERSION = "1.0.0";
		}
		
		LASTUPDATEMILLIS = 0l;

		TARGETFPS = 30;
	
		COUNTDOWNSECONDS = 10;
		
		AB.PT1 = new Paint();
		AB.PT1.setTextSize(AB.BUFFERHEIGHT);
		AB.PT1.setColor(Color.LTGRAY);
		AB.PT1.setTextAlign(Paint.Align.CENTER);
		AB.PT1.setAntiAlias(true);

		AB.PT2 = new Paint(AB.PT1);
		AB.PT2.setTextSize(AB.BUFFERHEIGHT*2/3);

		TEMPMATRIX = new Matrix();
		TEMPMATRIX2 = new Matrix();
		
		PREFS = getSharedPreferences(AB.PREFNAME, Context.MODE_PRIVATE);

		PREFS.edit().putString("version", THISVERSION).commit();
		
	}

    @Override
    public void onTerminate() {
        PREFS.edit().commit();
        super.onTerminate();
    }

}