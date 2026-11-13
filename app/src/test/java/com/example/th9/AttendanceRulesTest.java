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
    @Test public void csvPreservesUnicodeCommasQuotesNewlinesAndRegistrationFields() throws Exception {
        List<CsvTransfer.Row> rows=CsvTransfer.parse(new StringReader("\uFEFFho_ten,ngay_sinh,nien_khoa,gioi_tinh\r\n\"Nguyễn, \"\"An\"\"\r\nBình\",01/02/2004,2022-2026,nữ\r\n"));
        assertEquals(1,rows.size());assertEquals("Nguyễn, \"An\"\r\nBình",rows.get(0).name);
        assertEquals("01/02/2004",rows.get(0).birthDate);assertEquals("2022–2026",rows.get(0).course);assertEquals("Nữ",rows.get(0).gender);
    }
    @Test public void csvReadsRequiredRegistrationFields() throws Exception {
        List<CsvTransfer.Row> rows=CsvTransfer.parse(new StringReader("ho_ten,ngay_sinh,nien_khoa,gioi_tinh\nAn,01/01/2000,2022–2026,Nam\n"));
        assertEquals("An",rows.get(0).name);assertEquals("01/01/2000",rows.get(0).birthDate);
        assertEquals("2022–2026",rows.get(0).course);assertEquals("Nam",rows.get(0).gender);
    }
    @Test(expected=IllegalArgumentException.class) public void csvRejectsMissingHeader() throws Exception {
        CsvTransfer.parse(new StringReader("id,name\n001,An\n"));
    }
    @Test public void malformedRowIsPreviewError() throws Exception {
        assertNotNull(CsvTransfer.parse(new StringReader("ho_ten,ngay_sinh,nien_khoa,gioi_tinh\nAn,01/01/2000,2022-2026,Nam,extra\n")).get(0).error);
    }
}
