package com.robert.maps.downloader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.andnav.osm.views.util.StreamUtils;
import org.andnav.osm.views.util.Util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import com.robert.maps.R;
import com.robert.maps.tileprovider.TileSource;
import com.robert.maps.utils.SQLiteMapDatabase;
import com.robert.maps.utils.SimpleThreadFactory;
import com.robert.maps.utils.Ut;

public class MapDownloaderService extends Service {
	private final int THREADCOUNT = 5;

    private NotificationManager mNM;
	private int mZoomArr[];
	private int mCoordArr[];
	private String mMapID;
	private String mOfflineMapName;
	private TileIterator mTileIterator;
	private SQLiteMapDatabase mMapDatabase;
	private TileSource mTileSource;
	private ExecutorService mThreadPool = Executors.newFixedThreadPool(THREADCOUNT, new SimpleThreadFactory("MapDownloaderService"));
	private Handler mHandler = new DownloaderHanler();
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		showNotification();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		handleCommand(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Ut.w("onStartCommand downloader");
		handleCommand(intent);
		return START_STICKY;
	}

	private void handleCommand(Intent intent) {

		mZoomArr = intent.getIntArrayExtra("ZOOM");
		mCoordArr = intent.getIntArrayExtra("COORD");
		mMapID = intent.getStringExtra("MAPID");
		mOfflineMapName = intent.getStringExtra("OFFLINEMAPNAME");
		
		try {
			mTileSource = new TileSource(this, mMapID, true, false);
		} catch (Exception e1) {
			e1.printStackTrace();
		}

		final SQLiteMapDatabase cacheDatabase = new SQLiteMapDatabase();
		final File folder = Ut.getRMapsMapsDir(this);
		final File file = new File(folder.getAbsolutePath()+"/"+mOfflineMapName+".sqlitedb");
		if(file.exists())
			file.delete();
		try {
			cacheDatabase.setFile(file.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
		}
		mMapDatabase = cacheDatabase;
		
		mTileIterator = new TileIterator(mZoomArr, mCoordArr);
		
		for(int i = 0; i < THREADCOUNT; i++)
			mThreadPool.execute(new Downloader(i));
	}

	@Override
	public void onDestroy() {
		Ut.w("onDestroy downloader");
		
		mThreadPool.shutdown();
		if(mTileSource != null)
			mTileSource.Free();
		if(mMapDatabase != null)
			mMapDatabase.Free();
		
		mNM.cancel(R.string.remote_service_started);
		
		super.onDestroy();
	}

	private void showNotification() {
		// In this sample, we'll use the same text for the ticker and the expanded notification
		CharSequence text = getText(R.string.remote_service_started);

		// Set the icon, scrolling text and timestamp
		Notification notification = new Notification(R.drawable.r_download, text, System.currentTimeMillis());
		notification.flags = notification.flags | Notification.FLAG_NO_CLEAR;

		// The PendingIntent to launch our activity if the user selects this notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, AreaSelectorActivity.class), 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, getText(R.string.remote_service_started), text, contentIntent);

		// Send the notification.
		// We use a string id because it is a unique number. We use it later to cancel.
		mNM.notify(R.string.remote_service_started, notification);
	}
	
	private void downloadDone() {
//		((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(R.string.remote_service_started);
//		
//		CharSequence text = getText(R.string.auto_follow_enabled);
//
//		// Set the icon, scrolling text and timestamp
//		Notification notification = new Notification(R.drawable.track_writer_service, text, System.currentTimeMillis());
//		//notification.flags = notification.flags | Notification.FLAG_NO_CLEAR;
//
//		// The PendingIntent to launch our activity if the user selects this notification
//		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
//				new Intent(this, AreaSelectorActivity.class), 0);
//
//		// Set the info for the views that show in the notification panel.
//		notification.setLatestEventInfo(this, getText(R.string.auto_follow_enabled), text, contentIntent);
//
//		// Send the notification.
//		// We use a string id because it is a unique number. We use it later to cancel.
//		((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(R.string.auto_follow_enabled, notification);
		
		stopSelf();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	private class DownloaderHanler extends Handler {
		public static final int DONE = 0;
		private int doneCounter = 0;

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case DONE:
				doneCounter++;
				Ut.d("DONE "+doneCounter);
				if(doneCounter >= THREADCOUNT)
					downloadDone();
				break;
			}
		}
		
	}
	
	private class Downloader implements Runnable {
		private int mID;

		public Downloader(int id) {
			mID = id;
		}

		public void run() {
			
			XYZ tileParam = null;
			boolean continueExecute = true;
			
			while (continueExecute) {
				synchronized (mTileIterator) {
					if (mTileIterator.hasNext()) {
						tileParam = mTileIterator.next();
					} else {
						continueExecute = false;
						tileParam = null;
					}
				}
				
				
				if (tileParam != null) {
					tileParam.TILEURL = mTileSource.getTileURLGenerator().Get(tileParam.X, tileParam.Y, tileParam.Z);
					InputStream in = null;
					OutputStream out = null;
					
					try {
						Ut.i("Downloading #"+mID+" from url: " + tileParam.TILEURL);
						
						byte[] data = null;
						
						in = new BufferedInputStream(new URL(tileParam.TILEURL).openStream(),
								StreamUtils.IO_BUFFER_SIZE);
						
						final ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
						out = new BufferedOutputStream(dataStream, StreamUtils.IO_BUFFER_SIZE);
						StreamUtils.copy(in, out);
						out.flush();
						
						data = dataStream.toByteArray();
						
						if (data != null) {
							mMapDatabase.putTile(tileParam.X, tileParam.Y, tileParam.Z, data);
						}
						
						// SendMessageSuccess();
					} catch (Exception e) {
						// SendMessageFail();
					} catch (OutOfMemoryError e) {
						// SendMessageFail();
						System.gc();
					} finally {
						StreamUtils.closeStream(in);
						StreamUtils.closeStream(out);
					}
				}
			}
			
			if(mHandler != null)
				Message.obtain(mHandler, DownloaderHanler.DONE).sendToTarget();
		}

	}
	
	private class TileIterator {
		private int zInd = -1, zArr[];
		private int x, xMin = 0, xMax = 0;
		private int y, yMin = 0, yMax = 0;
		private int coordArr[];
		
		public TileIterator(int zarr[], int coordarr[]) {
			zArr = zarr;
			zInd = -1;
			x = 1;
			y = 1;
			coordArr = coordarr;
		}

		public boolean hasNext() {
			x++;
			if(x > xMax) {
				y++;
				x = xMin;
				if(y > yMax) {
					zInd++;
					y = yMin;
					if(zInd > zArr.length - 1) {
						return false;
					}
					final int c0[] = Util.getMapTileFromCoordinates(coordArr[0], coordArr[1], zArr[zInd], null, mTileSource.PROJECTION);
					final int c1[] = Util.getMapTileFromCoordinates(coordArr[2], coordArr[3], zArr[zInd], null, mTileSource.PROJECTION);
					xMin = Math.min(c0[0], c1[0]);
					xMax = Math.max(c0[0], c1[0]);
					yMin = Math.min(c0[1], c1[1]);
					yMax = Math.max(c0[1], c1[1]);
					x = xMin;
					y = yMin;
				}
			}
			
			return true;
		}

		public XYZ next() {
			try {
				return new XYZ(null, x, y, zArr[zInd]);
			} catch (Exception e) {
				return null;
			}
		}

	}
	
	private class XYZ {
		public String TILEURL;
		public int X;
		public int Y;
		public int Z;
		
		public XYZ(final String tileurl, final int x, final int y, final int z) {
			TILEURL = tileurl;
			X = x;
			Y = y;
			Z = z;
		}
	}
}
