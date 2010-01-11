package com.sabdroid.activity.queue;

import java.util.ArrayList;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.sabdroid.R;
import com.sabdroid.sabnzbd.SABnzbdController;
import com.sabdroid.util.Calculator;
import com.sabdroid.util.Formatter;

public class QueueListRowAdapter extends ArrayAdapter<String>
{
	Activity context;
	private ArrayList<String> items = new ArrayList<String>();

	public QueueListRowAdapter(Activity context, ArrayList<String> items)
	{
		super(context, R.layout.queue_list_item, items);

		this.context = context;
		this.items = items;
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		LayoutInflater inflater = context.getLayoutInflater();
		View row = inflater.inflate(R.layout.queue_list_item, null);

		String[] values = items.get(position).split("#");
		
		((TextView) row.findViewById(R.id.queueRowLabelFilename)).setText(values[0]);
		
		String eta = Calculator.calculateETA(Double.parseDouble(values[2]), SABnzbdController.speed);
		
		((TextView) row.findViewById(R.id.queueRowLabelEta)).setText(eta);
		
		String completed = Formatter.formatShort(values[2]) + " / " + Formatter.formatShort(values[1]) + " MB";
		
		((TextView) row.findViewById(R.id.queueRowLabelCompleted)).setText(completed);

		return (row);
	}
}
