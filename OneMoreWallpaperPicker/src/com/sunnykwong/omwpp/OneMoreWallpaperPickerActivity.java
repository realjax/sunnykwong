package com.sunnykwong.omwpp;

import java.io.BufferedInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.LogRecord;

import javax.net.ssl.HandshakeCompletedListener;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.opengl.Visibility;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.app.WallpaperManager;
import android.app.WallpaperManager;
import com.mobclix.android.sdk.MobclixMMABannerXLAdView;

public class OneMoreWallpaperPickerActivity extends Activity {
	
	public Gallery gallery;
	public Button btnApply, btnHelp;
	public TextView tvDebConsole, tvFileConsole;
	public RadioGroup cb16Bit;
	public RadioButton dithernone, ditherweak, ditherstrong;
	public WPPickerAdapter adapter;
	static public ProgressDialog PROGRESSDIALOG;
	static public int PROGRESSVAL;
	public Handler mHandler;
	public Thread wallpaperThread;
    final Runnable UPDATEPROGRESS = new Runnable() {
    	@Override
    	public void run() {								
			if (PROGRESSDIALOG!=null) {
				PROGRESSDIALOG.setProgress(PROGRESSVAL);
			}
    	} 
    };
    final Runnable UPDATEDONE = new Runnable() {
    	@Override
    	public void run() {								
			if (PROGRESSDIALOG!=null) {
				PROGRESSDIALOG.dismiss();
				finish();
			}
    	}
    };
    final Runnable PROGRESSINIT = new Runnable() {
    	@Override
    	public void run() {								
			if (PROGRESSDIALOG!=null) {
				PROGRESSDIALOG.show();
			}
    	}
    };
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mHandler = new Handler();
        wallpaperThread = null;
	    if (getResources().getDisplayMetrics().heightPixels < 600 && getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        if (savedInstanceState==null) { 
	        long startMillis = System.currentTimeMillis();
	        if (OMWPP.THUMBNAILQUEUE!=null)OMWPP.THUMBNAILQUEUE.clear();
	        if (OMWPP.UNZIPQUEUE!=null)OMWPP.UNZIPQUEUE.clear();
	        if (OMWPP.DOWNLOADQUEUE!=null)OMWPP.DOWNLOADQUEUE.clear();
	        
	        
	    	if (OMWPP.DEBUG) Log.i("OMWPPActivity","Starting Activity");
	        getWindow().setWindowAnimations(android.R.style.Animation_Toast);
	        getWindow().setFormat(PixelFormat.RGBA_8888);
			setResult(Activity.RESULT_CANCELED);
	 
			boolean bNeedRefresh=false;;
	
			// If the config file is less than 6 months old, let it be
	
			if (System.currentTimeMillis() - OMWPP.CONFIGJSON.optLong("lastupdateepoch",0l) < 262974l * 60000l) {
		    	if (OMWPP.DEBUG) Log.i("OMWPP","Config file less than 6 months old - no update.");
				bNeedRefresh = false;
			} else {
				// If last download was more than 6 months ago, then download
				if (System.currentTimeMillis() - OMWPP.LASTCONFIGREFRESH > 262974l * 60000l) {
			    	if (OMWPP.DEBUG) Log.i("OMWPP","Config file old, but last DL less than 6 months old - no update.");
					bNeedRefresh = true;
				} else {
			    	if (OMWPP.DEBUG) Log.i("OMWPP","Config file & last DL more than 6 months old - need update.");
					bNeedRefresh = false;
				} 
			}
			if (bNeedRefresh) {
		    	if (OMWPP.DEBUG) Log.w("OMWPP","WE SHOULD NEVER BE HERE");
	
				AsyncTask<String, Void, String> at = new AsyncTask<String, Void, String>() {
					@Override
					protected String doInBackground(String... urls) {
	
						String response = "";
						for (String url : urls) {
							DefaultHttpClient client = new DefaultHttpClient();
							HttpGet httpGet = new HttpGet(url);
							try {
								HttpResponse execute = client.execute(httpGet);
								JSONObject tempObj = OMWPP.streamToJSONObject(execute.getEntity().getContent());
								BufferedWriter out = new BufferedWriter(new FileWriter(new File(OMWPP.THUMBNAILROOT.getPath()+ "omwpp_config.json")),8192);
								out.write(tempObj.toString(5));
								out.close();
								OMWPP.PREFS.edit().putLong("lastupdateepoch", System.currentTimeMillis()).commit();
								OMWPP.LASTCONFIGREFRESH = System.currentTimeMillis();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
						return response;
					}
				};
				at.execute(new String[]{"http://www.yahoo.com"});
			}

	        if (!OMWPP.isSDPresent()) {
				finish();
				return;
	        }

	        PROGRESSDIALOG = new ProgressDialog(this);
	        PROGRESSDIALOG.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	        PROGRESSDIALOG.setMessage("Setting Wallpaper...");
	        PROGRESSDIALOG.setMax(100);
	        PROGRESSDIALOG.setOnCancelListener(new OnCancelListener() {
				
				@Override
				public void onCancel(DialogInterface dialog) {
					wallpaperThread.interrupt();
				}
			}); 
        }

        setContentView(R.layout.main);
        
        File testFile = new File(OMWPP.THUMBNAILROOT.getAbsolutePath()+"/.noads");
        if (testFile.exists()) {
	        TextView adtitle = (TextView)findViewById(R.id.AdTitle);
	        adtitle.setEnabled(false);
	        adtitle.setVisibility(View.INVISIBLE);
	        MobclixMMABannerXLAdView adview = (MobclixMMABannerXLAdView)findViewById(R.id.advertising_banner_view);
	        adview.cancelAd();
	        adview.pause();
	        adview.setEnabled(false);
	        adview.setVisibility(View.INVISIBLE);
        }
        
        gallery = (Gallery)findViewById(R.id.wpgallery);
        if (adapter==null) adapter = new WPPickerAdapter();
        gallery.setAdapter(adapter);
        
        
        //Load all wallpapers in dir into picker.

        tvDebConsole = (TextView)findViewById(R.id.debconsole);
        tvFileConsole = (TextView)findViewById(R.id.fileconsole);
        
        btnApply = (Button)findViewById(R.id.btnapply);
        btnApply.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				setWallpaper((String)gallery.getSelectedItem());				
			}
		});
        btnHelp = (Button)findViewById(R.id.btnhelp);
        btnHelp.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Toast.makeText(OMWPP.CONTEXT, "Sorry, no help.\nThis is alpha, remember?", Toast.LENGTH_SHORT).show();
			}
		}); 
        cb16Bit = (RadioGroup)findViewById(R.id.rgdither);
        dithernone = (RadioButton)findViewById(R.id.dithernone);
        ditherweak = (RadioButton)findViewById(R.id.ditherweak);
        ditherstrong = (RadioButton)findViewById(R.id.ditherstrong);
        cb16Bit.check(OMWPP.PREFS.getInt("cb16Bit", R.id.dithernone)); 
        cb16Bit.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				OMWPP.PREFS.edit().putInt("cb16Bit", checkedId).commit();
			}
		});
			
        

        if (savedInstanceState==null) {
	        OMWPP.PREVIEWTASK = new PopulateGalleryTask();
	        OMWPP.DOWNLOADTASK = new DownloadDebsTask();
	        OMWPP.THUMBNAILTASK = new GenerateThumbnailTask();
	        OMWPP.PREVIEWTASK.execute();
	        OMWPP.THUMBNAILTASK.execute("","","");
        }
        gallery.setSelection(0);
        gallery.setOnItemClickListener(new AdapterView.OnItemClickListener() {
        	@Override
        	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
        			long arg3) {
        		mHandler.post(PROGRESSINIT);
        		setWallpaper((String)gallery.getSelectedItem());
        	}
		});
        
    }        

    public void setWallpaper(final String fName) {
		OMWPP.WALLPAPERDONE=false;
    	mHandler.post(PROGRESSINIT);
    	OMWPP.PREFS.edit().putString("lastwp", fName).commit();
		wallpaperThread = new Thread(){
			public void run() {
				try {
        			float fScale = 1f;
        			if (OMWPP.DEBUG) Log.i("OMWPPActivity","New Wallpaper: " + fName);
        			Bitmap wpBitmap = BitmapFactory.decodeFile(fName,OMWPP.BMPQUERYOPTIONS);
        			float wpWidth = OMWPP.BMPQUERYOPTIONS.outWidth;
        			float wpHeight = OMWPP.BMPQUERYOPTIONS.outHeight;
        			if (OMWPP.DEBUG) Log.i("OMWPPActivity","Wallpaper size: " + wpWidth + "x" + wpHeight);
        			if (OMWPP.DEBUG) Log.i("OMWPPActivity","Virtual Screen: " + OMWPP.WPWIDTH + "x" + OMWPP.WPHEIGHT);
        			// If wp is smaller than phone, we scale up
        			if (wpWidth<OMWPP.WPWIDTH || wpHeight < OMWPP.WPHEIGHT) {
        				fScale = Math.max(OMWPP.WPWIDTH/wpWidth, OMWPP.WPHEIGHT/wpHeight);
        			} else {
        			// If wp is larger than phone, we scale down
        				fScale = 1f/(Math.min(wpWidth/OMWPP.WPWIDTH, wpHeight/OMWPP.WPHEIGHT));
        				while (fScale < 0.5) {
                			if (OMWPP.DEBUG) Log.i("OMWPPActivity","WP too large - Prescaling by half to fit homescreen.");
        					OMWPP.BMPAPPLYOPTIONS.inSampleSize*=2;
        					wpWidth/=2f;
        					wpHeight/=2f;
        					fScale*=2f;
        				}
        			}
        			if (OMWPP.DEBUG) Log.i("OMWPPActivity","Scaling by " + fScale + " to fit homescreen.");

        			wpBitmap = BitmapFactory.decodeFile(fName,OMWPP.BMPAPPLYOPTIONS);

        			OMWPP.BMPAPPLYOPTIONS.inSampleSize=1;

    				// If no dither, we're done.  Otherwise, bake noise.
    				Bitmap bmp;
    				double ditherStrength=0f;
    				
        			if (OMWPP.PREFS.getInt("cb16Bit", R.id.dithernone)==R.id.ditherstrong) {
        				ditherStrength=24d;
        				bmp= Bitmap.createBitmap(OMWPP.WPWIDTH,OMWPP.WPHEIGHT,Config.RGB_565);
        			} else if (OMWPP.PREFS.getInt("cb16Bit", R.id.dithernone)==R.id.ditherweak) {
        				ditherStrength=6d;
        				bmp= Bitmap.createBitmap(OMWPP.WPWIDTH,OMWPP.WPHEIGHT,Config.RGB_565);
        			} else {
        				bmp= Bitmap.createBitmap(OMWPP.WPWIDTH,OMWPP.WPHEIGHT,Config.ARGB_8888);
        			}
    				Canvas c = new Canvas(bmp);

    				Matrix mx = new Matrix();
        			int tempwidth = (int)(wpBitmap.getWidth()*fScale);
        			int tempheight = (int)(wpBitmap.getHeight()*fScale);
        			float translatex = (OMWPP.WPWIDTH-tempwidth)/2f;
        			float translatey = (OMWPP.WPHEIGHT-tempheight)/2f;
        			
    				mx.postScale(fScale, fScale);
    				mx.postTranslate(translatex, translatey);
    				Paint pt = new Paint();
    				pt.setDither(true);
    				pt.setAntiAlias(true);
    				pt.setFilterBitmap(true);
    				c.drawBitmap(wpBitmap, mx, pt);
    				wpBitmap.recycle();
        			
        			if (ditherStrength!=0) {
        				int color,r,g,b;
        				int ditherfactor;
        				if (OMWPP.DEBUG) Log.i("OMWPPActivity","Dither Strength is " + ditherStrength);
	        			for (int i=0;i<OMWPP.WPWIDTH;i+=1) {
	        				if (i%100==0) {
	        					if (OMWPP.DEBUG) Log.i("OMWPPActivity","baking noise... " + i + "/" + OMWPP.WPWIDTH);
	        				}
        					PROGRESSVAL = (int)(i*1f/OMWPP.WPWIDTH*90);
        					mHandler.post(UPDATEPROGRESS);
	        				for (int j=0;j<OMWPP.WPHEIGHT; j+=1) {
	        					color=bmp.getPixel(i, j);
	        					ditherfactor = (int)((Math.random()-0.5d)*ditherStrength);
	        					if (ditherfactor>0) {
		        					r=Math.min(((color >> 16) & 0xFF)+ditherfactor,255);
		        					g=Math.min(((color >> 8) & 0xFF)+ditherfactor,255);
		        					b=Math.min((color & 0xFF)+ditherfactor,255);
		        					bmp.setPixel(i, j, Color.rgb(r,g,b));
	        					} else if (ditherfactor<0) {
		        					r=Math.max(((color >> 16) & 0xFF)+ditherfactor,0);
		        					g=Math.max(((color >> 8) & 0xFF)+ditherfactor,0);
		        					b=Math.max((color & 0xFF)+ditherfactor,0);
		        					bmp.setPixel(i, j, Color.rgb(r,g,b));
	        					}
	        				}
	        			}
        			}

					PROGRESSVAL = (int)(95);
					mHandler.post(UPDATEPROGRESS);
    				OMWPP.WPM.setBitmap(bmp);
    				while (!OMWPP.WALLPAPERDONE) {
    					try {
    						Thread.sleep(100);
    					} catch (InterruptedException e) {
    						e.printStackTrace();
    						break;
    					}
    				}
    				mHandler.post(UPDATEDONE);
    				
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
				
			};
		};
		wallpaperThread.start();

    }
    
	@Override 
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.statusmenu, menu);
		return true; 
	}
	
	@Override 
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		// TODO Auto-generated method stub
		
		if (item.getItemId()==R.id.menuforcedownload) {
			AlertDialog ad = new AlertDialog.Builder(this)
								.setCancelable(true)
								.setTitle("WARNING: Large Download")
								.setMessage("OMWPP is about to contact Ubuntu servers for its background files, which total up to 50-60MB in size.  It is highly recommended to proceed only when you are on WiFi.")
								.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
									
									@Override
									public void onClick(DialogInterface dialog, int which) {
										//Do nothing
										}
								})
								.setPositiveButton("Ready!", new DialogInterface.OnClickListener() {
									
									@Override
									public void onClick(DialogInterface dialog, int which) {
										new DownloadDebsTask().execute("");
									}
								})
								.show();
		} else if (item.getItemId()==R.id.menudownloadstatus) {
			StringBuilder sb = new StringBuilder(1000);
			try {
				JSONArray archives = OMWPP.CONFIGJSON.getJSONArray("archives"); 
				for (int i = 0; i < archives.length(); i++) {
					JSONObject archive = archives.getJSONObject(i);
					sb.append(archive.getString("comment"))
						.append("(" + archive.getLong("size") + " bytes) is ")
						.append(archive.getBoolean("downloaded")?"downloaded.":"not downloaded.")
						.append("\n");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			AlertDialog ad = new AlertDialog.Builder(this)
								.setCancelable(true)
								.setTitle("Download Status")
								.setMessage(sb.toString())
								.setPositiveButton("OK", new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog, int which) {
									}
								}).show();
		} else {
			Toast.makeText(OMWPP.CONTEXT, "Sorry, no help.\nThis is alpha, remember?", Toast.LENGTH_SHORT).show();
		}
		
		return super.onMenuItemSelected(featureId, item);
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
		if (OMWPP.DOWNLOADTASK!=null)OMWPP.DOWNLOADTASK.cancel(true);
		if (OMWPP.THUMBNAILTASK!=null)OMWPP.THUMBNAILTASK.cancel(true);
		if (OMWPP.PREVIEWTASK!=null)OMWPP.PREVIEWTASK.cancel(true);
		OMWPP.DOWNLOADQUEUE.clear();
		OMWPP.DOWNLOADTASK=null;
		OMWPP.THUMBNAILQUEUE.clear();
		OMWPP.THUMBNAILTASK=null;
		OMWPP.PREVIEWTASK=null;
		if (adapter!=null) adapter.dispose();
		super.onPause();
		
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);
	    if (getResources().getDisplayMetrics().heightPixels < 600 && newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}

	public class PopulateGalleryTask extends AsyncTask<String, String, String> {
		int count=0;
		@Override
		protected String doInBackground(String... dummy) {
			//File[] files = OMWPP.SDROOT.listFiles();
			List<File> filelist = Arrays.asList(OMWPP.SDROOT.listFiles()); 
			Collections.sort(filelist, new Comparator<File>() {
				@Override
				public int compare(File object1, File object2) {
					// TODO Auto-generated method stub
					if (object2==null) return 1;
					if (object1==null) return -1;
					if (object1.getName().toLowerCase().compareTo(object2.getName().toLowerCase())>0) return 1;
					return -1;
				}
			});
			File[] files = (File[])filelist.toArray();
	        for (File f : files) {
				if ( isCancelled()) {
					if (OMWPP.DEBUG) Log.i("OMWPPreview", "Task interrupted. Ending.");
					return "";
				}
	        	final Bitmap bmp, thumbnail;
	        	// Spot check the file to see if it is a supported bitmap.
	        	// If it isn't, don't bother - move on.
	        	if (!f.getName().endsWith(".png") && !f.getName().endsWith(".jpg")) continue;
	        	
	        	// Custom blacklists.
	        	if (f.getName().endsWith("edubuntu_default.png")) continue;

	        	// If it is, create a thumbnail if it doesn't already exist.
    	    	if (OMWPP.DEBUG) Log.i("OMWPPreview",f.getName()+" is added to thumbnail queue.");
        		try {
        			OMWPP.THUMBNAILQUEUE.offer(f);
        		} catch (Exception e) {
        			e.printStackTrace();
        		}
	        	adapter.addItem(f);
	        	if (f.getAbsolutePath().equals(OMWPP.PREFS.getString("lastwp", ""))) publishProgress("lastwp",String.valueOf(adapter.getCount()-1));
	        	publishProgress("Found " + f.getName() + ".");
	        }
	        return "";
		}
		@Override
		protected void onProgressUpdate(String... values) {
			if (values.length==2 && values[0].equals("lastwp")) gallery.setSelection(Integer.parseInt(values[1]));
        	if (OMWPP.THUMBNAILTASK==null) OMWPP.THUMBNAILTASK = new GenerateThumbnailTask();
        	else if (OMWPP.THUMBNAILTASK.getStatus()==Status.FINISHED) OMWPP.THUMBNAILTASK = new GenerateThumbnailTask();
			try {
				if (OMWPP.THUMBNAILTASK.getStatus()==Status.PENDING) 
					OMWPP.THUMBNAILTASK.execute("","","");
			} catch (IllegalStateException e) {
    	    	if (OMWPP.DEBUG) Log.w("OMWPPreview","Illegal State Exception.");
				e.printStackTrace();
				try {
					Thread.sleep(500);
					OMWPP.THUMBNAILTASK.execute("","","");
				} catch (InterruptedException ee) {
					e.printStackTrace();
				}
			}
 
        	adapter.notifyDataSetChanged();
			tvDebConsole.setText(values[0]);
			super.onProgressUpdate(values);
		}
		@Override
		protected void onPostExecute(String result) {
			tvDebConsole.setText("File discovery complete.");
            adapter.notifyDataSetChanged();

			super.onPostExecute(result);
		}
	}

	public class GenerateThumbnailTask extends AsyncTask<String, String, String> {
		int count=0,savedcount=0,loadedcount=0;
		@Override
		protected String doInBackground(String... dummy) {
			File fullBmpFile=null;
			while (true) {
				if (isCancelled()) {
					if (OMWPP.DEBUG) Log.i("OMWPPTNThread", "Task interrupted. Ending.");
					OMWPP.THUMBNAILQUEUE.clear();
					return "";
				}
    	    	if (OMWPP.DEBUG) Log.i("OMWPPTNThread","Polling queue.");
				try {
					fullBmpFile = OMWPP.THUMBNAILQUEUE.poll();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (fullBmpFile==null) {
					try {
						Thread.sleep(100l);
					} catch (InterruptedException e) {
						break;
					}
				} else {
					try { 
			        	final File tnfile = new File(OMWPP.THUMBNAILROOT+"/XFTN_" + fullBmpFile.getName());
			        	if (tnfile.exists()) {
					    	if (OMWPP.DEBUG) Log.i("OMWPPTNThread",tnfile.getName()+" already cached.");
					    	loadedcount++;
			        	} else {
			        		if (OMWPP.DEBUG) Log.i("OMWPPTNThread","Generating preview for " + fullBmpFile.getName());
		        			Bitmap bmpFull = BitmapFactory.decodeFile(fullBmpFile.getAbsolutePath(),OMWPP.BMPQUERYOPTIONS);
		        			float wpWidth = OMWPP.BMPQUERYOPTIONS.outWidth;
		        			float wpHeight = OMWPP.BMPQUERYOPTIONS.outHeight;
		        			float fScale = 1f/(Math.min(wpWidth/(OMWPP.SCREENWIDTH/2), wpHeight/((int)(OMWPP.SCREENWIDTH*0.3125))));
	        				while (fScale < 0.5) {
	        					OMWPP.BMPAPPLYOPTIONS.inSampleSize*=2;
	        					wpWidth/=2f;
	        					wpHeight/=2f;
	        					fScale*=2f;
	        				}
			        		bmpFull = BitmapFactory.decodeFile(fullBmpFile.getAbsolutePath(),OMWPP.BMPAPPLYOPTIONS);
			        		OMWPP.BMPAPPLYOPTIONS.inSampleSize=1;
			        		Bitmap bmp = Bitmap.createScaledBitmap(bmpFull, OMWPP.SCREENWIDTH/2,(int)(OMWPP.SCREENWIDTH*0.3125),true);
			        		bmpFull.recycle(); 
			        		bmp.compress(CompressFormat.JPEG, 50, new FileOutputStream(tnfile));
			        		if (OMWPP.DEBUG) Log.i("OMWPPTNThread","Thumbnail saved: " + fullBmpFile.getName()+".");
					    	savedcount++;
					    }
			        	publishProgress("");
	        		} catch (Exception e) { 
		    	    	if (OMWPP.DEBUG) Log.w("OMWPPTNThread","Thumbnail could not be created: " + fullBmpFile.getName() +".");
	        			e.printStackTrace();
	        			continue;
	        		}
				}
			} 
			return "";
		}
		@Override
		protected void onProgressUpdate(String... values) {
			if (OMWPP.THUMBNAILQUEUE.isEmpty()) 
				tvFileConsole.setText("Thumbnail thread is idle.");
			else 
				tvFileConsole.setText(OMWPP.THUMBNAILQUEUE.size() + " thumbnails in process queue.");

			tvFileConsole.invalidate();
			count++;
//			if (count>3) {
//				count=0;
//	            adapter.notifyDataSetChanged();
//			}
			super.onProgressUpdate(values);
		}
		@Override
		protected void onPostExecute(String result) {
			tvFileConsole.setText((savedcount+loadedcount)+ " Thumbnails prepared.");
	        adapter.notifyDataSetChanged();
			super.onPostExecute(result);
		}
	}
	
	public class DownloadDebsTask extends AsyncTask<String, String, String> {
		@Override
		protected String doInBackground(String... params) {
			int iDebFile, iMirror, iMaxMirrors;
				// First of all, are we online?  If not, don't even try.
				if (!OMWPP.isConnected()) {
					Toast.makeText(OMWPP.CONTEXT, "The device is not online... Please try later!", Toast.LENGTH_LONG).show();
					return "";
				}
				// We're going to try a maximum of 5 times.
				int tries = 1;
				try {
					iMaxMirrors = OMWPP.CONFIGJSON.getJSONArray("mirrors").length();
					for (iDebFile = 0; iDebFile<OMWPP.CONFIGJSON.getJSONArray("archives").length(); iDebFile++) {
						//
						//	DEBUG ONLY
						//if (iDebFile!=3)continue;
						//	
						// Set the remote and local filenames
	
						JSONObject Debarchive = OMWPP.CONFIGJSON.getJSONArray("archives").getJSONObject(iDebFile);
						String friendlyName = Debarchive.getString("comment");
						boolean downloaded = Debarchive.getBoolean("downloaded");

						if (downloaded) {
							if (OMWPP.DEBUG) Log.i("OMWPPDLTask", friendlyName + " already downloaded.");
							continue;
						}
						
						File localFile = new File(OMWPP.SDROOT + "/" + Debarchive.getString("filename"));
						String md5sum = Debarchive.getString("md5sum");
						
						boolean success=false;
						URLConnection ucon = null;
						InputStream is = null;
						long startTime =0l;
						
						while (tries < 5) {
							if ( isCancelled()) {
								if (OMWPP.DEBUG) Log.i("OMWPPDLTask", "Task interrupted. Ending.");
								return "";
							}
							String sMirror = "http://"+OMWPP.CONFIGJSON.getJSONArray("mirrors").getString((int)(Math.random()*iMaxMirrors));
							URL url = new URL(sMirror + Debarchive.getString("url") + Debarchive.getString("filename"));
							startTime = System.currentTimeMillis();
							if (OMWPP.DEBUG) Log.i("OMWPPDLTask", "download url:" + url + " Attempt #" + tries);
							/* Open a connection to that URL. */
							try {
								/*
								 * Define InputStreams to read from the URLConnection.
								 */
								ucon = url.openConnection();
								is = ucon.getInputStream();
								
								// OK, we're properly connected.  Open a digest so we can compute MD5.
								MessageDigest md5;
								try {
									md5 = MessageDigest.getInstance("MD5");
								} catch (NoSuchAlgorithmException e) {
									e.printStackTrace();
									is.close();
									tries++;
									continue;
								}
								BufferedInputStream bis = new BufferedInputStream(new DigestInputStream(is,md5));
								
								FileOutputStream fos = new FileOutputStream(localFile);
	
								/*
								 * Read bytes to the Buffer until there is nothing more to read(-1).
								 */
								long targetByteCount = Debarchive.getLong("size");
								long bytecount=0;
							    byte[] buffer = new byte[8192];
							    int iBytesRead = 0;
							    while ((iBytesRead = bis.read(buffer))!= -1){
									if ( isCancelled()) {
										if (OMWPP.DEBUG) Log.i("OMWPPDLTask", "Task interrupted. Ending.");
										bis.close();
										fos.close();
										return "";
									}
							    	bytecount+=iBytesRead;
									publishProgress(friendlyName + ": Downloaded " + bytecount + " out of " +targetByteCount); 
							    	fos.write(buffer,0,iBytesRead);
							    }
							    bis.close();
								fos.close();
							    
							    // Compute checksum.
							    byte[] digest = md5.digest();
								BigInteger bigInt = new BigInteger(1, digest);
								String thissum = bigInt.toString(16);
								
								// If the md5 sum matches, we're done; otherwise, we retry the download.
								if (thissum.equals(md5sum)) {
									publishProgress(friendlyName + " downloaded in "+ ((System.currentTimeMillis() - startTime) / 1000)
											+ " sec"); 
									if (OMWPP.DEBUG) Log.i("OMWPPdeb", "download ready in "
													+ ((System.currentTimeMillis() - startTime) / 1000)
													+ " sec");
									Debarchive.put("downloaded", true);
									OMWPP.commitJSONChanges();
									success=true;
									
									try {
										OMWPP.unDeb(localFile, OMWPP.SDROOT);
									} catch (Exception e) {
										e.printStackTrace();
									}
									break;
								} else {
									Log.i("OMWPPDLTask", "MD5 sum mismatch! Retry...");
									publishProgress(friendlyName + ": Downloaded file is corrupt!  Retrying..."); 
									tries++;
									continue;
								}
							} catch (java.io.FileNotFoundException e) {
								Log.i("OMWPPDLTask", "File not found on server. Retry...");
								tries++;
								continue;
							} catch (java.net.UnknownHostException e) {
								Log.i("OMWPPDLTask", "Unknown Host. Retry...");
								tries++;
								continue;
							} catch (java.io.IOException e) {
								Log.i("OMWPPDLTask", "General IO Error! Retry...");
								e.printStackTrace();
								tries++;
								continue;
							}
						}
						if (!success) {
							Toast.makeText(OMWPP.CONTEXT, "Network difficulties... Please try later!", Toast.LENGTH_LONG).show();
							return "";
						}
	
					}
				} catch (JSONException e) {
					e.printStackTrace();
					return "";
				} catch (MalformedURLException e) {
					e.printStackTrace();
					return "";
				}
				return "";
		}
		@Override
		protected void onProgressUpdate(String... values) {
			tvDebConsole.setText(values[0]);
		}

		@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
		}
	}

	public class WPPickerAdapter extends BaseAdapter {

    	public ArrayList<File> mFiles;
    	public HashMap<File, Integer> mNames;

        public WPPickerAdapter() {
        	mFiles = new ArrayList<File>();
        	mNames = new HashMap<File, Integer>();
        }
        
        public int addItem(final File bitmapFile){
        	mNames.put(bitmapFile, mFiles.size());
        	mFiles.add(bitmapFile);
        	return mFiles.size();
        }


        public void removeItem(int pos){
        }        

        @Override
		public int getCount() {
            return mFiles.size();
        }

        @Override
		public Object getItem(int position) {
            return mFiles.get(position).getAbsolutePath();
        }

        public int getPosition(File f) {
        	return mNames.get(f);
        }

        @Override
		public long getItemId(int position) {
            return position;
        }

        @Override
		public View getView(int position, View convertView, ViewGroup parent) {
        	if (position >= mFiles.size()) {
				Log.i("OMWPPAdapter", "Position "+position+ " not loaded yet - show placeholder");
        		return (LinearLayout)((LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.preview, null);
        	}
        	File f = mFiles.get(position);
  
        	LinearLayout ll = (LinearLayout)((LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.preview, null);
        	((TextView)ll.findViewById(R.id.wpfilename)).setText(f.getName());
        	File fThumb = new File(OMWPP.THUMBNAILROOT+"/XFTN_"+mFiles.get(position).getName());
        	if (!fThumb.exists()) {
        		if (OMWPP.THUMBNAILTASK == null) {
        			OMWPP.THUMBNAILTASK = new OneMoreWallpaperPickerActivity.GenerateThumbnailTask();
        			OMWPP.THUMBNAILTASK.execute("","","");
        		}
				Log.i("OMWPPAdapter", "Position "+position+ " not loaded yet - enqueue file " + f.getName());
        		OMWPP.THUMBNAILQUEUE.offer(f);
            	((ImageView)ll.findViewById(R.id.wppreview)).setImageResource(R.drawable.ic_launcher);
        	} else {
            	((ImageView)ll.findViewById(R.id.wppreview)).setImageBitmap(BitmapFactory.decodeFile(OMWPP.THUMBNAILROOT+"/XFTN_"+mFiles.get(position).getName()));
        	}

        	ll.requestLayout();
        	notifyDataSetChanged(); 
        	
//        	if (position-5>=0) 	
//        		setBitmap(mFiles.get(position-5), null);
//         	if (position+5<mFiles.size())    	
//        		setBitmap(mFiles.get(position+5), null);

            return ll;
        }

        public void dispose() {    	
        	mFiles.clear();
        	mNames.clear();
        	System.gc();
        }
        
    }
    
        
} 