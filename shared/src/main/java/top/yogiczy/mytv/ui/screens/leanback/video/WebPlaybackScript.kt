package top.yogiczy.mytv.ui.screens.leanback.video

internal val PLAY_VIDEO_SCRIPT = """
    (function () {
        var video = document.querySelector('video');
        if (!video || !document.body) return null;
        if (video.parentNode !== document.body) document.body.appendChild(video);
        if (!document.getElementById('mytv-video-layout')) {
            var style = document.createElement('style');
            style.id = 'mytv-video-layout';
            style.textContent = 'html,body{margin:0!important;overflow:hidden!important;background:#000!important;}' +
                'body > :not([data-mytv-video]){display:none!important;}';
            document.head.appendChild(style);
        }
        video.setAttribute('data-mytv-video', '');
        video.removeAttribute('controls');
        video.style.cssText = 'position:fixed!important;left:0!important;top:0!important;' +
            'width:100vw!important;height:100vh!important;max-width:none!important;' +
            'max-height:none!important;object-fit:contain!important;background:black!important;' +
            'z-index:2147483647!important;margin:0!important;padding:0!important;';
        video.autoplay = true;
        if (video.paused) {
            var play = video.play();
            if (play && play.catch) play.catch(function () {});
        }
        if (video.paused || video.readyState < 2 || !video.videoWidth) return null;
        return [video.videoWidth, video.videoHeight];
    })()
""".trimIndent()
