var CameraMovement = pc.createScript('cameraMovement');

CameraMovement.attributes.add('mouseSpeed', { type: 'number', default: 1.4, description: 'Mouse sensitivity' });
CameraMovement.attributes.add('rotationSpeed', { type: 'number', default: 50, description: 'Mouse rotation speed' });
CameraMovement.attributes.add('mouseEdgeRange', { type: 'number', default: 5, description: 'Mouse edge range' });

var pageX = null;
var focusApp = true;
var rightClick = false;
// Called once after all resources are loaded and before the first update
CameraMovement.prototype.initialize = function () {
    this.eulers = new pc.Vec3();

    var app = this.app;

    this.targetAngle = new pc.Vec3();
    this.rayEnd = app.root.findByName('RaycastEndPoint');

    this.wheelClickCounter = { x: 0, clicked: false };

    app.mouse.disableContextMenu();

    app.mouse.on(pc.EVENT_MOUSEMOVE, this.onMouseMove, this);
    app.mouse.on(pc.EVENT_MOUSEWHEEL, this.onMouseWheel, this);
    app.mouse.on(pc.EVENT_MOUSEDOWN, this.onMouseDown, this);
    app.mouse.on(pc.EVENT_MOUSEUP, this.onMouseUp, this);

    document.addEventListener("mouseout", this.onMouseLeave, false);
    document.addEventListener("mouseover", this.onMouseOver, false);

    document.getElementById("application-canvas")
        .addEventListener("auxclick", (e) => {
            if (focusApp && e.which == 2) {
                this.wheelClickCounter.clicked = true;
                this.wheelClickCounter.x = 0;
            }
        }, false);

    let hidden;
    let visibilityChange;
    if (typeof document.hidden !== "undefined") { // Opera 12.10 and Firefox 18 and later support
        hidden = "hidden";
        visibilityChange = "visibilitychange";
    } else if (typeof document.msHidden !== "undefined") {
        hidden = "msHidden";
        visibilityChange = "msvisibilitychange";
    } else if (typeof document.webkitHidden !== "undefined") {
        hidden = "webkitHidden";
        visibilityChange = "webkitvisibilitychange";
    }

    function handleVisibilityChange() {
        if (document[hidden]) {
            focusApp = false;
            pageX = null;
            rightClick = false;
        } else {
            focusApp = true;
        }
    }

    if (typeof document.addEventListener === "undefined" || hidden === undefined) {
        console.log("This demo requires a browser, such as Google Chrome or Firefox, that supports the Page Visibility API.");
    } else {
        document.addEventListener(visibilityChange, handleVisibilityChange, false);
    }
};

CameraMovement.prototype.postUpdate = function (dt) {
    var originEntity = this.entity.parent;

    var targetY = this.eulers.x + 180;
    var targetX = this.eulers.y;

    targetX %= 360;
    targetX = -targetX;
    targetY %= 360;

    if (targetX > -5) {
        targetX = -5;
        this.eulers.y = 5;
    }
    if (targetX < -55) {
        targetX = -55;
        this.eulers.y = 55;
    }

    this.targetAngle.set(targetX, targetY, 0);

    originEntity.setEulerAngles(this.targetAngle);

    this.entity.setPosition(this.getWorldPoint());

    this.entity.lookAt(originEntity.getPosition());

    if (focusApp && (!rightClick && pageX && pageX <= this.mouseEdgeRange || pageX == 0)) {
        this.eulers.x += dt * this.rotationSpeed;
    }
    if (focusApp && (!rightClick && pageX && pageX >= window.innerWidth - this.mouseEdgeRange)) {
        this.eulers.x -= dt * this.rotationSpeed;
    }

    if (this.wheelClickCounter.clicked) {
        if (this.wheelClickCounter.x >= 180) {
            this.wheelClickCounter.clicked = false;
            this.wheelClickCounter.x = 0;
            this.eulers.x %= 360;
            return;
        }
        
        var factor = dt * 150;
        if (this.wheelClickCounter.x + factor < 180) {
            this.eulers.x += factor;
            this.wheelClickCounter.x += factor;
        }
    }
};

CameraMovement.prototype.onMouseDown = function (e) {
    if (e.button === pc.MOUSEBUTTON_RIGHT) {
        rightClick = true;
    }
};

CameraMovement.prototype.onMouseUp = function (e) {
    if (e.button === pc.MOUSEBUTTON_RIGHT) {
        rightClick = false;
    }
};

CameraMovement.prototype.onMouseLeave = function (e) {
    focusApp = false;
    rightClick = false;
};

CameraMovement.prototype.onMouseOver = function (e) {
    focusApp = true;
};

CameraMovement.prototype.onMouseMove = function (e) {
    pageX = e.x;
    if (rightClick) {
        this.eulers.x -= ((this.mouseSpeed * e.dx) / 60) % 360;
        this.eulers.y += ((this.mouseSpeed * e.dy) / 60) % 360;
    }
};

CameraMovement.prototype.onMouseWheel = function (e) {
    var rayPos = this.rayEnd.getLocalPosition();
    var z = rayPos.z + 0.1 * e.wheelDelta;
    if (z < 2) {
        z = 2;
    }
    if (z > 3.5) {
        z = 3.5;
    }
    this.rayEnd.setLocalPosition(rayPos.x, rayPos.y, z);
};

CameraMovement.prototype.onWheelClick = function (e, eulers, wheelClickCounter) {
    if (e.which == 2) {
        eulers.x += wheelClickCounter.x;
    }
};

CameraMovement.prototype.getWorldPoint = function () {
    var from = this.entity.parent.getPosition();
    var to = this.rayEnd.getPosition();

    var hitPoint = to;

    var app = this.app;
    var hit = app.systems.rigidbody.raycastFirst(from, to);
    if (hit && hit.entity.tags.list().includes("char")) {
        return to;
    }

    if (hit) {
        return hit.point;
    }

    return to;
};
