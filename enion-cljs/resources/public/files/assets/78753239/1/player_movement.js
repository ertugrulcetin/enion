var PlayerMovement = pc.createScript('playerMovement');

PlayerMovement.attributes.add('speed', {
    type: 'number'
});

PlayerMovement.prototype.initialize = function () {
    var app = this.app;
    this.camera = app.root.findByName('Camera');
    this.cameraScript = this.camera.script.cameraMovement;
    this.modelEntity = app.root.findByName("model");
    this.eulers = new pc.Vec3();
    this.jumped = false;
    this.force = new pc.Vec3();
};

PlayerMovement.prototype.update = function (dt) {
    var force = this.force;
    var app = this.app;

    // Get camera directions to determine movement directions
    var forward = this.camera.forward;
    var right = this.camera.right;

    // movement
    var x = 0;
    var z = 0;

    // Use W-A-S-D keys to move player
    // Check for key presses
    if (app.keyboard.isPressed(pc.KEY_A)) {
        x -= right.x;
        z -= right.z;
    }

    if (app.keyboard.isPressed(pc.KEY_D)) {
        x += right.x;
        z += right.z;
    }

    if (app.keyboard.isPressed(pc.KEY_W)) {
        x += forward.x;
        z += forward.z;
    }

    if (app.keyboard.isPressed(pc.KEY_S)) {
        x -= forward.x;
        z -= forward.z;
    }

    if (app.keyboard.isPressed(pc.KEY_SPACE)) {
        this.entity.rigidbody.applyImpulse(0, 80, 0);
    }

    // use direction from keypresses to apply a force to the character
    if (x !== 0 && z !== 0) {
        force.set(x, 0, z).normalize().scale(this.speed);
        this.entity.rigidbody.applyForce(force);
    }

    var targetY = this.cameraScript.eulers.x + 20;
    if (app.keyboard.isPressed(pc.KEY_A) && app.keyboard.isPressed(pc.KEY_W)) {
        targetY += 45;
    }
    if (app.keyboard.isPressed(pc.KEY_A) && !app.keyboard.isPressed(pc.KEY_W) && !app.keyboard.isPressed(pc.KEY_S)) {
        targetY += 90;
    }
    if (app.keyboard.isPressed(pc.KEY_D) && app.keyboard.isPressed(pc.KEY_W)) {
        targetY -= 45;
    }
    if (app.keyboard.isPressed(pc.KEY_D) && !app.keyboard.isPressed(pc.KEY_W) && !app.keyboard.isPressed(pc.KEY_S)) {
        targetY -= 90;
    }
    if (app.keyboard.isPressed(pc.KEY_A) && app.keyboard.isPressed(pc.KEY_S)) {
        targetY += 135;
    }
    if (app.keyboard.isPressed(pc.KEY_D) && app.keyboard.isPressed(pc.KEY_S)) {
        targetY -= 135;
    }
    if (app.keyboard.isPressed(pc.KEY_S) && !app.keyboard.isPressed(pc.KEY_D) && !app.keyboard.isPressed(pc.KEY_A)) {
        targetY += 180;
    }
    this.modelEntity.setLocalEulerAngles(0, targetY, 0);
};
