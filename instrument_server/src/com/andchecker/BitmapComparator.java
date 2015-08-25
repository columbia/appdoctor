package com.andchecker;

import java.io.File;
import java.util.Vector;

import android.app.Activity;
import android.util.Log;
import android.view.View;

public class BitmapComparator {
    private ACInstrumentation inst;
    private BBImage image;
    File beforeFile;
    File afterFile;
    
    boolean careAbout(View v) {
    	return !(v == null || !(v.getContext() instanceof Activity) || v.getClass().getName().contains("Menu"));
    }

    BitmapComparator(ACInstrumentation inst) throws ACIException {
    	beforeFile = null;
    	afterFile = null;
        this.inst = inst;
        View v = inst.getFocusedDecorView();
        if (!careAbout(v))
        	image = null;
        else {
       		image = inst.getViewImageAsBBImage(v);
        }
    }

    boolean compareWithCurrentView(boolean saveRet) throws ACIException, SizeChanged {
//        boolean ret = bitmap != null && inst.getViewBitmap(inst.getFocusedDecorView()).sameAs(bitmap);
    	View focusedView = inst.getFocusedDecorView();
    	if (!careAbout(focusedView)) {
    		if (image != null) {
    			Log.w("BitmapCompartor", "no focused view now!");
    			image.free();
    			image = null;
    		}
			return true;
    	}
    	if (image == null) {
    		return true;
    	}
    	BBImage newImage = inst.getViewImageAsBBImage(focusedView);
    	boolean ret;
    	try {
    		ret = compareBitmap(newImage, image);
    		if (!ret && image != null) {
    			if (saveRet) {
    				try {
    					beforeFile = image.save("before.png");
    					afterFile = newImage.save("after.png");
    				} catch (Exception e) {
    					e.printStackTrace();
    				}
    			}
    		}
    	} catch (SizeChanged e) {
    		newImage.free();
    		throw e;
    	}
    	image.free();
    	image = newImage;
        return ret;
    }
    
    void recycle() {
    	if (image != null) {
    		image.free();
    		image = null;
    	}
    }
    
    final int dx[] = {-1, 0, 1, 0};
    final int dy[] = {0, -1, 0, 1};
    
    class SizeChanged extends Exception { private static final long serialVersionUID = 1L; }
    
    boolean compareBitmap(BBImage a, BBImage b) throws SizeChanged {
    	if (a == null || b == null)
    		return true;
    	if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
    		if (a.getWidth() == b.getHeight() && a.getHeight() == b.getWidth()) {
    			Log.d("compareBitmap", String.format("size diff!"));
    			throw new SizeChanged();
    		} else {
    			return false;
    		}
    	}
    	return NativeHelper.diffNativeByteBuffers0(a.getBuffer(), b.getBuffer(), a.getWidth(), a.getHeight(), 2/*6*/);
    }
    	
    boolean compareBitmapOld(BBImage a, BBImage b) {
    	int width = a.getWidth();
    	int height = a.getHeight();
    	int[] ap = new int[width * height];
    	a.getPixels(ap);
    	int[] bp = new int[width * height];
    	b.getPixels(bp);
    	boolean visited[][] = new boolean[a.getWidth()][a.getHeight()];
    	for (int i=0; i<a.getWidth(); i++)
    		for (int j=0; j<a.getHeight(); j++)
    			visited[i][j] = false;
    	Log.d("compareBitmap", String.format("before BFS: %dx%d", a.getWidth(), a.getHeight())); 
    	for (int i=0; i<a.getWidth(); i++)
    		for (int j=0; j<a.getHeight(); j++) {
    			if (!visited[i][j]) {
    				visited[i][j] = true;
    				if (ap[j*width+i] != bp[j*width+i]) {
    					Vector<Integer> queuex = new Vector<Integer>();
    					Vector<Integer> queuey = new Vector<Integer>();
    					queuex.add(i);
    					queuey.add(j);
    					int minX = -1;
    					int minY = -1;
    					int maxX = -1;
    					int maxY = -1;
    					int count = 0;
    					int qh = 0;
    					int qe = 0;
    					int ne = 0;
    					while (qh <= qe) {
    						for (int qi = qh; qi <= qe; qi++) {
    							int x = queuex.get(qh);
    							int y = queuey.get(qh);
    							count += 1;
    							if (x < minX || minX == -1)
    								minX = x;
    							if (x > maxX)
    								maxX = x;
    							if (y < minY || minY == -1)
    								minY = y;
    							if (y > maxY)
    								maxY = y;
    							for (int l=0; l<4; l++) {
    								x += dx[l];
    								y += dy[l];
    								if ((x >= 0) && (x < a.getWidth()) && (y >= 0) && (y < a.getHeight())) {
    									if (!visited[x][y]) {
    										visited[x][y] = true;
    										if (ap[y*width+x] != bp[y*width+x]) {
    											queuex.add(x);
    											queuey.add(y);
    											ne += 1;
    										}
    									}
    								}
    								x -= dx[l];
    								y -= dy[l];
    							}
    						}
    						qh = qe + 1; qe = ne;
    					}
    					Log.d("compareBitmap", String.format("region: %d %d-%d x %d-%d", count, minX, maxX, minY, maxY)); 
    					if (maxX - minX > 6 && maxY - minY > 6)
    						return false;
    				}
    			}
    		}
    	Log.d("compareBitmap", String.format("after BFS: %dx%d", a.getWidth(), a.getHeight()));
    	return true;
    }
}
