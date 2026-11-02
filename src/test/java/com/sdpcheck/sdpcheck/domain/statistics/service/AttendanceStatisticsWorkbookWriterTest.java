package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AttendanceStatisticsWorkbookWriterTest {
    private static final List<String> HEADERS = List.of(
            "회원 ID", "이름", "기수", "출석", "지각", "결석", "출석 대상 세션", "출석률(%)", "이탈주의 여부");

    private final AttendanceStatisticsWorkbookWriter writer = new AttendanceStatisticsWorkbookWriter();

    @Test
    void createsExactlyOneHeaderOnlySheetForAnEmptyExport() throws Exception {
        byte[] content = writer.write(List.of());

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertEquals(1, workbook.getNumberOfSheets());
            var sheet = workbook.getSheet("회원별 출석 통계");
            assertNotNull(sheet);
            assertEquals(1, sheet.getPhysicalNumberOfRows());
            assertEquals(0, sheet.getLastRowNum());

            var header = sheet.getRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                assertEquals(CellType.STRING, header.getCell(column).getCellType());
                assertEquals(HEADERS.get(column), header.getCell(column).getStringCellValue());
            }
        }
    }

    @Test
    void preservesLongIdsWritesNumericCountsAndStoresFormulaLookingNamesAsText() throws Exception {
        List<MemberAttendanceStatisticsItem> members = List.of(
                item(Long.MAX_VALUE, "=SUM(1,1)", 17, 5, 1, 2, 8, 75, true),
                item(9_007_199_254_740_993L, "+1+1", 18, 0, 0, 0, 0, null, null),
                item(3L, "-1", 19, 2, 0, 1, 3, 67, false),
                item(4L, "@mention", 20, 1, 1, 0, 2, 100, false));

        byte[] content = writer.write(members);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("회원별 출석 통계");
            var first = sheet.getRow(1);
            assertEquals(CellType.STRING, first.getCell(0).getCellType());
            assertEquals(Long.toString(Long.MAX_VALUE), first.getCell(0).getStringCellValue());
            assertEquals(CellType.STRING, first.getCell(1).getCellType());
            assertEquals("=SUM(1,1)", first.getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC, first.getCell(2).getCellType());
            assertEquals(17.0, first.getCell(2).getNumericCellValue());
            assertEquals(5.0, first.getCell(3).getNumericCellValue());
            assertEquals(1.0, first.getCell(4).getNumericCellValue());
            assertEquals(2.0, first.getCell(5).getNumericCellValue());
            assertEquals(8.0, first.getCell(6).getNumericCellValue());
            assertEquals(75.0, first.getCell(7).getNumericCellValue());
            assertEquals(CellType.STRING, first.getCell(8).getCellType());
            assertEquals("주의", first.getCell(8).getStringCellValue());

            var unaggregated = sheet.getRow(2);
            assertEquals(CellType.STRING, unaggregated.getCell(0).getCellType());
            assertEquals("9007199254740993", unaggregated.getCell(0).getStringCellValue());
            assertEquals(CellType.STRING, unaggregated.getCell(1).getCellType());
            assertEquals("+1+1", unaggregated.getCell(1).getStringCellValue());
            assertEquals(CellType.BLANK, unaggregated.getCell(7).getCellType());
            assertEquals("집계 전", unaggregated.getCell(8).getStringCellValue());

            var atRiskFalse = sheet.getRow(3);
            assertEquals(CellType.STRING, atRiskFalse.getCell(1).getCellType());
            assertEquals("-1", atRiskFalse.getCell(1).getStringCellValue());
            assertEquals("해당 없음", atRiskFalse.getCell(8).getStringCellValue());

            var mention = sheet.getRow(4);
            assertEquals(CellType.STRING, mention.getCell(1).getCellType());
            assertEquals("@mention", mention.getCell(1).getStringCellValue());
            assertEquals(100.0, mention.getCell(7).getNumericCellValue());
        }
    }

    private MemberAttendanceStatisticsItem item(
            long memberId,
            String name,
            int generation,
            long presentCount,
            long lateCount,
            long absentCount,
            long totalSessionCount,
            Integer attendanceRate,
            Boolean isAtRisk
    ) {
        return new MemberAttendanceStatisticsItem(memberId, name, generation, presentCount, lateCount,
                absentCount, totalSessionCount, attendanceRate, isAtRisk);
    }
}
