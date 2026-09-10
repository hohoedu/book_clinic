package com.hohoedu.book_clinic.app;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.app._dto.BookstoreMainRespDTO;

/**
 * i-with 앱 전용 조회. 화면 하나 = 쿼리 하나. 로직은 담지 않는다.
 * (도메인 서비스가 아니라 화면 조립용 read 이므로 app 패키지에 둔다.)
 */
@Mapper
public interface AppRepository {

    BookstoreMainRespDTO selectBookstoreMain(@Param("studentId") String studentId);
}
