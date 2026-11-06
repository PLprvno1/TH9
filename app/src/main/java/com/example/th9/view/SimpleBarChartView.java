package com.example.th9.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SimpleBarChartView extends View {

    public static class BarData {
        public String label;         // e.g. "Buổi 1"
        public int presentCount;    // Có mặt
        public int lateCount;       // Đi muộn
        public int absentCount;     // Vắng
        public int total;

        public BarData(String label, int presentCount, int lateCount, int absentCount) {
            this.label = label;
            this.presentCount = presentCount;
            this.lateCount = lateCount;
            this.absentCount = absentCount;
            this.total = Math.max(1, presentCount + lateCount + absentCount);
        }
    }

    private final List<BarData> dataList = new ArrayList<>();
    private final Paint paintPresent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintLate = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintAbsent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();

    public SimpleBarChartView(Context context) {
        super(context);
        init();
    }

    public SimpleBarChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimpleBarChartView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintPresent.setColor(Color.parseColor("#16A34A")); // Xanh lá
        paintLate.setColor(Color.parseColor("#D97706"));    // Hổ phách
        paintAbsent.setColor(Color.parseColor("#DC2626"));  // Đỏ

        paintText.setColor(Color.parseColor("#475569"));
        paintText.setTextSize(spToPx(11));
        paintText.setTextAlign(Paint.Align.CENTER);

        paintGrid.setColor(Color.parseColor("#E2E8F0"));
        paintGrid.setStrokeWidth(dpToPx(1));


    }

    public void setData(List<BarData> list) {
        dataList.clear();
        if (list != null) {
            dataList.addAll(list);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0 || dataList.isEmpty()) return;

        float paddingLeft = dpToPx(24);
        float paddingRight = dpToPx(16);
        float paddingTop = dpToPx(28);
        float paddingBottom = dpToPx(36);

        float chartHeight = height - paddingTop - paddingBottom;
        float chartWidth = width - paddingLeft - paddingRight;

        // Baseline
        float baselineY = height - paddingBottom;
        canvas.drawLine(paddingLeft, baselineY, width - paddingRight, baselineY, paintGrid);

        // 100% and 50% dotted grid lines
        paintGrid.setColor(Color.parseColor("#F1F5F9"));
        canvas.drawLine(paddingLeft, paddingTop, width - paddingRight, paddingTop, paintGrid);
        canvas.drawLine(paddingLeft, paddingTop + chartHeight / 2f, width - paddingRight, paddingTop + chartHeight / 2f, paintGrid);

        int barCount = dataList.size();
        float slotWidth = chartWidth / barCount;
        float barWidth = Math.min(dpToPx(36), slotWidth * 0.6f);

        for (int i = 0; i < barCount; i++) {
            BarData data = dataList.get(i);
            float centerX = paddingLeft + (i + 0.5f) * slotWidth;
            float left = centerX - barWidth / 2f;
            float right = centerX + barWidth / 2f;

            float presentHeight = chartHeight * ((float) data.presentCount / data.total);
            float lateHeight = chartHeight * ((float) data.lateCount / data.total);
            float absentHeight = chartHeight * ((float) data.absentCount / data.total);

            // Draw stacked bars from bottom up
            float currentBottom = baselineY;

            // 1. Present (bottom)
            if (data.presentCount > 0) {
                float top = currentBottom - presentHeight;
                rectF.set(left, top, right, currentBottom);
                canvas.drawRoundRect(rectF, dpToPx(4), dpToPx(4), paintPresent);
                currentBottom = top;
            }

            // 2. Late (middle)
            if (data.lateCount > 0) {
                float top = currentBottom - lateHeight;
                rectF.set(left, top, right, currentBottom);
                canvas.drawRect(rectF, paintLate);
                currentBottom = top;
            }

            // 3. Absent (top)
            if (data.absentCount > 0) {
                float top = currentBottom - absentHeight;
                rectF.set(left, top, right, currentBottom);
                canvas.drawRoundRect(rectF, dpToPx(4), dpToPx(4), paintAbsent);
            }

            // Label at bottom
            paintText.setColor(Color.parseColor("#475569"));
            paintText.setTextSize(spToPx(11));
            canvas.drawText(data.label, centerX, height - dpToPx(12), paintText);

            // Attendance % at top
            int attendancePct = (data.presentCount + data.lateCount) * 100 / data.total;
            paintText.setColor(Color.parseColor("#0F172A"));
            paintText.setTextSize(spToPx(10));
            canvas.drawText(attendancePct + "%", centerX, currentBottom - dpToPx(4), paintText);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }
}

