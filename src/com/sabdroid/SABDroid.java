package com.sabdroid;

import java.util.ArrayList;

import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.sabdroid.activity.SettingsActivity;
import com.sabdroid.activity.queue.QueueListRowAdapter;
import com.sabdroid.sabnzbd.SABnzbdController;
import com.sabdroid.util.Calculator;
import com.sabdroid.util.Formatter;

/**
 * Main SABDroid Activity
 */
public class SABDroid extends Activity
{
	private static final int MENU_REFRESH = 1;
	private static final int MENU_SETTINGS = 2;
	private static final int MENU_QUIT = 3;
	private static final int MENU_PLAY_PAUSE = 4;
	private static final int MENU_ADD_NZB = 5;

	final static int DIALOG_SETUP_PROMPT = 999;

	private static ArrayList<String> rows = new ArrayList<String>();
	private static JSONObject backupJsonObject;
	protected boolean paused;

	@SuppressWarnings("unchecked")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.queue);

		SharedPreferences preferences = getSharedPreferences(SABDroidConstants.PREFERENCES_KEY, 0);
		Preferences.update(preferences);
		
		ListView listView = (ListView) findViewById(R.id.queueList);
		listView.setAdapter(new QueueListRowAdapter(this, rows));

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
	 * TODO add configuration to controll the auto updates
	 */
	private void startAutomaticUpdater()
	{
		Thread t = new Thread()
		{
			public void run()
			{
				for (;;)
				{
					try
					{
						Thread.sleep(5000);
					} catch (InterruptedException e)
					{
						e.printStackTrace();
					}
					if (!paused)
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

				updateLabels(jsonObject);
				updateStatus("");
				break;

			case SABnzbdController.MESSAGE_STATUS_UPDATE:
				updateStatus(msg.obj.toString());
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
			((TextView) findViewById(R.id.headerLeft)).setText(Formatter.formatShort(mbleft));
			((TextView) findViewById(R.id.headerDownloaded)).setText(Formatter.formatShort(Double.parseDouble(mb)));
			((TextView) findViewById(R.id.headerSpeed)).setText(Formatter.formatShort(kbpersec));
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
		alert.setMessage("Enter the NZB url to be downloaded:");

		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int whichButton)
			{
				String value = input.getText().toString();
				SABnzbdController.addFile(messageHandler, value);
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
					System.out.println("cancel clicked.");
				}
			};
			return new AlertDialog.Builder(SABDroid.this).setTitle("Would you like to configure SABnzb now?").setPositiveButton(
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

	private void updateStatus(String message)
	{
		TextView status = (TextView) findViewById(R.id.status);
		status.setText(message);
	}
}
