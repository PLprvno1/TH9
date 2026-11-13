package com.example.th9;
import com.example.th9.data.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class StudentCardRulesTest {
    @Test public void randomCodesHaveExactFormatAndNoDuplicates(){Set<String> values=new HashSet<>();for(int i=0;i<10000;i++){String code=CardCodes.create();assertTrue(code,CardCodes.valid(code));assertTrue(values.add(code));}}
    @Test public void codesAreOpaqueAndNotTrimmed(){String code=CardCodes.create();assertFalse(CardCodes.valid(code+" "));assertFalse(CardCodes.valid(code.toLowerCase(Locale.ROOT)));assertFalse(CardCodes.valid("TDMU1-OOOOOOOOOOOO"));assertFalse(CardCodes.valid(null));}
    @Test public void validDatesAndVariableCourseLength(){ProfileRules.validate("29/02/2004","2020–2027");ProfileRules.validate(null,null);ProfileRules.validate("01/01/2000","2022-2023");}
    @Test public void invalidDatesAndCoursesRejected(){for(String date:new String[]{"31/02/2000","01/01/2999","1/1/2000","abc"}){try{ProfileRules.validate(date,null);fail(date);}catch(IllegalArgumentException expected){}}
        for(String course:new String[]{"2026-2022","2022-2022","22-26","2022/2026"}){try{ProfileRules.validate(null,course);fail(course);}catch(IllegalArgumentException expected){}}}
}
