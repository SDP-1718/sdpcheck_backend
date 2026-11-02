package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class AttendanceStatisticsWorkbookWriter {
    private static final List<String> HEADERS = List.of(
            "회원 ID", "이름", "기수", "출석", "지각", "결석", "출석 대상 세션", "출석률(%)", "이탈주의 여부");

    public byte[] write(List<MemberAttendanceStatisticsItem> members) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("회원별 출석 통계");
            var font = workbook.createFont();
            font.setBold(true);
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFont(font);
            var header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                var cell = header.createCell(column, CellType.STRING);
                cell.setCellValue(HEADERS.get(column));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(column, 20 * 256);
            }
            sheet.setColumnWidth(0, 24 * 256);
            sheet.createFreezePane(0, 1);

            for (int index = 0; index < members.size(); index++) {
                var member = members.get(index);
                var row = sheet.createRow(index + 1);
                row.createCell(0, CellType.STRING).setCellValue(Long.toString(member.memberId()));
                row.createCell(1, CellType.STRING).setCellValue(member.name());
                row.createCell(2, CellType.NUMERIC).setCellValue(member.generation());
                row.createCell(3, CellType.NUMERIC).setCellValue(member.presentCount());
                row.createCell(4, CellType.NUMERIC).setCellValue(member.lateCount());
                row.createCell(5, CellType.NUMERIC).setCellValue(member.absentCount());
                row.createCell(6, CellType.NUMERIC).setCellValue(member.totalSessionCount());
                var rate = row.createCell(7, CellType.BLANK);
                if (member.attendanceRate() != null) {
                    rate.setCellValue(member.attendanceRate());
                }
                row.createCell(8, CellType.STRING).setCellValue(member.isAtRisk() == null
                        ? "집계 전" : member.isAtRisk() ? "주의" : "해당 없음");
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }
}
