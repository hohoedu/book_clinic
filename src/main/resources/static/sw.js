const CACHE_NAME = 'book-clinic-student-v2';
const OFFLINE_URL = '/student/main';

const PRECACHE_URLS = [ 
  '/student/main',
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
