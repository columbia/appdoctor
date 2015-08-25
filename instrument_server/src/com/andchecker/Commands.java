package com.andchecker;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.andchecker.ACICmdMenuClick.GetFocusActivityRunnable;
import com.andchecker.ACInstrumentation.IntentInfo;
import com.andchecker.ACInstrumentation.IntentInfo.ExtraInfo;

class ACICmdStart extends ACICommand
{
    public String getName() { return "Start"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.startTarget();
        mResult.append(" OK " + Base64.encodeBytes(mInst.getInstrumentDataDir().getAbsolutePath().getBytes()));
        mInst.addRequest("SetRandomSeed " + mInst.random.nextLong() + " " + mInst.random.nextLong());
        mInst.addRequest("SetWaitForAsyncTaskOn");
    }
}

class ACICmdSetRandomSeed extends ACICommand
{
    public String getName() { return "SetRandomSeed"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        long seed = Long.decode(mTokens.nextToken());
        long chkseed = 0;
        if (mTokens.hasMoreTokens())
            chkseed = Long.decode(mTokens.nextToken());
        mInst.setRandomSeed(seed, chkseed);
        mResult.append(" OK");
    }
}

class ACICmdEnterReplay extends ACICommand
{
    public String getName() { return "EnterReplay"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setInReplay(true);
        mInst.setCheckerEnabled(false);
        mResult.append(" OK");
    }
}

class ACICmdExitReplay extends ACICommand
{
    public String getName() { return "ExitReplay"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setInReplay(false);
        mResult.append(" OK");
    }
}

class ACICmdBeFaithful extends ACICommand
{
    public String getName() { return "BeFaithful"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.conf.faithful_event = Boolean.parseBoolean(mTokens.nextToken());
        mResult.append(" OK");
    }
}

class ACICmdEnableChecker extends ACICommand
{
    public String getName() { return "EnableChecker"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setCheckerEnabled(true);
        mResult.append(" OK");
    }
}

class ACICmdDisableChecker extends ACICommand
{
    public String getName() { return "DisableChecker"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setCheckerEnabled(false);
        mResult.append(" OK");
    }
}

class ACICmdSetOnlyViewTreeDescInStateOn extends ACICommand
{
    public String getName() { return "SetOnlyViewTreeDescInStateOn"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setOnlyViewTreeDescInState(true);
        mResult.append(" OK");
    }
}

class ACICmdSetOnlyViewTreeDescInStateOff extends ACICommand
{
    public String getName() { return "SetOnlyViewTreeDescInStateOff"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setOnlyViewTreeDescInState(false);
        mResult.append(" OK");
    }
}

class ACICmdEnableLifeCycleChecker extends ACICommand
{
    public String getName() { return "EnableLifeCycleChecker"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setLifeCycleCheckerEnabled(true);
        mResult.append(" OK");
    }
}

class ACICmdDisableLifeCycleChecker extends ACICommand
{
    public String getName() { return "DisableLifeCycleChecker"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setLifeCycleCheckerEnabled(false);
        mResult.append(" OK");
    }
}

class ACICmdDoInjectEvents extends ACICommand
{
    public String getName() { return "DoInjectEvents"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setInjectEvents(true);
        mResult.append(" OK");
    }
}

class ACICmdDontInjectEvents extends ACICommand
{
    public String getName() { return "DontInjectEvents"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setInjectEvents(false);
        mResult.append(" OK");
    }
}

class ACICmdGetViewGeo extends ACICommand
{
    public String getName() { return "GetViewGeo"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String viewIdStr = mTokens.nextToken();
        int viewId = Integer.decode(viewIdStr).intValue();
        int geo[] = new int[4];

        if (mInst.getReachableViewGeoById(viewId, geo))
        {
            mResult.append(" OK");
            mResult.append(' '); mResult.append(geo[0]);
            mResult.append(' '); mResult.append(geo[1]);
            mResult.append(' '); mResult.append(geo[2]);
            mResult.append(' '); mResult.append(geo[3]);
        }
        else
        {
            mResult.append(" ViewNotFound");
        }
    }
}

class ACICmdGetViewId extends ACICommand
{
    public String getName() { return "GetViewId"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String viewNameStr = mTokens.nextToken();
        int viewId = mInst.getInternalId(viewNameStr);
        mResult.append(" OK " + String.format("0x%08x", viewId));
    }
}

class ACICmdSetViewText extends ACICommand
{
    public String getName() { return "SetViewText"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        String viewIdStr = mTokens.nextToken();
        int viewId = Integer.decode(viewIdStr).intValue();
        String content = new String(Base64.decode(mTokens.nextToken()));
        View view = mInst.findReachableViewById(viewId);
        if (view instanceof TextView) {
            TextView tv = (TextView)view;
            tv.setText(content);
        }
        mResult.append(" OK");
    }

}

class ACICmdGetViewChild extends ACICommand
{
    public String getName() { return "GetViewChild"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }

    public void execute() throws Exception {
        String viewIdStr = mTokens.nextToken();
        int viewId = Integer.decode(viewIdStr);
        String childNumStr = mTokens.nextToken();
        int childNum = Integer.parseInt(childNumStr);
        View view = mInst.findReachableViewById(viewId);
        if (view == null) {
            mResult.append(" ViewNotFound");
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)view;
            if (childNum >= vg.getChildCount()) {
                mResult.append(" OutOfRange");
            } else {
                mResult.append(" OK " + String.format("0x%x", mInst.getViewId(vg.getChildAt(childNum))));
            }
        } else {
            mResult.append(" TypeMismatch");
        }
    }
}

class ACICmdGetFocusedDecorViewImage extends ACICommand
{
    public String getName() { return "GetFocusedDecorViewImage"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        View dv = mInst.getFocusedDecorView();
        
        if (dv == null)
        {
            mResult.append(" ViewNotFound");
        }
        else
        {
            BBImage image = mInst.getViewImageAsBBImage(dv);
            // Generate png buffer and transfer it to client 
            ByteArrayOutputStream bas = new ByteArrayOutputStream();
            image.save(bas);
            mResult.append(" OK ");
            mResult.append(Base64.encodeBytes(bas.toByteArray()));
        }
    }
}

class ACICmdGetViewImage extends ACICommand
{
    public String getName() { return "GetViewImage"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception { 
        String viewIdStr = mTokens.nextToken();
        int viewId = Integer.decode(viewIdStr).intValue();
        View v = mInst.findReachableViewById(viewId);

        if (v != null)
        {
            BBImage image = mInst.getViewImageAsBBImage(v);
            // Generate png buffer and transfer it to client 
            ByteArrayOutputStream bas = new ByteArrayOutputStream();
            image.save(bas);
            mResult.append(" OK ");
            mResult.append(Base64.encodeBytes(bas.toByteArray()));
        }
        else { mResult.append(" ViewNotFound"); }
    }
}

class ACICmdPointerClickOnPoint extends ACICommand
{
    public String getName() { return "PointerClickOnPoint"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        long dtime, etime;
        MotionEvent me;
        int x = Integer.decode(mTokens.nextToken()).intValue();
        int y = Integer.decode(mTokens.nextToken()).intValue();

        dtime = SystemClock.uptimeMillis();
        etime = SystemClock.uptimeMillis();
        me = MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, x, y, 0);                        
        mInst.sendPointerSync(me);

        dtime = SystemClock.uptimeMillis();
        etime = SystemClock.uptimeMillis();
        me = MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, x, y, 0);                        
        mInst.sendPointerSync(me);

        mResult.append(" OK");
    }
}

class ACICmdLongClick extends ACICommand
{
    public String getName() { return "LongClick"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        String viewIdStr = mTokens.nextToken();
        int viewId = Integer.decode(viewIdStr).intValue();
        View view = mInst.findReachableViewById(viewId);
        view.performLongClick();
/*                    long dtime, etime;
                      MotionEvent me;
                      int x = Integer.decode(mTokens.nextToken()).intValue();
                      int y = Integer.decode(mTokens.nextToken()).intValue();
                      int downTime = Integer.decode(mTokens.nextToken());

                      dtime = SystemClock.uptimeMillis() - downTime * 1000;
                      etime = SystemClock.uptimeMillis() - downTime * 1000;
                      me = MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, x, y, 0);                        
                      sendPointerSync(me);

                      dtime = SystemClock.uptimeMillis() - downTime * 1000;
                      etime = SystemClock.uptimeMillis();
                      me = MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, x, y, 0);                        
                      sendPointerSync(me);*/

        mResult.append(" OK");
    }
}

class ACICmdMenuClick extends ACICommand
{
    class GetFocusActivityRunnable implements Runnable {
        Activity result = null;
        @Override
        public void run() {
            try {
                result = (Activity)mInst.getFocusedDecorView().getContext();
            } catch (Exception e) {
                result = null;
            }
        }
    }

    public String getName() { return "MenuClick"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        GetFocusActivityRunnable run = new GetFocusActivityRunnable();
        mInst.runOnMainSync(run);
        Activity act = run.result;
        String menuIdStr = mTokens.nextToken();
        int menuId = Integer.decode(menuIdStr).intValue();
        boolean succ = mInst.invokeMenuActionSync(act, menuId, 0);
        if (!succ)
            mResult.append(" ExecutionFailed");
        else
            mResult.append(" OK");
    }
}

class ACICmdKeyDown extends ACICommand
{
    public String getName() { return "KeyDown"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        String keyCodeStr = mTokens.nextToken();
        int keyCode = Integer.decode(keyCodeStr).intValue();
        mInst.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        mResult.append(" OK");
    }
}

class ACICmdKeyUp extends ACICommand
{
    public String getName() { return "KeyUp"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        String keyCodeStr = mTokens.nextToken();
        int keyCode = Integer.decode(keyCodeStr).intValue();
        mInst.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        mResult.append(" OK");
    }
}

class ACICmdInput extends ACICommand
{
    public String getName() { return "Input"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        String str = mTokens.nextToken();
        mInst.sendStringSync(new String(Base64.decode(str)));
        mResult.append(" OK");
    }
}

class ACICmdWaitForIdle extends ACICommand
{
    public String getName() { return "WaitForIdle"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.waitForIdleSync();
        mResult.append(" OK");
    }
}

class ACICmdFinish extends ACICommand
{
    public String getName() { return "Finish"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.tryGenerateCoverageData();
        mResult.append(" OK");
        mInst.mSessionFinishFlag = true;
    }
}

class ACICmdLoadApk extends ACICommand
{
    public String getName() { return "LoadApk"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String className = mTokens.nextToken();
        String b64 = mTokens.nextToken();
        byte[] classData = Base64.decode(b64);
                    
        if (mInst.loadCommand(className, classData))
            mResult.append(" OK");
        else
            mResult.append(" LoadFailed");
    }
}

class ACICmdRotate extends ACICommand
{
    public String getName() { return "Rotate"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        mInst.doRotate(false);
        mResult.append(" OK");
    }
}

class ACICmdDumpViews extends ACICommand
{
    void dumpView(View view, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<level; i++) sb.append("  ");
        sb.append(view.getClass().getName());
        sb.append(" ");
        sb.append(String.format("0x%08x", view.getId()));
        sb.append(" @");
        sb.append(view.getLeft());
        sb.append("x");
        sb.append(view.getTop());
        sb.append(" ");
        sb.append(view.getWidth());
        sb.append("x");
        sb.append(view.getHeight());
        Log.d("dumpView", sb.toString());
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)view;
            for (int i=0; i<vg.getChildCount(); i++)
                dumpView(vg.getChildAt(i), level + 1);
        }
    }
    
    public String getName() { return "DumpViews"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        View[] views = mInst.getWindowDecorViews();
        if (views != null)
            for (View view : views) {
                Context context = view.getContext();
                if (context != null)
                    Log.d("dumpView", "Context: " + context.getClass().getName());
                dumpView(view, 0);
            }
        mResult.append(" OK");
    }
}

class ACICmdFindViewByClass extends ACICommand
{
    public String getName() { return "FindViewByClass"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String cls = mTokens.nextToken();
        int result = ACInstrumentation.VIEWID_NONE;
        for (View view : mInst.getWindowDecorViews()) {
            int id = mInst.findViewByClass(view, cls);
            if (id != ACInstrumentation.VIEWID_NONE) {
                result = id;
                break;
            }
        }
        if (result == ACInstrumentation.VIEWID_NONE)
            mResult.append(" NotFound");
        else
            mResult.append(" OK " + String.format("0x%x", result));
    }
}

class ACICmdCrawl extends ACICommand
{
    public String getName() { return "Crawl"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        int count;
        int waitCount = 0;
        Vector<Operation> operations;
        
        while (true)
        {
            CollectOperationsRunnable collector =
                new CollectOperationsRunnable(mInst);
            mInst.runOnMainSync(collector);
            
            operations = collector.getResult();
            mInst.collectedOperations = operations;
            mInst.collectedFaithfulOperations = collector.getFaithfulResult();
            if (operations == null)
                throw new ACIException(ACIException.LEVEL_INTERNAL, "Crawl", "failed to crawl operations");
            
            count = operations.size();
            if (count == 0)
            {
                if (mInst.isAppStoped() || waitCount >= 20) break;
                ++ waitCount;
                mInst.log("WAIT FOR OPERATIONS TO SHOW UP");
                Thread.sleep(1000);
            }
            else break;
        }
            
        Log.d("Crawler", "Operation count: " + count);
        double sum = 0;
        for (int i=0; i<count; i++) {
            sum += operations.get(i).getProb();
        }
        for (int i=0; i<count; i++) {
            Log.d("Crawler", String.format("Operation %d: %s %.1f%%", i, operations.get(i).toString(), operations.get(i).getProb() / sum * 100));
        }
        if (count != 0) {
            // we should always grab a random number
            // so the replay is consistent
            double psum = mInst.random.nextDouble();
            if (mInst.isInReplay())
            {
                mResult.append(" OK Replaying");
            } else {
                psum *= sum;
                int selection = 0;
                sum = 0;
                for (int i=0; i<count; i++) {
                    sum += operations.get(i).getProb();
                    if (sum > psum) {
                        selection = i;
                        break;
                    }
                }
                Operation op = operations.get(selection);
                // Log.d("Crawler", "Selected op: " + selection + " op: " + op.getClass().getName());
                // op.doIt(false);
                mInst.addRequest("Select " + selection);
                if (mInst.doInjectEvents())
                    mInst.injectLifeCycleEvent();
                mResult.append(String.format(" OK %d %d %s %s", selection, count, op.getClass().getSimpleName(), op.toString()));
            }
            for (int i=0; i<count; i++) {
                mResult.append(" ");
                mResult.append(operations.get(i).toString());
            }
        } else {
            mResult.append(" NothingToDo");
        }
    }
}

class ACICmdCollect extends ACICommand
{
    public String getName() { return "Collect"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        int waitCount = 0;
        int count;
        byte[] stateData = "OutOfScope".getBytes();
        
        while (true)
        {
            CollectOperationsRunnable collector =
                new CollectOperationsRunnable(mInst);
            mInst.runOnMainSync(collector);
            
            Parcelable data = mInst.getCurrentActivityInstanceState();
            
            mInst.collectedOperations = collector.getResult();
            mInst.collectedFaithfulOperations = collector.getFaithfulResult();
            
            if (mInst.collectedOperations == null)
                throw new ACIException(ACIException.LEVEL_INTERNAL, "Crawl", "failed to crawl operations");
            
            
            if (data != null)
            {
                Parcel parcel = Parcel.obtain();
                data.writeToParcel(parcel, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                stateData = parcel.marshall();
            }
            else
            {
                mInst.collectedOperations.clear();
            }
            
            count = mInst.collectedOperations.size();
            if (count == 0)
            {
                if (mInst.isAppStoped() || waitCount >= 20) break;
                ++ waitCount;
                mInst.log("WAIT FOR OPERATIONS TO SHOW UP");
                Thread.sleep(1000);
            }
            else break;
        }
        
        mResult.append(" OK " + Base64.encodeBytes(stateData) + " " + count);
        for (int i = 0; i < count; i ++)
            mResult.append(" " + Base64.encodeBytes(mInst.collectedOperations.get(i).toString().getBytes()));
    }
}

class ACICmdAddChecker extends ACICommand
{
    public String  getName() { return "AddChecker"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String genre = mTokens.nextToken();
        String name = mTokens.nextToken();
        Map<String, Checker> checkerSet = null;
        if (genre.equals("View"))
            checkerSet = Checker.viewCheckers;
        else if (genre.equals("General"))
            checkerSet = Checker.generalCheckers;
        else if (genre.equals("Orientation"))
            checkerSet = Checker.orientationCheckers;

        if (checkerSet != null)
        {
            String result = Checker.addChecker(checkerSet, name);
            if (result != null)
            {
                mResult.append(" OK " + result);
            }
            else mResult.append(" Failed");
        }
        else mResult.append(" WrongCheckerSetName");
    }
}

class ACICmdRemoveChecker extends ACICommand
{
    public String  getName() { return "RemoveChecker"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String genre = mTokens.nextToken();
        String name = mTokens.nextToken();
        Map<String, Checker> checkerSet = null;
        if (genre.equals("View"))
            checkerSet = Checker.viewCheckers;
        else if (genre.equals("General"))
            checkerSet = Checker.generalCheckers;
        else if (genre.equals("Orientation"))
            checkerSet = Checker.orientationCheckers;

        if (checkerSet != null)
        {
            String result = Checker.removeChecker(checkerSet, name);
            if (result != null)
            {
                mResult.append(" OK " + result);
            }
            else mResult.append(" Failed");
        }
        else mResult.append(" WrongCheckerSetName");
    }
}

class ACICmdRemoveAllCheckers extends ACICommand
{
    public String  getName() { return "RemoveAllCheckers"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String genre = mTokens.nextToken();
        Map<String, Checker> checkerSet = null;
        if (genre.equals("View"))
            checkerSet = Checker.viewCheckers;
        else if (genre.equals("General"))
            checkerSet = Checker.generalCheckers;
        else if (genre.equals("Orientation"))
            checkerSet = Checker.orientationCheckers;

        if (checkerSet != null)
        {
            checkerSet.clear();
            mResult.append(" OK");
        }
        else mResult.append(" WrongCheckerSetName");
    }
}

class ACICmdSelect extends ACICommand
{
    public String getName() { return "Select"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        if (mInst.collectedOperations == null)
            mResult.append(" NotCollected");
        else {
            String numS = mTokens.nextToken();
            int num = Integer.decode(numS);
            if (num < 0 || num >= mInst.collectedOperations.size())
                mResult.append(" OutOfRange");
            else {
                Operation op;
                if (mInst.isFaithful())
                    op = mInst.collectedFaithfulOperations.get(num);
                else
                    op = mInst.collectedOperations.get(num);
                op.doIt(false, mCookie.startsWith("FAKE"));
                mResult.append(" OK " + op.toString());
            }
        }
    }
}

class ACICmdPauseAndResume extends ACICommand
{
    public String getName() { return "PauseAndResume"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        Activity act = mInst.getCurrentActivity();
        if (act != null)
        {
            LifeCycleChecker lcc = null;
            if (mInst.isLifeCycleCheckerEnabled())
            {
                lcc = new LifeCycleChecker(mInst);
                lcc.before();
            }
            mInst.callActivityOnPause(act);
            mInst.callActivityOnResume(act);
            if (lcc != null)
                lcc.after();
            mResult.append(" OK");
        }
        else mResult.append(" NoActivity");
    }
}

class ACICmdStopAndRestart extends ACICommand
{
    public String getName() { return "StopAndRestart"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        Activity act = mInst.getCurrentActivity();
        if (act != null)
        {
            LifeCycleChecker lcc = null;
            if (mInst.isLifeCycleCheckerEnabled())
            {
                lcc = new LifeCycleChecker(mInst);
                lcc.before();
            }
            mInst.callActivityOnUserLeaving(act);
            mInst.callActivityOnPause(act);
            Bundle bundle = new Bundle();
            mInst.callActivityOnSaveInstanceState(act, bundle);
            mInst.callActivityOnStop(act);
            mInst.callActivityOnRestart(act);
            mInst.callActivityOnStart(act);
//            mInst.callActivityOnResume(act);
            mInst.activityMethodPerformResume.invoke(act);
            if (lcc != null)
                lcc.after();
            mResult.append(" OK");
        }
        else mResult.append(" NoActivity");
    }
}

class ACICmdRelaunchCurrentActivity extends ACICommand
{
    public String getName() { return "RelaunchCurrentActivity"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }

    private Activity act;
    private LifeCycleChecker lcc = null;
    private Exception ex = null;
    
    public void execute() throws Exception {
        act = mInst.getCurrentActivity();
        ex = null;
        lcc = null;
        
        if (act != null)
        {
            mInst.runOnMainSync(new Runnable() {
                    public void run() {
                        try
                        {
/*                            if (mInst.isCheckerEnabled())
                            {
                                // orientation checkers
                                for (Checker checker : Checker.orientationCheckers.values())
                                    checker.reset();
                                View[] dvs = mInst.getWindowDecorViews();
                                if (dvs != null)
                                    for (View v : dvs) {
                                        Checker.checkEveryView(v, Checker.orientationCheckers.values());
                                    }
                            }
                            
                            if (mInst.isLifeCycleCheckerEnabled())
                            {
                                lcc = new LifeCycleChecker(mInst);
                                lcc.before();
                            }*/

                            if (!mInst.relaunchActivity(act)) throw new ACIException(ACIException.LEVEL_INTERNAL, "RelaunchFailed", "Cannot relaunch current activity");
                        }
                        catch (Exception x)
                        { ex = x; }
                    }
                });
            

            if (ex != null)
            {
                if (ex instanceof ACIException && ((ACIException)ex).getLevel() == ACIException.LEVEL_INTERNAL)
                {
                    mResult.append(" " + ((ACIException)ex).getToken());
                }
                else throw ex;
            }

            mInst.waitForIdleSync();
/*            mInst.runOnMainSync(new Runnable() {
                    public void run() {
                        try
                        {
                            if (mInst.isCheckerEnabled())
                            {
                                // orientation checkers
                                for (Checker checker : Checker.orientationCheckers.values())
                                    checker.rotate();
                                View[] dvs = mInst.getWindowDecorViews();
                                if (dvs != null)
                                for (View v : dvs) {
                                    Checker.checkEveryView(v, Checker.orientationCheckers.values());
                                }
                            }
                            
                            if (lcc != null)
                            {
                                lcc.after();
                            }
                        }
                        catch (Exception x)
                        {
                            ex = x;
                        }
                    }
                });*/

            if (ex != null)
                throw ex;

            mResult.append(" OK");
        }
        else mResult.append(" NoCurrentActivity");
    }
}

class ACICmdRecreateCurrentActivity extends ACICommand
{
    public String getName() { return "$RecreateCurrentActivity"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        Activity act = mInst.getCurrentActivity();
        if (act != null)
        {
            act.recreate();
            mResult.append(" OK");
        }
        else mResult.append(" NoActivity");
    }
}

class ACICmdSleep extends ACICommand
{
    public String getName() { return "Sleep"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        int ms = Integer.decode(mTokens.nextToken());
        Thread.sleep(ms);
        mResult.append(" OK");
    }
}

class ACICmdDummyTest extends ACICommand
{
    public String getName() { return "DummyTest"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        ByteBuffer buf = mInst.getViewImageAsNativeBuffer(mInst.getFocusedDecorView());
        ByteBuffer buf_i = mInst.getViewImageAsNativeBuffer(mInst.getFocusedDecorView());

        if (!NativeHelper.equalNativeByteBuffer(buf, buf_i))
            Log.e("ACInstrumentation", "BAD TEST1");

        buf_i.rewind();
        buf_i.put(0, (byte)(255 - buf_i.get(0)));

        if (NativeHelper.equalNativeByteBuffer(buf, buf_i))
            Log.e("ACInstrumentation", "BAD TEST2");

        NativeHelper.freeNativeByteBuffer(buf);
        NativeHelper.freeNativeByteBuffer(buf_i);
        mResult.append(" OK");
    }
}

class ACICmdSetWaitForAsyncTaskOn extends ACICommand
{
    public String getName() { return "SetWaitForAsyncTaskOn"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setWaitForAsyncTask(true);
        mResult.append(" OK");
    }
}

class ACICmdSetWaitForAsyncTaskOff extends ACICommand
{
    public String getName() { return "SetWaitForAsyncTaskOff"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mInst.setWaitForAsyncTask(false);
        mResult.append(" OK");
    }
}

class ACICmdHintEdit extends ACICommand
{
    public String getName() { return "HintEdit"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String viewIdS = mTokens.nextToken();
        int viewId = Integer.decode(viewIdS);
        String[] values = decodeArray();
        EditTextContent provider = null;
        if (values.length == 1) {
            provider = new ConstEditTextContent(values[0]);
        } else {
            provider = new ConstMultiEditTextContent(values);
        }

        mInst.addEditProvider(viewId, provider);
        mResult.append(" OK");
    }
}

class ACICmdHintBtn extends ACICommand
{
    public String getName() { return "HintBtn"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String viewIdS = mTokens.nextToken();
        int viewId = Integer.decode(viewIdS);
        String[] values = decodeArray();

        mInst.addHint(viewId, values);
        mResult.append(" OK");
    }
}

class ACICmdUnhintEdit extends ACICommand
{
    public String getName() { return "UnhintEdit"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        String viewIdS = mTokens.nextToken();
        int viewId = Integer.decode(viewIdS);
        mInst.removeEditProvider(viewId);
        mResult.append(" OK");
    }
}

class ACICmdShouldChangeDpi extends ACICommand
{
    public String getName() { return "ShouldChangeDpi"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mResult.append(" OK");
        try {
            View view = mInst.getFocusedDecorView();
            if (view != null) {
                int viewHash = Checker.getViewHashFromAllCheckers(view);
                mResult.append(mInst.existsInViewHashs(viewHash) ? " NO" : " YES");
            } else {
                mResult.append(" NO");
            }
        } catch (ACIException e) {
            mResult.append(" NO");
        }
    }
}

class ACICmdChangeDpi extends ACICommand
{
    public String getName() { return "ChangeDpi"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        String density = mTokens.nextToken();
        mInst.systemPropertiesMethodSet.invoke(null, "qemu.sf.lcd_density", density);
        Activity act = mInst.getCurrentActivity();
        if (act != null)
            mInst.relaunchActivity(act);
        mResult.append(" OK");
    }
}

class ACICmdRoll extends ACICommand
{
    public String getName() { return "Roll"; }
    public boolean isUISync() { return true; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        Configuration config = mInst.getTargetContext().getResources().getConfiguration();
        int ori;
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ori = Configuration.ORIENTATION_PORTRAIT;
        } else {
            ori = Configuration.ORIENTATION_LANDSCAPE;
        }

        mInst.doRoll();
/*        config = mInst.getTargetContext().getResources().getConfiguration();
        if (ori != config.orientation) {
            mResult.append(" FAIL");
        } else {*/
            mResult.append(" OK");
            if (ori == Configuration.ORIENTATION_LANDSCAPE) {
                mResult.append(" LANDSCAPE");
            } else {
                mResult.append(" PORTRAIT");
            }
//        }
    }
}

class CmdlineIntentParser {
    StringTokenizer mTokens;

    CmdlineIntentParser(StringTokenizer tokens) {
        mTokens = tokens;
    }

    public IntentInfo parseIntent(ACInstrumentation instrumenter) {
        int args = Integer.parseInt(mTokens.nextToken());
        IntentInfo info = new IntentInfo();
        for (int i=0; i<args; i++) {
            String arg_key = mTokens.nextToken();
            if (arg_key.equals("a")) {
                info.action = mTokens.nextToken();
            } else if (arg_key.equals("c")) {
            	if (info.category == null) info.category = new Vector<String>();
                info.category.add(mTokens.nextToken());
            } else if (arg_key.equals("d")) {
                info.data = mTokens.nextToken();
            } else if (arg_key.equals("m")) {
                info.mimetype = mTokens.nextToken();
            } else if (arg_key.startsWith("e")) {
                ExtraInfo ei = new ExtraInfo();
                if (arg_key.equals("ei")) {
                    ei.type = ExtraInfo.ExtraType.INTEGER;
                } else if (arg_key.equals("eu")) {
                    ei.type = ExtraInfo.ExtraType.URI;
                } else if (arg_key.equals("es")) {
                    ei.type = ExtraInfo.ExtraType.STRING;
                } else {
                    ei.type = ExtraInfo.ExtraType.STRING;
                }
                ei.key = mTokens.nextToken();
                ei.value = mTokens.nextToken();
            	if (info.extra == null) info.extra = new Vector<ExtraInfo>();
                info.extra.add(ei);
            } else if (arg_key.equals("comp")) {
                info.component = instrumenter.getTargetContext().getPackageName() + "/" + mTokens.nextToken();
            }
		}
        return info;
    }
}

class ACICmdAddBroadcast extends ACICommand
{
	public String getName() { return "AddBroadcast"; }
	public boolean isUISync() { return false; }
	public boolean isInternal() { return false; }
	public void execute() throws Exception {
        double prob = Double.parseDouble(mTokens.nextToken());
        int count = Integer.parseInt(mTokens.nextToken());
        IntentInfo info = new CmdlineIntentParser(mTokens).parseIntent(mInst);
        mInst.addBroadcastReceiver(info, prob, count);
        mResult.append(" OK");
	}
}

class ACICmdAddIntent extends ACICommand
{
    public String getName() { return "AddIntent"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return false; }
    public void execute() throws Exception {
        double prob = Double.parseDouble(mTokens.nextToken());
        int count = Integer.parseInt(mTokens.nextToken());
        IntentInfo info = new CmdlineIntentParser(mTokens).parseIntent(mInst);
        mInst.addIntentReceiver(info, prob, count);
        mResult.append(" OK");
    }
}
