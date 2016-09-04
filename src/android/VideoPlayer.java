//TODO: set Title text if given
//TODO: apply font
//TODO: simplify by reusing media player (opt.)
//TODO: handle volume settings from options (opt.)

package com.moust.cordova.videoplayer;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.res.AssetFileDescriptor;
import android.graphics.drawable.PictureDrawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

public class VideoPlayer extends CordovaPlugin implements OnCompletionListener, OnPreparedListener, OnErrorListener, OnDismissListener {

    protected static final String LOG_TAG = "VideoPlayer";

    protected static final String ASSETS = "/android_asset/";

    private CallbackContext callbackContext = null;

    private Dialog dialog;
    private VideoView videoView;
    private TextView titleText;
    private ImageView closeButton;
    private ImageView restartButton;
    private ImageView pauseButton;
    private ImageView muteButton;
    private MediaPlayer player;

    private boolean isMuted = false;

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action        The action to execute.
     * @param args          JSONArray of arguments for the plugin.
     * @return              A PluginResult object with a status and message.
     */
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("play")) {
            this.callbackContext = callbackContext;

            CordovaResourceApi resourceApi = webView.getResourceApi();
            String target = args.getString(0);
            final JSONObject options = args.getJSONObject(1);

            //DEBUG
            target = "file:///android_asset/video/sample_video.mp4";

            String fileUriStr;
            try {
                Uri targetUri = resourceApi.remapUri(Uri.parse(target));
                fileUriStr = targetUri.toString();
            } catch (IllegalArgumentException e) {
                fileUriStr = target;
            }

            Log.v(LOG_TAG, fileUriStr);

            final String path = stripFileProtocol(fileUriStr);

            // Create dialog in new thread
            cordova.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                openVideoDialog(path, options);
                }
            });

            // Don't return any result now
            PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
            callbackContext = null;

            return true;
        }
        else if (action.equals("close")) {
            return endPlayback(callbackContext);
        }
        return false;
    }

    private boolean endPlayback(CallbackContext callbackContext) {
        if (dialog != null) {
            if(player.isPlaying()) {
                player.stop();
            }
            player.release();
            dialog.dismiss();
        }

        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }

        return true;
    }

    /**
     * Removes the "file://" prefix from the given URI string, if applicable.
     * If the given URI string doesn't have a "file://" prefix, it is returned unchanged.
     *
     * @param uriString the URI string to operate on
     * @return a path without the "file://" prefix
     */
    public static String stripFileProtocol(String uriString) {
        if (uriString.startsWith("file://")) {
            return Uri.parse(uriString).getPath();
        }
        return uriString;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void openVideoDialog(String path, JSONObject options) {

        player = new MediaPlayer();
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);

        if (path.startsWith(ASSETS)) {
            String f = path.substring(15);
            AssetFileDescriptor fd = null;
            try {
                fd = cordova.getActivity().getAssets().openFd(f);
                player.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (Exception e) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                result.setKeepCallback(false); // release status callback in JS side
                callbackContext.sendPluginResult(result);
                callbackContext = null;
                return;
            }
        }
        else {
            try {
                player.setDataSource(path);
            } catch (Exception e) {
                PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                result.setKeepCallback(false); // release status callback in JS side
                callbackContext.sendPluginResult(result);
                callbackContext = null;
                return;
            }
        }

        dialog = createDialog(player);
        dialog.show();
    }

    private Dialog createDialog(final MediaPlayer mediaPlayer){

        SVG closeSVG = null;
        SVG pauseSVG = null;
        SVG playSVG = null;
        SVG restartSVG = null;
        SVG speakerOffSVG = null;
        SVG speakerOnSVG = null;

        try {
            closeSVG = SVG.getFromResource(cordova.getActivity(), cordova.getActivity().getResources().getIdentifier("close", "raw", cordova.getActivity().getPackageName()));
            pauseSVG = SVG.getFromResource(cordova.getActivity(), cordova.getActivity().getResources().getIdentifier("pause", "raw", cordova.getActivity().getPackageName()));
            playSVG = SVG.getFromResource(cordova.getActivity(), cordova.getActivity().getResources().getIdentifier("play", "raw", cordova.getActivity().getPackageName()));
            restartSVG = SVG.getFromResource(cordova.getActivity(), cordova.getActivity().getResources().getIdentifier("repeat", "raw", cordova.getActivity().getPackageName()));
            speakerOffSVG = SVG.getFromResource(cordova.getActivity(), cordova.getActivity().getResources().getIdentifier("speakeroff", "raw", cordova.getActivity().getPackageName()));
            speakerOnSVG = SVG.getFromResource(cordova.getActivity(), cordova.getActivity().getResources().getIdentifier("speakeron", "raw", cordova.getActivity().getPackageName()));
        } catch (SVGParseException e) {
            e.printStackTrace();
        }

        int dialogLayout = cordova.getActivity().getResources().getIdentifier("video_player", "layout", cordova.getActivity().getPackageName());
        final Dialog dialog = new Dialog(cordova.getActivity(), android.R.style.Theme_NoTitleBar);

        LayoutInflater inflater = (LayoutInflater)cordova.getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        //setup dialog
        dialog.setContentView(inflater.inflate(dialogLayout, null));
        dialog.setCancelable(true);

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                Log.d(LOG_TAG, "Dismiss dialog");
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Log.d(LOG_TAG, "Show dialog");
            }
        });

        //fetch dialog elements
        videoView = (VideoView)dialog.findViewById(cordova.getActivity().getResources().getIdentifier("video_player", "id", cordova.getActivity().getPackageName()));
        titleText = (TextView) dialog.findViewById(cordova.getActivity().getResources().getIdentifier("header_text", "id", cordova.getActivity().getPackageName()));
        closeButton = (ImageView) dialog.findViewById(cordova.getActivity().getResources().getIdentifier("close_button", "id", cordova.getActivity().getPackageName()));
        restartButton = (ImageView) dialog.findViewById(cordova.getActivity().getResources().getIdentifier("restart_button", "id", cordova.getActivity().getPackageName()));
        pauseButton = (ImageView) dialog.findViewById(cordova.getActivity().getResources().getIdentifier("pause_button", "id", cordova.getActivity().getPackageName()));
        muteButton = (ImageView) dialog.findViewById(cordova.getActivity().getResources().getIdentifier("mute_button", "id", cordova.getActivity().getPackageName()));

        //setup display elements
        //TODO: check if any title is given, if so - display it

        setImage(closeButton, closeSVG);
        setImage(restartButton, restartSVG);
        setImage(pauseButton, pauseSVG);
        setImage(muteButton, speakerOnSVG);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: shut down media player, dismiss everything
                dialog.dismiss();
            }
        });

        //copy to final vars
        final SVG finalPlaySVG = playSVG;
        final SVG finalPauseSVG = pauseSVG;
        final SVG finalSpeakerOnSVG = speakerOnSVG;
        final SVG finalSpeakerOffSVG = speakerOffSVG;

        restartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayer.seekTo(0);
                if(!mediaPlayer.isPlaying()){
                    mediaPlayer.start();
                }
                setImage(pauseButton, finalPauseSVG);
            }
        });

        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mediaPlayer.isPlaying()){
                    mediaPlayer.pause();
                    setImage(pauseButton, finalPlaySVG);
                }
                else{
                    mediaPlayer.start();
                    setImage(pauseButton, finalPauseSVG);
                }
            }
        });

        muteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!isMuted){
                    mediaPlayer.setVolume(0.0f, 0.0f);
                    setImage(muteButton, finalSpeakerOffSVG);
                }else{
                    mediaPlayer.setVolume(1.0f, 1.0f);
                    setImage(muteButton, finalSpeakerOnSVG);
                }
                isMuted = !isMuted;
            }
        });

        final SurfaceHolder mHolder = videoView.getHolder();
        mHolder.setKeepScreenOn(true);
        mHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mediaPlayer.setDisplay(holder);

                try {
                    mediaPlayer.prepare();
                } catch (Exception e) {
                    PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getLocalizedMessage());
                    result.setKeepCallback(false); // release status callback in JS side
                    callbackContext.sendPluginResult(result);
                    callbackContext = null;
                }
            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mediaPlayer.release();
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
        });

        return dialog;
    }

    private void setImage(ImageView view, SVG svg){
        if(null == svg){
            throw new IllegalArgumentException("SVG must not be null");
        }
        view.setImageDrawable(new PictureDrawable(svg.renderToPicture()));
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(LOG_TAG, "MediaPlayer.onError(" + what + ", " + extra + ")");
        if(mp.isPlaying()) {
            mp.stop();
        }
        mp.release();
        dialog.dismiss();
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.d(LOG_TAG, "MediaPlayer completed");
        mp.release();
        dialog.dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        Log.d(LOG_TAG, "Dialog dismissed");
        if (callbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(false); // release status callback in JS side
            callbackContext.sendPluginResult(result);
            callbackContext = null;
        }
    }
}
