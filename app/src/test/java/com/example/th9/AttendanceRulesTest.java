package com.example.th9;

import com.example.th9.data.AttendanceRules;
import com.example.th9.data.CsvTransfer;
import org.junit.Test;
import java.io.StringReader;
import java.util.List;
import static org.junit.Assert.*;

public class AttendanceRulesTest {
    @Test public void lateBoundaryUsesWholeMinutes(){
        long threshold=100*60000L;
        assertEquals("PRESENT",AttendanceRules.statusAt(threshold-1,threshold));
        assertEquals("PRESENT",AttendanceRules.statusAt(threshold,threshold));
        assertEquals("PRESENT",AttendanceRules.statusAt(threshold+59999,threshold));
        assertEquals("LATE",AttendanceRules.statusAt(threshold+60000,threshold));
        assertEquals("PRESENT",AttendanceRules.statusAt(threshold+60000,null));
    }
    @Test public void csvPreservesZerosUnicodeCommasQuotesNewlinesAndOpaqueCard() throws Exception {
        List<CsvTransfer.Row> rows=CsvTransfer.parse(new StringReader("\uFEFFmssv,ho_ten,ma_the\r\n0001,\"Nguyễn, \"\"An\"\"\r\nBình\",SV:00010001\r\n"));
        assertEquals(1,rows.size());assertEquals("0001",rows.get(0).mssv);
        assertEquals("Nguyễn, \"An\"\r\nBình",rows.get(0).name);assertEquals("SV:00010001",rows.get(0).card);
    }
    @Test public void csvWithoutCardColumnDoesNotRequestCardChange() throws Exception {
        List<CsvTransfer.Row> rows=CsvTransfer.parse(new StringReader("mssv,ho_ten\n001,An\n"));
        assertFalse(rows.get(0).hasCardColumn);assertNull(rows.get(0).card);
    }
    @Test(expected=IllegalArgumentException.class) public void csvRejectsMissingHeader() throws Exception {
        CsvTransfer.parse(new StringReader("id,name\n001,An\n"));
    }
    @Test public void malformedRowIsPreviewError() throws Exception {
        assertNotNull(CsvTransfer.parse(new StringReader("mssv,ho_ten\n001,An,extra\n")).get(0).error);
    }
}
