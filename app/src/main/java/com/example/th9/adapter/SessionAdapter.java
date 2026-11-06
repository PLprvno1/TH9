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
import com.example.th9.data.AttendanceRepository;
import com.example.th9.model.AttendanceSession;
import com.example.th9.model.ClassRoom;

import java.util.ArrayList;
import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(AttendanceSession session);
    }

    private final Context context;
    private final List<AttendanceSession> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;

    public SessionAdapter(Context context, OnSessionClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setSessions(List<AttendanceSession> newSessions) {
        this.sessions.clear();
        if (newSessions != null) {
            this.sessions.addAll(newSessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        AttendanceSession item = sessions.get(position);
        holder.tvTitle.setText(item.getTitle());

        ClassRoom cr = AttendanceRepository.getInstance().getClassById(item.getClassId());
        if (cr != null) {
            holder.tvClass.setText(AttendanceRepository.getInstance().sessionClassLabel(item));
        } else {
            holder.tvClass.setText("Lớp học");
        }

        holder.tvDateTime.setText(item.getStartTime() + " • " + item.getDate());

        // Status badge
        switch (item.getStatus()) {
            case OPEN:
                holder.tvStatusBadge.setText(R.string.session_status_active);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_present);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_present_container));

                int checked = AttendanceRepository.getInstance().getCheckedInTotal(item.getId());
                int total = item.getExpectedCount();
                int pct = total > 0 ? (checked * 100 / total) : 0;
                holder.tvStats.setText(checked + "/" + total + " đã điểm danh (" + pct + "%)");
                break;
            case DRAFT:
                holder.tvStatusBadge.setText(R.string.session_status_upcoming);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_unchecked);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
                holder.tvStats.setText("Sĩ số: " + item.getExpectedCount() + " SV");
                break;
            case CLOSED:
                holder.tvStatusBadge.setText(R.string.session_status_finished);
                holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_late);
                holder.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_late_container));

                int present = AttendanceRepository.getInstance().getPresentCount(item.getId());
                int late = AttendanceRepository.getInstance().getLateCount(item.getId());
                int absent = AttendanceRepository.getInstance().getAbsentCount(item.getId());
                holder.tvStats.setText("Có mặt " + (present + late) + "/" + item.getExpectedCount() + " • Vắng " + absent);
                break;
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSessionClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvStatusBadge, tvClass, tvDateTime, tvStats;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_session_title);
            tvStatusBadge = itemView.findViewById(R.id.tv_session_status_badge);
            tvClass = itemView.findViewById(R.id.tv_session_class);
            tvDateTime = itemView.findViewById(R.id.tv_session_datetime);
            tvStats = itemView.findViewById(R.id.tv_session_stats);
        }
    }
}

