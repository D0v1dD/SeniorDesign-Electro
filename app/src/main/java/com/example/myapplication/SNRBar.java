package com.example.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class SNRBar extends View {

    private Paint paint;
    private Paint textPaint;
    private float snrRatio = 0f; // SNR ratio (0 to 1)
    private float snrValue = 0f; // Actual SNR value to display numerically

    // Constructors for custom view
    public SNRBar(Context context) {
        super(context);
        init();
    }

    public SNRBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();

        // Get custom attributes from XML (if any)
        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.SNRBar,
                0, 0);

        try {
            snrRatio = a.getFloat(R.styleable.SNRBar_snr_initial, 0f);
        } finally {
            a.recycle();
        }
    }

    public SNRBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    // Initialize the paint and other resources
    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);

        // Initialize paint for text
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    // Method to update the SNR ratio and redraw the bar
    public void updateSNR(float newSnrRatio, float newSnrValue) {
        // Ensure the ratio is between 0 and 1
        snrRatio = Math.max(0, Math.min(newSnrRatio, 1));
        snrValue = newSnrValue;
        invalidate(); // Trigger re-draw on UI thread
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Padding for the bar to avoid edge collision
        int padding = 10;

        // Calculate the height of the filled portion of the bar based on SNR ratio
        int barHeight = getHeight() - 2 * padding;
        int filledHeight = (int) (barHeight * snrRatio);

        // Draw the background (empty part of the bar)
        paint.setColor(0xFFCCCCCC); // Light grey color for background
        canvas.drawRect(0, padding, getWidth(), barHeight + padding, paint);

        // Set color based on SNR value
        if (snrRatio < 0.33f) {
            paint.setColor(0xFFFF0000); // Red for low SNR
            textPaint.setColor(Color.WHITE); // Set text color to white for better visibility
        } else if (snrRatio < 0.66f) {
            paint.setColor(0xFFFFFF00); // Yellow for medium SNR
            textPaint.setColor(Color.BLACK); // Set text color to black
        } else {
            paint.setColor(0xFF00FF00); // Green for high SNR
            textPaint.setColor(Color.BLACK); // Set text color to black
        }

        // Draw the filled part of the bar representing the SNR
        canvas.drawRect(0, barHeight + padding - filledHeight, getWidth(), barHeight + padding, paint);

        // Adjust text size based on the bar height
        textPaint.setTextSize(Math.min(40f, barHeight / 5f));

        // Draw the numerical SNR value at the center of the bar
        canvas.drawText(String.format("SNR: %.2f", snrValue), getWidth() / 2f, barHeight / 2f + padding, textPaint);
    }
}
