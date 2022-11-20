class Example extends pc.ScriptType {
    
    initialize() {

        const cameraEntity = this.app.root.findByName('Camera');

        const renderer = new AmmoDebugDrawer({
            limit: {
                entity: cameraEntity,
                distance: 50
            }
        });
        renderer.enabled = true;
        
        this.cubeIndex = 0;
        this.reset();
    }
    
    reset() {
        this.time = 0;
        this.delay = pc.math.random(0, 1);        
    }
    
    changeMode(mode) {
        if (!this.renderer) return;
        this.renderer.mode = mode;
    }
    
    update(dt) {
        if (!this.ready || this.allSpawned) return;
        
        this.time += dt;
    }
    
}

pc.registerScript(Example, 'example');
