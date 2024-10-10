package com.example.myapplication;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class SNRBar extends View {

    private Paint paint;
    private float snrRatio = 0f; // SNR ratio (0 to 1)

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
    }

    // Method to update the SNR ratio and redraw the bar
    public void updateSNR(float newSnrRatio) {
        // Ensure the ratio is between 0 and 1
        snrRatio = Math.max(0, Math.min(newSnrRatio, 1));
        invalidate(); // Trigger re-draw on UI thread
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Calculate the height of the filled portion of the bar based on SNR ratio
        int barHeight = getHeight();
        int filledHeight = (int) (barHeight * snrRatio);

        // Draw the background (empty part of the bar)
        paint.setColor(0xFFCCCCCC); // Light grey color for background
        canvas.drawRect(0, 0, getWidth(), barHeight, paint);

        // Set color based on SNR value
        if (snrRatio < 0.33f) {
            paint.setColor(0xFFFF0000); // Red for low SNR
        } else if (snrRatio < 0.66f) {
            paint.setColor(0xFFFFFF00); // Yellow for medium SNR
        } else {
            paint.setColor(0xFF00FF00); // Green for high SNR
        }

        // Draw the filled part of the bar representing the SNR
        canvas.drawRect(0, barHeight - filledHeight, getWidth(), barHeight, paint);
    }
}
