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
            '    background-image: url(bg.png), url(bg1.png), url(bg2.png), url(bg3.png);',
            '    background-size: cover, cover, cover, cover;',
            '    background-repeat: no-repeat, no-repeat, no-repeat, no-repeat;',
            '    background-position: center center, center center, center center, center center;',
            '    animation: bg-animation 24s infinite linear;',
            '}',
            '',
            '@keyframes bg-animation {',
            '    0% {',
            '        background-image: url(bg.png), url(bg1.png), url(bg2.png), url(bg3.png);',
            '    }',
            '    25% {',
            '        background-image: url(bg1.png), url(bg2.png), url(bg3.png), url(bg.png);',
            '    }',
            '    50% {',
            '        background-image: url(bg2.png), url(bg3.png), url(bg.png), url(bg1.png);',
            '    }',
            '    75% {',
            '        background-image: url(bg3.png), url(bg.png), url(bg1.png), url(bg2.png);',
            '    }',
            '    100% {',
            '        background-image: url(bg.png), url(bg1.png), url(bg2.png), url(bg3.png);',
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
