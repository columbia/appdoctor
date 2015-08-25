package com.andchecker;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class MonkeyRunnerGenerator {
    public final String DOWN = "DOWN";
    public final String UP = "UP";
    public final String DOWN_AND_UP = "DOWN_AND_UP";

    public MonkeyRunnerGenerator(String scriptFile) {
        try {
            mScript = new BufferedWriter(new FileWriter(scriptFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void writeHeader(String packageName, String activity) {
        try {
            mScript.write("from com.android.monkeyrunner import MonkeyRunner, MonkeyDevice\n" +
                    "import os, time\n" +
                    "os.system('adb shell pm clear " + packageName + "')\n" +
                    "device = MonkeyRunner.waitForConnection()\n");
            startActivity(packageName, activity);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void startActivity(String packageName, String activity) {
        startActivity(packageName, activity, 10);
    }

    public void startActivity(String packageName, String activity, double sleepTime) {
        writeCommand(String.format("device.startActivity(categories=['android.intent.category.LAUNCHER'], component='%s/%s')\n", packageName, activity), sleepTime);
    }

    public void press(String keyCode, String type) {
        press(keyCode, type, 5);
    }

    public void press(String keyCode, String type, double sleepTime) {
        writeCommand(String.format("device.press('%s', MonkeyDevice.%s)\n", keyCode, type), sleepTime);
    }

    public void touch(int x, int y, String type) {
        touch(x, y, type, 5);
    }

    public void touch(int x, int y, String type, double sleepTime) {
        writeCommand(String.format("device.touch(%d, %d, MonkeyDevice.%s)\n", x, y, type), sleepTime);
    }

    public void drag(int x, int y, int width, int height, int distanceX, int distanceY) {
        // may not behave correctly if distance is tiny.
        if (distanceX != 0) {
            // assume horizontal drags can be done once.
            writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y, x + distanceX, y + distanceY), 1);
        } else {
            // vertical drags may be split into several drags.
            int breakage = ACInstrumentation.getSelf().getContext().getResources().getDisplayMetrics().densityDpi / 20;
            if (distanceY < 0) {
                while (distanceY < (breakage + 2 - height) * 2) {
                    writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y, x, y + height - 2), 1);
                    distanceY += height - 2 - breakage;
                }
                if (distanceY < breakage + 2 - height) {
                    writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y, x, y - distanceY / 2 + breakage), 1);
                    distanceY += distanceY / 2;
                }
                writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y, x, y - distanceY + breakage), 1);
            } else if (distanceY > 0) {
                while (distanceY > (height - 2 - breakage) * 2) {
                    writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y + height - 2, x, y), 1);
                    distanceY -= height - 2 - breakage;
                }
                if (distanceY > height - 2 - breakage) {
                    writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y + distanceY / 2 + breakage, x, y), 1);
                    distanceY -= distanceY / 2;
                }
                writeCommand(String.format("device.drag((%d, %d), (%d, %d))\n", x, y + distanceY + breakage, x, y), 1);
            }
        }
    }

    public void type(String content) {
        writeCommand(String.format("device.type('%s')\n", content.replaceAll("\\\\", "\\\\").replaceAll("'", "\\'")), 3);
    }

    public void sleep(int ms) {
        writeCommand("", ms / 1000.0);
    }

    public void prompt(String message) {
        writeCommand(String.format("raw_input('%s')\n", message.replaceAll("\\\\", "\\\\").replaceAll("'", "\\'")), 1);
    }

    public void writeCommand(String command, double sleepTime) {
        try {
            mScript.write(command);
            mScript.write(String.format("time.sleep(%f)\n", sleepTime));
            mScript.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private BufferedWriter mScript;
}
