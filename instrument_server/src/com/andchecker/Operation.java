package com.andchecker;

import java.util.Vector;

import com.andchecker.ACInstrumentation.AvailableOpInfo;
import com.andchecker.ACInstrumentation.IntentInfo;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsSeekBar;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.GridView;
import android.widget.Button;
import android.widget.Checkable;

public abstract class Operation {
    ACInstrumentation instrumenter;
    MonkeyRunnerGenerator monkeyRunnerGenerator;
    double prob;
    Operation(ACInstrumentation instrumenter, double prob) {
        this.instrumenter = instrumenter;
        this.monkeyRunnerGenerator = instrumenter.getMonkeyRunnerGenerator();
        this.prob = prob;
        future = null;
    }
    Vector<Operation> future;
    public abstract void doIt(boolean inMain, boolean fake) throws Exception;
    protected void writeMonkeyRunnerCommand() {}
    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
    String getViewDesc(View v) {
    	int loc[] = new int[2];
    	v.getLocationOnScreen(loc);
        String ret = String.format("%s,0x%08x,0x%08x,@%dx%d,%dx%d",
       		v.getClass().getSimpleName(), v.getId(), instrumenter.getViewId(v),
       		loc[0], loc[1], v.getWidth(), v.getHeight());
		if (v instanceof TextView) {
			ret += ",T:" + ((TextView)v).getText().toString();
		}
		return ret;
    }

    protected void doInFuture(Operation op) {
        if (future == null)
            future = new Vector<Operation>();
        future.add(op);
    }

    double getProb() { return prob; }

    void readHints(View target) {
        String[] hints = instrumenter.resolveHint(target);
        if (hints != null) {
            for (String hint: hints) {
                String[] parts = hint.split(":");
                if (parts.length == 2) {
                    Log.d("Operation", "check hint: " + parts[0] + " : " + parts[1]);
                    if (parts[0].equals("sleep")) {
                        SleepOp sleepOp = new SleepOp(instrumenter, 0, Integer.decode(parts[1]));
                        doInFuture(sleepOp);
                    }
                } else {
                    Log.w("Operation", String.format("invalid hint %s for 0x%08x", hint, target.getId()));
                }
            }
        }
    }
}

class ExceptionRunnable implements Runnable {
    interface Target {
        public void run() throws Exception;
    }
    Exception exc = null;
    Target target;
    ExceptionRunnable(Target t) {
        target = t;
    }
    @Override
    public void run() {
        try {
            target.run();
        } catch (Exception e) {
            exc = e;
        }
    }

    Exception getException() { return exc; }
}

abstract class MainOp extends Operation {
    MainOp(ACInstrumentation instrumenter, double prob) {
        super(instrumenter, prob);
    }
    public abstract void doInMain(boolean fake) throws Exception;
    public void doIt(boolean inMain, final boolean fake) throws Exception {
        if (!fake)
            writeMonkeyRunnerCommand();
        if (inMain) {
            doInMain(fake);
        } else {
            ExceptionRunnable er = new ExceptionRunnable(new ExceptionRunnable.Target() {
                @Override
                public void run() throws Exception {
                    doInMain(fake);
                }
            });
            instrumenter.runOnMainSync(er);
            if (er.getException() != null) {
                throw er.getException();
            }
        }

        if (future != null) {
            Log.d("MainOp", "doing future tasks");
            for (Operation op : future) {
                op.doIt(inMain, fake);
            }
        }
    }
}

abstract class InstOp extends Operation {
    InstOp(ACInstrumentation instrumenter, double prob) {
        super(instrumenter, prob);
    }
    public abstract void doInInst(boolean fake) throws Exception;
    public void doIt(boolean inMain, boolean fake) throws Exception {
        if (inMain) {
            throw new Exception("Cannot execute this operation, from the main thread");
        } else {
            if (!fake)
                writeMonkeyRunnerCommand();
            doInInst(fake);
        }

        if (future != null)
            for (Operation op : future) {
                op.doIt(inMain, fake);
            }
    }
}

class SleepOp extends Operation {
    int ms;
    SleepOp(ACInstrumentation instrumenter, double prob, int ms) {
        super(instrumenter, prob);
        this.ms = ms;
    }

    public void doIt(boolean inMain, boolean fake) throws Exception {
        if (fake) return;
        if (inMain)
            Log.w("SleepOp", "doing SleepOp in Main Thread!");
        writeMonkeyRunnerCommand();
        Thread.sleep(ms);
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.sleep(ms);
    }
}

class ClickOp extends MainOp {
    View target = null;
    
    ClickOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = target;
    }

    void click() {
        long dtime = SystemClock.uptimeMillis();
        long etime = SystemClock.uptimeMillis();
        target.onTouchEvent(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, 0, 0, 0));
        target.onTouchEvent(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, 0, 0, 0));
        String[] hints = instrumenter.resolveHint(target);
        readHints(target);
    }

    @Override
    public void doInMain(boolean fake) {
        if (fake) return;
        click();
    }
    
    @Override
    public String toString() {
        return String.format("Click(%s)", instrumenter.getViewDesc(target));
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN_AND_UP);
        }
    }
}

class LongClickOp extends MainOp {
    View target = null;
    boolean sendClick = true;
    
    LongClickOp(ACInstrumentation instrumentation, double prob, View target, boolean sendClick) {
        super(instrumentation, prob);
        this.target = target;
        this.sendClick = sendClick;
    }

    void longClick() {
        try {
            if (sendClick) {
                long dtime = SystemClock.uptimeMillis();
                long etime = SystemClock.uptimeMillis();
                target.onTouchEvent(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, 0, 0, 0));
            }
            target.performLongClick();
            if (sendClick) {
                long dtime = SystemClock.uptimeMillis();
                long etime = SystemClock.uptimeMillis();
                target.onTouchEvent(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, 0, 0, 0));
            }
        } catch (java.lang.NullPointerException e) {
            if (target.getParent() == null) {
                Log.w("LongClickOp", "ignored NPE because getParent() is null");
            } else {
                throw e;
            }
        }
    }

    @Override
    public void doInMain(boolean fake) {
        if (fake) return;
        longClick();
    }
    
    @Override
    public String toString() {
        return String.format("LongClick(%s,%b)", instrumenter.getViewDesc(target), this.sendClick);
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN);
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.UP);
        }
    }
}

class GetLocationRunnable implements Runnable {
    int pos_x, pos_y;
    int width, height;
    int paddingLeft, paddingRight;
    boolean visible;
    View target;
    GetLocationRunnable(View target) {
        this.target = target;
    }
    @Override
    public void run() {
        Rect r = new Rect();
        visible = target.getGlobalVisibleRect(r);
        if (!visible)
            return;
        int []location = new int[2];
        target.getLocationOnScreen(location);
        pos_x = location[0] + 1;
        pos_y = location[1] + 1;
        width = target.getWidth();
        height = target.getHeight();
        paddingLeft = target.getPaddingLeft();
        paddingRight = target.getPaddingRight();
    }
}

class PointerClickOp extends InstOp {
    View target = null;
    
    PointerClickOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = target;
    }

    void click() {
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (!glr.visible) return;

        long dtime = SystemClock.uptimeMillis();
        long etime = SystemClock.uptimeMillis();
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, glr.pos_x, glr.pos_y, 0));
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, glr.pos_x, glr.pos_y, 0));
        readHints(target);
    }

    @Override
    public void doInInst(boolean fake) {
        if (fake) return;
        click();
    }
    
    @Override
    public String toString() {
        return String.format("PointerClick(%s)", instrumenter.getViewDesc(target));
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN_AND_UP);
        }
    }
}

class PointerLongClickOp extends InstOp {
    View target = null;
    
    PointerLongClickOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = target;
    }

    void longClick() {
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (!glr.visible) return;

        long dtime = SystemClock.uptimeMillis();
        long etime = SystemClock.uptimeMillis();
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, glr.pos_x, glr.pos_y, 0));
        try {
            Thread.sleep(3000);
        } catch (Exception e) { }
        etime = SystemClock.uptimeMillis();
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, glr.pos_x, glr.pos_y, 0));
    }

    @Override
    public void doInInst(boolean fake) {
        if (fake) return;
        longClick();
    }
    
    @Override
    public String toString() {
        return String.format("PointerLongClick(%s)", instrumenter.getViewDesc(target));
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN);
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.UP);
        }
    }
}

class KeyPressOp extends InstOp {
    int keyCode = 0;
    KeyPressOp(ACInstrumentation instrumentation, double prob, int keyCode) {
        super(instrumentation, prob);
        this.keyCode = keyCode;
    }

    @Override
    public void doInInst(boolean fake) {
        if (fake) return;
        instrumenter.sendKeySync(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        instrumenter.sendKeySync(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }
    
    @Override
    public String toString() {
        return String.format("KeyPress(%s)", getKeyCodeString());
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.press(getKeyCodeString(), monkeyRunnerGenerator.DOWN_AND_UP);
    }

    private String getKeyCodeString() {
        if (android.os.Build.VERSION.SDK_INT >= 14)
            return KeyEvent.keyCodeToString(keyCode);
        else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_MENU:
                    return "KEYCODE_MENU";
                case KeyEvent.KEYCODE_BACK:
                    return "KEYCODE_BACK";
                case KeyEvent.KEYCODE_SEARCH:
                    return "KEYCODE_SEARCH";
                default:
                    return String.valueOf(keyCode);
            }
        }
    }
}

class RealMoveSeekBarOp extends InstOp {
    AbsSeekBar target = null;
    int targetVal = -1;
    int touchX = -1, touchY;
    RealMoveSeekBarOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = (AbsSeekBar)target;
    }
    RealMoveSeekBarOp(ACInstrumentation instrumentation, double prob, View target, int targetVal) {
        this(instrumentation, prob, target);
        this.targetVal = targetVal;
    }
    @Override
    public void doInInst(boolean fake) {
        prepareTargetVal();
        if (fake) return;
        long dtime = SystemClock.uptimeMillis();
        long etime = SystemClock.uptimeMillis();
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, touchX, touchY, 0));
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, touchX, touchY, 0));
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        prepareTargetVal();
        if (touchX != -1) {
            monkeyRunnerGenerator.touch(touchX, touchY, monkeyRunnerGenerator.DOWN_AND_UP);
        } else {
            monkeyRunnerGenerator.prompt(String.format("Please move the seek bar to position %d/%d, and then press Enter to continue...", targetVal, target.getMax()));
        }
    }

    private void prepareTargetVal() {
        int max = target.getMax();
        if (targetVal == -1 || targetVal >= max || targetVal < 0) {
            targetVal = ACInstrumentation.getSelf().random.nextInt(max);
        }
        if (touchX == -1) {
            GetLocationRunnable glr = new GetLocationRunnable(target);
            instrumenter.runOnMainSync(glr);
            if (glr.visible) {
                touchX = glr.pos_x + glr.paddingLeft + (glr.width - glr.paddingLeft - glr.paddingRight) * targetVal / target.getMax();
                touchY = glr.pos_y;
            }
        }
    }
}

class MoveSeekBarOp extends MainOp {
    AbsSeekBar target = null;
    int targetVal = -1;
    int touchX = -1, touchY;
    MoveSeekBarOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = (AbsSeekBar)target;
    }
    MoveSeekBarOp(ACInstrumentation instrumentation, double prob, View target, int targetVal) {
        this(instrumentation, prob, target);
        this.targetVal = targetVal;
    }
    @Override
    public void doInMain(boolean fake) {
        prepareTargetVal();
        if (fake) return;
        if (target instanceof android.widget.SeekBar)
            try {
                instrumenter.seekBarMethodStartTracking.invoke(target);
            } catch (IllegalAccessException e) {}
              catch (java.lang.reflect.InvocationTargetException e) {}

        target.setProgress(targetVal);

        if (target instanceof android.widget.SeekBar)
            try {
                instrumenter.seekBarMethodStopTracking.invoke(target);
            } catch (IllegalAccessException e) {}
              catch (java.lang.reflect.InvocationTargetException e) {}

    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        prepareTargetVal();
        if (touchX != -1) {
            monkeyRunnerGenerator.touch(touchX, touchY, monkeyRunnerGenerator.DOWN_AND_UP);
        } else {
            monkeyRunnerGenerator.prompt(String.format("Please move the seek bar to position %d/%d, and then press Enter to continue...", targetVal, target.getMax()));
        }
    }

    private void prepareTargetVal() {
        int max = target.getMax();
        if (targetVal == -1 || targetVal >= max || targetVal < 0) {
            targetVal = ACInstrumentation.getSelf().random.nextInt(max);
        }
        if (touchX == -1) {
            GetLocationRunnable glr = new GetLocationRunnable(target);
            instrumenter.runOnMainSync(glr);
            if (glr.visible) {
                touchX = glr.pos_x + glr.paddingLeft + (glr.width - glr.paddingLeft - glr.paddingRight) * targetVal / target.getMax();
                touchY = glr.pos_y;
            }
        }
    }
}

class RealSetNumberPickerOp extends InstOp {
    NumberPicker target = null;
    int targetVal = -1;
    boolean hasTarget = false;
    RealSetNumberPickerOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = (NumberPicker)target;
        this.hasTarget = false;
    }
    RealSetNumberPickerOp(ACInstrumentation instrumentation, double prob, View target, int targetVal) {
        this(instrumentation, prob, target);
        this.targetVal = targetVal;
        this.hasTarget = true;
    }
    @Override
    public void doInInst(boolean fake) {
        int oldVal = target.getValue();
        prepareTargetVal();
        if (fake) return;
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        long dtime = SystemClock.uptimeMillis();
        long etime = SystemClock.uptimeMillis();
        int step = (android.os.Build.VERSION.SDK_INT <= 15) ? 1 : -1;
        for (int i = oldVal; (i - targetVal) * step < 0; i += step) {
            instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, glr.pos_x, glr.pos_y, 0));
            instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, glr.pos_x, glr.pos_y, 0));
            instrumenter.waitForIdleSync();
        }
        for (int i = oldVal; (i - targetVal) * step > 0; i -= step) {
            instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, glr.pos_x, glr.pos_y + glr.height - 2, 0));
            instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, glr.pos_x, glr.pos_y + glr.height - 2, 0));
            instrumenter.waitForIdleSync();
        }
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        int oldVal = target.getValue();
        prepareTargetVal();
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            int step = (android.os.Build.VERSION.SDK_INT <= 15) ? 1 : -1;
            for (int i = oldVal; (i - targetVal) * step < 0; i += step) {
                monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN_AND_UP, 1);
            }
            for (int i = oldVal; (i - targetVal) * step > 0; i -= step) {
                monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y + glr.height - 2, monkeyRunnerGenerator.DOWN_AND_UP, 1);
            }
        } else {
            monkeyRunnerGenerator.prompt(String.format("Please set the number picker to %d, and then press Enter to continue...", targetVal));
        }
    }

    private void prepareTargetVal() {
        int max = target.getMaxValue();
        int min = target.getMinValue();
        if (!hasTarget || targetVal < min || targetVal > max) {
            targetVal = ACInstrumentation.getSelf().random.nextInt(max - min + 1) + min;
            hasTarget = true;
        }
    }
}


class SetNumberPickerOp extends MainOp {
    NumberPicker target = null;
    int targetVal = -1;
    boolean hasTarget = false;
    SetNumberPickerOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = (NumberPicker)target;
        this.hasTarget = false;
    }
    SetNumberPickerOp(ACInstrumentation instrumentation, double prob, View target, int targetVal) {
        this(instrumentation, prob, target);
        this.targetVal = targetVal;
        this.hasTarget = true;
    }
    @Override
    public void doInMain(boolean fake) {
        prepareTargetVal();
        if (fake) return;
        target.setValue(targetVal);
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        int oldVal = target.getValue();
        prepareTargetVal();
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            int step = (android.os.Build.VERSION.SDK_INT <= 15) ? 1 : -1;
            for (int i = oldVal; (i - targetVal) * step < 0; i += step) {
                monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN_AND_UP, 1);
            }
            for (int i = oldVal; (i - targetVal) * step > 0; i -= step) {
                monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y + glr.height - 2, monkeyRunnerGenerator.DOWN_AND_UP, 1);
            }
        } else {
            monkeyRunnerGenerator.prompt(String.format("Please set the number picker to %d, and then press Enter to continue...", targetVal));
        }
    }

    private void prepareTargetVal() {
        int max = target.getMaxValue();
        int min = target.getMinValue();
        if (!hasTarget || targetVal < min || targetVal > max) {
            targetVal = ACInstrumentation.getSelf().random.nextInt(max - min + 1) + min;
            hasTarget = true;
        }
    }
}



abstract class EditTextContent {
    public abstract String getContent();
}

class ConstEditTextContent extends EditTextContent {
    String content;
    ConstEditTextContent(String content) {
        this.content = content;
    }

    @Override
    public String getContent() { return content; }
}

class ConstMultiEditTextContent extends EditTextContent {
    String[] contents;
    ConstMultiEditTextContent(String[] contents) {
        this.contents = contents;
    }

    @Override
    public String getContent() {
        return contents[ACInstrumentation.getSelf().random.nextInt(contents.length)];
    }
}

class NumberContent extends EditTextContent {
    String[] numbers = {"123", "123456789", "12345678901234567890"};
    boolean decimal, signed;
    NumberContent(boolean decimal, boolean signed) {
        this.decimal = decimal;
        this.signed = signed;
    }
    @Override
    public String getContent() {
        int result = ACInstrumentation.getSelf().random.nextInt(numbers.length);
        String num = numbers[result];
        if (decimal) {
            int first = ACInstrumentation.getSelf().random.nextInt(num.length());
            if (first != 0) {
                num = num.substring(0, first) + "." + num.substring(first);
            }
        }
        if (signed) {
            if (ACInstrumentation.getSelf().random.nextInt(2) == 1) {
                num = "-" + num;
            }
        }
        return num;
    }
}

class EmailContent extends EditTextContent {
    String[] emails = {"columbiatestmail@gmail.com", "doesnotexist@doesnotexist.com", "thisshouldnotexist@google.com"};
    @Override
    public String getContent() {
        int result = ACInstrumentation.getSelf().random.nextInt(emails.length);
        return emails[result];
    }
}

class UrlEditTextContent extends EditTextContent {
    String[] validUrls = {"http://www.google.com", "http://www.facebook.com",
        "https://www.google.com/search?ie=UTF-8&oe=UTF-8&q="+
            "android+emulator+no+display#hl=en&tbo=d&sclient=psy-ab&"+
            "q=android+emulator+disable+display&oq=android+emulator+"+
            "disable+display&gs_l=serp.3..33i29l4.10975.12810.0.13018"+
            ".16.16.0.0.0.0.458.2095.6j5j1j1j1.14.0.les%3Bpchatac..0.0"+
            "...1.1.3S-jQXnELCQ&pbx=1&bav=on.2,or.r_gc.r_pw.r_cp.r_qf.&"+
            "bvm=bv.1355534169,d.dmQ&fp=89c8db76974e7f67&bpcl=40096503&"+
            "biw=1918&bih=928",
    };
    String[] invalidUrls = {"http://www.thisdomaindoesnotexistreallyRCS.com",
        "http://www.veryveryveryveryveryveryveryveryveryveryvery" +
              "veryveryveryveryveryveryveryveryveryveryveryverylong.com",
        "http://www.veryveryveryveryveryveryveryveryveryveryvery" +
            "veryveryveryveryveryveryveryveryveryveryveryverylong.com/very"+
            "verylongpath",
    };

    @Override
    public String getContent() {
        int result = ACInstrumentation.getSelf().random.nextInt(validUrls.length + invalidUrls.length);
        if (result < validUrls.length)
            return validUrls[result];
        else
            return invalidUrls[result - validUrls.length];
    }
}

class ValidUrlEditTextContent extends UrlEditTextContent {
    @Override
    public String getContent() {
        int result = ACInstrumentation.getSelf().random.nextInt(validUrls.length);
        return validUrls[result];
    }
}

class EnterTextOp extends InstOp {
    EditText target = null;
    String content = null;
    EnterTextOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = (EditText)target;
    }
    EnterTextOp(ACInstrumentation instrumentation, double prob, View target, String content) {
        this(instrumentation, prob, target);
        this.content = content;
    }
    @Override
    public void doInInst(boolean fake) throws Exception {
        prepareContent();
        if (fake) return;
        PointerClickOp pco = new PointerClickOp(instrumenter, 0, target);
        pco.doIt(false, fake);
        instrumenter.waitForIdleSync();
        CharSequence curContent = target.getText();
        int len = curContent.length();
        for (int i=0; i<len; i++)
            new KeyPressOp(instrumenter, 0, KeyEvent.KEYCODE_DEL).doIt(false, fake);
        curContent = target.getText();
        len = curContent.length();
        for (int i=0; i<len; i++) {
            new KeyPressOp(instrumenter, 0, KeyEvent.KEYCODE_DPAD_RIGHT).doIt(false, fake);
            new KeyPressOp(instrumenter, 0, KeyEvent.KEYCODE_DEL).doIt(false, fake);
        }
        instrumenter.waitForIdleSync();

        monkeyRunnerGenerator.type(content);
        instrumenter.sendStringSync(content);
        instrumenter.waitForIdleSync();
        // dismiss soft keyboard: not required now
        //KeyPressOp kpo = new KeyPressOp(instrumenter, KeyEvent.KEYCODE_BACK);
        //kpo.doIt(false);
    }
    @Override
    public String toString() {
        if (content == null) {
            return String.format("EnterText(%s)", instrumenter.getViewDesc(target));
        } else {
            return String.format("EnterText(%s,%s)", instrumenter.getViewDesc(target), content);
        }
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        // Sadly, this method needs to be coupled with doInInst().
    }

    private void prepareContent() {
        if (content == null) {
            EditTextContent provider =
                    instrumenter.resolveEditContent(target);
            if (provider != null) {
                content = provider.getContent();
                Log.d("EnterTextOp", "got content '" + content + "' from db for " + getViewDesc(target));
            } else {
                int inputType = target.getInputType();
                if ((inputType & InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0) {
                    content = new EmailContent().getContent();
                } else if ((inputType & InputType.TYPE_TEXT_VARIATION_URI) != 0) {
                    content = new UrlEditTextContent().getContent();
                } else if ((inputType & InputType.TYPE_CLASS_NUMBER) != 0) {
                    content = new NumberContent((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0,
                            (inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0).getContent();
                } else {
                    String[] strs = {"test", "Henry", "hello", "password_test", "1234", "10025", "1"};
                    int result = ACInstrumentation.getSelf().random.nextInt(strs.length);
                    content = strs[result];
                }
            }
        }
    }
}


class SetEditTextOp extends MainOp {
    EditText target = null;
    String content = null;
    SetEditTextOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target = (EditText)target;
    }
    SetEditTextOp(ACInstrumentation instrumentation, double prob, View target, String content) {
        this(instrumentation, prob, target);
        this.content = content;
    }
    @Override
    public void doInMain(boolean fake) {
        prepareContent();
        if (fake) return;
        target.requestFocus();
        target.setText(content);
    }
    @Override
    public String toString() {
        if (content == null) {
            return String.format("SetEditText(%s)", instrumenter.getViewDesc(target));
        } else {
            String enc = "";
            try {
                enc = java.net.URLEncoder.encode(content, "UTF-8");
            } catch (Exception e) {}
            return String.format("SetEditText(%s,%s)", instrumenter.getViewDesc(target), enc);
        }
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        prepareContent();
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (glr.visible) {
            // this won't clear the current text already on the EditText.
            monkeyRunnerGenerator.touch(glr.pos_x, glr.pos_y, monkeyRunnerGenerator.DOWN_AND_UP);
            monkeyRunnerGenerator.type(content);
            // dismiss soft keyboard: not required now
            // monkeyRunnerGenerator.press("KEYCODE_BACK", monkeyRunnerGenerator.DOWN_AND_UP);
        }
    }

    private void prepareContent() {
        if (content == null) {
            EditTextContent provider =
                    instrumenter.resolveEditContent(target);
            if (provider != null) {
                content = provider.getContent();
                Log.d("SetEditTextOp", "got content '" + content + "' from db for " + getViewDesc(target));
            } else {
                int inputType = target.getInputType();
                if ((inputType & InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) != 0) {
                    content = new EmailContent().getContent();
                } else if ((inputType & InputType.TYPE_TEXT_VARIATION_URI) != 0) {
                    content = new UrlEditTextContent().getContent();
                } else if ((inputType & InputType.TYPE_CLASS_NUMBER) != 0) {
                    content = new NumberContent((inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0,
                            (inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0).getContent();
                } else {
                    String[] strs = {"test", "Henry", "hello", "password_test", "1234", "10025", "1"};
                    int result = ACInstrumentation.getSelf().random.nextInt(strs.length);
                    content = strs[result];
                }
            }
        }
    }
}

class ListSelectOp extends MainOp {
    ListView target = null;
    int item = -1;
    ListSelectOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target= (ListView)target;
        this.item = -1;
    }

    ListSelectOp(ACInstrumentation instrumentation, double prob, View target, int item) {
        this(instrumentation, prob, target);
        this.item = item;
    }
    @Override
    public void doInMain(boolean fake) {
        if (item < 0 || item >= target.getCount()) {
            if (target.getCount() == 0) return;
            item = ACInstrumentation.getSelf().random.nextInt(target.getCount());
        }
        if (fake) return;
        target.setSelection(item);
    }
    @Override
    public String toString() {
        if (item >= 0 && item < target.getCount())
            return String.format("ListSelect(%s,%d)", instrumenter.getViewDesc(target), item);
        else
            return String.format("ListSelect(%s)", instrumenter.getViewDesc(target));
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.prompt(String.format("Please select item #%d, and then press Enter to continue...", item));
    }
}

class ScrollOneItemRunnable implements Runnable {
    ScrollOneItemRunnable(ListView target, int item) {
        this.target = target;
        this.item = item;
    }

    public int getDistance() {
        return distance;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void run() {
        View firstView = target.getChildAt(0);
        if (firstView == null) {
            return;
        }
        int firstPos = target.getFirstVisiblePosition();
        int dividerHeight = target.getDividerHeight() + 1;
        if (item == firstPos) {
            distance = firstView.getTop();
            target.smoothScrollBy(distance, 400);
            finished = true;
        } else if (item < firstPos) {
            distance = firstView.getTop() - dividerHeight;
            target.smoothScrollBy(distance, 400);
        } else {
            if (target.getLastVisiblePosition() == target.getCount() - 1) {
                View lastView = target.getChildAt(target.getChildCount() - 1);
                if (lastView != null && lastView.getBottom() <= target.getHeight()) {
                    finished = true;
                    return;
                }
            }
            distance = firstView.getBottom() + dividerHeight;
            target.smoothScrollBy(distance, 400);
        }
    }

    private ListView target;
    private int item;
    private int distance;
    private boolean finished = false;
}

class ListScrollOp extends InstOp {
    ListView target = null;
    int item = -1;
    ListScrollOp(ACInstrumentation instrumentation, double prob, View target) {
        super(instrumentation, prob);
        this.target= (ListView)target;
        this.item = -1;
    }

    ListScrollOp(ACInstrumentation instrumentation, double prob, View target, int item) {
        this(instrumentation, prob, target);
        this.item = item;
    }
    @Override
    public void doInInst(boolean fake) {
        if (item == -1) {
            item = ACInstrumentation.getSelf().random.nextInt(target.getCount());
        }
        if (fake) return;
        GetLocationRunnable glr = new GetLocationRunnable(target);
        instrumenter.runOnMainSync(glr);
        if (!glr.visible) {
            monkeyRunnerGenerator.prompt(String.format("Please select item #%d, and then press Enter to continue...", item));
            return;
        }
        ScrollOneItemRunnable soir;
        int distance = 0;
        do {
            soir = new ScrollOneItemRunnable(target, item);
            instrumenter.runOnMainSync(soir);
            instrumenter.waitForIdleSync();
            distance += soir.getDistance();
        } while (!soir.isFinished());
        monkeyRunnerGenerator.drag(glr.pos_x, glr.pos_y, glr.width, glr.height, 0, distance);
    }
    @Override
    public String toString() {
        return String.format("ListScroll(%s,%d)", instrumenter.getViewDesc(target), item);
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        // Sadly, this method needs to be coupled with doInMain().
    }
}

class ForceRotateOp extends InstOp {
    ForceRotateOp(ACInstrumentation inst, double prob) {
        super(inst, prob);
    }

    @Override
    public void doInInst(boolean fake) throws Exception {
        if (fake) return;
        instrumenter.doRotate(true);
    }

    @Override
    public String toString() {
        return "ForceRotate()";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.prompt("Please rotate your device and rotate it back, and then press Enter to continue...");
    }
}

class RotateOp extends InstOp {
    RotateOp(ACInstrumentation inst, double prob) {
        super(inst, prob);
    }

    @Override
    public void doInInst(boolean fake) throws Exception {
        if (fake) return;
        instrumenter.doRotate(false);
    }

    @Override
    public String toString() {
        return "Rotate()";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.prompt("Please rotate your device and rotate it back, and then press Enter to continue...");
    }
}

class RollOp extends MainOp {
    RollOp(ACInstrumentation inst, double prob) {
        super(inst, prob);
    }

    @Override
    public void doInMain(boolean fake) throws Exception {
        if (fake) return;
        instrumenter.doRoll();
    }

    @Override
    public String toString() {
        return "Roll()";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.prompt("Please rotate your device once, and then press Enter to continue...");
    }
}

class PauseAndResumeOp extends MainOp {
    PauseAndResumeOp(ACInstrumentation instrumentation, double prob) {
        super(instrumentation, prob);
    }

    @Override
    public void doInMain(boolean fake) throws Exception {
        if (fake) return;
        if (!instrumenter.isFaithful()) {
            Activity act = instrumenter.getCurrentActivity();
            if (act != null)
            {
                LifeCycleChecker lcc = null;
                if (instrumenter.isLifeCycleCheckerEnabled())
                {
                    lcc = new LifeCycleChecker(instrumenter);
                    lcc.before();
                }
                instrumenter.callActivityOnPause(act);
                instrumenter.callActivityOnResume(act);
                if (lcc != null)
                    lcc.after();
            }
        }
    }

    @Override
    public String toString() {
        return "PauseAndResume()";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.startActivity("com.android.settings", ".Settings", 1);
        monkeyRunnerGenerator.press("KEYCODE_BACK", monkeyRunnerGenerator.DOWN_AND_UP);
    }
}

class StopAndRestartOp extends MainOp {
    StopAndRestartOp(ACInstrumentation inst, double prob) {
        super(inst, prob);
    }

    @Override
    public void doInMain(boolean fake) throws Exception {
        if (fake) return;
        if (!instrumenter.isFaithful()) {
            Activity act = instrumenter.getCurrentActivity();
            if (act != null)
            {
                LifeCycleChecker lcc = null;
                if (instrumenter.isLifeCycleCheckerEnabled())
                {
                    lcc = new LifeCycleChecker(instrumenter);
                    lcc.before();
                }
                instrumenter.callActivityOnUserLeaving(act);
                instrumenter.callActivityOnPause(act);
                Bundle bundle = new Bundle();
                instrumenter.callActivityOnSaveInstanceState(act, bundle);
                instrumenter.callActivityOnStop(act);
                instrumenter.callActivityOnRestart(act);
                instrumenter.callActivityOnStart(act);
                instrumenter.activityMethodPerformResume.invoke(act);
//                instrumenter.callActivityOnResume(act);
                if (lcc != null)
                    lcc.after();
            }
        }
    }

    @Override
    public String toString() {
        return "StopAndRestart()";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.startActivity("com.android.settings", ".Settings");
        monkeyRunnerGenerator.press("KEYCODE_BACK", monkeyRunnerGenerator.DOWN_AND_UP);
    }
}

class RelaunchOp extends InstOp {

    private Activity act;
    private LifeCycleChecker lcc = null;
    private Exception ex = null;
    
    RelaunchOp(ACInstrumentation instrumentation, double prob) {
        super(instrumentation, prob);
    }

    @Override
    public void doInInst(boolean fake) throws Exception {
        if (fake) return;
        act = instrumenter.getCurrentActivity();
        
        if (act != null)
        {
            instrumenter.runOnMainSync(new Runnable() {
                    public void run() {
                        try
                        {
                            if (instrumenter.isCheckerEnabled())
                            {
                                // orientation checkers
                                for (Checker checker : Checker.orientationCheckers.values())
                                    checker.reset();
                                View[] dvs = instrumenter.getWindowDecorViews();
                                if (dvs != null)
                                    for (View v : dvs) {
                                        Checker.checkEveryView(v, Checker.orientationCheckers.values());
                                    }
                            }
                            
                            if (instrumenter.isLifeCycleCheckerEnabled())
                            {
                                lcc = new LifeCycleChecker(instrumenter);
                                lcc.before();
                            }

                            if (!instrumenter.relaunchActivity(act)) throw new ACIException(ACIException.LEVEL_INTERNAL, "RelaunchFailed", "Cannot relaunch current activity");
                        }
                        catch (Exception x)
                        { ex = x; }
                    }
                });
            

            if (ex != null)
                throw ex;

            instrumenter.waitForIdleSync();
            instrumenter.runOnMainSync(new Runnable() {
                    public void run() {
                        try
                        {
                            if (instrumenter.isCheckerEnabled())
                            {
                                // orientation checkers
                                for (Checker checker : Checker.orientationCheckers.values())
                                    checker.rotate();
                                View[] dvs = instrumenter.getWindowDecorViews();
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
                });

            if (ex != null)
                throw ex;
        
        }
    }

    @Override
    public String toString() {
        return "Relaunch()";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
        monkeyRunnerGenerator.prompt("Please rotate your device and rotate it back, and then press Enter to continue...");
    }
}

abstract class ResultRunnable implements Runnable {
    protected abstract void work() throws Exception;
    Object result;
    Exception exc;
    @Override
    public void run() {
        try {
            work();
        } catch (Exception e) {
            exc = e;
            result = null;
        }
    }
    protected void setResult(Object o) { result = o; }
    public Object getResult() { return result; }
    public Exception getException() { return exc; }
}

class SlideOp extends InstOp {
    double start;
    double len;
    double pos;
    int steps;
    View target;
    Direction dir;
    public enum Direction {
        LEFT("LEFT"), RIGHT("RIGHT"), UP("UP"), DOWN("DOWN");
        private String name;
        Direction(String name) {
            this.name = name;
        }
        String getName() { return name; }
    }

    SlideOp(ACInstrumentation instrumentation, double prob, View target, Direction dir) {
        super(instrumentation, prob);
        this.dir = dir;
        this.target = target;
        start = 0.1;
        len = 0.8;
        pos = 0.5;
        steps = 10;
    }

    @Override
    public void doInInst(boolean fake) throws Exception {
        if (fake) return;
        View v = target;
        GetLocationRunnable glr = new GetLocationRunnable(v);
        instrumenter.runOnMainSync(glr);

        int startX, endX, startY, endY;

        switch (dir) {
            case LEFT:
            case RIGHT:
                startY = endY = glr.pos_y + (int)(glr.height * pos);
                break;
            case DOWN:
                startY = glr.pos_y + (int)(glr.height * start);
                endY = glr.pos_y + (int)(glr.height * (start + len));
                break;
            case UP:
                startY = glr.pos_y + (int)(glr.height * (1 - start));
                endY = glr.pos_y + (int)(glr.height * (1 - start - len));
                break;
            default:
                throw new Exception("impossible!");
        }
        switch (dir) {
            case UP:
            case DOWN:
                startX = endX = (int)(glr.pos_x + glr.width * pos);
                break;
            case RIGHT:
                startX = glr.pos_x + (int)(glr.width * start);
                endX = glr.pos_x + (int)(glr.width * (start + len));
                break;
            case LEFT:
                startX = glr.pos_x + (int)(glr.width * (1 - start));
                endX = glr.pos_x + (int)(glr.width * (1 - start - len));
                break;
            default:
                throw new Exception("impossible!");
        }

        Log.d("SlideOp", String.format("start: (%d,%d) end: (%d,%d)", startX, startY, endX, endY));
        long dtime = SystemClock.uptimeMillis();
        long etime = SystemClock.uptimeMillis();
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_DOWN, startX, startY, 0));
        for (int i=0; i<steps; i++) {
            instrumenter.sendPointerSync(
                    MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_MOVE,
                        startX * (steps - 1 -i) / (steps - 1) + endX * i / (steps - 1),
                        startY * (steps - 1 -i) / (steps - 1) + endY * i / (steps - 1), 0));
        }
        instrumenter.sendPointerSync(MotionEvent.obtain(dtime, etime, MotionEvent.ACTION_UP, endX, endY, 0));
    }

    @Override
    public String toString() {
        return "Slide(" + instrumenter.getViewDesc(target) + "," + dir.getName() + ")";
    }

    @Override
    protected void writeMonkeyRunnerCommand() {
    }
}

class BroadcastOp extends InstOp {
	IntentInfo info;
	BroadcastOp(ACInstrumentation inst, double prob, IntentInfo info) {
		super(inst, prob);
		this.info = info;
	}
	
	@Override
	public void doInInst(boolean fake) {
		if (fake) return;
		// In most cases, we don't have the permission to broadcast
		/*
		Intent intent = info.prepareIntent();
		instrumenter.getContext().sendBroadcast(intent);
		*/
		
		String cmdline = info.prepareCmdline();
		instrumenter.addResponse(
				"__Event__ TriggerBroadcast " + info.action + " " 
		+ Base64.encodeBytes(cmdline.getBytes()) + "\n", true);
		instrumenter.broadcastCount--;
		for (AvailableOpInfo opInfo : instrumenter.broadcastAvailable) {
			if (opInfo.intentInfo == info) {
				opInfo.count--;
				break;
			}
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Broadcast(");
		sb.append(info.getDesc());
		sb.append(")");
		return sb.toString();
	}
}

class IntentOp extends InstOp {
	IntentInfo info;
	IntentOp(ACInstrumentation inst, double prob, IntentInfo info) {
		super(inst, prob);
		this.info = info;
	}
	
	@Override
	public void doInInst(boolean fake) {
		if (fake) return;
		Intent intent = info.prepareIntent();
		instrumenter.getCurrentActivity().startActivity(intent);
		instrumenter.intentCount--;
		for (AvailableOpInfo opInfo : instrumenter.intentAvailable) {
			if (opInfo.intentInfo == info) {
				opInfo.count--;
				break;
			}
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Intent(");
		sb.append(info.getDesc());
		sb.append(")");
		return sb.toString();
	}
}

class CollectOperationsRunnable implements Runnable {
    Vector<Operation> operations = null;
    Vector<Operation> faithfulOperations = null;
    ACInstrumentation inst;
    CollectOperationsRunnable(ACInstrumentation inst) {
        this.inst = inst;
    }

    class ViewInfo {
        boolean inList;
        ViewInfo(boolean inList) {
            this.inList = inList;
        }

        boolean inListView() { return inList; }
    }

    boolean isNotActivityView(View v) {
        try {
            int mFeatureId = inst.decorViewFeature.getInt(v);
            if (mFeatureId == -1)
                return false;
            else
                return true;
        } catch (Exception e) {
            Log.e("isNotActivityView", "got exception!");
            e.printStackTrace();
            return false;
        }
    }

    public boolean visibleFromRoot(View v) {
    	int loc[] = new int[2];
    	v.getLocationOnScreen(loc);
        return visibleFromRoot(v, new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight()));
    }

    public boolean visibleFromRoot(View v, Rect region) {
        if (v.getVisibility() == View.VISIBLE) {
            if (v.getParent() == null || !(v.getParent() instanceof View)) {
                int loc[] = new int[2];
                v.getLocationOnScreen(loc);
                Rect r = new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
                Log.d("visibleFromRoot", "region: " + region.toString() + " root: " + r.toString() + " interect: " + r.intersect(region));
                return r.intersect(region);
            } else {
                return visibleFromRoot((View)v.getParent(), region);
            }
        } else {
            return false;
        }
    }
    
    void addBroadcastOps() {
    	for (Operation op : inst.getBroadcastOps()) {
    		addOp(op);
    	}
    }
    
    void addIntentOps() {
    	for (Operation op : inst.getIntentOps()) {
    		addOp(op);
    	}
    }

    @Override
    public void run() {
        operations = new Vector<Operation>();
        faithfulOperations = new Vector<Operation>();

        // don't click home, or we will be ejected...
        addOp(new KeyPressOp(inst, 0.8, KeyEvent.KEYCODE_MENU));
        addOp(new KeyPressOp(inst, 0.1, KeyEvent.KEYCODE_BACK));
        addOp(new KeyPressOp(inst, 0.1, KeyEvent.KEYCODE_SEARCH));

        addOp(new PauseAndResumeOp(inst, 0.05));
        addOp(new StopAndRestartOp(inst, 0.1));
        addOp(new ForceRotateOp(inst, 0.1), new RelaunchOp(inst, 0.1));
        addOp(new RotateOp(inst, 0.1));

        if (inst.mayRoll(inst.getCurrentActivity())) {
            addOp(new RollOp(inst, 0.2));
        }
        
        addBroadcastOps();
        addIntentOps();

        View focusedDecorView = null;
        boolean hasLeft = false, hasRight = false;
        Vector<View> stack = new Vector<View>();
        Vector<ViewInfo> info = new Vector<ViewInfo>();
        try {
            View v = inst.getFocusedDecorView();
            if (v == null) {
                operations.clear();
                faithfulOperations.clear();
                return;
            }
            focusedDecorView = v;
            stack.add(v);
            info.add(new ViewInfo(false));
        } catch (ACIException e) {
            return;
        }

        while (!stack.isEmpty()) {
            View v = stack.lastElement();
            ViewInfo vi = info.lastElement();
            stack.remove(stack.size() - 1);
            info.remove(info.size() - 1);
//            Log.d("Collector", "inspecting " + inst.getViewDesc(v));


            if (v.isEnabled() && visibleFromRoot(v)) {
//                Log.d("Collector", inst.getViewDesc(v) + " visible");
				if (v.getClass().getCanonicalName() != null &&
                		v.getClass().getCanonicalName().equals(
                            "com.android.internal.view.menu.ExpandedMenuView")) {
                    // IconMenu's items are clickable TextViews
                    // So they are covered in last rule
//                            ) || v.getClass().getName().equals(
//                            "com.android.internal.view.menu.IconMenuView")) {
                    ViewGroup vg = (ViewGroup)v;
                    for (int i=0; i<vg.getChildCount(); i++) {
                        if (visibleFromRoot(vg.getChildAt(i)))
                            addOp(new PointerClickOp(inst, 1, vg.getChildAt(i)));
                    }
                } else if (v instanceof ListView) {
                    ViewGroup vg = (ViewGroup)v;
                    int n = vg.getChildCount();
                    for (int i=0; i<vg.getChildCount(); i++) {
                        View child = vg.getChildAt(i);
                        // check for disabled items
                        // and title items
                        if (child.isEnabled() && 
                            !((child.getClass() == TextView.class) 
                            && (child.getId() == inst.getInternalId("title")))) {
                            if (visibleFromRoot(child)) {
                                addOp(new PointerClickOp(inst, 2.0 / (double)n,  child));
                                addOp(new PointerLongClickOp(inst, 1.5 / (double)n, child), new LongClickOp(inst, 1.5 / (double)n, child, false));
                            }
                        }
                    }
                    ListView lv = (ListView)v;
                    int firstVisible = lv.getFirstVisiblePosition();
                    int lastVisible = lv.getLastVisiblePosition();
                    int count = lv.getCount();
                    if (count > 0) {
                        addOp(new ListScrollOp(inst, 0.25, v, firstVisible), new ListSelectOp(inst, 0.25, v, firstVisible));
                        if (lastVisible != firstVisible)
                            addOp(new ListScrollOp(inst, 0.25, v, lastVisible), new ListSelectOp(inst, 0.25, v, lastVisible));
                        if (firstVisible - 1 >= 0)
                            addOp(new ListScrollOp(inst, 0.25, v, firstVisible - 1), new ListSelectOp(inst, 0.25, v, firstVisible - 1));
                        if (lastVisible + 1 < count)
                            addOp(new ListScrollOp(inst, 0.25, v, lastVisible + 1), new ListSelectOp(inst, 0.25, v, lastVisible + 1));
                    }
                } else if (v instanceof GridView) {
                    ViewGroup vg = (ViewGroup)v;
                    for (int i=0; i<vg.getChildCount(); i++) {
                        View child = vg.getChildAt(i);
                        // check for disabled items
                        // and title items
                        if (child.isEnabled() && visibleFromRoot(child)) {
                            addOp(new PointerClickOp(inst, 1.0 / (double)vg.getChildCount(), child));
                            // is GridView's child Longclickable?
//                            addOp(new LongClickOp(inst, child));
                        }
                    }
                } else {
/*                    if (v instanceof Button || v instanceof Checkable
                        || v instanceof ImageView || v instanceof TextView || v instanceof ViewGroup) {
                    // subclass: CheckBox, RadioButton, Switch, ToggleButton
                    // implemented: CheckedTextView, CompoundButton, ^^^*/
                    
                    // Everything isClickable() is clickable
                    // Instance of "View" has been seen
                    if (v.isClickable()) {
                        double prob = 1;
                        // Button is TextView
                        if (v instanceof TextView) prob = 0.1;
                        if (v instanceof Button) prob = 2;
                        // ToggleButton is Button, TextView & Checkable
                        if (v instanceof Checkable) prob = 0.3;
                        addOp(new PointerClickOp(inst, prob, v));
                    }
                    if (v.isLongClickable()) {
                        double prob = 1;
                        // Button is TextView
                        if (v instanceof TextView) prob = 0.1;
                        if (v instanceof Button) prob = 0.4;
                        addOp(new PointerLongClickOp(inst, prob, v), new LongClickOp(inst, prob, v, !vi.inListView()));
                    }
                }
				if (v instanceof AbsSeekBar) {
                    addOp(new RealMoveSeekBarOp(inst, 1, v), new MoveSeekBarOp(inst, 1, v));
                } else if (v instanceof EditText) {
                    addOp(new EnterTextOp(inst, 2, v), new SetEditTextOp(inst, 2, v));
                } else if (android.os.Build.VERSION.SDK_INT >= 11) {
                    if (v instanceof NumberPicker)
                        addOp(new RealSetNumberPickerOp(inst, 1, v), new SetNumberPickerOp(inst, 1, v));
                }
            }
            int loc[] = new int[2];
            v.getLocationOnScreen(loc);
            if (loc[0] < -focusedDecorView.getWidth() / 3) {
                hasLeft = true;
            }
            if (loc[0] > focusedDecorView.getWidth() * (1 + 1/3)) {
                hasRight = true;
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup)v;
                if (inst.instanceOf(vg, inst.viewPagerKlass)) {
                    View possible = null;
                    int maxLen = -1;
                    for (int i=0; i<vg.getChildCount(); i++) {
                        View vc = vg.getChildAt(i);
                        if (vc != null) {
                            vc.getLocationOnScreen(loc);
                            if (loc[0] >= focusedDecorView.getWidth())
                                hasRight = true;
                            if (loc[0] < 0) {
                                hasLeft = true;
                            }

                            if (visibleFromRoot(vc)) {
                                int len = getIntersectLen(loc[0], vc.getWidth(), 0, focusedDecorView.getWidth());
                                if (len > maxLen) {
                                    maxLen = len;
                                    possible = vc;
                                }
                            }
                        }
                    }
                    if (possible != null) {
                        stack.add(possible);
                        info.add(new ViewInfo(vi.inListView() ? true : false));
                    }
/*                    try {
                        View vc = vg.getChildAt((Integer)inst.viewPagerMethodGetCurrentItem.invoke(vg, (Object[])null));
                        if (vc != null) {
                            stack.add(vc);
                            info.add(new ViewInfo(vi.inListView() ? true : false));
                        }
                    } catch (Exception e) { e.printStackTrace(); }*/
                } else {
                	if (v instanceof ScrollView) {
                		ScrollView sv = (ScrollView)v;
                		if (vg.getChildCount() > 0) {
                			View child = sv.getChildAt(0);
                			if (child.getHeight() > sv.getHeight()) {
                				// Vertical ops
                				if (sv.getScrollY() > 0) {
                					addOp(new SlideOp(inst, 1, sv, SlideOp.Direction.DOWN));
                				}
                				if (sv.getScrollY() + sv.getHeight() < child.getHeight()) {
                					addOp(new SlideOp(inst, 1, sv, SlideOp.Direction.UP));
                				}
                			}
                			if (child.getWidth() > sv.getWidth()) {
                				// Horizontal ops
                				if (sv.getScrollX() > 0) {
                					addOp(new SlideOp(inst, 1, sv, SlideOp.Direction.RIGHT));
                				}
                				if (sv.getScrollX() + sv.getWidth() < child.getWidth()) {
                					addOp(new SlideOp(inst, 1, sv, SlideOp.Direction.LEFT));
                				}
                				
                			}

                		}
                	}
                    for (int i=0; i<vg.getChildCount(); i++) {
                        View vc = vg.getChildAt(i);
                        if (vc != null) {
                            stack.add(vc);
                            info.add(new ViewInfo(vi.inListView() ? true : vg instanceof ListView ? true : false));
                        }
                    }
                }
            }
        }
        if (hasLeft) {
            addOp(new SlideOp(inst, 1, focusedDecorView, SlideOp.Direction.RIGHT));
        }
        if (hasRight) {
            addOp(new SlideOp(inst, 1, focusedDecorView, SlideOp.Direction.LEFT));
        }
    }

    int getIntersectLen(int s1, int l1, int s2, int l2) {
        int s = Math.max(s1, s2);
        int e = Math.min(s1+l1, s2+l2);
        if (s < e) {
            return e-s;
        } else {
            return 0;
        }
    }

    void addOp(Operation op) {
        operations.add(op);
        faithfulOperations.add(op);
    }

    void addOp(Operation faiOp, Operation absOp) {
        operations.add(absOp);
        faithfulOperations.add(faiOp);
    }

    Vector<Operation> getResult() {
        return operations;
    }

    Vector<Operation> getFaithfulResult() {
        return faithfulOperations;
    }
}
