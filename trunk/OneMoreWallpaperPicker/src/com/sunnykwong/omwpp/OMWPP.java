package com.sunnykwong.omwpp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class OMWPP extends Application {
	
	static public final boolean DEBUG=true;
	static public JSONObject CONFIGJSON;
	static public File SDROOT,THUMBNAILROOT;
	static public long LASTCONFIGREFRESH;
	
	static public Context CONTEXT;
	static public AssetManager AM;
	static public SharedPreferences PREFS;
	
	@Override
	public void onCreate() {

		// TODO Auto-generated method stub
		super.onCreate();
		CONTEXT = this.getApplicationContext();
		AM = this.getAssets();
		PREFS = PreferenceManager.getDefaultSharedPreferences(OMWPP.CONTEXT);
		
		// Check and/or create the wallpapers directory.
        SDROOT = new File(Environment.getExternalStorageDirectory().getPath()+"/ubuntuwps/");
        if (OMWPP.DEBUG) Log.i("OMWPPApp","Checking/Creating " + SDROOT.getAbsoluteFile());
        SDROOT.mkdirs();
        THUMBNAILROOT = getExternalFilesDir(null);
        if (OMWPP.DEBUG) Log.i("OMWPPApp","Checking/Creating " + THUMBNAILROOT.getAbsoluteFile());
        THUMBNAILROOT.mkdirs();
		
		//First of all, let's load up the latest configuration JSON file.
		try {
			if (isSDPresent()) {
		        if (OMWPP.DEBUG) Log.i("OMWPPApp","Loading config file from TN folder");
				CONFIGJSON = streamToJSONObject(new FileInputStream(new File(THUMBNAILROOT.getPath()+ "/omwpp_config.json")));
			} else {
		        if (OMWPP.DEBUG) Log.i("OMWPPApp","No config file in TN folder");
				throw new Exception();
			}
		} catch (Exception e) {
			try {
		        if (OMWPP.DEBUG) Log.i("OMWPPApp","Loading fallback config file");
				CONFIGJSON = streamToJSONObject(OMWPP.AM.open("omwpp_config.json"));
		        if (OMWPP.DEBUG) Log.i("OMWPPApp","Copying fallback config file to TN folder");
				copyAssetToFile("omwpp_config.json", THUMBNAILROOT.getPath()+ "/omwpp_config.json");
			} catch (Exception ee) { e.printStackTrace(); }
		}

		// Figure out when we last downloaded a new config file.
		LASTCONFIGREFRESH = OMWPP.PREFS.getLong("LASTCONFIGREFRESH", 0l);
		
	}
	
	public static boolean copyAssetToFile(String src, String tgt) {
		try {
			FileOutputStream oTGT = new FileOutputStream(tgt);
			InputStream oSRC = OMWPP.AM.open(src);
			
		    byte[] buffer = new byte[8192];
		    int iBytesRead = 0;
		    while ((iBytesRead = oSRC.read(buffer))!= -1){
		    	oTGT.write(buffer,0,iBytesRead);
		    }
		    oTGT.close();
		    oSRC.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	static public boolean isSDPresent() {
		if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
        	Toast.makeText(OMWPP.CONTEXT, "SD Card not detected.\nRemember to turn off USB storage if it's still connected!", Toast.LENGTH_LONG).show();
			return false;
        }

		if (!Environment.getExternalStorageDirectory().canRead()) {
        	Toast.makeText(OMWPP.CONTEXT, "SD Card missing or corrupt.\nRemember to turn off USB storage if it's still connected!", Toast.LENGTH_LONG).show();
        	return false;
        }
		return true;
    }
	
    static public boolean isConnected() {
    	ConnectivityManager conMgr =  (ConnectivityManager)OMWPP.CONTEXT.getSystemService(Context.CONNECTIVITY_SERVICE);
    	boolean result = false;
    	for (NetworkInfo ni: conMgr.getAllNetworkInfo()) {
    		if (ni.isConnected()) {
    			result = true;
    			break;
    		}
    	}
    	return result;
    }
    
    static public JSONObject streamToJSONObject(InputStream is) throws IOException {
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr,8192);
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = br.readLine()) != null){
			sb.append(line+"\n");
		}
		isr.close();
		br.close();
		try {
			return new JSONObject(sb.toString());
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
    }

    /**
     * Unpack a deb archive provided as an input file, to an output directory.
     * <p>
     * 
     * @param inputDeb      the input deb file.
     * @param outputDir     the output directory.
     * @throws IOException 
     * @throws ArchiveException 
     * 
     * @returns A {@link List} of all the unpacked files.
     * 
     */
    public static List<File> unpack(final File inputDeb, final File outputDir) throws IOException, ArchiveException {
    	final List<File> unpackedFiles = new ArrayList<File>();
    	if (OMWPP.DEBUG) {
    		Log.i("OMWPPdeb",String.format("Unzipping deb file %s.", inputDeb.getAbsoluteFile()));
    	}

        final InputStream is = new FileInputStream(inputDeb); 
        final ArArchiveInputStream debInputStream = (ArArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("ar", is);
        ArArchiveEntry entry = null; 
        while ((entry = (ArArchiveEntry)debInputStream.getNextEntry()) != null) {
        	if (OMWPP.DEBUG) Log.i("OMWPPdeb","Read entry");
            final File outputFile = new File(outputDir, entry.getName());
            final OutputStream outputFileStream = new FileOutputStream(outputFile); 
            IOUtils.copy(debInputStream, outputFileStream);
            outputFileStream.close();
            unpackedFiles.add(outputFile);
        }
        debInputStream.close(); 
        return unpackedFiles;
    }

}
