package com.example.th9.adapter;

import android.view.*;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.th9.R;
import com.example.th9.data.TeacherDirectory.Entry;
import com.example.th9.model.Account;
import java.util.*;

public final class TeacherAdapter extends RecyclerView.Adapter<TeacherAdapter.Holder> {
    public interface Actions {void assign(Account user);void edit(Account user);void toggle(Account user);}
    private final Actions actions;
    private final List<Entry> rows=new ArrayList<>();
    public TeacherAdapter(Actions actions){this.actions=actions;setStateRestorationPolicy(StateRestorationPolicy.PREVENT_WHEN_EMPTY);}
    public void setRows(List<Entry> values){rows.clear();rows.addAll(values);notifyDataSetChanged();}
    @Override public int getItemCount(){return rows.size();}
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type){return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_teacher_account,parent,false));}
    @Override public void onBindViewHolder(@NonNull Holder holder,int position){
        Entry entry=rows.get(position);Account user=entry.account;
        holder.name.setText(user.fullName);holder.username.setText(user.username);
        holder.status.setText(user.enabled?R.string.teachers_active:R.string.teachers_locked);
        holder.status.setBackgroundResource(user.enabled?R.drawable.bg_badge_present:R.drawable.bg_badge_unchecked);
        holder.status.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),user.enabled?R.color.on_status_present_container:R.color.text_secondary));
        holder.classes.setText(holder.itemView.getContext().getString(R.string.teachers_classes,entry.classCount));
        holder.itemView.findViewById(R.id.teacher_assign).setOnClickListener(v->actions.assign(user));
        holder.itemView.findViewById(R.id.teacher_more).setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(v.getContext(),v);
            menu.getMenu().add(0,1,0,R.string.teachers_edit);
            menu.getMenu().add(0,2,1,user.enabled?R.string.teachers_lock:R.string.teachers_unlock);
            menu.setOnMenuItemClickListener(item->{if(item.getItemId()==1)actions.edit(user);else actions.toggle(user);return true;});menu.show();
        });
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name,username,status,classes;
        Holder(View view){super(view);name=view.findViewById(R.id.teacher_name);username=view.findViewById(R.id.teacher_username);status=view.findViewById(R.id.teacher_status);classes=view.findViewById(R.id.teacher_classes);}
    }
}
