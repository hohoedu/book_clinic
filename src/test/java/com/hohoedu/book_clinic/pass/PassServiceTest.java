package com.hohoedu.book_clinic.pass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hohoedu.book_clinic._core.utils.KstClock;

/**
 * nextBillingYm() — 날짜 커트오프 없이 "이 학생이 가장 늦게까지 사둔 달"로 다음 결제 대상월을
 * 정하는 로직. 실제 오늘 날짜가 언제든 통과하도록 KstClock.today() 기준 상대 월로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PassServiceTest {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    @Mock
    private PassRepository passRepository;

    @InjectMocks
    private PassService passService;

    @Test
    void nextBillingYm_기존_이용권이_없으면_이번_달() {
        when(passRepository.findLatestValidUntil("S1", "BOOK")).thenReturn(null);

        String result = passService.nextBillingYm("S1", "BOOK");

        assertEquals(YearMonth.from(KstClock.today()).format(YM), result);
    }

    @Test
    void nextBillingYm_이미_이번달까지_샀으면_다음_달() {
        YearMonth current = YearMonth.from(KstClock.today());
        when(passRepository.findLatestValidUntil("S1", "BOOK")).thenReturn(current.atEndOfMonth());

        String result = passService.nextBillingYm("S1", "BOOK");

        assertEquals(current.plusMonths(1).format(YM), result);
    }

    @Test
    void nextBillingYm_이미_다음달까지_미리_사둔_상태에서_또_사면_그다음_달() {
        YearMonth current = YearMonth.from(KstClock.today());
        when(passRepository.findLatestValidUntil("S1", "BOOK")).thenReturn(current.plusMonths(1).atEndOfMonth());

        String result = passService.nextBillingYm("S1", "BOOK");

        assertEquals(current.plusMonths(2).format(YM), result);
    }

    @Test
    void nextBillingYm_가장_늦은_달이_이미_지났으면_공백기_무시하고_이번_달로_리셋() {
        YearMonth current = YearMonth.from(KstClock.today());
        when(passRepository.findLatestValidUntil("S1", "BOOK")).thenReturn(current.minusMonths(2).atEndOfMonth());

        String result = passService.nextBillingYm("S1", "BOOK");

        assertEquals(current.format(YM), result);
    }
}
