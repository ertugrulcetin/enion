var FadeInOut = pc.createScript('fadeInOut');

// initialize code called once per entity
FadeInOut.prototype.initialize = function() {
     this.elapsedTime = 0;
     this.material = this.entity.render.meshInstances[0].material;
     this.material.blendType = pc.BLEND_NORMAL;
};

// update code called every frame
FadeInOut.prototype.update = function(dt) {
    this.elapsedTime += dt;
    this.material.opacity = Math.sin(this.elapsedTime * 5);
    this.material.update();
};

// swap method called for script hot-reloading
// inherit your script state here
// FadeInOut.prototype.swap = function(old) { };

// to learn more about script anatomy, please read:
// https://developer.playcanvas.com/en/user-manual/scripting/