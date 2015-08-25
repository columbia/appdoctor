package com.andchecker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.os.SystemClock;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.NumberPicker;
import android.widget.CheckedTextView;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.Exception;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import android.os.AsyncTask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.SynchronousQueue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.os.Parcelable;
import android.os.Parcel;
import android.os.Looper;
import android.os.MessageQueue;
import java.io.ByteArrayOutputStream;
import java.lang.ClassLoader;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.CountDownLatch;
import android.os.StrictMode;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import android.os.Message;
import android.os.Handler;
import java.io.StringWriter;
import java.io.PrintWriter;
import junit.framework.Assert;

// Accept a connection asynchornized
class RemoteControlListenTask extends AsyncTask<Integer, Void, Socket> {
    protected Socket doInBackground(Integer... ports)
    {
        int port = ports[0].intValue();
        ServerSocket srv = null;
        Socket socket;
        try
        {
            srv = new ServerSocket(port);
            socket = srv.accept();
        }
        catch (Exception x)
        {
            socket = null;
        }

        try { if (srv != null) srv.close(); } catch (Exception x) { }
            
        return socket;
    }
}

// Runnable that transfer lines from Reader to Concurrent Deque
class ReadLineRunnable implements Runnable {
    private BufferedReader mReader;
    private LinkedBlockingQueue<String> mQueue;
        
    public ReadLineRunnable(LinkedBlockingQueue<String> queue, BufferedReader reader) {
        mReader = reader;
        mQueue  = queue;
    }
        
    public void run() {
        while (true)
        {
            try
            {
                String result = mReader.readLine();
                mQueue.put(result);
            }
            catch (Exception x)
            {
                try { mQueue.put(null); } catch (Exception xx) { }
                break;
            }
        }
    }
}

// Runnable that transfer lines Concurrent Queue to Writer
class WriteLineRunnable implements Runnable {
    private BufferedWriter mWriter;
    private LinkedBlockingQueue<String> mQueue;
        
    public WriteLineRunnable(LinkedBlockingQueue<String> queue, BufferedWriter writer) {
        mWriter = writer;
        mQueue  = queue;
    }
        
    public void run() {
        while (true)
        {
            try
            {
                String line = mQueue.take();
                if (line == null) break;
                        
                mWriter.write(line, 0, line.length()); mWriter.flush();
            }
            catch (Exception x)
            {
                break;
            }
        }
    }
}


class ACIAsyncTaskProxy implements Executor
{
    private ACInstrumentation mInst;

    ACIAsyncTaskProxy(ACInstrumentation instrumentation) {
        mInst = instrumentation;
    }

    public void execute(final Runnable command) {
        mInst.mAsyncTaskExecutor.execute(new Runnable() {
                public void run() {
                    mInst.acquireAsyncTaskCount();
                    command.run();
                    mInst.releaseAsyncTaskCount();
                }
            });
    }
}

// Work around for Adnroid 2.3
class ACIAsyncTaskProxy23 extends ThreadPoolExecutor
{
    private ACInstrumentation mInst;

    ACIAsyncTaskProxy23(ACInstrumentation inst) {
        super(0, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        mInst = inst;
    }

    public void execute(final Runnable command) {
        mInst.mAsyncTaskExecutor.execute(new Runnable() {
                public void run() {
                    mInst.acquireAsyncTaskCount();
                    command.run();
                    mInst.releaseAsyncTaskCount();
                }
            });
    }
}

// Copy SyncRunnable class from Instrumentation.java in android
// framework.
class ACISyncRunnable implements Runnable {
    private final Runnable mTarget;
    private boolean mComplete;
    
    public ACISyncRunnable(Runnable target) {
        mTarget = target;
    }

    public void run() {
        mTarget.run();
        synchronized (this) {
            mComplete = true;
            notifyAll();
        }
    }

    public void waitForComplete() {
        synchronized (this) {
            while (!mComplete) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }
}

class ACIHandler extends Handler
{
    ACInstrumentation mInst;
    
    public ACIHandler(ACInstrumentation instrumentation) {
        mInst = instrumentation;
    }
    
    public void handleMessage(Message msg) {
        mInst.log("ACIHandler: got message");
    }
}

public class ACInstrumentation extends Instrumentation {

    static class InstConfiguration
    {
        String  message_proxy_type = "no";
        String  message_log_path   = "";
        int     msg_inst_srv_port  = 22229;
        boolean enable_strict_mode = false;
        boolean faithful_event     = false;
        String  coverage_filename  = "coverage";
    };

    InstConfiguration conf = new InstConfiguration();
    
    void initConfiguration(Bundle args) {
        if (args == null) return;
        
        try
        {
            for (Field f : conf.getClass().getDeclaredFields())
            {
                Class<?> t = f.getType();
                if (!args.containsKey(f.getName())) continue;
                
                String v = args.getString(f.getName());
                log("CONFIG: Set " + f.getName() + " to " + v);
                    
                if (t == Integer.TYPE)
                {
                    f.setInt(conf, Integer.decode(v));
                }
                else if (t == Boolean.TYPE)
                {
                    f.setBoolean(conf, Boolean.parseBoolean(v));
                }
                else if (t == String.class)
                {
                    f.set(conf, v);
                }
                else
                {
                    log("unsupported type in configuration: " + f.getName());
                }
            }
        }
        catch (Exception x)
        {
            log("Exception occured while initializing configuation");
            x.printStackTrace();
        }
    }
    
    // access android internal for current activity thread
    private static Class<?> looperKlass;
    private static Class<?> bundleKlass;
    private static Class<?> activityKlass;
    private static Class<?> activityThreadKlass;
    private static Class<?> activityThreadKlassApplicationThreadKlass;
    public  static Class<?> viewPagerKlass;
    public  static Class<?> activityThreadHandlerKlass;
    private static Method   currentActivityThread;
    private static Method   scheduleRelaunchActivity_5;
    private static Method   scheduleRelaunchActivity_6;
    private static Method   activityThreadMethodGetApplciationThread;
    private static Field    activityFieldToken;
    private static Method   activityMethodPerformPause;
    private static Method   activityMethodPerformSaveInstanceState;
    private static Method   activityMethodPerformStop;
    private static Method   activityMethodPerformRestart;
    private static Method   activityMethodPerformStart;
    private static Method   activityMethodPerformRestoreInstanceState;
    public  static Method   activityMethodPerformResume;
    private static Field    looperFieldSThreadLocal;
    private static Method   looperMethodSetMainLooper;
    private static Field    asyncTaskFieldExecutor;
    private static Method   activityMethodRecreate;
    public  static Field    decorViewFeature;
    public  static Method   viewPagerMethodGetCurrentItem;
    public  static Method   systemPropertiesMethodSet;
    public  static Field    looperFieldQueue;
    public  static Method   seekBarMethodStartTracking;
    public  static Method   seekBarMethodStopTracking;

    private void initReflection() throws Exception {
        looperKlass = Class.forName("android.os.Looper");
        bundleKlass = Class.forName("android.os.Bundle");
        activityKlass = Class.forName("android.app.Activity");
        activityThreadKlass = Class.forName("android.app.ActivityThread");
        activityThreadHandlerKlass = Class.forName("android.app.ActivityThread$H");
        activityThreadKlassApplicationThreadKlass = Class.forName("android.app.ActivityThread$ApplicationThread");

        activityMethodPerformPause = activityKlass.getDeclaredMethod("performPause");
        activityMethodPerformPause.setAccessible(true);
        activityMethodPerformSaveInstanceState = activityKlass.getDeclaredMethod("performSaveInstanceState", bundleKlass);
        activityMethodPerformSaveInstanceState.setAccessible(true);
        activityMethodPerformStop = activityKlass.getDeclaredMethod("performStop");
        activityMethodPerformStop.setAccessible(true);
        activityMethodPerformRestart = activityKlass.getDeclaredMethod("performRestart");
        activityMethodPerformRestart.setAccessible(true);
        activityMethodPerformStart = activityKlass.getDeclaredMethod("performStart");
        activityMethodPerformStart.setAccessible(true);
        activityMethodPerformRestoreInstanceState = activityKlass.getDeclaredMethod("performRestoreInstanceState", bundleKlass);
        activityMethodPerformRestoreInstanceState.setAccessible(true);
        activityMethodPerformResume = activityKlass.getDeclaredMethod("performResume");
        activityMethodPerformResume.setAccessible(true);

        looperFieldQueue = Class.forName("android.os.Looper").getDeclaredField("mQueue");
        looperFieldQueue.setAccessible(true);

        try
        {
            activityMethodRecreate = Class.forName("android.app.Activity").getMethod("recreate");
            log("Using Recreate interface to relaunch");
        }
        catch (NoSuchMethodException x)
        {
            log("No Recreate interface in activity");
            activityMethodRecreate = null;
        }

        try
        {
            asyncTaskFieldExecutor = Class.forName("android.os.AsyncTask").getDeclaredField("sExecutor");
        }
        catch (NoSuchFieldException x)
        {
            asyncTaskFieldExecutor = Class.forName("android.os.AsyncTask").getDeclaredField("sDefaultExecutor");
        }
        asyncTaskFieldExecutor.setAccessible(true);
        
        currentActivityThread = activityThreadKlass.getMethod("currentActivityThread");
        activityThreadMethodGetApplciationThread = activityThreadKlass.getMethod("getApplicationThread");
        
        activityFieldToken = Class.forName("android.app.Activity").getDeclaredField("mToken");
        activityFieldToken.setAccessible(true);

        looperFieldSThreadLocal = looperKlass.getDeclaredField("sThreadLocal");
        looperFieldSThreadLocal.setAccessible(true);

//        looperMethodSetMainLooper = looperKlass.getDeclaredMethod("setMainLooper", Class.forName("android.os.Looper"));
//        looperMethodSetMainLooper.setAccessible(true);
        decorViewFeature = Class.forName("com.android.internal.policy.impl.PhoneWindow$DecorView").getDeclaredField("mFeatureId");
        decorViewFeature.setAccessible(true);

        try {
            viewPagerKlass = Class.forName("android.support.v4.view.ViewPager");
            viewPagerMethodGetCurrentItem = viewPagerKlass.getDeclaredMethod("getCurrentItem");
        } catch (Exception e) {
            viewPagerKlass = null;
        }
        
        scheduleRelaunchActivity_5 = null;
        scheduleRelaunchActivity_6 = null;
        
        Method methods[] = activityThreadKlassApplicationThreadKlass.getMethods();
        for (Method m : methods)
        {
            if (m.getName().equals("scheduleRelaunchActivity"))
            {
                Class<?> args[] = m.getParameterTypes();
                if (args.length == 5)
                    scheduleRelaunchActivity_5 = m;
                else if (args.length == 6)
                    scheduleRelaunchActivity_6 = m;
                else
                {
                    throw new Exception("get scheduleRelaunchActivity with argc " + args.length);
                }
            }
        }

        if (scheduleRelaunchActivity_5 == null &&
            scheduleRelaunchActivity_6 == null)
            throw new Exception("no scheduleRelaunchActivity func");

        systemPropertiesMethodSet = Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class);

        try
        {
            seekBarMethodStartTracking = Class.forName("android.widget.SeekBar").getDeclaredMethod("onStartTrackingTouch");
            seekBarMethodStartTracking.setAccessible(true);
            seekBarMethodStopTracking = Class.forName("android.widget.SeekBar").getDeclaredMethod("onStopTrackingTouch");
            seekBarMethodStopTracking.setAccessible(true);
        } catch (NoSuchMethodException e) {
            seekBarMethodStartTracking = null;
        }
    }

    public boolean relaunchActivity(Activity act) {        
        try
        {            
            Object at = currentActivityThread.invoke(null);
            if (at == null) throw new Exception("no activity thread");

            // Try public interface first
            if (activityMethodRecreate != null)
            {
                activityMethodRecreate.invoke(act);
            }
            else
            {
                Object applicationThread = activityThreadMethodGetApplciationThread.invoke(at);
                Object token = activityFieldToken.get(act);

                // call updateConfiguration()
                act.setRequestedOrientation(act.getRequestedOrientation());
            
                if (scheduleRelaunchActivity_5 != null)
                    scheduleRelaunchActivity_5.invoke(applicationThread, token, null, null, 0, false);
                else
                    scheduleRelaunchActivity_6.invoke(applicationThread, token, null, null, 0, false, null);
            }

        }
        catch (Exception x)
        {
            x.printStackTrace();
            return false;
        }

        return true;
    }
    
    private SparseArray<View> tempIdMap;
    private Map<View, Integer> tempIdRevMap;
    public Random random = new Random();
    public Random chkrand = new Random();
    public static final int VIEWID_NONE = -2;

    public void setRandomSeed(long seed, long chkseed) { random.setSeed(seed); chkrand.setSeed(seed); }

    Map<Integer, EditTextContent> editTextDb = new TreeMap<Integer, EditTextContent>();
    Map<Integer, String[]> hintDb = new TreeMap<Integer, String[]>();
    public Vector<Operation> collectedOperations = null;
    public Vector<Operation> collectedFaithfulOperations = null;
    boolean waitForIdleTimedout = false;

    public void validateInAppThread() throws ACIException {
        boolean i;
        try { i = currentActivityThread.invoke(null) == null; }
        catch (Exception x)
        { throw new ACIException(ACIException.LEVEL_INTERNAL, "CannotAccessActivityThread", "Cannot access ActivityThread"); }
        if (i) throw new ACIException(ACIException.LEVEL_INTERNAL, "ShouldBeInAppThread", "Must be in application's thread");
    }

    public void validateNotInAppThread() throws ACIException {
        boolean i;
        try { i = currentActivityThread.invoke(null) == null; }
        catch (Exception x)
        { throw new ACIException(ACIException.LEVEL_INTERNAL, "CannotAccessActivityThread", "Cannot access ActivityThread"); }
        if (!i) throw new ACIException(ACIException.LEVEL_INTERNAL, "CannotBeInAppThread", "Cannot in application's thread");
    }

    private String    mInstName;
    private Resources mResources;
    private Socket    mCMDSocket = null;
            Executor                    mAsyncTaskExecutor;
    private Executor                    mAsyncTaskProxy;
    private ACIMessageProxy             mMessageProxy;
            MessageQueue                mMainQueue;
    private Handler                     mInstHandler;
    private ACIMessageQueueHijacker     mMainQueueHijacker;
    private LinkedBlockingQueue<String> mRequestQueue;
    private LinkedBlockingQueue<String> mGeneratedRequestQueue;
    private LinkedBlockingQueue<String> mResponseQueue;
    private BufferedWriter              mCmdLog;
    private MonkeyRunnerGenerator       mMonkeyRunnerGenerator;
    // private TreeMap<Integer, Activity>  mActivityMap;
    private static ACInstrumentation    mSelf; // For sub-classes
    private HashSet<Activity>           mCurrentActivities;
    private HashSet<Activity>           mPausedActivities;
    private HashSet<Runnable>           mChecker;
    private HashSet<Integer>            mViewHashs;
    private File                        mInstDataDir;
    private boolean                     mInReplay = false;
    private boolean                     mCheckerEnabled = true;
    private boolean                     mLifeCycleCheckerEnabled = true;
    private boolean                     mInjectEvents = false;
    private Map<String, Integer>        mRollableActs = new HashMap<String, Integer>();

    public Handler getHandler() {
        return mInstHandler;
    }

    public boolean isSystemClass(Class<?> klass) {
        // package could be null -- which is bad
        // Package pkg = klass.getPackage();
        String name = klass.getName();
        boolean result =
            name.startsWith("com.android.internal") ||
            name.startsWith("android.") ||
            name.startsWith("java.");
        return result;
    }

    public boolean shouldManageMessage(Message msg) {
        // log("examing msg " + MessageInfo.obtain(msg));
        if (msg.obj != null &&
            !isSystemClass(msg.obj.getClass())) return true;
        if (msg.getCallback() != null &&
            !isSystemClass(msg.getCallback().getClass())) return true;
        if (msg.getTarget() != null &&
            !isSystemClass(msg.getTarget().getClass())) return true;
        if (activityThreadHandlerKlass.isInstance(msg.getTarget())) return true;
        // log("ignore this msg");
        return false;
    }

    private static class ObjectWithNumberTag implements Comparable<ObjectWithNumberTag>
    {
        public Integer tag;
        public Object  value;

        public ObjectWithNumberTag(Integer tag, Object value) {
            this.tag = tag; this.value = value;
        }
        
        public int compareTo(ObjectWithNumberTag x) {
            return tag.compareTo(x.tag);
        }
    }

    boolean        mWaitForAsyncTask   = false;
    Object         mAsyncTaskCountLock = new Object();
    int            mAsyncTaskCount     = 0;
    CountDownLatch mAsyncTaskCountZero = null;

    public void setWaitForAsyncTask(boolean toWait) {
        mWaitForAsyncTask = toWait;
    }
    
    public void acquireAsyncTaskCount() {
        synchronized (mAsyncTaskCountLock)
        {
            ++ mAsyncTaskCount;
            log("aysnc task enter" + mAsyncTaskCount);
        }
    }

    public void releaseAsyncTaskCount() {
        synchronized (mAsyncTaskCountLock)
        {
            if (-- mAsyncTaskCount == 0)
            {
                if (mAsyncTaskCountZero != null)
                    mAsyncTaskCountZero.countDown();
            }
            log("aysnc task leave" + mAsyncTaskCount);
        }
    }

    class WaitForIdleRunnable implements Runnable {
        boolean idle = false;
        @Override
        public void run() {
            synchronized (this) {
                idle = true;
                notifyAll();
            }
        }
    }

    String getExceptionStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    void waitForIdleSync(long timeout) {
        WaitForIdleRunnable wir = new WaitForIdleRunnable();
        super.waitForIdle(wir);
        synchronized (wir) {
            if (!wir.idle) {
                try {
                    wir.wait(timeout);
                } catch (InterruptedException e) {
                }
            }
        }
        if (!wir.idle) {
            // We timed out once. We should not wait this long in the future...
            waitForIdleTimedout = true;
            Log.w("waitForIdle", "Timeout!");
        }
    }

    void waitForIdleAuto() {
        if (waitForIdleTimedout) {
            // It's possible that this will happen again
            waitForIdleSync(3000);
        } else {
            waitForIdleSync(120000);
        }
    }

    public void waitForIdleSync() {
        waitForIdleAuto();

        if (!mWaitForAsyncTask)
            return;
        log("now idle"); 
        // Assume mAsyncTaskCountZero == null
        while (true)
        {
            synchronized (mAsyncTaskCountLock)
            {
                if (mAsyncTaskCount > 0)
                    mAsyncTaskCountZero = new CountDownLatch(1);
            }
            
            if (mAsyncTaskCountZero == null) break;
            
            try { mAsyncTaskCountZero.await(); }
            catch (InterruptedException x) { continue; }
            synchronized (mAsyncTaskCountLock) {
                mAsyncTaskCountZero = null;
            }
            waitForIdleAuto();
        }
        log("now idle and no async tasks"); 
    }
    
    private ArrayList<ObjectWithNumberTag> mDelayList;

    public void addDelayedRunnable(int delay, DelayedRunnable runnable) {
        mDelayList.add(new ObjectWithNumberTag(delay, runnable));
    }

    public void setInReplay(boolean b) { mInReplay = b; }
    public boolean isInReplay() { return mInReplay; }
    
    public boolean isFaithful() { return conf.faithful_event; }

    public void setCheckerEnabled(boolean b) { mCheckerEnabled = b; }
    public boolean isCheckerEnabled() { return mCheckerEnabled; }

    public void setLifeCycleCheckerEnabled(boolean b) { mLifeCycleCheckerEnabled = b; }
    public boolean isLifeCycleCheckerEnabled() { return mCheckerEnabled && mLifeCycleCheckerEnabled; }

    private boolean mOnlyViewTreeInState = false;
    
    public void setOnlyViewTreeDescInState(boolean b) { mOnlyViewTreeInState = b; }
    public boolean onlyViewTreeDescInState() { return mOnlyViewTreeInState; }

    public void setInjectEvents(boolean b) { mInjectEvents = b; }
    public boolean doInjectEvents() { return mInjectEvents; }


    public File getInstrumentDataDir() { return mInstDataDir; }
    public static ACInstrumentation getSelf() { return mSelf; }

    public Activity getCurrentActivity() {
        Activity result;
        synchronized (this)
        {
            int size = mCurrentActivities.size();
            if (size == 0)
            {
                result = null;
            }
            else if (size == 1)
            {
                result = mCurrentActivities.iterator().next();
            }
            else
            {
                log("WTF: more than 1 activities is resumed, will return a random one");
                result = mCurrentActivities.iterator().next();
            }
        }
        return result;
    }

    public boolean isAppStoped() {
        synchronized (this)
        {
            return mCurrentActivities.size() == 0 && mPausedActivities.size() == 0;
        }
    }
    
    public void addChecker(Runnable cr) {
        mChecker.add(cr);
    }

    Iterator<Runnable> getCheckers() {
        return mChecker.iterator();
    }

    public void addRequest(String line) {
        if (!mInReplay)
            mGeneratedRequestQueue.add("- " + line);
    }

    public void addResponse(String line, boolean sendBack) {
        if (mCmdLog != null)
        {
            try
            {
                mCmdLog.write("#RESP# ");
                mCmdLog.write(line);
                mCmdLog.flush();
            }
            catch (Exception x)
            {
                x.printStackTrace();
                log("Exception while writing to file");
            }
        }
        
        if (sendBack && mResponseQueue != null)
            mResponseQueue.add(line);
    }

    public boolean mSessionFinishFlag;
    
    Thread mReadLineThread;
    Thread mWriteLineThread;

    public void log(String str) {
        Log.i(mInstName, str);
    }

    @Override
    public void runOnMainSync(Runnable runner) {
        if (mInstHandler == null)
            super.runOnMainSync(runner);
        else
        {
            // validateInAppThread();
            ACISyncRunnable sr = new ACISyncRunnable(runner);
            mInstHandler.post(sr);
            sr.waitForComplete();
        }
    }


    RemoteControlListenTask mListenTask = null;

    @SuppressLint("NewApi")
	@Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        
        mResources   = getContext().getResources();
        mInstName    = getContext().getString(R.string.instrumentation_name);
        mInstDataDir = getTargetContext().getDir("__instrument_data__", Context.MODE_WORLD_READABLE);
	if (mInstDataDir.exists())
	{
		log("instDataDir exists");
	}
	else
	{
		log("instDataDir not exists");
	}
        mSelf        = this;

        int port = mResources.getInteger(R.integer.instrumentation_cmd_server_port);
        tempIdMap = new SparseArray<View>();
        tempIdRevMap = new HashMap<View, Integer>();
        NativeHelper.initNativeLibrary(getContext());

        try
        {
            initReflection();
        }
        catch (Exception x)
        {
            x.printStackTrace();
            log("Error while initializing reflection part");
            return;
        }

        initConfiguration(arguments);

        mMainQueue = Looper.myQueue();
        if (conf.message_proxy_type.equals("record"))
        {
            log("Start message recording proxy");
            mMainQueueHijacker = new ACIMessageQueueRecorder(this, mMainQueue);
        }
        else if (conf.message_proxy_type.equals("replay") ||
                 conf.message_proxy_type.equals("try_replay"))
        {
            log("Start message replaying proxy");
            mMainQueueHijacker = new ACIMessageQueueReplayer2(this, mMainQueue);
        }
        else if (conf.message_proxy_type.equals("inst"))
        {
            log("Start message instrumentor");
            mMainQueueHijacker = new ACIMessageQueueInstrumenter(this, mMainQueue);
        }
        else
        {
            log("No message proxy");
        }

        mInstHandler = new ACIHandler(this);
        
        if (mMainQueueHijacker != null)
        {
            mMainQueueHijacker.prepare();
            mInstHandler.post(mMainQueueHijacker);
        }
        
        try
        {
            mAsyncTaskExecutor = (Executor)asyncTaskFieldExecutor.get(null);
            try
            {
                mAsyncTaskProxy = new ACIAsyncTaskProxy(this);
                asyncTaskFieldExecutor.set(null, mAsyncTaskProxy);
            }
            catch (Exception x)
            {
                log("Fall back to workaround for android 2.3");
                mAsyncTaskProxy = new ACIAsyncTaskProxy23(this);
                asyncTaskFieldExecutor.set(null, mAsyncTaskProxy);
            }
        }
        catch (Exception x)
        {
            x.printStackTrace();
            log("Exception during set executor for asyncTask");
            return;
        }

        if (conf.enable_strict_mode && Integer.valueOf(android.os.Build.VERSION.SDK) >= 9)
        {
            log("Enabling strict mode");
            // Set strict mode
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                       .detectAll()
                                       .penaltyLog()
                                       .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                                   .detectAll()
                                   .penaltyLog()
                                   .build());
        }

        log("Start listening");
        mListenTask = new RemoteControlListenTask();
        mListenTask.execute(Integer.valueOf(port));

        start();
    }

    public void startTarget() throws ACIException {
        validateNotInAppThread();

        Context targetContext = getTargetContext();
        String targetActivity = getContext().getString(R.string.instrumentation_target_activity);
        mMonkeyRunnerGenerator.writeHeader(targetContext.getPackageName(), targetActivity);

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(targetContext, targetActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

/*        Activity a = startActivitySync(intent);
        log("Started: " + a);*/
        ActivityInfo ai = intent.resolveActivityInfo(
                getTargetContext().getPackageManager(), 0);
        if (ai == null) {
            throw new RuntimeException("Unable to resolve activity for: " + intent);
        }

        intent.setComponent(new android.content.ComponentName(
                    ai.applicationInfo.packageName, ai.name));

        targetContext.startActivity(intent);
        waitForIdleSync();
        setInTouchMode(true);

    }

    public void switchBack() throws ACIException {
        validateNotInAppThread();
    }

    public View getFocusedDecorView() throws ACIException {
        validateInAppThread();
        View views[] = getWindowDecorViews();
        if (views == null) return null;
        for (int i = 0; i != views.length; ++ i)
            if (views[i].hasWindowFocus()) return views[i];
        return null;
    }

    public View findReachableViewById(int id) throws ACIException {
        validateInAppThread();
        if (tempIdMap.get(id) != null)
            return tempIdMap.get(id);
        View v = getFocusedDecorView();
        if (v == null) return null;
        return v.findViewById(id);
    }

    public boolean getReachableViewGeoById(int id, int[] ret) throws ACIException {
        validateInAppThread();
        View v = findReachableViewById(id);
        if (v == null) return false;
        int loc[] = new int[2];
        
        v.getLocationOnScreen(loc);
        ret[0] = loc[0];
        ret[1] = loc[1];
        ret[2] = v.getWidth();
        ret[3] = v.getHeight();
        return true;
    }

/*    public Bitmap getViewBitmap(View v) throws ACIException {
        validateInAppThread();
        boolean c = v.isDrawingCacheEnabled();
        v.setDrawingCacheEnabled(true);
        Bitmap b = Bitmap.createBitmap(v.getDrawingCache());
        if (b == null)
        {
            v.buildDrawingCache(true);
            b = Bitmap.createBitmap(v.getDrawingCache());
        }
        v.setDrawingCacheEnabled(c);
        return b;
    }*/

    public ByteBuffer getViewImageAsNativeBuffer(View v) throws ACIException {
        validateInAppThread();
        boolean c = v.isDrawingCacheEnabled();
        v.setDrawingCacheEnabled(true);
        ByteBuffer b = ByteBufferHelper.createNativeFromBitmap(v.getDrawingCache());
        if (b == null)
        {
            v.buildDrawingCache(true);
            b = ByteBufferHelper.createNativeFromBitmap(v.getDrawingCache());
        }
        v.setDrawingCacheEnabled(c);
        return b;
    }
    
    public BBImage getViewImageAsBBImage(View v) throws ACIException {
        validateInAppThread();
        boolean c = v.isDrawingCacheEnabled();
        v.setDrawingCacheEnabled(true);
        if (v.getDrawingCache() == null) {
            v.buildDrawingCache(true);
        }
        if (v.getDrawingCache() == null)
            return null;
        BBImage image = new BBImage(v.getDrawingCache());
        v.setDrawingCacheEnabled(c);
        return image;
    }

    @SuppressLint("NewApi")
	public String getViewTreeDesc(View v) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getClass().getSimpleName());
        sb.append(String.valueOf(v.getId()));
        if (v instanceof EditText) {
            String t = ((TextView)v).getText().toString();
            if (t.length() > 20)
                t = t.substring(0, 20);
            sb.append(t);
        }
        if (v instanceof CompoundButton) {
            sb.append(String.valueOf(((CompoundButton)v).isChecked()));
        }
        if (v instanceof AdapterView) {
            sb.append(String.valueOf(((AdapterView)v).getSelectedItemPosition()));
        }
        if (v instanceof ProgressBar) {
            sb.append(String.valueOf(((ProgressBar)v).getProgress()));
        }
        if (android.os.Build.VERSION.SDK_INT >= 11)
        {
            if (v instanceof NumberPicker) {
                sb.append(String.valueOf(((NumberPicker)v).getValue()));
            }
        }
        if (v instanceof DatePicker) {
            sb.append(String.valueOf(((DatePicker)v).getYear()));
            sb.append(String.valueOf(((DatePicker)v).getMonth()));
            sb.append(String.valueOf(((DatePicker)v).getDayOfMonth()));
        }
        if (v instanceof CheckedTextView) {
            sb.append(String.valueOf(((CheckedTextView)v).isChecked()));
        }
        if (v instanceof ViewGroup) {
            for (int i=0; i<((ViewGroup)v).getChildCount(); i++) {
                sb.append(getViewTreeDesc(((ViewGroup)v).getChildAt(i)));
            }
        }
        return sb.toString();
    }

    public Parcelable getCurrentActivityInstanceState() {
        final Parcelable result[] = new Parcelable[1];
        runOnMainSync(new Runnable () {
                public void run() {
                    Activity act = getCurrentActivity();
                    if (act == null)
                    {
                        result[0] = null;
                        return;
                    }

                    try
                    {
                        setInTouchMode(true);
                        Bundle bundle = new Bundle();
                        if (!onlyViewTreeDescInState())
                        {
                            callActivityOnPause(act);
                            callActivityOnSaveInstanceState(act, bundle);
                            activityMethodPerformResume.invoke(act);
                        }
                        bundle.putString("__ins__activity_name", act.getClass().getName());

                        View focusedView = getFocusedDecorView();
                        if (focusedView != null) {
                            bundle.putString("__ins__focused_decor_view_hiearchy", getViewTreeDesc(focusedView));
                        }
                        result[0] = bundle;
                    }
                    catch (Exception x)
                    {
                        result[0] = null;
                    }
                }
            });

        return result[0];
    }

    public void tryGenerateCoverageData() {
        try
        {
            Class<?> emmaRT = Class.forName("com.vladium.emma.rt.RT");
            emmaRT.getMethod("dumpCoverageData", File.class, boolean.class, boolean.class).
                invoke(null, new File(mInstDataDir.getAbsolutePath() + "/" + conf.coverage_filename), false, false);
        }
        catch (Exception x)
        {
            log("Exception while generating coverage data");
            x.printStackTrace();
        }
    }
    
/*    public File grabSnapshot(String filename, View view) throws ACIException, IOException {
        View v = getFocusedDecorView();
        if (v == null)
            return null;
        BBImage image = getViewImageAsBBImage(v);
        return image.save(filename);
    }*/
    
/*    public Bitmap grabSnapshot() throws ACIException, IOException {
        if (getFocusedDecorView() == null)
            return null;
        return getViewBitmap(getFocusedDecorView());
    }*/
    
    public BBImage grabSnapshotBB(View view) throws ACIException, IOException {
        View target = null;
        if (view == null) {
            target = this.getFocusedDecorView();
        } else {
            if (view.getContext() instanceof Activity) {
                target = ((Activity)view.getContext()).getWindow().getDecorView();
            } else {
                ViewParent v = view.getParent();
                while (v != null) {
                    if (v instanceof View) {
                        target = (View)v;
                    }
                    if (v.getParent() != null) {
                        v = v.getParent();
                    } else {
                        break;
                    }
                }
            }
        }
        if (target == null) return null;
        return getViewImageAsBBImage(target);
    }
    
    public File saveImage(String filename, Bitmap bitmap) throws IOException {
        File f = new File(getInstrumentDataDir().getAbsolutePath() + "/" + filename);
        FileOutputStream out = new FileOutputStream(f);
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        out.close();
        return f;
    }
    
    public int getInternalId(String name) {
        try {
            Class<?> classR = Class.forName("com.android.internal.R$id");
            Field field = classR.getField(name);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            return -2;
        }
    }
    
    class MyDexClassLoader extends DexClassLoader {

        public MyDexClassLoader(String dexPath, String optimizedDirectory,
                String libraryPath, ClassLoader parent) {
            super(dexPath, optimizedDirectory, libraryPath, parent);
        }
        
        public Class<?> doLoadClass(String name) throws ClassNotFoundException {
            return super.loadClass(name, true);
        }
    }

    public boolean loadCommand(String className, byte[] classData) {
        Object instance = null;
        try
        {
            File tmpFile = File.createTempFile("acinstrument-dyc", ".apk");
            tmpFile.deleteOnExit();
            FileOutputStream o = new FileOutputStream(tmpFile);
            o.write(classData, 0, classData.length);
            o.close();
            MyDexClassLoader classLoader = new MyDexClassLoader(
                tmpFile.getPath(), getTargetContext().getDir("dex", 0).getPath(), null, getClass().getClassLoader());
            Class<?> klass = classLoader.doLoadClass(className);
            instance = klass.newInstance();
            if (!(instance instanceof ACICommand))
                throw new Exception("TypeNotMatch");
        } catch (Exception x) {
            log("Got exception when loading class: " + x); 
            return false;
        }

        ACICommand cmd = (ACICommand)instance;
        return cmd.registerSelf();
    }

    public int getViewId(View view) {
//      if (view.getId() != View.NO_ID)
//          return view.getId();
        if (tempIdRevMap.get(view) != null)
            return tempIdRevMap.get(view);
        int id = random.nextInt(0x7fffffff);
        while (findViewById(id) != null || tempIdMap.get(id) != null)
            id = random.nextInt(0x7fffffff);
        tempIdRevMap.put(view, id);
        tempIdMap.put(id, view);
        return id;
    }

    String getViewDesc(View v) {
        int loc[] = new int[2];
        v.getLocationOnScreen(loc);
        String ret = String.format("%s,0x%08x,@%dx%d,%dx%d",
            v.getClass().getSimpleName(), v.getId(),
            loc[0], loc[1], v.getWidth(), v.getHeight());
        if (v instanceof TextView) {
            String text = ((TextView)v).getText().toString();
            if (text.length() > 20) {
                text = text.substring(0, 20);
            }
            try {
                ret += ",T:" + java.net.URLEncoder.encode(text, "UTF-8");
            } catch (Exception e) {}
        }
        if (v.getId() != View.NO_ID) {
            Resources r = v.getResources();
            int id = v.getId();
            if (r != null && id != 0) {
                try {
                    String pkgname;
                    switch (id & 0xff000000) {
                        case 0x7f000000:
                            pkgname = "app";
                            break;
                        case 0x01000000:
                            pkgname = "android";
                            break;
                        default:
                            pkgname = r.getResourcePackageName(id);
                    }
                    ret += String.format(",ID:%s:%s/%s",
                        pkgname, r.getResourceTypeName(id),
                        r.getResourceEntryName(id));
                } catch (Resources.NotFoundException e) {
                }
            }
        }
        return ret;
    }
    
    public int findViewByClass(View view, String cls) {
        if (view.getClass().getName().indexOf(cls) != -1) {
            return getViewId(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)view;
            for (int i=0; i<vg.getChildCount(); i++) {
                int ret = findViewByClass(vg.getChildAt(i), cls);
                if (ret != VIEWID_NONE)
                    return ret;
            }
        }
        return VIEWID_NONE;
    }

    public String formatException(Throwable e) {
        StringBuilder msg = new StringBuilder();
        if (e instanceof ACIException)
        {
            ACIException aci = (ACIException)e;
            msg.append("ACIException ");
            msg.append(aci.getLevel());
            msg.append(" ");
            msg.append(aci.getToken());
            msg.append(" ");
            msg.append(Base64.encodeBytes(aci.getMessage().getBytes()));
            msg.append(" ");
            Collection<String> fileNames = aci.getExtraFileNames();
            msg.append(fileNames.size());
            Iterator<String> it = fileNames.iterator();
            while (it.hasNext())
            {
                String fn = it.next();
                msg.append(" ");
                msg.append(Base64.encodeBytes(fn.getBytes()));
            }
        }
        else
        {
            msg.append("Exception ");
            msg.append(e.getClass().getName());
            msg.append(" ");
            msg.append(Base64.encodeBytes(e.toString().getBytes()));
            msg.append(" ");
            msg.append(Base64.encodeBytes(getExceptionStackTrace(e).getBytes()));
        }
        return msg.toString();
    }
    
       

    private void registerCommand(ACICommand instance) throws ACIException {
        if (!instance.registerSelf())
            throw new ACIException(ACIException.LEVEL_INTERNAL,
                                   "CannotRegisterCommand",
                                   "Cannot register command");
    }

    @Override
    public void onStart() {
        try { mCMDSocket = mListenTask.get(); } catch (Exception x) { mCMDSocket = null; }
        
        super.onStart();
        try
        {
            if (mCMDSocket == null) throw new Exception("CMDSocket is null");
            log("Get connection.");
            
            BufferedReader inputReader  = new BufferedReader(new InputStreamReader(mCMDSocket.getInputStream()));
            BufferedWriter outputWriter = new BufferedWriter(new OutputStreamWriter(mCMDSocket.getOutputStream()));
            mRequestQueue               = new LinkedBlockingQueue<String>();
            mGeneratedRequestQueue      = new LinkedBlockingQueue<String>();
            mResponseQueue              = new LinkedBlockingQueue<String>();
            mCmdLog                     = new BufferedWriter(new FileWriter(mInstDataDir.getAbsolutePath() + "/cmd_log"));
            mMonkeyRunnerGenerator      = new MonkeyRunnerGenerator(mInstDataDir.getAbsolutePath() + "/monkeyrunner_script.py");
            mReadLineThread             = new Thread(new ReadLineRunnable(mRequestQueue, inputReader));
            mWriteLineThread            = new Thread(new WriteLineRunnable(mResponseQueue, outputWriter));
            // mActivityMap                = new TreeMap<Integer, Activity>();
            mChecker                    = new HashSet<Runnable>();
            mViewHashs                  = new HashSet<Integer>();
            mCurrentActivities          = new HashSet<Activity>();
            mPausedActivities           = new HashSet<Activity>();
            mDelayList                  = new ArrayList<ObjectWithNumberTag>();
            
            registerCommand(new ACICmdStart());
            registerCommand(new ACICmdSetRandomSeed());
            registerCommand(new ACICmdEnterReplay());
            registerCommand(new ACICmdExitReplay());
            registerCommand(new ACICmdEnableChecker());
            registerCommand(new ACICmdDisableChecker());
            registerCommand(new ACICmdSetOnlyViewTreeDescInStateOn());
            registerCommand(new ACICmdSetOnlyViewTreeDescInStateOff());
            registerCommand(new ACICmdEnableLifeCycleChecker());
            registerCommand(new ACICmdDisableLifeCycleChecker());
            registerCommand(new ACICmdGetViewGeo());
            registerCommand(new ACICmdGetViewId());
            registerCommand(new ACICmdSetViewText());
            registerCommand(new ACICmdGetViewChild());
            registerCommand(new ACICmdGetFocusedDecorViewImage());
            registerCommand(new ACICmdGetViewImage());
            registerCommand(new ACICmdPointerClickOnPoint());
            registerCommand(new ACICmdLongClick());
            registerCommand(new ACICmdMenuClick());
            registerCommand(new ACICmdKeyDown());
            registerCommand(new ACICmdKeyUp());
            registerCommand(new ACICmdInput());
            registerCommand(new ACICmdWaitForIdle());
            registerCommand(new ACICmdFinish());
            registerCommand(new ACICmdLoadApk());
            registerCommand(new ACICmdRotate());
            registerCommand(new ACICmdDumpViews());
            registerCommand(new ACICmdFindViewByClass());
            registerCommand(new ACICmdCrawl());
            registerCommand(new ACICmdCollect());
            registerCommand(new ACICmdSelect());
            registerCommand(new ACICmdPauseAndResume());
            registerCommand(new ACICmdStopAndRestart());
            registerCommand(new ACICmdRelaunchCurrentActivity());
            registerCommand(new ACICmdRecreateCurrentActivity());
            registerCommand(new ACICmdHintEdit());
            registerCommand(new ACICmdUnhintEdit());
            registerCommand(new ACICmdHintBtn());
            registerCommand(new ACICmdDoInjectEvents());
            registerCommand(new ACICmdDontInjectEvents());
            registerCommand(new ACICmdSetWaitForAsyncTaskOn());
            registerCommand(new ACICmdSetWaitForAsyncTaskOff());
            registerCommand(new ACICmdAddChecker());
            registerCommand(new ACICmdRemoveChecker());
            registerCommand(new ACICmdRemoveAllCheckers());
            registerCommand(new ACICmdShouldChangeDpi());
            registerCommand(new ACICmdChangeDpi());
            registerCommand(new ACICmdRoll());
            registerCommand(new ACICmdSleep());
            registerCommand(new ACICmdBeFaithful());
            registerCommand(new ACICmdAddBroadcast());
            registerCommand(new ACICmdAddIntent());
            
            registerCommand(new ACICmdDummyTest());

            mReadLineThread.start();
            mWriteLineThread.start();

            mSessionFinishFlag = false;
            
            while (!mSessionFinishFlag)
            {
                String cmdLine;
                if (!mDelayList.isEmpty())
                {
                    ObjectWithNumberTag[] dl_arr = mDelayList.toArray(new ObjectWithNumberTag[0]);
                    mDelayList.clear();
                    
                    Arrays.sort(dl_arr);
                    int last = 0;
                    for (int i = 0; i < dl_arr.length; ++ i)
                    {
                        int toSleep = dl_arr[i].tag - last;
                        if (toSleep > 0)
                            Thread.sleep(toSleep);
                        last = dl_arr[i].tag;
                        
                        DelayedRunnable r = (DelayedRunnable)dl_arr[i].value;
                        runOnMainSync(r);
                        Exception x =r.getException();
                        if (x != null)
                        {
                            onException(this, x);
                        }
                    }
                    
                    continue;
                }
                
                if (!mGeneratedRequestQueue.isEmpty() && !mInReplay)
                {
                    cmdLine = mGeneratedRequestQueue.take();
                }
                else
                {
                    cmdLine = mRequestQueue.poll(1000, TimeUnit.MILLISECONDS);
                    if (cmdLine == null)
                        continue;
                }

                if (mCmdLog != null)
                {
                    mCmdLog.write(cmdLine);
                    mCmdLog.newLine();
                    mCmdLog.flush();
                }
                
                log("Get line: " + cmdLine);
                ACICommand cmd = ACICommand.buildCommand(this, cmdLine);
                if (cmd != null)
                {
                    log("running cmd: " + cmd.getName());
                    if (cmd.isUISync())
                        runOnMainSync(cmd);
                    else cmd.run();
                    log("cmd finished: " + cmd.getName());

                    if (!cmd.isInternal())
                    {
                        waitForIdleSync();
                        if (cmd.isFromUser() && doInjectEvents())
                        {
                            log("inject event after: " + cmd.getName());
                            injectLifeCycleEvent();
                            log("event injected: " + cmd.getName());
                        }

                        if (isCheckerEnabled())
                        {
                            log("running checkers: " + cmd.getName());
                            CheckerRunnable chkr = new CheckerRunnable(this);
                            runOnMainSync(chkr);
                            log("checkers finished: " + cmd.getName());
                            if (chkr.gotException())
                            onException(this, chkr.getException());
                        }
                    }

                    if (cmd.isFromUser() || cmd.isError() || isInReplay())
                    {
                        addResponse(cmd.getResultString(), true);
                        log("response sent: " + cmd.getName());
                    }
                    else addResponse(cmd.getResultString(), false);
                }
            }
        }
        catch (Exception x)
        {
            x.printStackTrace();
            log("Got exception while starting instrumentation: " + formatException(x));
        }

        log("Finished.");
        finish(Activity.RESULT_OK, new Bundle());
    }

    public void injectLifeCycleEvent() {
        int rret = random.nextInt(20);
        if (rret == 1)
            addRequest("PauseAndResume");
        else if (rret == 2)
            addRequest("Rotate");
        else if (rret >= 3 && rret <= 5)
            addRequest("RelaunchCurrentActivity");
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnCreate");
        // synchronized (this) {
        //     mActivityMap.put(activity.hashCode(), activity);
        // }
        super.callActivityOnCreate(activity, icicle);
        // Hack the context of this activity
        addResponse("__Event__ ActivityCreate " + activity.getClass().getName() + "\n", true);
        try
        {
            ContextWrapper cw = (ContextWrapper)activity;
            Class<?> klass = Class.forName("android.content.ContextWrapper");
            Field base = klass.getDeclaredField("mBase");
            base.setAccessible(true);
            Context ctx = (Context)base.get(cw);
            ACIContextWrapper aci_cw = new ACIContextWrapper(ctx, this, activity);
            base.set(cw, aci_cw);
        }
        catch (Exception x)
        {
            x.printStackTrace();
            Log.e(mInstName, "Exception while hacking activity's context");
        }
    }

    @Override
    public void callActivityOnDestroy(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnDestroy");
        // synchronized (this) {
        //     mActivityMap.remove(activity.hashCode());
        // }
        super.callActivityOnDestroy(activity);
    }

    @Override
    public void callActivityOnNewIntent(Activity activity, Intent intent) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnNewIntent");
        super.callActivityOnNewIntent(activity, intent);
    }

    @Override
    public void callActivityOnPause(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnPause");
        synchronized (this)
        {
            if (activity.getParent() == null)
            {
                if (!mCurrentActivities.contains(activity))
                {
                    log("activity is not in record in resumed activity");
                }
                else mCurrentActivities.remove(activity);

                if (mPausedActivities.contains(activity))
                {
                    log("activity is already recorded in paused activity");
                }
                else mPausedActivities.add(activity);
            }
            addResponse("__Event__ ActivityLeave " + activity.getClass().getName() + "\n", true);
        }
        super.callActivityOnPause(activity);
    }

    @Override
    public void callActivityOnPostCreate(Activity activity, Bundle icicle) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnPostCreate");
        super.callActivityOnPostCreate(activity, icicle);
    }

    @Override
    public void callActivityOnRestart(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnRestart");
        super.callActivityOnRestart(activity);
    }

    @Override
    public void callActivityOnRestoreInstanceState(Activity activity, Bundle savedInstanceState) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnRestoreInstanceState");
        super.callActivityOnRestoreInstanceState(activity, savedInstanceState);
    }

    @Override
    public void callActivityOnResume(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnResume");
        synchronized (this) {
            if (activity.getParent() == null)
            {
                if (mPausedActivities.contains(activity))
                    mPausedActivities.remove(activity);

                if (mCurrentActivities.contains(activity))
                {
                    log("activity is already recorded in resumed activity");
                }
                else mCurrentActivities.add(activity);
            }
            addResponse("__Event__ ActivityEnter " + activity.getClass().getName() + "\n", true);
        }
        super.callActivityOnResume(activity);
    }

    @Override
    public void callActivityOnSaveInstanceState(Activity activity, Bundle outState) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnSaveInstanceState");
        super.callActivityOnSaveInstanceState(activity, outState);
    }

    @Override
    public void callActivityOnStart(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnStart");
        addResponse("__Event__ ActivityStart " + activity.getClass().getName() + "\n", true);
        super.callActivityOnStart(activity);
    }

    @Override
    public void callActivityOnStop(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnStop");
        synchronized (this) {
            if (activity.getParent() == null)
            {
                if (mCurrentActivities.contains(activity))
                {
                    mCurrentActivities.remove(activity);
                    log("activity is still recorded in resumed activity");
                }
                
                if (mPausedActivities.contains(activity))
                    mPausedActivities.remove(activity);
                else log("activity is already recorded in paused activity");
            }
            addResponse("__Event__ ActivityEnter " + activity.getClass().getName() + "\n", true);
        }
        super.callActivityOnStop(activity);
    }

    @Override
    public void callActivityOnUserLeaving(Activity activity) {
        log("Activity " + activity.hashCode() + "(" + activity.getClass().getName() + ") OnUserLeaving");
        super.callActivityOnUserLeaving(activity);
    }

    @Override
    public boolean onException(Object obj, Throwable e) {
        // Catch the exception and send it to host
        try
        {
            e.printStackTrace();
            tryGenerateCoverageData();
            addResponse("__Exception__ " + formatException(e)  + "\n", true);
            return true;
        }
        catch (Exception x)
        {
            x.printStackTrace();
            return false;
        }
    }

    public View findViewById(int id, View root) {
        if (root != null)
            return root.findViewById(id);
        else return null;
    }

    public View findViewById(int id, Activity act) {
        if (act != null)
            return act.findViewById(id);
        else return null;
    }

    public View findViewById(int id) {
        return null;
    }

    // External code from robotium::ViewFetcher.java
    // Dirty hack to get all view including dialogs 
    private static Class<?> windowManager;
    private static String windowManagerString;
    static {
        try {
            String windowManagerClassName;
            if (Integer.valueOf(android.os.Build.VERSION.SDK) >= 17) {
                windowManagerClassName = "android.view.WindowManagerGlobal";
            } else {
                windowManagerClassName = "android.view.WindowManagerImpl"; 
            }
            windowManager = Class.forName(windowManagerClassName);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        if (Integer.valueOf(android.os.Build.VERSION.SDK) >= 17) {
            windowManagerString = "sDefaultWindowManager";

        } else if(Integer.valueOf(android.os.Build.VERSION.SDK) >= 13) {
            windowManagerString = "sWindowManager";

        } else {
            windowManagerString = "mWindowManager";
        }
    }

    public View[] getWindowDecorViews() {
        Field viewsField;
        Field instanceField;
        Method getInstanceMethod;
        try {
            viewsField = windowManager.getDeclaredField("mViews");
            viewsField.setAccessible(true);
            Object instance;
            if (Integer.valueOf(android.os.Build.VERSION.SDK) >= 17)
            {
                getInstanceMethod = windowManager.getMethod("getInstance");
                getInstanceMethod.setAccessible(true);
                instance = getInstanceMethod.invoke(null);
            }
            else
            {
                instanceField = windowManager.getDeclaredField(windowManagerString);
                instanceField.setAccessible(true);
                instance = instanceField.get(null);
            }
            return (View[]) viewsField.get(instance);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void addHint(int id, String[] hints) {
        log(String.format("add hints for 0x%08x", id));
        hintDb.put(id, hints);
    }
    
    public String[] resolveHint(View target) {
        int id = target.getId();
        if (id != View.NO_ID) {
            String[] hints = hintDb.get(id);
            if (hints != null) {
                log(String.format("found hints for id 0x%08x", id));
                return hints;
            }
        }
        return null;
    }

    public void addEditProvider(int id, EditTextContent provider) {
        log("add provider " + provider.toString() + " for id " + id);
        editTextDb.put(id, provider);
    }

    public void removeEditProvider(int id) {
        editTextDb.remove(id);
    }

    public EditTextContent resolveEditContent(View target) {
        int id = target.getId();
        if (id != View.NO_ID) {
            EditTextContent provider = editTextDb.get(id);
            if (provider != null) {
                log("found provider " + provider.toString() + " for id " + id);
                return provider;
            }
        }
        return null;
    }

    void removeFocus() {
        setInTouchMode(true);
/*        mInst.runOnMainSync(new Runnable () {
            public void run() {
                for (View view : mInst.getWindowDecorViews()) {
                    view.requestFocusFromTouch();
                }
            }
        });*/
        waitForIdleSync();
    }

    public boolean mayRoll(Activity act) {
        if (act == null)
            return false;
        if (mRollableActs.containsKey(act.getClass().getName()))
            return true;
        int reqOri = act.getRequestedOrientation();
        if (reqOri == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
         || reqOri == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
         || reqOri == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
         || reqOri == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)
            return false;
        return true;
    }

    public void recordRollableAct(Activity act) {
        if (mRollableActs.containsKey(act.getClass().getName()))
            return;
        mRollableActs.put(act.getClass().getName(), act.getRequestedOrientation());
    }

    public void doRoll() {
        Activity act = getCurrentActivity();
        if (!mayRoll(act)) return;

        recordRollableAct(act);

        int currOri = getTargetContext().getResources().getConfiguration().orientation;
        int targetOri = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        if (currOri == Configuration.ORIENTATION_LANDSCAPE)
            targetOri = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        else if (currOri == Configuration.ORIENTATION_PORTRAIT)
            targetOri = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

        act.setRequestedOrientation(targetOri);
    }

    public void doRotate(final boolean force) throws Exception {
        final Map<String, Integer> origOri = new HashMap<String, Integer>();
        final Exception curEx[] = new Exception[1];
        final int currOri = getTargetContext().getResources().getConfiguration().orientation;
        final int targetOri;
        final int oldOri;
        if (currOri == Configuration.ORIENTATION_LANDSCAPE) {
            targetOri = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            oldOri = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        } else if (currOri == Configuration.ORIENTATION_PORTRAIT) {
            targetOri = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            oldOri = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        } else {
            targetOri = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            oldOri = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        removeFocus();
        final LifeCycleChecker lcc = new LifeCycleChecker(this);
        
        runOnMainSync(new Runnable () {
                public void run() {
                    try {
/*                        if (!force) {
                            for (Checker checker : Checker.orientationCheckers.values())
                                checker.reset();
                            if (isLifeCycleCheckerEnabled())
                            {
                                lcc.before();
                            }
                        }*/
                        View[] dvs = getWindowDecorViews();
                        if (dvs != null)
                            for (View view : dvs)
                                if (view.getContext() instanceof Activity) {
                                    if (!force) Checker.checkEveryView(view, Checker.orientationCheckers.values());
                                    Activity act = (Activity)view.getContext();
                                    if (mayRoll(act) || force) {
                                        int prefOri = act.getRequestedOrientation();
                                        act.setRequestedOrientation(targetOri);
                                        if (!force) recordRollableAct(act);
                                        origOri.put(act.getClass().getName(), prefOri);
                                    }
                                }
                    }
                    catch (Exception ex)
                    {
                        curEx[0] = ex;
                    }
                }
            });
        if (curEx[0] != null) throw curEx[0];
        waitForIdleSync();
        runOnMainSync(new Runnable () {
            public void run() {
                View[] dvs = getWindowDecorViews();
                if (dvs != null)
                    for (View view : dvs)
                        if (view.getContext() instanceof Activity) {
                            Activity act = (Activity)view.getContext();
                            if (mayRoll(act) || force) {
                                if (act.getRequestedOrientation() == targetOri) {
                                    if (origOri.containsKey(act.getClass().getName())) {
                                        act.setRequestedOrientation(origOri.get(act.getClass().getName()));
                                    }
//                                    act.setRequestedOrientation(oldOri);
                                }
                            }
                        }
            }
        });
        waitForIdleSync();
        removeFocus();
/*        if (!force) {
            runOnMainSync(new Runnable () {
                public void run() {
                    try {
                        // getTargetContext().getResources().getConfiguration().orientation = targetOri;
                        for (Checker checker : Checker.orientationCheckers.values())
                            checker.rotate();
                        for (View view : getWindowDecorViews()) {
                            if (view.getContext() instanceof Activity) {
                                Checker.checkEveryView(view, Checker.orientationCheckers.values());
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        curEx[0] = ex;
                    }
                }
            });
            if (curEx[0] != null) throw curEx[0];
            waitForIdleSync();

            if (isLifeCycleCheckerEnabled())
            {
                long startTime = System.currentTimeMillis();
                while (true) {
                    runOnMainSync(new Runnable () {
                            public void run() {
                                try {
                                    curEx[0] = null;
                                    lcc.after();
                                }
                                catch (Exception ex)
                                {
                                    curEx[0] = ex;
                                }
                            }
                        });
                    if (curEx[0] != null) {
                        if (curEx[0] instanceof BitmapComparator.SizeChanged &&
                            System.currentTimeMillis() - startTime < 5000) {
                            waitForIdleSync();
                        } else {
                            throw curEx[0];
                        }
                    } else {
                        break;
                    }
                }
            }
        }*/
    }

    public boolean addToViewHashs(int viewHash) {
        return mViewHashs.add(viewHash);
    }

    public boolean existsInViewHashs(int viewHash) {
        return mViewHashs.contains(viewHash);
    }

    public MonkeyRunnerGenerator getMonkeyRunnerGenerator() {
        return mMonkeyRunnerGenerator;
    }

    boolean instanceOf(Object o, Class k) {
        if (k == null) {
            return false;
        }
        return k.isInstance(o);
    }

    Object getResult(ResultRunnable rr) throws Exception {
        runOnMainSync(rr);
        if (rr.getException() != null)
            throw rr.getException();
        return rr.getResult();
    }
    
    static class IntentInfo {
    	public String action;
    	public Vector<String> category;

    	public String data;
    	public String mimetype;
    	
    	static class ExtraInfo {
    		enum ExtraType {
    			INTEGER,
    			STRING,
    			URI,
    		}
    		public ExtraType type;
    		public String key;
    		public String value;
    	}
    	public Vector<ExtraInfo> extra;
    	
    	public String component;
    	
    	public String prepareCmdline() {
    		StringBuilder sb = new StringBuilder("am broadcast");
    		
    		sb.append(" -a ");
    		sb.append(action);
    		if (category != null)
    			for (String cat : category) {
    				sb.append(" -c ");
    				sb.append(cat);
    			}
    		
    		if (data != null) {
    			sb.append(" -d ");
    			sb.append(data);
    		}
    		
    		if (mimetype != null) {
    			sb.append(" -t ");
    			sb.append(mimetype);
    		}
    		
    		if (extra != null)
    			for (ExtraInfo ext : extra) {
    				switch (ext.type) {
    				case INTEGER:
    					sb.append(" --ei ");
    					break;
    				case STRING:
    					sb.append(" -e ");
    					break;
    				case URI:
    					sb.append(" --eu ");
    					break;
    				default:
    					Log.e("IntentInfo.prepareIntent", "unknown extra type: " + ext.type);
    					Assert.fail();
    				}
					sb.append(ext.key);
					sb.append(" ");
					sb.append(ext.value);
    			}
    		if (component != null) {
    			sb.append(" ");
    			sb.append(component);
    		}
    		
    		return sb.toString();
    	}
    	
    	public Intent prepareIntent() {
    		Intent intent = new Intent();

    		intent.setAction(action);
    		if (category != null)
    			for (String cat : category)
    				intent.addCategory(cat);
    		
    		if (data != null)
    			if (mimetype != null)
    				intent.setDataAndType(Uri.parse(data), mimetype);
    			else
    				intent.setData(Uri.parse(data));
    		if (extra != null)
    			for (ExtraInfo ext : extra) {
    				switch (ext.type) {
    				case INTEGER:
    					intent.putExtra(ext.key, Integer.parseInt(ext.value));
    					break;
    				case STRING:
    					intent.putExtra(ext.key, ext.value);
    					break;
    				case URI:
    					intent.putExtra(ext.key, Uri.parse(ext.value));
    					break;
    				default:
    					Log.e("IntentInfo.prepareIntent", "unknown extra type: " + ext.type);
    					Assert.fail();
    				}
    			}
    		if (component != null)
    			intent.setComponent(ComponentName.unflattenFromString(component));
    		return intent;
    	}
    	
    	public String getDesc() {
    		StringBuilder sb = new StringBuilder("action=");
    		sb.append(action);
    		if (data != null) {
    			sb.append(",data=");
    			sb.append(data);
    		}
    		if (mimetype != null) {
    			sb.append(",mimetype=");
    			sb.append(mimetype);
    		}
    		if (component != null) {
    			sb.append(",component=");
    			sb.append(component);
    		}
    		return sb.toString();
    	}
    }
    
    static class AvailableOpInfo {
    	IntentInfo intentInfo;
    	int count;
    	int total;
    	double prob;
    }
    
    LinkedList<AvailableOpInfo> intentAvailable = new LinkedList<AvailableOpInfo>();
    LinkedList<AvailableOpInfo> broadcastAvailable = new LinkedList<AvailableOpInfo>();
    int broadcastCount = 3;
    int intentCount = 3;
    
    void addIntentReceiver(IntentInfo info, double prob, int count) {
    	AvailableOpInfo opInfo = new AvailableOpInfo();
    	opInfo.intentInfo = info;
    	opInfo.count = count;
    	opInfo.total = count;
    	opInfo.prob = prob;
    	intentAvailable.add(opInfo);
    }
    
    void addBroadcastReceiver(IntentInfo info, double prob, int count) {
    	AvailableOpInfo opInfo = new AvailableOpInfo();
    	opInfo.intentInfo = info;
    	opInfo.count = count;
    	opInfo.total = count;
    	opInfo.prob = prob;
    	broadcastAvailable.add(opInfo);
    }
    
    Vector<Operation> getBroadcastOps() {
    	Vector<Operation> ops = new Vector<Operation>();
    	if (broadcastCount == 0) return ops;
    	for (Iterator<AvailableOpInfo> it = broadcastAvailable.iterator(); it.hasNext();) {
    		AvailableOpInfo opInfo = it.next();
    		IntentInfo info = opInfo.intentInfo;
    		if (opInfo.count > 0) {
    			Operation op = new BroadcastOp(this, opInfo.prob, info);
    			ops.add(op);
    		}
    	}
    	return ops;
    }
    
    Vector<Operation> getIntentOps() {
    	Vector<Operation> ops = new Vector<Operation>();
    	if (intentCount == 0) return ops;
    	for (Iterator<AvailableOpInfo> it = intentAvailable.iterator(); it.hasNext();) {
    		AvailableOpInfo opInfo = it.next();
    		IntentInfo info = opInfo.intentInfo;
    		if (opInfo.count > 0) {
    			Operation op = new IntentOp(this, opInfo.prob, info);
    			ops.add(op);
    		}
    	}
    	return ops;
    }
}
