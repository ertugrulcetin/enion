var KeyboardControls = pc.createScript('keyboardControls');

// initialize code called once per entity
KeyboardControls.prototype.initialize = function() {
    this.app.keyboard.on(pc.EVENT_KEYDOWN, this.keyDown, this);
    this.app.keyboard.on(pc.EVENT_KEYUP, this.keyUp, this);
};


KeyboardControls.prototype.keyDown = function (e) {
    if ((e.key === pc.KEY_W) && (this.entity.anim.baseLayer.activeState !== 'run')) {
        this.entity.anim.setBoolean('run', true);
    }
};

KeyboardControls.prototype.keyUp = function (e) {
    if ((e.key === pc.KEY_W) && (this.entity.anim.baseLayer.activeState === 'run')) {
        this.entity.anim.setBoolean('run', false);
    }
};
