package com.example.th9;
import com.example.th9.data.TeacherDirectory;
import com.example.th9.data.TeacherDirectory.*;
import com.example.th9.model.Account;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class TeacherDirectoryTest {
    Account a=new Account("a","gv.alpha","Đặng Ánh","TEACHER",true);
    Account b=new Account("b","gv.beta","Bình","TEACHER",false);
    Account z=new Account("z","gv.zeta","Zed","TEACHER",true);
    List<Account> accounts=Arrays.asList(z,a,b);
    Map<String,Integer> counts=new HashMap<>();
    public TeacherDirectoryTest(){counts.put("a",2);counts.put("b",0);counts.put("z",1);}
    List<Entry> select(String q,Sort sort){return TeacherDirectory.select(accounts,counts,q,sort);}
    @Test public void searchMatchesVietnameseWithoutAccentsAndCase(){assertEquals("a",select(" DANG   anh ",Sort.NAME_ASC).get(0).account.id);assertEquals(1,select("ĐẶNG ÁNH",Sort.NAME_ASC).size());assertEquals("b",select("BINH",Sort.NAME_ASC).get(0).account.id);}
    @Test public void usernameSearchAndNoResults(){assertEquals("a",select("ALPHA",Sort.NAME_ASC).get(0).account.id);assertTrue(select("missing",Sort.NAME_ASC).isEmpty());assertEquals(3,select("  ",Sort.NAME_ASC).size());}
    @Test public void usernameOrderingWorksBothWays(){assertEquals("a",select("",Sort.USERNAME_ASC).get(0).account.id);assertEquals("z",select("",Sort.USERNAME_DESC).get(0).account.id);}
    @Test public void classCountsOrderingIncludesUnassignedTeachers(){assertEquals("a",select("",Sort.CLASSES_DESC).get(0).account.id);assertEquals("b",select("",Sort.CLASSES_ASC).get(0).account.id);}
    @Test public void sortByNameHasReverseOrder(){List<Entry> asc=select("",Sort.NAME_ASC),desc=select("",Sort.NAME_DESC);for(int i=0;i<asc.size();i++)assertEquals(asc.get(i).account.id,desc.get(desc.size()-i-1).account.id);}
    @Test public void filteringPreservesLockedAccountsAndInputOrder(){List<Entry> filtered=select("gv.beta",Sort.NAME_DESC);assertEquals(1,filtered.size());assertFalse(filtered.get(0).account.enabled);assertEquals("z",accounts.get(0).id);assertEquals(3,accounts.size());}
}
