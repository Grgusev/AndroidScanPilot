package dk.webbook.scanpilot;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.webkit.JavascriptInterface;

/**
 * Methods of this class can be invoked from javascript
 *
 * Example:
 * Android.showToast("Hello World");
 */
public class ScanManWebAppInterface {
    static final String TAG = "ScanManWebAppInterface";

    private Context mContext;
    private SoundPool mSoundPool;
    private float mVolume;
    private boolean mLoaded;

    ScanManWebAppInterface(Context c) {
        mContext = c;
        mVolume = 0.5f;
        mLoaded = false;
        mSoundPool = createSoundPool();
        mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int i, int i1) {
                mLoaded = true;
            }
        });
        mSoundPool.load(c, R.raw.approved, 1);
        mSoundPool.load(c, R.raw.denied, 2);
        mSoundPool.load(c, R.raw.error, 3);
    }

    protected SoundPool createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return createNewSoundPool();
        }
        return createOldSoundPool();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected SoundPool createNewSoundPool(){
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        return new SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(3)
                .build();
    }

    @SuppressWarnings("deprecation")
    protected SoundPool createOldSoundPool(){
        return new SoundPool(3, AudioManager.STREAM_NOTIFICATION,0);
    }

    protected void releaseSoundPool() {
        mSoundPool.release();
    }

    /**
     * JavascriptInterfaces
     */
    @JavascriptInterface
    public void setVolume(float volume){
       mVolume = volume;
    }

    @JavascriptInterface
    public void playApproved() {
        if (mLoaded) {
            mSoundPool.play(R.raw.approved, mVolume, mVolume, 1, 0, 1.0f);
        }
    }

    @JavascriptInterface
    public void playDenied() {
        if (mLoaded) {
            mSoundPool.play(R.raw.denied, mVolume, mVolume, 1, 0, 1.0f);
        }
    }

    @JavascriptInterface
    public void playError() {
        if (mLoaded) {
            mSoundPool.play(R.raw.error, mVolume, mVolume, 1, 0, 1.0f);
        }
    }
}
