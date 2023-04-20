pc.script.createLoadingScreen(function (app) {
    var showSplash = function () {
        // splash wrapper
        var wrapper = document.createElement('div');
        wrapper.id = 'application-splash-wrapper';
        document.body.appendChild(wrapper);

        // splash
        var splash = document.createElement('div');
        splash.id = 'application-splash';
        wrapper.appendChild(splash);
        splash.style.display = 'none';

        var logo = document.createElement('img');
        if(window.ASSET_PREFIX === ''){
         logo.src = window.ASSET_PREFIX + 'logo.png';
        }else{
         logo.src = 'https://playcanvas.com/static-assets/images/play_text_252_white.png';
        }
        splash.appendChild(logo);
        logo.onload = function () {
            splash.style.display = 'block';
        };

        var container = document.createElement('div');
        container.id = 'progress-bar-container';
        splash.appendChild(container);

        var bar = document.createElement('div');
        bar.id = 'progress-bar';
        container.appendChild(bar);

        var para = document.createElement("p");
        var node = document.createTextNode("For the best experience, please use a Chrome based browser");
        para.appendChild(node);
        splash.appendChild(para);
        para.style.color = "white";
        para.style.fontSize = "24px";
        para.style.fontWeight = "bold";
        para.style.textAlign = "center";
        para.style.marginTop = "15px";
        para.style.marginBottom = "15px";

        var container = document.createElement('div');
        container.style.position = 'relative';
        container.style.width = '100%';
        container.style.height = '80px'; // match the height of the image

        var chrome = document.createElement('img');
        chrome.src = 'img/chrome.png';
        chrome.style.display = 'block';
        chrome.style.width = '75px';
        chrome.style.height = '75px';
        chrome.style.position = 'absolute';
        chrome.style.left = '50%';
        chrome.style.transform = 'translateX(-50%)';

        container.appendChild(chrome);
        splash.appendChild(container);
    };

    var hideSplash = function () {
        var splash = document.getElementById('application-splash-wrapper');
        splash.parentElement.removeChild(splash);
    };

    var setProgress = function (value) {
        var bar = document.getElementById('progress-bar');
        if(bar) {
            value = Math.min(1, Math.max(0, value));
            bar.style.width = value * 100 + '%';
        }
    };

        
    var createCss = function () {
        var css = [
            'body {',
            '    background-color: #283538;',
            '}',
            '',
            '#application-splash-wrapper {',
            '    position: absolute;',
            '    top: 0;',
            '    left: 0;',
            '    height: 100%;',
            '    width: 100%;',
            '    background-color: #283538;',
            '    background-image: url(bg.png);',
            '    background-size: cover;',
            '    background-position: center center;',
            '}',
            '',
            '#application-splash {',
            '    position: absolute;',
            '    top: calc(50%);',
            '    width: 512px;',
            '    left: calc(50%);',
            '    transform: translate(-50%, -50%);',
            '    background: #10131ddb;',
            '    border-radius: 10px;',
            '    padding: 15px;',
            '}',
            '',
            '@media (max-width: 1250px) {',
            ' #application-splash {',
            '    transform: translate(-50%, -50%) scale(0.8);',
            ' }',
            '}',
            '',
            '#application-splash img {',
            '    width: 100%;',
            '}',
            '',
            '#progress-bar-container {',
            '    margin: 20px auto 0 auto;',
            '    height: 10px;',
            '    width: 100%;',
            '    background-color: #1d292c;',
            '}',
            '',
            '#progress-bar {',
            '    width: 0%;',
            '    height: 100%;',
            '    background-color: #f60;',
            '    border-radius: 5px',
            '}',
            '',
            '@media (max-width: 480px) {',
            '    #application-splash {',
            '        width: 170px;',
            '        left: calc(50% - 85px);',
            '    }',
            '}'
        ].join('\n');

        var style = document.createElement('style');
        style.type = 'text/css';
        if (style.styleSheet) {
            style.styleSheet.cssText = css;
        } else {
            style.appendChild(document.createTextNode(css));
        }

        document.head.appendChild(style);
    };

    createCss();
    showSplash();

    app.on('preload:end', function () {
        app.off('preload:progress');
    });
    app.on('preload:progress', setProgress);
    app.on('start', hideSplash);
});
