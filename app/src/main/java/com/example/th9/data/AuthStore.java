package com.example.th9.data;
import android.content.SharedPreferences;
import com.example.th9.data.AttendanceDatabase.*;
import com.example.th9.model.Account;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static com.example.th9.data.AttendanceRules.require;
/** Called within repository transactions; cached identity is for detached UI reads only. */
final class AuthStore {
    private final Store dao;private final SharedPreferences prefs;
    private String token;
    private volatile Account cached;
    private volatile long validUntil;
    AuthStore(Store dao,SharedPreferences prefs){this.dao=dao;this.prefs=prefs;token=prefs==null?"":prefs.getString("session_token","");}
    static Account account(UserAccount u){return new Account(u.id,u.username,u.fullName,u.role,u.enabled);}
    private UserAccount validActor(){
        LocalLogin login=dao.login();if(token.isEmpty()||login==null||login.expiresAt<=System.currentTimeMillis())return null;
        if(!MessageDigest.isEqual(login.tokenHash.getBytes(StandardCharsets.UTF_8),PasswordHasher.tokenHash(token).getBytes(StandardCharsets.UTF_8)))return null;
        UserAccount user=dao.user(login.userId);return user!=null&&user.enabled?user:null;
    }
    void refresh(){UserAccount user=validActor();cached=user==null?null:account(user);LocalLogin login=dao.login();validUntil=login==null?0:login.expiresAt;}
    Account current(){return System.currentTimeMillis()<validUntil?cached:null;}
    Account requireCurrent(){Account user=current();require(user!=null,"Vui lòng đăng nhập lại");return user;}
    UserAccount actor(){UserAccount user=validActor();require(user!=null,"Vui lòng đăng nhập lại");return user;}
    void admin(){require("ADMIN".equals(actor().role),"Chỉ quản trị viên được thực hiện thao tác này");}
    boolean needsSetup(){return dao.users().isEmpty();}
    static String username(String value){String name=value==null?"":value.trim().toLowerCase(Locale.ROOT);require(name.matches("[a-z0-9._-]{3,40}"),"Tên đăng nhập gồm 3–40 ký tự a-z, số, dấu chấm, gạch dưới hoặc gạch ngang");return name;}
    static String fullName(String value){String name=value==null?"":value.trim();require(!name.isEmpty()&&name.length()<=120,"Nhập họ tên (tối đa 120 ký tự)");return name;}
    void setup(String name,String fullName,String password){
        require(needsSetup(),"Tài khoản quản trị đã được tạo");UserAccount user=new UserAccount();user.id=AttendanceRepository.id();user.username=username(name);user.fullName=fullName(fullName);user.role="ADMIN";PasswordHasher.set(user,password);dao.insertUser(user);startSession(user);
    }
    String login(String username,String password){
        UserAccount user=dao.username(username==null?"":username.trim().toLowerCase(Locale.ROOT));long now=System.currentTimeMillis();
        if(user==null||!user.enabled)return "Tên đăng nhập hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa";
        if(user.lockedUntil>now)return "Tạm khóa đăng nhập. Thử lại sau "+Math.max(1,(user.lockedUntil-now+999)/1000)+" giây";
        if(!PasswordHasher.matches(user,password)){
            user.failedAttempts++;if(user.failedAttempts>=5){user.lockedUntil=now+60000;user.failedAttempts=0;}dao.updateUser(user);return "Tên đăng nhập hoặc mật khẩu không đúng";
        }
        user.failedAttempts=0;user.lockedUntil=0;dao.updateUser(user);startSession(user);return null;
    }
    private void startSession(UserAccount user){
        String next=PasswordHasher.randomToken(32);LocalLogin login=new LocalLogin();login.userId=user.id;login.tokenHash=PasswordHasher.tokenHash(next);login.expiresAt=System.currentTimeMillis()+7L*24*60*60*1000;dao.saveLogin(login);
        if(prefs!=null)require(prefs.edit().putString("session_token",next).commit(),"Không thể lưu phiên đăng nhập");token=next;
    }
    void logout(){dao.clearLogin();token="";cached=null;validUntil=0;if(prefs!=null)prefs.edit().remove("session_token").commit();}
}
