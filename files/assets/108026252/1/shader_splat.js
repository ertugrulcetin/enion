var ShaderSplat = pc.createScript('shaderSplat');

ShaderSplat.attributes.add('splatShader', { 
    type: 'asset',
    assetType: 'shader',
    title: 'Splat Shader'
});

ShaderSplat.attributes.add('circleShader', { 
    type: 'asset',
    assetType: 'shader',
    title: 'Circle Shader'
});

ShaderSplat.attributes.add('splatTex', { 
    type: 'asset',
    assetType: 'texture',
    title: 'Splat Map'
});
ShaderSplat.attributes.add('redTex', { 
    type: 'asset',
    assetType: 'texture',
    title: 'Red Texture'
});
ShaderSplat.attributes.add('greenTex', { 
    type: 'asset',
    assetType: 'texture',
    title: 'Green Texture'
});
ShaderSplat.attributes.add('blueTex', { 
    type: 'asset',
    assetType: 'texture',
    title: 'Blue Texture'
});
ShaderSplat.attributes.add('alphaTex', { 
    type: 'asset',
    assetType: 'texture',
    title: 'Alpha Texture'
});

// initialize code called once per entity
ShaderSplat.prototype.initialize = function() {
    
    var app = this.app;
    var render = this.entity.render;
    
    this.material = render.meshInstances[0].material;
    this.player = app.root.findByName("Player");
    var playerPos = this.player.getPosition();
    
    // this.material.chunks.combinePS = this.circleShader.resource;
    // this.material.setParameter('texture_splat', this.alphaTex.resource);
    // this.material.setParameter('character_position', [playerPos.x, playerPos.z]);

    this.material.chunks.diffusePS = this.splatShader.resource;
    this.material.setParameter('splatMap', this.splatTex.resource);
    this.material.setParameter('texture_red', this.redTex.resource);
    this.material.setParameter('texture_green', this.greenTex.resource);
    this.material.setParameter('texture_blue', this.blueTex.resource);
    this.material.setParameter('texture_alpha', this.alphaTex.resource);

    this.material.setParameter('scale_factor', app.root.findByName("Terrain").script.terrain.height * 2);
    this.material.update();
};

// update code called every frame
ShaderSplat.prototype.update = function(dt) {
    var playerPos = this.player.getPosition();
    this.material.setParameter('character_position', [playerPos.x, playerPos.z]);
    this.material.setParameter('dt', (dt * 100) % 3.14);
    this.material.diffuseMapRotation = (dt * 10) % 360;
};