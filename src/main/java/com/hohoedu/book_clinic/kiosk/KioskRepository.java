package com.hohoedu.book_clinic.kiosk;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.hohoedu.book_clinic.kiosk._dto.KioskRespDTO;

/** 센터 기기 라이선스 매퍼 (2026-08-20) */
@Mapper
public interface KioskRepository {

    /** 발급 화면의 센터 선택용 — 본사 자신은 기기를 두지 않으므로 제외한다 */
    List<KioskRespDTO.CenterOptionDTO> findCenterOptions(@Param("hqCenterCode") String hqCenterCode);

    /** 본사 화면용 전체 목록(폐기 제외). centerCode가 비면 전 센터 */
    List<KioskRespDTO.LicenseDTO> findLicenses(@Param("centerCode") String centerCode);

    void insertLicense(@Param("centerCode") String centerCode,
                       @Param("keyHash") String keyHash,
                       @Param("label") String label,
                       @Param("issuedBy") String issuedBy,
                       @Param("deviceLimit") int deviceLimit);

    Integer findIdByHash(@Param("keyHash") String keyHash);

    /** 등록 시 키 확인용 — 폐기되지 않은 라이선스만. device_limit도 함께 읽는다 */
    KioskRespDTO.LicenseDTO findActiveLicenseByHash(@Param("keyHash") String keyHash);

    /** 이 키로 지금 등록되어 있는 기기 수 — 한도 검사 기준 */
    int countActiveDevices(@Param("licenseId") Integer licenseId);

    void insertDevice(@Param("licenseId") Integer licenseId,
                      @Param("tokenHash") String tokenHash,
                      @Param("userAgent") String userAgent);

    /**
     * 기기 토큰으로 소속 센터를 찾는다. 인터셉터가 매 요청 호출하는 경로 —
     * 기기와 라이선스 둘 다 폐기되지 않았을 때만 값이 나온다.
     */
    KioskRespDTO.ResolvedDTO findActiveDeviceByHash(@Param("tokenHash") String tokenHash);

    List<KioskRespDTO.DeviceDTO> findDevices(@Param("licenseId") Integer licenseId);

    void touchDeviceUsed(@Param("deviceId") Integer deviceId);

    int revokeDevice(@Param("deviceId") Integer deviceId, @Param("revokedBy") String revokedBy);

    int revoke(@Param("licenseId") Integer licenseId, @Param("revokedBy") String revokedBy);
}
