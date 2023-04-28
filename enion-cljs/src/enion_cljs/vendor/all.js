window.ASSET_PREFIX = "";
window.SCRIPT_PREFIX = "";
window.SCENE_PATH = "1396021.json";
window.CONTEXT_OPTIONS = {
    'antialias': true,
    'alpha': false,
    'preserveDrawingBuffer': false,
    'preferWebGl2': true,
    'powerPreference': "default"
};
window.SCRIPTS = [ 130420822, 118396797, 111773112, 108026253, 99767394, 108026252, 109387017, 110329954, 112098061, 114063573, 114085216, 118401522, 120163247, 120163248, 123110240 ];
window.CONFIG_FILENAME = "config.json";
window.INPUT_SETTINGS = {
    useKeyboard: true,
    useMouse: true,
    useGamepads: false,
    useTouch: true
};
pc.script.legacy = false;
window.PRELOAD_MODULES = [
    {'moduleName' : 'Ammo', 'glueUrl' : 'files/assets/120163247/1/ammo.wasm.js', 'wasmUrl' : 'files/assets/120163246/1/ammo.wasm.wasm', 'fallbackUrl' : 'files/assets/120163248/1/ammo.js', 'preload' : true},
];

var loadModules = function (modules, urlPrefix, doneCallback) { // eslint-disable-line no-unused-vars

    if (typeof modules === "undefined" || modules.length === 0) {
        // caller may depend on callback behaviour being async
        setTimeout(doneCallback);
    } else {
        let remaining = modules.length;
        const moduleLoaded = () => {
            if (--remaining === 0) {
                doneCallback();
            }
        };

        modules.forEach(function (m) {
            pc.WasmModule.setConfig(m.moduleName, {
                glueUrl: urlPrefix + m.glueUrl,
                wasmUrl: urlPrefix + m.wasmUrl,
                fallbackUrl: urlPrefix + m.fallbackUrl
            });

            if (!m.hasOwnProperty('preload') || m.preload) {
                pc.WasmModule.getInstance(m.moduleName, () => { moduleLoaded(); });
            } else {
                moduleLoaded();
            }
        });
    }
};

(function () {
    // Shared Lib
    var CANVAS_ID = 'application-canvas';

    // Needed as we will have edge cases for particular versions of iOS
    // returns null if not iOS
    var getIosVersion = function () {
        if (/iP(hone|od|ad)/.test(navigator.platform)) {
            var v = (navigator.appVersion).match(/OS (\d+)_(\d+)_?(\d+)?/);
            var version = [parseInt(v[1], 10), parseInt(v[2], 10), parseInt(v[3] || 0, 10)];
            return version;
        }

        return null;
    };

    var lastWindowHeight = window.innerHeight;
    var lastWindowWidth = window.innerWidth;
    var windowSizeChangeIntervalHandler = null;

    var pcBootstrap = {
        reflowHandler: null,
        iosVersion: getIosVersion(),

        createCanvas: function () {
            var canvas = document.createElement('canvas');
            canvas.setAttribute('id', CANVAS_ID);
            canvas.setAttribute('tabindex', 0);

            // Disable I-bar cursor on click+drag
            canvas.onselectstart = function () { return false; };

            // Disable long-touch select on iOS devices
            canvas.style['-webkit-user-select'] = 'none';

            document.body.appendChild(canvas);

            return canvas;
        },


        resizeCanvas: function (app, canvas) {
            canvas.style.width = '';
            canvas.style.height = '';
            app.resizeCanvas(canvas.width, canvas.height);

            var fillMode = app._fillMode;

            if (fillMode == pc.FILLMODE_NONE || fillMode == pc.FILLMODE_KEEP_ASPECT) {
                if ((fillMode == pc.FILLMODE_NONE && canvas.clientHeight < window.innerHeight) || (canvas.clientWidth / canvas.clientHeight >= window.innerWidth / window.innerHeight)) {
                    canvas.style.marginTop = Math.floor((window.innerHeight - canvas.clientHeight) / 2) + 'px';
                } else {
                    canvas.style.marginTop = '';
                }
            }

            lastWindowHeight = window.innerHeight;
            lastWindowWidth = window.innerWidth;

            // Work around when in landscape to work on iOS 12 otherwise
            // the content is under the URL bar at the top
            if (this.iosVersion && this.iosVersion[0] <= 12) {
                window.scrollTo(0, 0);
            }
        },

        reflow: function (app, canvas) {
            this.resizeCanvas(app, canvas);

            // Poll for size changes as the window inner height can change after the resize event for iOS
            // Have one tab only, and rotate from portrait -> landscape -> portrait
            if (windowSizeChangeIntervalHandler === null) {
                windowSizeChangeIntervalHandler = setInterval(function () {
                    if (lastWindowHeight !== window.innerHeight || lastWindowWidth !== window.innerWidth) {
                        this.resizeCanvas(app, canvas);
                    }
                }.bind(this), 100);

                // Don't want to do this all the time so stop polling after some short time
                setTimeout(function () {
                    if (!!windowSizeChangeIntervalHandler) {
                        clearInterval(windowSizeChangeIntervalHandler);
                        windowSizeChangeIntervalHandler = null;
                    }
                }, 2000);
            }
        }
    };

    // Expose the reflow to users so that they can override the existing
    // reflow logic if need be
    window.pcBootstrap = pcBootstrap;
})();


(function () {
    var canvas, devices, app;

    var createInputDevices = function (canvas) {
        var devices = {
            elementInput: new pc.ElementInput(canvas, {
                useMouse: INPUT_SETTINGS.useMouse,
                useTouch: INPUT_SETTINGS.useTouch
            }),
            keyboard: INPUT_SETTINGS.useKeyboard ? new pc.Keyboard(window) : null,
            mouse: INPUT_SETTINGS.useMouse ? new pc.Mouse(canvas) : null,
            gamepads: INPUT_SETTINGS.useGamepads ? new pc.GamePads() : null,
            touch: INPUT_SETTINGS.useTouch && pc.platform.touch ? new pc.TouchDevice(canvas) : null
        };

        return devices;
    };

    var configureCss = function (fillMode, width, height) {
        // Configure resolution and resize event
        if (canvas.classList) {
            canvas.classList.add('fill-mode-' + fillMode);
        }

        // css media query for aspect ratio changes
        var css  = "@media screen and (min-aspect-ratio: " + width + "/" + height + ") {";
        css += "    #application-canvas.fill-mode-KEEP_ASPECT {";
        css += "        width: auto;";
        css += "        height: 100%;";
        css += "        margin: 0 auto;";
        css += "    }";
        css += "}";

        // append css to style
        if (document.head.querySelector) {
            document.head.querySelector('style').innerHTML += css;
        }
    };

    var displayError = function (html) {
        var div = document.createElement('div');

        div.innerHTML  = [
            '<table style="background-color: #8CE; width: 100%; height: 100%;">',
            '  <tr>',
            '      <td align="center">',
            '          <div style="display: table-cell; vertical-align: middle;">',
            '              <div style="">' + html + '</div>',
            '          </div>',
            '      </td>',
            '  </tr>',
            '</table>'
        ].join('\n');

        document.body.appendChild(div);
    };

    canvas = pcBootstrap.createCanvas();
    devices = createInputDevices(canvas);

    try {
        app = new pc.Application(canvas, {
            elementInput: devices.elementInput,
            keyboard: devices.keyboard,
            mouse: devices.mouse,
            gamepads: devices.gamepads,
            touch: devices.touch,
            graphicsDeviceOptions: window.CONTEXT_OPTIONS,
            assetPrefix: window.ASSET_PREFIX || "",
            scriptPrefix: window.SCRIPT_PREFIX || "",
            scriptsOrder: window.SCRIPTS || []
        });
    } catch (e) {
        if (e instanceof pc.UnsupportedBrowserError) {
            displayError('This page requires a browser that supports WebGL.<br/>' +
                    '<a href="http://get.webgl.org">Click here to find out more.</a>');
        } else if (e instanceof pc.ContextCreationError) {
            displayError("It doesn't appear your computer can support WebGL.<br/>" +
                    '<a href="http://get.webgl.org/troubleshooting/">Click here for more information.</a>');
        } else {
            displayError('Could not initialize application. Error: ' + e);
        }

        return;
    }

    var configure = function () {
        app.configure(CONFIG_FILENAME, function (err) {
            if (err) {
                console.error(err);
            }

            configureCss(app._fillMode, app._width, app._height);

            const ltcMat1 = []; 
            const ltcMat2 = []; 

            if (ltcMat1.length && ltcMat2.length && app.setAreaLightLuts.length === 2) {
                app.setAreaLightLuts(ltcMat1, ltcMat2);
            }

            // do the first reflow after a timeout because of
            // iOS showing a squished iframe sometimes
            setTimeout(function () {
                pcBootstrap.reflow(app, canvas);
                pcBootstrap.reflowHandler = function () { pcBootstrap.reflow(app, canvas); };

                window.addEventListener('resize', pcBootstrap.reflowHandler, false);
                window.addEventListener('orientationchange', pcBootstrap.reflowHandler, false);

                app.preload(function (err) {
                    if (err) {
                        console.error(err);
                    }

                    app.scenes.loadScene(SCENE_PATH, function (err, scene) {
                        if (err) {
                            console.error(err);
                        }

                        app.start();
                    });
                });
            });
        });
    };

    if (PRELOAD_MODULES.length > 0) {
        loadModules(PRELOAD_MODULES, ASSET_PREFIX, configure);
    } else {
        configure();
    }
})();

pc.script.createLoadingScreen(function(app) {

    var introTextsCss = `
    .text-container {
      position: relative;
      display: flex;
      justify-content: center;
      align-items: center;
      font-size: 24px;
      font-weight: bold;
      color: white;
      text-align: center;
      margin-top: 50px;
    }

    .text-item {
      opacity: 0;
      position: absolute;
      top: 50%;
      left: 50%;
      width: 100%;
      transform: translate(-50%, -50%);
      animation: text-animation 15s linear infinite;
    }

    .text-item:nth-child(2) {
      animation-delay: 3s;
    }

    .text-item:nth-child(3) {
      animation-delay: 6s;
    }

    .text-item:nth-child(4) {
      animation-delay: 9s;
    }

    .text-item:nth-child(5) {
      animation-delay: 12s;
    }

    @keyframes text-animation {
      0% {
        opacity: 0;
      }
      3.33% {
        opacity: 1;
      }
      23.33% {
        opacity: 1;
      }
      26.67% {
        opacity: 0;
      }
    }
    `;


    var showSplash = function() {
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
        if (window.ASSET_PREFIX === '') {
            logo.src = window.ASSET_PREFIX + 'logo.png';
        } else {
            logo.src = 'https://playcanvas.com/static-assets/images/play_text_252_white.png';
        }
        splash.appendChild(logo);
        logo.onload = function() {
            splash.style.display = 'block';
        };

        var container = document.createElement('div');
        container.id = 'progress-bar-container';
        document.body.appendChild(container);

        var bar = document.createElement('div');
        bar.id = 'progress-bar';
        container.appendChild(bar);

        var barInner = document.createElement('div');
        barInner.id = 'progress-bar-inner';
        bar.appendChild(barInner);

        var para = document.createElement("p");
        var node = document.createTextNode("For the best experience, please use a Chrome based browser");
        para.appendChild(node);
        para.style.color = "white";
        para.style.fontSize = "24px";
        para.style.fontWeight = "bold";
        para.style.textAlign = "center";
        para.style.marginTop = "15px";
        para.style.marginBottom = "15px";

        var textContainer = document.createElement("div");
        textContainer.classList.add("text-container");

        var textItems =
        [
            "Choose your side wisely, as the battle between Orcs and Humans rages on...",
            "The thrill of PvP combat awaits you in this immersive MMORPG",
            "Explore a vast and mysterious world filled with NPCs and valuable loot",
            "Complete quests and earn rewards that will help you in your journey",
            "Unlock new abilities and spells as you progress through the game"
        ];

        textItems.forEach(function(item) {
            var textItem = document.createElement("div");
            textItem.classList.add("text-item");
            textItem.textContent = item;
            textContainer.appendChild(textItem);
        });

        splash.appendChild(textContainer);

        var container = document.createElement('div');
        container.style.position = 'relative';
        container.style.width = '100%';
        container.style.height = '80px'; // match the height of the image

        splash.appendChild(container);
    };

   function onLoadingFinished() {
      // Remove existing ::before and ::after styles
      const styleTags = document.getElementsByTagName('style');
      for (let i = 0; i < styleTags.length; i++) {
        const styleTag = styleTags[i];
        if (styleTag.innerHTML.includes('body::before') || styleTag.innerHTML.includes('body::after')) {
          styleTag.remove();
        }
      }

      // Set the body background color to black
      document.body.style.backgroundColor = 'black';
    }


    var hideSplash = function() {
        var splash = document.getElementById('application-splash-wrapper');
        splash.parentElement.removeChild(splash);

        var progressBar = document.getElementById('progress-bar-container');
        progressBar.parentElement.removeChild(progressBar);

        onLoadingFinished();
    };

    var setProgress = function(value) {
        var bar = document.getElementById('progress-bar-inner');
        if (bar) {
            value = Math.min(1, Math.max(0, value));
            bar.style.width = value * 100 + '%';
        }
    };


    var createCss = function() {
        var css = [
       `
        body::before,
        body::after {
          content: "";
          position: absolute;
          height: 15%; /* Adjust this value to change the height of the black bars */
          width: 100%;
          background-color: black;
          z-index: 1;
        }

        body::before {
          top: 0;
        }

        body::after {
          bottom: 0;
        }

        `,
            '',
            '#application-splash-wrapper {',
            '    position: absolute;',
            '    top: 0;',
            '    left: 0;',
            '    height: 100%;',
            '    width: 100%;',
            '    background-color: #283538;',
            '    background-image: url(bg.jpeg), url(bg1.jpeg);',
            '    background-size: cover, cover;',
            '    background-repeat: no-repeat, no-repeat;',
            '    background-position: center center, center center;',
            '    animation: bg-animation 12s infinite linear;',
            '}',
            '',
            '@keyframes bg-animation {',
            '    0% {',
            '        background-image: url(bg.jpeg), url(bg1.jpeg);',
            '    }',
            '    50% {',
            '        background-image: url(bg1.jpeg), url(bg.jpeg)',
            '    }',
            '    100% {',
            '        background-image: url(bg.jpeg), url(bg1.jpeg);',
            '    }',
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
            `
    #progress-bar-container {
      position: fixed;
      bottom: 5%;
      left: 50%;
      transform: translateX(-50%);
      width: 50%;
      height: 25px;
      padding: 5px;
      background-color: #2f2c2c;
      clip-path: polygon(7% 0%, 93% 0%, 98% 50%, 93% 100%, 7% 100%, 2% 50%);
      z-index: 2;
    }

    #progress-bar {
      position: relative;
      width: 100%;
      height: 100%;
      background-color: #333;
      box-shadow: 0 0 10px rgba(0, 0, 0, 0.5);
      border-radius: 5px;
      clip-path: polygon(7% 0%, 93% 0%, 98% 50%, 93% 100%, 7% 100%, 2% 50%);
    }

    #progress-bar-inner {
      position: absolute;
      height: 100%;
      background-image: linear-gradient(to right, rgba(30, 80, 15, 0.7) 0%, rgba(60, 130, 40) 50%, rgba(30, 80, 15, 0.7) 100%);
      border-radius: 3px;
      width: 50%;
    }
            `,
            '',
            '@media (max-width: 480px) {',
            '    #application-splash {',
            '        width: 170px;',
            '        left: calc(50% - 85px);',
            '    }',
            '}'
        ].join('\n');

        css += introTextsCss;

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

    app.on('preload:end', function() {
        app.off('preload:progress');
    });
    app.on('preload:progress', setProgress);
    app.on('start', hideSplash);
});
