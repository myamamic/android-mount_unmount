
package yama.tp.system.mountunmount;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.storage.IMountService;
import android.os.storage.StorageManager;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;

public class MainActivity extends Activity {

    private static final String TAG = "YAMOUNT";
    private static final String MOUNT_POINT = "/mnt/ext_usb";

    private static final String MOUNTCOUNT_LABEL = "Mount: ";
    private static final String UNMOUNTCOUNT_LABEL = "Unmount: ";

    private static final int EVENT_TEST_START       = 1;
    private static final int EVENT_REQUEST_MOUNT    = 2;
    private static final int EVENT_REQUEST_UNMOUNT  = 3;

    private IMountService mMountService = null;
    private MyHandler mHandler = null;

    Button mButtonMount;
    Button mButtonUnmount;
    Button mButtonLoop;
    TextView mMountCountText;
    TextView mUnmountCountText;

    private int mMountCount = 0;
    private int mUnmountCount = 0;
    private boolean isRunningTest = false;
    private int mLoopCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_main);

        mHandler = new MyHandler(this);

        mButtonMount = (Button)findViewById(R.id.button_mount);
        mButtonMount.setOnClickListener(mOnClickListener);
        mButtonUnmount = (Button)findViewById(R.id.button_unmount);
        mButtonUnmount.setOnClickListener(mOnClickListener);

        mButtonLoop = (Button)findViewById(R.id.button_loop);
        mButtonLoop.setOnClickListener(mOnLoopClickListener);

        mMountCountText = (TextView)findViewById(R.id.mount_count);
        mMountCountText.setText(MOUNTCOUNT_LABEL + "0");
        mUnmountCountText = (TextView)findViewById(R.id.unmount_count);
        mUnmountCountText.setText(UNMOUNTCOUNT_LABEL + "0");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public static class MyHandler extends Handler {
        private final WeakReference<MainActivity> mActivity;

        public MyHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity == null || activity.isRunningTest == false) {
                return;
            }

            switch(msg.what) {
                case EVENT_TEST_START:
                    sendMessageDelayed(obtainMessage(EVENT_REQUEST_MOUNT), 10);
                    break;
                case EVENT_REQUEST_MOUNT:
                    activity.mount();
                    activity.mMountCount++;
                    Log.e(TAG, "Mount request. ("+ activity.mMountCount + ")");
                    activity.mMountCountText.setText(MOUNTCOUNT_LABEL + activity.mMountCount);
                    sendMessageDelayed(obtainMessage(EVENT_REQUEST_UNMOUNT), 1000*5);
                    break;
                case EVENT_REQUEST_UNMOUNT:
                    activity.unmount();
                    activity.mUnmountCount++;
                    Log.e(TAG, "Unmount request. ("+ activity.mUnmountCount + ")");
                    activity.mUnmountCountText.setText(UNMOUNTCOUNT_LABEL + activity.mUnmountCount);
                    sendMessageDelayed(obtainMessage(EVENT_REQUEST_MOUNT), 1000*5);
                    break;
                default:
                    break;
            }
        }
    }

    View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.button_mount:
                    mount();
                    Log.e(TAG, "Mount");
                    break;
                case R.id.button_unmount:
                    unmount();
                    Log.e(TAG, "Unmount");
                    break;
                default:
                    break;
            }
        }
    };

    View.OnClickListener mOnLoopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isRunningTest) {
                isRunningTest = false;
                if (mHandler.hasMessages(EVENT_TEST_START)) {
                    mHandler.removeMessages(EVENT_TEST_START);
                }
                if (mHandler.hasMessages(EVENT_REQUEST_MOUNT)) {
                    mHandler.removeMessages(EVENT_REQUEST_MOUNT);
                }
                if (mHandler.hasMessages(EVENT_REQUEST_UNMOUNT)) {
                    mHandler.removeMessages(EVENT_REQUEST_UNMOUNT);
                }
            } else {
                isRunningTest = true;
                mMountCount = mUnmountCount = 0;
                mMountCountText.setText(MOUNTCOUNT_LABEL + mMountCount);
                mUnmountCountText.setText(UNMOUNTCOUNT_LABEL + mUnmountCount);
                mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_TEST_START), 10);
            }
            updateButtonState();
        }
    };

    private void updateButtonState() {
        mButtonMount.setEnabled(isRunningTest? false : true);
        mButtonUnmount.setEnabled(isRunningTest? false : true);
        mButtonLoop.setText(isRunningTest? "STOP loop test" : "START loop test");
    }
    
    // ------------------------------------------------------------------------

    private void mount() {
        IMountService mountService = getMountService();
        try {
            if (mountService != null) {
                mountService.mountVolume(MOUNT_POINT);
            } else {
                Log.e(TAG, "Mount service is null, can't mount");
            }
        } catch (RemoteException ex) {
            // Not much can be done
        }
    }

    private void unmount() {
        StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        String state = sm.getVolumeState(MOUNT_POINT);
        if (!Environment.MEDIA_MOUNTED.equals(state) &&
                !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // アンマウント済み
            Log.e(TAG, "Already unmounted.");
            return;
        }

        IMountService mountService = getMountService();
        try {
            if (mountService != null) {
                mountService.unmountVolume(MOUNT_POINT, true, false);
            } else {
                Log.e(TAG, "Mount service is null, can't unmount");
            }
        } catch (RemoteException ex) {
            // Not much can be done
        }
    }

    private synchronized IMountService getMountService() {
        if (mMountService == null) {
            IBinder service = ServiceManager.getService("mount");
            if (service != null) {
                mMountService = IMountService.Stub.asInterface(service);
            } else {
                Log.e(TAG, "Can't get mount service");
            }
        }
        return mMountService;
     }
}
