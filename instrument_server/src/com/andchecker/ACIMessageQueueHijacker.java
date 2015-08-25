package com.andchecker;

import android.os.MessageQueue;
import android.os.Message;
import android.os.Looper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

import java.net.Socket;
import java.net.ServerSocket;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.StringTokenizer;

class ACIMessageQueueHijacker implements Runnable
{
    protected ACInstrumentation mInst;
    protected MessageQueue mQueue;
    
    private static boolean mReflectionInit = false;
    private static Method mQueueNext;
    private static Method mQueueEnqueueMessage;
    private static Field  mMessageWhen;

    public ACIMessageQueueHijacker(ACInstrumentation instrumentation, MessageQueue queue) {
        mInst = instrumentation;
        mQueue = queue;
    }

    private boolean checkForExitCondition(Message msg) {
        // TODO: Different Android versions has multiple ways to exit
        return false;
    }

    public void prepare() { }

    protected void msgArrived(Message msg) {
        msgForward(msg);
    }
    

    protected final void msgForward(Message msg) {
        msg.getTarget().dispatchMessage(msg);
        msg.recycle();
    }

    public void run() {
        if (!mReflectionInit)
        {
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

                mReflectionInit = true;
            }
            catch (Exception x)
            {
                mQueueNext = null;
                mQueueEnqueueMessage = null;
                mMessageWhen = null;
            
                x.printStackTrace();
                mInst.log("Exception initializing reflection in hijacker: " + x.getMessage());
                return;
            }
        }
        
        for (;;)
        {
            Message msg;
            try
            {
                msg = (Message)mQueueNext.invoke(mQueue); // might block
            }
            catch (InvocationTargetException x)
            {
                x.getTargetException().printStackTrace();
                mInst.log("msg: InvocationTargetException!");
                msg = null;
            }
            catch (Exception x)
            {
                x.printStackTrace();
                msg = null;
            }

            if (checkForExitCondition(msg))
            {
                mInst.log("msg: hijacker reaches a exit condition");
                Looper.myLooper().quit();
                return;
            }

            if (msg != null)
            {
                msgArrived(msg);
            }
        }
    }
}

class ACIMessageQueueRecorder extends ACIMessageQueueHijacker
{
    private BufferedWriter mMsgLog;
    
    public ACIMessageQueueRecorder(ACInstrumentation instrumentation, MessageQueue queue) {
        super(instrumentation, queue);
    }

    public void prepare() {
        try
        {
            String path = mInst.conf.message_log_path.length() == 0 ?
                mInst.getInstrumentDataDir().getAbsolutePath() + "/msg_log" :
                mInst.conf.message_log_path;
            mInst.log("msg: write message log path: " + path);
            mMsgLog = new BufferedWriter(new FileWriter(path));
        }
        catch (Exception x)
        {
            mInst.log("msg: Cannot open message log file");
            mMsgLog = null;
        }
    }

    protected void msgForwardAndRecord(Message msg) {
        if (mMsgLog != null)
        {
            try
            {
                MessageInfo info = MessageInfo.obtain(msg);
                mInst.log("msg: record msg: " + info);
                mMsgLog.write(info.toString());
                mMsgLog.newLine();
                mMsgLog.flush();
            }
            catch (Exception x)
            { }
        }

        msgForward(msg);
    }
    
    protected void msgArrived(Message msg) {
        msgForwardAndRecord(msg);
    }
}

class ACIMessageQueueReplayer extends ACIMessageQueueRecorder
{
    class BufferedMessage
    {
        public Message msg;
    }
    
    private LinkedList<MessageInfo> mOrder;
    private HashMap<MessageInfo, LinkedList<BufferedMessage> > mBuffer;
    private HashMap<MessageInfo, Integer> mRefCount;

    public ACIMessageQueueReplayer(ACInstrumentation instrumentation, MessageQueue queue) {
        super(instrumentation, queue);
        
        mOrder = new LinkedList<MessageInfo>();
        mBuffer = new HashMap<MessageInfo, LinkedList<BufferedMessage> >();
        mRefCount = new HashMap<MessageInfo, Integer>();
    }

    public void prepare() {
        BufferedReader in;
        int retry = 0;

        while (true)
        {
            try
            {
                String path = mInst.conf.message_log_path.length() == 0 ?
                    mInst.getTargetContext().getApplicationInfo().dataDir + "/__instrument_msg_log" : 
                    mInst.conf.message_log_path;
                mInst.log("msg: read message log path: " + path);
                in = new BufferedReader(new FileReader(path));
            }
            catch (Exception x)
            {
                if (mInst.conf.message_proxy_type.equals("try_replay"))
                {
                    mInst.log("msg: Cannot open message log, ignore it");
                    break;
                }

                if (++ retry == 5)
                    throw new RuntimeException("Cannot open message log file");
                else
                {
                    mInst.log("msg: Cannnot open message log, wait for 3s");
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
                mInst.log("msg: Exception during read message logs");
                x.printStackTrace();
            }
            break;
        }

        super.prepare();
    }
    
    protected void msgArrived(Message msg) {
        if (!mInst.shouldManageMessage(msg))
        {
            msgForwardAndRecord(msg);
            return;
        }
        
        MessageInfo info = MessageInfo.obtain(msg);

        if (!mRefCount.containsKey(info) ||
            mRefCount.get(info) == 0)
        {
            mInst.log("msg: Replay message since no record: " + info);
            msgForwardAndRecord(msg);
            return;
        }

        mInst.log("msg: Buffering message: " + info);
        
        LinkedList<BufferedMessage> list = mBuffer.get(info);
        if (list == null)
        {
            list = new LinkedList<BufferedMessage>();
            mBuffer.put(info, list);
        }
        BufferedMessage bmsg = new BufferedMessage();
        bmsg.msg = msg;
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
                    mInst.log("msg: Replay message: " + MessageInfo.obtain(bmsg.msg));
                    int rc = mRefCount.get(headInfo) - 1;
                    if (rc > 0)
                        mRefCount.put(headInfo, rc);
                    else mRefCount.remove(headInfo);
                    mOrder.remove();
                    msgForwardAndRecord(bmsg.msg);
                    continue;
                }
            }
            break;
        }
    }
}

class ACIMessageQueueReplayer2 extends ACIMessageQueueRecorder
{

    private LinkedList<Message> mBlockedMessages;
    private LinkedList<Message> mBlockedACISyncRunnables;
    private LinkedList<HashMap<MessageInfo, Integer>> mOrder;
    private HashMap<MessageInfo, Integer> mCurrentSet;
    private int mFireCount;

    public ACIMessageQueueReplayer2(ACInstrumentation instrumentation, MessageQueue queue) {
        super(instrumentation, queue);
        
        mBlockedMessages = new LinkedList<Message>();
        mBlockedACISyncRunnables = new LinkedList<Message>();
        mOrder           = new LinkedList<HashMap<MessageInfo, Integer>>();
        mCurrentSet      = null;
        mFireCount       = 0;
    }

    public void prepare() {
        BufferedReader in;
        int retry = 0;

        while (true)
        {
            try
            {
                String path = mInst.conf.message_log_path.length() == 0 ?
                    mInst.getTargetContext().getApplicationInfo().dataDir + "/__instrument_msg_log" : 
                    mInst.conf.message_log_path;
                mInst.log("msg: read message log path: " + path);
                in = new BufferedReader(new FileReader(path));
            }
            catch (Exception x)
            {
                if (mInst.conf.message_proxy_type.equals("try_replay"))
                {
                    mInst.log("msg: Cannot open message log, ignore it");
                    break;
                }

                if (++ retry == 5)
                    throw new RuntimeException("Cannot open message log file");
                else
                {
                    mInst.log("msg: Cannnot open message log, wait for 3s");
                    try
                    { Thread.sleep(3000); }
                    catch (Exception xx) { }
                    continue;
                }
            }
            
            try {
                String line;
                HashMap<MessageInfo, Integer> currentSet = new HashMap<MessageInfo, Integer>();
                while ((line = in.readLine()) != null)
                {
                    MessageInfo info = MessageInfo.obtain(line);
                    if (info.cbDesc.equals("com.andchecker.ACISyncRunnable"))
                    {
                        mOrder.offer(currentSet);
                        currentSet = new HashMap<MessageInfo, Integer>();
                    }
                    else if (currentSet.containsKey(info))
                        currentSet.put(info, 1 + currentSet.get(info));
                    else currentSet.put(info, 1);
                }

                in.close();
            } catch (Exception x)
            {
                mInst.log("msg: Exception during read message logs");
                x.printStackTrace();
            }
            break;
        }

        super.prepare();
    }

    private void fireACISyncRunnable() {
        mInst.log("msg: fire ACISyncRunnable: " + (mFireCount ++));

        Message msg = mBlockedACISyncRunnables.remove();
        msgForwardAndRecord(msg);

        LinkedList<Message> bmsg = mBlockedMessages;
        mBlockedMessages = new LinkedList<Message>();

        mCurrentSet = null;
        mInst.log("msg: processing blocked messages");
        
        while (!bmsg.isEmpty())
        {
            msg = bmsg.remove();
            msgArrived(msg);
        }

        // bmsg = mBlockedMessages;
        // mBlockedMessages = new LinkedList<Message>();

        // while (!bmsg.isEmpty())
        // {
        //     msg = bmsg.remove();
        //     msgForwardAndRecord(msg);
        // }
    }

    protected void msgArrived(Message msg) {
        // if (!mInst.shouldManageMessage(msg))
        // {
        //     mInst.log("msg: msg not managed");
        //     msgForwardAndRecord(msg);
        //     return;
        // }
        
        if (mCurrentSet == null)
        {
            if (mOrder.isEmpty())
            {
                mInst.log("msg: replay message since record ended: " + MessageInfo.obtain(msg));
                msgForwardAndRecord(msg);
                return;
            }
            else mCurrentSet = mOrder.remove();
        }


        if (msg.getCallback() instanceof ACISyncRunnable)
        {
            mBlockedACISyncRunnables.addLast(msg);
            if (mCurrentSet.isEmpty())
                fireACISyncRunnable();
            else
                mInst.getHandler().sendEmptyMessageDelayed(1234, 5000);
            
            return;
        }
        else if (msg.getTarget() instanceof ACIHandler && msg.what == 1234)
        {
            mInst.log("msg: fire aci sync runnable for time out");
            msg.recycle();
            
            fireACISyncRunnable();
            return;
        }
        else
        {
            MessageInfo info = MessageInfo.obtain(msg);
            if (mCurrentSet.containsKey(info))
            {
                mInst.log("msg: forward msg " + info);
                msgForwardAndRecord(msg);
                int rc = mCurrentSet.get(info) - 1;
                if (rc > 0)
                    mCurrentSet.put(info, rc);
                else
                {
                    mCurrentSet.remove(info);
                    mInst.log("msg: all msg for info " + info + " sent this time");
                }
            }
            else mBlockedMessages.addLast(msg);
        }
    }
    
}

class ACIMessageQueueInstrumenter extends ACIMessageQueueHijacker
{
    private AsyncIONode<String> mIO;
    private Socket mSocket;
    private BufferedWriter mWriter;
    private BufferedReader mReader;
    private int mMyPid;
    private Object mSync;
    private HashMap<Integer, Message> mBuffer;
    private int mMsgCount;
    private boolean mBusy;
    
    public ACIMessageQueueInstrumenter(ACInstrumentation instrumentation, MessageQueue queue) {
        super(instrumentation, queue);
        mIO = new AsyncIONode<String>();
        mMyPid = android.os.Process.myPid();
        mSync = new Object();
        mMsgCount = 0;
        mBuffer = new HashMap<Integer, Message>();
        mBusy = false;
    }

    class WriteLineThread extends Thread {
        public void run() {
            while (true)
            {
                String msg;
                msg = mIO.receiveSync();
                try
                {
                    mWriter.write(msg);
                    mWriter.newLine();
                    mWriter.flush();
                }
                catch (Exception x)
                {
                    mInst.log("msg inst: exception in WriteThread");
                    x.printStackTrace();
                    mIO.sendSync("");
                    break;
                }
            }
        }
    }

    class ReadLineThread extends Thread {
        public void run() {
            try
            {
                ServerSocket ss = new ServerSocket(mInst.conf.msg_inst_srv_port);
                mSocket = ss.accept();
                synchronized (mSync)
                {
                    mWriter = new BufferedWriter(new OutputStreamWriter(mSocket.getOutputStream()));
                    mReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                }
                mInst.log("msg instrumenter got connection");
            }
            catch (Exception x)
            {
                return;
            }

            new WriteLineThread().start();
            
            while (true)
            {
                String msg;
                try
                {
                    msg = mReader.readLine();
                    if (msg.equals(""))
                    {
                        synchronized (mSync)
                        {
                            if (!mBusy)
                            {
                                mInst.getHandler().sendEmptyMessage(4444);
                                continue;
                            }
                        }
                    }
                }
                catch (Exception x)
                {
                    mInst.log("msg inst: exception in ReadThread");
                    x.printStackTrace();

                    mIO.sendSync("");
                    break;
                }
                mIO.sendSync(msg);
            }
        }
    }

    public void prepare() {
        new ReadLineThread().start();
    }
    
    protected void msgArrived(Message msg) {
        synchronized (mSync)
        {
            if (mReader != null)
                mBusy = true;
        }

        if (msg.getTarget() instanceof ACIHandler && msg.what == 4444)
        {
            msg.recycle();
            msg = null;
        }

        if (mBusy)
        {
            if (msg == null)
                mIO.putSync("@Interrupted");
            else mIO.putSync("@" + mMyPid + ": " + MessageInfo.obtain(msg));
            while (true)
            {
                String resp = mIO.getSync();
                if (resp.equals("n"))
                {
                    if (msg != null)
                    {
                        msgForward(msg);
                        msg = null;
                        mIO.putSync("OK");
                    }
                    else mIO.putSync("NothingHappened");
                    break;
                }
                else if (resp.equals("b"))
                {
                    if (msg != null)
                    {
                        int id = mMsgCount ++;
                        mBuffer.put(id, msg);
                        msg = null;
                        mIO.putSync("Saved " + id);
                    }
                    else mIO.putSync("NothingHappened");
                    break;
                }
                else if (resp.equals(""))
                {
                    mIO.putSync("@Hello");
                    continue;
                }
                
                try
                {
                    StringTokenizer tokens = new StringTokenizer(resp);
                    String head = tokens.nextToken();
                    
                    if (head.equals("i"))
                    {
                        int id = Integer.decode(tokens.nextToken());
                        if (mBuffer.containsKey(id))
                        {
                            msgForward(mBuffer.get(id));
                            mBuffer.remove(id);
                        }
                        mIO.putSync("@OK");
                    }
                    else
                    {
                        mIO.putSync("@NothingHappened");
                    }
                }
                catch (Exception x)
                {
                    mIO.putSync("@Exception");
                    break;
                }
            }

            synchronized (mSync)
            {
                mBusy = false;
            }
        }
        else
        {
            msgForward(msg);
        }
    }
}
