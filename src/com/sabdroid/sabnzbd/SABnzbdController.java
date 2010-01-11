package com.sabdroid.sabnzbd;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.sabdroid.Preferences;
import com.sabdroid.util.HttpUtil;
import com.sabdroid.util.HttpUtil.ServerConnectinoException;

public final class SABnzbdController
{
    public static double speed = 0.0;
    public static boolean paused = false;

    public static final int MESSAGE_UPDATE_QUEUE = 1;
    public static final int MESSAGE_STATUS_UPDATE = 2;

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

                    sendUpdateMessageStatus(messageHandler, "");
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

                        rows.add(rowValues);
                    }

                    results[1] = rows;

                    Message message = new Message();
                    message.setTarget(messageHandler);
                    message.what = MESSAGE_UPDATE_QUEUE;
                    message.obj = results;
                    message.sendToTarget();
                    
                    sendUpdateMessageStatus(messageHandler, "");
                }
                catch (ServerConnectinoException e)
                {
                    sendUpdateMessageStatus(messageHandler, e.getMessage());
                }
                catch (Throwable e)
                {
                    Log.w("ERROR", e);
                    sendUpdateMessageStatus(messageHandler, "");
                }
                finally
                {
                    executingRefreshQuery = false;
                }
            }
        };

        executingRefreshQuery = true;

        sendUpdateMessageStatus(messageHandler, "Updating....");

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

    public static String makeApiCall(String command, String xTraParams) throws ServerConnectinoException
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

    public static String makeApiCall(String command) throws ServerConnectinoException
    {
        return makeApiCall(command, "");
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
                finally
                {
                    sendUpdateMessageStatus(messageHandler, "");
                }
            }
        };

        sendUpdateMessageStatus(messageHandler, "Adding new download to queue...");

        thread.start();
    }
}