#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
docs/정책_및_플로우차트_전체.drawio 생성기.

정책이 바뀌면 아래 페이지 정의를 고치고 다시 실행한다:
    python3 docs/tools/policy2drawio.py
"""
"""book_clinic 전체 정책/플로우차트 draw.io 생성기"""
import html

# ── 스타일 팔레트 ────────────────────────────────────────────
S = {
    "start":  "rounded=1;arcSize=50;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=13;fontStyle=1;",
    "end":    "rounded=1;arcSize=50;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=13;fontStyle=1;",
    "stop":   "rounded=1;arcSize=50;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;fontSize=12;",
    "proc":   "rounded=0;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontSize=12;",
    "proc2":  "rounded=0;whiteSpace=wrap;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;fontSize=12;",
    "dec":    "rhombus;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;fontSize=11;",
    "note":   "shape=note;whiteSpace=wrap;html=1;size=14;fillColor=#fff2cc;strokeColor=#d6b656;fontSize=11;align=left;verticalAlign=top;spacingLeft=8;spacingTop=4;",
    "policy": "rounded=0;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#999999;fontSize=11;align=left;verticalAlign=top;spacingLeft=10;spacingTop=6;",
    "actor":  "rounded=1;arcSize=20;whiteSpace=wrap;html=1;fillColor=#ffe6cc;strokeColor=#d79b00;fontSize=12;fontStyle=1;",
    "dom":    "rounded=0;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;fontSize=11;",
    "store":  "shape=cylinder3;boundedLbl=1;backgroundOutline=1;size=12;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;fontSize=11;",
    "title":  "text;html=1;fontSize=20;fontStyle=1;align=left;verticalAlign=middle;",
    "sub":    "text;html=1;fontSize=12;align=left;verticalAlign=middle;fontColor=#666666;",
    "hdr":    "rounded=0;whiteSpace=wrap;html=1;fillColor=#6c8ebf;strokeColor=#3d5a80;fontColor=#ffffff;fontSize=13;fontStyle=1;",
}
E = {
    "":     "rounded=0;orthogonalEdgeStyle=orthogonalEdgeStyle;html=1;fontSize=11;exitX=0.5;exitY=1;exitDx=0;exitDy=0;entryX=0.5;entryY=0;entryDx=0;entryDy=0;",
    "free": "rounded=0;orthogonalEdgeStyle=orthogonalEdgeStyle;html=1;fontSize=11;",
    "no":   "rounded=0;orthogonalEdgeStyle=orthogonalEdgeStyle;html=1;fontSize=11;strokeColor=#b85450;fontColor=#b85450;",
    "yes":  "rounded=0;orthogonalEdgeStyle=orthogonalEdgeStyle;html=1;fontSize=11;strokeColor=#82b366;fontColor=#2d6a2d;",
    "dash": "rounded=0;orthogonalEdgeStyle=orthogonalEdgeStyle;html=1;fontSize=11;dashed=1;strokeColor=#999999;",
}

def esc(t):
    return html.escape(t, quote=True).replace("\n", "&#10;")

class Page:
    def __init__(self, name, w=1400, h=1900):
        self.name, self.w, self.h = name, w, h
        self.cells, self.i = [], 0
        self.pos = {}
    def _id(self, hint):
        self.i += 1
        return "%s_%d" % (hint or "c", self.i)
    def n(self, nid, label, x, y, w, h, style="proc"):
        self.pos[nid] = (x, y, w, h)
        self.cells.append(
            '<mxCell id="%s_%s" value="%s" style="%s" vertex="1" parent="1">'
            '<mxGeometry x="%d" y="%d" width="%d" height="%d" as="geometry"/></mxCell>'
            % (self.key, nid, esc(label), S[style], x, y, w, h))
        return nid
    def e(self, src, tgt, label="", kind="", style=None, waypoints=None):
        st = style if style else E[kind]
        geo = '<mxGeometry relative="1" as="geometry">'
        if waypoints:
            geo += '<Array as="points">' + "".join(
                '<mxPoint x="%d" y="%d"/>' % (px, py) for px, py in waypoints) + '</Array>'
        geo += '</mxGeometry>'
        self.cells.append(
            '<mxCell id="%s" value="%s" style="%s" edge="1" parent="1" source="%s_%s" target="%s_%s">%s</mxCell>'
            % (self._id("e"), esc(label), st, self.key, src, self.key, tgt, geo))
    def title(self, t, sub=""):
        self.cells.append('<mxCell id="%s" value="%s" style="%s" vertex="1" parent="1">'
                          '<mxGeometry x="40" y="24" width="1200" height="30" as="geometry"/></mxCell>'
                          % (self._id("t"), esc(t), S["title"]))
        if sub:
            self.cells.append('<mxCell id="%s" value="%s" style="%s" vertex="1" parent="1">'
                              '<mxGeometry x="40" y="56" width="1240" height="20" as="geometry"/></mxCell>'
                              % (self._id("t"), esc(sub), S["sub"]))
    def spine(self, items, cx=460, y0=120, gap=48, w=280):
        """items: (id, style, label, height) 를 세로로 쌓고 순차 연결. 반환: id 리스트"""
        y = y0
        ids = []
        prev = None
        for it in items:
            nid, sty, lbl = it[0], it[1], it[2]
            hh = it[3] if len(it) > 3 else (90 if sty == "dec" else 50)
            ww = it[4] if len(it) > 4 else w
            self.n(nid, lbl, cx - ww // 2, y, ww, hh, sty)
            if prev:
                self.e(prev, nid)
            prev = nid
            ids.append(nid)
            y += hh + gap
        self.bottom = y
        return ids
    def xml(self, key):
        return ('  <diagram name="%s" id="%s">\n'
                '    <mxGraphModel dx="1400" dy="900" grid="1" gridSize="10" guides="1" tooltips="1" '
                'connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="%d" pageHeight="%d" '
                'math="0" shadow="0">\n      <root>\n        <mxCell id="0"/>\n'
                '        <mxCell id="1" parent="0"/>\n        %s\n      </root>\n'
                '    </mxGraphModel>\n  </diagram>\n'
                % (esc(self.name), key, self.w, self.h, "\n        ".join(self.cells)))

PAGES = []
def page(name, key, w=1400, h=1900):
    p = Page(name, w, h)
    p.key = key
    PAGES.append(p)
    return p

# ── 1. 시스템 개요 ───────────────────────────────────────────
p = page("00. 시스템 개요", "overview", 1500, 1150)
p.title("호호책방 독서클리닉 — 시스템 개요",
        "행위자 · 도메인 · 데이터 흐름 (기준일 2026-09-03)")

p.n("a1", "학생 (개인 폰 PWA)\nstudent-main / 문제풀이", 60, 130, 210, 70, "actor")
p.n("a2", "출석체크 키오스크\n/attendance/enter", 60, 230, 210, 70, "actor")
p.n("a3", "직원 / 관리자 웹\n실시간 모니터링·독서일지", 60, 330, 210, 70, "actor")
p.n("a4", "PG (KG이니시스)\n승인·망취소·환불", 60, 450, 210, 70, "actor")
p.n("a5", "서당 (all_pass)\n청구 연동", 60, 550, 210, 70, "actor")
p.n("a6", "배치 스케줄러\n노쇼·READY 정리", 60, 650, 210, 70, "actor")

p.n("d1", "payment\n결제·환불", 400, 130, 170, 60, "dom")
p.n("d2", "pass\n이용권 발급/차감", 400, 215, 170, 60, "dom")
p.n("d3", "reservation\n회차 예약·출석", 400, 300, 170, 60, "dom")
p.n("d4", "monitor\n세션(입퇴실)·모니터링", 400, 385, 170, 60, "dom")
p.n("d5", "clinic\n추천·채점·보상", 400, 470, 170, 60, "dom")
p.n("d6", "book\n도서·재고·대여", 400, 555, 170, 60, "dom")
p.n("d7", "question\n문제 은행", 400, 640, 170, 60, "dom")
p.n("d8", "diary\n독서일지", 400, 725, 170, 60, "dom")
p.n("d9", "priority\n권장도서 순위", 400, 810, 170, 60, "dom")
p.n("d10", "schedule\n운영 스케줄·회차 슬롯", 400, 895, 170, 60, "dom")

p.n("s1", "MS SQL Server\nerp_bookstore_*", 700, 240, 190, 70, "store")
p.n("s2", "Firestore\n모니터링 카드 실시간 푸시", 700, 400, 190, 70, "store")

p.n("f1", "핵심 흐름 (한 줄 요약)\n\n"
    "결제 → 이용권 발급 → 회차 예약 → 입실(이용권 차감·출석 확정)\n"
    "→ 도서 추천 + 대여 → 독서 → 기본 문제 채점 → 결과·보상\n"
    "→ 심화 문제 → (게이트 통과 시) 다음 책 → 퇴실(반납)",
    980, 130, 460, 130, "policy")

p.n("f2", "이 파일에 들어있는 것\n\n"
    "• 01 학생 전체 여정 (End-to-End)\n"
    "• 02 예약 정책 / 03 입실·퇴실 / 04 이용권\n"
    "• 05 도서 추천 / 06 추천 도서 선정 규칙 / 07 심화 게이트\n"
    "• 08 기본 문제 채점·재도전 / 09 심화 문제\n"
    "• 10 보상(등급·뱃지·카드·레벨) / 11 모니터링 상태 6종\n"
    "• 12 결제 승인 / 13 환불 규정 / 14 앱 홈 상태머신\n"
    "• 15 도서 대여·반납 / 16 정책 요약표",
    980, 290, 460, 200, "policy")

p.n("f3", "읽는 법\n\n"
    "초록 = 시작/정상 종료   빨강 = 차단(예외)\n"
    "주황 마름모 = 분기   파랑 = 처리   보라 = 외부 호출\n"
    "노랑 메모 = 정책 근거·주의사항",
    980, 520, 460, 110, "policy")

for a in ["a1", "a2", "a3"]:
    p.e(a, "d4", "", "dash", waypoints=[(340, 415)])
p.e("a4", "d1", "", "dash", waypoints=[(340, 160)])
p.e("a5", "d2", "", "dash", waypoints=[(340, 245)])
p.e("a6", "d3", "", "dash", waypoints=[(340, 330)])
for d in ["d1", "d3", "d5", "d7", "d9"]:
    p.e(d, "s1", "", "dash")
p.e("d4", "s2", "", "dash")

# ── 01. 학생 전체 여정 ───────────────────────────────────────
p = page("01. 학생 전체 여정 (End-to-End)", "journey", 1500, 1900)
p.title("학생 전체 여정 — 결제부터 퇴실까지",
        "각 단계의 상세는 뒤 페이지 참고. 모든 시각 판단은 KST(KstClock) 기준")

ids = p.spine([
    ("j0", "start", "학생 / 학부모", 50),
    ("j1", "proc", "① 결제 (PG 또는 서당 청구)\n→ 이용권(pass) 발급, billing_ym 단위", 62),
    ("j2", "proc", "② 회차 예약\n월 상한 = 그 달 이용권 total_count 합\n하루 최대 4회차", 74),
    ("j3", "proc", "③ 입실 (키오스크 또는 앱 로그인)\n예약 필수 · 회차 시간대(시작 10분 전~종료)\n입실 1회 = 그날 예약 회차 전부 ATTENDED", 86),
    ("j4", "proc", "④ 이용권 차감 = 그날 출석 회차 수만큼", 50),
    ("j5", "proc", "⑤ 도서 추천 + 대여 확정\n상한 = 2권 × 그날 출석 회차 수", 62),
    ("j6", "proc", "⑥ 독서 (권장 독서시간 초과 시 모니터링에 시간초과)", 50),
    ("j7", "proc", "⑦ 기본 문제(qlevel=01) 제출\n첫 제출이면 무조건 status=DONE", 62),
    ("j8", "dec", "합격선(전체 2/3)\n이상인가?", 90),
], cx=420, y0=130, gap=44, w=380)

p.n("j9a", "만점 → grade=KING (독서왕)\n뱃지 2 독서왕", 130, 1120, 250, 60, "end")
p.n("j9b", "합격 → grade=FRIEND (독서친구)\n뱃지 1 독서완료", 400, 1120, 250, 60, "end")
p.n("j9c", "불합격 → grade=RETRY (재도전 필요)\n뱃지 1 독서완료", 670, 1120, 250, 60, "stop")
p.e("j8", "j9a", "만점", "yes")
p.e("j8", "j9b", "합격", "yes")
p.e("j8", "j9c", "불합격", "no")

p.n("j10", "⑧ 보상 지급 (첫 제출 때만)\n독서 카드 NORMAL 1장 · 10장마다 RARE\n레벨 = 학년별 완독 권수로 재계산", 260, 1240, 380, 74, "proc")
for s in ["j9a", "j9b", "j9c"]:
    p.e(s, "j10", "", "free")

p.n("j11", "⑨ 재도전 / 틀린 문제 다시 풀기\n(결과·완료 화면 버튼에서만 진입)", 950, 1120, 300, 60, "proc2")
p.e("j9c", "j11", "재도전", "free")
p.e("j9b", "j11", "재도전", "free")
p.n("j11n", "재도전은 점수·등급·뱃지를 올리기만 한다.\n'틀린 문제 다시 풀기'는 아무것도 바꾸지 않는다.\nKING은 재도전 버튼 없음(심화만).", 950, 1205, 300, 80, "note")

p.n("j12", "⑩ 심화 문제(qlevel=02) — 합격선 없음\n풀면 심화완료(3), 만점이면 심화왕(4)", 260, 1370, 380, 62, "proc")
p.e("j10", "j12")

p.n("j13", "다음 책 받기 (앱 '책 추천받기')", 260, 1490, 380, 50, "dec")
p.n("j13d", "심화 게이트\n통과?", 700, 1473, 200, 84, "dec")
p.e("j12", "j13")
p.e("j13", "j13d", "", "free")
p.e("j13d", "j5", "통과 → ⑤로 (다음 책)", "yes", waypoints=[(1180, 1515), (1180, 703)])
p.n("j14", "차단: '심화 문제를 먼저 풀어야\n다음 책을 받을 수 있어요'", 1000, 1490, 260, 60, "stop")
p.e("j13d", "j14", "미통과", "no")

p.n("j15", "⑪ 퇴실 (QR 또는 직원 처리)\n대여 도서 반납 · 독서일지 out_time 기록", 260, 1620, 380, 62, "end")
p.e("j13", "j15", "그만 읽기", "free", waypoints=[(180, 1515), (180, 1651)])
p.n("j15n", "퇴실해도 PENDING 추천은 남는다 —\n재입실 시 같은 책을 다시 대여해 이어 읽는다.", 700, 1620, 400, 62, "note")

# ── 02. 예약 정책 ────────────────────────────────────────────
p = page("02. 예약 정책 (reserveOne)", "reservation", 1750, 1450)
p.title("회차 예약 — ReservationService.reserveOne",
        "학생 본인 예약 / 직원 대리 예약(reserveByAdmin) 모두 같은 검증을 탄다")

p.spine([
    ("r0", "start", "예약 요청 (slotInstanceId)", 50),
    ("r1", "proc2", "lockStudentForReservation\n= erp_student 행 UPDLOCK/HOLDLOCK\n같은 학생 동시 요청 직렬화", 74),
    ("r2", "dec", "슬롯이 존재하는가?", 80),
    ("r3", "dec", "아직 끝나지 않은 회차인가?\n(ends_at > now)", 90),
    ("r4", "dec", "그 달 예약 상한 미만인가?\n비취소 예약수 < 그 달 이용권 total_count 합", 100),
    ("r5", "dec", "그 날짜 예약이 4회차 미만인가?\n(MAX_SLOTS_PER_DAY = 4)", 96),
    ("r6", "dec", "슬롯 정원이 남았는가?\n(incrementReservedCount)", 90),
    ("r7", "proc", "예약 INSERT + 상태로그(RESERVED)\n날짜는 요청이 아니라 슬롯에서 가져옴", 62),
    ("r8", "end", "예약 완료 → 모니터링 '미입실' 카드 생성", 50),
], cx=430, y0=130, gap=40, w=400)

blk = [
    ("x2", "404 존재하지 않는 회차입니다", "r2", 0),
    ("x3", "400 이미 종료된 회차입니다", "r3", 1),
    ("x4", "400 해당 월 이용권이 없습니다 (상한 0)\n또는 그 달 예약 가능 횟수 초과", "r4", 2),
    ("x5", "400 하루 4회차까지만 예약할 수 있습니다", "r5", 3),
    ("x6", "400 마감되었습니다 (정원 초과)", "r6", 4),
]
ys = [p.pos["r2"][1], p.pos["r3"][1], p.pos["r4"][1], p.pos["r5"][1], p.pos["r6"][1]]
for i, (nid, lbl, src, k) in enumerate(blk):
    y = p.pos[src][1] + 5
    p.n(nid, lbl, 880, y, 300, 70, "stop")
    p.e(src, nid, "아니오", "no")

p.n("rn1", "월 상한은 홀드/차감을 하지 않는다\n예약 취소가 잦아 되돌림 비용이 커서, 실제 차감은\n입실 시점(enterSession)에만 일어난다.\n기준 달은 '오늘'이 아니라 슬롯의 service_date가 속한 달 —\n4주 일괄 예약이 월 경계를 넘어도 각자 자기 달 상한을 탄다.",
    1230, 339, 430, 130, "note")
p.n("rn2", "동시성 방어\n(student_id, service_date) 유니크 인덱스를 제거한 자리를\n학생 행 락이 대신 지킨다. 'N건까지'는 유니크로 표현 불가.\nUX_reservation_slot_student(같은 회차 중복예약)는 유지.",
    1230, 490, 430, 110, "note")

p.n("rb", "4주 일괄 예약 미리보기 (previewBatch)\n\n같은 요일·회차를 4주치 훑어 주차별 상태를 돌려준다\n  OPEN — 예약 가능\n  DAY_CONFLICT — 그날 이미 4회차\n  MONTH_FULL — 그 달 상한 소진(배치 내 앞선 OPEN도 누적 카운트)\n  NO_PASS — 그 달 이용권 없음\n  CLOSED / FULL — 종료된 회차 · 정원 마감",
    60, 1140, 480, 170, "policy")
p.n("rc", "취소 · 노쇼\n\n• 학생 취소 / 직원 대리 취소 → status=CANCELED + 로그\n• 노쇼 배치(ReservationNoShowJob): ends_at 지났는데\n  RESERVED로 남은 예약 → NOSHOW (SYSTEM, '미입실 자동 처리')\n• NOSHOW는 월 상한 계산에 포함된다(status <> CANCELED)\n• 입실 1회로 그날 예약이 전부 ATTENDED가 되므로\n  나중 회차가 노쇼로 잡히지 않는다",
    600, 1140, 460, 170, "policy")
p.n("rd", "예약 상태 전이\n\nRESERVED ──입실──▶ ATTENDED\n   │\n   ├──학생/직원 취소──▶ CANCELED\n   └──ends_at 경과(배치)──▶ NOSHOW",
    1090, 1140, 300, 170, "policy")

# ── 03. 입실 / 퇴실 ──────────────────────────────────────────
p = page("03. 입실 / 퇴실 (MonitorService)", "session", 1500, 1900)
p.title("입실 / 퇴실 — enterSession · exitSession",
        "입실·퇴실은 하루 1회. 하나의 @Transactional — 중간에 막히면 세션 insert까지 롤백된다")

p.n("hh1", "입실  enterSession(studentId)", 130, 105, 420, 34, "hdr")
p.spine([
    ("e0", "start", "입실 요청\n(키오스크 /attendance/enter 또는 앱 로그인)", 62),
    ("e1", "dec", "오늘 열린 세션이 있는가?", 80),
    ("e2", "proc", "clinic_session INSERT (ENTERED)", 46),
    ("e3", "dec", "오늘 예약(RESERVED/ATTENDED)이\n하나라도 있는가?", 90),
    ("e4", "dec", "이미 ATTENDED가 있는가?\n(= 같은 날 재로그인)", 90),
    ("e5", "dec", "지금이 예약 회차 이용 시간대인가?\n[회차 시작 - 10분, 회차 종료]", 96),
    ("e6", "proc", "그날 RESERVED 회차를 전부 ATTENDED로 전환\n+ 상태 로그", 62),
    ("e7", "proc", "countAttendedSlotsToday → 목표 차감 회차 수", 46),
    ("e8", "dec", "이용권 차감 성공?\n(consume 반환값 ≠ -1)", 90),
    ("e9", "proc", "ensureDiary — 독서일지 헤더 생성(멱등)", 46),
    ("e10", "end", "입실 완료 → Firestore 카드 '미입실' → '독서 중'", 50),
], cx=340, y0=155, gap=36, w=420)

p.e("e1", "e3", "있음(멱등 재사용)", "yes", waypoints=[(120, 290), (120, 460)])
p.e("e4", "e6", "예 — 시간대 검사 생략", "yes", waypoints=[(600, 700), (600, 880)])

p.n("ex3", "400 오늘 예약 내역이 없습니다.\n예약 후 이용해주세요.\n(2026-08-20 예약 필수 정책)", 640, 455, 300, 74, "stop")
p.e("e3", "ex3", "없음", "no")
p.n("ex5", "400 N회차는 HH:MM~HH:MM에\n이용 가능합니다", 640, 660, 300, 60, "stop")
p.e("e5", "ex5", "아니오", "no")
p.n("ex8", "400 이용권이 모두 소진되었습니다.\n재결제 후 이용해주세요.\n→ 트랜잭션 전체 롤백", 640, 1020, 300, 74, "stop")
p.e("e8", "ex8", "-1", "no")

p.n("en1", "입실 1회 = 그날 예약 전부 출석 (2026-08-28)\n하루 4회차까지 예약 가능한데 입실은 1회뿐이라,\n첫 입실 한 번으로 그날 RESERVED를 모두 ATTENDED로 올린다.\n두 번째 회차 시간대가 아직 아니어도 출석으로 친다.",
    960, 660, 400, 100, "note")
p.n("en2", "이용권은 '그날 출석 회차 수'만큼 깎는다.\n재입실 시엔 (목표 − 이미 차감분)만 추가로 깎는다.\n모자라면 있는 만큼만 깎고 입실은 허용,\n하나도 못 깎았고 남은 게 없으면 -1로 차단.",
    960, 790, 400, 100, "note")

# 퇴실
p.n("hh2", "퇴실  exitSession(studentId)", 130, 1340, 420, 34, "hdr")
p.spine([
    ("q0", "start", "퇴실 요청 (학생 QR / 직원 모니터링)", 46),
    ("q1", "proc", "studentSessionRegistry.clear\n→ 문제풀이 기기 강제 로그아웃", 58),
    ("q2", "dec", "오늘 열린 세션이 있는가?", 76),
    ("q3", "proc", "세션 EXITED 처리 + 대여 도서 반납\n+ 독서일지 out_time 기록(수동 보정값은 보존)", 62),
    ("q4", "end", "퇴실 완료 → Firestore 카드 '퇴실'", 46),
], cx=340, y0=1390, gap=30, w=420)
p.n("qx", "이미 퇴실됨 → 조용히 무시", 640, 1580, 260, 46, "policy")
p.e("q2", "qx", "없음", "no")
p.n("qn", "완독 여부와 무관하게 퇴실 시점에 반납한다 —\n안 그러면 그 사이 다른 학생이 재고를 못 쓴다.\n추천(PENDING)은 남으므로 재입실하면 같은 책을 다시 대여한다.",
    960, 1450, 400, 90, "note")

# ── 04. 이용권 ───────────────────────────────────────────────
p = page("04. 이용권(Pass) 정책", "pass", 1450, 1300)
p.title("이용권 — 발급 · 차감 · 상한",
        "PassService. 서비스 코드 'BOOK', 발급 출처 PG / SEODANG / FREE")

p.n("h1", "발급  grant(...)", 60, 110, 380, 34, "hdr")
p.n("g1", "PG 승인 성공 직후 결제 트랜잭션 안에서 호출\n(여기서 실패하면 결제도 함께 롤백 → 망취소)",
    60, 155, 380, 60, "proc")
p.n("g2", "insertPass(billing_ym, valid_from=1일, valid_until=말일, total_count)",
    60, 240, 380, 56, "proc")
p.e("g1", "g2")
p.n("g3", "billing_ym(몇 월치인지)은 호출부가 반드시 정해 넘긴다.\nPassService가 '오늘이 몇 월이니 이번 달'이라고 임의 판단하지 않는다 —\nPG는 prepare 때 화면에 보여준 달, 서당은 all_pass가 정한 청구월을 써야 한다.",
    60, 315, 380, 90, "note")

p.n("h2", "다음 청구월  nextBillingYm()", 500, 110, 400, 34, "hdr")
p.spine([
    ("b1", "dec", "이 서비스에서 산 이용권이 있는가?", 80),
    ("b2", "dec", "가장 늦은 유효월이\n이번 달보다 과거인가?", 90),
    ("b3", "end", "가장 늦은 유효월 + 1개월", 46),
], cx=700, y0=155, gap=36, w=340)
p.n("b0a", "이번 달", 940, 175, 160, 46, "end")
p.e("b1", "b0a", "없음", "no")
p.n("b0b", "이번 달 (공백기 리셋)", 940, 300, 180, 46, "end")
p.e("b2", "b0b", "예", "yes")
p.n("bn", "날짜 커트오프(예: 20일 기준)를 쓰지 않는 이유 —\n'이미 이번 달을 샀는데 20일 이전에 다음 달 걸 미리 사려는' 경우를\n못 걸러낸다. 그래서 실제로 뭘 샀는지를 본다.",
    500, 470, 400, 90, "note")

p.n("h3", "차감  consume(studentId, 'BOOK', sessionId, targetUnits)", 60, 560, 620, 34, "hdr")
p.spine([
    ("c1", "start", "입실(enterSession)에서 호출\ntargetUnits = 그날 출석 확정 회차 수", 62),
    ("c2", "proc", "toCharge = targetUnits − 오늘 이미 차감한 행 수", 46),
    ("c3", "dec", "toCharge ≤ 0 ?", 70),
    ("c4", "proc", "쓸 수 있는 이용권을 찾아 remain_count −1\n+ pass_use 1행 INSERT  (toCharge 회 반복)", 62),
    ("c5", "dec", "이번에 하나도 못 깎았고\n오늘 차감 이력도 없는가?", 90),
    ("c6", "end", "차감 완료 → 남은 횟수 반환", 46),
], cx=340, y0=610, gap=34, w=420)
p.n("c3y", "남은 횟수만 반환 (재입실·새로고침)", 640, 745, 280, 46, "end")
p.e("c3", "c3y", "예", "yes")
p.n("c5y", "-1 반환 → 호출부가 입실 차단", 640, 990, 280, 46, "stop")
p.e("c5", "c5y", "예", "no")

p.n("cn1", "불변식: pass_use 1행 = remain_count 1 감소.\n환불 로직이 pass_use 행 수(COUNT(*))에 의존하므로\n이 불변식을 깨면 안 된다 — units 컬럼 방식이 기각된 이유.",
    960, 610, 400, 90, "note")
p.n("cn2", "UQ_pass_use_daily(student_id, used_date) 유니크는 제거됐다.\n하루 N행 허용 = 하루 여러 회차 차감(2026-08-28).",
    960, 720, 400, 70, "note")
p.n("cn3", "이용권이 회차 수보다 모자라면 있는 만큼만 깎고\n입실 자체는 허용한다. 아예 없을 때만 막는다.",
    960, 810, 400, 70, "note")
p.n("cn4", "월 예약 상한 monthlyCapacity(dateInMonth)\n= 그 달과 유효기간이 겹치는 살아있는 이용권의\n  total_count 합 (remain이 아님)\n→ 그 달 이용권이 없으면 상한 0 = 예약 불가",
    960, 900, 400, 100, "policy")

# ── 05. 도서 추천 ────────────────────────────────────────────
p = page("05. 도서 추천 (recommendBook)", "recommend", 1550, 1750)
p.title("도서 추천 — ClinicService.recommendBook",
        "멱등 처리. 앱 '책 추천받기' 버튼은 심화 게이트 적용, 키오스크 입실은 미적용")

p.spine([
    ("m0", "start", "추천 요청", 46),
    ("m1", "dec", "PENDING 추천이 이미 있는가?\n(= 지금 읽는 중인 책)", 90),
    ("m2", "dec", "심화 게이트를 적용하는가?\n(앱 '책 추천받기'만 true)", 90),
    ("m3", "dec", "오늘 추천 수 < 2권 × 출석 회차 수 ?", 84),
    ("m4", "proc", "직전에 다 읽은 책 반납 (returnActiveLoanSafely)", 46),
    ("m5", "proc", "센터코드 · 학년(schoolyear) · 연도 확인", 46),
    ("m6", "proc", "pickWithFallback — 다음 후보 도서 선정\n(제외목록 triedItemIds 반영)", 62),
    ("m7", "dec", "후보가 있는가?", 76),
    ("m8", "dec", "재고 확보 성공?\n(reserveItemById 원자적 UPDATE)", 90),
    ("m9", "proc", "item_loan INSERT (대여 확정)\n+ recommend_log INSERT (status=PENDING)", 62),
    ("m10", "proc", "enterSession — 모니터링 카드 '미입실' → '입실'\nexitQuiz — 이전 책의 quiz_started_at 제거", 62),
    ("m11", "end", "추천 도서 카드 반환", 46),
], cx=400, y0=130, gap=36, w=420)

p.n("mp", "그 책 그대로 반환\n(재추천·재대여 없음)\n+ enterSession\n+ ensureActiveLoan(재고 있으면 재대여)", 850, 195, 300, 90, "end")
p.e("m1", "mp", "있음", "yes")

p.n("mg", "advancedGateBlocks 판정\n→ 06 페이지", 850, 340, 240, 50, "proc2")
p.e("m2", "mg", "예", "free")
p.n("mgx", "400 심화 문제를 먼저 풀어야\n다음 책을 받을 수 있어요", 1150, 335, 280, 60, "stop")
p.e("mg", "mgx", "차단", "no")
p.e("m2", "m3", "아니오", "yes")

p.n("mx3", "400 오늘 추천받을 수 있는 책\nN권을 모두 받으셨습니다", 850, 470, 280, 60, "stop")
p.e("m3", "mx3", "초과", "no")
p.n("mx7", "404 추천할 수 있는\n도서가 더 이상 없습니다", 850, 850, 280, 60, "stop")
p.e("m7", "mx7", "없음", "no")
p.n("m8n", "그 item_id를 제외 목록에 넣고\n다음 후보로 (경합 = 그 사이 소진)", 850, 975, 300, 60, "proc")
p.e("m8", "m8n", "실패", "no")
p.e("m8n", "m6", "재시도 루프", "free", waypoints=[(1200, 1005), (1200, 779)])

p.n("n1", "추천 상한 = MAX_RECOMMENDATIONS_PER_SLOT(2) × 그날 ATTENDED 회차 수.\n하루 4회차면 최대 8권. recommend_log가 회차와 연결돼 있지 않아\n'회차별 정확히 2권'이 아니라 그날 총량으로만 제한한다(사용자 확정).",
    80, 1400, 470, 100, "note")
p.n("n2", "반납이 일어나는 시점은 이 세 곳뿐이다\n  1) 학생 QR 퇴실\n  2) 직원 퇴실 처리\n  3) 새 책 추천 (다 읽은 책을 여기서 놓아준다)\n완독(DONE) 자체는 반납을 유발하지 않는다 — 재도전 대비.",
    80, 1530, 470, 110, "policy")
p.n("n3", "추천 도서 교체 replaceRecommendedBook (2026-09-02)\n서가에 실제로 없거나 훼손된 책일 때 직원이 실행.\n일부러 @Transactional을 걸지 않았다 —\n  1) 취소 + 재고 차감 (없는 책이라는 사실은 그대로 유효)\n  2) 다음 책 추천 (실패할 수 있음)\n한 트랜잭션이면 2 실패 시 1까지 되돌아가 못 읽는 책이 다시 추천된다.",
    600, 1400, 500, 150, "policy")
p.n("n4", "생애 첫 로그인은 게이트를 타지 않고 즉시 추천한다\n(보여줄 '직전 책'이 없어 AWAITING_NEXT가 어색하므로).",
    600, 1580, 500, 90, "note")

# ── 06. 추천 도서 선정 규칙 ──────────────────────────────────
p = page("06. 추천 도서 선정 규칙 (pickNextItem)", "pick", 1300, 1150)
p.title("어떤 책을 고르는가 — 권장도서 순위 순회",
        "학년별 권장도서 순위(priority) 최상위부터 훑으며 조건에 처음 맞는 책을 확정한다")

p.spine([
    ("k0", "start", "학생 학년 · 소속 센터의\n권장도서 순위 최상위 책부터", 62),
    ("k1", "dec", "이 책(content)을 이미 읽었는가?\n※ item이 아니라 content 기준", 90),
    ("k2", "dec", "지금 대여 가능한 재고가 있는가?", 84),
    ("k3", "dec", "직전에 읽은 책과\n분류가 동일한가?", 90),
    ("k4", "dec", "직전에 읽은 책과\n장르가 동일한가?", 90),
    ("k5", "end", "이 책을 추천 도서로 확정", 50),
], cx=380, y0=140, gap=44, w=340)

p.n("kn", "다음 순위 책으로 이동", 800, 500, 240, 50, "proc")
p.e("k1", "kn", "읽었음", "no")
p.e("k2", "kn", "없음", "no")
p.e("k3", "kn", "동일", "no")
p.e("k4", "kn", "동일", "no")
p.e("kn", "k1", "재검사", "free", waypoints=[(1100, 525), (1100, 235), (560, 235)])

p.n("kend", "순위를 다 훑어도 없으면\n→ 폴백(pickWithFallback) → 그래도 없으면\n404 '추천할 수 있는 도서가 더 이상 없습니다'",
    800, 620, 340, 90, "stop")
p.e("kn", "kend", "순위 소진", "free")

p.n("kp1", "제외 기준은 content_id (2026-08-28 복원)\n\n같은 책이 사본(item) 여러 권으로 등록돼 있으면,\nitem_id 기준으로 제외할 때 이미 읽은 책이 다른 판본으로\n다시 추천되어 같은 문제가 재출제됐다.",
    80, 880, 470, 130, "policy")
p.n("kp2", "연속 회피 규칙\n\n직전 독서 책과 '분류'·'장르'가 같으면 건너뛴다 —\n같은 결의 책이 연달아 나오지 않게 하는 규칙.\n순위를 다 돌아도 후보가 없으면 이 회피 조건이 완화된다(폴백).",
    600, 880, 470, 130, "policy")

# ── 07. 심화 게이트 ──────────────────────────────────────────
p = page("07. 심화 게이트 (advancedGateBlocks)", "advgate", 1300, 1120)
p.title("다음 책을 받으려면 직전 책의 심화를 풀어야 한다",
        "2026-08-31 도입. 개인 폰 앱 '책 추천받기'(POST /clinic/recommend)에서만 적용")

p.spine([
    ("v0", "start", "'책 추천받기' 클릭", 46),
    ("v1", "dec", "완독(DONE) 이력이 있는가?", 80),
    ("v2", "dec", "직전 완독일이 오늘인가?", 80),
    ("v3", "dec", "그 책에 심화(qlevel=02) 문항이\n등록돼 있는가?", 90),
    ("v4", "dec", "그 책 심화 제출이 1회 이상 있는가?", 84),
    ("v5", "stop", "차단 — 400\n'심화 문제를 먼저 풀어야 다음 책을 받을 수 있어요'", 62),
], cx=380, y0=140, gap=44, w=400)

p.n("vp", "게이트 통과 → 다음 책 추천 진행", 830, 400, 320, 50, "end")
p.e("v1", "vp", "없음 (생애 첫 책)", "yes")
p.e("v2", "vp", "아니오 (날이 바뀜)", "yes")
p.e("v3", "vp", "없음", "yes")
p.e("v4", "vp", "있음", "yes")

p.n("vn1", "날이 바뀌면 심화 미응시여도 통과시킨다 (사용자 확정 2026-08-31) —\n어제 못 푼 심화 때문에 오늘 첫 책을 못 받는 상황을 막는다.",
    830, 500, 420, 80, "note")
p.n("vn2", "출석체크 키오스크(/attendance/enter)는 이 판정을 호출하지 않는다 —\n버튼이 없는 기기라 막히면 학생이 빠져나갈 방법이 없다.\n키오스크 입실은 항상 다음 책을 추천한다(2026-08-28).",
    830, 600, 420, 90, "note")
p.n("vn3", "알려진 이슈\n\n불합격(RETRY) 학생이 결과 화면을 벗어나 일반 홈으로 가면\nAWAITING_NEXT('책 추천받기')로 떨어져, 재도전 진입 경로가\n결과 화면 재도전 버튼 하나뿐이다. 의도된 동작으로 보고 유지 중.",
    80, 870, 620, 130, "policy")

# ── 08. 기본 문제 채점 / 재도전 ──────────────────────────────
p = page("08. 기본 문제 채점·재도전 (qlevel=01)", "quiz", 1550, 1600)
p.title("기본 문제 제출 — ClinicService.submitQuiz (qlevel=01)",
        "2026-08-28 재확정 · 2026-09-02 보정. 첫 제출은 결과와 무관하게 status=DONE")

p.spine([
    ("z0", "start", "문제 제출 (answers, mode)", 46),
    ("z1", "proc", "문항 조회 + recommend_log 조회\npriorAttemptRounds로 첫 시도 여부 판정", 62),
    ("z2", "proc", "채점\n· 이번에 제출된 문항 → 새로 채점 + 풀이 이력 기록\n· 첫 시도의 미제출 문항 → 오답으로 기록(0 = 미제출)\n· 재제출에서 안 낸 문항 → 직전 정답 여부를 이어받아 합산", 86),
    ("z3", "proc", "passLine = ceil(전체 문항 × 2/3)\nfreshGrade = 불합격 RETRY / 합격 FRIEND / 만점 KING", 62),
    ("z4", "dec", "이번 제출은 어떤 모드인가?", 84),
], cx=430, y0=130, gap=40, w=520)

y = 640
p.n("f1", "FIRST — 첫 제출", 120, y, 300, 40, "hdr")
p.n("f2", "status = DONE (불합격이어도)\ncorrect_count(처음 점수) = 이번 값\nfinal_correct_count = 이번 값\ngrade = freshGrade", 120, y + 55, 300, 90, "proc")
p.n("f3", "뱃지 부여 (첫 시도 결과 기준)\n만점 → 2 독서왕 / 그 외 → 1 독서완료", 120, y + 165, 300, 60, "proc")
p.n("f4", "독서 카드 NORMAL 1장 지급\nNORMAL 누적이 10의 배수면 RARE 1장 추가", 120, y + 245, 300, 62, "proc")
p.n("f5", "레벨 재계산 + 독서일지 기록", 120, y + 327, 300, 46, "proc")
p.e("z4", "f1", "첫 제출", "free")
p.e("f1", "f2"); p.e("f2", "f3"); p.e("f3", "f4"); p.e("f4", "f5")

p.n("g1", "RETRY — 재도전", 470, y, 300, 40, "hdr")
p.n("g2", "최종 점수 = max(이번 점수, 기존 최종)\n처음 점수는 고정 — 절대 안 바뀜", 470, y + 55, 300, 62, "proc")
p.n("g3", "grade = higherGrade(기존, 이번)\nRETRY(0) < FRIEND(1) < KING(2) — 오를 때만", 470, y + 137, 300, 62, "proc")
p.n("g4", "등급이 실제로 올랐는가?", 470, y + 219, 300, 60, "dec")
p.n("g5", "기본 뱃지 교체\n(deleteBasicBadge → awardBookBadge)", 470, y + 299, 300, 56, "proc")
p.n("g6", "그대로 (뱃지 변화 없음)", 810, y + 225, 240, 46, "policy")
p.e("z4", "g1", "재도전", "free")
p.e("g1", "g2"); p.e("g2", "g3"); p.e("g3", "g4")
p.e("g4", "g5", "예", "yes")
p.e("g4", "g6", "아니오", "no")

p.n("w1", "WRONG_ONLY — 틀린 문제 다시 풀기", 1100, y, 340, 40, "hdr")
p.n("w2", "점수 · 등급 · 뱃지 · 카드\n어떤 것도 바꾸지 않는다\n\n화면에는 원래(고정) 등급을 그대로 보여주고,\n이번에 몇 개 맞혔는지와 남은 오답만 갱신한다",
    1100, y + 55, 340, 110, "policy")
p.e("z4", "w1", "WRONG_ONLY", "free")

p.n("nn1", "재도전은 '올라가기만' 한다 (2026-08-28 최종)\n독서친구 → 재도전 불합격처럼 내려가는 방향은 반영하지 않는다.\n등급이 오르면 기본 뱃지도 상위로 교체 —\n그래야 grade · 뱃지 · '독서왕 횟수'(grade='KING')가 항상 일치한다.",
    120, 1200, 470, 110, "note")
p.n("nn2", "첫 제출 불합격도 grade='RETRY'로 저장한다 (2026-09-02, 예전엔 null)\n'완독 성공' 판정은 grade != null 이 아니라 isPassGrade(KING/FRIEND)로 한다.\ngetQuizMode · getLastResult · submitQuiz · 프론트 renderCompletion 모두 이 기준.",
    620, 1200, 470, 110, "note")
p.n("nn3", "mode 파라미터는 WRONG_ONLY만 의미가 있다.\n그 외/생략이면 서버가 제출 회차로 FIRST/RETRY를 판단한다\n(클라이언트가 mode를 위조해도 첫 제출은 첫 제출).",
    1120, 1200, 400, 100, "note")
p.n("nn4", "불합격이어도 다음 입실 때 다음 책을 받는다.\n재도전은 결과 화면 / 완료 화면 버튼에서만 —\n책이 아직 손에 있을 때만 이어서 푼다.\nKING은 재도전 버튼이 없다(심화만 남음).",
    120, 1350, 470, 110, "policy")
p.n("nn5", "중복 뱃지 방어\n첫 시도의 미제출 문항까지 항상 로그를 남기는 이유 —\n실제 문항과 다른 qnum만 담은 '빈 제출'을 반복해\ncountPriorAttempts를 속이고 뱃지를 중복 획득하는 걸 막는다.",
    620, 1350, 470, 110, "note")

# ── 09. 심화 문제 ────────────────────────────────────────────
p = page("09. 심화 문제 (qlevel=02)", "adv", 1500, 1300)
p.title("심화 문제 — 합격선이 없다",
        "2026-09-02 사용자 확정: 풀기만 하면 심화완료, 만점이면 심화왕. 불합격 개념 자체가 없음")

p.spine([
    ("s0", "start", "심화 문제 제출 (qlevel=02)", 46),
    ("s1", "proc", "채점 + 풀이 이력 기록 (기본과 동일한 방식)", 46),
    ("s2", "proc", "passed = 항상 true, grade = null\n(등급·완독·레벨 개념이 없다)", 62),
    ("s3", "dec", "이번 제출은 어떤 모드인가?", 84),
], cx=400, y0=140, gap=40, w=420)

y = 560
p.n("a1", "첫 시도", 100, y, 280, 40, "hdr")
p.n("a2", "뱃지 부여\n만점 → 4 심화왕\n그 외 → 3 심화완료", 100, y + 55, 280, 70, "proc")
p.n("a3", "독서일지 심화 '처음 점수' 기록\n(advanced_correct_cnt — 최초 1회만)", 100, y + 145, 280, 60, "proc")
p.e("s3", "a1", "첫 시도", "free")
p.e("a1", "a2"); p.e("a2", "a3")

p.n("b1", "재도전", 430, y, 280, 40, "hdr")
p.n("b2", "최종 점수만 max로 갱신\n(advanced_final_correct_cnt)", 430, y + 55, 280, 60, "proc")
p.n("b3", "뱃지 업그레이드\n심화완료 → 심화왕 (오를 때만)", 430, y + 135, 280, 60, "proc")
p.e("s3", "b1", "재도전", "free")
p.e("b1", "b2"); p.e("b2", "b3")

p.n("c1", "틀린 문제 다시 풀기", 760, y, 280, 40, "hdr")
p.n("c2", "점수 · 뱃지 어떤 것도\n바꾸지 않는다", 760, y + 55, 280, 60, "policy")
p.e("s3", "c1", "WRONG_ONLY", "free")

p.n("d1", "결과 화면에 함께 내려주는 것\n\n• 현재 레벨 (안 채우면 HTML placeholder 'Lv. 2'가 그대로 노출)\n• 그 책의 심화 뱃지 (새로 받은 게 없어도 보상 칸 유지)\n• 기본 등급 · 기본 오답 목록\n  → 심화왕이어도 기본이 독서왕이 아니면\n    기본 '틀린 문제 다시 풀기' 버튼이 남아야 하므로\n• 그 책에서 이미 받은 카드 (2026-09-03, 카드 칸이 통째로 비지 않게)",
    1080, 560, 340, 190, "policy")

p.n("e1", "심화에는 커트라인이 없다 — 고쳐진 버그\n\n예전엔 awardAdvancedBadge에 correct < passLine 가드가 있어\n커트라인 미달이면 뱃지를 안 줬는데, 모니터링은 제출만 있으면\nADV_DONE으로 찍고 있었다 → '카드엔 심화완료인데 뱃지는 없는'\n불일치. 가드를 제거해 정리했다(두 메서드에서 passLine 파라미터도 삭제).",
    100, 1000, 560, 140, "note")
p.n("e2", "심화는 1회성 도전이다\n\n결과 화면의 심화 버튼은 첫 시도 후 사라진다(advancedAvailable) —\n기본 문제처럼 '등급을 올리는' 업그레이드 경로가 없다.\n심화는 카드도 주지 않는다(카드는 기본 첫 제출 때 책당 1장).",
    700, 1000, 560, 140, "note")

# ── 10. 보상 정책 ────────────────────────────────────────────
p = page("10. 보상 정책 (등급·뱃지·카드·레벨)", "reward", 1500, 1250)
p.title("보상 체계 — 등급 · 뱃지 · 독서 카드 · 레벨/칭호",
        "2026-07-27 전면 재편 → 2026-09-02 뱃지 4종화. EXP는 폐지됨")

p.n("h1", "등급  recommend_log.grade", 60, 110, 420, 34, "hdr")
p.n("t1", "RETRY   재도전 필요 — 합격선 미만 (rank 0)\nFRIEND  독서친구 — 합격선 이상 (rank 1)\nKING    독서왕 — 만점 (rank 2)\n\n합격선 = ceil(전체 문항 수 × 2/3)\n  12문항 → 8개 · 15문항 → 10개\n\n재도전으로 올라가기만 한다. 내려가지 않는다.",
    60, 155, 420, 175, "policy")

p.n("h2", "뱃지  erp_bookstore_badge (책마다 부여)", 540, 110, 420, 34, "hdr")
p.n("t2", "1  독서완료   기본 문제를 풀면 — 합격/불합격 무관\n2  독서왕     기본 만점\n3  심화완료   심화를 풀면 — 합격선 없음\n4  심화왕     심화 만점\n\n책(content)마다 기본 1개(1~2 택1) + 심화 1개(3~4 택1)\nstudent_badge PK에 content_id 포함\n첫 시도 결과로 정해지고, 재도전으로 올라가기만 한다",
    540, 155, 420, 175, "policy")

p.n("h3", "독서 카드  erp_bookstore_student_card", 1020, 110, 420, 34, "hdr")
p.n("t3", "NORMAL  완독한 책마다 1장 (책당 고정 1종)\nRARE    NORMAL 10장마다 추가 지급 (책과 무관)\n\n기본 문제 첫 제출(DONE) 시점에 지급\nCARD_SET_SIZE = 10\n\nNORMAL 10장 = 오프라인 실물 카드 1장\n시스템은 진행도·달성 표시까지만 — 실물 지급은 수동\n결과 화면 팝업은 정체를 가린다(??? + N/10장)",
    1020, 155, 420, 175, "policy")

p.n("h4", "레벨 / 칭호  erp_bookstore_level", 60, 380, 900, 34, "hdr")
p.n("t4", "레벨 = min(12, ⌊해당 학년 완독(DONE) 권수 ÷ 학년별 필요 권수⌋ + 1)\n\n· EXP 완전 폐지. student_info·exp_rule 테이블도 폐지 — 로그에서 매번 재계산한다\n· 단계 = 학생 학년(01~06), 각 단계마다 레벨 1~12 → 칭호 6단계 × 12 = 72종\n· 추천이 학생 학년 = 도서 학년을 맞춰주므로 학년이 섞이지 않는다\n  (진도가 빠르면 학생 학년 자체를 상위로 변경)\n· 레벨 규칙은 DB가 아니라 Java 상수 ClinicService.LEVEL_RULES (어드민 편집 화면이 없어서)",
    60, 425, 900, 145, "policy")

p.n("t5", "학년별 레벨업 필요 권수 / 단계명\n\n01 초1  8권   기초 — 읽는 즐거움을 알아가는 단계\n02 초2  8권   성장 — 책 속 지식과 생각을 모으며 성장하는 단계\n03 초3  5권   탐구 — 스스로 질문하고 파고들며 사고를 넓히는 단계\n04 초4  4권   심화 — 지식을 깊이 있게 이해하고 연결하는 단계\n05 초5  4권   통찰 — 어휘와 문해력으로 글의 본질을 읽어내는 단계\n06 초6  4권   마스터 — 폭넓은 사고로 독서를 완성하는 단계",
    1020, 380, 420, 190, "policy")

p.n("t6", "독서탐험 지도 — 학년별 연간 목표 완독 권수 (2026-08-25 확정)\n\n초1 96  ·  초2 96  ·  초3 60  ·  초4 50  ·  초5 50  ·  초6 50\n중등(07)은 별도 확정 전이라 초6과 같은 값을 임시 사용",
    60, 620, 900, 110, "policy")

p.n("w1", "주의 — 뱃지명은 아이콘 이미지에 그려진 문구와 반드시 같아야 한다\n결과 화면이 이미지와 이름을 나란히 보여주기 때문. 새 아트를 받으면 DB 이름도 맞춘다.",
    1020, 620, 420, 110, "note")

p.n("w2", "주의 — 등급명 ≠ 뱃지명\nrecommend_log.grade는 KING/FRIEND/RETRY 그대로이고,\n결과 화면 hero는 여전히 '독서친구 달성!'이라고 뜬다.\n뱃지만 '독서완료'다. 통일 여부 미정.",
    60, 780, 460, 120, "note")
p.n("w3", "아이콘 용량\n원본(1254×1254, 합 6.8MB)은 docs/images/badges-original/에 보관하고\n서비스용은 256×256로 축소(합 413KB).\n모니터링은 카드마다 뱃지를 그려 한 화면에 수십 장이 동시 로드된다 —\n원본 그대로면 태블릿에서 느려진다.",
    560, 780, 460, 120, "note")
p.n("w4", "gradeUpgraded 분기에는 '뱃지가 실제로 바뀔 때만 교체'하는 가드가 있다.\n불합격 → 독서친구는 등급만 오르고 뱃지는 그대로(둘 다 1번)라,\n가드가 없으면 '뱃지 획득!'이 또 뜬다.",
    1060, 780, 400, 120, "note")

p.n("w5", "보상 테이블 4개\n\nlevel (칭호 마스터)  ·  badge (뱃지 마스터)\nstudent_badge (부여 이력)  ·  student_card (카드 지급 이력)",
    60, 950, 460, 110, "policy")

# ── 11. 모니터링 카드 상태 ───────────────────────────────────
p = page("11. 모니터링 카드 상태 6종", "monitor", 1500, 1400)
p.title("실시간 모니터링 카드 상태 판정 — resolveCardStatus",
        "2026-08-28 확정. 위에서부터 먼저 걸리는 것이 이긴다 (우선순위 판정)")

p.spine([
    ("c0", "start", "예약 기준 카드 (센터·일자·회차로 스코핑)", 46),
    ("c1", "dec", "session_id 가 있는가?", 76),
    ("c2", "dec", "session_status = EXITED ?", 76),
    ("c3", "dec", "quiz_started_at 이 있는가?", 76),
    ("c4", "dec", "기본 status = DONE ?", 76),
    ("c5", "dec", "권장 독서시간을 초과했는가?\n(elapsed > readingTime)", 90),
    ("c6", "end", "독서 중  READING", 46),
], cx=380, y0=140, gap=44, w=400)

p.n("r1", "미입실  NOT_ENTERED", 760, 245, 280, 46, "policy")
p.e("c1", "r1", "없음", "no")
p.n("r2", "퇴실  EXITED", 760, 365, 280, 46, "policy")
p.e("c2", "r2", "예", "yes")
p.n("r3", "문제 푸는 중  QUIZ_IN_PROGRESS\nN회차 (기본) 또는 '심화'", 760, 480, 280, 60, "policy")
p.e("c3", "r3", "있음", "yes")
p.n("r5", "시간초과  TIME_OVER", 760, 750, 280, 46, "policy")
p.e("c5", "r5", "예", "no")

p.n("d1", "결과 상태 세분 (기본 DONE)", 1090, 590, 340, 34, "hdr")
p.n("d2", "심화 제출 이력이 있는가?", 1120, 640, 280, 60, "dec")
p.n("d3", "심화 만점 → 심화왕  ADV_KING\n그 외 → 심화완료  ADV_DONE", 1090, 725, 340, 56, "policy")
p.n("d4", "grade = KING  → 독서왕  KING\ngrade = FRIEND → 독서친구  FRIEND\n그 외          → 재도전 필요  RETRY_NEEDED", 1090, 810, 340, 70, "policy")
p.e("c4", "d2", "예", "yes")
p.e("d2", "d3", "있음", "yes")
p.e("d2", "d4", "없음", "no")

p.n("n1", "'결과 확인중'(result_viewed_at 기반)은 폐지됐다 —\n제출 즉시 결과 상태로 넘어간다.\nresult_viewed_at 컬럼과 호출은 no-op로 남아 있다.",
    120, 940, 440, 90, "note")
p.n("n2", "시간초과는 아직 제출 전(독서 중)일 때만 의미가 있다 —\n제출했다면 독서는 이미 끝난 것이므로 결과 상태가 이긴다.",
    600, 940, 440, 90, "note")
p.n("n3", "'문제 푸는 중' 회차 표기\nclinic_session.quiz_qlevel + markQuizStarted(studentId, qlevel).\n기본 = basicAttemptRounds + 1 = 현재 회차, 심화면 '심화'.",
    1080, 940, 400, 100, "note")
p.n("n4", "상단 chip 집계 (buildCounts)\n\nnotEntered / reading / quizInProgress / timeOver /\nretryNeeded / completed(KING·FRIEND·ADV_DONE·ADV_KING)\nEXITED는 별도 chip 없음.\n독서일지 미등록 카운트는 미입실 카드를 제외하고 센다.",
    120, 1060, 440, 130, "policy")
p.n("n5", "실시간 갱신 3중 경로\n\nFirestore 구독(주) + 30초 백업 폴링 + 브라우저 로컬 카운트업.\nMonitorSyncService.syncCard/toBookMaps는 필드를 수동 매핑한다 —\n새 필드를 빠뜨리면 실시간 갱신 때 그 값만 사라진다.\n(Firestore에는 java.time 타입을 그대로 넣을 수 없어 Map으로 평탄화)",
    600, 1060, 460, 130, "note")
p.n("n6", "센터별 스코핑\n\n로그인 직원의 centerCode 기준 자동 필터.\n예약 조회 + Firestore 문서의 centerCode + 구독 where 조건.\nsessionDate·centerCode 두 등가 필터라 복합 인덱스가 필요할 수 있다.",
    1080, 1060, 400, 130, "policy")

# ── 12. 결제 승인 ────────────────────────────────────────────
p = page("12. 결제 승인 (KG이니시스)", "payment", 1600, 1500)
p.title("결제 — prepare → 승인 → 확정",
        "원칙: 승인 이후 어떤 이유로든 확정에 실패하면 반드시 망취소한다")

p.spine([
    ("y0", "start", "결제 시작 prepare / prepareGroup", 46),
    ("y1", "proc", "주문번호 발급 + payment READY 행 선기록\n(이니시스 정산 내역과 대조하기 위해)", 62),
    ("y2", "proc", "결제창 → PG 승인 요청", 46),
    ("y3", "dec", "payment.status = READY 인가?", 76),
    ("y4", "dec", "PG 승인 성공?", 70),
    ("y5", "dec", "승인 금액 = 우리가 기록한 금액?", 80),
    ("y6", "proc", "confirmPaid — status=PAID, tid·paid_at 기록\n+ 같은 트랜잭션에서 이용권 grant(billing_ym)", 62),
    ("y7", "dec", "확정 성공?", 70),
    ("y8", "end", "결제 완료", 46),
], cx=400, y0=130, gap=38, w=440)

p.n("p1", "PAID면 그대로 재사용 (멱등)\nREADY도 PAID도 아니면 400", 900, 400, 300, 60, "policy")
p.e("y3", "p1", "아니오", "no")
p.n("p2", "confirmFailed — status=FAILED", 900, 500, 300, 46, "stop")
p.e("y4", "p2", "실패", "no")
p.n("p3", "즉시 망취소 → 실패로 종료\n(금액 불일치는 그 자체가 사고)", 900, 590, 300, 60, "stop")
p.e("y5", "p3", "불일치", "no")
p.n("p4", "망취소 후 실패 처리", 900, 760, 300, 46, "stop")
p.e("y7", "p4", "실패", "no")

p.n("m1", "망취소가 불가능한 경우 → 수동 취소 대상\n\n• 망취소 주소(netCancelUrl)가 없음\n• 망취소 호출 자체가 실패\n• 모바일 승인 — 되돌릴 망취소 수단이 아예 없다\n\n→ markNeedsReview(orderNo, 사유)\n   상점관리자에서 직접 취소해야 한다.\n   망취소는 실패해도 예외를 위로 올리지 않는다\n   (호출부는 이미 실패 경로를 타고 있다).",
    900, 850, 440, 180, "note")

p.n("g1", "형제 묶음 결제 (prepareGroup)\n\n• 선택된 학생마다 개별 READY 행 + 공통 group_order_no\n• 전원 PAID면 완료, 전원 READY면 승인 진행\n• 섞인 상태는 정상 그룹과 분리해 처리\n• 전부 READY로 방치되면 PaymentCleanupJob이 정리\n• 동시 승인 경합(같은 주문에 서로 다른 tid로 이중 승인)이\n  감지되면 방금 승인분을 즉시 망취소한다 —\n  그대로 두면 고객 카드에서 조용히 중복으로 돈이 빠진다",
    120, 1080, 500, 190, "policy")
p.n("g2", "결제 상태\n\nREADY   결제창 열림 (배치가 정리)\nPAID    승인·확정 완료 → 이용권 발급됨\nFAILED  승인 실패 / 금액 불일치 망취소\nCLOSED  사용자가 창을 닫음 (abandon)\n+ needs_review 플래그 — 수동 취소 필요",
    660, 1080, 440, 190, "policy")
p.n("g3", "이용권 발급은 승인 트랜잭션 안에서 일어난다.\ngrant가 실패하면 결제도 함께 롤백되고 망취소로 이어져야 한다 —\n'돈은 빠졌는데 이용권은 없는' 상태를 만들지 않기 위해서다.\nbilling_ym은 prepare 시점에 화면에 보여준 달을 그대로 쓴다.",
    1140, 1080, 420, 190, "note")

# ── 13. 환불 규정 ────────────────────────────────────────────
p = page("13. 환불 규정", "refund", 1500, 1550)
p.title("환불 — quote(견적) · refund(실행)",
        "규정에 없는 환불을 코드가 임의로 만들어내지 않는다. 견적과 실제 환불은 같은 계산기를 쓴다")

p.spine([
    ("f0", "start", "환불 요청 (앱 또는 관리자)", 46),
    ("f1", "proc2", "원자적 UPDATE로 환불 진행 락 선점\n(동시 요청 경합 방지 — markPaid의 WHERE status='READY'와 같은 원리)", 70),
    ("f2", "dec", "status = PAID 인가?", 76),
    ("f3", "dec", "이미 환불된 건인가?\n(refund_amount > 0)", 84),
    ("f4", "dec", "승인 정보(paid_at, tid)가 있는가?", 76),
    ("f5", "proc", "이용권 사용 횟수 조회\nusedCount = pass_use 행 수", 56),
    ("f6", "dec", "usedCount에 맞는 규정이 있는가?\n(priority 순으로 처음 맞는 규정 하나만)", 90),
    ("f7", "proc", "환불액 = 결제금액 × 규정 환불율 ÷ 100 (원 단위 절사)\n남은 한도(결제금액 − 기환불액)까지만", 62),
    ("f8", "proc", "openCancel → PG 취소 API 호출", 46),
    ("f9", "dec", "PG 취소 승인?", 70),
    ("f10", "end", "confirmRefunded — 환불 확정 + 이용권 회수(revoke)", 50),
], cx=420, y0=130, gap=34, w=480)

for src, msg, yy in [
    ("f2", "환불할 수 있는 결제가 아닙니다", None),
    ("f3", "이미 환불 처리된 결제입니다", None),
    ("f4", "승인 정보가 없어 환불할 수 없습니다", None),
]:
    y = p.pos[src][1]
    nid = "x" + src
    p.n(nid, "환불 불가\n" + msg, 940, y - 5, 300, 60, "stop")
    p.e(src, nid, "아니오" if src != "f3" else "예", "no")

y6 = p.pos["f6"][1]
p.n("xf6", "환불 불가\n'환불 가능 기간이 지났거나\n사용 횟수가 초과되었습니다'", 940, y6, 300, 74, "stop")
p.e("f6", "xf6", "없음", "no")
y9 = p.pos["f9"][1]
p.n("xf9", "confirmRefundFailed\n→ 거절 사유 그대로 노출", 940, y9 - 5, 300, 60, "stop")
p.e("f9", "xf9", "거절/오류", "no")

p.n("r1", "환불 규정 (2026-08-05 — 날짜 조건 폐지, 사용 횟수만으로 판정)\n\n  이용권 0회 사용 →  100% 환불\n  1회 사용        →   75% 환불\n  2회 사용        →   50% 환불\n  3회 이상        →  규정에 맞는 행이 없어 자연히 환불 불가\n\nusedDays(경과 일수)는 판정에 쓰지 않지만\npayment_cancel 감사 스냅샷에는 계속 남긴다.",
    120, 1260, 520, 180, "policy")
p.n("r2", "환불은 결제 1건당 1번만 (2026-08-05)\n\nrefund_amount가 조금이라도 있으면 그걸로 끝이다.\n부분환불(75%) 후 남은 잔액을 다시 계산해 또 내주면,\n두 번 요청하는 것만으로 사실상 100% 환불이 되어\n'1회 사용 시 75%까지만' 규정이 무의미해진다.",
    680, 1260, 460, 180, "note")
p.n("r3", "규정 테이블은 활성 규정을 priority 순으로 훑는다.\n조건에 처음 맞는 규정 하나만 적용하고 멈춘다.\n어디에도 안 걸리면 환불 불가다.",
    1180, 1260, 250, 180, "note")

# ── 14. 앱 홈 상태머신 ───────────────────────────────────────
p = page("14. 학생 앱 홈 상태머신", "homestate", 1500, 1150)
p.title("홈 상태 — 기기에 따라 다른 두 갈래",
        "2026-07-29 '입실'과 '책 추천'을 분리했다. 홈 진입 = 자동 추천이 아니다")

p.n("h1", "출석체크 키오스크  /attendance/enter", 80, 120, 560, 34, "hdr")
p.n("k1", "getHomeState(studentId)\n※ 항상 입실 처리(enterSession)한다", 80, 165, 560, 56, "proc")
p.n("k2", "PENDING 추천이 있는가?", 250, 245, 300, 70, "dec")
p.n("k3", "READING — 그 책 표시\n(ensureActiveLoan으로 대여 상태 복구)", 80, 345, 300, 60, "end")
p.n("k4", "완독(DONE) 이력이 있는가?", 250, 435, 300, 70, "dec")
p.n("k5", "AWAITING_NEXT — 직전 책 표시\n'책 추천받기' 버튼 대기", 250, 535, 300, 60, "end")
p.n("k6", "생애 첫 로그인\n→ 즉시 recommendBook → READING", 400, 345, 240, 74, "end")
p.e("k1", "k2"); p.e("k2", "k3", "있음", "yes"); p.e("k2", "k4", "없음", "no")
p.e("k4", "k5", "있음", "yes"); p.e("k4", "k6", "없음", "no")
p.n("kn", "키오스크 입실은 무조건 다음 책을 추천한다 (2026-08-28).\n예전엔 getHomeState를 불러 DONE이면 마지막 책만 보여줬는데,\n버튼이 없는 키오스크에는 맞지 않았다 — recommendBook으로 교체.\n(심화 게이트도 적용하지 않는다)",
    80, 640, 560, 110, "note")

p.n("h2", "개인 폰 앱 (문제풀이 기기)  /home-state", 780, 120, 620, 34, "hdr")
p.n("m1", "getQuizHomeState(studentId)\n※ 입실도, 책 추천도 하지 않는다", 780, 165, 620, 56, "proc")
p.n("m2", "오늘 이미 퇴실했는가?", 940, 245, 300, 70, "dec")
p.n("m3", "EXITED — '이미 퇴실했습니다'", 780, 345, 280, 50, "stop")
p.n("m4", "PENDING 추천이 있는가?", 940, 425, 300, 70, "dec")
p.n("m5", "READING — 문제 풀기", 780, 525, 280, 46, "end")
p.n("m6", "완독(DONE) 이력이 있는가?", 940, 605, 300, 70, "dec")
p.n("m7", "COMPLETED — 완료 화면\n(재도전 · 틀린 문제 · 심화 버튼)", 780, 705, 300, 60, "end")
p.n("m8", "NOT_ENTERED — 안내 메시지", 1120, 705, 280, 46, "stop")
p.e("m1", "m2"); p.e("m2", "m3", "예", "no"); p.e("m2", "m4", "아니오", "yes")
p.e("m4", "m5", "있음", "yes"); p.e("m4", "m6", "없음", "no")
p.e("m6", "m7", "있음", "yes"); p.e("m6", "m8", "없음", "no")

p.n("qn", "문제풀이 화면 진입 모드 getQuizMode — '나가기'를 누르면 어디로 보낼지 결정한다\n\n  FIRST       이 책+난이도 첫 시도 → 나가면 일반 홈\n  RETRY       불합격 후 재도전 중 → 나가면 직전 불합격 결과 화면\n  COMPLETION  이미 합격한 책의 '틀린 문제만 다시 풀기' 중이거나 심화(qlevel=02) → 나가면 완료 화면\n\n심화는 기본이 이미 DONE이라 PENDING 추천이 없다. 그래서 첫 도전이어도 항상 완료 화면으로 보내야 한다 —\n일반 홈으로 보내면 PENDING을 못 찾고 '입실을 먼저 해주세요'가 뜬다(2026-08-25).",
    80, 800, 1320, 160, "policy")
p.n("qn2", "로그인 단계에서 hasEnteredToday로 '오늘 입실했고 아직 퇴실 전인지'를 본다.\nPENDING 추천 유무가 아니라 오늘 열린(ENTERED) 세션 유무로 판단하는 이유 —\n퇴실 전까지는 로그아웃 후 재로그인해도 그대로 들어올 수 있어야 한다(2026-08-25).",
    80, 985, 660, 110, "note")
p.n("qn3", "퇴실 처리되면 studentSessionRegistry.clear로 유효 세션 등록을 지운다 —\n다음 요청(또는 폴링)부터 문제풀이 기기가 강제 로그아웃된다(2026-08-26).",
    780, 985, 620, 110, "note")

# ── 15. 도서 대여·반납 ───────────────────────────────────────
p = page("15. 도서 대여 · 반납", "loan", 1400, 1000)
p.title("실물 도서 재고 — 대여와 반납의 시점",
        "재고 확보는 원자적 UPDATE(reserveItemById). 완독은 반납을 유발하지 않는다")

p.n("l0", "재고 있음 (AVAILABLE)", 120, 140, 260, 50, "start")
p.n("l1", "대여 중 (item_loan 열림)", 120, 280, 260, 50, "proc")
p.n("l2", "반납 (item_loan 닫힘)", 120, 420, 260, 50, "end")
p.e("l0", "l1", "추천 확정 시 즉시 대여", "free")
p.e("l1", "l2", "", "free")
p.e("l2", "l0", "재고 복귀", "free", waypoints=[(60, 445), (60, 165)])

p.n("t1", "반납이 일어나는 시점은 세 곳뿐이다\n\n  1) 학생 QR 퇴실  (StudentViewController.exitByQr → MonitorService.exitSession)\n  2) 직원 퇴실 처리 (MonitorController.exit → MonitorService.exitSession)\n  3) 새 책 추천     (ClinicService.recommendBook → returnActiveLoanSafely)\n\n완독(DONE)은 반납을 유발하지 않는다 — 재도전 때 책이 손에 있어야 하기 때문이다.\n학생이 다음 책을 받았다는 건 이제 이전 책을 놓아줘도 된다는 뜻이다.",
    460, 140, 640, 190, "policy")

p.n("t2", "퇴실 후 재입실 — ensureActiveLoan\n\n퇴실 시 대여만 반납되고 추천(PENDING)은 그대로 남는다.\n재입실하면 getHomeState / getQuizHomeState / recommendBook이\nensureActiveLoan으로 같은 책을 다시 대여해 이어 읽게 한다\n(그 사이 다른 학생이 마지막 한 권을 가져갔으면 재대여는 생략).",
    460, 360, 640, 150, "note")

p.n("t3", "재고 경합 처리\n\npickNextItem으로 고른 시점과 reserveItemById로 확보하는 시점 사이에\n다른 학생이 그 item의 마지막 한 권을 채가면 확보가 실패한다(null).\n그때는 예외를 내지 않고 그 item_id를 제외 목록에 넣어 다음 후보로 넘어간다.\n후보가 완전히 소진돼야 비로소 404 '추천할 도서 없음'으로 끝낸다(2026-08-28).",
    120, 560, 640, 150, "note")

p.n("t4", "추천 도서 교체 (직원)\n\n서가에 실제로 없거나 못 읽을 정도로 훼손된 책일 때 모니터링에서 실행.\n지금 추천을 취소하고(그 한 권은 재고에서 빠진다) 곧바로 다음 책을 추천한다.\n두 단계는 각자의 트랜잭션 — 2단계 실패가 1단계를 되돌리면 안 되기 때문.",
    800, 560, 500, 150, "policy")

p.n("t5", "도서 식별자\n\n현재 bcode는 UUID다. ISBN 복원을 검토 중\n(중복 판별 · 바코드 스캔 · 외부 API 연동 근거).",
    120, 760, 400, 110, "note")
p.n("t6", "시드 데이터 (매 기동 리셋에서 제외)\n\ncontent / item / itempool / center / priority(권장도서 순위)\n→ data-books.sql · data-itempool.sql · data-priority.sql 수동 실행\n나머지 테이블은 매 기동 리셋된다.",
    560, 760, 500, 110, "policy")

# ── 16. 정책 요약표 ──────────────────────────────────────────
p = page("16. 정책 요약표", "summary", 1600, 1500)
p.title("정책 한눈에 보기",
        "이 파일의 모든 규칙을 한 장으로. 상세는 각 페이지 참고")

cols = [
    ("예약 · 출석", [
        "하루 최대 4회차까지 예약 (MAX_SLOTS_PER_DAY = 4)",
        "월 예약 상한 = 그 달 이용권 total_count 합",
        "그 달 이용권이 없으면 예약 불가 (상한 0)",
        "이미 종료된 회차는 예약 불가",
        "입실·퇴실은 하루 1회",
        "입실 1회 = 그날 예약 회차 전부 ATTENDED",
        "입실 가능 시간 = [회차 시작 −10분, 회차 종료]",
        "예약 없이 온 도보 방문은 입실 차단 (2026-08-20)",
        "NOSHOW는 월 상한에 포함 (status <> CANCELED)",
        "동시성 방어 = 학생 행 UPDLOCK (유니크 인덱스 아님)",
    ]),
    ("이용권", [
        "차감 = 그날 출석 확정 회차 수만큼 (입실 시점)",
        "재입실은 (목표 − 이미 차감분)만 추가 차감",
        "모자라면 있는 만큼만 깎고 입실은 허용",
        "아예 없으면 −1 → 입실 차단",
        "pass_use 1행 = remain_count 1 감소 (불변식)",
        "billing_ym은 호출부가 정해서 넘긴다",
        "다음 청구월 = 가장 늦게 산 달 + 1 (공백기면 이번 달)",
        "발급은 결제 승인 트랜잭션 안에서",
    ]),
    ("도서 추천", [
        "추천 상한 = 2권 × 그날 출석 회차 수 (그날 총량)",
        "PENDING이 있으면 그 책 그대로 (멱등)",
        "심화 게이트 — 앱 '책 추천받기'에서만 적용",
        "키오스크 입실은 게이트 없이 무조건 다음 책",
        "제외 기준은 item이 아니라 content",
        "직전 책과 분류·장르가 같으면 건너뜀",
        "재고 확보 실패 시 제외 후 다음 후보로 재시도",
        "반납은 퇴실 2곳 + 새 책 추천, 총 3곳뿐",
    ]),
    ("문제 · 결과", [
        "합격선 = ceil(전체 문항 × 2/3)",
        "첫 제출이면 결과 무관 status = DONE",
        "첫 제출 불합격도 grade='RETRY'로 저장",
        "'완독 성공' 판정은 isPassGrade(KING/FRIEND)",
        "처음 점수는 최초 제출값에서 고정",
        "최종 점수는 재도전에서 더 잘했을 때만 갱신(max)",
        "등급·뱃지는 올라가기만 한다 (RETRY→FRIEND→KING)",
        "'틀린 문제 다시 풀기'는 아무것도 바꾸지 않는다",
        "심화에는 합격선이 없다 — 풀면 완료, 만점이면 심화왕",
        "심화 점수(처음)는 최초 1회만 기록",
        "심화는 1회성 — 첫 시도 후 버튼이 사라진다",
    ]),
    ("보상", [
        "뱃지 4종 — 1 독서완료 / 2 독서왕 / 3 심화완료 / 4 심화왕",
        "뱃지는 책(content)마다 부여, 첫 시도 결과 기준",
        "독서 카드 NORMAL = 완독한 책마다 1장",
        "NORMAL 10장마다 RARE 1장 추가",
        "NORMAL 10장 = 오프라인 실물 1장 (표시까지만)",
        "EXP 폐지 — 레벨은 완독 권수로 매번 재계산",
        "레벨 = min(12, ⌊학년 완독 권수 ÷ 필요권수⌋ + 1)",
        "필요권수 — 초1·2 8권 / 초3 5권 / 초4~6 4권",
        "단계 = 학생 학년, 칭호 6단계 × 12 = 72종",
    ]),
    ("결제 · 환불", [
        "승인 금액이 기록과 다르면 즉시 망취소",
        "승인 후 확정 실패는 반드시 망취소",
        "모바일은 망취소 수단이 없다 → needs_review",
        "환불은 사용 횟수로만 판정 (날짜 조건 폐지)",
        "0회 100% / 1회 75% / 2회 50% / 3회+ 불가",
        "환불은 결제 1건당 1번만",
        "규정에 없는 환불은 만들어내지 않는다",
        "환불 확정 시 이용권도 회수(revoke)",
    ]),
    ("모니터링 · 일지", [
        "카드 상태 6종 — 미입실/독서중/문제푸는중/시간초과/결과류/퇴실",
        "결과류 = 독서왕·독서친구·재도전필요·심화완료·심화왕",
        "'결과 확인중'은 폐지 (제출 즉시 결과 상태)",
        "시간초과는 제출 전에만 의미가 있다",
        "예약 카드가 미리 떠 있다가 책 추천 시점에 입실 전환",
        "help_needed = erp_student(현재 상태) + diary(그날 스냅샷)",
        "help_needed 토글은 실시간 모니터링에서만",
        "독서일지 헤더는 입실 시점에 미리 만든다(ensureDiary)",
        "카드 스코핑은 로그인 직원의 centerCode 기준",
    ]),
    ("공통 · 운영", [
        "모든 시각은 KST 기준 (KstClock)",
        "SQL에서 GETDATE() 금지 — DATEADD(HOUR, 9, GETUTCDATE())",
        "Firestore에 java.time을 그대로 넣지 않는다 (Map 평탄화)",
        "배포는 PWA — 스토어 게시·키오스크 앱 없음",
        "키오스크 라이선스 기능은 2026-08-28 코드에서 전부 삭제",
        "admin 영역은 서비스워커 캐싱 제외",
        "시드 제외 — content/item/itempool/center/priority",
    ]),
]

x, y = 60, 110
for i, (head, lines) in enumerate(cols):
    col = i % 4
    row = i // 4
    cx = 60 + col * 385
    cy = 110 + row * 660
    p.n("h%d" % i, head, cx, cy, 355, 34, "hdr")
    body = "\n".join("•  " + s for s in lines)
    p.n("b%d" % i, body, cx, cy + 40, 355, 22 * len(lines) + 30, "policy")

p.n("foot", "미착수 / 미확정 (2026-09-03 기준)\n\n"
    "• 결제완료 콜백 트리거 다음 달 일괄 예약 — 미구현(반복 스케줄 저장 구조부터 필요)\n"
    "• 예약 등록 관리자 화면 — 화면 목업만 있고 API 연동 없음\n"
    "• 운영 스케줄 — 화면·DB(11개 테이블)는 있으나 API 미착수, 기존 clinic_reservation과 중복 이슈\n"
    "• 사이드바 관리자 화면 11개 중 9개는 세부 기능 자체가 미확정 (9/30 오픈 필수 범위)\n"
    "• 결제 내역 화면 — 수기 등록 · 프로그램 탭 · 회차 매칭 디테일 남음\n"
    "• 독서여권 · 독서탐험 결과 화면 칸은 여전히 더미 placeholder\n"
    "• 도서 표지 이미지 — 시드에 image_url 없음 / 도서별 전용 카드 이미지도 예정\n"
    "• bcode UUID → ISBN 복원 검토 중",
    60, 1200, 1480, 220, "note")

# ── 출력 ─────────────────────────────────────────────────────
if __name__ == "__main__":
    import os, sys
    dest = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "정책_및_플로우차트_전체.drawio")
    out = ['<mxfile host="app.diagrams.net" agent="book_clinic" type="device">']
    for pg in PAGES:
        out.append(pg.xml(pg.key))
    out.append("</mxfile>")
    with open(dest, "w", encoding="utf-8") as fp:
        fp.write("\n".join(out))
    print("생성 완료: %s (%d 페이지)" % (os.path.normpath(dest), len(PAGES)))
