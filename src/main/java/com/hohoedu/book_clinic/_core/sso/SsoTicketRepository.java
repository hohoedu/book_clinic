package com.hohoedu.book_clinic._core.sso;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 올패스 ↔ 호호책방 SSO 토큰 브릿지의 1회성 티켓(sso_ticket) 매퍼.
 * all_pass와 공유하는 dbhohoedu_stst DB에 있으며, DDL은 db/ddl-sso.sql 참고.
 */
@Mapper
public interface SsoTicketRepository {

    /** 토큰 발급 시 INSERT — 이 행이 있어야 콜백에서 소비(consume) 가능 */
    void insert(@Param("jti") String jti, @Param("issuer") String issuer,
                @Param("userId") String userId, @Param("redirectUrl") String redirectUrl,
                @Param("expiresAt") LocalDateTime expiresAt);

    /**
     * 원자적 소비 — used_yn=0 AND 만료 전인 행만 1로 바꾼다.
     * 영향받은 행이 1이면 최초 소비, 0이면 이미 쓰였거나(재사용 시도) 만료된 것이다.
     */
    int consume(@Param("jti") String jti);
}
