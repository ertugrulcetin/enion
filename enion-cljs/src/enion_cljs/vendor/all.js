ASSET_PREFIX = "";
SCRIPT_PREFIX = "";
SCENE_PATH = "1396021.json";
CONTEXT_OPTIONS = {
    'antialias': true,
    'alpha': false,
    'preserveDrawingBuffer': false,
    'preferWebGl2': true,
    'powerPreference': "default"
};
SCRIPTS = [ 118396797, 78753240, 111773112, 108026253, 99767394, 108026252, 109387017, 109848422, 109848430, 110329954, 112098061, 114063573, 114085216, 118401522, 118403201, 120110871, 120110872 ];
CONFIG_FILENAME = "config.json";
INPUT_SETTINGS = {
    useKeyboard: true,
    useMouse: true,
    useGamepads: false,
    useTouch: true
};
pc.script.legacy = false;
PRELOAD_MODULES = [
    {'moduleName' : 'Ammo', 'glueUrl' : 'files/assets/120110871/1/ammo.wasm.js', 'wasmUrl' : 'files/assets/120110870/1/ammo.wasm.wasm', 'fallbackUrl' : 'files/assets/120110872/1/ammo.js', 'preload' : true},
];

var loadModules = function (modules, urlPrefix, doneCallback) { // eslint-disable-line no-unused-vars

    // check for wasm module support
    function wasmSupported() {
        try {
            if (typeof WebAssembly === "object" && typeof WebAssembly.instantiate === "function") {
                const module = new WebAssembly.Module(Uint8Array.of(0x0, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00));
                if (module instanceof WebAssembly.Module)
                    return new WebAssembly.Instance(module) instanceof WebAssembly.Instance;
            }
        } catch (e) { }
        return false;
    }

    // load a script
    function loadScriptAsync(url, doneCallback) {
        var tag = document.createElement('script');
        tag.onload = function () {
            doneCallback();
        };
        tag.onerror = function () {
            throw new Error('failed to load ' + url);
        };
        tag.async = true;
        tag.src = url;
        tag.crossOrigin = 'anonymous';
        document.head.appendChild(tag);
    }

    // load and initialize a wasm module
    function loadWasmModuleAsync(moduleName, jsUrl, binaryUrl, doneCallback) {
        loadScriptAsync(jsUrl, function () {
            var lib = window[moduleName];
            window[moduleName + 'Lib'] = lib;
            lib({ locateFile: function () { return binaryUrl; } } ).then( function (instance) {
                window[moduleName] = instance;
                doneCallback();
            });
        });
    }

    if (typeof modules === "undefined" || modules.length === 0) {
        // caller may depend on callback behaviour being async
        setTimeout(doneCallback);
    } else {
        var asyncCounter = modules.length;
        var asyncCallback = function () {
            asyncCounter--;
            if (asyncCounter === 0) {
                doneCallback();
            }
        };

        var wasm = wasmSupported();
        modules.forEach(function (m) {
            if (!m.hasOwnProperty('preload') || m.preload) {
                if (wasm) {
                    loadWasmModuleAsync(m.moduleName, urlPrefix + m.glueUrl, urlPrefix + m.wasmUrl, asyncCallback);
                } else {
                    if (!m.fallbackUrl) {
                        throw new Error('wasm not supported and no fallback supplied for module ' + m.moduleName);
                    }
                    loadWasmModuleAsync(m.moduleName, urlPrefix + m.fallbackUrl, "", asyncCallback);
                }
            } else {
                asyncCallback();
            }
        });
    }
};

(function () {
    // Shared Lib
    var CANVAS_ID = 'application-canvas';

    // Needed as we will have edge cases for particlar versions of iOS
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
            canvas = document.createElement('canvas'); // eslint-disable-line no-global-assign
            canvas.setAttribute('id', CANVAS_ID);
            canvas.setAttribute('tabindex', 0);
            // canvas.style.visibility = 'hidden';

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
            // Have one tab only, and rotrait to portrait -> landscape -> portrait
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
        logo.src = ASSET_PREFIX + 'logo.png';
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

    };

    var hideSplash = function () {
        var splash = document.getElementById('application-splash-wrapper');
        splash.parentElement.removeChild(splash);
    };

    var setProgress = function (value) {
        var bar = document.getElementById('progress-bar');
        if (bar) {
            value = Math.min(1, Math.max(0, value));
            bar.style.width = value * 100 + '%';
        }
    };

    var createCss = function () {
        var css = [
            'body {',
            '    background-color: #283538;',
            '}',

            '#application-splash-wrapper {',
            '    position: absolute;',
            '    top: 0;',
            '    left: 0;',
            '    height: 100%;',
            '    width: 100%;',
            '    background-color: #283538;',
            '}',

            '#application-splash {',
            '    position: absolute;',
            '    top: calc(50% - 28px);',
            '    width: 264px;',
            '    left: calc(50% - 132px);',
            '}',

            '#application-splash img {',
            '    width: 100%;',
            '}',

            '#progress-bar-container {',
            '    margin: 20px auto 0 auto;',
            '    height: 2px;',
            '    width: 100%;',
            '    background-color: #1d292c;',
            '}',

            '#progress-bar {',
            '    width: 0%;',
            '    height: 100%;',
            '    background-color: #f60;',
            '}',
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
