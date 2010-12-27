package com.googlecode.sabdroidplus.activity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.Activity;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;

import com.googlecode.sabdroidplus.R;
import com.googlecode.sabdroidplus.sabnzbd.SABnzbdController;

public class OpenNzbFile extends Activity {

	private Uri nzbUri = null;
	private String[] categories = new String[0];
	private Spinner categorySpinner = null;
	private ArrayAdapter<String> arrayAdapter = null;
	private ProgressDialog progressDialog;
	private EditText nzbUrl = null;
	
	private Handler messageHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case SABnzbdController.MESSAGE_LOAD_CATEGORIES:
				categories = (String[]) msg.obj;
				for (String category : categories) {
					arrayAdapter.add(category);
				}
				categorySpinner.setAdapter(arrayAdapter);
				progressDialog.dismiss();
				break;
			case SABnzbdController.MESSAGE_STATUS_UPDATE:
				updateStatus(msg.obj.toString());
				break;

			default:
				break;
			}
		}
	};
	
	private void updateStatus(String message)
	{
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.show();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTitle("Queue NZB");
		setContentView(R.layout.opennzbfile);
		
		categorySpinner = (Spinner) findViewById(R.id.openNzbFileCategory);
		arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
		arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		
		nzbUri = getIntent().getData();
		if(nzbUri != null)
		{
			nzbUrl = (EditText) findViewById(R.id.openNzbFileUrl);
			nzbUrl.setText(nzbUri.toString());
			nzbUrl.setEnabled(false);
		}
		
		((Button) findViewById(R.id.openNzbFileOk)).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				try {
					SABnzbdController.addFile(messageHandler, URLEncoder.encode(nzbUrl.getText().toString(), "utf-8"), categorySpinner.getSelectedItem().toString());
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				finish();
			}
		});
		
		((Button) findViewById(R.id.openNzbFileCancel)).setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				finish();
			}
		});
		
		progressDialog = ProgressDialog.show(this, "Loading categories", "Please wait while categories are being fetched", true);
		SABnzbdController.getCategories(messageHandler);
	}

}
