var KeyboardControls = pc.createScript('keyboardControls');

// initialize code called once per entity
KeyboardControls.prototype.initialize = function () {
    this.app.keyboard.on(pc.EVENT_KEYDOWN, this.keyDown, this);
    this.app.keyboard.on(pc.EVENT_KEYUP, this.keyUp, this);
};


KeyboardControls.prototype.keyDown = function (e) {
    if ((e.key === pc.KEY_W) ||
        (e.key === pc.KEY_S) ||
        (e.key === pc.KEY_A) ||
        (e.key === pc.KEY_D) && (this.entity.anim.baseLayer.activeState !== 'run')) {
        this.entity.anim.setBoolean('run', true);
    }

    if (e.key === pc.KEY_1) {
        this.entity.anim.setBoolean('attack_one_hand', true);
    }
};

KeyboardControls.prototype.keyUp = function (e) {
    if ((e.key === pc.KEY_W) ||
        (e.key === pc.KEY_S) ||
        (e.key === pc.KEY_A) ||
        (e.key === pc.KEY_D) && (this.entity.anim.baseLayer.activeState === 'run')) {
        this.entity.anim.setBoolean('run', false);
    }
    if (e.key === pc.KEY_1) {
        this.entity.anim.setBoolean('attack_one_hand', false);
    }
};

KeyboardControls.prototype.update = function (dt) {
    // if (this.app.keyboard.isPressed(pc.KEY_1) && this.entity.anim.baseLayer.activeState !== 'attack_one_hand') {
    //     this.entity.anim.setBoolean('attack_one_hand', true);
    // } else {
    //     this.entity.anim.setBoolean('attack_one_hand', false);
    // }
};