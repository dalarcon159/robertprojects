package com.robert.maps.kml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;

import com.robert.maps.R;
import com.robert.maps.kml.Track.TrackPoint;
import com.robert.maps.kml.XMLparser.SimpleXML;
import com.robert.maps.kml.constants.PoiConstants;
import com.robert.maps.utils.Ut;

public class PoiListActivity extends ListActivity {
	private PoiManager mPoiManager;
	private ProgressDialog dlgWait;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.poi_list);
        registerForContextMenu(getListView());
        mPoiManager = new PoiManager(this);
	}

	@Override
	protected void onDestroy() {
		mPoiManager.FreeDatabases();
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		FillData();
		super.onResume();
	}

	private void FillData() {
		Cursor c = mPoiManager.getGeoDatabase().getPoiListCursor();
        startManagingCursor(c);

        ListAdapter adapter = new SimpleCursorAdapter(this,
                R.layout.poilist_item, c,
                        new String[] { "name", "iconid", "poititle2", "descr" },
                        new int[] { R.id.title1, R.id.pic, R.id.title2, R.id.descr});
        setListAdapter(adapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.poilist_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		super.onOptionsItemSelected(item);

		switch(item.getItemId()){
		case R.id.menu_addpoi:
			final Intent PoiIntent = new Intent(this, PoiActivity.class); 
	        Bundle extras = getIntent().getExtras();
	        if(extras != null){
	        	PoiIntent.putExtra("lat", extras.getDouble("lat")).putExtra("lon", extras.getDouble("lon")).putExtra("title", extras.getString("title"));
	        }
			startActivity(PoiIntent);
			return true;
		case R.id.menu_categorylist:
			startActivity((new Intent(this, PoiCategoryListActivity.class)));
			return true;
		case R.id.menu_importpoi:
			startActivity((new Intent(this, ImportPoiActivity.class)));
			return true;
		case R.id.menu_deleteall:
			showDialog(R.id.menu_deleteall);
			return true;
		case R.id.menu_exportgpx:
			DoExportGpx();
			return true;
		case R.id.menu_exportkml:
			DoExportKml();
		}

		return true;
	}

	private void DoExportKml() {
		dlgWait = Ut.ShowWaitDialog(this, 0);
		
		new ExportKmlTask().execute();
	}
	
	class ExportKmlTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			SimpleXML xml = new SimpleXML("kml");
			xml.setAttr("xmlns:gx", "http://www.google.com/kml/ext/2.2");
			xml.setAttr("xmlns", "http://www.opengis.net/kml/2.2");
			SimpleXML fold = xml.createChild("Folder");
			
			Cursor c = mPoiManager.getGeoDatabase().getPoiListCursor();
			PoiPoint poi = null;
			
			if(c != null) {
				if(c.moveToFirst()) {
					do {
						poi = mPoiManager.getPoiPoint(c.getInt(4));
						
						SimpleXML wpt = fold.createChild("Placemark");
						wpt.createChild(PoiConstants.NAME).setText(poi.Title);
						wpt.createChild(PoiConstants.DESCRIPTION).setText(poi.Descr);
						SimpleXML point = wpt.createChild("Point");
						point.createChild("coordinates").setText(new StringBuilder().append(poi.GeoPoint.getLongitude()).append(",").append(poi.GeoPoint.getLatitude()).toString());
						
					} while(c.moveToNext());
				};
				c.close();
			}

			File folder = Ut.getRMapsExportDir(PoiListActivity.this);
			String filename = folder.getAbsolutePath() + "/poilist.kml";
			File file = new File(filename);
			FileOutputStream out;
			try {
				file.createNewFile();
				out = new FileOutputStream(file);
				OutputStreamWriter wr = new OutputStreamWriter(out);
				wr.write(SimpleXML.saveXml(xml));
				wr.close();
				return PoiListActivity.this.getResources().getString(R.string.message_poiexported, filename);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return PoiListActivity.this.getResources().getString(R.string.message_error, e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
				return PoiListActivity.this.getResources().getString(R.string.message_error, e.getMessage());
			}

		}

		@Override
		protected void onPostExecute(String result) {
			dlgWait.dismiss();
			Toast.makeText(PoiListActivity.this, result, Toast.LENGTH_LONG).show();
			super.onPostExecute(result);
		}
		
	}

	private void DoExportGpx() {
		dlgWait = Ut.ShowWaitDialog(this, 0);
		
		new ExportGpxTask().execute();
	}

	class ExportGpxTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			//SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			SimpleXML xml = new SimpleXML("gpx");
			xml.setAttr("xsi:schemaLocation", "http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd");
			xml.setAttr("xmlns", "http://www.topografix.com/GPX/1/0");
			xml.setAttr("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			xml.setAttr("creator", "RMaps - http://code.google.com/p/robertprojects/");
			xml.setAttr("version", "1.0");
			
			Cursor c = mPoiManager.getGeoDatabase().getPoiListCursor();
			PoiPoint poi = null;
			
			if(c != null) {
				if(c.moveToFirst()) {
					do {
						poi = mPoiManager.getPoiPoint(c.getInt(4));
						
						SimpleXML wpt = xml.createChild("wpt");
						wpt.setAttr(PoiConstants.LAT, Double.toString(poi.GeoPoint.getLatitude()));
						wpt.setAttr(PoiConstants.LON, Double.toString(poi.GeoPoint.getLongitude()));
						wpt.createChild(PoiConstants.ELE).setText(Double.toString(poi.Alt));
						wpt.createChild(PoiConstants.NAME).setText(poi.Title);
						wpt.createChild(PoiConstants.DESC).setText(poi.Descr);
						wpt.createChild(PoiConstants.TYPE).setText(mPoiManager.getPoiCategory(poi.CategoryId).Title);
						/*SimpleXML ext =*/ xml.createChild(PoiConstants.EXTENSIONS);
						
					} while(c.moveToNext());
				};
				c.close();
			}

			File folder = Ut.getRMapsExportDir(PoiListActivity.this);
			String filename = folder.getAbsolutePath() + "/poilist.gpx";
			File file = new File(filename);
			FileOutputStream out;
			try {
				file.createNewFile();
				out = new FileOutputStream(file);
				OutputStreamWriter wr = new OutputStreamWriter(out);
				wr.write(SimpleXML.saveXml(xml));
				wr.close();
				return PoiListActivity.this.getResources().getString(R.string.message_poiexported, filename);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				return PoiListActivity.this.getResources().getString(R.string.message_error, e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
				return PoiListActivity.this.getResources().getString(R.string.message_error, e.getMessage());
			}
		}

		@Override
		protected void onPostExecute(String result) {
			dlgWait.dismiss();
			Toast.makeText(PoiListActivity.this, result, Toast.LENGTH_LONG).show();
			super.onPostExecute(result);
		}
		
	};
	
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case R.id.menu_deleteall:
			return new AlertDialog.Builder(this)
				//.setIcon(R.drawable.alert_dialog_icon)
				.setTitle(R.string.warning_delete_all_poi)
				.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {
									mPoiManager.DeleteAllPoi();
									FillData();
								}
							}).setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int whichButton) {

									/* User clicked Cancel so do some stuff */
								}
							}).create();
		}
		;

		return super.onCreateDialog(id);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		int pointid = (int) ((AdapterView.AdapterContextMenuInfo)menuInfo).id;
		PoiPoint poi = mPoiManager.getPoiPoint(pointid);

		menu.add(0, R.id.menu_gotopoi, 0, getText(R.string.menu_goto));
		menu.add(0, R.id.menu_editpoi, 0, getText(R.string.menu_edit));
		if(poi.Hidden)
			menu.add(0, R.id.menu_show, 0, getText(R.string.menu_show));
		else
			menu.add(0, R.id.menu_hide, 0, getText(R.string.menu_hide));
		menu.add(0, R.id.menu_deletepoi, 0, getText(R.string.menu_delete));
		menu.add(0, R.id.menu_toradar, 0, getText(R.string.menu_toradar));

		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		int pointid = (int) ((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).id;
		PoiPoint poi = mPoiManager.getPoiPoint(pointid);

		switch(item.getItemId()){
		case R.id.menu_editpoi:
			startActivity((new Intent(this, PoiActivity.class)).putExtra("pointid", pointid));
			break;
		case R.id.menu_gotopoi:
			setResult(RESULT_OK, (new Intent()).putExtra("pointid", pointid));
			finish();
			break;
		case R.id.menu_deletepoi:
			mPoiManager.deletePoi(pointid);
			FillData();
	        break;
		case R.id.menu_hide:
			poi.Hidden = true;
			mPoiManager.updatePoi(poi);
			FillData();
	        break;
		case R.id.menu_show:
			poi.Hidden = false;
			mPoiManager.updatePoi(poi);
			FillData();
	        break;
		case R.id.menu_toradar:
			try {
					Intent i = new Intent("com.google.android.radar.SHOW_RADAR");
					i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
					i.putExtra("name", poi.Title);
					i.putExtra("latitude",  (float)(poi.GeoPoint.getLatitudeE6() / 1000000f));
					i.putExtra("longitude", (float)(poi.GeoPoint.getLongitudeE6() / 1000000f));
					startActivity(i);
				} catch (Exception e) {
					Toast.makeText(this, R.string.message_noradar, Toast.LENGTH_LONG).show();
				}
			break;
		}

		return super.onContextItemSelected(item);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
	}

}
