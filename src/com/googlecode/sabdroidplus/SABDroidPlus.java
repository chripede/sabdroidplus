package com.googlecode.sabdroidplus;

import java.util.ArrayList;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.googlecode.sabdroidplus.activity.SettingsActivity;
import com.googlecode.sabdroidplus.activity.queue.QueueListRowAdapter;
import com.googlecode.sabdroidplus.sabnzbd.SABnzbdController;
import com.googlecode.sabdroidplus.util.Calculator;
import com.googlecode.sabdroidplus.util.Formatter;

/**
 * Main SABDroid Activity
 */
public class SABDroidPlus extends Activity
{
	private static final int MENU_REFRESH = 1;
	private static final int MENU_SETTINGS = 2;
	private static final int MENU_QUIT = 3;
	private static final int MENU_PLAY_PAUSE = 4;
	private static final int MENU_ADD_NZB = 5;
	
	private static final int CONTEXT_RENAME = 1;
	private static final int CONTEXT_DELETE = 2;
	private static final int CONTEXT_MOVEUP = 3;
	private static final int CONTEXT_MOVEDOWN = 4;

	final static int DIALOG_SETUP_PROMPT = 999;

	private static ArrayList<String> rows = new ArrayList<String>();
	private static JSONObject backupJsonObject;
	private ListView listView = null;
	protected boolean paused;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.queue);

		SharedPreferences preferences = getSharedPreferences(SABDroidConstants.PREFERENCES_KEY, 0);
		Preferences.update(preferences);
		
		listView = (ListView) findViewById(R.id.queueList);
		listView.setAdapter(new QueueListRowAdapter(this, rows));
		registerForContextMenu(listView);

		// Tries to fetch recoverable data
		Object data[] = (Object[]) getLastNonConfigurationInstance();
		if (data != null)
		{
			rows = (ArrayList<String>) data[0];
			backupJsonObject = (JSONObject) data[1];
			updateLabels(backupJsonObject);
		}

		if (rows.size() > 0)
		{
			ArrayAdapter<String> adapter = (ArrayAdapter<String>) listView.getAdapter();
			adapter.notifyDataSetChanged();
		} else
		{
			manualRefreshQueue();
		}

		startAutomaticUpdater();
	}

	/**
	 * Fires up a new Thread to update the queue every X minutes
	 */
	private void startAutomaticUpdater()
	{
		Thread t = new Thread()
		{
			public void run()
			{
				int sleepTime = 5000;
				boolean refreshDisabled = false;
				WifiManager wifiManager = null;
				
				for (;;)
				{
					try
					{
						refreshDisabled = false;
						
						// Check for "Refresh only on wifi connection"
						if(Preferences.getBoolean(Preferences.REFRESH_ONLY_ON_WIFI, false))
						{
							wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
							if(wifiManager.getConnectionInfo().getSupplicantState() != SupplicantState.COMPLETED)
								refreshDisabled = true;
						}
						
						// Set sleeptime based on the refresh interval selected
						if(!refreshDisabled)
						{
							sleepTime = Integer.parseInt(Preferences.get(Preferences.REFRESH_INTERVAL, "5")) * 1000;
							refreshDisabled = sleepTime == 0;
						}
						
						if(!refreshDisabled)
							Thread.sleep(sleepTime);
						else
							Thread.sleep(10000);
					} 
					catch (InterruptedException e)
					{
						e.printStackTrace();
					}
					if (!paused && !refreshDisabled)
						SABnzbdController.refreshQueue(messageHandler);
				}
			}
		};
		
		t.start();
	}

	@Override
    protected void onPause()
    {
	    super.onPause();
	    paused = true;
    }

	@Override
    protected void onResume()
    {
	    super.onResume();
	    paused = false;
    }

	/**
	 * Refreshing the queue durring startup or on user request. Asks to
	 * configure if still not done
	 */
	void manualRefreshQueue()
	{
		// First run setup
		if (!Preferences.isSet("server_url"))
		{
			showDialog(DIALOG_SETUP_PROMPT);
			return;
		}

		SABnzbdController.refreshQueue(messageHandler);
	}

	// Instantiating the Handler associated with the main thread.
	private Handler messageHandler = new Handler()
	{
		@SuppressWarnings("unchecked")
		public void handleMessage(Message msg)
		{
			switch (msg.what)
			{
			case SABnzbdController.MESSAGE_UPDATE_QUEUE:

				Object result[] = (Object[]) msg.obj;
				// Updating rows
				rows.clear();
				rows.addAll((ArrayList<String>) result[1]);

				ListView listView = (ListView) findViewById(R.id.queueList);
				ArrayAdapter<String> adapter = (ArrayAdapter<String>) listView.getAdapter();
				adapter.notifyDataSetChanged();

				// Updating the header
				JSONObject jsonObject = (JSONObject) result[0];
				backupJsonObject = jsonObject;
				
				// Update paused/downloading status
				ImageView icon = (ImageView) findViewById(R.id.countIcon);
				boolean paused = msg.getData().getBoolean(SABnzbdController.STATUS_PAUSED, false);
				if(paused) {
					icon.setImageResource(R.drawable.icon_pause);
				} else {
					icon.setImageResource(R.drawable.icon);
				}

				updateLabels(jsonObject);
				break;

			case SABnzbdController.MESSAGE_STATUS_UPDATE:
				updateStatus(msg.obj.toString());
				break;
				
			case SABnzbdController.MESSAGE_SHOW_INDERTERMINATE_PROGRESS_BAR:
				setProgressBarIndeterminateVisibility(true);
				break;

			case SABnzbdController.MESSAGE_HIDE_INDERTERMINATE_PROGRESS_BAR:
				setProgressBarIndeterminateVisibility(false);
				break;
				
			case SABnzbdController.MESSAGE_REMOVE_ITEM:
			case SABnzbdController.MESSAGE_MOVED_ITEM:
			case SABnzbdController.MESSAGE_RENAMED_ITEM:
				SABnzbdController.refreshQueue(messageHandler);
				break;

			default:
				break;
			}
		}
	};

	private void updateLabels(JSONObject jsonObject)
	{
		try
		{
			Double mbleft = jsonObject.getDouble("mbleft");
			Double kbpersec = jsonObject.getDouble("kbpersec");
			String mb = jsonObject.getString("mb");
			String diskspace2 = jsonObject.getString("diskspace2");

			((TextView) findViewById(R.id.freeSpace)).setText(Formatter.formatFull(diskspace2));
			((TextView) findViewById(R.id.headerLeft)).setText(Formatter.formatFull(mbleft / 1024));
			((TextView) findViewById(R.id.headerDownloaded)).setText(Formatter.formatFull(Double.parseDouble(mb) / 1024));
			
			TextView headerSpeedType = ((TextView)findViewById(R.id.headerSpeedType));
			TextView headerSpeed = ((TextView) findViewById(R.id.headerSpeed));
			if(kbpersec < 1024) {
				headerSpeedType.setText("KB/s");
				headerSpeed.setText(Formatter.formatShort(kbpersec));
			} else {
				headerSpeedType.setText("MB/s");
				headerSpeed.setText(Formatter.formatFull(kbpersec / 1024));
			}
			
			((TextView) findViewById(R.id.headerEta)).setText(Calculator.calculateETA(mbleft, kbpersec));
		} catch (Throwable e)
		{
			throw new RuntimeException(e);
		}
	}

	/* Creates the menu items */
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_REFRESH, 0, "Refresh").setIcon(R.drawable.ic_menu_refresh);
		menu.add(0, MENU_PLAY_PAUSE, 0, "Pause/Resume").setIcon(R.drawable.ic_menu_play_clip);
		menu.add(0, MENU_ADD_NZB, 0, "Add...").setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, MENU_SETTINGS, 0, "Settings").setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, MENU_QUIT, 0, "Quit").setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case MENU_REFRESH:
			manualRefreshQueue();
			return true;
		case MENU_QUIT:
			System.exit(1);
			return true;
		case MENU_SETTINGS:
			showSettings();
			return true;
		case MENU_PLAY_PAUSE:
			SABnzbdController.pauseResumeQueue(messageHandler);
			return true;
		case MENU_ADD_NZB:
			addDownloadPrompt();
			return true;
		}
		return false;
	}

	private void addDownloadPrompt()
	{
		// First run setup
		if (!Preferences.isSet("server_url"))
		{
			showDialog(DIALOG_SETUP_PROMPT);
			return;
		}

		AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Add new NZB");
		alert.setMessage("Enter the NZB url or Newzbin ID to be downloaded:");

		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				String value = input.getText().toString();
				
				int newzbinId = 0;
				try
				{
					newzbinId = Integer.parseInt(value);
				}
				catch(NumberFormatException e)
				{
				}
				
				if(newzbinId != 0) {
					SABnzbdController.addNewzbinId(messageHandler, newzbinId);
				} else {				
					SABnzbdController.addFile(messageHandler, value);
				}
			}
		});

		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				// Canceled.
			}
		});

		alert.show();
	}

	private void showSettings()
	{
		startActivity(new Intent(this, SettingsActivity.class));
	}

	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
		case DIALOG_SETUP_PROMPT:

			OnClickListener okListener = new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
					showSettings();
					manualRefreshQueue();
				}
			};

			OnClickListener noListener = new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int whichButton)
				{
				}
			};
			return new AlertDialog.Builder(SABDroidPlus.this).setTitle("Setup the connection to SABnzbd+ now?").setPositiveButton(
			        "OK", okListener).setNegativeButton("Cancel", noListener).create();
		}
		return null;
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{
		Object data[] = new Object[2];
		data[0] = rows;
		data[1] = backupJsonObject;
		return data;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		menu.setHeaderTitle("Manage queue item");
		menu.add(0, CONTEXT_RENAME, 0, "Rename");
		menu.add(0, CONTEXT_DELETE, 0, "Delete");
		menu.add(0, CONTEXT_MOVEUP, 0, "Move up");
		menu.add(0, CONTEXT_MOVEDOWN, 0, "Move down");
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		View selectedItem = listView.getAdapter().getView(info.position, null, null);
		final String nzoId = ((TextView)selectedItem.findViewById(R.id.queueRowNzoId)).getText().toString();
		
		switch(item.getItemId())
		{
		case CONTEXT_DELETE:
			SABnzbdController.removeItem(messageHandler, nzoId);
			break;
		case CONTEXT_RENAME:
			AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
			alertDialog.setTitle("Rename");
			alertDialog.setMessage("Enter new name");
			final EditText textInput = new EditText(this);
			String currentName = ((TextView)selectedItem.findViewById(R.id.queueRowLabelFilename)).getText().toString();
			textInput.setText(currentName);
			alertDialog.setView(textInput);
			alertDialog.setPositiveButton("Ok", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					String newName = textInput.getText().toString();
					SABnzbdController.renameItem(messageHandler, nzoId, newName);
				}
			});
			alertDialog.setNegativeButton("Cancel", new OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					// Do nothing
				}
			});
			alertDialog.show();
			break;
		case CONTEXT_MOVEUP:
			int position = info.position == 0 ? 0 : info.position - 1;
			SABnzbdController.moveItem(messageHandler, nzoId, position);
			break;
		case CONTEXT_MOVEDOWN:
			int downPosition = info.position == (listView.getCount()-1) ? (listView.getCount()-1): info.position + 1;
			SABnzbdController.moveItem(messageHandler, nzoId, downPosition);
			break;
		}
		return true;
	}

	private void updateStatus(String message)
	{
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.show();
	}
}
