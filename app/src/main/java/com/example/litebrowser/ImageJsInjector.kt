package com.example.litebrowser

object ImageJsInjector {
    val IMAGE_LONGPRESS_JS: String = """
        (function() {
          try {
            if (window.__litebrowserImageLPAttached) return;
            window.__litebrowserImageLPAttached = true;

            function pickSrcFromSrcset(srcset) {
              if (!srcset) return '';
              const parts = srcset.split(',').map(s => s.trim()).filter(Boolean);
              if (!parts.length) return '';
              return parts[parts.length - 1].split(' ')[0] || '';
            }

            function looksLikeImageUrl(url) {
              return /\.(jpg|jpeg|png|gif|webp|avif|svg)(\?|#|$)/i.test(url || '');
            }

            function resolveImageUrl(img) {
              if (!img) return '';
              let url = img.currentSrc || img.src || '';
              if (!url) {
                url = img.dataset?.src || img.dataset?.lazySrc || img.dataset?.original || '';
              }

              if (!url) {
                const picture = img.closest('picture');
                if (picture) {
                  const sources = picture.querySelectorAll('source[srcset]');
                  for (const source of sources) {
                    const picked = pickSrcFromSrcset(source.getAttribute('srcset'));
                    if (picked) { url = picked; break; }
                  }
                }
              }

              if (!url) {
                const a = img.closest('a[href]');
                const href = a ? a.href : '';
                if (looksLikeImageUrl(href)) url = href;
              }

              return url || '';
            }

            function attachToImage(img) {
              if (!img || img.__liteLPBound) return;
              img.__liteLPBound = true;

              let timer = null;
              let startX = 0;
              let startY = 0;
              let startTs = 0;

              function clearTimer() {
                if (timer) { clearTimeout(timer); timer = null; }
              }

              function trigger(e) {
                try {
                  const url = resolveImageUrl(img);
                  if (url && window.ImageJsBridge && window.ImageJsBridge.onImageLongPress) {
                    window.ImageJsBridge.onImageLongPress(url);
                    if (e && e.preventDefault) e.preventDefault();
                  }
                } catch (_) {}
              }

              img.addEventListener('touchstart', function(e) {
                const t = e.touches && e.touches[0];
                if (!t) return;
                startX = t.clientX; startY = t.clientY; startTs = Date.now();
                clearTimer();
                timer = setTimeout(() => trigger(e), 500);
              }, { passive: false });

              img.addEventListener('touchmove', function(e) {
                const t = e.touches && e.touches[0];
                if (!t) return;
                const moved = Math.hypot(t.clientX - startX, t.clientY - startY);
                if (moved > 8) clearTimer();
              }, { passive: true });

              img.addEventListener('touchend', function(e) {
                const hold = Date.now() - startTs;
                if (hold < 500) clearTimer();
              }, { passive: true });

              img.addEventListener('pointerdown', function(e) {
                if (e.pointerType === 'mouse' || e.pointerType === 'pen') {
                  clearTimer();
                  timer = setTimeout(() => trigger(e), 500);
                }
              });

              img.addEventListener('pointerup', clearTimer);
              img.addEventListener('pointercancel', clearTimer);
              img.addEventListener('contextmenu', function(e) { e.preventDefault(); });
            }

            function attachAll() {
              document.querySelectorAll('img').forEach(attachToImage);
              document.querySelectorAll('picture source[srcset]').forEach(function(source) {
                source.__liteLPObserved = true;
              });
            }

            attachAll();
            const obs = new MutationObserver(() => attachAll());
            obs.observe(document.documentElement || document.body, { childList: true, subtree: true, attributes: true });
          } catch (_) {}
        })();
    """.trimIndent()
}
