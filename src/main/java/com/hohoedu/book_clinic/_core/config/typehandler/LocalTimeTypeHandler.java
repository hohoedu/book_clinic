package com.hohoedu.book_clinic._core.config.typehandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalTime;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

/**
 * TIME(0) 컬럼 ↔ java.time.LocalTime 변환기.
 *
 * MyBatis 기본 LocalTimeTypeHandler는 ps.setObject(i, LocalTime)을 그대로 호출하는데,
 * mssql-jdbc는 대상 타입을 모르는 setObject를 varbinary로 직렬화해버려
 * "Implicit conversion from data type varbinary to time is not allowed" 오류가 난다.
 * 그래서 java.sql.Time으로 명시 변환해서 넘긴다.
 *
 * TIME(0) 컬럼이라 Time.valueOf()가 버리는 나노초는 어차피 저장되지 않는다.
 */
@MappedTypes(LocalTime.class)
public class LocalTimeTypeHandler extends BaseTypeHandler<LocalTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTime(i, Time.valueOf(parameter));
    }

    @Override
    public LocalTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLocalTime(rs.getTime(columnName));
    }

    @Override
    public LocalTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLocalTime(rs.getTime(columnIndex));
    }

    @Override
    public LocalTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLocalTime(cs.getTime(columnIndex));
    }

    private LocalTime toLocalTime(Time time) {
        return time == null ? null : time.toLocalTime();
    }

}
