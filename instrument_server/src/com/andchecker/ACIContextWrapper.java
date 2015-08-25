package com.andchecker;

import android.content.Context;
import android.content.ContextWrapper;
import android.app.Activity;

class ACIContextWrapper extends ContextWrapper
{
    ACInstrumentation mInst;
    Activity mActivity;
    
    public ACIContextWrapper(Context base, ACInstrumentation inst, Activity activity) {
        super(base);
        mInst = inst;
        mActivity = activity;
    }
}
