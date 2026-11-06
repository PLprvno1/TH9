package com.example.th9.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceRecord;
import com.example.th9.model.Student;

import java.util.ArrayList;
import java.util.List;

public class AttendanceRecordAdapter extends RecyclerView.Adapter<AttendanceRecordAdapter.RecordViewHolder> {

    public interface OnRecordClickListener {
        void onRecordClick(AttendanceRecord record, Student student);
    }

    public interface OnRecordLongClickListener {
        void onRecordLongClick(AttendanceRecord record, Student student, View anchorView);
    }

    private final Context context;
    private final List<AttendanceRecord> records = new ArrayList<>();
    private final OnRecordClickListener clickListener;
    private final OnRecordLongClickListener longClickListener;
    private int selectedPosition = -1;

    public AttendanceRecordAdapter(Context context, OnRecordClickListener clickListener, OnRecordLongClickListener longClickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setRecords(List<AttendanceRecord> newRecords) {
        this.records.clear();
        if (newRecords != null) {
            this.records.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    public AttendanceRecord getRecordAt(int position) {
        if (position >= 0 && position < records.size()) {
            return records.get(position);
        }
        return null;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_attendance_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        AttendanceRecord record = records.get(position);
        Student s = AttendanceRepository.getInstance().snapshotStudent(record);

        if (s != null) {
            holder.tvName.setText(s.getFullName());
            holder.tvMssv.setText("MSSV: " + s.getMssv());
            holder.tvInitial.setText(s.getInitial());

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(s.getAvatarBgColor() != 0 ? s.getAvatarBgColor() : ContextCompat.getColor(context, R.color.primary));
            holder.flAvatar.setBackground(bg);
        } else {
            holder.tvName.setText("Sinh viên");
            holder.tvMssv.setText("");
            holder.tvInitial.setText("?");
        }

        // Source label
        if (record.getSource() != null) {
            holder.tvSource.setText("• " + record.getSourceDisplayName()+(record.getEditedAt()==null?"":"\nChỉnh: "+AttendanceRepository.format(record.getEditedAt(),"dd/MM HH:mm")+" · "+record.getNote()));
            holder.tvSource.setVisibility(View.VISIBLE);
        } else {
            holder.tvSource.setVisibility(View.GONE);
        }

        // Timestamp
        if (record.getCheckInTime() != null && !record.getCheckInTime().isEmpty()) {
            holder.tvTime.setText(record.getCheckInTime());
            holder.tvTime.setVisibility(View.VISIBLE);
        } else {
            holder.tvTime.setVisibility(View.GONE);
        }

        // Status badge styling
        switch (record.getStatus()) {
            case PRESENT:
                holder.tvStatusBadge.setText(R.string.result_filter_present);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_present);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_present_container));
                break;
            case LATE:
                holder.tvStatusBadge.setText(R.string.result_filter_late);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_late);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_late_container));
                break;
            case ABSENT:
                holder.tvStatusBadge.setText(R.string.result_filter_absent);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_absent);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_absent_container));
                break;
            case PENDING:
                holder.tvStatusBadge.setText(R.string.result_filter_unchecked);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_unchecked);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                break;
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onRecordClick(record, s);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            selectedPosition = holder.getAdapterPosition();
            if (longClickListener != null) {
                longClickListener.onRecordLongClick(record, s, v);
            }
            v.showContextMenu();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public static class RecordViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        FrameLayout flAvatar;
        TextView tvInitial, tvName, tvMssv, tvSource, tvStatusBadge, tvTime;

        public RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            flAvatar = itemView.findViewById(R.id.fl_record_avatar);
            tvInitial = itemView.findViewById(R.id.tv_record_initial);
            tvName = itemView.findViewById(R.id.tv_record_name);
            tvMssv = itemView.findViewById(R.id.tv_record_mssv);
            tvSource = itemView.findViewById(R.id.tv_record_source);
            tvStatusBadge = itemView.findViewById(R.id.tv_record_status_badge);
            tvTime = itemView.findViewById(R.id.tv_record_time);
            itemView.setOnCreateContextMenuListener(this);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(tvName.getText());
            menu.add(Menu.NONE, R.id.context_view_profile, 1, R.string.context_view_profile);
            menu.add(Menu.NONE, R.id.context_change_status, 2, R.string.context_change_status);
        }
    }
}

