var Wave = pc.createScript('wave');

Wave.attributes.add('up_speed', { type: 'number', default: 0.001 });
Wave.attributes.add('rot_speed', { type: 'number', default: 0.1 });

// initialize code called once per entity
Wave.prototype.initialize = function() {
    this.elapsedTime = 0;
};

// update code called every frame
Wave.prototype.update = function(dt) {
    this.elapsedTime += dt;

    var p = this.entity.getPosition();
    var e = this.entity.getLocalEulerAngles();
    
    this.entity.setLocalEulerAngles(e.x + Math.cos(this.elapsedTime) * this.rot_speed, e.y, e.z + Math.sin(this.elapsedTime) * this.rot_speed);
    this.entity.setPosition(p.x, p.y + Math.sin(this.elapsedTime) * this.up_speed, p.z);
};

// swap method called for script hot-reloading
// inherit your script state here
// Wave.prototype.swap = function(old) { };

// to learn more about script anatomy, please read:
// https://developer.playcanvas.com/en/user-manual/scripting/