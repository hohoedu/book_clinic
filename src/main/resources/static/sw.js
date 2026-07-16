const CACHE_NAME = 'book-clinic-student-v3';
const OFFLINE_URL = '/student/login';

const PRECACHE_URLS = [
  '/student/login',
  '/css/student/student-common.css',
  '/css/student/student-main.css',
  '/js/student/student-main.js',
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

  // 로그인/메인 등 페이지 이동(navigate)은 학생마다·시점마다 내용이 달라지므로
  // 항상 네트워크에서 최신 화면을 받아오고, 오프라인일 때만 캐시된 로그인 화면으로 대체한다
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match(OFFLINE_URL)));
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
            return caches.match(OFFLINE_URL);
          }
        });
    })
  );
});
