package com.andchecker;

import java.util.concurrent.LinkedBlockingQueue;
import android.util.Log;
import android.graphics.Color;

public class Utils {
	static double dist(double x1, double y1, double z1, double x2, double y2, double z2) {
		return Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2) + (z1-z2)*(z1-z2));
	}
	
	static int blendColor(int color1, int color2) {
		double a1 = (double)Color.alpha(color1) / 255.0;
		double a2 = (double)Color.alpha(color2) / 255.0;
		double ao = a1 + a2 * (1-a1);
		int ro = (int)((Color.red(color1) * a1 + Color.red(color2) * a2 * (1-a1))/ao);
		int go = (int)((Color.green(color1) * a1 + Color.green(color2) * a2 * (1-a1))/ao);
		int bo = (int)((Color.blue(color1) * a1 + Color.blue(color2) * a2 * (1-a1))/ao);
		return Color.argb((int)(ao * 255), ro, go, bo);
	}
}

class AsyncIONode<E>
{
    private LinkedBlockingQueue<E> mInput;
    private LinkedBlockingQueue<E> mOutput;

    public AsyncIONode() {
        mInput = new LinkedBlockingQueue<E>();
        mOutput = new LinkedBlockingQueue<E>();
    }

    public final void sendSync(E msg) {
        while (true)
        {
            try
            { mInput.put(msg); }
            catch (InterruptedException x)
            {
                Log.d("AsyncIONode", "interrupted during sending");
                continue;
            }
            break;
        }
    }

    public final E receiveSync() {
        E msg;
        while (true)
        {
            try
            { msg = mOutput.take(); }
            catch (InterruptedException x)
            {
                Log.d("AsyncIONode", "interrupted during receiving");
                continue;
            }
            break;
        }
        return msg;
    }

    public final void putSync(E msg)
    {
        while (true)
        {
            try
            { mOutput.put(msg); }
            catch (InterruptedException x)
            {
                Log.d("AsyncIONode", "interrupted during putting");
                continue;
            }
            break;
        }
    }

    public final E getSync() {
        E msg;
        while (true)
        {
            try
            { msg = mInput.take(); }
            catch (InterruptedException x)
            {
                Log.d("AsyncIONode", "interrupted during getting");
                continue;
            }
            break;
        }
        return msg;
    }

    public final void send(E msg) throws InterruptedException {
        mInput.put(msg);
    }

    public final E receive() throws InterruptedException {
        return mOutput.take();
    }

    public final void put(E msg) throws InterruptedException
    {
        mOutput.put(msg);
    }

    public final E get() throws InterruptedException {
        return mInput.take();
    }

}

class FuncRunnable implements Runnable {
    protected void work() throws Exception { }
    
    private Object result;
    private Exception exc;
    private boolean mComplete;
   
    @Override
    public final void run() {
        try {
            work();
        } catch (Exception e) {
            exc = e;
            result = null;
        }
        
        synchronized (this) {
            mComplete = true;
            notifyAll();
        }
    }

    protected final void set(Object o) { result = o; }

    public final void waitForComplete() {
        synchronized (this) {
            while (!mComplete) {
                try {
                    wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }
    
    public final Object get() throws Exception {
        if (exc != null)
            throw exc;
        else return result;
    }

    public final Object getSync() throws Exception {
        waitForComplete();
        return get();
    }
}
