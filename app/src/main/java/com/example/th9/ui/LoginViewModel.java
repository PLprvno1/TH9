package com.example.th9.ui;
import androidx.lifecycle.*;
import com.example.th9.data.*;
/** Retains authentication work across recreation without retaining an Activity or password. */
public class LoginViewModel extends ViewModel {
    static final class State {
        final boolean busy,success;final String error;
        State(boolean busy,boolean success,String error){this.busy=busy;this.success=success;this.error=error;}
    }
    final MutableLiveData<State> state=new MutableLiveData<>(new State(false,false,""));
    void submit(boolean setup,String username,String name,String password){
        State previous=state.getValue();if(previous!=null&&previous.busy)return;
        state.setValue(new State(true,false,""));AttendanceRepository repo=AttendanceRepository.getInstance();
        Tasks.IO.execute(()->{try{if(setup)repo.setupAdmin(username,name,password);else repo.login(username,password);state.postValue(new State(false,true,""));}
            catch(Exception e){state.postValue(new State(false,false,e.getMessage()==null?"Không thể đăng nhập":e.getMessage()));}});
    }
}
