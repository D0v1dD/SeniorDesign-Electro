package com.example.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class SNRBar extends View {
    private float currentSNR = 0;
    private float maxSNR = 0;
    private Paint snrPaint;
    private Paint maxSNRPaint;
    private Paint textPaint;
    private String snrCategory = "";
    private LinearGradient gradient;

    public SNRBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        snrPaint = new Paint();

        maxSNRPaint = new Paint();
        maxSNRPaint.setColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark));
        maxSNRPaint.setStrokeWidth(5f);
        maxSNRPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(context, android.R.color.primary_text_light));
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // Initialize gradient colors from resources or defaults
        int startColor = ContextCompat.getColor(context, android.R.color.holo_red_dark);
        int endColor = ContextCompat.getColor(context, android.R.color.holo_green_dark);

        // Handle custom attributes (if any)
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SNRBar, 0, 0);
            try {
                startColor = a.getColor(R.styleable.SNRBar_startColor, startColor);
                endColor = a.getColor(R.styleable.SNRBar_endColor, endColor);
            } finally {
                a.recycle();
            }
        }

        gradient = new LinearGradient(0, 0, 0, getHeight(), startColor, endColor, Shader.TileMode.CLAMP);
        snrPaint.setShader(gradient);

        setWillNotDraw(false);
    }

    public void setSNRValue(final double snrValue) {
        // Ensure updates happen on the main thread
        post(() -> {
            currentSNR = (float) snrValue;

            // Update max SNR if the current value is greater
            if (currentSNR > maxSNR) {
                maxSNR = currentSNR;
            }

            // Update SNR category
            snrCategory = getSNRRating(currentSNR);

            // Trigger redraw
            invalidate();
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Recreate gradient with updated height
        gradient = new LinearGradient(0, 0, 0, getHeight(),
                ContextCompat.getColor(getContext(), android.R.color.holo_red_dark),
                ContextCompat.getColor(getContext(), android.R.color.holo_green_dark),
                Shader.TileMode.CLAMP);
        snrPaint.setShader(gradient);

        // Normalize SNR value to a 0-1 range for the bar height
        float normalizedSNR = (currentSNR + 20) / 100;  // Adjust based on expected SNR range
        normalizedSNR = Math.max(0, Math.min(normalizedSNR, 1));  // Clamp between 0 and 1

        float barHeight = getHeight() * normalizedSNR;
        RectF rect = new RectF(0, getHeight() - barHeight, getWidth(), getHeight());
        canvas.drawRect(rect, snrPaint);

        // Draw the maximum SNR indicator
        float normalizedMaxSNR = (maxSNR + 20) / 100;
        normalizedMaxSNR = Math.max(0, Math.min(normalizedMaxSNR, 1));
        float maxSNRHeight = getHeight() * normalizedMaxSNR;
        canvas.drawLine(0, getHeight() - maxSNRHeight, getWidth(), getHeight() - maxSNRHeight, maxSNRPaint);

        // Draw the SNR category text
        canvas.drawText(snrCategory, getWidth() / 2, getHeight() / 2, textPaint);
    }

    // Provide feedback on the SNR quality
    private String getSNRRating(float snrValue) {
        if (snrValue < 0) {
            return "Very Poor";
        } else if (snrValue < 10) {
            return "Poor";
        } else if (snrValue < 20) {
            return "Fair";
        } else if (snrValue < 30) {
            return "Good";
        } else if (snrValue < 40) {
            return "Very Good";
        } else {
            return "Excellent";
        }
    }
}
