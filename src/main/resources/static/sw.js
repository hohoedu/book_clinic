const CACHE_NAME = 'book-clinic-student-v3';
const OFFLINE_URL = '/student/login';

const PRECACHE_URLS = [
  '/student/login',
  '/css/student-common.css',
  '/css/student-main.css',
  '/js/student-main.js',
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

  // API 응답은 항상 최신 세션/사용자 정보를 반영해야 하므로 캐시하지 않는다
  const url = new URL(request.url);
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(request));
    return;
  }

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
