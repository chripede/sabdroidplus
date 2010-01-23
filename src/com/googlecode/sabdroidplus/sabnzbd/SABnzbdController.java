package com.googlecode.sabdroidplus.sabnzbd;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.googlecode.sabdroidplus.Preferences;
import com.googlecode.sabdroidplus.util.HttpUtil;
import com.googlecode.sabdroidplus.util.HttpUtil.ServerConnectionException;

public final class SABnzbdController
{
    public static double speed = 0.0;
    public static boolean paused = false;

    public static final int MESSAGE_UPDATE_QUEUE = 1;
    public static final int MESSAGE_STATUS_UPDATE = 2;

	public static final int MESSAGE_SHOW_INDERTERMINATE_PROGRESS_BAR = 3;
	public static final int MESSAGE_HIDE_INDERTERMINATE_PROGRESS_BAR = 4;
	
	public final static int MESSAGE_LOAD_CATEGORIES = 5;
	public final static int MESSAGE_ADD_NZB = 6;

	public static final int MESSAGE_REMOVE_ITEM = 7;
	public static final int MESSAGE_MOVED_ITEM = 8;
	public static final int MESSAGE_RENAMED_ITEM = 9;

    public static final String STATUS_PAUSED = "paused";

    private static boolean executingRefreshQuery = false;
    private static boolean executingCommand = false;

    private static final String URL_TEMPLATE = "http://[SERVER_URL]/api?mode=[COMMAND]&output=json";

    /**
     * Pauses or resumes the queue depending on the current status
     */
    public static void pauseResumeQueue(final Handler messageHandler)
    {
        // Already running or settings not ready
        if (executingCommand || !Preferences.isSet(Preferences.SERVER_URL))
            return;

        Thread thread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    String result = "";
                    if (paused)
                    {
                        result = makeApiCall("resume");
                    }
                    else
                    {
                        result = makeApiCall("pause");
                    }

                    if (result.startsWith("ok"))
                    {
                        paused = !paused;

                        // Reloading the queue
                        SABnzbdController.refreshQueue(messageHandler);
                    }
                }
                catch (Throwable e)
                {
                    // Ignore for now
                    // TODO do something different
                }
                finally
                {
                    executingCommand = false;
                }
            }
        };
        executingCommand = true;

        if (paused)
            sendUpdateMessageStatus(messageHandler, "Resuming queue...");
        else
            sendUpdateMessageStatus(messageHandler, "Pausing queue...");

        thread.start();
    }

    public static void refreshQueue(final Handler messageHandler)
    {
        // Already running or settings not ready
        if (executingRefreshQuery || !Preferences.isSet(Preferences.SERVER_URL))
            return;

        Thread thread = new Thread()
        {
            public void run()
            {
            	try
                {
                	Message progressMessage = new Message();
                	progressMessage.setTarget(messageHandler);
                	progressMessage.what =  MESSAGE_SHOW_INDERTERMINATE_PROGRESS_BAR;
                	progressMessage.sendToTarget();
                	
                    Object results[] = new Object[2];

                    String queueData = makeApiCall("qstatus");

                    ArrayList<Object> rows = new ArrayList<Object>();

                    JSONObject jsonObject = new JSONObject(queueData);
                    speed = jsonObject.getDouble("kbpersec");
                    if (jsonObject.get("paused") == null)
                        paused = false;
                    else
                    {
                        // Due to a bug(?) on sabnzbd right after a restart this field is "null" as a string
                        // parseBoolean should take care of that since anything but "true" is considered false
                        paused = Boolean.parseBoolean(jsonObject.getString("paused"));
                    }
                    
                    results[0] = jsonObject;

                    JSONArray jobs = jsonObject.getJSONArray("jobs");
                    rows.clear();
                    for (int i = 0; i < jobs.length(); i++)
                    {
                        String rowValues = jobs.getJSONObject(i).get("filename").toString();
                        rowValues = rowValues + "#" + jobs.getJSONObject(i).getDouble("mb");
                        rowValues = rowValues + "#" + jobs.getJSONObject(i).getDouble("mbleft");
                        rowValues = rowValues + "#" + jobs.getJSONObject(i).getString("id");

                        rows.add(rowValues);
                    }

                    results[1] = rows;

                    Bundle bundle = new Bundle(1);
                    bundle.putBoolean(STATUS_PAUSED, paused);
                    
                    Message message = new Message();
                    message.setTarget(messageHandler);
                    message.what = MESSAGE_UPDATE_QUEUE;
                    message.obj = results;
                    message.setData(bundle);
                    message.sendToTarget();
                }
                catch (ServerConnectionException e)
                {
                    sendUpdateMessageStatus(messageHandler, e.getMessage());
                }
                catch (Throwable e)
                {
                    Log.w("ERROR", e);
                }
                finally
                {
                	Message progressMessage = new Message();
                	progressMessage.setTarget(messageHandler);
                	progressMessage.what =  MESSAGE_HIDE_INDERTERMINATE_PROGRESS_BAR;
                	progressMessage.sendToTarget();

                    executingRefreshQuery = false;
                }
            }
        };

        executingRefreshQuery = true;

        //sendUpdateMessageStatus(messageHandler, "Updating....");

        thread.start();
    }

    /**
     * Sends a message to the calling {@link Activity} to update it's status bar
     */
    private static void sendUpdateMessageStatus(Handler messageHandler, String text)
    {
        Message message = new Message();
        message.setTarget(messageHandler);
        message.what = MESSAGE_STATUS_UPDATE;
        message.obj = text;
        message.sendToTarget();
    }

    public static String makeApiCall(String command, String xTraParams) throws ServerConnectionException
    {
        String url = URL_TEMPLATE;
        url = url.replace("[SERVER_URL]", fixUrlFromPreferences(Preferences.get(Preferences.SERVER_URL)));
        String apiKey = Preferences.get(Preferences.API_KEY);
        if (!apiKey.trim().equals(""))
        {
            url = url + "&apikey=" + apiKey;
        }
        url = url.replace("[COMMAND]", command);
        url = url + getPreferencesParams();
        if (xTraParams != null && !xTraParams.trim().equals(""))
        {
            url = url + "&" + xTraParams;
        }

        return HttpUtil.instance().getData(url);
    }

    /**
     * Removes the http if included in the settings URL
     * @param string
     * @return
     */
    private static CharSequence fixUrlFromPreferences(String url)
    {
        if (url.toUpperCase().startsWith("HTTP://"))
        {
            return url.substring(7);
        }
        return url;
    }

    private static String getPreferencesParams()
    {
        String username = Preferences.get(Preferences.SERVER_USERNAME);
        String password = Preferences.get(Preferences.SERVER_PASSWORD);

        if (username != null && password != null)
        {
            return "&ma_username=" + username + "&ma_password=" + password;
        }

        return "";
    }

    public static String makeApiCall(String command) throws ServerConnectionException
    {
        return makeApiCall(command, "");
    }
    
    public static void addNewzbinId(final Handler messageHandler, final int value)
    {
    	if(!Preferences.isSet(Preferences.SERVER_URL))
    		return;
    	
    	Thread thread = new Thread()
    	{
    		public void run()
    		{
    			try
    			{
    				makeApiCall("addid", "name=" + value);
    			}
    			catch(Exception e)
    			{
    			}
    		}
    	};
    	
    	sendUpdateMessageStatus(messageHandler, "Adding Newzbin ID to queue...");
    	
    	thread.start();
    }
    
    public static void addFile(final Handler messageHandler, final String nzbUrl, final String category)
    {
        if (!Preferences.isSet(Preferences.SERVER_URL))
            return;

        Thread thread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    makeApiCall("addurl", "cat=" + category + "&name=" + nzbUrl);
                }
                catch (Exception e)
                {
                }
            }
        };

        sendUpdateMessageStatus(messageHandler, "Adding new download to queue...");

        thread.start();
    }

    public static void addFile(final Handler messageHandler, final String value)
    {
        // Already running or settings not ready
        if (executingCommand || !Preferences.isSet(Preferences.SERVER_URL))
            return;

        Thread thread = new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    makeApiCall("addurl", "name=" + value);
                }
                catch (Exception e)
                {
                }
            }
        };

        sendUpdateMessageStatus(messageHandler, "Adding new download to queue...");

        thread.start();
    }
    
    public static void getCategories(final Handler messageHandler)
    {
    	Thread thread = new Thread()
    	{
    		@Override
    		public void run()
    		{
    			String[] categories = null;
    	    	try 
    	    	{
    				String jsonCategories = makeApiCall("get_cats");
    	            JSONObject jsonObject = new JSONObject(jsonCategories);
    	            JSONArray categoriesArray = jsonObject.getJSONArray("categories");
    	            
    	        	categories = new String[categoriesArray.length()];
    	            for (int i = 0; i < categoriesArray.length(); i++)
    	            {
    	                categories[i] = categoriesArray.getString(i);
    	            }
    	    	} 
    	    	catch (Exception e) 
    	    	{
    	    		categories = new String[] { "None" };
    			}
    	    	
    	    	Message msg = new Message();
    	    	msg.setTarget(messageHandler);
    	    	msg.what = MESSAGE_LOAD_CATEGORIES;
    	    	msg.obj = categories;
    	    	msg.sendToTarget();
    		}
    	};
    	
    	thread.start();
    }
    
    public static void removeItem(final Handler messageHandler, final String nzoId)
    {
    	Thread thread = new Thread()
    	{
			@Override
			public void run() {
				try {
					makeApiCall("queue", "name=delete&value=" + nzoId);
				} catch (ServerConnectionException e) {
					e.printStackTrace();
				}
				
				Message msg = new Message();
				msg.setTarget(messageHandler);
				msg.what = MESSAGE_REMOVE_ITEM;
				msg.sendToTarget();
			}
    	};
    	
    	thread.start();
    }
    
    public static void moveItem(final Handler messageHandler, final String nzoId, final int position)
    {
    	Thread thread = new Thread()
    	{
    		@Override
    		public void run()
    		{
    			try {
    				makeApiCall("switch", "value=" + nzoId + "&value2=" + position);
    			} catch (ServerConnectionException e) {
    				e.printStackTrace();
    			}
    			
    			Message msg = new Message();
    			msg.setTarget(messageHandler);
    			msg.what = MESSAGE_MOVED_ITEM;
    			msg.sendToTarget();
    		}
    	};
    	
    	thread.start();
    }
    
    public static void renameItem(final Handler messageHandler, final String nzoId, final String newName)
    {
    	Thread thread = new Thread()
    	{
    		@Override
    		public void run()
    		{
    			try {
    				makeApiCall("queue", "name=rename&value=" + nzoId + "&value2=" + newName);
    			} catch (ServerConnectionException e) {
					e.printStackTrace();
				}
    			
    			Message msg = new Message();
    			msg.setTarget(messageHandler);
    			msg.what = MESSAGE_RENAMED_ITEM;
    			msg.sendToTarget();
    		}
    	};
    	
    	thread.start();
    }
}
