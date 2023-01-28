var FaceCamera = pc.createScript('faceCamera');

// initialize code called once per entity
FaceCamera.prototype.initialize = function () {
    this.camera = this.app.root.findByName('camera');
};

// update code called every frame
FaceCamera.prototype.update = function (dt) {
    this.entity.setRotation(this.camera.getRotation());
};
