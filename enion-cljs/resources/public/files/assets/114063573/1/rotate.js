var Rotate = pc.createScript('rotate');

Rotate.attributes.add('speed', { type: 'number', default: 10 });

// initialize code called once per entity
Rotate.prototype.initialize = function() {

};

// update code called every frame
Rotate.prototype.update = function(dt) {
    this.entity.rotate(0, this.speed * dt, 0);
};

// swap method called for script hot-reloading
// inherit your script state here
// Rotate.prototype.swap = function(old) { };

// to learn more about script anatomy, please read:
// https://developer.playcanvas.com/en/user-manual/scripting/