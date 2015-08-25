package com.andchecker;

import android.os.MessageQueue;
import android.os.Message;
import android.os.Looper;
import java.util.concurrent.SynchronousQueue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.Class;
import java.lang.reflect.InvocationTargetException;
import android.os.Parcel;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.FileReader;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;

public abstract class ACIMessageProxy extends Thread
{
    protected ACInstrumentation        mInst;
    private   MessageQueue             mTargetQueue;
    private   SynchronousQueue<Object> mSync;
    private   Method mQueueNext;
    private   Method mQueueEnqueueMessage;
    private   Field  mMessageWhen;
    
    public ACIMessageProxy(ACInstrumentation instrumentation,
                           MessageQueue targetQueue,
                           SynchronousQueue<Object> sync) {
        mInst = instrumentation;
        mTargetQueue = targetQueue;
        mSync = sync;
    }

    protected void prepare() { }
    protected void msgArrived(Message msg, long when) { }
    
    protected final boolean msgForward(Message msg, long when) {
        try{
            mQueueEnqueueMessage.invoke(mTargetQueue, msg, when);
            return true;
        }
        catch (InvocationTargetException x)
        {
            x.getTargetException().printStackTrace();
            mInst.log("InvocationTargetException!");
            return false;
        }
        catch (Exception x)
        {
            x.printStackTrace();
            return false;
        }
    }
    
    public void run() {
        prepare();
        
        Looper looper;
        MessageQueue queue;
        Looper.prepare();
        looper = Looper.myLooper();
        queue = Looper.myQueue();
        try
        {
            mQueueNext =
                Class.forName("android.os.MessageQueue").
                getDeclaredMethod("next");
            mQueueEnqueueMessage =
                Class.forName("android.os.MessageQueue").
                getDeclaredMethod("enqueueMessage",
                                  Class.forName("android.os.Message"),
                                  Long.TYPE);
            mMessageWhen =
                Class.forName("android.os.Message").
                getDeclaredField("when");
            
            mQueueNext.setAccessible(true);
            mQueueEnqueueMessage.setAccessible(true);
            mMessageWhen.setAccessible(true);
            
            mSync.put(queue);
        }
        catch (Exception x)
        {
            mQueueNext = null;
            mQueueEnqueueMessage = null;
            
            x.printStackTrace();
            mInst.log("Exception in message proxy: " + x.getMessage());
            return;
        }
        
        while (true) {
            try
            {
                Message msg = (Message)mQueueNext.invoke(queue); // might block
                if (msg != null) {
                    Message cpy = Message.obtain(msg);
                    long when = msg.getWhen();
                    msg.recycle();
                    if (mInst.shouldManageMessage(cpy))
                        msgArrived(cpy, when);
                    else msgForward(cpy, when);
                }
            }
            catch (InvocationTargetException x)
            {
                x.getTargetException().printStackTrace();
                mInst.log("InvocationTargetException!");
            }
            catch (Exception x)
            {
                x.printStackTrace();
                break;
            }
        }
    }
}

class MessageInfo
{
    public int what;
    public int arg1;
    public int arg2;
    public int objHash;
    public int cbHash;
    public int targetHash;
    public String objDesc = "";
    public String cbDesc = "";
    public String targetDesc = "";

    public boolean equals(Object object) {
        if (!(object instanceof MessageInfo)) return false;
        MessageInfo i = (MessageInfo)object;
        return
            i.what == what && i.arg1 == arg1 && i.arg2 == arg2 &&
            cbHash == i.cbHash && objHash == i.objHash && targetHash == i.targetHash;
    }

    static private int hash(int x) {
        x ^= (x << 13);
        x ^= (x >>> 17);        
        x ^= (x << 5);
        return x;
    }

    static private int hash(Object x) {
        return x == null ? 0 : x.getClass().getName().hashCode();
    }

    static private String getDesc(Object x) {
        if (x == null) return "NULL";
        else return x.getClass().getName();
    }
        
    public int hashCode() {
        return
            hash(what) ^ hash(arg1) ^ hash(arg2) ^
            cbHash ^ objHash ^ targetHash;
    }

    static public MessageInfo obtain(Message msg) {
        MessageInfo info = new MessageInfo();
        info.what       = msg.what;
        info.arg1       = msg.arg1;
        info.arg2       = msg.arg2;
        info.objHash    = hash(msg.obj);
        info.cbHash     = hash(msg.getCallback());
        info.targetHash = hash(msg.getTarget());
        info.objDesc    = getDesc(msg.obj);
        info.cbDesc     = getDesc(msg.getCallback());
        info.targetDesc = getDesc(msg.getTarget());
        return info;
    }

    public String toString() {
        return what + " " + arg1 + " " + arg2 + " " + objHash + " " + cbHash + " " + targetHash + " ## obj[" + objDesc + "] cb[" + cbDesc + "] target[" + targetDesc + "]";
    }

    static public MessageInfo obtain(String s) {
        try
        {
            StringTokenizer tokens = new StringTokenizer(s);
            MessageInfo info = new MessageInfo();
            
            info.what = Integer.decode(tokens.nextToken());
            info.arg1 = Integer.decode(tokens.nextToken());
            info.arg2 = Integer.decode(tokens.nextToken());
            info.objHash = Integer.decode(tokens.nextToken());
            info.cbHash = Integer.decode(tokens.nextToken());
            info.targetHash = Integer.decode(tokens.nextToken());

            tokens.nextToken();

            String token;

            // XXX: Too ugly
            token = tokens.nextToken(); info.objDesc = token.substring(4, token.length() - 1);
            token = tokens.nextToken(); info.cbDesc = token.substring(3, token.length() - 1);
            token = tokens.nextToken(); info.targetDesc = token.substring(7, token.length() - 1);

            return info;
        }
        catch (Exception x)
        {
            return null;
        }
    }
}

class ACIMessageRecordProxy extends ACIMessageProxy
{
    private BufferedWriter mMsgLog;
    
    public ACIMessageRecordProxy(ACInstrumentation instrumentation,
                                 MessageQueue targetQueue,
                                 SynchronousQueue<Object> sync) {
        super(instrumentation, targetQueue, sync);        
    }

    protected void prepare() {
        try
        {
            String path = mInst.conf.message_log_path.length() == 0 ?
                mInst.getInstrumentDataDir().getAbsolutePath() + "/msg_log" :
                mInst.conf.message_log_path;
            mInst.log("write message log path: " + path);
            mMsgLog = new BufferedWriter(new FileWriter(path));
        }
        catch (Exception x)
        {
            mInst.log("Cannot open message log file");
            mMsgLog = null;
        }
    }

    protected void msgArrived(Message msg, long when) {
        if (mMsgLog != null)
        {
            try
            {
                MessageInfo info = MessageInfo.obtain(msg);
                mInst.log("Get message: " + info);
                mMsgLog.write(info.toString());
                mMsgLog.newLine();
                mMsgLog.flush();
            }
            catch (Exception x)
            { }
        }
        
        msgForward(msg, when);
    }
    
}

class ACIMessageReplayProxy extends ACIMessageProxy
{
    class BufferedMessage
    {
        public Message msg;
        public long when;
    }
    
    private LinkedList<MessageInfo> mOrder;
    private HashMap<MessageInfo, LinkedList<BufferedMessage> > mBuffer;
    private HashMap<MessageInfo, Integer> mRefCount;

    public ACIMessageReplayProxy(ACInstrumentation instrumentation,
                                 MessageQueue targetQueue,
                                 SynchronousQueue<Object> sync) {
        super(instrumentation, targetQueue, sync);
        mOrder = new LinkedList<MessageInfo>();
        mBuffer = new HashMap<MessageInfo, LinkedList<BufferedMessage> >();
        mRefCount = new HashMap<MessageInfo, Integer>();
    }

    protected void prepare() {
        BufferedReader in;
        int retry = 0;
        
        while (true)
        {
            try
            {
                String path = mInst.conf.message_log_path.length() == 0 ?
                    mInst.getTargetContext().getApplicationInfo().dataDir + "/__instrument_msg_log" : 
                    mInst.conf.message_log_path;
                mInst.log("read message log path: " + path);
                in = new BufferedReader(new FileReader(path));
            }
            catch (Exception x)
            {
                if (++ retry == 5)
                    throw new RuntimeException("Cannot open message log file");
                else
                {
                    mInst.log("Cannnot open message log, wait for 3s");
                    try
                    { Thread.sleep(3000); }
                    catch (Exception xx) { }
                    continue;
                }
            }
            
            try {
                String line;
                while ((line = in.readLine()) != null)
                {
                    MessageInfo info = MessageInfo.obtain(line);
                    mOrder.offer(info);

                    if (mRefCount.containsKey(info))
                    {
                        mRefCount.put(info, mRefCount.get(info) + 1);
                    }
                    else mRefCount.put(info, 1);
                }

                in.close();
            } catch (Exception x)
            {
                mInst.log("Exception during read message logs");
                x.printStackTrace();
            }
            break;
        }
    }
    
    protected void msgArrived(Message msg, long when) {
        MessageInfo info = MessageInfo.obtain(msg);

        if (!mRefCount.containsKey(info) ||
            mRefCount.get(info) == 0)
        {
            mInst.log("Replay message since no record: " + info);
            msgForward(msg, when);
            return;
        }
        else
        {
            mInst.log("Buffering message: " + info);
        }
        
        LinkedList<BufferedMessage> list = mBuffer.get(info);
        if (list == null)
        {
            list = new LinkedList<BufferedMessage>();
            mBuffer.put(info, list);
        }
        BufferedMessage bmsg = new BufferedMessage();
        bmsg.msg = msg;
        bmsg.when = when;
        list.offer(bmsg);

        while (!mOrder.isEmpty())
        {
            MessageInfo headInfo = mOrder.peek();
            list = mBuffer.get(headInfo);
            if (list != null)
            {
                bmsg = list.poll();
                if (bmsg != null)
                {
                    mInst.log("Replay message: " + headInfo);
                    int rc = mRefCount.get(headInfo) - 1;
                    if (rc > 0)
                        mRefCount.put(headInfo, rc);
                    else mRefCount.remove(headInfo);
                    mOrder.remove();
                    msgForward(bmsg.msg, bmsg.when);
                    continue;
                }
            }
            break;
        }
    }
}
