namespace VixzDesktop.Services
{
    public static class PlayerScripts
    {
            public const string WatchPageCleanerScript = @"
    (function() {
        function cleanWatchPage() {
            if (!document.getElementById('vixz-watch-cleaner-style')) {
                var style = document.createElement('style');
                style.id = 'vixz-watch-cleaner-style';
                style.innerHTML = `
                    header, #masthead-container, ytd-masthead, #masthead,
                    #below, #secondary, #related, #comments, #chat, #chat-container,
                    .ytp-chrome-top, .ytp-watermark, .ytp-youtube-button, .ytp-pause-overlay,
                    ytd-banner-promo-renderer, ytd-merch-shelf-renderer,
                    tp-yt-app-drawer, #guide-wrapper, ytd-miniplayer,
                    #cinematics, #ticket-shelf,
                    .video-ads, .ytp-ad-module, .ytp-ad-player-overlay, .ytp-ad-player-overlay-layout,
                    .ytp-ad-image-overlay, .ytp-ad-text, .ytp-ad-action-interstitial,
                    .ytp-ad-overlay-container, ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,
                    ytd-promoted-video-renderer, ytd-compact-promoted-video-renderer,
                    ytd-player-legacy-desktop-watch-ads-renderer, .ytp-ad-preview-container,
                    .ytp-ad-message-container, .ad-container, .ad-div {
                        display: none !important;
                    }
                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        overflow: hidden !important;
                        background: #000 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                    }
                    #content, ytd-watch-flexy, #page-manager, #columns, #primary, #primary-inner,
                    #player-theater-container, #full-bleed-container, #player-container, #player-container-outer,
                    #ytd-player, #movie_player, .html5-video-player, video {
                        position: fixed !important;
                        top: 0 !important;
                        left: 0 !important;
                        width: 100vw !important;
                        height: 100vh !important;
                        max-width: 100vw !important;
                        max-height: 100vh !important;
                        z-index: 2147483640 !important;
                        object-fit: contain !important;
                        background: #000 !important;
                        margin: 0 !important;
                        padding: 0 !important;
                    }
                    .ytp-chrome-bottom {
                        position: absolute !important;
                        bottom: 0 !important;
                        left: 0 !important;
                        width: 100% !important;
                        z-index: 2147483647 !important;
                    }
                `;
                document.head.appendChild(style);
            }
    
            function skipVideoAds() {
                var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                var isAd = false;
                if (player) {
                    isAd = player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
                }
                if (!isAd && document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay, .video-ads .ytp-ad-text')) {
                    isAd = true;
                }
    
                var skipSelectors = [
                    '.ytp-ad-skip-button',
                    '.ytp-ad-skip-button-modern',
                    '.ytp-skip-ad-button',
                    '.ytp-ad-skip-button-container button',
                    'button[id^=""skip-button:""]',
                    '.ytp-ad-overlay-close-button',
                    'button.ytp-ad-skip-button-modern',
                    '.ytp-skip-ad-button__text',
                    '.ytp-ad-survey-answer'
                ];
                for (var s = 0; s < skipSelectors.length; s++) {
                    var btns = document.querySelectorAll(skipSelectors[s]);
                    for (var b = 0; b < btns.length; b++) {
                        try { btns[b].click(); } catch(e) {}
                    }
                }
    
                var adVid = document.querySelector('video');
                if (isAd && adVid) {
                    try {
                        adVid.muted = true;
                        adVid.playbackRate = 16.0;
                        if (isFinite(adVid.duration) && adVid.duration > 0) {
                            adVid.currentTime = adVid.duration - 0.1;
                        }
                    } catch(e) {}
                } else if (!isAd && adVid && adVid.playbackRate > 2.0) {
                    try {
                        adVid.playbackRate = 1.0;
                        adVid.muted = false;
                    } catch(e) {}
                }
            }
    
            var v = document.querySelector('video');
            if (v) {
                v.muted = false;
                if (v.paused) {
                    try { v.play(); } catch(e) {}
                }
                if (!v._vixzHooked) {
                    v._vixzHooked = true;
                    v.addEventListener('ended', function() {
                        if (window.chrome && window.chrome.webview) {
                            window.chrome.webview.postMessage('VIDEO_ENDED');
                        }
                    });
                    v.addEventListener('timeupdate', function() {
                        skipVideoAds();
                        if (window.chrome && window.chrome.webview && v.duration) {
                            window.chrome.webview.postMessage('POS:' + (window.location.search || '') + ':' + Math.floor(v.currentTime));
                        }
                    });
                }
            }
    
            var playerElem = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
            if (playerElem && !playerElem._vixzAdObserver) {
                playerElem._vixzAdObserver = true;
                try {
                    var obs = new MutationObserver(function() {
                        skipVideoAds();
                    });
                    obs.observe(playerElem, { attributes: true, attributeFilter: ['class'] });
                } catch(e) {}
            }
    
            skipVideoAds();
    
            var playBtn = document.querySelector('.ytp-play-button');
            if (playBtn && playBtn.getAttribute('aria-keyshortcuts') === 'k' && playBtn.title && playBtn.title.toLowerCase().indexOf('play') !== -1) {
                try { playBtn.click(); } catch(e) {}
            }
    
            var confirmBtn = document.querySelector('button[aria-label*=""proceed"" i], button#confirm-button, ytd-button-renderer#confirm-button button');
            if (confirmBtn) {
                try { confirmBtn.click(); } catch(e) {}
            }
        }
    
        window.togglePlay = function() {
            var v = document.querySelector('video');
            if (v) {
                if (v.paused) v.play();
                else v.pause();
            }
        };
        window.seek = function(delta) {
            var v = document.querySelector('video');
            if (v) {
                v.currentTime = Math.max(0, v.currentTime + delta);
            }
        };
        window.toggleMute = function() {
            var v = document.querySelector('video');
            if (v) {
                v.muted = !v.muted;
            }
        };
        window.getCurrentTime = function() {
            var v = document.querySelector('video');
            return v ? v.currentTime : 0;
        };
    
        cleanWatchPage();
        setInterval(cleanWatchPage, 150);
    })();
    ";
    }
}
