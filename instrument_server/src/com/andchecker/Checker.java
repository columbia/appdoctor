package com.andchecker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Collection;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewParent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.TextView;
import java.util.Iterator;

public abstract class Checker {
    Context context;
    void checkView(View view) throws Exception { }
    void check() throws Exception {}
    protected int getViewHash(View view) { return 0; }
    void reset() {}
    void rotate() {}
    void recycle() {}

    static HashMap<String, Checker> viewCheckers = new HashMap<String, Checker>();
    static HashMap<String, Checker> generalCheckers = new HashMap<String, Checker>();
    static HashMap<String, Checker> orientationCheckers = new HashMap<String, Checker>();
    static String addChecker(Map<String, Checker> checkerSet, Checker c) {
        try
        {
            String className = c.getClass().getName();
            if (checkerSet.containsKey(className)) return className;
            checkerSet.put(className, c);
            return className;
        }
        catch (Exception x)
        {
            return null;
        }
    }
    static String addChecker(Map<String, Checker> checkerSet, String name) {
        Class<?> c;
        try
        {
            try
            { c = Class.forName(name); }
            catch (ClassNotFoundException x)
            { c = null; }
            
            if (c == null)
                c = Class.forName(Checker.class.getPackage().getName() + "." + name);

            return addChecker(checkerSet, (Checker)c.newInstance());
        }
        catch (Exception x)
        { return null; }
    }

    static String removeChecker(Map<String, Checker> checkerSet, String name) {
        Class<?> c;
        try
        {
            try
            { c = Class.forName(name); }
            catch (ClassNotFoundException x)
            { c = null; }
            
            if (c == null)
                c = Class.forName(Checker.class.getPackage().getName() + "." + name);

            String cn = c.getName();
            if (checkerSet.containsKey(cn))
                checkerSet.remove(cn);
            return cn;
        }
        catch (Exception x)
        { return null; }
    }

    static String addViewChecker(String name) {
        return addChecker(viewCheckers, name);
    }

    static String addViewChecker(Checker c) {
        return addChecker(viewCheckers, c);
    }

    static String addGeneralChecker(String name) {
        return addChecker(generalCheckers, name);
    }

    static String addGeneralChecker(Checker c) {
        return addChecker(generalCheckers, c);
    }

    static String addOrientationChecker(String name) {
        return addChecker(orientationCheckers, name);
    }

    static String addOrientationChecker(Checker c) {
        return addChecker(orientationCheckers, c);
    }

    static String removeViewChecker(String name) {
        return removeChecker(viewCheckers, name);
    }

    static String removeGeneralChecker(String name) {
        return removeChecker(generalCheckers, name);
    }

    static String removeOrientationChecker(String name) {
        return removeChecker(orientationCheckers, name);
    }
    
    static{
        addViewChecker(new TextSizeChecker());
        addViewChecker(new TextColorChecker());
        addViewChecker(new InputConnectionChecker());
        addViewChecker(new TextOverlapChecker());
//        addGeneralChecker(new LogcatChecker());
        addOrientationChecker(new OrientationChecker());
    }

    // static Checker[] viewCheckers = {new TextSizeChecker(), new TextColorChecker(), new InputConnectionChecker(), new TextOverlapChecker()};
    // static Checker[] generalCheckers = {new LogcatChecker()};
    // static Checker[] orientationCheckers = {new OrientationChecker()};
    static void checkEveryView(View view, Collection<Checker> checkers) throws Exception {
        checkEveryViewInternal(view, checkers, true);
    }

    static int getViewHashFromAllCheckers(View view) throws Exception {
        return checkEveryViewInternal(view, viewCheckers.values(), false);
    }

    private static int checkEveryViewInternal(View view, Collection<Checker> checkers, boolean shouldCheck) throws Exception {
        int viewHash = view.hashCode();
        if (view.getVisibility() != View.VISIBLE)
            return viewHash;
        for (Checker checker : checkers) {
//            Log.d("Checker", checker.getClass().getName() + ": checking " + view.getId());
            if (shouldCheck) {
                checker.checkView(view);
            }
            viewHash ^= checker.getViewHash(view);
//            Log.d("Checker", checker.getClass().getName() + ": checked  " + view.getId());
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)view;
            for (int i=0; i<vg.getChildCount(); i++)
                viewHash ^= checkEveryViewInternal(vg.getChildAt(i), checkers, shouldCheck);
        }
        return viewHash;
    }
}

class TextSizeChecker extends Checker {
    @Override
    void checkView(View view) throws CheckerException {
        if (view instanceof TextView) {
            TextView tv = (TextView)view;
            // empty textview? ignore
            if (tv.getText().length() == 0) return;
            Rect r = new Rect();
            if (!view.getGlobalVisibleRect(r))
                return;

            float width = tv.getPaint().measureText(tv.getText(), 0, 1);
            
            DisplayMetrics metrics = view.getResources().getDisplayMetrics();
            float phyWidth = width / metrics.xdpi;
            Log.d("TextSizeChecker", "Text size: " + phyWidth);
            
            if (phyWidth < 0.02) {
            	CheckerException ce = new CheckerException("TextSizeChecker", String.format(
            			"Text size too small: %f inch, @%dx%d %dx%d id: 0x%08x",
            			phyWidth, r.left, r.top, r.width(), r.height(), tv.getId()));
            	try {
            		BBImage image = ACInstrumentation.getSelf().grabSnapshotBB(view);
            		image.drawRects(new Rect[] {r});
            		ce.addExtraFile(image.save("textsize.png"));
            	} catch (Exception e) {}
            	ACInstrumentation.getSelf().onException(this, ce);
            }
        }
    }

    @Override
    protected int getViewHash(View view) {
        return (view instanceof TextView) ? (int)(((TextView) view).getText().toString().hashCode() * ((TextView) view).getTextSize()) : 0;
    }
}

class TextColorChecker extends Checker {
    static final double CONTRAST_MIN_DIST = 25;
    static final int RANDOM_PI_COUNT = 50;
    BBImage backImage;
    class Invisible extends Exception {
        private static final long serialVersionUID = 1L;
    }
    int getBackColor(View view, int left, int top, int width, int height) throws Invisible {
        Log.d("TextColorChecker", String.format("getBackColor for %s %08x: @%dx%d %dx%d", view.toString(), view.getId(), left, top, width, height));
    	if (backImage != null) {
    		backImage.free();
    		backImage = null;
    	}
        long avg_red = 0, avg_green = 0, avg_blue = 0, avg_alpha = 0;
        if (view.getBackground() instanceof ColorDrawable) {
            ColorDrawable cd = (ColorDrawable)view.getBackground();
            Bitmap img = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(img);
            cd.draw(canvas);
            int color = img.getPixel(0, 0);
            avg_alpha = cd.getAlpha();
            avg_red = Color.red(color);
            avg_green = Color.green(color);
            avg_blue = Color.blue(color);
            canvas = null;
            img = null;
        } else {
        	if (view.getBackground() != null)
        		Log.w("TextColorChecker", "calculating: " + view.getBackground().getClass().getName());
        	else
        		Log.w("TextColorChecker", "calculating background");
            int backColor = view.getDrawingCacheBackgroundColor();
            view.setDrawingCacheBackgroundColor(0);
            boolean dcEnabled = view.isDrawingCacheEnabled();
            try {
            	view.setDrawingCacheEnabled(true);
            	/*          view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), 
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());*/

            	view.buildDrawingCache();
            	Bitmap cache = view.getDrawingCache();
            	if (cache == null) {
            		cache = view.getDrawingCache();
            		if (cache == null) {
            			Log.e("TextColorChecker", "getDrawingCache() == null");
            			throw new Invisible();
            		}
            	}
            	//Bitmap img = Bitmap.createBitmap(cache);
            	Bitmap img = cache;
            	//          view.setDrawingCacheEnabled(s);
            	Log.w("TextColorChecker", "snapshot taken, " + img.getWidth() + "x" + img.getHeight());
//            	backImage = new BBImage(img);
            	if (top + height > img.getHeight())
            		height = img.getHeight() - top;
            	if (left + width > img.getWidth())
            		width = img.getWidth() - left;
            	if (height <= 0 || width <= 0)
            		throw new Invisible();


            	for (int pid=0; pid<RANDOM_PI_COUNT; pid++) {
            		int i = top + (int)(ACInstrumentation.getSelf().chkrand.nextDouble() * height);
            		int j = left + (int)(ACInstrumentation.getSelf().chkrand.nextDouble() * width);
            		int pixel = img.getPixel(j, i);
            		avg_alpha += Color.alpha(pixel);
            		avg_red += Color.red(pixel);
            		avg_green += Color.green(pixel);
            		avg_blue += Color.blue(pixel);
            		//                    Log.w("TextColorChecker", "point: " + Color.alpha(pixel) + " " + String.format("%02x%02x%02x", Color.red(pixel), Color.green(pixel), Color.blue(pixel)));
            	}
            	avg_alpha /= RANDOM_PI_COUNT;
            	avg_red /= RANDOM_PI_COUNT;
            	avg_green /= RANDOM_PI_COUNT;
            	avg_blue /= RANDOM_PI_COUNT;
            } finally {
            	view.destroyDrawingCache();
                view.setDrawingCacheEnabled(dcEnabled);
                view.setDrawingCacheBackgroundColor(backColor);
            }
        }
        Log.w("TextColorChecker", "result: " + avg_alpha + " " + String.format("%02x%02x%02x", avg_red, avg_green, avg_blue));
        int result = Color.argb((int)avg_alpha, (int)avg_red, (int)avg_green, (int)avg_blue); 
        if (avg_alpha > 200) {
            return result;
        } else {
            ViewParent parent = view.getParent();
            if (parent instanceof View) {
            	int newLeft = view.getLeft() + left;
            	int newTop = view.getTop() + top;
            	int newWidth = width;
            	int newHeight = height;
            	if (newLeft < 0) {
            		newWidth = width + newLeft;
            		if (newWidth < 0)
            			throw new Invisible();
            		newLeft = 0;
            	}
            	if (newTop < 0) {
            		newHeight = height + newTop;
            		if (newHeight < 0)
            			throw new Invisible();
            		newTop = 0;
            	}
                return Utils.blendColor(result, getBackColor((View)parent, newLeft, newTop, newWidth, newHeight));
            } else
                return Color.argb(255, 255, 255, 255);
        }       
    }
    @Override
    void checkView(View view) throws CheckerException {
        if (view instanceof TextView) {
            TextView tv = (TextView)view;
            // empty? ignore
            if (tv.getText().length() == 0) return;
            Rect r = new Rect();
            // invisible? ignore
            if (!tv.getGlobalVisibleRect(r))
            	return;
            
            backImage = null;
            
            try {
                int textColor = tv.getTextColors().getDefaultColor();
                int backColor = getBackColor(tv, 0, 0, tv.getWidth(), tv.getHeight());
                double colorDist = Utils.dist(Color.red(textColor), Color.green(textColor), Color.blue(textColor), 
                        Color.red(backColor), Color.green(backColor), Color.blue(backColor)); 
                Log.w("TextColorChecker", "text contrast: " + view.getClass().getName() + " " + view.getId() + " dist: " + colorDist);
                if (colorDist < CONTRAST_MIN_DIST) {
                    CheckerException ce = new CheckerException("TextColorChecker",
                    	"text contrast too low: " + view.getClass().getName() + " " + view.getId() + " dist: " + colorDist);
                    try {
                    	BBImage image = ACInstrumentation.getSelf().grabSnapshotBB(view);
                    	image.drawRects(new Rect[] {r});
                        ce.addExtraFile(image.save("textcontrast.png"));
                        if (backImage != null)
                        	ce.addExtraFile(backImage.save("textcolor_back.png"));
                    } catch (Exception e) {}
                    ACInstrumentation.getSelf().onException(this, ce);
                }
            } catch (Invisible e) {
                return;
            } finally {
            	if (backImage != null) {
            		backImage.free();
            		backImage = null;
            	}
            }
        }
    }

    @Override
    protected int getViewHash(View view) {
        return (view instanceof TextView) ? ((TextView) view).getTextColors().getDefaultColor() : 0;
    }
}

class CheckerRunnable implements Runnable {
    ACInstrumentation inst;
    Exception exception;
    CheckerRunnable(ACInstrumentation inst) {
        this.inst = inst;
    }
    
    boolean gotException() {
        return (exception != null);
    }
    Exception getException() {
        return exception;
    }
    @Override
    public void run() {
        exception = null;
        try {
            Log.d("CheckerRunnable", "start");
            View views[] = inst.getWindowDecorViews();
            if (views != null) {
                Log.d("CheckerRunnable", "got decor views");
                for (View view : views) {
                    int viewHash = Checker.getViewHashFromAllCheckers(view);
                    if (!inst.existsInViewHashs(viewHash)) {
                        inst.addToViewHashs(viewHash);
                        for (Checker checker : Checker.viewCheckers.values())
                            checker.reset();
                        Checker.checkEveryView(view, Checker.viewCheckers.values());
                    }
                }
                Log.d("CheckerRunnable", "view checkers finished");
            } else {
            	Log.w("CheckerRunnable", "fail to get decor views");
            }
            for (Checker checker : Checker.generalCheckers.values())
            {
                Log.d("CheckerRunnable", "running general checker: " + checker.getClass().getName());
                checker.check();
            }
            
            Log.d("CheckerRunnable", "general checkers finished");
            
            Iterator<Runnable> c = inst.getCheckers();
            while (c.hasNext())
            {
                Runnable r = c.next();
                r.run();
            }
            Log.d("CheckerRunnable", "extra checkers finished");
        } catch (Exception e) {
            exception = e;
        }
    }
}

abstract class DelayedRunnable implements Runnable {
    Exception exception;
    DelayedRunnable() {
        exception = null;
    }
    public Exception getException() {
        return exception;
    }
}

class InputConnectionChecker extends Checker {
    @Override
    void checkView(View view) throws CheckerException {
        EditorInfo editInfo = new EditorInfo();
        InputConnection conn = view.onCreateInputConnection(editInfo);
        if (conn == null) return;
        if (editInfo.inputType != InputType.TYPE_NULL) return;
        try {
            if (conn.getClass().getMethod("deleteSurroundingText").getDeclaringClass().equals(BaseInputConnection.class)) {
                CheckerException ce = new CheckerException("InputConnectionChecker", 
                		"implemented onCreateInputConnection but deleteSurringText not overriden");
                ACInstrumentation.getSelf().onException(this, ce);
            }
        } catch (NoSuchMethodException e) {
            CheckerException ce = new CheckerException("InputConnectionChecker",
            	"implemented onCreateInputConnection but deleteSurringText not implemented. class: " + conn.getClass().toString());
            ACInstrumentation.getSelf().onException(this, ce);
        }
    }
}

class LogcatChecker extends Checker {
    private static class Worker extends Thread {
        Vector<String> lines;
        Process process;
        boolean error;
        int count;
        int totCount;
        private Worker() {
            process = null;
            error = false;
            count = 0;
            totCount = 0;
            lines = new Vector<String>();
        }
        public void run() {
            BufferedReader reader = null;
            int myPid = android.os.Process.myPid();
            String myPidStr = String.valueOf(myPid);
            Log.d("LogcatChecker", "running logcat");
            try {
                process = Runtime.getRuntime().exec(new String[]
                        {"logcat", "-d", "*:W"});
                Log.d("LogcatChecker", "logcat started");
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                Log.d("LogcatChecker", "checking logcat messages");
                String line;
                while ((line = reader.readLine()) != null) {
                    totCount ++;
                    if (line.indexOf(myPidStr) != -1) {
                        count += 1;
                        lines.add(line);
                    }
                }
                for (int i=lines.size() - 1; i>=0; i--) {
                    line = lines.get(i);
                    if (line.indexOf("logcat checker read stamp") != -1) {
                        break;
                    }
                    // find exception
                    // but ignore myself // may be redundant due to next rule
                    // and ignore other checkers
                    if (line.indexOf("Exception:") != -1 && line.indexOf("exception in logcat") == -1 && line.indexOf("CheckerException") == -1 && line.indexOf("INJECT_EVENT") == -1) {
                        Log.w("LogcatChecker", "LogcatChecker triggered by line \"" + line + "\"");
                        error = true;
                        break;
                    }
                }
                Log.d("LogcatChecker", String.format("checked %d msgs from %d msgs, pid %d", count, totCount, myPid));
                process.waitFor();
                Log.d("LogcatChecker", "logcat finished");
            } catch (InterruptedException e) {
            } catch (IOException e) {
                Log.e("LogcatChecker", "IOException: " + e.getMessage());
            } finally {
                if (reader != null)
                    try {
                        reader.close();
                    } catch (Exception e) {
                        Log.e("LogcatChecker", "fail to close reader");
                    }
            }
        }
    }
    @Override
    void check() throws CheckerException {
        Worker worker = new Worker();
        worker.start();
        try {
        	worker.join(500);
        	Log.w("LogcatChecker", "logcat checker read stamp");
        	if (worker.error) {
        		String path = ACInstrumentation.getSelf().getInstrumentDataDir().getAbsolutePath() + "/logcat.txt";
        		File f = new File(path);
        		try {
        			BufferedWriter bo = new BufferedWriter(new FileWriter(f));
        			for (String line : worker.lines) {
        				bo.write(line);
        				bo.newLine();
        			}
        			bo.close();
        		} catch (FileNotFoundException e) {
        			Log.e("LogcatChecker", "FileNotFound writing logcat! " + e.toString());
        		}
        		catch (IOException ex) {
        			Log.e("LogcatChecker", "IOException writing logcat! " + ex.toString());
        		}
        		
        		CheckerException ce = (CheckerException)new CheckerException("LogcatChecker",
        				"exception in logcat").addExtraFile(f);
        		ACInstrumentation.getSelf().onException(this, ce);
        	}
        } catch (InterruptedException e) {
            worker.interrupt();
        } finally {
            if (worker.process != null) {
                worker.process.destroy();
            }
        }
    }
}

class OrientationChecker extends Checker {
    SparseArray<Set<String>> values;
    BBImage before;
    boolean rotated;
    void beforeOrientationChange(View view) throws CheckerException {
        if (view instanceof TextView) {
            TextView tv = (TextView)view;
            String text = tv.getText().toString();
            if (values.get(view.getId()) != null) {
                Set<String> pastVal = values.get(view.getId());
                if (!pastVal.contains(text)) {
                    pastVal.add(text);
                }
            } else {
                Set<String> pastVal = new HashSet<String>();
                pastVal.add(text);
                values.put(view.getId(), pastVal);
            }
        }
    }
    void afterOrientationChange(View view) throws CheckerWarning {
    	Rect r = new Rect();
    	if (!view.getGlobalVisibleRect(r))
    		return;
        if (view instanceof TextView) {
            TextView tv = (TextView)view;
            String text = tv.getText().toString();
            if (values.get(view.getId()) != null) {
            	Set<String> pastVal = values.get(view.getId());
            	if (!pastVal.contains(text)) {
                	Log.e("OrientationChecker", "original text list:");
                	for (String str : pastVal)
                		Log.e("OrientationChecker", "original text: " + str);
                	Log.e("OrientationChecker", "changed text: " + text);
                	CheckerWarning cw = new CheckerWarning("OrientationChecker",
                			"content change after orientatino change");
                	try {
                		BBImage after = ACInstrumentation.getSelf().grabSnapshotBB(view);
                		after.drawRects(new Rect[] {r});
                		cw.addExtraFile(after.save("ori_after.png"));
                		if (before != null) {
                			before.drawRects(new Rect[] {r});
                			cw.addExtraFile(before.save("ori_before.png"));
                			before.free();
                			before = null;
                		}
                	} catch (Exception e) {}
                	ACInstrumentation.getSelf().onException(this, cw);
                }
            }
        }
    }
    @Override
    void checkView(View view) throws Exception {
        if (rotated)
            afterOrientationChange(view);
        else
            beforeOrientationChange(view);
    }
    OrientationChecker() {
        values = new SparseArray<Set<String>>();
        rotated = false;
        before = null;
    }
    @Override
    void reset() {
        values = new SparseArray<Set<String>>();
        rotated = false;
        if (before != null) {
        	before.free();
        }
        try {
            before = ACInstrumentation.getSelf().grabSnapshotBB(null);
        } catch (Exception e) {}
    }
    @Override
    void rotate() {
        rotated = true;
    }
    @Override
    void recycle() {
    	before.free();
    	before = null;
    }
}

class TextOverlapChecker extends Checker {
    Set<Rect> rects;
    @Override
    void reset() {
        rects = new HashSet<Rect>();
    }
    @Override
    void checkView(View view) throws Exception {
        if (view.getVisibility() != View.VISIBLE) return;
        if (!(view instanceof TextView)) return;
        TextView tv = (TextView)view;
        if (tv.getText().length() == 0) return;
        if (tv.getLayoutParams().width != LayoutParams.WRAP_CONTENT) return;
        if (tv.getLayoutParams().height != LayoutParams.WRAP_CONTENT) return;
        Rect r = new Rect();
        int[] location = new int[2];
        tv.getLocationOnScreen(location);
        r.left = location[0];
        r.top = location[1];
        r.right = r.left + tv.getWidth();
        r.bottom = r.top + tv.getHeight();
        
        r.left += tv.getPaddingLeft();
        r.top += tv.getPaddingTop();
        r.right -= tv.getPaddingRight();
        r.bottom -= tv.getPaddingBottom();
        
        if (r.right <= r.left || r.bottom <= r.top)
        	return;
        
        Rect r2 = new Rect();
        if (!tv.getGlobalVisibleRect(r2))
            return;
        
        if (r2.left > r.left)
        	r.left = r2.left;
        if (r2.top > r.top)
        	r.top = r2.top;

        if (r2.right < r.right)
        	r.right = r2.right;
        if (r2.bottom < r.bottom)
        	r.bottom = r2.bottom;
        
        Log.d("TextOverlapChecker", "View: " + view.toString() + " Rect: " + r.toString());
        for (Rect oldr : rects)
            if ((oldr.left < r.right) && (oldr.right > r.left)
                    && (oldr.top < r.bottom) && (oldr.bottom > r.top)) {
            	CheckerException ce = new CheckerException("TextOverlapChecker",
            			"Text Overlap! " + r.toString() + "/" + oldr.toString());
            	try {
            		BBImage image = ACInstrumentation.getSelf().grabSnapshotBB(view);
            		image.drawRects(new Rect[] {oldr, r});
            		ce.addExtraFile(image.save("overlap.png"));
            	} catch (Exception e) {
            		Log.e("TextOverlapChecker", "fail to grab image: " + e.toString());
            	}
            	ACInstrumentation.getSelf().onException(this, ce);
            }
        rects.add(r);
    }

    @Override
    protected int getViewHash(View view) {
        return (view instanceof TextView) ? (int)(((TextView) view).getText().toString().hashCode() * ((TextView) view).getTextSize()) : 0;
    }

    class RectXIntervalFetcher extends IntervalTree.IntervalFetcher<Rect> {
        @Override
        int start(Rect obj) { return obj.left; }
        @Override
        int end(Rect obj) { return obj.right; }
    }
    class RectYIntervalFetcher extends IntervalTree.IntervalFetcher<Rect> {
        @Override
        int start(Rect obj) { return obj.top; }
        @Override
        int end(Rect obj) { return obj.bottom; }
    }
}

class LifeCycleChecker extends Checker {
	ACInstrumentation inst;
	BitmapComparator bc;
	
	LifeCycleChecker(ACInstrumentation inst) {
		this.inst = inst;
	}
	void before() throws Exception {
		Log.d("LifeCycleChecker", "before life cycle events");
		bc = new BitmapComparator(inst);
	}
	void after() throws Exception {
		Log.d("LifeCycleChecker", "after life cycle events");
		boolean same = bc.compareWithCurrentView(true);
		if (!same) {
			ACInstrumentation.getSelf().addDelayedRunnable(5000, new DelayedRunnable() {
				@Override
				public void run() {
					Log.d("LifeCycleChecker", "delayed check");
					try {
						boolean sameAsLast = bc.compareWithCurrentView(false);
						if (sameAsLast) {
							CheckerException ce = new CheckerException("LifeCycleChecker", "Screen changed after life cycle events!");
							ce.addExtraFile(bc.beforeFile);
							ce.addExtraFile(bc.afterFile);
							ACInstrumentation.getSelf().onException(this, ce);
						}
					} catch (Exception e) {
						exception = e;
					}
					bc.recycle();
					bc = null;
				}
			});
		} else {
			bc.recycle();
			bc = null;
		}
	}
}
