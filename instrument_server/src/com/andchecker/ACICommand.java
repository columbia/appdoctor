package com.andchecker;

import java.util.StringTokenizer;
import java.util.HashMap;

public abstract class ACICommand implements Runnable
{
    protected ACInstrumentation mInst;
    protected String            mCookie;
    protected StringTokenizer   mTokens;
    protected StringBuilder     mResult;
    protected boolean           mError;

    public void init(ACInstrumentation instrumentation, String cookie, StringTokenizer tokens) {
        mInst = instrumentation;
        mCookie = cookie;
        mTokens = tokens;
        mResult = new StringBuilder();
        mError = false;
    }

    public boolean isFromUser() { return !mCookie.equals("-"); }
    abstract public String getName();
    abstract public boolean isUISync();
    abstract public boolean isInternal();
    abstract public void execute() throws Exception;
    public String getResultString() { return mResult.toString(); }
    final public void run() {
        try
        {
            mResult.append(mCookie);
            execute();
            mResult.append('\n');
        }
        catch (ACIException aci)
        {
            mError = true;
            mResult = new StringBuilder();
            mResult.append(mCookie);
            mResult.append(" ");
            mResult.append(mInst.formatException(aci));
            mResult.append("\n");
        }
        catch (Exception x)
        {
            x.printStackTrace();
            mError = true;
            mResult = new StringBuilder();
            mResult.append(mCookie);
            mResult.append(" ");
            mResult.append(mInst.formatException(x));
            mResult.append("\n");
        }
    }
    
    public boolean isError() { return mError; }

    // ============================================================
    
    private static HashMap<String, Class<?> > mCommands;
    static { mCommands = new HashMap<String, Class<?> >(); }

    final public static ACICommand buildCommand(ACInstrumentation instrumentation, String cmdLine) {
        synchronized (mCommands)
        {
            try
            {
                StringTokenizer tokens = new StringTokenizer(cmdLine);
                String cookie = tokens.nextToken();
                String name;
                try
                { name = tokens.nextToken(); }
                catch (Exception x) { name = ""; }
                Class<?> klass = mCommands.get(name);
                ACICommand result;
                if (klass == null)
                    result = new ACICmdUnknown();
                else result = (ACICommand)klass.newInstance();
                result.init(instrumentation, cookie, tokens);
                return result;
            }
            catch (Exception x)
            {
                x.printStackTrace();
                return null;
            }
        }
    }

    final private static boolean registerCommand(String name, Class<?> klass) {
        synchronized (mCommands)
        {
            if (name == null)
                return false;
            else if (klass == null)
                return false;
            else if (mCommands.containsKey(name))
                return false;
            mCommands.put(name, klass);
            return true;
        }
    }

    final public boolean registerSelf() {
        try
        {
            return registerCommand(getName(), getClass());
        } catch (Exception x)
        { return false; }
    }

    protected String[] decodeArray() throws java.io.IOException{
        String numS = mTokens.nextToken();
        int num = Integer.decode(numS);
        String[] values = new String[num];
        for (int i=0; i<num; i++) {
            String valueB64 = mTokens.nextToken();
            byte[] valueB = Base64.decode(valueB64);
            String value = "";
            try { value = new String(valueB, "UTF-8"); } catch (java.io.UnsupportedEncodingException e) {}
            values[i] = value;
        }
        return values;
    }
}

class ACICmdUnknown extends ACICommand
{
    public String getName() { return "[Unknown]"; }
    public boolean isUISync() { return false; }
    public boolean isInternal() { return true; }
    public void execute() throws Exception {
        mResult.append(" UnknownCommand");
    }
}
