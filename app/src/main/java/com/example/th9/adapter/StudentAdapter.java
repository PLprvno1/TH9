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
import com.example.th9.model.Student;

import java.util.ArrayList;
import java.util.List;

public class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.StudentViewHolder> {

    public interface OnStudentClickListener {
        void onStudentClick(Student student, int position);
    }

    public interface OnStudentLongClickListener {
        void onStudentLongClick(Student student, int position, View anchorView);
    }

    public interface OnStudentDeleteListener { void onDelete(Student student); }
    private final OnStudentDeleteListener deleteListener;
    private final Context context;
    private final List<Student> students = new ArrayList<>();
    private final OnStudentClickListener clickListener;
    private final OnStudentLongClickListener longClickListener;
    private int selectedPosition = -1;

    public StudentAdapter(Context context, OnStudentClickListener clickListener, OnStudentLongClickListener longClickListener) {
        this(context,clickListener,longClickListener,null);
    }

    public StudentAdapter(Context context, OnStudentClickListener clickListener, OnStudentLongClickListener longClickListener, OnStudentDeleteListener deleteListener) {
        this.deleteListener=deleteListener;
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    public void setStudents(List<Student> newStudents) {
        this.students.clear();
        if (newStudents != null) {
            this.students.addAll(newStudents);
        }
        notifyDataSetChanged();
    }

    public Student getStudentAt(int position) {
        if (position >= 0 && position < students.size()) {
            return students.get(position);
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
    public StudentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_student, parent, false);
        return new StudentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StudentViewHolder holder, int position) {
        Student s = students.get(position);
        boolean canDelete=deleteListener!=null&&com.example.th9.data.AttendanceRepository.getInstance().isAdmin();
        View delete=holder.itemView.findViewById(R.id.btn_student_delete);
        delete.setVisibility(canDelete?View.VISIBLE:View.GONE);
        delete.setOnClickListener(canDelete?v->deleteListener.onDelete(s):null);
        holder.tvBarcodeBadge.setVisibility(canDelete?View.GONE:View.VISIBLE);
        holder.tvName.setText(s.getFullName());
        com.example.th9.model.ClassRoom main=com.example.th9.data.AttendanceRepository.getInstance().getClassById(s.getPrimaryClassId());
        holder.tvMssv.setText("MSSV: " + s.getMssv()+"\n"+(main==null?"Chưa chọn lớp chính":main.getName())+" • "+(s.getCourse()==null?"Chưa có khóa học":s.getCourse()));
        android.widget.ImageView photo=holder.flAvatar.findViewWithTag("photo");if(photo==null){photo=new android.widget.ImageView(context);photo.setTag("photo");photo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);holder.flAvatar.addView(photo,new FrameLayout.LayoutParams(-1,-1));}com.example.th9.data.StudentPhotos.show(photo,s.getPhotoPath());
        holder.tvInitial.setText(s.getInitial());

        // Avatar background color
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(s.getAvatarBgColor() != 0 ? s.getAvatarBgColor() : ContextCompat.getColor(context, R.color.primary));
        holder.flAvatar.setBackground(bg);

        // Barcode badge
        if (s.getMssv()!=null && !s.getMssv().isEmpty()) {
            holder.tvBarcodeBadge.setText("Thẻ sẵn sàng");
            holder.tvBarcodeBadge.setBackgroundResource(R.drawable.bg_badge_present);
            holder.tvBarcodeBadge.setTextColor(ContextCompat.getColor(context, R.color.on_status_present_container));
            holder.tvBarcodeBadge.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_barcode, 0, 0, 0);
        } else {
            holder.tvBarcodeBadge.setText("Chưa có thẻ điện tử");
            holder.tvBarcodeBadge.setBackgroundResource(R.drawable.bg_badge_unchecked);
            holder.tvBarcodeBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            holder.tvBarcodeBadge.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onStudentClick(s, holder.getAdapterPosition());
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            selectedPosition = holder.getAdapterPosition();
            if (longClickListener != null) {
                longClickListener.onStudentLongClick(s, holder.getAdapterPosition(), v);
            }
            v.showContextMenu();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return students.size();
    }

    public static class StudentViewHolder extends RecyclerView.ViewHolder implements View.OnCreateContextMenuListener {
        FrameLayout flAvatar;
        TextView tvInitial, tvName, tvMssv, tvBarcodeBadge;

        public StudentViewHolder(@NonNull View itemView) {
            super(itemView);
            flAvatar = itemView.findViewById(R.id.fl_avatar);
            tvInitial = itemView.findViewById(R.id.tv_avatar_initial);
            tvName = itemView.findViewById(R.id.tv_student_name);
            tvMssv = itemView.findViewById(R.id.tv_student_mssv);
            tvBarcodeBadge = itemView.findViewById(R.id.tv_barcode_badge);
            itemView.setOnCreateContextMenuListener(this);
        }

        @Override
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
            menu.setHeaderTitle(tvName.getText());
            menu.add(Menu.NONE, R.id.context_view_profile, 1, R.string.context_view_profile);
            if(com.example.th9.data.AttendanceRepository.getInstance().isAdmin())menu.add(Menu.NONE, R.id.context_edit_student, 2, R.string.context_edit_student);
            menu.add(Menu.NONE,7401,3,"Xem thẻ sinh viên");
            if(com.example.th9.data.AttendanceRepository.getInstance().isAdmin() && v.getContext() instanceof com.example.th9.ui.StudentsActivity){menu.add(Menu.NONE,7402,4,"Xóa sinh viên");}
            else if(com.example.th9.data.AttendanceRepository.getInstance().isAdmin())menu.add(Menu.NONE,7003,4,"Gỡ khỏi lớp");
        }
    }
}

