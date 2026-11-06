package com.example.th9.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;

import java.util.ArrayList;
import java.util.List;

public class CsvPreviewAdapter extends RecyclerView.Adapter<CsvPreviewAdapter.CsvViewHolder> {

    public static class CsvRowItem {
        public int stt;
        public String mssv;
        public String name;
        public boolean isValid;
        public String errorMessage;

        public CsvRowItem(int stt, String mssv, String name, boolean isValid, String errorMessage) {
            this.stt = stt;
            this.mssv = mssv;
            this.name = name;
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    private final Context context;
    private final List<CsvRowItem> rows = new ArrayList<>();

    public CsvPreviewAdapter(Context context) {
        this.context = context;
    }

    public void setRows(List<CsvRowItem> newRows) {
        this.rows.clear();
        if (newRows != null) {
            this.rows.addAll(newRows);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CsvViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_csv_row, parent, false);
        return new CsvViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CsvViewHolder holder, int position) {
        CsvRowItem item = rows.get(position);
        holder.tvStt.setText(String.valueOf(item.stt));
        holder.tvMssv.setText(item.mssv != null && !item.mssv.isEmpty() ? item.mssv : "—");
        holder.tvName.setText(item.name);

        if (item.isValid) {
            holder.tvBadge.setText(item.errorMessage);
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_present);
            holder.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_present_container));
            holder.tvError.setVisibility(View.GONE);
        } else {
            holder.tvBadge.setText("Lỗi");
            holder.tvBadge.setBackgroundResource(R.drawable.bg_badge_absent);
            holder.tvBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_absent_container));
            holder.tvError.setText(item.errorMessage);
            holder.tvError.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class CsvViewHolder extends RecyclerView.ViewHolder {
        TextView tvStt, tvMssv, tvName, tvError, tvBadge;

        public CsvViewHolder(@NonNull View itemView) {
            super(itemView);
            tvStt = itemView.findViewById(R.id.tv_csv_stt);
            tvMssv = itemView.findViewById(R.id.tv_csv_mssv);
            tvName = itemView.findViewById(R.id.tv_csv_name);
            tvError = itemView.findViewById(R.id.tv_csv_error_reason);
            tvBadge = itemView.findViewById(R.id.tv_csv_status_badge);
        }
    }
}

