package com.example.th9.data;

import com.example.th9.model.Account;
import java.text.Collator;
import java.text.Normalizer;
import java.util.*;

/** Detached search/sort view of teacher accounts. Does not mutate accounts or assignments. */
public final class TeacherDirectory {
    public enum Sort { NAME_ASC,NAME_DESC,USERNAME_ASC,USERNAME_DESC,CLASSES_DESC,CLASSES_ASC }
    public static final class Entry {
        public final Account account;
        public final int classCount;
        public Entry(Account account,int classCount){this.account=account;this.classCount=classCount;}
    }
    private TeacherDirectory(){}
    private static String searchable(String value){
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
            .replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).trim();
    }
    public static List<Entry> select(List<Account> accounts,Map<String,Integer> counts,String query,Sort sort){
        String normalized=searchable(query);String[] words=normalized.isEmpty()?new String[0]:normalized.split("\\s+");
        List<Entry> result=new ArrayList<>();
        for(Account account:accounts){
            String haystack=searchable(account.fullName+" "+account.username);boolean matches=true;
            for(String word:words)if(!haystack.contains(word)){matches=false;break;}
            if(matches)result.add(new Entry(account,counts.getOrDefault(account.id,0)));
        }
        Collator vi=Collator.getInstance(new Locale("vi","VN"));vi.setStrength(Collator.SECONDARY);
        Comparator<Entry> order;
        switch(sort){
            case NAME_DESC:order=(a,b)->vi.compare(b.account.fullName,a.account.fullName);break;
            case USERNAME_ASC:order=(a,b)->a.account.username.compareToIgnoreCase(b.account.username);break;
            case USERNAME_DESC:order=(a,b)->b.account.username.compareToIgnoreCase(a.account.username);break;
            case CLASSES_DESC:order=Comparator.comparingInt((Entry e)->e.classCount).reversed();break;
            case CLASSES_ASC:order=Comparator.comparingInt(e->e.classCount);break;
            default:order=(a,b)->vi.compare(a.account.fullName,b.account.fullName);
        }
        result.sort(order.thenComparing(e->e.account.username,String.CASE_INSENSITIVE_ORDER).thenComparing(e->e.account.id));
        return result;
    }
}
