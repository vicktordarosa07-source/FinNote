const CACHE = 'finnote-shell-premium-v2'
const scopeUrl = new URL(self.registration.scope)
const appRoot = scopeUrl.pathname
const shell = [appRoot, `${appRoot}index.html`, `${appRoot}manifest.webmanifest`, `${appRoot}icon.svg`]

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(shell)))
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key)))))
  self.clients.claim()
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  const url = new URL(request.url)
  if (request.method !== 'GET' || url.origin !== self.location.origin) return
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then((response) => {
      const copy = response.clone()
      caches.open(CACHE).then((cache) => cache.put(`${appRoot}index.html`, copy))
      return response
    }).catch(async () => (await caches.match(`${appRoot}index.html`)) || caches.match(appRoot)))
    return
  }
  event.respondWith(caches.match(request).then((cached) => {
    const fresh = fetch(request).then((response) => {
      if (response.ok) caches.open(CACHE).then((cache) => cache.put(request, response.clone()))
      return response
    })
    return cached || fresh
  }))
})
