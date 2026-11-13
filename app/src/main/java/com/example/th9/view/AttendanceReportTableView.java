package com.example.th9.view;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;
import com.example.th9.data.AttendanceReport;

import java.util.List;

/** The date header stays visible while the student rows scroll inside the table. */
public final class AttendanceReportTableView extends HorizontalScrollView {
    private final AttendanceReport.ClassTable table;

    public AttendanceReportTableView(Context context, AttendanceReport.ClassTable table) {
        super(context);
        this.table = table;
        setHorizontalScrollBarEnabled(true);
        setFillViewport(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int width = 0;
        for (int i = 0; i < table.headers.size(); i++) width += columnWidth(i);
        addView(content, new HorizontalScrollView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = createRow(true);
        bind(header, table.headers, true, false);
        content.addView(header, new LinearLayout.LayoutParams(width, dp(70)));
        if (!table.rows.isEmpty()) {
            RecyclerView body = new RecyclerView(context);
            body.setLayoutManager(new LinearLayoutManager(context));
            body.setNestedScrollingEnabled(true);
            body.setAdapter(new RowsAdapter());
            int visibleRows = Math.min(table.rows.size(), 7);
            content.addView(body, new LinearLayout.LayoutParams(width, dp(visibleRows * 48)));
        }
    }

    private int columnWidth(int column) {
        return dp(column == 0 ? 44 : column == 1 ? 136 : column == 2 ? 200 : 116);
    }

    private LinearLayout createRow(boolean header) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < table.headers.size(); i++) {
            TextView cell = new TextView(getContext());
            cell.setTextSize(header ? 12 : 13);
            cell.setGravity((i == 1 || i == 2 ? Gravity.START : Gravity.CENTER_HORIZONTAL) | Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(8), dp(4), dp(8), dp(4));
            cell.setMaxLines(header ? 3 : 1);
            cell.setEllipsize(TextUtils.TruncateAt.END);
            if (header || i >= 3) cell.setTypeface(null, Typeface.BOLD);
            row.addView(cell, new LinearLayout.LayoutParams(columnWidth(i), ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return row;
    }

    private void bind(LinearLayout row, List<String> cells, boolean header, boolean alternate) {
        for (int i = 0; i < cells.size(); i++) {
            TextView cell = (TextView) row.getChildAt(i);
            String value = cells.get(i);
            cell.setText(value);
            cell.setContentDescription(header ? value : table.headers.get(i).replace('\n', ' ') + ": "
                    + (i < 3 ? value : "x".equals(value) ? "Có mặt" : "t".equals(value) ? "Đi trễ" : "Trống"));
            int color = header ? R.color.on_primary : i >= 3 && "x".equals(value) ? R.color.status_present
                    : i >= 3 && "t".equals(value) ? R.color.status_late : R.color.text_primary;
            cell.setTextColor(ContextCompat.getColor(getContext(), color));
            GradientDrawable background = new GradientDrawable();
            background.setColor(ContextCompat.getColor(getContext(), header ? R.color.primary
                    : alternate ? R.color.surface_variant : R.color.surface));
            background.setStroke(dp(1), ContextCompat.getColor(getContext(), R.color.divider));
            cell.setBackground(background);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class RowsAdapter extends RecyclerView.Adapter<RowHolder> {
        @NonNull @Override public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LinearLayout row = createRow(false);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            return new RowHolder(row);
        }
        @Override public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            bind((LinearLayout) holder.itemView, table.rows.get(position), false, position % 2 == 1);
        }
        @Override public int getItemCount() { return table.rows.size(); }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        RowHolder(LinearLayout row) { super(row); }
    }
}
