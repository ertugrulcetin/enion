var AboveWorld = pc.createScript('aboveWorld');

// initialize code called once per entity
AboveWorld.prototype.initialize = function () {
    const clearDepthLayer = this.app.scene.layers.getLayerByName('Clear Depth');
    clearDepthLayer.clearDepthBuffer = true;
};
