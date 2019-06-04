/*  This file is part of DroidPad.
 *
 *  DroidPad is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  DroidPad is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with DroidPad.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.digitalsquid.droidpad;

import java.io.IOException;
import java.util.List;

import uk.digitalsquid.droidpad.Pairing.DevicePair;
import uk.digitalsquid.droidpad.buttons.Layout;
import uk.digitalsquid.droidpad.buttons.ModeSpec;
import android.annotation.TargetApi;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.TabActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * This is the new, much improved main UI for DroidPad.
 * @author william
 *
 */
@SuppressWarnings("deprecation")
public class DroidPad extends TabActivity implements OnClickListener, OnItemClickListener, LogTag {
	
	TabHost tabHost;
	
	// Global state, for getting layouts. In the future, these could also be user defined.
	App app;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		app = (App) getApplication();
		setContentView(R.layout.main2);
		// tabHost = (TabHost) findViewById(android.R.id.tabhost);
		tabHost = getTabHost();
		
		app.rescanFiles();
		
		// set up tabs
		TabSpec spec;
		spec = tabHost.newTabSpec("jsTab");
		spec.setIndicator(
				getResources().getString(R.string.js),
				getResources().getDrawable(R.drawable.ic_tab_js));
		spec.setContent(R.id.jsTab);
		tabHost.addTab(spec);
		
		// Also create the list adapter for each tab.
		jsList = (ListView) findViewById(R.id.jsList);
		jsModes = new ModeListAdapter(app.getLayouts(ModeSpec.LAYOUTS_JS));
		jsList.setAdapter(jsModes);
		jsList.setOnItemClickListener(this);
		
		spec = tabHost.newTabSpec("mouseTab");
		spec.setIndicator(
				getResources().getString(R.string.mouse),
				getResources().getDrawable(R.drawable.ic_tab_mouse));
		spec.setContent(R.id.mouseTab);
		tabHost.addTab(spec);
		
		mouseList = (ListView) findViewById(R.id.mouseList);
		mouseModes = new ModeListAdapter(app.getLayouts(ModeSpec.LAYOUTS_MOUSE));
		mouseList.setAdapter(mouseModes);
		mouseList.setOnItemClickListener(this);
		
		spec = tabHost.newTabSpec("slideTab");
		spec.setIndicator(
				getResources().getString(R.string.slideshow),
				getResources().getDrawable(R.drawable.ic_tab_slide));
		spec.setContent(R.id.slideTab);
		tabHost.addTab(spec);
		
		slideList = (ListView) findViewById(R.id.slideList);
		slideModes = new ModeListAdapter(app.getLayouts(ModeSpec.LAYOUTS_SLIDE));
		slideList.setAdapter(slideModes);
		slideList.setOnItemClickListener(this);
		
		if(getIntent().getData() != null) {
			Uri uri = getIntent().getData();
			Log.i(TAG, "Received data with URI:");
			Log.i(TAG, "Host="+uri.getHost());
			if(uri.getHost().equals("droidpad-pair.digitalsquid.co.uk")) {
				// Correct URI, so add new pairing
				try {
					String computerId = uri.getQueryParameter("computerId");
					String computerName = uri.getQueryParameter("computerName");
					String deviceId = uri.getQueryParameter("deviceId");
					String psk = uri.getQueryParameter("psk");
					
					DevicePair newPair = app.getPairingEngine().pairNewDevice(computerId, computerName, deviceId, psk);
					tmpDeviceName = newPair.getComputerName();
					showDialog(DIALOG_PAIRSUCCESS);
				} catch(UnsupportedOperationException e) {
					Log.w(TAG, "Failed to decode device pair URI", e);
					showDialog(DIALOG_PAIRFAILED);
				} catch (IllegalArgumentException e) {
					Log.e(TAG, "Failed to decode barcode content", e);
					showDialog(DIALOG_PAIRFAILED);
				} catch (IOException e) {
					Log.e(TAG, "Failed to save new pairing content to DB", e);
					// TODO: Show a different dialog?
					showDialog(DIALOG_PAIRFAILED);
				}
			}
		}
		
		// TODO: Remove in release
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
		if(prefs.getInt("firstTime", 1) == 1) {
			prefs.edit().putInt("firstTime", 0).commit();
			showDialog(DIALOG_UPGRADE);
		}
	}
	
	static final int DIALOG_UPGRADE = 1;
	static final int DIALOG_PAIRFAILED = 2;
	static final int DIALOG_PAIRSUCCESS = 3;
	static final int DIALOG_EDITLAYOUT = 4;
	
	@Override
	public Dialog onCreateDialog(int id) {
		Builder builder = new Builder(this);
		switch(id) {
		case DIALOG_UPGRADE: // Temp dialog to show new version needs to be dl'd
			builder.setTitle(R.string.app_name);
			builder.setMessage(R.string.welcome);
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			return builder.create();
		case DIALOG_PAIRSUCCESS:
			builder.setTitle(R.string.pairsuccesstitle);
			builder.setMessage(getResources().getString(R.string.pairsuccessdescription, tmpDeviceName));
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			return builder.create();
		case DIALOG_PAIRFAILED:
			builder.setTitle(R.string.pairfailedtitle);
			builder.setMessage(R.string.pairfaileddescription);
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			return builder.create();
		case DIALOG_EDITLAYOUT:
			builder.setTitle(R.string.editlayouttitle);
			builder.setMessage(R.string.editlayoutdescription);
			builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			return builder.create();
		}
		return null;
	}
	
	@Override
	public void onClick(View arg0) {
	}
	
	private ModeListAdapter jsModes, mouseModes, slideModes;
	private ListView jsList, slideList, mouseList;
	
	private class ModeListAdapter extends BaseAdapter {
		private final LayoutInflater inflater;
		
		private List<Layout> modes;
		
		public ModeListAdapter(List<Layout> modes) {
			this.modes = modes;
			inflater = LayoutInflater.from(DroidPad.this);
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
	        if (convertView == null) {
	            convertView = inflater.inflate(R.layout.layoutitem, null);
	        }
	        
	        TextView title = (TextView) convertView.findViewById(R.id.title);
	        TextView description = (TextView) convertView.findViewById(R.id.description);
	        
        	title.setText(modes.get(position).getTitle());
        	description.setText(modes.get(position).getDescription());

            return convertView;
		}
		
		@Override
		public long getItemId(int position) {
			return position;
		}
		
		@Override
		public Layout getItem(int position) {
			if(modes == null) return null;
			return modes.get(position);
		}
		
		@Override
		public int getCount() {
			if(modes == null) return 0;
			return modes.size();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		int type = -1;
		Layout layout = (Layout) parent.getItemAtPosition(position);
		if(parent.equals(jsList))
			type = ModeSpec.LAYOUTS_JS;
		else if(parent.equals(mouseList))
			type = ModeSpec.LAYOUTS_MOUSE;
		else if(parent.equals(slideList))
			type = ModeSpec.LAYOUTS_SLIDE;
		Log.v(TAG, "Using layout type " + type + ", \"" + layout.getTitle() + "\".");
		
		Intent intent = new Intent(this, Buttons.class);
		ModeSpec spec = new ModeSpec();
		spec.setLayout(layout);
		if(layout.getExtraDetail() == Layout.EXTRA_MOUSE_ABSOLUTE)
			type = ModeSpec.LAYOUTS_MOUSE_ABS; // Special case
		spec.setMode(type);
		intent.putExtra(Buttons.MODE_SPEC, spec);
		startActivity(intent);
	}
	
    @TargetApi(11)
	@Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.menu, menu);
    	// Item 0 should be settings
    	if(Build.VERSION.SDK_INT >= 11)
	    	menu.getItem(0).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		return true;
    	
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (!item.hasSubMenu()) {
    		Intent i;
    		switch (item.getItemId()) {
    		case R.id.wifi:
    			i = new Intent(Settings.ACTION_WIFI_SETTINGS);
    			try
    			{
    				startActivity(i);
    			}
    			catch (ActivityNotFoundException a)
    			{
    				Toast.makeText(getBaseContext(), "Could not launch Wifi settings.", Toast.LENGTH_SHORT).show();
    			}
    			break;
    		case R.id.website:
    			i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://digitalsquid.co.uk/droidpad/"));
    			try {
    				startActivity(i);
    			} catch (ActivityNotFoundException a) {
    				Toast.makeText(getBaseContext(), "Could not launch Browser.", Toast.LENGTH_SHORT).show();
    			}
    			break;
    		case R.id.pairnewdevice:
    			Toast.makeText(this, "Please scan the barcode created by the software on your computer.", Toast.LENGTH_LONG).show();
    			IntentIntegrator barcodeIntegrator = new IntentIntegrator(this);
    			barcodeIntegrator.setMessageByID(R.string.pairdevicemessage);
    			barcodeIntegrator.initiateScan();
    			break;
    		case R.id.settings:
    			i = new Intent(this, SettingsMenu.class);
    			startActivity(i);
    			break;
    		case R.id.about:
    			i = new Intent(this, AboutActivity.class);
    			startActivity(i);
    			break;
    		case R.id.customlayouthelp:
    			showDialog(DIALOG_EDITLAYOUT);
    			break;
    		case R.id.gettingstarted:
    			i = new Intent(this, WebActivity.class);
    			startActivity(i);
    			break;
    		}
    	}
		return true;
    }
    
    /**
     * Temp variable used by successful dialog box.
     */
    private String tmpDeviceName;
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    	super.onActivityResult(requestCode, resultCode, intent);
    	IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
    	if (scanResult != null) {
    		try {
				DevicePair newPair = app.getPairingEngine().pairNewDevice(scanResult.getContents());
				tmpDeviceName = newPair.getComputerName();
				showDialog(DIALOG_PAIRSUCCESS);
			} catch (IllegalArgumentException e) {
				Log.e(TAG, "Failed to decode barcode content", e);
				showDialog(DIALOG_PAIRFAILED);
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "Failed to save new pairing content to DB", e);
				// TODO: Show a different dialog?
				showDialog(DIALOG_PAIRFAILED);
			}
    		return;
    	}
	}
}
