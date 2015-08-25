package com.andchecker;
import android.app.Activity;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainUI extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Button button = new Button(this);
        button.setText("Start Instrumentation");
        button.setOnClickListener(mGoListener);

        setContentView(button);
    }

    public static int mPublic = 0;

    private OnClickListener mGoListener = new OnClickListener() {
        public void onClick(View v) {
            startInstrumentation(
                new ComponentName(MainUI.this,
                                  ACInstrumentation.class), null, null);
        }
    };
}

