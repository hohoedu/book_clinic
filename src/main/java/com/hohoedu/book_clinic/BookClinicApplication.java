package com.hohoedu.book_clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// READY로 방치된 결제를 정리하는 배치(PaymentCleanupJob)가 필요해서 켠다.
// 앱의 X 버튼·abandon 호출은 사용자가 강제 종료하거나 네트워크가 끊기면 서버에 닿지 못하므로,
// 클라이언트 신호에만 기대면 방치 건을 놓치는 경우가 반드시 생긴다.
@EnableScheduling
@SpringBootApplication
public class BookClinicApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookClinicApplication.class, args);
	}

}
