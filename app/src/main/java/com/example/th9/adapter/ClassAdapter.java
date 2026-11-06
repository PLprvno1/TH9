package com.example.th9.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th9.R;
import com.example.th9.model.ClassRoom;

import java.util.ArrayList;
import java.util.List;

public class ClassAdapter extends RecyclerView.Adapter<ClassAdapter.ClassViewHolder> {

    public interface OnClassClickListener {
        void onClassClick(ClassRoom classRoom);
    }

    private final Context context;
    private final List<ClassRoom> classes = new ArrayList<>();
    private final OnClassClickListener listener;

    public ClassAdapter(Context context, OnClassClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setClasses(List<ClassRoom> newClasses) {
        this.classes.clear();
        if (newClasses != null) {
            this.classes.addAll(newClasses);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClassViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_class, parent, false);
        return new ClassViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClassViewHolder holder, int position) {
        ClassRoom item = classes.get(position);
        holder.tvName.setText(item.getName());
        holder.tvCode.setText(item.getCode());
        holder.tvNote.setText(item.getNote());
        holder.tvStudentCount.setText(item.getStudentCount() + " sinh viên");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClassClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return classes.size();
    }

    static class ClassViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCode, tvNote, tvStudentCount;

        public ClassViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_class_name);
            tvCode = itemView.findViewById(R.id.tv_class_code);
            tvNote = itemView.findViewById(R.id.tv_class_note);
            tvStudentCount = itemView.findViewById(R.id.tv_student_count_badge);
        }
    }
}

