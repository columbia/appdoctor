package com.andchecker;

import java.io.File;
import java.util.Collection;
import java.util.ArrayList;

public class ACIException extends Exception {
	private static final long serialVersionUID = 1L;
	public static final int LEVEL_INTERNAL = 1;
    public static final int LEVEL_ERROR    = 2;
    public static final int LEVEL_BUG      = 3;
    public static final int LEVEL_WARNING  = 4;

    private int    mLevel;
    private String mToken;
    private ArrayList<String> mExtraFileNames;
    public ACIException(int level, String token, String detail) { super(detail); mLevel = level; mToken = token; mExtraFileNames = new ArrayList<String>(); }
    public ACIException addExtraFile(File file) { if (file == null) return this; mExtraFileNames.add(file.getAbsolutePath()); return this; }
    public String getToken() { return mToken; }
    public int getLevel() { return mLevel; }
    public Collection<String> getExtraFileNames() { return mExtraFileNames; }
}
