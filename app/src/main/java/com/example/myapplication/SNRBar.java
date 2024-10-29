package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class SNRBar extends View {
    private float currentSNR;
    private float maxSNR;  // Track the highest recorded SNR value
    private Paint snrPaint;
    private Paint maxSNRPaint;
    private Paint textPaint;
    private String snrCategory = "";  // Track the current SNR category

    public SNRBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        snrPaint = new Paint();
        snrPaint.setColor(Color.BLUE);

        maxSNRPaint = new Paint();
        maxSNRPaint.setColor(Color.RED);
        maxSNRPaint.setStrokeWidth(5f);
        maxSNRPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        maxSNR = 0;  // Initialize with 0 as no recording has been done yet
    }

    public void updateSNR(float snrRatio, float snrValue) {
        currentSNR = snrValue;

        // Update max SNR if the current value is greater
        if (currentSNR > maxSNR) {
            maxSNR = currentSNR;
        }

        // Trigger redraw
        invalidate();
    }

    public void setSNRCategory(String category) {
        this.snrCategory = category;
        invalidate();  // Trigger redraw to update the displayed category
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Calculate the height of the bar based on the current SNR value
        float snrHeight = getHeight() * (currentSNR / 100);  // Assuming SNR value is normalized to max 100
        canvas.drawRect(0, getHeight() - snrHeight, getWidth(), getHeight(), snrPaint);

        // Draw the maximum SNR indicator
        float maxSNRHeight = getHeight() * (maxSNR / 100);
        canvas.drawLine(0, getHeight() - maxSNRHeight, getWidth(), getHeight() - maxSNRHeight, maxSNRPaint);

        // Draw the rating text to indicate SNR quality
        canvas.drawText(snrCategory, getWidth() / 2, getHeight() / 2, textPaint);
    }

    // Provide feedback on the SNR quality
    private String getSNRRating(float snrValue) {
        if (snrValue < 10) {
            return "Poor";
        } else if (snrValue < 30) {
            return "Average";
        } else if (snrValue < 60) {
            return "Good";
        } else {
            return "Excellent";
        }
    }
}
