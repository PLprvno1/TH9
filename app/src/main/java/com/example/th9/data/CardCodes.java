package com.example.th9.data;
import java.security.SecureRandom;
public final class CardCodes {
    private static final String ALPHABET="23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM=new SecureRandom();
    public static String create(){StringBuilder out=new StringBuilder("TDMU1-");for(int i=0;i<12;i++)out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));return out.toString();}
    public static boolean valid(String code){if(code==null||code.length()!=18||!code.startsWith("TDMU1-"))return false;
        for(int i=6;i<18;i++)if(ALPHABET.indexOf(code.charAt(i))<0)return false;return true;}
}
