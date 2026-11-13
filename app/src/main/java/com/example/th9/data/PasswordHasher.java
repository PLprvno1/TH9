package com.example.th9.data;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
/** Salted PBKDF2, performed on the IO executor. No plaintext password is persisted. */
public final class PasswordHasher {
    private static final SecureRandom RANDOM=new SecureRandom();
    public static void set(AttendanceDatabase.UserAccount user,String password){
        AttendanceRules.require(password!=null&&password.length()>=8&&password.length()<=128,"Mật khẩu cần từ 8 đến 128 ký tự");
        user.salt=randomToken(16);user.algorithm="PBKDF2WithHmacSHA256";user.iterations=600000;
        try{SecretKeyFactory.getInstance(user.algorithm);}catch(NoSuchAlgorithmException e){user.algorithm="PBKDF2WithHmacSHA1";user.iterations=1300000;}
        user.passwordHash=derive(password,user.salt,user.algorithm,user.iterations);
    }
    public static boolean matches(AttendanceDatabase.UserAccount user,String password){
        if(password==null||password.length()>128)return false;
        return MessageDigest.isEqual(android.util.Base64.decode(user.passwordHash,android.util.Base64.NO_WRAP),android.util.Base64.decode(derive(password,user.salt,user.algorithm,user.iterations),android.util.Base64.NO_WRAP));
    }
    private static String derive(String password,String salt,String algorithm,int iterations){
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),android.util.Base64.decode(salt,android.util.Base64.NO_WRAP),iterations,256);
        try{return android.util.Base64.encodeToString(SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded(),android.util.Base64.NO_WRAP);}
        catch(GeneralSecurityException e){throw new IllegalStateException("Thiết bị không hỗ trợ bộ băm mật khẩu",e);}finally{spec.clearPassword();}
    }
    public static String randomToken(int bytes){byte[] value=new byte[bytes];RANDOM.nextBytes(value);return android.util.Base64.encodeToString(value,android.util.Base64.NO_WRAP);}
    public static String tokenHash(String token){try{return android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
