var PlayerMovement = pc.createScript('playerMovement');

PlayerMovement.attributes.add('power', {
    type: 'number'
});

PlayerMovement.prototype.initialize = function () {
    var app = this.app;
    this.camera = app.root.findByName('Camera');
    this.eulers = new pc.Vec3();
    this.jumped = false;
    this.force = new pc.Vec3();
};

PlayerMovement.prototype.update = function (dt) {
      // If a camera isn't assigned from the Editor, create one
    if (!this.camera) {
        this._createCamera();
    }
    
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
    if (app.keyboard.isPressed(pc.KEY_A) || app.keyboard.isPressed(pc.KEY_Q)) {
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
        force.set(x, 0, z).normalize().scale(this.power);
        this.entity.rigidbody.applyForce(force);
    }

    // this.entity.setLocalEulerAngles(0, this.eulers.y, 0);
    //this.entity.lookAt(10, 0, 10);

    // update camera angle from mouse events
    this.camera.setLocalEulerAngles(this.eulers.y, this.eulers.x, 0);
};

PlayerMovement.prototype._createCamera = function () {
    // If user hasn't assigned a camera, create a new one
    this.camera = new pc.Entity();
    this.camera.setName("First Person Camera");
    this.camera.addComponent("camera");
    this.entity.addChild(this.camera);
    this.camera.translateLocal(0, 0.5, 0);
};
