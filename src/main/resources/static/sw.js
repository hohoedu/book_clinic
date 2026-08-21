const CACHE_NAME = 'book-clinic-student-v9';

// 앱은 하나(manifest.json, start_url=/launch)지만 그 안에 문제풀이(/student/**)/출석체크
// (/attendance/**) 두 화면이 있다 — 오프라인 폴백은 지금 들어가려던 화면이 어느 쪽인지에 따라
// 각자의 홈으로 보내야 한다(하나로 고정하면 다른 쪽은 엉뚱한 화면으로 튕긴다, 2026-07-30).
function offlineFallbackFor(request) {
  const pathname = new URL(request.url).pathname;
  if (pathname.startsWith('/attendance')) return '/attendance';
  if (pathname.startsWith('/student')) return '/student/login';
  return '/launch';
}

const PRECACHE_URLS = [
  '/launch',
  '/student/login',
  '/attendance',
  '/css/student/launcher.css',
  '/css/student/student-common.css',
  '/css/student/student-main.css',
  '/css/student/qr-scan.css',
  '/css/student/attendance-home.css',
  '/css/student/book-confirm.css',
  '/js/student/launcher.js',
  '/js/student/student-main.js',
  '/js/student/qr-scan.js',
  '/js/student/attendance-home.js',
  '/js/vendor/jsqr/jsQR.js',
  '/manifest.json',
  '/images/logo_chaekbang.png',
  '/images/book-sample.png',
  '/images/img-my-ch01.png',
  '/images/bg01.png',
  '/images/badge-1-01.png',
  '/images/badge-1-02.png',
  '/images/badge-1-03.png',
  '/images/badge-1-04.png',
  '/images/icons/icon-192.png',
  '/images/icons/icon-512.png'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys
          .filter((key) => key !== CACHE_NAME)
          .map((key) => caches.delete(key))
      )
    ).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;

  if (request.method !== 'GET') {
    return;
  }

  // http(s) 외 스킴(chrome-extension:// 등)은 Cache API에 저장할 수 없어 무시한다
  // (일부 브라우저 확장이 자체 요청을 이 서비스워커의 스코프로 흘려보내는 경우가 있음)
  if (!request.url.startsWith('http')) {
    return;
  }

  const url = new URL(request.url);

  // 다른 도메인으로 나가는 요청(Firestore 실시간 구독의 장시간 스트리밍 요청 등)은 절대 가로채지
  // 않는다 — 이 서비스워커는 스코프가 '/'라 관리자 화면(실시간 모니터링 등)도 통제 대상인데,
  // respondWith(fetch(request))로 cross-origin 스트리밍 요청을 다시 감싸면 브라우저가 스트림을
  // 정상 처리하지 못하고 "Failed to fetch"로 끊겨버린다(Firestore onSnapshot 재연결 반복 → 실시간
  // 반영이 30초 폴백 폴링에만 의존하게 되는 원인이었음, 2026-07-23). 그대로 두면(respondWith를
  // 호출하지 않으면) 브라우저가 원래 하던 대로 직접 처리한다.
  if (url.origin !== self.location.origin) {
    return;
  }

  // 관리자 영역은 서비스워커가 일절 관여하지 않는다 — SW는 학생 PWA(설치·오프라인)용인데 등록
  // 스코프가 '/'라 admin 화면까지 통제 대상에 들어온다. admin은 실시간 대시보드라 오프라인/캐시
  // 가치가 없고, 캐시가 끼면 "지금 보는 게 최신인지 캐시인지" 혼란만 준다. admin 페이지·API와
  // admin 전용 스크립트(모니터링 JS·Firebase SDK 벤들)는 respondWith 없이 그대로 네트워크로
  // 흘려보내(=SW 미개입) 항상 최신을 받게 한다. (2026-07-24)
  if (url.pathname.startsWith('/admin/')
      || url.pathname.startsWith('/js/monitor/')
      || url.pathname.startsWith('/js/vendor/')) {
    return;
  }

  // 로그인/메인 등 페이지 이동(navigate)은 학생마다·시점마다 내용이 달라지므로
  // 항상 네트워크에서 최신 화면을 받아오고, 오프라인일 때만 캐시된 로그인 화면으로 대체한다
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match(offlineFallbackFor(request))));
    return;
  }

  // JS/CSS는 개발 중 계속 바뀌는 파일이라 캐시 우선으로 서빙하면 배포해도 예전 코드가 계속 재생된다.
  // 항상 네트워크에서 최신 버전을 받아오고, 오프라인일 때만 캐시로 폴백한다(network-first).
  if (url.pathname.startsWith('/js/') || url.pathname.startsWith('/css/')) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          if (response && response.ok && response.type === 'basic') {
            const responseClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, responseClone));
          }
          return response;
        })
        .catch(() => caches.match(request))
    );
    return;
  }

  // 그 외 모든 요청(도서/문제/순위 등 데이터 API GET 포함)은 항상 최신 상태를 반영해야 하므로 캐시하지 않는다.
  // "API 경로만 블랙리스트로 제외"하던 이전 방식은 컨트롤러가 /api/ 접두사 없이 매핑된 경우(/book, /question, /priority 등)
  // 캐시 대상으로 잘못 흘러들어가 삭제/수정/복구 직후에도 새로고침 시 옛 데이터가 다시 보이는 사고로 이어졌다.
  // 그래서 캐시는 화이트리스트(이미지·매니페스트 등 진짜 정적 자산)만 허용하고 나머지는 전부 네트워크로 보낸다.
  const isCacheableStaticAsset = url.pathname.startsWith('/images/') || url.pathname === '/manifest.json';
  if (!isCacheableStaticAsset) {
    event.respondWith(fetch(request));
    return;
  }

  // 이미지 등 자주 안 바뀌는 정적 자산은 그대로 캐시 우선
  event.respondWith(
    caches.match(request).then((cached) => {
      if (cached) {
        return cached;
      }

      return fetch(request)
        .then((response) => {
          if (response && response.ok && response.type === 'basic') {
            const responseClone = response.clone();
            caches.open(CACHE_NAME).then((cache) => cache.put(request, responseClone));
          }
          return response;
        })
        .catch(() => {
          if (request.mode === 'navigate') {
            return caches.match(offlineFallbackFor(request));
          }
        });
    })
  );
});
