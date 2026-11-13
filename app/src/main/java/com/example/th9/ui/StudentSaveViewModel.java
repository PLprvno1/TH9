package com.example.th9.ui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.th9.data.AttendanceRepository;
import com.example.th9.data.Tasks;
import com.example.th9.data.StudentPhotos;
import com.example.th9.model.Student;

/** Keeps a submission alive across rotation without retaining the Activity. */
public class StudentSaveViewModel extends ViewModel {
    public static final class State {
        public final boolean saving;
        public final Student student;
        public final String error;
        State(boolean saving,Student student,String error){this.saving=saving;this.student=student;this.error=error;}
    }
    private final MutableLiveData<State> state=new MutableLiveData<>();
    public static final class PhotoState {
        public final boolean loading;
        public final String path,error;
        PhotoState(boolean loading,String path,String error){this.loading=loading;this.path=path;this.error=error;}
    }
    private final MutableLiveData<PhotoState> photo=new MutableLiveData<>();
    public LiveData<PhotoState> photo(){return photo;}
    public void importPhoto(android.content.Context applicationContext,android.net.Uri uri){
        if(photo.getValue()!=null&&photo.getValue().loading)return;
        photo.setValue(new PhotoState(true,null,null));
        Tasks.IO.execute(()->{
            try{photo.postValue(new PhotoState(false,StudentPhotos.importPhoto(applicationContext,uri),null));}
            catch(Exception e){photo.postValue(new PhotoState(false,null,e.getMessage()==null?"Không thể đọc ảnh":e.getMessage()));}
        });
    }
    public LiveData<State> state(){return state;}
    public void save(Student student,String classId,boolean editing){
        State previous=state.getValue();if(previous!=null&&(previous.saving||previous.student!=null))return;
        state.setValue(new State(true,null,null));
        Tasks.IO.execute(()->{
            try{
                AttendanceRepository repo=AttendanceRepository.getInstance();
                Student saved;
                if(editing){repo.updateStudent(student);saved=repo.getStudentById(student.getId());}
                else saved=repo.registerStudent(student,classId);
                state.postValue(new State(false,saved,null));
            }catch(Exception e){state.postValue(new State(false,null,e.getMessage()==null?"Không thể lưu hồ sơ":e.getMessage()));}
        });
    }
}
